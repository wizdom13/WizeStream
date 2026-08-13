/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.equalizer;

import androidx.annotation.NonNull;

interface EqualizerEngine {
    void apply(@NonNull int[] canonicalGainSteps);

    void setEnabled(boolean enabled);

    void release();
}
