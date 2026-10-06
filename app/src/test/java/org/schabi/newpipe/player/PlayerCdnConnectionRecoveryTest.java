package org.schabi.newpipe.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.Uri;

import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.io.IOException;
import java.net.ConnectException;

@RunWith(RobolectricTestRunner.class)
public class PlayerCdnConnectionRecoveryTest {
    @Test
    public void failedHlsCdnConnectionUsesMediaUrlRefresh() {
        final Throwable error = new RuntimeException("Source error", connectionFailure(
                "https://rr1---sn-f58xn2xxq-aj5s.googlevideo.com/segment.ts",
                new ConnectException("Failed to connect"), HttpDataSourceException.TYPE_OPEN));
        assertTrue(PlayerHttpErrorRecovery.isRecoverableMediaUrlFailure(error));
        assertTrue(PlayerHttpErrorRecovery.isRecoverableYouTubeMediaUrlFailure(
                error, queueItem(ServiceList.YouTube.getServiceId())));
    }

    @Test
    public void unrelatedAndLookalikeHostsDoNotRefreshYouTubeUrls() {
        for (final String host : new String[] {"example.com", "notgooglevideo.com",
                "googlevideo.com.example.com", "invidious.example.com"}) {
            assertFalse(PlayerHttpErrorRecovery.isRecoverableMediaUrlFailure(connectionFailure(
                    "https://" + host + "/segment.ts", new ConnectException(),
                    HttpDataSourceException.TYPE_OPEN)));
        }
    }

    @Test
    public void readAndTlsFailuresKeepTheirExistingRecoveryPath() {
        assertFalse(PlayerHttpErrorRecovery.isRecoverableMediaUrlFailure(connectionFailure(
                "https://cdn.googlevideo.com/segment.ts", new ConnectException(),
                HttpDataSourceException.TYPE_READ)));
        assertFalse(PlayerHttpErrorRecovery.isRecoverableMediaUrlFailure(connectionFailure(
                "https://cdn.googlevideo.com/segment.ts", new javax.net.ssl.SSLException("TLS"),
                HttpDataSourceException.TYPE_OPEN)));
    }

    @Test
    public void otherServicesAndMissingQueueItemsCannotUseYouTubeRecovery() {
        final Throwable error = connectionFailure("https://cdn.googlevideo.com/segment.ts",
                new ConnectException(), HttpDataSourceException.TYPE_OPEN);
        assertFalse(PlayerHttpErrorRecovery.isRecoverableYouTubeMediaUrlFailure(error, null));
        assertFalse(PlayerHttpErrorRecovery.isRecoverableYouTubeMediaUrlFailure(
                error, queueItem(ServiceList.SoundCloud.getServiceId())));
    }

    private static Throwable connectionFailure(final String url, final IOException cause,
                                                final int type) {
        return new HttpDataSourceException(cause, new DataSpec(Uri.parse(url)),
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, type);
    }

    private static PlayQueueItem queueItem(final int serviceId) {
        final PlayQueueItem item = mock(PlayQueueItem.class);
        when(item.getServiceId()).thenReturn(serviceId);
        return item;
    }
}
