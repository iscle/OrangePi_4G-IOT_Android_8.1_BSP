/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.location.cts.asn1.base;

/**
 * Reads a stream of bits.
 *
 * <p>This class is not thread-safe.
 * 
 */
public class BitStreamReader {
  private static final int BITS_IN_BYTE = 8;

  private final byte[] buffer;
  private int position = 0;
  private int bitsRead = 0;

  public BitStreamReader(byte[] bytes) {
    buffer = bytes;
  }

  /**
   * Returns true if the next bit in the stream is set.
   * @throws IndexOutOfBoundsException if there is no more data.
   */
  public boolean readBit() {
    bitsRead++;
    if (bitsRead > BITS_IN_BYTE) {
      position++;
      bitsRead = 1;
    }
    return ((buffer[position] >> (BITS_IN_BYTE - bitsRead)) & 1) == 1;
  }

  /**
   * Returns true if there is another readable bit in the stream.
   */
  public boolean hasBit() {
    return position + 1 < buffer.length
           || (bitsRead < BITS_IN_BYTE && position < buffer.length);
  }

  public void spoolToByteBoundary() {
    if (bitsRead == 0) {
      return;
    }
    bitsRead = 0;
    position++;
  }

  /**
   * Returns next byte's worth of data (8 bits) from the stream.
   * @throws IndexOutOfBoundsException if there is no more data.
   */
  public byte readByte() {
    int mask = (1 << (8 - bitsRead)) - 1;
    byte result = (byte) ((buffer[position] & mask) << bitsRead);
    position++;
    if (bitsRead > 0) {
      result = (byte) (result | (buffer[position] & 0xFF) >>> (8 - bitsRead));
    }
    return result;
  }

  /**
   * Returns next {@code howMany} bits as the low bits in the returned byte.
   * @throws IndexOutOfBoundsException if there is no more data.
   */
  public int readLowBits(int howMany) {
    int result = 0;
    for (int i = 0; i < howMany; i++) {
      result <<= 1;
      result |= (readBit() ? 1 : 0);
    }
    return result;
  }
}
