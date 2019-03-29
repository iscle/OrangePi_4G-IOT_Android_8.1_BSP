# 7\. Hardware Compatibility

If a device includes a particular hardware component that has a corresponding
API for third-party developers:

*    [C-0-1] The device implementation MUST implement that
API as described in the Android SDK documentation.

If an API in the SDK
interacts with a hardware component that is stated to be optional and the
device implementation does not possess that component:

*   [C-0-2] Complete class definitions (as documented by the SDK) for the component
APIs MUST still be presented.
*   [C-0-3] The API’s behaviors MUST be implemented as no-ops in some reasonable
fashion.
*   [C-0-4] API methods MUST return null values where permitted by the SDK
documentation.
*   [C-0-5] API methods MUST return no-op implementations of classes where null values
are not permitted by the SDK documentation.
*   [C-0-6] API methods MUST NOT throw exceptions not documented by the SDK
documentation.
*    [C-0-7] Device implementations MUST consistently report accurate hardware
configuration information via the `getSystemAvailableFeatures()` and
`hasSystemFeature(String)` methods on the
[android.content.pm.PackageManager](
http://developer.android.com/reference/android/content/pm/PackageManager.html)
class for the same build fingerprint.

A typical example of a scenario where these requirements apply is the telephony
API: Even on non-phone devices, these APIs must be implemented as reasonable
no-ops.