#include "ScheduleTest.h"
#include <pthread.h>
#include <iomanip>
#include <iostream>

using namespace std;

#define ASSERT(cond)                                                      \
    do {                                                                  \
        if (!(cond)) {                                                    \
            cerr << __func__ << ":" << __LINE__ << " condition:" << #cond \
                 << " failed\n"                                           \
                 << endl;                                                 \
            exit(EXIT_FAILURE);                                           \
        }                                                                 \
    } while (0)

static int threadPri() {
    struct sched_param param;
    int policy;
    ASSERT(!pthread_getschedparam(pthread_self(), &policy, &param));
    return param.sched_priority;
}

static void threadDump(const char* prefix, int verbose) {
    struct sched_param param;
    int policy;
    if (!verbose) return;
    cout << "--------------------------------------------------" << endl;
    cout << setw(12) << left << prefix << " pid: " << getpid()
         << " tid: " << gettid() << " cpu: " << sched_getcpu() << endl;
    ASSERT(!pthread_getschedparam(pthread_self(), &policy, &param));
    string s = (policy == SCHED_OTHER)
                   ? "SCHED_OTHER"
                   : (policy == SCHED_FIFO)
                         ? "SCHED_FIFO"
                         : (policy == SCHED_RR) ? "SCHED_RR" : "???";
    cout << setw(12) << left << s << param.sched_priority << endl;
    return;
}

namespace android {
namespace hardware {
namespace tests {
namespace libhwbinder {
namespace V1_0 {
namespace implementation {

// Methods from ::android::hardware::tests::libhwbinder::V1_0::IScheduleTest
// follow.
Return<uint32_t> ScheduleTest::send(uint32_t cfg, uint32_t callerSta) {
    // TODO implement
    int priority = threadPri();
    int priority_caller = (callerSta >> 16) & 0xffff;
    int verbose = cfg & 1;
    threadDump("hwbinder", verbose);
    uint32_t h = 0, s = 0;
    if (priority_caller != priority) {
        h++;
        if (verbose) {
            cout << "err priority_caller:" << priority_caller
                 << ", priority:" << priority << endl;
        }
    }
    int cpu = sched_getcpu();
    int cpu_caller = (callerSta)&0xffff;
    if (cpu != cpu_caller) {
        s++;
    }
    return (h << 16) | (s & 0xffff);
}

// Methods from ::android::hidl::base::V1_0::IBase follow.

IScheduleTest* HIDL_FETCH_IScheduleTest(const char* /* name */) {
    return new ScheduleTest();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace libhwbinder
}  // namespace tests
}  // namespace hardware
}  // namespace android
