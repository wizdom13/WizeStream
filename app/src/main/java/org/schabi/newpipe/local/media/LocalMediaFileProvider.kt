/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.local.media

import androidx.core.content.FileProvider

/** Shares explicitly selected external-storage media using an exact, temporary read grant. */
class LocalMediaFileProvider : FileProvider()
