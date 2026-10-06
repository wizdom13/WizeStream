package org.schabi.newpipe.local.history;

import android.app.Application;

import androidx.preference.PreferenceManager;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.sync.HistorySyncRecorder;
import org.schabi.newpipe.util.ExtractorHelper;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class MarkWatchedTransactionTest {
    @After
    public void resetSchedulers() {
        RxJavaPlugins.reset();
    }

    @Test
    public void missingDurationIsFetchedBeforeStartingTheDatabaseTransaction() {
        RxJavaPlugins.setIoSchedulerHandler(scheduler -> Schedulers.trampoline());
        final Application context = RuntimeEnvironment.getApplication();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(context.getString(R.string.enable_watch_history_key), true).commit();
        final AppDatabase database = mock(AppDatabase.class);
        final AtomicBoolean transactionStarted = new AtomicBoolean();
        final AtomicBoolean extractionStarted = new AtomicBoolean();
        when(database.runInTransaction(any(Callable.class))).thenAnswer(invocation -> {
            transactionStarted.set(true);
            return ((Callable<?>) invocation.getArgument(0)).call();
        });
        final StreamInfoItem item = new StreamInfoItem(0, "https://example.org/video",
                "Video", StreamType.VIDEO_STREAM);
        item.setDuration(-1);
        try (MockedStatic<NewPipeDatabase> databases = mockStatic(NewPipeDatabase.class);
             MockedStatic<HistorySyncRecorder> recorders = mockStatic(HistorySyncRecorder.class);
             MockedStatic<ExtractorHelper> extractors = mockStatic(ExtractorHelper.class)) {
            databases.when(() -> NewPipeDatabase.getInstance(context)).thenReturn(database);
            extractors.when(() -> ExtractorHelper.getStreamInfo(0, item.getUrl(), false))
                    .thenReturn(Single.defer(() -> {
                        extractionStarted.set(true);
                        assertFalse(transactionStarted.get());
                        return Single.error(new IOException("Extraction failed"));
                    }));
            new HistoryRecordManager(context, "profile").markAsWatched(item).test()
                    .awaitDone(5, TimeUnit.SECONDS)
                    .assertError(error -> error instanceof RuntimeException
                            && error.getCause() instanceof IOException);
            assertTrue(extractionStarted.get());
            assertFalse(transactionStarted.get());
        }
    }
}
