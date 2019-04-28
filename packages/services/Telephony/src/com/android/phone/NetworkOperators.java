/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.phone;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.TwoStatePreference;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

/**
 * "Networks" settings UI for the Phone app.
 */
public class NetworkOperators extends PreferenceCategory
        implements Preference.OnPreferenceChangeListener {

    private static final String LOG_TAG = "NetworkOperators";
    private static final boolean DBG = true;

    private static final int EVENT_AUTO_SELECT_DONE = 100;
    private static final int EVENT_GET_NETWORK_SELECTION_MODE_DONE = 200;

    //String keys for preference lookup
    public static final String BUTTON_NETWORK_SELECT_KEY = "button_network_select_key";
    public static final String BUTTON_AUTO_SELECT_KEY = "button_auto_select_key";
    public static final String CATEGORY_NETWORK_OPERATORS_KEY = "network_operators_category_key";

    int mPhoneId = SubscriptionManager.INVALID_PHONE_INDEX;

    //preference objects
    private NetworkSelectListPreference mNetworkSelect;
    private TwoStatePreference mAutoSelect;

    private int mSubId;
    private ProgressDialog mProgressDialog;

    public NetworkOperators(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NetworkOperators(Context context) {
        super(context);
    }

    /**
     * Initialize NetworkOperators instance.
     */
    public void initialize() {
        mNetworkSelect =
                (NetworkSelectListPreference) findPreference(BUTTON_NETWORK_SELECT_KEY);
        mAutoSelect =
                (TwoStatePreference) findPreference(BUTTON_AUTO_SELECT_KEY);
        mProgressDialog = new ProgressDialog(getContext());
    }

    /**
     * Update NetworkOperators instance if like subId or queryService are updated.
     *
     * @param subId Corresponding subscription ID of this network.
     * @param queryService The service to do network queries.
     */
    protected void update(final int subId, INetworkQueryService queryService) {
        mSubId = subId;
        mPhoneId = SubscriptionManager.getPhoneId(mSubId);

        if (mAutoSelect != null) {
            mAutoSelect.setOnPreferenceChangeListener(this);
        }

        if (mNetworkSelect != null) {
            mNetworkSelect.initialize(mSubId, queryService, this, mProgressDialog);
        }

        getNetworkSelectionMode();
    }

    /**
     * Implemented to support onPreferenceChangeListener to look for preference
     * changes specifically on auto select button.
     *
     * @param preference is the preference to be changed, should be auto select button.
     * @param newValue should be the value of whether autoSelect is checked.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mAutoSelect) {
            boolean autoSelect = (Boolean) newValue;
            selectNetworkAutomatic(autoSelect);
            return true;
        }
        return false;
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
                case EVENT_AUTO_SELECT_DONE:
                    mAutoSelect.setEnabled(true);
                    dismissProgressBar();

                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) logd("automatic network selection: failed!");
                        displayNetworkSelectionFailed(ar.exception);
                    } else {
                        if (DBG) logd("automatic network selection: succeeded!");
                        displayNetworkSelectionSucceeded();
                    }

                    break;
                case EVENT_GET_NETWORK_SELECTION_MODE_DONE:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) logd("get network selection mode: failed!");
                    } else if (ar.result != null) {
                        try {
                            int[] modes = (int[]) ar.result;
                            boolean autoSelect = (modes[0] == 0);
                            if (DBG) {
                                logd("get network selection mode: "
                                        + (autoSelect ? "auto" : "manual") + " selection");
                            }
                            if (mAutoSelect != null) {
                                mAutoSelect.setChecked(autoSelect);
                            }
                            if (mNetworkSelect != null) {
                                mNetworkSelect.setEnabled(!autoSelect);
                            }
                        } catch (Exception e) {
                            if (DBG) loge("get network selection mode: unable to parse result.");
                        }
                    }
            }

            return;
        }
    };

    // Used by both mAutoSelect and mNetworkSelect buttons.
    protected void displayNetworkSelectionFailed(Throwable ex) {
        String status;

        if ((ex != null && ex instanceof CommandException)
                && ((CommandException) ex).getCommandError()
                        == CommandException.Error.ILLEGAL_SIM_OR_ME) {
            status = getContext().getResources().getString(R.string.not_allowed);
        } else {
            status = getContext().getResources().getString(R.string.connect_later);
        }

        final PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);

        TelephonyManager tm = (TelephonyManager) app.getSystemService(Context.TELEPHONY_SERVICE);
        Phone phone = PhoneFactory.getPhone(mPhoneId);
        if (phone != null) {
            ServiceState ss = tm.getServiceStateForSubscriber(phone.getSubId());
            if (ss != null) {
                app.notificationMgr.updateNetworkSelection(ss.getState(), phone.getSubId());
            }
        }
    }

    // Used by both mAutoSelect and mNetworkSelect buttons.
    protected void displayNetworkSelectionSucceeded() {
        String status = getContext().getResources().getString(R.string.registration_done);

        final PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);
    }

    private void selectNetworkAutomatic(boolean autoSelect) {
        if (mNetworkSelect != null) {
            mNetworkSelect.setEnabled(!autoSelect);
        }
        if (autoSelect) {
            if (DBG) logd("select network automatically...");
            showAutoSelectProgressBar();
            mAutoSelect.setEnabled(false);
            Message msg = mHandler.obtainMessage(EVENT_AUTO_SELECT_DONE);
            Phone phone = PhoneFactory.getPhone(mPhoneId);
            if (phone != null) {
                phone.setNetworkSelectionModeAutomatic(msg);
            }
        } else if (mNetworkSelect != null) {
            mNetworkSelect.onClick();
        }
    }

    protected void getNetworkSelectionMode() {
        if (DBG) logd("getting network selection mode...");
        Message msg = mHandler.obtainMessage(EVENT_GET_NETWORK_SELECTION_MODE_DONE);
        Phone phone = PhoneFactory.getPhone(mPhoneId);
        if (phone != null) {
            phone.getNetworkSelectionMode(msg);
        }
    }

    private void dismissProgressBar() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    private void showAutoSelectProgressBar() {
        mProgressDialog.setMessage(
                getContext().getResources().getString(R.string.register_automatically));
        mProgressDialog.setCanceledOnTouchOutside(false);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.show();
    }

    protected boolean preferenceTreeClick(Preference preference) {
        return (preference == mAutoSelect || preference == mNetworkSelect);
    }

    private void logd(String msg) {
        Log.d(LOG_TAG, "[NetworksList] " + msg);
    }

    private void loge(String msg) {
        Log.e(LOG_TAG, "[NetworksList] " + msg);
    }
}
