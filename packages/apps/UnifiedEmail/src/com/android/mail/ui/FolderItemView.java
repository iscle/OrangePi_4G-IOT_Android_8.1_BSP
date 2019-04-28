/**
 * Copyright (c) 2012, Google Inc.
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
package com.android.mail.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.graphics.drawable.shapes.Shape;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.Folder;
import com.android.mail.utils.FolderUri;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

/**
 * The view for each folder in the folder list.
 */
public class FolderItemView extends LinearLayout {
    private final String LOG_TAG = LogTag.getLogTag();

    private static float[] sUnseenCornerRadii;

    private Folder mFolder;
    private TextView mFolderTextView;
    private TextView mUnreadCountTextView;
    private TextView mUnseenCountTextView;

    public FolderItemView(Context context) {
        super(context);

        loadResources(context);
    }

    public FolderItemView(Context context, AttributeSet attrs) {
        super(context, attrs);

        loadResources(context);
    }

    public FolderItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        loadResources(context);
    }

    private void loadResources(Context context) {
        if (sUnseenCornerRadii == null) {
            final float cornerRadius =
                    context.getResources().getDimension(R.dimen.folder_rounded_corner_radius);
            sUnseenCornerRadii = new float[] {
                    cornerRadius, cornerRadius, // top left
                    cornerRadius, cornerRadius, // top right
                    cornerRadius, cornerRadius, // bottom right
                    cornerRadius, cornerRadius  // bottom left
            };
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mFolderTextView = (TextView)findViewById(R.id.name);
        mUnreadCountTextView = (TextView)findViewById(R.id.unread);
        mUnseenCountTextView = (TextView)findViewById(R.id.unseen);
    }

    /**
     * Returns true if the two folders lead to identical {@link FolderItemView} objects.
     * @param a
     * @param b
     * @return true if the two folders would still lead to the same {@link FolderItemView}.
     */
    public static boolean areSameViews(final Folder a, final Folder b) {
        if (a == null) {
            return b == null;
        }
        if (b == null) {
            // a is not null because it would have returned above.
            return false;
        }
        return (a == b || (a.folderUri.equals(b.folderUri)
                && a.name.equals(b.name)
                && a.hasChildren == b.hasChildren
                && a.unseenCount == b.unseenCount
                && a.unreadCount == b.unreadCount));
    }

    public void bind(final Folder folder, final FolderUri parentUri) {
        mFolder = folder;

        mFolderTextView.setText(folder.name);

        if (parentUri != null) {
            final boolean isParent = folder.folderUri.equals(parentUri);

            // If child folder, make spacer view visible, otherwise hide it away
            findViewById(R.id.nested_folder_space).setVisibility(
                    isParent ? View.GONE : View.VISIBLE);
        }

        if (mFolder.isInbox() && mFolder.unseenCount > 0) {
            mUnreadCountTextView.setVisibility(View.GONE);
            setUnseenCount(mFolder.getBackgroundColor(Color.BLACK), mFolder.unseenCount);
        } else {
            mUnseenCountTextView.setVisibility(View.GONE);
            setUnreadCount(Utils.getFolderUnreadDisplayCount(mFolder));
        }
    }

    /**
     * Sets the icon, if any.
     */
    public void setIcon(final Folder folder) {
        final ImageView folderIconView = (ImageView) findViewById(R.id.folder_icon);
        Folder.setIcon(folder, folderIconView);
    }

    /**
     * Sets the unread count, taking care to hide/show the textview if the count is zero/non-zero.
     */
    private void setUnreadCount(int count) {
        mUnreadCountTextView.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        if (count > 0) {
            mUnreadCountTextView.setText(Utils.getUnreadCountString(getContext(), count));
        }
    }

    /**
     * Sets the unseen count, taking care to hide/show the textview if the count is zero/non-zero.
     */
    private void setUnseenCount(final int color, final int count) {
        mUnseenCountTextView.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        if (count > 0) {
            final Shape shape = new RoundRectShape(sUnseenCornerRadii, null, null);
            final ShapeDrawable drawable = new ShapeDrawable(shape);
            drawable.getPaint().setColor(color);
            mUnseenCountTextView.setBackgroundDrawable(drawable);
            mUnseenCountTextView.setText(Utils.getUnseenCountString(getContext(), count));
        }
    }

    /**
     * Used if we detect a problem with the unread count and want to force an override.
     * @param count
     */
    public final void overrideUnreadCount(int count) {
        LogUtils.e(LOG_TAG, "FLF->FolderItem.getFolderView: unread count mismatch found (%s vs %d)",
                mUnreadCountTextView.getText(), count);
        setUnreadCount(count);
    }

}
