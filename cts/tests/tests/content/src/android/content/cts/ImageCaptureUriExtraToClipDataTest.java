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
package android.content.cts;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.provider.MediaStore;
import android.test.AndroidTestCase;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ImageCaptureUriExtraToClipDataTest extends AndroidTestCase {
    private static final String FILE_NAME = "testFile.txt";
    private File mTestFile;
    private final Semaphore mFileReadySemaphore = new Semaphore(0);

    public static final String TEST_INPUT = "testString";
    public static final String TAG = "ImageCaptureUriExtraToClipDataTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        assertEquals(0, mFileReadySemaphore.availablePermits());

        BroadcastReceiver mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    mFileReadySemaphore.release();
                }
            };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ImageCaptureActivity.ACTION_FILE_READY);
        getContext().registerReceiver(mReceiver, filter);

        mTestFile = new File(getContext().getFilesDir() + File.separator + FILE_NAME);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mTestFile.exists()) {
            assertTrue(mTestFile.delete());
        }
        super.tearDown();
    }


    public void testUriExtraOutputMigratedToClipData_imageCaptureIntent() {
        startActivityWithAction(MediaStore.ACTION_IMAGE_CAPTURE);
        waitForFileReady();
        assertFileContents();
    }

    public void testUriExtraOutputMigratedToClipData_imageCaptureSecureIntent() {
        startActivityWithAction(MediaStore.ACTION_IMAGE_CAPTURE_SECURE);
        waitForFileReady();
        assertFileContents();
    }

    public void testUriExtraOutputMigratedToClipData_videoCaptureIntent() {
        startActivityWithAction(MediaStore.ACTION_VIDEO_CAPTURE);
        waitForFileReady();
        assertFileContents();
    }

    private void startActivityWithAction(String action) {
        Intent intent = new Intent(action);
        intent.setComponent(new ComponentName("android.content.cts",
                        "android.content.cts.ImageCaptureActivity"));
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mTestFile));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
    }

    private void waitForFileReady() {
        try {
            assertTrue(mFileReadySemaphore.tryAcquire(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }

    private void assertFileContents() {
        char[] buffer = new char[TEST_INPUT.length()];
        try {
            FileReader reader = new FileReader(mTestFile);
            reader.read(buffer);
            reader.close();
        } catch (IOException e) {
            // Problem
            fail(e.toString());
        }
        String fileContents = new String(buffer);
        assertEquals(TEST_INPUT, fileContents);
    }
}
