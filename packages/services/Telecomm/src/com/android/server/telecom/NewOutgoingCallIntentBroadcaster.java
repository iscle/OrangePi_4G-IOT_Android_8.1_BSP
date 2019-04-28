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

package com.android.server.telecom;

import android.app.AppOpsManager;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Trace;
import android.os.UserHandle;
import android.telecom.GatewayInfo;
import android.telecom.Log;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.DisconnectCause;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;

// TODO: Needed for move to system service: import com.android.internal.R;

/**
 * OutgoingCallIntentBroadcaster receives CALL and CALL_PRIVILEGED Intents, and broadcasts the
 * ACTION_NEW_OUTGOING_CALL intent. ACTION_NEW_OUTGOING_CALL is an ordered broadcast intent which
 * contains the phone number being dialed. Applications can use this intent to (1) see which numbers
 * are being dialed, (2) redirect a call (change the number being dialed), or (3) prevent a call
 * from being placed.
 *
 * After the other applications have had a chance to see the ACTION_NEW_OUTGOING_CALL intent, it
 * finally reaches the {@link NewOutgoingCallBroadcastIntentReceiver}.
 *
 * Calls where no number is present (like for a CDMA "empty flash" or a nonexistent voicemail
 * number) are exempt from being broadcast.
 *
 * Calls to emergency numbers are still broadcast for informative purposes. The call is placed
 * prior to sending ACTION_NEW_OUTGOING_CALL and cannot be redirected nor prevented.
 */
@VisibleForTesting
public class NewOutgoingCallIntentBroadcaster {
    /**
     * Legacy string constants used to retrieve gateway provider extras from intents. These still
     * need to be copied from the source call intent to the destination intent in order to
     * support third party gateway providers that are still using old string constants in
     * Telephony.
     */
    public static final String EXTRA_GATEWAY_PROVIDER_PACKAGE =
            "com.android.phone.extra.GATEWAY_PROVIDER_PACKAGE";
    public static final String EXTRA_GATEWAY_URI = "com.android.phone.extra.GATEWAY_URI";

    private final CallsManager mCallsManager;
    private final Call mCall;
    private final Intent mIntent;
    private final Context mContext;
    private final PhoneNumberUtilsAdapter mPhoneNumberUtilsAdapter;
    private final TelecomSystem.SyncRoot mLock;

    /*
     * Whether or not the outgoing call intent originated from the default phone application. If
     * so, it will be allowed to make emergency calls, even with the ACTION_CALL intent.
     */
    private final boolean mIsDefaultOrSystemPhoneApp;

    @VisibleForTesting
    public NewOutgoingCallIntentBroadcaster(Context context, CallsManager callsManager, Call call,
            Intent intent, PhoneNumberUtilsAdapter phoneNumberUtilsAdapter,
            boolean isDefaultPhoneApp) {
        mContext = context;
        mCallsManager = callsManager;
        mCall = call;
        mIntent = intent;
        mPhoneNumberUtilsAdapter = phoneNumberUtilsAdapter;
        mIsDefaultOrSystemPhoneApp = isDefaultPhoneApp;
        mLock = mCallsManager.getLock();
    }

