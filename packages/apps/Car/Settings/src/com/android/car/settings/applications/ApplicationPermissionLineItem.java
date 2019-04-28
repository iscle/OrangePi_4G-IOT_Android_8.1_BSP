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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.icu.text.ListFormatter;
import android.text.TextUtils;
import android.util.Log;

import com.android.car.settings.R;
import com.android.car.settings.common.AnimationUtil;
import com.android.car.settings.common.TextLineItem;
import com.android.settingslib.Utils;
import com.android.settingslib.applications.PermissionsSummaryHelper;
import com.android.settingslib.applications.PermissionsSummaryHelper.PermissionsResultCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows details about an application and action associated with that application,
 * like uninstall, forceStop.
 */
public class ApplicationPermissionLineItem extends TextLineItem {
    private static final String TAG = "AppPermissionLineItem";

    private final ResolveInfo mResolveInfo;
    private final Context mContext;
    private TextLineItem.ViewHolder mViewHolder;
    private CharSequence mSummary;

    public ApplicationPermissionLineItem(Context context, ResolveInfo resolveInfo) {
        super(context.getText(R.string.permissions_label));
        mResolveInfo = resolveInfo;
        mContext = context;

        PermissionsSummaryHelper.getPermissionSummary(mContext,
                mResolveInfo.activityInfo.packageName, mPermissionCallback);
    }

    @Override
    public void bindViewHolder(TextLineItem.ViewHolder viewHolder) {
        mViewHolder = viewHolder;
        viewHolder.titleView.setText(mTitle);
        if (TextUtils.isEmpty(mSummary)) {
            viewHolder.itemView.setOnClickListener(null);
            viewHolder.descView.setText(R.string.computing_size);
            viewHolder.itemView.setEnabled(false);
        } else {
            viewHolder.itemView.setOnClickListener(mOnClickListener);
            viewHolder.descView.setText(mSummary);
            viewHolder.itemView.setEnabled(true);
        }
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
    public void onClick() {
        // start new activity to manage app permissions
        Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS);
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME, mResolveInfo.activityInfo.packageName);
        try {
            mContext.startActivity(
                    intent, AnimationUtil.slideInFromRightOption(mContext).toBundle());
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "No app can handle android.intent.action.MANAGE_APP_PERMISSIONS");
        }
    }

    private final PermissionsResultCallback mPermissionCallback = new PermissionsResultCallback() {
        @Override
        public void onPermissionSummaryResult(int standardGrantedPermissionCount,
                int requestedPermissionCount, int additionalGrantedPermissionCount,
                List<CharSequence> grantedGroupLabels) {
            Resources res = mContext.getResources();

            if (requestedPermissionCount == 0) {
                mSummary = res.getString(
                        R.string.runtime_permissions_summary_no_permissions_requested);
            } else {
                ArrayList<CharSequence> list = new ArrayList<>(grantedGroupLabels);
                if (additionalGrantedPermissionCount > 0) {
                    // N additional permissions.
                    list.add(res.getQuantityString(
                            R.plurals.runtime_permissions_additional_count,
                            additionalGrantedPermissionCount, additionalGrantedPermissionCount));
                }
                if (list.size() == 0) {
                    mSummary = res.getString(
                            R.string.runtime_permissions_summary_no_permissions_granted);
                } else {
                    mSummary = ListFormatter.getInstance().format(list);
                }
            }
            if (mViewHolder != null) {
                bindViewHolder(mViewHolder);
            }
        }
    };
}
