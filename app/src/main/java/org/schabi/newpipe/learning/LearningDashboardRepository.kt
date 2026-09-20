/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.learning.dao.LearningDashboardDAO

class LearningDashboardRepository(
    private val dao: LearningDashboardDAO,
    private val profileId: String
) {
    fun observe(
        limit: Int = DEFAULT_SECTION_LIMIT
    ): Flowable<LearningDashboardSnapshot> {
        return Flowable.combineLatest(
            dao.observePlaylistSummaries(profileId),
            dao.observeLearningContent(profileId, limit),
            dao.observeContinueLearning(profileId, limit),
            dao.observeRecentlyAnnotated(profileId, limit),
            dao.observeDailyStudyActivity(profileId)
        ) { playlists, learningContent, continueLearning, recentlyAnnotated, dailyActivity ->
            LearningDashboardSnapshot(
                playlists,
                learningContent,
                continueLearning,
                recentlyAnnotated,
                LearningStudyStatistics.from(dailyActivity)
            )
        }
    }

    companion object {
        const val DEFAULT_SECTION_LIMIT = 5
    }
}
