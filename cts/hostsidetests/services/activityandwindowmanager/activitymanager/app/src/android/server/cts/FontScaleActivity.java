/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package android.server.cts;

import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public class FontScaleActivity extends AbstractLifecycleLogActivity {
    private static final String TAG = FontScaleActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        dumpFontSize();
    }

    // We're basically ensuring that no matter what happens to the resources underneath the
    // Activity, any TypedArrays obtained from the pool have the correct DisplayMetrics.
    protected void dumpFontSize() {
        try (XmlResourceParser parser = getResources().getXml(R.layout.font_scale)) {
            //noinspection StatementWithEmptyBody
            while (parser.next() != XmlPullParser.START_TAG) { }

            final AttributeSet attrs = Xml.asAttributeSet(parser);
            TypedArray ta = getTheme().obtainStyledAttributes(attrs,
                    new int[] { android.R.attr.textSize }, 0, 0);
            try {
                final int fontPixelSize = ta.getDimensionPixelSize(0, -1);
                if (fontPixelSize == -1) {
                    throw new AssertionError("android:attr/textSize not found");
                }

                Log.i(getTag(), "fontPixelSize=" + fontPixelSize);
            } finally {
                ta.recycle();
            }
        } catch (XmlPullParserException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getTag() {
        return TAG;
    }

}
