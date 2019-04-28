/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.common;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_USER;
import static android.app.admin.DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC;
import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.TrampolineActivity;
import com.android.managedprovisioning.model.PackageDownloadInfo;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class containing various auxiliary methods.
 */
public class Utils {
    public static final String SHA256_TYPE = "SHA-256";
    public static final String SHA1_TYPE = "SHA-1";

    // value chosen to match UX designs; when updating check status bar icon colors
    private static final int THRESHOLD_BRIGHT_COLOR = 190;

    public Utils() {}

    /**
     * Returns the system apps currently available to a given user.
     *
     * <p>Calls the {@link IPackageManager} to retrieve all system apps available to a user and
     * returns their package names.
     *
     * @param ipm an {@link IPackageManager} object
     * @param userId the id of the user to check the apps for
     */
    public Set<String> getCurrentSystemApps(IPackageManager ipm, int userId) {
        Set<String> apps = new HashSet<>();
        List<ApplicationInfo> aInfos = null;
        try {
            aInfos = ipm.getInstalledApplications(
                    PackageManager.MATCH_UNINSTALLED_PACKAGES, userId).getList();
        } catch (RemoteException neverThrown) {
            ProvisionLogger.loge("This should not happen.", neverThrown);
        }
        for (ApplicationInfo aInfo : aInfos) {
            if ((aInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                apps.add(aInfo.packageName);
            }
        }
        return apps;
    }

    /**
     * Disables a given component in a given user.
     *
     * @param toDisable the component that should be disabled
     * @param userId the id of the user where the component should be disabled.
     */
    public void disableComponent(ComponentName toDisable, int userId) {
        setComponentEnabledSetting(
                IPackageManager.Stub.asInterface(ServiceManager.getService("package")),
                toDisable,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                userId);
    }

    /**
     * Enables a given component in a given user.
     *
     * @param toEnable the component that should be enabled
     * @param userId the id of the user where the component should be disabled.
     */
    public void enableComponent(ComponentName toEnable, int userId) {
        setComponentEnabledSetting(
                IPackageManager.Stub.asInterface(ServiceManager.getService("package")),
                toEnable,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                userId);
    }

    /**
     * Disables a given component in a given user.
     *
     * @param ipm an {@link IPackageManager} object
     * @param toDisable the component that should be disabled
     * @param userId the id of the user where the component should be disabled.
     */
    @VisibleForTesting
    void setComponentEnabledSetting(IPackageManager ipm, ComponentName toDisable,
            int enabledSetting, int userId) {
        try {
            ipm.setComponentEnabledSetting(toDisable,
                    enabledSetting, PackageManager.DONT_KILL_APP,
                    userId);
        } catch (RemoteException neverThrown) {
            ProvisionLogger.loge("This should not happen.", neverThrown);
        } catch (Exception e) {
            ProvisionLogger.logw("Component not found, not changing enabled setting: "
                + toDisable.toShortString());
        }
    }

    /**
     * Check the validity of the admin component name supplied, or try to infer this componentName
     * from the package.
     *
     * We are supporting lookup by package name for legacy reasons.
     *
     * If dpcComponentName is supplied (not null): dpcPackageName is ignored.
     * Check that the package of dpcComponentName is installed, that dpcComponentName is a
     * receiver in this package, and return it. The receiver can be in disabled state.
     *
     * Otherwise: dpcPackageName must be supplied (not null).
     * Check that this package is installed, try to infer a potential device admin in this package,
     * and return it.
     */
    @NonNull
    public ComponentName findDeviceAdmin(String dpcPackageName, ComponentName dpcComponentName,
            Context context) throws IllegalProvisioningArgumentException {
        if (dpcComponentName != null) {
            dpcPackageName = dpcComponentName.getPackageName();
        }
        if (dpcPackageName == null) {
            throw new IllegalProvisioningArgumentException("Neither the package name nor the"
                    + " component name of the admin are supplied");
        }
        PackageInfo pi;
        try {
            pi = context.getPackageManager().getPackageInfo(dpcPackageName,
                    PackageManager.GET_RECEIVERS | PackageManager.MATCH_DISABLED_COMPONENTS);
        } catch (NameNotFoundException e) {
            throw new IllegalProvisioningArgumentException("Dpc "+ dpcPackageName
                    + " is not installed. ", e);
        }

        final ComponentName componentName = findDeviceAdminInPackageInfo(dpcPackageName,
                dpcComponentName, pi);
        if (componentName == null) {
            throw new IllegalProvisioningArgumentException("Cannot find any admin receiver in "
                    + "package " + dpcPackageName + " with component " + dpcComponentName);
        }
        return componentName;
    }

    /**
     * If dpcComponentName is not null: dpcPackageName is ignored.
     * Check that the package of dpcComponentName is installed, that dpcComponentName is a
     * receiver in this package, and return it. The receiver can be in disabled state.
     *
     * Otherwise, try to infer a potential device admin component in this package info.
     *
     * @return infered device admin component in package info. Otherwise, null
     */
    @Nullable
    public ComponentName findDeviceAdminInPackageInfo(@NonNull String dpcPackageName,
            @Nullable ComponentName dpcComponentName, @NonNull PackageInfo pi) {
        if (dpcComponentName != null) {
            if (!isComponentInPackageInfo(dpcComponentName, pi)) {
                ProvisionLogger.logw("The component " + dpcComponentName + " isn't registered in "
                        + "the apk");
                return null;
            }
            return dpcComponentName;
        } else {
            return findDeviceAdminInPackage(dpcPackageName, pi);
        }
    }

    /**
     * Finds a device admin in a given {@link PackageInfo} object.
     *
     * <p>This function returns {@code null} if no or multiple admin receivers were found, and if
     * the package name does not match dpcPackageName.</p>
     * @param packageName packge name that should match the {@link PackageInfo} object.
     * @param packageInfo package info to be examined.
     * @return admin receiver or null in case of error.
     */
    @Nullable
    private ComponentName findDeviceAdminInPackage(String packageName, PackageInfo packageInfo) {
        if (packageInfo == null || !TextUtils.equals(packageInfo.packageName, packageName)) {
            return null;
        }

        ComponentName mdmComponentName = null;
        for (ActivityInfo ai : packageInfo.receivers) {
            if (TextUtils.equals(ai.permission, android.Manifest.permission.BIND_DEVICE_ADMIN)) {
                if (mdmComponentName != null) {
                    ProvisionLogger.logw("more than 1 device admin component are found");
                    return null;
                } else {
                    mdmComponentName = new ComponentName(packageName, ai.name);
                }
            }
        }
        return mdmComponentName;
    }

    private boolean isComponentInPackageInfo(ComponentName dpcComponentName,
            PackageInfo pi) {
        for (ActivityInfo ai : pi.receivers) {
            if (dpcComponentName.getClassName().equals(ai.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return if a given package has testOnly="true", in which case we'll relax certain rules
     * for CTS.
     *
     * The system allows this flag to be changed when an app is updated. But
     * {@link DevicePolicyManager} uses the persisted version to do actual checks for relevant
     * dpm command.
     *
     * @see DevicePolicyManagerService#isPackageTestOnly for more info
     */
    public boolean isPackageTestOnly(PackageManager pm, String packageName, int userHandle) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }

        try {
            final ApplicationInfo ai = pm.getApplicationInfoAsUser(packageName,
                    PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE, userHandle);
            return ai != null && (ai.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

    }

    /**
     * Returns whether the current user is the system user.
     */
    public boolean isCurrentUserSystem() {
        return UserHandle.myUserId() == UserHandle.USER_SYSTEM;
    }

    /**
     * Returns whether the device is currently managed.
     */
    public boolean isDeviceManaged(Context context) {
        DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm.isDeviceManaged();
    }

    /**
     * Returns true if the given package requires an update.
     *
     * <p>There are two cases where an update is required:
     * 1. The package is not currently present on the device.
     * 2. The package is present, but the version is below the minimum supported version.
     *
     * @param packageName the package to be checked for updates
     * @param minSupportedVersion the minimum supported version
     * @param context a {@link Context} object
     */
    public boolean packageRequiresUpdate(String packageName, int minSupportedVersion,
            Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
            // Always download packages if no minimum version given.
            if (minSupportedVersion != PackageDownloadInfo.DEFAULT_MINIMUM_VERSION
                    && packageInfo.versionCode >= minSupportedVersion) {
                return false;
            }
        } catch (NameNotFoundException e) {
            // Package not on device.
        }

        return true;
    }

    /**
     * Returns the first existing managed profile if any present, null otherwise.
     *
     * <p>Note that we currently only support one managed profile per device.
     */
    // TODO: Add unit tests
    public UserHandle getManagedProfile(Context context) {
        UserManager userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        int currentUserId = userManager.getUserHandle();
        List<UserInfo> userProfiles = userManager.getProfiles(currentUserId);
        for (UserInfo profile : userProfiles) {
            if (profile.isManagedProfile()) {
                return new UserHandle(profile.id);
            }
        }
        return null;
    }

    /**
     * Returns the user id of an already existing managed profile or -1 if none exists.
     */
    // TODO: Add unit tests
    public int alreadyHasManagedProfile(Context context) {
        UserHandle managedUser = getManagedProfile(context);
        if (managedUser != null) {
            return managedUser.getIdentifier();
        } else {
            return -1;
        }
    }

    /**
     * Removes an account.
     *
     * <p>This removes the given account from the calling user's list of accounts.
     *
     * @param context a {@link Context} object
     * @param account the account to be removed
     */
    // TODO: Add unit tests
    public void removeAccount(Context context, Account account) {
        try {
            AccountManager accountManager =
                    (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
            AccountManagerFuture<Bundle> bundle = accountManager.removeAccount(account,
                    null, null /* callback */, null /* handler */);
            // Block to get the result of the removeAccount operation
            if (bundle.getResult().getBoolean(AccountManager.KEY_BOOLEAN_RESULT, false)) {
                ProvisionLogger.logw("Account removed from the primary user.");
            } else {
                Intent removeIntent = (Intent) bundle.getResult().getParcelable(
                        AccountManager.KEY_INTENT);
                if (removeIntent != null) {
                    ProvisionLogger.logi("Starting activity to remove account");
                    TrampolineActivity.startActivity(context, removeIntent);
                } else {
                    ProvisionLogger.logw("Could not remove account from the primary user.");
                }
            }
        } catch (OperationCanceledException | AuthenticatorException | IOException e) {
            ProvisionLogger.logw("Exception removing account from the primary user.", e);
        }
    }

    /**
     * Returns whether FRP is supported on the device.
     */
    public boolean isFrpSupported(Context context) {
        Object pdbManager = context.getSystemService(Context.PERSISTENT_DATA_BLOCK_SERVICE);
        return pdbManager != null;
    }

    /**
     * Translates a given managed provisioning intent to its corresponding provisioning flow, using
     * the action from the intent.
     *
     * <p/>This is necessary because, unlike other provisioning actions which has 1:1 mapping, there
     * are multiple actions that can trigger the device owner provisioning flow. This includes
     * {@link ACTION_PROVISION_MANAGED_DEVICE}, {@link ACTION_NDEF_DISCOVERED} and
     * {@link ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE}. These 3 actions are equivalent
     * excepts they are sent from a different source.
     *
     * @return the appropriate DevicePolicyManager declared action for the given incoming intent.
     * @throws IllegalProvisioningArgumentException if intent is malformed
     */
    // TODO: Add unit tests
    public String mapIntentToDpmAction(Intent intent)
            throws IllegalProvisioningArgumentException {
        if (intent == null || intent.getAction() == null) {
            throw new IllegalProvisioningArgumentException("Null intent action.");
        }

        // Map the incoming intent to a DevicePolicyManager.ACTION_*, as there is a N:1 mapping in
        // some cases.
        String dpmProvisioningAction;
        switch (intent.getAction()) {
            // Trivial cases.
            case ACTION_PROVISION_MANAGED_DEVICE:
            case ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE:
            case ACTION_PROVISION_MANAGED_USER:
            case ACTION_PROVISION_MANAGED_PROFILE:
                dpmProvisioningAction = intent.getAction();
                break;

            // NFC cases which need to take mime-type into account.
            case ACTION_NDEF_DISCOVERED:
                String mimeType = intent.getType();
                if (mimeType == null) {
                    throw new IllegalProvisioningArgumentException(
                            "Unknown NFC bump mime-type: " + mimeType);
                }
                switch (mimeType) {
                    case MIME_TYPE_PROVISIONING_NFC:
                        dpmProvisioningAction = ACTION_PROVISION_MANAGED_DEVICE;
                        break;

                    default:
                        throw new IllegalProvisioningArgumentException(
                                "Unknown NFC bump mime-type: " + mimeType);
                }
                break;

            // Device owner provisioning from a trusted app.
            // TODO (b/27217042): review for new management modes in split system-user model
            case ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE:
                dpmProvisioningAction = ACTION_PROVISION_MANAGED_DEVICE;
                break;

            default:
                throw new IllegalProvisioningArgumentException("Unknown intent action "
                        + intent.getAction());
        }
        return dpmProvisioningAction;
    }

    /**
     * Sends an intent to trigger a factory reset.
     */
    // TODO: Move the FR intent into a Globals class.
    public void sendFactoryResetBroadcast(Context context, String reason) {
        Intent intent = new Intent(Intent.ACTION_FACTORY_RESET);
        // Send explicit broadcast due to Broadcast Limitations
        intent.setPackage("android");
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(Intent.EXTRA_REASON, reason);
        context.sendBroadcast(intent);
    }

    /**
     * Returns whether the given provisioning action is a profile owner action.
     */
    // TODO: Move the list of device owner actions into a Globals class.
    public final boolean isProfileOwnerAction(String action) {
        return action.equals(ACTION_PROVISION_MANAGED_PROFILE)
                || action.equals(ACTION_PROVISION_MANAGED_USER);
    }

    /**
     * Returns whether the given provisioning action is a device owner action.
     */
    // TODO: Move the list of device owner actions into a Globals class.
    public final boolean isDeviceOwnerAction(String action) {
        return action.equals(ACTION_PROVISION_MANAGED_DEVICE)
                || action.equals(ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE);
    }

    /**
     * Returns whether the device currently has connectivity.
     */
    public boolean isConnectedToNetwork(Context context) {
        NetworkInfo info = getActiveNetworkInfo(context);
        return info != null && info.isConnected();
    }

    /**
     * Returns whether the device is currently connected to a wifi.
     */
    public boolean isConnectedToWifi(Context context) {
        NetworkInfo info = getActiveNetworkInfo(context);
        return info != null
                && info.isConnected()
                && info.getType() == ConnectivityManager.TYPE_WIFI;
    }

    /**
     * Returns the active network info of the device.
     */
    public NetworkInfo getActiveNetworkInfo(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo();
    }

    /**
     * Returns whether encryption is required on this device.
     *
     * <p>Encryption is required if the device is not currently encrypted and the persistent
     * system flag {@code persist.sys.no_req_encrypt} is not set.
     */
    public boolean isEncryptionRequired() {
        return !isPhysicalDeviceEncrypted()
                && !SystemProperties.getBoolean("persist.sys.no_req_encrypt", false);
    }

    /**
     * Returns whether the device is currently encrypted.
     */
    public boolean isPhysicalDeviceEncrypted() {
        return StorageManager.isEncrypted();
    }

    /**
     * Returns the wifi pick intent.
     */
    // TODO: Move this intent into a Globals class.
    public Intent getWifiPickIntent() {
        Intent wifiIntent = new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);
        wifiIntent.putExtra("extra_prefs_show_button_bar", true);
        wifiIntent.putExtra("wifi_enable_next_on_connect", true);
        return wifiIntent;
    }

    /**
     * Returns whether the device has a split system user.
     *
     * <p>Split system user means that user 0 is system only and all meat users are separate from
     * the system user.
     */
    public boolean isSplitSystemUser() {
        return UserManager.isSplitSystemUser();
    }

    /**
     * Returns whether the currently chosen launcher supports managed profiles.
     *
     * <p>A launcher is deemed to support managed profiles when its target API version is at least
     * {@link Build.VERSION_CODES#LOLLIPOP}.
     */
    public boolean currentLauncherSupportsManagedProfiles(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);

        PackageManager pm = context.getPackageManager();
        ResolveInfo launcherResolveInfo = pm.resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (launcherResolveInfo == null) {
            return false;
        }
        try {
            // If the user has not chosen a default launcher, then launcherResolveInfo will be
            // referring to the resolver activity. It is fine to create a managed profile in
            // this case since there will always be at least one launcher on the device that
            // supports managed profile feature.
            ApplicationInfo launcherAppInfo = pm.getApplicationInfo(
                    launcherResolveInfo.activityInfo.packageName, 0 /* default flags */);
            return versionNumberAtLeastL(launcherAppInfo.targetSdkVersion);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Returns whether the given version number is at least lollipop.
     *
     * @param versionNumber the version number to be verified.
     */
    private boolean versionNumberAtLeastL(int versionNumber) {
        return versionNumber >= Build.VERSION_CODES.LOLLIPOP;
    }

    /**
     * Computes the sha 256 hash of a byte array.
     */
    @Nullable
    public byte[] computeHashOfByteArray(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance(SHA256_TYPE);
            md.update(bytes);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            ProvisionLogger.loge("Hashing algorithm " + SHA256_TYPE + " not supported.", e);
            return null;
        }
    }

    /**
     * Computes a hash of a file with a spcific hash algorithm.
     */
    // TODO: Add unit tests
    @Nullable
    public byte[] computeHashOfFile(String fileLocation, String hashType) {
        InputStream fis = null;
        MessageDigest md;
        byte hash[] = null;
        try {
            md = MessageDigest.getInstance(hashType);
        } catch (NoSuchAlgorithmException e) {
            ProvisionLogger.loge("Hashing algorithm " + hashType + " not supported.", e);
            return null;
        }
        try {
            fis = new FileInputStream(fileLocation);

            byte[] buffer = new byte[256];
            int n = 0;
            while (n != -1) {
                n = fis.read(buffer);
                if (n > 0) {
                    md.update(buffer, 0, n);
                }
            }
            hash = md.digest();
        } catch (IOException e) {
            ProvisionLogger.loge("IO error.", e);
        } finally {
            // Close input stream quietly.
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                // Ignore.
            }
        }
        return hash;
    }

    public boolean isBrightColor(int color) {
        // This comes from the YIQ transformation. We're using the formula:
        // Y = .299 * R + .587 * G + .114 * B
        return Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114
                >= 1000 * THRESHOLD_BRIGHT_COLOR;
    }

    /**
     * Returns whether given intent can be resolved for the user.
     */
    public boolean canResolveIntentAsUser(Context context, Intent intent, int userId) {
        return intent != null
                && context.getPackageManager().resolveActivityAsUser(intent, 0, userId) != null;
    }

    public boolean isPackageDeviceOwner(DevicePolicyManager dpm, String packageName) {
        final ComponentName deviceOwner = dpm.getDeviceOwnerComponentOnCallingUser();
        return deviceOwner != null && deviceOwner.getPackageName().equals(packageName);
    }
}