    /**
     * Processes the result of the outgoing call broadcast intent, and performs callbacks to
     * the OutgoingCallIntentBroadcasterListener as necessary.
     */
    public class NewOutgoingCallBroadcastIntentReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                Log.startSession("NOCBIR.oR");
                Trace.beginSection("onReceiveNewOutgoingCallBroadcast");
                synchronized (mLock) {
                    Log.v(this, "onReceive: %s", intent);

                    // Once the NEW_OUTGOING_CALL broadcast is finished, the resultData is
                    // used as the actual number to call. (If null, no call will be placed.)
                    String resultNumber = getResultData();
                    Log.i(this, "Received new-outgoing-call-broadcast for %s with data %s", mCall,
                            Log.pii(resultNumber));

                    boolean endEarly = false;
                    long disconnectTimeout =
                            Timeouts.getNewOutgoingCallCancelMillis(mContext.getContentResolver());
                    if (resultNumber == null) {
                        Log.v(this, "Call cancelled (null number), returning...");
                        disconnectTimeout = getDisconnectTimeoutFromApp(
                                getResultExtras(false), disconnectTimeout);
                        endEarly = true;
                    } else if (mPhoneNumberUtilsAdapter.isPotentialLocalEmergencyNumber(
                            mContext, resultNumber)) {
                        Log.w(this, "Cannot modify outgoing call to emergency number %s.",
                                resultNumber);
                        disconnectTimeout = 0;
                        endEarly = true;
                    }

                    if (endEarly) {
                        if (mCall != null) {
                            mCall.disconnect(disconnectTimeout);
                        }
                        return;
                    }

                    // If this call is already disconnected then we have nothing more to do.
                    if (mCall.isDisconnected()) {
                        Log.w(this, "Call has already been disconnected," +
                                        " ignore the broadcast Call %s", mCall);
                        return;
                    }

                    Uri resultHandleUri = Uri.fromParts(
                            mPhoneNumberUtilsAdapter.isUriNumber(resultNumber) ?
                                    PhoneAccount.SCHEME_SIP : PhoneAccount.SCHEME_TEL,
                            resultNumber, null);

                    Uri originalUri = mIntent.getData();

                    if (originalUri.getSchemeSpecificPart().equals(resultNumber)) {
                        Log.v(this, "Call number unmodified after" +
                                " new outgoing call intent broadcast.");
                    } else {
                        Log.v(this, "Retrieved modified handle after outgoing call intent" +
                                " broadcast: Original: %s, Modified: %s",
                                Log.pii(originalUri),
                                Log.pii(resultHandleUri));
                    }

                    GatewayInfo gatewayInfo = getGateWayInfoFromIntent(intent, resultHandleUri);
                    mCall.setNewOutgoingCallIntentBroadcastIsDone();
                    mCallsManager.placeOutgoingCall(mCall, resultHandleUri, gatewayInfo,
                            mIntent.getBooleanExtra(
                                    TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false),
                            mIntent.getIntExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                                    VideoProfile.STATE_AUDIO_ONLY));
                }
            } finally {
                Trace.endSection();
                Log.endSession();
            }
        }
    }

    /**
     * Processes the supplied intent and starts the outgoing call broadcast process relevant to the
     * intent.
     *
     * This method will handle three kinds of actions:
     *
     * - CALL (intent launched by all third party dialers)
     * - CALL_PRIVILEGED (intent launched by system apps e.g. system Dialer, voice Dialer)
     * - CALL_EMERGENCY (intent launched by lock screen emergency dialer)
     *
     * @return {@link DisconnectCause#NOT_DISCONNECTED} if the call succeeded, and an appropriate
     *         {@link DisconnectCause} if the call did not, describing why it failed.
     */
    @VisibleForTesting
    public int processIntent() {
        Log.v(this, "Processing call intent in OutgoingCallIntentBroadcaster.");

        Intent intent = mIntent;
        String action = intent.getAction();
        final Uri handle = intent.getData();

        if (handle == null) {
            Log.w(this, "Empty handle obtained from the call intent.");
            return DisconnectCause.INVALID_NUMBER;
        }

        boolean isVoicemailNumber = PhoneAccount.SCHEME_VOICEMAIL.equals(handle.getScheme());
        if (isVoicemailNumber) {
            if (Intent.ACTION_CALL.equals(action)
                    || Intent.ACTION_CALL_PRIVILEGED.equals(action)) {
                // Voicemail calls will be handled directly by the telephony connection manager
                Log.i(this, "Placing call immediately instead of waiting for "
                        + " OutgoingCallBroadcastReceiver: %s", intent);

                // Since we are not going to go through "Outgoing call broadcast", make sure
                // we mark it as ready.
                mCall.setNewOutgoingCallIntentBroadcastIsDone();

                boolean speakerphoneOn = mIntent.getBooleanExtra(
                        TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false);
                mCallsManager.placeOutgoingCall(mCall, handle, null, speakerphoneOn,
                        VideoProfile.STATE_AUDIO_ONLY);

                return DisconnectCause.NOT_DISCONNECTED;
            } else {
                Log.i(this, "Unhandled intent %s. Ignoring and not placing call.", intent);
                return DisconnectCause.OUTGOING_CANCELED;
            }
        }

        String number = mPhoneNumberUtilsAdapter.getNumberFromIntent(intent, mContext);
        if (TextUtils.isEmpty(number)) {
            Log.w(this, "Empty number obtained from the call intent.");
            return DisconnectCause.NO_PHONE_NUMBER_SUPPLIED;
        }

        boolean isUriNumber = mPhoneNumberUtilsAdapter.isUriNumber(number);
        if (!isUriNumber) {
            number = mPhoneNumberUtilsAdapter.convertKeypadLettersToDigits(number);
            number = mPhoneNumberUtilsAdapter.stripSeparators(number);
        }

        final boolean isPotentialEmergencyNumber = isPotentialEmergencyNumber(number);
        Log.v(this, "isPotentialEmergencyNumber = %s", isPotentialEmergencyNumber);

        rewriteCallIntentAction(intent, isPotentialEmergencyNumber);
        action = intent.getAction();
        // True for certain types of numbers that are not intended to be intercepted or modified
        // by third parties (e.g. emergency numbers).
        boolean callImmediately = false;

        if (Intent.ACTION_CALL.equals(action)) {
            if (isPotentialEmergencyNumber) {
                if (!mIsDefaultOrSystemPhoneApp) {
                    Log.w(this, "Cannot call potential emergency number %s with CALL Intent %s "
                            + "unless caller is system or default dialer.", number, intent);
                    launchSystemDialer(intent.getData());
                    return DisconnectCause.OUTGOING_CANCELED;
                } else {
                    callImmediately = true;
                }
            }
        } else if (Intent.ACTION_CALL_EMERGENCY.equals(action)) {
            if (!isPotentialEmergencyNumber) {
                Log.w(this, "Cannot call non-potential-emergency number %s with EMERGENCY_CALL "
                        + "Intent %s.", number, intent);
                return DisconnectCause.OUTGOING_CANCELED;
            }
            callImmediately = true;
        } else {
            Log.w(this, "Unhandled Intent %s. Ignoring and not placing call.", intent);
            return DisconnectCause.INVALID_NUMBER;
        }

        // True for all managed calls, false for self-managed calls.
        boolean sendNewOutgoingCallBroadcast = true;
        PhoneAccountHandle targetPhoneAccount = mIntent.getParcelableExtra(
                TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE);
        if (targetPhoneAccount != null) {
            PhoneAccount phoneAccount =
                    mCallsManager.getPhoneAccountRegistrar().getPhoneAccountUnchecked(
                            targetPhoneAccount);
            if (phoneAccount != null && phoneAccount.isSelfManaged()) {
                callImmediately = true;
                sendNewOutgoingCallBroadcast = false;
                Log.i(this, "Skipping NewOutgoingCallBroadcast for self-managed call.");
            }
        }

        if (callImmediately) {
            Log.i(this, "Placing call immediately instead of waiting for "
                    + " OutgoingCallBroadcastReceiver: %s", intent);
            String scheme = isUriNumber ? PhoneAccount.SCHEME_SIP : PhoneAccount.SCHEME_TEL;
            boolean speakerphoneOn = mIntent.getBooleanExtra(
                    TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false);
            int videoState = mIntent.getIntExtra(
                    TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                    VideoProfile.STATE_AUDIO_ONLY);
            mCallsManager.placeOutgoingCall(mCall, Uri.fromParts(scheme, number, null), null,
                    speakerphoneOn, videoState);

            // Don't return but instead continue and send the ACTION_NEW_OUTGOING_CALL broadcast
            // so that third parties can still inspect (but not intercept) the outgoing call. When
            // the broadcast finally reaches the OutgoingCallBroadcastReceiver, we'll know not to
            // initiate the call again because of the presence of the EXTRA_ALREADY_CALLED extra.
        }

        if (sendNewOutgoingCallBroadcast) {
            UserHandle targetUser = mCall.getInitiatingUser();
            Log.i(this, "Sending NewOutgoingCallBroadcast for %s to %s", mCall, targetUser);
            broadcastIntent(intent, number, !callImmediately, targetUser);
        }
        return DisconnectCause.NOT_DISCONNECTED;
    }

    /**
     * Sends a new outgoing call ordered broadcast so that third party apps can cancel the
     * placement of the call or redirect it to a different number.
     *
     * @param originalCallIntent The original call intent.
     * @param number Call number that was stored in the original call intent.
     * @param receiverRequired Whether or not the result from the ordered broadcast should be
     *                         processed using a {@link NewOutgoingCallIntentBroadcaster}.
     * @param targetUser User that the broadcast sent to.
     */
    private void broadcastIntent(
            Intent originalCallIntent,
            String number,
            boolean receiverRequired,
            UserHandle targetUser) {
        Intent broadcastIntent = new Intent(Intent.ACTION_NEW_OUTGOING_CALL);
        if (number != null) {
            broadcastIntent.putExtra(Intent.EXTRA_PHONE_NUMBER, number);
        }

        // Force receivers of this broadcast intent to run at foreground priority because we
        // want to finish processing the broadcast intent as soon as possible.
        broadcastIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND
                | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        Log.v(this, "Broadcasting intent: %s.", broadcastIntent);

        checkAndCopyProviderExtras(originalCallIntent, broadcastIntent);

        mContext.sendOrderedBroadcastAsUser(
                broadcastIntent,
                targetUser,
                android.Manifest.permission.PROCESS_OUTGOING_CALLS,
                AppOpsManager.OP_PROCESS_OUTGOING_CALLS,
                receiverRequired ? new NewOutgoingCallBroadcastIntentReceiver() : null,
                null,  // scheduler
                Activity.RESULT_OK,  // initialCode
                number,  // initialData: initial value for the result data (number to be modified)
                null);  // initialExtras
    }

    /**
     * Copy all the expected extras set when a 3rd party gateway provider is to be used, from the
     * source intent to the destination one.
     *
     * @param src Intent which may contain the provider's extras.
     * @param dst Intent where a copy of the extras will be added if applicable.
     */
    public void checkAndCopyProviderExtras(Intent src, Intent dst) {
        if (src == null) {
            return;
        }
        if (hasGatewayProviderExtras(src)) {
            dst.putExtra(EXTRA_GATEWAY_PROVIDER_PACKAGE,
                    src.getStringExtra(EXTRA_GATEWAY_PROVIDER_PACKAGE));
            dst.putExtra(EXTRA_GATEWAY_URI,
                    src.getStringExtra(EXTRA_GATEWAY_URI));
            Log.d(this, "Found and copied gateway provider extras to broadcast intent.");
            return;
        }

        Log.d(this, "No provider extras found in call intent.");
    }

    /**
     * Check if valid gateway provider information is stored as extras in the intent
     *
     * @param intent to check for
     * @return true if the intent has all the gateway information extras needed.
     */
    private boolean hasGatewayProviderExtras(Intent intent) {
        final String name = intent.getStringExtra(EXTRA_GATEWAY_PROVIDER_PACKAGE);
        final String uriString = intent.getStringExtra(EXTRA_GATEWAY_URI);

        return !TextUtils.isEmpty(name) && !TextUtils.isEmpty(uriString);
    }

    private static Uri getGatewayUriFromString(String gatewayUriString) {
        return TextUtils.isEmpty(gatewayUriString) ? null : Uri.parse(gatewayUriString);
    }

    /**
     * Extracts gateway provider information from a provided intent..
     *
     * @param intent to extract gateway provider information from.
     * @param trueHandle The actual call handle that the user is trying to dial
     * @return GatewayInfo object containing extracted gateway provider information as well as
     *     the actual handle the user is trying to dial.
     */
    public static GatewayInfo getGateWayInfoFromIntent(Intent intent, Uri trueHandle) {
        if (intent == null) {
            return null;
        }

        // Check if gateway extras are present.
        String gatewayPackageName = intent.getStringExtra(EXTRA_GATEWAY_PROVIDER_PACKAGE);
        Uri gatewayUri = getGatewayUriFromString(intent.getStringExtra(EXTRA_GATEWAY_URI));
        if (!TextUtils.isEmpty(gatewayPackageName) && gatewayUri != null) {
            return new GatewayInfo(gatewayPackageName, gatewayUri, trueHandle);
        }

        return null;
    }

    private void launchSystemDialer(Uri handle) {
        Intent systemDialerIntent = new Intent();
        final Resources resources = mContext.getResources();
        systemDialerIntent.setClassName(
                resources.getString(R.string.ui_default_package),
                resources.getString(R.string.dialer_default_class));
        systemDialerIntent.setAction(Intent.ACTION_DIAL);
        systemDialerIntent.setData(handle);
        systemDialerIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Log.v(this, "calling startActivity for default dialer: %s", systemDialerIntent);
        mContext.startActivityAsUser(systemDialerIntent, UserHandle.CURRENT);
    }

    /**
     * Check whether or not this is an emergency number, in order to enforce the restriction
     * that only the CALL_PRIVILEGED and CALL_EMERGENCY intents are allowed to make emergency
     * calls.
     *
     * To prevent malicious 3rd party apps from making emergency calls by passing in an
     * "invalid" number like "9111234" (that isn't technically an emergency number but might
     * still result in an emergency call with some networks), we use
     * isPotentialLocalEmergencyNumber instead of isLocalEmergencyNumber.
     *
     * @param number number to inspect in order to determine whether or not an emergency number
     * is potentially being dialed
     * @return True if the handle is potentially an emergency number.
     */
    private boolean isPotentialEmergencyNumber(String number) {
        Log.v(this, "Checking restrictions for number : %s", Log.pii(number));
        return (number != null)
                && mPhoneNumberUtilsAdapter.isPotentialLocalEmergencyNumber(mContext, number);
    }

    /**
     * Given a call intent and whether or not the number to dial is an emergency number, rewrite
     * the call intent action to an appropriate one.
     *
     * @param intent Intent to rewrite the action for
     * @param isPotentialEmergencyNumber Whether or not the number is potentially an emergency
     * number.
     */
    private void rewriteCallIntentAction(Intent intent, boolean isPotentialEmergencyNumber) {
        String action = intent.getAction();

        /* Change CALL_PRIVILEGED into CALL or CALL_EMERGENCY as needed. */
        if (Intent.ACTION_CALL_PRIVILEGED.equals(action)) {
            if (isPotentialEmergencyNumber) {
                Log.i(this, "ACTION_CALL_PRIVILEGED is used while the number is a potential"
                        + " emergency number. Using ACTION_CALL_EMERGENCY as an action instead.");
                action = Intent.ACTION_CALL_EMERGENCY;
            } else {
                action = Intent.ACTION_CALL;
            }
            Log.v(this, " - updating action from CALL_PRIVILEGED to %s", action);
            intent.setAction(action);
        }
    }

    private long getDisconnectTimeoutFromApp(Bundle resultExtras, long defaultTimeout) {
        if (resultExtras != null) {
            long disconnectTimeout = resultExtras.getLong(
                    TelecomManager.EXTRA_NEW_OUTGOING_CALL_CANCEL_TIMEOUT, defaultTimeout);
            if (disconnectTimeout < 0) {
                disconnectTimeout = 0;
            }
            return Math.min(disconnectTimeout,
                    Timeouts.getMaxNewOutgoingCallCancelMillis(mContext.getContentResolver()));
        } else {
            return defaultTimeout;
        }
    }
}
