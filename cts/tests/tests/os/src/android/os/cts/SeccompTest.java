/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.MemoryFile;
import android.os.SystemClock;
import android.os.Build;
import android.util.Log;
import android.test.AndroidTestCase;

import com.android.compatibility.common.util.CpuFeatures;
import com.google.common.util.concurrent.AbstractFuture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Date;

public class SeccompTest extends AndroidTestCase {
    final static String TAG = "SeccompTest";

    static {
        System.loadLibrary("ctsos_jni");
    }

    // As this test validates a kernel system call interface, if the CTS tests
    // were built for ARM but are running on an x86 CPU, the system call numbers
    // will not be correct, so skip those tests.
    private boolean isRunningUnderEmulatedAbi() {
        final String primaryAbi = Build.SUPPORTED_ABIS[0];
        return (CpuFeatures.isArmCpu() || CpuFeatures.isArm64Cpu()) &&
               !(primaryAbi.equals("armeabi-v7a") || primaryAbi.equals("arm64-v8a"));
    }

    public void testSeccomp() {
        if (OSFeatures.needsSeccompSupport()) {
            assertTrue("Please enable seccomp support "
                       + "in your kernel (CONFIG_SECCOMP_FILTER=y)",
                       OSFeatures.hasSeccompSupport());
        }
    }

    public void testKernelBasicTests() {
        if (!OSFeatures.needsSeccompSupport())
            return;

        if (isRunningUnderEmulatedAbi()) {
            Log.d(TAG, "Skipping test running under an emulated ABI");
            return;
        }

        final String[] tests = {
            /* "global.mode_strict_support", // all Android processes already have seccomp filter */
            /* "global.mode_strict_cannot_call_prctl", // all Android processes already have seccomp
             * filter */
            "global.no_new_privs_support",
            "global.mode_filter_support",
            /* "global.mode_filter_without_nnp", // all Android processes already have nnp */
            "global.filter_size_limits",
            "global.filter_chain_limits",
            "global.mode_filter_cannot_move_to_strict",
            /* "global.mode_filter_get_seccomp", // all Android processes already have seccomp
             * filter */
            "global.ALLOW_all",
            "global.empty_prog",
            "global.unknown_ret_is_kill_inside",
            "global.unknown_ret_is_kill_above_allow",
            "global.KILL_all",
            "global.KILL_one",
            "global.KILL_one_arg_one",
            "global.KILL_one_arg_six",
            "global.arg_out_of_range",
            "global.ERRNO_valid",
            "global.ERRNO_zero",
            /* "global.ERRNO_capped", // presently fails */
        };
        runKernelUnitTestSuite(tests);
    }

    public void testKernelTrapTests() {
        if (!OSFeatures.needsSeccompSupport())
            return;

        final String[] tests = {
            "TRAP.dfl",
            "TRAP.ign",
            "TRAP.handler",
        };
        runKernelUnitTestSuite(tests);
    }

    public void testKernelPrecedenceTests() {
        if (!OSFeatures.needsSeccompSupport())
            return;

        final String[] tests = {
            "precedence.allow_ok",
            "precedence.kill_is_highest",
            "precedence.kill_is_highest_in_any_order",
            "precedence.trap_is_second",
            "precedence.trap_is_second_in_any_order",
            "precedence.errno_is_third",
            "precedence.errno_is_third_in_any_order",
            "precedence.trace_is_fourth",
            "precedence.trace_is_fourth_in_any_order",
        };
        runKernelUnitTestSuite(tests);
    }

    /* // The SECCOMP_RET_TRACE does not work under Android Arm32.
    public void testKernelTraceTests() {
        if (!OSFeatures.needsSeccompSupport())
            return;

        final String[] tests = {
            "TRACE_poke.read_has_side_effects",
            "TRACE_poke.getpid_runs_normally",
            "TRACE_syscall.syscall_allowed",
            "TRACE_syscall.syscall_redirected",
            "TRACE_syscall.syscall_dropped",
        };
        runKernelUnitTestSuite(tests);
    }
    */

