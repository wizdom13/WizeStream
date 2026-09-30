/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning

import java.io.Serializable

/** Explicit playlist origin, retained with the queue across player changes and restoration. */
data class LearningPlaylistContext(
    val sourceId: String,
    val title: String,
    val profileId: String
) : Serializable
