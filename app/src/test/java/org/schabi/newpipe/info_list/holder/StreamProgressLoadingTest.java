package org.schabi.newpipe.info_list.holder;

import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.views.AnimatedProgressBar;

import java.io.IOException;

import io.reactivex.rxjava3.subjects.SingleSubject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class StreamProgressLoadingTest {
    private StreamMiniInfoItemHolder holder;
    private AnimatedProgressBar progress;
    private HistoryRecordManager manager;

    @Before
    public void setup() {
        final Application context = RuntimeEnvironment.getApplication();
        context.setTheme(R.style.LightTheme);
        holder = new StreamMiniInfoItemHolder(new InfoItemBuilder(context),
                new FrameLayout(context));
        progress = holder.itemView.findViewById(R.id.itemProgressView);
        manager = mock(HistoryRecordManager.class);
    }

    @After
    public void cleanup() {
        holder.recycle();
    }

    private StreamInfoItem item(final String id) {
        final StreamInfoItem item = new StreamInfoItem(0, "https://example.org/" + id,
                id, StreamType.VIDEO_STREAM);
        item.setDuration(100);
        item.setUploaderName("Channel");
        return item;
    }

    @Test(timeout = 5000)
    public void bindingReturnsWhileProgressIsPending() {
        final StreamInfoItem item = item("first");
        final SingleSubject<StreamStateEntity[]> pending = SingleSubject.create();
        when(manager.loadStreamState(item)).thenReturn(pending);
        holder.updateFromItem(item, manager);
        assertTrue(pending.hasObservers());
        assertEquals(View.GONE, progress.getVisibility());
        pending.onSuccess(new StreamStateEntity[]{new StreamStateEntity(1, 60000)});
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.VISIBLE, progress.getVisibility());
        assertEquals(60, progress.getProgress());
    }

    @Test(timeout = 5000)
    public void rebindingCancelsPendingAndQueuedResults() {
        final StreamInfoItem first = item("first");
        final StreamInfoItem second = item("second");
        final SingleSubject<StreamStateEntity[]> old = SingleSubject.create();
        final SingleSubject<StreamStateEntity[]> next = SingleSubject.create();
        when(manager.loadStreamState(first)).thenReturn(old);
        when(manager.loadStreamState(second)).thenReturn(next);
        holder.updateFromItem(first, manager);
        old.onSuccess(new StreamStateEntity[]{new StreamStateEntity(1, 90000)});
        holder.updateFromItem(second, manager);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.GONE, progress.getVisibility());
        next.onSuccess(new StreamStateEntity[]{new StreamStateEntity(2, 20000)});
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(20, progress.getProgress());
        assertEquals(View.VISIBLE, progress.getVisibility());
    }

    @Test(timeout = 5000)
    public void recyclingDisposesTheProgressRequest() {
        final StreamInfoItem item = item("first");
        final SingleSubject<StreamStateEntity[]> pending = SingleSubject.create();
        when(manager.loadStreamState(item)).thenReturn(pending);
        holder.updateFromItem(item, manager);
        holder.recycle();
        assertFalse(pending.hasObservers());
        pending.onSuccess(new StreamStateEntity[]{new StreamStateEntity(1, 90000)});
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.GONE, progress.getVisibility());
        assertEquals(0, progress.getProgress());
    }

    @Test(timeout = 5000)
    public void progressRefreshHandlesErrorsWithoutCrashing() {
        final StreamInfoItem item = item("first");
        final SingleSubject<StreamStateEntity[]> pending = SingleSubject.create();
        when(manager.loadStreamState(item)).thenReturn(pending);
        holder.updateState(item, manager);
        assertTrue(pending.hasObservers());
        pending.onError(new IOException("Database unavailable"));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.GONE, progress.getVisibility());
    }
}
