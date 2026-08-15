package org.schabi.newpipe.settings.tabs;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HomeDestinationResolver {
    private static final String TAG = "HomeDestinationResolver";

    private HomeDestinationResolver() { }

    public static Set<HomeDestinationKey> fromTabs(@NonNull final Context context,
                                                   @NonNull final List<Tab> tabs) {
        final Set<HomeDestinationKey> keys = new HashSet<>();
        for (final Tab tab : tabs) {
            final HomeDestinationKey key = fromTab(context, tab);
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }


    static Set<HomeDestinationKey> fromTabs(@NonNull final List<Tab> tabs,
                                            final int selectedServiceId,
                                            @NonNull final DefaultKioskResolver resolver) {
        final Set<HomeDestinationKey> keys = new HashSet<>();
        for (final Tab tab : tabs) {
            final HomeDestinationKey key = fromTab(tab, selectedServiceId, resolver);
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    public static HomeDestinationKey fromTab(@NonNull final Context context,
                                             @NonNull final Tab tab) {
        return fromTab(tab, ServiceHelper.getSelectedServiceId(context), serviceId -> {
            final StreamingService service = NewPipe.getService(serviceId);
            return service.getKioskList().getDefaultKioskId();
        });
    }

    static HomeDestinationKey fromTab(@NonNull final Tab tab,
                                      final int selectedServiceId,
                                      @NonNull final DefaultKioskResolver resolver) {
        if (tab instanceof Tab.SubscriptionsTab) {
            return HomeDestinationKey.SUBSCRIPTIONS;
        } else if (tab instanceof Tab.FeedTab) {
            return HomeDestinationKey.FEED;
        } else if (tab instanceof Tab.BookmarksTab) {
            return HomeDestinationKey.BOOKMARKS;
        } else if (tab instanceof Tab.DownloadsTab) {
            return HomeDestinationKey.DOWNLOADS;
        } else if (tab instanceof Tab.LocalMediaTab) {
            return HomeDestinationKey.LOCAL_MEDIA;
        } else if (tab instanceof Tab.HistoryTab) {
            return HomeDestinationKey.HISTORY;
        } else if (tab instanceof Tab.KioskTab) {
            final Tab.KioskTab kioskTab = (Tab.KioskTab) tab;
            return HomeDestinationKey.kiosk(kioskTab.getKioskServiceId(), kioskTab.getKioskId());
        } else if (tab instanceof Tab.DefaultKioskTab) {
            try {
                return HomeDestinationKey.kiosk(selectedServiceId,
                        resolver.getDefaultKioskId(selectedServiceId));
            } catch (final ExtractionException e) {
                Log.w(TAG, "Unable to resolve default kiosk home destination", e);
            }
        }
        return null;
    }

    interface DefaultKioskResolver {
        String getDefaultKioskId(int serviceId) throws ExtractionException;
    }
}
