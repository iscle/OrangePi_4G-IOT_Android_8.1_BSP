/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef C2_H_
#define C2_H_

#include <string>
#include <vector>
#include <list>

#ifdef __ANDROID__

#include <utils/Errors.h>       // for status_t
#include <utils/Timers.h>       // for nsecs_t

namespace android {

#else

#include <errno.h>
typedef int64_t nsecs_t;

enum {
    GRALLOC_USAGE_SW_READ_OFTEN,
    GRALLOC_USAGE_RENDERSCRIPT,
    GRALLOC_USAGE_HW_TEXTURE,
    GRALLOC_USAGE_HW_COMPOSER,
    GRALLOC_USAGE_HW_VIDEO_ENCODER,
    GRALLOC_USAGE_PROTECTED,
    GRALLOC_USAGE_SW_WRITE_OFTEN,
    GRALLOC_USAGE_HW_RENDER,
};

#endif

/** \mainpage Codec2
 *
 * Codec2 is a frame-based data processing API used by android.
 *
 * The framework accesses components via the \ref API.
 */

/** \ingroup API
 *
 * The Codec2 API defines the operation of data processing components and their interaction with
 * the rest of the system.
 *
 * Coding Conventions
 *
 * Mitigating Binary Compatibility.
 *
 * While full binary compatibility is not a goal of the API (due to our use of STL), we try to
 * mitigate binary breaks by adhering to the following conventions:
 *
 * - at most one vtable with placeholder virtual methods
 * - all optional/placeholder virtual methods returning a status_t, with C2_NOT_IMPLEMENTED not
 *   requiring any update to input/output arguments.
 * - limiting symbol export of inline methods
 * - use of pimpl (or shared-pimpl)
 *
 * Naming
 *
 * - all classes and types prefix with C2
 * - classes for internal use prefix with _C2
 * - enum values in global namespace prefix with C2_ all caps
 * - enum values inside classes have no C2_ prefix as class already has it
 * - supporting two kinds of enum naming: all-caps and kCamelCase
 * \todo revisit kCamelCase for param-type
 *
 * Aspects
 *
 * Aspects define certain common behavior across a group of objects.
 * - classes whose name matches _C2.*Aspect
 * - only protected constructors
 * - no desctructor and copiable
 * - all methods are inline or static (this is opposite of the interface paradigm where all methods
 *   are virtual, which would not work due to the at most one vtable rule.)
 * - only private variables (this prevents subclasses interfering with the aspects.)
 */

/// \defgroup types Common Types
/// @{

/**
 * C2String: basic string implementation
 */
typedef std::string C2String;
typedef const char *C2StringLiteral;

/**
 * C2Error: status codes used.
 */
typedef int32_t C2Error;
enum {
#ifndef __ANDROID__
    OK                  = 0,
    BAD_VALUE           = -EINVAL,
    BAD_INDEX           = -EOVERFLOW,
    UNKNOWN_TRANSACTION = -EBADMSG,
    ALREADY_EXISTS      = -EEXIST,
    NAME_NOT_FOUND      = -ENOENT,
    INVALID_OPERATION   = -ENOSYS,
    NO_MEMORY           = -ENOMEM,
    PERMISSION_DENIED   = -EPERM,
    TIMED_OUT           = -ETIMEDOUT,
    UNKNOWN_ERROR       = -EINVAL,
#endif

    C2_OK               = OK,                   ///< operation completed successfully

    // bad input
    C2_BAD_VALUE        = BAD_VALUE,            ///< argument has invalid value (user error)
    C2_BAD_INDEX        = BAD_INDEX,            ///< argument uses invalid index (user error)
    C2_UNSUPPORTED      = UNKNOWN_TRANSACTION,  ///< argument/index is value but not supported \todo is this really BAD_INDEX/VALUE?

    // bad sequencing of events
    C2_DUPLICATE        = ALREADY_EXISTS,       ///< object already exists
    C2_NOT_FOUND        = NAME_NOT_FOUND,       ///< object not found
    C2_BAD_STATE        = INVALID_OPERATION,    ///< operation is not permitted in the current state

    // bad environment
    C2_NO_MEMORY        = NO_MEMORY,            ///< not enough memory to complete operation
    C2_NO_PERMISSION    = PERMISSION_DENIED,    ///< missing permission to complete operation
    C2_TIMED_OUT        = TIMED_OUT,            ///< operation did not complete within timeout

    // bad versioning
    C2_NOT_IMPLEMENTED  = UNKNOWN_TRANSACTION,  ///< operation is not implemented (optional only) \todo for now reuse error code

