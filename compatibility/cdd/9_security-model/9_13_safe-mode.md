## 9.13\. Safe Boot Mode

Android provides Safe Boot Mode, which allows users to boot up into a mode
where only preinstalled system apps are allowed to run and all third-party
apps are disabled. This mode, known as "Safe Boot Mode", provides the user the
capability to uninstall potentially harmful third-party apps.

Device implementations are:

*   [SR] STRONGLY RECOMMENDED to implement Safe Boot Mode.

If device implementations implement Safe Boot Mode, they:

*   [C-1-1] MUST provide the user an option to
    enter Safe Boot Mode in such a way that is uninterruptible from third-party
    apps installed on the device, except when the third-party app is a
    Device Policy Controller and has set the [`UserManager.DISALLOW_SAFE_BOOT`](
    https://developer.android.com/reference/android/os/UserManager.html#DISALLOW_SAFE_BOOT)
    flag as true.

*   [C-1-2] MUST provide the user the capability to
    uninstall any third-party apps within Safe Mode.

*   SHOULD provide the user an option to enter Safe Boot Mode from the
boot menu using a workflow that is different from that of a normal boot.
