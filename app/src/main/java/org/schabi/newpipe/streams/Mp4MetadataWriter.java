package org.schabi.newpipe.streams;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Creates the iTunes-style metadata atoms stored below an MP4 {@code moov/udta} box. */
final class Mp4MetadataWriter {
    private static final int ATOM_UDTA = 0x75647461;
    private static final int ATOM_META = 0x6D657461;
    private static final int ATOM_HDLR = 0x68646C72;
    private static final int ATOM_ILST = 0x696C7374;
    private static final int ATOM_DATA = 0x64617461;

    private static final int TAG_TITLE = 0xA96E616D;
    private static final int TAG_ARTIST = 0xA9415254;
    private static final int TAG_GENRE = 0xA967656E;
    private static final int TAG_DATE = 0xA9646179;
    private static final int TAG_COMMENT = 0xA9636D74;

    private Mp4MetadataWriter() {
    }

    @NonNull
    static byte[] makeUdta(@NonNull final MediaTagMetadata metadata) {
        final List<byte[]> items = new ArrayList<>();
        addTextItem(items, TAG_TITLE, metadata.title);
        addTextItem(items, TAG_ARTIST, metadata.artist);
        addTextItem(items, TAG_GENRE, metadata.genre);
        addTextItem(items, TAG_DATE, metadata.date);
        addTextItem(items, TAG_COMMENT, metadata.comment);

        final byte[] handler = makeHandler();
        final byte[] itemList = box(ATOM_ILST, items.toArray(new byte[0][]));
        final byte[] meta = box(ATOM_META, ByteBuffer.allocate(4).putInt(0).array(),
                handler, itemList);
        return box(ATOM_UDTA, meta);
    }

    private static void addTextItem(@NonNull final List<byte[]> items,
                                    final int type,
                                    final String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        final byte[] text = value.trim().getBytes(StandardCharsets.UTF_8);
        final byte[] data = box(ATOM_DATA,
                ByteBuffer.allocate(8).putInt(1).putInt(0).array(), text);
        items.add(box(type, data));
    }

    @NonNull
    private static byte[] makeHandler() {
        return box(ATOM_HDLR, ByteBuffer.allocate(25)
                .putInt(0)
                .putInt(0)
                .putInt(0x6D646972)
                .put(new byte[12])
                .put((byte) 0)
                .array());
    }

    @NonNull
    private static byte[] box(final int type, @NonNull final byte[]... payloads) {
        int size = 8;
        for (final byte[] payload : payloads) {
            size += payload.length;
        }
        final ByteBuffer buffer = ByteBuffer.allocate(size).putInt(size).putInt(type);
        for (final byte[] payload : payloads) {
            buffer.put(payload);
        }
        return buffer.array();
    }
}