    public void testKernelTSYNCTests() {
        if (!OSFeatures.needsSeccompSupport())
            return;

        if (isRunningUnderEmulatedAbi()) {
            Log.d(TAG, "Skipping test running under an emulated ABI");
            return;
        }

        final String[] tests = {
            "global.seccomp_syscall",
            "global.seccomp_syscall_mode_lock",
            "global.TSYNC_first",
            "TSYNC.siblings_fail_prctl",
            "TSYNC.two_siblings_with_ancestor",
            /* "TSYNC.two_sibling_want_nnp", // all Android processes already have nnp */
            "TSYNC.two_siblings_with_no_filter",
            "TSYNC.two_siblings_with_one_divergence",
            "TSYNC.two_siblings_not_under_filter",
            /* "global.syscall_restart", // ptrace attach fails */
        };
        runKernelUnitTestSuite(tests);
    }

    /**
     * Runs a kernel unit test suite (an array of kernel test names).
     */
    private void runKernelUnitTestSuite(final String[] tests) {
        for (final String test : tests) {
            // TODO: Replace the URL with the documentation when it's finished.
            assertTrue(test + " failed. This test requires kernel functionality to pass. Please go to "
                       + "http://source.android.com/devices/tech/config/kernel.html#Seccomp-BPF-TSYNC"
                       + " for instructions on how to enable or backport the required functionality.",
                       runKernelUnitTest(test));
        }
    }

    /**
     * Integration test for seccomp-bpf policy applied to an isolatedProcess=true
     * service. This will perform various operations in an isolated process under a
     * fairly restrictive seccomp policy.
     */
    public void testIsolatedServicePolicy() throws InterruptedException, ExecutionException,
           RemoteException {
        if (!OSFeatures.needsSeccompSupport())
            return;

        if (isRunningUnderEmulatedAbi()) {
            Log.d(TAG, "Skipping test running under an emulated ABI");
            return;
        }

        final IsolatedServiceConnection peer = new IsolatedServiceConnection();
        final Intent intent = new Intent(getContext(), IsolatedService.class);
        assertTrue(getContext().bindService(intent, peer, Context.BIND_AUTO_CREATE));

        final ISeccompIsolatedService service = peer.get();

        // installFilter() must be called first, to set the seccomp policy.
        assertTrue(service.installFilter());
        assertTrue(service.createThread());
        assertTrue(service.getSystemInfo());
        doFileWriteTest(service);
        assertTrue(service.openAshmem());
        assertTrue(service.openDevFile());

        getContext().unbindService(peer);
    }

    /**
     * Integration test for seccomp-bpf policy with isolatedProcess, where the
     * process then violates the policy and gets killed by the kernel.
     */
    public void testViolateIsolatedServicePolicy() throws InterruptedException,
           ExecutionException, RemoteException {
        if (!OSFeatures.needsSeccompSupport())
            return;

        if (isRunningUnderEmulatedAbi()) {
            Log.d(TAG, "Skipping test running under an emulated ABI");
            return;
        }

        final IsolatedServiceConnection peer = new IsolatedServiceConnection();
        final Intent intent = new Intent(getContext(), IsolatedService.class);
        assertTrue(getContext().bindService(intent, peer, Context.BIND_AUTO_CREATE));

        final ISeccompIsolatedService service = peer.get();

        assertTrue(service.installFilter());
        boolean gotRemoteException = false;
        try {
            service.violatePolicy();
        } catch (RemoteException e) {
            gotRemoteException = true;
        }
        assertTrue(gotRemoteException);

        getContext().unbindService(peer);
    }

