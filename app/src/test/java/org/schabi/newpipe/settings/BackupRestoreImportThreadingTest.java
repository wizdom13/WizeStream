package org.schabi.newpipe.settings;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BackupRestoreImportThreadingTest {
    @Test
    public void databaseValidationRunsOffTheUiThread() throws IOException {
        final String source = readMainSource();
        final int methodStart = source.indexOf("private void importDatabase(");
        final int methodEnd = source.indexOf("Remove settings that are not supposed", methodStart);
        final String method = source.substring(methodStart, methodEnd);

        final int execute = method.indexOf("executor.execute(() -> {");
        final int validation = method.indexOf("NewPipeDatabase.validateImportDatabase(");

        assertTrue(execute >= 0);
        assertTrue(validation > execute);
    }

    @Test
    public void importResultsReturnToTheUiThread() throws IOException {
        final String source = readMainSource();
        final int methodStart = source.indexOf("private void importDatabase(");
        final int methodEnd = source.indexOf("Remove settings that are not supposed", methodStart);
        final String method = source.substring(methodStart, methodEnd);

        assertTrue(method.contains("activity.runOnUiThread(() -> {"));
        assertTrue(method.contains("finishImport(importDataUri);"));
        assertTrue(method.contains(
                "showErrorSnackbar(e, \"Importing database and settings\");"));
    }

    private static String readMainSource() throws IOException {
        final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
                ? Path.of("src/main/java") : Path.of("app/src/main/java");
        return Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/settings/BackupRestoreSettingsFragment.java"));
    }
}
