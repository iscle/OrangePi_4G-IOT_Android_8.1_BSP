## 7.2\. Input Devices

Device implementations:

*    [C-0-1] MUST include an input mechanism, such as a
[touchscreen](#7_2_4_touchScreen_input) or [non-touch navigation](#7_2_2_non-touch_navigation),
to navigate between the UI elements.

### 7.2.1\. Keyboard

*    [H-0-1]  Handheld device implementations MUST include support for
third-party Input Method Editor (IME) applications.
*    [T-0-1] Television device implementations  MUST include support for
third-party Input Method Editor (IME) applications.

If device implementations include support for third-party
Input Method Editor (IME) applications, they:

*   [C-1-1] MUST declare the [`android.software.input_methods`](https://developer.android.com/reference/android/content/pm/PackageManager.html#FEATURE_INPUT_METHODS)
feature flag.
*   [C-1-2] MUST implement fully [`Input Management Framework`](https://developer.android.com/reference/android/view/inputmethod/InputMethodManager.html)
*   [C-1-3] MUST have a preloaded software keyboard.

Device implementations:
*   [C-0-1] MUST NOT include a hardware keyboard that does not match one of the
    formats specified in [android.content.res.Configuration.keyboard](
    http://developer.android.com/reference/android/content/res/Configuration.html)
    (QWERTY or 12-key).
*   SHOULD include additional soft keyboard implementations.
*   MAY include a hardware keyboard.

### 7.2.2\. Non-touch Navigation

Android includes support for d-pad, trackball, and wheel as mechanisms for
non-touch navigation.

Television device implementations:

*    [T-0-1] MUST support [D-pad](https://developer.android.com/reference/android/content/res/Configuration.html#NAVIGATION_DPAD).

Device implementations:

*   [C-0-1] MUST report the correct value for
    [android.content.res.Configuration.navigation](
    https://developer.android.com/reference/android/content/res/Configuration.html#navigation).

If device implementations lack non-touch navigations, they:

*   [C-1-1] MUST provide a reasonable alternative user interface mechanism for the
    selection and editing of text, compatible with Input Management Engines. The
    upstream Android open source implementation includes a selection mechanism
    suitable for use with devices that lack non-touch navigation inputs.


### 7.2.3\. Navigation Keys

The [Home](http://developer.android.com/reference/android/view/KeyEvent.html#`KEYCODE_HOME`),
[Recents](http://developer.android.com/reference/android/view/KeyEvent.html#`KEYCODE_APP_SWITCH`),
and [Back](http://developer.android.com/reference/android/view/KeyEvent.html#`KEYCODE_BACK`)
functions typically provided via an interaction with a dedicated physical button
or a distinct portion of the touch screen, are essential to the Android
navigation paradigm and therefore:

*   [H-0-1] Android Handheld device implementations MUST provide the Home,
    Recents, and Back functions.
*   [H-0-2] Android Handheld device implementations MUST send both the normal
    and long press event of the the Back function ([`KEYCODE_BACK`](http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_BACK))
    to the foreground application.
*   [T-0-1] Android Television device implementations MUST provide the Home
    and Back functions.
*   [T-0-2] Android Television device implementations MUST send both the normal
    and long press event of the the Back function ([`KEYCODE_BACK`](http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_BACK))
    to the foreground application.
*   [W-0-1] Android Watch device implementations MUST have the Home function
    available to the user, and the Back function except for when it is in
    `UI_MODE_TYPE_WATCH`.
*   [A-0-1] Automotive device implementations MUST provide the Home function
    and MAY provide Back and Recent functions.
*   [A-0-2] Automotive device implementations MUST send both the normal
    and long press event of the the Back function ([`KEYCODE_BACK`](http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_BACK))
    to the foreground application.

*   [C-0-1] MUST provide the Home function.
*   SHOULD provide buttons for the Recents and Back function.

If the Home, Recents, or Back functions are provided, they:

*   [C-1-1] MUST be accessible with a single action (e.g. tap, double-click or
gesture) when any of them are accessible.
*   [C-1-2] MUST provide a clear indication of which single action would trigger
each function. Having a visible icon imprinted on the button, showing a software
icon on the navigation bar portion of the screen, or walking the user through a
guided step-by-step demo flow during the out-of-box setup experience are
examples of such an indication.

Device implementations:

*    [SR] are STRONGLY RECOMMENDED to not provide the input mechanism for the
[Menu function](http://developer.android.com/reference/android/view/KeyEvent.html#`KEYCODE_BACK`)
as it is deprecated in favor of action bar since Android 4.0.

If device implementations provide the Menu function, they:

*    [C-2-1] MUST display the action overflow button whenever the action
overflow menu popup is not empty and the action bar is visible.
*    [C-2-2] MUST NOT modify the position of the action overflow popup
displayed by selecting the overflow button in the action bar, but MAY render
the action overflow popup at a modified position on the screen when it is
displayed by selecting the Menu function.

If device implementations do not provide the Menu function, for backwards
compatibility, they:
*    [C-3-1] MUST make the Menu function available to applications when
`targetSdkVersion` is less than 10, either by a physical button, a software key,
or gestures. This Menu function should be accessible unless hidden together with
other navigation functions.

If device implementations provide the [Assist function]((http://developer.android.com/reference/android/view/KeyEvent.html#`KEYCODE_ASSIST`),
they:
*    [C-4-1] MUST make the Assist function accessible with a single action
(e.g. tap, double-click or gesture) when other navigation keys are accessible.
*    [SR] STRONGLY RECOMMENDED to use long press on HOME function as this
designated interaction.

If device implementations use a distinct portion of the screen to display the
navigation keys, they:

*   [C-5-1] Navigation keys MUST use a distinct portion of the screen, not
    available to applications, and MUST NOT obscure or otherwise interfere with
    the portion of the screen available to applications.
*   [C-5-2] MUST make available a portion of the display to applications that
    meets the requirements defined in [section 7.1.1](#7_1_1_screen_configuration).
*   [C-5-3] MUST honor the flags set by the app through the [`View.setSystemUiVisibility()`](https://developer.android.com/reference/android/view/View.html#setSystemUiVisibility%28int%29)
    API method, so that this distinct portion of the screen
    (a.k.a. the navigation bar) is properly hidden away as documented in
    the SDK.

### 7.2.4\. Touchscreen Input

Android includes support for a variety of pointer input systems, such as
touchscreens, touch pads, and fake touch input devices.
[Touchscreen-based device implementations](http://source.android.com/devices/tech/input/touch-devices.html)
are associated with a display such that the user has the impression of directly
manipulating items on screen. Since the user is directly touching the screen,
the system does not require any additional affordances to indicate the objects
being manipulated.

*    [H-0-1] Handheld device implementations MUST support touchscreen input.
*    [W-0-2] Watch device implementations MUST support touchscreen input.

Device implementations:

*    SHOULD have a pointer input system of some kind
     (either mouse-like or touch).
*    SHOULD support fully independently tracked pointers.

If device implementations include a touchscreen (single-touch or better), they:

*    [C-1-1] MUST report `TOUCHSCREEN_FINGER` for the [`Configuration.touchscreen`](https://developer.android.com/reference/android/content/res/Configuration.html#touchscreen)
     API field.
*    [C-1-2] MUST report the `android.hardware.touchscreen` and
     `android.hardware.faketouch` feature flags

If device implementations include a touchscreen that can track more than
a single touch, they:

*    [C-2-1] MUST report the appropriate feature flags  `android.hardware.touchscreen.multitouch`,
`android.hardware.touchscreen.multitouch.distinct`, `android.hardware.touchscreen.multitouch.jazzhand`
corresponding to the type of the specific touchscreen on the device.

If device implementations do not include a touchscreen (and rely on a pointer
device only) and meet the fake touch requirements in
[section 7.2.5](#7_2_5_fake_touch_input), they:

*    [C-3-1] MUST NOT report any feature flag starting with
`android.hardware.touchscreen` and MUST report only `android.hardware.faketouch`.

### 7.2.5\. Fake Touch Input

Fake touch interface provides a user input system that approximates a subset of
touchscreen capabilities. For example, a  mouse or remote control that drives
an on-screen cursor approximates touch, but requires the user to first point or
focus then click. Numerous input devices like the mouse, trackpad, gyro-based
air mouse, gyro-pointer, joystick, and multi-touch trackpad can support fake
touch interactions. Android includes the feature constant
android.hardware.faketouch, which corresponds to a high-fidelity non-touch
(pointer-based) input device such as a mouse or trackpad that can adequately
emulate touch-based input (including basic gesture support), and indicates that
the device supports an emulated subset of touchscreen functionality.

If device implementations do not include a touchscreen but include another
pointer input system which they want to make available, they:

*    SHOULD declare support for the `android.hardware.faketouch` feature flag.

If device implementations declare support for `android.hardware.faketouch`,
they:

*   [C-1-1] MUST report the [absolute X and Y screen positions](
http://developer.android.com/reference/android/view/MotionEvent.html)
of the pointer location and display a visual pointer on the screen.
*   [C-1-2] MUST report touch event with the action code that specifies the
state change that occurs on the pointer [going down or up on the
screen](http://developer.android.com/reference/android/view/MotionEvent.html).
*   [C-1-3] MUST support pointer down and up on an object on the screen, which
allows users to emulate tap on an object on the screen.
*   [C-1-4] MUST support pointer down, pointer up, pointer down then pointer up
in the same place on an object on the screen within a time threshold, which
allows users to [emulate double tap](
http://developer.android.com/reference/android/view/MotionEvent.html)
on an object on the screen.
*   [C-1-5] MUST support pointer down on an arbitrary point on the screen,
pointer move to any other arbitrary point on the screen, followed by a pointer
up, which allows users to emulate a touch drag.
*   [C-1-6] MUST support pointer down then allow users to quickly move the
object to a different position on the screen and then pointer up on the screen,
which allows users to fling an object on the screen.
*   [C-1-7] MUST report `TOUCHSCREEN_NOTOUCH` for the [`Configuration.touchscreen`](https://developer.android.com/reference/android/content/res/Configuration.html#touchscreen)
API field.

If device implementations declare support for
`android.hardware.faketouch.multitouch.distinct`, they:

*    [C-2-1] MUST declare support for `android.hardware.faketouch`.
*    [C-2-2] MUST support distinct tracking of two or more independent pointer
inputs.

If device implementations declare support for
`android.hardware.faketouch.multitouch.jazzhand`, they:

*    [C-3-1] MUST declare support for `android.hardware.faketouch`.
*    [C-3-2] MUST support distinct tracking of 5 (tracking a hand of fingers)
or more pointer inputs fully independently.

### 7.2.6\. Game Controller Support

#### 7.2.6.1\. Button Mappings

Television device implementations:
*    [T-0-1] MUST include support for game controllers and declare the
`android.hardware.gamepad` feature flag.

If device implementations declare the `android.hardware.gamepad` feature flag,
they:
*    [C-1-1] MUST have embed a controller or ship with a separate controller
in the box, that would provide means to input all the events listed in the
below tables.
*    [C-1-2] MUST be capable to map HID events to it's associated Android
`view.InputEvent` constants as listed in the below tables. The upstream Android
implementation includes implementation for game controllers that satisfies this
requirement.

<table>
 <tr>
    <th>Button</th>
    <th>HID Usage<sup>2</sup></th>
    <th>Android Button</th>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_BUTTON_A">A</a><sup>1</sup></td>
    <td>0x09 0x0001</td>
    <td>KEYCODE_BUTTON_A (96)</td>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_BUTTON_B">B</a><sup>1</sup></td>
    <td>0x09 0x0002</td>
    <td>KEYCODE_BUTTON_B (97)</td>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_BUTTON_X">X</a><sup>1</sup></td>
    <td>0x09 0x0004</td>
    <td>KEYCODE_BUTTON_X (99)</td>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_BUTTON_Y">Y</a><sup>1</sup></td>
    <td>0x09 0x0005</td>
    <td>KEYCODE_BUTTON_Y (100)</td>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_DPAD_UP">D-pad up</a><sup>1</sup><br />

<a href="http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_DPAD_DOWN">D-pad down</a><sup>1</sup></td>
    <td>0x01 0x0039<sup>3</sup></td>
    <td><a href="http://developer.android.com/reference/android/view/MotionEvent.html#AXIS_HAT_Y">AXIS_HAT_Y</a><sup>4</sup></td>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_DPAD_LEFT">D-pad left</a>1<br />

<a href="http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_DPAD_RIGHT">D-pad right</a><sup>1</sup></td>
    <td>0x01 0x0039<sup>3</sup></td>
    <td><a href="http://developer.android.com/reference/android/view/MotionEvent.html#AXIS_HAT_X">AXIS_HAT_X</a><sup>4</sup></td>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_BUTTON_L1">Left shoulder button</a><sup>1</sup></td>
    <td>0x09 0x0007</td>
    <td>KEYCODE_BUTTON_L1 (102)</td>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_BUTTON_R1">Right shoulder button</a><sup>1</sup></td>
    <td>0x09 0x0008</td>
    <td>KEYCODE_BUTTON_R1 (103)</td>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_BUTTON_THUMBL">Left stick click</a><sup>1</sup></td>
    <td>0x09 0x000E</td>
    <td>KEYCODE_BUTTON_THUMBL (106)</td>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_BUTTON_THUMBR">Right stick click</a><sup>1</sup></td>
    <td>0x09 0x000F</td>
    <td>KEYCODE_BUTTON_THUMBR (107)</td>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_HOME">Home</a><sup>1</sup></td>
    <td>0x0c 0x0223</td>
    <td>KEYCODE_HOME (3)</td>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/KeyEvent.html#KEYCODE_BACK">Back</a><sup>1</sup></td>
    <td>0x0c 0x0224</td>
    <td>KEYCODE_BACK (4)</td>
 </tr>
</table>


<p class="table_footnote">1 <a href="http://developer.android.com/reference/android/view/KeyEvent.html">KeyEvent</a></p>

<p class="table_footnote">2 The above HID usages must be declared within a Game
pad CA (0x01 0x0005).</p>

<p class="table_footnote">3 This usage must have a Logical Minimum of 0, a
Logical Maximum of 7, a Physical Minimum of 0, a Physical Maximum of 315, Units
in Degrees, and a Report Size of 4. The logical value is defined to be the
clockwise rotation away from the vertical axis; for example, a logical value of
0 represents no rotation and the up button being pressed, while a logical value
of 1 represents a rotation of 45 degrees and both the up and left keys being
pressed.</p>

<p class="table_footnote">4 <a
href="http://developer.android.com/reference/android/view/MotionEvent.html">MotionEvent</a></p>

<table>
 <tr>
    <th>Analog Controls<sup>1</sup></th>
    <th>HID Usage</th>
    <th>Android Button</th>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/MotionEvent.html#AXIS_LTRIGGER">Left Trigger</a></td>
    <td>0x02 0x00C5</td>
    <td>AXIS_LTRIGGER </td>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/MotionEvent.html#AXIS_THROTTLE">Right Trigger</a></td>
    <td>0x02 0x00C4</td>
    <td>AXIS_RTRIGGER </td>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/MotionEvent.html#AXIS_Y">Left Joystick</a></td>
    <td>0x01 0x0030<br />

0x01 0x0031</td>
    <td>AXIS_X<br />

AXIS_Y</td>
 </tr>
 <tr>
    <td><a href="http://developer.android.com/reference/android/view/MotionEvent.html#AXIS_Z">Right Joystick</a></td>
    <td>0x01 0x0032<br />

0x01 0x0035</td>
    <td>AXIS_Z<br />

AXIS_RZ</td>
 </tr>
</table>


<p class="table_footnote">1 <a
href="http://developer.android.com/reference/android/view/MotionEvent.html">MotionEvent</a></p>

### 7.2.7\. Remote Control

Television device implementations:

*    SHOULD provide a remote control from which users can access
[non-touch navigation](#7_2_2_non-touch_navigation) and
[core navigation keys](#7_2_3_navigation_keys) inputs.