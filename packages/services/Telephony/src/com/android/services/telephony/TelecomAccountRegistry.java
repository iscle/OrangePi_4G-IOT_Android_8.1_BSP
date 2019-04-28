/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2017. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
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

package com.android.services.telephony;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.ims.ImsManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;
import com.android.phone.R;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Owns all data we have registered with Telecom including handling dynamic addition and
 * removal of SIMs and SIP accounts.
 */
public final class TelecomAccountRegistry {
    private static final boolean DBG = false; /* STOP SHIP if true */

    // This icon is the one that is used when the Slot ID that we have for a particular SIM
    // is not supported, i.e. SubscriptionManager.INVALID_SLOT_ID or the 5th SIM in a phone.
    private final static int DEFAULT_SIM_ICON =  R.drawable.ic_multi_sim;
    private final static String GROUP_PREFIX = "group_";

    final class AccountEntry implements PstnPhoneCapabilitiesNotifier.Listener {
        private final Phone mPhone;
        private PhoneAccount mAccount;
        private final PstnIncomingCallNotifier mIncomingCallNotifier;
        private final PstnPhoneCapabilitiesNotifier mPhoneCapabilitiesNotifier;
        private boolean mIsEmergency;
        private boolean mIsDummy;
        private boolean mIsVideoCapable;
        private boolean mIsVideoPresenceSupported;
        private boolean mIsVideoPauseSupported;
        private boolean mIsMergeCallSupported;
        private boolean mIsMergeImsCallSupported;
        private boolean mIsVideoConferencingSupported;
        private boolean mIsMergeOfWifiCallsAllowedWhenVoWifiOff;

        AccountEntry(Phone phone, boolean isEmergency, boolean isDummy) {
            mPhone = phone;
            mIsEmergency = isEmergency;
            mIsDummy = isDummy;
            mAccount = registerPstnPhoneAccount(isEmergency, isDummy);
            Log.i(this, "Registered phoneAccount: %s with handle: %s",
                    mAccount, mAccount.getAccountHandle());
            mIncomingCallNotifier = new PstnIncomingCallNotifier((Phone) mPhone);
            mPhoneCapabilitiesNotifier = new PstnPhoneCapabilitiesNotifier((Phone) mPhone,
                    this);
        }

        void teardown() {
            mIncomingCallNotifier.teardown();
            mPhoneCapabilitiesNotifier.teardown();
        }

