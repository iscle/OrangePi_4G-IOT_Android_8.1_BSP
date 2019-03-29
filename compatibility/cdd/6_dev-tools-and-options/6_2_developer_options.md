## 6.2\. Developer Options

Android includes support for developers to configure application
development-related settings.

Device implementations MUST provide a consistent experience for
Developer Options, they:

*   [C-0-1] MUST honor the [android.settings.APPLICATION_DEVELOPMENT_SETTINGS](
http://developer.android.com/reference/android/provider/Settings.html#ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
intent to show application development-related settings. The upstream Android
implementation hides the Developer Options menu by default and enables users to
launch Developer Options after pressing seven (7) times on the **Settings** >
**About Device** > **Build Number** menu item.
*   [C-0-2] MUST hide Developer Options by default and MUST provide a mechanism
to enable Developer Options without the need for any special whitelisting.
*   MAY temporarily limit access to the Developer Options menu, by visually
hiding or disabling the menu, to prevent distraction for scenarios where the
safety of the user is of concern.