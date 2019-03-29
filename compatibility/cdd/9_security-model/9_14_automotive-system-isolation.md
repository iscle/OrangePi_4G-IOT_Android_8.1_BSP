## 9.14\. Automotive Vehicle System Isolation

Android Automotive devices are expected to exchange data with critical vehicle
subsystems by using the [vehicle HAL](http://source.android.com/devices/automotive.html)
to send and receive messages over vehicle networks such as CAN bus.

The data exchange can be secured by implementing security features below the
Android framework layers to prevent malicious or unintentional interaction with
these subsystems. Automotive device implementations:

*    [A-0-1] MUST gatekeep messages from Android framework vehicle subsystems,
e.g., whitelisting permitted message types and message sources.
*    [A-0-2] MUST watchdog against denial of service attacks from the Android
framework or third-party apps. This guards against malicious software flooding
the vehicle network with traffic, which may lead to malfunctioning vehicle
subsystems.