        /**
         * Registers the specified account with Telecom as a PhoneAccountHandle.
         */
        private PhoneAccount registerPstnPhoneAccount(boolean isEmergency, boolean isDummyAccount) {
            String dummyPrefix = isDummyAccount ? "Dummy " : "";

            // Build the Phone account handle.
            PhoneAccountHandle phoneAccountHandle =
                    PhoneUtils.makePstnPhoneAccountHandleWithPrefix(
                            mPhone, dummyPrefix, isEmergency);

            // Populate the phone account data.
            int subId = mPhone.getSubId();
            String subscriberId = mPhone.getSubscriberId();
            int color = PhoneAccount.NO_HIGHLIGHT_COLOR;
            int slotId = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
            String line1Number = mTelephonyManager.getLine1Number(subId);
            if (line1Number == null) {
                line1Number = "";
            }
            String subNumber = mPhone.getLine1Number();
            if (subNumber == null) {
                subNumber = "";
            }

            String label;
            String description;
            Icon icon = null;

            // We can only get the real slotId from the SubInfoRecord, we can't calculate the
            // slotId from the subId or the phoneId in all instances.
            SubscriptionInfo record =
                    mSubscriptionManager.getActiveSubscriptionInfo(subId);

            if (isEmergency) {
                label = mContext.getResources().getString(R.string.sim_label_emergency_calls);
                description =
                        mContext.getResources().getString(R.string.sim_description_emergency_calls);
            } else if (mTelephonyManager.getPhoneCount() == 1) {
                // For single-SIM devices, we show the label and description as whatever the name of
                // the network is.
                description = label = mTelephonyManager.getNetworkOperatorName();
            } else {
                CharSequence subDisplayName = null;

                if (record != null) {
                    subDisplayName = record.getDisplayName();
                    slotId = record.getSimSlotIndex();
                    color = record.getIconTint();
                    icon = Icon.createWithBitmap(record.createIconBitmap(mContext));
                }

                String slotIdString;
                if (SubscriptionManager.isValidSlotIndex(slotId)) {
                    slotIdString = Integer.toString(slotId);
                } else {
                    slotIdString = mContext.getResources().getString(R.string.unknown);
                }

                if (TextUtils.isEmpty(subDisplayName)) {
                    // Either the sub record is not there or it has an empty display name.
                    Log.w(this, "Could not get a display name for subid: %d", subId);
                    subDisplayName = mContext.getResources().getString(
                            R.string.sim_description_default, slotIdString);
                }

                // The label is user-visible so let's use the display name that the user may
                // have set in Settings->Sim cards.
                label = dummyPrefix + subDisplayName;
                description = dummyPrefix + mContext.getResources().getString(
                                R.string.sim_description_default, slotIdString);
            }

            // By default all SIM phone accounts can place emergency calls.
            int capabilities = PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION |
                    PhoneAccount.CAPABILITY_CALL_PROVIDER |
                    PhoneAccount.CAPABILITY_MULTI_USER;

            if (mContext.getResources().getBoolean(R.bool.config_pstnCanPlaceEmergencyCalls)) {
                capabilities |= PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS;
            }

            mIsVideoCapable = mPhone.isVideoEnabled();
            boolean isVideoEnabledByPlatform =
                    ImsManager.isVtEnabledByPlatform(mPhone.getContext());

            if (!mIsPrimaryUser) {
                Log.i(this, "Disabling video calling for secondary user.");
                mIsVideoCapable = false;
                isVideoEnabledByPlatform = false;
            }

            if (mIsVideoCapable) {
                capabilities |= PhoneAccount.CAPABILITY_VIDEO_CALLING;
            }

            if (isVideoEnabledByPlatform) {
                capabilities |= PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING;
            }

            mIsVideoPresenceSupported = isCarrierVideoPresenceSupported();
            if (mIsVideoCapable && mIsVideoPresenceSupported) {
                capabilities |= PhoneAccount.CAPABILITY_VIDEO_CALLING_RELIES_ON_PRESENCE;
            }

            if (mIsVideoCapable && isCarrierEmergencyVideoCallsAllowed()) {
                capabilities |= PhoneAccount.CAPABILITY_EMERGENCY_VIDEO_CALLING;
            }

            mIsVideoPauseSupported = isCarrierVideoPauseSupported();
            Bundle extras = new Bundle();
            if (isCarrierInstantLetteringSupported()) {
                capabilities |= PhoneAccount.CAPABILITY_CALL_SUBJECT;
                extras.putAll(getPhoneAccountExtras());
            }

            final boolean isHandoverFromSupported = mContext.getResources().getBoolean(
                    R.bool.config_support_handover_from);
            if (isHandoverFromSupported && !isEmergency) {
                // Only set the extra is handover is supported and this isn't the emergency-only
                // acct.
                extras.putBoolean(PhoneAccount.EXTRA_SUPPORTS_HANDOVER_FROM,
                        isHandoverFromSupported);
            }

            extras.putBoolean(PhoneAccount.EXTRA_SUPPORTS_VIDEO_CALLING_FALLBACK,
                    mContext.getResources()
                            .getBoolean(R.bool.config_support_video_calling_fallback));

            if (slotId != SubscriptionManager.INVALID_SIM_SLOT_INDEX) {
                extras.putString(PhoneAccount.EXTRA_SORT_ORDER,
                    String.valueOf(slotId));
            }

            mIsMergeCallSupported = isCarrierMergeCallSupported();
            mIsMergeImsCallSupported = isCarrierMergeImsCallSupported();
            mIsVideoConferencingSupported = isCarrierVideoConferencingSupported();
            mIsMergeOfWifiCallsAllowedWhenVoWifiOff =
                    isCarrierMergeOfWifiCallsAllowedWhenVoWifiOff();

            if (isEmergency && mContext.getResources().getBoolean(
                    R.bool.config_emergency_account_emergency_calls_only)) {
                capabilities |= PhoneAccount.CAPABILITY_EMERGENCY_CALLS_ONLY;
            }

            if (icon == null) {
                // TODO: Switch to using Icon.createWithResource() once that supports tinting.
                Resources res = mContext.getResources();
                Drawable drawable = res.getDrawable(DEFAULT_SIM_ICON, null);
                drawable.setTint(res.getColor(R.color.default_sim_icon_tint_color, null));
                drawable.setTintMode(PorterDuff.Mode.SRC_ATOP);

                int width = drawable.getIntrinsicWidth();
                int height = drawable.getIntrinsicHeight();
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);

                icon = Icon.createWithBitmap(bitmap);
            }

