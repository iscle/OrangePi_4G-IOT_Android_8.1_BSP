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

package android.jobscheduler.cts;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.content.ContentResolver;
import android.content.Context;
import android.jobscheduler.DummyJobContentProvider;
import android.jobscheduler.TriggerContentJobService;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Process;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Schedules jobs that look for content URI changes and ensures they are triggered correctly.
 */
@TargetApi(23)
public class TriggerContentTest extends ConstraintTest {
    public static final int TRIGGER_CONTENT_JOB_ID = TriggerContentTest.class.hashCode();

    // The root URI of the media provider, to monitor for generic changes to its content.
    static final Uri MEDIA_URI = Uri.parse("content://" + MediaStore.AUTHORITY + "/");

    // Media URI for all external media content.
    static final Uri MEDIA_EXTERNAL_URI = Uri.parse("content://" + MediaStore.AUTHORITY
            + "/external");

    // Path segments for image-specific URIs in the provider.
    static final List<String> EXTERNAL_PATH_SEGMENTS
            = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.getPathSegments();

    // The columns we want to retrieve about a particular image.
    static final String[] PROJECTION = new String[] {
            MediaStore.Images.ImageColumns._ID, MediaStore.Images.ImageColumns.DATA
    };
    static final int PROJECTION_ID = 0;
    static final int PROJECTION_DATA = 1;

