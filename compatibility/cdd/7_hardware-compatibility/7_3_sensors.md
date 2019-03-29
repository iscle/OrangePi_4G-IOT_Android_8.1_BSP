## 7.3\. Sensors

If device implementations include a particular sensor type that has a
corresponding API for third-party developers, the device implementation
MUST implement that API as described in the Android SDK documentation and
the Android Open Source documentation on [sensors](
http://source.android.com/devices/sensors/).

Device implementations:

*   [C-0-1] MUST accurately report the presence or absence of sensors per the
[`android.content.pm.PackageManager`](
http://developer.android.com/reference/android/content/pm/PackageManager.html)
class.
*   [C-0-2] MUST return an accurate list of supported sensors via the
`SensorManager.getSensorList()` and similar methods.
*   [C-0-3] MUST behave reasonably for all other sensor APIs (for example, by
returning `true` or `false` as appropriate when applications attempt to register
listeners, not calling sensor listeners when the corresponding sensors are not
present; etc.).

If device implementations include a particular sensor type that has a
corresponding API for third-party developers, they:

*   [C-1-1] MUST [report all sensor measurements](
http://developer.android.com/reference/android/hardware/SensorEvent.html)
using the relevant International System of Units (metric) values for each
sensor type as defined in the Android SDK documentation.
*   [C-1-2] MUST report sensor data with a maximum latency of 100 milliseconds
+ 2 * sample_time for the case of a sensor streamed with a minimum required
latency of 5 ms + 2 * sample_time when the application processor is active.
This delay does not include any filtering delays.
*   [C-1-3] MUST report the first sensor sample within 400 milliseconds + 2 *
sample_time of the sensor being activated. It is acceptable for this sample to
have an accuracy of 0.
*   [SR] SHOULD [report the event time](
http://developer.android.com/reference/android/hardware/SensorEvent.html#timestamp)
in nanoseconds as defined in the Android SDK documentation, representing the
time the event happened and synchronized with the
SystemClock.elapsedRealtimeNano() clock. Existing and new Android devices are
**STRONGLY RECOMMENDED** to meet these requirements so they will be able to
upgrade to the future platform releases where this might become a REQUIRED
component. The synchronization error SHOULD be below 100 milliseconds.

*   [C-1-7] For any API indicated by the Android SDK documentation to be a
     [continuous sensor](
     https://source.android.com/devices/sensors/report-modes.html#continuous),
     device implementations MUST continuously provide
     periodic data samples that SHOULD have a jitter below 3%,
     where jitter is defined as the standard deviation of the difference of the
     reported timestamp values between consecutive events.

*   [C-1-8] MUST ensure that the sensor event stream
     MUST NOT prevent the device CPU from entering a suspend state or waking up
     from a suspend state.
*   When several sensors are activated, the power consumption SHOULD NOT exceed
     the sum of the individual sensor’s reported power consumption.

The list above is not comprehensive; the documented behavior of the Android SDK
and the Android Open Source Documentations on
[sensors](http://source.android.com/devices/sensors/) is to be considered
authoritative.


Some sensor types are composite, meaning they can be derived from data provided
by one or more other sensors. (Examples include the orientation sensor and the
linear acceleration sensor.)

Device implementations:

*   SHOULD implement these sensor types, when they
include the prerequisite physical sensors as described
in [sensor types](https://source.android.com/devices/sensors/sensor-types.html).

If device implementations include a composite sensor, they:

*    [C-2-1] MUST implement the sensor as described in the Android Open Source
documentation on [composite sensors](
https://source.android.com/devices/sensors/sensor-types.html#composite_sensor_type_summary).


### 7.3.1\. Accelerometer

*   Device implementations SHOULD include a 3-axis accelerometer.
*   [H-SR] Handheld device implementations are STRONGLY RECOMMENDED to
    include a 3-axis accelerometer.
*   [A-SR] Automotive device implementations are STRONGLY RECOMMENDED to
    include a 3-axis accelerometer.
*   [W-SR] Watch device implementations are STRONGLY RECOMMENDED to
    include a 3-axis accelerometer.



If Handheld device implementations include a 3-axis accelerometer, they:

*   [H-1-1] MUST be able to report events up to a frequency of at least 100 Hz.

If Automotive device implementations include a 3-axis accelerometer, they:

*   [A-1-1] MUST be able to report events up to a frequency of at least 100 Hz.
*   [A-1-2] MUST comply with the Android
    [car sensor coordinate system](
    http://source.android.com/devices/sensors/sensor-types.html#auto_axes).

If device implementations include a 3-axis accelerometer, they:

*   [C-1-1] MUST be able to report events up to a frequency of at least 50 Hz.
*   [C-1-2] MUST implement and report [`TYPE_ACCELEROMETER`](
    http://developer.android.com/reference/android/hardware/Sensor.html#TYPE_ACCELEROMETER)
    sensor.
*   [C-1-3] MUST comply with the [Android sensor coordinate system](
http://developer.android.com/reference/android/hardware/SensorEvent.html)
as detailed in the Android APIs.
*   [C-1-4] MUST be capable of measuring from freefall up to four times the
gravity(4g) or more on any axis.
*   [C-1-5] MUST have a resolution of at least 12-bits.
*   [C-1-6] MUST have a standard deviation no greater than 0.05 m/s^, where
the standard deviation should be calculated on a per axis basis on samples
collected over a period of at least 3 seconds at the fastest sampling rate.
*   [SR] are **STRONGLY RECOMMENDED** to implement the `TYPE_SIGNIFICANT_MOTION`
    composite sensor.
*   [SR] are STRONGLY RECOMMENDED to implement the
    `TYPE_ACCELEROMETER_UNCALIBRATED` sensor if online accelerometer calibration
    is available.
*   SHOULD implement the `TYPE_SIGNIFICANT_MOTION`, `TYPE_TILT_DETECTOR`,
`TYPE_STEP_DETECTOR`, `TYPE_STEP_COUNTER` composite sensors as described
in the Android SDK document.
*   SHOULD report events up to at least 200 Hz.
*   SHOULD have a resolution of at least 16-bits.
*   SHOULD be calibrated while in use if the characteristics changes over
the life cycle and compensated, and preserve the compensation parameters
between device reboots.
*   SHOULD be temperature compensated.
*   SHOULD also implement [`TYPE_ACCELEROMETER_UNCALIBRATED`](
https://developer.android.com/reference/android/hardware/Sensor.html#STRING_TYPE_ACCELEROMETER_UNCALIBRATED)
    sensor.

If device implementations include a 3-axis accelerometer and any of the
`TYPE_SIGNIFICANT_MOTION`, `TYPE_TILT_DETECTOR`, `TYPE_STEP_DETECTOR`,
`TYPE_STEP_COUNTER` composite sensors are implemented:

*   [C-2-1] The sum of their power consumption MUST always be less than 4 mW.
*   SHOULD each be below 2 mW and 0.5 mW for when the device is in a dynamic or
    static condition.

If device implementations include a 3-axis accelerometer and a gyroscope sensor,
they:

*   [C-3-1] MUST implement the `TYPE_GRAVITY` and `TYPE_LINEAR_ACCELERATION`
composite sensors.
*   SHOULD implement the `TYPE_GAME_ROTATION_VECTOR` composite sensor.
*   [SR] Existing and new Android devices are STRONGLY RECOMMENDED to
implement the `TYPE_GAME_ROTATION_VECTOR` sensor.

If device implementations include a 3-axis accelerometer, a gyroscope sensor
and a magnetometer sensor, they:

*   [C-4-1] MUST implement a `TYPE_ROTATION_VECTOR` composite sensor.

### 7.3.2\. Magnetometer

*   Device implementations SHOULD include a 3-axis magnetometer (compass).

If device impelementations include a 3-axis magnetometer, they:

*   [C-1-1] MUST implement the `TYPE_MAGNETIC_FIELD` sensor.
*   [C-1-2] MUST be able to report events up to a frequency of at least 10 Hz
and SHOULD report events up to at least 50 Hz.
*   [C-1-3] MUST comply with the [Android sensor coordinate system](
    http://developer.android.com/reference/android/hardware/SensorEvent.html)
    as detailed in the
    Android APIs.
*   [C-1-4] MUST be capable of measuring between -900 µT and +900 µT on each
axis before saturating.
*   [C-1-5] MUST have a hard iron offset value less than 700 µT and SHOULD have
a value below 200 µT, by placing the magnetometer far from
dynamic (current-induced) and static (magnet-induced) magnetic fields.
*   [C-1-6] MUST have a resolution equal or denser than 0.6 µT.
*   [C-1-7] MUST support online calibration and compensation of the hard iron
    bias, and preserve the compensation parameters between device reboots.
*   [C-1-8] MUST have the soft iron compensation applied—the calibration can be
done either while in use or during the production of the device.
*   [C-1-9] MUST have a standard deviation, calculated on a per axis basis on
samples collected over a period of at least 3 seconds at the fastest sampling
rate, no greater than 1.5 µT; SHOULD have a standard deviation no greater than
0.5 µT.
*   SHOULD implement `TYPE_MAGNETIC_FIELD_UNCALIBRATED` sensor.
*   [SR] Existing and new Android devices are STRONGLY RECOMMENDED to implement the
    `TYPE_MAGNETIC_FIELD_UNCALIBRATED` sensor.


If device impelementations include a 3-axis magnetometer, an accelerometer
sensor and a gyroscope sensor, they:

*   [C-2-1] MUST implement a `TYPE_ROTATION_VECTOR` composite sensor.

If device impelementations include a 3-axis magnetometer, an accelerometer, they:

*   MAY implement the `TYPE_GEOMAGNETIC_ROTATION_VECTOR` sensor.

If device impelementations include a 3-axis magnetometer, an accelerometer and
`TYPE_GEOMAGNETIC_ROTATION_VECTOR` sensor, they:

*   [C-3-1] MUST consume less than 10 mW.
*   SHOULD consume less than 3 mW when the sensor is registered for batch mode at 10 Hz.

### 7.3.3\. GPS

Device implementations:

*   SHOULD include a GPS/GNSS receiver.

If device implementations include a GPS/GNSS receiver and report the capability
to applications through the `android.hardware.location.gps` feature flag, they:

*   [C-1-1] MUST support location outputs at a rate of at least 1 Hz when
requested via `LocationManager#requestLocationUpdate`.
*   [C-1-2] MUST be able to determine the location in open-sky conditions
    (strong signals, negligible multipath, HDOP < 2) within 10 seconds (fast
    time to first fix), when connected to a 0.5 Mbps or faster data speed
    internet connection. This requirement is typically met by the use of some
    form of Assisted or Predicted GPS/GNSS technique
    to minimize GPS/GNSS lock-on time (Assistance data includes Reference Time,
    Reference Location and Satellite Ephemeris/Clock).
       * [SR] After making such a location calculation, it is
         STRONGLY RECOMMENDED for the device to
         be able to determine its location, in open sky, within 10 seconds,
         when location requests are restarted, up to an hour after the initial
         location calculation, even when the subsequent request is made without
         a data connection, and/or after a power cycle.
*   In open sky conditions after determining the location, while stationary or
    moving with less than 1 meter per second squared of acceleration:

       * [C-1-3] MUST be able to determine location within 20 meters, and speed
         within 0.5 meters per second, at least 95% of the time.
       * [C-1-4] MUST simultaneously track and report via
       [`GnssStatus.Callback`](
       https://developer.android.com/reference/android/location/GnssStatus.Callback.html#GnssStatus.Callback()')
         at least 8 satellites from one constellation.
       * SHOULD be able to simultaneously track at least 24 satellites, from
       multiple constellations (e.g. GPS + at least one of Glonass, Beidou,
       Galileo).
*   [C-1-5] MUST report the GNSS technology generation through the test API
‘getGnssYearOfHardware’.
*    [SR] Continue to deliver normal GPS/GNSS location outputs during an
emergency phone call.
*    [SR] Report GNSS measurements from all constellations tracked (as reported
in GnssStatus messages), with the exception of SBAS.
*    [SR] Report AGC, and Frequency of GNSS measurement.
*    [SR] Report all accuracy estimates (including Bearing, Speed, and Vertical)
as part of each GPS Location.
*    [SR] are STRONGLY RECOMMENDED to meet as many as possible from the
additional mandatory requirements for devices reporting the year "2016" or
"2017" through the Test API `LocationManager.getGnssYearOfHardware()`.

If Automotive device implementations include a GPS/GNSS receiver and report
the capability to applications through the `android.hardware.location.gps`
feature flag:

*   [A-1-1] GNSS technology generation MUST be the year "2017" or newer.

If device implementations include a GPS/GNSS receiver and report the capability
to applications through the `android.hardware.location.gps` feature flag and the
`LocationManager.getGnssYearOfHardware()` Test API reports the year "2016" or
newer, they:

*    [C-2-1] MUST report GPS measurements, as soon as they are found, even if a
location calculated from GPS/GNSS is not yet reported.
*    [C-2-2] MUST report GPS pseudoranges and pseudorange rates, that, in
open-sky conditions after determining the location, while stationary or moving
with less than 0.2 meter per second squared of acceleration, are sufficient to
calculate position within 20 meters, and speed within 0.2 meters per second,
at least 95% of the time.

If device implementations include a GPS/GNSS receiver and report the capability
to applications through the `android.hardware.location.gps` feature flag and the
`LocationManager.getGnssYearOfHardware()` Test API reports the year "2017" or
newer, they:

*    [C-3-1] MUST continue to deliver normal GPS/GNSS location outputs during an
emergency phone call.
*    [C-3-2] MUST report GNSS measurements from all constellations tracked (as
reported in
     GnssStatus messages), with the exception of SBAS.
*    [C-3-3] MUST report AGC, and Frequency of GNSS measurement.
*    [C-3-4] MUST report all accuracy estimates (including Bearing, Speed, and
Vertical) as part of each GPS Location.


### 7.3.4\. Gyroscope

Device implementations:

*    SHOULD include a gyroscope (angular change sensor).
*    SHOULD NOT include a gyroscope sensor unless a 3-axis accelerometer is
also included.

If device implementations include a gyroscope, they:

*   [C-1-1] MUST be able to report events up to a frequency of at least 50 Hz.
*   [C-1-2] MUST implement the `TYPE_GYROSCOPE` sensor and SHOULD also implement
`TYPE_GYROSCOPE_UNCALIBRATED` sensor.
*   [C-1-3] MUST be capable of measuring orientation changes up to 1,000 degrees
per second.
*   [C-1-4] MUST have a resolution of 12-bits or more and SHOULD have a
resolution of 16-bits or more.
*   [C-1-5] MUST be temperature compensated.
*   [C-1-6] MUST be calibrated and compensated while in use, and preserve the
    compensation parameters between device reboots.
*   [C-1-7] MUST have a variance no greater than 1e-7 rad^2 / s^2 per Hz
(variance per Hz, or rad^2 / s). The variance is allowed to vary with the
sampling rate, but MUST be constrained by this value. In other words, if you
measure the variance of the gyro at 1 Hz sampling rate it SHOULD be no greater
than 1e-7 rad^2/s^2.
*   [SR] Existing and new Android devices are STRONGLY RECOMMENDED to
implement the `SENSOR_TYPE_GYROSCOPE_UNCALIBRATED` sensor.
*   [SR] Calibration error is STRONGLY RECOMMENDED to be less than 0.01 rad/s
when device is stationary at room temperature.
*   SHOULD report events up to at least 200 Hz.

If Handheld device implementations include a gyroscope, they:

*   [H-1-1] MUST be able to report events up to a frequency of at least 100 Hz.

If Automotive device implementations include a gyroscope, they:

*   [A-1-1] MUST be able to report events up to a frequency of at least 100 Hz.

If Television device implementations include a gyroscope, they:

*   [T-1-1] MUST be able to report events up to a frequency of at least 100 Hz.


If device implementations include a gyroscope, an accelerometer sensor and a
magnetometer sensor, they:

*   [C-2-1] MUST implement a `TYPE_ROTATION_VECTOR` composite sensor.

If device implementations include a gyroscope and a accelerometer sensor, they:

*   [C-3-1] MUST implement the `TYPE_GRAVITY` and
`TYPE_LINEAR_ACCELERATION` composite sensors.
*   [SR] Existing and new Android devices are STRONGLY RECOMMENDED to implement
the `TYPE_GAME_ROTATION_VECTOR` sensor.
*   SHOULD implement the `TYPE_GAME_ROTATION_VECTOR` composite sensor.

### 7.3.5\. Barometer

*    Device implementations SHOULD include a barometer (ambient air pressure
sensor).

If device implementations include a barometer, they:

*   [C-1-1] MUST implement and report `TYPE_PRESSURE` sensor.
*   [C-1-2] MUST be able to deliver events at 5 Hz or greater.
*   [C-1-3] MUST be temperature compensated.
*   [SR] STRONGLY RECOMMENDED to be able to report pressure measurements in the
    range 300hPa to 1100hPa.
*   SHOULD have an absolute accuracy of 1hPa.
*   SHOULD have a relative accuracy of 0.12hPa over 20hPa range
    (equivalent to ~1m accuracy over ~200m change at sea level).

### 7.3.6\. Thermometer

Device implementations:
*   MAY include an ambient thermometer (temperature sensor).
*   MAY but SHOULD NOT include a CPU temperature sensor.

If device implementations include an ambient thermometer (temperature sensor),
they:

*   [C-1-1] MUST be defined as `SENSOR_TYPE_AMBIENT_TEMPERATURE` and MUST
    measure the ambient (room/vehicle cabin) temperature from where the user
    is interacting with the device in degrees Celsius.
*   [C-1-2] MUST be defined as `SENSOR_TYPE_TEMPERATURE`.
*   [C-1-3] MUST measure the temperature of the device CPU.
*   [C-1-4] MUST NOT measure any other temperature.

Note the `SENSOR_TYPE_TEMPERATURE` sensor type was deprecated in Android 4.0.

### 7.3.7\. Photometer

*   Device implementations MAY include a photometer (ambient light sensor).

### 7.3.8\. Proximity Sensor

*   Device implementations MAY include a proximity sensor.
*   Handheld device implementations that can make a voice call and indicate
any value other than `PHONE_TYPE_NONE` in `getPhoneType`
SHOULD include a proximity sensor.

If device implementations include a proximity sensor, they:

*   [C-1-1] MUST measure the proximity of an object in the same direction as the
    screen. That is, the proximity sensor MUST be oriented to detect objects
    close to the screen, as the primary intent of this sensor type is to
    detect a phone in use by the user. If device implementations include a
    proximity sensor with any other orientation, it MUST NOT be accessible
    through this API.
*   [C-1-2] MUST have 1-bit of accuracy or more.


### 7.3.9\. High Fidelity Sensors

If device implementations include a set of higher quality sensors as defined
in this section, and make available them to third-party apps, they:

*   [C-1-1] MUST identify the capability through the
`android.hardware.sensor.hifi_sensors` feature flag.

If device implementations declare `android.hardware.sensor.hifi_sensors`,
they:

*   [C-2-1] MUST have a `TYPE_ACCELEROMETER` sensor which:
    *   MUST have a measurement range between at least -8g and +8g.
    *   MUST have a measurement resolution of at least 1024 LSB/G.
    *   MUST have a minimum measurement frequency of 12.5 Hz or lower.
    *   MUST have a maximum measurement frequency of 400 Hz or higher.
    *   MUST have a measurement noise not above 400 uG/√Hz.
    *   MUST implement a non-wake-up form of this sensor with a buffering
        capability of at least 3000 sensor events.
    *   MUST have a batching power consumption not worse than 3 mW.
    *   SHOULD have a stationary noise bias stability of \<15 μg √Hz from 24hr static
        dataset.
    *   SHOULD have a bias change vs. temperature of ≤ +/- 1mg / °C.
    *   SHOULD have a best-fit line non-linearity of ≤ 0.5%, and sensitivity change vs. temperature of ≤
        0.03%/C°.
    *   SHOULD have white noise spectrum to ensure adequate qualification
        of sensor’s noise integrity.

*   [C-2-2] MUST have a `TYPE_ACCELEROMETER_UNCALIBRATED` with the same
quality requirements as `TYPE_ACCELEROMETER`.

*   [C-2-3] MUST have a `TYPE_GYROSCOPE` sensor which:
    *   MUST have a measurement range between at least -1000 and +1000 dps.
    *   MUST have a measurement resolution of at least 16 LSB/dps.
    *   MUST have a minimum measurement frequency of 12.5 Hz or lower.
    *   MUST have a maximum measurement frequency of 400 Hz or higher.
    *   MUST have a measurement noise not above 0.014°/s/√Hz.
    *   SHOULD have a stationary bias stability of < 0.0002 °/s √Hz from 24-hour static dataset.
    *   SHOULD have a bias change vs. temperature of ≤ +/- 0.05 °/ s / °C.
    *   SHOULD have a sensitivity change vs. temperature of ≤ 0.02% / °C.
    *   SHOULD have a best-fit line non-linearity of ≤ 0.2%.
    *   SHOULD have a noise density of ≤ 0.007 °/s/√Hz.
    *   SHOULD have white noise spectrum to ensure adequate qualification
        of sensor’s noise integrity.
    *   SHOULD have calibration error less than 0.002 rad/s in
        temperature range 10 ~ 40 ℃ when device is stationary.

*   [C-2-4] MUST have a `TYPE_GYROSCOPE_UNCALIBRATED` with the same quality
requirements as `TYPE_GYROSCOPE`.
*   [C-2-5] MUST have a `TYPE_GEOMAGNETIC_FIELD` sensor which:
    *   MUST have a measurement range between at least -900 and +900 uT.
    *   MUST have a measurement resolution of at least 5 LSB/uT.
    *   MUST have a minimum measurement frequency of 5 Hz or lower.
    *   MUST have a maximum measurement frequency of 50 Hz or higher.
    *   MUST have a measurement noise not above 0.5 uT.
*   [C-2-6] MUST have a `TYPE_MAGNETIC_FIELD_UNCALIBRATED` with the same quality
requirements as `TYPE_GEOMAGNETIC_FIELD` and in addition:
    *   MUST implement a non-wake-up form of this sensor with a buffering
        capability of at least 600 sensor events.
    *   SHOULD have white noise spectrum to ensure adequate qualification
        of sensor’s noise integrity.
*   [C-2-7] MUST have a `TYPE_PRESSURE` sensor which:
    *   MUST have a measurement range between at least 300 and 1100 hPa.
    *   MUST have a measurement resolution of at least 80 LSB/hPa.
    *   MUST have a minimum measurement frequency of 1 Hz or lower.
    *   MUST have a maximum measurement frequency of 10 Hz or higher.
    *   MUST have a measurement noise not above 2 Pa/√Hz.
    *   MUST implement a non-wake-up form of this sensor with a buffering
        capability of at least 300 sensor events.
    *   MUST have a batching power consumption not worse than 2 mW.
*   [C-2-8] MUST have a `TYPE_GAME_ROTATION_VECTOR` sensor which:
    *   MUST implement a non-wake-up form of this sensor with a buffering
        capability of at least 300 sensor events.
    *   MUST have a batching power consumption not worse than 4 mW.
*   [C-2-9] MUST have a `TYPE_SIGNIFICANT_MOTION` sensor which:
    *   MUST have a power consumption not worse than 0.5 mW when device is
        static and 1.5 mW when device is moving.
*   [C-2-10] MUST have a `TYPE_STEP_DETECTOR` sensor which:
    *   MUST implement a non-wake-up form of this sensor with a buffering
        capability of at least 100 sensor events.
    *   MUST have a power consumption not worse than 0.5 mW when device is
        static and 1.5 mW when device is moving.
    *   MUST have a batching power consumption not worse than 4 mW.
*   [C-2-11] MUST have a `TYPE_STEP_COUNTER` sensor which:
    *   MUST have a power consumption not worse than 0.5 mW when device is
        static and 1.5 mW when device is moving.
*   [C-2-12] MUST have a `TILT_DETECTOR` sensor which:
    *   MUST have a power consumption not worse than 0.5 mW when device is
        static and 1.5 mW when device is moving.
*   [C-2-13] The event timestamp of the same physical event reported by the
Accelerometer, Gyroscope sensor and Magnetometer MUST be within 2.5
milliseconds of each other.
*   [C-2-14] MUST have Gyroscope sensor event timestamps on the same time
base as the camera subsystem and within 1 milliseconds of error.
*   [C-2-15] MUST deliver samples to applications within 5 milliseconds from
the time when the data is available on any of the above physical sensors
to the application.
*   [C-2-16] MUST not have a power consumption higher than 0.5 mW
when device is static and 2.0 mW when device is moving
when any combination of the following sensors are enabled:
    *   `SENSOR_TYPE_SIGNIFICANT_MOTION`
    *   `SENSOR_TYPE_STEP_DETECTOR`
    *   `SENSOR_TYPE_STEP_COUNTER`
    *   `SENSOR_TILT_DETECTORS`
*   [C-2-17] MAY have a `TYPE_PROXIMITY` sensor, but if present MUST have
a minimum buffer capability of 100 sensor events.

Note that all power consumption requirements in this section do not include the
power consumption of the Application Processor. It is inclusive of the power
drawn by the entire sensor chain—the sensor, any supporting circuitry, any
dedicated sensor processing system, etc.

If device implementations include direct sensor support, they:

* [C-3-1] MUST correctly declare support of direct channel types and direct
  report rates level through the [`isDirectChannelTypeSupported`](
  https://developer.android.com/reference/android/hardware/Sensor.html#isDirectChannelTypeSupported%28int%29)
  and [`getHighestDirectReportRateLevel`](
  https://developer.android.com/reference/android/hardware/Sensor.html#getHighestDirectReportRateLevel%28%29)
  API.
* [C-3-2] MUST support at least one of the two sensor direct channel types
  for all sensors that declare support for sensor direct channel
  *   [`TYPE_HARDWARE_BUFFER`](https://developer.android.com/reference/android/hardware/SensorDirectChannel.html#TYPE_HARDWARE_BUFFER)
  *   [`TYPE_MEMORY_FILE`](https://developer.android.com/reference/android/hardware/SensorDirectChannel.html#TYPE_MEMORY_FILE)
* SHOULD support event reporting through sensor direct channel for primary
  sensor (non-wakeup variant) of the following types:
  *   `TYPE_ACCELEROMETER`
  *   `TYPE_ACCELEROMETER_UNCALIBRATED`
  *   `TYPE_GYROSCOPE`
  *   `TYPE_GYROSCOPE_UNCALIBRATED`
  *   `TYPE_MAGNETIC_FIELD`
  *   `TYPE_MAGNETIC_FIELD_UNCALIBRATED`

### 7.3.10\. Fingerprint Sensor

If device implementations include a secure lock screen, they:

*    SHOULD include a fingerprint sensor.

If device implementations include a fingerprint sensor and make the sensor
available to third-party apps, they:

*   [C-1-1] MUST declare support for the `android.hardware.fingerprint` feature.
*   [C-1-2] MUST fully implement the
[corresponding API](
https://developer.android.com/reference/android/hardware/fingerprint/package-summary.html)
as described in the Android SDK documentation.
*   [C-1-3] MUST have a false acceptance rate not higher than 0.002%.
*   [C-1-4] MUST rate limit attempts for at least 30 seconds after five false
trials for fingerprint verification.
*   [C-1-5] MUST have a hardware-backed keystore implementation, and perform the
fingerprint matching in a Trusted Execution Environment (TEE) or on a chip with
a secure channel to the TEE.
*   [C-1-6] MUST have all identifiable fingerprint data encrypted and
cryptographically authenticated such that they cannot be acquired, read or
altered outside of the Trusted Execution Environment (TEE) as documented in the
[implementation guidelines](
https://source.android.com/devices/tech/security/authentication/fingerprint-hal.html)
on the Android Open Source Project site.
*   [C-1-7] MUST prevent adding a fingerprint without first establishing a chain
of trust by having the user confirm existing or add a new device credential
(PIN/pattern/password) that's secured by TEE; the Android Open Source Project
    implementation provides the mechanism in the framework to do so.
*   [C-1-8] MUST NOT enable 3rd-party applications to distinguish between
individual fingerprints.
*   [C-1-9] MUST honor the DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT
flag.
*   [C-1-10] MUST, when upgraded from a version earlier than Android 6.0, have
the fingerprint data securely migrated to meet the above requirements or
removed.
*   [SR] STRONGLY RECOMMENDED to have a false rejection rate of less than 10%,
as measured on the device.
*   [SR] STRONGLY RECOMMENDED to have a latency below 1 second, measured from
when the fingerprint sensor is touched until the screen is unlocked, for one
enrolled finger.
*   SHOULD use the Android Fingerprint icon provided in the Android Open Source
Project.

### 7.3.11\. Android Automotive-only sensors

Automotive-specific sensors are defined in the
`android.car.CarSensorManager API`.


#### 7.3.11.1\. Current Gear

*    Android Automotive implementations SHOULD provide current gear as
`SENSOR_TYPE_GEAR`.

#### 7.3.11.2\. Day Night Mode

Automotive device implementations:

*    [A-0-1] MUST support day/night mode
defined as `SENSOR_TYPE_NIGHT`.
*    [A-0-2] The value of the `SENSOR_TYPE_NIGHT` flag MUST be consistent with
dashboard day/night mode and SHOULD be based on ambient light sensor input.

*    The underlying ambient light sensor MAY be the same as
[Photometer](#7_3_7_photometer).

#### 7.3.11.3\. Driving Status

Automotive device implementations:

*    [A-0-1] MUST support driving status
defined as `SENSOR_TYPE_DRIVING_STATUS`, with a default value of
`DRIVE_STATUS_UNRESTRICTED` when the vehicle is fully stopped and parked. It is
the responsibility of device manufacturers to configure
`SENSOR_TYPE_DRIVING_STATUS` in compliance with all
laws and regulations that apply to markets where the product is shipping.

#### 7.3.11.4\. Wheel Speed

Automotive device implementations:

*    [A-0-1] MUST provide vehicle speed defined as `SENSOR_TYPE_CAR_SPEED`.

## 7.3.12\. Pose Sensor

Device implementations:

*   MAY support pose sensor with 6 degrees of freedom.

Handheld device implementations are:

*   RECOMMENDED to support this sensor.

If device implementations support pose sensor with 6 degrees of freedom, they:

*   [C-1-1] MUST implement and report [`TYPE_POSE_6DOF`](
https://developer.android.com/reference/android/hardware/Sensor.html#TYPE_POSE_6DOF)
sensor.
*   [C-1-2] MUST be more accurate than the rotation vector alone.
