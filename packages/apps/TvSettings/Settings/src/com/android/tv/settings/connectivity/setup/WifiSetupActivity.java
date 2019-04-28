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

package com.android.tv.settings.connectivity.setup;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.ObjectAnimator;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Process;
import android.text.TextUtils;
import android.util.Pair;
import android.view.accessibility.AccessibilityEvent;

import com.android.settingslib.wifi.WifiTracker;
import com.android.tv.settings.R;
import com.android.tv.settings.connectivity.AdvancedWifiOptionsFlow;
import com.android.tv.settings.connectivity.ConnectToWifiFragment;
import com.android.tv.settings.connectivity.FormPageDisplayer;
import com.android.tv.settings.connectivity.TimedMessageWizardFragment;
import com.android.tv.settings.connectivity.WifiConfigHelper;
import com.android.tv.settings.connectivity.WifiFormPageType;
import com.android.tv.settings.connectivity.WifiMultiPagedFormActivity;
import com.android.tv.settings.connectivity.WifiSecurity;
import com.android.tv.settings.connectivity.WpsConnectionActivity;
import com.android.tv.settings.connectivity.setup.SelectFromListWizardFragment.ListItem;
import com.android.tv.settings.form.FormPage;
import com.android.tv.settings.form.FormPageResultListener;
import com.android.tv.settings.util.ThemeHelper;
import com.android.tv.settings.util.TransitionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Wi-Fi settings during initial setup for a large no-touch device
 */
