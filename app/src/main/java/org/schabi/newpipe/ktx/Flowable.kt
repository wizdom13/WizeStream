package org.schabi.newpipe.ktx

import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit

/**
 * Throttle a state stream without failing when the downstream temporarily stops requesting.
 *
 * RxJava's [Flowable.throttleLatest] signals a `MissingBackpressureException` when its timer fires
 * without outstanding demand. UI and notification state streams only need the newest value, so
 * perform the timed coalescing as an Observable and retain only the latest value for the Flowable
 * subscriber.
 */
fun <T : Any> Flowable<T>.throttleLatestDropOverflow(
    timeout: Long,
    unit: TimeUnit,
    scheduler: Scheduler = Schedulers.computation()
): Flowable<T> = toObservable()
    .throttleLatest(timeout, unit, scheduler)
    .toFlowable(BackpressureStrategy.LATEST)
