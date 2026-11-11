/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * R8 9.2.14 injects retarget interfaces in IdentityHashMap iteration order. In AGP 9.2.1
 * this class is named o90. Replace its single IdentityHashMap with a TreeMap: its keys
 * are DexType instances whose natural ordering compares their descriptors.
 *
 * Change the constant-pool class name so NEW, casts, method owners and stack-map
 * frames all refer to TreeMap together. No instructions or other compiler classes change.
 * The exact class checksum deliberately rejects compiler updates until reviewed.
 * Upstream: r8.googlesource.com/r8/+/9.2.14/src/main/java/com/android/tools/r8/ir/
 * desugar/desugaredlibrary/retargeter/DesugaredLibraryRetargeterPostProcessor.java
 */
@CacheableTransform
abstract class StableRetargetInterfaces : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        // Gradle's classpath instrumentation also emits marker files and directories.
        if (input.isDirectory) {
            outputs.dir(input)
            return
        }
        if (input.extension != "jar") {
            outputs.file(input)
            return
        }
        ZipFile(input).use { source ->
            if (source.getEntry("com/android/tools/r8/R8.class") == null) {
                outputs.file(input)
                return
            }
            val target = "com/android/tools/r8/internal/o90.class"
            val entry = checkNotNull(source.getEntry(target)) {
                "Unsupported R8 compiler: review the reproducibility patch before upgrading AGP."
            }
            val original = source.getInputStream(entry).use { it.readBytes() }
            val checksum = MessageDigest.getInstance("SHA-256").digest(original)
                .joinToString("") { "%02x".format(it) }
            check(checksum == "8e4cdbcebf2fbb1353c376ea9393b438900f922332078800abce7178b3f297a8") {
                "R8 retargeter changed ($checksum): review the reproducibility patch."
            }
            val patched = patchClass(original)
            val entries = source.entries().toList().sortedBy { it.name }
            check(entries.none { it.name.matches(Regex("META-INF/.*\\.(SF|RSA|DSA|EC)")) }) {
                "Refusing to modify a signed compiler JAR."
            }
            ZipOutputStream(outputs.file("stable-${input.name}").outputStream().buffered()).use { jar ->
                for (item in entries) {
                    jar.putNextEntry(ZipEntry(item.name).apply { time = 315532800000L })
                    if (item.name == target) {
                        jar.write(patched)
                    } else {
                        source.getInputStream(item).use { it.copyTo(jar) }
                    }
                    jar.closeEntry()
                }
                jar.putNextEntry(ZipEntry(MARKER).apply { time = 315532800000L })
                jar.write("R8 9.2.14: descriptor-ordered retarget interfaces\n".toByteArray())
                jar.closeEntry()
            }
        }
    }

    private fun patchClass(original: ByteArray): ByteArray {
        val result = ByteArrayOutputStream()
        DataInputStream(ByteArrayInputStream(original)).use { input ->
            DataOutputStream(result).use { output ->
                output.write(input.readNBytes(8))
                val count = input.readUnsignedShort()
                output.writeShort(count)
                var index = 1
                var replacements = 0
                while (index < count) {
                    val tag = input.readUnsignedByte()
                    output.writeByte(tag)
                    if (tag == 1) {
                        val text = input.readUTF()
                        if (text == "java/util/IdentityHashMap") {
                            output.writeUTF("java/util/TreeMap")
                            replacements++
                        } else {
                            output.writeUTF(text)
                        }
                    } else {
                        val size = when (tag) {
                            3, 4, 9, 10, 11, 12, 17, 18 -> 4
                            5, 6 -> 8.also { index++ }
                            7, 8, 16, 19, 20 -> 2
                            15 -> 3
                            else -> error("Unexpected class constant tag: $tag")
                        }
                        output.write(input.readNBytes(size))
                    }
                    index++
                }
                check(replacements == 1) { "Expected exactly one retargeter map replacement." }
                input.copyTo(output)
            }
        }
        return result.toByteArray()
    }

    companion object {
        const val MARKER = "META-INF/wizestream-stable-retarget-interfaces.txt"
    }
}

fun patchCompilerClasspath(handler: ScriptHandler) {
    val patched = Attribute.of("org.wizestream.stable-retarget-interfaces", Boolean::class.javaObjectType)
    // Patch before Gradle 9.5.1 analyzes/instruments the classpath, so its original-artifact
    // registry and instrumentation analysis both describe the same corrected compiler.
    val instrumentation = Attribute.of("org.gradle.internal.instrumented", String::class.java)
    handler.dependencies.apply {
        attributesSchema.attribute(patched)
        artifactTypes.maybeCreate(ArtifactTypeDefinition.JAR_TYPE).attributes.attribute(patched, false)
        registerTransform(StableRetargetInterfaces::class.java) {
            from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                .attribute(patched, false)
                .attribute(instrumentation, "not-instrumented")
            to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                .attribute(patched, true)
                .attribute(instrumentation, "not-instrumented")
        }
    }
    handler.configurations.configureEach {
        attributes.attribute(patched, true)
    }
}

gradle.beforeSettings {
    patchCompilerClasspath(buildscript)
}

gradle.beforeProject {
    patchCompilerClasspath(buildscript)
}
