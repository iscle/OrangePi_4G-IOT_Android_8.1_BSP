package android.autofillservice.cts;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper used to block tests until a secure settings value has been updated.
 */
final class OneTimeSettingsListener extends ContentObserver {
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final ContentResolver mResolver;
    private final String mKey;

    public OneTimeSettingsListener(Context context, String key) {
        super(new Handler(Looper.getMainLooper()));
        mKey = key;
        mResolver = context.getContentResolver();
        mResolver.registerContentObserver(Settings.Secure.getUriFor(key), false, this);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        mResolver.unregisterContentObserver(this);
        mLatch.countDown();
    }

    /**
     * Blocks for a few seconds until it's called.
     *
     * @throws IllegalStateException if it's not called.
     */
    void assertCalled() {
        try {
            final boolean updated = mLatch.await(5, TimeUnit.SECONDS);
            if (!updated) {
                throw new IllegalStateException("Settings " + mKey + " not called in 5s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }
}