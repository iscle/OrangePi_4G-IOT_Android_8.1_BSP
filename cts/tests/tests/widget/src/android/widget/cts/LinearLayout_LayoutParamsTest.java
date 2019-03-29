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
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.Gravity;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.LinearLayout;
import android.widget.cts.util.XmlUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class LinearLayout_LayoutParamsTest {
    @Test
    public void testConstructor() throws XmlPullParserException, IOException {
        final Context context = InstrumentationRegistry.getTargetContext();
        XmlResourceParser p = context.getResources().getLayout(R.layout.linearlayout_layout2);

        XmlUtils.beginDocument(p, "LinearLayout");
        LinearLayout.LayoutParams linearLayoutParams =
                new LinearLayout.LayoutParams(context, p);
        assertEquals(LayoutParams.MATCH_PARENT, linearLayoutParams.width);
        assertEquals(LayoutParams.WRAP_CONTENT, linearLayoutParams.height);
        assertEquals(0.0f, linearLayoutParams.weight, 0.0f);
        assertEquals(-1, linearLayoutParams.gravity);

        linearLayoutParams = new LinearLayout.LayoutParams(320, 240);
        assertEquals(320, linearLayoutParams.width);
        assertEquals(240, linearLayoutParams.height);
        assertEquals(0.0f, linearLayoutParams.weight, 0.0f);
        assertEquals(-1, linearLayoutParams.gravity);

        linearLayoutParams = new LinearLayout.LayoutParams(360, 320, 0.4f);
        assertEquals(360, linearLayoutParams.width);
        assertEquals(320, linearLayoutParams.height);
        assertEquals(0.4f, linearLayoutParams.weight, 0.0f);
        assertEquals(-1, linearLayoutParams.gravity);

        LayoutParams layoutParams = new LayoutParams(200, 480);
        linearLayoutParams = new LinearLayout.LayoutParams(layoutParams);
        assertEquals(200, linearLayoutParams.width);
        assertEquals(480, linearLayoutParams.height);
        assertEquals(0.0f, linearLayoutParams.weight, 0.0f);
        assertEquals(-1, linearLayoutParams.gravity);

        MarginLayoutParams marginLayoutParams = new MarginLayoutParams(320, 200);
        linearLayoutParams = new LinearLayout.LayoutParams(marginLayoutParams);
        assertEquals(320, linearLayoutParams.width);
        assertEquals(200, linearLayoutParams.height);
        assertEquals(0.0f, linearLayoutParams.weight, 0.0f);
        assertEquals(-1, linearLayoutParams.gravity);

        LinearLayout.LayoutParams linearLayoutParams2 = new LinearLayout.LayoutParams(360, 720);
        linearLayoutParams2.weight = 0.9f;
        linearLayoutParams2.gravity = Gravity.RIGHT;
        linearLayoutParams = new LinearLayout.LayoutParams(linearLayoutParams2);
        assertEquals(360, linearLayoutParams.width);
        assertEquals(720, linearLayoutParams.height);
        assertEquals(0.9f, linearLayoutParams.weight, 0.0f);
        assertEquals(Gravity.RIGHT, linearLayoutParams.gravity);
    }

    @Test
    public void testDebug() {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(320, 240);
        assertNotNull(layoutParams.debug("test: "));
    }
}
