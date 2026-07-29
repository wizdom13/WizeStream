package org.schabi.newpipe.local.feed.service

import androidx.annotation.StringRes
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.processors.BehaviorProcessor
import java.util.concurrent.ConcurrentHashMap
import org.schabi.newpipe.local.feed.FeedScope
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.IdleEvent

object FeedEventManager {
    private val processors = ConcurrentHashMap<FeedScope, BehaviorProcessor<Event>>()

    fun postEvent(scope: FeedScope, event: Event) {
        processor(scope).onNext(event)
    }

    fun events(scope: FeedScope): Flowable<Event> {
        return processor(scope).hide()
    }

    fun reset(scope: FeedScope) {
        postEvent(scope, IdleEvent)
    }

    private fun processor(scope: FeedScope): BehaviorProcessor<Event> {
        return synchronized(processors) {
            processors.getOrPut(scope) { BehaviorProcessor.createDefault(IdleEvent) }
        }
    }

    sealed class Event {
        data object IdleEvent : Event()
        data class ProgressEvent(val currentProgress: Int = -1, val maxProgress: Int = -1, @StringRes val progressMessage: Int = 0) : Event() {
            constructor(@StringRes progressMessage: Int) : this(-1, -1, progressMessage)
        }

        data class SuccessResultEvent(val itemsErrors: List<Throwable> = emptyList()) : Event()
        data class ErrorResultEvent(val error: Throwable) : Event()
    }
}
