/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.cts;

import android.service.appwidget.AppWidgetServiceDumpProto;
import android.service.appwidget.WidgetProto;

/**
 * Test to check that the appwidget service properly outputs its dump state.
 */
public class AppWidgetIncidentTest extends ProtoDumpTestCase {

    private static final String DEVICE_TEST_CLASS = ".AppWidgetTest";
    private static final String DEVICE_SIDE_TEST_APK = "CtsAppWidgetApp.apk";
    private static final String DEVICE_SIDE_TEST_PACKAGE = "android.appwidget.cts";
    private static final String DEVICE_SIDE_WIDGET_CLASS_1 =
        "android.appwidget.cts.provider.FirstAppWidgetProvider";
    private static final String DEVICE_SIDE_WIDGET_CLASS_2 =
        "android.appwidget.cts.provider.SecondAppWidgetProvider";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        installPackage(DEVICE_SIDE_TEST_APK, /* grantPermissions= */ true);
    }

    @Override
    protected void tearDown() throws Exception {
        getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);
        super.tearDown();
    }

    private boolean hasAppWidgets() throws Exception {
        return getDevice().hasFeature("android.software.app_widgets");
    }

    public void testAppWidgetProtoDump_firstComponent() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        WidgetProto widget = prepare("testBindWidget1");

        assertNotNull(widget);
        assertEquals(DEVICE_SIDE_TEST_PACKAGE,
            widget.getProviderPackage());
        assertEquals(DEVICE_SIDE_WIDGET_CLASS_1,
            widget.getProviderClass());
        assertEquals(false, widget.getIsCrossProfile());
        assertEquals(false, widget.getIsHostStopped());
        assertEquals(0, widget.getMinWidth());
        assertEquals(0, widget.getMinHeight());
        assertEquals(0, widget.getMaxWidth());
        assertEquals(0, widget.getMaxHeight());

        cleanup();
    }

    public void testAppWidgetProtoDump_secondComponent() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        WidgetProto widget = prepare("testBindWidget2");

        assertNotNull(widget);
        assertEquals(DEVICE_SIDE_TEST_PACKAGE,
            widget.getProviderPackage());
        assertEquals(DEVICE_SIDE_WIDGET_CLASS_2,
            widget.getProviderClass());
        assertEquals(false, widget.getIsCrossProfile());
        assertEquals(false, widget.getIsHostStopped());
        assertEquals(1, widget.getMinWidth());
        assertEquals(2, widget.getMinHeight());
        assertEquals(3, widget.getMaxWidth());
        assertEquals(4, widget.getMaxHeight());

        cleanup();
    }

    public void testAppWidgetProtoDump_firstComponentNotBound() throws Exception {
        if (!hasAppWidgets()) {
            return;
        }

        WidgetProto widget = prepare("testAllocateOnlyWidget1");

        // a widget that is not bound must not show up in the dump
        assertNull(widget);

        cleanup();
    }

    private WidgetProto prepare(String testMethodName) throws Exception {
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, DEVICE_TEST_CLASS, testMethodName);

        AppWidgetServiceDumpProto dump = getDump(AppWidgetServiceDumpProto.parser(),
            "dumpsys appwidget --proto");

        for (WidgetProto widgetProto : dump.getWidgetsList()) {
            if (DEVICE_SIDE_TEST_PACKAGE.equals(widgetProto.getHostPackage())) {
                return widgetProto;
            }
        }
        return null;
    }

    private void cleanup() throws Exception {
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, DEVICE_TEST_CLASS, "testCleanup");
    }

}