            // Check to see if the newly registered account should replace the old account.
            String groupId = "";
            String[] mergedImsis = mTelephonyManager.getMergedSubscriberIds();
            boolean isMergedSim = false;
            if (mergedImsis != null && subscriberId != null && !isEmergency) {
                for (String imsi : mergedImsis) {
                    if (imsi.equals(subscriberId)) {
                        isMergedSim = true;
                        break;
                    }
                }
            }
            if(isMergedSim) {
                groupId = GROUP_PREFIX + line1Number;
                Log.i(this, "Adding Merged Account with group: " + Log.pii(groupId));
            }

            PhoneAccount account = PhoneAccount.builder(phoneAccountHandle, label)
                    .setAddress(Uri.fromParts(PhoneAccount.SCHEME_TEL, line1Number, null))
                    .setSubscriptionAddress(
                            Uri.fromParts(PhoneAccount.SCHEME_TEL, subNumber, null))
                    .setCapabilities(capabilities)
                    .setIcon(icon)
                    .setHighlightColor(color)
                    .setShortDescription(description)
                    .setSupportedUriSchemes(Arrays.asList(
                            PhoneAccount.SCHEME_TEL, PhoneAccount.SCHEME_VOICEMAIL))
                    .setExtras(extras)
                    .setGroupId(groupId)
                    .build();

            // Register with Telecom and put into the account entry.
            mTelecomManager.registerPhoneAccount(account);

            return account;
        }

        public PhoneAccountHandle getPhoneAccountHandle() {
            return mAccount != null ? mAccount.getAccountHandle() : null;
        }

        /**
         * Determines from carrier configuration whether pausing of IMS video calls is supported.
         *
         * @return {@code true} if pausing IMS video calls is supported.
         */
        private boolean isCarrierVideoPauseSupported() {
            // Check if IMS video pause is supported.
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b != null &&
                    b.getBoolean(CarrierConfigManager.KEY_SUPPORT_PAUSE_IMS_VIDEO_CALLS_BOOL);
        }

