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

package android.widget.cts;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.ListPopupWindow;

public class MockViewForListPopupWindow extends EditText {
    private ListPopupWindow mListPopupWindow;

    public MockViewForListPopupWindow(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MockViewForListPopupWindow(Context context) {
        super(context);
    }

    public void wireTo(ListPopupWindow listPopupWindow) {
        mListPopupWindow = listPopupWindow;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (mListPopupWindow != null) {
            return mListPopupWindow.onKeyPreIme(keyCode, event);
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mListPopupWindow != null) {
            return mListPopupWindow.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mListPopupWindow != null) {
            return mListPopupWindow.onKeyUp(keyCode, event);
        }
        return super.onKeyUp(keyCode, event);
    }
}

