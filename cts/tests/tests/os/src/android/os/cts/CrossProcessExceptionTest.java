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

import android.app.ActivityManager;
import android.app.AuthenticationRequiredException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.test.AndroidTestCase;

import com.google.common.util.concurrent.AbstractFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CrossProcessExceptionTest extends AndroidTestCase {

    private Intent greenIntent;
    private PeerConnection greenConn;
    private IBinder green;

    public static class PeerConnection extends AbstractFuture<IBinder>
            implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            set(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

        @Override
        public IBinder get() throws InterruptedException, ExecutionException {
            try {
                return get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Context context = getContext();

        // Bring up both remote processes and wire them to each other
        greenIntent = new Intent();
        greenIntent.setComponent(new ComponentName(
                "android.os.cts", "android.os.cts.CrossProcessExceptionService"));
        greenConn = new PeerConnection();
        context.startService(greenIntent);
        getContext().bindService(greenIntent, greenConn, 0);
        green = greenConn.get();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        final Context context = getContext();
        context.unbindService(greenConn);
        context.stopService(greenIntent);

        final ActivityManager am = (ActivityManager) mContext.getSystemService(
                Context.ACTIVITY_SERVICE);
        am.killBackgroundProcesses(context.getPackageName());
    }

    public void testNone() throws Exception {
        doCommand("none");
    }

    public void testSE() throws Exception {
        try {
            doCommand("SE");
            fail("Missing SecurityException!");
        } catch (SecurityException expected) {
        }
    }

    public void testARE() throws Exception {
        try {
            doCommand("ARE");
            fail("Missing SecurityException!");
        } catch (SecurityException e) {
            if (e instanceof AuthenticationRequiredException) {
                final AuthenticationRequiredException are = (AuthenticationRequiredException) e;
                assertEquals("FNFE", are.getMessage());
                assertEquals(android.os.Process.myUid(), are.getUserAction().getCreatorUid());
            } else {
                fail("Odd, expected ARE but found " + e);
            }
        }
    }

    public void testRE() throws Exception {
        doCommand("RE");
    }

    /**
     * Verify that we only support custom exceptions that are defined in the
     * base class-path.
     */
    public void testCustom() throws Exception {
        try {
            doCommand("custom");
            fail("Missing IllegalArgumentException!");
        } catch (IllegalArgumentException e) {
            if (e.getClass() != IllegalArgumentException.class) {
                fail("Unexpected subclass " + e.getClass());
            }
            assertEquals("LOCAL", e.getMessage());
        }
    }

    private void doCommand(String cmd) throws Exception {
        final ParcelFileDescriptor[] fds = ParcelFileDescriptor.createPipe();
        try {
            green.dump(fds[0].getFileDescriptor(), new String[] { cmd });
        } finally {
            fds[0].close();
            fds[1].close();
        }
    }
}
