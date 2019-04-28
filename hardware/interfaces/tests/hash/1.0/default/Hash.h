#ifndef ANDROID_HARDWARE_TESTS_HASH_V1_0_HASH_H
#define ANDROID_HARDWARE_TESTS_HASH_V1_0_HASH_H

#include <android/hardware/tests/hash/1.0/IHash.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

namespace android {
namespace hardware {
namespace tests {
namespace hash {
namespace V1_0 {
namespace implementation {

using ::android::hardware::tests::hash::V1_0::IHash;
using ::android::hidl::base::V1_0::DebugInfo;
using ::android::hidl::base::V1_0::IBase;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

struct Hash : public IHash {
    // Methods from ::android::hardware::tests::hash::V1_0::IHash follow.
    Return<void> dummy() override;
    Return<void> functions() override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.
};

extern "C" IHash* HIDL_FETCH_IHash(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace hash
}  // namespace tests
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_TESTS_HASH_V1_0_HASH_H
