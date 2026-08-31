package org.schabi.newpipe.settings.tabs;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.Proxy;

public class DrawerServiceSectionsPolicyTest {
    private static final String PREFERENCE_KEY = "show_service_sections";

    @Test
    public void missingPreferenceDefaultsToVisible() {
        assertTrue(DrawerServiceSectionsPolicy.shouldShow(preferencesReturning(null),
                PREFERENCE_KEY));
    }

    @Test
    public void disabledPreferenceHidesServiceSections() {
        assertFalse(DrawerServiceSectionsPolicy.shouldShow(preferencesReturning(false),
                PREFERENCE_KEY));
    }

    private SharedPreferences preferencesReturning(final Boolean value) {
        return (SharedPreferences) Proxy.newProxyInstance(
                DrawerServiceSectionsPolicyTest.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class},
                (proxy, method, arguments) -> {
                    if ("getBoolean".equals(method.getName())) {
                        return value == null ? arguments[1] : value;
                    }
                    throw new AssertionError("Unexpected SharedPreferences call: "
                            + method.getName());
                });
    }
}
