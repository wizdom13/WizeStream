package org.schabi.newpipe.profiles

import androidx.annotation.DrawableRes
import org.schabi.newpipe.R

data class ProfileRecord(
    val id: String,
    val name: String,
    val description: String,
    val iconKey: String,
    val createdAt: Long
) {
    @get:DrawableRes
    val iconRes: Int
        get() = ProfileIcon.fromKey(iconKey).drawableRes
}

enum class ProfileIcon(
    val key: String,
    @DrawableRes val drawableRes: Int
) {
    PERSON("person", R.drawable.ic_person),
    WORK("work", R.drawable.ic_work),
    STUDY("study", R.drawable.ic_school),
    ENTERTAINMENT("entertainment", R.drawable.ic_movie),
    MUSIC("music", R.drawable.ic_music_note),
    FAVORITES("favorites", R.drawable.ic_favorite);

    companion object {
        @JvmStatic
        fun fromKey(key: String?): ProfileIcon {
            return entries.firstOrNull { it.key == key } ?: PERSON
        }
    }
}
