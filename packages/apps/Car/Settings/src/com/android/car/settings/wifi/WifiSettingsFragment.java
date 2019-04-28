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
 * limitations under the License.
 */
package com.android.car.settings.wifi;

import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import android.annotation.StringRes;

import com.android.car.settings.common.BaseFragment;
import com.android.car.settings.R;
import com.android.car.view.PagedListView;

import com.android.settingslib.wifi.AccessPoint;


/**
 * Main page to host Wifi related preferences.
 */
public class WifiSettingsFragment extends BaseFragment implements CarWifiManager.Listener {
    private static final String TAG = "WifiSettingsFragment";

    private CarWifiManager mCarWifiManager;
    private AccessPointListAdapter mAdapter;
    private Switch mWifiSwitch;
    private ProgressBar mProgressBar;
    private PagedListView mListView;
    private TextView mMessageView;
    private ViewSwitcher mViewSwitcher;

    public static WifiSettingsFragment getInstance() {
        WifiSettingsFragment wifiSettingsFragment = new WifiSettingsFragment();
        Bundle bundle = BaseFragment.getBundle();
        bundle.putInt(EXTRA_TITLE_ID, R.string.wifi_settings);
        bundle.putInt(EXTRA_LAYOUT, R.layout.wifi_list);
        bundle.putInt(EXTRA_ACTION_BAR_LAYOUT, R.layout.action_bar_with_toggle);
        wifiSettingsFragment.setArguments(bundle);
        return wifiSettingsFragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mCarWifiManager = new CarWifiManager(getContext(), this /* listener */);

        mProgressBar = (ProgressBar) getView().findViewById(R.id.wifi_search_progress);
        mListView = (PagedListView) getView().findViewById(R.id.list);
        mMessageView = (TextView) getView().findViewById(R.id.message);
        mViewSwitcher = (ViewSwitcher) getView().findViewById(R.id.view_switcher);
        setupWifiSwitch();
        if (mCarWifiManager.isWifiEnabled()) {
            showList();
        } else {
            showMessage(R.string.wifi_disabled);
        }
        // Set this to light mode, since the scroll bar buttons always appear
        // on top of a dark scrim.
        mListView.setDarkMode();
        mAdapter = new AccessPointListAdapter(
                getContext(),
                mCarWifiManager,
                mCarWifiManager.getAccessPoints(),
                mFragmentController);
        mListView.setAdapter(mAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        mCarWifiManager.start();
    }

    @Override
    public void onStop() {
        super.onStop();
        mCarWifiManager.stop();
    }

    @Override
    public void onAccessPointsChanged() {
        refreshData();
    }

    @Override
    public void onWifiStateChanged(int state) {
        mWifiSwitch.setChecked(mCarWifiManager.isWifiEnabled());
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING:
                showList();
                setProgressBarVisible(true);
                break;
            case WifiManager.WIFI_STATE_DISABLED:
                setProgressBarVisible(false);
                showMessage(R.string.wifi_disabled);
                break;
            default:
                showList();
        }
    }

    private  void setProgressBarVisible(boolean visible) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshData() {
        if (mAdapter != null) {
            mAdapter.updateAccessPoints(mCarWifiManager.getAccessPoints());
            // if the list is empty, keep showing the progress bar, the list should reset
            // every couple seconds.
            // TODO: Consider show a message in the list view place.
            if (!mAdapter.isEmpty()) {
                setProgressBarVisible(false);
            }
        }
        mWifiSwitch.setChecked(mCarWifiManager.isWifiEnabled());
    }

    private void showMessage(@StringRes int resId) {
        if (mViewSwitcher.getCurrentView() != mMessageView) {
            mViewSwitcher.showNext();
        }
        mMessageView.setText(getResources().getString(resId));
    }

    private void showList() {
        if (mViewSwitcher.getCurrentView() != mListView) {
            mViewSwitcher.showPrevious();
        }
    }

    private void setupWifiSwitch() {
        mWifiSwitch = (Switch) getActivity().findViewById(R.id.toggle_switch);
        mWifiSwitch.setChecked(mCarWifiManager.isWifiEnabled());
        mWifiSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mWifiSwitch.isChecked() != mCarWifiManager.isWifiEnabled()) {
                mCarWifiManager.setWifiEnabled(mWifiSwitch.isChecked());
            }
        });
    }
}
