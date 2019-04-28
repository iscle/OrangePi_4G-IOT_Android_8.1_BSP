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

#ifndef ANDROID_TYPE_CONVERTER_H_
#define ANDROID_TYPE_CONVERTER_H_

#include <string>
#include <string.h>

#include <system/audio.h>
#include <utils/Log.h>
#include <utils/Vector.h>
#include <utils/SortedVector.h>

#include <media/AudioParameter.h>
#include "convert.h"

namespace android {

struct SampleRateTraits
{
    typedef uint32_t Type;
    typedef SortedVector<Type> Collection;
};
struct DeviceTraits
{
    typedef audio_devices_t Type;
    typedef Vector<Type> Collection;
};
struct OutputDeviceTraits : public DeviceTraits {};
struct InputDeviceTraits : public DeviceTraits {};
struct OutputFlagTraits
{
    typedef audio_output_flags_t Type;
    typedef Vector<Type> Collection;
};
struct InputFlagTraits
{
    typedef audio_input_flags_t Type;
    typedef Vector<Type> Collection;
};
struct FormatTraits
{
    typedef audio_format_t Type;
    typedef Vector<Type> Collection;
};
struct ChannelTraits
{
    typedef audio_channel_mask_t Type;
    typedef SortedVector<Type> Collection;
};
struct OutputChannelTraits : public ChannelTraits {};
struct InputChannelTraits : public ChannelTraits {};
struct ChannelIndexTraits : public ChannelTraits {};
struct GainModeTraits
{
    typedef audio_gain_mode_t Type;
    typedef Vector<Type> Collection;
};
struct StreamTraits
{
    typedef audio_stream_type_t Type;
    typedef Vector<Type> Collection;
};
struct AudioModeTraits
{
    typedef audio_mode_t Type;
    typedef Vector<Type> Collection;
};
struct UsageTraits
{
    typedef audio_usage_t Type;
    typedef Vector<Type> Collection;
};
struct SourceTraits
{
    typedef audio_source_t Type;
    typedef Vector<Type> Collection;
};
template <typename T>
struct DefaultTraits
{
    typedef T Type;
    typedef Vector<Type> Collection;
};

template <class Traits>
static void collectionFromString(const std::string &str, typename Traits::Collection &collection,
                                 const char *del = AudioParameter::valueListSeparator)
{
    char *literal = strdup(str.c_str());
    for (const char *cstr = strtok(literal, del); cstr != NULL; cstr = strtok(NULL, del)) {
        typename Traits::Type value;
        if (utilities::convertTo<std::string, typename Traits::Type >(cstr, value)) {
            collection.add(value);
        }
    }
    free(literal);
}

template <class Traits>
class TypeConverter
{
public:
    static bool toString(const typename Traits::Type &value, std::string &str);

    static bool fromString(const std::string &str, typename Traits::Type &result);

    static void collectionFromString(const std::string &str,
                                     typename Traits::Collection &collection,
                                     const char *del = AudioParameter::valueListSeparator);

    static uint32_t maskFromString(
            const std::string &str, const char *del = AudioParameter::valueListSeparator);

    static void maskToString(
            uint32_t mask, std::string &str, const char *del = AudioParameter::valueListSeparator);

protected:
    struct Table {
        const char *literal;
        typename Traits::Type value;
    };

