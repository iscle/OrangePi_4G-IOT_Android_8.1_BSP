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

import android.content.Context;
import android.graphics.Rect;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ActionModeCallback2Test {
    private static final int VIEW_WIDTH = 123;
    private static final int VIEW_HEIGHT = 456;

    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testCallbackOnGetContentRectDefaultWithView() {
        View view = new View(mContext);
        view.setLeft(0);
        view.setRight(VIEW_WIDTH);
        view.setTop(0);
        view.setBottom(VIEW_HEIGHT);

        Rect outRect = new Rect();
        ActionMode.Callback2 callback = new MockActionModeCallback2();
        callback.onGetContentRect(null, view, outRect);

        assertEquals(0, outRect.top);
        assertEquals(0, outRect.left);
        assertEquals(VIEW_HEIGHT, outRect.bottom);
        assertEquals(VIEW_WIDTH, outRect.right);
    }

    @Test
    public void testCallbackOnGetContentRectDefaultWithoutView() {
        Rect outRect = new Rect();
        ActionMode.Callback2 callback = new MockActionModeCallback2();
        callback.onGetContentRect(null, null, outRect);

        assertEquals(0, outRect.top);
        assertEquals(0, outRect.left);
        assertEquals(0, outRect.bottom);
        assertEquals(0, outRect.right);
    }

    private static class MockActionModeCallback2 extends ActionMode.Callback2 {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {}
    }

}
