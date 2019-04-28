/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.layoutlib.bridge.intensive;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.internal.R;
import com.android.layoutlib.bridge.android.BridgeContext;
import com.android.layoutlib.bridge.android.RenderParamsFlags;
import com.android.layoutlib.bridge.impl.ParserFactory;
import com.android.layoutlib.bridge.impl.RenderAction;
import com.android.layoutlib.bridge.impl.RenderActionTestUtil;
import com.android.layoutlib.bridge.impl.ResourceHelper;
import com.android.layoutlib.bridge.intensive.setup.ConfigGenerator;
import com.android.layoutlib.bridge.intensive.setup.LayoutLibTestCallback;
import com.android.layoutlib.bridge.intensive.setup.LayoutPullParser;
import com.android.resources.Density;
import com.android.resources.Navigation;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;

import org.junit.Test;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources_Delegate;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Set of render tests
 */
public class RenderTests extends RenderTestBase {

    @Test
    public void testActivity() throws ClassNotFoundException, FileNotFoundException {
        renderAndVerify("activity.xml", "activity.png");
    }

    @Test
    public void testActivityOnOldTheme() throws ClassNotFoundException, FileNotFoundException {
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        LayoutPullParser parser = LayoutPullParser.createFromString(
                "<RelativeLayout xmlns:android=\"http://schemas" +
                ".android.com/apk/res/android\"\n" +
                "                android:layout_width=\"match_parent\"\n" +
                "                android:layout_height=\"match_parent\"\n" +
                "                android:paddingLeft=\"@dimen/activity_horizontal_margin\"\n" +
                "                android:paddingRight=\"@dimen/activity_horizontal_margin\"\n" +
                "                android:paddingTop=\"@dimen/activity_vertical_margin\"\n" +
                "                android:paddingBottom=\"@dimen/activity_vertical_margin\">\n" +
                "    <TextView\n" +
                "        android:text=\"@string/hello_world\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"200dp\"\n" +
                "        android:background=\"#FF0000\"\n" +
                "        android:id=\"@+id/text1\"/>\n" +
                "</RelativeLayout>");
        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_5,
                layoutLibCallback, "Theme.NoTitleBar", false,
                RenderingMode.NORMAL, 22);

