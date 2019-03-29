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

package android.os.cts;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SharedMemory;
import android.system.ErrnoException;

import java.nio.ByteBuffer;

public class SharedMemoryService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return new SharedMemoryServiceImpl();
    }

    private static class SharedMemoryServiceImpl extends ISharedMemoryService.Stub {
        private SharedMemory mSharedMemory;
        private ByteBuffer mMappedBuffer;

        @Override
        public void setup(SharedMemory memory, int prot) throws RemoteException {
            mSharedMemory = memory;
            try {
                mMappedBuffer = mSharedMemory.map(prot, 0, mSharedMemory.getSize());
            } catch (ErrnoException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public byte read(int index) throws RemoteException {
            // Although we expect only one client we need to insert memory barriers to ensure
            // visibility
            synchronized (mMappedBuffer) {
                return mMappedBuffer.get(index);
            }
        }

        @Override
        public void write(int index, byte value) throws RemoteException {
            // Although we expect only one client we need to insert memory barriers to ensure
            // visibility
            synchronized (mMappedBuffer) {
                mMappedBuffer.put(index, value);
            }
        }
    }
}
