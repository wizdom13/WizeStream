/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
rootProject.name = "NewPipe_Material"

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
val adjacentNewPipeExtractor = file("../PipePipeExtractor")

val newPipeExtractorDir = when {
    externalNewPipeExtractor.isDirectory -> externalNewPipeExtractor
    adjacentNewPipeExtractor.isDirectory -> adjacentNewPipeExtractor
    else -> throw GradleException(
        "PipePipeExtractor source checkout not found. " +
            "Clone https://github.com/wizdom13/PipePipeExtractor into external/NewPipeExtractor " +
            "or run git clone https://github.com/wizdom13/PipePipeExtractor ..\\PipePipeExtractor " +
            "for a Windows-style adjacent checkout. The external directory name remains " +
            "NewPipeExtractor because the app still depends on the TeamNewPipe artifact name " +
            "while this experiment substitutes it with PipePipeExtractor source."
    )
}

// Temporary experiment: resolve the TeamNewPipe extractor artifact to PipePipeExtractor
// source so local and CI builds exercise the same extractor checkout.
includeBuild(newPipeExtractorDir) {
    dependencySubstitution {
        substitute(module("com.github.TeamNewPipe:NewPipeExtractor"))
            .using(project(":extractor"))
    }
}
