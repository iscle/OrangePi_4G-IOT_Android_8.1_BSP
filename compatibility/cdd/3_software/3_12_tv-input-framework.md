## 3.12\. TV Input Framework

The [Android Television Input Framework (TIF)](
http://source.android.com/devices/tv/index.html) simplifies the delivery of live
content to Android Television devices. TIF provides a standard API to create
input modules that control Android Television devices.

*    [T-0-1] Android Television device implementations MUST support TV Input
Framework.

If device implementations support TIF, they:

*    [C-1-1] MUST declare the platform feature `android.software.live_tv`.
*    [C-1-2] MUST preload a TV application (TV App) and meet all requirements
     described in [section 3.12.1](#3_12_tv-input-framework).

### 3.12.1\. TV App

If device implementations support TIF:

*    [C-1-1] The TV App MUST provide facilities to install and use [TV Channels](
http://developer.android.com/reference/android/media/tv/TvContract.Channels.html)
and meet the following requirements:

The TV app that is required for Android device implementations declaring the
`android.software.live_tv` feature flag, MUST meet the following requirements:

*   Device implementations SHOULD allow third-party TIF-based inputs
    ([third-party inputs](
    https://source.android.com/devices/tv/index.html#third-party_input_example))
    to be installed and managed.
*   Device implementations MAY provide visual separation between pre-installed
    [TIF-based inputs](
    https://source.android.com/devices/tv/index.html#tv_inputs)
    (installed inputs) and third-party inputs.
*   Device implementations SHOULD NOT display the third-party inputs more than a
    single navigation action away from the TV App (i.e. expanding a list of
    third-party inputs from the TV App).

The Android Open Source Project provides an implementation of the TV App that
meets the above requirements.

#### 3.12.1.1\. Electronic Program Guide

If device implementations support TIF, they:

*    [C-1-1] MUST show an informational and
interactive overlay, which MUST include an electronic program guide (EPG)
generated from the values in the [TvContract.Programs](
https://developer.android.com/reference/android/media/tv/TvContract.Programs.html)
fields.
*   [C-1-2] On channel change, device implementations MUST display EPG data for
    the currently playing program.
*   [SR] The EPG is STRONGLY RECOMMENDED to display installed inputs and
    third-party inputs with equal prominence. The EPG SHOULD NOT display the
    third-party inputs more than a single navigation action away from the
    installed inputs on the EPG.
*   The EPG SHOULD display information from all installed inputs and third-party
    inputs.
*   The EPG MAY provide visual separation between the installed inputs and
    third-party inputs.

#### 3.12.1.2\. Navigation

If device implementations support TIF, they:

*    [C-1-1] MUST allow navigation for the following functions via
the D-pad, Back, and Home keys on the Android Television device’s input
device(s) (i.e. remote control, remote control application, or game controller):

    *   Changing TV channels
    *   Opening EPG
    *   Configuring and tuning to third-party TIF-based inputs (if those inputs are supported)
    *   Opening Settings menu

*    SHOULD pass key events to HDMI inputs through CEC.

#### 3.12.1.3\. TV input app linking

Android Television device implementations SHOULD support
[TV input app linking](http://developer.android.com/reference/android/media/tv/TvContract.Channels.html#COLUMN_APP_LINK_INTENT_URI),
which allows all inputs to provide activity links from the current activity to
another activity (i.e. a link from live programming to related content). The TV
App SHOULD show TV input app linking when it is provided.

#### 3.12.1.4\. Time shifting

If device implementations support TIF, they:

*    [SR] STRONGLY RECOMMENDED to support time shifting, which allows the user
to pause and resume live content.
*    SHOULD provide the user a way to pause and resume the currently playing
program, if time shifting for that program [is available](
https://developer.android.com/reference/android/media/tv/TvInputManager.html#TIME_SHIFT_STATUS_AVAILABLE).

#### 3.12.1.5\. TV recording

If device implementations support TIF, they:

*    [SR] STRONGLY RECOMMENDED to support TV recording.
*    If the TV input supports recording and the recording of a program is not
[prohibited](
https://developer.android.com/reference/android/media/tv/TvContract.Programs.html#COLUMN_RECORDING_PROHIBITED),
the EPG MAY provide a way to [record a program](
https://developer.android.com/reference/android/media/tv/TvInputInfo.html#canRecord%28%29).
