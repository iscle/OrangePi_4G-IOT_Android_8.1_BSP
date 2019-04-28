
#define LOG_TAG "hidl_test"

#include "Fetcher.h"
#include <android-base/logging.h>
#include <inttypes.h>

namespace android {
namespace hardware {
namespace tests {
namespace inheritance {
namespace V1_0 {
namespace implementation {

Fetcher::Fetcher() {
    mPrecious = IChild::getService("local child", true);
    CHECK(!mPrecious->isRemote());
}

sp<IChild> selectService(bool sendRemote, sp<IChild> &local) {
    sp<IChild> toSend;
    if (sendRemote) {
        toSend = IChild::getService("child");
        if (!toSend->isRemote()) {
            toSend = nullptr;
        }
    } else {
        toSend = local;
    }
    LOG(INFO) << "SERVER(Fetcher) selectService returning " << toSend.get();
    return toSend;
}

// Methods from ::android::hardware::tests::inheritance::V1_0::IFetcher follow.
Return<sp<IGrandparent>> Fetcher::getGrandparent(bool sendRemote)  {
    return selectService(sendRemote, mPrecious);
}

Return<sp<IParent>> Fetcher::getParent(bool sendRemote)  {
    return selectService(sendRemote, mPrecious);
}

Return<sp<IChild>> Fetcher::getChild(bool sendRemote)  {
    return selectService(sendRemote, mPrecious);
}

IFetcher* HIDL_FETCH_IFetcher(const char* /* name */) {
    return new Fetcher();
}

} // namespace implementation
}  // namespace V1_0
}  // namespace inheritance
}  // namespace tests
}  // namespace hardware
}  // namespace android
