package android.graphics.drawable.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.cts.R;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.graphics.drawable.StateListDrawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AdaptiveIconDrawableTest {

    public static final String TAG = AdaptiveIconDrawableTest.class.getSimpleName();
    public static void L(String s, Object... parts) {
        Log.d(TAG, (parts.length == 0) ? s : String.format(s, parts));
    }

    @Test
    public void testConstructor() {
        new AdaptiveIconDrawable(null, null);
    }

    @Test
    public void testInflate() throws Throwable {
        AdaptiveIconDrawable dr = new AdaptiveIconDrawable(null, null);

        Resources r = InstrumentationRegistry.getTargetContext().getResources();
        XmlPullParser parser = r.getXml(R.layout.framelayout_layout);
        AttributeSet attrs = Xml.asAttributeSet(parser);

        // Should not throw inflate exception
        dr.inflate(r, parser, attrs);
    }

    @Test(expected=NullPointerException.class)
    public void testInflateNull() throws Throwable {
        AdaptiveIconDrawable dr = new AdaptiveIconDrawable(null, null);
        dr.inflate(null, null, null);
    }

    @Test
    public void testDraw() {
        Canvas c = new Canvas();
        AdaptiveIconDrawable dr = new AdaptiveIconDrawable(null, null);
        dr.draw(c);
    }

    @Test(expected=NullPointerException.class)
    public void testDrawNull() {
        AdaptiveIconDrawable iconDrawable = new AdaptiveIconDrawable(
            new ColorDrawable(Color.RED), new ColorDrawable(Color.BLUE));
        iconDrawable.setBounds(0, 0, 100, 100);
        iconDrawable.draw(null);
    }

    @Test
    public void testInvalidateDrawable() {
        AdaptiveIconDrawable iconDrawable = new AdaptiveIconDrawable(
            new ColorDrawable(Color.RED), new ColorDrawable(Color.BLUE));
        iconDrawable.invalidateDrawable(
            InstrumentationRegistry.getTargetContext().getDrawable(R.drawable.pass));
    }

    @Test
    public void testScheduleDrawable() {
        AdaptiveIconDrawable iconDrawable = new AdaptiveIconDrawable(
            new ColorDrawable(Color.RED), new ColorDrawable(Color.BLUE));
        iconDrawable.scheduleDrawable(
            InstrumentationRegistry.getTargetContext().getDrawable(R.drawable.pass),
            () -> {}, 10);

        // input null as params
        iconDrawable.scheduleDrawable(null, null, -1);
        // expected, no Exception thrown out, test success
    }

    @Test
    public void testUnscheduleDrawable() {
        AdaptiveIconDrawable iconDrawable = new AdaptiveIconDrawable(
            new ColorDrawable(Color.RED), new ColorDrawable(Color.BLUE));
        iconDrawable.unscheduleDrawable(
            InstrumentationRegistry.getTargetContext().getDrawable(R.drawable.pass), () -> {});

        // input null as params
        iconDrawable.unscheduleDrawable(null, null);
        // expected, no Exception thrown out, test success
    }

    @Test
    public void testGetChangingConfigurations() {
        AdaptiveIconDrawable iconDrawable = new AdaptiveIconDrawable(
            new ColorDrawable(Color.RED), new ColorDrawable(Color.BLUE));
        iconDrawable.setChangingConfigurations(11);
        assertEquals(11, iconDrawable.getChangingConfigurations());

        iconDrawable.setChangingConfigurations(-21);
        assertEquals(-21, iconDrawable.getChangingConfigurations());
    }

    /**
     * When setBound isn't called before draw method is called.
     * Nothing is drawn.
     */
    @Test
    public void testDrawWithoutSetBounds() throws Exception {
        Drawable backgroundDrawable = new ColorDrawable(Color.BLUE);
        Drawable foregroundDrawable = new ColorDrawable(Color.RED);
        AdaptiveIconDrawable iconDrawable = new AdaptiveIconDrawable(backgroundDrawable, foregroundDrawable);
        Context context = InstrumentationRegistry.getTargetContext();
        File dir = context.getExternalFilesDir(null);
        L("writing temp bitmaps to %s...", dir);

        final Bitmap bm_test = Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888);
        final Bitmap bm_org = bm_test.copy(Config.ARGB_8888, false);
        final Canvas can1 = new Canvas(bm_test);

        // Even when setBounds is not called, should not crash
        iconDrawable.draw(can1);
        // Draws nothing! Hence same as original.
        if (!equalBitmaps(bm_test, bm_org)) {
            findBitmapDifferences(bm_test, bm_org);
            fail("bm differs, check " + dir);
        }
    }

    /**
     * When setBound is called, translate accordingly.
     */
    @Test
    public void testDrawSetBounds() throws Exception {
        int dpi = 4 ;
        int top = 18 * dpi;
        int left = 18 * dpi;
        int right = 90 * dpi;
        int bottom = 90 * dpi;
        int width = right - left;
        int height = bottom - top;
        Context context = InstrumentationRegistry.getTargetContext();
        AdaptiveIconDrawable iconDrawable = (AdaptiveIconDrawable) context.getResources().getDrawable(android.R.drawable.sym_def_app_icon);
        File dir = context.getExternalFilesDir(null);
        L("writing temp bitmaps to %s...", dir);
        final Bitmap bm_org = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas can_org = new Canvas(bm_org);
        iconDrawable.setBounds(0, 0, width, height);
        iconDrawable.draw(can_org);

        // Tested bitmap is drawn from the adaptive icon drawable.
        final Bitmap bm_test = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas can_test = new Canvas(bm_test);

        iconDrawable.setBounds(left, top, right, bottom);
        can_test.translate(-left, -top);
        iconDrawable.draw(can_test);
        can_test.translate(left, top);


        bm_org.compress(Bitmap.CompressFormat.PNG, 100,
            new FileOutputStream(new File(dir, "adaptive-bm-original.png")));
        bm_test.compress(Bitmap.CompressFormat.PNG, 100,
            new FileOutputStream(new File(dir, "adaptive-bm-test.png")));
        Region region = new Region(new Rect(0, 0, width, height));

        Path circle = new Path();
        circle.addCircle(width / 2, height / 2,  (right - left)/2 -10 /* room for anti-alias */, Direction.CW);

        region.setPath(circle, region);
        if (!equalBitmaps(bm_test, bm_org, region)) {
            findBitmapDifferences(bm_test, bm_org);
            fail("bm differs, check " + dir);
        }
    }

    @Test
    public void testSetVisible() {
        AdaptiveIconDrawable iconDrawable = new AdaptiveIconDrawable(
            new ColorDrawable(Color.RED), new ColorDrawable(Color.BLUE));
        assertFalse(iconDrawable.setVisible(true, true)); /* unchanged */
        assertTrue(iconDrawable.setVisible(false, true)); /* changed */
        assertFalse(iconDrawable.setVisible(false, true)); /* unchanged */
    }

    @Test
    public void testSetAlpha() {
        AdaptiveIconDrawable iconDrawable = new AdaptiveIconDrawable(
            new ColorDrawable(Color.RED), new ColorDrawable(Color.BLUE));
        iconDrawable.setAlpha(1);
        iconDrawable.setAlpha(-1);

        iconDrawable.setAlpha(0);
        iconDrawable.setAlpha(Integer.MAX_VALUE);
        iconDrawable.setAlpha(Integer.MIN_VALUE);
    }

    @Test
    public void testSetColorFilter() {
        AdaptiveIconDrawable iconDrawable = new AdaptiveIconDrawable(
            new ColorDrawable(Color.RED), new ColorDrawable(Color.BLUE));
        ColorFilter cf = new ColorFilter();
        iconDrawable.setColorFilter(cf);

        // input null as param
        iconDrawable.setColorFilter(null);
        // expected, no Exception thrown out, test success
    }

    @Test
    public void testGetOpacity() {
        AdaptiveIconDrawable iconDrawable = new AdaptiveIconDrawable(
            new ColorDrawable(Color.RED), new ColorDrawable(Color.BLUE));
        iconDrawable.setOpacity(PixelFormat.OPAQUE);
        assertEquals(PixelFormat.OPAQUE, iconDrawable.getOpacity());

        iconDrawable.setOpacity(PixelFormat.TRANSPARENT);
        assertEquals(PixelFormat.TRANSPARENT, iconDrawable.getOpacity());
    }

    @Test
    public void testIsStateful() {
        AdaptiveIconDrawable iconDrawable = new AdaptiveIconDrawable(
            new ColorDrawable(Color.RED), new ColorDrawable(Color.BLUE));
        assertFalse(iconDrawable.isStateful());

        iconDrawable = new AdaptiveIconDrawable(new StateListDrawable(), new ColorDrawable(Color.RED));
        assertTrue(iconDrawable.isStateful());
    }


    @Test
    public void testGetConstantState() {
        AdaptiveIconDrawable iconDrawable = new AdaptiveIconDrawable(
            new ColorDrawable(Color.RED), new ColorDrawable(Color.BLUE));
        ConstantState constantState = iconDrawable.getConstantState();
        assertNotNull(constantState);
    }

    @Test
    public void testMutate() {
        // Obtain the first instance, then mutate and modify a property held by
        // constant state. If mutate() works correctly, the property should not
        // be modified on the second or third instances.
        Resources res = InstrumentationRegistry.getTargetContext().getResources();
        AdaptiveIconDrawable first = (AdaptiveIconDrawable) res.getDrawable(R.drawable.adaptive_icon_drawable, null);
        AdaptiveIconDrawable pre = (AdaptiveIconDrawable) res.getDrawable(R.drawable.adaptive_icon_drawable, null);

        first.mutate().setBounds(0, 0, 100, 100);

        assertEquals("Modified first loaded instance", 100, first.getBounds().width());
        assertEquals("Did not modify pre-mutate() instance", 0, pre.getBounds().width());

        AdaptiveIconDrawable post = (AdaptiveIconDrawable) res.getDrawable(R.drawable.adaptive_icon_drawable, null);

        assertEquals("Did not modify post-mutate() instance", 0, post.getBounds().width());
    }

    @Test
    public void testGetForegroundBackground() {
        Context context = InstrumentationRegistry.getTargetContext();
        AdaptiveIconDrawable iconDrawable = new AdaptiveIconDrawable(
            new ColorDrawable(Color.RED), new ColorDrawable(Color.BLUE));
        Drawable fgDrawable = iconDrawable.getForeground();
        Drawable bgDrawable = iconDrawable.getBackground();
        assertTrue("Foreground layer is color drawable.", fgDrawable instanceof ColorDrawable);
        assertTrue("Backgroud layer is color drawable.", bgDrawable instanceof ColorDrawable);

        AdaptiveIconDrawable iconDrawableInflated =
            (AdaptiveIconDrawable) context.getDrawable(R.drawable.adaptive_icon_drawable);
        fgDrawable = iconDrawableInflated.getForeground();
        bgDrawable = iconDrawableInflated.getBackground();
        assertTrue("Foreground layer is color drawable.", fgDrawable instanceof BitmapDrawable);
        assertTrue("Backgroud layer is color drawable.", bgDrawable instanceof BitmapDrawable);
    }

    @Test
    public void testGetIntrinsicWidth() {
        Context context = InstrumentationRegistry.getTargetContext();
        AdaptiveIconDrawable iconDrawableInflated =
            (AdaptiveIconDrawable) context.getDrawable(R.drawable.adaptive_icon_drawable);
        int fgWidth = iconDrawableInflated.getForeground().getIntrinsicWidth();
        int bgWidth = iconDrawableInflated.getBackground().getIntrinsicWidth();
        float iconIntrinsicWidth = Math.max(fgWidth, bgWidth) / (1 + 2 * AdaptiveIconDrawable.getExtraInsetFraction());
        assertEquals("Max intrinsic width of the layers should be icon's intrinsic width",
            (int) iconIntrinsicWidth, iconDrawableInflated.getIntrinsicWidth());
    }

    @Test
    public void testGetIntrinsicHeight() {
        Context context = InstrumentationRegistry.getTargetContext();
        AdaptiveIconDrawable iconDrawableInflated =
            (AdaptiveIconDrawable) context.getDrawable(R.drawable.adaptive_icon_drawable);
        int fgWidth = iconDrawableInflated.getForeground().getIntrinsicHeight();
        int bgWidth = iconDrawableInflated.getBackground().getIntrinsicHeight();
        float iconIntrinsicHeight = Math.max(fgWidth, bgWidth) / (1 + 2 * AdaptiveIconDrawable.getExtraInsetFraction());
        assertEquals("Max intrinsic height of the layers should be icon's intrinsic height",
            (int) iconIntrinsicHeight, iconDrawableInflated.getIntrinsicHeight());
    }

    //
    // Utils
    //

    boolean equalBitmaps(Bitmap a, Bitmap b) {
      return equalBitmaps(a, b, null);
    }

    boolean equalBitmaps(Bitmap a, Bitmap b, Region region) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;

        final int w = a.getWidth();
        final int h = a.getHeight();
        int[] aPix = new int[w * h];
        int[] bPix = new int[w * h];

        if (region != null) {
            for (int i = 0; i < w; i++) {
                for (int j = 0; j < h; j++) {
                    int ra = (a.getPixel(i, j) >> 16) & 0xff;
                    int ga = (a.getPixel(i, j) >> 8) & 0xff;
                    int ba = a.getPixel(i, j) & 0xff;
                    int rb = (b.getPixel(i, j) >> 16) & 0xff;
                    int gb = (b.getPixel(i, j) >> 8) & 0xff;
                    int bb = b.getPixel(i, j) & 0xff;
                    if (region.contains(i, j) && a.getPixel(i, j) != b.getPixel(i, j) ) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            a.getPixels(aPix, 0, w, 0, 0, w, h);
            b.getPixels(bPix, 0, w, 0, 0, w, h);
            return Arrays.equals(aPix, bPix);
        }
    }

    void findBitmapDifferences(Bitmap a, Bitmap b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            L("different sizes: %dx%d vs %dx%d",
                a.getWidth(), a.getHeight(), b.getWidth(), b.getHeight());
            return;
        }

        final int w = a.getWidth();
        final int h = a.getHeight();
        int[] aPix = new int[w * h];
        int[] bPix = new int[w * h];

        a.getPixels(aPix, 0, w, 0, 0, w, h);
        b.getPixels(bPix, 0, w, 0, 0, w, h);

        L("bitmap a (%dx%d)", w, h);
        printBits(aPix, w, h);
        L("bitmap b (%dx%d)", w, h);
        printBits(bPix, w, h);

        StringBuffer sb = new StringBuffer("Different pixels: ");
        for (int i=0; i<w; i++) {
            for (int j=0; j<h; j++) {
                if (aPix[i+w*j] != bPix[i+w*j]) {
                    sb.append(" ").append(i).append(",").append(j).append("<")
                        .append(aPix[i+w*j]).append(",").append(bPix[i+w*j]).append(">");
                }
            }
        }
        L(sb.toString());
    }

    static void printBits(int[] a, int w, int h) {
        final StringBuilder sb = new StringBuilder();
        for (int i=0; i<w; i++) {
            for (int j=0; j<h; j++) {
                sb.append(colorToChar(a[i+w*j]));
            }
            sb.append('\n');
        }
        L(sb.toString());
    }

    static char colorToChar(int color) {
        int sum = ((color >> 16) & 0xff)
            + ((color >> 8)  & 0xff)
            + ((color)       & 0xff);
        return GRADIENT[sum * (GRADIENT.length-1) / (3*0xff)];
    }
    static final char[] GRADIENT = " .:;+=xX$#".toCharArray();
}
