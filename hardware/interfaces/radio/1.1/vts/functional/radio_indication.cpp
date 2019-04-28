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

#include <radio_hidl_hal_utils_v1_1.h>

RadioIndication_v1_1::RadioIndication_v1_1(RadioHidlTest_v1_1& parent) : parent_v1_1(parent) {}

/* 1.1 Apis */
Return<void> RadioIndication_v1_1::carrierInfoForImsiEncryption(RadioIndicationType /*info*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::networkScanResult(RadioIndicationType /*type*/,
                                                     const NetworkScanResult& /*result*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::keepaliveStatus(RadioIndicationType /*type*/,
                                                   const KeepaliveStatus& /*status*/) {
    return Void();
}

/* 1.0 Apis */
Return<void> RadioIndication_v1_1::radioStateChanged(RadioIndicationType /*type*/,
                                                     RadioState /*radioState*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::callStateChanged(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::networkStateChanged(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::newSms(RadioIndicationType /*type*/,
                                          const ::android::hardware::hidl_vec<uint8_t>& /*pdu*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::newSmsStatusReport(
    RadioIndicationType /*type*/, const ::android::hardware::hidl_vec<uint8_t>& /*pdu*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::newSmsOnSim(RadioIndicationType /*type*/,
                                               int32_t /*recordNumber*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::onUssd(RadioIndicationType /*type*/, UssdModeType /*modeType*/,
                                          const ::android::hardware::hidl_string& /*msg*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::nitzTimeReceived(
    RadioIndicationType /*type*/, const ::android::hardware::hidl_string& /*nitzTime*/,
    uint64_t /*receivedTime*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::currentSignalStrength(RadioIndicationType /*type*/,
                                                         const SignalStrength& /*signalStrength*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::dataCallListChanged(
    RadioIndicationType /*type*/,
    const ::android::hardware::hidl_vec<SetupDataCallResult>& /*dcList*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::suppSvcNotify(RadioIndicationType /*type*/,
                                                 const SuppSvcNotification& /*suppSvc*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::stkSessionEnd(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::stkProactiveCommand(
    RadioIndicationType /*type*/, const ::android::hardware::hidl_string& /*cmd*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::stkEventNotify(RadioIndicationType /*type*/,
                                                  const ::android::hardware::hidl_string& /*cmd*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::stkCallSetup(RadioIndicationType /*type*/, int64_t /*timeout*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::simSmsStorageFull(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::simRefresh(RadioIndicationType /*type*/,
                                              const SimRefreshResult& /*refreshResult*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::callRing(RadioIndicationType /*type*/, bool /*isGsm*/,
                                            const CdmaSignalInfoRecord& /*record*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::simStatusChanged(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::cdmaNewSms(RadioIndicationType /*type*/,
                                              const CdmaSmsMessage& /*msg*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::newBroadcastSms(
    RadioIndicationType /*type*/, const ::android::hardware::hidl_vec<uint8_t>& /*data*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::cdmaRuimSmsStorageFull(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::restrictedStateChanged(RadioIndicationType /*type*/,
                                                          PhoneRestrictedState /*state*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::enterEmergencyCallbackMode(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::cdmaCallWaiting(RadioIndicationType /*type*/,
                                                   const CdmaCallWaiting& /*callWaitingRecord*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::cdmaOtaProvisionStatus(RadioIndicationType /*type*/,
                                                          CdmaOtaProvisionStatus /*status*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::cdmaInfoRec(RadioIndicationType /*type*/,
                                               const CdmaInformationRecords& /*records*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::indicateRingbackTone(RadioIndicationType /*type*/,
                                                        bool /*start*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::resendIncallMute(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::cdmaSubscriptionSourceChanged(
    RadioIndicationType /*type*/, CdmaSubscriptionSource /*cdmaSource*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::cdmaPrlChanged(RadioIndicationType /*type*/,
                                                  int32_t /*version*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::exitEmergencyCallbackMode(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::rilConnected(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::voiceRadioTechChanged(RadioIndicationType /*type*/,
                                                         RadioTechnology /*rat*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::cellInfoList(
    RadioIndicationType /*type*/, const ::android::hardware::hidl_vec<CellInfo>& /*records*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::imsNetworkStateChanged(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::subscriptionStatusChanged(RadioIndicationType /*type*/,
                                                             bool /*activate*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::srvccStateNotify(RadioIndicationType /*type*/,
                                                    SrvccState /*state*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::hardwareConfigChanged(
    RadioIndicationType /*type*/,
    const ::android::hardware::hidl_vec<HardwareConfig>& /*configs*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::radioCapabilityIndication(RadioIndicationType /*type*/,
                                                             const RadioCapability& /*rc*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::onSupplementaryServiceIndication(
    RadioIndicationType /*type*/, const StkCcUnsolSsResult& /*ss*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::stkCallControlAlphaNotify(
    RadioIndicationType /*type*/, const ::android::hardware::hidl_string& /*alpha*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::lceData(RadioIndicationType /*type*/,
                                           const LceDataInfo& /*lce*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::pcoData(RadioIndicationType /*type*/,
                                           const PcoDataInfo& /*pco*/) {
    return Void();
}

Return<void> RadioIndication_v1_1::modemReset(RadioIndicationType /*type*/,
                                              const ::android::hardware::hidl_string& /*reason*/) {
    return Void();
}