## 3.16\. Companion Device Pairing

Android includes support for companion device pairing to more effectively manage
association with companion devices and provides the [`CompanionDeviceManager`
](https://developer.android.com/reference/android/companion/CompanionDeviceManager.html)
API for apps to access this feature.

If device implementations support the companion device pairing feature, they:

*   [C-1-1] MUST declare the feature flag [`FEATURE_COMPANION_DEVICE_SETUP`
    ](https://developer.android.com/reference/android/content/pm/PackageManager.html?#FEATURE_COMPANION_DEVICE_SETUP)
    .
*   [C-1-2] MUST ensure the APIs in the [`android.companion`
    ](https://developer.android.com/reference/android/companion/package-summary.html)
    package is fully implemented.
*   [C-1-3] MUST provide user affordances for the user to select/confirm a companion
    device is present and operational.
