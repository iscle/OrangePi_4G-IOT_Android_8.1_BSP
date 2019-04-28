#ifndef ANDROID_HARDWARE_TESTS_INHERITANCE_V1_0_FETCHER_H
#define ANDROID_HARDWARE_TESTS_INHERITANCE_V1_0_FETCHER_H

#include "Child.h"
#include <android/hardware/tests/inheritance/1.0/IFetcher.h>
#include <hidl/Status.h>

#include <hidl/MQDescriptor.h>
namespace android {
namespace hardware {
namespace tests {
namespace inheritance {
namespace V1_0 {
namespace implementation {

using ::android::hardware::tests::inheritance::V1_0::IFetcher;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

struct Fetcher : public IFetcher {

    Fetcher();

    // Methods from ::android::hardware::tests::inheritance::V1_0::IFetcher follow.
    Return<sp<IGrandparent>> getGrandparent(bool sendRemote)  override;
    Return<sp<IParent>> getParent(bool sendRemote)  override;
    Return<sp<IChild>> getChild(bool sendRemote)  override;

private:
    sp<IChild> mPrecious;
};

extern "C" IFetcher* HIDL_FETCH_IFetcher(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace inheritance
}  // namespace tests
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_TESTS_INHERITANCE_V1_0_FETCHER_H
