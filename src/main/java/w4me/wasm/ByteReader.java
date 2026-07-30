package w4me.wasm;

import java.io.UnsupportedEncodingException;

final class ByteReader {
    private final byte[] data;
    private int position;
    private final int limit;

    ByteReader(byte[] data) {
        this(data, 0, data.length);
    }

    private ByteReader(byte[] data, int position, int limit) {
        this.data = data;
        this.position = position;
        this.limit = limit;
    }

    int position() {
        return position;
    }

    int remaining() {
        return limit - position;
    }

    boolean hasRemaining() {
        return position < limit;
    }

    int readU8() throws WasmException {
        require(1);
        return data[position++] & 0xff; // NOPMD -- Compact Java 1.3 cursor bytecode.
    }

    int readUnsigned32LittleEndian() throws WasmException {
        int b0 = readU8();
        int b1 = readU8();
        int b2 = readU8();
        int b3 = readU8();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    long readUnsigned64LittleEndian() throws WasmException {
        long low = readUnsigned32LittleEndian() & 0xffffffffL;
        long high = readUnsigned32LittleEndian() & 0xffffffffL;
        return low | (high << 32);
    }

    int readVariableUnsigned32() throws WasmException {
        require(1);
        int value = data[position++] & 0xff; // NOPMD -- Compact Java 1.3 cursor bytecode.
        if ((value & 0x80) == 0) {
            return value;
        }
        long result = value & 0x7f;
        int shift = 7;
        int count = 1;
        while (true) {
            value = readU8();
            result |= (long) (value & 0x7f) << shift;
            count++;
            if ((value & 0x80) == 0) {
                if (count == 5 && (value & 0xf0) != 0) {
                    throw error("u32 LEB128 overflow");
                }
                return (int) result;
            }
            if (count >= 5) {
                throw error("u32 LEB128 is too long");
            }
            shift += 7;
        }
    }

    int readVarInt32() throws WasmException {
        long value = readVarInt(32);
        return (int) value;
    }

    long readVarInt64() throws WasmException {
        return readVarInt(64);
    }

    private long readVarInt(int bits) throws WasmException {
        require(1);
        int value = data[position++] & 0xff; // NOPMD -- Compact Java 1.3 cursor bytecode.
        if ((value & 0x80) == 0) {
            long result = value & 0x7f;
            if ((value & 0x40) != 0) {
                result |= -1L << 7;
            }
            return result;
        }
        long result = value & 0x7f;
        int shift = 7;
        int maxBytes = (bits + 6) / 7;
        int count = 1;
        while (true) {
            value = readU8();
            count++;
            if (count > maxBytes) {
                throw error("signed LEB128 is too long");
            }
            if (count == maxBytes) {
                int usedBits = bits - (maxBytes - 1) * 7;
                int valueBits = value & 0x7f;
                int signBit = 1 << (usedBits - 1);
                int unusedMask = 0x7f ^ ((1 << usedBits) - 1);
                if ((valueBits & signBit) == 0) {
                    if ((valueBits & unusedMask) != 0) {
                        throw error("signed LEB128 overflow");
                    }
                } else if ((valueBits & unusedMask) != unusedMask) {
                    throw error("signed LEB128 overflow");
                }
            }
            result |= (long) (value & 0x7f) << shift;
            shift += 7;
            if ((value & 0x80) == 0) {
                break;
            }
            if (count == maxBytes) {
                throw error("signed LEB128 is too long");
            }
        }

        if (shift < 64 && (value & 0x40) != 0) {
            result |= -1L << shift;
        }
        return result;
    }

    String readName() throws WasmException {
        int length = readLength("name", 65535);
        require(length);
        try {
            String value = new String(data, position, length, "UTF-8");
            position += length;
            return value;
        } catch (UnsupportedEncodingException exception) {
            throw error( // NOPMD -- CLDC 1.1 has no cause chaining.
                    "UTF-8 is unavailable");
        }
    }

    byte[] readBytes(int length) throws WasmException {
        require(length);
        byte[] result = new byte[length];
        System.arraycopy(data, position, result, 0, length);
        position += length;
        return result;
    }

    void skip(int length) throws WasmException {
        require(length);
        position += length;
    }

    ByteReader readSection(String label) throws WasmException {
        int length = readLength(label);
        require(length);
        ByteReader section = new ByteReader(data, position, position + length);
        position += length;
        return section;
    }

    int readLength(String label) throws WasmException {
        int length = readVariableUnsigned32();
        if (length < 0) {
            throw error(label + " length exceeds Java array limits");
        }
        return length;
    }

    int readLength(String label, int maximum) throws WasmException {
        int length = readLength(label);
        if (length > maximum) {
            throw error(label + " exceeds limit " + maximum);
        }
        return length;
    }

    void requireEnd(String label) throws WasmException {
        if (hasRemaining()) {
            throw error(label + " has " + remaining() + " trailing bytes");
        }
    }

    WasmException error(String message) {
        return new WasmException(message + " at byte " + position);
    }

    private void require(int length) throws WasmException {
        if (length < 0 || length > remaining()) {
            throw error("unexpected end of module");
        }
    }
}
