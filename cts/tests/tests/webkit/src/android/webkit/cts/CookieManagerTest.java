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
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;
import android.webkit.ValueCallback;

import com.android.compatibility.common.util.NullWebViewUtils;
import com.android.compatibility.common.util.PollingCheck;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CookieManagerTest extends
        ActivityInstrumentationTestCase2<CookieSyncManagerCtsActivity> {

    private static final int TEST_TIMEOUT = 5000;

    private WebView mWebView;
    private CookieManager mCookieManager;
    private WebViewOnUiThread mOnUiThread;

    public CookieManagerTest() {
        super("android.webkit.cts", CookieSyncManagerCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mWebView = getActivity().getWebView();
        if (mWebView != null) {
            mOnUiThread = new WebViewOnUiThread(this, mWebView);

            mCookieManager = CookieManager.getInstance();
            assertNotNull(mCookieManager);

            // We start with no cookies.
            mCookieManager.removeAllCookie();
            assertFalse(mCookieManager.hasCookies());

            // But accepting cookies.
            mCookieManager.setAcceptCookie(false);
            assertFalse(mCookieManager.acceptCookie());
        }
    }

    public void testGetInstance() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        mOnUiThread.cleanUp();
        CookieManager c1 = CookieManager.getInstance();
        CookieManager c2 = CookieManager.getInstance();

        assertSame(c1, c2);
    }

    public void testClone() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
    }

    public void testFlush() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        mCookieManager.flush();
    }

    public void testAcceptCookie() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        mCookieManager.setAcceptCookie(false);
        assertFalse(mCookieManager.acceptCookie());

        CtsTestServer server = new CtsTestServer(getActivity(), false);
        String url = server.getCookieUrl("conquest.html");
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals("0", mOnUiThread.getTitle()); // no cookies passed
        Thread.sleep(500);
        assertNull(mCookieManager.getCookie(url));

        mCookieManager.setAcceptCookie(true);
        assertTrue(mCookieManager.acceptCookie());

        url = server.getCookieUrl("war.html");
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals("0", mOnUiThread.getTitle()); // no cookies passed
        waitForCookie(url);
        String cookie = mCookieManager.getCookie(url);
        assertNotNull(cookie);
        // 'count' value of the returned cookie is 0
        final Pattern pat = Pattern.compile("count=(\\d+)");
        Matcher m = pat.matcher(cookie);
        assertTrue(m.matches());
        assertEquals("0", m.group(1));

        url = server.getCookieUrl("famine.html");
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals("1|count=0", mOnUiThread.getTitle()); // outgoing cookie
        waitForCookie(url);
        cookie = mCookieManager.getCookie(url);
        assertNotNull(cookie);
        m = pat.matcher(cookie);
        assertTrue(m.matches());
        assertEquals("1", m.group(1)); // value got incremented

        url = server.getCookieUrl("death.html");
        mCookieManager.setCookie(url, "count=41");
        mOnUiThread.loadUrlAndWaitForCompletion(url);
        assertEquals("1|count=41", mOnUiThread.getTitle()); // outgoing cookie
        waitForCookie(url);
        cookie = mCookieManager.getCookie(url);
        assertNotNull(cookie);
        m = pat.matcher(cookie);
        assertTrue(m.matches());
        assertEquals("42", m.group(1)); // value got incremented
    }

    public void testSetCookie() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        String url = "http://www.example.com";
        String cookie = "name=test";
        mCookieManager.setCookie(url, cookie);
        assertEquals(cookie, mCookieManager.getCookie(url));
        assertTrue(mCookieManager.hasCookies());
    }

    public void testSetCookieNullCallback() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        final String url = "http://www.example.com";
        final String cookie = "name=test";
        mCookieManager.setCookie(url, cookie, null);
        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                String c = mCookieManager.getCookie(url);
                return mCookieManager.getCookie(url).contains(cookie);
            }
        }.run();
    }

    public void testSetCookieCallback() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        final Semaphore s = new Semaphore(0);
        final AtomicBoolean status = new AtomicBoolean();
        final ValueCallback<Boolean> callback = new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(Boolean success) {
                status.set(success);
                s.release();
            }
        };
    }

    public void testRemoveCookies() throws InterruptedException {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        final String url = "http://www.example.com";
        final String sessionCookie = "cookie1=peter";
        final String longCookie = "cookie2=sue";
        final String quickCookie = "cookie3=marc";

        mCookieManager.setCookie(url, sessionCookie);
        mCookieManager.setCookie(url, makeExpiringCookie(longCookie, 600));
        mCookieManager.setCookie(url, makeExpiringCookieMs(quickCookie, 1500));

        String allCookies = mCookieManager.getCookie(url);
        assertTrue(allCookies.contains(sessionCookie));
        assertTrue(allCookies.contains(longCookie));
        assertTrue(allCookies.contains(quickCookie));

        mCookieManager.removeSessionCookie();
        allCookies = mCookieManager.getCookie(url);
        assertFalse(allCookies.contains(sessionCookie));
        assertTrue(allCookies.contains(longCookie));
        assertTrue(allCookies.contains(quickCookie));

        Thread.sleep(2000); // wait for quick cookie to expire
        mCookieManager.removeExpiredCookie();
        allCookies = mCookieManager.getCookie(url);
        assertFalse(allCookies.contains(sessionCookie));
        assertTrue(allCookies.contains(longCookie));
        assertFalse(allCookies.contains(quickCookie));

        mCookieManager.removeAllCookie();
        assertNull(mCookieManager.getCookie(url));
        assertFalse(mCookieManager.hasCookies());
    }

    public void testRemoveCookiesNullCallback() throws InterruptedException {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        final String url = "http://www.example.com";
        final String sessionCookie = "cookie1=peter";
        final String longCookie = "cookie2=sue";
        final String quickCookie = "cookie3=marc";

        mCookieManager.setCookie(url, sessionCookie);
        mCookieManager.setCookie(url, makeExpiringCookie(longCookie, 600));
        mCookieManager.setCookie(url, makeExpiringCookieMs(quickCookie, 1500));

        String allCookies = mCookieManager.getCookie(url);
        assertTrue(allCookies.contains(sessionCookie));
        assertTrue(allCookies.contains(longCookie));
        assertTrue(allCookies.contains(quickCookie));

        mCookieManager.removeSessionCookies(null);
        allCookies = mCookieManager.getCookie(url);
        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                String c = mCookieManager.getCookie(url);
                return !c.contains(sessionCookie) &&
                        c.contains(longCookie) &&
                        c.contains(quickCookie);
            }
        }.run();

        mCookieManager.removeAllCookies(null);
        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return !mCookieManager.hasCookies();
            }
        }.run();
        assertNull(mCookieManager.getCookie(url));
    }

    public void testRemoveCookiesCallback() throws InterruptedException {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }

        final Semaphore s = new Semaphore(0);
        final AtomicBoolean anyDeleted = new AtomicBoolean();
        final ValueCallback<Boolean> callback = new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(Boolean n) {
                anyDeleted.set(n);
                s.release();
            }
        };

        final String url = "http://www.example.com";
        final String sessionCookie = "cookie1=peter";
        final String normalCookie = "cookie2=sue";

        // We set one session cookie and one normal cookie.
        mCookieManager.setCookie(url, sessionCookie);
        mCookieManager.setCookie(url, makeExpiringCookie(normalCookie, 600));

        String allCookies = mCookieManager.getCookie(url);
        assertTrue(allCookies.contains(sessionCookie));
        assertTrue(allCookies.contains(normalCookie));

        // When we remove session cookies there are some to remove.
        removeSessionCookiesOnUiThread(callback);
        assertTrue(s.tryAcquire(TEST_TIMEOUT, TimeUnit.MILLISECONDS));
        assertTrue(anyDeleted.get());

        // The normal cookie is not removed.
        assertTrue(mCookieManager.getCookie(url).contains(normalCookie));

        // When we remove session cookies again there are none to remove.
        removeSessionCookiesOnUiThread(callback);
        assertTrue(s.tryAcquire(TEST_TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(anyDeleted.get());

        // When we remove all cookies there are some to remove.
        removeAllCookiesOnUiThread(callback);
        assertTrue(s.tryAcquire(TEST_TIMEOUT, TimeUnit.MILLISECONDS));
        assertTrue(anyDeleted.get());

        // Now we have no cookies.
        assertFalse(mCookieManager.hasCookies());
        assertNull(mCookieManager.getCookie(url));

        // When we remove all cookies again there are none to remove.
        removeAllCookiesOnUiThread(callback);
        assertTrue(s.tryAcquire(TEST_TIMEOUT, TimeUnit.MILLISECONDS));
        assertFalse(anyDeleted.get());
    }

    public void testThirdPartyCookie() throws Throwable {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        CtsTestServer server = null;
        try {
            // In theory we need two servers to test this, one server ('the first party')
            // which returns a response with a link to a second server ('the third party')
            // at different origin. This second server attempts to set a cookie which should
            // fail if AcceptThirdPartyCookie() is false.
            // Strictly according to the letter of RFC6454 it should be possible to set this
            // situation up with two TestServers on different ports (these count as having
            // different origins) but Chrome is not strict about this and does not check the
            // port. Instead we cheat making some of the urls come from localhost and some
            // from 127.0.0.1 which count (both in theory and pratice) as having different
            // origins.
            server = new CtsTestServer(getActivity());

            // Turn on Javascript (otherwise <script> aren't fetched spoiling the test).
            mOnUiThread.getSettings().setJavaScriptEnabled(true);

            // Turn global allow on.
            mCookieManager.setAcceptCookie(true);
            assertTrue(mCookieManager.acceptCookie());

            // When third party cookies are disabled...
            mOnUiThread.setAcceptThirdPartyCookies(false);
            assertFalse(mOnUiThread.acceptThirdPartyCookies());

            // ...we can't set third party cookies.
            // First on the third party server we get a url which tries to set a cookie.
            String cookieUrl = toThirdPartyUrl(
                    server.getSetCookieUrl("cookie_1.js", "test1", "value1"));
            // Then we create a url on the first party server which links to the first url.
            String url = server.getLinkedScriptUrl("/content_1.html", cookieUrl);
            mOnUiThread.loadUrlAndWaitForCompletion(url);
            assertNull(mCookieManager.getCookie(cookieUrl));

            // When third party cookies are enabled...
            mOnUiThread.setAcceptThirdPartyCookies(true);
            assertTrue(mOnUiThread.acceptThirdPartyCookies());

            // ...we can set third party cookies.
            cookieUrl = toThirdPartyUrl(
                    server.getSetCookieUrl("/cookie_2.js", "test2", "value2"));
            url = server.getLinkedScriptUrl("/content_2.html", cookieUrl);
            mOnUiThread.loadUrlAndWaitForCompletion(url);
            waitForCookie(cookieUrl);
            String cookie = mCookieManager.getCookie(cookieUrl);
            assertNotNull(cookie);
            assertTrue(cookie.contains("test2"));
        } finally {
            if (server != null) server.shutdown();
            mOnUiThread.getSettings().setJavaScriptEnabled(false);
        }
    }

    public void testb3167208() throws Exception {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        String uri = "http://host.android.com/path/";
        // note the space after the domain=
        String problemCookie = "foo=bar; domain= .android.com; path=/";
        mCookieManager.setCookie(uri, problemCookie);
        String cookie = mCookieManager.getCookie(uri);
        assertNotNull(cookie);
        assertTrue(cookie.contains("foo=bar"));
    }

    private void waitForCookie(final String url) {
        new PollingCheck(TEST_TIMEOUT) {
            @Override
            protected boolean check() {
                return mCookieManager.getCookie(url) != null;
            }
        }.run();
    }

    @SuppressWarnings("deprecation")
    private String makeExpiringCookie(String cookie, int secondsTillExpiry) {
        return makeExpiringCookieMs(cookie, 1000*secondsTillExpiry);
    }

    @SuppressWarnings("deprecation")
    private String makeExpiringCookieMs(String cookie, int millisecondsTillExpiry) {
        Date date = new Date();
        date.setTime(date.getTime() + millisecondsTillExpiry);
        return cookie + "; expires=" + date.toGMTString();
    }

    private void removeAllCookiesOnUiThread(final ValueCallback<Boolean> callback) {
        runTestOnUiThreadAndCatch(new Runnable() {
            @Override
            public void run() {
                mCookieManager.removeAllCookies(callback);
            }
        });
    }

    private void removeSessionCookiesOnUiThread(final ValueCallback<Boolean> callback) {
        runTestOnUiThreadAndCatch(new Runnable() {
            @Override
            public void run() {
                mCookieManager.removeSessionCookies(callback);
            }
        });
    }

    private void setCookieOnUiThread(final String url, final String cookie,
            final ValueCallback<Boolean> callback) {
        runTestOnUiThreadAndCatch(new Runnable() {
            @Override
            public void run() {
                mCookieManager.setCookie(url, cookie, callback);
            }
        });
    }

    private void runTestOnUiThreadAndCatch(Runnable runnable) {
        try {
            runTestOnUiThread(runnable);
        } catch (Throwable t) {
            fail("Unexpected error while running on UI thread: " + t.getMessage());
        }
    }

    /**
     * Makes a url look as if it comes from a different host.
     * @param url the url to fake.
     * @return the resulting url after faking.
     */
    public String toThirdPartyUrl(String url) {
        return url.replace("localhost", "127.0.0.1");
    }
 }
