/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
rootProject.name = "WizeStream"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
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
include(":ffmpeg")

val wizeStreamExtractorDir = file("external/WizeStreamExtractor")
if (!wizeStreamExtractorDir.isDirectory) {
    throw GradleException(
        "WizeStreamExtractor submodule checkout not found. " +
            "Run git submodule update --init --recursive or scripts/prepare-extractor.sh."
    )
}

includeBuild(wizeStreamExtractorDir) {
    dependencySubstitution {
        substitute(module("com.github.TeamNewPipe:NewPipeExtractor"))
            .using(project(":extractor"))
    }
}
