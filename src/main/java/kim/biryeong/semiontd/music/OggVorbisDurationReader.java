package kim.biryeong.semiontd.music;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OggVorbisDurationReader {
    private static final byte[] OGG_CAPTURE = new byte[] {'O', 'g', 'g', 'S'};
    private static final byte[] VORBIS_CAPTURE = new byte[] {'v', 'o', 'r', 'b', 'i', 's'};

    private OggVorbisDurationReader() {
    }

    public static long readDurationTicks(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return readDurationTicks(input);
        }
    }

    public static long readDurationTicks(InputStream input) throws IOException {
        int sampleRate = -1;
        long lastGranulePosition = -1;

        while (true) {
            byte[] header = input.readNBytes(27);
            if (header.length == 0) {
                break;
            }
            if (header.length < 27 || !matches(header, OGG_CAPTURE, 0)) {
                throw new IOException("Invalid Ogg page header.");
            }

            long granulePosition = littleEndianLong(header, 6);
            int segments = Byte.toUnsignedInt(header[26]);
            byte[] lacingValues = input.readNBytes(segments);
            if (lacingValues.length < segments) {
                throw new IOException("Truncated Ogg segment table.");
            }

            int bodyLength = 0;
            for (byte value : lacingValues) {
                bodyLength += Byte.toUnsignedInt(value);
            }
            byte[] body = input.readNBytes(bodyLength);
            if (body.length < bodyLength) {
                throw new IOException("Truncated Ogg page body.");
            }

            if (sampleRate < 1) {
                sampleRate = tryReadVorbisSampleRate(lacingValues, body);
            }
            if (granulePosition >= 0) {
                lastGranulePosition = granulePosition;
            }
        }

        if (sampleRate < 1 || lastGranulePosition < 0) {
            throw new IOException("Missing Vorbis sample rate or final granule position.");
        }
        return Math.max(1L, (long) Math.ceil(lastGranulePosition * 20.0 / sampleRate));
    }

    private static int tryReadVorbisSampleRate(byte[] lacingValues, byte[] body) {
        int offset = 0;
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        for (byte lacingValue : lacingValues) {
            int length = Byte.toUnsignedInt(lacingValue);
            if (offset + length > body.length) {
                return -1;
            }
            packet.write(body, offset, length);
            offset += length;
            if (length < 255) {
                byte[] data = packet.toByteArray();
                if (data.length >= 16
                        && data[0] == 1
                        && matches(data, VORBIS_CAPTURE, 1)) {
                    return littleEndianInt(data, 12);
                }
                packet.reset();
            }
        }
        return -1;
    }

    private static boolean matches(byte[] data, byte[] expected, int offset) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (data[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static int littleEndianInt(byte[] data, int offset) {
        return Byte.toUnsignedInt(data[offset])
                | (Byte.toUnsignedInt(data[offset + 1]) << 8)
                | (Byte.toUnsignedInt(data[offset + 2]) << 16)
                | (Byte.toUnsignedInt(data[offset + 3]) << 24);
    }

    private static long littleEndianLong(byte[] data, int offset) {
        long value = 0L;
        for (int index = 0; index < 8; index++) {
            value |= (long) Byte.toUnsignedInt(data[offset + index]) << (8 * index);
        }
        return value;
    }
}
