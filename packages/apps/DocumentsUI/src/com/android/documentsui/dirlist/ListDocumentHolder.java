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

import android.annotation.Nullable;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Rect;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Events.InputEvent;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.Shared;
import com.android.documentsui.roots.RootCursorWrapper;

final class ListDocumentHolder extends DocumentHolder {

    private final TextView mTitle;
    private final @Nullable LinearLayout mDetails;  // Container of date/size/summary
    private final TextView mDate;
    private final TextView mSize;
    private final TextView mType;
    private final TextView mSummary;
    private final ImageView mIconMime;
    private final ImageView mIconThumb;
    private final ImageView mIconCheck;
    private final View mIconLayout;

    private final IconHelper mIconHelper;
    private final Lookup<String, String> mFileTypeLookup;
    // This is used in as a convenience in our bind method.
    private final DocumentInfo mDoc;

    public ListDocumentHolder(Context context, ViewGroup parent, IconHelper iconHelper,
            Lookup<String, String> fileTypeLookup) {
        super(context, parent, R.layout.item_doc_list);

        mIconLayout = itemView.findViewById(android.R.id.icon);
        mIconMime = (ImageView) itemView.findViewById(R.id.icon_mime);
        mIconThumb = (ImageView) itemView.findViewById(R.id.icon_thumb);
        mIconCheck = (ImageView) itemView.findViewById(R.id.icon_check);
        mTitle = (TextView) itemView.findViewById(android.R.id.title);
        mSummary = (TextView) itemView.findViewById(android.R.id.summary);
        mSize = (TextView) itemView.findViewById(R.id.size);
        mDate = (TextView) itemView.findViewById(R.id.date);
        mType = (TextView) itemView.findViewById(R.id.file_type);
        // Warning: mDetails view doesn't exists in layout-sw720dp-land layout
        mDetails = (LinearLayout) itemView.findViewById(R.id.line2);

        mIconHelper = iconHelper;
        mFileTypeLookup = fileTypeLookup;
        mDoc = new DocumentInfo();
    }

    @Override
    public void setSelected(boolean selected, boolean animate) {
        // We always want to make sure our check box disappears if we're not selected,
        // even if the item is disabled. But it should be an error (see assert below)
        // to be set to selected && be disabled.
        float checkAlpha = selected ? 1f : 0f;
        if (animate) {
            fade(mIconCheck, checkAlpha).start();
        } else {
            mIconCheck.setAlpha(checkAlpha);
        }

        if (!itemView.isEnabled()) {
            assert(!selected);
            return;
        }

        super.setSelected(selected, animate);

        if (animate) {
            fade(mIconMime, 1f - checkAlpha).start();
            fade(mIconThumb, 1f - checkAlpha).start();
        } else {
            mIconMime.setAlpha(1f - checkAlpha);
            mIconThumb.setAlpha(1f - checkAlpha);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        // Text colors enabled/disabled is handle via a color set.
        final float imgAlpha = enabled ? 1f : DISABLED_ALPHA;
        mIconMime.setAlpha(imgAlpha);
        mIconThumb.setAlpha(imgAlpha);
    }

    @Override
    public boolean isInDragHotspot(InputEvent event) {
        // If itemView is activated = selected, then whole region is interactive
        if (itemView.isActivated()) {
            return true;
        }

        // Do everything in global coordinates - it makes things simpler.
        int[] coords = new int[2];
        mIconLayout.getLocationOnScreen(coords);

        Rect textBounds = new Rect();
        mTitle.getPaint().getTextBounds(
                mTitle.getText().toString(), 0, mTitle.getText().length(), textBounds);

        Rect rect = new Rect(
                coords[0],
                coords[1],
                coords[0] + mIconLayout.getWidth() + textBounds.width(),
                coords[1] + Math.max(mIconLayout.getHeight(), textBounds.height()));

        // If the tap occurred inside icon or the text, these are interactive spots.
        return rect.contains((int) event.getRawX(), (int) event.getRawY());
    }

    @Override
    public boolean isOverDocIcon(InputEvent event) {
        Rect iconRect = new Rect();
        mIconLayout.getGlobalVisibleRect(iconRect);

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

        mModelId = modelId;

        mDoc.updateFromCursor(cursor, getCursorString(cursor, RootCursorWrapper.COLUMN_AUTHORITY));

        mIconHelper.stopLoading(mIconThumb);

        mIconMime.animate().cancel();
        mIconMime.setAlpha(1f);
        mIconThumb.animate().cancel();
        mIconThumb.setAlpha(0f);

        mIconHelper.load(mDoc, mIconThumb, mIconMime, null);

        mTitle.setText(mDoc.displayName, TextView.BufferType.SPANNABLE);
        mTitle.setVisibility(View.VISIBLE);


        boolean hasDetails = false;
        if (mDoc.isDirectory()) {
            // Note, we don't show any details for any directory...ever.
            hasDetails = false;
        } else {
            if (mDoc.summary != null) {
                hasDetails = true;
                mSummary.setText(mDoc.summary);
                mSummary.setVisibility(View.VISIBLE);
            } else {
                mSummary.setVisibility(View.INVISIBLE);
            }

            if (mDoc.lastModified > 0) {
                hasDetails = true;
                mDate.setText(Shared.formatTime(mContext, mDoc.lastModified));
            } else {
                mDate.setText(null);
            }

            if (mDoc.size > -1) {
                hasDetails = true;
                mSize.setVisibility(View.VISIBLE);
                mSize.setText(Formatter.formatFileSize(mContext, mDoc.size));
            } else {
                mSize.setVisibility(View.INVISIBLE);
            }

            mType.setText(mFileTypeLookup.lookup(mDoc.mimeType));
        }

        // mDetails view doesn't exists in layout-sw720dp-land layout
        if (mDetails != null) {
            mDetails.setVisibility(hasDetails ? View.VISIBLE : View.GONE);
        }

        // TODO: Add document debug info
        // Call includeDebugInfo
    }
}
