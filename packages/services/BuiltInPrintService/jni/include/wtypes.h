/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
 * Copyright (C) 2013 Hewlett-Packard Development Company, L.P.
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
#ifndef __WTYPES_H__
#define __WTYPES_H__

#include <stddef.h>
#include <stdbool.h>

/*
 * A return type for functions.
 */
typedef enum {
    /* Request succeeded */
    OK = 0,

    /* Request failed due to an unspecified error */
    ERROR = -1,

    /* Request failed because it was cancelled */
    CANCELLED = -2,

    /* Request failed because corrupt data was detected */
    CORRUPT = -3,
} status_t;

#define ARRAY_SIZE(X) (sizeof(X)/sizeof(X[0]))

#ifndef MIN
#define MIN(x, y) (((x) < (y)) ? (x) : (y))
#endif

#ifndef MAX
#define MAX(x, y) (((x) > (y)) ? (x) : (y))
#endif

/*
 * Return a the pointer of an enclosing structure from the address of one of its members, where
 * "class" is the enclosing structure type, "member" is the name of structure member, and
 * "pointer" is that member's pointer.
 */
#define IMPL(class, member, pointer) ( (class *)( ((uint32)pointer) - offsetof(class, member) ) )

typedef unsigned char ubyte;
typedef unsigned char uint8;
typedef unsigned short uint16;
typedef signed short sint16;
typedef signed long sint32;
typedef unsigned long uint32;
typedef unsigned long long uint64;

/** A job handle */
typedef unsigned long wJob_t;

#endif // __WTYPES_H__