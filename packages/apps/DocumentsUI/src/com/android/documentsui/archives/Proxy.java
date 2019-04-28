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

package com.android.documentsui.archives;

import android.os.ProxyFileDescriptorCallback;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;
import android.util.jar.StrictJarFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;

import libcore.io.IoUtils;

/**
 * Provides a backend for a seekable file descriptors for files in archives.
 */
public class Proxy extends ProxyFileDescriptorCallback {
    private final StrictJarFile mFile;
    private final ZipEntry mEntry;
    private InputStream mInputStream = null;
    private long mOffset = 0;

    Proxy(StrictJarFile file, ZipEntry entry) throws IOException {
        mFile = file;
        mEntry = entry;
        recreateInputStream();
    }

    @Override
    public long onGetSize() throws ErrnoException {
        return mEntry.getSize();
    }

    @Override
    public int onRead(long offset, int size, byte[] data) throws ErrnoException {
        // TODO: Add a ring buffer to prevent expensive seeks.
        if (offset < mOffset) {
            try {
                recreateInputStream();
            } catch (IOException e) {
                throw new ErrnoException("onRead", OsConstants.EIO);
            }
        }

        while (mOffset < offset) {
            try {
                mOffset +=  mInputStream.skip(offset - mOffset);
            } catch (IOException e) {
                throw new ErrnoException("onRead", OsConstants.EIO);
            }
        }

        int remainingSize = size;
        while (remainingSize > 0) {
            try {
                int bytes = mInputStream.read(data, size - remainingSize, remainingSize);
                if (bytes <= 0) {
                    return size - remainingSize;
                }
                remainingSize -= bytes;
                mOffset += bytes;
            } catch (IOException e) {
                throw new ErrnoException("onRead", OsConstants.EIO);
            }
        }

        return size - remainingSize;
   }

    @Override public void onRelease() {
        IoUtils.closeQuietly(mInputStream);
    }

    private void recreateInputStream() throws IOException {
        IoUtils.closeQuietly(mInputStream);
        mInputStream = mFile.getInputStream(mEntry);
        mOffset = 0;
    }
}
