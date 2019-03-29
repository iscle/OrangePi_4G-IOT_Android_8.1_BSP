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

package android.content.res.cts;

import static com.google.common.truth.Truth.assertThat;

import org.xmlpull.v1.XmlPullParserException;

import android.content.cts.R;
import android.content.cts.util.XmlUtils;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Typeface;
import android.test.AndroidTestCase;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import java.io.IOException;

public class TypedArrayTest extends AndroidTestCase {
    private static final int DEFINT = -1;
    private static final float DEFFLOAT = -1.0f;
    private static final int EXPECTED_COLOR = 0xff0000ff;
    private static final int EXPECTED_COLOR_STATE = 0xff00ff00;
    private static final float EXPECTED_DIMENSION = 0.75f;
    private static final int EXPECTED_PIXEL_OFFSET = 10;
    private static final int EXPECTED_LAYOUT_DIMENSION = 10;
    private static final int EXPECTED_PIXEL_SIZE = 18;
    private static final float EXPECTED_FLOAT = 3.14f;
    private static final float EXPECTED_FRACTION = 10.0f;
    private static final int EXPECTED_INT = 365;
    private static final String EXPECTED_STRING = "Hello, Android!";
    private static final String EXPECTED_TEXT = "TypedArray Test!";
    private static final String[] EXPECTED_TEXT_ARRAY = {"Easy", "Medium", "Hard"};
    private static final int EXPECTED_INDEX_COUNT = 19;
    private static final String EXPTECTED_POS_DESCRIP = "<internal>";
    private static final int EXPECTED_LENGTH = 20;
    private static final String EXPECTED_NON_RESOURCE_STRING = "testNonResourcesString";
    private static final String XML_BEGIN = "resources";
    private static final int EXPECTED_INT_ATT = 86400;
    private static final int EXPECTED_CHANGING_CONFIG =
            ActivityInfo.CONFIG_ORIENTATION | ActivityInfo.CONFIG_LOCALE;

