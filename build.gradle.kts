/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

buildscript {
    dependencies {
        // https://developer.android.com/build/releases/agp-9-0-0-release-notes#runtime-dependency-on-kotlin-gradle-plugin-upgrade
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.legacy.kapt) apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.jetbrains.kotlin.parcelize) apply false
    alias(libs.plugins.jetbrains.kotlinx.serialization) apply false
    alias(libs.plugins.squareup.wire) apply false
}

tasks.register("verifyReproducibleEnvironment") {
    group = "verification"
    description = "Checks the JVM constraints required for reproducible release builds"

    doLast {
        val activeProcessors = Runtime.getRuntime().availableProcessors()
        check(activeProcessors == 1) {
            "Reproducible release builds require exactly one active processor, " +
                "but the Gradle JVM detected $activeProcessors"
        }
        logger.lifecycle("Reproducible release JVM active processors: $activeProcessors")
    }
}
