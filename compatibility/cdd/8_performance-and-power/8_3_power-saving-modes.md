## 8.3\. Power-Saving Modes

Android includes App Standby and Doze power-saving modes to optimize battery
usage.

*   [H-0-1] All Apps exempted from App Standby and Doze power-saving modes
MUST be made visible to the end user.
*   [H-0-2] The triggering, maintenance, wakeup algorithms and the use of
global system settings of App Standby and Doze power-saving modes MUST not
deviate from the Android Open Source Project.

*   [T-0-1] All Apps exempted from App Standby and Doze power-saving modes
MUST be made visible to the end user.
*   [T-0-2] The triggering, maintenance, wakeup algorithms and the use of
global system settings of App Standby and Doze power-saving modes MUST not
deviate from the Android Open Source Project.

*   [A-0-1] All Apps exempted from App Standby and Doze power-saving modes
MUST be made visible to the end user.
*   [A-0-2] The triggering, maintenance, wakeup algorithms and the use of
global system settings of App Standby and Doze power-saving modes MUST not
deviate from the Android Open Source Project.

*   [SR] All Apps exempted from these modes are STRONGLY RECOMMENDED to be made
visible to the end user.
*   [SR] The triggering, maintenance, wakeup algorithms and the use of
global system settings of these power-saving modes are STRONGLY RECOMMENDED NOT
to deviate from the Android Open Source Project.

In addition to the power-saving modes, Android device implementations MAY
implement any or all of the 4 sleeping power states as defined by the Advanced
Configuration and Power Interface (ACPI).

If device implementations implements S3 and S4 power states as defined by the
ACPI, they:

*   [C-1-1] MUST only enter these states when closing a lid that is physically
    part of the device.
