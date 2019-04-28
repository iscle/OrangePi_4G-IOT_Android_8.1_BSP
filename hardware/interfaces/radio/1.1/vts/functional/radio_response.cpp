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

CardStatus cardStatus;

RadioResponse_v1_1::RadioResponse_v1_1(RadioHidlTest_v1_1& parent) : parent_v1_1(parent) {}

/* 1.0 Apis */
Return<void> RadioResponse_v1_1::getIccCardStatusResponse(const RadioResponseInfo& info,
                                                          const CardStatus& card_status) {
    rspInfo = info;
    cardStatus = card_status;
    parent_v1_1.notify();
    return Void();
}

Return<void> RadioResponse_v1_1::supplyIccPinForAppResponse(const RadioResponseInfo& /*info*/,
                                                            int32_t /*remainingRetries*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::supplyIccPukForAppResponse(const RadioResponseInfo& /*info*/,
                                                            int32_t /*remainingRetries*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::supplyIccPin2ForAppResponse(const RadioResponseInfo& /*info*/,
                                                             int32_t /*remainingRetries*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::supplyIccPuk2ForAppResponse(const RadioResponseInfo& /*info*/,
                                                             int32_t /*remainingRetries*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::changeIccPinForAppResponse(const RadioResponseInfo& /*info*/,
                                                            int32_t /*remainingRetries*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::changeIccPin2ForAppResponse(const RadioResponseInfo& /*info*/,
                                                             int32_t /*remainingRetries*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::supplyNetworkDepersonalizationResponse(
    const RadioResponseInfo& /*info*/, int32_t /*remainingRetries*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getCurrentCallsResponse(
    const RadioResponseInfo& /*info*/, const ::android::hardware::hidl_vec<Call>& /*calls*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::dialResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getIMSIForAppResponse(
    const RadioResponseInfo& /*info*/, const ::android::hardware::hidl_string& /*imsi*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::hangupConnectionResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::hangupWaitingOrBackgroundResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::hangupForegroundResumeBackgroundResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::switchWaitingOrHoldingAndActiveResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::conferenceResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::rejectCallResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getLastCallFailCauseResponse(
    const RadioResponseInfo& /*info*/, const LastCallFailCauseInfo& /*failCauseInfo*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getSignalStrengthResponse(const RadioResponseInfo& /*info*/,
                                                           const SignalStrength& /*sig_strength*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getVoiceRegistrationStateResponse(
    const RadioResponseInfo& /*info*/, const VoiceRegStateResult& /*voiceRegResponse*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getDataRegistrationStateResponse(
    const RadioResponseInfo& /*info*/, const DataRegStateResult& /*dataRegResponse*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getOperatorResponse(
    const RadioResponseInfo& /*info*/, const ::android::hardware::hidl_string& /*longName*/,
    const ::android::hardware::hidl_string& /*shortName*/,
    const ::android::hardware::hidl_string& /*numeric*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setRadioPowerResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::sendDtmfResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::sendSmsResponse(const RadioResponseInfo& /*info*/,
                                                 const SendSmsResult& /*sms*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::sendSMSExpectMoreResponse(const RadioResponseInfo& /*info*/,
                                                           const SendSmsResult& /*sms*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setupDataCallResponse(const RadioResponseInfo& /*info*/,
                                                       const SetupDataCallResult& /*dcResponse*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::iccIOForAppResponse(const RadioResponseInfo& /*info*/,
                                                     const IccIoResult& /*iccIo*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::sendUssdResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::cancelPendingUssdResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getClirResponse(const RadioResponseInfo& /*info*/, int32_t /*n*/,
                                                 int32_t /*m*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setClirResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getCallForwardStatusResponse(
    const RadioResponseInfo& /*info*/, const ::android::hardware::hidl_vec<CallForwardInfo>&
    /*callForwardInfos*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setCallForwardResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getCallWaitingResponse(const RadioResponseInfo& /*info*/,
                                                        bool /*enable*/, int32_t /*serviceClass*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setCallWaitingResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::acknowledgeLastIncomingGsmSmsResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::acceptCallResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::deactivateDataCallResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getFacilityLockForAppResponse(const RadioResponseInfo& /*info*/,
                                                               int32_t /*response*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setFacilityLockForAppResponse(const RadioResponseInfo& /*info*/,
                                                               int32_t /*retry*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setBarringPasswordResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getNetworkSelectionModeResponse(const RadioResponseInfo& /*info*/,
                                                                 bool /*manual*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setNetworkSelectionModeAutomaticResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setNetworkSelectionModeManualResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getAvailableNetworksResponse(
    const RadioResponseInfo& /*info*/,
    const ::android::hardware::hidl_vec<OperatorInfo>& /*networkInfos*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::startDtmfResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::stopDtmfResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getBasebandVersionResponse(
    const RadioResponseInfo& /*info*/, const ::android::hardware::hidl_string& /*version*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::separateConnectionResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setMuteResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getMuteResponse(const RadioResponseInfo& /*info*/,
                                                 bool /*enable*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getClipResponse(const RadioResponseInfo& /*info*/,
                                                 ClipStatus /*status*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getDataCallListResponse(
    const RadioResponseInfo& /*info*/,
    const ::android::hardware::hidl_vec<SetupDataCallResult>& /*dcResponse*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::sendOemRilRequestRawResponse(
    const RadioResponseInfo& /*info*/, const ::android::hardware::hidl_vec<uint8_t>& /*data*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::sendOemRilRequestStringsResponse(
    const RadioResponseInfo& /*info*/,
    const ::android::hardware::hidl_vec< ::android::hardware::hidl_string>& /*data*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setSuppServiceNotificationsResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::writeSmsToSimResponse(const RadioResponseInfo& /*info*/,
                                                       int32_t /*index*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::deleteSmsOnSimResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setBandModeResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getAvailableBandModesResponse(
    const RadioResponseInfo& /*info*/,
    const ::android::hardware::hidl_vec<RadioBandMode>& /*bandModes*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::sendEnvelopeResponse(
    const RadioResponseInfo& /*info*/,
    const ::android::hardware::hidl_string& /*commandResponse*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::sendTerminalResponseToSimResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::handleStkCallSetupRequestFromSimResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::explicitCallTransferResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setPreferredNetworkTypeResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getPreferredNetworkTypeResponse(const RadioResponseInfo& /*info*/,
                                                                 PreferredNetworkType /*nw_type*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getNeighboringCidsResponse(
    const RadioResponseInfo& /*info*/,
    const ::android::hardware::hidl_vec<NeighboringCell>& /*cells*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setLocationUpdatesResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setCdmaSubscriptionSourceResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setCdmaRoamingPreferenceResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getCdmaRoamingPreferenceResponse(const RadioResponseInfo& /*info*/,
                                                                  CdmaRoamingType /*type*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setTTYModeResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getTTYModeResponse(const RadioResponseInfo& /*info*/,
                                                    TtyMode /*mode*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setPreferredVoicePrivacyResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getPreferredVoicePrivacyResponse(const RadioResponseInfo& /*info*/,
                                                                  bool /*enable*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::sendCDMAFeatureCodeResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::sendBurstDtmfResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::sendCdmaSmsResponse(const RadioResponseInfo& /*info*/,
                                                     const SendSmsResult& /*sms*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::acknowledgeLastIncomingCdmaSmsResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getGsmBroadcastConfigResponse(
    const RadioResponseInfo& /*info*/,
    const ::android::hardware::hidl_vec<GsmBroadcastSmsConfigInfo>& /*configs*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setGsmBroadcastConfigResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setGsmBroadcastActivationResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getCdmaBroadcastConfigResponse(
    const RadioResponseInfo& /*info*/,
    const ::android::hardware::hidl_vec<CdmaBroadcastSmsConfigInfo>& /*configs*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setCdmaBroadcastConfigResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setCdmaBroadcastActivationResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getCDMASubscriptionResponse(
    const RadioResponseInfo& /*info*/, const ::android::hardware::hidl_string& /*mdn*/,
    const ::android::hardware::hidl_string& /*hSid*/,
    const ::android::hardware::hidl_string& /*hNid*/,
    const ::android::hardware::hidl_string& /*min*/,
    const ::android::hardware::hidl_string& /*prl*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::writeSmsToRuimResponse(const RadioResponseInfo& /*info*/,
                                                        uint32_t /*index*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::deleteSmsOnRuimResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getDeviceIdentityResponse(
    const RadioResponseInfo& /*info*/, const ::android::hardware::hidl_string& /*imei*/,
    const ::android::hardware::hidl_string& /*imeisv*/,
    const ::android::hardware::hidl_string& /*esn*/,
    const ::android::hardware::hidl_string& /*meid*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::exitEmergencyCallbackModeResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getSmscAddressResponse(
    const RadioResponseInfo& /*info*/, const ::android::hardware::hidl_string& /*smsc*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setSmscAddressResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::reportSmsMemoryStatusResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::reportStkServiceIsRunningResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getCdmaSubscriptionSourceResponse(
    const RadioResponseInfo& /*info*/, CdmaSubscriptionSource /*source*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::requestIsimAuthenticationResponse(
    const RadioResponseInfo& /*info*/, const ::android::hardware::hidl_string& /*response*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::acknowledgeIncomingGsmSmsWithPduResponse(
    const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::sendEnvelopeWithStatusResponse(const RadioResponseInfo& /*info*/,
                                                                const IccIoResult& /*iccIo*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getVoiceRadioTechnologyResponse(const RadioResponseInfo& /*info*/,
                                                                 RadioTechnology /*rat*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getCellInfoListResponse(
    const RadioResponseInfo& /*info*/,
    const ::android::hardware::hidl_vec<CellInfo>& /*cellInfo*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setCellInfoListRateResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setInitialAttachApnResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getImsRegistrationStateResponse(
    const RadioResponseInfo& /*info*/, bool /*isRegistered*/, RadioTechnologyFamily /*ratFamily*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::sendImsSmsResponse(const RadioResponseInfo& /*info*/,
                                                    const SendSmsResult& /*sms*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::iccTransmitApduBasicChannelResponse(
    const RadioResponseInfo& /*info*/, const IccIoResult& /*result*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::iccOpenLogicalChannelResponse(
    const RadioResponseInfo& /*info*/, int32_t /*channelId*/,
    const ::android::hardware::hidl_vec<int8_t>& /*selectResponse*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::iccCloseLogicalChannelResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::iccTransmitApduLogicalChannelResponse(
    const RadioResponseInfo& /*info*/, const IccIoResult& /*result*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::nvReadItemResponse(
    const RadioResponseInfo& /*info*/, const ::android::hardware::hidl_string& /*result*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::nvWriteItemResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::nvWriteCdmaPrlResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::nvResetConfigResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setUiccSubscriptionResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setDataAllowedResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getHardwareConfigResponse(
    const RadioResponseInfo& /*info*/,
    const ::android::hardware::hidl_vec<HardwareConfig>& /*config*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::requestIccSimAuthenticationResponse(
    const RadioResponseInfo& /*info*/, const IccIoResult& /*result*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setDataProfileResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::requestShutdownResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getRadioCapabilityResponse(const RadioResponseInfo& /*info*/,
                                                            const RadioCapability& /*rc*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setRadioCapabilityResponse(const RadioResponseInfo& /*info*/,
                                                            const RadioCapability& /*rc*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::startLceServiceResponse(const RadioResponseInfo& /*info*/,
                                                         const LceStatusInfo& /*statusInfo*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::stopLceServiceResponse(const RadioResponseInfo& /*info*/,
                                                        const LceStatusInfo& /*statusInfo*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::pullLceDataResponse(const RadioResponseInfo& /*info*/,
                                                     const LceDataInfo& /*lceInfo*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getModemActivityInfoResponse(
    const RadioResponseInfo& /*info*/, const ActivityStatsInfo& /*activityInfo*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setAllowedCarriersResponse(const RadioResponseInfo& /*info*/,
                                                            int32_t /*numAllowed*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::getAllowedCarriersResponse(
    const RadioResponseInfo& /*info*/, bool /*allAllowed*/,
    const CarrierRestrictions& /*carriers*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::sendDeviceStateResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setIndicationFilterResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::setSimCardPowerResponse(const RadioResponseInfo& /*info*/) {
    return Void();
}

