/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.local.feed

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.util.ServiceHelper

/**
 * Identifies the subscription/feed namespace selected in the service drawer.
 *
 * YouTube and YouTube Music share an extractor service id, so [youtubeModeMask] keeps their
 * subscriptions and feed updates independent.
 */
data class FeedScope(
    val serviceId: Int,
    val youtubeModeMask: Int
) {
    fun includes(subscription: SubscriptionEntity): Boolean {
        if (subscription.serviceId != serviceId) {
            return false
        }
        return serviceId != SubscriptionEntity.YOUTUBE_SERVICE_ID ||
            subscription.youtubeModeMask and youtubeModeMask != 0
    }

    companion object {
        @JvmStatic
        fun from(context: Context): FeedScope {
            return FeedScope(
                ServiceHelper.getSelectedServiceId(context),
                if (ServiceHelper.isYoutubeMusicMode(context)) {
                    SubscriptionEntity.YOUTUBE_MODE_MUSIC
                } else {
                    SubscriptionEntity.YOUTUBE_MODE_REGULAR
                }
            )
        }

        /**
         * Emits the current scope and every subsequent service or YouTube mode selection.
         *
         * The stream owns its preference listener, so disposing the subscription also unregisters
         * the listener. Distinct scope values prevent an unchanged preference value from
         * rebuilding service-scoped database subscriptions.
         */
        @JvmStatic
        fun changes(context: Context): Flowable<FeedScope> {
            val applicationContext = context.applicationContext
            val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val servicePreferenceKey = applicationContext.getString(R.string.current_service_key)

            return Flowable
                .create(
                    { emitter ->
                        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                            if (key == servicePreferenceKey) {
                                emitter.onNext(from(applicationContext))
                            }
                        }

                        preferences.registerOnSharedPreferenceChangeListener(listener)
                        emitter.setCancellable {
                            preferences.unregisterOnSharedPreferenceChangeListener(listener)
                        }
                        emitter.onNext(from(applicationContext))
                    },
                    BackpressureStrategy.LATEST
                )
                .distinctUntilChanged()
        }
    }
}
