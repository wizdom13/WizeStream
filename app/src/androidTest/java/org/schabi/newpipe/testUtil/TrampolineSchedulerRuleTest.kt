package org.schabi.newpipe.testUtil

import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.io.InterruptedIOException
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

class TrampolineSchedulerRuleTest {
    @Test
    fun networkCancellationStillReachesTheExistingHandlerAfterTestCleanup() {
        val original = RxJavaPlugins.getErrorHandler()
        var received: Throwable? = null
        val handler = Consumer<Throwable> { received = it }
        try {
            RxJavaPlugins.setErrorHandler(handler)
            TrampolineSchedulerRule().apply(
                object : Statement() {
                    override fun evaluate() = Unit
                },
                Description.EMPTY
            ).evaluate()
            assertSame(handler, RxJavaPlugins.getErrorHandler())
            val cancellation = UndeliverableException(InterruptedIOException())
            RxJavaPlugins.onError(cancellation)
            assertSame(cancellation, received)
        } finally {
            RxJavaPlugins.setErrorHandler(original)
        }
    }

    @Test
    fun failedTestsAlsoRestoreTheExistingHandler() {
        val original = RxJavaPlugins.getErrorHandler()
        val handler = Consumer<Throwable> { _ -> Unit }
        val failure = AssertionError("fixture failure")
        try {
            RxJavaPlugins.setErrorHandler(handler)
            try {
                TrampolineSchedulerRule().apply(
                    object : Statement() {
                        override fun evaluate() {
                            throw failure
                        }
                    },
                    Description.EMPTY
                ).evaluate()
                fail("Expected the fixture failure")
            } catch (actual: AssertionError) {
                assertSame(failure, actual)
            }
            assertSame(handler, RxJavaPlugins.getErrorHandler())
        } finally {
            RxJavaPlugins.setErrorHandler(original)
        }
    }
}
