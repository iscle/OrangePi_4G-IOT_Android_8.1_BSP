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

package android.content.pm.cts;

import static android.content.pm.PackageInfo.INSTALL_LOCATION_AUTO;
import static android.content.pm.PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY;
import static android.content.pm.PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL;
import static android.content.pm.PackageInfo.INSTALL_LOCATION_UNSPECIFIED;
import static android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL;
import static android.content.pm.PackageInstaller.SessionParams.MODE_INHERIT_EXISTING;
import static android.content.pm.PackageManager.INSTALL_REASON_DEVICE_RESTORE;
import static android.content.pm.PackageManager.INSTALL_REASON_DEVICE_SETUP;
import static android.content.pm.PackageManager.INSTALL_REASON_POLICY;
import static android.content.pm.PackageManager.INSTALL_REASON_UNKNOWN;
import static android.content.pm.PackageManager.INSTALL_REASON_USER;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageInstaller.SessionParams;
import android.graphics.Bitmap;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@RunWith(Parameterized.class)
public class InstallSessionParamsUnitTest {
    private static final String LOG_TAG = InstallSessionParamsUnitTest.class.getSimpleName();
    private static Optional UNSET = new Optional(false, null);

    @Parameterized.Parameter(0)
    public Optional<Integer> mode;
    @Parameterized.Parameter(1)
    public Optional<Integer> installLocation;
    @Parameterized.Parameter(2)
    public Optional<Integer> size;
    @Parameterized.Parameter(3)
    public Optional<String> appPackageName;
    @Parameterized.Parameter(4)
    public Optional<Bitmap> appIcon;
    @Parameterized.Parameter(5)
    public Optional<String> appLabel;
    @Parameterized.Parameter(6)
    public Optional<Uri> originatingUri;
    @Parameterized.Parameter(7)
    public Optional<Integer> originatingUid;
    @Parameterized.Parameter(8)
    public Optional<Uri> referredUri;
    @Parameterized.Parameter(9)
    public Optional<Integer> installReason;
    @Parameterized.Parameter(10)
    public boolean expectFailure;

    /**
     * Generate test-parameters where all params are the same, but one param cycles through all
     * values.
     */
    private static ArrayList<Object[]> getSingleParameterChangingTests(
            Object[][][] allParameterValues, int changingParameterIndex,
            Object[] changingParameterValues, boolean expectFailure) {
        ArrayList<Object[]> params = new ArrayList<>();

        for (Object changingParameterValue : changingParameterValues) {
            ArrayList<Object> singleTestParams = new ArrayList<>();

            // parameterIndex is the index of the parameter (0 = mode, ...)
            for (int parameterIndex = 0; parameterIndex < allParameterValues.length;
                    parameterIndex++) {
                Object[][] parameterValues = allParameterValues[parameterIndex];

                if (parameterIndex == changingParameterIndex) {
                    if (changingParameterValue == UNSET) {
                        // No need to wrap UNSET again
                        singleTestParams.add(UNSET);
                    } else {
                        singleTestParams.add(Optional.of(changingParameterValue));
                    }
                } else {
                    singleTestParams.add(Optional.of(parameterValues[0][0]));
                }
            }
            singleTestParams.add(expectFailure);
            params.add(singleTestParams.toArray());
        }

        return params;
    }

    /**
     * Generate test-parameters for all tests.
     */
    @Parameterized.Parameters
    public static Collection<Object[]> getParameters() {
        // {{{valid parameters}, {invalid parameters}}}
        Object[][][] allParameterValues = {
         /*mode*/
                {{MODE_FULL_INSTALL, MODE_INHERIT_EXISTING}, {0xfff}},
         /*installLocation*/
                {{INSTALL_LOCATION_UNSPECIFIED, INSTALL_LOCATION_AUTO,
                        INSTALL_LOCATION_INTERNAL_ONLY, INSTALL_LOCATION_PREFER_EXTERNAL,
                        /* parame is not verified */ 0xfff}, {}},
         /*size*/
                {{1, 8092, Integer.MAX_VALUE, /* parame is not verified */ -1, 0}, {}},
         /*appPackageName*/
                {{"a.package.name", null, /* param is not verified */ "android"}, {}},
         /*appIcon*/
                {{null, Bitmap.createBitmap(42, 42, Bitmap.Config.ARGB_8888)}, {}},
         /*appLabel*/
                {{"A label", null}, {}},
         /*originatingUri*/
                {{Uri.parse("android.com"), null}, {}},
         /*originatingUid*/
                {{-1, 0, 1}, {}},
         /*referredUri*/
                {{Uri.parse("android.com"), null}, {}},
         /*installReason*/
                {{INSTALL_REASON_UNKNOWN, INSTALL_REASON_POLICY, INSTALL_REASON_DEVICE_RESTORE,
                        INSTALL_REASON_DEVICE_SETUP, INSTALL_REASON_USER,
                        /* parame is not verified */ 0xfff}, {}}};

        ArrayList<Object[]> allTestParams = new ArrayList<>();

        // changingParameterIndex is the index the parameter that changes (0 = mode ...)
        for (int changingParameterIndex = 0; changingParameterIndex < allParameterValues.length;
                changingParameterIndex++) {
            // Allowed values
            allTestParams.addAll(getSingleParameterChangingTests(allParameterValues,
                    changingParameterIndex, allParameterValues[changingParameterIndex][0], false));

            // Value unset (mode param cannot be unset)
            if (changingParameterIndex != 0) {
                Object[] unset = {UNSET};
                allTestParams.addAll(getSingleParameterChangingTests(allParameterValues,
                        changingParameterIndex, unset, false));
            }

            // Illegal values
            allTestParams.addAll(getSingleParameterChangingTests(allParameterValues,
                    changingParameterIndex, allParameterValues[changingParameterIndex][1], true));
        }

        return allTestParams;
    }

