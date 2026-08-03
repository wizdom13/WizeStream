package org.schabi.newpipe.extractor.downloader;

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

    public void setFinished() {
        isFinished = true;
    }

    Call getCall() {
        return call;
    }
}