    static const Table mTable[];
};

template <class Traits>
inline bool TypeConverter<Traits>::toString(const typename Traits::Type &value, std::string &str)
{
    for (size_t i = 0; mTable[i].literal; i++) {
        if (mTable[i].value == value) {
            str = mTable[i].literal;
            return true;
        }
    }
    char result[64];
    snprintf(result, sizeof(result), "Unknown enum value %d", value);
    str = result;
    return false;
}

template <class Traits>
inline bool TypeConverter<Traits>::fromString(const std::string &str, typename Traits::Type &result)
{
    for (size_t i = 0; mTable[i].literal; i++) {
        if (strcmp(mTable[i].literal, str.c_str()) == 0) {
            ALOGV("stringToEnum() found %s", mTable[i].literal);
            result = mTable[i].value;
            return true;
        }
    }
    return false;
}

template <class Traits>
inline void TypeConverter<Traits>::collectionFromString(const std::string &str,
        typename Traits::Collection &collection,
        const char *del)
{
    char *literal = strdup(str.c_str());

    for (const char *cstr = strtok(literal, del); cstr != NULL; cstr = strtok(NULL, del)) {
        typename Traits::Type value;
        if (fromString(cstr, value)) {
            collection.add(value);
        }
    }
    free(literal);
}

template <class Traits>
inline uint32_t TypeConverter<Traits>::maskFromString(const std::string &str, const char *del)
{
    char *literal = strdup(str.c_str());
    uint32_t value = 0;
    for (const char *cstr = strtok(literal, del); cstr != NULL; cstr = strtok(NULL, del)) {
        typename Traits::Type type;
        if (fromString(cstr, type)) {
            value |= static_cast<uint32_t>(type);
        }
    }
    free(literal);
    return value;
}

template <class Traits>
inline void TypeConverter<Traits>::maskToString(uint32_t mask, std::string &str, const char *del)
{
    if (mask != 0) {
        bool first_flag = true;
        for (size_t i = 0; mTable[i].literal; i++) {
            uint32_t value = static_cast<uint32_t>(mTable[i].value);
            if (mTable[i].value != 0 && ((mask & value) == value)) {
                if (!first_flag) str += del;
                first_flag = false;
                str += mTable[i].literal;
            }
        }
    } else {
        toString(static_cast<typename Traits::Type>(0), str);
    }
}

typedef TypeConverter<OutputDeviceTraits> OutputDeviceConverter;
typedef TypeConverter<InputDeviceTraits> InputDeviceConverter;
typedef TypeConverter<OutputFlagTraits> OutputFlagConverter;
typedef TypeConverter<InputFlagTraits> InputFlagConverter;
typedef TypeConverter<FormatTraits> FormatConverter;
typedef TypeConverter<OutputChannelTraits> OutputChannelConverter;
typedef TypeConverter<InputChannelTraits> InputChannelConverter;
typedef TypeConverter<ChannelIndexTraits> ChannelIndexConverter;
typedef TypeConverter<GainModeTraits> GainModeConverter;
typedef TypeConverter<StreamTraits> StreamTypeConverter;
typedef TypeConverter<AudioModeTraits> AudioModeConverter;
typedef TypeConverter<UsageTraits> UsageTypeConverter;
typedef TypeConverter<SourceTraits> SourceTypeConverter;

template<> const OutputDeviceConverter::Table OutputDeviceConverter::mTable[];
template<> const InputDeviceConverter::Table InputDeviceConverter::mTable[];
template<> const OutputFlagConverter::Table OutputFlagConverter::mTable[];
template<> const InputFlagConverter::Table InputFlagConverter::mTable[];
template<> const FormatConverter::Table FormatConverter::mTable[];
template<> const OutputChannelConverter::Table OutputChannelConverter::mTable[];
template<> const InputChannelConverter::Table InputChannelConverter::mTable[];
template<> const ChannelIndexConverter::Table ChannelIndexConverter::mTable[];
template<> const GainModeConverter::Table GainModeConverter::mTable[];
template<> const StreamTypeConverter::Table StreamTypeConverter::mTable[];
template<> const AudioModeConverter::Table AudioModeConverter::mTable[];
template<> const UsageTypeConverter::Table UsageTypeConverter::mTable[];
template<> const SourceTypeConverter::Table SourceTypeConverter::mTable[];

bool deviceFromString(const std::string& literalDevice, audio_devices_t& device);

bool deviceToString(audio_devices_t device, std::string& literalDevice);

SampleRateTraits::Collection samplingRatesFromString(
        const std::string &samplingRates, const char *del = AudioParameter::valueListSeparator);

FormatTraits::Collection formatsFromString(
        const std::string &formats, const char *del = AudioParameter::valueListSeparator);

audio_format_t formatFromString(
        const std::string &literalFormat, audio_format_t defaultFormat = AUDIO_FORMAT_DEFAULT);

audio_channel_mask_t channelMaskFromString(const std::string &literalChannels);

ChannelTraits::Collection channelMasksFromString(
        const std::string &channels, const char *del = AudioParameter::valueListSeparator);

InputChannelTraits::Collection inputChannelMasksFromString(
        const std::string &inChannels, const char *del = AudioParameter::valueListSeparator);

OutputChannelTraits::Collection outputChannelMasksFromString(
        const std::string &outChannels, const char *del = AudioParameter::valueListSeparator);

}; // namespace android

#endif  /*ANDROID_TYPE_CONVERTER_H_*/
