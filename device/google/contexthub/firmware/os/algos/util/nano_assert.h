#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_UTIL_NANO_ASSERT_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_UTIL_NANO_ASSERT_H_

#if defined(__NANOHUB__) || defined(_OS_BUILD_)

// For external nanoapps (__NANOHUB__ defined), use SRC_FILENAME provided
// by the build system, which has the directory stripped. But allow the
// full filename for OS builds, where ASSERT should only be used for local
// development.
#if !defined(SRC_FILENAME) && defined(_OS_BUILD_)
#define SRC_FILENAME __FILENAME__
#endif

#ifdef NANOHUB_NON_CHRE_API
  #include <syscallDo.h>
  #define ASSERT_LOG_FUNC(fmt, ...) eOsLog(LOG_ERROR, fmt, ##__VA_ARGS__)
#else
  #include <chre.h>
  #define ASSERT_LOG_FUNC(fmt, ...) chreLog(LOG_ERROR, fmt, ##__VA_ARGS__)
#endif

// Note that this just logs and doesn't actually stop execution on Nanohub
#define ASSERT_IMPL(x) do {                                          \
    if (!(x)) {                                                      \
      ASSERT_LOG_FUNC("Assertion: %s:%d\n", SRC_FILENAME, __LINE__); \
    }                                                                \
  } while (0)

#else  // Off-target testing, e.g. Google3

#define NANO_ASSERT_ENABLED
#include <assert.h>
#define ASSERT_IMPL(x) assert(x)

#endif

#ifndef ASSERT
#ifdef NANO_ASSERT_ENABLED
#define ASSERT(x) ASSERT_IMPL(x)
#else
#define ASSERT(x) ((void)(x))
#endif  // NANO_ASSERT_ENABLED
#endif  // ASSERT

// Use NULL when compiling for C and nullptr for C++.
#ifdef __cplusplus
#define ASSERT_NOT_NULL(ptr) ASSERT((ptr) != nullptr)
#else
#define ASSERT_NOT_NULL(ptr) ASSERT((ptr) != NULL)
#endif

#define UNUSED(x) ((void)(sizeof(x)))

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_UTIL_NANO_ASSERT_H_
