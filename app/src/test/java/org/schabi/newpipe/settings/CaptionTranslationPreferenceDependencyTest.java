/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.settings;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class CaptionTranslationPreferenceDependencyTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void languagePreferenceIsAttachedBeforeRegisteringDependency() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/settings/VideoAudioSettingsFragment.java"));
        final String setupMethod = methodBody(
                source,
                "private void setupCaptionTranslationPreferences()");

        final int autoTranslateAdded = setupMethod.indexOf(
                "category.addPreference(autoTranslate);");
        final int languageAdded = setupMethod.indexOf(
                "category.addPreference(language);");
        final int dependencyRegistered = setupMethod.indexOf(
                "language.setDependency(getString(R.string.caption_auto_translate_key));");

        assertTrue(autoTranslateAdded >= 0);
        assertTrue(languageAdded > autoTranslateAdded);
        assertTrue(dependencyRegistered > languageAdded);
    }

    private static String methodBody(final String source, final String signature) {
        final int signatureIndex = source.indexOf(signature);
        assertTrue("Missing method: " + signature, signatureIndex >= 0);
        final int bodyStart = source.indexOf('{', signatureIndex);
        assertTrue("Missing method body: " + signature, bodyStart >= 0);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            if (source.charAt(i) == '{') {
                depth++;
            } else if (source.charAt(i) == '}' && --depth == 0) {
                return source.substring(bodyStart, i + 1);
            }
        }
        throw new AssertionError("Unclosed method body: " + signature);
    }
}
