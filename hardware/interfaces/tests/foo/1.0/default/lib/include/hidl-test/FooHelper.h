#ifndef ANDROID_HIDL_TEST_FOO_HELPER_H
#define ANDROID_HIDL_TEST_FOO_HELPER_H
#include <string>
#include <android/hardware/tests/foo/1.0/IFoo.h>
#include <utils/Timers.h>

namespace android {

using std::to_string;
using hardware::hidl_string;
using hardware::hidl_vec;
using hardware::hidl_array;
using hardware::tests::foo::V1_0::IFoo;

static constexpr nsecs_t DELAY_S = 1;
static constexpr nsecs_t DELAY_NS = seconds_to_nanoseconds(DELAY_S);
static constexpr nsecs_t TOLERANCE_NS = milliseconds_to_nanoseconds(10);
static constexpr nsecs_t ONEWAY_TOLERANCE_NS = milliseconds_to_nanoseconds(1);

std::string to_string(const IFoo::StringMatrix5x3 &M);
std::string to_string(const IFoo::StringMatrix3x5 &M);
// Add quotes around s. For testing purposes only.
std::string to_string(const hidl_string &s);

template<typename T>
std::string to_string(const T *elems, size_t n) {
    std::string out;
    out = "[";
    for (size_t i = 0; i < n; ++i) {
        if (i > 0) {
            out += ", ";
        }
        out += to_string(elems[i]);
    }
    out += "]";

    return out;
}

template<typename T, size_t SIZE>
std::string to_string(const hidl_array<T, SIZE> &array) {
    return to_string(&array[0], SIZE);
}

template<typename T, size_t SIZE1, size_t SIZE2>
std::string to_string(const hidl_array<T, SIZE1, SIZE2> &array) {
    std::string out;
    out = "[";
    for (size_t i = 0; i < SIZE1; ++i) {
        if (i > 0) {
            out += ", ";
        }

        out += "[";
        for (size_t j = 0; j < SIZE2; ++j) {
            if (j > 0) {
                out += ", ";
            }

            out += to_string(array[i][j]);
        }
        out += "]";
    }
    out += "]";

    return out;
}

template<typename T>
std::string to_string(const hidl_vec<T> &vec) {
    return to_string(&vec[0], vec.size());
}

std::string QuuxToString(const IFoo::Quux &val);

std::string MultiDimensionalToString(const IFoo::MultiDimensional &val);

} // namespace android
#endif // ANDROID_HIDL_TEST_TEST_HELPER_H
