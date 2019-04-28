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
 * limitations under the License
 */

package com.android.car.settings.users;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.os.UserManager;
import android.widget.ImageView;

import com.android.car.settings.R;
import com.android.car.settings.common.BaseFragment;
import com.android.car.settings.common.IconTextLineItem;

/**
 * Represents a user in settings page.
 */
public class UserLineItem extends IconTextLineItem {
    private final Context mContext;
    private final UserInfo mUserInfo;
    private final UserManager mUserManager;
    private final BaseFragment.FragmentController mFragmentController;

    public UserLineItem(
            @NonNull Context context,
            UserInfo userInfo,
            UserManager userManager,
            BaseFragment.FragmentController fragmentController) {
        super(userInfo.name);
        mContext = context;
        mUserInfo = userInfo;
        mUserManager = userManager;
        mFragmentController = fragmentController;
    }

    @Override
    public void bindViewHolder(IconTextLineItem.ViewHolder viewHolder) {
        super.bindViewHolder(viewHolder);
        viewHolder.titleView.setText(isCurrentUser(mUserInfo)
                ? mContext.getString(R.string.current_user_name, mUserInfo.name) : mUserInfo.name);
    }

    @Override
    public void onClick() {
        mFragmentController.launchFragment(UserDetailsSettingsFragment.getInstance(mUserInfo));
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isExpandable() {
        return true;
    }

    @Override
    public CharSequence getDesc() {
        return null;
    }

    @Override
    public void setIcon(ImageView iconView) {
        Bitmap picture = mUserManager.getUserIcon(mUserInfo.id);

        if (picture != null) {
            int avatarSize = mContext.getResources()
                    .getDimensionPixelSize(R.dimen.stream_button_icon_size);
            picture = Bitmap.createScaledBitmap(
                    picture, avatarSize, avatarSize, true);
            iconView.setImageBitmap(picture);
        } else {
            iconView.setImageDrawable(mContext.getDrawable(R.drawable.ic_user));
        }
    }

    private boolean isCurrentUser(UserInfo userInfo) {
        return ActivityManager.getCurrentUser() == userInfo.id;
    }
}
