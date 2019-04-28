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

#include <radio_hidl_hal_utils_v1_0.h>

RadioIndication::RadioIndication(RadioHidlTest& parent) : parent(parent) {}

Return<void> RadioIndication::radioStateChanged(RadioIndicationType /*type*/,
                                                RadioState /*radioState*/) {
    return Void();
}

Return<void> RadioIndication::callStateChanged(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication::networkStateChanged(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication::newSms(RadioIndicationType /*type*/,
                                     const ::android::hardware::hidl_vec<uint8_t>& /*pdu*/) {
    return Void();
}

Return<void> RadioIndication::newSmsStatusReport(
    RadioIndicationType /*type*/, const ::android::hardware::hidl_vec<uint8_t>& /*pdu*/) {
    return Void();
}

Return<void> RadioIndication::newSmsOnSim(RadioIndicationType /*type*/, int32_t /*recordNumber*/) {
    return Void();
}

Return<void> RadioIndication::onUssd(RadioIndicationType /*type*/, UssdModeType /*modeType*/,
                                     const ::android::hardware::hidl_string& /*msg*/) {
    return Void();
}

Return<void> RadioIndication::nitzTimeReceived(RadioIndicationType /*type*/,
                                               const ::android::hardware::hidl_string& /*nitzTime*/,
                                               uint64_t /*receivedTime*/) {
    return Void();
}

Return<void> RadioIndication::currentSignalStrength(RadioIndicationType /*type*/,
                                                    const SignalStrength& /*signalStrength*/) {
    return Void();
}

Return<void> RadioIndication::dataCallListChanged(
    RadioIndicationType /*type*/,
    const ::android::hardware::hidl_vec<SetupDataCallResult>& /*dcList*/) {
    return Void();
}

Return<void> RadioIndication::suppSvcNotify(RadioIndicationType /*type*/,
                                            const SuppSvcNotification& /*suppSvc*/) {
    return Void();
}

Return<void> RadioIndication::stkSessionEnd(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication::stkProactiveCommand(RadioIndicationType /*type*/,
                                                  const ::android::hardware::hidl_string& /*cmd*/) {
    return Void();
}

Return<void> RadioIndication::stkEventNotify(RadioIndicationType /*type*/,
                                             const ::android::hardware::hidl_string& /*cmd*/) {
    return Void();
}

Return<void> RadioIndication::stkCallSetup(RadioIndicationType /*type*/, int64_t /*timeout*/) {
    return Void();
}

Return<void> RadioIndication::simSmsStorageFull(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication::simRefresh(RadioIndicationType /*type*/,
                                         const SimRefreshResult& /*refreshResult*/) {
    return Void();
}

Return<void> RadioIndication::callRing(RadioIndicationType /*type*/, bool /*isGsm*/,
                                       const CdmaSignalInfoRecord& /*record*/) {
    return Void();
}

Return<void> RadioIndication::simStatusChanged(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication::cdmaNewSms(RadioIndicationType /*type*/,
                                         const CdmaSmsMessage& /*msg*/) {
    return Void();
}

Return<void> RadioIndication::newBroadcastSms(
    RadioIndicationType /*type*/, const ::android::hardware::hidl_vec<uint8_t>& /*data*/) {
    return Void();
}

Return<void> RadioIndication::cdmaRuimSmsStorageFull(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication::restrictedStateChanged(RadioIndicationType /*type*/,
                                                     PhoneRestrictedState /*state*/) {
    return Void();
}

Return<void> RadioIndication::enterEmergencyCallbackMode(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication::cdmaCallWaiting(RadioIndicationType /*type*/,
                                              const CdmaCallWaiting& /*callWaitingRecord*/) {
    return Void();
}

Return<void> RadioIndication::cdmaOtaProvisionStatus(RadioIndicationType /*type*/,
                                                     CdmaOtaProvisionStatus /*status*/) {
    return Void();
}

Return<void> RadioIndication::cdmaInfoRec(RadioIndicationType /*type*/,
                                          const CdmaInformationRecords& /*records*/) {
    return Void();
}

Return<void> RadioIndication::indicateRingbackTone(RadioIndicationType /*type*/, bool /*start*/) {
    return Void();
}

Return<void> RadioIndication::resendIncallMute(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication::cdmaSubscriptionSourceChanged(RadioIndicationType /*type*/,
                                                            CdmaSubscriptionSource /*cdmaSource*/) {
    return Void();
}

Return<void> RadioIndication::cdmaPrlChanged(RadioIndicationType /*type*/, int32_t /*version*/) {
    return Void();
}

Return<void> RadioIndication::exitEmergencyCallbackMode(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication::rilConnected(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication::voiceRadioTechChanged(RadioIndicationType /*type*/,
                                                    RadioTechnology /*rat*/) {
    return Void();
}

Return<void> RadioIndication::cellInfoList(
    RadioIndicationType /*type*/, const ::android::hardware::hidl_vec<CellInfo>& /*records*/) {
    return Void();
}

Return<void> RadioIndication::imsNetworkStateChanged(RadioIndicationType /*type*/) {
    return Void();
}

Return<void> RadioIndication::subscriptionStatusChanged(RadioIndicationType /*type*/,
                                                        bool /*activate*/) {
    return Void();
}

Return<void> RadioIndication::srvccStateNotify(RadioIndicationType /*type*/, SrvccState /*state*/) {
    return Void();
}

Return<void> RadioIndication::hardwareConfigChanged(
    RadioIndicationType /*type*/,
    const ::android::hardware::hidl_vec<HardwareConfig>& /*configs*/) {
    return Void();
}

Return<void> RadioIndication::radioCapabilityIndication(RadioIndicationType /*type*/,
                                                        const RadioCapability& /*rc*/) {
    return Void();
}

Return<void> RadioIndication::onSupplementaryServiceIndication(RadioIndicationType /*type*/,
                                                               const StkCcUnsolSsResult& /*ss*/) {
    return Void();
}

Return<void> RadioIndication::stkCallControlAlphaNotify(
    RadioIndicationType /*type*/, const ::android::hardware::hidl_string& /*alpha*/) {
    return Void();
}

Return<void> RadioIndication::lceData(RadioIndicationType /*type*/, const LceDataInfo& /*lce*/) {
    return Void();
}

Return<void> RadioIndication::pcoData(RadioIndicationType /*type*/, const PcoDataInfo& /*pco*/) {
    return Void();
}

Return<void> RadioIndication::modemReset(RadioIndicationType /*type*/,
                                         const ::android::hardware::hidl_string& /*reason*/) {
    return Void();
}