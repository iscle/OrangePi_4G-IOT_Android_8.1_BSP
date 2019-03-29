/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CONTEXTHUB_H_
#define CONTEXTHUB_H_

#include "nanomessage.h"
#include "noncopyable.h"

#include <bitset>
#include <functional>
#include <vector>

namespace android {

class AppToHostEvent;
class SensorEvent;

// Array length helper macro
#define ARRAY_LEN(arr) (sizeof(arr) / sizeof(arr[0]))

enum class SensorType {
    Invalid_ = 0,

    // The order of this enum must correspond to sensor types in nanohub's
    // sensType.h
    Accel,
    AnyMotion,
    NoMotion,
    SignificantMotion,
    Flat,
    Gyro,
    GyroUncal,
    Magnetometer,
    MagnetometerUncal,
    Barometer,
    Temperature,
    AmbientLightSensor,
    Proximity,
    Orientation,
    HeartRateECG,
    HeartRatePPG,
    Gravity,
    LinearAccel,
    RotationVector,
    GeomagneticRotationVector,
    GameRotationVector,
    StepCount,
    StepDetect,
    Gesture,
    Tilt,
    DoubleTwist,
    DoubleTap,
    WindowOrientation,
    Hall,
    Activity,
    Vsync,
    CompressedAccel,
    WristTilt = 39,
    Humidity = 61,

    Max_
};

// Overloaded values of rate used in sensor enable request (see sensors.h)
enum class SensorSpecialRate : uint32_t {
    None     = 0,
    OnDemand = 0xFFFFFF00,
    OnChange = 0xFFFFFF01,
    OneShot  = 0xFFFFFF02,
};

struct SensorSpec {
    SensorType sensor_type = SensorType::Invalid_;

    // When enabling a sensor, rate can be specified in Hz or as one of the
    // special values
    SensorSpecialRate special_rate = SensorSpecialRate::None;
    float rate_hz = -1;
    uint64_t latency_ns = 0;

    // Reference value (ground truth) used for calibration
    bool have_cal_ref = false;
    float cal_ref;
};

/*
 * An interface for communicating with a ContextHub.
 */
class ContextHub : public NonCopyable {
  public:
    virtual ~ContextHub() {};

    static std::string SensorTypeToAbbrevName(SensorType sensor_type);
    static SensorType SensorAbbrevNameToType(const char *abbrev_name);
    static SensorType SensorAbbrevNameToType(const std::string& abbrev_name);
    static std::string ListAllSensorAbbrevNames();

    /*
     * Performs initialization to allow commands to be sent to the context hub.
     * Must be called before any other functions that send commands. Returns
     * true on success, false on failure.
     */
    virtual bool Initialize() = 0;

    /*
     * Configures the ContextHub to allow logs to be printed to stdout.
     */
    virtual void SetLoggingEnabled(bool logging_enabled) = 0;

    /*
     * Loads a new firmware image to the ContextHub. The firmware image is
     * specified by filename. Returns false if an error occurs.
     */
    bool Flash(const std::string& filename);

    /*
     * Performs the sensor calibration routine and writes the resulting data to
     * a file.
     */
    bool CalibrateSensors(const std::vector<SensorSpec>& sensors);

    /*
     * Performs the sensor self-test routine.
     */
    bool TestSensors(const std::vector<SensorSpec>& sensors);

    /*
     * Sends a sensor enable request to the context hub.
     */
    bool EnableSensor(const SensorSpec& sensor);
    bool EnableSensors(const std::vector<SensorSpec>& sensors);

    /*
     * Sends a disable sensor request to context hub. Note that this always
     * results in sending a request, i.e. this does not check whether the sensor
     * is currently enabled or not.
     */
    bool DisableSensor(SensorType sensor_type);
    bool DisableSensors(const std::vector<SensorSpec>& sensors);

    /*
     * Sends a disable sensor request for every sensor type we know about.
     */
    bool DisableAllSensors();

    /*
     * Calls DisableSensor() on all active sensors (i.e. those which have been
     * enabled but not yet disabled). This should be called from the destructor
     * of derived classes before tearing down communications to ensure we don't
     * leave sensors enabled after exiting.
     */
    bool DisableActiveSensors();

