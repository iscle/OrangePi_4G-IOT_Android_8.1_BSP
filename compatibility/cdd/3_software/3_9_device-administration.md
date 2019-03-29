## 3.9\. Device Administration

Android includes features that allow security-aware applications to perform
device administration functions at the system level, such as enforcing password
policies or performing remote wipe, through the
[Android Device Administration API](http://developer.android.com/guide/topics/admin/device-admin.html)].

If device implementations implement the full range of [device administration](
http://developer.android.com/guide/topics/admin/device-admin.html)
policies defined in the Android SDK documentation, they:

*   [C-1-1] MUST declare `android.software.device_admin`.
*   [C-1-2] MUST support device owner provisioning as described in
    [section 3.9.1](#3_9_1_device_provisioning) and
    [section 3.9.1.1](#3_9_1_1_device_owner_provisioning).
*   [C-1-3] MUST declare the support of manged profiles via the
    `android.software.managed_users` feature flag, except for when the device is
    configured so that it would [report](
    http://developer.android.com/reference/android/app/ActivityManager.html#isLowRamDevice%28%29)
    itself as a low RAM device or so that it allocate internal (non-removable)
    storage as shared storage.

### 3.9.1 Device Provisioning

#### 3.9.1.1 Device owner provisioning

If device implementations declare `android.software.device_admin`, they:

*   [C-1-1] MUST support enrolling a Device Policy Client (DPC) as a
    [Device Owner app](
    http://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#isDeviceOwnerApp%28java.lang.String%29)
    as described below:.
    *   when the device implementation has no user data is configured yet, it:
        *    [C-1-3] MUST report `true` for [`DevicePolicyManager.isProvisioningAllowed(ACTION_PROVISION_MANAGED_DEVICE)`](https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html\#isProvisioningAllowed\(java.lang.String\)).
        *    [C-1-4] MUST enroll the DPC application as the Device Owner app in
             response to the intent action [`android.app.action.PROVISION_MANAGED_DEVICE`](http://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#ACTION_PROVISION_MANAGED_DEVICE).
        *    [C-1-5] MUST enroll the DPC application as the Device Owner app if the
             device declares Near-Field Communications (NFC) support via the feature
             flag `android.hardware.nfc` and receives an NFC message containing a
             record with MIME type [`MIME_TYPE_PROVISIONING_NFC`](https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#MIME_TYPE_PROVISIONING_NFC).
    *   When the device implementation has user data, it:
        *    [C-1-6] MUST report `false` for the [`DevicePolicyManager.isProvisioningAllowed(ACTION_PROVISION_MANAGED_DEVICE)`](https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html\#isProvisioningAllowed\(java.lang.String\)).
        *    [C-1-7] MUST not enroll any DPC application as the Device Owner App
             any more.
*   [C-1-2] MUST NOT set an application (including pre-installed app) as the
    Device Owner app without explicit consent or action from the user or the
    administrator of the device.

If device implementations declare `android.software.device_admin`, but also
include a proprietary Device Owner management solution and provide a mechanism
to promote an application configured in their solution as a "Device Owner
equivalent" to the standard "Device Owner" as recognized by the standard Android
[DevicePolicyManager](
http://developer.android.com/reference/android/app/admin/DevicePolicyManager.html)
APIs, they:

*    [C-2-1] MUST have a process in place to verify that the specific app
     being promoted belongs to a legitimate enterprise device management
     solution and it has been already configured in the proprietary solution
     to have the rights equivalent as a "Device Owner".
*    [C-2-2] MUST show the same AOSP Device Owner consent disclosure as the
     flow initiated by [`android.app.action.PROVISION_MANAGED_DEVICE`](http://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#ACTION_PROVISION_MANAGED_DEVICE)
     prior to enrolling the DPC application as "Device Owner".
*    MAY have user data on the device prior to enrolling the DPC application
     as "Device Owner".

#### 3.9.1.2 Managed profile provisioning

If device implementations declare `android.software.managed_users`, they:

*   [C-1-1] MUST implement the [APIs](http://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#ACTION_PROVISION_MANAGED_PROFILE)
allowing a Device Policy Controller (DPC) application to become the
[owner of a new Managed Profile](http://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#isProfileOwnerApp%28java.lang.String%29).

*   [C-1-2] The managed profile provisioning process (the flow initiated by
[android.app.action.PROVISION_MANAGED_PROFILE](
http://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#ACTION_PROVISION_MANAGED_PROFILE))
users experience MUST align with the AOSP implementation.

*   [C-1-3] MUST provide the following user affordances within the Settings to
    indicate to the user when a particular system function has been disabled by
    the Device Policy Controller (DPC):
    *   A consistent icon or other user affordance (for example the upstream
        AOSP info icon) to represent when a particular setting is restricted by
        a Device Admin.
    *   A short explanation message, as provided by the Device Admin via the
        [`setShortSupportMessage`](
        https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#setShortSupportMessage%28android.content.ComponentName, java.lang.CharSequence%29).
    *   The DPC application’s icon.

## 3.9.2 Managed Profile Support

If device implementations declare `android.software.managed_users`, they:

*   [C-1-1] MUST support managed profiles via the `android.app.admin.DevicePolicyManager`
    APIs.
*   [C-1-2] MUST allow one and only [one managed profile to be created](http://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#ACTION_PROVISION_MANAGED_PROFILE).
*   [C-1-3] MUST use an icon badge (similar to the AOSP upstream work badge) to
    represent the managed applications and widgets and other badged UI elements
    like Recents &amp; Notifications.
*   [C-1-4] MUST display a notification icon (similar to the AOSP upstream work
    badge) to indicate when user is within a managed profile application.
*   [C-1-5] MUST display a toast indicating that the user is in the managed
    profile if and when the device wakes up (ACTION_USER_PRESENT) and the
    foreground application is within the managed profile.
*   [C-1-6] Where a managed profile exists, MUST show a visual affordance in the
    Intent 'Chooser' to allow the user to forward the intent from the managed
    profile to the primary user or vice versa, if enabled by the Device Policy
    Controller.
*   [C-1-7] Where a managed profile exists, MUST expose the following user
    affordances for both the primary user and the managed profile:
    *   Separate accounting for battery, location, mobile data and storage usage
        for the primary user and managed profile.
    *   Independent management of VPN Applications installed within the primary
        user or managed profile.
    *   Independent management of applications installed within the primary user
        or managed profile.
    *   Independent management of accounts within the primary user or managed
        profile.
*   [C-1-8] MUST ensure the preinstalled dialer, contacts and messaging
    applications can search for and look up caller information from the managed
    profile (if one exists) alongside those from the primary profile, if the
    Device Policy Controller permits it.
*   [C-1-9] MUST ensure that it satisfies all the security requirements
    applicable for a device with multiple users enabled
    (see[section 9.5](#9_5_multi-user_support)), even though the managed profile
    is not counted as another user in addition to the primary user.
*   [C-1-10] MUST support the ability to specify a separate lock screen meeting
    the following requirements to grant access to apps running in a managed
    profile.
    *   Device implementations MUST honor the
        [`DevicePolicyManager.ACTION_SET_NEW_PASSWORD`](https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#ACTION_SET_NEW_PASSWORD)
        intent and show an interface to configure a separate lock screen
        credential for the managed profile.
    *   The lock screen credentials of the managed profile MUST use the same
        credential storage and management mechanisms as the parent profile,
        as documented on the
        [Android Open Source Project Site](http://source.android.com/security/authentication/index.html)
    *   The DPC [password policies](https://developer.android.com/guide/topics/admin/device-admin.html#pwd)
        MUST apply to only the managed profile's lock screen credentials unless
        called upon the `DevicePolicyManager` instance returned by
        <a href="https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html#getParentProfileInstance%28android.content.ComponentName%29">getParentProfileInstance</a>.
*   When contacts from the managed profile are displayed
    in the preinstalled call log, in-call UI, in-progress and missed-call
    notifications, contacts and messaging apps they SHOULD be badged with the
    same badge used to indicate managed profile applications.
