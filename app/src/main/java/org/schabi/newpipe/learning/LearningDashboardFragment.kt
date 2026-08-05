/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentLearningDashboardBinding
import org.schabi.newpipe.databinding.ItemLearningDashboardBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.image.CoilHelper

class LearningDashboardFragment : BaseStateFragment<LearningDashboardSnapshot>() {
    private var _binding: FragmentLearningDashboardBinding? = null
    private val binding get() = _binding!!
    private val disposables = CompositeDisposable()
    private lateinit var repository: LearningDashboardRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = LearningDashboardRepository(
            NewPipeDatabase.getInstance(requireContext()).learningDashboardDAO()
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentLearningDashboardBinding.inflate(inflater, container, false).also {
        _binding = it
    }.root

    override fun onResume() {
        super.onResume()
        setTitle(getString(R.string.learning_dashboard_title))
        if (!LearningMode.isEnabled(requireContext())) {
            parentFragmentManager.popBackStack()
        }
    }

    override fun startLoading(forceLoad: Boolean) {
        super.startLoading(forceLoad)
        disposables.clear()
        disposables.add(
            repository.observe()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(::handleResult) { error ->
                    showError(
                        ErrorInfo(
                            error,
                            UserAction.SOMETHING_ELSE,
                            "Loading learning dashboard"
                        )
                    )
                }
        )
    }

    override fun handleResult(result: LearningDashboardSnapshot) {
        val visibleResult = result.copy(
            playlists = if (LearningMode.isPlaylistProgressEnabled(requireContext())) {
                result.playlists
            } else {
                emptyList()
            },
            continueLearning = if (LearningMode.isPlaylistProgressEnabled(requireContext())) {
                result.continueLearning
            } else {
                emptyList()
            },
            recentlyAnnotated = if (LearningMode.areNotesEnabled(requireContext())) {
                result.recentlyAnnotated
            } else {
                emptyList()
            }
        )
        super.handleResult(visibleResult)
        binding.learningDashboardContent.isVisible = true
        renderStudyStatistics(visibleResult.studyStatistics)
        binding.learningDashboardSummaryCard.isVisible =
            LearningMode.isPlaylistProgressEnabled(requireContext())
        binding.learningDashboardSummary.text = getString(
            R.string.learning_dashboard_summary_format,
            visibleResult.completedStreams,
            visibleResult.eligibleStreams,
            visibleResult.overallPercentage
        )
        binding.learningDashboardOverallProgress.setProgressCompat(
            visibleResult.overallPercentage,
            true
        )
        binding.learningDashboardPlaylistSummary.text = getString(
            R.string.learning_dashboard_playlists_format,
            visibleResult.activePlaylists.size,
            visibleResult.completedPlaylists.size
        )

        renderStreamSection(
            binding.learningDashboardContinueTitle,
            binding.learningDashboardContinueList,
            visibleResult.continueLearning,
            false
        )
        renderPlaylistSection(
            binding.learningDashboardActiveTitle,
            binding.learningDashboardActiveList,
            visibleResult.activePlaylists.take(LearningDashboardRepository.DEFAULT_SECTION_LIMIT)
        )
        renderPlaylistSection(
            binding.learningDashboardCompletedTitle,
            binding.learningDashboardCompletedList,
            visibleResult.completedPlaylists.take(
                LearningDashboardRepository.DEFAULT_SECTION_LIMIT
            )
        )
        renderStreamSection(
            binding.learningDashboardNotesTitle,
            binding.learningDashboardNotesList,
            visibleResult.recentlyAnnotated,
            true
        )
    }

    private fun renderStudyStatistics(statistics: LearningStudyStatistics) {
        binding.learningStatisticsToday.text = getString(
            R.string.learning_statistics_today,
            studyDuration(statistics.todayMillis)
        )
        binding.learningStatisticsWeek.text = getString(
            R.string.learning_statistics_week,
            studyDuration(statistics.weekMillis)
        )
        binding.learningStatisticsAllTime.text = getString(
            R.string.learning_statistics_all_time,
            studyDuration(statistics.allTimeMillis)
        )
        binding.learningStatisticsStreak.text = getString(
            R.string.learning_statistics_streak,
            statistics.currentStreak,
            statistics.longestStreak
        )

        val calendar = binding.learningActivityCalendar
        calendar.removeAllViews()
        val maximum = statistics.calendar.maxOfOrNull(LearningCalendarDay::watchedDurationMillis)
            ?.coerceAtLeast(1) ?: 1
        val activeColor = MaterialColors.getColor(calendar, com.google.android.material.R.attr.colorPrimary)
        val emptyColor = MaterialColors.getColor(calendar, com.google.android.material.R.attr.colorSurface)
        statistics.calendar.forEach { day ->
            val intensity = day.watchedDurationMillis.toFloat() / maximum
            val background = ColorUtils.blendARGB(emptyColor, activeColor, 0.15f + intensity * 0.85f)
            calendar.addView(
                TextView(requireContext()).apply {
                    text = day.date.dayOfMonth.toString()
                    gravity = Gravity.CENTER
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
                    textSize = 11f
                    contentDescription = getString(
                        R.string.learning_activity_day_description,
                        day.date.toString(),
                        studyDuration(day.watchedDurationMillis)
                    )
                    this.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = resources.displayMetrics.density * 6
                        setColor(background)
                    }
                    layoutParams = android.widget.GridLayout.LayoutParams().apply {
                        width = 0
                        height = (resources.displayMetrics.density * 36).toInt()
                        columnSpec = android.widget.GridLayout.spec(
                            android.widget.GridLayout.UNDEFINED,
                            1f
                        )
                    }
                }
            )
        }
    }

