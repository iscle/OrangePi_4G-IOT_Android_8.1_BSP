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

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.widget.Gallery;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Test {@link Gallery.LayoutParams}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class Gallery_LayoutParamsTest {
    @Test
    public void testConstructor() throws XmlPullParserException, IOException {
        Context context = InstrumentationRegistry.getTargetContext();
        XmlResourceParser p = context.getResources().getLayout(R.layout.gallery_test);
        WidgetTestUtils.beginDocument(p, "LinearLayout");
        new Gallery.LayoutParams(context, p);

        Gallery.LayoutParams params = new Gallery.LayoutParams(320, 480);
        new Gallery.LayoutParams(params);
    }
}
