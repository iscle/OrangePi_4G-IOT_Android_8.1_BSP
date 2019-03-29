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

package android.graphics.drawable.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class Drawable_ConstantStateTest {
    @Test
    public void testNewDrawable() {
        Context context = InstrumentationRegistry.getTargetContext();
        Resources resources = context.getResources();

        MockConstantState mock = spy(new MockConstantState());
        ConstantState cs = mock;

        assertEquals(null, cs.newDrawable());
        verify(mock, times(1)).newDrawable();
        reset(mock);

        assertEquals(null, cs.newDrawable(resources));
        verify(mock, times(1)).newDrawable();
        reset(mock);

        assertEquals(null, cs.newDrawable(resources, context.getTheme()));
        verify(mock, times(1)).newDrawable();
        reset(mock);
    }

    @Test
    public void testCanApplyTheme() {
        ConstantState cs = new MockConstantState();
        assertFalse(cs.canApplyTheme());
    }

    public static class MockConstantState extends ConstantState {
        @Override
        public Drawable newDrawable() {
            return null;
        }

        @Override
        public int getChangingConfigurations() {
            return 0;
        }
    }
}
