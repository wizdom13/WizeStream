package org.schabi.newpipe

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.preference.PreferenceManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.jakewharton.processphoenix.ProcessPhoenix
import io.reactivex.rxjava3.exceptions.CompositeException
import io.reactivex.rxjava3.exceptions.MissingBackpressureException
import io.reactivex.rxjava3.exceptions.OnErrorNotImplementedException
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import org.acra.ACRA.init
import org.acra.ACRA.isACRASenderServiceProcess
import org.acra.config.CoreConfigurationBuilder
import org.schabi.newpipe.error.ReCaptchaActivity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.services.youtube.YoutubeApiDecoder
import org.schabi.newpipe.ktx.hasAssignableCause
import org.schabi.newpipe.player.datasource.LocalDomPoTokenProvider
import org.schabi.newpipe.player.datasource.SabrPolicyRuntime
import org.schabi.newpipe.player.datasource.SabrPolicyUpdateWorker
import org.schabi.newpipe.settings.NewPipeSettings
import org.schabi.newpipe.sync.DeviceSyncBackgroundScheduler
import org.schabi.newpipe.sync.DeviceSyncManager
import org.schabi.newpipe.util.BridgeStateSaverInitializer
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ServiceHelper
import org.schabi.newpipe.util.StateSaver
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.image.ImageStrategy
import org.schabi.newpipe.util.image.PreferredImageQuality

/*
 * Copyright (C) Hans-Christoph Steiner 2016 <hans@eds.org>
 * App.kt is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */
