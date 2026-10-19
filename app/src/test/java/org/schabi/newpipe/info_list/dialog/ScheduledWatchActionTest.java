package org.schabi.newpipe.info_list.dialog;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.extractor.exceptions.LiveNotStartException;
import org.schabi.newpipe.extractor.exceptions.VideoNotReleaseException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 35}, application = Application.class)
public class ScheduledWatchActionTest {
    @Test
    public void scheduledStreamAndPremiereExplainAvailabilityWithoutAnErrorReport() {
        final Application context = RuntimeEnvironment.getApplication();
        final Throwable[] errors = {
            new LiveNotStartException("This live event will begin in 13 hours."),
            new VideoNotReleaseException("Premiere has not started."),
            new RuntimeException(new LiveNotStartException("Scheduled")),
            new RuntimeException(new IllegalStateException(
                    new VideoNotReleaseException("Scheduled")))
        };
        try (MockedStatic<ErrorUtil> reports = mockStatic(ErrorUtil.class)) {
            for (final Throwable error : errors) {
                StreamDialogDefaultEntry.handleMarkAsWatchedError(context, error);
                assertEquals(context.getString(R.string.mark_watched_not_started),
                        ShadowToast.getTextOfLatestToast());
            }
            reports.verifyNoInteractions();
        }
    }

    @Test
    public void unexpectedErrorsRemainReportable() {
        final Application context = RuntimeEnvironment.getApplication();
        final RuntimeException first = new RuntimeException("Database failure");
        try (MockedStatic<ErrorUtil> reports = mockStatic(ErrorUtil.class)) {
            StreamDialogDefaultEntry.handleMarkAsWatchedError(context, first);
            reports.verify(() -> ErrorUtil.showSnackbar(eq(context), any(
                    org.schabi.newpipe.error.ErrorInfo.class)));
        }
    }
}
