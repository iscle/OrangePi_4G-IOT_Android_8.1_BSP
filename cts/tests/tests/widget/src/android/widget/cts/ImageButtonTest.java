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

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.widget.ImageButton;
import android.widget.cts.util.TestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ImageButtonTest {
    private Activity mActivity;
    private ImageButton mImageButton;

    @Rule
    public ActivityTestRule<ImageButtonCtsActivity> mActivityRule =
            new ActivityTestRule<>(ImageButtonCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mImageButton = (ImageButton) mActivity.findViewById(R.id.image_button);
    }

    @Test
    public void testConstructor() {
        new ImageButton(mActivity);
        new ImageButton(mActivity, null);
        new ImageButton(mActivity, null, android.R.attr.imageButtonStyle);
        new ImageButton(mActivity, null, 0, android.R.style.Widget_DeviceDefault_ImageButton);
        new ImageButton(mActivity, null, 0, android.R.style.Widget_DeviceDefault_Light_ImageButton);
        new ImageButton(mActivity, null, 0, android.R.style.Widget_Material_ImageButton);
        new ImageButton(mActivity, null, 0, android.R.style.Widget_Material_Light_ImageButton);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext1() {
        new ImageButton(null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext2() {
        new ImageButton(null, null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext3() {
        new ImageButton(null, null, -1);
    }

    @UiThreadTest
    @Test
    public void testImageSource() {
        Drawable imageButtonDrawable = mImageButton.getDrawable();
        TestUtils.assertAllPixelsOfColor("Default source is red", imageButtonDrawable,
                imageButtonDrawable.getIntrinsicWidth(), imageButtonDrawable.getIntrinsicHeight(),
                true, Color.RED, 1, false);

        mImageButton.setImageResource(R.drawable.icon_green);
        imageButtonDrawable = mImageButton.getDrawable();
        TestUtils.assertAllPixelsOfColor("New source is green", imageButtonDrawable,
                imageButtonDrawable.getIntrinsicWidth(), imageButtonDrawable.getIntrinsicHeight(),
                true, Color.GREEN, 1, false);

        mImageButton.setImageDrawable(mActivity.getDrawable(R.drawable.icon_yellow));
        imageButtonDrawable = mImageButton.getDrawable();
        TestUtils.assertAllPixelsOfColor("New source is yellow", imageButtonDrawable,
                imageButtonDrawable.getIntrinsicWidth(), imageButtonDrawable.getIntrinsicHeight(),
                true, Color.YELLOW, 1, false);
    }
}