    /*
     * Sends all data stored in the calibration file to the context hub.
     */
    virtual bool LoadCalibration();

    /*
     * Prints up to <limit> incoming events. If limit is 0, then continues
     * indefinitely.
     */
    void PrintAllEvents(unsigned int limit);

    /*
     * Requests bridge version information
     */
    bool PrintBridgeVersion();

    /*
     * Prints up to <sample_limit> incoming sensor samples corresponding to the
     * given SensorType, ignoring other events. If sample_limit is 0, then
     * continues indefinitely.
     */
    void PrintSensorEvents(SensorType sensor_type, int sample_limit);
    void PrintSensorEvents(const std::vector<SensorSpec>& sensors,
        int sample_limit);

  protected:
    enum class TransportResult {
        Success,
        GeneralFailure,
        Timeout,
        ParseFailure,
        Canceled,
        // Add more specific error reasons as needed
    };

    // Performs the calibration routine, but does not call SaveCalibration()
    bool CalibrateSingleSensor(const SensorSpec& sensor);

    // Performs the self-test routine
    bool TestSingleSensor(const SensorSpec& sensor);

    /*
     * Iterates over sensors, invoking the given callback on each element.
     * Returns true if all callbacks returned true. Exits early on failure.
     */
    bool ForEachSensor(const std::vector<SensorSpec>& sensors,
        std::function<bool(const SensorSpec&)> callback);

    /*
     * Parses a calibration result event and invokes the appropriate
     * SetCalibration function with the calibration data.
     */
    bool HandleCalibrationResult(const SensorSpec& sensor,
        const AppToHostEvent &event);

    /*
     * Parses a self-test result event
     */
    bool HandleTestResult(const SensorSpec& sensor,
        const AppToHostEvent &event);

    /*
     * Same as ReadSensorEvents, but filters on AppToHostEvent instead of
     * SensorEvent.
     */
    TransportResult ReadAppEvents(std::function<bool(const AppToHostEvent&)> callback,
        int timeout_ms = 0);

    /*
     * Calls ReadEvent in a loop, handling errors and ignoring events that
     * didn't originate from a sensor. Valid SensorEvents are passed to the
     * callback for further processing. The callback should return a boolean
     * indicating whether to continue (true) or exit the read loop (false).
     */
    void ReadSensorEvents(std::function<bool(const SensorEvent&)> callback);

    /*
     * Sends the given calibration data down to the hub
     */
    bool SendCalibrationData(SensorType sensor_type,
        const std::vector<uint8_t>& cal_data);

    /*
     * Read an event from the sensor hub. Block until a event is successfully
     * read, no event traffic is generated for the timeout period, or an error
     * occurs, such as a CRC check failure.
     */
    virtual TransportResult ReadEvent(std::vector<uint8_t>& response,
        int timeout_ms) = 0;
    virtual TransportResult WriteEvent(const std::vector<uint8_t>& request) = 0;

    // Implements the firmware loading functionality for the sensor hub. Returns
    // false if an error occurs while writing the firmware to the device.
    virtual bool FlashSensorHub(const std::vector<uint8_t>& bytes) = 0;

    // Convenience functions that build on top of the more generic byte-level
    // interface
    TransportResult ReadEvent(std::unique_ptr<ReadEventResponse>* response,
        int timeout_ms = 0);
    TransportResult WriteEvent(const WriteEventRequest& request);

    // Override these if saving calibration data to persistent storage is
    // supported on the platform
    virtual bool SetCalibration(SensorType sensor_type, int32_t data);
    virtual bool SetCalibration(SensorType sensor_type, float data);
    virtual bool SetCalibration(SensorType sensor_type, int32_t x,
        int32_t y, int32_t z);
    virtual bool SetCalibration(SensorType sensor_type, int32_t x,
        int32_t y, int32_t z, int32_t w);
    virtual bool SaveCalibration();

private:
    std::bitset<static_cast<int>(SensorType::Max_)> sensor_is_active_;
};

}  // namespace android

#endif  // CONTEXTHUB_H_
