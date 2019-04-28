/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.task.nonrequiredapps;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_USER;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.inputmethod.InputMethodInfo;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.view.IInputMethodManager;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class that provides the apps that are not required on a managed device / profile according to the
 * overlays provided via (vendor_|)required_apps_managed_(profile|device).xml.
 */
public class OverlayPackagesProvider {

    private static final int DEVICE_OWNER = 0;
    private static final int PROFILE_OWNER = 1;
    private static final int MANAGED_USER = 2;

    private final PackageManager mPm;
    private final IInputMethodManager mIInputMethodManager;
    private final String mDpcPackageName;
    private final List<String> mRequiredAppsList;
    private final List<String> mDisallowedAppsList;
    private final List<String> mVendorRequiredAppsList;
    private final List<String> mVendorDisallowedAppsList;
    private final int mProvisioningType;
    private final boolean mLeaveAllSystemAppsEnabled;

    public OverlayPackagesProvider(Context context, ProvisioningParams params) {
        this(context, params, getIInputMethodManager());
    }

    @VisibleForTesting
    OverlayPackagesProvider(
            Context context,
            ProvisioningParams params,
            IInputMethodManager iInputMethodManager) {
        mPm = checkNotNull(context.getPackageManager());
        mIInputMethodManager = checkNotNull(iInputMethodManager);
        mDpcPackageName = checkNotNull(params.inferDeviceAdminPackageName());

        // For split system user devices that will have a system device owner, don't adjust the set
        // of enabled packages in the system user as we expect the right set of packages to be
        // enabled for the system user out of the box. For other devices, the set of available
        // packages can vary depending on management state.
        mLeaveAllSystemAppsEnabled = params.leaveAllSystemAppsEnabled ||
                params.provisioningAction.equals(ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE);

        int requiredAppsListArray;
        int vendorRequiredAppsListArray;
        int disallowedAppsListArray;
        int vendorDisallowedAppsListArray;
        switch (params.provisioningAction) {
            case ACTION_PROVISION_MANAGED_USER:
                mProvisioningType = MANAGED_USER;
                requiredAppsListArray = R.array.required_apps_managed_user;
                disallowedAppsListArray = R.array.disallowed_apps_managed_user;
                vendorRequiredAppsListArray = R.array.vendor_required_apps_managed_user;
                vendorDisallowedAppsListArray = R.array.vendor_disallowed_apps_managed_user;
                break;
            case ACTION_PROVISION_MANAGED_PROFILE:
                mProvisioningType = PROFILE_OWNER;
                requiredAppsListArray = R.array.required_apps_managed_profile;
                disallowedAppsListArray = R.array.disallowed_apps_managed_profile;
                vendorRequiredAppsListArray = R.array.vendor_required_apps_managed_profile;
                vendorDisallowedAppsListArray = R.array.vendor_disallowed_apps_managed_profile;
                break;
            case ACTION_PROVISION_MANAGED_DEVICE:
            case ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE:
                mProvisioningType = DEVICE_OWNER;
                requiredAppsListArray = R.array.required_apps_managed_device;
                disallowedAppsListArray = R.array.disallowed_apps_managed_device;
                vendorRequiredAppsListArray = R.array.vendor_required_apps_managed_device;
                vendorDisallowedAppsListArray = R.array.vendor_disallowed_apps_managed_device;
                break;
            default:
                throw new IllegalArgumentException("Provisioning action "
                        + params.provisioningAction + " not implemented.");
        }

        Resources resources = context.getResources();
        mRequiredAppsList = Arrays.asList(resources.getStringArray(requiredAppsListArray));
        mDisallowedAppsList = Arrays.asList(resources.getStringArray(disallowedAppsListArray));
        mVendorRequiredAppsList = Arrays.asList(
                resources.getStringArray(vendorRequiredAppsListArray));
        mVendorDisallowedAppsList = Arrays.asList(
                resources.getStringArray(vendorDisallowedAppsListArray));
    }

    /**
     * Computes non-required apps. All the system apps with a launcher that are not in
     * the required set of packages will be considered as non-required apps.
     *
     * Note: If an app is mistakenly listed as both required and disallowed, it will be treated as
     * disallowed.
     *
     * @param userId The userId for which the non-required apps needs to be computed.
     * @return the set of non-required apps.
     */
    public Set<String> getNonRequiredApps(int userId) {
        if (mLeaveAllSystemAppsEnabled) {
            return Collections.emptySet();
        }

        Set<String> nonRequiredApps = getCurrentAppsWithLauncher(userId);
        // Newly installed system apps are uninstalled when they are not required and are either
        // disallowed or have a launcher icon.
        nonRequiredApps.removeAll(getRequiredApps());
        // Don't delete the system input method packages in case of Device owner provisioning.
        if (mProvisioningType == DEVICE_OWNER || mProvisioningType == MANAGED_USER) {
            nonRequiredApps.removeAll(getSystemInputMethods());
        }
        nonRequiredApps.addAll(getDisallowedApps());
        return nonRequiredApps;
    }

    private Set<String> getCurrentAppsWithLauncher(int userId) {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = mPm.queryIntentActivitiesAsUser(launcherIntent,
                PackageManager.MATCH_UNINSTALLED_PACKAGES
                        | PackageManager.MATCH_DISABLED_COMPONENTS
                        | PackageManager.MATCH_DIRECT_BOOT_AWARE
                        | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                userId);
        Set<String> apps = new HashSet<>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            apps.add(resolveInfo.activityInfo.packageName);
        }
        return apps;
    }

    private Set<String> getSystemInputMethods() {
        // InputMethodManager is final so it cannot be mocked.
        // So, we're using IInputMethodManager directly because it can be mocked.
        List<InputMethodInfo> inputMethods;
        try {
            inputMethods = mIInputMethodManager.getInputMethodList();
        } catch (RemoteException e) {
            ProvisionLogger.loge("Could not communicate with IInputMethodManager", e);
            return Collections.emptySet();
        }
        Set<String> systemInputMethods = new HashSet<>();
        for (InputMethodInfo inputMethodInfo : inputMethods) {
            ApplicationInfo applicationInfo = inputMethodInfo.getServiceInfo().applicationInfo;
            if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                systemInputMethods.add(inputMethodInfo.getPackageName());
            }
        }
        return systemInputMethods;
    }

    private Set<String> getRequiredApps() {
        HashSet<String> requiredApps = new HashSet<>();
        requiredApps.addAll(mRequiredAppsList);
        requiredApps.addAll(mVendorRequiredAppsList);
        requiredApps.add(mDpcPackageName);
        return requiredApps;
    }

    private Set<String> getDisallowedApps() {
        HashSet<String> disallowedApps = new HashSet<>();
        disallowedApps.addAll(mDisallowedAppsList);
        disallowedApps.addAll(mVendorDisallowedAppsList);
        return disallowedApps;
    }

    private static IInputMethodManager getIInputMethodManager() {
        IBinder b = ServiceManager.getService(Context.INPUT_METHOD_SERVICE);
        return IInputMethodManager.Stub.asInterface(b);
    }
}
