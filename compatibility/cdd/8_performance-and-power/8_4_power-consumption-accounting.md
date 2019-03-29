## 8.4\. Power Consumption Accounting

A more accurate accounting and reporting of the power consumption provides the
app developer both the incentives and the tools to optimize the power usage
pattern of the application.

Handheld device implementations:

*    [H-0-1] MUST provide a per-component power profile that defines the
[current consumption value](
http://source.android.com/devices/tech/power/values.html)
for each hardware component and the approximate battery drain caused by the
components over time as documented in the Android Open Source Project site.
*    [H-0-2] MUST report all power consumption values in milliampere
hours (mAh).
*    [H-0-3] MUST report CPU power consumption per each process's UID.
The Android Open Source Project meets the requirement through the
`uid_cputime` kernel module implementation.
*    SHOULD be attributed to the hardware component itself if unable to
attribute hardware component power usage to an application.
*   [H-0-4] MUST make this power usage available via the
[`adb shell dumpsys batterystats`](
http://source.android.com/devices/tech/power/batterystats.html)
shell command to the app developer.

Television device implementations:

*    [T-0-1] MUST provide a per-component power profile that defines the
[current consumption value](
http://source.android.com/devices/tech/power/values.html)
for each hardware component and the approximate battery drain caused by the
components over time as documented in the Android Open Source Project site.
*    [T-0-2] MUST report all power consumption values in milliampere
hours (mAh).
*    [T-0-3] MUST report CPU power consumption per each process's UID.
The Android Open Source Project meets the requirement through the
`uid_cputime` kernel module implementation.
*    SHOULD be attributed to the hardware component itself if unable to
attribute hardware component power usage to an application.
*   [T-0-4] MUST make this power usage available via the
[`adb shell dumpsys batterystats`](
http://source.android.com/devices/tech/power/batterystats.html)
shell command to the app developer.

Automotive device implementations:

*    [A-0-1] MUST provide a per-component power profile that defines the
[current consumption value](
http://source.android.com/devices/tech/power/values.html)
for each hardware component and the approximate battery drain caused by the
components over time as documented in the Android Open Source Project site.
*    [A-0-2] MUST report all power consumption values in milliampere
hours (mAh).
*    [A-0-3] MUST report CPU power consumption per each process's UID.
The Android Open Source Project meets the requirement through the
`uid_cputime` kernel module implementation.
*    SHOULD be attributed to the hardware component itself if unable to
attribute hardware component power usage to an application.
*   [A-0-4] MUST make this power usage available via the
[`adb shell dumpsys batterystats`](
http://source.android.com/devices/tech/power/batterystats.html)
shell command to the app developer.

Device implementations:

*   [SR] STRONGLY RECOMMENDED to provide a per-component power profile
that defines the [current consumption value](
http://source.android.com/devices/tech/power/values.html)
for each hardware component and the approximate battery drain caused by the
components over time as documented in the Android Open Source Project site.
*   [SR] STRONGLY RECOMMENDED to report all power consumption values in milliampere
hours (mAh).
*   [SR] STRONGLY RECOMMENDED to report CPU power consumption per each process's UID.
The Android Open Source Project meets the requirement through the
`uid_cputime` kernel module implementation.
*   [SR] STRONGLY RECOMMENDED to make this power usage available via the
[`adb shell dumpsys batterystats`](
http://source.android.com/devices/tech/power/batterystats.html)
shell command to the app developer.
*   SHOULD be attributed to the hardware component itself if unable to
attribute hardware component power usage to an application.


If Handheld device implementations include a screen or video output, they:

*   [H-1-1] MUST honor the [`android.intent.action.POWER_USAGE_SUMMARY`](
http://developer.android.com/reference/android/content/Intent.html#ACTION_POWER_USAGE_SUMMARY)
intent and display a settings menu that shows this power usage.

