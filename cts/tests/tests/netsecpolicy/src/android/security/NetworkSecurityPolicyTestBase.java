package android.security;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.test.AndroidTestCase;
import android.webkit.cts.CtsTestServer;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownServiceException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

abstract class NetworkSecurityPolicyTestBase extends AndroidTestCase {
    private CtsTestServer mHttpOnlyWebServer;

    private final boolean mCleartextTrafficExpectedToBePermitted;

    NetworkSecurityPolicyTestBase(boolean cleartextTrafficExpectedToBePermitted) {
        mCleartextTrafficExpectedToBePermitted = cleartextTrafficExpectedToBePermitted;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mHttpOnlyWebServer = new CtsTestServer(mContext, false);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            mHttpOnlyWebServer.shutdown();
        } finally {
            super.tearDown();
        }
    }

    public void testNetworkSecurityPolicy() {
        assertEquals(mCleartextTrafficExpectedToBePermitted,
                NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted());
    }

    public void testApplicationInfoFlag() {
        ApplicationInfo appInfo = getContext().getApplicationInfo();
        int expectedValue = (mCleartextTrafficExpectedToBePermitted)
                ? ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC : 0;
        assertEquals(expectedValue, appInfo.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC);
    }

    public void testDefaultHttpURLConnection() throws Exception {
        if (mCleartextTrafficExpectedToBePermitted) {
            assertCleartextHttpURLConnectionSucceeds();
        } else {
            assertCleartextHttpURLConnectionBlocked();
        }
    }

    private void assertCleartextHttpURLConnectionSucceeds() throws Exception {
        URL url = new URL(mHttpOnlyWebServer.getUserAgentUrl());
        HttpURLConnection conn = null;
        try {
            mHttpOnlyWebServer.resetRequestState();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            assertEquals(200, conn.getResponseCode());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        Uri uri = Uri.parse(url.toString()).buildUpon().scheme(null).authority(null).build();
        assertTrue(mHttpOnlyWebServer.wasResourceRequested(uri.toString()));
    }

    private void assertCleartextHttpURLConnectionBlocked() throws Exception {
        URL url = new URL(mHttpOnlyWebServer.getUserAgentUrl());
        HttpURLConnection conn = null;
        try {
            mHttpOnlyWebServer.resetRequestState();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.getResponseCode();
            fail();
        } catch (IOException e) {
            if ((e.getMessage() == null) || (!e.getMessage().toLowerCase().contains("cleartext"))) {
                fail("Exception with which request failed does not mention cleartext: " + e);
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        Uri uri = Uri.parse(url.toString()).buildUpon().scheme(null).authority(null).build();
        assertFalse(mHttpOnlyWebServer.wasResourceRequested(uri.toString()));
    }

    public void testAndroidHttpClient() throws Exception {
        if (mCleartextTrafficExpectedToBePermitted) {
            assertAndroidHttpClientCleartextRequestSucceeds();
        } else {
            assertAndroidHttpClientCleartextRequestBlocked();
        }
    }

    private void assertAndroidHttpClientCleartextRequestSucceeds() throws Exception {
        URL url = new URL(mHttpOnlyWebServer.getUserAgentUrl());
        AndroidHttpClient httpClient = AndroidHttpClient.newInstance(null);
        try {
            HttpResponse response = httpClient.execute(new HttpGet(url.toString()));
            assertEquals(200, response.getStatusLine().getStatusCode());
        } finally {
            httpClient.close();
        }
        Uri uri = Uri.parse(url.toString()).buildUpon().scheme(null).authority(null).build();
        assertTrue(mHttpOnlyWebServer.wasResourceRequested(uri.toString()));
    }

    private void assertAndroidHttpClientCleartextRequestBlocked() throws Exception {
        URL url = new URL(mHttpOnlyWebServer.getUserAgentUrl());
        AndroidHttpClient httpClient = AndroidHttpClient.newInstance(null);
        try {
            HttpResponse response = httpClient.execute(new HttpGet(url.toString()));
            fail();
        } catch (IOException e) {
            if ((e.getMessage() == null) || (!e.getMessage().toLowerCase().contains("cleartext"))) {
                fail("Exception with which request failed does not mention cleartext: " + e);
            }
        } finally {
            httpClient.close();
        }
        Uri uri = Uri.parse(url.toString()).buildUpon().scheme(null).authority(null).build();
        assertFalse(mHttpOnlyWebServer.wasResourceRequested(uri.toString()));
    }

    public void testMediaPlayer() throws Exception {
        if (mCleartextTrafficExpectedToBePermitted) {
            assertMediaPlayerCleartextRequestSucceeds();
        } else {
            assertMediaPlayerCleartextRequestBlocked();
        }
    }

    private void assertMediaPlayerCleartextRequestSucceeds() throws Exception {
        MediaPlayer mediaPlayer = new MediaPlayer();
        Uri uri = Uri.parse(mHttpOnlyWebServer.getUserAgentUrl());
        mediaPlayer.setDataSource(getContext(), uri);

        try {
            mediaPlayer.prepare();
        } catch (IOException expected) {
        } finally {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
            }
        }
        uri = uri.buildUpon().scheme(null).authority(null).build();
        assertTrue(mHttpOnlyWebServer.wasResourceRequested(uri.toString()));
    }

    private void assertMediaPlayerCleartextRequestBlocked() throws Exception {
        MediaPlayer mediaPlayer = new MediaPlayer();
        Uri uri = Uri.parse(mHttpOnlyWebServer.getUserAgentUrl());
        mediaPlayer.setDataSource(getContext(), uri);

        try {
            mediaPlayer.prepare();
        } catch (IOException expected) {
        } finally {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
            }
        }
        uri = uri.buildUpon().scheme(null).authority(null).build();
        assertFalse(mHttpOnlyWebServer.wasResourceRequested(uri.toString()));
    }

    public void testDownloadManager() throws Exception {
        Uri uri = Uri.parse(mHttpOnlyWebServer.getTestDownloadUrl("netsecpolicy", 0));
        int[] result = downloadUsingDownloadManager(uri);
        int status = result[0];
        int reason = result[1];
        uri = uri.buildUpon().scheme(null).authority(null).build();
        if (mCleartextTrafficExpectedToBePermitted) {
            assertEquals(DownloadManager.STATUS_SUCCESSFUL, status);
            assertTrue(mHttpOnlyWebServer.wasResourceRequested(uri.toString()));
        } else {
            assertEquals(DownloadManager.STATUS_FAILED, status);
            assertEquals(400, reason);
            assertFalse(mHttpOnlyWebServer.wasResourceRequested(uri.toString()));
        }
    }


    private int[] downloadUsingDownloadManager(Uri uri) throws Exception {
        DownloadManager downloadManager =
                (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
        removeAllDownloads(downloadManager);
        BroadcastReceiver downloadCompleteReceiver = null;
        try {
            final SettableFuture<Intent> downloadCompleteIntentFuture = new SettableFuture<Intent>();
            downloadCompleteReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    downloadCompleteIntentFuture.set(intent);
                }
            };
            getContext().registerReceiver(
                    downloadCompleteReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

            Intent downloadCompleteIntent;

            long downloadId = downloadManager.enqueue(new DownloadManager.Request(uri));
            downloadCompleteIntent = downloadCompleteIntentFuture.get(5, TimeUnit.SECONDS);

            assertEquals(downloadId,
                    downloadCompleteIntent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1));
            Cursor c = downloadManager.query(
                    new DownloadManager.Query().setFilterById(downloadId));
            try {
                if (!c.moveToNext()) {
                    fail("Download not found");
                    return null;
                }
                int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                int reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                return new int[] {status, reason};
            } finally {
                c.close();
            }
        } finally {
            if (downloadCompleteReceiver != null) {
                getContext().unregisterReceiver(downloadCompleteReceiver);
            }
            removeAllDownloads(downloadManager);
        }
    }

    private static void removeAllDownloads(DownloadManager downloadManager) {
        Cursor cursor = null;
        try {
            DownloadManager.Query query = new DownloadManager.Query();
            cursor = downloadManager.query(query);
            if (cursor.getCount() == 0) {
                return;
            }
            long[] removeIds = new long[cursor.getCount()];
            int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID);
            for (int i = 0; cursor.moveToNext(); i++) {
                removeIds[i] = cursor.getLong(columnIndex);
            }
            assertEquals(removeIds.length, downloadManager.remove(removeIds));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static class SettableFuture<T> implements Future<T> {

        private final Object mLock = new Object();
        private boolean mDone;
        private boolean mCancelled;
        private T mValue;
        private Throwable mException;

        public void set(T value) {
            synchronized (mLock) {
                if (!mDone) {
                    mValue = value;
                    mDone = true;
                    mLock.notifyAll();
                }
            }
        }

        public void setException(Throwable exception) {
            synchronized (mLock) {
                if (!mDone) {
                    mException = exception;
                    mDone = true;
                    mLock.notifyAll();
                }
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            synchronized (mLock) {
                if (mDone) {
                    return false;
                }
                mCancelled = true;
                mDone = true;
                mLock.notifyAll();
                return true;
            }
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            synchronized (mLock) {
                while (!mDone) {
                    mLock.wait();
                }
                return getValue();
            }
        }

        @Override
        public T get(long timeout, TimeUnit timeUnit)
                throws InterruptedException, ExecutionException, TimeoutException {
            synchronized (mLock) {
                if (mDone) {
                    return getValue();
                }
                long timeoutMillis = timeUnit.toMillis(timeout);
                long deadlineTimeMillis = System.currentTimeMillis() + timeoutMillis;

                while (!mDone) {
                    long millisTillDeadline = deadlineTimeMillis - System.currentTimeMillis();
                    if ((millisTillDeadline <= 0) || (millisTillDeadline > timeoutMillis)) {
                        throw new TimeoutException();
                    }
                    mLock.wait(millisTillDeadline);
                }
                return getValue();
            }
        }

        private T getValue() throws ExecutionException {
            synchronized (mLock) {
                if (!mDone) {
                    throw new IllegalStateException("Not yet done");
                }
                if (mCancelled) {
                    throw new CancellationException();
                }
                if (mException != null) {
                    throw new ExecutionException(mException);
                }
                return mValue;
            }
        }

        @Override
        public boolean isCancelled() {
            synchronized (mLock) {
                return mCancelled;
            }
        }

        @Override
        public boolean isDone() {
            synchronized (mLock) {
                return mDone;
            }
        }
    }
}
