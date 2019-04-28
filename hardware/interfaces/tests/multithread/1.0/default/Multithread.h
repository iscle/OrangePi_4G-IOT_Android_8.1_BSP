#ifndef ANDROID_HARDWARE_TESTS_MULTITHREAD_V1_0_MULTITHREAD_H
#define ANDROID_HARDWARE_TESTS_MULTITHREAD_V1_0_MULTITHREAD_H

#include <android/hardware/tests/multithread/1.0/IMultithread.h>
#include <hidl/Status.h>

#include <chrono>
#include <condition_variable>
#include <mutex>

namespace android {
namespace hardware {
namespace tests {
namespace multithread {
namespace V1_0 {
namespace implementation {

using ::android::hardware::tests::multithread::V1_0::IMultithread;
using ::android::hardware::Return;
using ::android::hardware::Void;

using namespace std::chrono_literals;

struct Multithread : public IMultithread {
    // Methods from ::android::hardware::tests::multithread::V1_0::IMultithread follow.
    virtual Return<void> setNumThreads(int32_t maxThreads, int32_t numThreads) override;
    virtual Return<bool> runNewThread() override;

   private:
    int32_t mNumThreads;
    bool mNoTimeout;

    std::condition_variable mCv;
    std::mutex mCvMutex;

    static constexpr auto kTimeoutDuration = 100ms;
};

extern "C" IMultithread* HIDL_FETCH_IMultithread(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace multithread
}  // namespace tests
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_TESTS_MULTITHREAD_V1_0_MULTITHREAD_H
