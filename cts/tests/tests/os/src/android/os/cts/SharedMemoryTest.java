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

import static android.support.test.InstrumentationRegistry.getContext;
import static android.system.OsConstants.PROT_READ;
import static android.system.OsConstants.PROT_WRITE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SharedMemory;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.system.ErrnoException;
import android.system.OsConstants;

import com.google.common.util.concurrent.AbstractFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class SharedMemoryTest {

    static {
        System.loadLibrary("ctsos_jni");
    }

    private Instrumentation mInstrumentation;
    private Intent mRemoteIntent;
    private PeerConnection mRemoteConnection;
    private ISharedMemoryService mRemote;

    public static class PeerConnection extends AbstractFuture<ISharedMemoryService>
            implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            set(ISharedMemoryService.Stub.asInterface(service));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

        @Override
        public ISharedMemoryService get() throws InterruptedException, ExecutionException {
            try {
                return get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = mInstrumentation.getContext();
        // Bring up both remote processes and wire them to each other
        mRemoteIntent = new Intent();
        mRemoteIntent.setComponent(new ComponentName(
                "android.os.cts", "android.os.cts.SharedMemoryService"));
        mRemoteConnection = new PeerConnection();
        getContext().bindService(mRemoteIntent, mRemoteConnection,
                Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
        mRemote = mRemoteConnection.get();
    }

    @After
    public void tearDown() {
        final Context context = mInstrumentation.getContext();
        context.unbindService(mRemoteConnection);
    }

    @Test
    public void testReadWrite() throws RemoteException, ErrnoException {
        try (SharedMemory sharedMemory = SharedMemory.create(null, 1)) {
            ByteBuffer buffer = sharedMemory.mapReadWrite();
            mRemote.setup(sharedMemory, PROT_READ | PROT_WRITE);

            byte expected = 5;
            buffer.put(0, expected);
            assertEquals(expected, buffer.get(0));
            // Memory barrier
            synchronized (sharedMemory) {}
            assertEquals(expected, mRemote.read(0));
            expected = 10;
            mRemote.write(0, expected);
            // Memory barrier
            synchronized (sharedMemory) {}
            assertEquals(expected, buffer.get(0));
            SharedMemory.unmap(buffer);
        }
    }

    @Test
    public void testReadOnly() throws RemoteException, ErrnoException {
        try (SharedMemory sharedMemory = SharedMemory.create(null, 1)) {
            ByteBuffer buffer = sharedMemory.mapReadWrite();
            sharedMemory.setProtect(PROT_READ);
            mRemote.setup(sharedMemory, PROT_READ);

            byte expected = 15;
            buffer.put(0, expected);
            assertEquals(expected, buffer.get(0));
            // Memory barrier
            synchronized (sharedMemory) {}
            assertEquals(expected, mRemote.read(0));
            expected = 20;
            try {
                mRemote.write(0, expected);
                fail("write shouldn't have worked, should be read only");
            } catch (Exception e) {}

            buffer.put(0, expected);
            assertEquals(expected, buffer.get(0));
            // Memory barrier
            synchronized (sharedMemory) {}
            assertEquals(expected, mRemote.read(0));
        }
    }

    @Test
    public void testUseAfterClose() throws RemoteException, ErrnoException {
        ByteBuffer buffer;
        try (SharedMemory sharedMemory = SharedMemory.create(null, 1)) {
            buffer = sharedMemory.mapReadWrite();
            mRemote.setup(sharedMemory, PROT_READ | PROT_WRITE);
        }
        byte expected = 5;
        buffer.put(0, expected);
        assertEquals(expected, buffer.get(0));
        // Memory barrier
        synchronized (buffer) {}
        assertEquals(expected, mRemote.read(0));
        expected = 10;
        mRemote.write(0, expected);
        // Memory barrier
        synchronized (buffer) {}
        assertEquals(expected, buffer.get(0));
        SharedMemory.unmap(buffer);
    }

    @Test
    public void testUseAfterUnmap() throws RemoteException, ErrnoException {
        SharedMemory sharedMemory = SharedMemory.create(null, 1);
        ByteBuffer buffer = sharedMemory.mapReadWrite();
        byte expected = 5;
        buffer.put(0, expected);
        assertEquals(expected, buffer.get(0));
        SharedMemory.unmap(buffer);
        boolean failed = false;
        try {
            buffer.get(0);
            failed = true;
        } catch (Throwable t) { }
        assertFalse(failed);
    }

    private static native boolean nWriteByte(SharedMemory memory, int index, byte value);

    @Test
    public void testNdkInterop() throws ErrnoException {
        SharedMemory sharedMemory = SharedMemory.create("hello", 1024);
        ByteBuffer buffer = sharedMemory.mapReadWrite();
        assertEquals(0, buffer.get(0));
        assertTrue(nWriteByte(sharedMemory, 0, (byte) 1));
        assertEquals(1, buffer.get(0));
        sharedMemory.close();
        buffer.put(0, (byte) 5);
        assertFalse(nWriteByte(sharedMemory, 0, (byte) 2));
        assertEquals(5, buffer.get(0));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidCreate() throws ErrnoException {
        SharedMemory.create(null, -1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidMapProt() throws ErrnoException {
        try (SharedMemory sharedMemory = SharedMemory.create(null, 1)) {
            sharedMemory.map(-1, 0, 1);
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidSetProt() throws ErrnoException {
        try (SharedMemory sharedMemory = SharedMemory.create(null, 1)) {
            sharedMemory.setProtect(-1);
        }
    }

    @Test
    public void testSetProtAddProt() throws ErrnoException {
        try (SharedMemory sharedMemory = SharedMemory.create(null, 1)) {
            assertTrue(sharedMemory.setProtect(OsConstants.PROT_READ));
            assertTrue(sharedMemory.setProtect(OsConstants.PROT_READ));
            assertFalse(sharedMemory.setProtect(OsConstants.PROT_READ | OsConstants.PROT_WRITE));
            assertTrue(sharedMemory.setProtect(OsConstants.PROT_NONE));
            assertFalse(sharedMemory.setProtect(OsConstants.PROT_READ));
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testMapAfterClose() throws ErrnoException {
        SharedMemory sharedMemory = SharedMemory.create(null, 1);
        sharedMemory.close();
        sharedMemory.mapReadWrite();
    }

    @Test(expected=ReadOnlyBufferException.class)
    public void testWriteToReadOnly() throws ErrnoException {
        try (SharedMemory sharedMemory = SharedMemory.create(null, 1)) {
            sharedMemory.setProtect(PROT_READ);
            ByteBuffer buffer = null;
            try {
                buffer = sharedMemory.mapReadWrite();
                fail("Should have thrown an exception");
            } catch (ErrnoException ex) {
                assertEquals(OsConstants.EPERM, ex.errno);
            }
            buffer = sharedMemory.mapReadOnly();
            assertTrue(buffer.isReadOnly());
            buffer.put(0, (byte) 0);
        }
    }
}
