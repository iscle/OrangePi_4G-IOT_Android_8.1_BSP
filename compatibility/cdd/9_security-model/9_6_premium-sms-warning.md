## 9.6\. Premium SMS Warning

Android includes support for warning users of any outgoing
[premium SMS message](http://en.wikipedia.org/wiki/Short_code). Premium SMS
messages are text messages sent to a service registered with a carrier that may
incur a charge to the user.

If device implementations declare support for `android.hardware.telephony`,
they:

*    [C-1-1]  MUST warn users before sending a SMS message to numbers
identified by regular expressions defined in `/data/misc/sms/codes.xml`
file in the device. The upstream Android Open Source Project provides
an implementation that satisfies this requirement.
