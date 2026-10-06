package org.schabi.newpipe.player.datasource;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import org.schabi.newpipe.extractor.services.youtube.invidious.InvidiousBackend;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

/** Applies the instance boundary to each manifest, segment, encryption key and caption request. */
public final class InvidiousDataSource implements DataSource {
    private final DataSource delegate;
    private final String instance;

    private InvidiousDataSource(final DataSource delegate) {
        this.delegate = delegate;
        instance = InvidiousBackend.getInstance();
    }

    public static Factory restrict(final Factory factory) {
        return () -> new InvidiousDataSource(factory.createDataSource());
    }

    @Override
    public long open(@NonNull final DataSpec dataSpec) throws IOException {
        final String scheme = dataSpec.uri.getScheme();
        // Downloaded local media and inline captions can still be played offline.
        if (scheme != null && !List.of("file", "content", "asset", "data", "android.resource")
                .contains(scheme)) {
            try {
                if (!instance.equals(InvidiousBackend.getInstance())) {
                    throw new IOException("Invidious settings changed; reload this stream");
                }
                InvidiousBackend.checkInstanceRequest(URI.create(dataSpec.uri.toString()));
            } catch (final IllegalArgumentException e) {
                throw new IOException("Invalid Invidious media URL", e);
            }
        }
        return delegate.open(dataSpec);
    }

    @Override
    public int read(@NonNull final byte[] buffer, final int offset, final int length)
            throws IOException {
        return delegate.read(buffer, offset, length);
    }

    @Override
    public void addTransferListener(@NonNull final TransferListener listener) {
        delegate.addTransferListener(listener);
    }

    @Nullable
    @Override
    public Uri getUri() {
        return delegate.getUri();
    }

    @NonNull
    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return delegate.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
