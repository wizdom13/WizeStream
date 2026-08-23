package us.shandian.giga.postprocessing;

import android.media.MediaDataSource;

import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;

final class SharpStreamMediaDataSource extends MediaDataSource {
    private final SharpStream stream;
    private final long size;
    private long position;

    SharpStreamMediaDataSource(final SharpStream stream, final long size) {
        this.stream = stream;
        this.size = size;
    }

    @Override
    public synchronized int readAt(final long requestedPosition,
                                   final byte[] buffer,
                                   final int offset,
                                   final int count) throws IOException {
        if (requestedPosition < 0 || requestedPosition >= size) {
            return -1;
        }
        if (position != requestedPosition) {
            stream.seek(requestedPosition);
            position = requestedPosition;
        }
        final int read = stream.read(buffer, offset, (int) Math.min(count, size - position));
        if (read <= 0) {
            return -1;
        }
        position += read;
        return read;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public void close() {
        stream.close();
    }
}
