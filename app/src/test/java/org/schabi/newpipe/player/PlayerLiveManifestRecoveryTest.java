package org.schabi.newpipe.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.exoplayer.dash.DashManifestStaleException;

import org.junit.Test;

import java.io.IOException;

public class PlayerLiveManifestRecoveryTest {
    @Test
    public void refreshesStreamInfoForNestedStaleDashManifest() {
        final Throwable error = new IOException("source", new DashManifestStaleException());

        assertTrue(PlayerHttpErrorRecovery.isRecoverableMediaUrlFailure(error));
        assertFalse(PlayerHttpErrorRecovery.isRecoverableMediaUrlFailure(
                new IOException("unrelated source failure")));
    }

}
