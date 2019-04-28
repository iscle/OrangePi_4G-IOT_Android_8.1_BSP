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

package com.android.tv.ui.sidepanel;

import android.accounts.Account;
import android.app.Activity;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.BuildConfig;
import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.data.epg.EpgFetcher;
import com.android.tv.experiments.Experiments;
import com.android.tv.tuner.TunerPreferences;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Options for developers only
 */
public class DeveloperOptionFragment extends SideFragment {
    private static final String TAG = "DeveloperOptionFragment";
    private static final String TRACKER_LABEL = "debug options";

    @Override
    protected String getTitle() {
        return getString(R.string.menu_developer_options);
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    protected List<Item> getItemList() {
        List<Item> items = new ArrayList<>();
        if (CommonFeatures.DVR.isEnabled(getContext())) {
            items.add(new ActionItem(getString(R.string.dev_item_dvr_history)) {
                @Override
                protected void onSelected() {
                    getMainActivity().getOverlayManager().showDvrHistoryDialog();
                }
            });
        }
        if (Utils.isDeveloper()) {
            items.add(new ActionItem(getString(R.string.dev_item_watch_history)) {
                @Override
                protected void onSelected() {
                    getMainActivity().getOverlayManager().showRecentlyWatchedDialog();
                }
            });
        }
        items.add(new SwitchItem(getString(R.string.dev_item_store_ts_on),
                getString(R.string.dev_item_store_ts_off),
                getString(R.string.dev_item_store_ts_description)) {
            @Override
            protected void onUpdate() {
                super.onUpdate();
                setChecked(TunerPreferences.getStoreTsStream(getContext()));
            }

            @Override
            protected void onSelected() {
                super.onSelected();
                TunerPreferences.setStoreTsStream(getContext(), isChecked());
            }
        });
        if (Utils.isDeveloper()) {
            items.add(
                    new ActionItem(getString(R.string.dev_item_show_performance_monitor_log)) {
                        @Override
                        protected void onSelected() {
                            TvApplication.getSingletons(getContext())
                                    .getPerformanceMonitor()
                                    .startPerformanceMonitorEventDebugActivity(getContext());
                        }
                    });
        }
        return items;
    }

}