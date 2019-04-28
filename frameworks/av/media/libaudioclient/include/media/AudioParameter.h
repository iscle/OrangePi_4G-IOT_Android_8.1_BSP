/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2008-2011 The Android Open Source Project
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

#ifndef ANDROID_AUDIOPARAMETER_H_
#define ANDROID_AUDIOPARAMETER_H_

#include <utils/Errors.h>
#include <utils/KeyedVector.h>
#include <utils/String8.h>

namespace android {

class AudioParameter {

public:
    AudioParameter() {}
    AudioParameter(const String8& keyValuePairs);
    virtual ~AudioParameter();

    // reserved parameter keys for changing standard parameters with setParameters() function.
    // Using these keys is mandatory for AudioFlinger to properly monitor audio output/input
    // configuration changes and act accordingly.
    //  keyRouting: to change audio routing, value is an int in audio_devices_t
    //  keySamplingRate: to change sampling rate routing, value is an int
    //  keyFormat: to change audio format, value is an int in audio_format_t
    //  keyChannels: to change audio channel configuration, value is an int in audio_channels_t
    //  keyFrameCount: to change audio output frame count, value is an int
    //  keyInputSource: to change audio input source, value is an int in audio_source_t
    //     (defined in media/mediarecorder.h)
    //  keyScreenState: either "on" or "off"
    static const char * const keyRouting;
    static const char * const keySamplingRate;
    static const char * const keyFormat;
    static const char * const keyChannels;
    static const char * const keyFrameCount;
    static const char * const keyInputSource;
    static const char * const keyScreenState;

    //  keyBtNrec: BT SCO Noise Reduction + Echo Cancellation parameters
    //  keyHwAvSync: get HW synchronization source identifier from a device
    //  keyMonoOutput: Enable mono audio playback
    //  keyStreamHwAvSync: set HW synchronization source identifier on a stream
    static const char * const keyBtNrec;
    static const char * const keyHwAvSync;
    static const char * const keyMonoOutput;
    static const char * const keyStreamHwAvSync;

    //  keyStreamConnect / Disconnect: value is an int in audio_devices_t
    static const char * const keyStreamConnect;
    static const char * const keyStreamDisconnect;

    // For querying stream capabilities. All the returned values are lists.
    //   keyStreamSupportedFormats: audio_format_t
    //   keyStreamSupportedChannels: audio_channel_mask_t
    //   keyStreamSupportedSamplingRates: sampling rate values
    static const char * const keyStreamSupportedFormats;
    static const char * const keyStreamSupportedChannels;
    static const char * const keyStreamSupportedSamplingRates;

    static const char * const valueOn;
    static const char * const valueOff;

    static const char * const valueListSeparator;

    String8 toString() const { return toStringImpl(true); }
    String8 keysToString() const { return toStringImpl(false); }

    status_t add(const String8& key, const String8& value);
    status_t addInt(const String8& key, const int value);
    status_t addKey(const String8& key);
    status_t addFloat(const String8& key, const float value);

    status_t remove(const String8& key);

    status_t get(const String8& key, String8& value) const;
    status_t getInt(const String8& key, int& value) const;
    status_t getFloat(const String8& key, float& value) const;
    status_t getAt(size_t index, String8& key) const;
    status_t getAt(size_t index, String8& key, String8& value) const;

    size_t size() const { return mParameters.size(); }

private:
    String8 mKeyValuePairs;
    KeyedVector <String8, String8> mParameters;

    String8 toStringImpl(bool useValues) const;

// <MTK_AUDIO
public:
    // For audio dump debug
    static const char * const keyAudioDumpMixer;
    static const char * const keyAudioDumpTrack;
    static const char * const keyAudioDumpOffload;
    static const char * const keyAudioDumpResampler;
    static const char * const keyAudioDumpMixerEnd;
    static const char * const keyAudioDumpRecord;
    static const char * const keyAudioDumpEffect;
    static const char * const keyAudioDumpDrc;
    static const char * const keyAudioDumpLog;

    enum PROP_AUDIO_DUMP {
        PROP_AUDIO_DUMP_MIXER = 0,
        PROP_AUDIO_DUMP_TRACK,
        PROP_AUDIO_DUMP_OFFLOAD,
        PROP_AUDIO_DUMP_RESAMPLER,
        PROP_AUDIO_DUMP_MIXEREND,
        PROP_AUDIO_DUMP_RECORD,
        PROP_AUDIO_DUMP_EFFECT,
        PROP_AUDIO_DUMP_DRC,
        PROP_AUDIO_DUMP_LOG,
        PROP_AUDIO_DUMP_MAXNUM
    };

    static const char *audioDumpPropertyStr[];
    static const char * const af_track_pcm;
    static const char * const af_mixer_pcm;
    static const char * const af_mixer_write_pcm;
    static const char * const af_mixer_end_pcm;
    static const char * const af_offload_write_raw;
    static const char * const af_record_pcm;
    static const char * const af_effect_pcm;
    static const char * const af_mixer_drc_pcm_before;
    static const char * const af_mixer_drc_pcm_after;
    static const char * const af_resampler_in_pcm;
    static const char * const af_resampler_out_pcm;
// MTK_AUDIO>
};

};  // namespace android

#endif  /*ANDROID_AUDIOPARAMETER_H_*/
