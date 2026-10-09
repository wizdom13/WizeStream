package org.schabi.newpipe.fragments.list.search

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.TestScheduler
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.FragmentSearchBinding
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.info_list.InfoListAdapter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SearchDeepCancellationTest {
    @Test
    fun backCancelsCollectionAndKeepsResultsAndContinuation() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.LightTheme)
        val binding = FragmentSearchBinding.inflate(LayoutInflater.from(context))
        val fragment = SearchFragment()
        val item = StreamInfoItem(0, "video", "Video", StreamType.VIDEO_STREAM)
        val adapter = mock(InfoListAdapter::class.java)
        `when`(adapter.itemsList).thenReturn(arrayListOf<InfoItem>(item))
        set(fragment, "searchBinding", binding)
        set(fragment, "itemsList", RecyclerView(context))
        set(fragment, "infoListAdapter", adapter)
        set(fragment, "nextPage", Page("next"))
        set(fragment, "deepSearchBudget", SearchPageBudget(25))
        set(fragment, "deepSearchRunning", true)
        set(fragment, "durationOrder", 1)
        val scheduler = TestScheduler()
        var requests = 0
        val request = Single.timer(2, TimeUnit.SECONDS, scheduler).subscribe { requests++ }
        set(fragment, "searchDisposable", request)
        val loading = get(fragment, "isLoading") as AtomicBoolean
        loading.set(true)
        @Suppress("UNCHECKED_CAST")
        val collected = get(fragment, "collectedSearchItems") as MutableList<InfoItem>
        collected.add(item)

        assertTrue(fragment.onBackPressed())

        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
        assertTrue(request.isDisposed)
        assertEquals(0, requests)
        assertFalse(loading.get())
        assertFalse(get(fragment, "deepSearchRunning") as Boolean)
        assertEquals(listOf(item), collected)
        assertEquals("next", (get(fragment, "nextPage") as Page).url)
        assertEquals(context.getString(R.string.search_fetch_more), binding.deepSearchButton.text.toString())
        assertTrue(binding.deepSearchButton.isEnabled)
    }

    private fun field(name: String): java.lang.reflect.Field {
        var type: Class<*>? = SearchFragment::class.java
        while (type != null) {
            try {
                return type.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        error("Missing field $name")
    }

    private fun set(target: Any, name: String, value: Any) = field(name).set(target, value)
    private fun get(target: Any, name: String): Any = requireNotNull(field(name).get(target))
}
