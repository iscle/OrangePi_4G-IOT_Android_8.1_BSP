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
import android.view.ViewGroup;

import com.android.car.view.PagedListView;

import java.util.ArrayList;
import java.util.List;

/**
 * An adapter that binds each of the rows within the {@link LensResolverActivity}.
 */
public class ResolverAdapter extends RecyclerView.Adapter<ResolverListRow>
        implements PagedListView.ItemCap {
    private static final String TAG = "ResolverAdapter";

    private final Context mContext;
    private final List<ResolveInfo> mResolveInfos;
    private final List<LensPickerItem> mItems = new ArrayList<>();
    private ResolverListRow.ResolverSelectionHandler mHandler;

    public ResolverAdapter(Context context, List<ResolveInfo> resolveInfos) {
        mContext = context;
        mResolveInfos = resolveInfos;

        new LoadTask().execute(null, null, null);
    }

    public void setSelectionHandler(ResolverListRow.ResolverSelectionHandler handler) {
        mHandler = handler;
    }

    @Override
    public ResolverListRow onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return new ResolverListRow(
                inflater.inflate(R.layout.car_list_item_1_card, parent, false));
    }

    @Override
    public void onBindViewHolder(ResolverListRow holder, int position) {
        holder.bind(mContext, mResolveInfos.get(position), mItems.get(position), mHandler);
    }

    @Override
    public int getItemCount() {
        return mItems == null ? 0 : mItems.size();
    }

    @Override
    public void setMaxItems(int maxItems) {
        // No-op, but method override is needed for PagedListView.
    }

    /**
     * An {@link AsyncTask} that will construct the corresponding launch Intents for each of the
     * activities represented by {@link #mResolveInfos}. Upon completion, that information is
     * packaged into a {@link LensPickerItem} and stored within {@link #mItems}.
     */
    private class LoadTask extends AsyncTask<Void, Void, List<LensPickerItem>> {
        @Override
        protected List<LensPickerItem> doInBackground(Void... unused) {
            List<LensPickerItem> items = new ArrayList<>();

            PackageManager packageManager = mContext.getPackageManager();
            SharedPreferences sharedPref = LensPickerUtils.getFacetSharedPrefs(mContext);

            for (ResolveInfo info : mResolveInfos) {
                String packageName = LensPickerUtils.getPackageName(info);
                Intent launchIntent = LensPickerUtils.getLaunchIntent(packageName, info,
                        packageManager);

                try {
                    ApplicationInfo aInfo = packageManager.getApplicationInfo(packageName, 0);
                    String displayName = "";
                    if (LensPickerUtils.isMediaService(info)) {
                        // For media services we take the service tag instead of the package name.
                        // This is done to avoid Bluetooth showing Bluetooth Share as the package
                        // name.
                        displayName = info.loadLabel(packageManager).toString();
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Media service label set to: " + displayName);
                        }
                    }

                    // If we found an empty label for above case or if we did not hit the above if
                    // block then simply set this string to package name.
                    if (displayName.equals("")) {
                        displayName = getComponentLabel(packageManager, aInfo);
                    }

                    items.add(new LensPickerItem(displayName,
                            getComponentIcon(packageManager, aInfo), launchIntent,
                            null /* facetId */));
                } catch (PackageManager.NameNotFoundException e) {
                    // skip this package.
                }
            }

            return items;
        }

        @Override
        protected void onPostExecute(List<LensPickerItem> items) {
            mItems.addAll(items);
            notifyDataSetChanged();
        }

        /**
         * Returns the icon for the application represented by the given parameters.
         */
        private Drawable getComponentIcon(PackageManager packageManager, ApplicationInfo info) {
            return packageManager.getApplicationIcon(info);
        }

        /**
         * Returns the name of the application represented by the given parameters.
         */
        private String getComponentLabel(PackageManager packageManager, ApplicationInfo info) {
            CharSequence appLabel = packageManager.getApplicationLabel(info);

            if (appLabel == null) {
                return null;
            }

            return appLabel.toString();
        }
    }


}
