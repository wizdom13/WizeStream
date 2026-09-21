/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AiSListContentHelperTest {
    @Test
    void warnBehaviorParsesEverySupportedPreferenceValue() {
        assertEquals(
                AiSListContentHelper.WarnBehavior.IGNORE,
                AiSListContentHelper.WarnBehavior.fromPreferenceValue("ignore"));
        assertEquals(
                AiSListContentHelper.WarnBehavior.LABEL,
                AiSListContentHelper.WarnBehavior.fromPreferenceValue("label"));
        assertEquals(
                AiSListContentHelper.WarnBehavior.WARN,
                AiSListContentHelper.WarnBehavior.fromPreferenceValue("warn"));
        assertEquals(
                AiSListContentHelper.WarnBehavior.HIDE,
                AiSListContentHelper.WarnBehavior.fromPreferenceValue("hide"));
    }

    @Test
    void unknownWarnBehaviorFallsBackToLabel() {
        assertEquals(
                AiSListContentHelper.WarnBehavior.LABEL,
                AiSListContentHelper.WarnBehavior.fromPreferenceValue("future-value"));
        assertEquals(
                AiSListContentHelper.WarnBehavior.LABEL,
                AiSListContentHelper.WarnBehavior.fromPreferenceValue(null));
    }
}
