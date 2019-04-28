#ifndef ANDROID_HARDWARE_TESTS_BAR_V1_0_IMPORTTYPES_H
#define ANDROID_HARDWARE_TESTS_BAR_V1_0_IMPORTTYPES_H

#include <android/hardware/tests/bar/1.0/IImportTypes.h>
#include <hidl/Status.h>

#include <hidl/MQDescriptor.h>
namespace android {
namespace hardware {
namespace tests {
namespace bar {
namespace V1_0 {
namespace implementation {

using ::android::hardware::tests::bar::V1_0::IImportTypes;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

struct ImportTypes : public IImportTypes {
    // Methods from ::android::hardware::tests::bar::V1_0::IImportTypes follow.

};

extern "C" IImportTypes* HIDL_FETCH_IImportTypes(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace bar
}  // namespace tests
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_TESTS_BAR_V1_0_IMPORTTYPES_H
