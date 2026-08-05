/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.database.learning.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import org.schabi.newpipe.database.learning.model.LearningSessionEntity

@Dao
interface LearningSessionDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(session: LearningSessionEntity)
}
