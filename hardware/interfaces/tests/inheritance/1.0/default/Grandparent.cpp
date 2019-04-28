#define LOG_TAG "hidl_test"
#include <android-base/logging.h>

#include "Grandparent.h"

namespace android {
namespace hardware {
namespace tests {
namespace inheritance {
namespace V1_0 {
namespace implementation {

// Methods from ::android::hardware::tests::inheritance::V1_0::IGrandparent follow.
Return<void> Grandparent::doGrandparent()  {
    ALOGI("SERVER(Bar) Grandparent::doGrandparent");
    return Void();
}


IGrandparent* HIDL_FETCH_IGrandparent(const char* /* name */) {
    return new Grandparent();
}

} // namespace implementation
}  // namespace V1_0
}  // namespace inheritance
}  // namespace tests
}  // namespace hardware
}  // namespace android
