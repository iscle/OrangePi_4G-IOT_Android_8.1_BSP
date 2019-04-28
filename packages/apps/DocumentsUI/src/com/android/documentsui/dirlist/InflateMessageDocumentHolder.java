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
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.documentsui.R;

/**
 * RecyclerView.ViewHolder class that displays a message when there are no contents
 * in the directory, whether due to no items, no search results or an error.
 * Used by {@link DirectoryAddonsAdapter}.
 */
final class InflateMessageDocumentHolder extends MessageHolder {
    private Message mMessage;
    private TextView mMsgView;
    private ImageView mImageView;

    public InflateMessageDocumentHolder(Context context, ViewGroup parent) {
        super(context, parent, R.layout.item_doc_inflated_message);
        mMsgView = (TextView) itemView.findViewById(R.id.message);
        mImageView = (ImageView) itemView.findViewById(R.id.artwork);
    }

    public void bind(Message message) {
        mMessage = message;
        bind(null, null);
    }

    @Override
    public void bind(Cursor cursor, String modelId) {
        mMsgView.setText(mMessage.getMessageString());
        mImageView.setImageDrawable(mMessage.getIcon());
    }
}
