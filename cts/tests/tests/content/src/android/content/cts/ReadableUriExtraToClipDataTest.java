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
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.List;

public class ReadableUriExtraToClipDataTest extends AndroidTestCase {
    private static final List<String> FILE_NAMES = Arrays.asList("testFile1.txt", "testFile2.txt");
    private static final List<File> mTestFiles = new ArrayList<File>();
    private static final ArrayList<Uri> mTestFileUris = new ArrayList<Uri>();
    private final Semaphore mReadSuccessSemaphore = new Semaphore(0);

    public static final String TEST_INPUT = "testString";
    public static final String TAG = "ReadableUriExtraToClipDataTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        assertEquals(0, mReadSuccessSemaphore.availablePermits());

        BroadcastReceiver mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    mReadSuccessSemaphore.release();
                }
            };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ReadableFileReceiverActivity.ACTION_CONFIRM_READ_SUCCESS);
        getContext().registerReceiver(mReceiver, filter);

        for (String fileName : FILE_NAMES) {
            File testFile = new File(getContext().getFilesDir() + File.separator + fileName);
            writeTestInputToFile(testFile);
            mTestFiles.add(testFile);
            mTestFileUris.add(Uri.fromFile(testFile));
        }
    }

    @Override
    protected void tearDown() throws Exception {
        for (File testFile : mTestFiles) {
            if (testFile.exists()) {
                assertTrue(testFile.delete());
            }
        }
        mTestFiles.clear();
        mTestFileUris.clear();
        super.tearDown();
    }

    public void testUriExtraStreamMigratedToClipData_sendIntent() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setComponent(new ComponentName(getContext(), ReadableFileReceiverActivity.class));
        intent.putExtra(Intent.EXTRA_STREAM, mTestFileUris.get(0));
        intent.setType("*/*");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        getContext().startActivity(intent);

        waitForConfirmationReadSuccess();
    }

    public void testUriExtraStreamMigratedToClipData_sendMultipleIntent() {
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setComponent(new ComponentName(getContext(), ReadableFileReceiverActivity.class));
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, mTestFileUris);
        intent.setType("*/*");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        getContext().startActivity(intent);

        waitForConfirmationReadSuccess();
    }

    private void writeTestInputToFile(File file) {
        try {
            FileWriter writer = new FileWriter(file);
            writer.write(TEST_INPUT);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            fail(e.toString());
            return;
        }
    }

    private void waitForConfirmationReadSuccess() {
        try {
            assertTrue(mReadSuccessSemaphore.tryAcquire(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail(e.toString());
        }
    }
}