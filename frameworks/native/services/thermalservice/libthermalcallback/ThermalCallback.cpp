#define LOG_TAG "android.hardware.thermal.thermalcallback@1.1-impl"
#include <log/log.h>

#include "ThermalCallback.h"
#include "services/thermalservice/ThermalService.h"
#include <math.h>
#include <android/os/Temperature.h>
#include <hardware/thermal.h>

namespace android {
namespace hardware {
namespace thermal {
namespace V1_1 {
namespace implementation {

using ::android::os::ThermalService;
using ::android::hardware::thermal::V1_0::TemperatureType;

// Register a binder ThermalService object for sending events
void ThermalCallback::registerThermalService(sp<ThermalService> thermalService)
{
    mThermalService = thermalService;
}

// Methods from IThermalCallback::V1_1 follow.
Return<void> ThermalCallback::notifyThrottling(
      bool isThrottling,
      const android::hardware::thermal::V1_0::Temperature& temperature) {

    // Convert HIDL IThermal Temperature to binder IThermalService Temperature.
    if (mThermalService != nullptr) {
        float value = NAN;
        int type = DEVICE_TEMPERATURE_UNKNOWN;

        switch(temperature.type) {
          case TemperatureType::CPU:
            type = DEVICE_TEMPERATURE_CPU;
            break;
          case TemperatureType::GPU:
            type = DEVICE_TEMPERATURE_GPU;
            break;
          case TemperatureType::BATTERY:
            type = DEVICE_TEMPERATURE_BATTERY;
            break;
          case TemperatureType::SKIN:
            type = DEVICE_TEMPERATURE_SKIN;
            break;
          case TemperatureType::UNKNOWN:
          default:
            type = DEVICE_TEMPERATURE_UNKNOWN;
            break;
        }

        value = temperature.currentValue == UNKNOWN_TEMPERATURE ? NAN :
            temperature.currentValue;

        android::os::Temperature thermal_svc_temp(value, type);
        mThermalService->notifyThrottling(isThrottling, thermal_svc_temp);
    } else {
        ALOGE("IThermalService binder service not created, drop throttling event");
    }
    return Void();
}

}  // namespace implementation
}  // namespace V1_1
}  // namespace thermal
}  // namespace hardware
}  // namespace android
