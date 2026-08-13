/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player.equalizer;

import androidx.annotation.NonNull;

interface EqualizerStateStore {
    @NonNull
    EqualizerState load();

    void save(@NonNull EqualizerState state);
}
