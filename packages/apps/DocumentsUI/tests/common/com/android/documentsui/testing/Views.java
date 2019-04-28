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

package com.android.documentsui.testing;

import android.graphics.drawable.Drawable;
import android.view.View;

import org.mockito.Mockito;

public final class Views {

    private Views() {}

    public static View createTestView() {
        final View view = Mockito.mock(View.class);
        Mockito.doCallRealMethod().when(view).setTag(Mockito.anyInt(), Mockito.any());
        Mockito.doCallRealMethod().when(view).getTag(Mockito.anyInt());

        return view;
    }

    /*
     * Dummy View object with (x, y) coordinates
     */
    public static View createTestView(float x, float y) {
        View view = createTestView();
        Mockito.when(view.getX()).thenReturn(x);
        Mockito.when(view.getY()).thenReturn(y);

        return view;
    }

    public static View createTestView(boolean activated) {
        View view = createTestView();
        Mockito.when(view.isActivated()).thenReturn(activated);

        return view;
    }

    public static void setBackground(View testView, Drawable background) {
        Mockito.when(testView.getBackground()).thenReturn(background);
    }
}
