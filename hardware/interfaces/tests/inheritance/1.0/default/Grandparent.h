#ifndef ANDROID_HARDWARE_TESTS_INHERITANCE_V1_0_GRANDPARENT_H
#define ANDROID_HARDWARE_TESTS_INHERITANCE_V1_0_GRANDPARENT_H

#include <android/hardware/tests/inheritance/1.0/IGrandparent.h>
#include <hidl/Status.h>

#include <hidl/MQDescriptor.h>
namespace android {
namespace hardware {
namespace tests {
namespace inheritance {
namespace V1_0 {
namespace implementation {

using ::android::hardware::tests::inheritance::V1_0::IGrandparent;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

struct Grandparent : public IGrandparent {
    // Methods from ::android::hardware::tests::inheritance::V1_0::IGrandparent follow.
    Return<void> doGrandparent()  override;

};

extern "C" IGrandparent* HIDL_FETCH_IGrandparent(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace inheritance
}  // namespace tests
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_TESTS_INHERITANCE_V1_0_GRANDPARENT_H
