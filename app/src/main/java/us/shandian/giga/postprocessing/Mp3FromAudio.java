package us.shandian.giga.postprocessing;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.NonNull;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

final class Mp3FromAudio extends Postprocessing {
    private static final long CODEC_TIMEOUT_US = 10_000;
    private static final int COPY_BUFFER_SIZE = 128 * 1_024;

    Mp3FromAudio() {
        super(false, false, ALGORITHM_MP3_FROM_AUDIO);
    }

    @Override
    int process(final SharpStream out, final SharpStream... sources) throws IOException {
        final int bitrate = Mp3OutputOptions.parseBitrate(
                getArgumentAt(0, String.valueOf(Mp3OutputOptions.DEFAULT_BITRATE_KBPS)));
        final File outputFile = getTemporalFile();
        if (outputFile == null) {
            throw new IOException("MP3 conversion temporary file is unavailable");
        }
        final long estimatedOutputBytes = Mp3OutputOptions.estimateOutputBytes(
                streamInfo == null ? 0 : streamInfo.getDuration(), bitrate);
        final File parent = outputFile.getParentFile();
        if (parent != null && estimatedOutputBytes > 0
                && parent.getUsableSpace() <= estimatedOutputBytes) {
            throw new IOException("Not enough temporary storage for MP3 conversion");
        }

        try {
            transcode(outputFile, bitrate);
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

    private void transcode(@NonNull final File outputFile, final int bitrate) throws IOException {
        final MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try (SharpStreamMediaDataSource source = new SharpStreamMediaDataSource(
                getMission().storage.getStream(), getMission().storage.length());
             FileOutputStream output = new FileOutputStream(outputFile)) {
            extractor.setDataSource(source);
            final int audioTrack = findAudioTrack(extractor);
            extractor.selectTrack(audioTrack);
            final MediaFormat inputFormat = extractor.getTrackFormat(audioTrack);
            final String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            if (mime == null) {
                throw new IOException("Downloaded audio has no decoder MIME type");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING,
                        AudioFormat.ENCODING_PCM_16BIT);
            }

            Id3MetadataWriter.write(output,
                    streamInfo == null ? null : streamInfo.getName(),
                    streamInfo == null ? null : streamInfo.getUploaderName(),
                    streamInfo == null ? null : streamInfo.getUrl());

            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(inputFormat, null, null, 0);
            decoder.start();
            final long durationUs = inputFormat.containsKey(MediaFormat.KEY_DURATION)
                    ? inputFormat.getLong(MediaFormat.KEY_DURATION) : -1;
            decodeToMp3(extractor, decoder, output, bitrate, durationUs);
        } finally {
            extractor.release();
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (final IllegalStateException ignored) {
                    // The decoder may not have reached the started state.
                }
                decoder.release();
            }
        }
    }

    private int findAudioTrack(final MediaExtractor extractor) throws IOException {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            final String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return i;
            }
        }
        throw new IOException("Downloaded file contains no audio track");
    }

    private void decodeToMp3(final MediaExtractor extractor,
                             final MediaCodec decoder,
                             final FileOutputStream output,
                             final int bitrate,
                             final long durationUs) throws IOException {
        final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputEnded = false;
        boolean outputEnded = false;
        JavaLamePcmEncoder encoder = null;

        try {
            while (!outputEnded) {
            throwIfCancellationRequested();
                if (!inputEnded) {
                    final int inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US);
                    if (inputIndex >= 0) {
                        final ByteBuffer input = decoder.getInputBuffer(inputIndex);
                        if (input == null) {
                            throw new IOException("Audio decoder returned no input buffer");
                        }
                        input.clear();
                        final int sampleSize = extractor.readSampleData(input, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize,
                                    extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                final int outputIndex = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    final MediaFormat outputFormat = decoder.getOutputFormat();
                    final int pcmEncoding = outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            ? outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            : AudioFormat.ENCODING_PCM_16BIT;
                    if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT) {
                        throw new IOException("Unsupported decoder PCM encoding: " + pcmEncoding);
                    }
                    encoder = new JavaLamePcmEncoder(
                            outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                            outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                            bitrate,
                            output);
                } else if (outputIndex >= 0) {
                    final ByteBuffer decoded = decoder.getOutputBuffer(outputIndex);
                    if (decoded == null) {
                        throw new IOException("Audio decoder returned no output buffer");
                    }
                    if (encoder == null) {
                        final MediaFormat outputFormat = decoder.getOutputFormat(outputIndex);
                        encoder = new JavaLamePcmEncoder(
                                outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                                outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                                bitrate,
                                output);
                    }
                    if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        decoded.position(info.offset);
                        decoded.limit(info.offset + info.size);
                        encoder.encode(decoded.slice());
                        reportProgress(info.presentationTimeUs, durationUs);
                    }
                    outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(outputIndex, false);
                }
            }
            if (encoder == null) {
                throw new IOException("Audio decoder produced no PCM output");
            }
            encoder.finish();
        } finally {
            if (encoder != null) {
                encoder.close();
            }
        }
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

    private void replaceDownloadedFile(final File converted) throws IOException {
        throwIfCancellationRequested();
        try (FileInputStream input = new FileInputStream(converted);
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
