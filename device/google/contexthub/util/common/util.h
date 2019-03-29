#ifndef COMMON_UTIL_H_
#define COMMON_UTIL_H_

#include <chrono>

namespace android {

typedef std::chrono::time_point<std::chrono::steady_clock> SteadyClock;

}  // namespace android

#endif  // COMMON_UTIL_H_
