/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.verifier.managedprovisioning;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.ProxyInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.android.cts.verifier.R;
import com.android.cts.verifier.managedprovisioning.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CommandReceiverActivity extends Activity {
    private static final String TAG = "CommandReceiverActivity";

    public static final String ACTION_EXECUTE_COMMAND =
            "com.android.cts.verifier.managedprovisioning.action.EXECUTE_COMMAND";
    public static final String EXTRA_COMMAND =
            "com.android.cts.verifier.managedprovisioning.extra.COMMAND";

    public static final String COMMAND_SET_USER_RESTRICTION = "set-user_restriction";
    public static final String COMMAND_DISALLOW_KEYGUARD_UNREDACTED_NOTIFICATIONS =
            "disallow-keyguard-unredacted-notifications";
    public static final String COMMAND_SET_AUTO_TIME_REQUIRED = "set-auto-time-required";
    public static final String COMMAND_SET_GLOBAL_SETTING =
            "set-global-setting";
    public static final String COMMAND_SET_MAXIMUM_TO_LOCK = "set-maximum-time-to-lock";
    public static final String COMMAND_SET_PASSWORD_QUALITY = "set-password-quality";
    public static final String COMMAND_SET_KEYGUARD_DISABLED = "set-keyguard-disabled";
    public static final String COMMAND_SET_LOCK_SCREEN_INFO = "set-lock-screen-info";
    public static final String COMMAND_SET_STATUSBAR_DISABLED = "set-statusbar-disabled";
    public static final String COMMAND_ALLOW_ONLY_SYSTEM_INPUT_METHODS =
            "allow-only-system-input-methods";
    public static final String COMMAND_ALLOW_ONLY_SYSTEM_ACCESSIBILITY_SERVICES =
            "allow-only-system-accessibility-services";
    public static final String COMMAND_CLEAR_POLICIES = "clear-policies";
    public static final String COMMAND_REMOVE_DEVICE_OWNER = "remove-device-owner";
    public static final String COMMAND_REQUEST_BUGREPORT = "request-bugreport";
    public static final String COMMAND_SET_USER_ICON = "set-user-icon";
    public static final String COMMAND_RETRIEVE_NETWORK_LOGS = "retrieve-network-logs";
    public static final String COMMAND_RETRIEVE_SECURITY_LOGS = "retrieve-security-logs";
    public static final String COMMAND_SET_ORGANIZATION_NAME = "set-organization-name";
    public static final String COMMAND_ENABLE_NETWORK_LOGGING = "enable-network-logging";
    public static final String COMMAND_DISABLE_NETWORK_LOGGING = "disable-network-logging";
    public static final String COMMAND_INSTALL_HELPER_PACKAGE = "install-helper-package";
    public static final String COMMAND_UNINSTALL_HELPER_PACKAGE = "uninstall-helper-package";
    public static final String COMMAND_SET_PERMISSION_GRANT_STATE = "set-permission-grant-state";
    public static final String COMMAND_ADD_PERSISTENT_PREFERRED_ACTIVITIES =
            "add-persistent-preferred-activities";
    public static final String COMMAND_CLEAR_PERSISTENT_PREFERRED_ACTIVITIES =
            "clear-persistent-preferred-activities";
    public static final String COMMAND_CREATE_MANAGED_PROFILE = "create-managed-profile";
    public static final String COMMAND_REMOVE_MANAGED_PROFILE = "remove-managed-profile";
    public static final String COMMAND_SET_ALWAYS_ON_VPN = "set-always-on-vpn";
    public static final String COMMAND_CLEAR_ALWAYS_ON_VPN = "clear-always-on-vpn";
    public static final String COMMAND_SET_GLOBAL_HTTP_PROXY = "set-global-http-proxy";
    public static final String COMMAND_CLEAR_GLOBAL_HTTP_PROXY = "clear-global-http-proxy";
    public static final String COMMAND_INSTALL_CA_CERT = "install-ca-cert";
    public static final String COMMAND_CLEAR_CA_CERT = "clear-ca-cert";
    public static final String COMMAND_SET_MAXIMUM_PASSWORD_ATTEMPTS =
            "set-maximum-password-attempts";
    public static final String COMMAND_CLEAR_MAXIMUM_PASSWORD_ATTEMPTS =
            "clear-maximum-password-attempts";
    public static final String COMMAND_SET_DEFAULT_IME = "set-default-ime";
    public static final String COMMAND_CLEAR_DEFAULT_IME = "clear-default-ime";

    public static final String EXTRA_USER_RESTRICTION =
            "com.android.cts.verifier.managedprovisioning.extra.USER_RESTRICTION";
    public static final String EXTRA_SETTING =
            "com.android.cts.verifier.managedprovisioning.extra.SETTING";
    // This extra can be used along with a command extra to set policy to
    // specify if that policy is enforced or not.
    public static final String EXTRA_ENFORCED =
            "com.android.cts.verifier.managedprovisioning.extra.ENFORCED";
    public static final String EXTRA_VALUE =
            "com.android.cts.verifier.managedprovisioning.extra.VALUE";
    public static final String EXTRA_ORGANIZATION_NAME =
            "com.android.cts.verifier.managedprovisioning.extra.ORGANIZATION_NAME";
    public static final String EXTRA_PERMISSION =
            "com.android.cts.verifier.managedprovisioning.extra.PERMISSION";
    public static final String EXTRA_GRANT_STATE =
            "com.android.cts.verifier.managedprovisioning.extra.GRANT_STATE";

    // We care about installing and uninstalling only. It does not matter what apk is used.
    // NotificationBot.apk is a good choice because it comes bundled with the CTS verifier.
    protected static final String HELPER_APP_LOCATION = "/sdcard/NotificationBot.apk";
    protected static final String HELPER_APP_PKG = "com.android.cts.robot";

    public static final String ACTION_INSTALL_COMPLETE =
            "com.android.cts.verifier.managedprovisioning.action.ACTION_INSTALL_COMPLETE";
    public static final String ACTION_UNINSTALL_COMPLETE =
            "com.android.cts.verifier.managedprovisioning.action.ACTION_UNINSTALL_COMPLETE";

    /*
     * The CA cert below is the content of cacert.pem as generated by:
     *
     * openssl req -new -x509 -days 3650 -extensions v3_ca -keyout cakey.pem -out cacert.pem
     */
    private static final String TEST_CA =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIIDXTCCAkWgAwIBAgIJAK9Tl/F9V8kSMA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNV\n" +
            "BAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRlcm5ldCBX\n" +
            "aWRnaXRzIFB0eSBMdGQwHhcNMTUwMzA2MTczMjExWhcNMjUwMzAzMTczMjExWjBF\n" +
            "MQswCQYDVQQGEwJBVTETMBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UECgwYSW50\n" +
            "ZXJuZXQgV2lkZ2l0cyBQdHkgTHRkMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB\n" +
            "CgKCAQEAvItOutsE75WBTgTyNAHt4JXQ3JoseaGqcC3WQij6vhrleWi5KJ0jh1/M\n" +
            "Rpry7Fajtwwb4t8VZa0NuM2h2YALv52w1xivql88zce/HU1y7XzbXhxis9o6SCI+\n" +
            "oVQSbPeXRgBPppFzBEh3ZqYTVhAqw451XhwdA4Aqs3wts7ddjwlUzyMdU44osCUg\n" +
            "kVg7lfPf9sTm5IoHVcfLSCWH5n6Nr9sH3o2ksyTwxuOAvsN11F/a0mmUoPciYPp+\n" +
            "q7DzQzdi7akRG601DZ4YVOwo6UITGvDyuAAdxl5isovUXqe6Jmz2/myTSpAKxGFs\n" +
            "jk9oRoG6WXWB1kni490GIPjJ1OceyQIDAQABo1AwTjAdBgNVHQ4EFgQUH1QIlPKL\n" +
            "p2OQ/AoLOjKvBW4zK3AwHwYDVR0jBBgwFoAUH1QIlPKLp2OQ/AoLOjKvBW4zK3Aw\n" +
            "DAYDVR0TBAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAcMi4voMMJHeQLjtq8Oky\n" +
            "Azpyk8moDwgCd4llcGj7izOkIIFqq/lyqKdtykVKUWz2bSHO5cLrtaOCiBWVlaCV\n" +
            "DYAnnVLM8aqaA6hJDIfaGs4zmwz0dY8hVMFCuCBiLWuPfiYtbEmjHGSmpQTG6Qxn\n" +
            "ZJlaK5CZyt5pgh5EdNdvQmDEbKGmu0wpCq9qjZImwdyAul1t/B0DrsWApZMgZpeI\n" +
            "d2od0VBrCICB1K4p+C51D93xyQiva7xQcCne+TAnGNy9+gjQ/MyR8MRpwRLv5ikD\n" +
            "u0anJCN8pXo6IMglfMAsoton1J6o5/ae5uhC6caQU8bNUsCK570gpNfjkzo6rbP0\n" +
            "wQ==\n" +
            "-----END CERTIFICATE-----";

    private ComponentName mAdmin;
    private DevicePolicyManager mDpm;
    private UserManager mUm;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        try {
            mDpm = (DevicePolicyManager) getSystemService(
                    Context.DEVICE_POLICY_SERVICE);
            mUm = (UserManager) getSystemService(Context.USER_SERVICE);
            mAdmin = DeviceAdminTestReceiver.getReceiverComponentName();
            Log.i(TAG, "Command: " + intent);

            final String command = getIntent().getStringExtra(EXTRA_COMMAND);
            switch (command) {
                case COMMAND_SET_USER_RESTRICTION: {
                    String restrictionKey = intent.getStringExtra(EXTRA_USER_RESTRICTION);
                    boolean enforced = intent.getBooleanExtra(EXTRA_ENFORCED, false);
                    if (enforced) {
                        mDpm.addUserRestriction(mAdmin, restrictionKey);
                    } else {
                        mDpm.clearUserRestriction(mAdmin, restrictionKey);
                    }
                } break;
                case COMMAND_DISALLOW_KEYGUARD_UNREDACTED_NOTIFICATIONS: {
                    boolean enforced = intent.getBooleanExtra(EXTRA_ENFORCED, false);
                    mDpm.setKeyguardDisabledFeatures(mAdmin, enforced
                            ? DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS
                            : 0);
                } break;
                case COMMAND_SET_AUTO_TIME_REQUIRED: {
                    mDpm.setAutoTimeRequired(mAdmin,
                            intent.getBooleanExtra(EXTRA_ENFORCED, false));
                }
                case COMMAND_SET_LOCK_SCREEN_INFO: {
                    mDpm.setDeviceOwnerLockScreenInfo(mAdmin, intent.getStringExtra(EXTRA_VALUE));
                }
                case COMMAND_SET_MAXIMUM_TO_LOCK: {
                    final long timeInSeconds = Long.parseLong(intent.getStringExtra(EXTRA_VALUE));
                    mDpm.setMaximumTimeToLock(mAdmin,
                            TimeUnit.SECONDS.toMillis(timeInSeconds) /* in milliseconds */);
                } break;
                case COMMAND_SET_PASSWORD_QUALITY: {
                    int quality = intent.getIntExtra(EXTRA_VALUE, 0);
                    mDpm.setPasswordQuality(mAdmin, quality);
                } break;
                case COMMAND_SET_KEYGUARD_DISABLED: {
                    boolean enforced = intent.getBooleanExtra(EXTRA_ENFORCED, false);
                    KeyguardManager km = this.getSystemService(KeyguardManager.class);
                    if (km.isKeyguardSecure()) {
                        Toast.makeText(this, getString(R.string.device_owner_lockscreen_secure),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        mDpm.setKeyguardDisabled(mAdmin, enforced);
                    }
                } break;
                case COMMAND_SET_STATUSBAR_DISABLED: {
                    boolean enforced = intent.getBooleanExtra(EXTRA_ENFORCED, false);
                    mDpm.setStatusBarDisabled(mAdmin, enforced);
                } break;
                case COMMAND_ALLOW_ONLY_SYSTEM_INPUT_METHODS: {
                    boolean enforced = intent.getBooleanExtra(EXTRA_ENFORCED, false);
                    mDpm.setPermittedInputMethods(mAdmin, enforced ? new ArrayList() : null);
                } break;
                case COMMAND_ALLOW_ONLY_SYSTEM_ACCESSIBILITY_SERVICES: {
                    boolean enforced = intent.getBooleanExtra(EXTRA_ENFORCED, false);
                    mDpm.setPermittedAccessibilityServices(mAdmin,
                            enforced ? new ArrayList() : null);
                } break;
                case COMMAND_SET_GLOBAL_SETTING: {
                    final String setting = intent.getStringExtra(EXTRA_SETTING);
                    final String value = intent.getStringExtra(EXTRA_VALUE);
                    mDpm.setGlobalSetting(mAdmin, setting, value);
                } break;
                case COMMAND_REMOVE_DEVICE_OWNER: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    clearAllPoliciesAndRestrictions();
                    mDpm.clearDeviceOwnerApp(getPackageName());
                } break;
                case COMMAND_REQUEST_BUGREPORT: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    final boolean bugreportStarted = mDpm.requestBugreport(mAdmin);
                    if (!bugreportStarted) {
                        Utils.showBugreportNotification(this, getString(
                                R.string.bugreport_already_in_progress),
                                Utils.BUGREPORT_NOTIFICATION_ID);
                    }
                } break;
                case COMMAND_CLEAR_POLICIES: {
                    int mode = intent.getIntExtra(PolicyTransparencyTestListActivity.EXTRA_MODE,
                            PolicyTransparencyTestListActivity.MODE_DEVICE_OWNER);
                    if (mode == PolicyTransparencyTestListActivity.MODE_DEVICE_OWNER) {
                        if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                            return;
                        }
                        clearAllPoliciesAndRestrictions();
                    } else if(mode == PolicyTransparencyTestListActivity.MODE_PROFILE_OWNER) {
                        if (!mDpm.isProfileOwnerApp(getPackageName())) {
                            return;
                        }
                        clearProfileOwnerRelatedPoliciesAndRestrictions();
                    }
                    // No policies need to be cleared for COMP at the moment.
                } break;
                case COMMAND_SET_USER_ICON: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setUserIcon(mAdmin, BitmapFactory.decodeResource(getResources(),
                            com.android.cts.verifier.R.drawable.icon));
                } break;
                case COMMAND_RETRIEVE_NETWORK_LOGS: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setNetworkLoggingEnabled(mAdmin, true);
                    mDpm.retrieveNetworkLogs(mAdmin, 0 /* batchToken */);
                    mDpm.setNetworkLoggingEnabled(mAdmin, false);
                } break;
                case COMMAND_RETRIEVE_SECURITY_LOGS: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setSecurityLoggingEnabled(mAdmin, true);
                    mDpm.retrieveSecurityLogs(mAdmin);
                    mDpm.setSecurityLoggingEnabled(mAdmin, false);
                } break;
                case COMMAND_SET_ORGANIZATION_NAME: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setOrganizationName(mAdmin,
                            intent.getStringExtra(EXTRA_ORGANIZATION_NAME));
                } break;
                case COMMAND_ENABLE_NETWORK_LOGGING: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setNetworkLoggingEnabled(mAdmin, true);
                } break;
                case COMMAND_DISABLE_NETWORK_LOGGING: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setNetworkLoggingEnabled(mAdmin, false);
                } break;
                case COMMAND_INSTALL_HELPER_PACKAGE: {
                    installHelperPackage();
                } break;
                case COMMAND_UNINSTALL_HELPER_PACKAGE: {
                    uninstallHelperPackage();
                } break;
                case COMMAND_SET_PERMISSION_GRANT_STATE: {
                    mDpm.setPermissionGrantState(mAdmin, getPackageName(),
                            intent.getStringExtra(EXTRA_PERMISSION),
                            intent.getIntExtra(EXTRA_GRANT_STATE,
                                    DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT));
                } break;
                case COMMAND_ADD_PERSISTENT_PREFERRED_ACTIVITIES: {
                    final ComponentName componentName =
                            EnterprisePrivacyTestDefaultAppActivity.COMPONENT_NAME;
                    // Browser
                    IntentFilter filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_VIEW);
                    filter.addCategory(Intent.CATEGORY_BROWSABLE);
                    filter.addDataScheme("http");
                    mDpm.addPersistentPreferredActivity(mAdmin, filter, componentName);
                    // Camera
                    filter = new IntentFilter();
                    filter.addAction(MediaStore.ACTION_IMAGE_CAPTURE);
                    filter.addAction(MediaStore.ACTION_VIDEO_CAPTURE);
                    mDpm.addPersistentPreferredActivity(mAdmin, filter, componentName);
                    // Map
                    filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_VIEW);
                    filter.addDataScheme("geo");
                    mDpm.addPersistentPreferredActivity(mAdmin, filter, componentName);
                    // E-mail
                    filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_SENDTO);
                    filter.addAction(Intent.ACTION_SEND);
                    filter.addAction(Intent.ACTION_SEND_MULTIPLE);
                    mDpm.addPersistentPreferredActivity(mAdmin, filter, componentName);
                    // Calendar
                    filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_INSERT);
                    filter.addDataType("vnd.android.cursor.dir/event");
                    mDpm.addPersistentPreferredActivity(mAdmin, filter, componentName);
                    // Contacts
                    filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_PICK);
                    filter.addDataType(ContactsContract.Contacts.CONTENT_TYPE);
                    mDpm.addPersistentPreferredActivity(mAdmin, filter, componentName);
                    // Dialer
                    filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_DIAL);
                    filter.addAction(Intent.ACTION_CALL);
                    mDpm.addPersistentPreferredActivity(mAdmin, filter, componentName);
                    getPackageManager().setComponentEnabledSetting(componentName,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP);
                } break;
                case COMMAND_CLEAR_PERSISTENT_PREFERRED_ACTIVITIES: {
                    mDpm.clearPackagePersistentPreferredActivities(mAdmin, getPackageName());
                    getPackageManager().setComponentEnabledSetting(
                            EnterprisePrivacyTestDefaultAppActivity.COMPONENT_NAME,
                            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                            PackageManager.DONT_KILL_APP);
                } break;
                case COMMAND_CREATE_MANAGED_PROFILE: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    if (mUm.getUserProfiles().size() > 1) {
                        return;
                    }
                    startActivityForResult(new Intent(
                            DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
                            .putExtra(DevicePolicyManager
                                    .EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                                    CompDeviceAdminTestReceiver.getReceiverComponentName())
                            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
                            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_USER_CONSENT,
                                true), 0);
                } break;
                case COMMAND_REMOVE_MANAGED_PROFILE: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    removeManagedProfile();
                } break;
                case COMMAND_SET_ALWAYS_ON_VPN: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setAlwaysOnVpnPackage(mAdmin, getPackageName(),
                            false /* lockdownEnabled */);
                } break;
                case COMMAND_CLEAR_ALWAYS_ON_VPN: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setAlwaysOnVpnPackage(mAdmin, null /* vpnPackage */,
                            false /* lockdownEnabled */);
                } break;
                case COMMAND_SET_GLOBAL_HTTP_PROXY: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setRecommendedGlobalProxy(mAdmin,
                            ProxyInfo.buildDirectProxy("example.com", 123));
                } break;
                case COMMAND_CLEAR_GLOBAL_HTTP_PROXY: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setRecommendedGlobalProxy(mAdmin, null);
                }
                case COMMAND_INSTALL_CA_CERT: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.installCaCert(mAdmin, TEST_CA.getBytes());
                } break;
                case COMMAND_CLEAR_CA_CERT: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.uninstallCaCert(mAdmin, TEST_CA.getBytes());
                } break;
                case COMMAND_SET_MAXIMUM_PASSWORD_ATTEMPTS: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setMaximumFailedPasswordsForWipe(mAdmin, 100);
                } break;
                case COMMAND_CLEAR_MAXIMUM_PASSWORD_ATTEMPTS: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setMaximumFailedPasswordsForWipe(mAdmin, 0);
                } break;
                case COMMAND_SET_DEFAULT_IME: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setSecureSetting(mAdmin, Settings.Secure.DEFAULT_INPUT_METHOD,
                            getPackageName());
                } break;
                case COMMAND_CLEAR_DEFAULT_IME: {
                    if (!mDpm.isDeviceOwnerApp(getPackageName())) {
                        return;
                    }
                    mDpm.setSecureSetting(mAdmin, Settings.Secure.DEFAULT_INPUT_METHOD, null);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute command: " + intent, e);
        } finally {
            finish();
        }
    }

    private void installHelperPackage() throws Exception {
        final PackageInstaller packageInstaller = getPackageManager().getPackageInstaller();
        final PackageInstaller.Session session = packageInstaller.openSession(
                packageInstaller.createSession(new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL)));
        final File file = new File(HELPER_APP_LOCATION);
        final InputStream in = new FileInputStream(file);
        final OutputStream out = session.openWrite("CommandReceiverActivity", 0, file.length());
        final byte[] buffer = new byte[65536];
        int count;
        while ((count = in.read(buffer)) != -1) {
            out.write(buffer, 0, count);
        }
        session.fsync(out);
        in.close();
        out.close();
        session.commit(PendingIntent.getBroadcast(this, 0, new Intent(ACTION_INSTALL_COMPLETE), 0)
                .getIntentSender());
    }

    private void uninstallHelperPackage() {
        try {
            getPackageManager().getPackageInstaller().uninstall(HELPER_APP_PKG,
                    PendingIntent.getBroadcast(this, 0, new Intent(ACTION_UNINSTALL_COMPLETE), 0)
                            .getIntentSender());
        } catch (IllegalArgumentException e) {
            // The package is not installed: that's fine
        }
    }

    private void clearAllPoliciesAndRestrictions() throws Exception {
        clearProfileOwnerRelatedPolicies();
        clearPolicyTransparencyUserRestriction(
                PolicyTransparencyTestListActivity.MODE_DEVICE_OWNER);

        // There are a few user restrictions that are used, but not for policy transparency
        mDpm.clearUserRestriction(mAdmin, UserManager.DISALLOW_CONFIG_BLUETOOTH);
        mDpm.clearUserRestriction(mAdmin, UserManager.DISALLOW_CONFIG_VPN);
        mDpm.clearUserRestriction(mAdmin, UserManager.DISALLOW_DATA_ROAMING);

        mDpm.setDeviceOwnerLockScreenInfo(mAdmin, null);
        mDpm.setKeyguardDisabled(mAdmin, false);
        mDpm.setAutoTimeRequired(mAdmin, false);
        mDpm.setStatusBarDisabled(mAdmin, false);
        mDpm.setOrganizationName(mAdmin, null);
        mDpm.setNetworkLoggingEnabled(mAdmin, false);
        mDpm.setSecurityLoggingEnabled(mAdmin, false);
        mDpm.setPermissionGrantState(mAdmin, getPackageName(),
                Manifest.permission.ACCESS_FINE_LOCATION,
                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
        mDpm.setPermissionGrantState(mAdmin, getPackageName(), Manifest.permission.RECORD_AUDIO,
                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
        mDpm.setPermissionGrantState(mAdmin, getPackageName(), Manifest.permission.CAMERA,
                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
        mDpm.clearPackagePersistentPreferredActivities(mAdmin, getPackageName());
        mDpm.setAlwaysOnVpnPackage(mAdmin, null, false);
        mDpm.setRecommendedGlobalProxy(mAdmin, null);
        mDpm.uninstallCaCert(mAdmin, TEST_CA.getBytes());
        mDpm.setMaximumFailedPasswordsForWipe(mAdmin, 0);
        mDpm.setSecureSetting(mAdmin, Settings.Secure.DEFAULT_INPUT_METHOD, null);

        uninstallHelperPackage();
        removeManagedProfile();
        getPackageManager().setComponentEnabledSetting(
                EnterprisePrivacyTestDefaultAppActivity.COMPONENT_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP);
    }

    private void clearProfileOwnerRelatedPoliciesAndRestrictions() {
        clearPolicyTransparencyUserRestriction(
                PolicyTransparencyTestListActivity.MODE_PROFILE_OWNER);
        clearProfileOwnerRelatedPolicies();
    }

    private void clearProfileOwnerRelatedPolicies() {
        mDpm.setKeyguardDisabledFeatures(mAdmin, 0);
        mDpm.setPasswordQuality(mAdmin, 0);
        mDpm.setMaximumTimeToLock(mAdmin, 0);
        mDpm.setPermittedAccessibilityServices(mAdmin, null);
        mDpm.setPermittedInputMethods(mAdmin, null);
    }

    private void clearPolicyTransparencyUserRestriction(int mode) {
        for (String userRestriction : UserRestrictions.getUserRestrictionsForPolicyTransparency(
                mode)) {
            mDpm.clearUserRestriction(mAdmin, userRestriction);
        }
    }

    private void removeManagedProfile() {
        for (final UserHandle userHandle : mUm.getUserProfiles()) {
            mDpm.removeUser(mAdmin, userHandle);
        }
    }
}
