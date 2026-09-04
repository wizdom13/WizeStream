package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VideoDetailLiveStreamErrorTest {
    @Test
    public void liveNotStartedUsesDialogInsteadOfGenericErrorReporting() throws IOException {
        final String source = readMainSource();
        final int workerStart = source.indexOf("private void runWorker(");
        final int dialogStart = source.indexOf(
                "private void showLiveNotStartedDialog()", workerStart);

        assertTrue(workerStart >= 0);
        assertTrue(dialogStart > workerStart);

        final String workerFlow = source.substring(workerStart, dialogStart);
        final int liveCheck = workerFlow.indexOf(
                "if (throwable instanceof LiveNotStartException");
        final int unreleasedCheck = workerFlow.indexOf(
                "throwable instanceof VideoNotReleaseException", liveCheck);
        final int liveDialog = workerFlow.indexOf("showLiveNotStartedDialog()", liveCheck);
        final int genericError = workerFlow.indexOf("showError(new ErrorInfo(", liveDialog);

        assertTrue(liveCheck >= 0);
        assertTrue(unreleasedCheck > liveCheck);
        assertTrue(unreleasedCheck < liveDialog);
        assertTrue(liveDialog > liveCheck);
        assertTrue(genericError > liveDialog);
    }

    @Test
    public void liveNotStartedDialogStopsLoadingAndOffersRetry() throws IOException {
        final String source = readMainSource();
        final int dialogStart = source.indexOf("private void showLiveNotStartedDialog()");
        final int dialogEnd = source.indexOf(
                "//////////////////////////////////////////////////////////////////////////\n"
                        + "    // Tabs", dialogStart);

        assertTrue(dialogStart >= 0);
        assertTrue(dialogEnd > dialogStart);

        final String dialog = source.substring(dialogStart, dialogEnd);
        assertTrue(dialog.contains("handleError();"));
        assertTrue(source.contains(
                "import com.google.android.material.dialog."
                        + "MaterialAlertDialogBuilder;"));
        assertTrue(dialog.contains(
                "new MaterialAlertDialogBuilder(activity)"));
        assertTrue(dialog.contains(
                ".setTitle(R.string.live_stream_not_started_title)"));
        assertTrue(dialog.contains(
                ".setMessage(R.string.live_stream_not_started_message)"));
        assertTrue(dialog.contains(".setNegativeButton(R.string.close, null)"));
        assertTrue(dialog.contains(
                ".setPositiveButton(R.string.retry, (dialog, which) -> reloadContent())"));
    }

    @Test
    public void liveNotStartedDialogStringsAreDefined() throws IOException {
        final Path resourceDirectory = Files.exists(Path.of("src/main/res"))
                ? Path.of("src/main/res") : Path.of("app/src/main/res");
        final String strings = Files.readString(resourceDirectory.resolve("values/strings.xml"));

        assertTrue(strings.contains(
                "<string name=\"live_stream_not_started_title\">"
                        + "Live stream has not started</string>"));
        assertTrue(strings.contains(
                "<string name=\"live_stream_not_started_message\">"
                        + "This live stream has not started yet. "
                        + "Please try again when it begins.</string>"));
    }

    private static String readMainSource() throws IOException {
        final Path sourceDirectory = Files.exists(Path.of("src/main/java"))
                ? Path.of("src/main/java") : Path.of("app/src/main/java");
        return Files.readString(sourceDirectory.resolve(
                "org/schabi/newpipe/fragments/detail/VideoDetailFragment.java"));
    }
}
