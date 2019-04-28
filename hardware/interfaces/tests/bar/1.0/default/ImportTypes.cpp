#include "ImportTypes.h"

namespace android {
namespace hardware {
namespace tests {
namespace bar {
namespace V1_0 {
namespace implementation {

// Methods from ::android::hardware::tests::bar::V1_0::IImportTypes follow.

IImportTypes* HIDL_FETCH_IImportTypes(const char* /* name */) {
    return new ImportTypes();
}

} // namespace implementation
}  // namespace V1_0
}  // namespace bar
}  // namespace tests
}  // namespace hardware
}  // namespace android
