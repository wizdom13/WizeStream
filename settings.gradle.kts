/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
pluginManagement {
    val reproducibleBuildProperties = java.util.Properties().apply {
        file("gradle/reproducible-build.properties").inputStream().use { load(it) }
    }

    plugins {
        id("com.android.settings") version
            reproducibleBuildProperties.getProperty("androidGradlePluginVersion")
    }

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.settings")
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
rootProject.name = "WizeStream"

android {
    execution {
        profiles {
            create("reproducibleRelease") {
                r8 {
                    runInSeparateProcess = true
                    jvmOptions.addAll(
                        listOf(
                            "-Xmx2048m",
                            "-XX:ActiveProcessorCount=1",
                            "-Dfile.encoding=UTF-8",
                            "-Duser.language=en",
                            "-Duser.country=US",
                            "-Duser.timezone=UTC"
                        )
                    )
                }
            }
        }
        defaultProfile = "reproducibleRelease"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://dl.cloudsmith.io/public/consensys/maven/maven/")
        maven(url = "https://dl.cloudsmith.io/public/libp2p/jvm-libp2p/maven/")
        maven(url = "https://jitpack.io")
        maven(url = "https://repo.clojars.org")
    }
}
include(":app") // androidApp
