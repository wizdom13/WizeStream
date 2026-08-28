package org.schabi.newpipe.extractor.settings;

import java.util.HashMap;
import java.util.Map;

public class ServiceSettings {

    private final Map<Integer, Boolean> settings = new HashMap<>();

    public void setting(final boolean enable, final int setting) {
        if (enable) {
            settings.put(setting, true);
        } else {
            settings.remove(setting);
        }
    }

    public boolean isSettingEnabled(final int setting) {
        return settings.get(setting) != null;
    }
}
