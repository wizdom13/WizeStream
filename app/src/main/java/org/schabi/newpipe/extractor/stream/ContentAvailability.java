/*
 * SPDX-FileCopyrightText: 2025 FineFindus <FineFindus@proton.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.stream;

/** Describes whether a stream is publicly available or access-restricted. */
public enum ContentAvailability {
    UNKNOWN,
    AVAILABLE,
    MEMBERSHIP,
    PAID,
    UPCOMING
}
