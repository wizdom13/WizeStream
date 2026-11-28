/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Makes the external SDK's API-23 interface companions real R8 inputs. R8 9 no longer
 * applies interface keep rules to companions it synthesizes itself. A native plugin's
 * separately compiled DEX still calls those stable $-CC classes directly.
 *
 * Only extractor classes are desugared here; the original compile outputs and exported
 * pluginSdkJar remain unchanged. Core-library desugaring and all app optimization are
 * left to AGP. Sorted inputs, one D8 worker and normalized ZIP metadata are reproducible.
 *
 * R8 documents this two-stage approach for separately compiled clients:
 * https://r8.googlesource.com/r8/+/1e46b9385d5c917c693c0c143bb01e6d0b9e6bbd
 */
@CacheableTask
abstract class DesugarPluginApi : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputJars: ListProperty<RegularFile>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectories: ListProperty<Directory>

    @get:Classpath
    abstract val compileClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val androidLibraries: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val compilerJar: RegularFileProperty

    @get:Input
    abstract val minimumApi: Property<Int>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun desugar() {
        val original = sortedMapOf<String, ByteArray>()
        fun add(name: String, content: ByteArray) {
            val previous = original.putIfAbsent(name, content)
            check(previous == null || previous.contentEquals(content)) { "Conflicting project class: $name" }
        }
        inputJars.get().map { it.asFile }.sortedBy(File::getAbsolutePath).forEach { file ->
            ZipFile(file).use { jar ->
                jar.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                    add(entry.name, jar.getInputStream(entry).use { it.readBytes() })
                }
            }
        }
        inputDirectories.get().map { it.asFile }.sortedBy(File::getAbsolutePath).forEach { directory ->
            directory.walkTopDown().filter { it.isFile }.forEach { file ->
                add(file.relativeTo(directory).invariantSeparatorsPath, file.readBytes())
            }
        }
        val extractor = original.filterKeys { it.startsWith(EXTRACTOR_PREFIX) && it.endsWith(".class") }
        check(extractor.isNotEmpty()) { "No compiled plugin SDK classes were supplied" }
        val remaining = original.filterKeys { it !in extractor }
        val source = File(temporaryDir, "extractor.jar")
        val otherClasses = File(temporaryDir, "other-project-classes.jar")
        val desugared = File(temporaryDir, "desugared-extractor.jar")
        writeJar(source, extractor)
        writeJar(otherClasses, remaining)
        desugared.delete()
        runD8(source, otherClasses, desugared)
        val materialized = sortedMapOf<String, ByteArray>()
        ZipFile(desugared).use { jar ->
            jar.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                materialized[entry.name] = jar.getInputStream(entry).use { it.readBytes() }
            }
        }
        REQUIRED_COMPANIONS.forEach { name ->
            check(materialized.containsKey(name)) { "D8 did not materialize the plugin ABI companion: $name" }
        }
        check(materialized.keys.none { it in remaining }) { "Desugared SDK collides with an app class" }
        writeJar(outputJar.get().asFile, (remaining + materialized).toSortedMap())
    }

    private fun runD8(source: File, otherClasses: File, output: File) {
        // An isolated loader uses AGP's exact embedded compiler without adding a second
        // R8 dependency to Gradle's plugin classpath or changing the host shrinker.
        URLClassLoader(arrayOf(compilerJar.get().asFile.toURI().toURL()), ClassLoader.getPlatformClassLoader()).use { loader ->
            val commandType = loader.loadClass("com.android.tools.r8.D8Command")
            val builder = commandType.getMethod("builder").invoke(null)
            fun call(name: String, parameter: Class<*>, value: Any) {
                builder.javaClass.getMethod(name, parameter).invoke(builder, value)
            }
            call("addProgramFiles", Collection::class.java, listOf(source.toPath()))
            call("addLibraryFiles", Collection::class.java, androidLibraries.files.sortedBy(File::getAbsolutePath).map(File::toPath))
            call(
                "addClasspathFiles",
                Collection::class.java,
                (compileClasspath.files + otherClasses).sortedBy(File::getAbsolutePath).map(File::toPath)
            )
            call("setMinApiLevel", Int::class.javaPrimitiveType!!, minimumApi.get())
            val modeType = loader.loadClass("com.android.tools.r8.CompilationMode")
            call("setMode", modeType, modeType.getField("RELEASE").get(null))
            val consumerType = loader.loadClass("com.android.tools.r8.ClassFileConsumer\$ArchiveConsumer")
            val consumer = consumerType.getConstructor(Path::class.java).newInstance(output.toPath())
            call("setProgramConsumer", loader.loadClass("com.android.tools.r8.ProgramConsumer"), consumer)
            val executor = Executors.newSingleThreadExecutor()
            try {
                val command = builder.javaClass.getMethod("build").invoke(builder)
                loader.loadClass("com.android.tools.r8.D8")
                    .getMethod("run", commandType, java.util.concurrent.ExecutorService::class.java)
                    .invoke(null, command, executor)
            } catch (failure: InvocationTargetException) {
                throw failure.targetException
            } finally {
                executor.shutdown()
            }
        }
    }

    private fun writeJar(file: File, entries: Map<String, ByteArray>) {
        file.parentFile.mkdirs()
        ZipOutputStream(file.outputStream().buffered()).use { output ->
            entries.toSortedMap().forEach { (name, content) ->
                output.putNextEntry(ZipEntry(name).apply { time = 0 })
                output.write(content)
                output.closeEntry()
            }
        }
    }

    companion object {
        private const val EXTRACTOR_PREFIX = "org/schabi/newpipe/extractor/"
        private val REQUIRED_COMPANIONS = listOf(
            "${EXTRACTOR_PREFIX}InfoItemExtractor\$-CC.class",
            "${EXTRACTOR_PREFIX}stream/StreamInfoItemExtractor\$-CC.class"
        )
    }
}
