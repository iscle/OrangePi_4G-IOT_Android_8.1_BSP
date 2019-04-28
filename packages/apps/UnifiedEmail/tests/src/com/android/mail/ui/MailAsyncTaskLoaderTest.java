package com.android.mail.ui;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.test.ActivityUnitTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.google.common.annotations.VisibleForTesting;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MailAsyncTaskLoaderTest
        extends ActivityUnitTestCase<MailAsyncTaskLoaderTest.LoaderTestActivity> {

    public static class LoaderTestActivity extends Activity {
        final CountDownLatch loadFinishedLatch = new CountDownLatch(1);
        final CountDownLatch resultDiscardedLatch = new CountDownLatch(1);
        volatile Object result;

        public void runLoaderTest() {
            result = new Object();

            getLoaderManager().initLoader(0, null, new LoaderManager.LoaderCallbacks<Object>() {
                @Override
                public Loader<Object> onCreateLoader(int id, Bundle args) {
                    return new MailAsyncTaskLoader<Object>(LoaderTestActivity.this) {
                        @Override
                        protected void onDiscardResult(Object result) {
                            MailAsyncTaskLoaderTest.assertNotNull(result);
                            resultDiscardedLatch.countDown();
                        }

                        @Override
                        public Object loadInBackground() {
                            return result;
                        }
                    };
                }

                @Override
                public void onLoadFinished(Loader<Object> loader, Object data) {
                    MailAsyncTaskLoaderTest.assertEquals(data, result);
                    loadFinishedLatch.countDown();
                }

                @Override
                public void onLoaderReset(Loader<Object> loader) {}
            });
            while (true) {
                try {
                    MailAsyncTaskLoaderTest.assertTrue(
                            loadFinishedLatch.await(30, TimeUnit.SECONDS));
                } catch (final InterruptedException e) {
                    continue;
                }
                break;
            }
            getLoaderManager().destroyLoader(0);
            while (true) {
                try {
                    MailAsyncTaskLoaderTest.assertTrue(
                            resultDiscardedLatch.await(30, TimeUnit.SECONDS));
                } catch (final InterruptedException e) {
                    continue;
                }
                break;
            }
        }
    }

    public MailAsyncTaskLoaderTest() {
        super(LoaderTestActivity.class);
    }

    @SmallTest
    public void testLoader() {
        startActivity(new Intent(Intent.ACTION_MAIN), null, null);
        getInstrumentation().callActivityOnStart(getActivity());
        getActivity().runLoaderTest();
    }
}
