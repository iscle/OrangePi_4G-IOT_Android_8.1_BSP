package android.uirendering.cts.util;

import android.view.ViewTreeObserver.OnDrawListener;
import android.webkit.WebView;
import android.webkit.WebView.VisualStateCallback;
import android.webkit.WebViewClient;

import java.util.concurrent.CountDownLatch;

public final class WebViewReadyHelper {
    private final CountDownLatch mLatch;
    private final WebView mWebView;

    public WebViewReadyHelper(WebView webview, CountDownLatch latch) {
        mWebView = webview;
        mLatch = latch;
        mWebView.setWebViewClient(mClient);
    }

    public void loadData(String data) {
        mWebView.loadData(data, null, null);
    }

    private WebViewClient mClient = new WebViewClient() {
        public void onPageFinished(WebView view, String url) {
            mWebView.postVisualStateCallback(0, mVisualStateCallback);
        }
    };

    private VisualStateCallback mVisualStateCallback = new VisualStateCallback() {
        @Override
        public void onComplete(long requestId) {
            mWebView.getViewTreeObserver().addOnDrawListener(mOnDrawListener);
            mWebView.invalidate();
        }
    };

    private OnDrawListener mOnDrawListener = new OnDrawListener() {
        @Override
        public void onDraw() {
            mWebView.post(() -> {
                mWebView.getViewTreeObserver().removeOnDrawListener(mOnDrawListener);
                mLatch.countDown();
            });
        }
    };
}
