package us.shandian.giga.postprocessing;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

class M4aFromMp4Demuxer extends Postprocessing {
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 1024;
    private static final int COPY_BUFFER_SIZE = 128 * 1024;

    M4aFromMp4Demuxer() {
        super(false, false, ALGORITHM_M4A_FROM_MP4_DEMUXER);
    }

    @Override
    int process(final SharpStream out, final SharpStream... sources) throws IOException {
        final File outputFile = getTemporalFile();
        if (outputFile == null) {
            throw new IOException("M4A extraction temporary file is unavailable");
        }
        final File parent = outputFile.getParentFile();
        if (parent != null && parent.getUsableSpace() <= getMission().storage.length()) {
            throw new IOException("Not enough temporary storage for M4A extraction");
        }

        try {
            extractAudio(outputFile);
            throwIfCancellationRequested();
            replaceDownloadedFile(outputFile);
            throwIfCancellationRequested();
            getMission().length = getMission().storage.length();
            getMission().done = getMission().length;
            return OK_RESULT;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            outputFile.delete();
        }
    }

    private void extractAudio(final File outputFile) throws IOException {
        final MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean muxerStarted = false;
        try (SharpStreamMediaDataSource source = new SharpStreamMediaDataSource(
                getMission().storage.getStream(), getMission().storage.length())) {
            extractor.setDataSource(source);
            final int audioTrack = findAudioTrack(extractor);
            extractor.selectTrack(audioTrack);
            final MediaFormat inputFormat = extractor.getTrackFormat(audioTrack);
            final long durationUs = inputFormat.containsKey(MediaFormat.KEY_DURATION)
                    ? inputFormat.getLong(MediaFormat.KEY_DURATION) : -1;
            final int bufferSize = inputFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)
                    ? Math.max(DEFAULT_BUFFER_SIZE,
                            inputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    : DEFAULT_BUFFER_SIZE;

            muxer = new MediaMuxer(outputFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            final int outputTrack = muxer.addTrack(inputFormat);
            muxer.start();
            muxerStarted = true;

            final ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
            final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                throwIfCancellationRequested();
                buffer.clear();
                final int size = extractor.readSampleData(buffer, 0);
                if (size < 0) {
                    break;
                }
                info.set(0, size, extractor.getSampleTime(), extractor.getSampleFlags());
                muxer.writeSampleData(outputTrack, buffer, info);
                reportProgress(info.presentationTimeUs, durationUs);
                extractor.advance();
            }
        } finally {
            extractor.release();
            if (muxer != null) {
                if (muxerStarted) {
                    muxer.stop();
                }
                muxer.release();
            }
        }
    }

    private static int findAudioTrack(final MediaExtractor extractor) throws IOException {
        final String[] trackMimes = new String[extractor.getTrackCount()];
        for (int i = 0; i < trackMimes.length; i++) {
            trackMimes[i] = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
        }
        final int audioTrack = findAudioTrackIndex(trackMimes);
        if (audioTrack < 0) {
            throw new IOException("Downloaded MP4 contains no audio track");
        }
        return audioTrack;
    }

    static int findAudioTrackIndex(final String... trackMimes) {
        for (int i = 0; i < trackMimes.length; i++) {
            if (trackMimes[i] != null && trackMimes[i].startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    private void reportProgress(final long presentationTimeUs, final long durationUs) {
        if (durationUs <= 0 || presentationTimeUs < 0) {
            return;
        }
        final long sourceLength = getMission().storage.length();
        getMission().length = sourceLength;
        getMission().done = Math.min(sourceLength, (long) (sourceLength
                * Math.min(1.0d, (double) presentationTimeUs / durationUs)));
    }

    private void replaceDownloadedFile(final File extracted) throws IOException {
        throwIfCancellationRequested();
        try (FileInputStream input = new FileInputStream(extracted);
             SharpStream target = getMission().storage.openAndTruncateStream()) {
            final byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                throwIfCancellationRequested();
                target.write(buffer, 0, read);
            }
            throwIfCancellationRequested();
            target.flush();
        }
    }

}
