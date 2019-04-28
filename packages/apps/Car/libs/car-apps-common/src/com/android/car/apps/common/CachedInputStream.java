/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.car.apps.common;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A replacement of BufferedInputStream (no multiple thread): <p>
 * - use list of byte array (chunks) instead of keep growing a single byte array (more efficent)
 *   <br>
 * - support overriding the markLimit passed in mark() call (The value that BitmapFactory
 *   uses 1024 is too small for detecting bitmap bounds and reset()) <br>
 * @hide
 */
public class CachedInputStream extends FilterInputStream {

    private static final int CHUNK_SIZE = ByteArrayPool.CHUNK16K;

    private ArrayList<byte[]> mBufs = new ArrayList<byte[]>();
    private int mPos = 0;  // current read position inside the chunk buffers
    private int mCount = 0; // total validate bytes in chunk buffers
    private int mMarkPos = -1; // marked read position in chunk buffers
    private int mOverrideMarkLimit; // to override readlimit of mark() call
    private int mMarkLimit; // effective marklimit
    private byte[] tmp = new byte[1]; // tmp buffer used in read()

    public CachedInputStream(InputStream in) {
        super(in);
    }

    @Override
    public boolean markSupported() {
        return true;
    }

    /**
     * set the value that will override small readlimit passed in mark() call.
     */
    public void setOverrideMarkLimit(int overrideMarkLimit) {
        mOverrideMarkLimit = overrideMarkLimit;
    }

    public int getOverrideMarkLimit() {
        return mOverrideMarkLimit;
    }

    @Override
    public void mark(int readlimit) {
        readlimit = readlimit < mOverrideMarkLimit ? mOverrideMarkLimit : readlimit;
        if (mMarkPos >= 0) {
            // for replacing existing mark(), discard anything before mPos
            // and move mMarkPos to mPos
            int chunks = mPos / CHUNK_SIZE;
            if (chunks > 0) {
                // trim the header buffers
                int removedBytes = chunks * CHUNK_SIZE;
                List<byte[]> subList = mBufs.subList(0, chunks);
                releaseChunks(subList);
                subList.clear();
                mPos = mPos - removedBytes;
                mCount = mCount - removedBytes;
            }
        }
        mMarkPos = mPos;
        mMarkLimit = readlimit;
    }

    @Override
    public void reset() throws IOException {
        if (mMarkPos < 0) {
            throw new IOException("mark has been invalidated");
        }
        mPos = mMarkPos;
    }

    @Override
    public int read() throws IOException {
        // TODO, not efficient, but the function is not called by BitmapFactory
        int r = read(tmp, 0, 1);
        if (r <= 0) {
            return -1;
        }
        return tmp[0] & 0xFF;
    }

    @Override
    public void close() throws IOException {
        if (in!=null) {
            in.close();
            in = null;
        }
        releaseChunks(mBufs);
    }

    private static void releaseChunks(List<byte[]> bufs) {
        ByteArrayPool.get16KBPool().releaseChunks(bufs);
    }

    private byte[] allocateChunk() {
        return ByteArrayPool.get16KBPool().allocateChunk();
    }

    private boolean invalidate() {
        if (mCount - mMarkPos > mMarkLimit) {
            mMarkPos = -1;
            mCount = 0;
            mPos = 0;
            releaseChunks(mBufs);
            mBufs.clear();
            return true;
        }
        return false;
    }

    @Override
    public int read(byte[] buffer, int offset, int count) throws IOException {
        if (in == null) {
            throw streamClosed();
        }
        if (mMarkPos == -1) {
            int reads = in.read(buffer, offset, count);
            return reads;
        }
        if (count == 0) {
            return 0;
        }
        int copied = copyMarkedBuffer(buffer, offset, count);
        count -= copied;
        offset += copied;
        int totalReads = copied;
        while (count > 0) {
            if (mPos == mBufs.size() * CHUNK_SIZE) {
                mBufs.add(allocateChunk());
            }
            int currentBuf = mPos / CHUNK_SIZE;
            int indexInBuf = mPos - currentBuf * CHUNK_SIZE;
            byte[] buf = mBufs.get(currentBuf);
            int end = (currentBuf + 1) * CHUNK_SIZE;
            int leftInBuffer = end - mPos;
            int toRead = count > leftInBuffer ? leftInBuffer : count;
            int reads = in.read(buf, indexInBuf, toRead);
            if (reads > 0) {
                System.arraycopy(buf, indexInBuf, buffer, offset, reads);
                mPos += reads;
                mCount += reads;
                totalReads += reads;
                offset += reads;
                count -= reads;
                if (invalidate()) {
                    reads = in.read(buffer, offset, count);
                    if (reads >0 ) {
                        totalReads += reads;
                    }
                    break;
                }
            } else {
                break;
            }
        }
        if (totalReads == 0) {
            return -1;
        }
        return totalReads;
    }

    private int copyMarkedBuffer(byte[] buffer, int offset, int read) {
        int totalRead = 0;
        while (read > 0 && mPos < mCount) {
            int currentBuf = mPos / CHUNK_SIZE;
            int indexInBuf = mPos - currentBuf * CHUNK_SIZE;
            byte[] buf = mBufs.get(currentBuf);
            int end = (currentBuf + 1) * CHUNK_SIZE;
            if (end > mCount) {
                end = mCount;
            }
            int leftInBuffer = end - mPos;
            int toRead = read > leftInBuffer ? leftInBuffer : read;
            System.arraycopy(buf, indexInBuf, buffer, offset, toRead);
            offset += toRead;
            read -= toRead;
            totalRead += toRead;
            mPos += toRead;
        }
        return totalRead;
    }

    @Override
    public int available() throws IOException {
        if (in == null) {
            throw streamClosed();
        }
        return mCount - mPos + in.available();
    }

    @Override
    public long skip(long byteCount) throws IOException {
        if (in == null) {
            throw streamClosed();
        }
        if (mMarkPos <0) {
            return in.skip(byteCount);
        }
        long totalSkip = 0;
        totalSkip = mCount - mPos;
        if (totalSkip > byteCount) {
            totalSkip = byteCount;
        }
        mPos += totalSkip;
        byteCount -= totalSkip;
        while (byteCount > 0) {
            if (mPos == mBufs.size() * CHUNK_SIZE) {
                mBufs.add(allocateChunk());
            }
            int currentBuf = mPos / CHUNK_SIZE;
            int indexInBuf = mPos - currentBuf * CHUNK_SIZE;
            byte[] buf = mBufs.get(currentBuf);
            int end = (currentBuf + 1) * CHUNK_SIZE;
            int leftInBuffer = end - mPos;
            int toRead = (int) (byteCount > leftInBuffer ? leftInBuffer : byteCount);
            int reads = in.read(buf, indexInBuf, toRead);
            if (reads > 0) {
                mPos += reads;
                mCount += reads;
                byteCount -= reads;
                totalSkip += reads;
                if (invalidate()) {
                    if (byteCount > 0) {
                        totalSkip += in.skip(byteCount);
                    }
                    break;
                }
            } else {
                break;
            }
        }
        return totalSkip;
    }

    private static IOException streamClosed() {
        return new IOException("stream closed");
    }

}
