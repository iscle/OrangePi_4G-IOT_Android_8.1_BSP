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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ResolveInfo.DisplayNameComparator;
import android.os.Bundle;
import android.service.media.MediaBrowserService;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.android.car.view.PagedListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * An Activity to present the user with a list of applications that can be started for a given
 * facet.
 */
public class LensPickerActivity extends Activity implements LensPickerSelectionHandler {
    private static final String TAG = "LensPickerActivity";
    private PackageManager mPackageManager;
    private SharedPreferences mSharedPrefs;

    private String mLastLaunchedFacetId;
    private String mLastLaunchedPackageName;
    private Intent mLastLaunchedIntent;

    private PagedListView mPagedListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPackageManager = getPackageManager();
        mSharedPrefs = LensPickerUtils.getFacetSharedPrefs(this);

        setContentView(R.layout.lens_list);
        mPagedListView = (PagedListView) findViewById(R.id.list_view);
        // Set this to light mode, since the scroll bar buttons always appear
        // on top of a dark scrim.
        mPagedListView.setLightMode();

        findViewById(R.id.dismiss_area).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = getIntent();
        String[] categories = intent.getStringArrayExtra(
                LensPickerConstants.EXTRA_FACET_CATEGORIES);
        String[] packages = intent.getStringArrayExtra(LensPickerConstants.EXTRA_FACET_PACKAGES);
        String facetId = intent.getStringExtra(LensPickerConstants.EXTRA_FACET_ID);

        List<ResolveInfo> resolveInfos = getComponents(packages, categories);

        if (resolveInfos != null && resolveInfos.size() == 1) {
            // Directly launch the package rather than showing a list of 1.
            ResolveInfo rInfo = resolveInfos.get(0);
            String packageName = LensPickerUtils.getPackageName(rInfo);
            Intent launchIntent = LensPickerUtils.getLaunchIntent(packageName, rInfo,
                    mPackageManager);
            if (launchIntent != null) {
                launch(facetId, packageName, launchIntent);
            } else {
                Log.e(TAG, "Failed to get launch intent for package" + packageName);
            }
            finish();
            return;
        }

        mPagedListView.setAdapter(new LensPickerAdapter(this, resolveInfos, facetId,
                this /* LensPickerSelectionHandler */));
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mLastLaunchedFacetId == null || mLastLaunchedPackageName == null
                || mLastLaunchedIntent == null) {
            return;
        }

        LensPickerUtils.saveLastLaunchedAppInfo(mSharedPrefs, mLastLaunchedFacetId,
                mLastLaunchedPackageName, mLastLaunchedIntent);
    }

    private boolean isReclick(Intent intent, Intent oldIntent) {
        String oldFacetId = oldIntent.getStringExtra(LensPickerConstants.EXTRA_FACET_ID);
        String newFacetId = intent.getStringExtra(LensPickerConstants.EXTRA_FACET_ID);
        return TextUtils.equals(oldFacetId, newFacetId);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Intent oldIntent = getIntent();
        setIntent(intent);
        if (isReclick(intent, oldIntent)) {
            finish();
        }
    }

    private ArrayList<ResolveInfo> getComponents(String[] packages, String[] categories) {
        List<ResolveInfo> packageList = new ArrayList<>();
        if (packages != null) {
            for (int i = 0; i < packages.length; i++) {
                packageList.addAll(resolvePackage(packages[i]));
            }
        }

        if (categories != null) {
            for (int i = 0; i < categories.length; i++) {
                packageList.addAll(resolveCategory(categories[i]));

                if (categories[i].equals(Intent.CATEGORY_APP_MUSIC)) {
                    packageList.addAll(resolveMediaBrowserServices());
                }
            }
        }

        // De-dupe the list based on package names
        HashMap<String, ResolveInfo> dedupeList = new HashMap<>();
        for (ResolveInfo pkg : packageList) {
            String packageName = LensPickerUtils.getPackageName(pkg);
            if (!dedupeList.containsKey(packageName) || LensPickerUtils.isMediaService(pkg)) {
                dedupeList.put(packageName, pkg);
            }
        }

        ArrayList<ResolveInfo> filteredPackageList = new ArrayList<>(dedupeList.values());
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            printResolveInfo("before dedupe", packageList);
            printResolveInfo("after dedupe", filteredPackageList);
        }

        // If presenting a category, alphabetize the list based on name.
        if (categories != null) {
            Collections.sort(filteredPackageList, new DisplayNameComparator(mPackageManager));
        }


        return filteredPackageList;
    }

    private List<ResolveInfo> resolvePackage(String packageName) {
        Intent intent = new Intent();
        intent.setPackage(packageName);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        return mPackageManager.queryIntentActivities(intent, 0);
    }

    private List<ResolveInfo> resolveCategory(String category) {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addCategory(category);
        return mPackageManager.queryIntentActivities(intent, 0);
    }

    private List<ResolveInfo> resolveMediaBrowserServices() {
        Intent intent = new Intent();
        intent.setAction(MediaBrowserService.SERVICE_INTERFACE);
        return mPackageManager.queryIntentServices(intent, PackageManager.GET_RESOLVED_FILTER);
    }

    private void printResolveInfo(String title, List<ResolveInfo> list) {
        String names = "";
        for (ResolveInfo info : list) {
            names += " " + LensPickerUtils.getPackageName(info);
        }
        Log.d(TAG, title + " resolve info name: " + names);
    }

    @Override
    public void onActivitySelected(LensPickerItem item) {
        Intent launchIntent = item.getLaunchIntent();
        launch(item.getFacetId(), launchIntent.getPackage(), launchIntent);
        finish();
    }

    private void launch(String facetId, String packageName, Intent launchIntent) {
        // Save the information for the application that is about to be launched.
        mLastLaunchedFacetId = facetId;
        mLastLaunchedPackageName = packageName;
        mLastLaunchedIntent = launchIntent;

        LensPickerUtils.launch(this /* context */, mSharedPrefs, facetId, packageName,
                launchIntent);
    }
}
