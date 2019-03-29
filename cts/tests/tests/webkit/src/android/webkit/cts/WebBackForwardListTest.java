/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.webkit.cts;

import android.test.ActivityInstrumentationTestCase2;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.webkit.WebView;

import com.android.compatibility.common.util.NullWebViewUtils;
import com.android.compatibility.common.util.PollingCheck;

public class WebBackForwardListTest extends ActivityInstrumentationTestCase2<WebViewCtsActivity> {

    private static final int TEST_TIMEOUT = 10000;

    private WebViewOnUiThread mOnUiThread;

    public WebBackForwardListTest() {
        super("android.webkit.cts", WebViewCtsActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        WebView webview = getActivity().getWebView();
        if (webview != null) {
            mOnUiThread = new WebViewOnUiThread(this, webview);
        }
    }

    @Override
    public void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
        super.tearDown();
    }

    public void testGetCurrentItem() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        WebBackForwardList list = mOnUiThread.copyBackForwardList();

        assertNull(list.getCurrentItem());
        assertEquals(0, list.getSize());
        assertEquals(-1, list.getCurrentIndex());
        assertNull(list.getItemAtIndex(-1));
        assertNull(list.getItemAtIndex(2));

        CtsTestServer server = new CtsTestServer(getActivity(), false);
        try {
            String url1 = server.getAssetUrl(TestHtmlConstants.HTML_URL1);
            String url2 = server.getAssetUrl(TestHtmlConstants.HTML_URL2);
            String url3 = server.getAssetUrl(TestHtmlConstants.HTML_URL3);

            mOnUiThread.loadUrlAndWaitForCompletion(url1);
            checkBackForwardList(url1);

            mOnUiThread.loadUrlAndWaitForCompletion(url2);
            checkBackForwardList(url1, url2);

            mOnUiThread.loadUrlAndWaitForCompletion(url3);
            checkBackForwardList(url1, url2, url3);
        } finally {
            server.shutdown();
        }
    }

    private void checkBackForwardList(final String... url) {
        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                if (mOnUiThread.getProgress() < 100) {
                    return false;
                }
                WebBackForwardList list = mOnUiThread.copyBackForwardList();
                if (list.getSize() != url.length) {
                    return false;
                }
                if (list.getCurrentIndex() != url.length - 1) {
                    return false;
                }
                for (int i = 0; i < url.length; i++) {
                    WebHistoryItem item = list.getItemAtIndex(i);
                    if (!url[i].equals(item.getUrl())) {
                        return false;
                    }
                }
                return true;
            }

        }.run();
    }

    public void testClone() {
    }

}