    private fun studyDuration(durationMillis: Long): String = Localization.getDurationString(durationMillis / 1_000)

    private fun renderPlaylistSection(
        title: View,
        container: LinearLayout,
        playlists: List<LearningPlaylistSummary>
    ) {
        title.isVisible = playlists.isNotEmpty()
        container.isVisible = playlists.isNotEmpty()
        container.removeAllViews()
        playlists.forEach { playlist ->
            val item = ItemLearningDashboardBinding.inflate(layoutInflater, container, false)
            item.learningDashboardItemTitle.text = playlist.playlistName
                ?: getString(R.string.learning_dashboard_untitled_playlist)
            item.learningDashboardItemSubtitle.text = if (playlist.isCompleted) {
                getString(R.string.learning_progress_completed)
            } else {
                getString(
                    R.string.learning_progress_format,
                    playlist.completedCount,
                    playlist.eligibleCount,
                    playlist.percentage
                )
            }
            item.learningDashboardItemProgress.setProgressCompat(playlist.percentage, false)
            CoilHelper.loadPlaylistThumbnail(item.learningDashboardThumbnail, playlist.thumbnailUrl)
            item.root.setOnClickListener {
                NavigationHelper.openLocalPlaylistFragment(
                    fm,
                    playlist.playlistId,
                    playlist.playlistName.orEmpty()
                )
            }
            container.addView(item.root)
        }
    }

    private fun renderStreamSection(
        title: View,
        container: LinearLayout,
        streams: List<LearningDashboardStream>,
        showNoteCount: Boolean
    ) {
        title.isVisible = streams.isNotEmpty()
        container.isVisible = streams.isNotEmpty()
        container.removeAllViews()
        streams.forEach { dashboardStream ->
            val stream = dashboardStream.stream
            val item = ItemLearningDashboardBinding.inflate(layoutInflater, container, false)
            item.learningDashboardItemTitle.text = stream.title
            item.learningDashboardItemSubtitle.text = if (showNoteCount) {
                resources.getQuantityString(
                    R.plurals.learning_dashboard_note_count,
                    dashboardStream.noteCount,
                    dashboardStream.noteCount
                )
            } else {
                getString(
                    R.string.learning_dashboard_video_progress,
                    Localization.getDurationString(dashboardStream.progressMillis / 1_000),
                    Localization.getDurationString(stream.duration),
                    dashboardStream.progressPercentage
                )
            }
            item.learningDashboardItemProgress.isVisible = !showNoteCount
            item.learningDashboardItemProgress.setProgressCompat(
                dashboardStream.progressPercentage,
                false
            )
            CoilHelper.loadThumbnail(item.learningDashboardThumbnail, stream.thumbnailUrl)
            item.root.setOnClickListener {
                NavigationHelper.openVideoDetailFragment(
                    requireContext(),
                    fm,
                    stream.serviceId,
                    stream.url,
                    stream.title,
                    null,
                    false
                )
            }
            container.addView(item.root)
        }
    }

    override fun onDestroyView() {
        disposables.clear()
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        disposables.dispose()
        super.onDestroy()
    }
}
