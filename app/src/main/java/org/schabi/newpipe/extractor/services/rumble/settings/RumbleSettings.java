package org.schabi.newpipe.extractor.services.rumble.settings;

import org.schabi.newpipe.extractor.settings.ServiceSettings;

public final class RumbleSettings extends ServiceSettings {
    public static final int HIDE_PREMIUM_STREAMS = 1;

    private static final RumbleSettings INSTANCE =
            new RumbleSettings();

    private RumbleSettings() {
    }

    public static RumbleSettings getInstance() {
        return INSTANCE;
    }
}
