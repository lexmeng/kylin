/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kylin.common.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkPositionIndex;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sun.misc.Unsafe;

/**
 * Utility class that handles byte arrays, conversions to/from other types,
 * comparisons, hash code generation, manufacturing keys for HashMaps or
 * HashSets, etc.
 *
 * Copied from org.apache.hadoop.hbase.util.Bytes
 */
public class Bytes {
    //HConstants.UTF8_ENCODING should be updated if this changed
    /**
     * When we encode strings, we always specify UTF8 encoding
     */
    private static final String UTF8_ENCODING = "UTF-8";

    //HConstants.UTF8_CHARSET should be updated if this changed
    /**
     * When we encode strings, we always specify UTF8 encoding
     */
    private static final Charset UTF8_CHARSET = StandardCharsets.UTF_8;

    //HConstants.EMPTY_BYTE_ARRAY should be updated if this changed
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private static final Log LOG = LogFactory.getLog(Bytes.class);

    /**
     * Size of boolean in bytes
     */
    public static final int SIZEOF_BOOLEAN = 1;

    /**
     * Size of byte in bytes
     */
    public static final int SIZEOF_BYTE = SIZEOF_BOOLEAN;

    /**
     * Size of char in bytes
     */
    public static final int SIZEOF_CHAR = Character.SIZE / Byte.SIZE;

    /**
     * Size of double in bytes
     */
    public static final int SIZEOF_DOUBLE = Double.SIZE / Byte.SIZE;

    /**
     * Size of float in bytes
     */
    public static final int SIZEOF_FLOAT = Float.SIZE / Byte.SIZE;

    /**
     * Size of int in bytes
     */
    public static final int SIZEOF_INT = Integer.SIZE / Byte.SIZE;

    /**
     * Size of long in bytes
     */
    public static final int SIZEOF_LONG = Long.SIZE / Byte.SIZE;

    /**
     * Size of short in bytes
     */
    public static final int SIZEOF_SHORT = Short.SIZE / Byte.SIZE;

    /**
     * Estimate of size cost to pay beyond payload in jvm for instance of byte [].
     * Estimate based on study of jhat and jprofiler numbers.
     */
    // JHat says BU is 56 bytes.
    // SizeOf which uses java.lang.instrument says 24 bytes. (3 longs?)
    public static final int ESTIMATED_HEAP_TAX = 16;

    /**
     * Returns length of the byte array, returning 0 if the array is null.
     * Useful for calculating sizes.
     *
     * @param b byte array, which can be null
     * @return 0 if b is null, otherwise returns length
     */
    final public static int len(byte[] b) {
        return b == null ? 0 : b.length;
    }

    /**
     * Put bytes at the specified byte array position.
     *
     * @param tgtBytes  the byte array
     * @param tgtOffset position in the array
     * @param srcBytes  array to write out
     * @param srcOffset source offset
     * @param srcLength source length
     * @return incremented offset
     */
    public static int putBytes(byte[] tgtBytes, int tgtOffset, byte[] srcBytes, int srcOffset, int srcLength) {
        System.arraycopy(srcBytes, srcOffset, tgtBytes, tgtOffset, srcLength);
        return tgtOffset + srcLength;
    }

    /**
     * Write a single byte out to the specified byte array position.
     *
     * @param bytes  the byte array
     * @param offset position in the array
     * @param b      byte to write out
     * @return incremented offset
     */
    public static int putByte(byte[] bytes, int offset, byte b) {
        bytes[offset] = b;
        return offset + 1;
    }

    /**
     * Add the whole content of the ByteBuffer to the bytes arrays. The ByteBuffer is modified.
     *
     * @param bytes  the byte array
     * @param offset position in the array
     * @param buf    ByteBuffer to write out
     * @return incremented offset
     */
    public static int putByteBuffer(byte[] bytes, int offset, ByteBuffer buf) {
        int len = buf.remaining();
        buf.get(bytes, offset, len);
        return offset + len;
    }

    /**
     * Returns a new byte array, copied from the given {@code buf},
     * from the index 0 (inclusive) to the limit (exclusive),
     * regardless of the current position.
     * The position and the other index parameters are not changed.
     *
     * @param buf a byte buffer
     * @return the byte array
     * @see #getBytes(ByteBuffer)
     */
    public static byte[] toBytes(ByteBuffer buf) {
        ByteBuffer dup = buf.duplicate();
        dup.position(0);
        return readBytes(dup);
    }

