package com.acme.scaffold.common.messaging.codec;

/**
 * Varint / ZigZag 原语（LEB128 无符号变长编码 + ZigZag 有符号映射）。
 *
 * <p>对应设计稿 §1.1「Protobuf 二进制（热路径禁用 JSON）」的思路：网关与客户端之间
 * 采用自建二进制帧，长度与序号字段用 varint 压缩，小消息（≤128B）可省 30%+ 头部开销。
 *
 * <p>实现约定：
 * <ul>
 *   <li>varint 为无符号 LEB128，每字节 7 位数据、高位延续；上限 10 字节（64 位）；</li>
 *   <li>zigzag 用于可能为负的定长字段（seq 防御性），保证小负数也占 1 字节；</li>
 *   <li>读写共用 {@link Reader} 游标，截断/越界一律抛 {@link CorruptFrameException}，
 *       不返回猜测值（协议防御）。</li>
 * </ul>
 */
public final class Varint {

    /** varint 最大字节数（64 位值）。 */
    public static final int MAX_VARINT_BYTES = 10;

    private Varint() {
    }

    // ---------------------------------------------------------------- 写入

    /**
     * 无符号 varint 写入，返回写入后的偏移。
     *
     * <p>按<b>无符号 64 位语义</b>处理入参：负 long 以其位模式参与编码
     * （zigzag64 的输出正是这种形态，如 Long.MIN_VALUE zigzag 后为 -1）。
     */
    public static int writeVarint(byte[] buf, int offset, long value) {
        int pos = offset;
        long remaining = value;
        while ((remaining & 0xFFFFFFFFFFFFFF80L) != 0) {
            if (pos >= buf.length) {
                throw new IllegalArgumentException("缓冲区不足: 偏移 " + pos + " >= 长度 " + buf.length);
            }
            buf[pos++] = (byte) ((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        if (pos >= buf.length) {
            throw new IllegalArgumentException("缓冲区不足: 偏移 " + pos + " >= 长度 " + buf.length);
        }
        buf[pos++] = (byte) remaining;
        return pos;
    }

    /** zigzag64 写入（有符号 → 无符号 → varint）。 */
    public static int writeZigZag64(byte[] buf, int offset, long value) {
        return writeVarint(buf, offset, encodeZigZag64(value));
    }

    /** 计算无符号 varint 编码长度（负值按无符号 64 位计算，最长 10 字节）。 */
    public static int varintSize(long value) {
        int size = 1;
        long remaining = value >>> 7;
        while (remaining != 0) {
            size++;
            remaining >>>= 7;
        }
        return size;
    }

    public static int zigZag64Size(long value) {
        return varintSize(encodeZigZag64(value));
    }

    // ---------------------------------------------------------------- ZigZag 映射

    public static long encodeZigZag64(long value) {
        return (value << 1) ^ (value >> 63);
    }

    public static long decodeZigZag64(long value) {
        return (value >>> 1) ^ -(value & 1);
    }

    public static int encodeZigZag32(int value) {
        return (value << 1) ^ (value >> 31);
    }

    public static int decodeZigZag32(int value) {
        return (value >>> 1) ^ -(value & 1);
    }

    // ---------------------------------------------------------------- 读取游标

    /** 顺序读取游标：封装边界检查，调用方无需重复判空/判越界。 */
    public static final class Reader {

        private final byte[] buf;
        private final int end;
        private int pos;

        public Reader(byte[] buf) {
            this(buf, 0, buf.length);
        }

        public Reader(byte[] buf, int offset, int length) {
            if (buf == null) {
                throw new IllegalArgumentException("buf 不能为空");
            }
            if (offset < 0 || length < 0 || offset + length > buf.length) {
                throw new IllegalArgumentException("读取窗口越界: offset=" + offset + ", length=" + length);
            }
            this.buf = buf;
            this.pos = offset;
            this.end = offset + length;
        }

        public int position() {
            return pos;
        }

        public int remaining() {
            return end - pos;
        }

        public boolean hasRemaining() {
            return pos < end;
        }

        /** 读取 1 字节（无符号）。 */
        public int readByte() {
            require(1);
            return buf[pos++] & 0xFF;
        }

        public long readVarint() {
            long result = 0;
            int shift = 0;
            while (true) {
                require(1);
                byte b = buf[pos++];
                if (shift == 63) {
                    // 第 10 字节：仅允许保留 bit63 一个数据位，且不得再带延续位
                    if ((b & 0x7F) > 1) {
                        throw new CorruptFrameException("varint 第 10 字节超出 64 位范围: " + (b & 0xFF));
                    }
                    result |= (long) (b & 0x7F) << 63;
                    if ((b & 0x80) != 0) {
                        throw new CorruptFrameException("varint 超过 10 字节上限");
                    }
                    return result;
                }
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
                shift += 7;
                if (shift > 63) {
                    throw new CorruptFrameException("varint 超过 10 字节上限");
                }
            }
        }

        public long readZigZag64() {
            return decodeZigZag64(readVarint());
        }

        /** 读取定长字节段（长度需调用方先行读取并校验）。 */
        public byte[] readBytes(int length) {
            if (length < 0) {
                throw new CorruptFrameException("长度字段为负: " + length);
            }
            require(length);
            byte[] out = new byte[length];
            System.arraycopy(buf, pos, out, 0, length);
            pos += length;
            return out;
        }

        /** 读取 varint 长度前缀的 UTF-8 字符串。 */
        public String readString(int maxLength) {
            long len = readVarint();
            if (len < 0 || len > maxLength) {
                throw new CorruptFrameException("字符串长度越界(0.." + maxLength + "): " + len);
            }
            return new String(readBytes((int) len), java.nio.charset.StandardCharsets.UTF_8);
        }

        /** 确认缓冲区已按协议消费完毕（防粘连帧/尾部垃圾）。 */
        public void expectEnd() {
            if (hasRemaining()) {
                throw new CorruptFrameException("帧尾部存在未消费字节: " + remaining());
            }
        }

        private void require(int n) {
            if (n < 0 || end - pos < n) {
                throw new CorruptFrameException("缓冲区截断: 需要 " + n + " 字节, 剩余 " + (end - pos));
            }
        }
    }
}
