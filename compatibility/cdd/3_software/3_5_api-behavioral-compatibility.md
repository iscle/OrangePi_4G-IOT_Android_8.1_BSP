## 3.5\. API Behavioral Compatibility

The behaviors of each of the API types (managed, soft, native, and web) must be
consistent with the preferred implementation of the upstream
[Android Open Source Project](http://source.android.com/). Some specific areas
of compatibility are:

*    [C-0-1] Devices MUST NOT change the behavior or semantics of a
     standard intent.
*    [C-0-2] Devices MUST NOT alter the lifecycle or lifecycle semantics of
     a particular type of system component (such as Service, Activity, ContentProvider, etc.).
*    [C-0-3] Devices MUST NOT change the semantics of a standard permission.
*    Devices MUST NOT alter the limitations enforced on background applications.
     More specifically, for background apps:
     *    [C-0-4] they MUST stop executing callbacks that are registered by the
          app to receive outputs from the [`GnssMeasurement`](
          https://developer.android.com/reference/android/location/GnssMeasurement.html)
          and [`GnssNavigationMessage`](
          https://developer.android.com/reference/android/location/GnssNavigationMessage.html).
     *    [C-0-5] they MUST rate-limit the frequency of updates that are
          provided to the app through the [`LocationManager`](
          https://developer.android.com/reference/android/location/LocationManager.html)
          API class or the [`WifiManager.startScan()`](
          https://developer.android.com/reference/android/net/wifi/WifiManager.html#startScan%28%29)
          method.
     *    [C-0-6] if the app is targeting API level 25 or higher, they MUST NOT
          allow to register broadcast receivers for the implicit broadcasts of
          standard Android intents in the app's manifest, unless the broadcast
          intent requires a `"signature"` or `"signatureOrSystem"`
          [`protectionLevel`](
          https://developer.android.com/guide/topics/manifest/permission-element.html#plevel)
          permission or are on the [exemption list](
          https://developer.android.com/preview/features/background-broadcasts.html)
          .
     *    [C-0-7] if the app is targeting API level 25 or higher, they MUST stop
          the app's background services, just as if the app had called the
          services'[`stopSelf()`](
          https://developer.android.com/reference/android/app/Service.html#stopSelf%28%29)
          method, unless the app is placed on a temporary whitelist to handle a
          task that's visible to the user.
     *    [C-0-8] if the app is targeting API level 25 or higher, they MUST
          release the wakelocks the app holds.

The above list is not comprehensive. The Compatibility Test Suite (CTS) tests
significant portions of the platform for behavioral compatibility, but not all.
It is the responsibility of the implementer to ensure behavioral compatibility
with the Android Open Source Project. For this reason, device implementers
SHOULD use the source code available via the Android Open Source Project where
possible, rather than re-implement significant parts of the system.