    /**
     * Get the sessionInfo if this package owns the session.
     *
     * @param sessionId The id of the session
     *
     * @return The {@link PackageInstaller.SessionInfo} object, or {@code null} if session is not
     * owned by the this package.
     */
    private SessionInfo getSessionInfo(int sessionId) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        List<SessionInfo> mySessionInfos = installer.getMySessions();

        for (SessionInfo sessionInfo : mySessionInfos) {
            if (sessionInfo.sessionId == sessionId) {
                return sessionInfo;
            }
        }

        return null;
    }

    /**
     * Create a new installer session.
     *
     * @return The new session
     */
    private int createSession(SessionParams params) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();

        return installer.createSession(params);
    }

    @Test
    public void checkSessionParams() throws Exception {
        Log.i(LOG_TAG, "mode=" + mode + " installLocation=" + installLocation + " size=" + size
                + " appPackageName=" + appPackageName + " appIcon=" + appIcon + " appLabel="
                + appLabel + " originatingUri=" + originatingUri + " originatingUid="
                + originatingUid + " referredUri=" + referredUri + " installReason=" + installReason
                + " expectFailure=" + expectFailure);

        SessionParams params = new SessionParams(mode.get());
        installLocation.ifPresent(params::setInstallLocation);
        size.ifPresent(params::setSize);
        appPackageName.ifPresent(params::setAppPackageName);
        appIcon.ifPresent(params::setAppIcon);
        appLabel.ifPresent(params::setAppLabel);
        originatingUri.ifPresent(params::setOriginatingUri);
        originatingUid.ifPresent(params::setOriginatingUid);
        referredUri.ifPresent(params::setReferrerUri);
        installReason.ifPresent(params::setInstallReason);

        int sessionId;
        try {
            sessionId = createSession(params);

            if (expectFailure) {
                fail("Creating session did not fail");
            }
        } catch (Exception e) {
            if (expectFailure) {
                return;
            }

            throw e;
        }

        SessionInfo info = getSessionInfo(sessionId);

        assertThat(info.getMode()).isEqualTo(mode.get());
        installLocation.ifPresent(i -> assertThat(info.getInstallLocation()).isEqualTo(i));
        size.ifPresent(i -> assertThat(info.getSize()).isEqualTo(i));
        appPackageName.ifPresent(s -> assertThat(info.getAppPackageName()).isEqualTo(s));

        if (appIcon.isPresent()) {
            if (appIcon.get() == null) {
                assertThat(info.getAppIcon()).isNull();
            } else {
                assertThat(appIcon.get().sameAs(info.getAppIcon())).isTrue();
            }
        }

        appLabel.ifPresent(s -> assertThat(info.getAppLabel()).isEqualTo(s));
        originatingUri.ifPresent(uri -> assertThat(info.getOriginatingUri()).isEqualTo(uri));
        originatingUid.ifPresent(i -> assertThat(info.getOriginatingUid()).isEqualTo(i));
        referredUri.ifPresent(uri -> assertThat(info.getReferrerUri()).isEqualTo(uri));
        installReason.ifPresent(i -> assertThat(info.getInstallReason()).isEqualTo(i));
    }

    /** Similar to java.util.Optional but distinguishing between null and unset */
    private static class Optional<T> {
        private final boolean mIsSet;
        private final T mValue;

        Optional(boolean isSet, T value) {
            mIsSet = isSet;
            mValue = value;
        }

        static <T> Optional of(T value) {
            return new Optional(true, value);
        }

        T get() {
            if (!mIsSet) {
                throw new IllegalStateException(this + " is not set");
            }
            return mValue;
        }

        public String toString() {
            if (!mIsSet) {
                return "unset";
            } else if (mValue == null) {
                return "null";
            } else {
                return mValue.toString();
            }
        }

        boolean isPresent() {
            return mIsSet;
        }

        void ifPresent(Consumer<T> consumer) {
            if (mIsSet) {
                consumer.accept(mValue);
            }
        }
    }
}