    private static byte[] readBytes(ByteBuffer buf) {
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * @param b Presumed UTF-8 encoded byte array.
     * @return String made from <code>b</code>
     */
    public static String toString(final byte[] b) {
        if (b == null) {
            return null;
        }
        return toString(b, 0, b.length);
    }

    /**
     * Joins two byte arrays together using a separator.
     *
     * @param b1  The first byte array.
     * @param sep The separator to use.
     * @param b2  The second byte array.
     */
    public static String toString(final byte[] b1, String sep, final byte[] b2) {
        return toString(b1, 0, b1.length) + sep + toString(b2, 0, b2.length);
    }

    /**
     * This method will convert utf8 encoded bytes into a string. If
     * the given byte array is null, this method will return null.
     *
     * @param b   Presumed UTF-8 encoded byte array.
     * @param off offset into array
     * @param len length of utf-8 sequence
     * @return String made from <code>b</code> or null
     */
    public static String toString(final byte[] b, int off, int len) {
        if (b == null) {
            return null;
        }
        if (len == 0) {
            return "";
        }
        return new String(b, off, len, UTF8_CHARSET);
    }

    /**
     * Write a printable representation of a byte array.
     *
     * @param b byte array
     * @return string
     * @see #toStringBinary(byte[], int, int)
     */
    public static String toStringBinary(final byte[] b) {
        if (b == null)
            return "null";
        return toStringBinary(b, 0, b.length);
    }

    /**
     * Converts the given byte buffer to a printable representation,
     * from the index 0 (inclusive) to the limit (exclusive),
     * regardless of the current position.
     * The position and the other index parameters are not changed.
     *
     * @param buf a byte buffer
     * @return a string representation of the buffer's binary contents
     * @see #toBytes(ByteBuffer)
     * @see #getBytes(ByteBuffer)
     */
    public static String toStringBinary(ByteBuffer buf) {
        if (buf == null)
            return "null";
        if (buf.hasArray()) {
            return toStringBinary(buf.array(), buf.arrayOffset(), buf.limit());
        }
        return toStringBinary(toBytes(buf));
    }

    /**
     * Write a printable representation of a byte array. Non-printable
     * characters are hex escaped in the format \\x%02X, eg:
     * \x00 \x05 etc
     *
     * @param b   array to write out
     * @param off offset to start at
     * @param len length to write
     * @return string output
     */
    public static String toStringBinary(final byte[] b, int off, int len) {
        StringBuilder result = new StringBuilder();
        // Just in case we are passed a 'len' that is > buffer length...
        if (off >= b.length)
            return result.toString();
        if (off + len > b.length)
            len = b.length - off;
        for (int i = off; i < off + len; ++i) {
            int ch = b[i] & 0xFF;
            if ((ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')
                    || " `~!@#$%^&*()-_=+[]{}|;:'\",.<>/?".indexOf(ch) >= 0) {
                result.append((char) ch);
            } else {
                result.append(String.format(Locale.ROOT, "\\x%02X", ch));
            }
        }
        return result.toString();
    }

    private static boolean isHexDigit(char c) {
        return (c >= 'A' && c <= 'F') || (c >= '0' && c <= '9');
    }

    /**
     * Takes a ASCII digit in the range A-F0-9 and returns
     * the corresponding integer/ordinal value.
     *
     * @param ch The hex digit.
     * @return The converted hex value as a byte.
     */
    public static byte toBinaryFromHex(byte ch) {
        if (ch >= 'A' && ch <= 'F')
            return (byte) ((byte) 10 + (byte) (ch - 'A'));
        // else
        return (byte) (ch - '0');
    }

    public static byte[] toBytesBinary(String in) {
        // this may be bigger than we need, but let's be safe.
        byte[] b = new byte[in.length()];
        int size = 0;
        for (int i = 0; i < in.length(); ++i) {
            char ch = in.charAt(i);
            if (ch == '\\' && in.length() > i + 1 && in.charAt(i + 1) == 'x') {
                // ok, take next 2 hex digits.
                char hd1 = in.charAt(i + 2);
                char hd2 = in.charAt(i + 3);

                // they need to be A-F0-9:
                if (!isHexDigit(hd1) || !isHexDigit(hd2)) {
                    // bogus escape code, ignore:
                    continue;
                }
                // turn hex ASCII digit -> number
                byte d = (byte) ((toBinaryFromHex((byte) hd1) << 4) + (toBinaryFromHex((byte) hd2) & 0xff) & 0xff);

                b[size++] = d;
                i += 3; // skip 3
            } else {
                b[size++] = (byte) ch;
            }
        }
        // resize:
        byte[] b2 = new byte[size];
        System.arraycopy(b, 0, b2, 0, size);
        return b2;
    }

    /**
     * Converts a string to a UTF-8 byte array.
     *
     * @param s string
     * @return the byte array
     */
    public static byte[] toBytes(String s) {
        return s.getBytes(UTF8_CHARSET);
    }

    /**
     * Convert a boolean to a byte array. True becomes -1
     * and false becomes 0.
     *
     * @param b value
     * @return <code>b</code> encoded in a byte array.
     */
    public static byte[] toBytes(final boolean b) {
        return new byte[] { b ? (byte) -1 : (byte) 0 };
    }

    /**
     * Reverses {@link #toBytes(boolean)}
     *
     * @param b array
     * @return True or false.
     */
    public static boolean toBoolean(final byte[] b) {
        if (b.length != 1) {
            throw new IllegalArgumentException("Array has wrong size: " + b.length);
        }
        return b[0] != (byte) 0;
    }

    /**
     * Convert a long value to a byte array using big-endian.
     *
     * @param val value to convert
     * @return the byte array
     */
    public static byte[] toBytes(long val) {
        byte[] b = new byte[8];
        for (int i = 7; i > 0; i--) {
            b[i] = (byte) val;
            val >>>= 8;
        }
        b[0] = (byte) val;
        return b;
    }

    /**
     * Converts a byte array to a long value. Reverses
     * {@link #toBytes(long)}
     *
     * @param bytes array
     * @return the long value
     */
    public static long toLong(byte[] bytes) {
        return toLong(bytes, 0, SIZEOF_LONG);
    }

    /**
     * Converts a byte array to a long value. Assumes there will be
     * {@link #SIZEOF_LONG} bytes available.
     *
     * @param bytes  bytes
     * @param offset offset
     * @return the long value
     */
    public static long toLong(byte[] bytes, int offset) {
        return toLong(bytes, offset, SIZEOF_LONG);
    }

    /**
     * Converts a byte array to a long value.
     *
     * @param bytes  array of bytes
     * @param offset offset into array
     * @param length length of data (must be {@link #SIZEOF_LONG})
     * @return the long value
     * @throws IllegalArgumentException if length is not {@link #SIZEOF_LONG} or
     *                                  if there's not enough room in the array at the offset indicated.
     */
    public static long toLong(byte[] bytes, int offset, final int length) {
        if (length != SIZEOF_LONG || offset + length > bytes.length) {
            throw explainWrongLengthOrOffset(bytes, offset, length, SIZEOF_LONG);
        }
        if (Bytes.LexicographicalComparerHolder.UnsafeComparer.isAvailable()) {
            return toLongUnsafe(bytes, offset);
        } else {
            long l = 0;
            for (int i = offset; i < offset + length; i++) {
                l <<= 8;
                l ^= bytes[i] & 0xFF;
            }
            return l;
        }
    }

    private static IllegalArgumentException explainWrongLengthOrOffset(final byte[] bytes, final int offset,
            final int length, final int expectedLength) {
        String reason;
        if (length != expectedLength) {
            reason = "Wrong length: " + length + ", expected " + expectedLength;
        } else {
            reason = "offset (" + offset + ") + length (" + length + ") exceed the" + " capacity of the array: "
                    + bytes.length;
        }
        return new IllegalArgumentException(reason);
    }

    /**
     * Put a long value out to the specified byte array position.
     *
     * @param bytes  the byte array
     * @param offset position in the array
     * @param val    long to write out
     * @return incremented offset
     * @throws IllegalArgumentException if the byte array given doesn't have
     *                                  enough room at the offset specified.
     */
    public static int putLong(byte[] bytes, int offset, long val) {
        if (bytes.length - offset < SIZEOF_LONG) {
            throw new IllegalArgumentException(
                    "Not enough room to put a long at" + " offset " + offset + " in a " + bytes.length + " byte array");
        }
        if (Bytes.LexicographicalComparerHolder.UnsafeComparer.isAvailable()) {
            return putLongUnsafe(bytes, offset, val);
        } else {
            for (int i = offset + 7; i > offset; i--) {
                bytes[i] = (byte) val;
                val >>>= 8;
            }
            bytes[offset] = (byte) val;
            return offset + SIZEOF_LONG;
        }
    }

    /**
     * Put a long value out to the specified byte array position (Unsafe).
     *
     * @param bytes  the byte array
     * @param offset position in the array
     * @param val    long to write out
     * @return incremented offset
     */
    public static int putLongUnsafe(byte[] bytes, int offset, long val) {
        if (Bytes.LexicographicalComparerHolder.UnsafeComparer.littleEndian) {
            val = Long.reverseBytes(val);
        }
        Bytes.LexicographicalComparerHolder.UnsafeComparer.theUnsafe.putLong(bytes,
                (long) offset + Bytes.LexicographicalComparerHolder.UnsafeComparer.BYTE_ARRAY_BASE_OFFSET, val);
        return offset + SIZEOF_LONG;
    }

    /**
     * Presumes float encoded as IEEE 754 floating-point "single format"
     *
     * @param bytes byte array
     * @return Float made from passed byte array.
     */
    public static float toFloat(byte[] bytes) {
        return toFloat(bytes, 0);
    }

    /**
     * Presumes float encoded as IEEE 754 floating-point "single format"
     *
     * @param bytes  array to convert
     * @param offset offset into array
     * @return Float made from passed byte array.
     */
    public static float toFloat(byte[] bytes, int offset) {
        return Float.intBitsToFloat(toInt(bytes, offset, SIZEOF_INT));
    }

    /**
     * @param bytes  byte array
     * @param offset offset to write to
     * @param f      float value
     * @return New offset in <code>bytes</code>
     */
    public static int putFloat(byte[] bytes, int offset, float f) {
        return putInt(bytes, offset, Float.floatToRawIntBits(f));
    }

    /**
     * @param f float value
     * @return the float represented as byte []
     */
    public static byte[] toBytes(final float f) {
        // Encode it as int
        return Bytes.toBytes(Float.floatToRawIntBits(f));
    }

    /**
     * @param bytes byte array
     * @return Return double made from passed bytes.
     */
    public static double toDouble(final byte[] bytes) {
        return toDouble(bytes, 0);
    }

    /**
     * @param bytes  byte array
     * @param offset offset where double is
     * @return Return double made from passed bytes.
     */
    public static double toDouble(final byte[] bytes, final int offset) {
        return Double.longBitsToDouble(toLong(bytes, offset, SIZEOF_LONG));
    }

    /**
     * @param bytes  byte array
     * @param offset offset to write to
     * @param d      value
     * @return New offset into array <code>bytes</code>
     */
    public static int putDouble(byte[] bytes, int offset, double d) {
        return putLong(bytes, offset, Double.doubleToLongBits(d));
    }

    /**
     * Serialize a double as the IEEE 754 double format output. The resultant
     * array will be 8 bytes long.
     *
     * @param d value
     * @return the double represented as byte []
     */
    public static byte[] toBytes(final double d) {
        // Encode it as a long
        return Bytes.toBytes(Double.doubleToRawLongBits(d));
    }

    /**
     * Convert an int value to a byte array.  Big-endian.  Same as what DataOutputStream.writeInt
     * does.
     *
     * @param val value
     * @return the byte array
     */
    public static byte[] toBytes(int val) {
        byte[] b = new byte[4];
        for (int i = 3; i > 0; i--) {
            b[i] = (byte) val;
            val >>>= 8;
        }
        b[0] = (byte) val;
        return b;
    }

    /**
     * Converts a byte array to an int value
     *
     * @param bytes byte array
     * @return the int value
     */
    public static int toInt(byte[] bytes) {
        return toInt(bytes, 0, SIZEOF_INT);
    }

    /**
     * Converts a byte array to an int value
     *
     * @param bytes  byte array
     * @param offset offset into array
     * @return the int value
     */
    public static int toInt(byte[] bytes, int offset) {
        return toInt(bytes, offset, SIZEOF_INT);
    }

    /**
     * Converts a byte array to an int value
     *
     * @param bytes  byte array
     * @param offset offset into array
     * @param length length of int (has to be {@link #SIZEOF_INT})
     * @return the int value
     * @throws IllegalArgumentException if length is not {@link #SIZEOF_INT} or
     *                                  if there's not enough room in the array at the offset indicated.
     */
    public static int toInt(byte[] bytes, int offset, final int length) {
        if (length != SIZEOF_INT || offset + length > bytes.length) {
            throw explainWrongLengthOrOffset(bytes, offset, length, SIZEOF_INT);
        }
        if (Bytes.LexicographicalComparerHolder.UnsafeComparer.isAvailable()) {
            return toIntUnsafe(bytes, offset);
        } else {
            int n = 0;
            for (int i = offset; i < (offset + length); i++) {
                n <<= 8;
                n ^= bytes[i] & 0xFF;
            }
            return n;
        }
    }

    /**
     * Converts a byte array to an int value (Unsafe version)
     *
     * @param bytes  byte array
     * @param offset offset into array
     * @return the int value
     */
    public static int toIntUnsafe(byte[] bytes, int offset) {
        if (Bytes.LexicographicalComparerHolder.UnsafeComparer.littleEndian) {
            return Integer.reverseBytes(Bytes.LexicographicalComparerHolder.UnsafeComparer.theUnsafe.getInt(bytes,
                    (long) offset + Bytes.LexicographicalComparerHolder.UnsafeComparer.BYTE_ARRAY_BASE_OFFSET));
        } else {
            return Bytes.LexicographicalComparerHolder.UnsafeComparer.theUnsafe.getInt(bytes,
                    (long) offset + Bytes.LexicographicalComparerHolder.UnsafeComparer.BYTE_ARRAY_BASE_OFFSET);
        }
    }

    /**
     * Converts a byte array to an short value (Unsafe version)
     *
     * @param bytes  byte array
     * @param offset offset into array
     * @return the short value
     */
    public static short toShortUnsafe(byte[] bytes, int offset) {
        if (Bytes.LexicographicalComparerHolder.UnsafeComparer.littleEndian) {
            return Short.reverseBytes(Bytes.LexicographicalComparerHolder.UnsafeComparer.theUnsafe.getShort(bytes,
                    (long) offset + Bytes.LexicographicalComparerHolder.UnsafeComparer.BYTE_ARRAY_BASE_OFFSET));
        } else {
            return Bytes.LexicographicalComparerHolder.UnsafeComparer.theUnsafe.getShort(bytes,
                    (long) offset + Bytes.LexicographicalComparerHolder.UnsafeComparer.BYTE_ARRAY_BASE_OFFSET);
        }
    }

    /**
     * Converts a byte array to an long value (Unsafe version)
     *
     * @param bytes  byte array
     * @param offset offset into array
     * @return the long value
     */
    public static long toLongUnsafe(byte[] bytes, int offset) {
        if (Bytes.LexicographicalComparerHolder.UnsafeComparer.littleEndian) {
            return Long.reverseBytes(Bytes.LexicographicalComparerHolder.UnsafeComparer.theUnsafe.getLong(bytes,
                    (long) offset + Bytes.LexicographicalComparerHolder.UnsafeComparer.BYTE_ARRAY_BASE_OFFSET));
        } else {
            return Bytes.LexicographicalComparerHolder.UnsafeComparer.theUnsafe.getLong(bytes,
                    (long) offset + Bytes.LexicographicalComparerHolder.UnsafeComparer.BYTE_ARRAY_BASE_OFFSET);
        }
    }

    /**
     * Converts a byte array to an int value
     *
     * @param bytes  byte array
     * @param offset offset into array
     * @param length how many bytes should be considered for creating int
     * @return the int value
     * @throws IllegalArgumentException if there's not enough room in the array at the offset
     *                                  indicated.
     */
    public static int readAsInt(byte[] bytes, int offset, final int length) {
        if (offset + length > bytes.length) {
            throw new IllegalArgumentException("offset (" + offset + ") + length (" + length + ") exceed the"
                    + " capacity of the array: " + bytes.length);
        }
        int n = 0;
        for (int i = offset; i < (offset + length); i++) {
            n <<= 8;
            n ^= bytes[i] & 0xFF;
        }
        return n;
    }

    /**
     * Put an int value out to the specified byte array position.
     *
     * @param bytes  the byte array
     * @param offset position in the array
     * @param val    int to write out
     * @return incremented offset
     * @throws IllegalArgumentException if the byte array given doesn't have
     *                                  enough room at the offset specified.
     */
    public static int putInt(byte[] bytes, int offset, int val) {
        if (bytes.length - offset < SIZEOF_INT) {
            throw new IllegalArgumentException(
                    "Not enough room to put an int at" + " offset " + offset + " in a " + bytes.length + " byte array");
        }
        if (Bytes.LexicographicalComparerHolder.UnsafeComparer.isAvailable()) {
            return putIntUnsafe(bytes, offset, val);
        } else {
            for (int i = offset + 3; i > offset; i--) {
                bytes[i] = (byte) val;
                val >>>= 8;
            }
            bytes[offset] = (byte) val;
            return offset + SIZEOF_INT;
        }
    }

    /**
     * Put an int value out to the specified byte array position (Unsafe).
     *
     * @param bytes  the byte array
     * @param offset position in the array
     * @param val    int to write out
     * @return incremented offset
     */
    public static int putIntUnsafe(byte[] bytes, int offset, int val) {
        if (Bytes.LexicographicalComparerHolder.UnsafeComparer.littleEndian) {
            val = Integer.reverseBytes(val);
        }
        Bytes.LexicographicalComparerHolder.UnsafeComparer.theUnsafe.putInt(bytes,
                (long) offset + Bytes.LexicographicalComparerHolder.UnsafeComparer.BYTE_ARRAY_BASE_OFFSET, val);
        return offset + SIZEOF_INT;
    }

    /**
     * Convert a short value to a byte array of {@link #SIZEOF_SHORT} bytes long.
     *
     * @param val value
     * @return the byte array
     */
    public static byte[] toBytes(short val) {
        byte[] b = new byte[SIZEOF_SHORT];
        b[1] = (byte) val;
        val >>= 8;
        b[0] = (byte) val;
        return b;
    }

    /**
     * Converts a byte array to a short value
     *
     * @param bytes byte array
     * @return the short value
     */
    public static short toShort(byte[] bytes) {
        return toShort(bytes, 0, SIZEOF_SHORT);
    }

    /**
     * Converts a byte array to a short value
     *
     * @param bytes  byte array
     * @param offset offset into array
     * @return the short value
     */
    public static short toShort(byte[] bytes, int offset) {
        return toShort(bytes, offset, SIZEOF_SHORT);
    }

    /**
     * Converts a byte array to a short value
     *
     * @param bytes  byte array
     * @param offset offset into array
     * @param length length, has to be {@link #SIZEOF_SHORT}
     * @return the short value
     * @throws IllegalArgumentException if length is not {@link #SIZEOF_SHORT}
     *                                  or if there's not enough room in the array at the offset indicated.
     */
    public static short toShort(byte[] bytes, int offset, final int length) {
        if (length != SIZEOF_SHORT || offset + length > bytes.length) {
            throw explainWrongLengthOrOffset(bytes, offset, length, SIZEOF_SHORT);
        }
        if (Bytes.LexicographicalComparerHolder.UnsafeComparer.isAvailable()) {
            return toShortUnsafe(bytes, offset);
        } else {
            short n = 0;
            n ^= bytes[offset] & 0xFF;
            n <<= 8;
            n ^= bytes[offset + 1] & 0xFF;
            return n;
        }
    }

    /**
     * Returns a new byte array, copied from the given {@code buf},
     * from the position (inclusive) to the limit (exclusive).
     * The position and the other index parameters are not changed.
     *
     * @param buf a byte buffer
     * @return the byte array
     * @see #toBytes(ByteBuffer)
     */
    public static byte[] getBytes(ByteBuffer buf) {
        return readBytes(buf.duplicate());
    }

    /**
     * Put a short value out to the specified byte array position.
     *
     * @param bytes  the byte array
     * @param offset position in the array
     * @param val    short to write out
     * @return incremented offset
     * @throws IllegalArgumentException if the byte array given doesn't have
     *                                  enough room at the offset specified.
     */
    public static int putShort(byte[] bytes, int offset, short val) {
        if (bytes.length - offset < SIZEOF_SHORT) {
            throw new IllegalArgumentException("Not enough room to put a short at" + " offset " + offset + " in a "
                    + bytes.length + " byte array");
        }
        if (Bytes.LexicographicalComparerHolder.UnsafeComparer.isAvailable()) {
            return putShortUnsafe(bytes, offset, val);
        } else {
            bytes[offset + 1] = (byte) val;
            val >>= 8;
            bytes[offset] = (byte) val;
            return offset + SIZEOF_SHORT;
        }
    }

    /**
     * Put a short value out to the specified byte array position (Unsafe).
     *
     * @param bytes  the byte array
     * @param offset position in the array
     * @param val    short to write out
     * @return incremented offset
     */
    public static int putShortUnsafe(byte[] bytes, int offset, short val) {
        if (Bytes.LexicographicalComparerHolder.UnsafeComparer.littleEndian) {
            val = Short.reverseBytes(val);
        }
        Bytes.LexicographicalComparerHolder.UnsafeComparer.theUnsafe.putShort(bytes,
                (long) offset + Bytes.LexicographicalComparerHolder.UnsafeComparer.BYTE_ARRAY_BASE_OFFSET, val);
        return offset + SIZEOF_SHORT;
    }

    /**
     * Put an int value as short out to the specified byte array position. Only the lower 2 bytes of
     * the short will be put into the array. The caller of the API need to make sure they will not
     * loose the value by doing so. This is useful to store an unsigned short which is represented as
     * int in other parts.
     *
     * @param bytes  the byte array
     * @param offset position in the array
     * @param val    value to write out
     * @return incremented offset
     * @throws IllegalArgumentException if the byte array given doesn't have
     *                                  enough room at the offset specified.
     */
    public static int putAsShort(byte[] bytes, int offset, int val) {
        if (bytes.length - offset < SIZEOF_SHORT) {
            throw new IllegalArgumentException("Not enough room to put a short at" + " offset " + offset + " in a "
                    + bytes.length + " byte array");
        }
        bytes[offset + 1] = (byte) val;
        val >>= 8;
        bytes[offset] = (byte) val;
        return offset + SIZEOF_SHORT;
    }

    /**
     * Convert a BigDecimal value to a byte array
     *
     * @param val
     * @return the byte array
     */
    public static byte[] toBytes(BigDecimal val) {
        byte[] valueBytes = val.unscaledValue().toByteArray();
        byte[] result = new byte[valueBytes.length + SIZEOF_INT];
        int offset = putInt(result, 0, val.scale());
        putBytes(result, offset, valueBytes, 0, valueBytes.length);
        return result;
    }

    /**
     * Converts a byte array to a BigDecimal
     *
     * @param bytes
     * @return the char value
     */
    public static BigDecimal toBigDecimal(byte[] bytes) {
        return toBigDecimal(bytes, 0, bytes.length);
    }

    /**
     * Converts a byte array to a BigDecimal value
     *
     * @param bytes
     * @param offset
     * @param length
     * @return the char value
     */
    public static BigDecimal toBigDecimal(byte[] bytes, int offset, final int length) {
        if (bytes == null || length < SIZEOF_INT + 1 || (offset + length > bytes.length)) {
            return null;
        }

        int scale = toInt(bytes, offset);
        byte[] tcBytes = new byte[length - SIZEOF_INT];
        System.arraycopy(bytes, offset + SIZEOF_INT, tcBytes, 0, length - SIZEOF_INT);
        return new BigDecimal(new BigInteger(tcBytes), scale);
    }

    /**
     * Put a BigDecimal value out to the specified byte array position.
     *
     * @param bytes  the byte array
     * @param offset position in the array
     * @param val    BigDecimal to write out
     * @return incremented offset
     */
    public static int putBigDecimal(byte[] bytes, int offset, BigDecimal val) {
        if (bytes == null) {
            return offset;
        }

        byte[] valueBytes = val.unscaledValue().toByteArray();
        byte[] result = new byte[valueBytes.length + SIZEOF_INT];
        offset = putInt(result, offset, val.scale());
        return putBytes(result, offset, valueBytes, 0, valueBytes.length);
    }

    /**
     * @param left  left operand
     * @param right right operand
     * @return 0 if equal, < 0 if left is less than right, etc.
     */
    public static int compareTo(final byte[] left, final byte[] right) {
        return LexicographicalComparerHolder.BEST_COMPARER.compareTo(left, 0, left.length, right, 0, right.length);
    }

    /**
     * Lexicographically compare two arrays.
     *
     * @param buffer1 left operand
     * @param buffer2 right operand
     * @param offset1 Where to start comparing in the left buffer
     * @param offset2 Where to start comparing in the right buffer
     * @param length1 How much to compare from the left buffer
     * @param length2 How much to compare from the right buffer
     * @return 0 if equal, < 0 if left is less than right, etc.
     */
    public static int compareTo(byte[] buffer1, int offset1, int length1, byte[] buffer2, int offset2, int length2) {
        return LexicographicalComparerHolder.BEST_COMPARER.compareTo(buffer1, offset1, length1, buffer2, offset2,
                length2);
    }

    interface Comparer<T> {
        int compareTo(T buffer1, int offset1, int length1, T buffer2, int offset2, int length2);
    }

    static Comparer<byte[]> lexicographicalComparerJavaImpl() {
        return LexicographicalComparerHolder.PureJavaComparer.INSTANCE;
    }

    /**
     * Provides a lexicographical comparer implementation; either a Java
     * implementation or a faster implementation based on {@link sun.misc.Unsafe}.
     * <p/>
     * <p>Uses reflection to gracefully fall back to the Java implementation if
     * {@code Unsafe} isn't available.
     */
    static class LexicographicalComparerHolder {
        static final String UNSAFE_COMPARER_NAME = LexicographicalComparerHolder.class.getName() + "$UnsafeComparer";

        static final Comparer<byte[]> BEST_COMPARER = getBestComparer();

        /**
         * Returns the Unsafe-using Comparer, or falls back to the pure-Java
         * implementation if unable to do so.
         */
        static Comparer<byte[]> getBestComparer() {
            try {
                Class<?> theClass = Class.forName(UNSAFE_COMPARER_NAME);

                // yes, UnsafeComparer does implement Comparer<byte[]>
                @SuppressWarnings("unchecked")
                Comparer<byte[]> comparer = (Comparer<byte[]>) theClass.getEnumConstants()[0];
                return comparer;
            } catch (Throwable t) { // ensure we really catch *everything*
                return lexicographicalComparerJavaImpl();
            }
        }

        enum PureJavaComparer implements Comparer<byte[]> {
            INSTANCE;

            @Override
            public int compareTo(byte[] buffer1, int offset1, int length1, byte[] buffer2, int offset2, int length2) {
                // Short circuit equal case
                if (buffer1 == buffer2 && offset1 == offset2 && length1 == length2) {
                    return 0;
                }
                // Bring WritableComparator code local
                int end1 = offset1 + length1;
                int end2 = offset2 + length2;
                for (int i = offset1, j = offset2; i < end1 && j < end2; i++, j++) {
                    int a = (buffer1[i] & 0xff);
                    int b = (buffer2[j] & 0xff);
                    if (a != b) {
                        return a - b;
                    }
                }
                return length1 - length2;
            }
        }

        enum UnsafeComparer implements Comparer<byte[]> {
            INSTANCE;

            static final Unsafe theUnsafe;

            /**
             * The offset to the first element in a byte array.
             */
            static final int BYTE_ARRAY_BASE_OFFSET;

            static {
                theUnsafe = (Unsafe) AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                    try {
                        Field f = Unsafe.class.getDeclaredField("theUnsafe");
                        f.setAccessible(true);
                        return f.get(null);
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        // It doesn't matter what we throw;
                        // it's swallowed in getBestComparer().
                        throw new Error();
                    }
                });

                BYTE_ARRAY_BASE_OFFSET = theUnsafe.arrayBaseOffset(byte[].class);

                // sanity check - this should never fail
                if (theUnsafe.arrayIndexScale(byte[].class) != 1) {
                    throw new AssertionError();
                }
            }

            static final boolean littleEndian = ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN);

            /**
             * Returns true if x1 is less than x2, when both values are treated as
             * unsigned long.
             */
            static boolean lessThanUnsignedLong(long x1, long x2) {
                return (x1 + Long.MIN_VALUE) < (x2 + Long.MIN_VALUE);
            }

            /**
             * Returns true if x1 is less than x2, when both values are treated as
             * unsigned int.
             */
            static boolean lessThanUnsignedInt(int x1, int x2) {
                return (x1 & 0xffffffffL) < (x2 & 0xffffffffL);
            }

            /**
             * Returns true if x1 is less than x2, when both values are treated as
             * unsigned short.
             */
            static boolean lessThanUnsignedShort(short x1, short x2) {
                return (x1 & 0xffff) < (x2 & 0xffff);
            }

            /**
             * Checks if Unsafe is available
             *
             * @return true, if available, false - otherwise
             */
            public static boolean isAvailable() {
                return theUnsafe != null;
            }

            /**
             * Lexicographically compare two arrays.
             *
             * @param buffer1 left operand
             * @param buffer2 right operand
             * @param offset1 Where to start comparing in the left buffer
             * @param offset2 Where to start comparing in the right buffer
             * @param length1 How much to compare from the left buffer
             * @param length2 How much to compare from the right buffer
             * @return 0 if equal, < 0 if left is less than right, etc.
             */
            @Override
            public int compareTo(byte[] buffer1, int offset1, int length1, byte[] buffer2, int offset2, int length2) {

                // Short circuit equal case
                if (buffer1 == buffer2 && offset1 == offset2 && length1 == length2) {
                    return 0;
                }
                final int minLength = Math.min(length1, length2);
                final int minWords = minLength / SIZEOF_LONG;
                final long offset1Adj = offset1 + (long) BYTE_ARRAY_BASE_OFFSET;
                final long offset2Adj = offset2 + (long) BYTE_ARRAY_BASE_OFFSET;

                /*
                 * Compare 8 bytes at a time. Benchmarking shows comparing 8 bytes at a
                 * time is no slower than comparing 4 bytes at a time even on 32-bit.
                 * On the other hand, it is substantially faster on 64-bit.
                 */
                for (int i = 0; i < minWords * SIZEOF_LONG; i += SIZEOF_LONG) {
                    long lw = theUnsafe.getLong(buffer1, offset1Adj + (long) i);
                    long rw = theUnsafe.getLong(buffer2, offset2Adj + (long) i);
                    long diff = lw ^ rw;
                    if (littleEndian) {
                        lw = Long.reverseBytes(lw);
                        rw = Long.reverseBytes(rw);
                    }
                    if (diff != 0) {
                        return lessThanUnsignedLong(lw, rw) ? -1 : 1;
                    }
                }
                int offset = minWords * SIZEOF_LONG;

                if (minLength - offset >= SIZEOF_INT) {
                    int il = theUnsafe.getInt(buffer1, offset1Adj + offset);
                    int ir = theUnsafe.getInt(buffer2, offset2Adj + offset);
                    if (littleEndian) {
                        il = Integer.reverseBytes(il);
                        ir = Integer.reverseBytes(ir);
                    }
                    if (il != ir) {
                        return lessThanUnsignedInt(il, ir) ? -1 : 1;
                    }
                    offset += SIZEOF_INT;
                }
                if (minLength - offset >= SIZEOF_SHORT) {
                    short sl = theUnsafe.getShort(buffer1, offset1Adj + offset);
                    short sr = theUnsafe.getShort(buffer2, offset2Adj + offset);
                    if (littleEndian) {
                        sl = Short.reverseBytes(sl);
                        sr = Short.reverseBytes(sr);
                    }
                    if (sl != sr) {
                        return lessThanUnsignedShort(sl, sr) ? -1 : 1;
                    }
                    offset += SIZEOF_SHORT;
                }
                if (minLength - offset == 1) {
                    int a = (buffer1[(int) (offset1 + offset)] & 0xff);
                    int b = (buffer2[(int) (offset2 + offset)] & 0xff);
                    if (a != b) {
                        return a - b;
                    }
                }
                return length1 - length2;
            }
        }
    }

    /**
     * @param left  left operand
     * @param right right operand
     * @return True if equal
     */
    public static boolean equals(final byte[] left, final byte[] right) {
        // Could use Arrays.equals?
        //noinspection SimplifiableConditionalExpression
        if (left == right)
            return true;
        if (left == null || right == null)
            return false;
        if (left.length != right.length)
            return false;
        if (left.length == 0)
            return true;

        // Since we're often comparing adjacent sorted data,
        // it's usual to have equal arrays except for the very last byte
        // so check that first
        if (left[left.length - 1] != right[right.length - 1])
            return false;

        return compareTo(left, right) == 0;
    }

    public static boolean equals(final byte[] left, int leftOffset, int leftLen, final byte[] right, int rightOffset,
            int rightLen) {
        // short circuit case
        if (left == right && leftOffset == rightOffset && leftLen == rightLen) {
            return true;
        }
        // different lengths fast check
        if (leftLen != rightLen) {
            return false;
        }
        if (leftLen == 0) {
            return true;
        }

        // Since we're often comparing adjacent sorted data,
        // it's usual to have equal arrays except for the very last byte
        // so check that first
        if (left[leftOffset + leftLen - 1] != right[rightOffset + rightLen - 1])
            return false;

        return LexicographicalComparerHolder.BEST_COMPARER.compareTo(left, leftOffset, leftLen, right, rightOffset,
                rightLen) == 0;
    }

    /**
     * @param a   left operand
     * @param buf right operand
     * @return True if equal
     */
    public static boolean equals(byte[] a, ByteBuffer buf) {
        if (a == null)
            return buf == null;
        if (buf == null)
            return false;
        if (a.length != buf.remaining())
            return false;

        // Thou shalt not modify the original byte buffer in what should be read only operations.
        ByteBuffer b = buf.duplicate();
        for (byte anA : a) {
            if (anA != b.get()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Return true if the byte array on the right is a prefix of the byte
     * array on the left.
     */
    public static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes != null && prefix != null && bytes.length >= prefix.length
                && LexicographicalComparerHolder.BEST_COMPARER.compareTo(bytes, 0, prefix.length, prefix, 0,
                        prefix.length) == 0;
    }

    /**
     * @param b bytes to hash
     * @return Runs {@link org.apache.hadoop.io.WritableComparator#hashBytes(byte[], int)} on the
     * passed in array.  This method is what {@link org.apache.hadoop.io.Text} use calculating hash code.
     */
    public static int hashCode(final byte[] b) {
        return hashCode(b, b.length);
    }

    /**
     * @param b      value
     * @param length length of the value
     * @return Runs {@link org.apache.hadoop.io.WritableComparator#hashBytes(byte[], int)} on the
     * passed in array.  This method is what {@link org.apache.hadoop.io.Text} use calculating hash code.
     */
    public static int hashCode(final byte[] b, final int length) {
        return hashBytes(b, 0, length);
    }

    /** Compute hash for binary data. */
    public static int hashBytes(byte[] bytes, int offset, int length) {
        int hash = 1;
        for (int i = offset; i < offset + length; i++)
            hash = (31 * hash) + (int) bytes[i];
        return hash;
    }

    /**
     * @param b bytes to hash
     * @return A hash of <code>b</code> as an Integer that can be used as key in
     * Maps.
     */
    public static Integer mapKey(final byte[] b) {
        return hashCode(b);
    }

    /**
     * @param b      bytes to hash
     * @param length length to hash
     * @return A hash of <code>b</code> as an Integer that can be used as key in
     * Maps.
     */
    public static Integer mapKey(final byte[] b, final int length) {
        return hashCode(b, length);
    }

    /**
     * @param a lower half
     * @param b upper half
     * @return New array that has a in lower half and b in upper half.
     */
    public static byte[] add(final byte[] a, final byte[] b) {
        return add(a, b, EMPTY_BYTE_ARRAY);
    }

    /**
     * @param a first third
     * @param b second third
     * @param c third third
     * @return New array made from a, b and c
     */
    public static byte[] add(final byte[] a, final byte[] b, final byte[] c) {
        byte[] result = new byte[a.length + b.length + c.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        System.arraycopy(c, 0, result, a.length + b.length, c.length);
        return result;
    }

    /**
     * @param a      array
     * @param length amount of bytes to grab
     * @return First <code>length</code> bytes from <code>a</code>
     */
    public static byte[] head(final byte[] a, final int length) {
        if (a.length < length) {
            return null;
        }
        byte[] result = new byte[length];
        System.arraycopy(a, 0, result, 0, length);
        return result;
    }

    /**
     * @param a      array
     * @param length amount of bytes to snarf
     * @return Last <code>length</code> bytes from <code>a</code>
     */
    public static byte[] tail(final byte[] a, final int length) {
        if (a.length < length) {
            return null;
        }
        byte[] result = new byte[length];
        System.arraycopy(a, a.length - length, result, 0, length);
        return result;
    }

    /**
     * @param a      array
     * @param length new array size
     * @return Value in <code>a</code> plus <code>length</code> prepended 0 bytes
     */
    public static byte[] padHead(final byte[] a, final int length) {
        byte[] padding = new byte[length];
        for (int i = 0; i < length; i++) {
            padding[i] = 0;
        }
        return add(padding, a);
    }

    /**
     * @param a      array
     * @param length new array size
     * @return Value in <code>a</code> plus <code>length</code> appended 0 bytes
     */
    public static byte[] padTail(final byte[] a, final int length) {
        byte[] padding = new byte[length];
        for (int i = 0; i < length; i++) {
            padding[i] = 0;
        }
        return add(a, padding);
    }

    /**
     * Split passed range.  Expensive operation relatively.  Uses BigInteger math.
     * Useful splitting ranges for MapReduce jobs.
     *
     * @param a   Beginning of range
     * @param b   End of range
     * @param num Number of times to split range.  Pass 1 if you want to split
     *            the range in two; i.e. one split.
     * @return Array of dividing values
     */
    public static byte[][] split(final byte[] a, final byte[] b, final int num) {
        return split(a, b, false, num);
    }

    /**
     * Split passed range.  Expensive operation relatively.  Uses BigInteger math.
     * Useful splitting ranges for MapReduce jobs.
     *
     * @param a         Beginning of range
     * @param b         End of range
     * @param inclusive Whether the end of range is prefix-inclusive or is
     *                  considered an exclusive boundary.  Automatic splits are generally exclusive
     *                  and manual splits with an explicit range utilize an inclusive end of range.
     * @param num       Number of times to split range.  Pass 1 if you want to split
     *                  the range in two; i.e. one split.
     * @return Array of dividing values
     */
    public static byte[][] split(final byte[] a, final byte[] b, boolean inclusive, final int num) {
        byte[][] ret = new byte[num + 2][];
        int i = 0;
        Iterable<byte[]> iter = iterateOnSplits(a, b, inclusive, num);
        if (iter == null)
            return null;
        for (byte[] elem : iter) {
            ret[i++] = elem;
        }
        return ret;
    }

    /**
     * Iterate over keys within the passed range, splitting at an [a,b) boundary.
     */
    public static Iterable<byte[]> iterateOnSplits(final byte[] a, final byte[] b, final int num) {
        return iterateOnSplits(a, b, false, num);
    }

    /**
     * Iterate over keys within the passed range.
     */
    public static Iterable<byte[]> iterateOnSplits(final byte[] a, final byte[] b, boolean inclusive, final int num) {
        byte[] aPadded;
        byte[] bPadded;
        if (a.length < b.length) {
            aPadded = padTail(a, b.length - a.length);
            bPadded = b;
        } else if (b.length < a.length) {
            aPadded = a;
            bPadded = padTail(b, a.length - b.length);
        } else {
            aPadded = a;
            bPadded = b;
        }
        if (compareTo(aPadded, bPadded) >= 0) {
            throw new IllegalArgumentException("b <= a");
        }
        if (num <= 0) {
            throw new IllegalArgumentException("num cannot be <= 0");
        }
        byte[] prependHeader = { 1, 0 };
        final BigInteger startBI = new BigInteger(add(prependHeader, aPadded));
        final BigInteger stopBI = new BigInteger(add(prependHeader, bPadded));
        BigInteger diffBI = stopBI.subtract(startBI);
        if (inclusive) {
            diffBI = diffBI.add(BigInteger.ONE);
        }
        final BigInteger splitsBI = BigInteger.valueOf(num + 1L);
        if (diffBI.compareTo(splitsBI) < 0) {
            return null;
        }
        final BigInteger intervalBI;
        try {
            intervalBI = diffBI.divide(splitsBI);
        } catch (Exception e) {
            LOG.error("Exception caught during division", e);
            return null;
        }

        final Iterator<byte[]> iterator = new Iterator<byte[]>() {
            private int i = -1;

            @Override
            public boolean hasNext() {
                return i < num + 1;
            }

            @Override
            public byte[] next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                i++;
                if (i == 0)
                    return a;
                if (i == num + 1)
                    return b;

                BigInteger curBI = startBI.add(intervalBI.multiply(BigInteger.valueOf(i)));
                byte[] padded = curBI.toByteArray();
                if (padded[1] == 0)
                    padded = tail(padded, padded.length - 2);
                else
                    padded = tail(padded, padded.length - 1);
                return padded;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        };

        return new Iterable<byte[]>() {
            @Override
            public Iterator<byte[]> iterator() {
                return iterator;
            }
        };
    }

    /**
     * @param bytes  array to hash
     * @param offset offset to start from
     * @param length length to hash
     */
    public static int hashCode(byte[] bytes, int offset, int length) {
        int hash = 1;
        for (int i = offset; i < offset + length; i++)
            hash = (31 * hash) + (int) bytes[i];
        return hash;
    }

    /**
     * @param t operands
     * @return Array of byte arrays made from passed array of Text
     */
    public static byte[][] toByteArrays(final String[] t) {
        byte[][] result = new byte[t.length][];
        for (int i = 0; i < t.length; i++) {
            result[i] = Bytes.toBytes(t[i]);
        }
        return result;
    }

    /**
     * @param column operand
     * @return A byte array of a byte array where first and only entry is
     * <code>column</code>
     */
    public static byte[][] toByteArrays(final String column) {
        return toByteArrays(toBytes(column));
    }

    /**
     * @param column operand
     * @return A byte array of a byte array where first and only entry is
     * <code>column</code>
     */
    public static byte[][] toByteArrays(final byte[] column) {
        byte[][] result = new byte[1][];
        result[0] = column;
        return result;
    }

    /**
     * Bytewise binary increment/deincrement of long contained in byte array
     * on given amount.
     *
     * @param value  - array of bytes containing long (length <= SIZEOF_LONG)
     * @param amount value will be incremented on (deincremented if negative)
     * @return array of bytes containing incremented long (length == SIZEOF_LONG)
     */
    public static byte[] incrementBytes(byte[] value, long amount) {
        byte[] val = value;
        if (val.length < SIZEOF_LONG) {
            // Hopefully this doesn't happen too often.
            byte[] newvalue;
            if (val[0] < 0) {
                newvalue = new byte[] { -1, -1, -1, -1, -1, -1, -1, -1 };
            } else {
                newvalue = new byte[SIZEOF_LONG];
            }
            System.arraycopy(val, 0, newvalue, newvalue.length - val.length, val.length);
            val = newvalue;
        } else if (val.length > SIZEOF_LONG) {
            throw new IllegalArgumentException("Increment Bytes - value too big: " + val.length);
        }
        if (amount == 0)
            return val;
        if (val[0] < 0) {
            return binaryIncrementNeg(val, amount);
        }
        return binaryIncrementPos(val, amount);
    }

    /* increment/deincrement for positive value */
    private static byte[] binaryIncrementPos(byte[] value, long amount) {
        long amo = amount;
        int sign = 1;
        if (amount < 0) {
            amo = -amount;
            sign = -1;
        }
        for (int i = 0; i < value.length; i++) {
            int cur = ((int) amo % 256) * sign;
            amo = (amo >> 8);
            int val = value[value.length - i - 1] & 0x0ff;
            int total = val + cur;
            if (total > 255) {
                amo += sign;
                total %= 256;
            } else if (total < 0) {
                amo -= sign;
            }
            value[value.length - i - 1] = (byte) total;
            if (amo == 0)
                return value;
        }
        return value;
    }

    /* increment/deincrement for negative value */
    private static byte[] binaryIncrementNeg(byte[] value, long amount) {
        long amo = amount;
        int sign = 1;
        if (amount < 0) {
            amo = -amount;
            sign = -1;
        }
        for (int i = 0; i < value.length; i++) {
            int cur = ((int) amo % 256) * sign;
            amo = (amo >> 8);
            int val = ((~value[value.length - i - 1]) & 0x0ff) + 1;
            int total = cur - val;
            if (total >= 0) {
                amo += sign;
            } else if (total < -256) {
                amo -= sign;
                total %= 256;
            }
            value[value.length - i - 1] = (byte) total;
            if (amo == 0)
                return value;
        }
        return value;
    }

    /**
     * Writes a string as a fixed-size field, padded with zeros.
     */
    public static void writeStringFixedSize(final DataOutput out, String s, int size) throws IOException {
        byte[] b = toBytes(s);
        if (b.length > size) {
            throw new IOException("Trying to write " + b.length + " bytes (" + toStringBinary(b)
                    + ") into a field of length " + size);
        }

        out.writeBytes(s);
        for (int i = 0; i < size - s.length(); ++i)
            out.writeByte(0);
    }

    /**
     * Reads a fixed-size field and interprets it as a string padded with zeros.
     */
    public static String readStringFixedSize(final DataInput in, int size) throws IOException {
        byte[] b = new byte[size];
        in.readFully(b);
        int n = b.length;
        while (n > 0 && b[n - 1] == 0)
            --n;

        return toString(b, 0, n);
    }

    /**
     * Copy the byte array given in parameter and return an instance
     * of a new byte array with the same length and the same content.
     *
     * @param bytes the byte array to duplicate
     * @return a copy of the given byte array
     */
    public static byte[] copy(byte[] bytes) {
        if (bytes == null)
            return null;
        byte[] result = new byte[bytes.length];
        System.arraycopy(bytes, 0, result, 0, bytes.length);
        return result;
    }

    /**
     * Copy the byte array given in parameter and return an instance
     * of a new byte array with the same length and the same content.
     *
     * @param bytes  the byte array to copy from
     * @param offset
     * @param length
     * @return a copy of the given designated byte array
     */
    public static byte[] copy(byte[] bytes, final int offset, final int length) {
        if (bytes == null)
            return null;
        byte[] result = new byte[length];
        System.arraycopy(bytes, offset, result, 0, length);
        return result;
    }

    /**
     * Search sorted array "a" for byte "key". I can't remember if I wrote this or copied it from
     * somewhere. (mcorgan)
     *
     * @param a         Array to search. Entries must be sorted and unique.
     * @param fromIndex First index inclusive of "a" to include in the search.
     * @param toIndex   Last index exclusive of "a" to include in the search.
     * @param key       The byte to search for.
     * @return The index of key if found. If not found, return -(index + 1), where negative indicates
     * "not found" and the "index + 1" handles the "-0" case.
     */
    public static int unsignedBinarySearch(byte[] a, int fromIndex, int toIndex, byte key) {
        int unsignedKey = key & 0xff;
        int low = fromIndex;
        int high = toIndex - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = a[mid] & 0xff;

            if (midVal < unsignedKey) {
                low = mid + 1;
            } else if (midVal > unsignedKey) {
                high = mid - 1;
            } else {
                return mid; // key found
            }
        }
        return -(low + 1); // key not found.
    }

    /**
     * Treat the byte[] as an unsigned series of bytes, most significant bits first.  Start by adding
     * 1 to the rightmost bit/byte and carry over all overflows to the more significant bits/bytes.
     *
     * @param input The byte[] to increment.
     * @return The incremented copy of "in".  May be same length or 1 byte longer.
     */
    public static byte[] unsignedCopyAndIncrement(final byte[] input) {
        byte[] copy = copy(input);
        if (copy == null) {
            throw new IllegalArgumentException("cannot increment null array");
        }
        for (int i = copy.length - 1; i >= 0; --i) {
            if (copy[i] == -1) {// -1 is all 1-bits, which is the unsigned maximum
                copy[i] = 0;
            } else {
                ++copy[i];
                return copy;
            }
        }
        // we maxed out the array
        byte[] out = new byte[copy.length + 1];
        out[0] = 1;
        System.arraycopy(copy, 0, out, 1, copy.length);
        return out;
    }

    public static boolean equals(List<byte[]> a, List<byte[]> b) {
        if (a == null) {
            if (b == null) {
                return true;
            }
            return false;
        }
        if (b == null) {
            return false;
        }
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); ++i) {
            if (!Bytes.equals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the index of the first appearance of the value {@code target} in
     * {@code array}.
     *
     * @param array  an array of {@code byte} values, possibly empty
     * @param target a primitive {@code byte} value
     * @return the least index {@code i} for which {@code array[i] == target}, or
     * {@code -1} if no such index exists.
     */
    public static int indexOf(byte[] array, byte target) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == target) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the start position of the first occurrence of the specified {@code
     * target} within {@code array}, or {@code -1} if there is no such occurrence.
     * <p/>
     * <p>More formally, returns the lowest index {@code i} such that {@code
     * java.util.Arrays.copyOfRange(array, i, i + target.length)} contains exactly
     * the same elements as {@code target}.
     *
     * @param array  the array to search for the sequence {@code target}
     * @param target the array to search for as a sub-sequence of {@code array}
     */
    public static int indexOf(byte[] array, byte[] target) {
        checkNotNull(array, "array");
        checkNotNull(target, "target");
        if (target.length == 0) {
            return 0;
        }

        outer: for (int i = 0; i < array.length - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /**
     * @param array  an array of {@code byte} values, possibly empty
     * @param target a primitive {@code byte} value
     * @return {@code true} if {@code target} is present as an element anywhere in {@code array}.
     */
    public static boolean contains(byte[] array, byte target) {
        return indexOf(array, target) > -1;
    }

    /**
     * @param array  an array of {@code byte} values, possibly empty
     * @param target an array of {@code byte}
     * @return {@code true} if {@code target} is present anywhere in {@code array}
     */
    public static boolean contains(byte[] array, byte[] target) {
        return indexOf(array, target) > -1;
    }

    /**
     * Fill given array with zeros.
     *
     * @param b array which needs to be filled with zeros
     */
    public static void zero(byte[] b) {
        zero(b, 0, b.length);
    }

    /**
     * Fill given array with zeros at the specified position.
     *
     * @param b
     * @param offset
     * @param length
     */
    public static void zero(byte[] b, int offset, int length) {
        checkPositionIndex(offset, b.length, "offset");
        checkArgument(length > 0, "length must be greater than 0");
        checkPositionIndex(offset + length, b.length, "offset + length");
        Arrays.fill(b, offset, offset + length, (byte) 0);
    }

    private static final SecureRandom RNG = new SecureRandom();

    /**
     * Fill given array with random bytes.
     *
     * @param b array which needs to be filled with random bytes
     */
    public static void random(byte[] b) {
        RNG.nextBytes(b);
    }

    /**
     * Fill given array with random bytes at the specified position.
     *
     * @param b
     * @param offset
     * @param length
     */
    public static void random(byte[] b, int offset, int length) {
        checkPositionIndex(offset, b.length, "offset");
        checkArgument(length > 0, "length must be greater than 0");
        checkPositionIndex(offset + length, b.length, "offset + length");
        byte[] buf = new byte[length];
        RNG.nextBytes(buf);
        System.arraycopy(buf, 0, b, offset, length);
    }

    /**
     * Create a max byte array with the specified max byte count
     *
     * @param maxByteCount the length of returned byte array
     * @return the created max byte array
     */
    public static byte[] createMaxByteArray(int maxByteCount) {
        byte[] maxByteArray = new byte[maxByteCount];
        for (int i = 0; i < maxByteArray.length; i++) {
            maxByteArray[i] = (byte) 0xff;
        }
        return maxByteArray;
    }

    /**
     * Create a byte array which is multiple given bytes
     *
     * @param srcBytes
     * @param multiNum
     * @return byte array
     */
    public static byte[] multiple(byte[] srcBytes, int multiNum) {
        if (multiNum <= 0) {
            return new byte[0];
        }
        byte[] result = new byte[srcBytes.length * multiNum];
        for (int i = 0; i < multiNum; i++) {
            System.arraycopy(srcBytes, 0, result, i * srcBytes.length, srcBytes.length);
        }
        return result;
    }

    /**
     * Convert a byte array into a hex string
     *
     * @param b
     */
    public static String toHex(byte[] b) {
        checkArgument(b.length > 0, "length must be greater than 0");
        return String.format(Locale.ROOT, "%x", new BigInteger(1, b));
    }

    /**
     * Create a byte array from a string of hash digits. The length of the
     * string must be a multiple of 2
     *
     * @param hex
     */
    public static byte[] fromHex(String hex) {
        checkArgument(hex.length() > 0, "length must be greater than 0");
        checkArgument(hex.length() % 2 == 0, "length must be a multiple of 2");
        // Make sure letters are upper case
        hex = hex.toUpperCase(Locale.ROOT);
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) ((toBinaryFromHex((byte) hex.charAt(2 * i)) << 4)
                    + (toBinaryFromHex((byte) hex.charAt((2 * i + 1))) & 0xff));
        }
        return b;
    }

}
