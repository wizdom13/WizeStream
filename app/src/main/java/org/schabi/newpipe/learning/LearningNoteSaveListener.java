/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.learning;

@FunctionalInterface
public interface LearningNoteSaveListener {
    void onSave(long timestampMillis, String noteText);
}
