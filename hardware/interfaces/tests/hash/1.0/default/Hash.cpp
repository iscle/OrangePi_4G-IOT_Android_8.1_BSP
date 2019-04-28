#include "Hash.h"

namespace android {
namespace hardware {
namespace tests {
namespace hash {
namespace V1_0 {
namespace implementation {

// Methods from ::android::hardware::tests::hash::V1_0::IHash follow.
Return<void> Hash::dummy() {
    return Void();
}

Return<void> Hash::functions() {
    return Void();
}

// Methods from ::android::hidl::base::V1_0::IBase follow.

IHash* HIDL_FETCH_IHash(const char* /* name */) {
    return new Hash();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace hash
}  // namespace tests
}  // namespace hardware
}  // namespace android