public class WifiSetupActivity extends WifiMultiPagedFormActivity
        implements ConnectToWifiFragment.Listener, TimedMessageWizardFragment.Listener {

    private static final String TAG = "WifiSetupActivity";
    private static final int NETWORK_REFRESH_BUFFER_DURATION = 5000;

    private static final String EXTRA_SHOW_SUMMARY = "extra_show_summary";
    private static final String EXTRA_SHOW_SKIP_NETWORK = "extra_show_skip_network";
    private static final String EXTRA_SHOW_WPS_AT_TOP = "extra_show_wps_at_top";
    private static final String EXTRA_MOVING_FORWARD = "movingForward";
    // If you change this constant, make sure to change the constant in setup wizard
    private static final int RESULT_NETWORK_SKIPPED = 3;

    private boolean mResultOk = false;
    private boolean mShowFirstFragmentBackwards;
    private boolean mShowSkipNetwork;
    private boolean mShowWpsAtTop;
    private AdvancedWifiOptionsFlow mAdvancedWifiOptionsFlow;
    private WifiTracker mWifiTracker;
    private HandlerThread mBgThread;

    private WifiConfiguration mConfiguration;
    private String mConnectedNetwork;
    private WifiSecurity mWifiSecurity;
    private FormPageDisplayer.UserActivityListener mUserActivityListener;
    private FormPage mChooseNetworkPage;
    private FormPage mSsidPage;
    private FormPage mSecurityPage;
    private FormPage mPasswordPage;
    private SelectFromListWizardFragment mNetworkListFragment;
    private FormPage mConnectPage;
    private FormPage mSuccessPage;
    private long mNextNetworkRefreshTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mConfiguration = new WifiConfiguration();

        WifiTracker.WifiListener wifiListener = new WifiTracker.WifiListener() {
            @Override
            public void onWifiStateChanged(int state) {}

            @Override
            public void onConnectedChanged() {}

            @Override
            public void onAccessPointsChanged() {
                long currentTime = System.currentTimeMillis();
                if (mNetworkListFragment != null && currentTime >= mNextNetworkRefreshTime) {
                    mNetworkListFragment.update(WifiFormPageType.CHOOSE_NETWORK.getChoices(
                            WifiSetupActivity.this, getNetworks()));
                    mNextNetworkRefreshTime = currentTime + NETWORK_REFRESH_BUFFER_DURATION;
                }
            }
        };
        mBgThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        mBgThread.start();
        mWifiTracker = new WifiTracker(this, wifiListener, mBgThread.getLooper(), true, true);
        mNextNetworkRefreshTime = System.currentTimeMillis() + NETWORK_REFRESH_BUFFER_DURATION;

        mUserActivityListener = new FormPageDisplayer.UserActivityListener() {
            @Override
            public void onUserActivity() {
                mNextNetworkRefreshTime =
                        System.currentTimeMillis() + NETWORK_REFRESH_BUFFER_DURATION;
            }
        };

        boolean showSummary = getIntent().getBooleanExtra(EXTRA_SHOW_SUMMARY, false);
        mShowSkipNetwork = getIntent().getBooleanExtra(EXTRA_SHOW_SKIP_NETWORK, false);
        mShowWpsAtTop = getIntent().getBooleanExtra(EXTRA_SHOW_WPS_AT_TOP, false);
        // If we are not moving forwards during the setup flow, we need to show the first fragment
        // with the reverse animation.
        mShowFirstFragmentBackwards = !getIntent().getBooleanExtra(EXTRA_MOVING_FORWARD, true);

        if (showSummary) {
            addSummaryPage();
        } else {
            addPage(WifiFormPageType.CHOOSE_NETWORK);
        }

        super.onCreate(savedInstanceState);

        // fade in
        ObjectAnimator animator = TransitionUtils.createActivityFadeInAnimator(getResources(),
                true);
        animator.setTarget(getContentView());
        animator.start();
    }

    @Override
    public void onResume() {
        super.onResume();
        mWifiTracker.startTracking();
    }

    @Override
    public void onPause() {
        super.onPause();
        mWifiTracker.stopTracking();
    }

    @Override
    public void finish() {
        Animator animator;

        // Choose finish animation based on whether we are in Setup or Settings and really
        // finish this activity when the animation is complete.
        if (ThemeHelper.fromSetupWizard(getIntent())) {
            animator = mResultOk
                    ? AnimatorInflater.loadAnimator(this, R.anim.setup_fragment_open_out)
                    : AnimatorInflater.loadAnimator(this, R.anim.setup_fragment_close_out);
        } else {
            animator = TransitionUtils.createActivityFadeOutAnimator(getResources(), true);
        }

        animator.setTarget(getContentView());
        animator.addListener(new Animator.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                doFinish();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }
        });
        animator.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBgThread.quit();
    }

    @Override
    public void onConnectToWifiCompleted(int reason) {
        Bundle result = new Bundle();
        result.putString(FormPage.DATA_KEY_SUMMARY_STRING, Integer.toString(reason));
        onBundlePageResult(mConnectPage, result);
    }

    @Override
    public void onTimedMessageCompleted() {
        Bundle result = new Bundle();
        result.putString(FormPage.DATA_KEY_SUMMARY_STRING, "");
        onBundlePageResult(mSuccessPage, result);
    }

    @Override
    public void addPage(WifiFormPageType formPageType) {
        for (int i = mFormPages.size() - 1; i >= 0; i--) {
            if (getFormPageType(mFormPages.get (i)) == formPageType) {
                for (int j = mFormPages.size() - 1; j >= i; j--) {
                    mFormPages.remove(j);
                }
                break;
            }
        }
        addPage(formPageType.create());
    }

    @Override
    protected boolean onPageComplete(WifiFormPageType formPageType, FormPage formPage) {

        switch (formPageType) {
            case KNOWN_NETWORK:
                if (choiceChosen(formPage, R.string.wifi_connect)) {
                    addStartPage();
                } else if (choiceChosen(formPage, R.string.wifi_forget_network)) {
                    ((WifiManager) getSystemService(Context.WIFI_SERVICE)).forget(
                            mConfiguration.networkId, null);
                    addPage(WifiFormPageType.CHOOSE_NETWORK);
                }
                break;
            case CHOOSE_NETWORK:
                if (choiceChosen(formPage, R.string.skip_network)) {
                    WifiConfigHelper.forgetWifiNetwork(this);
                    setResult(RESULT_NETWORK_SKIPPED);
                } else {
                    mWifiTracker.pauseScanning();
                    mNetworkListFragment = null;
                    mChooseNetworkPage = formPage;
                    addPageBasedOnNetworkChoice(mChooseNetworkPage);
                }
                break;
            case ENTER_SSID:
                mSsidPage = formPage;
                String ssid = formPage.getDataSummary();
                if (mPasswordPage != null
                        && !TextUtils.equals(mConfiguration.getPrintableSsid(), ssid)) {
                    mPasswordPage.clearData();
                }
                WifiConfigHelper.setConfigSsid(mConfiguration, ssid);
                addPage(WifiFormPageType.CHOOSE_SECURITY);
                break;
            case CHOOSE_SECURITY:
                mSecurityPage = formPage;
                if (choiceChosen(formPage, R.string.wifi_security_type_none)) {
                    mWifiSecurity = WifiSecurity.NONE;
                } else if (choiceChosen(formPage, R.string.wifi_security_type_wep)) {
                    mWifiSecurity = WifiSecurity.WEP;
                } else if (choiceChosen(formPage, R.string.wifi_security_type_wpa)) {
                    mWifiSecurity = WifiSecurity.PSK;
                } else if (choiceChosen(formPage, R.string.wifi_security_type_eap)) {
                    mWifiSecurity = WifiSecurity.EAP;
                }
                WifiConfigHelper.setConfigKeyManagementBySecurity(mConfiguration, mWifiSecurity);
                if (mWifiSecurity == WifiSecurity.NONE) {
                    optionsOrConnect();
                } else {
                    addPage(WifiFormPageType.ENTER_PASSWORD);
                }
                break;
            case ENTER_PASSWORD:
                mPasswordPage = formPage;
                String password = formPage.getDataSummary();
                setWifiConfigurationPassword(mConfiguration, mWifiSecurity, password);
                optionsOrConnect();
                break;
            case CONNECT:
                switch (Integer.valueOf(formPage.getDataSummary())) {
                    case ConnectToWifiFragment.RESULT_REJECTED_BY_AP:
                        addPage(WifiFormPageType.CONNECT_REJECTED_BY_AP);
                        break;
                    case ConnectToWifiFragment.RESULT_UNKNOWN_ERROR:
                        addPage(WifiFormPageType.CONNECT_FAILED);
                        break;
                    case ConnectToWifiFragment.RESULT_TIMEOUT:
                        addPage(WifiFormPageType.CONNECT_TIMEOUT);
                        break;
                    case ConnectToWifiFragment.RESULT_BAD_AUTHENTICATION:
                        WifiConfigHelper.forgetConfiguration(this, mConfiguration);
                        addPage(WifiFormPageType.CONNECT_AUTHENTICATION_FAILURE);
                        break;
                    case ConnectToWifiFragment.RESULT_SUCCESS:
                        WifiConfigHelper.saveConfiguration(this, mConfiguration);
                        addPage(WifiFormPageType.SUCCESS);
                        break;
                    default:
                        break;
                }
                break;
            case CONNECT_TIMEOUT:
                mAdvancedWifiOptionsFlow = new AdvancedWifiOptionsFlow(this, this, true, null);
                // fall through
            case CONNECT_REJECTED_BY_AP:
                if (choiceChosen(formPage, R.string.wifi_action_try_again)) {
                    optionsOrConnect();
                } else {
                    mSsidPage = null;
                    mSecurityPage = null;
                    mPasswordPage = null;
                    addPage(WifiFormPageType.CHOOSE_NETWORK);
                }
                break;
            case CONNECT_FAILED:
                // fall through
            case CONNECT_AUTHENTICATION_FAILURE:
                if (choiceChosen(formPage, R.string.wifi_action_try_again)) {
                    addPageBasedOnNetworkChoice(mChooseNetworkPage);
                } else {
                    mSsidPage = null;
                    mSecurityPage = null;
                    mPasswordPage = null;
                    addPage(WifiFormPageType.CHOOSE_NETWORK);
                }
                break;
            case SUMMARY_CONNECTED_WIFI:
                if (choiceChosen(formPage, R.string.wifi_action_dont_change_network)) {
                    setResultOk();
                    finish();
                } else if (choiceChosen(formPage, R.string.wifi_action_change_network)) {
                    addPage(WifiFormPageType.CHOOSE_NETWORK);
                }
                break;
            case SUMMARY_CONNECTED_NON_WIFI:
                setResultOk();
                finish();
                break;
            case SUMMARY_NOT_CONNECTED:
                addPage(WifiFormPageType.CHOOSE_NETWORK);
                break;
            case SUCCESS:
                setResultOk();
                break;
            case WPS:
                setResultOk();
                break;
            default:
                if (mAdvancedWifiOptionsFlow != null) {
                    switch (mAdvancedWifiOptionsFlow.handlePageComplete(formPageType, formPage)) {
                        case AdvancedWifiOptionsFlow.RESULT_ALL_PAGES_COMPLETE:
                            connect();
                            break;
                        case AdvancedWifiOptionsFlow.RESULT_UNKNOWN_PAGE:
                        case AdvancedWifiOptionsFlow.RESULT_PAGE_HANDLED:
                        default:
                            break;
                    }
                }
                break;
        }
        return true;
    }

    @Override
    protected void displayPage(FormPage formPage, FormPageResultListener listener,
            boolean forward) {
        WifiFormPageType formPageType = getFormPageType(formPage);

        if (mShowFirstFragmentBackwards) {
            // We entered this activity by moving backwards in setup, so set the forward boolean
            // to false in order to show the first fragment with backwards animation.
            forward = false;

            // Reset the flag to false so that following fragments are handled normally.
            mShowFirstFragmentBackwards = false;
        }

        if (formPageType == WifiFormPageType.CONNECT) {
            mConnectPage = formPage;
            Fragment fragment = ConnectToWifiFragment.newInstance(
                    getString(formPageType.getTitleResourceId(), mConfiguration.getPrintableSsid()),
                    true, mConfiguration);
            displayFragment(fragment, forward);
        } else if (formPageType == WifiFormPageType.SUCCESS) {
            mSuccessPage = formPage;
            Fragment fragment = TimedMessageWizardFragment.newInstance(
                    getString(formPageType.getTitleResourceId()));
            displayFragment(fragment, forward);
        } else if (formPageType == WifiFormPageType.WPS) {
            displayFragment(MessageWizardFragment.newInstance("", true), forward);
        } else {
            Fragment fragment = displayPage(formPageType, mConfiguration.getPrintableSsid(),
                    formPageType == WifiFormPageType.SUMMARY_CONNECTED_WIFI ? mConnectedNetwork
                            : null,
                    formPageType == WifiFormPageType.CHOOSE_NETWORK ? getNetworks() : null,
                    getLastPage(formPageType),
                    formPageType == WifiFormPageType.CHOOSE_NETWORK ? mUserActivityListener : null,
                    formPageType != WifiFormPageType.SUCCESS, formPage, listener, forward,
                            (mAdvancedWifiOptionsFlow != null) ? mAdvancedWifiOptionsFlow
                            .isEmptyTextAllowed(formPageType) : false);
            if (formPageType == WifiFormPageType.CHOOSE_NETWORK) {
                mNetworkListFragment = (SelectFromListWizardFragment) fragment;
                mWifiTracker.resumeScanning();
            }
        }
        if (ThemeHelper.fromSetupWizard(getIntent())) {
            updateTitle(formPageType);
        }
    }

    @Override
    protected void undisplayCurrentPage() {
        mWifiTracker.pauseScanning();

        FragmentManager fragMan = getFragmentManager();
        Fragment target = fragMan.findFragmentById(R.id.content);
        FragmentTransaction transaction = fragMan.beginTransaction();
        transaction.remove(target);
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
        transaction.commit();
    }

    private void doFinish() {
        super.finish();
    }

    private void connect() {
        if (!WifiConfigHelper.isNetworkSaved(mConfiguration) &&
            mAdvancedWifiOptionsFlow != null) {
            mAdvancedWifiOptionsFlow.updateConfiguration(mConfiguration);
        }
        addPage(WifiFormPageType.CONNECT);
    }

    private void optionsOrConnect() {
        if (mAdvancedWifiOptionsFlow != null) {
            addPage(mAdvancedWifiOptionsFlow.getInitialPage());
        } else {
            connect();
        }
    }

    private FormPage getLastPage(WifiFormPageType formPageType) {
        switch (formPageType) {
            case CHOOSE_NETWORK:
                return mChooseNetworkPage;
            case CHOOSE_SECURITY:
                return mSecurityPage;
            case ENTER_PASSWORD:
                return mPasswordPage;
            case ENTER_SSID:
                return mSsidPage;
            default:
                return (mAdvancedWifiOptionsFlow != null) ? mAdvancedWifiOptionsFlow
                        .getPreviousPage(formPageType)
                        : null;
        }
    }

    private ArrayList<ListItem> getNetworks() {
        ArrayList<SelectFromListWizardFragment.ListItem> listItems = new ArrayList<>();
        final List<ScanResult> results = mWifiTracker.getManager().getScanResults();
        final HashMap<Pair<String, WifiSecurity>, ScanResult> consolidatedScanResults =
                new HashMap<>();
        for (ScanResult result : results) {
            if (TextUtils.isEmpty(result.SSID)) {
                continue;
            }

            Pair<String, WifiSecurity> key =
                    new Pair<>(result.SSID, WifiSecurity.getSecurity(result));
            ScanResult existing = consolidatedScanResults.get(key);
            if (existing == null || existing.level < result.level) {
                consolidatedScanResults.put(key, result);
            }
        }
        for (ScanResult result : consolidatedScanResults.values()) {
            listItems.add(new SelectFromListWizardFragment.ListItem(result));
        }

        int wpsPinnedPos = mShowWpsAtTop ? SelectFromListWizardFragment.PinnedListItem.FIRST
                                         : SelectFromListWizardFragment.PinnedListItem.LAST;

        SelectFromListWizardFragment.PinnedListItem wpsItem =
                new SelectFromListWizardFragment.PinnedListItem(
                        getString(R.string.wps_network), R.drawable.setup_wps, wpsPinnedPos, 0);

        listItems.add(wpsItem);

        if (mShowSkipNetwork) {
            listItems.add(new SelectFromListWizardFragment.PinnedListItem(
                    getString(R.string.skip_network), R.drawable.ic_arrow_forward,
                    SelectFromListWizardFragment.PinnedListItem.LAST, 1));
        }

        return listItems;
    }

    private void addPageBasedOnNetworkChoice(FormPage chooseNetworkPage) {
        if (choiceChosen(chooseNetworkPage, R.string.other_network)) {
            mConfiguration.hiddenSSID = true;
            addPage(WifiFormPageType.ENTER_SSID);
        } else if (choiceChosen(chooseNetworkPage, R.string.wps_network)) {
            addPage(WifiFormPageType.WPS, new Intent(this, WpsConnectionActivity.class)
                    .putExtras(getIntent().getExtras()));
        } else {
            ScanResult scanResult = getListItem(chooseNetworkPage).getScanResult();

            // If we are entering password for an AP that is different from the previously saved AP,
            // clear out the saved password.
            if (mPasswordPage != null
                    && (mConfiguration == null
                               || !scanResult.SSID.equals(mConfiguration.getPrintableSsid()))) {
                mPasswordPage.clearData();
            }

            mConfiguration = WifiConfigHelper.getConfiguration(this, scanResult.SSID,
                    WifiSecurity.getSecurity(scanResult));
            mWifiSecurity = WifiSecurity.getSecurity(scanResult);

            if (WifiConfigHelper.isNetworkSaved(mConfiguration)) {
                addPage(WifiFormPageType.KNOWN_NETWORK);
            } else {
                addStartPage();
            }
        }
    }

    private void addStartPage() {
        if ((mWifiSecurity == WifiSecurity.WEP && TextUtils.isEmpty(mConfiguration.wepKeys[0]))
                || (!mWifiSecurity.isOpen() && TextUtils.isEmpty(mConfiguration.preSharedKey))) {
            addPage(WifiFormPageType.ENTER_PASSWORD);
        } else {
            connect();
        }
    }

    private void addSummaryPage() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo currentConnection = connectivityManager.getActiveNetworkInfo();
        boolean isConnected = (currentConnection != null) && currentConnection.isConnected();
        if (isConnected) {
            if (currentConnection.getType() == ConnectivityManager.TYPE_WIFI) {
                WifiInfo currentWifiConnection = mWifiTracker.getManager().getConnectionInfo();
                mConnectedNetwork = WifiInfo.removeDoubleQuotes(
                        currentWifiConnection.getSSID());
                if (mConnectedNetwork == null) {
                    mConnectedNetwork = getString(R.string.wifi_summary_unknown_network);
                }
                addPage(WifiFormPageType.SUMMARY_CONNECTED_WIFI);
            } else {
                addPage(WifiFormPageType.SUMMARY_CONNECTED_NON_WIFI);
            }
        } else {
            addPage(WifiFormPageType.SUMMARY_NOT_CONNECTED);
        }
    }

    private void updateTitle(WifiFormPageType pageType) {
        switch (pageType) {
            // Fall through for all pageTypes that require the SSID of the network for
            // the title.
            case ADVANCED_OPTIONS:
            case CONNECT:
            case CONNECT_FAILED:
            case CONNECT_TIMEOUT:
            case KNOWN_NETWORK:
            case SAVE:
            case SAVE_FAILED:
                setTitle(getResources().getString(pageType.getTitleResourceId(),
                        mConfiguration.getPrintableSsid()));
                break;
            case WPS:
                // Delegate title to the WPSConnectionActivity. Use blank string to prevent
                // talkback from announcing a misplaced title.
                setTitle("");
                return;
            case ENTER_PASSWORD:
                // IME screen; Let the IME handle announcements.
                setTitle("");
                return;
            case ENTER_SSID:
                // IME screen; Let the IME handle announcements.
                setTitle("");
                return;
            default:
                setTitle(getResources().getString(pageType.getTitleResourceId()));
                break;
        }
        getWindow().getDecorView()
                .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    private void setResultOk() {
        setResult(RESULT_OK);
        mResultOk = true;
    }
}