    // This is the external storage directory where cameras place pictures.
    static final String DCIM_DIR = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DCIM).getPath();

    static final String PIC_1_NAME = "TriggerContentTest1_" + Process.myPid();
    static final String PIC_2_NAME = "TriggerContentTest2_" + Process.myPid();

    File[] mActiveFiles = new File[5];
    Uri[] mActiveUris = new Uri[5];

    static class MediaScanner implements MediaScannerConnection.OnScanCompletedListener {
        private static final long DEFAULT_TIMEOUT_MILLIS = 1000L; // 1 second.

        private CountDownLatch mLatch;
        private String mScannedPath;
        private Uri mScannedUri;

        public boolean scan(Context context, String file, String mimeType)
                throws InterruptedException {
            mLatch = new CountDownLatch(1);
            MediaScannerConnection.scanFile(context,
                    new String[] { file.toString() }, new String[] { mimeType }, this);
            return mLatch.await(DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }

        public String getScannedPath() {
            synchronized (this) {
                return mScannedPath;
            }
        }

        public Uri getScannedUri() {
            synchronized (this) {
                return mScannedUri;
            }
        }

        @Override public void onScanCompleted(String path, Uri uri) {
            synchronized (this) {
                mScannedPath = path;
                mScannedUri = uri;
                mLatch.countDown();
            }
        }
    }

    private void cleanupActive(int which) {
        if (mActiveUris[which] != null) {
            getContext().getContentResolver().delete(mActiveUris[which], null, null);
            mActiveUris[which] = null;
        }
        if (mActiveFiles[which] != null) {
            mActiveFiles[which].delete();
            mActiveFiles[which] = null;
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        for (int i=0; i<mActiveFiles.length; i++) {
            cleanupActive(i);
        }
    }

    private JobInfo makeJobInfo(Uri uri, int flags) {
        JobInfo.Builder builder = new JobInfo.Builder(TRIGGER_CONTENT_JOB_ID,
                kTriggerContentServiceComponent);
        builder.addTriggerContentUri(new JobInfo.TriggerContentUri(uri, flags));
        // For testing purposes, react quickly.
        builder.setTriggerContentUpdateDelay(500);
        builder.setTriggerContentMaxDelay(500);
        return builder.build();
    }

    private JobInfo makePhotosJobInfo() {
        JobInfo.Builder builder = new JobInfo.Builder(TRIGGER_CONTENT_JOB_ID,
                kTriggerContentServiceComponent);
        // Look for specific changes to images in the provider.
        builder.addTriggerContentUri(new JobInfo.TriggerContentUri(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS));
        // Also look for general reports of changes in the overall provider.
        builder.addTriggerContentUri(new JobInfo.TriggerContentUri(MEDIA_URI, 0));
        // For testing purposes, react quickly.
        builder.setTriggerContentUpdateDelay(500);
        builder.setTriggerContentMaxDelay(500);
        return builder.build();
    }

    public static void copyToFileOrThrow(InputStream inputStream, File destFile)
            throws IOException {
        if (destFile.exists()) {
            destFile.delete();
        }
        FileOutputStream out = new FileOutputStream(destFile);
        try {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) >= 0) {
                out.write(buffer, 0, bytesRead);
            }
        } finally {
            out.flush();
            try {
                out.getFD().sync();
            } catch (IOException e) {
            }
            out.close();
            inputStream.close();
        }
    }

    public Uri createAndAddImage(File destFile, InputStream image) throws IOException,
            InterruptedException {
        copyToFileOrThrow(image, destFile);
        MediaScanner scanner = new MediaScanner();
        boolean success = scanner.scan(getContext(), destFile.toString(), "image/jpeg");
        if (success) {
            return scanner.getScannedUri();
        }
        return null;
    }

    public Uri makeActiveFile(int which, File file, InputStream source) throws IOException,
                InterruptedException {
        mActiveFiles[which] = file;
        mActiveUris[which] = createAndAddImage(file, source);
        return mActiveUris[which];
    }

    private void assertUriArrayLength(int length, Uri[] uris) {
        if (uris.length != length) {
            StringBuilder sb = new StringBuilder();
            sb.append("Expected ");
            sb.append(length);
            sb.append(" URI, got ");
            sb.append(uris.length);
            if (uris.length > 0) {
                sb.append(": ");
                for (int i=0; i<uris.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(uris[i]);
                }
            }
            fail(sb.toString());
        }
    }

    private void assertHasUri(Uri wanted, Uri[] uris) {
        for (int i=0; i<uris.length; i++) {
            if (wanted.equals(uris[i])) {
                return;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Don't have uri ");
        sb.append(wanted);
        sb.append(" in: ");
        for (int i=0; i<uris.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(uris[i]);
        }
        fail(sb.toString());
    }

    public void testDescendantsObserver() throws Exception {
        String base = "content://" + DummyJobContentProvider.AUTHORITY + "/root";
        Uri uribase = Uri.parse(base);
        Uri uri1 = Uri.parse(base + "/sub1");
        Uri uri2 = Uri.parse(base + "/sub2");

        // Start watching.
        JobInfo triggerJob = makeJobInfo(uribase,
                JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS);
        kTriggerTestEnvironment.setExpectedExecutions(1);
        kTriggerTestEnvironment.setMode(
                TriggerContentJobService.TestEnvironment.MODE_ONE_REPEAT_RESCHEDULE, triggerJob);
        mJobScheduler.schedule(triggerJob);

        // Report changes.
        getContext().getContentResolver().notifyChange(uribase, null, 0);
        getContext().getContentResolver().notifyChange(uri1, null, 0);

        // Wait and check results
        boolean executed = kTriggerTestEnvironment.awaitExecution();
        kTriggerTestEnvironment.setExpectedExecutions(1);
        assertTrue("Timed out waiting for trigger content.", executed);
        JobParameters params = kTriggerTestEnvironment.getLastJobParameters();
        Uri[] uris = params.getTriggeredContentUris();
        assertUriArrayLength(2, uris);
        assertHasUri(uribase, uris);
        assertHasUri(uri1, uris);
        String[] auths = params.getTriggeredContentAuthorities();
        assertEquals(1, auths.length);
        assertEquals(DummyJobContentProvider.AUTHORITY, auths[0]);

        // Report more changes, this time not letting it see the top-level change
        getContext().getContentResolver().notifyChange(uribase, null,
                ContentResolver.NOTIFY_SKIP_NOTIFY_FOR_DESCENDANTS);
        getContext().getContentResolver().notifyChange(uri2, null, 0);

        // Wait for the job to wake up and verify it saw the change.
        executed = kTriggerTestEnvironment.awaitExecution();
        assertTrue("Timed out waiting for trigger content.", executed);
        params = kTriggerTestEnvironment.getLastJobParameters();
        uris = params.getTriggeredContentUris();
        assertUriArrayLength(1, uris);
        assertEquals(uri2, uris[0]);
        auths = params.getTriggeredContentAuthorities();
        assertEquals(1, auths.length);
        assertEquals(DummyJobContentProvider.AUTHORITY, auths[0]);
    }

    public void testNonDescendantsObserver() throws Exception {
        String base = "content://" + DummyJobContentProvider.AUTHORITY + "/root";
        Uri uribase = Uri.parse(base);
        Uri uri1 = Uri.parse(base + "/sub1");
        Uri uri2 = Uri.parse(base + "/sub2");

        // Start watching.
        JobInfo triggerJob = makeJobInfo(uribase, 0);
        kTriggerTestEnvironment.setExpectedExecutions(1);
        kTriggerTestEnvironment.setMode(
                TriggerContentJobService.TestEnvironment.MODE_ONE_REPEAT_RESCHEDULE, triggerJob);
        mJobScheduler.schedule(triggerJob);

        // Report changes.
        getContext().getContentResolver().notifyChange(uribase, null, 0);
        getContext().getContentResolver().notifyChange(uri1, null, 0);

        // Wait and check results
        boolean executed = kTriggerTestEnvironment.awaitExecution();
        kTriggerTestEnvironment.setExpectedExecutions(1);
        assertTrue("Timed out waiting for trigger content.", executed);
        JobParameters params = kTriggerTestEnvironment.getLastJobParameters();
        Uri[] uris = params.getTriggeredContentUris();
        assertUriArrayLength(1, uris);
        assertEquals(uribase, uris[0]);
        String[] auths = params.getTriggeredContentAuthorities();
        assertEquals(1, auths.length);
        assertEquals(DummyJobContentProvider.AUTHORITY, auths[0]);

        // Report more changes, this time not letting it see the top-level change
        getContext().getContentResolver().notifyChange(uribase, null,
                ContentResolver.NOTIFY_SKIP_NOTIFY_FOR_DESCENDANTS);
        getContext().getContentResolver().notifyChange(uri2, null, 0);

        // Wait for the job to wake up and verify it saw the change.
        executed = kTriggerTestEnvironment.awaitExecution();
        assertTrue("Timed out waiting for trigger content.", executed);
        params = kTriggerTestEnvironment.getLastJobParameters();
        uris = params.getTriggeredContentUris();
        assertUriArrayLength(1, uris);
        assertEquals(uribase, uris[0]);
        auths = params.getTriggeredContentAuthorities();
        assertEquals(1, auths.length);
        assertEquals(DummyJobContentProvider.AUTHORITY, auths[0]);
    }

    public void testPhotoAdded_Reschedule() throws Exception {
        JobInfo triggerJob = makePhotosJobInfo();

        kTriggerTestEnvironment.setExpectedExecutions(1);
        kTriggerTestEnvironment.setMode(
                TriggerContentJobService.TestEnvironment.MODE_ONE_REPEAT_RESCHEDULE, triggerJob);
        mJobScheduler.schedule(triggerJob);

        // Create a file that our job should see.
        makeActiveFile(0, new File(DCIM_DIR, PIC_1_NAME),
                getContext().getResources().getAssets().open("violet.jpg"));
        assertNotNull(mActiveUris[0]);

        // Wait for the job to wake up with the change and verify it.
        boolean executed = kTriggerTestEnvironment.awaitExecution();
        kTriggerTestEnvironment.setExpectedExecutions(1);
        assertTrue("Timed out waiting for trigger content.", executed);
        JobParameters params = kTriggerTestEnvironment.getLastJobParameters();
        Uri[] uris = params.getTriggeredContentUris();
        assertUriArrayLength(1, uris);
        assertEquals(mActiveUris[0], uris[0]);
        String[] auths = params.getTriggeredContentAuthorities();
        assertEquals(1, auths.length);
        assertEquals(MediaStore.AUTHORITY, auths[0]);

        // While the job is still running, create another file it should see.
        // (This tests that it will see changes that happen before the next job
        // is scheduled.)
        makeActiveFile(1, new File(DCIM_DIR, PIC_2_NAME),
                getContext().getResources().getAssets().open("violet.jpg"));
        assertNotNull(mActiveUris[1]);

        // Wait for the job to wake up and verify it saw the change.
        executed = kTriggerTestEnvironment.awaitExecution();
        assertTrue("Timed out waiting for trigger content.", executed);
        params = kTriggerTestEnvironment.getLastJobParameters();
        uris = params.getTriggeredContentUris();
        assertUriArrayLength(1, uris);
        assertEquals(mActiveUris[1], uris[0]);
        auths = params.getTriggeredContentAuthorities();
        assertEquals(1, auths.length);
        assertEquals(MediaStore.AUTHORITY, auths[0]);

        // Schedule a new job to look at what we see when deleting the files.
        kTriggerTestEnvironment.setExpectedExecutions(1);
        kTriggerTestEnvironment.setMode(TriggerContentJobService.TestEnvironment.MODE_ONESHOT,
                triggerJob);
        mJobScheduler.schedule(triggerJob);

        // Delete the files.  Note that this will result in a general change, not for specific URIs.
        cleanupActive(0);
        cleanupActive(1);

        // Wait for the job to wake up and verify it saw the change.
        executed = kTriggerTestEnvironment.awaitExecution();
        assertTrue("Timed out waiting for trigger content.", executed);
        params = kTriggerTestEnvironment.getLastJobParameters();
        uris = params.getTriggeredContentUris();
        assertUriArrayLength(1, uris);
        assertEquals(MEDIA_EXTERNAL_URI, uris[0]);
        auths = params.getTriggeredContentAuthorities();
        assertEquals(1, auths.length);
        assertEquals(MediaStore.AUTHORITY, auths[0]);
    }

    // Doesn't work.  Should it?
    public void xxxtestPhotoAdded_FinishTrue() throws Exception {
        JobInfo triggerJob = makePhotosJobInfo();

        kTriggerTestEnvironment.setExpectedExecutions(1);
        kTriggerTestEnvironment.setMode(
                TriggerContentJobService.TestEnvironment.MODE_ONE_REPEAT_FINISH_TRUE, triggerJob);
        mJobScheduler.schedule(triggerJob);

        // Create a file that our job should see.
        makeActiveFile(0, new File(DCIM_DIR, PIC_1_NAME),
                getContext().getResources().getAssets().open("violet.jpg"));
        assertNotNull(mActiveUris[0]);

        // Wait for the job to wake up with the change and verify it.
        boolean executed = kTriggerTestEnvironment.awaitExecution();
        kTriggerTestEnvironment.setExpectedExecutions(1);
        assertTrue("Timed out waiting for trigger content.", executed);
        JobParameters params = kTriggerTestEnvironment.getLastJobParameters();
        Uri[] uris = params.getTriggeredContentUris();
        assertUriArrayLength(1, uris);
        assertEquals(mActiveUris[0], uris[0]);
        String[] auths = params.getTriggeredContentAuthorities();
        assertEquals(1, auths.length);
        assertEquals(MediaStore.AUTHORITY, auths[0]);

        // While the job is still running, create another file it should see.
        // (This tests that it will see changes that happen before the next job
        // is scheduled.)
        makeActiveFile(1, new File(DCIM_DIR, PIC_2_NAME),
                getContext().getResources().getAssets().open("violet.jpg"));
        assertNotNull(mActiveUris[1]);

        // Wait for the job to wake up and verify it saw the change.
        executed = kTriggerTestEnvironment.awaitExecution();
        assertTrue("Timed out waiting for trigger content.", executed);
        params = kTriggerTestEnvironment.getLastJobParameters();
        uris = params.getTriggeredContentUris();
        assertUriArrayLength(1, uris);
        assertEquals(mActiveUris[1], uris[0]);
        auths = params.getTriggeredContentAuthorities();
        assertEquals(1, auths.length);
        assertEquals(MediaStore.AUTHORITY, auths[0]);

        // Schedule a new job to look at what we see when deleting the files.
        kTriggerTestEnvironment.setExpectedExecutions(1);
        kTriggerTestEnvironment.setMode(TriggerContentJobService.TestEnvironment.MODE_ONESHOT,
                triggerJob);
        mJobScheduler.schedule(triggerJob);

        // Delete the files.  Note that this will result in a general change, not for specific URIs.
        cleanupActive(0);
        cleanupActive(1);

        // Wait for the job to wake up and verify it saw the change.
        executed = kTriggerTestEnvironment.awaitExecution();
        assertTrue("Timed out waiting for trigger content.", executed);
        params = kTriggerTestEnvironment.getLastJobParameters();
        uris = params.getTriggeredContentUris();
        assertUriArrayLength(1, uris);
        assertEquals(MEDIA_EXTERNAL_URI, uris[0]);
        auths = params.getTriggeredContentAuthorities();
        assertEquals(1, auths.length);
        assertEquals(MediaStore.AUTHORITY, auths[0]);
    }
}