open class App :
    Application(),
    SingletonImageLoader.Factory {
    var isFirstRun = false
        private set
    var notificationsRequested = false
        private set

    protected var isFullAppInitializationEnabled = false
        private set

    fun setNotificationsRequested() {
        notificationsRequested = true
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        initACRA()
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        val isAcraProcess = isACRASenderServiceProcess()
        val isPhoenixProcess = ProcessPhoenix.isPhoenixProcess(this)
        isFullAppInitializationEnabled =
            shouldInitializeFullApp(isAcraProcess, isPhoenixProcess)
        if (!isFullAppInitializationEnabled) {
            val skippedProcess =
                if (isAcraProcess) "the ACRA sender process" else "a phoenix process"
            Log.i(
                TAG,
                "Skipping full application initialization in $skippedProcess"
            )
            return
        }

        // check if the last used preference version is set
        // to determine whether this is the first app run
        val lastUsedPrefVersion =
            PreferenceManager
                .getDefaultSharedPreferences(this)
                .getInt(getString(R.string.last_used_preferences_version), -1)
        isFirstRun = lastUsedPrefVersion == -1

        // Initialize settings first because other initializations can use its values
        NewPipeSettings.initSettings(this)
        applyDynamicColorsIfAvailable()

        NewPipe.init(
            getDownloader(),
            Localization.getPreferredLocalization(this),
            Localization.getPreferredContentCountry(this)
        )
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val sessionPoTokenProvider = LocalDomPoTokenProvider.shared(this)
        NewPipe.setYoutubeSessionPoTokenProvider { clientName, localization, contentCountry, loggedIn ->
            val visitorDataEnabled = prefs.getBoolean(
                getString(R.string.youtube_session_visitor_data_key),
                false
            )
            if (visitorDataEnabled || clientName == "ANDROID_VR") {
                sessionPoTokenProvider.getSessionPoToken(
                    clientName,
                    localization,
                    contentCountry,
                    loggedIn
                )
            } else {
                null
            }
        }
        runCatching {
            SabrPolicyRuntime.initialize(this, BuildConfig.SABR_POLICY_PUBLIC_KEY_BASE64, 0)
            SabrPolicyUpdateWorker.initialize(this)
        }.onFailure { error ->
            Log.e(TAG, "Could not initialize SABR cloud policy; using builtin", error)
        }
        val webViewAvailabilityChecker = AndroidWebViewAvailabilityChecker(this)
        NewPipe.setWebViewAvailabilityChecker(webViewAvailabilityChecker)
        webViewAvailabilityChecker.warmUp()
        YoutubeApiDecoder.setLocalDecoder(WebViewJavaScriptDecoder(this))
        Localization.initPrettyTime(Localization.resolvePrettyTime())

        BridgeStateSaverInitializer.init(this)
        StateSaver.init(this)
        initNotificationChannels()

        ServiceHelper.initServices(this)
        sessionPoTokenProvider.prewarmSessionPoToken(
            Localization.getPreferredLocalization(this),
            Localization.getPreferredContentCountry(this),
            org.schabi.newpipe.extractor.ServiceList.YouTube.hasTokens()
        )

        // Initialize image loader
        NewPipe.setYoutubePlayerClient(
            prefs.getString(getString(R.string.youtube_player_client_key), "mweb") ?: "mweb"
        )
        ImageStrategy.setPreferredImageQuality(
            PreferredImageQuality.fromPreferenceKey(
                this,
                prefs.getString(
                    getString(R.string.image_quality_key),
                    getString(R.string.image_quality_default)
                )
            )
        )

        configureRxJavaErrorHandler()
        initializeDeviceSyncScheduling()
    }

    private fun initializeDeviceSyncScheduling() {
        runCatching {
            DeviceSyncBackgroundScheduler.initialize(
                this,
                hasTrustedPeers = DeviceSyncManager.hasTrustedPeers(this)
            )
        }.onFailure { error ->
            Log.e(TAG, "Could not initialize background device synchronization", error)
        }
    }

    private fun applyDynamicColorsIfAvailable() {
        val dynamicColorsOptions = DynamicColorsOptions.Builder()
            .setPrecondition { activity, _ -> ThemeHelper.shouldApplyDynamicColors(activity) }
            .build()
        DynamicColors.applyToActivitiesIfAvailable(this, dynamicColorsOptions)
    }

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader
        .Builder(this)
        .logger(if (BuildConfig.DEBUG) DebugLogger() else null)
        .allowRgb565(getSystemService<ActivityManager>()!!.isLowRamDevice)
        .crossfade(true)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = DownloaderImpl.getInstance().client))
        }.build()

    protected open fun getDownloader(): Downloader {
        val downloader = DownloaderImpl.init(null)
        setCookiesToDownloader(downloader)
        return downloader
    }

    protected fun setCookiesToDownloader(downloader: DownloaderImpl) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val key = getString(R.string.recaptcha_cookies_key)
        downloader.setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, prefs.getString(key, null))
        downloader.updateYoutubeRestrictedModeCookies(this)
    }

    private fun configureRxJavaErrorHandler() {
        // https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
        RxJavaPlugins.setErrorHandler(
            object : Consumer<Throwable> {
                override fun accept(throwable: Throwable) {
                    Log.e(TAG, "RxJavaPlugins.ErrorHandler called with -> : throwable = [${throwable.javaClass.getName()}]")

                    // As UndeliverableException is a wrapper,
                    // get the cause of it to get the "real" exception
                    val actualThrowable = (throwable as? UndeliverableException)?.cause ?: throwable

                    val errors = (actualThrowable as? CompositeException)?.exceptions ?: listOf(actualThrowable)

                    for (error in errors) {
                        if (isThrowableIgnored(error)) {
                            return
                        }
                        if (isThrowableCritical(error)) {
                            reportException(error)
                            return
                        }
                    }

                    // Out-of-lifecycle exceptions should only be reported if a debug user wishes so,
                    // When exception is not reported, log it
                    if (isDisposedRxExceptionsReported()) {
                        reportException(actualThrowable)
                    } else {
                        Log.e(TAG, "RxJavaPlugin: Undeliverable Exception received: ", actualThrowable)
                    }
                }

                fun isThrowableIgnored(throwable: Throwable): Boolean {
                    // Don't crash the application over a simple network problem
                    return throwable // network api cancellation
                        .hasAssignableCause(
                            IOException::class.java,
                            SocketException::class.java, // blocking code disposed
                            InterruptedException::class.java,
                            InterruptedIOException::class.java
                        )
                }

                fun isThrowableCritical(throwable: Throwable): Boolean {
                    // Though these exceptions cannot be ignored
                    return throwable
                        .hasAssignableCause(
                            // bug in app
                            NullPointerException::class.java,
                            IllegalArgumentException::class.java,
                            OnErrorNotImplementedException::class.java,
                            MissingBackpressureException::class.java,
                            // bug in operator
                            IllegalStateException::class.java
                        )
                }

                fun reportException(throwable: Throwable) {
                    // Throw uncaught exception that will trigger the report system
                    Thread
                        .currentThread()
                        .uncaughtExceptionHandler
                        .uncaughtException(Thread.currentThread(), throwable)
                }
            }
        )
    }

    /**
     * Called in [.attachBaseContext] after calling the `super` method.
     * Should be overridden if MultiDex is enabled, since it has to be initialized before ACRA.
     */
    protected fun initACRA() {
        if (isACRASenderServiceProcess()) {
            return
        }

        val acraConfig =
            CoreConfigurationBuilder()
                .withBuildConfigClass(BuildConfig::class.java)
        init(this, acraConfig)
    }

    private fun initNotificationChannels() {
        // Keep the importance below DEFAULT to avoid making noise on every notification update for
        // the main and update channels
        val mainChannel =
            NotificationChannelCompat
                .Builder(
                    getString(R.string.notification_channel_id),
                    NotificationManagerCompat.IMPORTANCE_LOW
                ).setName(getString(R.string.notification_channel_name))
                .setDescription(getString(R.string.notification_channel_description))
                .build()
        val appUpdateChannel =
            NotificationChannelCompat
                .Builder(
                    getString(R.string.app_update_notification_channel_id),
                    NotificationManagerCompat.IMPORTANCE_LOW
                ).setName(getString(R.string.app_update_notification_channel_name))
                .setDescription(getString(R.string.app_update_notification_channel_description))
                .build()
        val hashChannel =
            NotificationChannelCompat
                .Builder(
                    getString(R.string.hash_channel_id),
                    NotificationManagerCompat.IMPORTANCE_HIGH
                ).setName(getString(R.string.hash_channel_name))
                .setDescription(getString(R.string.hash_channel_description))
                .build()
        val errorReportChannel =
            NotificationChannelCompat
                .Builder(
                    getString(R.string.error_report_channel_id),
                    NotificationManagerCompat.IMPORTANCE_LOW
                ).setName(getString(R.string.error_report_channel_name))
                .setDescription(getString(R.string.error_report_channel_description))
                .build()
        val newStreamChannel =
            NotificationChannelCompat
                .Builder(
                    getString(R.string.streams_notification_channel_id),
                    NotificationManagerCompat.IMPORTANCE_DEFAULT
                ).setName(getString(R.string.streams_notification_channel_name))
                .setDescription(getString(R.string.streams_notification_channel_description))
                .build()
        val sabrBackoffChannel =
            NotificationChannelCompat
                .Builder(
                    getString(R.string.sabr_backoff_notification_channel_id),
                    NotificationManagerCompat.IMPORTANCE_DEFAULT
                ).setName(getString(R.string.sabr_backoff_notification_channel_name))
                .setDescription(
                    getString(R.string.sabr_backoff_notification_channel_description)
                ).setSound(null, null)
                .setVibrationEnabled(false)
                .setShowBadge(false)
                .build()

        val channels =
            listOf(
                mainChannel,
                appUpdateChannel,
                hashChannel,
                errorReportChannel,
                newStreamChannel,
                sabrBackoffChannel
            )

        NotificationManagerCompat.from(this).createNotificationChannelsCompat(channels)
    }

    protected open fun isDisposedRxExceptionsReported(): Boolean = false

    companion object {
        const val PACKAGE_NAME: String = BuildConfig.APPLICATION_ID
        private val TAG = App::class.java.toString()

        @JvmStatic
        internal fun shouldInitializeFullApp(
            isAcraSenderServiceProcess: Boolean,
            isPhoenixProcess: Boolean
        ): Boolean = !isAcraSenderServiceProcess && !isPhoenixProcess

        @JvmStatic
        lateinit var instance: App
            private set
    }
}
