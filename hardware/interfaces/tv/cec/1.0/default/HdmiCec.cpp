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

#define LOG_TAG "android.hardware.tv.cec@1.0-impl"
#include <android-base/logging.h>

#include <hardware/hardware.h>
#include <hardware/hdmi_cec.h>
#include "HdmiCec.h"

namespace android {
namespace hardware {
namespace tv {
namespace cec {
namespace V1_0 {
namespace implementation {

static_assert(CEC_DEVICE_INACTIVE == static_cast<int>(CecDeviceType::INACTIVE),
        "CecDeviceType::INACTIVE must match legacy value.");
static_assert(CEC_DEVICE_TV == static_cast<int>(CecDeviceType::TV),
        "CecDeviceType::TV must match legacy value.");
static_assert(CEC_DEVICE_RECORDER == static_cast<int>(CecDeviceType::RECORDER),
        "CecDeviceType::RECORDER must match legacy value.");
static_assert(CEC_DEVICE_TUNER == static_cast<int>(CecDeviceType::TUNER),
        "CecDeviceType::TUNER must match legacy value.");
static_assert(CEC_DEVICE_PLAYBACK == static_cast<int>(CecDeviceType::PLAYBACK),
        "CecDeviceType::PLAYBACK must match legacy value.");
static_assert(CEC_DEVICE_AUDIO_SYSTEM == static_cast<int>(CecDeviceType::AUDIO_SYSTEM),
        "CecDeviceType::AUDIO_SYSTEM must match legacy value.");
static_assert(CEC_DEVICE_MAX == static_cast<int>(CecDeviceType::MAX),
        "CecDeviceType::MAX must match legacy value.");

static_assert(CEC_ADDR_TV == static_cast<int>(CecLogicalAddress::TV),
        "CecLogicalAddress::TV must match legacy value.");
static_assert(CEC_ADDR_RECORDER_1 == static_cast<int>(CecLogicalAddress::RECORDER_1),
        "CecLogicalAddress::RECORDER_1 must match legacy value.");
static_assert(CEC_ADDR_RECORDER_2 == static_cast<int>(CecLogicalAddress::RECORDER_2),
        "CecLogicalAddress::RECORDER_2 must match legacy value.");
static_assert(CEC_ADDR_TUNER_1 == static_cast<int>(CecLogicalAddress::TUNER_1),
        "CecLogicalAddress::TUNER_1 must match legacy value.");
static_assert(CEC_ADDR_PLAYBACK_1 == static_cast<int>(CecLogicalAddress::PLAYBACK_1),
        "CecLogicalAddress::PLAYBACK_1 must match legacy value.");
static_assert(CEC_ADDR_AUDIO_SYSTEM == static_cast<int>(CecLogicalAddress::AUDIO_SYSTEM),
        "CecLogicalAddress::AUDIO_SYSTEM must match legacy value.");
static_assert(CEC_ADDR_TUNER_2 == static_cast<int>(CecLogicalAddress::TUNER_2),
        "CecLogicalAddress::TUNER_2 must match legacy value.");
static_assert(CEC_ADDR_TUNER_3 == static_cast<int>(CecLogicalAddress::TUNER_3),
        "CecLogicalAddress::TUNER_3 must match legacy value.");
static_assert(CEC_ADDR_PLAYBACK_2 == static_cast<int>(CecLogicalAddress::PLAYBACK_2),
        "CecLogicalAddress::PLAYBACK_2 must match legacy value.");
static_assert(CEC_ADDR_RECORDER_3 == static_cast<int>(CecLogicalAddress::RECORDER_3),
        "CecLogicalAddress::RECORDER_3 must match legacy value.");
static_assert(CEC_ADDR_TUNER_4 == static_cast<int>(CecLogicalAddress::TUNER_4),
        "CecLogicalAddress::TUNER_4 must match legacy value.");
static_assert(CEC_ADDR_PLAYBACK_3 == static_cast<int>(CecLogicalAddress::PLAYBACK_3),
        "CecLogicalAddress::PLAYBACK_3 must match legacy value.");
static_assert(CEC_ADDR_FREE_USE == static_cast<int>(CecLogicalAddress::FREE_USE),
        "CecLogicalAddress::FREE_USE must match legacy value.");
static_assert(CEC_ADDR_UNREGISTERED == static_cast<int>(CecLogicalAddress::UNREGISTERED),
        "CecLogicalAddress::UNREGISTERED must match legacy value.");
static_assert(CEC_ADDR_BROADCAST == static_cast<int>(CecLogicalAddress::BROADCAST),
        "CecLogicalAddress::BROADCAST must match legacy value.");

static_assert(CEC_MESSAGE_FEATURE_ABORT == static_cast<int>(CecMessageType::FEATURE_ABORT),
        "CecMessageType::FEATURE_ABORT must match legacy value.");
static_assert(CEC_MESSAGE_IMAGE_VIEW_ON == static_cast<int>(CecMessageType::IMAGE_VIEW_ON),
        "CecMessageType::IMAGE_VIEW_ON must match legacy value.");
static_assert(CEC_MESSAGE_TUNER_STEP_INCREMENT == static_cast<int>(
        CecMessageType::TUNER_STEP_INCREMENT),
        "CecMessageType::TUNER_STEP_INCREMENT must match legacy value.");
static_assert(CEC_MESSAGE_TUNER_STEP_DECREMENT == static_cast<int>(
        CecMessageType::TUNER_STEP_DECREMENT),
        "CecMessageType::TUNER_STEP_DECREMENT must match legacy value.");
static_assert(CEC_MESSAGE_TUNER_DEVICE_STATUS == static_cast<int>(
        CecMessageType::TUNER_DEVICE_STATUS),
        "CecMessageType::TUNER_DEVICE_STATUS must match legacy value.");
static_assert(CEC_MESSAGE_GIVE_TUNER_DEVICE_STATUS == static_cast<int>(
        CecMessageType::GIVE_TUNER_DEVICE_STATUS),
        "CecMessageType::GIVE_TUNER_DEVICE_STATUS must match legacy value.");
static_assert(CEC_MESSAGE_RECORD_ON == static_cast<int>(CecMessageType::RECORD_ON),
        "CecMessageType::RECORD_ON must match legacy value.");
static_assert(CEC_MESSAGE_RECORD_STATUS == static_cast<int>(CecMessageType::RECORD_STATUS),
        "CecMessageType::RECORD_STATUS must match legacy value.");
static_assert(CEC_MESSAGE_RECORD_OFF == static_cast<int>(CecMessageType::RECORD_OFF),
        "CecMessageType::RECORD_OFF must match legacy value.");
static_assert(CEC_MESSAGE_TEXT_VIEW_ON == static_cast<int>(CecMessageType::TEXT_VIEW_ON),
        "CecMessageType::TEXT_VIEW_ON must match legacy value.");
static_assert(CEC_MESSAGE_RECORD_TV_SCREEN == static_cast<int>(CecMessageType::RECORD_TV_SCREEN),
        "CecMessageType::RECORD_TV_SCREEN must match legacy value.");
static_assert(CEC_MESSAGE_GIVE_DECK_STATUS == static_cast<int>(CecMessageType::GIVE_DECK_STATUS),
        "CecMessageType::GIVE_DECK_STATUS must match legacy value.");
static_assert(CEC_MESSAGE_STANDBY == static_cast<int>(CecMessageType::STANDBY),
        "CecMessageType::STANDBY must match legacy value.");
static_assert(CEC_MESSAGE_PLAY == static_cast<int>(CecMessageType::PLAY),
        "CecMessageType::PLAY must match legacy value.");
static_assert(CEC_MESSAGE_DECK_CONTROL == static_cast<int>(CecMessageType::DECK_CONTROL),
        "CecMessageType::DECK_CONTROL must match legacy value.");
static_assert(CEC_MESSAGE_TIMER_CLEARED_STATUS == static_cast<int>(
        CecMessageType::TIMER_CLEARED_STATUS),
        "CecMessageType::TIMER_CLEARED_STATUS must match legacy value.");
static_assert(CEC_MESSAGE_USER_CONTROL_PRESSED == static_cast<int>(
        CecMessageType::USER_CONTROL_PRESSED),
        "CecMessageType::USER_CONTROL_PRESSED must match legacy value.");
static_assert(CEC_MESSAGE_USER_CONTROL_RELEASED == static_cast<int>(
        CecMessageType::USER_CONTROL_RELEASED),
        "CecMessageType::USER_CONTROL_RELEASED must match legacy value.");
static_assert(CEC_MESSAGE_GIVE_OSD_NAME == static_cast<int>(CecMessageType::GIVE_OSD_NAME),
        "CecMessageType::GIVE_OSD_NAME must match legacy value.");
static_assert(CEC_MESSAGE_SET_OSD_NAME == static_cast<int>(CecMessageType::SET_OSD_NAME),
        "CecMessageType::SET_OSD_NAME must match legacy value.");
static_assert(CEC_MESSAGE_SYSTEM_AUDIO_MODE_REQUEST == static_cast<int>(
        CecMessageType::SYSTEM_AUDIO_MODE_REQUEST),
        "CecMessageType::SYSTEM_AUDIO_MODE_REQUEST must match legacy value.");
static_assert(CEC_MESSAGE_GIVE_AUDIO_STATUS == static_cast<int>(CecMessageType::GIVE_AUDIO_STATUS),
        "CecMessageType::GIVE_AUDIO_STATUS must match legacy value.");
static_assert(CEC_MESSAGE_SET_SYSTEM_AUDIO_MODE == static_cast<int>(
        CecMessageType::SET_SYSTEM_AUDIO_MODE),
        "CecMessageType::SET_SYSTEM_AUDIO_MODE must match legacy value.");
static_assert(CEC_MESSAGE_REPORT_AUDIO_STATUS == static_cast<int>(
        CecMessageType::REPORT_AUDIO_STATUS),
        "CecMessageType::REPORT_AUDIO_STATUS must match legacy value.");
static_assert(CEC_MESSAGE_GIVE_SYSTEM_AUDIO_MODE_STATUS == static_cast<int>(
        CecMessageType::GIVE_SYSTEM_AUDIO_MODE_STATUS),
        "CecMessageType::GIVE_SYSTEM_AUDIO_MODE_STATUS must match legacy value.");
static_assert(CEC_MESSAGE_SYSTEM_AUDIO_MODE_STATUS == static_cast<int>(
        CecMessageType::SYSTEM_AUDIO_MODE_STATUS),
        "CecMessageType::SYSTEM_AUDIO_MODE_STATUS must match legacy value.");
static_assert(CEC_MESSAGE_ROUTING_CHANGE == static_cast<int>(CecMessageType::ROUTING_CHANGE),
        "CecMessageType::ROUTING_CHANGE must match legacy value.");
static_assert(CEC_MESSAGE_ROUTING_INFORMATION == static_cast<int>(
        CecMessageType::ROUTING_INFORMATION),
        "CecMessageType::ROUTING_INFORMATION must match legacy value.");
static_assert(CEC_MESSAGE_ACTIVE_SOURCE == static_cast<int>(CecMessageType::ACTIVE_SOURCE),
        "CecMessageType::ACTIVE_SOURCE must match legacy value.");
static_assert(CEC_MESSAGE_GIVE_PHYSICAL_ADDRESS == static_cast<int>(
        CecMessageType::GIVE_PHYSICAL_ADDRESS),
        "CecMessageType::GIVE_PHYSICAL_ADDRESS must match legacy value.");
static_assert(CEC_MESSAGE_REPORT_PHYSICAL_ADDRESS == static_cast<int>(
        CecMessageType::REPORT_PHYSICAL_ADDRESS),
        "CecMessageType::REPORT_PHYSICAL_ADDRESS must match legacy value.");
static_assert(CEC_MESSAGE_REQUEST_ACTIVE_SOURCE == static_cast<int>(
        CecMessageType::REQUEST_ACTIVE_SOURCE),
        "CecMessageType::REQUEST_ACTIVE_SOURCE must match legacy value.");
static_assert(CEC_MESSAGE_SET_STREAM_PATH == static_cast<int>(CecMessageType::SET_STREAM_PATH),
        "CecMessageType::SET_STREAM_PATH must match legacy value.");
static_assert(CEC_MESSAGE_DEVICE_VENDOR_ID == static_cast<int>(CecMessageType::DEVICE_VENDOR_ID),
        "CecMessageType::DEVICE_VENDOR_ID must match legacy value.");
static_assert(CEC_MESSAGE_VENDOR_COMMAND == static_cast<int>(CecMessageType::VENDOR_COMMAND),
        "CecMessageType::VENDOR_COMMAND must match legacy value.");
static_assert(CEC_MESSAGE_VENDOR_REMOTE_BUTTON_DOWN == static_cast<int>(
        CecMessageType::VENDOR_REMOTE_BUTTON_DOWN),
        "CecMessageType::VENDOR_REMOTE_BUTTON_DOWN must match legacy value.");
static_assert(CEC_MESSAGE_VENDOR_REMOTE_BUTTON_UP == static_cast<int>(
        CecMessageType::VENDOR_REMOTE_BUTTON_UP),
        "CecMessageType::VENDOR_REMOTE_BUTTON_UP must match legacy value.");
static_assert(CEC_MESSAGE_GIVE_DEVICE_VENDOR_ID == static_cast<int>(
        CecMessageType::GIVE_DEVICE_VENDOR_ID),
        "CecMessageType::GIVE_DEVICE_VENDOR_ID must match legacy value.");
static_assert(CEC_MESSAGE_MENU_REQUEST == static_cast<int>(CecMessageType::MENU_REQUEST),
        "CecMessageType::MENU_REQUEST must match legacy value.");
static_assert(CEC_MESSAGE_MENU_STATUS == static_cast<int>(CecMessageType::MENU_STATUS),
        "CecMessageType::MENU_STATUS must match legacy value.");
static_assert(CEC_MESSAGE_GIVE_DEVICE_POWER_STATUS == static_cast<int>(
        CecMessageType::GIVE_DEVICE_POWER_STATUS),
        "CecMessageType::GIVE_DEVICE_POWER_STATUS must match legacy value.");
static_assert(CEC_MESSAGE_REPORT_POWER_STATUS == static_cast<int>(
        CecMessageType::REPORT_POWER_STATUS),
        "CecMessageType::REPORT_POWER_STATUS must match legacy value.");
static_assert(CEC_MESSAGE_GET_MENU_LANGUAGE == static_cast<int>(CecMessageType::GET_MENU_LANGUAGE),
        "CecMessageType::GET_MENU_LANGUAGE must match legacy value.");
static_assert(CEC_MESSAGE_SELECT_ANALOG_SERVICE == static_cast<int>(
        CecMessageType::SELECT_ANALOG_SERVICE),
        "CecMessageType::SELECT_ANALOG_SERVICE must match legacy value.");
static_assert(CEC_MESSAGE_SELECT_DIGITAL_SERVICE == static_cast<int>(
        CecMessageType::SELECT_DIGITAL_SERVICE),
        "CecMessageType::SELECT_DIGITAL_SERVICE must match legacy value.");
static_assert(CEC_MESSAGE_SET_DIGITAL_TIMER == static_cast<int>(CecMessageType::SET_DIGITAL_TIMER),
        "CecMessageType::SET_DIGITAL_TIMER must match legacy value.");
static_assert(CEC_MESSAGE_CLEAR_DIGITAL_TIMER == static_cast<int>(
        CecMessageType::CLEAR_DIGITAL_TIMER),
        "CecMessageType::CLEAR_DIGITAL_TIMER must match legacy value.");
static_assert(CEC_MESSAGE_SET_AUDIO_RATE == static_cast<int>(CecMessageType::SET_AUDIO_RATE),
        "CecMessageType::SET_AUDIO_RATE must match legacy value.");
static_assert(CEC_MESSAGE_INACTIVE_SOURCE == static_cast<int>(CecMessageType::INACTIVE_SOURCE),
        "CecMessageType::INACTIVE_SOURCE must match legacy value.");
static_assert(CEC_MESSAGE_CEC_VERSION == static_cast<int>(CecMessageType::CEC_VERSION),
        "CecMessageType::CEC_VERSION must match legacy value.");
static_assert(CEC_MESSAGE_GET_CEC_VERSION == static_cast<int>(CecMessageType::GET_CEC_VERSION),
        "CecMessageType::GET_CEC_VERSION must match legacy value.");
static_assert(CEC_MESSAGE_VENDOR_COMMAND_WITH_ID == static_cast<int>(
        CecMessageType::VENDOR_COMMAND_WITH_ID),
        "CecMessageType::VENDOR_COMMAND_WITH_ID must match legacy value.");
static_assert(CEC_MESSAGE_CLEAR_EXTERNAL_TIMER == static_cast<int>(
        CecMessageType::CLEAR_EXTERNAL_TIMER),
        "CecMessageType::CLEAR_EXTERNAL_TIMER must match legacy value.");
static_assert(CEC_MESSAGE_SET_EXTERNAL_TIMER == static_cast<int>(
        CecMessageType::SET_EXTERNAL_TIMER),
        "CecMessageType::SET_EXTERNAL_TIMER must match legacy value.");
static_assert(CEC_MESSAGE_INITIATE_ARC == static_cast<int>(CecMessageType::INITIATE_ARC),
        "CecMessageType::INITIATE_ARC must match legacy value.");
static_assert(CEC_MESSAGE_REPORT_ARC_INITIATED == static_cast<int>(
        CecMessageType::REPORT_ARC_INITIATED),
        "CecMessageType::REPORT_ARC_INITIATED must match legacy value.");
static_assert(CEC_MESSAGE_REPORT_ARC_TERMINATED == static_cast<int>(
        CecMessageType::REPORT_ARC_TERMINATED),
        "CecMessageType::REPORT_ARC_TERMINATED must match legacy value.");
static_assert(CEC_MESSAGE_REQUEST_ARC_INITIATION == static_cast<int>(
        CecMessageType::REQUEST_ARC_INITIATION),
        "CecMessageType::REQUEST_ARC_INITIATION must match legacy value.");
static_assert(CEC_MESSAGE_REQUEST_ARC_TERMINATION == static_cast<int>(
        CecMessageType::REQUEST_ARC_TERMINATION),
        "CecMessageType::REQUEST_ARC_TERMINATION must match legacy value.");
static_assert(CEC_MESSAGE_TERMINATE_ARC == static_cast<int>(CecMessageType::TERMINATE_ARC),
        "CecMessageType::TERMINATE_ARC must match legacy value.");
static_assert(CEC_MESSAGE_ABORT == static_cast<int>(CecMessageType::ABORT),
        "CecMessageType::ABORT must match legacy value.");

static_assert(ABORT_UNRECOGNIZED_MODE == static_cast<int>(AbortReason::UNRECOGNIZED_MODE),
        "AbortReason::UNRECOGNIZED_MODE must match legacy value.");
static_assert(ABORT_NOT_IN_CORRECT_MODE == static_cast<int>(AbortReason::NOT_IN_CORRECT_MODE),
        "AbortReason::NOT_IN_CORRECT_MODE must match legacy value.");
static_assert(ABORT_CANNOT_PROVIDE_SOURCE == static_cast<int>(AbortReason::CANNOT_PROVIDE_SOURCE),
        "AbortReason::CANNOT_PROVIDE_SOURCE must match legacy value.");
static_assert(ABORT_INVALID_OPERAND == static_cast<int>(AbortReason::INVALID_OPERAND),
        "AbortReason::INVALID_OPERAND must match legacy value.");
static_assert(ABORT_REFUSED == static_cast<int>(AbortReason::REFUSED),
        "AbortReason::REFUSED must match legacy value.");
static_assert(ABORT_UNABLE_TO_DETERMINE == static_cast<int>(AbortReason::UNABLE_TO_DETERMINE),
        "AbortReason::UNABLE_TO_DETERMINE must match legacy value.");

static_assert(HDMI_RESULT_SUCCESS == static_cast<int>(SendMessageResult::SUCCESS),
        "SendMessageResult::SUCCESS must match legacy value.");
static_assert(HDMI_RESULT_NACK == static_cast<int>(SendMessageResult::NACK),
        "SendMessageResult::NACK must match legacy value.");
static_assert(HDMI_RESULT_BUSY == static_cast<int>(SendMessageResult::BUSY),
        "SendMessageResult::BUSY must match legacy value.");
static_assert(HDMI_RESULT_FAIL == static_cast<int>(SendMessageResult::FAIL),
        "SendMessageResult::FAIL must match legacy value.");

static_assert(HDMI_INPUT == static_cast<int>(HdmiPortType::INPUT),
        "HdmiPortType::INPUT must match legacy value.");
static_assert(HDMI_OUTPUT == static_cast<int>(HdmiPortType::OUTPUT),
        "HdmiPortType::OUTPUT must match legacy value.");

static_assert(HDMI_OPTION_WAKEUP == static_cast<int>(OptionKey::WAKEUP),
        "OptionKey::WAKEUP must match legacy value.");
static_assert(HDMI_OPTION_ENABLE_CEC == static_cast<int>(OptionKey::ENABLE_CEC),
        "OptionKey::ENABLE_CEC must match legacy value.");
static_assert(HDMI_OPTION_SYSTEM_CEC_CONTROL == static_cast<int>(OptionKey::SYSTEM_CEC_CONTROL),
        "OptionKey::SYSTEM_CEC_CONTROL must match legacy value.");

sp<IHdmiCecCallback> HdmiCec::mCallback = nullptr;

HdmiCec::HdmiCec(hdmi_cec_device_t* device) : mDevice(device) {
}

// Methods from ::android::hardware::tv::cec::V1_0::IHdmiCec follow.
Return<Result> HdmiCec::addLogicalAddress(CecLogicalAddress addr) {
    int ret = mDevice->add_logical_address(mDevice, static_cast<cec_logical_address_t>(addr));
    switch (ret) {
        case 0:
            return Result::SUCCESS;
        case -EINVAL:
            return Result::FAILURE_INVALID_ARGS;
        case -ENOTSUP:
            return Result::FAILURE_NOT_SUPPORTED;
        case -EBUSY:
            return Result::FAILURE_BUSY;
        default:
            return Result::FAILURE_UNKNOWN;
    }
}

Return<void> HdmiCec::clearLogicalAddress() {
    mDevice->clear_logical_address(mDevice);
    return Void();
}

Return<void> HdmiCec::getPhysicalAddress(getPhysicalAddress_cb _hidl_cb) {
    uint16_t addr;
    int ret = mDevice->get_physical_address(mDevice, &addr);
    switch (ret) {
        case 0:
            _hidl_cb(Result::SUCCESS, addr);
            break;
        case -EBADF:
            _hidl_cb(Result::FAILURE_INVALID_STATE, addr);
            break;
        default:
            _hidl_cb(Result::FAILURE_UNKNOWN, addr);
            break;
    }
    return Void();
}

Return<SendMessageResult> HdmiCec::sendMessage(const CecMessage& message) {
    cec_message_t legacyMessage {
        .initiator = static_cast<cec_logical_address_t>(message.initiator),
        .destination = static_cast<cec_logical_address_t>(message.destination),
        .length = message.body.size(),
    };
    for (size_t i = 0; i < message.body.size(); ++i) {
        legacyMessage.body[i] = static_cast<unsigned char>(message.body[i]);
    }
    return static_cast<SendMessageResult>(mDevice->send_message(mDevice, &legacyMessage));
}

Return<void> HdmiCec::setCallback(const sp<IHdmiCecCallback>& callback) {
    mCallback = callback;
    mDevice->register_event_callback(mDevice, eventCallback, nullptr);
    return Void();
}

Return<int32_t> HdmiCec::getCecVersion() {
    int version;
    mDevice->get_version(mDevice, &version);
    return static_cast<int32_t>(version);
}

Return<uint32_t> HdmiCec::getVendorId() {
    uint32_t vendor_id;
    mDevice->get_vendor_id(mDevice, &vendor_id);
    return vendor_id;
}

Return<void> HdmiCec::getPortInfo(getPortInfo_cb _hidl_cb) {
    struct hdmi_port_info* legacyPorts;
    int numPorts;
    hidl_vec<HdmiPortInfo> portInfos;
    mDevice->get_port_info(mDevice, &legacyPorts, &numPorts);
    portInfos.resize(numPorts);
    for (int i = 0; i < numPorts; ++i) {
        portInfos[i] = {
            .type = static_cast<HdmiPortType>(legacyPorts[i].type),
            .portId = static_cast<uint32_t>(legacyPorts[i].port_id),
            .cecSupported = legacyPorts[i].cec_supported != 0,
            .arcSupported = legacyPorts[i].arc_supported != 0,
            .physicalAddress = legacyPorts[i].physical_address
        };
    }
    _hidl_cb(portInfos);
    return Void();
}

Return<void> HdmiCec::setOption(OptionKey key, bool value) {
    mDevice->set_option(mDevice, static_cast<int>(key), value ? 1 : 0);
    return Void();
}

Return<void> HdmiCec::setLanguage(const hidl_string& language) {
    if (language.size() != 3) {
        LOG(ERROR) << "Wrong language code: expected 3 letters, but it was " << language.size()
                << ".";
        return Void();
    }
    const char *languageStr = language.c_str();
    int convertedLanguage = ((languageStr[0] & 0xFF) << 16)
            | ((languageStr[1] & 0xFF) << 8)
            | (languageStr[2] & 0xFF);
    mDevice->set_option(mDevice, HDMI_OPTION_SET_LANG, convertedLanguage);
    return Void();
}

Return<void> HdmiCec::enableAudioReturnChannel(int32_t portId, bool enable) {
    mDevice->set_audio_return_channel(mDevice, portId, enable ? 1 : 0);
    return Void();
}

Return<bool> HdmiCec::isConnected(int32_t portId) {
    return mDevice->is_connected(mDevice, portId) > 0;
}


IHdmiCec* HIDL_FETCH_IHdmiCec(const char* hal) {
    hdmi_cec_device_t* hdmi_cec_device;
    int ret = 0;
    const hw_module_t* hw_module = nullptr;

    ret = hw_get_module (HDMI_CEC_HARDWARE_MODULE_ID, &hw_module);
    if (ret == 0) {
        ret = hdmi_cec_open (hw_module, &hdmi_cec_device);
        if (ret != 0) {
            LOG(ERROR) << "hdmi_cec_open " << hal << " failed: " << ret;
        }
    } else {
        LOG(ERROR) << "hw_get_module " << hal << " failed: " << ret;
    }

    if (ret == 0) {
        return new HdmiCec(hdmi_cec_device);
    } else {
        LOG(ERROR) << "Passthrough failed to load legacy HAL.";
        return nullptr;
    }
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace cec
}  // namespace tv
}  // namespace hardware
}  // namespace android
