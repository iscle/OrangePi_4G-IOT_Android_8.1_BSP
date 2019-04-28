#ifndef ANDROID_HARDWARE_BENCHMARK_V1_0_BENCHMARK_H
#define ANDROID_HARDWARE_BENCHMARK_V1_0_BENCHMARK_H

#include <android/hardware/tests/libhwbinder/1.0/IBenchmark.h>
#include <hidl/Status.h>

namespace android {
namespace hardware {
namespace tests {
namespace libhwbinder {
namespace V1_0 {
namespace implementation {

using ::android::hardware::tests::libhwbinder::V1_0::IBenchmark;
using ::android::hardware::Return;
using ::android::hardware::hidl_vec;

struct Benchmark : public IBenchmark {
  virtual Return<void> sendVec(const hidl_vec<uint8_t>& data, sendVec_cb _hidl_cb)  override;
};

extern "C" IBenchmark* HIDL_FETCH_IBenchmark(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace libhwbinder
}  // namespace tests
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_BENCHMARK_V1_0_BENCHMARK_H
