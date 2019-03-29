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

import static android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.Session;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import libcore.io.Streams;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class InstallSessionTransferTest {
    /**
     * Get the sessionInfo if this package owns the session.
     *
     * @param sessionId The id of the session
     *
     * @return The {@link PackageInstaller.SessionInfo} object, or {@code null} if session is not
     *         owned by the this package.
     */
    private SessionInfo getSessionInfo(@NonNull PackageInstaller installer,
            int sessionId) {
        List<SessionInfo> mySessionInfos = installer.getMySessions();

        for (SessionInfo sessionInfo : mySessionInfos) {
            if (sessionInfo.sessionId == sessionId) {
                return sessionInfo;
            }
        }

        return null;
    }

    /**
     * Get name of the package installer.
     *
     * @return The package name of the package installer
     */
    private static String getPackageInstallerPackageName() throws Exception {
        Intent installerIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        installerIntent.setDataAndType(Uri.fromFile(new File("foo.apk")),
                "application/vnd.android.package-archive");

        ResolveInfo installer = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getPackageManager().resolveActivity(installerIntent,
                        PackageManager.MATCH_DEFAULT_ONLY);

        if (installer != null) {
            return installer.activityInfo.packageName;
        }

        return null;
    }

    /**
     * Write an APK to the session.
     *
     * @param session The session to write to
     * @param name The name of the apk to write
     */
    private void writeApk(@NonNull Session session, @NonNull String name) throws IOException {
        try (InputStream in = new FileInputStream("/data/local/tmp/cts/content/" + name + ".apk")) {
            try (OutputStream out = session.openWrite(name, 0, -1)) {
                Streams.copy(in, out);
            }
        }
    }

    /**
     * Create a new installer session.
     *
     * @return The new session
     */
    private Session createSession() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        PackageInstaller installer = context.getPackageManager().getPackageInstaller();

        SessionParams params = new SessionParams(MODE_FULL_INSTALL);
        int sessionId = installer.createSession(params);
        return installer.openSession(sessionId);
    }

    @Test
    public void transferSession() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        String packageInstallerPackage = getPackageInstallerPackageName();
        assumeNotNull(packageInstallerPackage);

        PackageInstaller installer = context.getPackageManager().getPackageInstaller();

        SessionParams params = new SessionParams(MODE_FULL_INSTALL);
        int sessionId = installer.createSession(params);
        Session session = installer.openSession(sessionId);

        writeApk(session, "CtsContentTestCases");

        InputStream danglingReadStream = session.openRead("CtsContentTestCases");

        SessionInfo info = getSessionInfo(installer, sessionId);
        assertThat(info.getInstallerPackageName()).isEqualTo(context.getPackageName());
        assertThat(info.isSealed()).isFalse();

        // This transfers the session to the new owner
        session.transfer(packageInstallerPackage);
        assertThat(getSessionInfo(installer, sessionId)).isNull();

        try {
            // Session is transferred, all operations on the session are invalid
            session.getNames();
            fail();
        } catch (SecurityException e) {
            // expected
        }

        // Even when the session is transferred read streams still work and contain the same content
        // that we initially wrote into it.
        try (InputStream originalContent = new FileInputStream(
                "/data/local/tmp/cts/content/CtsContentTestCases.apk")) {
            try (InputStream sessionContent = danglingReadStream) {
                byte[] buffer = new byte[4096];
                while (true) {
                    int numReadOriginal = originalContent.read(buffer);
                    int numReadSession = sessionContent.read(buffer);

                    assertThat(numReadOriginal).isEqualTo(numReadSession);
                    if (numReadOriginal == -1) {
                        break;
                    }
                }
            }
        }

        danglingReadStream.close();
    }

    @Test
    public void transferToInvalidNewOwner() throws Exception {
        Session session = createSession();
        writeApk(session, "CtsContentTestCases");

        try {
            // This will fail as the name of the new owner is invalid
            session.transfer("android.content.cts.invalid.package");
            fail();
        } catch (PackageManager.NameNotFoundException e) {
            // Expected
        }

        session.abandon();
    }

    @Test
    public void transferToOwnerWithoutInstallPermission() throws Exception {
        Session session = createSession();
        writeApk(session, "CtsContentTestCases");

        try {
            // This will fail as the current package does not own the install-packages permission
            session.transfer(InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getPackageName());
            fail();
        } catch (SecurityException e) {
            // Expected
        }

        session.abandon();
    }

    @Test
    public void transferWithOpenWrite() throws Exception {
        Session session = createSession();
        String packageInstallerPackage = getPackageInstallerPackageName();
        assumeNotNull(packageInstallerPackage);

        session.openWrite("danglingWriteStream", 0, 1);
        try {
            // This will fail as the danglingWriteStream is still open
            session.transfer(packageInstallerPackage);
            fail();
        } catch (SecurityException e) {
            // Expected
        }

        session.abandon();
    }

    @Test
    public void transferSessionWithInvalidApk() throws Exception {
        Session session = createSession();
        String packageInstallerPackage = getPackageInstallerPackageName();
        assumeNotNull(packageInstallerPackage);

        try (OutputStream out = session.openWrite("invalid", 0, 2)) {
            out.write(new byte[]{23, 42});
            out.flush();
        }

        try {
            // This will fail as the content of 'invalid' is not a valid APK
            session.transfer(packageInstallerPackage);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        session.abandon();
    }

    @Test
    public void transferWithApkFromWrongPackage() throws Exception {
        Session session = createSession();
        String packageInstallerPackage = getPackageInstallerPackageName();
        assumeNotNull(packageInstallerPackage);

        writeApk(session, "CtsContentEmptyTestApp");

        try {
            // This will fail as the session contains the a apk from the wrong package
            session.transfer(packageInstallerPackage);
            fail();
        } catch (SecurityException e) {
            // expected
        }

        session.abandon();
    }
}
