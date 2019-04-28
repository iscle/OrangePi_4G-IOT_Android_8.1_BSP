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
import android.content.DialogInterface;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.preference.ListPreference;
import android.preference.Preference;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import com.android.internal.telephony.OperatorInfo;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

import java.util.List;


/**
 * "Networks" preference in "Mobile network" settings UI for the Phone app.
 * It's used to manually search and choose mobile network. Enabled only when
 * autoSelect preference is turned off.
 */
public class NetworkSelectListPreference extends ListPreference
        implements DialogInterface.OnCancelListener,
        Preference.OnPreferenceChangeListener{

    private static final String LOG_TAG = "networkSelect";
    private static final boolean DBG = true;

    private static final int EVENT_NETWORK_SCAN_COMPLETED = 100;
    private static final int EVENT_NETWORK_SELECTION_DONE = 200;

    //dialog ids
    private static final int DIALOG_NETWORK_SELECTION = 100;
    private static final int DIALOG_NETWORK_LIST_LOAD = 200;

    private int mPhoneId = SubscriptionManager.INVALID_PHONE_INDEX;
    private List<OperatorInfo> mOperatorInfoList;
    private OperatorInfo mOperatorInfo;

    private int mSubId;
    private NetworkOperators mNetworkOperators;

    private ProgressDialog mProgressDialog;
    public NetworkSelectListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NetworkSelectListPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onClick() {
        loadNetworksList();
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            switch (msg.what) {
                case EVENT_NETWORK_SCAN_COMPLETED:
                    networksListLoaded((List<OperatorInfo>) msg.obj, msg.arg1);
                    break;

                case EVENT_NETWORK_SELECTION_DONE:
                    if (DBG) logd("hideProgressPanel");
                    try {
                        dismissProgressBar();
                    } catch (IllegalArgumentException e) {
                    }
                    setEnabled(true);

                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        if (DBG) logd("manual network selection: failed!");
                        mNetworkOperators.displayNetworkSelectionFailed(ar.exception);
                    } else {
                        if (DBG) {
                            logd("manual network selection: succeeded!"
                                    + getNetworkTitle(mOperatorInfo));
                        }
                        mNetworkOperators.displayNetworkSelectionSucceeded();
                    }
                    mNetworkOperators.getNetworkSelectionMode();
                    break;
            }

            return;
        }
    };

    INetworkQueryService mNetworkQueryService = null;
    /**
     * This implementation of INetworkQueryServiceCallback is used to receive
     * callback notifications from the network query service.
     */
    private final INetworkQueryServiceCallback mCallback = new INetworkQueryServiceCallback.Stub() {

        /** place the message on the looper queue upon query completion. */
        public void onQueryComplete(List<OperatorInfo> networkInfoArray, int status) {
            if (DBG) logd("notifying message loop of query completion.");
            Message msg = mHandler.obtainMessage(EVENT_NETWORK_SCAN_COMPLETED,
                    status, 0, networkInfoArray);
            msg.sendToTarget();
        }
    };

    @Override
    //implemented for DialogInterface.OnCancelListener
    public void onCancel(DialogInterface dialog) {
        // request that the service stop the query with this callback object.
        try {
            if (mNetworkQueryService != null) {
                mNetworkQueryService.stopNetworkQuery(mCallback);
            }
            // If cancelled, we query NetworkSelectMode and update states of AutoSelect button.
            mNetworkOperators.getNetworkSelectionMode();
        } catch (RemoteException e) {
            loge("onCancel: exception from stopNetworkQuery " + e);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        // If dismissed, we query NetworkSelectMode and update states of AutoSelect button.
        if (!positiveResult) {
            mNetworkOperators.getNetworkSelectionMode();
        }
    }

    /**
     * Return normalized carrier name given network info.
     *
     * @param ni is network information in OperatorInfo type.
     */
    public String getNormalizedCarrierName(OperatorInfo ni) {
        if (ni != null) {
            return ni.getOperatorAlphaLong() + " (" + ni.getOperatorNumeric() + ")";
        }
        return null;
    }

    // This method is provided besides initialize() because bind to network query service
    // may be binded after initialize(). In that case this method needs to be called explicitly
    // to set mNetworkQueryService. Otherwise mNetworkQueryService will remain null.
    public void setNetworkQueryService(INetworkQueryService queryService) {
        mNetworkQueryService = queryService;
    }

    // This initialize method needs to be called for this preference to work properly.
    protected void initialize(int subId, INetworkQueryService queryService,
            NetworkOperators networkOperators, ProgressDialog progressDialog) {
        mSubId = subId;
        mNetworkQueryService = queryService;
        mNetworkOperators = networkOperators;
        // This preference should share the same progressDialog with networkOperators category.
        mProgressDialog = progressDialog;

        if (SubscriptionManager.isValidSubscriptionId(mSubId)) {
            mPhoneId = SubscriptionManager.getPhoneId(mSubId);
        }

        TelephonyManager telephonyManager = (TelephonyManager)
                getContext().getSystemService(Context.TELEPHONY_SERVICE);

        setSummary(telephonyManager.getNetworkOperatorName());

        setOnPreferenceChangeListener(this);
    }

    @Override
    protected void onPrepareForRemoval() {
        destroy();
        super.onPrepareForRemoval();
    }

    private void destroy() {
        try {
            dismissProgressBar();
        } catch (IllegalArgumentException e) {
            loge("onDestroy: exception from dismissProgressBar " + e);
        }

        try {
            if (mNetworkQueryService != null) {
                // used to un-register callback
                mNetworkQueryService.unregisterCallback(mCallback);
            }
        } catch (RemoteException e) {
            loge("onDestroy: exception from unregisterCallback " + e);
        }
    }

    private void displayEmptyNetworkList() {
        String status = getContext().getResources().getString(R.string.empty_networks_list);

        final PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);
    }

    private void displayNetworkSelectionInProgress() {
        showProgressBar(DIALOG_NETWORK_SELECTION);
    }

    private void displayNetworkQueryFailed(int error) {
        String status = getContext().getResources().getString(R.string.network_query_error);

        try {
            dismissProgressBar();
        } catch (IllegalArgumentException e1) {
            // do nothing
        }

        final PhoneGlobals app = PhoneGlobals.getInstance();
        app.notificationMgr.postTransientNotification(
                NotificationMgr.NETWORK_SELECTION_NOTIFICATION, status);
    }

    private void loadNetworksList() {
        if (DBG) logd("load networks list...");

        showProgressBar(DIALOG_NETWORK_LIST_LOAD);

        // delegate query request to the service.
        try {
            if (mNetworkQueryService != null) {
                mNetworkQueryService.startNetworkQuery(mCallback, mPhoneId);
            } else {
                displayNetworkQueryFailed(NetworkQueryService.QUERY_EXCEPTION);
            }
        } catch (RemoteException e) {
            loge("loadNetworksList: exception from startNetworkQuery " + e);
            displayNetworkQueryFailed(NetworkQueryService.QUERY_EXCEPTION);
        }
    }

    /**
     * networksListLoaded has been rewritten to take an array of
     * OperatorInfo objects and a status field, instead of an
     * AsyncResult.  Otherwise, the functionality which takes the
     * OperatorInfo array and creates a list of preferences from it,
     * remains unchanged.
     */
    private void networksListLoaded(List<OperatorInfo> result, int status) {
        if (DBG) logd("networks list loaded");

        // used to un-register callback
        try {
            if (mNetworkQueryService != null) {
                mNetworkQueryService.unregisterCallback(mCallback);
            }
        } catch (RemoteException e) {
            loge("networksListLoaded: exception from unregisterCallback " + e);
        }

        // update the state of the preferences.
        if (DBG) logd("hideProgressPanel");

        // Always try to dismiss the dialog because activity may
        // be moved to background after dialog is shown.
        try {
            dismissProgressBar();
        } catch (IllegalArgumentException e) {
            // It's not a error in following scenario, we just ignore it.
            // "Load list" dialog will not show, if NetworkQueryService is
            // connected after this activity is moved to background.
            loge("Fail to dismiss network load list dialog " + e);
        }

        setEnabled(true);
        clearList();

        if (status != NetworkQueryService.QUERY_OK) {
            if (DBG) logd("error while querying available networks");
            displayNetworkQueryFailed(status);
        } else {
            if (result != null) {
                // create a preference for each item in the list.
                // just use the operator name instead of the mildly
                // confusing mcc/mnc.
                mOperatorInfoList = result;
                CharSequence[] networkEntries = new CharSequence[result.size()];
                CharSequence[] networkEntryValues = new CharSequence[result.size()];
                for (int i = 0; i < mOperatorInfoList.size(); i++) {
                    if (mOperatorInfoList.get(i).getState() == OperatorInfo.State.FORBIDDEN) {
                        networkEntries[i] = getNetworkTitle(mOperatorInfoList.get(i))
                            + " "
                            + getContext().getResources().getString(R.string.forbidden_network);
                    } else {
                        networkEntries[i] = getNetworkTitle(mOperatorInfoList.get(i));
                    }
                    networkEntryValues[i] = Integer.toString(i + 2);
                }

                setEntries(networkEntries);
                setEntryValues(networkEntryValues);

                super.onClick();
            } else {
                displayEmptyNetworkList();
            }
        }
    }

    /**
     * Returns the title of the network obtained in the manual search.
     *
     * @param ni contains the information of the network.
     *
     * @return Long Name if not null/empty, otherwise Short Name if not null/empty,
     * else MCCMNC string.
     */
    private String getNetworkTitle(OperatorInfo ni) {
        if (!TextUtils.isEmpty(ni.getOperatorAlphaLong())) {
            return ni.getOperatorAlphaLong();
        } else if (!TextUtils.isEmpty(ni.getOperatorAlphaShort())) {
            return ni.getOperatorAlphaShort();
        } else {
            BidiFormatter bidiFormatter = BidiFormatter.getInstance();
            return bidiFormatter.unicodeWrap(ni.getOperatorNumeric(), TextDirectionHeuristics.LTR);
        }
    }

    private void clearList() {
        if (mOperatorInfoList != null) {
            mOperatorInfoList.clear();
        }
    }

    private void dismissProgressBar() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
    }

    private void showProgressBar(int id) {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(getContext());
        } else {
            // Dismiss progress bar if it's showing now.
            dismissProgressBar();
        }

        if ((id == DIALOG_NETWORK_SELECTION) || (id == DIALOG_NETWORK_LIST_LOAD)) {
            switch (id) {
                case DIALOG_NETWORK_SELECTION:
                    final String networkSelectMsg = getContext().getResources()
                            .getString(R.string.register_on_network,
                                    getNetworkTitle(mOperatorInfo));
                    mProgressDialog.setMessage(networkSelectMsg);
                    mProgressDialog.setCanceledOnTouchOutside(false);
                    mProgressDialog.setCancelable(false);
                    mProgressDialog.setIndeterminate(true);
                    break;
                case DIALOG_NETWORK_LIST_LOAD:
                    mProgressDialog.setMessage(
                            getContext().getResources().getString(R.string.load_networks_progress));
                    mProgressDialog.setCanceledOnTouchOutside(false);
                    mProgressDialog.setCancelable(true);
                    mProgressDialog.setIndeterminate(false);
                    mProgressDialog.setOnCancelListener(this);
                    break;
                default:
            }
            mProgressDialog.show();
        }
    }

    /**
     * Implemented to support onPreferenceChangeListener to look for preference
     * changes specifically on this button.
     *
     * @param preference is the preference to be changed, should be network select button.
     * @param newValue should be the value of the selection as index of operators.
     */
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        int operatorIndex = findIndexOfValue((String) newValue);
        mOperatorInfo = mOperatorInfoList.get(operatorIndex);

        if (DBG) logd("selected network: " + getNetworkTitle(mOperatorInfo));

        Message msg = mHandler.obtainMessage(EVENT_NETWORK_SELECTION_DONE);
        Phone phone = PhoneFactory.getPhone(mPhoneId);
        if (phone != null) {
            phone.selectNetworkManually(mOperatorInfo, true, msg);
            displayNetworkSelectionInProgress();
        } else {
            loge("Error selecting network. phone is null.");
        }

        return true;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.mDialogListEntries = getEntries();
        myState.mDialogListEntryValues = getEntryValues();
        myState.mOperatorInfoList = mOperatorInfoList;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;

        if (getEntries() == null && myState.mDialogListEntries != null) {
            setEntries(myState.mDialogListEntries);
        }
        if (getEntryValues() == null && myState.mDialogListEntryValues != null) {
            setEntryValues(myState.mDialogListEntryValues);
        }
        if (mOperatorInfoList == null && myState.mOperatorInfoList != null) {
            mOperatorInfoList = myState.mOperatorInfoList;
        }

        super.onRestoreInstanceState(myState.getSuperState());
    }

    /**
     *  We save entries, entryValues and operatorInfoList into bundle.
     *  At onCreate of fragment, dialog will be restored if it was open. In this case,
     *  we need to restore entries, entryValues and operatorInfoList. Without those information,
     *  onPreferenceChange will fail if user select network from the dialog.
     */
    private static class SavedState extends BaseSavedState {
        CharSequence[] mDialogListEntries;
        CharSequence[] mDialogListEntryValues;
        List<OperatorInfo> mOperatorInfoList;

        SavedState(Parcel source) {
            super(source);
            final ClassLoader boot = Object.class.getClassLoader();
            mDialogListEntries = source.readCharSequenceArray();
            mDialogListEntryValues = source.readCharSequenceArray();
            mOperatorInfoList = source.readParcelableList(mOperatorInfoList, boot);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeCharSequenceArray(mDialogListEntries);
            dest.writeCharSequenceArray(mDialogListEntryValues);
            dest.writeParcelableList(mOperatorInfoList, flags);
        }

        SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    private void logd(String msg) {
        Log.d(LOG_TAG, "[NetworksList] " + msg);
    }

    private void loge(String msg) {
        Log.e(LOG_TAG, "[NetworksList] " + msg);
    }
}
