package org.schabi.newpipe.settings.tabs;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class HomeDrawerPolicy {
    private HomeDrawerPolicy() { }

    public static boolean shouldShow(@NonNull final Set<HomeDestinationKey> configured,
                                     @NonNull final HomeDestinationKey candidate) {
        return !configured.contains(candidate);
    }

    public static Map<Integer, KioskTarget> assignKioskMenuIds(
            @NonNull final Set<HomeDestinationKey> configured,
            final int serviceId,
            @NonNull final Iterable<String> kioskIds,
            final int firstMenuId) {
        final Map<Integer, KioskTarget> result = new LinkedHashMap<>();
        int nextId = firstMenuId;
        for (final String kioskId : kioskIds) {
            final HomeDestinationKey key = HomeDestinationKey.kiosk(serviceId, kioskId);
            if (shouldShow(configured, key)) {
                result.put(nextId, new KioskTarget(serviceId, kioskId));
                nextId++;
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public static final class KioskTarget {
        private final int serviceId;
        private final String kioskId;

        public KioskTarget(final int serviceId, @NonNull final String kioskId) {
            this.serviceId = serviceId;
            this.kioskId = kioskId;
        }

        public int getServiceId() {
            return serviceId;
        }

        public String getKioskId() {
            return kioskId;
        }
    }
}
