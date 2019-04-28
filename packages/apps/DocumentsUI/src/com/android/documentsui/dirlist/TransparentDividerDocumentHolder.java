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

package com.android.documentsui.dirlist;


import android.content.Context;
import android.database.Cursor;
import android.widget.Space;

import com.android.documentsui.R;
import com.android.documentsui.base.State;

/**
 * The most elegant transparent blank box that spans N rows ever conceived.
 * Used by {@link DirectoryAddonsAdapter}.
 */
final class TransparentDividerDocumentHolder extends MessageHolder {
    private final int mVisibleHeight;
    private State mState;

    public TransparentDividerDocumentHolder(Context context) {
        super(context, new Space(context));

        mVisibleHeight = context.getResources().getDimensionPixelSize(
                R.dimen.grid_section_separator_height);
    }

    public void bind(State state) {
        mState = state;
        bind(null, null);
    }

    @Override
    public void bind(Cursor cursor, String modelId) {
        if (mState.derivedMode == State.MODE_GRID) {
            itemView.setMinimumHeight(mVisibleHeight);
        } else {
            itemView.setMinimumHeight(0);
        }
        return;
    }
}