Return<void> RadioResponse_v1_1::acknowledgeRequest(int32_t /*serial*/) {
    return Void();
}

/* 1.1 Apis */
Return<void> RadioResponse_v1_1::setCarrierInfoForImsiEncryptionResponse(
    const RadioResponseInfo& info) {
    rspInfo = info;
    parent_v1_1.notify();
    return Void();
}

Return<void> RadioResponse_v1_1::setSimCardPowerResponse_1_1(const RadioResponseInfo& info) {
    rspInfo = info;
    parent_v1_1.notify();
    return Void();
}

Return<void> RadioResponse_v1_1::startNetworkScanResponse(const RadioResponseInfo& info) {
    rspInfo = info;
    parent_v1_1.notify();
    return Void();
}

Return<void> RadioResponse_v1_1::stopNetworkScanResponse(const RadioResponseInfo& info) {
    rspInfo = info;
    parent_v1_1.notify();
    return Void();
}

Return<void> RadioResponse_v1_1::startKeepaliveResponse(const RadioResponseInfo& info,
                                                        const KeepaliveStatus& status) {
    rspInfo = info;
    keepaliveStatus = status;
    parent_v1_1.notify();
    return Void();
}

Return<void> RadioResponse_v1_1::stopKeepaliveResponse(const RadioResponseInfo& info) {
    rspInfo = info;
    parent_v1_1.notify();
    return Void();
}
