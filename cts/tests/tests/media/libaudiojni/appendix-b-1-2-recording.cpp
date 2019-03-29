/*
 * Copyright (C) 2015 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "OpenSL-ES-Test-B-1-2-Recording"

#include "sl-utils.h"

/*
 * See https://www.khronos.org/registry/sles/specs/OpenSL_ES_Specification_1.0.1.pdf
 * Appendix B.1.2 sample code.
 *
 * Minor edits made to conform to Android coding style.
 *
 * Correction to code: SL_IID_AUDIOIODEVICECAPABILITIES is not supported.
 * Detection of microphone should be made in Java layer.
 */

#define MAX_NUMBER_INTERFACES 5
#define MAX_NUMBER_INPUT_DEVICES 3
#define POSITION_UPDATE_PERIOD 1000 /* 1 sec */

static void RecordEventCallback(SLRecordItf caller __unused,
        void *pContext __unused,
        SLuint32 recordevent __unused)
{
    /* Callback code goes here */
}

/*
 * Test recording of audio from a microphone into a specified file
 */
static void TestAudioRecording(SLObjectItf sl)
{
    SLObjectItf recorder;
    SLRecordItf recordItf;
    SLEngineItf EngineItf;
    SLAudioIODeviceCapabilitiesItf AudioIODeviceCapabilitiesItf;
    SLAudioInputDescriptor AudioInputDescriptor;
    SLresult res;

    SLDataSource audioSource;
    SLDataLocator_IODevice locator_mic;
    SLDeviceVolumeItf devicevolumeItf;
    SLDataSink audioSink;

    int i;
    SLboolean required[MAX_NUMBER_INTERFACES];
    SLInterfaceID iidArray[MAX_NUMBER_INTERFACES];

    SLuint32 InputDeviceIDs[MAX_NUMBER_INPUT_DEVICES];
    SLint32 numInputs = 0;
    SLboolean mic_available = SL_BOOLEAN_FALSE;
    SLuint32 mic_deviceID = 0;

    /* Get the SL Engine Interface which is implicit */
    res = (*sl)->GetInterface(sl, SL_IID_ENGINE, (void *)&EngineItf);
    CheckErr(res);

    AudioIODeviceCapabilitiesItf = NULL;
    /* Get the Audio IO DEVICE CAPABILITIES interface, which is also
       implicit */
    res = (*sl)->GetInterface(sl, SL_IID_AUDIOIODEVICECAPABILITIES,
            (void *)&AudioIODeviceCapabilitiesItf);
    // ANDROID: obtaining SL_IID_AUDIOIODEVICECAPABILITIES may fail
    if (AudioIODeviceCapabilitiesItf != NULL ) {
        numInputs = MAX_NUMBER_INPUT_DEVICES;
        res = (*AudioIODeviceCapabilitiesItf)->GetAvailableAudioInputs(
                AudioIODeviceCapabilitiesItf, &numInputs, InputDeviceIDs);
        CheckErr(res);
        /* Search for either earpiece microphone or headset microphone input
           device - with a preference for the latter */
        for (i = 0; i < numInputs; i++) {
            res = (*AudioIODeviceCapabilitiesItf)->QueryAudioInputCapabilities(
                    AudioIODeviceCapabilitiesItf, InputDeviceIDs[i], &AudioInputDescriptor);
            CheckErr(res);
            if ((AudioInputDescriptor.deviceConnection == SL_DEVCONNECTION_ATTACHED_WIRED)
                    && (AudioInputDescriptor.deviceScope == SL_DEVSCOPE_USER)
                    && (AudioInputDescriptor.deviceLocation == SL_DEVLOCATION_HEADSET)) {
                mic_deviceID = InputDeviceIDs[i];
                mic_available = SL_BOOLEAN_TRUE;
                break;
            }
            else if ((AudioInputDescriptor.deviceConnection == SL_DEVCONNECTION_INTEGRATED)
                    && (AudioInputDescriptor.deviceScope == SL_DEVSCOPE_USER)
                    && (AudioInputDescriptor.deviceLocation == SL_DEVLOCATION_HANDSET)) {
                mic_deviceID = InputDeviceIDs[i];
                mic_available = SL_BOOLEAN_TRUE;
                break;
            }
        }
    } else {
        mic_deviceID = SL_DEFAULTDEVICEID_AUDIOINPUT;
        mic_available = true;
    }

    /* If neither of the preferred input audio devices is available, no
       point in continuing */
    if (!mic_available) {
        /* Appropriate error message here */
        ALOGW("No microphone available");
        return;
    }

    /* Initialize arrays required[] and iidArray[] */
    for (i = 0; i < MAX_NUMBER_INTERFACES; i++) {
        required[i] = SL_BOOLEAN_FALSE;
        iidArray[i] = SL_IID_NULL;
    }

    // ANDROID: the following may fail for volume
    devicevolumeItf = NULL;
    /* Get the optional DEVICE VOLUME interface from the engine */
    res = (*sl)->GetInterface(sl, SL_IID_DEVICEVOLUME,
            (void *)&devicevolumeItf);

    /* Set recording volume of the microphone to -3 dB */
    if (devicevolumeItf != NULL) { // ANDROID: Volume may not be supported
        res = (*devicevolumeItf)->SetVolume(devicevolumeItf, mic_deviceID, -300);
        CheckErr(res);
    }

    /* Setup the data source structure */
    locator_mic.locatorType = SL_DATALOCATOR_IODEVICE;
    locator_mic.deviceType = SL_IODEVICE_AUDIOINPUT;
    locator_mic.deviceID = mic_deviceID;
    locator_mic.device= NULL;

    audioSource.pLocator = (void *)&locator_mic;
    audioSource.pFormat = NULL;

#if 0
    /* Setup the data sink structure */
    uri.locatorType = SL_DATALOCATOR_URI;
    uri.URI = (SLchar *) "file:///recordsample.wav";
    mime.formatType = SL_DATAFORMAT_MIME;
    mime.mimeType = (SLchar *) "audio/x-wav";
    mime.containerType = SL_CONTAINERTYPE_WAV;
    audioSink.pLocator = (void *)&uri;
    audioSink.pFormat = (void *)&mime;
#else
    // FIXME: Android requires SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE
    // because the recorder makes the distinction from SL_DATALOCATOR_BUFFERQUEUE
    // which the player does not.
    SLDataLocator_AndroidSimpleBufferQueue loc_bq = {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2
    };
    SLDataFormat_PCM format_pcm = {
            SL_DATAFORMAT_PCM, 1, SL_SAMPLINGRATE_16,
            SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_SPEAKER_FRONT_LEFT, SL_BYTEORDER_LITTLEENDIAN
    };
    audioSink = { &loc_bq, &format_pcm };
#endif

    /* Create audio recorder */
    res = (*EngineItf)->CreateAudioRecorder(EngineItf, &recorder,
            &audioSource, &audioSink, 0, iidArray, required);
    CheckErr(res);

    /* Realizing the recorder in synchronous mode. */
    res = (*recorder)->Realize(recorder, SL_BOOLEAN_FALSE);
    CheckErr(res);

    /* Get the RECORD interface - it is an implicit interface */
    res = (*recorder)->GetInterface(recorder, SL_IID_RECORD, (void *)&recordItf);
    CheckErr(res);

    // ANDROID: Should register SL_IID_ANDROIDSIMPLEBUFFERQUEUE interface for callback.
    // but does original SL_DATALOCATOR_BUFFERQUEUE variant work just as well ?

    /* Setup to receive position event callbacks */
    res = (*recordItf)->RegisterCallback(recordItf, RecordEventCallback, NULL);
    CheckErr(res);

    /* Set notifications to occur after every second - may be useful in
       updating a recording progress bar */
    res = (*recordItf)->SetPositionUpdatePeriod(recordItf, POSITION_UPDATE_PERIOD);
    CheckErr(res);
    res = (*recordItf)->SetCallbackEventsMask(recordItf, SL_RECORDEVENT_HEADATNEWPOS);
    CheckErr(res);

    /* Set the duration of the recording - 30 seconds (30,000
       milliseconds) */
    res = (*recordItf)->SetDurationLimit(recordItf, 30000);
    CheckErr(res);

    /* Record the audio */
    res = (*recordItf)->SetRecordState(recordItf, SL_RECORDSTATE_RECORDING);
    CheckErr(res);

    // ANDROID: BUG - we don't wait for anything to record!

    /* Destroy the recorder object */
    (*recorder)->Destroy(recorder);
}

extern "C" void Java_android_media_cts_AudioNativeTest_nativeAppendixBRecording(
        JNIEnv * /* env */, jclass /* clazz */)
{
    SLObjectItf engineObject = android::OpenSLEngine();
    LOG_ALWAYS_FATAL_IF(engineObject == NULL, "cannot open OpenSL ES engine");

    TestAudioRecording(engineObject);
    android::CloseSLEngine(engineObject);
}
