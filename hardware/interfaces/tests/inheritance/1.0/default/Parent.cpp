#define LOG_TAG "hidl_test"

#include <log/log.h>

#include "Parent.h"

namespace android {
namespace hardware {
namespace tests {
namespace inheritance {
namespace V1_0 {
namespace implementation {

// Methods from ::android::hardware::tests::inheritance::V1_0::IGrandparent follow.
Return<void> Parent::doGrandparent()  {
    ALOGI("SERVER(Bar) Parent::doGrandparent");
    return Void();
}

// Methods from ::android::hardware::tests::inheritance::V1_0::IParent follow.
Return<void> Parent::doParent()  {
    ALOGI("SERVER(Bar) Parent::doParent");
    return Void();
}


IParent* HIDL_FETCH_IParent(const char* /* name */) {
    return new Parent();
}

} // namespace implementation
}  // namespace V1_0
}  // namespace inheritance
}  // namespace tests
}  // namespace hardware
}  // namespace android
