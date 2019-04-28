#ifndef ANDROID_HARDWARE_TESTS_INHERITANCE_V1_0_PARENT_H
#define ANDROID_HARDWARE_TESTS_INHERITANCE_V1_0_PARENT_H

#include <android/hardware/tests/inheritance/1.0/IParent.h>
#include <hidl/Status.h>

#include <hidl/MQDescriptor.h>
namespace android {
namespace hardware {
namespace tests {
namespace inheritance {
namespace V1_0 {
namespace implementation {

using ::android::hardware::tests::inheritance::V1_0::IParent;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

struct Parent : public IParent {
    // Methods from ::android::hardware::tests::inheritance::V1_0::IGrandparent follow.
    Return<void> doGrandparent()  override;

    // Methods from ::android::hardware::tests::inheritance::V1_0::IParent follow.
    Return<void> doParent()  override;

};

extern "C" IParent* HIDL_FETCH_IParent(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace inheritance
}  // namespace tests
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_TESTS_INHERITANCE_V1_0_PARENT_H
