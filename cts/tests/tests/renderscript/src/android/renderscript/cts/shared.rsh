#pragma version(1)
#pragma rs java_package_name(android.renderscript.cts)

static int64_t g_time;

static inline void start(void) {
    g_time = rsUptimeMillis();
}

static inline float end(void) {
    int64_t t = rsUptimeMillis() - g_time;
    return ((float)t) / 1000.f;
}

#define _RS_ASSERT(b) \
do { \
    if (!(b)) { \
        failed = true; \
        rsDebug(#b " FAILED", 0); \
    } \
\
} while (0)

#define _RS_ASSERT_EQU(e1, e2) \
  (((e1) != (e2)) ? (failed = true, rsDebug(#e1 " != " #e2, (e1), (e2)), false) : true)

/* These constants must match those in UnitTest.java */
static const int RS_MSG_TEST_PASSED = 100;
static const int RS_MSG_TEST_FAILED = 101;
