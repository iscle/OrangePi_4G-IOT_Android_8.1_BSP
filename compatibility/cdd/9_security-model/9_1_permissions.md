## 9.1\. Permissions

Device implementations:

*   [C-0-1] MUST support the [Android permissions model](
http://developer.android.com/guide/topics/security/permissions.html)
as defined in the Android developer documentation. Specifically, they
MUST enforce each permission defined as described in the SDK documentation; no
permissions may be omitted, altered, or ignored.

*   MAY add additional permissions, provided the new permission ID strings
are not in the `android.\*` namespace.

*   [C-0-2] Permissions with a `protectionLevel` of
[`PROTECTION_FLAG_PRIVILEGED`](
https://developer.android.com/reference/android/content/pm/PermissionInfo.html#PROTECTION&lowbar;FLAG&lowbar;PRIVILEGED)
MUST only be granted to apps preloaded in the privileged path(s) of the system
image and within the subset of the explicitly whitelisted permissions for each
app. The AOSP implementation meets this requirement by reading and honoring
the whitelisted permissions for each app from the files in the
`etc/permissions/` path and using the `system/priv-app` path as the
privileged path.

Permissions with a protection level of dangerous are runtime permissions.
Applications with `targetSdkVersion` > 22 request them at runtime.

Device implementations:

*   [C-0-3] MUST show a dedicated interface for the user to decide
     whether to grant the requested runtime permissions and also provide
     an interface for the user to manage runtime permissions.
*   [C-0-4] MUST have one and only one implementation of both user
     interfaces.
*   [C-0-5] MUST NOT grant any runtime permissions to preinstalled
     apps unless:
   *   the user's consent can be obtained before the application
       uses it
   *   the runtime permissions are associated with an intent pattern
       for which the preinstalled application is set as the default handler

Handheld device implementations:

*   [H-0-1] MUST allow third-party apps to access the usage statistics via the
    `android.permission.PACKAGE_USAGE_STATS` permission and provide a
    user-accessible mechanism to grant or revoke access to such apps in response
    to the [`android.settings.ACTION_USAGE_ACCESS_SETTINGS`](
    https://developer.android.com/reference/android/provider/Settings.html#ACTION&lowbar;USAGE&lowbar;ACCESS&lowbar;SETTINGS)
    intent.

If device implementations include a pre-installed app or wish to allow
third-party apps to access the usage statistics, they:

*   [C-1-1] are STRONGLY RECOMMENDED provide user-accessible mechanism to grant
    or revoke access to the usage stats in response to the
    [`android.settings.ACTION_USAGE_ACCESS_SETTINGS`](
    https://developer.android.com/reference/android/provider/Settings.html#ACTION&lowbar;USAGE&lowbar;ACCESS&lowbar;SETTINGS)
    intent for apps that declare the `android.permission.PACKAGE_USAGE_STATS`
    permission.

If device implementations intend to disallow any apps, including pre-installed
apps, from accessing the usage statistics, they:

*   [C-2-1] MUST still have an activity that handles the
    [`android.settings.ACTION_USAGE_ACCESS_SETTINGS`](
    https://developer.android.com/reference/android/provider/Settings.html#ACTION&lowbar;USAGE&lowbar;ACCESS&lowbar;SETTINGS)
    intent pattern but MUST implement it as a no-op, that is to have an
    equivalent behavior as when the user is declined for access.