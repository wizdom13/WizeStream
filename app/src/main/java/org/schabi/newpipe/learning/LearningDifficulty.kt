package org.schabi.newpipe.learning

import android.content.Context
import android.content.SharedPreferences
import android.widget.LinearLayout
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import java.security.MessageDigest
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.profiles.ProfileManager

object LearningDifficulty {
    @JvmStatic
    fun isEnabled(context: Context): Boolean = LearningMode.isEnabled(context) &&
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean("learning_difficulty_enabled", false)

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences("learning_difficulty", Context.MODE_PRIVATE)

    internal fun key(profile: String, service: Int, url: String): String = "$profile:$service:" +
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun rating(context: Context, profile: String, service: Int, url: String): Int = preferences(context).getInt(key(profile, service, url), 0).coerceIn(0, 3)

    fun setRating(context: Context, profile: String, service: Int, url: String, rating: Int) {
        require(rating in 0..3)
        preferences(context).edit().putInt(key(profile, service, url), rating).apply()
    }

    fun label(context: Context, rating: Int): String = context.resources.getStringArray(R.array.learning_difficulty_labels)[rating.coerceIn(0, 3)]

    fun choose(context: Context, profile: String, service: Int, url: String, changed: () -> Unit) {
        MaterialAlertDialogBuilder(context).setTitle(R.string.learning_difficulty_title)
            .setSingleChoiceItems(R.array.learning_difficulty_labels, rating(context, profile, service, url)) { dialog, which ->
                setRating(context, profile, service, url, which)
                changed()
                dialog.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    @JvmStatic
    fun addDescriptionButton(layout: LinearLayout, service: Int, url: String) {
        val context = layout.context
        if (!isEnabled(context) || !LearningContentManager.getInstance(context).isStreamLearning(service, url)) return
        val profile = ProfileManager.getActiveProfileId(context)
        val button = MaterialButton(context)
        fun update() {
            button.text = context.getString(R.string.learning_difficulty_format, label(context, rating(context, profile, service, url)))
        }
        update()
        button.setOnClickListener { choose(context, profile, service, url, ::update) }
        layout.addView(button, 0)
    }

    private fun changes(context: Context): Flowable<Int> = Flowable.create({ emitter ->
        val prefs = preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> emitter.onNext(0) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        emitter.setCancellable { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        emitter.onNext(0)
    }, BackpressureStrategy.LATEST)

    fun observe(context: Context, source: String?, profile: String): Flowable<List<StreamEntity>> = Flowable.combineLatest(
        NewPipeDatabase.getInstance(context).learningContentDAO().reviewStreams(source, profile),
        changes(context)
    ) { streams, _ -> streams.sortedWith(compareByDescending<StreamEntity> { rating(context, profile, it.serviceId, it.url) }.thenBy { it.title.lowercase() }) }
}
