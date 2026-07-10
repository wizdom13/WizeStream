package org.schabi.newpipe.settings.tabs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

public final class HomeDestinationKey {
    public enum Type {
        SUBSCRIPTIONS,
        FEED,
        BOOKMARKS,
        DOWNLOADS,
        HISTORY,
        KIOSK
    }

    public static final HomeDestinationKey SUBSCRIPTIONS =
            new HomeDestinationKey(Type.SUBSCRIPTIONS, -1, null);
    public static final HomeDestinationKey FEED =
            new HomeDestinationKey(Type.FEED, -1, null);
    public static final HomeDestinationKey BOOKMARKS =
            new HomeDestinationKey(Type.BOOKMARKS, -1, null);
    public static final HomeDestinationKey DOWNLOADS =
            new HomeDestinationKey(Type.DOWNLOADS, -1, null);
    public static final HomeDestinationKey HISTORY =
            new HomeDestinationKey(Type.HISTORY, -1, null);

    private final Type type;
    private final int serviceId;
    @Nullable
    private final String kioskId;

    private HomeDestinationKey(@NonNull final Type type, final int serviceId,
                               @Nullable final String kioskId) {
        this.type = type;
        this.serviceId = serviceId;
        this.kioskId = kioskId;
    }

    public static HomeDestinationKey kiosk(final int serviceId, @NonNull final String kioskId) {
        return new HomeDestinationKey(Type.KIOSK, serviceId, kioskId);
    }

    public Type getType() {
        return type;
    }

    public int getServiceId() {
        return serviceId;
    }

    @Nullable
    public String getKioskId() {
        return kioskId;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof HomeDestinationKey)) {
            return false;
        }
        final HomeDestinationKey other = (HomeDestinationKey) obj;
        return serviceId == other.serviceId
                && type == other.type
                && Objects.equals(kioskId, other.kioskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, serviceId, kioskId);
    }
}
