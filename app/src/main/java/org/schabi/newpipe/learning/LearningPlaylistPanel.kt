/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import androidx.core.view.isVisible
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.LearningPlaylistPanelBinding
import org.schabi.newpipe.fragments.detail.VideoDetailFragment
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.profiles.ProfileManager
import org.schabi.newpipe.util.image.CoilHelper

object LearningPlaylistPanel {
    @JvmStatic
    fun observe(parent: VideoDetailFragment, binding: LearningPlaylistPanelBinding, serviceId: Int, url: String?, showNext: Boolean): Disposable = parent.learningQueueUpdates()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe({ optional ->
            render(binding, optional.orElse(null), serviceId, url, showNext) { parent.openNextLearningLesson(it) }
        }, { binding.root.isVisible = false })

    @JvmStatic
    fun render(binding: LearningPlaylistPanelBinding, queue: PlayQueue?, serviceId: Int, url: String?, showNext: Boolean, openNext: (PlayQueue) -> Unit) {
        val context = binding.root.context
        val navigation = LearningPlaylistNavigation.from(queue, ProfileManager.getActiveProfileId(context), serviceId, url)
        val visible = navigation != null && LearningMode.isPlaylistNavigationEnabled(context) &&
            LearningContentManager.getInstance(context).isSourceMarked(navigation.course.sourceId)
        binding.root.isVisible = visible
        if (!visible || navigation == null || queue == null) return
        binding.learningCourseTitle.text = context.getString(R.string.learning_course_format, navigation.course.title)
        binding.learningCoursePosition.text = if (navigation.total != null) {
            context.getString(R.string.learning_lesson_position, navigation.position, navigation.total)
        } else {
            context.getString(R.string.learning_lesson_position_partial, navigation.position)
        }
        val next = navigation.next
        binding.learningNextHeading.isVisible = showNext && next != null
        binding.learningNextVideo.isVisible = showNext && next != null
        binding.learningCourseStatus.isVisible = showNext && next == null && (navigation.atEnd || navigation.loadError)
        binding.learningCourseStatus.setText(if (navigation.atEnd) R.string.learning_playlist_end else R.string.learning_next_lesson_error)
        binding.learningLoadNext.isVisible = showNext && next == null && !navigation.atEnd && !navigation.loadError
        binding.learningLoadNext.isEnabled = true
        binding.learningLoadNext.setOnClickListener {
            binding.learningLoadNext.isEnabled = false
            queue.fetch()
        }
        if (showNext && next != null) {
            binding.learningNextTitle.text = next.title
            if (next.isLocalMedia) {
                CoilHelper.loadThumbnail(binding.learningNextThumbnail, next.localThumbnailUrl)
            } else {
                CoilHelper.loadThumbnail(binding.learningNextThumbnail, next.thumbnails)
            }
            binding.learningNextVideo.setOnClickListener { openNext(queue) }
        }
    }
}
