/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.app.cts;

import static org.mockito.Mockito.*;

import android.app.stubs.MockActivity;
import android.support.test.annotation.UiThreadTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.ActionMode;
import android.view.Window;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ActivityActionModeTest {

    private ActionMode.Callback mCallback;

    @Rule
    public ActivityTestRule<MockActivity> mActivityRule =
            new ActivityTestRule<>(MockActivity.class);

    @Before
    public void setUp() {
        mCallback = mock(ActionMode.Callback.class);
        when(mCallback.onCreateActionMode(any(), any())).thenReturn(true);
        when(mCallback.onPrepareActionMode(any(), any())).thenReturn(true);
    }

    @Test
    @UiThreadTest
    public void testStartPrimaryActionMode() {
        if (!mActivityRule.getActivity().getWindow().hasFeature(Window.FEATURE_ACTION_BAR)) {
            return;
        }

        final ActionMode mode = mActivityRule.getActivity().startActionMode(
                mCallback, ActionMode.TYPE_PRIMARY);

        assertNotNull(mode);
        assertEquals(ActionMode.TYPE_PRIMARY, mode.getType());
    }

    @Test
    @UiThreadTest
    public void testStartFloatingActionMode() {
        if (!mActivityRule.getActivity().getWindow().hasFeature(Window.FEATURE_ACTION_BAR)) {
            return;
        }

        final ActionMode mode = mActivityRule.getActivity().startActionMode(
                mCallback, ActionMode.TYPE_FLOATING);

        assertNotNull(mode);
        assertEquals(ActionMode.TYPE_FLOATING, mode.getType());
    }

    @Test
    @UiThreadTest
    public void testStartTypelessActionMode() {
        if (!mActivityRule.getActivity().getWindow().hasFeature(Window.FEATURE_ACTION_BAR)) {
            return;
        }

        final ActionMode mode = mActivityRule.getActivity().startActionMode(mCallback);

        assertNotNull(mode);
        assertEquals(ActionMode.TYPE_PRIMARY, mode.getType());
    }
}
