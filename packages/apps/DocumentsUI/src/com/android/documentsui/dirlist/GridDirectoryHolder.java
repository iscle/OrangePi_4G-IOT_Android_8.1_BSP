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

package com.android.documentsui.dirlist;

import static com.android.documentsui.base.DocumentInfo.getCursorString;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Rect;
import android.provider.DocumentsContract.Document;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.documentsui.R;
import com.android.documentsui.base.DebugFlags;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Events.InputEvent;
import com.android.documentsui.roots.RootCursorWrapper;

final class GridDirectoryHolder extends DocumentHolder {

    final TextView mTitle;

    private final ImageView mIconCheck;
    private final ImageView mIconMime;

    public GridDirectoryHolder(Context context, ViewGroup parent) {
        super(context, parent, R.layout.item_dir_grid);

        mTitle = (TextView) itemView.findViewById(android.R.id.title);
        mIconMime = (ImageView) itemView.findViewById(R.id.icon_mime_sm);
        mIconCheck = (ImageView) itemView.findViewById(R.id.icon_check);
    }

    @Override
    public void setSelected(boolean selected, boolean animate) {
        super.setSelected(selected, animate);
        float checkAlpha = selected ? 1f : 0f;

        if (animate) {
            fade(mIconCheck, checkAlpha).start();
            fade(mIconMime, 1f - checkAlpha).start();
        } else {
            mIconCheck.setAlpha(checkAlpha);
            mIconMime.setAlpha(1f - checkAlpha);
        }
    }

    @Override
    public boolean isInDragHotspot(InputEvent event) {
        // Entire grid box should be draggable
        return true;
    }

    @Override
    public boolean isOverDocIcon(InputEvent event) {
        Rect iconRect = new Rect();
        mIconMime.getGlobalVisibleRect(iconRect);

        return iconRect.contains((int) event.getRawX(), (int) event.getRawY());
    }

    /**
     * Bind this view to the given document for display.
     * @param cursor Pointing to the item to be bound.
     * @param modelId The model ID of the item.
     */
    @Override
    public void bind(Cursor cursor, String modelId) {
        assert(cursor != null);

        this.mModelId = modelId;

        mTitle.setText(
                getCursorString(cursor, Document.COLUMN_DISPLAY_NAME),
                TextView.BufferType.SPANNABLE);
    }
}
