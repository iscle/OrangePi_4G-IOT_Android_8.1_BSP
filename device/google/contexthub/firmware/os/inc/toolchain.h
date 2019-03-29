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

#ifndef TOOLCHAIN_H
#define TOOLCHAIN_H

#ifdef __cplusplus
extern "C" {
#endif

#if defined (__ICCARM__)
#include <stdint.h>
#include "intrinsics.h"

/* IAR DLib errno.h does not include all the necessary error codes by default. */
#define EIO         5
#define ENXIO       6
#define ENOMEM      12
#define EBUSY       16
#define ENODEV      19
#define EINVAL      22
#define EOPNOTSUPP  95

/* IAR does not support LIKELY() and UNLIKELY() for optimization purposes. */
#define LIKELY(x)       (x)
#define UNLIKELY(x)     (x)
#define CLZ             __CLZ

#define KEEP_SYMBOL  __root static void
#define NO_RETURN __noreturn void
#define VOID_WEAK __weak void
#define WEAK_ALIAS(X,Y) _Pragma(PRAGMA_HELPER(X,Y,weak)) void X(void)
#define PLACE_IN(loc, type, name) _Pragma(PRAGMA_HELPER(location,loc,)) type name
#define PLACE_IN_NAKED(loc, type, name) _Pragma(PRAGMA_HELPER(location,loc,)) __task type name
#define APP_ENTRY   _Pragma(PRAGMA_HELPER(location,".internal_app_init",)) __root static const struct AppHdr
#define APP_ENTRY2    _Pragma(PRAGMA_HELPER(location,".app_init",)) __root static struct AppFuncs

#define STACKLESS      __stackless

#ifndef PRINTF_ATTRIBUTE
#define PRINTF_ATTRIBUTE(string_index, first_to_check)
#endif

#define SET_PACKED_STRUCT_MODE_ON       _Pragma("pack(push, 1)")
#define SET_PACKED_STRUCT_MODE_OFF      _Pragma("pack(pop)")

#ifndef ATTRIBUTE_PACKED
#define ATTRIBUTE_PACKED
#endif

#define UNROLLED

#define SET_INTERNAL_LOCATION(x, y)           _Pragma((x, y)) __root

#define SET_INTERNAL_LOCATION_ATTRIBUTES(x, y)

#define SET_EXTERNAL_APP_ATTRIBUTES(x, y, z)

#define SET_EXTERNAL_APP_VERSION(x, y, z)

#define DECLARE_OS_ALIGNMENT(nam, size, extra_keyword, struct_type)    _Pragma("data_alignment=4") extra_keyword uint8_t _##nam##_store [size]; extra_keyword #struct_type *nam = (#struct_type *)_##nam##_store

#elif defined(__GNUC__)

#define STRINGIFY(s) _STRINGIFY(s)
#define _STRINGIFY(s) #s

#define NO_RETURN void __attribute__((noreturn))
#define LIKELY(x)   (__builtin_expect(x, 1))
#define UNLIKELY(x) (__builtin_expect(x, 0))

#define CLZ             __builtin_clz
#define APP_ENTRY   static struct AppEntry __attribute__((used,section (".app_init")))
#define KEEP_SYMBOL  static void __attribute__((used))
#define PLACE_IN(loc, type, name) type __attribute__ ((section(loc))) name
#define PLACE_IN_NAKED(loc, type, name) type __attribute__ ((naked, section(loc))) name
#define VOID_WEAK void __attribute__ ((weak))
#define WEAK_ALIAS(X,Y) void X(void) __attribute__ ((weak, alias (STRINGIFY(Y))))

#define STACKLESS __attribute__((naked))

#ifndef PRINTF_ATTRIBUTE
#define PRINTF_ATTRIBUTE(string_index, first_to_check) \
    __attribute__((format(printf, string_index, first_to_check)))
#endif

#define SET_PACKED_STRUCT_MODE_ON
#define SET_PACKED_STRUCT_MODE_OFF

#ifndef ATTRIBUTE_PACKED
#define ATTRIBUTE_PACKED __attribute__((packed))
#endif

#define UNROLLED   __attribute__((optimize("unroll-loops")))

#define SET_INTERNAL_LOCATION(x, y)

#define SET_INTERNAL_LOCATION_ATTRIBUTES(x, y) __attribute__((x,y))

#define SET_EXTERNAL_APP_ATTRIBUTES(x, y, z) __attribute__((x, y, z))

#define SET_EXTERNAL_APP_VERSION(x, y, z) __attribute__((x, y, z))

#define DECLARE_OS_ALIGNMENT(nam, size, extra_keyword, struct_type) extra_keyword uint8_t _##nam##_store [size] __attribute__((aligned(4))); extra_keyword struct_type *nam = (struct_type *)_##nam##_store

#endif

#ifdef __cplusplus
}
#endif

#endif
