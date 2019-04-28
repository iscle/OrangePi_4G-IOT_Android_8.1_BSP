#define LOG_TAG "libhwbinder_benchmark"

#include "Benchmark.h"

namespace android {
namespace hardware {
namespace tests {
namespace libhwbinder {
namespace V1_0 {
namespace implementation {

Return<void> Benchmark::sendVec(
        const ::android::hardware::hidl_vec<uint8_t>& data,
        sendVec_cb _hidl_cb) {
    _hidl_cb(data);
    return Void();
}

IBenchmark* HIDL_FETCH_IBenchmark(const char* /* name */) {
    return new Benchmark();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace libhwbinder
}  // namespace tests
}  // namespace hardware
}  // namespace android
