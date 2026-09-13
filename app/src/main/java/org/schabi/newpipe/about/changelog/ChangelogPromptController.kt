package org.schabi.newpipe.about.changelog

import android.content.Intent
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.util.Constants

class ChangelogPromptController(private val activity: FragmentActivity) : DefaultLifecycleObserver {
    private var load: Disposable? = null
    private var loaded = false
    private var pending: ChangelogNotice? = null

    init {
        activity.lifecycle.addObserver(this)
    }

    fun maybeShow() {
        val fragments = activity.supportFragmentManager
        if (activity.isFinishing || activity.isDestroyed || fragments.isStateSaved ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !activity.hasWindowFocus() ||
            fragments.findFragmentByTag(ChangelogDialogFragment.TAG) != null
        ) return
        // Leave external playback/search intents uninterrupted; show on the next normal app launch.
        if ((activity.intent.action != null && activity.intent.action != Intent.ACTION_MAIN) ||
            activity.intent.hasExtra(Constants.KEY_LINK_TYPE) || activity.intent.hasExtra(Constants.KEY_OPEN_SEARCH)
        ) return
        if (!loaded) {
            loaded = true
            val context = activity.applicationContext
            load = Maybe.fromCallable {
                val html = context.assets.open("changelog.html").bufferedReader().use { it.readText() }
                ChangelogCatalog.parse(html).notice(BuildConfig.VERSION_NAME, ChangelogPreferences(context).lastSeenCode)
            }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    pending = it
                    maybeShow()
                }, { Log.w(TAG, "Could not load bundled changelog", it) })
        } else {
            pending?.let { notice ->
                pending = null
                ChangelogDialogFragment.newInstance(notice).showNow(fragments, ChangelogDialogFragment.TAG)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        load?.dispose()
        pending = null
    }

    companion object {
        private const val TAG = "ChangelogPrompt"
    }
}
