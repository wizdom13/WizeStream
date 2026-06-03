/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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
        maven(url = "https://jitpack.io")
        maven(url = "https://repo.clojars.org")
    }
}
include(":app") // androidApp
include(":desktopApp")
include("shared")

val externalNewPipeExtractor = file("external/NewPipeExtractor")
val adjacentNewPipeExtractor = file("../NewPipeExtractor")

val newPipeExtractorDir = when {
    externalNewPipeExtractor.isDirectory -> externalNewPipeExtractor
    adjacentNewPipeExtractor.isDirectory -> adjacentNewPipeExtractor
    else -> throw GradleException(
        "NewPipeExtractor source checkout not found. " +
            "Clone https://github.com/wizdom13/NewPipeExtractor into external/NewPipeExtractor " +
            "or run git clone https://github.com/wizdom13/NewPipeExtractor ..\\NewPipeExtractor " +
            "for a Windows-style adjacent checkout."
    )
}

includeBuild(newPipeExtractorDir) {
    dependencySubstitution {
        substitute(module("com.github.TeamNewPipe:NewPipeExtractor"))
            .using(project(":extractor"))
    }
}
