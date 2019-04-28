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

import java.util.ArrayList;
import java.util.List;

/**
 * @hide
 */
public final class ByteArrayPool {

    public static final int CHUNK16K = 16 * 1024;
    public static final int DEFAULT_MAX_NUM = 8;

    private final static ByteArrayPool sChunk16K = new ByteArrayPool(CHUNK16K, DEFAULT_MAX_NUM);

    private final ArrayList<byte[]> mCachedBuf;
    private final int mChunkSize;
    private final int mMaxNum;

    private ByteArrayPool(int chunkSize, int maxNum) {
        mChunkSize = chunkSize;
        mMaxNum = maxNum;
        mCachedBuf = new ArrayList<byte[]>(mMaxNum);
    }

    /**
     * get singleton of 16KB byte[] pool
     */
    public static ByteArrayPool get16KBPool() {
        return sChunk16K;
    }

    public byte[] allocateChunk() {
        synchronized (mCachedBuf) {
            int size = mCachedBuf.size();
            if (size > 0) {
                return mCachedBuf.remove(size - 1);
            }
            return new byte[mChunkSize];
        }
    }

    public void clear() {
        synchronized (mCachedBuf) {
            mCachedBuf.clear();
        }
    }

    public void releaseChunk(byte[] buf) {
        if (buf == null || buf.length != mChunkSize) {
            return;
        }
        synchronized (mCachedBuf) {
            if (mCachedBuf.size() < mMaxNum) {
                mCachedBuf.add(buf);
            }
        }
    }

    public void releaseChunks(List<byte[]> bufs) {
        synchronized (mCachedBuf) {
            for (int i = 0, c = bufs.size(); i < c; i++) {
                if (mCachedBuf.size() == mMaxNum) {
                    break;
                }
                byte[] buf = bufs.get(i);
                if (buf != null && buf.length == mChunkSize) {
                    mCachedBuf.add(bufs.get(i));
                }
            }
        }
    }

}