    private TypedArray mTypedArray;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTypedArray = getContext().getTheme()
                .obtainStyledAttributes(R.style.Whatever, R.styleable.style1);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mTypedArray.recycle();
    }

    public void testGetType() {
        final TypedArray t = getContext().getTheme().obtainStyledAttributes(
                R.style.Whatever, R.styleable.style1);

        assertEquals(TypedValue.TYPE_INT_BOOLEAN, t.getType(R.styleable.style1_type1));
        assertEquals(TypedValue.TYPE_INT_BOOLEAN, t.getType(R.styleable.style1_type2));
        assertEquals(TypedValue.TYPE_INT_COLOR_ARGB8, t.getType(R.styleable.style1_type3));
        assertEquals(TypedValue.TYPE_INT_COLOR_ARGB8, t.getType(R.styleable.style1_type4));
        assertEquals(TypedValue.TYPE_DIMENSION, t.getType(R.styleable.style1_type5));
        assertEquals(TypedValue.TYPE_DIMENSION, t.getType(R.styleable.style1_type6));
        assertEquals(TypedValue.TYPE_DIMENSION, t.getType(R.styleable.style1_type7));
        assertEquals(TypedValue.TYPE_STRING, t.getType(R.styleable.style1_type8));
        assertEquals(TypedValue.TYPE_FLOAT, t.getType(R.styleable.style1_type9));
        assertEquals(TypedValue.TYPE_FRACTION, t.getType(R.styleable.style1_type10));
        assertEquals(TypedValue.TYPE_INT_DEC, t.getType(R.styleable.style1_type11));
        assertEquals(TypedValue.TYPE_INT_DEC, t.getType(R.styleable.style1_type12));
        assertEquals(TypedValue.TYPE_STRING, t.getType(R.styleable.style1_type13));
        assertEquals(TypedValue.TYPE_STRING, t.getType(R.styleable.style1_type14));
        assertEquals(TypedValue.TYPE_REFERENCE, t.getType(R.styleable.style1_type15));
        assertEquals(TypedValue.TYPE_STRING, t.getType(R.styleable.style1_type16));
        assertEquals(TypedValue.TYPE_NULL, t.getType(R.styleable.style1_typeEmpty));
        assertEquals(TypedValue.TYPE_NULL, t.getType(R.styleable.style1_typeUndefined));

        t.recycle();
    }

    public void testBasics() {
        final TypedArray t = getContext().getTheme().obtainStyledAttributes(
                R.style.Whatever, R.styleable.style1);

        assertEquals(EXPECTED_CHANGING_CONFIG, t.getChangingConfigurations());
        assertEquals(EXPECTED_INDEX_COUNT, t.getIndexCount());
        assertEquals(EXPTECTED_POS_DESCRIP, t.getPositionDescription());
        assertEquals(EXPECTED_LENGTH, t.length());
        assertEquals(getContext().getResources(), t.getResources());
        assertNotNull(t.toString());

        t.recycle();
    }

    public void testGetAttributes() {
        final TypedArray t = getContext().getTheme().obtainStyledAttributes(
                R.style.Whatever, R.styleable.style1);

        assertTrue(t.getBoolean(R.styleable.style1_type1, false));
        assertFalse(t.getBoolean(R.styleable.style1_type2, true));

        assertEquals(EXPECTED_COLOR,
                t.getColor(R.styleable.style1_type3, DEFINT));
        assertEquals(EXPECTED_COLOR_STATE,
                t.getColorStateList(R.styleable.style1_type4).getDefaultColor());

        // This get values equals attribute dimension value set in styles.xml
        // multiplied by the appropriate metric, the metric is unknown.
        assertEquals(EXPECTED_DIMENSION,
                t.getDimension(R.styleable.style1_type5, DEFFLOAT));

        assertEquals(EXPECTED_PIXEL_OFFSET,
                t.getDimensionPixelOffset(R.styleable.style1_type6, DEFINT));
        assertEquals(EXPECTED_LAYOUT_DIMENSION,
                t.getLayoutDimension(R.styleable.style1_type6, "type6"));
        assertEquals(EXPECTED_LAYOUT_DIMENSION,
                t.getLayoutDimension(R.styleable.style1_type6, 0));

        assertEquals(EXPECTED_PIXEL_SIZE,
                t.getDimensionPixelSize(R.styleable.style1_type7, DEFINT));

        assertNotNull(t.getDrawable(R.styleable.style1_type8));
        assertEquals(R.drawable.pass, t.getResourceId(R.styleable.style1_type8, DEFINT));

        assertEquals(EXPECTED_FLOAT,
                t.getFloat(R.styleable.style1_type9, DEFFLOAT));
        assertEquals(EXPECTED_FRACTION,
                t.getFraction(R.styleable.style1_type10, 10, 10, DEFFLOAT));
        assertEquals(EXPECTED_INT,
                t.getInt(R.styleable.style1_type11, DEFINT));
        assertEquals(EXPECTED_INT_ATT,
                t.getInteger(R.styleable.style1_type12, DEFINT));

        assertEquals(EXPECTED_STRING, t.getString(R.styleable.style1_type13));
        assertNull(t.getNonResourceString(R.styleable.style1_type14));
        assertEquals(EXPECTED_TEXT, t.getText(R.styleable.style1_type14));

        final CharSequence[] textArray = t.getTextArray(R.styleable.style1_type15);
        assertEquals(EXPECTED_TEXT_ARRAY[0], textArray[0]);
        assertEquals(EXPECTED_TEXT_ARRAY[1], textArray[1]);
        assertEquals(EXPECTED_TEXT_ARRAY[2], textArray[2]);

        // Verify that all the attributes retrieved are expected and present.
        final int[] actual_indices = new int[t.getIndexCount()];
        for (int idx = 0; idx < t.getIndexCount(); idx++) {
            final int attr_index = t.getIndex(idx);
            assertTrue(t.hasValueOrEmpty(attr_index));
            actual_indices[idx] = attr_index;
        }

        final Typeface font = t.getFont(R.styleable.style1_type18);
        assertEquals(mContext.getResources().getFont(R.font.sample_regular_font), font);

        // NOTE: order does not matter here.
        // R.styleable.style1_typeUndefined is not expected because TYPE_NULL values do not get
        // included in the index list.
        assertThat(actual_indices).asList().containsExactly(
                R.styleable.style1_type1,
                R.styleable.style1_type2,
                R.styleable.style1_type3,
                R.styleable.style1_type4,
                R.styleable.style1_type5,
                R.styleable.style1_type6,
                R.styleable.style1_type7,
                R.styleable.style1_type8,
                R.styleable.style1_type9,
                R.styleable.style1_type10,
                R.styleable.style1_type11,
                R.styleable.style1_type12,
                R.styleable.style1_type13,
                R.styleable.style1_type14,
                R.styleable.style1_type15,
                R.styleable.style1_type16,
                R.styleable.style1_type17,
                R.styleable.style1_type18,
                R.styleable.style1_typeEmpty);
    }

    public void testPeekValue() {
        final TypedArray t = getContext().getTheme().obtainStyledAttributes(
                R.style.Whatever, R.styleable.style1);

        final TypedValue v = t.peekValue(R.styleable.style1_type11);
        assertNotNull(v);
        assertEquals(TypedValue.TYPE_INT_DEC, v.type);
        assertEquals(EXPECTED_INT, v.data);

        t.recycle();
    }

    public void testHasValue() {
        final TypedArray t = getContext().getTheme().obtainStyledAttributes(
                R.style.Whatever, R.styleable.style1);

        // hasValue()
        assertTrue(t.hasValue(R.styleable.style1_type16));
        assertFalse(t.hasValue(R.styleable.style1_typeEmpty));
        assertFalse(t.hasValue(R.styleable.style1_typeUndefined));

        // hasValueOrEmpty()
        assertTrue(t.hasValueOrEmpty(R.styleable.style1_type16));
        assertTrue(t.hasValueOrEmpty(R.styleable.style1_typeEmpty));
        assertFalse(t.hasValueOrEmpty(R.styleable.style1_typeUndefined));

        t.recycle();
    }

    public void testRecycle() {
        final ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(getContext(), 0);
        contextThemeWrapper.setTheme(R.style.TextAppearance);
        final TypedArray test = contextThemeWrapper.getTheme().obtainStyledAttributes(
                R.styleable.TextAppearance);
        test.recycle();
    }

    public void testNonResourceString() throws XmlPullParserException, IOException {
        final XmlResourceParser parser = getContext().getResources().getXml(R.xml.test_color);
        XmlUtils.beginDocument(parser, XML_BEGIN);
        final AttributeSet set = parser;
        assertEquals(1, set.getAttributeCount());
        final TypedArray ta = getContext().getResources().obtainAttributes(set,
                com.android.internal.R.styleable.AndroidManifest);
        assertEquals(1, ta.getIndexCount());
        assertEquals(EXPECTED_NON_RESOURCE_STRING, ta.getNonResourceString(
                com.android.internal.R.styleable.AndroidManifest_versionName));
        ta.recycle();
        parser.close();
    }

    public void testEmptyXmlAttributeDoesNotFallbackToTheme() throws Exception {
        final Resources resources = getContext().getResources();
        try (final XmlResourceParser parser = resources.getXml(R.xml.empty)) {
            XmlUtils.beginDocument(parser, "element");
            assertEquals(1, parser.getAttributeCount());

            final Resources.Theme theme = resources.newTheme();
            theme.applyStyle(R.style.Whatever, false);

            final TypedArray ta = theme.obtainStyledAttributes(parser, R.styleable.style1, 0, 0);
            try {
                assertTrue(ta.hasValueOrEmpty(R.styleable.style1_type1));
                assertEquals(TypedValue.TYPE_NULL, ta.getType(R.styleable.style1_type1));
            } finally {
                ta.recycle();
            }
        }
    }
}
