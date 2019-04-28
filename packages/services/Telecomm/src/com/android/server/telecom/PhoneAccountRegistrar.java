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

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.AsyncTask;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.telecom.CallAudioState;
import android.telecom.ConnectionService;
import android.telecom.DefaultDialerManager;
import android.telecom.Log;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AtomicFile;
import android.util.Base64;
import android.util.Xml;

// TODO: Needed for move to system service: import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Integer;
import java.lang.SecurityException;
import java.lang.String;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Handles writing and reading PhoneAccountHandle registration entries. This is a simple verbatim
 * delegate for all the account handling methods on {@link android.telecom.TelecomManager} as
 * implemented in {@link TelecomServiceImpl}, with the notable exception that
 * {@link TelecomServiceImpl} is responsible for security checking to make sure that the caller has
 * proper authority over the {@code ComponentName}s they are declaring in their
 * {@code PhoneAccountHandle}s.
 *
 *
 *  -- About Users and Phone Accounts --
 *
 * We store all phone accounts for all users in a single place, which means that there are three
 * users that we have to deal with in code:
 * 1) The Android User that is currently active on the device.
 * 2) The user which owns/registers the phone account.
 * 3) The user running the app that is requesting the phone account information.
 *
 * For example, I have a device with 2 users, primary (A) and secondary (B), and the secondary user
 * has a work profile running as another user (B2). Each user/profile only have the visibility of
 * phone accounts owned by them. Lets say, user B (settings) is requesting a list of phone accounts,
 * and the list only contains phone accounts owned by user B and accounts with
 * {@link PhoneAccount#CAPABILITY_MULTI_USER}.
 *
 * In practice, (2) is stored with the phone account handle and is part of the handle's ID. (1) is
 * saved in {@link #mCurrentUserHandle} and (3) we get from Binder.getCallingUser(). We check these
 * users for visibility before returning any phone accounts.
 */
public class PhoneAccountRegistrar {

    public static final PhoneAccountHandle NO_ACCOUNT_SELECTED =
            new PhoneAccountHandle(new ComponentName("null", "null"), "NO_ACCOUNT_SELECTED");

    public abstract static class Listener {
        public void onAccountsChanged(PhoneAccountRegistrar registrar) {}
        public void onDefaultOutgoingChanged(PhoneAccountRegistrar registrar) {}
        public void onSimCallManagerChanged(PhoneAccountRegistrar registrar) {}
        public void onPhoneAccountRegistered(PhoneAccountRegistrar registrar,
                                             PhoneAccountHandle handle) {}
        public void onPhoneAccountUnRegistered(PhoneAccountRegistrar registrar,
                                             PhoneAccountHandle handle) {}
    }

    /**
     * Abstracts away dependency on the {@link PackageManager} required to fetch the label for an
     * app.
     */
    public interface AppLabelProxy {
        CharSequence getAppLabel(String packageName);
    }

    private static final String FILE_NAME = "phone-account-registrar-state.xml";
    @VisibleForTesting
    public static final int EXPECTED_STATE_VERSION = 9;

    /** Keep in sync with the same in SipSettings.java */
    private static final String SIP_SHARED_PREFERENCES = "SIP_PREFERENCES";

    private final List<Listener> mListeners = new CopyOnWriteArrayList<>();
    private final AtomicFile mAtomicFile;
    private final Context mContext;
    private final UserManager mUserManager;
    private final SubscriptionManager mSubscriptionManager;
    private final DefaultDialerCache mDefaultDialerCache;
    private final AppLabelProxy mAppLabelProxy;
    private State mState;
    private UserHandle mCurrentUserHandle;
    private interface PhoneAccountRegistrarWriteLock {}
    private final PhoneAccountRegistrarWriteLock mWriteLock =
            new PhoneAccountRegistrarWriteLock() {};

    @VisibleForTesting
    public PhoneAccountRegistrar(Context context, DefaultDialerCache defaultDialerCache,
                                 AppLabelProxy appLabelProxy) {
        this(context, FILE_NAME, defaultDialerCache, appLabelProxy);
    }

    @VisibleForTesting
    public PhoneAccountRegistrar(Context context, String fileName,
            DefaultDialerCache defaultDialerCache, AppLabelProxy appLabelProxy) {

        mAtomicFile = new AtomicFile(new File(context.getFilesDir(), fileName));

        mState = new State();
        mContext = context;
        mUserManager = UserManager.get(context);
        mDefaultDialerCache = defaultDialerCache;
        mSubscriptionManager = SubscriptionManager.from(mContext);
        mAppLabelProxy = appLabelProxy;
        mCurrentUserHandle = Process.myUserHandle();
        read();
    }

    /**
     * Retrieves the subscription id for a given phone account if it exists. Subscription ids
     * apply only to PSTN/SIM card phone accounts so all other accounts should not have a
     * subscription id.
     * @param accountHandle The handle for the phone account for which to retrieve the
     * subscription id.
     * @return The value of the subscription id or -1 if it does not exist or is not valid.
     */
    public int getSubscriptionIdForPhoneAccount(PhoneAccountHandle accountHandle) {
        PhoneAccount account = getPhoneAccountUnchecked(accountHandle);

        if (account != null && account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
            TelephonyManager tm =
                    (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            return tm.getSubIdForPhoneAccount(account);
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    /**
     * Retrieves the default outgoing phone account supporting the specified uriScheme. Note that if
     * {@link #mCurrentUserHandle} does not have visibility into the current default, {@code null}
     * will be returned.
     *
     * @param uriScheme The URI scheme for the outgoing call.
     * @return The {@link PhoneAccountHandle} to use.
     */
    public PhoneAccountHandle getOutgoingPhoneAccountForScheme(String uriScheme,
            UserHandle userHandle) {
        final PhoneAccountHandle userSelected = getUserSelectedOutgoingPhoneAccount(userHandle);

        if (userSelected != null) {
            // If there is a default PhoneAccount, ensure it supports calls to handles with the
            // specified uriScheme.
            final PhoneAccount userSelectedAccount = getPhoneAccountUnchecked(userSelected);
            if (userSelectedAccount.supportsUriScheme(uriScheme)) {
                return userSelected;
            }
        }

        List<PhoneAccountHandle> outgoing = getCallCapablePhoneAccounts(uriScheme, false,
                userHandle);
        switch (outgoing.size()) {
            case 0:
                // There are no accounts, so there can be no default
                return null;
            case 1:
                // There is only one account, which is by definition the default.
                return outgoing.get(0);
            default:
                // There are multiple accounts with no selected default
                return null;
        }
    }

    public PhoneAccountHandle getOutgoingPhoneAccountForSchemeOfCurrentUser(String uriScheme) {
        return getOutgoingPhoneAccountForScheme(uriScheme, mCurrentUserHandle);
    }

    /**
     * @return The user-selected outgoing {@link PhoneAccount}, or null if it hasn't been set (or
     *      if it was set by another user).
     */
    @VisibleForTesting
    public PhoneAccountHandle getUserSelectedOutgoingPhoneAccount(UserHandle userHandle) {
        if (userHandle == null) {
            return null;
        }
        DefaultPhoneAccountHandle defaultPhoneAccountHandle = mState.defaultOutgoingAccountHandles
                .get(userHandle);
        if (defaultPhoneAccountHandle == null) {
            return null;
        }
        // Make sure the account is still registered and owned by the user.
        PhoneAccount account = getPhoneAccount(defaultPhoneAccountHandle.phoneAccountHandle,
                userHandle);

        if (account != null) {
            return defaultPhoneAccountHandle.phoneAccountHandle;
        }
        return null;
    }

    /**
     * @return The {@link DefaultPhoneAccountHandle} containing the user-selected default calling
     * account and group Id for the {@link UserHandle} specified.
     */
    private DefaultPhoneAccountHandle getUserSelectedDefaultPhoneAccount(UserHandle userHandle) {
        if (userHandle == null) {
            return null;
        }
        DefaultPhoneAccountHandle defaultPhoneAccountHandle = mState.defaultOutgoingAccountHandles
                .get(userHandle);
        if (defaultPhoneAccountHandle == null) {
            return null;
        }

        return defaultPhoneAccountHandle;
    }

    /**
     * @return The currently registered PhoneAccount in Telecom that has the same group Id.
     */
    private PhoneAccount getPhoneAccountByGroupId(String groupId, ComponentName groupComponentName,
            UserHandle userHandle, PhoneAccountHandle excludePhoneAccountHandle) {
        if (groupId == null || groupId.isEmpty() || userHandle == null) {
            return null;
        }
        // Get the PhoneAccount with the same group Id (and same ComponentName) that is not the
        // newAccount that was just added
        List<PhoneAccount> accounts = getAllPhoneAccounts(userHandle).stream()
                .filter(account -> groupId.equals(account.getGroupId()) &&
                        !account.getAccountHandle().equals(excludePhoneAccountHandle) &&
                        Objects.equals(account.getAccountHandle().getComponentName(),
                                groupComponentName))
                .collect(Collectors.toList());
        // There should be one or no PhoneAccounts with the same group Id
        if (accounts.size() > 1) {
            Log.w(this, "Found multiple PhoneAccounts registered to the same Group Id!");
        }
        return accounts.isEmpty() ? null : accounts.get(0);
    }

    /**
     * Sets the phone account with which to place all calls by default. Set by the user
     * within phone settings.
     */
    public void setUserSelectedOutgoingPhoneAccount(PhoneAccountHandle accountHandle,
            UserHandle userHandle) {
        if (userHandle == null) {
            return;
        }
        if (accountHandle == null) {
            // Asking to clear the default outgoing is a valid request
            mState.defaultOutgoingAccountHandles.remove(userHandle);
        } else {
            PhoneAccount account = getPhoneAccount(accountHandle, userHandle);
            if (account == null) {
                Log.w(this, "Trying to set nonexistent default outgoing %s",
                        accountHandle);
                return;
            }

            if (!account.hasCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)) {
                Log.w(this, "Trying to set non-call-provider default outgoing %s",
                        accountHandle);
                return;
            }

            if (account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
                // If the account selected is a SIM account, propagate down to the subscription
                // record.
                int subId = getSubscriptionIdForPhoneAccount(accountHandle);
                mSubscriptionManager.setDefaultVoiceSubId(subId);
            }

            mState.defaultOutgoingAccountHandles
                    .put(userHandle, new DefaultPhoneAccountHandle(userHandle, accountHandle,
                            account.getGroupId()));
        }

        write();
        fireDefaultOutgoingChanged();
    }

    boolean isUserSelectedSmsPhoneAccount(PhoneAccountHandle accountHandle) {
        return getSubscriptionIdForPhoneAccount(accountHandle) ==
                SubscriptionManager.getDefaultSmsSubscriptionId();
    }

    public ComponentName getSystemSimCallManagerComponent() {
        String defaultSimCallManager = null;
        CarrierConfigManager configManager = (CarrierConfigManager) mContext.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
        PersistableBundle configBundle = configManager.getConfig();
        if (configBundle != null) {
            defaultSimCallManager = configBundle.getString(
                    CarrierConfigManager.KEY_DEFAULT_SIM_CALL_MANAGER_STRING);
        }
        return TextUtils.isEmpty(defaultSimCallManager)
            ?  null : ComponentName.unflattenFromString(defaultSimCallManager);
    }

    public PhoneAccountHandle getSimCallManagerOfCurrentUser() {
        return getSimCallManager(mCurrentUserHandle);
    }

    /**
     * Returns the {@link PhoneAccountHandle} corresponding to the currently active SIM Call
     * Manager. SIM Call Manager returned corresponds to the following priority order:
     * 1. If a SIM Call Manager {@link PhoneAccount} is registered for the same package as the
     * default dialer, then that one is returned.
     * 2. If there is a SIM Call Manager {@link PhoneAccount} registered which matches the
     * carrier configuration's default, then that one is returned.
     * 3. Otherwise, we return null.
     */
    public PhoneAccountHandle getSimCallManager(UserHandle userHandle) {
        // Get the default dialer in case it has a connection manager associated with it.
        String dialerPackage = mDefaultDialerCache
                .getDefaultDialerApplication(userHandle.getIdentifier());

        // Check carrier config.
        ComponentName systemSimCallManagerComponent = getSystemSimCallManagerComponent();

        PhoneAccountHandle dialerSimCallManager = null;
        PhoneAccountHandle systemSimCallManager = null;

        if (!TextUtils.isEmpty(dialerPackage) || systemSimCallManagerComponent != null) {
            // loop through and look for any connection manager in the same package.
            List<PhoneAccountHandle> allSimCallManagers = getPhoneAccountHandles(
                    PhoneAccount.CAPABILITY_CONNECTION_MANAGER, null, null,
                    true /* includeDisabledAccounts */, userHandle);
            for (PhoneAccountHandle accountHandle : allSimCallManagers) {
                ComponentName component = accountHandle.getComponentName();

                // Store the system connection manager if found
                if (systemSimCallManager == null
                        && Objects.equals(component, systemSimCallManagerComponent)
                        && !resolveComponent(accountHandle).isEmpty()) {
                    systemSimCallManager = accountHandle;

                // Store the dialer connection manager if found
                } else if (dialerSimCallManager == null
                        && Objects.equals(component.getPackageName(), dialerPackage)
                        && !resolveComponent(accountHandle).isEmpty()) {
                    dialerSimCallManager = accountHandle;
                }
            }
        }

        PhoneAccountHandle retval = dialerSimCallManager != null ?
                dialerSimCallManager : systemSimCallManager;

        Log.i(this, "SimCallManager queried, returning: %s", retval);

        return retval;
    }

    /**
     * If it is a outgoing call, sim call manager of call-initiating user is returned.
     * Otherwise, we return the sim call manager of the user associated with the
     * target phone account.
     * @return phone account handle of sim call manager based on the ongoing call.
     */
    public PhoneAccountHandle getSimCallManagerFromCall(Call call) {
        if (call == null) {
            return null;
        }
        UserHandle userHandle = call.getInitiatingUser();
        if (userHandle == null) {
            userHandle = call.getTargetPhoneAccount().getUserHandle();
        }
        return getSimCallManager(userHandle);
    }

    /**
     * Update the current UserHandle to track when users are switched. This will allow the
     * PhoneAccountRegistar to self-filter the PhoneAccounts to make sure we don't leak anything
     * across users.
     * We cannot simply check the calling user because that would always return the primary user for
     * all invocations originating with the system process.
     *
     * @param userHandle The {@link UserHandle}, as delivered by
     *          {@link Intent#ACTION_USER_SWITCHED}.
     */
    public void setCurrentUserHandle(UserHandle userHandle) {
        if (userHandle == null) {
            Log.d(this, "setCurrentUserHandle, userHandle = null");
            userHandle = Process.myUserHandle();
        }
        Log.d(this, "setCurrentUserHandle, %s", userHandle);
        mCurrentUserHandle = userHandle;
    }

    /**
     * @return {@code true} if the phone account was successfully enabled/disabled, {@code false}
     *         otherwise.
     */
    public boolean enablePhoneAccount(PhoneAccountHandle accountHandle, boolean isEnabled) {
        PhoneAccount account = getPhoneAccountUnchecked(accountHandle);
        Log.i(this, "Phone account %s %s.", accountHandle, isEnabled ? "enabled" : "disabled");
        if (account == null) {
            Log.w(this, "Could not find account to enable: " + accountHandle);
            return false;
        } else if (account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
            // We never change the enabled state of SIM-based accounts.
            Log.w(this, "Could not change enable state of SIM account: " + accountHandle);
            return false;
        }

        if (account.isEnabled() != isEnabled) {
            account.setIsEnabled(isEnabled);
            if (!isEnabled) {
                // If the disabled account is the default, remove it.
                removeDefaultPhoneAccountHandle(accountHandle);
            }
            write();
            fireAccountsChanged();
        }
        return true;
    }

    private void removeDefaultPhoneAccountHandle(PhoneAccountHandle phoneAccountHandle) {
        Iterator<Map.Entry<UserHandle, DefaultPhoneAccountHandle>> iterator =
                mState.defaultOutgoingAccountHandles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UserHandle, DefaultPhoneAccountHandle> entry = iterator.next();
            if (phoneAccountHandle.equals(entry.getValue().phoneAccountHandle)) {
                iterator.remove();
            }
        }
    }

    private boolean isVisibleForUser(PhoneAccount account, UserHandle userHandle,
            boolean acrossProfiles) {
        if (account == null) {
            return false;
        }

        if (userHandle == null) {
            Log.w(this, "userHandle is null in isVisibleForUser");
            return false;
        }

        // If this PhoneAccount has CAPABILITY_MULTI_USER, it should be visible to all users and
        // all profiles. Only Telephony and SIP accounts should have this capability.
        if (account.hasCapabilities(PhoneAccount.CAPABILITY_MULTI_USER)) {
            return true;
        }

        UserHandle phoneAccountUserHandle = account.getAccountHandle().getUserHandle();
        if (phoneAccountUserHandle == null) {
            return false;
        }

        if (mCurrentUserHandle == null) {
            // In case we need to have emergency phone calls from the lock screen.
            Log.d(this, "Current user is null; assuming true");
            return true;
        }

        if (acrossProfiles) {
            return UserManager.get(mContext).isSameProfileGroup(userHandle.getIdentifier(),
                    phoneAccountUserHandle.getIdentifier());
        } else {
            return phoneAccountUserHandle.equals(userHandle);
        }
    }

    private List<ResolveInfo> resolveComponent(PhoneAccountHandle phoneAccountHandle) {
        return resolveComponent(phoneAccountHandle.getComponentName(),
                phoneAccountHandle.getUserHandle());
    }

    private List<ResolveInfo> resolveComponent(ComponentName componentName,
            UserHandle userHandle) {
        PackageManager pm = mContext.getPackageManager();
        Intent intent = new Intent(ConnectionService.SERVICE_INTERFACE);
        intent.setComponent(componentName);
        try {
            if (userHandle != null) {
                return pm.queryIntentServicesAsUser(intent, 0, userHandle.getIdentifier());
            } else {
                return pm.queryIntentServices(intent, 0);
            }
        } catch (SecurityException e) {
            Log.e(this, e, "%s is not visible for the calling user", componentName);
            return Collections.EMPTY_LIST;
        }
    }

    /**
     * Retrieves a list of all {@link PhoneAccountHandle}s registered.
     * Only returns accounts which are enabled.
     *
     * @return The list of {@link PhoneAccountHandle}s.
     */
    public List<PhoneAccountHandle> getAllPhoneAccountHandles(UserHandle userHandle) {
        return getPhoneAccountHandles(0, null, null, false, userHandle);
    }

    public List<PhoneAccount> getAllPhoneAccounts(UserHandle userHandle) {
        return getPhoneAccounts(0, null, null, false, userHandle);
    }

    public List<PhoneAccount> getAllPhoneAccountsOfCurrentUser() {
        return getAllPhoneAccounts(mCurrentUserHandle);
    }

    /**
     * Retrieves a list of all phone account call provider phone accounts supporting the
     * specified URI scheme.
     *
     * @param uriScheme The URI scheme.
     * @return The phone account handles.
     */
    public List<PhoneAccountHandle> getCallCapablePhoneAccounts(
            String uriScheme, boolean includeDisabledAccounts, UserHandle userHandle) {
        return getPhoneAccountHandles(
                PhoneAccount.CAPABILITY_CALL_PROVIDER,
                PhoneAccount.CAPABILITY_EMERGENCY_CALLS_ONLY /*excludedCapabilities*/,
                uriScheme, null, includeDisabledAccounts, userHandle);
    }

    /**
     * Retrieves a list of all phone accounts which have
     * {@link PhoneAccount#CAPABILITY_SELF_MANAGED}.
     * <p>
     * Returns only the {@link PhoneAccount}s which are enabled as self-managed accounts are
     * automatically enabled by default (see {@link #registerPhoneAccount(PhoneAccount)}).
     *
     * @param userHandle User handle of phone account owner.
     * @return The phone account handles.
     */
    public List<PhoneAccountHandle> getSelfManagedPhoneAccounts(UserHandle userHandle) {
        return getPhoneAccountHandles(
                PhoneAccount.CAPABILITY_SELF_MANAGED,
                PhoneAccount.CAPABILITY_EMERGENCY_CALLS_ONLY /* excludedCapabilities */,
                null /* uriScheme */, null /* packageName */, false /* includeDisabledAccounts */,
                userHandle);
    }

    public List<PhoneAccountHandle> getCallCapablePhoneAccountsOfCurrentUser(
            String uriScheme, boolean includeDisabledAccounts) {
        return getCallCapablePhoneAccounts(uriScheme, includeDisabledAccounts, mCurrentUserHandle);
    }

    /**
     * Retrieves a list of all the SIM-based phone accounts.
     */
    public List<PhoneAccountHandle> getSimPhoneAccounts(UserHandle userHandle) {
        return getPhoneAccountHandles(
                PhoneAccount.CAPABILITY_CALL_PROVIDER | PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION,
                null, null, false, userHandle);
    }

    public List<PhoneAccountHandle> getSimPhoneAccountsOfCurrentUser() {
        return getSimPhoneAccounts(mCurrentUserHandle);
    }

        /**
         * Retrieves a list of all phone accounts registered by a specified package.
         *
         * @param packageName The name of the package that registered the phone accounts.
         * @return The phone account handles.
         */
    public List<PhoneAccountHandle> getPhoneAccountsForPackage(String packageName,
            UserHandle userHandle) {
        return getPhoneAccountHandles(0, null, packageName, false, userHandle);
    }

    // TODO: Should we implement an artificial limit for # of accounts associated with a single
    // ComponentName?
    public void registerPhoneAccount(PhoneAccount account) {
        // Enforce the requirement that a connection service for a phone account has the correct
        // permission.
        if (!phoneAccountRequiresBindPermission(account.getAccountHandle())) {
            Log.w(this,
                    "Phone account %s does not have BIND_TELECOM_CONNECTION_SERVICE permission.",
                    account.getAccountHandle());
            throw new SecurityException("PhoneAccount connection service requires "
                    + "BIND_TELECOM_CONNECTION_SERVICE permission.");
        }

        addOrReplacePhoneAccount(account);
    }

    /**
     * Adds a {@code PhoneAccount}, replacing an existing one if found.
     *
     * @param account The {@code PhoneAccount} to add or replace.
     */
    private void addOrReplacePhoneAccount(PhoneAccount account) {
        Log.d(this, "addOrReplacePhoneAccount(%s -> %s)",
                account.getAccountHandle(), account);

        // Start _enabled_ property as false.
        // !!! IMPORTANT !!! It is important that we do not read the enabled state that the
        // source app provides or else an third party app could enable itself.
        boolean isEnabled = false;
        boolean isNewAccount;

        PhoneAccount oldAccount = getPhoneAccountUnchecked(account.getAccountHandle());
        if (oldAccount != null) {
            mState.accounts.remove(oldAccount);
            isEnabled = oldAccount.isEnabled();
            Log.i(this, "Modify account: %s", getAccountDiffString(account, oldAccount));
            isNewAccount = false;
        } else {
            Log.i(this, "New phone account registered: " + account);
            isNewAccount = true;
        }

        // When registering a self-managed PhoneAccount we enforce the rule that the label that the
        // app uses is also its phone account label.  Also ensure it does not attempt to declare
        // itself as a sim acct, call manager or call provider.
        if (account.hasCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)) {
            // Turn off bits we don't want to be able to set (TelecomServiceImpl protects against
            // this but we'll also prevent it from happening here, just to be safe).
            int newCapabilities = account.getCapabilities() &
                    ~(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_CONNECTION_MANAGER |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION);

            // Ensure name is correct.
            CharSequence newLabel = mAppLabelProxy.getAppLabel(
                    account.getAccountHandle().getComponentName().getPackageName());

            account = account.toBuilder()
                    .setLabel(newLabel)
                    .setCapabilities(newCapabilities)
                    .build();
        }

        mState.accounts.add(account);
        // Set defaults and replace based on the group Id.
        maybeReplaceOldAccount(account);
        // Reset enabled state to whatever the value was if the account was already registered,
        // or _true_ if this is a SIM-based account.  All SIM-based accounts are always enabled,
        // as are all self-managed phone accounts.
        account.setIsEnabled(
                isEnabled || account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                || account.hasCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED));

        write();
        fireAccountsChanged();
        if (isNewAccount) {
            fireAccountRegistered(account.getAccountHandle());
        }
    }

    public void unregisterPhoneAccount(PhoneAccountHandle accountHandle) {
        PhoneAccount account = getPhoneAccountUnchecked(accountHandle);
        if (account != null) {
            if (mState.accounts.remove(account)) {
                write();
                fireAccountsChanged();
                fireAccountUnRegistered(accountHandle);
            }
        }
    }

    /**
     * Un-registers all phone accounts associated with a specified package.
     *
     * @param packageName The package for which phone accounts will be removed.
     * @param userHandle The {@link UserHandle} the package is running under.
     */
    public void clearAccounts(String packageName, UserHandle userHandle) {
        boolean accountsRemoved = false;
        Iterator<PhoneAccount> it = mState.accounts.iterator();
        while (it.hasNext()) {
            PhoneAccount phoneAccount = it.next();
            PhoneAccountHandle handle = phoneAccount.getAccountHandle();
            if (Objects.equals(packageName, handle.getComponentName().getPackageName())
                    && Objects.equals(userHandle, handle.getUserHandle())) {
                Log.i(this, "Removing phone account " + phoneAccount.getLabel());
                mState.accounts.remove(phoneAccount);
                accountsRemoved = true;
            }
        }

        if (accountsRemoved) {
            write();
            fireAccountsChanged();
        }
    }

    public boolean isVoiceMailNumber(PhoneAccountHandle accountHandle, String number) {
        int subId = getSubscriptionIdForPhoneAccount(accountHandle);
        return PhoneNumberUtils.isVoiceMailNumber(mContext, subId, number);
    }

    public void addListener(Listener l) {
        mListeners.add(l);
    }

    public void removeListener(Listener l) {
        if (l != null) {
            mListeners.remove(l);
        }
    }

    private void fireAccountRegistered(PhoneAccountHandle handle) {
        for (Listener l : mListeners) {
            l.onPhoneAccountRegistered(this, handle);
        }
    }

    private void fireAccountUnRegistered(PhoneAccountHandle handle) {
        for (Listener l : mListeners) {
            l.onPhoneAccountUnRegistered(this, handle);
        }
    }

    private void fireAccountsChanged() {
        for (Listener l : mListeners) {
            l.onAccountsChanged(this);
        }
    }

    private void fireDefaultOutgoingChanged() {
        for (Listener l : mListeners) {
            l.onDefaultOutgoingChanged(this);
        }
    }

    private String getAccountDiffString(PhoneAccount account1, PhoneAccount account2) {
        if (account1 == null || account2 == null) {
            return "Diff: " + account1 + ", " + account2;
        }

        StringBuffer sb = new StringBuffer();
        sb.append("[").append(account1.getAccountHandle());
        appendDiff(sb, "addr", Log.piiHandle(account1.getAddress()),
                Log.piiHandle(account2.getAddress()));
        appendDiff(sb, "cap", account1.getCapabilities(), account2.getCapabilities());
        appendDiff(sb, "hl", account1.getHighlightColor(), account2.getHighlightColor());
        appendDiff(sb, "lbl", account1.getLabel(), account2.getLabel());
        appendDiff(sb, "desc", account1.getShortDescription(), account2.getShortDescription());
        appendDiff(sb, "subAddr", Log.piiHandle(account1.getSubscriptionAddress()),
                Log.piiHandle(account2.getSubscriptionAddress()));
        appendDiff(sb, "uris", account1.getSupportedUriSchemes(),
                account2.getSupportedUriSchemes());
        sb.append("]");
        return sb.toString();
    }

    private void appendDiff(StringBuffer sb, String attrName, Object obj1, Object obj2) {
        if (!Objects.equals(obj1, obj2)) {
            sb.append("(")
                .append(attrName)
                .append(": ")
                .append(obj1)
                .append(" -> ")
                .append(obj2)
                .append(")");
        }
    }

    private void maybeReplaceOldAccount(PhoneAccount newAccount) {
        UserHandle newAccountUserHandle = newAccount.getAccountHandle().getUserHandle();
        DefaultPhoneAccountHandle defaultHandle =
                getUserSelectedDefaultPhoneAccount(newAccountUserHandle);
        if (defaultHandle == null || defaultHandle.groupId.isEmpty()) {
            Log.v(this, "maybeReplaceOldAccount: Not replacing PhoneAccount, no group Id or " +
                    "default.");
            return;
        }
        if (!defaultHandle.groupId.equals(newAccount.getGroupId())) {
            Log.v(this, "maybeReplaceOldAccount: group Ids are not equal.");
            return;
        }
        if (Objects.equals(newAccount.getAccountHandle().getComponentName(),
                defaultHandle.phoneAccountHandle.getComponentName())) {
            // Move default calling account over to new user, since the ComponentNames and Group Ids
            // are the same.
            setUserSelectedOutgoingPhoneAccount(newAccount.getAccountHandle(),
                    newAccountUserHandle);
        } else {
            Log.v(this, "maybeReplaceOldAccount: group Ids are equal, but ComponentName is not" +
                    " the same as the default. Not replacing default PhoneAccount.");
        }
        PhoneAccount replacementAccount = getPhoneAccountByGroupId(newAccount.getGroupId(),
                newAccount.getAccountHandle().getComponentName(), newAccountUserHandle,
                newAccount.getAccountHandle());
        if (replacementAccount != null) {
            // Unregister the old PhoneAccount.
            Log.v(this, "maybeReplaceOldAccount: Unregistering old PhoneAccount: " +
                    replacementAccount.getAccountHandle());
            unregisterPhoneAccount(replacementAccount.getAccountHandle());
        }
    }

    /**
     * Determines if the connection service specified by a {@link PhoneAccountHandle} requires the
     * {@link Manifest.permission#BIND_TELECOM_CONNECTION_SERVICE} permission.
     *
     * @param phoneAccountHandle The phone account to check.
     * @return {@code True} if the phone account has permission.
     */
    public boolean phoneAccountRequiresBindPermission(PhoneAccountHandle phoneAccountHandle) {
        List<ResolveInfo> resolveInfos = resolveComponent(phoneAccountHandle);
        if (resolveInfos.isEmpty()) {
            Log.w(this, "phoneAccount %s not found", phoneAccountHandle.getComponentName());
            return false;
        }
        for (ResolveInfo resolveInfo : resolveInfos) {
            ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            if (serviceInfo == null) {
                return false;
            }

            if (!Manifest.permission.BIND_CONNECTION_SERVICE.equals(serviceInfo.permission) &&
                    !Manifest.permission.BIND_TELECOM_CONNECTION_SERVICE.equals(
                            serviceInfo.permission)) {
                // The ConnectionService must require either the deprecated BIND_CONNECTION_SERVICE,
                // or the public BIND_TELECOM_CONNECTION_SERVICE permissions, both of which are
                // system/signature only.
                return false;
            }
        }
        return true;
    }

    //
    // Methods for retrieving PhoneAccounts and PhoneAccountHandles
    //

    /**
     * Returns the PhoneAccount for the specified handle.  Does no user checking.
     *
     * @param handle
     * @return The corresponding phone account if one exists.
     */
    public PhoneAccount getPhoneAccountUnchecked(PhoneAccountHandle handle) {
        for (PhoneAccount m : mState.accounts) {
            if (Objects.equals(handle, m.getAccountHandle())) {
                return m;
            }
        }
        return null;
    }

    /**
     * Like getPhoneAccount, but checks to see if the current user is allowed to see the phone
     * account before returning it. The current user is the active user on the actual android
     * device.
     */
    public PhoneAccount getPhoneAccount(PhoneAccountHandle handle, UserHandle userHandle) {
        return getPhoneAccount(handle, userHandle, /* acrossProfiles */ false);
    }

    public PhoneAccount getPhoneAccount(PhoneAccountHandle handle,
            UserHandle userHandle, boolean acrossProfiles) {
        PhoneAccount account = getPhoneAccountUnchecked(handle);
        if (account != null && (isVisibleForUser(account, userHandle, acrossProfiles))) {
            return account;
        }
        return null;
    }

    public PhoneAccount getPhoneAccountOfCurrentUser(PhoneAccountHandle handle) {
        return getPhoneAccount(handle, mCurrentUserHandle);
    }

    private List<PhoneAccountHandle> getPhoneAccountHandles(
            int capabilities,
            String uriScheme,
            String packageName,
            boolean includeDisabledAccounts,
            UserHandle userHandle) {
        return getPhoneAccountHandles(capabilities, 0 /*excludedCapabilities*/, uriScheme,
                packageName, includeDisabledAccounts, userHandle);
    }

    /**
     * Returns a list of phone account handles with the specified capabilities, uri scheme,
     * and package name.
     */
    private List<PhoneAccountHandle> getPhoneAccountHandles(
            int capabilities,
            int excludedCapabilities,
            String uriScheme,
            String packageName,
            boolean includeDisabledAccounts,
            UserHandle userHandle) {
        List<PhoneAccountHandle> handles = new ArrayList<>();

        for (PhoneAccount account : getPhoneAccounts(
                capabilities, excludedCapabilities, uriScheme, packageName,
                includeDisabledAccounts, userHandle)) {
            handles.add(account.getAccountHandle());
        }
        return handles;
    }

    private List<PhoneAccount> getPhoneAccounts(
            int capabilities,
            String uriScheme,
            String packageName,
            boolean includeDisabledAccounts,
            UserHandle userHandle) {
        return getPhoneAccounts(capabilities, 0 /*excludedCapabilities*/, uriScheme, packageName,
                includeDisabledAccounts, userHandle);
    }

    /**
     * Returns a list of phone account handles with the specified flag, supporting the specified
     * URI scheme, within the specified package name.
     *
     * @param capabilities Capabilities which the {@code PhoneAccount} must have. Ignored if 0.
     * @param excludedCapabilities Capabilities which the {@code PhoneAccount} must not have.
     *                             Ignored if 0.
     * @param uriScheme URI schemes the PhoneAccount must handle.  {@code null} bypasses the
     *                  URI scheme check.
     * @param packageName Package name of the PhoneAccount. {@code null} bypasses packageName check.
     */
    private List<PhoneAccount> getPhoneAccounts(
            int capabilities,
            int excludedCapabilities,
            String uriScheme,
            String packageName,
            boolean includeDisabledAccounts,
            UserHandle userHandle) {
        List<PhoneAccount> accounts = new ArrayList<>(mState.accounts.size());
        for (PhoneAccount m : mState.accounts) {
            if (!(m.isEnabled() || includeDisabledAccounts)) {
                // Do not include disabled accounts.
                continue;
            }

            if ((m.getCapabilities() & excludedCapabilities) != 0) {
                // If an excluded capability is present, skip.
                continue;
            }

            if (capabilities != 0 && !m.hasCapabilities(capabilities)) {
                // Account doesn't have the right capabilities; skip this one.
                continue;
            }
            if (uriScheme != null && !m.supportsUriScheme(uriScheme)) {
                // Account doesn't support this URI scheme; skip this one.
                continue;
            }
            PhoneAccountHandle handle = m.getAccountHandle();

            if (resolveComponent(handle).isEmpty()) {
                // This component cannot be resolved anymore; skip this one.
                continue;
            }
            if (packageName != null &&
                    !packageName.equals(handle.getComponentName().getPackageName())) {
                // Not the right package name; skip this one.
                continue;
            }
            if (!isVisibleForUser(m, userHandle, false)) {
                // Account is not visible for the current user; skip this one.
                continue;
            }
            accounts.add(m);
        }
        return accounts;
    }

    //
    // State Implementation for PhoneAccountRegistrar
    //

    /**
     * The state of this {@code PhoneAccountRegistrar}.
     */
    @VisibleForTesting
    public static class State {
        /**
         * Store the default phone account handle of users. If no record of a user can be found in
         * the map, it means that no default phone account handle is set in that user.
         */
        public final Map<UserHandle, DefaultPhoneAccountHandle> defaultOutgoingAccountHandles
                = new ConcurrentHashMap<>();

        /**
         * The complete list of {@code PhoneAccount}s known to the Telecom subsystem.
         */
        public final List<PhoneAccount> accounts = new CopyOnWriteArrayList<>();

        /**
         * The version number of the State data.
         */
        public int versionNumber;
    }

    /**
     * The default {@link PhoneAccountHandle} of a user.
     */
    public static class DefaultPhoneAccountHandle {

        public final UserHandle userHandle;

        public final PhoneAccountHandle phoneAccountHandle;

        public final String groupId;

        public DefaultPhoneAccountHandle(UserHandle userHandle,
                PhoneAccountHandle phoneAccountHandle, String groupId) {
            this.userHandle = userHandle;
            this.phoneAccountHandle = phoneAccountHandle;
            this.groupId = groupId;
        }
    }

    /**
     * Dumps the state of the {@link CallsManager}.
     *
     * @param pw The {@code IndentingPrintWriter} to write the state to.
     */
    public void dump(IndentingPrintWriter pw) {
        if (mState != null) {
            pw.println("xmlVersion: " + mState.versionNumber);
            DefaultPhoneAccountHandle defaultPhoneAccountHandle
                    = mState.defaultOutgoingAccountHandles.get(Process.myUserHandle());
            pw.println("defaultOutgoing: " + (defaultPhoneAccountHandle == null ? "none" :
                    defaultPhoneAccountHandle.phoneAccountHandle));
            pw.println("simCallManager: " + getSimCallManager(mCurrentUserHandle));
            pw.println("phoneAccounts:");
            pw.increaseIndent();
            for (PhoneAccount phoneAccount : mState.accounts) {
                pw.println(phoneAccount);
            }
            pw.decreaseIndent();
        }
    }

    private void sortPhoneAccounts() {
        if (mState.accounts.size() > 1) {
            // Sort the phone accounts using sort order:
            // 1) SIM accounts first, followed by non-sim accounts
            // 2) Sort order, with those specifying no sort order last.
            // 3) Label

            // Comparator to sort SIM subscriptions before non-sim subscriptions.
            Comparator<PhoneAccount> bySimCapability = (p1, p2) -> {
                if (p1.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                        && !p2.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
                    return -1;
                } else if (!p1.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                        && p2.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
                    return 1;
                } else {
                    return 0;
                }
            };

            // Create a string comparator which will sort strings, placing nulls last.
            Comparator<String> nullSafeStringComparator = Comparator.nullsLast(
                    String::compareTo);

            // Comparator which places PhoneAccounts with a specified sort order first, followed by
            // those with no sort order.
            Comparator<PhoneAccount> bySortOrder = (p1, p2) -> {
                String sort1 = p1.getExtras() == null ? null :
                        p1.getExtras().getString(PhoneAccount.EXTRA_SORT_ORDER, null);
                String sort2 = p2.getExtras() == null ? null :
                        p2.getExtras().getString(PhoneAccount.EXTRA_SORT_ORDER, null);
                return nullSafeStringComparator.compare(sort1, sort2);
            };

            // Comparator which sorts PhoneAccounts by label.
            Comparator<PhoneAccount> byLabel = (p1, p2) -> {
                String s1 = p1.getLabel() == null ? null : p1.getLabel().toString();
                String s2 = p2.getLabel() == null ? null : p2.getLabel().toString();
                return nullSafeStringComparator.compare(s1, s2);
            };

            // Sort the phone accounts.
            mState.accounts.sort(bySimCapability.thenComparing(bySortOrder.thenComparing(byLabel)));
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // State management
    //

    private class AsyncXmlWriter extends AsyncTask<ByteArrayOutputStream, Void, Void> {
        @Override
        public Void doInBackground(ByteArrayOutputStream... args) {
            final ByteArrayOutputStream buffer = args[0];
            FileOutputStream fileOutput = null;
            try {
                synchronized (mWriteLock) {
                    fileOutput = mAtomicFile.startWrite();
                    buffer.writeTo(fileOutput);
                    mAtomicFile.finishWrite(fileOutput);
                }
            } catch (IOException e) {
                Log.e(this, e, "Writing state to XML file");
                mAtomicFile.failWrite(fileOutput);
            }
            return null;
        }
    }

    private void write() {
        try {
            sortPhoneAccounts();
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            XmlSerializer serializer = new FastXmlSerializer();
            serializer.setOutput(os, "utf-8");
            writeToXml(mState, serializer, mContext);
            serializer.flush();
            new AsyncXmlWriter().execute(os);
        } catch (IOException e) {
            Log.e(this, e, "Writing state to XML buffer");
        }
    }

    private void read() {
        final InputStream is;
        try {
            is = mAtomicFile.openRead();
        } catch (FileNotFoundException ex) {
            return;
        }

        boolean versionChanged = false;

        XmlPullParser parser;
        try {
            parser = Xml.newPullParser();
            parser.setInput(new BufferedInputStream(is), null);
            parser.nextTag();
            mState = readFromXml(parser, mContext);
            versionChanged = mState.versionNumber < EXPECTED_STATE_VERSION;

        } catch (IOException | XmlPullParserException e) {
            Log.e(this, e, "Reading state from XML file");
            mState = new State();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                Log.e(this, e, "Closing InputStream");
            }
        }

        // Verify all of the UserHandles.
        List<PhoneAccount> badAccounts = new ArrayList<>();
        for (PhoneAccount phoneAccount : mState.accounts) {
            UserHandle userHandle = phoneAccount.getAccountHandle().getUserHandle();
            if (userHandle == null) {
                Log.w(this, "Missing UserHandle for %s", phoneAccount);
                badAccounts.add(phoneAccount);
            } else if (mUserManager.getSerialNumberForUser(userHandle) == -1) {
                Log.w(this, "User does not exist for %s", phoneAccount);
                badAccounts.add(phoneAccount);
            }
        }
        mState.accounts.removeAll(badAccounts);

        // If an upgrade occurred, write out the changed data.
        if (versionChanged || !badAccounts.isEmpty()) {
            write();
        }
    }

    private static void writeToXml(State state, XmlSerializer serializer, Context context)
            throws IOException {
        sStateXml.writeToXml(state, serializer, context);
    }

    private static State readFromXml(XmlPullParser parser, Context context)
            throws IOException, XmlPullParserException {
        State s = sStateXml.readFromXml(parser, 0, context);
        return s != null ? s : new State();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // XML serialization
    //

    @VisibleForTesting
    public abstract static class XmlSerialization<T> {
        private static final String TAG_VALUE = "value";
        private static final String ATTRIBUTE_LENGTH = "length";
        private static final String ATTRIBUTE_KEY = "key";
        private static final String ATTRIBUTE_VALUE_TYPE = "type";
        private static final String VALUE_TYPE_STRING = "string";
        private static final String VALUE_TYPE_INTEGER = "integer";
        private static final String VALUE_TYPE_BOOLEAN = "boolean";

        /**
         * Write the supplied object to XML
         */
        public abstract void writeToXml(T o, XmlSerializer serializer, Context context)
                throws IOException;

        /**
         * Read from the supplied XML into a new object, returning null in case of an
         * unrecoverable schema mismatch or other data error. 'parser' must be already
         * positioned at the first tag that is expected to have been emitted by this
         * object's writeToXml(). This object tries to fail early without modifying
         * 'parser' if it does not recognize the data it sees.
         */
        public abstract T readFromXml(XmlPullParser parser, int version, Context context)
                throws IOException, XmlPullParserException;

        protected void writeTextIfNonNull(String tagName, Object value, XmlSerializer serializer)
                throws IOException {
            if (value != null) {
                serializer.startTag(null, tagName);
                serializer.text(Objects.toString(value));
                serializer.endTag(null, tagName);
            }
        }

        /**
         * Serializes a string array.
         *
         * @param tagName The tag name for the string array.
         * @param values The string values to serialize.
         * @param serializer The serializer.
         * @throws IOException
         */
        protected void writeStringList(String tagName, List<String> values,
                XmlSerializer serializer)
                throws IOException {

            serializer.startTag(null, tagName);
            if (values != null) {
                serializer.attribute(null, ATTRIBUTE_LENGTH, Objects.toString(values.size()));
                for (String toSerialize : values) {
                    serializer.startTag(null, TAG_VALUE);
                    if (toSerialize != null ){
                        serializer.text(toSerialize);
                    }
                    serializer.endTag(null, TAG_VALUE);
                }
            } else {
                serializer.attribute(null, ATTRIBUTE_LENGTH, "0");
            }
            serializer.endTag(null, tagName);
        }

        protected void writeBundle(String tagName, Bundle values, XmlSerializer serializer)
            throws IOException {

            serializer.startTag(null, tagName);
            if (values != null) {
                for (String key : values.keySet()) {
                    Object value = values.get(key);

                    if (value == null) {
                        continue;
                    }

                    String valueType;
                    if (value instanceof String) {
                        valueType = VALUE_TYPE_STRING;
                    } else if (value instanceof Integer) {
                        valueType = VALUE_TYPE_INTEGER;
                    } else if (value instanceof Boolean) {
                        valueType = VALUE_TYPE_BOOLEAN;
                    } else {
                        Log.w(this,
                                "PhoneAccounts support only string, integer and boolean extras TY.");
                        continue;
                    }

                    serializer.startTag(null, TAG_VALUE);
                    serializer.attribute(null, ATTRIBUTE_KEY, key);
                    serializer.attribute(null, ATTRIBUTE_VALUE_TYPE, valueType);
                    serializer.text(Objects.toString(value));
                    serializer.endTag(null, TAG_VALUE);
                }
            }
            serializer.endTag(null, tagName);
        }

        protected void writeIconIfNonNull(String tagName, Icon value, XmlSerializer serializer)
                throws IOException {
            if (value != null) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                value.writeToStream(stream);
                byte[] iconByteArray = stream.toByteArray();
                String text = Base64.encodeToString(iconByteArray, 0, iconByteArray.length, 0);

                serializer.startTag(null, tagName);
                serializer.text(text);
                serializer.endTag(null, tagName);
            }
        }

        protected void writeLong(String tagName, long value, XmlSerializer serializer)
                throws IOException {
            serializer.startTag(null, tagName);
            serializer.text(Long.valueOf(value).toString());
            serializer.endTag(null, tagName);
        }

        protected void writeNonNullString(String tagName, String value, XmlSerializer serializer)
                throws IOException {
            serializer.startTag(null, tagName);
            serializer.text(value != null ? value : "");
            serializer.endTag(null, tagName);
        }

        /**
         * Reads a string array from the XML parser.
         *
         * @param parser The XML parser.
         * @return String array containing the parsed values.
         * @throws IOException Exception related to IO.
         * @throws XmlPullParserException Exception related to parsing.
         */
        protected List<String> readStringList(XmlPullParser parser)
                throws IOException, XmlPullParserException {

            int length = Integer.parseInt(parser.getAttributeValue(null, ATTRIBUTE_LENGTH));
            List<String> arrayEntries = new ArrayList<String>(length);
            String value = null;

            if (length == 0) {
                return arrayEntries;
            }

            int outerDepth = parser.getDepth();
            while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                if (parser.getName().equals(TAG_VALUE)) {
                    parser.next();
                    value = parser.getText();
                    arrayEntries.add(value);
                }
            }

            return arrayEntries;
        }

        /**
         * Reads a bundle from the XML parser.
         *
         * @param parser The XML parser.
         * @return Bundle containing the parsed values.
         * @throws IOException Exception related to IO.
         * @throws XmlPullParserException Exception related to parsing.
         */
        protected Bundle readBundle(XmlPullParser parser)
                throws IOException, XmlPullParserException {

            Bundle bundle = null;
            int outerDepth = parser.getDepth();
            while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                if (parser.getName().equals(TAG_VALUE)) {
                    String valueType = parser.getAttributeValue(null, ATTRIBUTE_VALUE_TYPE);
                    String key = parser.getAttributeValue(null, ATTRIBUTE_KEY);
                    parser.next();
                    String value = parser.getText();

                    if (bundle == null) {
                        bundle = new Bundle();
                    }

                    // Do not write null values to the bundle.
                    if (value == null) {
                        continue;
                    }

                    if (VALUE_TYPE_STRING.equals(valueType)) {
                        bundle.putString(key, value);
                    } else if (VALUE_TYPE_INTEGER.equals(valueType)) {
                        try {
                            int intValue = Integer.parseInt(value);
                            bundle.putInt(key, intValue);
                        } catch (NumberFormatException nfe) {
                            Log.w(this, "Invalid integer PhoneAccount extra.");
                        }
                    } else if (VALUE_TYPE_BOOLEAN.equals(valueType)) {
                        boolean boolValue = Boolean.parseBoolean(value);
                        bundle.putBoolean(key, boolValue);
                    } else {
                        Log.w(this, "Invalid type " + valueType + " for PhoneAccount bundle.");
                    }
                }
            }
            return bundle;
        }

        protected Bitmap readBitmap(XmlPullParser parser) {
            byte[] imageByteArray = Base64.decode(parser.getText(), 0);
            return BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.length);
        }

        protected Icon readIcon(XmlPullParser parser) throws IOException {
            byte[] iconByteArray = Base64.decode(parser.getText(), 0);
            ByteArrayInputStream stream = new ByteArrayInputStream(iconByteArray);
            return Icon.createFromStream(stream);
        }
    }

    @VisibleForTesting
    public static final XmlSerialization<State> sStateXml =
            new XmlSerialization<State>() {
        private static final String CLASS_STATE = "phone_account_registrar_state";
        private static final String DEFAULT_OUTGOING = "default_outgoing";
        private static final String ACCOUNTS = "accounts";
        private static final String VERSION = "version";

        @Override
        public void writeToXml(State o, XmlSerializer serializer, Context context)
                throws IOException {
            if (o != null) {
                serializer.startTag(null, CLASS_STATE);
                serializer.attribute(null, VERSION, Objects.toString(EXPECTED_STATE_VERSION));

                serializer.startTag(null, DEFAULT_OUTGOING);
                for (DefaultPhoneAccountHandle defaultPhoneAccountHandle : o
                        .defaultOutgoingAccountHandles.values()) {
                    sDefaultPhoneAcountHandleXml
                            .writeToXml(defaultPhoneAccountHandle, serializer, context);
                }
                serializer.endTag(null, DEFAULT_OUTGOING);

                serializer.startTag(null, ACCOUNTS);
                for (PhoneAccount m : o.accounts) {
                    sPhoneAccountXml.writeToXml(m, serializer, context);
                }
                serializer.endTag(null, ACCOUNTS);

                serializer.endTag(null, CLASS_STATE);
            }
        }

        @Override
        public State readFromXml(XmlPullParser parser, int version, Context context)
                throws IOException, XmlPullParserException {
            if (parser.getName().equals(CLASS_STATE)) {
                State s = new State();

                String rawVersion = parser.getAttributeValue(null, VERSION);
                s.versionNumber = TextUtils.isEmpty(rawVersion) ? 1 : Integer.parseInt(rawVersion);

                int outerDepth = parser.getDepth();
                while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                    if (parser.getName().equals(DEFAULT_OUTGOING)) {
                        if (s.versionNumber < 9) {
                            // Migrate old default phone account handle here by assuming the
                            // default phone account handle belongs to the primary user. Also,
                            // assume there are no groups.
                            parser.nextTag();
                            PhoneAccountHandle phoneAccountHandle = sPhoneAccountHandleXml
                                    .readFromXml(parser, s.versionNumber, context);
                            UserManager userManager = UserManager.get(context);
                            UserInfo primaryUser = userManager.getPrimaryUser();
                            if (primaryUser != null) {
                                UserHandle userHandle = primaryUser.getUserHandle();
                                DefaultPhoneAccountHandle defaultPhoneAccountHandle
                                        = new DefaultPhoneAccountHandle(userHandle,
                                        phoneAccountHandle, "" /* groupId */);
                                s.defaultOutgoingAccountHandles
                                        .put(userHandle, defaultPhoneAccountHandle);
                            }
                        } else {
                            int defaultAccountHandlesDepth = parser.getDepth();
                            while (XmlUtils.nextElementWithin(parser, defaultAccountHandlesDepth)) {
                                DefaultPhoneAccountHandle accountHandle
                                        = sDefaultPhoneAcountHandleXml
                                        .readFromXml(parser, s.versionNumber, context);
                                if (accountHandle != null && s.accounts != null) {
                                    s.defaultOutgoingAccountHandles
                                            .put(accountHandle.userHandle, accountHandle);
                                }
                            }
                        }
                    } else if (parser.getName().equals(ACCOUNTS)) {
                        int accountsDepth = parser.getDepth();
                        while (XmlUtils.nextElementWithin(parser, accountsDepth)) {
                            PhoneAccount account = sPhoneAccountXml.readFromXml(parser,
                                    s.versionNumber, context);

                            if (account != null && s.accounts != null) {
                                s.accounts.add(account);
                            }
                        }
                    }
                }
                return s;
            }
            return null;
        }
    };

    @VisibleForTesting
    public static final XmlSerialization<DefaultPhoneAccountHandle> sDefaultPhoneAcountHandleXml  =
            new XmlSerialization<DefaultPhoneAccountHandle>() {
                private static final String CLASS_DEFAULT_OUTGOING_PHONE_ACCOUNT_HANDLE
                        = "default_outgoing_phone_account_handle";
                private static final String USER_SERIAL_NUMBER = "user_serial_number";
                private static final String GROUP_ID = "group_id";
                private static final String ACCOUNT_HANDLE = "account_handle";

                @Override
                public void writeToXml(DefaultPhoneAccountHandle o, XmlSerializer serializer,
                        Context context) throws IOException {
                    if (o != null) {
                        final UserManager userManager = UserManager.get(context);
                        final long serialNumber = userManager.getSerialNumberForUser(o.userHandle);
                        if (serialNumber != -1) {
                            serializer.startTag(null, CLASS_DEFAULT_OUTGOING_PHONE_ACCOUNT_HANDLE);
                            writeLong(USER_SERIAL_NUMBER, serialNumber, serializer);
                            writeNonNullString(GROUP_ID, o.groupId, serializer);
                            serializer.startTag(null, ACCOUNT_HANDLE);
                            sPhoneAccountHandleXml.writeToXml(o.phoneAccountHandle, serializer,
                                    context);
                            serializer.endTag(null, ACCOUNT_HANDLE);
                            serializer.endTag(null, CLASS_DEFAULT_OUTGOING_PHONE_ACCOUNT_HANDLE);
                        }
                    }
                }

                @Override
                public DefaultPhoneAccountHandle readFromXml(XmlPullParser parser, int version,
                        Context context)
                        throws IOException, XmlPullParserException {
                    if (parser.getName().equals(CLASS_DEFAULT_OUTGOING_PHONE_ACCOUNT_HANDLE)) {
                        int outerDepth = parser.getDepth();
                        PhoneAccountHandle accountHandle = null;
                        String userSerialNumberString = null;
                        String groupId = "";
                        while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                            if (parser.getName().equals(ACCOUNT_HANDLE)) {
                                parser.nextTag();
                                accountHandle = sPhoneAccountHandleXml.readFromXml(parser, version,
                                        context);
                            } else if (parser.getName().equals(USER_SERIAL_NUMBER)) {
                                parser.next();
                                userSerialNumberString = parser.getText();
                            } else if (parser.getName().equals(GROUP_ID)) {
                                if (parser.next() == XmlPullParser.TEXT) {
                                    groupId = parser.getText();
                                }
                            }
                        }
                        UserHandle userHandle = null;
                        if (userSerialNumberString != null) {
                            try {
                                long serialNumber = Long.parseLong(userSerialNumberString);
                                userHandle = UserManager.get(context)
                                        .getUserForSerialNumber(serialNumber);
                            } catch (NumberFormatException e) {
                                Log.e(this, e,
                                        "Could not parse UserHandle " + userSerialNumberString);
                            }
                        }
                        if (accountHandle != null && userHandle != null && groupId != null) {
                            return new DefaultPhoneAccountHandle(userHandle, accountHandle,
                                    groupId);
                        }
                    }
                    return null;
                }
            };


    @VisibleForTesting
    public static final XmlSerialization<PhoneAccount> sPhoneAccountXml =
            new XmlSerialization<PhoneAccount>() {
        private static final String CLASS_PHONE_ACCOUNT = "phone_account";
        private static final String ACCOUNT_HANDLE = "account_handle";
        private static final String ADDRESS = "handle";
        private static final String SUBSCRIPTION_ADDRESS = "subscription_number";
        private static final String CAPABILITIES = "capabilities";
        private static final String SUPPORTED_AUDIO_ROUTES = "supported_audio_routes";
        private static final String ICON_RES_ID = "icon_res_id";
        private static final String ICON_PACKAGE_NAME = "icon_package_name";
        private static final String ICON_BITMAP = "icon_bitmap";
        private static final String ICON_TINT = "icon_tint";
        private static final String HIGHLIGHT_COLOR = "highlight_color";
        private static final String LABEL = "label";
        private static final String SHORT_DESCRIPTION = "short_description";
        private static final String SUPPORTED_URI_SCHEMES = "supported_uri_schemes";
        private static final String ICON = "icon";
        private static final String EXTRAS = "extras";
        private static final String ENABLED = "enabled";

        @Override
        public void writeToXml(PhoneAccount o, XmlSerializer serializer, Context context)
                throws IOException {
            if (o != null) {
                serializer.startTag(null, CLASS_PHONE_ACCOUNT);

                if (o.getAccountHandle() != null) {
                    serializer.startTag(null, ACCOUNT_HANDLE);
                    sPhoneAccountHandleXml.writeToXml(o.getAccountHandle(), serializer, context);
                    serializer.endTag(null, ACCOUNT_HANDLE);
                }

                writeTextIfNonNull(ADDRESS, o.getAddress(), serializer);
                writeTextIfNonNull(SUBSCRIPTION_ADDRESS, o.getSubscriptionAddress(), serializer);
                writeTextIfNonNull(CAPABILITIES, Integer.toString(o.getCapabilities()), serializer);
                writeIconIfNonNull(ICON, o.getIcon(), serializer);
                writeTextIfNonNull(HIGHLIGHT_COLOR,
                        Integer.toString(o.getHighlightColor()), serializer);
                writeTextIfNonNull(LABEL, o.getLabel(), serializer);
                writeTextIfNonNull(SHORT_DESCRIPTION, o.getShortDescription(), serializer);
                writeStringList(SUPPORTED_URI_SCHEMES, o.getSupportedUriSchemes(), serializer);
                writeBundle(EXTRAS, o.getExtras(), serializer);
                writeTextIfNonNull(ENABLED, o.isEnabled() ? "true" : "false" , serializer);
                writeTextIfNonNull(SUPPORTED_AUDIO_ROUTES, Integer.toString(
                        o.getSupportedAudioRoutes()), serializer);

                serializer.endTag(null, CLASS_PHONE_ACCOUNT);
            }
        }

        public PhoneAccount readFromXml(XmlPullParser parser, int version, Context context)
                throws IOException, XmlPullParserException {
            if (parser.getName().equals(CLASS_PHONE_ACCOUNT)) {
                int outerDepth = parser.getDepth();
                PhoneAccountHandle accountHandle = null;
                Uri address = null;
                Uri subscriptionAddress = null;
                int capabilities = 0;
                int supportedAudioRoutes = 0;
                int iconResId = PhoneAccount.NO_RESOURCE_ID;
                String iconPackageName = null;
                Bitmap iconBitmap = null;
                int iconTint = PhoneAccount.NO_ICON_TINT;
                int highlightColor = PhoneAccount.NO_HIGHLIGHT_COLOR;
                String label = null;
                String shortDescription = null;
                List<String> supportedUriSchemes = null;
                Icon icon = null;
                boolean enabled = false;
                Bundle extras = null;

                while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                    if (parser.getName().equals(ACCOUNT_HANDLE)) {
                        parser.nextTag();
                        accountHandle = sPhoneAccountHandleXml.readFromXml(parser, version,
                                context);
                    } else if (parser.getName().equals(ADDRESS)) {
                        parser.next();
                        address = Uri.parse(parser.getText());
                    } else if (parser.getName().equals(SUBSCRIPTION_ADDRESS)) {
                        parser.next();
                        String nextText = parser.getText();
                        subscriptionAddress = nextText == null ? null : Uri.parse(nextText);
                    } else if (parser.getName().equals(CAPABILITIES)) {
                        parser.next();
                        capabilities = Integer.parseInt(parser.getText());
                    } else if (parser.getName().equals(ICON_RES_ID)) {
                        parser.next();
                        iconResId = Integer.parseInt(parser.getText());
                    } else if (parser.getName().equals(ICON_PACKAGE_NAME)) {
                        parser.next();
                        iconPackageName = parser.getText();
                    } else if (parser.getName().equals(ICON_BITMAP)) {
                        parser.next();
                        iconBitmap = readBitmap(parser);
                    } else if (parser.getName().equals(ICON_TINT)) {
                        parser.next();
                        iconTint = Integer.parseInt(parser.getText());
                    } else if (parser.getName().equals(HIGHLIGHT_COLOR)) {
                        parser.next();
                        highlightColor = Integer.parseInt(parser.getText());
                    } else if (parser.getName().equals(LABEL)) {
                        parser.next();
                        label = parser.getText();
                    } else if (parser.getName().equals(SHORT_DESCRIPTION)) {
                        parser.next();
                        shortDescription = parser.getText();
                    } else if (parser.getName().equals(SUPPORTED_URI_SCHEMES)) {
                        supportedUriSchemes = readStringList(parser);
                    } else if (parser.getName().equals(ICON)) {
                        parser.next();
                        icon = readIcon(parser);
                    } else if (parser.getName().equals(ENABLED)) {
                        parser.next();
                        enabled = "true".equalsIgnoreCase(parser.getText());
                    } else if (parser.getName().equals(EXTRAS)) {
                        extras = readBundle(parser);
                    } else if (parser.getName().equals(SUPPORTED_AUDIO_ROUTES)) {
                        parser.next();
                        supportedAudioRoutes = Integer.parseInt(parser.getText());
                    }
                }

                ComponentName pstnComponentName = new ComponentName("com.android.phone",
                        "com.android.services.telephony.TelephonyConnectionService");
                ComponentName sipComponentName = new ComponentName("com.android.phone",
                        "com.android.services.telephony.sip.SipConnectionService");

                // Upgrade older phone accounts to specify the supported URI schemes.
                if (version < 2) {
                    supportedUriSchemes = new ArrayList<>();

                    // Handle the SIP connection service.
                    // Check the system settings to see if it also should handle "tel" calls.
                    if (accountHandle.getComponentName().equals(sipComponentName)) {
                        boolean useSipForPstn = useSipForPstnCalls(context);
                        supportedUriSchemes.add(PhoneAccount.SCHEME_SIP);
                        if (useSipForPstn) {
                            supportedUriSchemes.add(PhoneAccount.SCHEME_TEL);
                        }
                    } else {
                        supportedUriSchemes.add(PhoneAccount.SCHEME_TEL);
                        supportedUriSchemes.add(PhoneAccount.SCHEME_VOICEMAIL);
                    }
                }

                // Upgrade older phone accounts with explicit package name
                if (version < 5) {
                    if (iconBitmap == null) {
                        iconPackageName = accountHandle.getComponentName().getPackageName();
                    }
                }

                if (version < 6) {
                    // Always enable all SIP accounts on upgrade to version 6
                    if (accountHandle.getComponentName().equals(sipComponentName)) {
                        enabled = true;
                    }
                }
                if (version < 7) {
                    // Always enabled all PSTN acocunts on upgrade to version 7
                    if (accountHandle.getComponentName().equals(pstnComponentName)) {
                        enabled = true;
                    }
                }
                if (version < 8) {
                    // Migrate the SIP account handle ids to use SIP username instead of SIP URI.
                    if (accountHandle.getComponentName().equals(sipComponentName)) {
                        Uri accountUri = Uri.parse(accountHandle.getId());
                        if (accountUri.getScheme() != null &&
                            accountUri.getScheme().equals(PhoneAccount.SCHEME_SIP)) {
                            accountHandle = new PhoneAccountHandle(accountHandle.getComponentName(),
                                    accountUri.getSchemeSpecificPart(),
                                    accountHandle.getUserHandle());
                        }
                    }
                }

                if (version < 9) {
                    // Set supported audio routes to all by default
                    supportedAudioRoutes = CallAudioState.ROUTE_ALL;
                }

                PhoneAccount.Builder builder = PhoneAccount.builder(accountHandle, label)
                        .setAddress(address)
                        .setSubscriptionAddress(subscriptionAddress)
                        .setCapabilities(capabilities)
                        .setSupportedAudioRoutes(supportedAudioRoutes)
                        .setShortDescription(shortDescription)
                        .setSupportedUriSchemes(supportedUriSchemes)
                        .setHighlightColor(highlightColor)
                        .setExtras(extras)
                        .setIsEnabled(enabled);

                if (icon != null) {
                    builder.setIcon(icon);
                } else if (iconBitmap != null) {
                    builder.setIcon(Icon.createWithBitmap(iconBitmap));
                } else if (!TextUtils.isEmpty(iconPackageName)) {
                    builder.setIcon(Icon.createWithResource(iconPackageName, iconResId));
                    // TODO: Need to set tint.
                }

                return builder.build();
            }
            return null;
        }

        /**
         * Determines if the SIP call settings specify to use SIP for all calls, including PSTN
         * calls.
         *
         * @param context The context.
         * @return {@code True} if SIP should be used for all calls.
         */
        private boolean useSipForPstnCalls(Context context) {
            String option = Settings.System.getString(context.getContentResolver(),
                    Settings.System.SIP_CALL_OPTIONS);
            option = (option != null) ? option : Settings.System.SIP_ADDRESS_ONLY;
            return option.equals(Settings.System.SIP_ALWAYS);
        }
    };

    @VisibleForTesting
    public static final XmlSerialization<PhoneAccountHandle> sPhoneAccountHandleXml =
            new XmlSerialization<PhoneAccountHandle>() {
        private static final String CLASS_PHONE_ACCOUNT_HANDLE = "phone_account_handle";
        private static final String COMPONENT_NAME = "component_name";
        private static final String ID = "id";
        private static final String USER_SERIAL_NUMBER = "user_serial_number";

        @Override
        public void writeToXml(PhoneAccountHandle o, XmlSerializer serializer, Context context)
                throws IOException {
            if (o != null) {
                serializer.startTag(null, CLASS_PHONE_ACCOUNT_HANDLE);

                if (o.getComponentName() != null) {
                    writeTextIfNonNull(
                            COMPONENT_NAME, o.getComponentName().flattenToString(), serializer);
                }

                writeTextIfNonNull(ID, o.getId(), serializer);

                if (o.getUserHandle() != null && context != null) {
                    UserManager userManager = UserManager.get(context);
                    writeLong(USER_SERIAL_NUMBER,
                            userManager.getSerialNumberForUser(o.getUserHandle()), serializer);
                }

                serializer.endTag(null, CLASS_PHONE_ACCOUNT_HANDLE);
            }
        }

        @Override
        public PhoneAccountHandle readFromXml(XmlPullParser parser, int version, Context context)
                throws IOException, XmlPullParserException {
            if (parser.getName().equals(CLASS_PHONE_ACCOUNT_HANDLE)) {
                String componentNameString = null;
                String idString = null;
                String userSerialNumberString = null;
                int outerDepth = parser.getDepth();

                UserManager userManager = UserManager.get(context);

                while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                    if (parser.getName().equals(COMPONENT_NAME)) {
                        parser.next();
                        componentNameString = parser.getText();
                    } else if (parser.getName().equals(ID)) {
                        parser.next();
                        idString = parser.getText();
                    } else if (parser.getName().equals(USER_SERIAL_NUMBER)) {
                        parser.next();
                        userSerialNumberString = parser.getText();
                    }
                }
                if (componentNameString != null) {
                    UserHandle userHandle = null;
                    if (userSerialNumberString != null) {
                        try {
                            long serialNumber = Long.parseLong(userSerialNumberString);
                            userHandle = userManager.getUserForSerialNumber(serialNumber);
                        } catch (NumberFormatException e) {
                            Log.e(this, e, "Could not parse UserHandle " + userSerialNumberString);
                        }
                    }
                    return new PhoneAccountHandle(
                            ComponentName.unflattenFromString(componentNameString),
                            idString,
                            userHandle);
                }
            }
            return null;
        }
    };

    private String nullToEmpty(String str) {
        return str == null ? "" : str;
    }
}
