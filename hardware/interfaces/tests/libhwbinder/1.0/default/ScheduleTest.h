#ifndef ANDROID_HARDWARE_TESTS_LIBHWBINDER_V1_0_SCHEDULETEST_H
#define ANDROID_HARDWARE_TESTS_LIBHWBINDER_V1_0_SCHEDULETEST_H

#include <android/hardware/tests/libhwbinder/1.0/IScheduleTest.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

namespace android {
namespace hardware {
namespace tests {
namespace libhwbinder {
namespace V1_0 {
namespace implementation {

using ::android::hardware::tests::libhwbinder::V1_0::IScheduleTest;
using ::android::hidl::base::V1_0::DebugInfo;
using ::android::hidl::base::V1_0::IBase;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

struct ScheduleTest : public IScheduleTest {
    // Methods from ::android::hardware::tests::libhwbinder::V1_0::IScheduleTest
    // follow.
    Return<uint32_t> send(uint32_t cfg, uint32_t callerSta) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.
};

extern "C" IScheduleTest* HIDL_FETCH_IScheduleTest(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace libhwbinder
}  // namespace tests
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_TESTS_LIBHWBINDER_V1_0_SCHEDULETEST_H