        renderAndVerify(params, "simple_activity-old-theme.png");
    }

    @Test
    public void testTranslucentBars() throws ClassNotFoundException, FileNotFoundException {
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        LayoutPullParser parser = createParserFromPath("four_corners.xml");
        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_5,
                layoutLibCallback, "Theme.Material.Light.NoActionBar.TranslucentDecor", false,
                RenderingMode.NORMAL, 22);
        renderAndVerify(params, "four_corners_translucent.png");

        parser = createParserFromPath("four_corners.xml");
        params = getSessionParams(parser, ConfigGenerator.NEXUS_5_LAND,
                layoutLibCallback, "Theme.Material.Light.NoActionBar.TranslucentDecor", false,
                RenderingMode.NORMAL, 22);
        renderAndVerify(params, "four_corners_translucent_land.png");

        parser = createParserFromPath("four_corners.xml");
        params = getSessionParams(parser, ConfigGenerator.NEXUS_5,
                layoutLibCallback, "Theme.Material.Light.NoActionBar", false,
                RenderingMode.NORMAL, 22);
        renderAndVerify(params, "four_corners.png");
    }

    @Test
    public void testAllWidgets() throws ClassNotFoundException, FileNotFoundException {
        renderAndVerify("allwidgets.xml", "allwidgets.png");

        // We expect fidelity warnings for Path.isConvex. Fail for anything else.
        sRenderMessages.removeIf(message -> message.equals("Path.isConvex is not supported."));
    }

    @Test
    public void testArrayCheck() throws ClassNotFoundException, FileNotFoundException {
        renderAndVerify("array_check.xml", "array_check.png");
    }

    @Test
    public void testAllWidgetsTablet() throws ClassNotFoundException, FileNotFoundException {
        renderAndVerify("allwidgets.xml", "allwidgets_tab.png", ConfigGenerator.NEXUS_7_2012);

        // We expect fidelity warnings for Path.isConvex. Fail for anything else.
        sRenderMessages.removeIf(message -> message.equals("Path.isConvex is not supported."));
    }

    @Test
    public void testActivityActionBar() throws ClassNotFoundException {
        String simpleActivity =
                "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "                android:layout_width=\"match_parent\"\n" +
                "                android:layout_height=\"match_parent\"\n" +
                "                android:paddingLeft=\"@dimen/activity_horizontal_margin\"\n" +
                "                android:paddingRight=\"@dimen/activity_horizontal_margin\"\n" +
                "                android:paddingTop=\"@dimen/activity_vertical_margin\"\n" +
                "                android:paddingBottom=\"@dimen/activity_vertical_margin\">\n" +
                "    <TextView\n" +
                "        android:text=\"@string/hello_world\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"200dp\"\n" +
                "        android:background=\"#FF0000\"\n" +
                "        android:id=\"@+id/text1\"/>\n" +
                "</RelativeLayout>";

        LayoutPullParser parser = LayoutPullParser.createFromString(simpleActivity);
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_5,
                layoutLibCallback, "Theme.Material.Light.NoActionBar", false,
                RenderingMode.V_SCROLL, 22);

        renderAndVerify(params, "simple_activity_noactionbar.png");

        parser = LayoutPullParser.createFromString(simpleActivity);
        params = getSessionParams(parser, ConfigGenerator.NEXUS_5,
                layoutLibCallback, "Theme.Material.Light", false,
                RenderingMode.V_SCROLL, 22);

        renderAndVerify(params, "simple_activity.png");

        // This also tests that a theme with "NoActionBar" DOES HAVE an action bar when we are
        // displaying menus.
        parser = LayoutPullParser.createFromString(simpleActivity);
        params = getSessionParams(parser, ConfigGenerator.NEXUS_5,
                layoutLibCallback, "Theme.Material.Light.NoActionBar", false,
                RenderingMode.V_SCROLL, 22);
        params.setFlag(RenderParamsFlags.FLAG_KEY_ROOT_TAG, "menu");
        renderAndVerify(params, "simple_activity.png");
    }

    @Test
    public void testOnApplyInsetsCall()
            throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        // We get the widget via reflection to avoid IntelliJ complaining about the class being
        // located in the wrong package. (From the Bridge tests point of view, it is)
        Class insetsWidgetClass = Class.forName("com.android.layoutlib.test.myapplication.widgets" +
                ".InsetsWidget");
        Field field = insetsWidgetClass.getDeclaredField("sApplyInsetsCalled");
        assertFalse((Boolean)field.get(null));

        LayoutPullParser parser = LayoutPullParser.createFromString(
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "              android:padding=\"16dp\"\n" +
                "              android:orientation=\"horizontal\"\n" +
                "              android:layout_width=\"wrap_content\"\n" +
                "              android:layout_height=\"wrap_content\">\n" + "\n" +
                "    <com.android.layoutlib.test.myapplication.widgets.InsetsWidget\n" +
                "        android:text=\"Hello world\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:id=\"@+id/text1\"/>\n" + "</LinearLayout>\n");
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_5,
                layoutLibCallback, "Theme.Material.Light.NoActionBar", false,
                RenderingMode.NORMAL, 22);

        render(params, -1);

        assertTrue((Boolean)field.get(null));
        field.set(null, false);
    }

    /** Test expand_layout.xml */
    @Test
    public void testExpand() throws ClassNotFoundException, FileNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = createParserFromPath("expand_vert_layout.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        ConfigGenerator customConfigGenerator = new ConfigGenerator()
                .setScreenWidth(300)
                .setScreenHeight(20)
                .setDensity(Density.XHIGH)
                .setNavigation(Navigation.NONAV);

        SessionParams params = getSessionParams(parser, customConfigGenerator,
                layoutLibCallback, "Theme.Material.Light.NoActionBar.Fullscreen", false,
                RenderingMode.V_SCROLL, 22);

        renderAndVerify(params, "expand_vert_layout.png");

        customConfigGenerator = new ConfigGenerator()
                .setScreenWidth(20)
                .setScreenHeight(300)
                .setDensity(Density.XHIGH)
                .setNavigation(Navigation.NONAV);
        parser = createParserFromPath("expand_horz_layout.xml");
        params = getSessionParams(parser, customConfigGenerator,
                layoutLibCallback, "Theme.Material.Light.NoActionBar.Fullscreen", false,
                RenderingMode.H_SCROLL, 22);

        renderAndVerify(params, "expand_horz_layout.png");
    }

    /** Test indeterminate_progressbar.xml */
    @Test
    public void testVectorAnimation() throws ClassNotFoundException {
        String layout = "\n" +
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "              android:padding=\"16dp\"\n" +
                "              android:orientation=\"horizontal\"\n" +
                "              android:layout_width=\"fill_parent\"\n" +
                "              android:layout_height=\"fill_parent\">\n" + "\n" +
                "    <ProgressBar\n" + "             android:layout_height=\"fill_parent\"\n" +
                "             android:layout_width=\"fill_parent\" />\n" + "\n" +
                "</LinearLayout>\n";

        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_5,
                layoutLibCallback, "Theme.Material.NoActionBar.Fullscreen", false,
                RenderingMode.V_SCROLL, 22);

        renderAndVerify(params, "animated_vector.png", TimeUnit.SECONDS.toNanos(2));

        parser = LayoutPullParser.createFromString(layout);
        params = getSessionParams(parser, ConfigGenerator.NEXUS_5,
                layoutLibCallback, "Theme.Material.NoActionBar.Fullscreen", false,
                RenderingMode.V_SCROLL, 22);
        renderAndVerify(params, "animated_vector_1.png", TimeUnit.SECONDS.toNanos(3));
    }

    /**
     * Test a vector drawable that uses trimStart and trimEnd. It also tests all the primitives
     * for vector drawables (lines, moves and cubic and quadratic curves).
     */
    @Test
    public void testVectorDrawable() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString(
               "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                       "              android:padding=\"16dp\"\n" +
                       "              android:orientation=\"horizontal\"\n" +
                       "              android:layout_width=\"fill_parent\"\n" +
                       "              android:layout_height=\"fill_parent\">\n" +
                       "    <ImageView\n" +
                       "             android:layout_height=\"fill_parent\"\n" +
                       "             android:layout_width=\"fill_parent\"\n" +
                       "             android:src=\"@drawable/multi_path\" />\n" + "\n" +
                       "</LinearLayout>");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_5,
                layoutLibCallback, "Theme.Material.NoActionBar.Fullscreen", false,
                RenderingMode.V_SCROLL, 22);

        renderAndVerify(params, "vector_drawable.png", TimeUnit.SECONDS.toNanos(2));
    }

    /**
     * Regression test for http://b.android.com/91383 and http://b.android.com/203797
     */
    @Test
    public void testVectorDrawable91383() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString(
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "              android:padding=\"16dp\"\n" +
                        "              android:orientation=\"vertical\"\n" +
                        "              android:layout_width=\"fill_parent\"\n" +
                        "              android:layout_height=\"fill_parent\">\n" +
                        "    <ImageView\n" +
                        "        android:layout_height=\"wrap_content\"\n" +
                        "        android:layout_width=\"wrap_content\"\n" +
                        "        android:src=\"@drawable/android\"/>\n" +
                        "    <ImageView\n" +
                        "        android:layout_height=\"wrap_content\"\n" +
                        "        android:layout_width=\"wrap_content\"\n" +
                        "        android:src=\"@drawable/headset\"/>\n" +
                        "</LinearLayout>");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_5,
                layoutLibCallback, "Theme.Material.NoActionBar.Fullscreen", false,
                RenderingMode.V_SCROLL, 22);

        renderAndVerify(params, "vector_drawable_91383.png", TimeUnit.SECONDS.toNanos(2));
    }

    /**
     * Test a vector drawable that uses trimStart and trimEnd. It also tests all the primitives
     * for vector drawables (lines, moves and cubic and quadratic curves).
     */
    @Test
    public void testVectorDrawableHasMultipleLineInPathData() throws ClassNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = LayoutPullParser.createFromString(
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "              android:padding=\"16dp\"\n" +
                        "              android:orientation=\"horizontal\"\n" +
                        "              android:layout_width=\"match_parent\"\n" +
                        "              android:layout_height=\"match_parent\">\n" +
                        "    <ImageView\n" +
                        "             android:layout_height=\"match_parent\"\n" +
                        "             android:layout_width=\"match_parent\"\n" +
                        "             android:src=\"@drawable/multi_line_of_path_data\" />\n\n" +
                        "</LinearLayout>");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_5,
                layoutLibCallback, "Theme.Material.NoActionBar.Fullscreen", false,
                RenderingMode.V_SCROLL, 22);

        renderAndVerify(params, "vector_drawable_multi_line_of_path_data.png",
                TimeUnit.SECONDS.toNanos(2));
    }

    /** Test activity.xml */
    @Test
    public void testScrollingAndMeasure() throws ClassNotFoundException, FileNotFoundException {
        // Create the layout pull parser.
        LayoutPullParser parser = createParserFromPath("scrolled.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_5,
                layoutLibCallback, "Theme.Material.NoActionBar.Fullscreen", false,
                RenderingMode.V_SCROLL, 22);
        params.setForceNoDecor();
        params.setExtendedViewInfoMode(true);

        // Do an only-measure pass
        RenderSession session = sBridge.createSession(params);
        session.measure();
        RenderResult result = RenderResult.getFromSession(session);
        assertNotNull(result);
        assertNotNull(result.getResult());
        assertTrue(result.getResult().isSuccess());

        ViewInfo rootLayout = result.getRootViews().get(0);
        // Check the first box in the main LinearLayout
        assertEquals(-90, rootLayout.getChildren().get(0).getTop());
        assertEquals(-30, rootLayout.getChildren().get(0).getLeft());
        assertEquals(90, rootLayout.getChildren().get(0).getBottom());
        assertEquals(150, rootLayout.getChildren().get(0).getRight());

        // Check the first box within the nested LinearLayout
        assertEquals(-450, rootLayout.getChildren().get(5).getChildren().get(0).getTop());
        assertEquals(90, rootLayout.getChildren().get(5).getChildren().get(0).getLeft());
        assertEquals(-270, rootLayout.getChildren().get(5).getChildren().get(0).getBottom());
        assertEquals(690, rootLayout.getChildren().get(5).getChildren().get(0).getRight());

        // Do a full render pass
        parser = createParserFromPath("scrolled.xml");

        params = getSessionParams(parser, ConfigGenerator.NEXUS_5,
                layoutLibCallback, "Theme.Material.NoActionBar.Fullscreen", false,
                RenderingMode.V_SCROLL, 22);
        params.setForceNoDecor();
        params.setExtendedViewInfoMode(true);

        result = renderAndVerify(params, "scrolled.png");
        assertNotNull(result);
        assertNotNull(result.getResult());
        assertTrue(result.getResult().isSuccess());
    }

    @Test
    public void testGetResourceNameVariants() throws Exception {
        // Setup
        // Create the layout pull parser for our resources (empty.xml can not be part of the test
        // app as it won't compile).
        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_4,
                layoutLibCallback, "AppTheme", true, RenderingMode.NORMAL, 22);
        AssetManager assetManager = AssetManager.getSystem();
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);
        BridgeContext context = new BridgeContext(params.getProjectKey(), metrics, params.getResources(),
                params.getAssets(), params.getLayoutlibCallback(), configuration,
                params.getTargetSdkVersion(), params.isRtlSupported());
        Resources resources = Resources_Delegate.initSystem(context, assetManager, metrics,
                configuration, params.getLayoutlibCallback());
        // Test
        assertEquals("android:style/ButtonBar",
                resources.getResourceName(android.R.style.ButtonBar));
        assertEquals("android", resources.getResourcePackageName(android.R.style.ButtonBar));
        assertEquals("ButtonBar", resources.getResourceEntryName(android.R.style.ButtonBar));
        assertEquals("style", resources.getResourceTypeName(android.R.style.ButtonBar));
        int id = Resources_Delegate.getLayoutlibCallback(resources).getResourceId(
                ResourceType.STRING,
                "app_name");
        assertEquals("com.android.layoutlib.test.myapplication:string/app_name",
                resources.getResourceName(id));
        assertEquals("com.android.layoutlib.test.myapplication",
                resources.getResourcePackageName(id));
        assertEquals("string", resources.getResourceTypeName(id));
        assertEquals("app_name", resources.getResourceEntryName(id));

        context.disposeResources();
    }

    @Test
    public void testStringEscaping() throws Exception {
        // Setup
        // Create the layout pull parser for our resources (empty.xml can not be part of the test
        // app as it won't compile).
        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(RenderTestBase.getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_4,
                layoutLibCallback, "AppTheme", true, RenderingMode.NORMAL, 22);
        AssetManager assetManager = AssetManager.getSystem();
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);
        BridgeContext context = new BridgeContext(params.getProjectKey(), metrics, params.getResources(),
                params.getAssets(), params.getLayoutlibCallback(), configuration,
                params.getTargetSdkVersion(), params.isRtlSupported());
        Resources resources = Resources_Delegate.initSystem(context, assetManager, metrics,
                configuration, params.getLayoutlibCallback());

        int id = Resources_Delegate.getLayoutlibCallback(resources).getResourceId(
                ResourceType.ARRAY,
                "string_array");
        String[] strings = resources.getStringArray(id);
        assertArrayEquals(
                new String[]{"mystring", "Hello world!", "candidates", "Unknown", "?EC"},
                strings);
        assertTrue(sRenderMessages.isEmpty());

        context.disposeResources();
    }

    @Test
    public void testFonts() throws ClassNotFoundException, FileNotFoundException {
        // TODO: styles seem to be broken in TextView
        renderAndVerify("fonts_test.xml", "font_test.png");
    }

    @Test
    public void testAdaptiveIcon() throws ClassNotFoundException, FileNotFoundException {
        // Create the layout pull parser.
        String layout =
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "              android:padding=\"16dp\"\n" +
                        "              android:orientation=\"horizontal\"\n" +
                        "              android:layout_width=\"fill_parent\"\n" +
                        "              android:layout_height=\"fill_parent\">\n" +
                        "    <ImageView\n" +
                        "             android:layout_height=\"wrap_content\"\n" +
                        "             android:layout_width=\"wrap_content\"\n" +
                        "             android:src=\"@drawable/adaptive\" />\n" +
                        "</LinearLayout>\n";
        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_5,
                layoutLibCallback, "Theme.Material.NoActionBar.Fullscreen", false,
                RenderingMode.V_SCROLL, 22);

        renderAndVerify(params, "adaptive_icon.png");

        layoutLibCallback.setAdaptiveIconMaskPath(
                "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 " +
                        "22.4 22.4 0 50 0Z");
        params =
                getSessionParams(LayoutPullParser.createFromString(layout), ConfigGenerator.NEXUS_5,
                        layoutLibCallback, "Theme.Material.NoActionBar.Fullscreen", false,
                        RenderingMode.V_SCROLL, 22);
        renderAndVerify(params, "adaptive_icon_circle.png");

        layoutLibCallback.setAdaptiveIconMaskPath(
                "M50,0L92,0C96.42,0 100,4.58 100 8L100,92C100, 96.42 96.42 100 92 100L8 100C4.58," +
                        " 100 0 96.42 0 92L0 8 C 0 4.42 4.42 0 8 0L50 0Z");
        params =
                getSessionParams(LayoutPullParser.createFromString(layout), ConfigGenerator.NEXUS_5,
                        layoutLibCallback, "Theme.Material.NoActionBar.Fullscreen", false,
                        RenderingMode.V_SCROLL, 22);
        renderAndVerify(params, "adaptive_icon_rounded_corners.png");

        layoutLibCallback.setAdaptiveIconMaskPath(
                "M50,0 C10,0 0,10 0,50 0,90 10,100 50,100 90,100 100,90 100,50 100,10 90,0 50,0 Z");
        params =
                getSessionParams(LayoutPullParser.createFromString(layout), ConfigGenerator.NEXUS_5,
                        layoutLibCallback, "Theme.Material.NoActionBar.Fullscreen", false,
                        RenderingMode.V_SCROLL, 22);
        renderAndVerify(params, "adaptive_icon_squircle.png");
    }

    @Test
    public void testColorTypedValue() throws Exception {
        // Setup
        // Create the layout pull parser for our resources (empty.xml can not be part of the test
        // app as it won't compile).
        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(RenderTestBase.getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_4,
                layoutLibCallback, "AppTheme", true, RenderingMode.NORMAL, 22);
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);

        BridgeContext mContext =
                new BridgeContext(params.getProjectKey(), metrics, params.getResources(),
                        params.getAssets(), params.getLayoutlibCallback(), configuration,
                        params.getTargetSdkVersion(), params.isRtlSupported());

        TypedValue outValue = new TypedValue();
        mContext.resolveThemeAttribute(android.R.attr.colorPrimary, outValue, true);
        assertEquals(TypedValue.TYPE_INT_COLOR_ARGB8, outValue.type);
        assertNotEquals(0, outValue.data);
        assertTrue(sRenderMessages.isEmpty());
    }

    @Test
    public void testColorStateList() throws Exception {
        final String STATE_LIST = "<selector xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "    <item android:state_pressed=\"true\"\n" +
                "          android:color=\"?android:attr/colorForeground\"/> \n" +
                "    <item android:state_focused=\"true\"\n" +
                "          android:color=\"?android:attr/colorBackground\"/> \n" +
                "    <item android:color=\"#a000\"/> <!-- default -->\n" + "</selector>";

        File tmpColorList = File.createTempFile("statelist", "xml");
        try(PrintWriter output = new PrintWriter(new FileOutputStream(tmpColorList))) {
            output.println(STATE_LIST);
        }

        // Setup
        // Create the layout pull parser for our resources (empty.xml can not be part of the test
        // app as it won't compile).
        ParserFactory.setParserFactory(new com.android.ide.common.rendering.api.ParserFactory() {
            @Override
            public XmlPullParser createParser(String debugName) throws XmlPullParserException {
                return new KXmlParser();
            }
        });

        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(RenderTestBase.getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_4,
                layoutLibCallback, "Theme.Material", false, RenderingMode.NORMAL, 22);
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);

        BridgeContext mContext =
                new BridgeContext(params.getProjectKey(), metrics, params.getResources(),
                        params.getAssets(), params.getLayoutlibCallback(), configuration,
                        params.getTargetSdkVersion(), params.isRtlSupported());
        mContext.initResources();
        BridgeContext oldContext = RenderActionTestUtil.setBridgeContext(mContext);

        try {
            ColorStateList stateList = ResourceHelper.getColorStateList(
                    new ResourceValue(ResourceUrl.create(null, ResourceType.COLOR, "test_list"),
                            tmpColorList.getAbsolutePath()), mContext, null);
            assertNotNull(stateList);
            assertEquals(Color.parseColor("#ffffffff"), stateList.getColorForState(
                    StateSet.get(StateSet.VIEW_STATE_PRESSED),
                    0
            ));
            assertEquals(Color.parseColor("#ff303030"), stateList.getColorForState(
                    StateSet.get(StateSet.VIEW_STATE_FOCUSED),
                    0
            ));
            assertEquals(Color.parseColor("#AA000000"), stateList.getDefaultColor());

            // Now apply theme overlay and check the colors changed
            Resources.Theme theme = mContext.getResources().newTheme();
            theme.applyStyle(R.style.ThemeOverlay_Material_Light, true);
            stateList = ResourceHelper.getColorStateList(
                    new ResourceValue(ResourceUrl.create(null, ResourceType.COLOR, "test_list"),
                            tmpColorList.getAbsolutePath()), mContext, theme);
            assertNotNull(stateList);
            assertEquals(Color.parseColor("#ff000000"), stateList.getColorForState(
                    StateSet.get(StateSet.VIEW_STATE_PRESSED),
                    0
            ));
            assertEquals(Color.parseColor("#fffafafa"), stateList.getColorForState(
                    StateSet.get(StateSet.VIEW_STATE_FOCUSED),
                    0
            ));
            assertEquals(Color.parseColor("#AA000000"), stateList.getDefaultColor());
        } finally {
            RenderActionTestUtil.setBridgeContext(oldContext);
        }
        mContext.disposeResources();
    }

    @Test
    public void testRectangleShadow() throws Exception {
        renderAndVerify("shadows_test.xml", "shadows_test.png");
    }

    @Test
    public void testResourcesGetIdentifier() throws Exception {
        // Setup
        // Create the layout pull parser for our resources (empty.xml can not be part of the test
        // app as it won't compile).
        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_4,
                layoutLibCallback, "AppTheme", true, RenderingMode.NORMAL, 22);
        AssetManager assetManager = AssetManager.getSystem();
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);
        BridgeContext context = new BridgeContext(params.getProjectKey(), metrics, params.getResources(),
                params.getAssets(), params.getLayoutlibCallback(), configuration,
                params.getTargetSdkVersion(), params.isRtlSupported());
        Resources resources = Resources_Delegate.initSystem(context, assetManager, metrics,
                configuration, params.getLayoutlibCallback());
        int id = Resources_Delegate.getLayoutlibCallback(resources).getResourceId(
                ResourceType.STRING,
                "app_name");
        assertEquals(id, resources.getIdentifier("string/app_name", null, null));
        assertEquals(id, resources.getIdentifier("app_name", "string", null));
        assertEquals(0, resources.getIdentifier("string/does_not_exist", null, null));
        assertEquals(R.string.accept, resources.getIdentifier("android:string/accept", null,
                null));
        assertEquals(R.string.accept, resources.getIdentifier("string/accept", null,
                "android"));
        assertEquals(R.id.message, resources.getIdentifier("id/message", null,
                "android"));
        assertEquals(R.string.accept, resources.getIdentifier("accept", "string",
                "android"));

        context.disposeResources();
    }

    /**
     * If a 9patch image was in the nodpi or anydpi folder, the density of the image was 0 resulting
     * in a float division by 0 and thus an infinite padding
     * when layoutlib tries to scale the padding of the 9patch.
     *
     * http://b/37136109
     */
    @Test
    public void test9PatchNoDPIBackground() throws Exception {
        String layout =
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    android:layout_width=\"match_parent\"\n" +
                        "    android:layout_height=\"match_parent\"\n" +
                        "    android:background=\"@drawable/ninepatch\"\n" +
                        "    android:layout_margin=\"20dp\"\n" +
                        "    android:orientation=\"vertical\">\n\n" +
                        "    <Button\n" +
                        "        android:layout_width=\"wrap_content\"\n" +
                        "        android:layout_height=\"wrap_content\"\n" +
                        "        android:text=\"Button\" />\n\n" +
                        "    <Button\n" +
                        "        android:layout_width=\"wrap_content\"\n" +
                        "        android:layout_height=\"wrap_content\"\n" +
                        "        android:text=\"Button\" />\n"
                        + "</LinearLayout>";

        LayoutPullParser parser = LayoutPullParser.createFromString(layout);
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();

        SessionParams params = getSessionParams(parser, ConfigGenerator.NEXUS_5, layoutLibCallback,
                "Theme.Material.NoActionBar.Fullscreen", false, RenderingMode.V_SCROLL, 22);

        renderAndVerify(params, "ninepatch_background.png");
    }
}
