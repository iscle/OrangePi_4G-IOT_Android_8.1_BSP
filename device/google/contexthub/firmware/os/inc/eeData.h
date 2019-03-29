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

#ifndef _EEDATA_H_
#define _EEDATA_H_

#include <stdbool.h>
#include <stdint.h>

/*
 * EEDATA allows storage of data in a persistent area that survives reboots and OS updates.
 * Each data piece has a name and stores 0..EE_DATA_LEN_MAX bytes of data. The name is a
 * non-zero number in the range 0..EE_DATA_NAME_MAX - 1. All names below EE_DATA_FIRST_USER
 * are reserved for OS purposes and are not accessible using the external-app-visible API.
 * To store an EEDATA item, use eeDataSet(). Setting the buffer to NULL will delete an
 * existing item and not replace it. Otherwise an item is replaced. Return value is success
 * (deleting a non-existent item always suceeds). You can use eeDataGet() to get items. It
 * will return true if an item exists. If called with buf set to NULL and szP not NULL,
 * *szP will be filled with its size. Else if buf and szP are both not NULL, up to *szP
 * bytes will be stored into buf, and *szP will be updated with the number of bytes written.
 * True is returned if the data item exists at all, else false is. For encryption keys, we
 * [ab]use eeDataGetAllVersions to get all keys (as each has the same name).
 */



#define EE_DATA_NAME_MAX   0x000FFFFF
#define EE_DATA_LEN_MAX    0x00000FFF //in bytes
#define EE_DATA_FIRST_USER 0x00000100


bool eeDataGet(uint32_t name, void *buf, uint32_t *szP);
bool eeDataSet(uint32_t name, const void *buf, uint32_t len);

//allow getting old "versions". Set state to NULL initially, call till you get NULL as return value
void *eeDataGetAllVersions(uint32_t name, void *buf, uint32_t *szP, void **stateP);
bool eeDataEraseOldVersion(uint32_t name, void *addr); // addr is non-NULL address returned by call to eeDataGetAllVersions

//predefined key types

#define EE_DATA_NAME_ENCR_KEY            1



#endif

