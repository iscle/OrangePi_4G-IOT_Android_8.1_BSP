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

import com.android.bitmap.BitmapCache;
import com.android.mail.R;
import com.android.mail.bitmap.ContactResolver;
import com.android.mail.providers.Account;
import com.android.mail.ui.AccountItemView;
import com.android.mail.ui.ControllableActivity;
import com.android.mail.utils.FolderUri;

class AccountDrawerItem extends DrawerItem {
    /** True if the drawer item represents the current account, false otherwise */
    private final boolean mIsSelected;
    private final BitmapCache mImagesCache;
    private final ContactResolver mContactResolver;

    AccountDrawerItem(ControllableActivity activity, Account account,
            int unreadCount, boolean isCurrentAccount, BitmapCache cache,
            ContactResolver contactResolver) {
        super(activity, null, NONFOLDER_ITEM, account);
        mIsSelected = isCurrentAccount;
        mImagesCache = cache;
        mContactResolver = contactResolver;
        // TODO: Unread count should eventually percolate through to the account switcher
    }

    @Override
    public String toString() {
        return "[DrawerItem VIEW_ACCOUNT, mAccount=" + mAccount + "]";
    }

    /**
     * Return a view for an account object.
     *
     * @param convertView a view, possibly null, to be recycled.
     * @param parent the parent viewgroup to attach to.
     * @return a view to display at this position.
     */
    @Override
    public View getView(View convertView, ViewGroup parent) {
        final AccountItemView accountItemView;
        if (convertView != null) {
            accountItemView = (AccountItemView) convertView;
        } else {
            accountItemView =
                    (AccountItemView) mInflater.inflate(R.layout.account_item, parent, false);
        }
        accountItemView.bind(mActivity.getActivityContext(), mAccount, mIsSelected,
                mImagesCache, mContactResolver);
        return accountItemView;
    }

    @Override
    public boolean isHighlighted(FolderUri currentFolder, int currentType) {
        return false;
    }

    @Override
    public boolean isItemEnabled() {
        return true;
    }

    @Override
    public @DrawerItemType int getType() {
        return VIEW_ACCOUNT;
    }
}
