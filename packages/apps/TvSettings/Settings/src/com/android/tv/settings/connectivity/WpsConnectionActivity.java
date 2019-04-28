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

package com.android.tv.settings.connectivity;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WpsCallback;
import android.net.wifi.WpsInfo;
import android.os.Bundle;

import com.android.tv.settings.R;
import com.android.tv.settings.connectivity.setup.SelectFromListWizardFragment;
import com.android.tv.settings.dialog.old.DialogActivity;
import com.android.tv.settings.util.ThemeHelper;

import java.util.ArrayList;

public class WpsConnectionActivity extends DialogActivity
        implements SelectFromListWizardFragment.Listener, TimedMessageWizardFragment.Listener {

    private static final String WPS_FRAGMENT_TAG = "wps_fragment_tag";

    private WifiManager mWifiManager;
    private boolean mWpsComplete;
    private boolean mActive;

    private final WpsCallback mWpsCallback = new WpsCallback() {
        @Override
        public void onStarted(String pin) {
            if (pin != null && mActive) {
                displayFragment(createEnterPinFragment(pin), true);
            }
        }

        @Override
        public void onSucceeded() {
            mWpsComplete = true;

            if (!mActive) {
                return;
            }

            displayFragment(createSuccessFragment(), true);
        }

        @Override
        public void onFailed(int reason) {
            mWpsComplete = true;

            if (!mActive) {
                return;
            }

            String errorMessage;
            switch (reason) {
                case WifiManager.WPS_OVERLAP_ERROR:
                    errorMessage = getString(R.string.wifi_wps_failed_overlap);
                    break;
                case WifiManager.WPS_WEP_PROHIBITED:
                    errorMessage = getString(R.string.wifi_wps_failed_wep);
                    break;
                case WifiManager.WPS_TKIP_ONLY_PROHIBITED:
                    errorMessage = getString(R.string.wifi_wps_failed_tkip);
                    break;
                case WifiManager.IN_PROGRESS:
                    mWifiManager.cancelWps(null);
                    startWps();
                    return;
                case WifiManager.WPS_TIMED_OUT:
                    startWps();
                    return;
                default:
                    errorMessage = getString(R.string.wifi_wps_failed_generic);
                    break;
            }
            displayFragment(createErrorFragment(errorMessage), true);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeHelper.getThemeResource(getIntent()));
        setLayoutProperties(R.layout.setup_auth_activity, R.id.description, R.id.action);
        super.onCreate(savedInstanceState);
        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        overridePendingTransition(R.anim.wps_activity_open_in, R.anim.wps_activity_open_out);

        if (ThemeHelper.fromSetupWizard(getIntent())) {
            setTitle(getResources().getString(R.string.wifi_wps_title));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Must be set before all other actions.
        mActive = true;

        startWps();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mActive = false;

        if (!mWpsComplete) {
            mWifiManager.cancelWps(null);
        }
    }

    @Override
    public void onTimedMessageCompleted() {
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onListSelectionComplete(SelectFromListWizardFragment.ListItem listItem) {
        startWps();
    }

    @Override
    public void onListFocusChanged(SelectFromListWizardFragment.ListItem listItem) {
        // Do nothing.
    }

    private void startWps() {
        Fragment currentFragment = getFragmentManager().findFragmentByTag(WPS_FRAGMENT_TAG);
        if (!(currentFragment instanceof WpsScanningFragment)) {
            displayFragment(createWpsScanningFragment(), true);
        }
        mWpsComplete = false;
        WpsInfo wpsConfig = new WpsInfo();
        wpsConfig.setup = WpsInfo.PBC;
        mWifiManager.startWps(wpsConfig, mWpsCallback);
    }

    private Fragment createWpsScanningFragment() {
        return WpsScanningFragment.newInstance();
    }

    private Fragment createEnterPinFragment(String pin) {
        return WpsPinFragment.newInstance(pin);
    }

    private Fragment createErrorFragment(String errorMessage) {
        SelectFromListWizardFragment.ListItem retryListItem =
                new SelectFromListWizardFragment.ListItem(
                        getString(R.string.wifi_wps_retry_scan), 0);
        ArrayList<SelectFromListWizardFragment.ListItem> listItems = new ArrayList<>();
        listItems.add(retryListItem);
        return SelectFromListWizardFragment.newInstance(errorMessage, null, listItems,
                retryListItem);
    }

    private Fragment createSuccessFragment() {
        return TimedMessageWizardFragment.newInstance(
                getString(R.string.wifi_setup_connection_success));
    }

    private void displayFragment(Fragment fragment, boolean forward) {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        if (forward) {
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        } else {
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
        }
        transaction.replace(R.id.content, fragment, WPS_FRAGMENT_TAG).commit();
    }
}