    // unknown fatal
    C2_CORRUPTED        = UNKNOWN_ERROR,        ///< some unexpected error prevented the operation
};

/// @}

/// \defgroup utils Utilities
/// @{

#define C2_DO_NOT_COPY(type, args...) \
    type args& operator=(const type args&) = delete; \
    type(const type args&) = delete; \

#define C2_PURE __attribute__((pure))
#define C2_CONST __attribute__((const))
#define C2_HIDE __attribute__((visibility("hidden")))
#define C2_INTERNAL __attribute__((internal_linkage))

#define DEFINE_OTHER_COMPARISON_OPERATORS(type) \
    inline bool operator!=(const type &other) { return !(*this == other); } \
    inline bool operator<=(const type &other) { return (*this == other) || (*this < other); } \
    inline bool operator>=(const type &other) { return !(*this < other); } \
    inline bool operator>(const type &other) { return !(*this < other) && !(*this == other); }

#define DEFINE_FIELD_BASED_COMPARISON_OPERATORS(type, field) \
    inline bool operator<(const type &other) const { return field < other.field; } \
    inline bool operator==(const type &other) const { return field == other.field; } \
    DEFINE_OTHER_COMPARISON_OPERATORS(type)

/// \cond INTERNAL

/// \defgroup utils_internal
/// @{

template<typename... T> struct c2_types;

/** specialization for a single type */
template<typename T>
struct c2_types<T> {
    typedef typename std::decay<T>::type wide_type;
    typedef wide_type narrow_type;
    typedef wide_type mintype;
};

/** specialization for two types */
template<typename T, typename U>
struct c2_types<T, U> {
    static_assert(std::is_floating_point<T>::value == std::is_floating_point<U>::value,
                  "mixing floating point and non-floating point types is disallowed");
    static_assert(std::is_signed<T>::value == std::is_signed<U>::value,
                  "mixing signed and unsigned types is disallowed");

    typedef typename std::decay<
            decltype(true ? std::declval<T>() : std::declval<U>())>::type wide_type;
    typedef typename std::decay<
            typename std::conditional<sizeof(T) < sizeof(U), T, U>::type>::type narrow_type;
    typedef typename std::conditional<
            std::is_signed<T>::value, wide_type, narrow_type>::type mintype;
};

/// @}

/// \endcond

/**
 * Type support utility class. Only supports similar classes, such as:
 * - all floating point
 * - all unsigned/all signed
 * - all pointer
 */
template<typename T, typename U, typename... V>
struct c2_types<T, U, V...> {
    /** Common type that accommodates all template parameter types. */
    typedef typename c2_types<typename c2_types<T, U>::wide_type, V...>::wide_type wide_type;
    /** Narrowest type of the template parameter types. */
    typedef typename c2_types<typename c2_types<T, U>::narrow_type, V...>::narrow_type narrow_type;
    /** Type that accommodates the minimum value for any input for the template parameter types. */
    typedef typename c2_types<typename c2_types<T, U>::mintype, V...>::mintype mintype;
};

/**
 *  \ingroup utils_internal
 * specialization for two values */
template<typename T, typename U>
inline constexpr typename c2_types<T, U>::wide_type c2_max(const T a, const U b) {
    typedef typename c2_types<T, U>::wide_type wide_type;
    return ({ wide_type a_(a), b_(b); a_ > b_ ? a_ : b_; });
}

/**
 * Finds the maximum value of a list of "similarly typed" values.
 *
 * This is an extension to std::max where the types do not have to be identical, and the smallest
 * resulting type is used that accommodates the argument types.
 *
 * \note Value types must be similar, e.g. all floating point, all pointers, all signed, or all
 * unsigned.
 *
 * @return the largest of the input arguments.
 */
template<typename T, typename U, typename... V>
constexpr typename c2_types<T, U, V...>::wide_type c2_max(const T a, const U b, const V ... c) {
    typedef typename c2_types<T, U, V...>::wide_type wide_type;
    return ({ wide_type a_(a), b_(c2_max(b, c...)); a_ > b_ ? a_ : b_; });
}

/**
 *  \ingroup utils_internal
 * specialization for two values */
template<typename T, typename U>
inline constexpr typename c2_types<T, U>::mintype c2_min(const T a, const U b) {
    typedef typename c2_types<T, U>::wide_type wide_type;
    return ({
        wide_type a_(a), b_(b);
        static_cast<typename c2_types<T, U>::mintype>(a_ < b_ ? a_ : b_);
    });
}

/**
 * Finds the minimum value of a list of "similarly typed" values.
 *
 * This is an extension to std::min where the types do not have to be identical, and the smallest
 * resulting type is used that accommodates the argument types.
 *
 * \note Value types must be similar, e.g. all floating point, all pointers, all signed, or all
 * unsigned.
 *
 * @return the smallest of the input arguments.
 */
template<typename T, typename U, typename... V>
constexpr typename c2_types<T, U, V...>::mintype c2_min(const T a, const U b, const V ... c) {
    typedef typename c2_types<U, V...>::mintype rest_type;
    typedef typename c2_types<T, rest_type>::wide_type wide_type;
    return ({
        wide_type a_(a), b_(c2_min(b, c...));
        static_cast<typename c2_types<T, rest_type>::mintype>(a_ < b_ ? a_ : b_);
    });
}

/// @}

#ifdef __ANDROID__
} // namespace android
#endif

#endif  // C2_H_
