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
package com.android.support.car.lenspicker;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.car.view.PagedListView;

import java.util.ArrayList;
import java.util.List;

/**
 * Lens picker adapter that provides the data fed into lens picker list.
 *
 */
public class LensPickerAdapter extends RecyclerView.Adapter<LensPickerRow>
        implements PagedListView.ItemCap{
    private static final String TAG = "LensPickerAdapter";

    private final Context mContext;
    private final LensPickerSelectionHandler mSelectionHandler;
    private final List<LensPickerItem> mItems = new ArrayList<>();

    private LoadTask mLoader;
    private PackageManager mPackageManager;
    private SharedPreferences mSharedPrefs;
    private List<ResolveInfo> mResolveInfos;
    private String mFacetId;

    public LensPickerAdapter(Context context, List<ResolveInfo> rInfo, String facetId,
            LensPickerSelectionHandler selectionHandler) {
        mContext = context;
        mSelectionHandler = selectionHandler;
        mPackageManager = context.getPackageManager();
        mSharedPrefs = LensPickerUtils.getFacetSharedPrefs(context);
        mResolveInfos = rInfo;
        mFacetId = facetId;
        mLoader = new LoadTask();
        mLoader.execute(null, null, null);
    }

    @Override
    public LensPickerRow onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.car_list_item_1_card, parent, false);
        return new LensPickerRow(view);
    }

    @Override
    public void onBindViewHolder(LensPickerRow holder, int position) {
        holder.bind(mContext, mItems.get(position), mSelectionHandler);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public void setMaxItems(int maxItems) {
        // Ignore maxItems
    }

    private class LoadTask extends AsyncTask<Void, Void, Void> {
        private List<LensPickerItem> mLoadedItems = new ArrayList<LensPickerItem>();

        @Override
        protected Void doInBackground(Void... unused) {
            for (int i = 0; i < mResolveInfos.size(); i++) {
                ResolveInfo rInfo = mResolveInfos.get(i);
                String packageName = LensPickerUtils.getPackageName(rInfo);
                Intent launchIntent = LensPickerUtils.getLaunchIntent(packageName, rInfo,
                        mPackageManager);
                if (launchIntent == null) {
                    Log.w(TAG, "No launch intent for package " + packageName + " skipping.");
                    continue;
                }

                try {
                    ApplicationInfo aInfo = mPackageManager.getApplicationInfo(packageName, 0);
                    String displayName = "";
                    if (LensPickerUtils.isMediaService(rInfo)) {
                        // For media services we take the service tag instead of the package name.
                        // This is done to avoid Bluetooth showing Bluetooth Share as the package
                        // name.
                        displayName = rInfo.loadLabel(mPackageManager).toString();
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Media service label set to: " + displayName);
                        }
                    }

                    // If we found an empty label for above case or if we did not hit the above if
                    // block then simply set this string to package name.
                    if (displayName.equals("")) {
                        displayName = getComponentLabel(aInfo);
                    }
                    mLoadedItems.add(
                            new LensPickerItem(
                                    displayName,
                                    getComponentIcon(aInfo),
                                    launchIntent,
                                    mFacetId));
                } catch (PackageManager.NameNotFoundException e) {
                    // skip this package.
                }

                if (i % 2 == 0) {
                    publishProgress();
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... unused) {
            appendAndNotify();
        }

        @Override
        protected void onPostExecute(Void unused) {
            appendAndNotify();
        }

        private void appendAndNotify() {
            int oldSize = mItems.size();
            for (int i = oldSize; i < mLoadedItems.size(); i++) {
                mItems.add(mLoadedItems.get(i));
            }
            notifyItemRangeInserted(oldSize, mItems.size());
        }

        private Drawable getComponentIcon(ApplicationInfo aInfo) {
            return mPackageManager.getApplicationIcon(aInfo);
        }

        private String getComponentLabel(ApplicationInfo aInfo) {
            CharSequence appLabel = mPackageManager.getApplicationLabel(aInfo);
            if (appLabel != null) {
                return appLabel.toString();
            }
            return null;
        }
    };
}