        /**
         * Determines from carrier configuration whether RCS presence indication for video calls is
         * supported.
         *
         * @return {@code true} if RCS presence indication for video calls is supported.
         */
        private boolean isCarrierVideoPresenceSupported() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b != null &&
                    b.getBoolean(CarrierConfigManager.KEY_USE_RCS_PRESENCE_BOOL);
        }

        /**
         * Determines from carrier config whether instant lettering is supported.
         *
         * @return {@code true} if instant lettering is supported, {@code false} otherwise.
         */
        private boolean isCarrierInstantLetteringSupported() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b != null &&
                    b.getBoolean(CarrierConfigManager.KEY_CARRIER_INSTANT_LETTERING_AVAILABLE_BOOL);
        }

        /**
         * Determines from carrier config whether merging calls is supported.
         *
         * @return {@code true} if merging calls is supported, {@code false} otherwise.
         */
        private boolean isCarrierMergeCallSupported() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b != null &&
                    b.getBoolean(CarrierConfigManager.KEY_SUPPORT_CONFERENCE_CALL_BOOL);
        }

        /**
         * Determines from carrier config whether merging IMS calls is supported.
         *
         * @return {@code true} if merging IMS calls is supported, {@code false} otherwise.
         */
        private boolean isCarrierMergeImsCallSupported() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b.getBoolean(CarrierConfigManager.KEY_SUPPORT_IMS_CONFERENCE_CALL_BOOL);
        }

        /**
         * Determines from carrier config whether emergency video calls are supported.
         *
         * @return {@code true} if emergency video calls are allowed, {@code false} otherwise.
         */
        private boolean isCarrierEmergencyVideoCallsAllowed() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b != null &&
                    b.getBoolean(CarrierConfigManager.KEY_ALLOW_EMERGENCY_VIDEO_CALLS_BOOL);
        }

        /**
         * Determines from carrier config whether video conferencing is supported.
         *
         * @return {@code true} if video conferencing is supported, {@code false} otherwise.
         */
        private boolean isCarrierVideoConferencingSupported() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b != null &&
                    b.getBoolean(CarrierConfigManager.KEY_SUPPORT_VIDEO_CONFERENCE_CALL_BOOL);
        }

        /**
         * Determines from carrier config whether merging of wifi calls is allowed when VoWIFI is
         * turned off.
         *
         * @return {@code true} merging of wifi calls when VoWIFI is disabled should be prevented,
         *      {@code false} otherwise.
         */
        private boolean isCarrierMergeOfWifiCallsAllowedWhenVoWifiOff() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            return b != null && b.getBoolean(
                    CarrierConfigManager.KEY_ALLOW_MERGE_WIFI_CALLS_WHEN_VOWIFI_OFF_BOOL);
        }

        /**
         * Where a device supports instant lettering and call subjects, retrieves the necessary
         * PhoneAccount extras for those features.
         * @return The {@link PhoneAccount} extras associated with the current subscription.
         */
        private Bundle getPhoneAccountExtras() {
            PersistableBundle b =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());

            int instantLetteringMaxLength = b.getInt(
                    CarrierConfigManager.KEY_CARRIER_INSTANT_LETTERING_LENGTH_LIMIT_INT);
            String instantLetteringEncoding = b.getString(
                    CarrierConfigManager.KEY_CARRIER_INSTANT_LETTERING_ENCODING_STRING);
            Bundle phoneAccountExtras = new Bundle();
            phoneAccountExtras.putInt(PhoneAccount.EXTRA_CALL_SUBJECT_MAX_LENGTH,
                    instantLetteringMaxLength);
            phoneAccountExtras.putString(PhoneAccount.EXTRA_CALL_SUBJECT_CHARACTER_ENCODING,
                    instantLetteringEncoding);
            return phoneAccountExtras;
        }

        /**
         * Receives callback from {@link PstnPhoneCapabilitiesNotifier} when the video capabilities
         * have changed.
         *
         * @param isVideoCapable {@code true} if video is capable.
         */
        @Override
        public void onVideoCapabilitiesChanged(boolean isVideoCapable) {
            mIsVideoCapable = isVideoCapable;
            synchronized (mAccountsLock) {
                if (!mAccounts.contains(this)) {
                    // Account has already been torn down, don't try to register it again.
                    // This handles the case where teardown has already happened, and we got a video
                    // update that lost the race for the mAccountsLock.  In such a scenario by the
                    // time we get here, the original phone account could have been torn down.
                    return;
                }
                mAccount = registerPstnPhoneAccount(mIsEmergency, mIsDummy);
            }
        }

        /**
         * Indicates whether this account supports pausing video calls.
         * @return {@code true} if the account supports pausing video calls, {@code false}
         * otherwise.
         */
        public boolean isVideoPauseSupported() {
            return mIsVideoCapable && mIsVideoPauseSupported;
        }

        /**
         * Indicates whether this account supports merging calls (i.e. conferencing).
         * @return {@code true} if the account supports merging calls, {@code false} otherwise.
         */
        public boolean isMergeCallSupported() {
            return mIsMergeCallSupported;
        }

        /**
         * Indicates whether this account supports merging IMS calls (i.e. conferencing).
         * @return {@code true} if the account supports merging IMS calls, {@code false} otherwise.
         */
        public boolean isMergeImsCallSupported() {
            return mIsMergeImsCallSupported;
        }

        /**
         * Indicates whether this account supports video conferencing.
         * @return {@code true} if the account supports video conferencing, {@code false} otherwise.
         */
        public boolean isVideoConferencingSupported() {
            return mIsVideoConferencingSupported;
        }

        /**
         * Indicate whether this account allow merging of wifi calls when VoWIFI is off.
         * @return {@code true} if allowed, {@code false} otherwise.
         */
        public boolean isMergeOfWifiCallsAllowedWhenVoWifiOff() {
            return mIsMergeOfWifiCallsAllowedWhenVoWifiOff;
        }
    }

    private OnSubscriptionsChangedListener mOnSubscriptionsChangedListener =
            new OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            // Any time the SubscriptionInfo changes...rerun the setup
            tearDownAccounts();
            setupAccounts();
        }
    };

    private final BroadcastReceiver mUserSwitchedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(this, "User changed, re-registering phone accounts.");

            int userHandleId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, 0);
            UserHandle currentUserHandle = new UserHandle(userHandleId);
            mIsPrimaryUser = UserManager.get(mContext).getPrimaryUser().getUserHandle()
                    .equals(currentUserHandle);

            // Any time the user changes, re-register the accounts.
            tearDownAccounts();
            setupAccounts();
        }
    };

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            int newState = serviceState.getState();
            if (newState == ServiceState.STATE_IN_SERVICE && mServiceState != newState) {
                tearDownAccounts();
                setupAccounts();
            }
            mServiceState = newState;
        }
    };

    private static TelecomAccountRegistry sInstance;
    private final Context mContext;
    private final TelecomManager mTelecomManager;
    private final TelephonyManager mTelephonyManager;
    private final SubscriptionManager mSubscriptionManager;
    private List<AccountEntry> mAccounts = new LinkedList<AccountEntry>();
    private Object mAccountsLock = new Object();
    private int mServiceState = ServiceState.STATE_POWER_OFF;
    private boolean mIsPrimaryUser = true;

    // TODO: Remove back-pointer from app singleton to Service, since this is not a preferred
    // pattern; redesign. This was added to fix a late release bug.
    private TelephonyConnectionService mTelephonyConnectionService;

    TelecomAccountRegistry(Context context) {
        mContext = context;
        mTelecomManager = TelecomManager.from(context);
        mTelephonyManager = TelephonyManager.from(context);
        mSubscriptionManager = SubscriptionManager.from(context);
    }

    public static synchronized final TelecomAccountRegistry getInstance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new TelecomAccountRegistry(context);
        }
        return sInstance;
    }

    void setTelephonyConnectionService(TelephonyConnectionService telephonyConnectionService) {
        this.mTelephonyConnectionService = telephonyConnectionService;
    }

    TelephonyConnectionService getTelephonyConnectionService() {
        return mTelephonyConnectionService;
    }

    /**
     * Determines if the {@link AccountEntry} associated with a {@link PhoneAccountHandle} supports
     * pausing video calls.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if video pausing is supported.
     */
    public boolean isVideoPauseSupported(PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getPhoneAccountHandle().equals(handle)) {
                    return entry.isVideoPauseSupported();
                }
            }
        }
        return false;
    }

    /**
     * Determines if the {@link AccountEntry} associated with a {@link PhoneAccountHandle} supports
     * merging calls.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if merging calls is supported.
     */
    boolean isMergeCallSupported(PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getPhoneAccountHandle().equals(handle)) {
                    return entry.isMergeCallSupported();
                }
            }
        }
        return false;
    }

    /**
     * Determines if the {@link AccountEntry} associated with a {@link PhoneAccountHandle} supports
     * video conferencing.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if video conferencing is supported.
     */
    boolean isVideoConferencingSupported(PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getPhoneAccountHandle().equals(handle)) {
                    return entry.isVideoConferencingSupported();
                }
            }
        }
        return false;
    }

    /**
     * Determines if the {@link AccountEntry} associated with a {@link PhoneAccountHandle} allows
     * merging of wifi calls when VoWIFI is disabled.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if merging of wifi calls is allowed when VoWIFI is disabled.
     */
    boolean isMergeOfWifiCallsAllowedWhenVoWifiOff(final PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            Optional<AccountEntry> result = mAccounts.stream().filter(
                    entry -> entry.getPhoneAccountHandle().equals(handle)).findFirst();

            if (result.isPresent()) {
                return result.get().isMergeOfWifiCallsAllowedWhenVoWifiOff();
            } else {
                return false;
            }
        }
    }

    /**
     * Determines if the {@link AccountEntry} associated with a {@link PhoneAccountHandle} supports
     * merging IMS calls.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if merging IMS calls is supported.
     */
    boolean isMergeImsCallSupported(PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getPhoneAccountHandle().equals(handle)) {
                    return entry.isMergeImsCallSupported();
                }
            }
        }
        return false;
    }

    /**
     * @return Reference to the {@code TelecomAccountRegistry}'s subscription manager.
     */
    SubscriptionManager getSubscriptionManager() {
        return mSubscriptionManager;
    }

    /**
     * Returns the address (e.g. the phone number) associated with a subscription.
     *
     * @param handle The phone account handle to find the subscription address for.
     * @return The address.
     */
    Uri getAddress(PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getPhoneAccountHandle().equals(handle)) {
                    return entry.mAccount.getAddress();
                }
            }
        }
        return null;
    }

    /**
     * Sets up all the phone accounts for SIMs on first boot.
     */
    void setupOnBoot() {
        // TODO: When this object "finishes" we should unregister by invoking
        // SubscriptionManager.getInstance(mContext).unregister(mOnSubscriptionsChangedListener);
        // This is not strictly necessary because it will be unregistered if the
        // notification fails but it is good form.

        // Register for SubscriptionInfo list changes which is guaranteed
        // to invoke onSubscriptionsChanged the first time.
        SubscriptionManager.from(mContext).addOnSubscriptionsChangedListener(
                mOnSubscriptionsChangedListener);

        // We also need to listen for changes to the service state (e.g. emergency -> in service)
        // because this could signal a removal or addition of a SIM in a single SIM phone.
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);

        // Listen for user switches.  When the user switches, we need to ensure that if the current
        // use is not the primary user we disable video calling.
        mContext.registerReceiver(mUserSwitchedReceiver,
                new IntentFilter(Intent.ACTION_USER_SWITCHED));
    }

    /**
     * Determines if the list of {@link AccountEntry}(s) contains an {@link AccountEntry} with a
     * specified {@link PhoneAccountHandle}.
     *
     * @param handle The {@link PhoneAccountHandle}.
     * @return {@code True} if an entry exists.
     */
    boolean hasAccountEntryForPhoneAccount(PhoneAccountHandle handle) {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                if (entry.getPhoneAccountHandle().equals(handle)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Un-registers any {@link PhoneAccount}s which are no longer present in the list
     * {@code AccountEntry}(s).
     */
    private void cleanupPhoneAccounts() {
        ComponentName telephonyComponentName =
                new ComponentName(mContext, TelephonyConnectionService.class);
        // This config indicates whether the emergency account was flagged as emergency calls only
        // in which case we need to consider all phone accounts, not just the call capable ones.
        final boolean emergencyCallsOnlyEmergencyAccount = mContext.getResources().getBoolean(
                R.bool.config_emergency_account_emergency_calls_only);
        List<PhoneAccountHandle> accountHandles = emergencyCallsOnlyEmergencyAccount
                ? mTelecomManager.getAllPhoneAccountHandles()
                : mTelecomManager.getCallCapablePhoneAccounts(true /* includeDisabled */);

        for (PhoneAccountHandle handle : accountHandles) {
            if (telephonyComponentName.equals(handle.getComponentName()) &&
                    !hasAccountEntryForPhoneAccount(handle)) {
                Log.i(this, "Unregistering phone account %s.", handle);
                mTelecomManager.unregisterPhoneAccount(handle);
            }
        }
    }

    private void setupAccounts() {
        // Go through SIM-based phones and register ourselves -- registering an existing account
        // will cause the existing entry to be replaced.
        Phone[] phones = PhoneFactory.getPhones();
        Log.d(this, "Found %d phones.  Attempting to register.", phones.length);

        final boolean phoneAccountsEnabled = mContext.getResources().getBoolean(
                R.bool.config_pstn_phone_accounts_enabled);

        synchronized (mAccountsLock) {
            if (phoneAccountsEnabled) {
                for (Phone phone : phones) {
                    int subscriptionId = phone.getSubId();
                    Log.d(this, "Phone with subscription id %d", subscriptionId);
                    // setupAccounts can be called multiple times during service changes. Don't add an
                    // account if the Icc has not been set yet.
                    if (subscriptionId >= 0 && phone.getFullIccSerialNumber() != null) {
                        mAccounts.add(new AccountEntry(phone, false /* emergency */,
                                false /* isDummy */));
                    }
                }
            }

            // If we did not list ANY accounts, we need to provide a "default" SIM account
            // for emergency numbers since no actual SIM is needed for dialing emergency
            // numbers but a phone account is.
            if (mAccounts.isEmpty()) {
                mAccounts.add(new AccountEntry(PhoneFactory.getDefaultPhone(), true /* emergency */,
                        false /* isDummy */));
            }

            // Add a fake account entry.
            if (DBG && phones.length > 0 && "TRUE".equals(System.getProperty("dummy_sim"))) {
                mAccounts.add(new AccountEntry(phones[0], false /* emergency */,
                        true /* isDummy */));
            }
        }

        // Clean up any PhoneAccounts that are no longer relevant
        cleanupPhoneAccounts();

        // At some point, the phone account ID was switched from the subId to the iccId.
        // If there is a default account, check if this is the case, and upgrade the default account
        // from using the subId to iccId if so.
        PhoneAccountHandle defaultPhoneAccount =
                mTelecomManager.getUserSelectedOutgoingPhoneAccount();
        ComponentName telephonyComponentName =
                new ComponentName(mContext, TelephonyConnectionService.class);

        if (defaultPhoneAccount != null &&
                telephonyComponentName.equals(defaultPhoneAccount.getComponentName()) &&
                !hasAccountEntryForPhoneAccount(defaultPhoneAccount)) {

            String phoneAccountId = defaultPhoneAccount.getId();
            if (!TextUtils.isEmpty(phoneAccountId) && TextUtils.isDigitsOnly(phoneAccountId)) {
                PhoneAccountHandle upgradedPhoneAccount =
                        PhoneUtils.makePstnPhoneAccountHandle(
                                PhoneGlobals.getPhone(Integer.parseInt(phoneAccountId)));

                if (hasAccountEntryForPhoneAccount(upgradedPhoneAccount)) {
                    mTelecomManager.setUserSelectedOutgoingPhoneAccount(upgradedPhoneAccount);
                }
            }
        }
    }

    private void tearDownAccounts() {
        synchronized (mAccountsLock) {
            for (AccountEntry entry : mAccounts) {
                entry.teardown();
            }
            mAccounts.clear();
        }
    }
}
