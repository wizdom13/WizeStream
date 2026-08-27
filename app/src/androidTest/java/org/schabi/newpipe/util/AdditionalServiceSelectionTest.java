/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;

@RunWith(AndroidJUnit4.class)
public class AdditionalServiceSelectionTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @After
    public void restoreDefaultService() {
        ServiceHelper.setSelectedServiceId(context, ServiceList.YouTube.getServiceId());
    }

    @Test
    public void bilibiliSelectionPersists() {
        assertSelectionPersists(ServiceList.BiliBili);
    }

    @Test
    public void niconicoSelectionPersists() {
        assertSelectionPersists(ServiceList.NicoNico);
    }

    private void assertSelectionPersists(final StreamingService service) {
        ServiceHelper.setSelectedServiceId(context, service.getServiceId());

        assertEquals(service.getServiceId(), ServiceHelper.getSelectedServiceId(context));
        assertSame(service, ServiceHelper.getSelectedService(context));
    }
}
