package org.schabi.newpipe.extractor.downloader;

import java.util.concurrent.TimeUnit;

import okhttp3.Call;

public class CancellableCall {
    private final Call call;
    private volatile boolean isFinished = false;

    public CancellableCall(Call call) {
        this.call = call;
    }

    public void cancel() {
        call.cancel();
    }

    public boolean isCancelled() {
        return call.isCanceled();
    }

    public boolean isFinished() {
        return isFinished;
    }

    public synchronized void setFinished() {
        isFinished = true;
        notifyAll();
    }

    /** Waits for response processing to finish without occupying a CPU core. */
    public synchronized boolean awaitFinished(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        final long budgetNanos = unit.toNanos(timeout);
        final long startNanos = System.nanoTime();
        long remainingNanos = budgetNanos;
        while (!isFinished && remainingNanos > 0) {
            TimeUnit.NANOSECONDS.timedWait(this, remainingNanos);
            remainingNanos = budgetNanos - (System.nanoTime() - startNanos);
        }
        return isFinished;
    }

    Call getCall() {
        return call;
    }
}
