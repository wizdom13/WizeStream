package us.shandian.giga.service

import org.junit.Test
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import us.shandian.giga.get.DownloadMission

class DownloadWindowPauseTest {
    @Test
    fun userCanUnqueueAWindowBlockedMission() {
        val manager = mock(DownloadManager::class.java, CALLS_REAL_METHODS)
        val mission = mock(DownloadMission::class.java)
        manager.pauseMission(mission)
        verify(mission).setEnqueued(false)
        verify(mission, never()).pause()
    }

    @Test
    fun explicitPauseClearsQueueIntentBeforeStoppingAnActiveTransfer() {
        val manager = mock(DownloadManager::class.java, CALLS_REAL_METHODS)
        val mission = mock(DownloadMission::class.java)
        mission.running = true
        manager.pauseMission(mission)
        val order = org.mockito.Mockito.inOrder(mission)
        order.verify(mission).setEnqueued(false)
        order.verify(mission).pause()
    }
}
