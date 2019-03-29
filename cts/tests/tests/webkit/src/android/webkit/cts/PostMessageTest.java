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

package android.webkit.cts;

import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.webkit.WebMessage;
import android.webkit.WebMessagePort;
import android.webkit.WebView;

import com.android.compatibility.common.util.NullWebViewUtils;
import com.android.compatibility.common.util.PollingCheck;

import java.util.concurrent.CountDownLatch;
import junit.framework.Assert;

public class PostMessageTest extends ActivityInstrumentationTestCase2<WebViewCtsActivity> {
    public static final long TIMEOUT = 20000L;

    private WebView mWebView;
    private WebViewOnUiThread mOnUiThread;

    private static final String WEBVIEW_MESSAGE = "from_webview";
    private static final String BASE_URI = "http://www.example.com";

    public PostMessageTest() {
        super("android.webkit.cts", WebViewCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final WebViewCtsActivity activity = getActivity();
        mWebView = activity.getWebView();
        if (mWebView != null) {
            mOnUiThread = new WebViewOnUiThread(this, mWebView);
            mOnUiThread.getSettings().setJavaScriptEnabled(true);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mOnUiThread != null) {
            mOnUiThread.cleanUp();
        }
        super.tearDown();
    }

    private static final String TITLE_FROM_POST_MESSAGE =
            "<!DOCTYPE html><html><body>"
            + "    <script>"
            + "        var received = '';"
            + "        onmessage = function (e) {"
            + "            received += e.data;"
            + "            document.title = received; };"
            + "    </script>"
            + "</body></html>";

    // Acks each received message from the message channel with a seq number.
    private static final String CHANNEL_MESSAGE =
            "<!DOCTYPE html><html><body>"
            + "    <script>"
            + "        var counter = 0;"
            + "        onmessage = function (e) {"
            + "            var myPort = e.ports[0];"
            + "            myPort.onmessage = function (f) {"
            + "                myPort.postMessage(f.data + counter++);"
            + "            }"
            + "        }"
            + "   </script>"
            + "</body></html>";

    private void loadPage(String data) {
        mOnUiThread.loadDataWithBaseURLAndWaitForCompletion(BASE_URI, data,
                "text/html", "UTF-8", null);
    }

    private void waitForTitle(final String title) {
        new PollingCheck(TIMEOUT) {
            @Override
            protected boolean check() {
                return mOnUiThread.getTitle().equals(title);
            }
        }.run();
    }

    // Post a string message to main frame and make sure it is received.
    public void testSimpleMessageToMainFrame() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        loadPage(TITLE_FROM_POST_MESSAGE);
        WebMessage message = new WebMessage(WEBVIEW_MESSAGE);
        mOnUiThread.postWebMessage(message, Uri.parse(BASE_URI));
        waitForTitle(WEBVIEW_MESSAGE);
    }

    // Post multiple messages to main frame and make sure they are received in
    // correct order.
    public void testMultipleMessagesToMainFrame() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        loadPage(TITLE_FROM_POST_MESSAGE);
        for (int i = 0; i < 10; i++) {
            mOnUiThread.postWebMessage(new WebMessage(Integer.toString(i)),
                    Uri.parse(BASE_URI));
        }
        waitForTitle("0123456789");
    }

    // Create a message channel and make sure it can be used for data transfer to/from js.
    public void testMessageChannel() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        loadPage(CHANNEL_MESSAGE);
        final WebMessagePort[] channel = mOnUiThread.createWebMessageChannel();
        WebMessage message = new WebMessage(WEBVIEW_MESSAGE, new WebMessagePort[]{channel[1]});
        mOnUiThread.postWebMessage(message, Uri.parse(BASE_URI));
        final int messageCount = 3;
        final CountDownLatch latch = new CountDownLatch(messageCount);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < messageCount; i++) {
                    channel[0].postMessage(new WebMessage(WEBVIEW_MESSAGE + i));
                }
                channel[0].setWebMessageCallback(new WebMessagePort.WebMessageCallback() {
                    @Override
                    public void onMessage(WebMessagePort port, WebMessage message) {
                        int i = messageCount - (int)latch.getCount();
                        assertEquals(WEBVIEW_MESSAGE + i + i, message.getData());
                        latch.countDown();
                    }
                });
            }
        });
        // Wait for all the responses to arrive.
        boolean ignore = latch.await(TIMEOUT, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    // Test that a message port that is closed cannot used to send a message
    public void testClose() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        loadPage(CHANNEL_MESSAGE);
        final WebMessagePort[] channel = mOnUiThread.createWebMessageChannel();
        WebMessage message = new WebMessage(WEBVIEW_MESSAGE, new WebMessagePort[]{channel[1]});
        mOnUiThread.postWebMessage(message, Uri.parse(BASE_URI));
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    channel[0].close();
                    channel[0].postMessage(new WebMessage(WEBVIEW_MESSAGE));
                } catch (IllegalStateException ex) {
                    // expect to receive an exception
                    return;
                }
                Assert.fail("A closed port cannot be used to transfer messages");
            }
         });
    }

    // Sends a new message channel from JS to Java.
    private static final String CHANNEL_FROM_JS =
            "<!DOCTYPE html><html><body>"
            + "    <script>"
            + "        var counter = 0;"
            + "        var mc = new MessageChannel();"
            + "        var received = '';"
            + "        mc.port1.onmessage = function (e) {"
            + "               received = e.data;"
            + "               document.title = e.data;"
            + "        };"
            + "        onmessage = function (e) {"
            + "            var myPort = e.ports[0];"
            + "            myPort.postMessage('', [mc.port2]);"
            + "        };"
            + "   </script>"
            + "</body></html>";

    // Test a message port created in JS can be received and used for message transfer.
    public void testReceiveMessagePort() throws Throwable {
        final String hello = "HELLO";
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        loadPage(CHANNEL_FROM_JS);
        final WebMessagePort[] channel = mOnUiThread.createWebMessageChannel();
        WebMessage message = new WebMessage(WEBVIEW_MESSAGE, new WebMessagePort[]{channel[1]});
        mOnUiThread.postWebMessage(message, Uri.parse(BASE_URI));
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                channel[0].setWebMessageCallback(new WebMessagePort.WebMessageCallback() {
                    @Override
                    public void onMessage(WebMessagePort port, WebMessage message) {
                        message.getPorts()[0].postMessage(new WebMessage(hello));
                    }
                });
            }
        });
        waitForTitle(hello);
    }
}
