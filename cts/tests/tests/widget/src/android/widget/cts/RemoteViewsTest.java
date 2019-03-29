/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsoluteLayout;
import android.widget.AnalogClock;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.DatePicker;
import android.widget.DateTimeView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.RemoteViews;
import android.widget.RemoteViews.ActionException;
import android.widget.SeekBar;
import android.widget.StackView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.ViewFlipper;
import android.widget.cts.util.TestUtils;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Test {@link RemoteViews}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class RemoteViewsTest {
    private static final String PACKAGE_NAME = "android.widget.cts";

    private static final int INVALID_ID = -1;

    private static final long TEST_TIMEOUT = 5000;

    @Rule
    public ActivityTestRule<RemoteViewsCtsActivity> mActivityRule =
            new ActivityTestRule<>(RemoteViewsCtsActivity.class);

    @Rule
    public ExpectedException mExpectedException = ExpectedException.none();

    private Instrumentation mInstrumentation;

    private Context mContext;

    private RemoteViews mRemoteViews;

    private View mResult;

    @UiThreadTest
    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mRemoteViews = new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_good);
        mResult = mRemoteViews.apply(mContext, null);

        // Add our host view to the activity behind this test. This is similar to how launchers
        // add widgets to the on-screen UI.
        ViewGroup root = (ViewGroup) mActivityRule.getActivity().findViewById
                (R.id.remoteView_host);
        FrameLayout.MarginLayoutParams lp = new FrameLayout.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mResult.setLayoutParams(lp);

        root.addView(mResult);
    }

    @Test
    public void testConstructor() {
        new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_good);

        new RemoteViews(Parcel.obtain());
    }

    @Test
    public void testGetPackage() {
        assertEquals(PACKAGE_NAME, mRemoteViews.getPackage());

        mRemoteViews = new RemoteViews(null, R.layout.remoteviews_good);
        assertNull(mRemoteViews.getPackage());
    }

    @Test
    public void testGetLayoutId() {
        assertEquals(R.layout.remoteviews_good, mRemoteViews.getLayoutId());

        mRemoteViews = new RemoteViews(PACKAGE_NAME, R.layout.listview_layout);
        assertEquals(R.layout.listview_layout, mRemoteViews.getLayoutId());

        mRemoteViews = new RemoteViews(PACKAGE_NAME, INVALID_ID);
        assertEquals(INVALID_ID, mRemoteViews.getLayoutId());

        mRemoteViews = new RemoteViews(PACKAGE_NAME, 0);
        assertEquals(0, mRemoteViews.getLayoutId());
    }

    @Test
    public void testSetContentDescription() throws Throwable {
        View view = mResult.findViewById(R.id.remoteView_frame);

        assertNull(view.getContentDescription());

        CharSequence contentDescription = mContext.getString(R.string.remote_content_description);
        mRemoteViews.setContentDescription(R.id.remoteView_frame, contentDescription);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertTrue(TextUtils.equals(contentDescription, view.getContentDescription()));
    }

    @Test
    public void testSetViewVisibility() throws Throwable {
        View view = mResult.findViewById(R.id.remoteView_chronometer);
        assertEquals(View.VISIBLE, view.getVisibility());

        mRemoteViews.setViewVisibility(R.id.remoteView_chronometer, View.INVISIBLE);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(View.INVISIBLE, view.getVisibility());

        mRemoteViews.setViewVisibility(R.id.remoteView_chronometer, View.GONE);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(View.GONE, view.getVisibility());

        mRemoteViews.setViewVisibility(R.id.remoteView_chronometer, View.VISIBLE);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(View.VISIBLE, view.getVisibility());
    }

    @Test
    public void testSetTextViewText() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
        assertEquals("", textView.getText().toString());

        String expected = "This is content";
        mRemoteViews.setTextViewText(R.id.remoteView_text, expected);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(expected, textView.getText().toString());

        mRemoteViews.setTextViewText(R.id.remoteView_text, null);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals("", textView.getText().toString());

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setTextViewText(R.id.remoteView_absolute, "");
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetTextViewTextSize() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setTextViewTextSize(R.id.remoteView_text, TypedValue.COMPLEX_UNIT_SP, 18);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(mContext.getResources().getDisplayMetrics().scaledDensity * 18,
                textView.getTextSize(), 0.001f);

        mExpectedException.expect(Throwable.class);
        mRemoteViews.setTextViewTextSize(R.id.remoteView_absolute, TypedValue.COMPLEX_UNIT_SP, 20);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetIcon() throws Throwable {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        Icon iconBlack = Icon.createWithResource(mContext, R.drawable.icon_black);
        mRemoteViews.setIcon(R.id.remoteView_image, "setImageIcon", iconBlack);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertNotNull(image.getDrawable());
        BitmapDrawable dBlack = (BitmapDrawable) mContext.getDrawable(R.drawable.icon_black);
        WidgetTestUtils.assertEquals(dBlack.getBitmap(),
                ((BitmapDrawable) image.getDrawable()).getBitmap());
    }

    @Test
    public void testSetImageViewIcon() throws Throwable {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        Icon iconBlue = Icon.createWithResource(mContext, R.drawable.icon_blue);
        mRemoteViews.setImageViewIcon(R.id.remoteView_image, iconBlue);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertNotNull(image.getDrawable());
        BitmapDrawable dBlue = (BitmapDrawable) mContext.getDrawable(R.drawable.icon_blue);
        WidgetTestUtils.assertEquals(dBlue.getBitmap(),
                ((BitmapDrawable) image.getDrawable()).getBitmap());

    }

    @Test
    public void testSetImageViewResource() throws Throwable {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        mRemoteViews.setImageViewResource(R.id.remoteView_image, R.drawable.testimage);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertNotNull(image.getDrawable());
        BitmapDrawable d = (BitmapDrawable) mContext.getDrawable(R.drawable.testimage);
        WidgetTestUtils.assertEquals(d.getBitmap(),
                ((BitmapDrawable) image.getDrawable()).getBitmap());

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setImageViewResource(R.id.remoteView_absolute, R.drawable.testimage);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetImageViewUri() throws Throwable {
        String path = getTestImagePath();
        File imageFile = new File(path);

        try {
            createSampleImage(imageFile, R.raw.testimage);

            Uri uri = Uri.parse(path);
            ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
            assertNull(image.getDrawable());

            mRemoteViews.setImageViewUri(R.id.remoteView_image, uri);
            mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));

            Bitmap imageViewBitmap = ((BitmapDrawable) image.getDrawable()).getBitmap();
            Bitmap expectedBitmap = WidgetTestUtils.getUnscaledAndDitheredBitmap(
                    mContext.getResources(), R.raw.testimage, imageViewBitmap.getConfig());
            WidgetTestUtils.assertEquals(expectedBitmap, imageViewBitmap);
        } finally {
            imageFile.delete();
        }
    }

    /**
     * Returns absolute file path of location where test image should be stored
     */
    private String getTestImagePath() {
        return mContext.getFilesDir() + "/test.jpg";
    }

    @Test
    public void testSetChronometer() throws Throwable {
        long base1 = 50;
        long base2 = -50;
        Chronometer chronometer = (Chronometer) mResult.findViewById(R.id.remoteView_chronometer);

        mRemoteViews.setChronometer(R.id.remoteView_chronometer, base1, "HH:MM:SS",
                false);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(base1, chronometer.getBase());
        assertEquals("HH:MM:SS", chronometer.getFormat());

        mRemoteViews.setChronometer(R.id.remoteView_chronometer, base2, "HH:MM:SS",
                false);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(base2, chronometer.getBase());
        assertEquals("HH:MM:SS", chronometer.getFormat());

        mRemoteViews.setChronometer(R.id.remoteView_chronometer, base1, "invalid",
                true);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(base1, chronometer.getBase());
        assertEquals("invalid", chronometer.getFormat());

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setChronometer(R.id.remoteView_absolute, base1, "invalid", true);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetChronometerCountDown() throws Throwable {
        Chronometer chronometer = (Chronometer) mResult.findViewById(R.id.remoteView_chronometer);

        mRemoteViews.setChronometerCountDown(R.id.remoteView_chronometer, true);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertTrue(chronometer.isCountDown());

        mRemoteViews.setChronometerCountDown(R.id.remoteView_chronometer, false);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertFalse(chronometer.isCountDown());

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setChronometerCountDown(R.id.remoteView_absolute, true);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetProgressBar() throws Throwable {
        ProgressBar progress = (ProgressBar) mResult.findViewById(R.id.remoteView_progress);
        assertEquals(100, progress.getMax());
        assertEquals(0, progress.getProgress());
        // the view uses style progressBarHorizontal, so the default is false
        assertFalse(progress.isIndeterminate());

        mRemoteViews.setProgressBar(R.id.remoteView_progress, 80, 50, true);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        // make the bar indeterminate will not affect max and progress
        assertEquals(100, progress.getMax());
        assertEquals(0, progress.getProgress());
        assertTrue(progress.isIndeterminate());

        mRemoteViews.setProgressBar(R.id.remoteView_progress, 60, 50, false);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(60, progress.getMax());
        assertEquals(50, progress.getProgress());
        assertFalse(progress.isIndeterminate());

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setProgressBar(R.id.remoteView_relative, 60, 50, false);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testApply() {
        assertNotNull(mResult);
        assertNotNull(mResult.findViewById(R.id.remoteViews_good));
        assertNotNull(mResult.findViewById(R.id.remoteView_absolute));
        assertNotNull(mResult.findViewById(R.id.remoteView_chronometer));
        assertNotNull(mResult.findViewById(R.id.remoteView_frame));
        assertNotNull(mResult.findViewById(R.id.remoteView_image));
        assertNotNull(mResult.findViewById(R.id.remoteView_linear));
        assertNotNull(mResult.findViewById(R.id.remoteView_progress));
        assertNotNull(mResult.findViewById(R.id.remoteView_relative));
        assertNotNull(mResult.findViewById(R.id.remoteView_text));
    }

    @Test
    public void testReapply() throws Throwable {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        mRemoteViews.setImageViewResource(R.id.remoteView_image, R.drawable.testimage);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, image));
        assertNotNull(image.getDrawable());
        BitmapDrawable d = (BitmapDrawable) mContext
                .getResources().getDrawable(R.drawable.testimage);
        WidgetTestUtils.assertEquals(d.getBitmap(),
                ((BitmapDrawable) image.getDrawable()).getBitmap());
    }

    @Test
    public void testOnLoadClass() {
        mRemoteViews = new RemoteViews(Parcel.obtain());

        assertTrue(mRemoteViews.onLoadClass(AbsoluteLayout.class));
        assertTrue(mRemoteViews.onLoadClass(AnalogClock.class));
        assertTrue(mRemoteViews.onLoadClass(Button.class));
        assertTrue(mRemoteViews.onLoadClass(Chronometer.class));
        assertTrue(mRemoteViews.onLoadClass(DateTimeView.class));
        assertTrue(mRemoteViews.onLoadClass(FrameLayout.class));
        assertTrue(mRemoteViews.onLoadClass(GridLayout.class));
        assertTrue(mRemoteViews.onLoadClass(GridView.class));
        assertTrue(mRemoteViews.onLoadClass(ImageButton.class));
        assertTrue(mRemoteViews.onLoadClass(ImageView.class));
        assertTrue(mRemoteViews.onLoadClass(LinearLayout.class));
        assertTrue(mRemoteViews.onLoadClass(ListView.class));
        assertTrue(mRemoteViews.onLoadClass(ProgressBar.class));
        assertTrue(mRemoteViews.onLoadClass(RelativeLayout.class));
        assertTrue(mRemoteViews.onLoadClass(StackView.class));
        assertTrue(mRemoteViews.onLoadClass(TextClock.class));
        assertTrue(mRemoteViews.onLoadClass(TextView.class));
        assertTrue(mRemoteViews.onLoadClass(ViewFlipper.class));

        // those classes without annotation @RemoteView
        assertFalse(mRemoteViews.onLoadClass(EditText.class));
        assertFalse(mRemoteViews.onLoadClass(DatePicker.class));
        assertFalse(mRemoteViews.onLoadClass(NumberPicker.class));
        assertFalse(mRemoteViews.onLoadClass(RatingBar.class));
        assertFalse(mRemoteViews.onLoadClass(SeekBar.class));
    }

    @Test
    public void testDescribeContents() {
        mRemoteViews = new RemoteViews(Parcel.obtain());
        mRemoteViews.describeContents();
    }

    @Test
    public void testWriteToParcel() {
        mRemoteViews.setTextViewText(R.id.remoteView_text, "This is content");
        mRemoteViews.setViewVisibility(R.id.remoteView_frame, View.GONE);
        Parcel p = Parcel.obtain();
        mRemoteViews.writeToParcel(p, 0);
        p.setDataPosition(0);

        // the package and layout are successfully written into parcel
        mRemoteViews = RemoteViews.CREATOR.createFromParcel(p);
        View result = mRemoteViews.apply(mContext, null);
        assertEquals(PACKAGE_NAME, mRemoteViews.getPackage());
        assertEquals(R.layout.remoteviews_good, mRemoteViews.getLayoutId());
        assertEquals("This is content", ((TextView) result.findViewById(R.id.remoteView_text))
                .getText().toString());
        assertEquals(View.GONE, result.findViewById(R.id.remoteView_frame).getVisibility());

        p = Parcel.obtain();

        // currently the flag is not used
        mRemoteViews.writeToParcel(p, -1);

        p.recycle();

        RemoteViews[] remote = RemoteViews.CREATOR.newArray(1);
        assertNotNull(remote);
        assertEquals(1, remote.length);
    }

    @Test(expected=NullPointerException.class)
    public void testWriteNullToParcel() {
        mRemoteViews.writeToParcel(null, 0);
    }

    @Test(expected=NegativeArraySizeException.class)
    public void testCreateNegativeSizedArray() {
        RemoteViews.CREATOR.newArray(-1);
    }

    @Test
    public void testSetImageViewBitmap() throws Throwable {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        Bitmap bitmap =
                BitmapFactory.decodeResource(mContext.getResources(), R.drawable.testimage);
        mRemoteViews.setImageViewBitmap(R.id.remoteView_image, bitmap);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertNotNull(image.getDrawable());
        WidgetTestUtils.assertEquals(bitmap, ((BitmapDrawable) image.getDrawable()).getBitmap());

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setImageViewBitmap(R.id.remoteView_absolute, bitmap);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetBitmap() throws Throwable {
        ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
        assertNull(image.getDrawable());

        Bitmap bitmap =
                BitmapFactory.decodeResource(mContext.getResources(), R.drawable.testimage);
        mRemoteViews.setBitmap(R.id.remoteView_image, "setImageBitmap", bitmap);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertNotNull(image.getDrawable());
        WidgetTestUtils.assertEquals(bitmap, ((BitmapDrawable) image.getDrawable()).getBitmap());

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setBitmap(R.id.remoteView_absolute, "setImageBitmap", bitmap);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetBoolean() throws Throwable {
        ProgressBar progress = (ProgressBar) mResult.findViewById(R.id.remoteView_progress);
        // the view uses style progressBarHorizontal, so the default is false
        assertFalse(progress.isIndeterminate());

        mRemoteViews.setBoolean(R.id.remoteView_progress, "setIndeterminate", true);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertTrue(progress.isIndeterminate());

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setBoolean(R.id.remoteView_relative, "setIndeterminate", false);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetCharSequence() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);
        assertEquals("", textView.getText().toString());

        String expected = "test setCharSequence";
        mRemoteViews.setCharSequence(R.id.remoteView_text, "setText", expected);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(expected, textView.getText().toString());

        mRemoteViews.setCharSequence(R.id.remoteView_text, "setText", null);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals("", textView.getText().toString());

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setCharSequence(R.id.remoteView_absolute, "setText", "");
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetInt() throws Throwable {
        View view = mResult.findViewById(R.id.remoteView_chronometer);
        assertEquals(View.VISIBLE, view.getVisibility());

        mRemoteViews.setInt(R.id.remoteView_chronometer, "setVisibility", View.INVISIBLE);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(View.INVISIBLE, view.getVisibility());

        mRemoteViews.setInt(R.id.remoteView_chronometer, "setVisibility", View.GONE);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(View.GONE, view.getVisibility());

        mRemoteViews.setInt(R.id.remoteView_chronometer, "setVisibility", View.VISIBLE);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(View.VISIBLE, view.getVisibility());
    }

    @Test
    public void testSetString() throws Throwable {
        String format = "HH:MM:SS";
        Chronometer chronometer = (Chronometer) mResult.findViewById(R.id.remoteView_chronometer);
        assertNull(chronometer.getFormat());

        mRemoteViews.setString(R.id.remoteView_chronometer, "setFormat", format);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(format, chronometer.getFormat());

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setString(R.id.remoteView_image, "setFormat", format);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetUri() throws Throwable {
        String path = getTestImagePath();
        File imagefile = new File(path);

        try {
            createSampleImage(imagefile, R.raw.testimage);

            Uri uri = Uri.parse(path);
            ImageView image = (ImageView) mResult.findViewById(R.id.remoteView_image);
            assertNull(image.getDrawable());

            mRemoteViews.setUri(R.id.remoteView_image, "setImageURI", uri);
            mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));

            Bitmap imageViewBitmap = ((BitmapDrawable) image.getDrawable()).getBitmap();
            Bitmap expectedBitmap = WidgetTestUtils.getUnscaledAndDitheredBitmap(
                    mContext.getResources(), R.raw.testimage, imageViewBitmap.getConfig());
            WidgetTestUtils.assertEquals(expectedBitmap, imageViewBitmap);

            mExpectedException.expect(ActionException.class);
            mRemoteViews.setUri(R.id.remoteView_absolute, "setImageURI", uri);
            mRemoteViews.reapply(mContext, mResult);
        } finally {
            // remove the test image file
            imagefile.delete();
        }
    }

    @Test
    public void testSetTextColor() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setTextColor(R.id.remoteView_text, R.color.testcolor1);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertSame(ColorStateList.valueOf(R.color.testcolor1), textView.getTextColors());

        mRemoteViews.setTextColor(R.id.remoteView_text, R.color.testcolor2);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertSame(ColorStateList.valueOf(R.color.testcolor2), textView.getTextColors());

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setTextColor(R.id.remoteView_absolute, R.color.testcolor1);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetTextCompoundDrawables() throws Throwable {
        TextView textView = (TextView) mResult.findViewById(R.id.remoteView_text);

        TestUtils.verifyCompoundDrawables(textView, -1, -1, -1, -1);

        mRemoteViews.setTextViewCompoundDrawables(R.id.remoteView_text, R.drawable.start,
                R.drawable.pass, R.drawable.failed, 0);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        TestUtils.verifyCompoundDrawables(textView, R.drawable.start, R.drawable.failed,
                R.drawable.pass, -1);

        mRemoteViews.setTextViewCompoundDrawables(R.id.remoteView_text, 0,
                R.drawable.icon_black, R.drawable.icon_red, R.drawable.icon_green);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        TestUtils.verifyCompoundDrawables(textView, -1,  R.drawable.icon_red, R.drawable.icon_black,
                R.drawable.icon_green);

        mExpectedException.expect(Throwable.class);
        mRemoteViews.setTextViewCompoundDrawables(R.id.remoteView_absolute, 0,
                R.drawable.start, R.drawable.failed, 0);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetTextCompoundDrawablesRelative() throws Throwable {
        TextView textViewLtr = (TextView) mResult.findViewById(R.id.remoteView_text_ltr);
        TextView textViewRtl = (TextView) mResult.findViewById(R.id.remoteView_text_rtl);

        TestUtils.verifyCompoundDrawables(textViewLtr, -1, -1, -1, -1);
        TestUtils.verifyCompoundDrawables(textViewRtl, -1, -1, -1, -1);

        mRemoteViews.setTextViewCompoundDrawablesRelative(R.id.remoteView_text_ltr,
                R.drawable.start, R.drawable.pass, R.drawable.failed, 0);
        mRemoteViews.setTextViewCompoundDrawablesRelative(R.id.remoteView_text_rtl,
                R.drawable.start, R.drawable.pass, R.drawable.failed, 0);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        TestUtils.verifyCompoundDrawables(textViewLtr, R.drawable.start, R.drawable.failed,
                R.drawable.pass, -1);
        TestUtils.verifyCompoundDrawables(textViewRtl, R.drawable.failed, R.drawable.start,
                R.drawable.pass, -1);

        mRemoteViews.setTextViewCompoundDrawablesRelative(R.id.remoteView_text_ltr, 0,
                R.drawable.icon_black, R.drawable.icon_red, R.drawable.icon_green);
        mRemoteViews.setTextViewCompoundDrawablesRelative(R.id.remoteView_text_rtl, 0,
                R.drawable.icon_black, R.drawable.icon_red, R.drawable.icon_green);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        TestUtils.verifyCompoundDrawables(textViewLtr, -1, R.drawable.icon_red,
                R.drawable.icon_black, R.drawable.icon_green);
        TestUtils.verifyCompoundDrawables(textViewRtl, R.drawable.icon_red, -1,
                R.drawable.icon_black, R.drawable.icon_green);

        mExpectedException.expect(Throwable.class);
        mRemoteViews.setTextViewCompoundDrawablesRelative(R.id.remoteView_absolute, 0,
                R.drawable.start, R.drawable.failed, 0);
        mRemoteViews.reapply(mContext, mResult);
    }

    @LargeTest
    @Test
    public void testSetOnClickPendingIntent() throws Throwable {
        Uri uri = Uri.parse("ctstest://RemoteView/test");
        ActivityMonitor am = mInstrumentation.addMonitor(MockURLSpanTestActivity.class.getName(),
                null, false);
        View view = mResult.findViewById(R.id.remoteView_image);
        view.performClick();
        Activity newActivity = am.waitForActivityWithTimeout(TEST_TIMEOUT);
        assertNull(newActivity);

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        mRemoteViews.setOnClickPendingIntent(R.id.remoteView_image, pendingIntent);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        mActivityRule.runOnUiThread(() -> view.performClick());
        newActivity = am.waitForActivityWithTimeout(TEST_TIMEOUT);
        assertNotNull(newActivity);
        assertTrue(newActivity instanceof MockURLSpanTestActivity);
        newActivity.finish();
    }

    @Test
    public void testSetLong() throws Throwable {
        long base1 = 50;
        long base2 = -50;
        Chronometer chronometer = (Chronometer) mResult.findViewById(R.id.remoteView_chronometer);

        mRemoteViews.setLong(R.id.remoteView_chronometer, "setBase", base1);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(base1, chronometer.getBase());

        mRemoteViews.setLong(R.id.remoteView_chronometer, "setBase", base2);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(base2, chronometer.getBase());

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setLong(R.id.remoteView_absolute, "setBase", base1);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetFloat() throws Throwable {
        LinearLayout linearLayout = (LinearLayout) mResult.findViewById(R.id.remoteView_linear);
        assertTrue(linearLayout.getWeightSum() <= 0.0f);

        mRemoteViews.setFloat(R.id.remoteView_linear, "setWeightSum", 0.5f);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(0.5f, linearLayout.getWeightSum(), 0.001f);

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setFloat(R.id.remoteView_absolute, "setWeightSum", 1.0f);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetByte() throws Throwable {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertEquals(0, customView.getByteField());

        byte b = 100;
        mRemoteViews.setByte(R.id.remoteView_custom, "setByteField", b);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(b, customView.getByteField());

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setByte(R.id.remoteView_absolute, "setByteField", b);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetChar() throws Throwable {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertEquals('\u0000', customView.getCharField());

        mRemoteViews.setChar(R.id.remoteView_custom, "setCharField", 'q');
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals('q', customView.getCharField());

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setChar(R.id.remoteView_absolute, "setCharField", 'w');
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetDouble() throws Throwable {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertEquals(0.0, customView.getDoubleField(), 0.0f);

        mRemoteViews.setDouble(R.id.remoteView_custom, "setDoubleField", 0.5);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(0.5, customView.getDoubleField(), 0.001f);

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setDouble(R.id.remoteView_absolute, "setDoubleField", 1.0);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetShort() throws Throwable {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertEquals(0, customView.getShortField());

        short s = 25;
        mRemoteViews.setShort(R.id.remoteView_custom, "setShortField", s);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(s, customView.getShortField());

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setShort(R.id.remoteView_absolute, "setShortField", s);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetBundle() throws Throwable {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertNull(customView.getBundleField());

        final Bundle bundle = new Bundle();
        bundle.putString("STR", "brexit");
        bundle.putInt("INT", 2016);
        mRemoteViews.setBundle(R.id.remoteView_custom, "setBundleField", bundle);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        final Bundle fromRemote = customView.getBundleField();
        assertEquals("brexit", fromRemote.getString("STR", ""));
        assertEquals(2016, fromRemote.getInt("INT", 0));

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setBundle(R.id.remoteView_absolute, "setBundleField", bundle);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testSetIntent() throws Throwable {
        MyRemotableView customView = (MyRemotableView) mResult.findViewById(R.id.remoteView_custom);
        assertNull(customView.getIntentField());

        final Intent intent = new Intent(mContext, SwitchCtsActivity.class);
        intent.putExtra("STR", "brexit");
        intent.putExtra("INT", 2016);
        mRemoteViews.setIntent(R.id.remoteView_custom, "setIntentField", intent);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        final Intent fromRemote = customView.getIntentField();
        assertEquals(SwitchCtsActivity.class.getName(), fromRemote.getComponent().getClassName());
        assertEquals("brexit", fromRemote.getStringExtra("STR"));
        assertEquals(2016, fromRemote.getIntExtra("INT", 0));

        mExpectedException.expect(ActionException.class);
        mRemoteViews.setIntent(R.id.remoteView_absolute, "setIntentField", intent);
        mRemoteViews.reapply(mContext, mResult);
    }

    @Test
    public void testRemoveAllViews() throws Throwable {
        ViewGroup root = (ViewGroup) mResult.findViewById(R.id.remoteViews_good);
        assertTrue(root.getChildCount() > 0);

        mRemoteViews.removeAllViews(R.id.remoteViews_good);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(0, root.getChildCount());
    }

    @Test
    public void testAddView() throws Throwable {
        ViewGroup root = (ViewGroup) mResult.findViewById(R.id.remoteViews_good);
        int originalChildCount = root.getChildCount();

        assertNull(root.findViewById(R.id.remoteView_frame_extra));

        // Create a RemoteViews wrapper around a layout and add it to our root
        RemoteViews extra = new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_extra);
        mRemoteViews.addView(R.id.remoteViews_good, extra);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));

        // Verify that our root has that layout as its last (new) child
        assertEquals(originalChildCount + 1, root.getChildCount());
        assertNotNull(root.findViewById(R.id.remoteView_frame_extra));
        assertEquals(R.id.remoteView_frame_extra, root.getChildAt(originalChildCount).getId());
    }

    @Test
    public void testSetLabelFor() throws Throwable {
        View labelView = mResult.findViewById(R.id.remoteView_label);
        assertEquals(View.NO_ID, labelView.getLabelFor());

        mRemoteViews.setLabelFor(R.id.remoteView_label, R.id.remoteView_text);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(R.id.remoteView_text, labelView.getLabelFor());
    }

    @Test
    public void testSetAccessibilityTraversalAfter() throws Throwable {
        View textView = mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setAccessibilityTraversalAfter(R.id.remoteView_text, R.id.remoteView_frame);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(R.id.remoteView_frame, textView.getAccessibilityTraversalAfter());

        mRemoteViews.setAccessibilityTraversalAfter(R.id.remoteView_text, R.id.remoteView_linear);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(R.id.remoteView_linear, textView.getAccessibilityTraversalAfter());
    }

    @Test
    public void testSetAccessibilityTraversalBefore() throws Throwable {
        View textView = mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setAccessibilityTraversalBefore(R.id.remoteView_text, R.id.remoteView_frame);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(R.id.remoteView_frame, textView.getAccessibilityTraversalBefore());

        mRemoteViews.setAccessibilityTraversalBefore(R.id.remoteView_text, R.id.remoteView_linear);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(R.id.remoteView_linear, textView.getAccessibilityTraversalBefore());
    }

    @Test
    public void testSetViewPadding() throws Throwable {
        View textView = mResult.findViewById(R.id.remoteView_text);

        mRemoteViews.setViewPadding(R.id.remoteView_text, 10, 20, 30, 40);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(10, textView.getPaddingLeft());
        assertEquals(20, textView.getPaddingTop());
        assertEquals(30, textView.getPaddingRight());
        assertEquals(40, textView.getPaddingBottom());

        mRemoteViews.setViewPadding(R.id.remoteView_text, 40, 30, 20, 10);
        mActivityRule.runOnUiThread(() -> mRemoteViews.reapply(mContext, mResult));
        assertEquals(40, textView.getPaddingLeft());
        assertEquals(30, textView.getPaddingTop());
        assertEquals(20, textView.getPaddingRight());
        assertEquals(10, textView.getPaddingBottom());
    }

    private void createSampleImage(File imagefile, int resid) throws IOException {
        try (InputStream source = mContext.getResources().openRawResource(resid);
             OutputStream target = new FileOutputStream(imagefile)) {

            byte[] buffer = new byte[1024];
            for (int len = source.read(buffer); len > 0; len = source.read(buffer)) {
                target.write(buffer, 0, len);
            }
        }
    }
}
