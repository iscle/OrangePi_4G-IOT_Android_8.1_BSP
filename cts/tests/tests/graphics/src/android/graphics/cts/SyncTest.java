/*
 * Copyright 2017 The Android Open Source Project
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

package android.graphics.cts;

import static org.junit.Assert.assertEquals;
import static android.opengl.EGL14.*;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.support.test.filters.SmallTest;

import java.util.concurrent.CyclicBarrier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;


// This class contains tests for the Linux kernel sync file system, and the NDK interfaces for it
// (android/sync.h). Unfortunately, the interfaces exposed by the kernel make it difficult to test
// for a couple reasons:
//
// (a) There isn't a standard kernel interface for creating a fence/sync_file. Drivers can create
//     them via driver-specific interfaces. Currently this means we have to use APIs like OpenGL ES
//     or interact with the system compositor in order to generate fences. That makes tests larger
//     and more complicated than they otherwise need to be.
//
//     This is further complicated by the fact that most of the time GPU work executes in the order
//     it was submitted to the kernel; there isn't much out-of-order execution in practice. So
//     detecting some kinds of bugs is difficult using only the GPU as an event source.
//
// (b) A core principal of sync files is that they cannot be created until the work that will
//     signal them has been submitted to the kernel, and will complete without further action from
//     userland. This means that it is impossible to reliably do something before a sync file has
//     signaled.

@SmallTest
@RunWith(BlockJUnit4ClassRunner.class)
public class SyncTest {

    static {
        System.loadLibrary("ctsgraphics_jni");
    }

    private static final String TAG = SyncTest.class.getSimpleName();
    private static final boolean DEBUG = false;

    private EGLDisplay mEglDisplay = EGL_NO_DISPLAY;
    private EGLConfig mEglConfig = null;

    @Before
    public void setup() throws Throwable {
        mEglDisplay = EGL14.eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (mEglDisplay == EGL_NO_DISPLAY) {
            throw new RuntimeException("no EGL display");
        }
        int[] major = new int[1];
        int[] minor = new int[1];
        if (!EGL14.eglInitialize(mEglDisplay, major, 0, minor, 0)) {
            throw new RuntimeException("error in eglInitialize");
        }

        int[] numConfigs = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        if (!EGL14.eglChooseConfig(mEglDisplay,
                new int[] {
                    EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                    EGL_BUFFER_SIZE, 32,
                    EGL_NONE},
                0, configs, 0, 1, numConfigs, 0)) {
            throw new RuntimeException("eglChooseConfig failed");
        }
        mEglConfig = configs[0];
    }

    @After
    public void teardown() throws Throwable {
        EGL14.eglTerminate(mEglDisplay);
    }

    @Test
    public void testMergedSyncSignalOrder() {
        // TODO
    }

    private static final int STATUS_UNSIGNALED = 0;
    private static final int STATUS_SIGNALED = 1;
    private static final int STATUS_ERROR = -1;
    private static class SyncFileInfo {
        String name;            // char name[32]
        int status;             // __s32 status
        long flags;             // __u32 flags
        SyncFenceInfo[] fences; // __u32 num_fences; __u64 sync_fence_info
    }
    private static class SyncFenceInfo {
        String name;            // char obj_name[32]
        String driver_name;     // char driver_name[32]
        int status;             // __s32 status
        long flags;             // __u32 flags
        long timestamp_ns;      // __u64 timestamp_ns
    }

    private static native boolean nSyncPoll(int[] fds, int[] status);
    private static native int nSyncMerge(String name, int fd1, int fd2);
    private static native SyncFileInfo nSyncFileInfo(int fd);
    private static native void nSyncClose(int fd);
}
