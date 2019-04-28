#pragma once

#include "managerdefault/AudioPolicyManager.h"

namespace android {

class AudioPolicyManager;

class AudioPolicyManagerCustomInterface
{
    public:
        virtual ~AudioPolicyManagerCustomInterface() {}
        virtual status_t common_set(AudioPolicyManager *audioPolicyManger) = 0;
        virtual audio_stream_type_t gainTable_getVolumeStream() = 0;
        virtual int gainTable_getVolumeIndex() = 0;
        virtual audio_devices_t gainTable_getVolumeDevice() = 0;
        virtual status_t gainTable_setVolumeStream(audio_stream_type_t stream) = 0;
        virtual status_t gainTable_setVolumeIndex(int index) = 0;
        virtual status_t gainTable_setVolumeDevice(audio_devices_t device) = 0;
        virtual status_t gainTable_getCustomAudioVolume(void) = 0;
        virtual float gainTable_getVolumeDbFromComputeVolume(audio_stream_type_t stream, int index, audio_devices_t device, float volumeDB) = 0;
        virtual audio_devices_t gainTable_getDeviceFromComputeVolume(audio_stream_type_t stream, int index, audio_devices_t device) = 0;
        virtual float gainTable_getCorrectVolumeDbFromComputeVolume(audio_stream_type_t stream, float volumeDB, audio_devices_t device) = 0;
        virtual audio_devices_t gainTable_checkInvalidDeviceFromCheckAndSetVolume(const sp<AudioOutputDescriptor>& outputDesc, audio_devices_t device) = 0;
        virtual status_t gainTable_applyAnalogGainFromCheckAndSetVolume(audio_stream_type_t stream, int index,
                                           const sp<AudioOutputDescriptor>& outputDesc,
                                           audio_devices_t device,
                                           int delayMs, bool force) = 0;
        virtual status_t gainTable_setVolumeFromCheckAndSetVolume(audio_stream_type_t stream, int index,
                                           const sp<AudioOutputDescriptor>& outputDesc,
                                           audio_devices_t device,
                                           int delayMs, bool force, float volumeDb) = 0;
        virtual status_t gainTable_routeAndApplyVolumeFromStopSource(const sp<AudioOutputDescriptor>& outputDesc, audio_devices_t device,
                                           audio_stream_type_t stream, bool force) = 0;
        virtual bool gainTable_skipAdjustGainFromSetStreamVolumeIndex(audio_devices_t curDevice, audio_devices_t wantDevice) = 0;
        virtual audio_devices_t gainTable_replaceApplyDeviceFromSetStreamVolumeIndex(audio_devices_t outputDevice, audio_devices_t curDevice) = 0;
        virtual status_t common_setPolicyManagerCustomParameters(int par1, int par2, int par3, int par4) = 0;
        virtual status_t fm_initOutputIdForApp(void) = 0;
        virtual audio_devices_t fm_correctDeviceFromSetDeviceConnectionStateInt(const sp<AudioOutputDescriptor>& outputDesc, audio_devices_t device, bool force) = 0;
        virtual status_t fm_addAudioPatch(audio_patch_handle_t handle, const sp<AudioPatch>& patch) = 0;
        virtual status_t fm_removeAudioPatch(audio_patch_handle_t handle) = 0;
        virtual status_t fm_applyGainFromCheckAndSetVolume(audio_stream_type_t stream, int index, const sp<AudioOutputDescriptor>& outputDesc,audio_devices_t device,
                                                           int delayMs, bool force) = 0;
        virtual status_t fm_muteStrategyFromCheckOutputForStrategy(routing_strategy strategy, audio_devices_t oldDevice, audio_devices_t newDevice) = 0;
        virtual status_t fm_checkSkipVolumeFromCheckOutputForStrategy(routing_strategy strategy, audio_devices_t oldDevice, audio_devices_t newDevice) = 0;
        virtual status_t fm_releaseSkipVolumeFromCheckOutputForStrategy(void) = 0;
        virtual bool fm_checkFirstMusicFromStartSource(const sp<AudioOutputDescriptor>& outputDesc, audio_stream_type_t stream) = 0;
        virtual uint32_t fm_extendMuteFromCheckDeviceMuteStrategies(const sp<AudioOutputDescriptor>& outputDesc, routing_strategy strategy, uint32_t muteDurationMs, uint32_t extendDurationMs) = 0;
        virtual status_t fm_signalAPProutingFromSetOutputDevice(const sp<AudioOutputDescriptor>& outputDesc, bool force) = 0;
        virtual uint32_t fm_extendSleepFromCheckDeviceMuteStrategies(const sp<AudioOutputDescriptor>& outputDesc, uint32_t muteWaitMs) = 0;
        virtual status_t usbPhoneCall_connectFromSetDeviceConnectionState(audio_devices_t device,
                                                      audio_policy_dev_state_t state,
                                                      const char *device_address,
                                                      const char *device_name) = 0;
        virtual status_t usbPhoneCall_setOutputDeviceFromUpdateCallRouting(const sp<AudioOutputDescriptor>& outputDesc,
                                             audio_devices_t rxDevice,
                                             bool force,
                                             int delayMs,
                                             audio_devices_t *txDevice,
                                             uint32_t *muteWaitMs) = 0;
        virtual status_t usbPhoneCall_setPrevModeFromSetPhoneState(audio_mode_t state) = 0;
        virtual status_t usbPhoneCall_closeAllInputsFromSetPhoneState(void) = 0;
        virtual audio_devices_t usbPhoneCall_addCurrentVolumeIndexFromSetStreamVolumeIndex(audio_stream_type_t stream,
                                                  int index,
                                                  audio_devices_t device) = 0;
        virtual audio_devices_t usbPhoneCall_correctDeviceFromGetDevicesForStream(audio_devices_t devices) = 0;
        virtual bool usbPhoneCall_isSupportUSBPhoneCallDevice(const String8& address, audio_devices_t device) = 0;
        virtual status_t gainNvram_remapIndexRangeFromInitStreamVolume(audio_stream_type_t stream,
                                            int *indexMin,
                                            int *indexMax) = 0;
        virtual status_t gainNvram_remapIndexFromSetStreamVolumeIndex(audio_stream_type_t stream,
                                                  int *index,
                                                  audio_devices_t device) = 0;
        virtual status_t gainNvram_remapIndexFromGetStreamVolumeIndex(audio_stream_type_t stream,
                                          int *index,
                                          audio_devices_t device) = 0;
        virtual status_t lowLatency_updatePrimaryModuleDeviceFromSetPhoneState(audio_mode_t state, audio_devices_t rxDevice) = 0;
        virtual status_t lowLatency_CheckSpeakerProtectionDevice(const sp<IOProfile>& outProfile) = 0;
        virtual bool lowLatency_shareModuleActiveFromIsStrategyActive(const sp<AudioOutputDescriptor>& outputDesc,
                                          routing_strategy strategy, uint32_t inPastMs,
                                          nsecs_t sysTime, bool bShareHwModule, audio_stream_type_t stream) = 0;
        virtual bool lowLatency_skipSelectedDeviceFormSetOutputDevice(const sp<AudioOutputDescriptor>& outputDesc, audio_devices_t device) = 0;
        virtual bool lowLatency_skipOutputCheckFromGetOutputsForDevice(audio_devices_t device, const SwAudioOutputCollection& openOutputs, size_t i) = 0;
        virtual bool lowLatency_stopToRouteFromStopSource(audio_devices_t newDevice, const sp<AudioOutputDescriptor>& outputDesc) = 0;
        virtual bool lowLatency_startToRouteFromStartSource(const sp<AudioOutputDescriptor>& outputDesc, bool beFirstActive) = 0;
        virtual bool lowLatency_isOutputActiveFromStartSource(const sp<AudioOutputDescriptor>& outputDesc) = 0;
        virtual status_t besLoudness_signalDupOutputFromSetOutputDevice(const sp<AudioOutputDescriptor>& outputDesc,
                                             audio_devices_t device,
                                             int delayMs) = 0;
        virtual bool debug_skipShowLog() = 0;
        virtual bool debug_showGetOutputForAttr(audio_devices_t device, const audio_config_t *config, audio_output_flags_t flags, audio_stream_type_t stream, audio_io_handle_t output) = 0;
        virtual bool debug_showGetInputForAttr(AudioPolicyInterface::input_type_t inputType, audio_devices_t device, audio_io_handle_t input) = 0;
        virtual bool debug_showSetOutputDevice(const sp<AudioOutputDescriptor>& outputDesc, audio_devices_t device, bool force, int delayMs) = 0;
};

};

