package org.schabi.newpipe.testUtil

import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Always run on [Schedulers.trampoline].
 * This executes the task in the current thread in FIFO manner.
 * This ensures that tasks are run quickly inside the tests
 * and not scheduled away to another thread for later execution
 */
class TrampolineSchedulerRule : TestRule {

    private val scheduler = Schedulers.trampoline()

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val computationHandler = RxJavaPlugins.getComputationSchedulerHandler()
            val ioHandler = RxJavaPlugins.getIoSchedulerHandler()
            val newThreadHandler = RxJavaPlugins.getNewThreadSchedulerHandler()
            val singleHandler = RxJavaPlugins.getSingleSchedulerHandler()
            val mainHandler = RxAndroidPlugins.getInitMainThreadSchedulerHandler()
            try {
                RxJavaPlugins.setComputationSchedulerHandler { scheduler }
                RxJavaPlugins.setIoSchedulerHandler { scheduler }
                RxJavaPlugins.setNewThreadSchedulerHandler { scheduler }
                RxJavaPlugins.setSingleSchedulerHandler { scheduler }
                RxAndroidPlugins.setInitMainThreadSchedulerHandler { scheduler }

                base.evaluate()
            } finally {
                // Restore only the hooks this rule changes. A global reset removes the app's
                // error handler while asynchronous requests from UI tests are being cancelled.
                RxJavaPlugins.setComputationSchedulerHandler(computationHandler)
                RxJavaPlugins.setIoSchedulerHandler(ioHandler)
                RxJavaPlugins.setNewThreadSchedulerHandler(newThreadHandler)
                RxJavaPlugins.setSingleSchedulerHandler(singleHandler)
                RxAndroidPlugins.setInitMainThreadSchedulerHandler(mainHandler)
            }
        }
    }
}
