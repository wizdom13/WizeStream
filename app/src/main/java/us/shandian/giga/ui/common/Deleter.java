package us.shandian.giga.ui.common;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.view.View;

import androidx.core.os.HandlerCompat;

import com.google.android.material.snackbar.Snackbar;

import org.schabi.newpipe.R;

import java.util.ArrayList;
import java.util.Optional;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.FinishedMission;
import us.shandian.giga.get.Mission;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManager.MissionIterator;
import us.shandian.giga.ui.adapter.MissionAdapter;

public class Deleter {
    private static final String COMMIT = "commit";
    private static final String NEXT = "next";
    private static final String SHOW = "show";

    private static final int TIMEOUT = 5000;// ms
    private static final int DELAY = 350;// ms
    private static final int DELAY_RESUME = 400;// ms

    private Snackbar snackbar;
    private ArrayList<PendingDeletion> items;
    private boolean running = true;

    private final Context mContext;
    private final MissionAdapter mAdapter;
    private final DownloadManager mDownloadManager;
    private final MissionIterator mIterator;
    private final Handler mHandler;
    private final View mView;

    public Deleter(View v, Context c, MissionAdapter a, DownloadManager d, MissionIterator i, Handler h) {
        mView = v;
        mContext = c;
        mAdapter = a;
        mDownloadManager = d;
        mIterator = i;
        mHandler = h;

        items = new ArrayList<>(2);
    }

    public void append(final Mission item, final boolean alsoDeleteFile) {
        /* If a mission is removed from the list while the Snackbar for a previously
         * removed item is still showing, commit the action for the previous item
         * immediately. This prevents Snackbars from stacking up in reverse order.
         */
        mHandler.removeCallbacksAndMessages(COMMIT);
        commit();

        final PendingDeletion pendingDeletion =
                PendingDeletion.capture(item, alsoDeleteFile);
        mIterator.hide(item);
        pendingDeletion.suspend(mDownloadManager);
        items.add(0, pendingDeletion);

        show();
    }

    private void forget() {
        final PendingDeletion pendingDeletion = items.remove(0);
        mIterator.unHide(pendingDeletion.mission);
        pendingDeletion.restore(mDownloadManager);
        mAdapter.applyChanges();

        show();
    }

    private void show() {
        if (items.size() < 1) return;

        pause();
        running = true;

        HandlerCompat.postDelayed(mHandler, this::next, NEXT, DELAY);
    }

    private void next() {
        if (items.size() < 1) return;

        final Optional<String> fileToBeDeleted = items.stream()
                .filter(pendingDeletion -> pendingDeletion.alsoDeleteFile)
                .map(pendingDeletion -> pendingDeletion.mission.storage.getName())
                .findFirst();

        String msg;
        if (fileToBeDeleted.isPresent()) {
            msg = mContext.getString(R.string.file_deleted)
                    .concat(":\n")
                    .concat(fileToBeDeleted.get());
        } else {
            msg = mContext.getString(R.string.entry_deleted);
        }

        snackbar = Snackbar.make(mView, msg, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.undo, s -> forget());
        snackbar.setActionTextColor(Color.YELLOW);
        snackbar.show();

        HandlerCompat.postDelayed(mHandler, this::commit, COMMIT, TIMEOUT);
    }

    private void commit() {
        if (items.size() < 1) return;

        while (items.size() > 0) {
            final PendingDeletion pendingDeletion = items.remove(0);
            final Mission mission = pendingDeletion.mission;
            if (mission.deleted) continue;

            mIterator.unHide(mission);
            mDownloadManager.deleteMission(mission, pendingDeletion.alsoDeleteFile);

            if (mission instanceof FinishedMission) {
                mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mission.storage.getUri()));
            }
            break;
        }

        if (items.size() < 1) {
            pause();
            return;
        }

        show();
    }

    public void pause() {
        running = false;
        mHandler.removeCallbacksAndMessages(NEXT);
        mHandler.removeCallbacksAndMessages(SHOW);
        mHandler.removeCallbacksAndMessages(COMMIT);
        if (snackbar != null) snackbar.dismiss();
    }

    public void resume() {
        if (!running) {
            HandlerCompat.postDelayed(mHandler, this::show, SHOW, DELAY_RESUME);
        }
    }

    public void dispose() {
        if (items.size() < 1) return;

        pause();

        for (final PendingDeletion pendingDeletion : items) {
            mDownloadManager.deleteMission(
                    pendingDeletion.mission, pendingDeletion.alsoDeleteFile);
        }
        items = null;
    }

    static final class PendingDeletion {
        private final Mission mission;
        private final boolean alsoDeleteFile;
        private final boolean wasRunning;
        private final boolean wasEnqueued;

        private PendingDeletion(final Mission mission,
                                final boolean alsoDeleteFile,
                                final boolean wasRunning,
                                final boolean wasEnqueued) {
            this.mission = mission;
            this.alsoDeleteFile = alsoDeleteFile;
            this.wasRunning = wasRunning;
            this.wasEnqueued = wasEnqueued;
        }

        static PendingDeletion capture(final Mission mission,
                                       final boolean alsoDeleteFile) {
            if (mission instanceof DownloadMission) {
                final DownloadMission downloadMission = (DownloadMission) mission;
                return new PendingDeletion(mission, alsoDeleteFile,
                        downloadMission.running, downloadMission.enqueued);
            }
            return new PendingDeletion(mission, alsoDeleteFile, false, false);
        }

        void suspend(final DownloadManager downloadManager) {
            if (!(mission instanceof DownloadMission)) {
                return;
            }
            final DownloadMission downloadMission = (DownloadMission) mission;
            if (downloadMission.running) {
                downloadManager.pauseMission(downloadMission);
            } else if (downloadMission.enqueued) {
                downloadMission.setEnqueued(false);
            }
        }

        void restore(final DownloadManager downloadManager) {
            if (!(mission instanceof DownloadMission) || mission.deleted) {
                return;
            }
            final DownloadMission downloadMission = (DownloadMission) mission;
            downloadMission.setEnqueued(wasEnqueued);
            if (wasRunning) {
                downloadManager.resumeMission(downloadMission);
            }
        }
    }
}
