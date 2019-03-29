/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.XmlResourceParser;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.util.Xml;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.LayoutInflater.Factory;
import android.view.LayoutInflater.Filter;
import android.view.View;
import android.view.ViewGroup;
import android.view.cts.util.XmlUtils;
import android.widget.LinearLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class LayoutInflaterTest {
    private LayoutInflater mLayoutInflater;

    private Context mContext;

    private final Factory mFactory = (String name, Context context, AttributeSet attrs) -> null;

    private boolean isOnLoadClass;

    private final Filter mFilter = (Class clazz) -> {
        isOnLoadClass = true;
        return true;
    };

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
        mLayoutInflater = (LayoutInflater) mContext.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
    }

    @Test
    public void testFrom() {
        mLayoutInflater = null;
        mLayoutInflater = LayoutInflater.from(mContext);
        assertNotNull(mLayoutInflater);
        mLayoutInflater = null;
        mLayoutInflater = new MockLayoutInflater(mContext);
        assertNotNull(mLayoutInflater);

        LayoutInflater layoutInflater = new MockLayoutInflater(mLayoutInflater,
                mContext);
        assertNotNull(layoutInflater);
    }

    @Test
    public void testAccessLayoutInflaterProperties() {
        mLayoutInflater.setFilter(mFilter);
        assertSame(mFilter, mLayoutInflater.getFilter());
        mLayoutInflater.setFactory(mFactory);
        assertSame(mFactory, mLayoutInflater.getFactory());
        mLayoutInflater = new MockLayoutInflater(mContext);
        assertSame(mContext, mLayoutInflater.getContext());
    }

    private AttributeSet getAttrs() {
        XmlResourceParser parser = null;
        AttributeSet attrs = null;
        ActivityInfo ai = null;
        ComponentName mComponentName = new ComponentName(mContext,
                MockActivity.class);
        try {
            ai = mContext.getPackageManager().getActivityInfo(mComponentName,
                    PackageManager.GET_META_DATA);
            parser = ai.loadXmlMetaData(mContext.getPackageManager(),
                    "android.widget.layout");
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && type != XmlPullParser.START_TAG) {
            }
            String nodeName = parser.getName();
            if (!"alias".equals(nodeName)) {
                throw new InflateException();
            }
            int outerDepth = parser.getDepth();
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                    continue;
                }
                nodeName = parser.getName();
                if ("AbsoluteLayout".equals(nodeName)) {
                    attrs = Xml.asAttributeSet(parser);
                } else {
                    XmlUtils.skipCurrentTag(parser);
                }
            }
        } catch (Exception e) {
        }
        return attrs;
    }

    @Test
    public void testCreateView() {
        AttributeSet attrs = getAttrs();
        isOnLoadClass = false;
        View view = null;
        try {
            view = mLayoutInflater.createView("testthrow", "com.android", attrs);
            fail("should throw exception");
        } catch (InflateException e) {
        } catch (ClassNotFoundException e) {
        }
        assertFalse(isOnLoadClass);
        assertNull(view);
        mLayoutInflater = null;
        mLayoutInflater = LayoutInflater.from(mContext);
        isOnLoadClass = false;
        mLayoutInflater.setFilter((Class clazz) -> {
            isOnLoadClass = true;
            return false;
        });
        try {
            view = mLayoutInflater.createView("MockActivity",
                    "com.android.app.", attrs);
            fail("should throw exception");
        } catch (InflateException e) {
        } catch (ClassNotFoundException e) {
        }
        assertFalse(isOnLoadClass);
        assertNull(view);

        isOnLoadClass = false;
        // allowedState is false
        try {
            view = mLayoutInflater.createView(MockActivity.class.getName(),
                    MockActivity.class.getPackage().toString(), attrs);
            fail("should throw exception");
        } catch (InflateException e) {
        } catch (ClassNotFoundException e) {
        }
        assertFalse(isOnLoadClass);
        assertNull(view);
        mLayoutInflater = null;
        mLayoutInflater = LayoutInflater.from(mContext);
        try {
            mLayoutInflater.setFilter(null);
            view = mLayoutInflater.createView("com.android.app.MockActivity",
                    null, attrs);
            assertNotNull(view);
            assertFalse(isOnLoadClass);
            mLayoutInflater = null;
            mLayoutInflater = LayoutInflater.from(mContext);
            mLayoutInflater.setFilter(null);

            view = mLayoutInflater.createView(MockActivity.class.getName(),
                    MockActivity.class.getPackage().toString(), attrs);
            assertNotNull(view);
            assertFalse(isOnLoadClass);
            mLayoutInflater.setFilter(mFilter);
            view = mLayoutInflater.createView(MockActivity.class.getName(),
                    MockActivity.class.getPackage().toString(), attrs);
            assertNotNull(view);
            assertTrue(isOnLoadClass);
            // allowedState!=null
            view = mLayoutInflater.createView(MockActivity.class.getName(),
                    MockActivity.class.getPackage().toString(), attrs);
            assertNotNull(view);
            assertTrue(isOnLoadClass);
        } catch (InflateException e) {
        } catch (ClassNotFoundException e) {
        }
    }

    @Test(expected=Resources.NotFoundException.class)
    public void testInflateInvalidId() {
        mLayoutInflater.inflate(-1, null);
    }

    @Test
    public void testInflate() {
        View view = mLayoutInflater.inflate(
                android.view.cts.R.layout.inflater_layout, null);
        assertNotNull(view);

        LinearLayout linearLayout = new LinearLayout(mContext);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setHorizontalGravity(Gravity.LEFT);
        linearLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        assertEquals(0, linearLayout.getChildCount());
        view = mLayoutInflater.inflate(R.layout.inflater_layout,
                linearLayout);
        assertNotNull(view);
        assertEquals(1, linearLayout.getChildCount());
    }

    @Test(expected=Resources.NotFoundException.class)
    public void testInflateAttachToRootInvalidId() {
        mLayoutInflater.inflate(-1, null, false);
    }

    @Test
    public void testInflateAttachToRoot() {
        View view = mLayoutInflater.inflate(
                R.layout.inflater_layout, null, false);
        assertNotNull(view);

        LinearLayout linearLayout = new LinearLayout(mContext);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setHorizontalGravity(Gravity.LEFT);
        linearLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        assertEquals(0, linearLayout.getChildCount());
        view = mLayoutInflater.inflate(R.layout.inflater_layout,
                linearLayout, false);
        assertNotNull(view);
        assertEquals(0, linearLayout.getChildCount());

        view = mLayoutInflater.inflate(R.layout.inflater_layout,
                linearLayout, true);
        assertNotNull(view);
        assertEquals(1, linearLayout.getChildCount());
    }

    @Test(expected=NullPointerException.class)
    public void testInflateParserNullParser() {
        mLayoutInflater.inflate(null, null);
    }

    @Test
    public void testInflateParser() {
        XmlResourceParser parser = mContext.getResources().getLayout(R.layout.inflater_layout);
        View view = mLayoutInflater.inflate(parser, null);
        assertNotNull(view);

        LinearLayout linearLayout = new LinearLayout(mContext);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setHorizontalGravity(Gravity.LEFT);
        linearLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        assertEquals(0, linearLayout.getChildCount());

        try {
            mLayoutInflater.inflate(parser, linearLayout);
            fail("should throw exception");
        } catch (NullPointerException e) {
        }

        parser = mContext.getResources().getLayout(R.layout.inflater_layout);
        view = mLayoutInflater.inflate(parser, linearLayout);
        assertNotNull(view);
        assertEquals(1, linearLayout.getChildCount());
        parser = mContext.getResources().getLayout(R.layout.inflater_layout);
        view = mLayoutInflater.inflate(parser, linearLayout);
        assertNotNull(view);
        assertEquals(2, linearLayout.getChildCount());

        parser = getParser();
        view = mLayoutInflater.inflate(parser, linearLayout);
        assertNotNull(view);
        assertEquals(3, linearLayout.getChildCount());
    }

    @Test(expected=NullPointerException.class)
    public void testInflateParserAttachToRootNullParser() {
        mLayoutInflater.inflate(null, null, false);
    }

    @Test
    public void testInflateParserAttachToRoot() {
        XmlResourceParser parser = mContext.getResources().getLayout(R.layout.inflater_layout);
        View view = mLayoutInflater.inflate(parser, null, false);
        assertNotNull(view);

        LinearLayout linearLayout = new LinearLayout(mContext);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setHorizontalGravity(Gravity.LEFT);
        linearLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        assertEquals(0, linearLayout.getChildCount());

        try {
            mLayoutInflater.inflate(parser, linearLayout, false);
            fail("should throw exception");
        } catch (NullPointerException e) {
        }

        parser = mContext.getResources().getLayout(R.layout.inflater_layout);
        view = mLayoutInflater.inflate(parser, linearLayout, false);
        assertNull(view.getParent());
        assertNotNull(view);
        assertEquals(0, linearLayout.getChildCount());
        parser = mContext.getResources().getLayout(R.layout.inflater_layout);
        assertEquals(0, linearLayout.getChildCount());
        view = mLayoutInflater.inflate(parser, linearLayout, true);
        assertNotNull(view);
        assertNull(view.getParent());
        assertEquals(1, linearLayout.getChildCount());

        parser = getParser();
        try {
            mLayoutInflater.inflate(parser, linearLayout, false);
            fail("should throw exception");
        } catch (InflateException e) {
        }

        parser = getParser();

        view = mLayoutInflater.inflate(parser, linearLayout, true);
        assertNotNull(view);
        assertEquals(2, linearLayout.getChildCount());
    }

    @Test
    public void testOverrideTheme() {
        View container = mLayoutInflater.inflate(R.layout.inflater_override_theme_layout, null);
        verifyThemeType(container, "view_outer", R.id.view_outer, 1);
        verifyThemeType(container, "view_inner", R.id.view_inner, 2);
        verifyThemeType(container, "view_attr", R.id.view_attr, 3);
        verifyThemeType(container, "view_include", R.id.view_include, 4);
        verifyThemeType(container, "view_include_notheme", R.id.view_include_notheme, 5);
    }

    private void verifyThemeType(View container, String tag, int id, int type) {
        TypedValue outValue = new TypedValue();
        View view = container.findViewById(id);
        assertNotNull("Found " + tag, view);
        Theme theme = view.getContext().getTheme();
        boolean resolved = theme.resolveAttribute(R.attr.themeType, outValue, true);
        assertTrue("Resolved themeType for " + tag, resolved);
        assertEquals(tag + " has themeType " + type, type, outValue.data);
    }

    @Test
    public void testInflateTags() {
        final View view = mLayoutInflater.inflate(
                android.view.cts.R.layout.inflater_layout_tags, null);
        assertNotNull(view);

        checkViewTag(view, R.id.viewlayout_root, R.id.tag_viewlayout_root, R.string.tag1);
        checkViewTag(view, R.id.mock_view, R.id.tag_mock_view, R.string.tag2);
    }

    private void checkViewTag(View parent, int viewId, int tagId, int valueResId) {
        final View target = parent.findViewById(viewId);
        assertNotNull("Found target view for " + viewId, target);

        final Object tag = target.getTag(tagId);
        assertNotNull("Tag is set", tag);
        assertTrue("Tag is a character sequence", tag instanceof CharSequence);

        final Context targetContext = target.getContext();
        final CharSequence expectedValue = targetContext.getString(valueResId);
        assertEquals(tagId + " has tag " + expectedValue, expectedValue, tag);
    }

    @Test
    public void testInclude() {
        final Context themedContext = new ContextThemeWrapper(mContext, R.style.Test_Theme);
        final LayoutInflater inflater = LayoutInflater.from(themedContext);

        final View container = inflater.inflate(R.layout.inflater_layout_include, null);
        assertNotNull(container.findViewById(R.id.include_layout_explicit));
        assertNotNull(container.findViewById(R.id.include_layout_from_attr));
    }

    @Test(expected = InflateException.class)
    public void testIncludeMissingAttr() {
        final View container = mLayoutInflater.inflate(R.layout.inflater_layout_include, null);
        assertNotNull(container.findViewById(R.id.include_layout_from_attr));
    }

    static class MockLayoutInflater extends LayoutInflater {

        public MockLayoutInflater(Context c) {
            super(c);
        }

        public MockLayoutInflater(LayoutInflater original, Context newContext) {
            super(original, newContext);
        }

        @Override
        public View onCreateView(String name, AttributeSet attrs)
                throws ClassNotFoundException {
            return super.onCreateView(name, attrs);
        }

        @Override
        public LayoutInflater cloneInContext(Context newContext) {
            return null;
        }
    }

    private XmlResourceParser getParser() {
        XmlResourceParser parser = null;
        ActivityInfo ai = null;
        ComponentName mComponentName = new ComponentName(mContext,
                MockActivity.class);
        try {
            ai = mContext.getPackageManager().getActivityInfo(mComponentName,
                    PackageManager.GET_META_DATA);
            parser = ai.loadXmlMetaData(mContext.getPackageManager(),
                    "android.view.merge");
        } catch (Exception e) {
        }
        return parser;
    }
}
