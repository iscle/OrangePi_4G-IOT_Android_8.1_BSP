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

package android.provider.cts;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.provider.cts.GetResultActivity.Result;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.support.v4.content.FileProvider;
import android.test.InstrumentationTestCase;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MediaStoreUiTest extends InstrumentationTestCase {
    private static final String TAG = "MediaStoreUiTest";

    private static final int REQUEST_CODE = 42;
    private static final String CONTENT = "Test";

    private UiDevice mDevice;
    private GetResultActivity mActivity;

    private File mFile;
    private Uri mMediaStoreUri;

    @Override
    public void setUp() throws Exception {
        mDevice = UiDevice.getInstance(getInstrumentation());

        final Context context = getInstrumentation().getContext();
        mActivity = launchActivity(context.getPackageName(), GetResultActivity.class, null);
        mActivity.clearResult();
    }

    @Override
    public void tearDown() throws Exception {
        if (mFile != null) {
            mFile.delete();
        }

        final ContentResolver resolver = mActivity.getContentResolver();
        for (UriPermission permission : resolver.getPersistedUriPermissions()) {
            mActivity.revokeUriPermission(
                    permission.getUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }

        mActivity.finish();
    }

    public void testGetDocumentUri() throws Exception {
        if (!supportsHardware()) return;

        prepareFile();

        final Uri treeUri = acquireAccess(mFile, Environment.DIRECTORY_DOCUMENTS);
        assertNotNull(treeUri);

        final Uri docUri = MediaStore.getDocumentUri(mActivity, mMediaStoreUri);
        assertNotNull(docUri);

        final ContentResolver resolver = mActivity.getContentResolver();
        try (ParcelFileDescriptor fd = resolver.openFileDescriptor(docUri, "rw")) {
            // Test reading
            try (final BufferedReader reader =
                         new BufferedReader(new FileReader(fd.getFileDescriptor()))) {
                assertEquals(CONTENT, reader.readLine());
            }

            // Test writing
            try (final OutputStream out = new FileOutputStream(fd.getFileDescriptor())) {
                out.write(CONTENT.getBytes());
            }
        }
    }

    public void testGetDocumentUri_ThrowsWithoutPermission() throws Exception {
        if (!supportsHardware()) return;

        prepareFile();

        try {
            MediaStore.getDocumentUri(mActivity, mMediaStoreUri);
            fail("Expecting SecurityException.");
        } catch (SecurityException e) {
            // Expected
        }
    }

    private void maybeClick(UiSelector sel) {
        try { mDevice.findObject(sel).click(); } catch (Throwable ignored) { }
    }

    private void maybeClick(BySelector sel) {
        try { mDevice.findObject(sel).click(); } catch (Throwable ignored) { }
    }

    /**
     * Verify that whoever handles {@link MediaStore#ACTION_IMAGE_CAPTURE} can
     * correctly write the contents into a passed {@code content://} Uri.
     */
    public void testImageCapture() throws Exception {
        final Context context = getInstrumentation().getContext();
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            Log.d(TAG, "Skipping due to lack of camera");
            return;
        }

        final File targetDir = new File(context.getFilesDir(), "debug");
        final File target = new File(targetDir, "capture.jpg");

        targetDir.mkdirs();
        assertFalse(target.exists());

        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT,
                FileProvider.getUriForFile(context, "android.provider.cts.fileprovider", target));

        // Figure out who is going to answer the phone
        final ResolveInfo ri = context.getPackageManager().resolveActivity(intent, 0);
        final String pkg = ri.activityInfo.packageName;
        Log.d(TAG, "We're probably launching " + ri);

        // Grant them all the permissions they might want
        getInstrumentation().getUiAutomation().executeShellCommand("pm grant "
                + pkg + " " + android.Manifest.permission.CAMERA);
        getInstrumentation().getUiAutomation().executeShellCommand("pm grant "
                + pkg + " " + android.Manifest.permission.ACCESS_COARSE_LOCATION);
        getInstrumentation().getUiAutomation().executeShellCommand("pm grant "
                + pkg + " " + android.Manifest.permission.ACCESS_FINE_LOCATION);
        getInstrumentation().getUiAutomation().executeShellCommand("pm grant "
                + pkg + " " + android.Manifest.permission.RECORD_AUDIO);
        getInstrumentation().getUiAutomation().executeShellCommand("pm grant "
                + pkg + " " + android.Manifest.permission.READ_EXTERNAL_STORAGE);
        getInstrumentation().getUiAutomation().executeShellCommand("pm grant "
                + pkg + " " + android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        SystemClock.sleep(DateUtils.SECOND_IN_MILLIS);

        mActivity.startActivityForResult(intent, REQUEST_CODE);
        mDevice.waitForIdle();

        // Try a couple different strategies for taking a photo: first take a
        // photo and confirm using hardware keys
        mDevice.pressKeyCode(KeyEvent.KEYCODE_CAMERA);
        mDevice.waitForIdle();
        SystemClock.sleep(5 * DateUtils.SECOND_IN_MILLIS);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_DPAD_CENTER);
        mDevice.waitForIdle();

        // Maybe that gave us a result?
        Result result = mActivity.getResult(15, TimeUnit.SECONDS);
        Log.d(TAG, "First pass result was " + result);

        // Hrm, that didn't work; let's try an alternative approach of digging
        // around for a shutter button
        if (result == null) {
            maybeClick(new UiSelector().resourceId(pkg + ":id/shutter_button"));
            mDevice.waitForIdle();
            SystemClock.sleep(5 * DateUtils.SECOND_IN_MILLIS);
            maybeClick(new UiSelector().resourceId(pkg + ":id/shutter_button"));
            mDevice.waitForIdle();
            maybeClick(new UiSelector().resourceId(pkg + ":id/done_button"));
            mDevice.waitForIdle();

            result = mActivity.getResult(15, TimeUnit.SECONDS);
            Log.d(TAG, "Second pass result was " + result);
        }

        // Grr, let's try hunting around even more
        if (result == null) {
            maybeClick(By.pkg(pkg).descContains("Capture"));
            mDevice.waitForIdle();
            SystemClock.sleep(5 * DateUtils.SECOND_IN_MILLIS);
            maybeClick(By.pkg(pkg).descContains("Done"));
            mDevice.waitForIdle();

            result = mActivity.getResult(15, TimeUnit.SECONDS);
            Log.d(TAG, "Third pass result was " + result);
        }

        assertNotNull("Expected to get a IMAGE_CAPTURE result; your camera app should "
                + "respond to the CAMERA and DPAD_CENTER keycodes", result);

        assertTrue("exists", target.exists());
        assertTrue("has data", target.length() > 65536);

        // At the very least we expect photos generated by the device to have
        // sane baseline EXIF data
        final ExifInterface exif = new ExifInterface(new FileInputStream(target));
        assertAttribute(exif, ExifInterface.TAG_MAKE);
        assertAttribute(exif, ExifInterface.TAG_MODEL);
        assertAttribute(exif, ExifInterface.TAG_DATETIME);
    }

    private static void assertAttribute(ExifInterface exif, String tag) {
        final String res = exif.getAttribute(tag);
        if (res == null || res.length() == 0) {
            Log.d(TAG, "Expected valid EXIF tag for tag " + tag);
        }
    }

    private boolean supportsHardware() {
        final PackageManager pm = getInstrumentation().getContext().getPackageManager();
        return !pm.hasSystemFeature("android.hardware.type.television")
                && !pm.hasSystemFeature("android.hardware.type.watch");
    }

    private void prepareFile() throws Exception {
        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());

        final File documents =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        documents.mkdirs();
        assertTrue(documents.isDirectory());

        mFile = new File(documents, "test.txt");
        try (OutputStream os = new FileOutputStream(mFile)) {
            os.write(CONTENT.getBytes());
        }

        final CountDownLatch latch = new CountDownLatch(1);
        MediaScannerConnection.scanFile(
                mActivity,
                new String[]{ mFile.getAbsolutePath() },
                new String[]{ "plain/text" },
                (String path, Uri uri) -> onScanCompleted(uri, latch)
        );
        assertTrue(
                "MediaScanner didn't finish scanning in 30s.", latch.await(30, TimeUnit.SECONDS));
    }

    private void onScanCompleted(Uri uri, CountDownLatch latch) {
        mMediaStoreUri = uri;
        latch.countDown();
    }

    private Uri acquireAccess(File file, String directoryName) {
        StorageManager storageManager =
                (StorageManager) mActivity.getSystemService(Context.STORAGE_SERVICE);

        // Request access from DocumentsUI
        final StorageVolume volume = storageManager.getStorageVolume(file);
        final Intent intent = volume.createAccessIntent(directoryName);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // Granting the access
        BySelector buttonPanelSelector = By.pkg("com.android.documentsui")
                .res("android:id/buttonPanel");
        mDevice.wait(Until.hasObject(buttonPanelSelector), 30 * DateUtils.SECOND_IN_MILLIS);
        final UiObject2 buttonPanel = mDevice.findObject(buttonPanelSelector);
        final UiObject2 allowButton = buttonPanel.findObject(By.res("android:id/button1"));
        allowButton.click();

        mDevice.waitForIdle();

        // Check granting result and take persistent permission
        final Result result = mActivity.getResult();
        assertEquals(Activity.RESULT_OK, result.resultCode);

        final Intent resultIntent = result.data;
        final Uri resultUri = resultIntent.getData();
        final int flags = resultIntent.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        mActivity.getContentResolver().takePersistableUriPermission(resultUri, flags);
        return resultUri;
    }
}
