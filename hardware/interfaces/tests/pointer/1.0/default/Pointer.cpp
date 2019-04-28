#define LOG_TAG "hidl_test"

#include "Pointer.h"

#include <log/log.h>

namespace android {
namespace hardware {
namespace tests {
namespace pointer {
namespace V1_0 {
namespace implementation {

Return<int32_t> Pointer::getErrors() {
    if(!errors.empty()) {
        for(const auto& e : errors)
            ALOGW("SERVER(Pointer) error: %s", e.c_str());
    }
    return errors.size();
}

IPointer* HIDL_FETCH_IPointer(const char* /* name */) {
    return new Pointer();
}

} // namespace implementation
}  // namespace V1_0
}  // namespace pointer
}  // namespace tests
}  // namespace hardware
}  // namespace android
