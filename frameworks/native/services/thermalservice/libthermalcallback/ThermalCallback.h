#ifndef ANDROID_HARDWARE_THERMAL_V1_1_THERMALCALLBACK_H
#define ANDROID_HARDWARE_THERMAL_V1_1_THERMALCALLBACK_H

#include <android/hardware/thermal/1.1/IThermalCallback.h>
#include <android/hardware/thermal/1.0/types.h>
#include <android/os/Temperature.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include "services/thermalservice/ThermalService.h"

namespace android {
namespace hardware {
namespace thermal {
namespace V1_1 {
namespace implementation {

using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::os::ThermalService;

class ThermalCallback : public IThermalCallback {
 public:
    // Register a binder ThermalService object for sending events
    void registerThermalService(sp<ThermalService> thermalService);

    // Methods from IThermalCallback::V1_1 follow.
    Return<void> notifyThrottling(
        bool isThrottling,
        const android::hardware::thermal::V1_0::Temperature& temperature)
        override;

 private:
    // Our registered binder ThermalService object to use for sending events
    sp<android::os::ThermalService> mThermalService;
};

}  // namespace implementation
}  // namespace V1_1
}  // namespace thermal
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_THERMAL_V1_1_THERMALCALLBACK_H
