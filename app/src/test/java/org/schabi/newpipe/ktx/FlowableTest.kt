package org.schabi.newpipe.ktx

import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.TestScheduler
import java.util.concurrent.TimeUnit
import org.junit.Test

class FlowableTest {
    @Test
    fun `throttling keeps the latest value while downstream demand is paused`() {
        val scheduler = TestScheduler()
        val source = PublishProcessor.create<Int>()
        val subscriber = source
            .throttleLatestDropOverflow(1, TimeUnit.SECONDS, scheduler)
            .test(0)

        source.onNext(1)
        source.onNext(2)
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS)
        source.onNext(3)
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS)

        subscriber.request(1)

        subscriber.assertValue(3)
        subscriber.assertNoErrors()
    }
}
