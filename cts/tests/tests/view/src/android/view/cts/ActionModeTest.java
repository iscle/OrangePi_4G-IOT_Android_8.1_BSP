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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.graphics.Rect;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ActionModeTest {
    private Instrumentation mInstrumentation;
    private ActionModeCtsActivity mActivity;

    @Rule
    public ActivityTestRule<ActionModeCtsActivity> mActivityRule =
            new ActivityTestRule<>(ActionModeCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testSetType() {
        final ActionMode mockActionMode = new MockActionMode();
        assertEquals(ActionMode.TYPE_PRIMARY, mockActionMode.getType());

        mockActionMode.setType(ActionMode.TYPE_FLOATING);
        assertEquals(ActionMode.TYPE_FLOATING, mockActionMode.getType());

        mockActionMode.setType(ActionMode.TYPE_PRIMARY);
        assertEquals(ActionMode.TYPE_PRIMARY, mockActionMode.getType());
    }

    @Test
    public void testInvalidateContentRectDoesNotInvalidateFull() {
        final ActionMode mockActionMode = spy(new MockActionMode());

        mockActionMode.invalidateContentRect();

        verify(mockActionMode, never()).invalidate();
    }

    @Test
    public void testInvalidateContentRectOnFloatingCallsCallback() throws Throwable {
        final View view = mActivity.contentView;
        final ActionMode.Callback2 mockCallback = mock(ActionMode.Callback2.class);
        doReturn(Boolean.TRUE).when(mockCallback).onCreateActionMode(
                any(ActionMode.class), any(Menu.class));
        doReturn(Boolean.TRUE).when(mockCallback).onPrepareActionMode(
                any(ActionMode.class), any(Menu.class));

        mActivityRule.runOnUiThread(() -> {
            ActionMode mode = view.startActionMode(mockCallback, ActionMode.TYPE_FLOATING);
            assertNotNull(mode);
            mode.invalidateContentRect();
        });
        mInstrumentation.waitForIdleSync();

        verify(mockCallback, atLeastOnce()).onGetContentRect(any(ActionMode.class), any(View.class),
                any(Rect.class));
    }

    @Test
    public void testSetAndGetTitleOptionalHint() {
        final ActionMode actionMode = new MockActionMode();

        // Check default value.
        assertFalse(actionMode.getTitleOptionalHint());
        // Test set and get.
        actionMode.setTitleOptionalHint(true);
        assertTrue(actionMode.getTitleOptionalHint());
        actionMode.setTitleOptionalHint(false);
        assertFalse(actionMode.getTitleOptionalHint());
    }

    @Test
    public void testSetAndGetTag() {
        final ActionMode actionMode = new MockActionMode();
        Object tag = new Object();

        // Check default value.
        assertNull(actionMode.getTag());

        actionMode.setTag(tag);
        assertSame(tag, actionMode.getTag());
    }

    @Test
    public void testIsTitleOptional() {
        final ActionMode actionMode = new MockActionMode();

        // Check default value.
        assertFalse(actionMode.isTitleOptional());
    }

    @Test
    public void testIsUiFocusable() {
        final ActionMode actionMode = new MockActionMode();

        // Check default value.
        assertTrue(actionMode.isUiFocusable());
    }

    @Test
    public void testHide() {
        final ActionMode actionMode = new MockActionMode();

        actionMode.hide(0);
        actionMode.hide(ActionMode.DEFAULT_HIDE_DURATION);
    }

    @Test
    public void testOnWindowFocusChanged() {
        final ActionMode actionMode = new MockActionMode();

        actionMode.onWindowFocusChanged(true);
        actionMode.onWindowFocusChanged(false);
    }

    protected static class MockActionMode extends ActionMode {
        @Override
        public void setTitle(CharSequence title) {}

        @Override
        public void setTitle(int resId) {}

        @Override
        public void setSubtitle(CharSequence subtitle) {}

        @Override
        public void setSubtitle(int resId) {}

        @Override
        public void setCustomView(View view) {}

        @Override
        public void invalidate() {
        }

        @Override
        public void finish() {}

        @Override
        public Menu getMenu() {
            return null;
        }

        @Override
        public CharSequence getTitle() {
            return null;
        }

        @Override
        public CharSequence getSubtitle() {
            return null;
        }

        @Override
        public View getCustomView() {
            return null;
        }

        @Override
        public MenuInflater getMenuInflater() {
            return null;
        }
    }
}
