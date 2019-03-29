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

#ifndef _APP_SEC_H_
#define _APP_SEC_H_

#include <stdbool.h>
#include <stdint.h>

//types
struct AppSecState;
typedef uint32_t AppSecErr;

//callbacks
typedef AppSecErr (*AppSecWriteCbk)(const void *data, uint32_t len);
typedef AppSecErr (*AppSecPubKeyFindCbk)(const uint32_t *gotKey, bool *foundP); // fill in *foundP on result of lookup
typedef AppSecErr (*AppSecGetAesKeyCbk)(uint64_t keyIdx, void *keyBuf); // return APP_SEC_KEY_NOT_FOUND or APP_SEC_NO_ERROR

//return values
#define APP_SEC_NO_ERROR              0 //all went ok
#define APP_SEC_NEED_MORE_TIME        1 //please call appSecDoSomeProcessing().
#define APP_SEC_KEY_NOT_FOUND         2 //we did not find the encr key
#define APP_SEC_HEADER_ERROR          3 //data (decrypted or input) has no recognizable header
#define APP_SEC_TOO_MUCH_DATA         4 //we got more data than expected
#define APP_SEC_TOO_LITTLE_DATA       5 //we got less data than expected
#define APP_SEC_SIG_VERIFY_FAIL       6 //some signature verification failed
#define APP_SEC_SIG_DECODE_FAIL       7 //some signature verification failed
#define APP_SEC_SIG_ROOT_UNKNOWN      8 //signatures all verified but the referenced root of trust is unknown
#define APP_SEC_MEMORY_ERROR          9 //we ran out of memory while doing things
#define APP_SEC_INVALID_DATA         10 //data is invalid in some way not described by other error messages
#define APP_SEC_VERIFY_FAILED        11 //decrypted data verification failed
#define APP_SEC_BAD                 127 //something irrecoverably bad happened and we gave up. Sorry...

//init/deinit
struct AppSecState *appSecInit(AppSecWriteCbk writeCbk, AppSecPubKeyFindCbk pubKeyFindCbk, AppSecGetAesKeyCbk aesKeyAccessCbk, bool mandateSigning);
void appSecDeinit (struct AppSecState *state);

//actually doing things
AppSecErr appSecRxData(struct AppSecState *state, const void *data, uint32_t len, uint32_t *lenUnusedP);
AppSecErr appSecDoSomeProcessing(struct AppSecState *state); //caleed when any appSec function returns APP_SEC_NEED_MORE_TIME
AppSecErr appSecRxDataOver(struct AppSecState *state); //caleed when there is no more data

#endif
