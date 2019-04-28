/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.documentsui.inspector;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.documentsui.ProviderExecutor;
import com.android.documentsui.ThumbnailLoader;
import com.android.documentsui.base.Display;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.R;
import java.util.function.Consumer;

/**
 * Organizes and displays the title and thumbnail for a given document
 */
public final class HeaderView extends RelativeLayout implements Consumer<DocumentInfo> {

    private static final String TAG = HeaderView.class.getCanonicalName();

    private final Context mContext;
    private final View mHeader;
    private ImageView mThumbnail;
    private final TextView mTitle;
    private Point mImageDimensions;

    public HeaderView(Context context) {
        this(context, null);
    }

    public HeaderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HeaderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        mContext = context;
        mHeader = inflater.inflate(R.layout.inspector_header, null);
        mThumbnail = (ImageView) mHeader.findViewById(R.id.inspector_thumbnail);
        mTitle = (TextView) mHeader.findViewById(R.id.inspector_file_title);

        int width = (int) Display.screenWidth((Activity)context);
        int height = mContext.getResources().getDimensionPixelSize(R.dimen.inspector_header_height);
        mImageDimensions = new Point(width, height);
    }

    @Override
    public void accept(DocumentInfo info) {
        if (!hasHeader()) {
            addView(mHeader);
        }

        if (!hasHeaderImage()) {
            if (info.isDirectory()) {
                loadFileIcon(info);
            } else {
                loadHeaderImage(info);
            }
        }
        mTitle.setText(info.displayName);
    }

    private boolean hasHeader() {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).equals(mHeader)) {
                return true;
            }
        }
        return false;
    }

    private void loadFileIcon(DocumentInfo info) {
        Drawable mimeIcon = mContext.getContentResolver()
            .getTypeDrawable(info.mimeType);
        mThumbnail.setScaleType(ScaleType.FIT_CENTER);
        mThumbnail.setImageDrawable(mimeIcon);
    }

    private void loadHeaderImage(DocumentInfo info) {

        Consumer<Bitmap> callback = new Consumer<Bitmap>() {
            @Override
            public void accept(Bitmap bitmap) {
                if (bitmap != null) {
                    mThumbnail.setScaleType(ScaleType.CENTER_CROP);
                    mThumbnail.setImageBitmap(bitmap);
                } else {
                    loadFileIcon(info);
                }
                mThumbnail.animate().alpha(1.0f).start();
            }
        };

        // load the thumbnail async.
        final ThumbnailLoader task = new ThumbnailLoader(info.derivedUri, mThumbnail,
            mImageDimensions, info.lastModified, callback, false);
        task.executeOnExecutor(ProviderExecutor.forAuthority(info.derivedUri.getAuthority()),
            info.derivedUri);
    }

    private boolean hasHeaderImage() {
        return mThumbnail.getAlpha() == 1.0f;
    }
}