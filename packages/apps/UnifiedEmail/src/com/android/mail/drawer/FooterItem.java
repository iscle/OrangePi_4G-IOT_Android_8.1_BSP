/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.mail.drawer;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.ui.ControllableActivity;
import com.android.mail.ui.DrawerController;
import com.android.mail.ui.FolderListFragment;

/**
 * The base class of all footer items. Subclasses must fill in the logic of
 * {@link #onFooterClicked()} which contains the behavior when the item is selected.
 */
public abstract class FooterItem extends DrawerItem implements View.OnClickListener {

    private final FolderListFragment.DrawerStateListener mDrawerListener;
    private final int mImageResourceId;
    private final int mTextResourceId;

    FooterItem(ControllableActivity activity, Account account,
            FolderListFragment.DrawerStateListener drawerListener,
            final int imageResourceId, final int textResourceId) {
        super(activity, null, NONFOLDER_ITEM, account);
        mDrawerListener = drawerListener;
        mImageResourceId = imageResourceId;
        mTextResourceId = textResourceId;
    }

    private int getImageResourceId() {
        return mImageResourceId;
    }

    private int getTextResourceId() {
        return mTextResourceId;
    }

    /**
     * Executes the behavior associated with this footer item.<br>
     * <br>
     * WARNING: you probably don't want to call this directly; use {@link #onClick(View)} instead.
     * This method actually performs the action, and its execution may be deferred from when the
     * 'click' happens so we can smoothly close the drawer beforehand.
     */
    public abstract void onFooterClicked();

    @Override
    public final void onClick(View v) {
        final DrawerController dc = mActivity.getDrawerController();
        if (dc.isDrawerEnabled()) {
            // close the drawer and defer handling the click until onDrawerClosed
            mActivity.getAccountController().closeDrawer(false /* hasNewFolderOrAccount */,
                    null /* nextAccount */, null /* nextFolder */);
            mDrawerListener.setPendingFooterClick(this);
        } else {
            onFooterClicked();
        }
    }

    /**
     * For analytics
     * @return label for analytics event
     */
    protected String getEventLabel() {
        return "drawer_footer/" + mActivity.getViewMode().getModeString();
    }

    @Override
    public View getView(View convertView, ViewGroup parent) {
        final ViewGroup footerItemView;
        if (convertView != null) {
            footerItemView = (ViewGroup) convertView;
        } else {
            footerItemView =
                    (ViewGroup) mInflater.inflate(R.layout.drawer_footer_item, parent, false);
        }

        // adjust the text of the footer item
        final TextView textView = (TextView) footerItemView.
                findViewById(R.id.drawer_footer_text);
        textView.setText(getTextResourceId());

        // adjust the icon of the footer item
        final ImageView imageView = (ImageView) footerItemView.
                findViewById(R.id.drawer_footer_image);
        imageView.setImageResource(getImageResourceId());
        return footerItemView;
    }
}
