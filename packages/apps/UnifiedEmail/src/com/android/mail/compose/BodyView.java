/**
 * Copyright (c) 2014, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail.compose;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

public class BodyView extends EditText {

    public interface SelectionChangedListener {
        /**
         * @param start new selection start
         * @param end new selection end
         * @return true to suppress normal selection change processing
         */
        boolean onSelectionChanged(int start, int end);
    }

    private SelectionChangedListener mSelectionListener;

    public BodyView(Context c) {
        this(c, null);
    }

    public BodyView(Context c, AttributeSet attrs) {
        super(c, attrs);
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        if (mSelectionListener != null) {
            if (mSelectionListener.onSelectionChanged(selStart, selEnd)) {
                return;
            }
        }
        super.onSelectionChanged(selStart, selEnd);
    }

    public void setSelectionChangedListener(SelectionChangedListener l) {
        mSelectionListener = l;
    }

}
