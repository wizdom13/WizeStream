package org.schabi.newpipe.player.seekbarpreview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.graphics.Bitmap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.schabi.newpipe.extractor.stream.Frameset;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import coil3.request.Disposable;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SeekbarPreviewThumbnailHolderTest {
    private final List<Pending> requests = new ArrayList<>();
    private SeekbarPreviewThumbnailHolder holder;
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        holder = new SeekbarPreviewThumbnailHolder((ctx, url, callback) -> {
            final Pending pending = new Pending(url, callback);
            requests.add(pending);
            return pending.disposable;
        });
        reset();
    }

    private void reset() {
        holder.resetFrom(context, List.of(new Frameset(
                List.of("page0", "page1", "page2", "page3"), 2, 2, 8, 1000, 2, 1)), 0);
    }

    @Test
    public void resetDoesNotDownloadAnyStoryboards() {
        assertTrue(requests.isEmpty());
    }

    @Test
    public void samePageDeliversLatestFrameWithoutRestartingRequest() {
        final AtomicReference<Bitmap> first = new AtomicReference<>();
        final AtomicReference<Bitmap> latest = new AtomicReference<>();
        holder.requestBitmapAt(0, first::set);
        holder.requestBitmapAt(1000, latest::set);
        assertEquals(1, requests.size());
        final Bitmap page = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888);
        page.setPixel(2, 0, 0xff00ff00);
        requests.get(0).callback.accept(page);
        assertNull(first.get());
        assertEquals(0xff00ff00, latest.get().getPixel(0, 0));
    }

    @Test
    public void changingPageCancelsAndIgnoresOldResult() {
        final AtomicReference<Bitmap> latest = new AtomicReference<>();
        holder.requestBitmapAt(0, latest::set);
        holder.requestBitmapAt(4000, latest::set);
        verify(requests.get(0).disposable).dispose();
        assertEquals("page2", requests.get(1).url);
        requests.get(0).callback.accept(page());
        assertNull(latest.get());
        requests.get(1).callback.accept(page());
        assertEquals(2, latest.get().getWidth());
    }

    @Test
    public void resetIgnoresPreviousVideoAndDisabledSettingDownloadsNothing() {
        final AtomicReference<Bitmap> preview = new AtomicReference<>();
        holder.requestBitmapAt(0, preview::set);
        holder.resetFrom(context, List.of(), 2);
        verify(requests.get(0).disposable).dispose();
        requests.get(0).callback.accept(page());
        holder.requestBitmapAt(0, preview::set);
        assertNull(preview.get());
        assertEquals(1, requests.size());
    }

    @Test
    public void stopSeekingCancelsPendingCallback() {
        final AtomicReference<Bitmap> preview = new AtomicReference<>();
        holder.requestBitmapAt(0, preview::set);
        holder.cancelRequest();
        requests.get(0).callback.accept(page());
        assertNull(preview.get());
        verify(requests.get(0).disposable).dispose();
    }

    @Test
    public void cacheKeepsAtMostThreePagesAndClampsVideoEndpoint() {
        for (int i = 0; i < 4; i++) {
            holder.requestBitmapAt(i * 2000, bitmap -> { });
            requests.get(i).callback.accept(page());
        }
        holder.requestBitmapAt(8000, bitmap -> { });
        assertEquals(4, requests.size()); // Last actual frame is on cached page3.
        holder.requestBitmapAt(0, bitmap -> { });
        assertEquals(5, requests.size()); // page0 was evicted.
    }

    @Test
    public void oversizedPageIsNotRetainedAndCropDoesNotShareSourceOwnership() {
        final AtomicReference<Bitmap> preview = new AtomicReference<>();
        holder.requestBitmapAt(0, preview::set);
        final Bitmap oversized = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888);
        requests.get(0).callback.accept(oversized);
        assertNotSame(oversized, preview.get());
        preview.get().recycle();
        assertFalse(oversized.isRecycled());
        holder.requestBitmapAt(0, preview::set);
        assertEquals(2, requests.size());
    }

    @Test
    public void wholePageCropCanBeRecycledWithoutPoisoningCachedPage() {
        holder.resetFrom(context, List.of(new Frameset(
                List.of("single"), 2, 2, 1, 1000, 1, 1)), 0);
        final AtomicReference<Bitmap> preview = new AtomicReference<>();
        holder.requestBitmapAt(0, preview::set);
        final Bitmap source = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                .copy(Bitmap.Config.ARGB_8888, false);
        requests.get(0).callback.accept(source);
        assertNotSame(source, preview.get());
        preview.get().recycle();
        holder.requestBitmapAt(0, preview::set);
        assertEquals(1, requests.size());
        assertFalse(source.isRecycled());
        assertFalse(preview.get().isRecycled());
    }

    @Test
    public void undersizedPageReturnsNoPreviewInsteadOfInvalidCrop() {
        final AtomicReference<Bitmap> preview = new AtomicReference<>();
        holder.requestBitmapAt(0, preview::set);
        requests.get(0).callback.accept(
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888));
        assertNull(preview.get());
    }

    private static Bitmap page() {
        return Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888);
    }

    private static final class Pending {
        private final String url;
        private final Consumer<Bitmap> callback;
        private final Disposable disposable = mock(Disposable.class);

        private Pending(final String url, final Consumer<Bitmap> callback) {
            this.url = url;
            this.callback = callback;
        }
    }
}
