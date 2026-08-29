/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class ReproducibleBuildConfigurationTest {
    private final Path repositoryRoot = Files.exists(Path.of("scripts/reproducible-build.sh"))
            ? Path.of(".") : Path.of("..");

    @Test
    public void releaseScriptSerializesTheCompleteJavaAndGradlePipeline() throws Exception {
        final String script = read("scripts/reproducible-build.sh");

        assertTrue(script.contains("export JAVA_TOOL_OPTIONS="));
        assertTrue(script.contains("-XX:ActiveProcessorCount=1"));
        assertTrue(script.contains("--max-workers=1"));
        assertTrue(script.contains("verifyReproducibleEnvironment"));
    }

    @Test
    public void releaseBuildFailsWhenTheGradleJvmSeesMultipleProcessors() throws Exception {
        final String buildScript = read("build.gradle.kts");

        assertTrue(buildScript.contains("tasks.register(\"verifyReproducibleEnvironment\")"));
        assertTrue(buildScript.contains("Runtime.getRuntime().availableProcessors()"));
        assertTrue(buildScript.contains("check(activeProcessors == 1)"));
    }

    @Test
    public void ciAndPublishingUseTheSharedReproducibleEntryPoint() throws Exception {
        final String ciWorkflow = read(".github/workflows/ci.yml");
        final String releaseWorkflow = read(".github/workflows/release.yml");

        assertEquals(1, occurrences(ciWorkflow, "scripts/reproducible-build.sh"));
        assertEquals(2, occurrences(releaseWorkflow, "scripts/reproducible-build.sh"));
    }

    @Test
    public void preReleasePublishingUsesAnExplicitNonLatestBranchTrigger() throws Exception {
        final String releaseWorkflow = read(".github/workflows/release.yml");

        assertTrue(releaseWorkflow.contains("- \"release-pre-*\""));
        assertTrue(releaseWorkflow.contains("if [ \"$GITHUB_REF_TYPE\" = \"tag\" ]"));
        assertTrue(releaseWorkflow.contains("github.ref_type == 'tag' && github.ref_name"));
        assertTrue(releaseWorkflow.contains("prerelease: ${{ github.ref_type == 'branch' }}"));
        assertTrue(releaseWorkflow.contains("make_latest: ${{ github.ref_type == 'tag' }}"));
        assertTrue(releaseWorkflow.contains("github.event_name != 'pull_request' ||"));
        assertTrue(releaseWorkflow.contains("startsWith(github.head_ref, 'release-pre-')"));
        assertEquals(2, occurrences(releaseWorkflow,
                "github.event_name == 'pull_request'"));
    }

    @Test
    public void downstreamInstructionsRequireTheSharedEntryPoint() throws Exception {
        final String building = read("BUILDING.md");

        assertTrue(building.contains("Independent rebuilders must use "
                + "`scripts/reproducible-build.sh`"));
        assertTrue(building.contains("rather than invoking\n`./gradlew assembleRelease` directly"));
    }

    private String read(final String relativePath) throws Exception {
        return Files.readString(repositoryRoot.resolve(relativePath));
    }

    private int occurrences(final String text, final String value) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }
}
