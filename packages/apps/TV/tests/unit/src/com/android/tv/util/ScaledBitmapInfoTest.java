package com.android.tv.util;

import static org.junit.Assert.assertEquals;

import android.graphics.Bitmap;
import android.support.test.filters.SmallTest;

import com.android.tv.util.BitmapUtils.ScaledBitmapInfo;

import org.junit.Test;

/**
 * Tests for {@link ScaledBitmapInfo}.
 */
@SmallTest
public class ScaledBitmapInfoTest {
    private static final Bitmap B80x100 = Bitmap.createBitmap(80, 100, Bitmap.Config.RGB_565);
    private static final Bitmap B960x1440 = Bitmap.createBitmap(960, 1440, Bitmap.Config.RGB_565);

    @Test
    public void testSize_B100x100to50x50() {
        ScaledBitmapInfo actual = BitmapUtils.createScaledBitmapInfo("B80x100", B80x100, 50, 50);
        assertScaledBitmapSize(2, 40, 50, actual);
    }

    @Test
    public void testNeedsToReload_B100x100to50x50() {
        ScaledBitmapInfo actual = BitmapUtils.createScaledBitmapInfo("B80x100", B80x100, 50, 50);
        assertNeedsToReload(false, actual, 25, 25);
        assertNeedsToReload(false, actual, 50, 50);
        assertNeedsToReload(false, actual, 99, 99);
        assertNeedsToReload(true, actual, 100, 100);
        assertNeedsToReload(true, actual, 101, 101);
    }

    /**
     * Reproduces <a href="http://b/20488453">b/20488453</a>.
     */
    @Test
    public void testBug20488453() {
        ScaledBitmapInfo actual = BitmapUtils
                .createScaledBitmapInfo("B960x1440", B960x1440, 284, 160);
        assertScaledBitmapSize(8, 107, 160, actual);
        assertNeedsToReload(false, actual, 284, 160);
    }

    private static void assertNeedsToReload(boolean expected, ScaledBitmapInfo scaledBitmap,
            int reqWidth, int reqHeight) {
        assertEquals(scaledBitmap.id + " needToReload(" + reqWidth + "," + reqHeight + ")",
                expected, scaledBitmap.needToReload(reqWidth, reqHeight));
    }

    private static void assertScaledBitmapSize(int expectedInSampleSize, int expectedWidth,
            int expectedHeight, ScaledBitmapInfo actual) {
        assertEquals(actual.id + " inSampleSize", expectedInSampleSize, actual.inSampleSize);
        assertEquals(actual.id + " width", expectedWidth, actual.bitmap.getWidth());
        assertEquals(actual.id + " height", expectedHeight, actual.bitmap.getHeight());
    }
}
