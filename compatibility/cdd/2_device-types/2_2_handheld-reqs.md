## 2.2\. Handheld Requirements

An **Android Handheld device** refers to an Android device implementation that is
typically used by holding it in the hand, such as an mp3 player, phone, or
tablet.

Android device implementations are classified as a Handheld if they meet all the
following criteria:

*   Have a power source that provides mobility, such as a battery.
*   Have a physical diagonal screen size in the range of 2.5 to 8 inches.

The additional requirements in the rest of this section are specific to Android
Handheld device implementations.

### 2.2.1\. Hardware

**Touchscreen Input (Section 7.2.4)**

*   [H-0-1]  Handheld devices MUST have a touchscreen embedded in the device.

More to be added.

### 2.2.2\. Multimedia

To be added.

### 2.2.3\. Software

**WebView Compatibility (Section 3.4.1)**

*   [H-0-1] Handheld devices MUST provide a complete implementation of the android.webkit.Webview API.

**Browser Compatibility (Section 3.4.2)**

*   [H-0-1] Handheled device implementations MUST include a standalone Browser application for general
user web browsing.

**Launcher (Section 3.8.1)**

*   [H-SR] Handheld device implementations are STRONGLY RECOMMENDED to implement a default launcher
    that supports in-app pinning of shortcuts and widgets.

*   [H-SR] Device implementations are STRONGLY RECOMMENDED to implement a default launcher that
    provides quick access to the additional shortcuts provided by third-party apps through the
    [ShortcutManager](
    https://developer.android.com/reference/android/content/pm/ShortcutManager.html) API.

*   [H-SR] Handheld devices are STRONGLY RECOMMENDED to include a default
    launcher app that shows badges for the app icons.

**Widgets (Section 3.8.2)**

*   [H-SR] Handheld Device implementations are STRONGLY RECOMMENDED to support
    third-party app widgets.


**Notifications (Section 3.8.3)**

Android Handheld device implementations:

*   [H-0-1] MUST allow third-party apps to notify users
    of notable events through the [`Notification`](
    https://developer.android.com/reference/android/app/Notification.html) and
    [`NotificationManager`](
    https://developer.android.com/reference/android/app/NotificationManager.html)
    API classes.
*   [H-0-2] MUST support rich notifications.
*   [H-0-3] MUST support heads-up notifications.
*   [H-0-4] MUST include a notification shade, providing the user the ability
    to directly control (e.g. reply, snooze, dismiss, block) the notifications
    through user affordance such as action buttons or the control panel as
    implemented in the AOSP.

**Search (Section 3.8.4)**

*   [H-SR] Handheld device implementations are STRONGLY RECOMMENDED to implement
    an assistant on the device to handle the [Assist action](
    http://developer.android.com/reference/android/content/Intent.html#ACTION_ASSIST).

**Lock Screen Media Control (Section 3.8.10)**

If Android Handheld device implementations support a lock screen,they:

*   [H-1-1] MUST display the Lock screen Notifications including the Media Notification Template.

**Device administration (Section 3.9)**

If Handheld device implementations support a secure lock screen, they:

*   [H-1-1] MUST implement the full range of [device administration](
http://developer.android.com/guide/topics/admin/device-admin.html) 
policies defined in the Android SDK documentation.

**Accessibility (Section 3.10)**

*  [H-SR] Android Handheld device implementations MUST support third-party
   accessibility services.

*  [H-SR] Android Handheld device implementations are STRONGLY RECOMMENDED to
   preload accessibility services on the device comparable with or exceeding
   functionality of the Switch Access and TalkBack (for languages supported by
   the preloaded Text-to-speech engine) accessibility services as provided in
   the [talkback open source project](https://github.com/google/talkback).

**Text-to-Speech (Section 3.11)**

Android handheld device implementations: 

*   [H-SR] STRONGLY RECOMMENDED to include a TTS engine supporting the
    languages available on the device.

*   [H-0-1] MUST support installation of third-party TTS engines.


**Quick Settings (Section 3.13)**

*    [H-SR] Android Handheld devices are STRONGLY RECOMMENDED to include a
     Quick Settings UI component.

**Companion Device Pairing (Section 3.15)**

If Android handheld device implementations declare `FEATURE_BLUETOOTH` or `FEATURE_WIFI` support, they:

*    [H-1-1] MUST support the companion device pairing feature.