    private void doFileWriteTest(ISeccompIsolatedService service) throws RemoteException {
        final String fileName = "seccomp_test";
        ParcelFileDescriptor fd = null;
        try {
            FileOutputStream fOut = getContext().openFileOutput(fileName, 0);
            fd = ParcelFileDescriptor.dup(fOut.getFD());
            fOut.close();
        } catch (FileNotFoundException e) {
            fail(e.getMessage());
            return;
        } catch (IOException e) {
            fail(e.getMessage());
            return;
        }

        assertTrue(service.writeToFile(fd));

        try {
            FileInputStream fIn = getContext().openFileInput(fileName);
            assertEquals('!', fIn.read());
            fIn.close();
        } catch (FileNotFoundException e) {
            fail(e.getMessage());
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    class IsolatedServiceConnection extends AbstractFuture<ISeccompIsolatedService>
            implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            set(ISeccompIsolatedService.Stub.asInterface(service));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

        @Override
        public ISeccompIsolatedService get() throws InterruptedException, ExecutionException {
            try {
                return get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class IsolatedService extends Service {
        private final ISeccompIsolatedService.Stub mService = new ISeccompIsolatedService.Stub() {
            public boolean installFilter() {
                return installTestFilter(getAssets());
            }

            public boolean createThread() {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                        }
                    }
                });
                thread.run();
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    return false;
                }
                return true;
            }

            public boolean getSystemInfo() {
                long uptimeMillis = SystemClock.uptimeMillis();
                if (uptimeMillis < 1) {
                    Log.d(TAG, "SystemClock failed");
                    return false;
                }

                String version = Build.VERSION.CODENAME;
                if (version.length() == 0) {
                    Log.d(TAG, "Build.VERSION failed");
                    return false;
                }

                long time = (new Date()).getTime();
                if (time < 100) {
                    Log.d(TAG, "getTime failed");
                    return false;
                }

                return true;
            }

            public boolean writeToFile(ParcelFileDescriptor fd) {
                FileOutputStream fOut = new FileOutputStream(fd.getFileDescriptor());
                try {
                    fOut.write('!');
                    fOut.close();
                } catch (IOException e) {
                    return false;
                }
                return true;
            }

            public boolean openAshmem() {
                byte[] buffer = {'h', 'e', 'l', 'l', 'o'};
                try {
                    MemoryFile file = new MemoryFile("seccomp_isolated_test", 32);
                    file.writeBytes(buffer, 0, 0, buffer.length);
                    file.close();
                    return true;
                } catch (IOException e) {
                    return false;
                }
            }

            public boolean openDevFile() {
                try {
                    FileInputStream fIn = new FileInputStream("/dev/zero");
                    boolean succeed = fIn.read() == 0;
                    succeed &= fIn.read() == 0;
                    succeed &= fIn.read() == 0;
                    fIn.close();
                    return succeed;
                } catch (FileNotFoundException e) {
                    return false;
                } catch (IOException e) {
                    return false;
                }
            }

            public void violatePolicy() {
                getClockBootTime();
            }
        };

        @Override
        public IBinder onBind(Intent intent) {
            return mService;
        }
    }

    /**
     * Loads an architecture-specific policy file from the AssetManager and
     * installs it using Minijail.
     */
    private static boolean installTestFilter(final AssetManager assets) {
        final String arch = getPolicyAbiString();
        if (arch == null) {
            throw new RuntimeException("Unsupported architecture/ABI");
        }

        try {
            // Create a pipe onto which we will write the composite Seccomp policy.
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            final ParcelFileDescriptor.AutoCloseOutputStream outputStream =
                    new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);

            // The policy files to concat together.
            final AssetFileDescriptor[] policyFiles = {
                assets.openFd("minijail/isolated-" + arch + ".policy"),
                assets.openFd("minijail/isolated-common.policy"),
                arch.equals("i386") ? null : assets.openFd("minijail/isolated-common-not-i386.policy"),
            };

            // Convert our PID to ASCII byte string.
            final byte[] myPidBytes = Integer.toString(Process.myPid()).getBytes();

            // Concatenate all the policyFiles together on the pipe.
            final byte[] buffer = new byte[2048];
            for (AssetFileDescriptor policyFile : policyFiles) {
                if (policyFile == null)
                    continue;

                final FileInputStream policyStream = policyFile.createInputStream();
                while (true) {
                    int bytesRead = policyStream.read(buffer);
                    if (bytesRead == -1)
                        break;

                    // Replace the literal '$' with our PID. This allows us to lock down
                    // certain syscalls that take a pid/tgid.
                    for (int i = 0; i < bytesRead; i++) {
                        if (buffer[i] == '$') {
                            outputStream.write(myPidBytes);
                        } else {
                            outputStream.write(buffer[i]);
                        }
                    }
                }
                policyStream.close();
            }
            outputStream.close();

            return nativeInstallTestFilter(pipe[0].detachFd());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load policy file", e);
        }
    }

    /**
     * Returns the architecture name policy file substring.
     */
    private static native String getPolicyAbiString();

    /**
     * Runs the seccomp_bpf_unittest of the given name.
     */
    private native boolean runKernelUnitTest(final String name);

    /**
     * Installs a Minijail seccomp policy from a FD. This takes ownership of
     * the FD and closes it.
     */
    private native static boolean nativeInstallTestFilter(int policyFd);

    /**
     * Attempts to get the CLOCK_BOOTTIME, which is a violation of the
     * policy specified by installTestFilter().
     */
    private native static int getClockBootTime();
}
