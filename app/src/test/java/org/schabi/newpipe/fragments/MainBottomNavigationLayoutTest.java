package org.schabi.newpipe.fragments;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class MainBottomNavigationLayoutTest {
    private final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
            ? Path.of("src/main/java") : Path.of("app/src/main/java");

    @Test
    public void menuSelectionAndResumeScheduleAFullRemeasure() throws Exception {
        final String source = Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/fragments/MainFragment.java"));

        assertMethodContains(source, "public void onResume()",
                "scheduleBottomNavigationRemeasure();");
        assertMethodContains(source, "private void updateBottomNavigationItems()",
                "scheduleBottomNavigationRemeasure();");
        assertMethodContains(source, "private void updateBottomNavigationSelection(",
                "scheduleBottomNavigationRemeasure();");
        assertMethodContains(source, "private void scheduleBottomNavigationRemeasure()",
                "navigation.post(() -> {");
        assertMethodContains(source, "private void scheduleBottomNavigationRemeasure()",
                "navigation.requestLayout();");
        assertMethodContains(source, "private void scheduleBottomNavigationRemeasure()",
                "navigation.getChildAt(i).requestLayout();");
    }

    private void assertMethodContains(final String source,
                                      final String signature,
                                      final String expected) {
        final int methodStart = source.indexOf(signature);
        assertTrue(signature, methodStart >= 0);
        final int nextMethod = source.indexOf("\n    private ", methodStart + signature.length());
        final int methodEnd = nextMethod >= 0 ? nextMethod : source.length();
        assertTrue(signature + " should contain " + expected,
                source.substring(methodStart, methodEnd).contains(expected));
    }
}
