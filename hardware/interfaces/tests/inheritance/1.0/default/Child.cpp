#define LOG_TAG "hidl_test"

#include <log/log.h>

#include "Child.h"

namespace android {
namespace hardware {
namespace tests {
namespace inheritance {
namespace V1_0 {
namespace implementation {

// Methods from ::android::hardware::tests::inheritance::V1_0::IGrandparent follow.
Return<void> Child::doGrandparent()  {
    ALOGI("SERVER(Bar) Child::doGrandparent");
    return Void();
}

// Methods from ::android::hardware::tests::inheritance::V1_0::IParent follow.
Return<void> Child::doParent()  {
    ALOGI("SERVER(Bar) Child::doParent");
    return Void();
}


// Methods from ::android::hardware::tests::inheritance::V1_0::IChild follow.
Return<void> Child::doChild()  {
    ALOGI("SERVER(Bar) Child::doChild");
    return Void();
}


IChild* HIDL_FETCH_IChild(const char* /* name */) {
    return new Child();
}

} // namespace implementation
}  // namespace V1_0
}  // namespace inheritance
}  // namespace tests
}  // namespace hardware
}  // namespace android
