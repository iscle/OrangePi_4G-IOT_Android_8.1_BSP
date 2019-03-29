/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.telecom.cts;

import com.android.compatibility.common.util.ApiLevelUtil;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class TestUtils {
    static final String TAG = "TelecomCTSTests";
    static final boolean HAS_TELECOM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    static final long WAIT_FOR_STATE_CHANGE_TIMEOUT_MS = 10000;
    static final long WAIT_FOR_CALL_ADDED_TIMEOUT_S = 15;
    static final long WAIT_FOR_STATE_CHANGE_TIMEOUT_CALLBACK = 50;

    // Non-final to allow modification by tests not in this package (e.g. permission-related
    // tests in the Telecom2 test package.
    public static String PACKAGE = "android.telecom.cts";
    public static final String COMPONENT = "android.telecom.cts.CtsConnectionService";
    public static final String SELF_MANAGED_COMPONENT =
            "android.telecom.cts.CtsSelfManagedConnectionService";
    public static final String REMOTE_COMPONENT = "android.telecom.cts.CtsRemoteConnectionService";
    public static final String ACCOUNT_ID = "xtstest_CALL_PROVIDER_ID";
    public static final PhoneAccountHandle TEST_PHONE_ACCOUNT_HANDLE =
            new PhoneAccountHandle(new ComponentName(PACKAGE, COMPONENT), ACCOUNT_ID);
    public static final String REMOTE_ACCOUNT_ID = "xtstest_REMOTE_CALL_PROVIDER_ID";
    public static final String SELF_MANAGED_ACCOUNT_ID_1 = "ctstest_SELF_MANAGED_ID_1";
    public static final PhoneAccountHandle TEST_SELF_MANAGED_HANDLE_1 =
            new PhoneAccountHandle(new ComponentName(PACKAGE, SELF_MANAGED_COMPONENT),
                    SELF_MANAGED_ACCOUNT_ID_1);
    public static final String SELF_MANAGED_ACCOUNT_ID_2 = "ctstest_SELF_MANAGED_ID_2";
    public static final PhoneAccountHandle TEST_SELF_MANAGED_HANDLE_2 =
            new PhoneAccountHandle(new ComponentName(PACKAGE, SELF_MANAGED_COMPONENT),
                    SELF_MANAGED_ACCOUNT_ID_2);

    public static final String ACCOUNT_LABEL = "CTSConnectionService";
    public static final PhoneAccount TEST_PHONE_ACCOUNT = PhoneAccount.builder(
            TEST_PHONE_ACCOUNT_HANDLE, ACCOUNT_LABEL)
            .setAddress(Uri.parse("tel:555-TEST"))
            .setSubscriptionAddress(Uri.parse("tel:555-TEST"))
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                    PhoneAccount.CAPABILITY_VIDEO_CALLING |
                    PhoneAccount.CAPABILITY_RTT |
                    PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
            .setHighlightColor(Color.RED)
            .setShortDescription(ACCOUNT_LABEL)
            .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
            .addSupportedUriScheme(PhoneAccount.SCHEME_VOICEMAIL)
            .build();
    public static final String REMOTE_ACCOUNT_LABEL = "CTSRemoteConnectionService";
    public static final String SELF_MANAGED_ACCOUNT_LABEL = "android.telecom.cts";
    public static final PhoneAccount TEST_SELF_MANAGED_PHONE_ACCOUNT_2 = PhoneAccount.builder(
            TEST_SELF_MANAGED_HANDLE_2, SELF_MANAGED_ACCOUNT_LABEL)
            .setAddress(Uri.parse("sip:test@test.com"))
            .setSubscriptionAddress(Uri.parse("sip:test@test.com"))
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED |
                    PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING |
                    PhoneAccount.CAPABILITY_VIDEO_CALLING)
            .setHighlightColor(Color.BLUE)
            .setShortDescription(SELF_MANAGED_ACCOUNT_LABEL)
            .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
            .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
            .build();
    public static final PhoneAccount TEST_SELF_MANAGED_PHONE_ACCOUNT_1 = PhoneAccount.builder(
            TEST_SELF_MANAGED_HANDLE_1, SELF_MANAGED_ACCOUNT_LABEL)
            .setAddress(Uri.parse("sip:test@test.com"))
            .setSubscriptionAddress(Uri.parse("sip:test@test.com"))
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED |
                    PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING |
                    PhoneAccount.CAPABILITY_VIDEO_CALLING)
            .setHighlightColor(Color.BLUE)
            .setShortDescription(SELF_MANAGED_ACCOUNT_LABEL)
            .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
            .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
            .build();

    private static final String COMMAND_SET_DEFAULT_DIALER = "telecom set-default-dialer ";

    private static final String COMMAND_GET_DEFAULT_DIALER = "telecom get-default-dialer";

    private static final String COMMAND_GET_SYSTEM_DIALER = "telecom get-system-dialer";

    private static final String COMMAND_ENABLE = "telecom set-phone-account-enabled ";

    private static final String COMMAND_REGISTER_SIM = "telecom register-sim-phone-account ";

    private static final String COMMAND_WAIT_ON_HANDLERS = "telecom wait-on-handlers";

    public static final String MERGE_CALLER_NAME = "calls-merged";
    public static final String SWAP_CALLER_NAME = "calls-swapped";
    private static final String PRIMARY_USER_SN = "0";

    public static boolean shouldTestTelecom(Context context) {
        if (!HAS_TELECOM) {
            return false;
        }
        final PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) &&
                pm.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE);
    }

    public static String setDefaultDialer(Instrumentation instrumentation, String packageName)
            throws Exception {
        return executeShellCommand(instrumentation, COMMAND_SET_DEFAULT_DIALER + packageName);
    }

    public static String getDefaultDialer(Instrumentation instrumentation) throws Exception {
        return executeShellCommand(instrumentation, COMMAND_GET_DEFAULT_DIALER);
    }

    public static String getSystemDialer(Instrumentation instrumentation) throws Exception {
        return executeShellCommand(instrumentation, COMMAND_GET_SYSTEM_DIALER);
    }

    public static void enablePhoneAccount(Instrumentation instrumentation,
            PhoneAccountHandle handle) throws Exception {
        final ComponentName component = handle.getComponentName();
        executeShellCommand(instrumentation, COMMAND_ENABLE
                + component.getPackageName() + "/" + component.getClassName() + " "
                + handle.getId() + " " + PRIMARY_USER_SN);
    }

    public static void registerSimPhoneAccount(Instrumentation instrumentation,
            PhoneAccountHandle handle, String label, String address) throws Exception {
        final ComponentName component = handle.getComponentName();
        executeShellCommand(instrumentation, COMMAND_REGISTER_SIM
                + component.getPackageName() + "/" + component.getClassName() + " "
                + handle.getId() + " " + PRIMARY_USER_SN + " " + label + " " + address);
    }

    public static void waitOnAllHandlers(Instrumentation instrumentation) throws Exception {
        executeShellCommand(instrumentation, COMMAND_WAIT_ON_HANDLERS);
    }

    public static void waitOnLocalMainLooper(long timeoutMs) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        final CountDownLatch lock = new CountDownLatch(1);
        mainHandler.post(lock::countDown);
        while (lock.getCount() > 0) {
            try {
                lock.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
    }

    /**
     * Executes the given shell command and returns the output in a string. Note that even
     * if we don't care about the output, we have to read the stream completely to make the
     * command execute.
     */
    public static String executeShellCommand(Instrumentation instrumentation,
            String command) throws Exception {
        final ParcelFileDescriptor pfd =
                instrumentation.getUiAutomation().executeShellCommand(command);
        BufferedReader br = null;
        try (InputStream in = new FileInputStream(pfd.getFileDescriptor())) {
            br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String str = null;
            StringBuilder out = new StringBuilder();
            while ((str = br.readLine()) != null) {
                out.append(str);
            }
            return out.toString();
        } finally {
            if (br != null) {
                closeQuietly(br);
            }
            closeQuietly(pfd);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    public static CountDownLatch waitForLock(CountDownLatch lock) {
        boolean success;
        try {
            if (lock == null) {
                return null;
            }
            success = lock.await(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            return null;
        }

        if (success) {
            return new CountDownLatch(1);
        } else {
            return null;
        }
    }

    /**
     * Adds a new incoming call.
     *
     * @param instrumentation the Instrumentation, used for shell command execution.
     * @param telecomManager the TelecomManager.
     * @param handle the PhoneAccountHandle associated with the call.
     * @param address the incoming address.
     * @return the new self-managed incoming call.
     */
    public static void addIncomingCall(Instrumentation instrumentation,
                                       TelecomManager telecomManager, PhoneAccountHandle handle,
                                       Uri address) {

        // Inform telecom of new incoming self-managed connection.
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, address);
        telecomManager.addNewIncomingCall(handle, extras);

        // Wait for Telecom to finish creating the new connection.
        try {
            waitOnAllHandlers(instrumentation);
        } catch (Exception e) {
            TestCase.fail("Failed to wait on handlers");
        }
    }

    /**
     * Places a new outgoing call.
     *
     * @param telecomManager the TelecomManager.
     * @param handle the PhoneAccountHandle associated with the call.
     * @param address outgoing call address.
     * @return the new self-managed outgoing call.
     */
    public static void placeOutgoingCall(Instrumentation instrumentation,
                                          TelecomManager telecomManager, PhoneAccountHandle handle,
                                          Uri address) {
        // Inform telecom of new incoming self-managed connection.
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
        telecomManager.placeCall(address, extras);

        // Wait for Telecom to finish creating the new connection.
        try {
            waitOnAllHandlers(instrumentation);
        } catch (Exception e) {
            TestCase.fail("Failed to wait on handlers");
        }
    }

    /**
     * Waits for a new SelfManagedConnection with the given address to be added.
     * @param address The expected address.
     * @return The SelfManagedConnection found.
     */
    public static SelfManagedConnection waitForAndGetConnection(Uri address) {
        // Wait for creation of the new connection.
        CtsSelfManagedConnectionService connectionService =
                CtsSelfManagedConnectionService.getConnectionService();
        TestCase.assertTrue(connectionService.waitForUpdate(
                CtsSelfManagedConnectionService.CONNECTION_CREATED_LOCK));

        Optional<SelfManagedConnection> connectionOptional = connectionService.getConnections()
                .stream()
                .filter(connection -> address.equals(connection.getAddress()))
                .findFirst();
        assert(connectionOptional.isPresent());
        return connectionOptional.get();
    }

    /**
     * Utility class used to track the number of times a callback was invoked, and the arguments it
     * was invoked with. This class is prefixed Invoke rather than the more typical Call for
     * disambiguation purposes.
     */
    public static final class InvokeCounter {
        private final String mName;
        private final Object mLock = new Object();
        private final ArrayList<Object[]> mInvokeArgs = new ArrayList<>();

        private int mInvokeCount;

        public InvokeCounter(String callbackName) {
            mName = callbackName;
        }

        public void invoke(Object... args) {
            synchronized (mLock) {
                mInvokeCount++;
                mInvokeArgs.add(args);
                mLock.notifyAll();
            }
        }

        public Object[] getArgs(int index) {
            synchronized (mLock) {
                return mInvokeArgs.get(index);
            }
        }

        public int getInvokeCount() {
            synchronized (mLock) {
                return mInvokeCount;
            }
        }

        public void waitForCount(int count) {
            waitForCount(count, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        }

        public void waitForCount(int count, long timeoutMillis) {
            waitForCount(count, timeoutMillis, null);
        }

        public void waitForCount(long timeoutMillis) {
             synchronized (mLock) {
             try {
                  mLock.wait(timeoutMillis);
             }catch (InterruptedException ex) {
                  ex.printStackTrace();
             }
           }
        }

        public void waitForCount(int count, long timeoutMillis, String message) {
            synchronized (mLock) {
                final long startTimeMillis = SystemClock.uptimeMillis();
                while (mInvokeCount < count) {
                    try {
                        final long elapsedTimeMillis = SystemClock.uptimeMillis() - startTimeMillis;
                        final long remainingTimeMillis = timeoutMillis - elapsedTimeMillis;
                        if (remainingTimeMillis <= 0) {
                            if (message != null) {
                                TestCase.fail(message);
                            } else {
                                TestCase.fail(String.format("Expected %s to be called %d times.",
                                        mName, count));
                            }
                        }
                        mLock.wait(timeoutMillis);
                    } catch (InterruptedException ie) {
                        /* ignore */
                    }
                }
            }
        }

        /**
         * Waits for a predicate to return {@code true} within the specified timeout.  Uses the
         * {@link #mLock} for this {@link InvokeCounter} to eliminate the need to perform busy-wait.
         * @param predicate The predicate.
         * @param timeoutMillis The timeout.
         */
        public void waitForPredicate(Predicate predicate, long timeoutMillis) {
            synchronized (mLock) {
                mInvokeArgs.clear();
                long startTimeMillis = SystemClock.uptimeMillis();
                long elapsedTimeMillis = 0;
                long remainingTimeMillis = timeoutMillis;
                Object foundValue = null;
                boolean wasFound = false;
                do {
                    try {
                        mLock.wait(timeoutMillis);
                        foundValue = (mInvokeArgs.get(mInvokeArgs.size()-1))[0];
                        wasFound = predicate.test(foundValue);
                        elapsedTimeMillis = SystemClock.uptimeMillis() - startTimeMillis;
                        remainingTimeMillis = timeoutMillis - elapsedTimeMillis;
                    } catch (InterruptedException ie) {
                        /* ignore */
                    }
                } while (!wasFound && remainingTimeMillis > 0);
                if (wasFound) {
                    return;
                } else if (remainingTimeMillis <= 0) {
                    TestCase.fail("Expected value not found within time limit");
                }
            }
        }
    }
}
