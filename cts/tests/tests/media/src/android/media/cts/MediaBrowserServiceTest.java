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
package android.media.cts;

import android.content.ComponentName;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.os.Bundle;
import android.service.media.MediaBrowserService;
import android.service.media.MediaBrowserService.BrowserRoot;
import android.test.InstrumentationTestCase;

import java.util.List;

/**
 * Test {@link android.media.browse.MediaBrowserService}.
 */
public class MediaBrowserServiceTest extends InstrumentationTestCase {
    // The maximum time to wait for an operation.
    private static final long TIME_OUT_MS = 3000L;
    private static final long WAIT_TIME_FOR_NO_RESPONSE_MS = 500L;
    private static final ComponentName TEST_BROWSER_SERVICE = new ComponentName(
            "android.media.cts", "android.media.cts.StubMediaBrowserService");
    private final Object mWaitLock = new Object();

    private final MediaBrowser.ConnectionCallback mConnectionCallback =
            new MediaBrowser.ConnectionCallback() {
        @Override
        public void onConnected() {
            synchronized (mWaitLock) {
                mMediaBrowserService = StubMediaBrowserService.sInstance;
                mWaitLock.notify();
            }
        }
    };

    private final MediaBrowser.SubscriptionCallback mSubscriptionCallback =
            new MediaBrowser.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(String parentId, List<MediaItem> children) {
                synchronized (mWaitLock) {
                    mOnChildrenLoaded = true;
                    if (children != null) {
                        for (MediaItem item : children) {
                            assertRootHints(item);
                        }
                    }
                    mWaitLock.notify();
                }
            }

            @Override
            public void onChildrenLoaded(String parentId, List<MediaItem> children,
                    Bundle options) {
                synchronized (mWaitLock) {
                    mOnChildrenLoadedWithOptions = true;
                    if (children != null) {
                        for (MediaItem item : children) {
                            assertRootHints(item);
                        }
                    }
                    mWaitLock.notify();
                }
            }
        };

    private final MediaBrowser.ItemCallback mItemCallback = new MediaBrowser.ItemCallback() {
        @Override
        public void onItemLoaded(MediaItem item) {
            synchronized (mWaitLock) {
                mOnItemLoaded = true;
                assertRootHints(item);
                mWaitLock.notify();
            }
        }
    };

    private MediaBrowser mMediaBrowser;
    private StubMediaBrowserService mMediaBrowserService;
    private boolean mOnChildrenLoaded;
    private boolean mOnChildrenLoadedWithOptions;
    private boolean mOnItemLoaded;
    private Bundle mRootHints;

    @Override
    protected void setUp() throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mRootHints = new Bundle();
                mRootHints.putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, true);
                mRootHints.putBoolean(MediaBrowserService.BrowserRoot.EXTRA_OFFLINE, true);
                mRootHints.putBoolean(MediaBrowserService.BrowserRoot.EXTRA_SUGGESTED, true);
                mMediaBrowser = new MediaBrowser(getInstrumentation().getTargetContext(),
                        TEST_BROWSER_SERVICE, mConnectionCallback, mRootHints);
            }
        });
        synchronized (mWaitLock) {
            mMediaBrowser.connect();
            mWaitLock.wait(TIME_OUT_MS);
        }
        assertNotNull(mMediaBrowserService);
    }

    public void testGetSessionToken() {
        assertEquals(StubMediaBrowserService.sSession.getSessionToken(),
                mMediaBrowserService.getSessionToken());
    }

    public void testNotifyChildrenChanged() throws Exception {
        synchronized (mWaitLock) {
            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, mSubscriptionCallback);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mOnChildrenLoaded);

            mOnChildrenLoaded = false;
            mMediaBrowserService.notifyChildrenChanged(StubMediaBrowserService.MEDIA_ID_ROOT);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mOnChildrenLoaded);
        }
    }

    public void testNotifyChildrenChangedWithPagination() throws Exception {
        synchronized (mWaitLock) {
            final int pageSize = 5;
            final int page = 2;
            Bundle options = new Bundle();
            options.putInt(MediaBrowser.EXTRA_PAGE_SIZE, pageSize);
            options.putInt(MediaBrowser.EXTRA_PAGE, page);

            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, options,
                    mSubscriptionCallback);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mOnChildrenLoadedWithOptions);

            mOnChildrenLoadedWithOptions = false;
            mMediaBrowserService.notifyChildrenChanged(StubMediaBrowserService.MEDIA_ID_ROOT);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mOnChildrenLoadedWithOptions);
        }
    }

    public void testDelayedNotifyChildrenChanged() throws Exception {
        synchronized (mWaitLock) {
            mOnChildrenLoaded = false;
            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_CHILDREN_DELAYED,
                    mSubscriptionCallback);
            mWaitLock.wait(WAIT_TIME_FOR_NO_RESPONSE_MS);
            assertFalse(mOnChildrenLoaded);

            mMediaBrowserService.sendDelayedNotifyChildrenChanged();
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mOnChildrenLoaded);

            mOnChildrenLoaded = false;
            mMediaBrowserService.notifyChildrenChanged(
                    StubMediaBrowserService.MEDIA_ID_CHILDREN_DELAYED);
            mWaitLock.wait(WAIT_TIME_FOR_NO_RESPONSE_MS);
            assertFalse(mOnChildrenLoaded);

            mMediaBrowserService.sendDelayedNotifyChildrenChanged();
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mOnChildrenLoaded);
        }
    }

    public void testDelayedItem() throws Exception {
        synchronized (mWaitLock) {
            mOnItemLoaded = false;
            mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_CHILDREN_DELAYED,
                    mItemCallback);
            mWaitLock.wait(WAIT_TIME_FOR_NO_RESPONSE_MS);
            assertFalse(mOnItemLoaded);

            mMediaBrowserService.sendDelayedItemLoaded();
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mOnItemLoaded);
        }
    }

    public void testBrowserRoot() {
        final String id = "test-id";
        final String key = "test-key";
        final String val = "test-val";
        final Bundle extras = new Bundle();
        extras.putString(key, val);

        MediaBrowserService.BrowserRoot browserRoot = new BrowserRoot(id, extras);
        assertEquals(id, browserRoot.getRootId());
        assertEquals(val, browserRoot.getExtras().getString(key));
    }

    private void assertRootHints(MediaItem item) {
        Bundle rootHints = item.getDescription().getExtras();
        assertNotNull(rootHints);
        assertEquals(mRootHints.getBoolean(BrowserRoot.EXTRA_RECENT),
                rootHints.getBoolean(BrowserRoot.EXTRA_RECENT));
        assertEquals(mRootHints.getBoolean(BrowserRoot.EXTRA_OFFLINE),
                rootHints.getBoolean(BrowserRoot.EXTRA_OFFLINE));
        assertEquals(mRootHints.getBoolean(BrowserRoot.EXTRA_SUGGESTED),
                rootHints.getBoolean(BrowserRoot.EXTRA_SUGGESTED));
    }
}
