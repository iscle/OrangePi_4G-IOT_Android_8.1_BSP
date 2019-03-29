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
package android.app.cts;

import android.app.Instrumentation;
import android.app.ListActivity;
import android.app.stubs.MockListActivity;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import junit.framework.Assert;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SmallTest
public class ListActivityTest extends ActivityInstrumentationTestCase2<MockListActivity> {
    private ListActivity mActivity;

    public ListActivityTest() {
        super(MockListActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
    }

    public void testAdapter() throws Throwable {
        final ListAdapter listAdapter = new ArrayAdapter<String>(mActivity,
                android.R.layout.simple_list_item_1,
                new String[] { "Mercury", "Mars", "Earth", "Venus"});
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.setListAdapter(listAdapter);
            }
        });
        assertEquals(listAdapter, mActivity.getListAdapter());
        assertEquals(listAdapter, mActivity.getListView().getAdapter());
    }

    public void testSelection() throws Throwable {
        final ListAdapter listAdapter = new ArrayAdapter<String>(mActivity,
                android.R.layout.simple_list_item_1,
                new String[] { "Alpha", "Bravo", "Charlie", "Delta", "Echo"});
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.setListAdapter(listAdapter);
            }
        });
        assertEquals(0, mActivity.getSelectedItemPosition());
        assertEquals(0, mActivity.getSelectedItemId());

        runOnMainAndDrawSync(getInstrumentation(), mActivity.getListView(), new Runnable() {
            @Override
            public void run() {
                mActivity.setSelection(2);
            }
        });
        assertEquals(2, mActivity.getSelectedItemPosition());
        assertEquals(2, mActivity.getSelectedItemId());
    }

    public void testItemClick() throws Throwable {
        final ListAdapter listAdapter = new ArrayAdapter<String>(mActivity,
                android.R.layout.simple_list_item_1,
                new String[] { "Froyo", "Gingerbread", "Ice Cream Sandwich" });
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.setListAdapter(listAdapter);
            }
        });

        final MockListActivity mockListActivity = (MockListActivity) mActivity;
        assertFalse(mockListActivity.isOnListItemClickCalled);

        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.getListView().performItemClick(null, 1, 1);
            }
        });

        assertTrue(mockListActivity.isOnListItemClickCalled);
        assertEquals(1, mockListActivity.itemClickCallCount);
        assertEquals(1, mockListActivity.clickedItemPosition);
    }

    private static void runOnMainAndDrawSync(Instrumentation instrumentation,
            final View view, final Runnable runner) {
        final Semaphore token = new Semaphore(0);

        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                final ViewTreeObserver observer = view.getViewTreeObserver();
                final ViewTreeObserver.OnDrawListener listener =
                        new ViewTreeObserver.OnDrawListener() {
                    @Override
                    public void onDraw() {
                        view.post(() -> view.getViewTreeObserver().removeOnDrawListener(this));
                        view.post(() -> token.release());
                    }
                };

                observer.addOnDrawListener(listener);
                runner.run();
            }
        });

        try {
            Assert.assertTrue("Expected draw pass occurred within 5 seconds",
                    token.tryAcquire(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
