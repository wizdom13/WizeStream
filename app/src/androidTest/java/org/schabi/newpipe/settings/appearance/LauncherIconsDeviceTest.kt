package org.schabi.newpipe.settings.appearance

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.MainActivity

class LauncherIconsDeviceTest {
    @Test fun eachChoiceHasOnePhoneAndTvEntryAndLeavesMainActivityAvailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        val read = { key: String -> pm.getComponentEnabledSetting(LauncherIcons.component(context, key)) }
        val write = { key: String, state: Int -> pm.setComponentEnabledSetting(LauncherIcons.component(context, key), state, PackageManager.DONT_KILL_APP) }
        val original = LauncherIcons.variants.associateWith(read)
        try {
            for (variant in LauncherIcons.variants) {
                LauncherIcons.apply(variant, read, write)
                assertEquals(variant, LauncherIcons.selected(context))
                for (category in listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)) {
                    val entries = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(context.packageName), 0)
                    assertEquals(1, entries.size)
                    val activity = entries.single().activityInfo
                    assertEquals(LauncherIcons.component(context, variant).className, activity.name)
                    assertNotNull(activity.loadIcon(pm))
                    assertNotNull(activity.loadBanner(pm))
                }
                assertTrue(pm.getActivityInfo(ComponentName(context, MainActivity::class.java), 0).enabled)
                assertNotNull(pm.getLaunchIntentForPackage(context.packageName))
            }
        } finally {
            LauncherIcons.apply("default", read, write)
            original.filterValues { it != PackageManager.COMPONENT_ENABLED_STATE_DISABLED }.forEach { (key, state) -> write(key, state) }
            original.filterValues { it == PackageManager.COMPONENT_ENABLED_STATE_DISABLED }.forEach { (key, state) -> write(key, state) }
        }
    }
}
