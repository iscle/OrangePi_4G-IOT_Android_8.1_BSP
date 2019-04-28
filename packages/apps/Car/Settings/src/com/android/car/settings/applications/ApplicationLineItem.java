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

package com.android.car.settings.applications;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PorterDuff;
import android.widget.ImageView;

import com.android.car.settings.common.BaseFragment;
import com.android.car.settings.common.IconTextLineItem;

/**
 * Represents an application in application settings page.
 */
public class ApplicationLineItem extends IconTextLineItem {
    private final ResolveInfo mResolveInfo;
    private final Context mContext;
    private final PackageManager mPm;
    private final boolean mClickable;
    private final BaseFragment.FragmentController mFragmentController;

    public ApplicationLineItem(
            @NonNull Context context,
            PackageManager pm,
            ResolveInfo resolveInfo,
            BaseFragment.FragmentController fragmentController) {
        this(context, pm, resolveInfo, fragmentController, true);
    }

    public ApplicationLineItem(
            @NonNull Context context,
            PackageManager pm,
            ResolveInfo resolveInfo,
            BaseFragment.FragmentController fragmentController,
            boolean clickable) {
        super(resolveInfo.loadLabel(pm));
        mContext = context;
        mPm = pm;
        mResolveInfo = resolveInfo;
        mFragmentController = fragmentController;
        mClickable = clickable;
    }

    @Override
    public void onClick() {
        if (mClickable) {
            mFragmentController.launchFragment(ApplicationDetailFragment.getInstance(mResolveInfo));
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isExpandable() {
        return mClickable;
    }

    @Override
    public CharSequence getDesc() {
        return null;
    }

    @Override
    public void setIcon(ImageView iconView) {
        iconView.setImageDrawable(mResolveInfo.loadIcon(mPm));
        iconView.setImageTintMode(PorterDuff.Mode.DST);
    }
}
