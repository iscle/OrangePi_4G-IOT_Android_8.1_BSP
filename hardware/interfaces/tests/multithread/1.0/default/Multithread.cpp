#define LOG_TAG "hidl_test"

#include <android-base/logging.h>
#include "Multithread.h"
#include <inttypes.h>
#include <thread>

#include <hidl/HidlTransportSupport.h>

namespace android {
namespace hardware {
namespace tests {
namespace multithread {
namespace V1_0 {
namespace implementation {

// Methods from ::android::hardware::tests::multithread::V1_0::IMultithread follow.
Return<void> Multithread::setNumThreads(int32_t maxThreads, int32_t numThreads) {
    LOG(INFO) << "SERVER(Multithread) setNumThreads("
              << maxThreads << ", " << numThreads << ")";

    LOG(INFO) << "SERVER(Multithread) call configureRpcThreadpool("
              << maxThreads << ")";
    ::android::hardware::configureRpcThreadpool(maxThreads, /*willjoin*/ false);

    mNumThreads = numThreads;
    mNoTimeout = true;

    return Void();
}

Return<bool> Multithread::runNewThread() {
    LOG(INFO) << "SERVER(Multithread) runNewThread()";

    std::unique_lock<std::mutex> lk(mCvMutex);
    --mNumThreads;

    LOG(INFO) << "SERVER(Multithread) runNewThread()";
    LOG(INFO) << mNumThreads << "threads left";

    mCv.notify_all();
    bool noTimeout = mCv.wait_for(lk, kTimeoutDuration,
        [&] { return mNumThreads <= 0 || !mNoTimeout; });

    if (!noTimeout) {
        mNoTimeout = false;
        mCv.notify_all();
    }
    return mNoTimeout;
}

IMultithread* HIDL_FETCH_IMultithread(const char* /* name */) {
    return new Multithread();
}

decltype(Multithread::kTimeoutDuration) Multithread::kTimeoutDuration;

}  // namespace implementation
}  // namespace V1_0
}  // namespace multithread
}  // namespace tests
}  // namespace hardware
}  // namespace android
