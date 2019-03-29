/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.view.accessibility.cts;

import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;
import android.test.InstrumentationTestCase;
import android.view.accessibility.CaptioningManager;
import android.view.accessibility.CaptioningManager.CaptionStyle;
import android.view.accessibility.CaptioningManager.CaptioningChangeListener;
import org.mockito.Mockito;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import static org.mockito.Matchers.anyFloat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Tests whether the CaptioningManager APIs are functional.
 */
public class CaptioningManagerTest extends InstrumentationTestCase {
    private static final int LISTENER_TIMEOUT = 3000;
    private CaptioningManager mManager;
    private UiAutomation mUiAutomation;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mManager = getInstrumentation().getTargetContext().getSystemService(
                CaptioningManager.class);

        assertNotNull("Obtained captioning manager", mManager);

        mUiAutomation = getInstrumentation().getUiAutomation();
    }

    /**
     * Tests whether a client can observe changes in caption properties.
     */
    public void testChangeListener() {
        putSecureSetting("accessibility_captioning_enabled","0");
        putSecureSetting("accessibility_captioning_preset", "1");
        putSecureSetting("accessibility_captioning_locale", "en_US");
        putSecureSetting("accessibility_captioning_font_scale", "1.0");

        CaptioningChangeListener mockListener = mock(CaptioningChangeListener.class);
        mManager.addCaptioningChangeListener(mockListener);

        putSecureSetting("accessibility_captioning_enabled", "1");
        verify(mockListener, timeout(LISTENER_TIMEOUT)).onEnabledChanged(true);

        // Style change gets posted in a Runnable, so we need to wait for idle.
        putSecureSetting("accessibility_captioning_preset", "-1");
        getInstrumentation().waitForIdleSync();
        verify(mockListener, timeout(LISTENER_TIMEOUT)).onUserStyleChanged(anyObject());

        putSecureSetting("accessibility_captioning_locale", "ja_JP");
        verify(mockListener, timeout(LISTENER_TIMEOUT)).onLocaleChanged(anyObject());

        putSecureSetting("accessibility_captioning_font_scale", "2.0");
        verify(mockListener, timeout(LISTENER_TIMEOUT)).onFontScaleChanged(anyFloat());

        mManager.removeCaptioningChangeListener(mockListener);

        Mockito.reset(mockListener);

        putSecureSetting("accessibility_captioning_enabled","0");
        verifyZeroInteractions(mockListener);

        try {
            mManager.removeCaptioningChangeListener(mockListener);
        } catch (Exception e) {
            throw new AssertionError("Fails silently when removing listener twice", e);
        }
    }

    public void testProperties() {
        putSecureSetting("accessibility_captioning_font_scale", "2.0");
        putSecureSetting("accessibility_captioning_locale", "ja_JP");
        putSecureSetting("accessibility_captioning_enabled", "1");

        assertEquals("Test runner set font scale to 2.0", 2.0f, mManager.getFontScale());
        assertEquals("Test runner set locale to Japanese", Locale.JAPAN, mManager.getLocale());
        assertEquals("Test runner set enabled to true", true, mManager.isEnabled());
    }

    public void testUserStyle() {
        putSecureSetting("accessibility_captioning_preset", "-1");
        putSecureSetting("accessibility_captioning_foreground_color", "511");
        putSecureSetting("accessibility_captioning_background_color", "511");
        putSecureSetting("accessibility_captioning_window_color", "511");
        putSecureSetting("accessibility_captioning_edge_color", "511");
        putSecureSetting("accessibility_captioning_edge_type", "-1");
        deleteSecureSetting("accessibility_captioning_typeface");

        CaptionStyle userStyle = mManager.getUserStyle();
        assertNotNull("Default user style is not null", userStyle);
        assertFalse("Default user style has no edge type", userStyle.hasEdgeType());
        assertFalse("Default user style has no edge color", userStyle.hasEdgeColor());
        assertFalse("Default user style has no foreground color", userStyle.hasForegroundColor());
        assertFalse("Default user style has no background color", userStyle.hasBackgroundColor());
        assertFalse("Default user style has no window color", userStyle.hasWindowColor());
        assertNull("Default user style has no typeface", userStyle.getTypeface());
    }

    private void deleteSecureSetting(String name) {
        execShellCommand("settings delete secure " + name);
    }

    private void putSecureSetting(String name, String value) {
        execShellCommand("settings put secure " + name + " " + value);
    }

    private void execShellCommand(String cmd) {
        ParcelFileDescriptor pfd = mUiAutomation.executeShellCommand(cmd);
        InputStream is = new FileInputStream(pfd.getFileDescriptor());
        try {
            final byte[] buffer = new byte[8192];
            while ((is.read(buffer)) != -1);
        } catch (IOException e) {
            throw new RuntimeException("Failed to exec: " + cmd);
        }
    }
}
