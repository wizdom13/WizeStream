package org.schabi.newpipe.player.ui

import android.content.Context
import android.view.Menu
import java.util.function.Consumer
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.settings.PreferredPlaybackLanguages
import org.schabi.newpipe.util.Localization

object PlaybackLanguageMenu {
    @JvmStatic
    fun audio(context: Context, menu: Menu, group: Int, streams: List<AudioStream>, showAll: Boolean, expand: Runnable) {
        var hidden = false
        streams.forEachIndexed { index, stream ->
            if (showAll || PreferredPlaybackLanguages.allows(context, stream.audioLocale)) {
                menu.add(group, index, Menu.NONE, Localization.audioTrackName(context, stream))
            } else {
                hidden = true
            }
        }
        if (hidden) addShowAll(menu, group, expand)
    }

    @JvmStatic
    fun captions(context: Context, menu: Menu, group: Int, languages: List<String?>, showAll: Boolean, select: Consumer<String?>, expand: Runnable) {
        menu.add(group, 0, Menu.NONE, R.string.caption_none).setOnMenuItemClickListener {
            select.accept(null)
            true
        }
        var hidden = false
        languages.forEachIndexed { index, language ->
            if (language == null) return@forEachIndexed
            if (showAll || PreferredPlaybackLanguages.allows(context, PlayerHelper.captionLanguageStemOf(language))) {
                menu.add(group, index + 1, Menu.NONE, language).setOnMenuItemClickListener {
                    select.accept(language)
                    true
                }
            } else {
                hidden = true
            }
        }
        if (hidden) addShowAll(menu, group, expand)
    }

    private fun addShowAll(menu: Menu, group: Int, expand: Runnable) {
        menu.add(group, -1, Menu.NONE, R.string.show_all_playback_languages).setOnMenuItemClickListener {
            expand.run()
            true
        }
    }
}
