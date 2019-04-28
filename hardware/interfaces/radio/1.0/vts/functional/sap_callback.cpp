/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <sap_hidl_hal_utils.h>

SapCallback::SapCallback(SapHidlTest& parent) : parent(parent) {}

Return<void> SapCallback::connectResponse(int32_t token, SapConnectRsp /*sapConnectRsp*/,
                                          int32_t /*maxMsgSize*/) {
    sapResponseToken = token;
    parent.notify();
    return Void();
}

Return<void> SapCallback::disconnectResponse(int32_t token) {
    sapResponseToken = token;
    parent.notify();
    return Void();
}

Return<void> SapCallback::disconnectIndication(int32_t /*token*/,
                                               SapDisconnectType /*disconnectType*/) {
    return Void();
}

Return<void> SapCallback::apduResponse(int32_t token, SapResultCode resultCode,
                                       const ::android::hardware::hidl_vec<uint8_t>& /*apduRsp*/) {
    sapResponseToken = token;
    sapResultCode = resultCode;
    parent.notify();
    return Void();
}

Return<void> SapCallback::transferAtrResponse(
    int32_t token, SapResultCode resultCode,
    const ::android::hardware::hidl_vec<uint8_t>& /*atr*/) {
    sapResponseToken = token;
    sapResultCode = resultCode;
    parent.notify();
    return Void();
}

Return<void> SapCallback::powerResponse(int32_t token, SapResultCode resultCode) {
    sapResponseToken = token;
    sapResultCode = resultCode;
    parent.notify();
    return Void();
}

Return<void> SapCallback::resetSimResponse(int32_t token, SapResultCode resultCode) {
    sapResponseToken = token;
    sapResultCode = resultCode;
    parent.notify();
    return Void();
}

Return<void> SapCallback::statusIndication(int32_t /*token*/, SapStatus /*status*/) {
    return Void();
}

Return<void> SapCallback::transferCardReaderStatusResponse(int32_t token, SapResultCode resultCode,
                                                           int32_t /*cardReaderStatus*/) {
    sapResponseToken = token;
    sapResultCode = resultCode;
    parent.notify();
    return Void();
}

Return<void> SapCallback::errorResponse(int32_t /*token*/) {
    return Void();
}

Return<void> SapCallback::transferProtocolResponse(int32_t token, SapResultCode resultCode) {
    sapResponseToken = token;
    sapResultCode = resultCode;
    parent.notify();
    return Void();
}
