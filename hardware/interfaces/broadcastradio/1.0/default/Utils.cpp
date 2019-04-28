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
#define LOG_TAG "BroadcastRadioHalUtils"
//#define LOG_NDEBUG 0

#include <log/log.h>
#include <utils/misc.h>
#include <system/radio_metadata.h>

#include "Utils.h"

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_0 {
namespace implementation {

const char *Utils::sClassModuleNames[] = {
    RADIO_HARDWARE_MODULE_ID_FM, /* corresponds to RADIO_CLASS_AM_FM */
    RADIO_HARDWARE_MODULE_ID_SAT,  /* corresponds to RADIO_CLASS_SAT */
    RADIO_HARDWARE_MODULE_ID_DT,   /* corresponds to RADIO_CLASS_DT */
};

// make sure HIDL enum values are aligned with legacy values
static_assert(RADIO_CLASS_AM_FM == static_cast<int>(Class::AM_FM),
              "AM/FM class mismatch with legacy");
static_assert(RADIO_CLASS_SAT == static_cast<int>(Class::SAT),
              "SAT class mismatch with legacy");
static_assert(RADIO_CLASS_DT == static_cast<int>(Class::DT),
              "DT class mismatch with legacy");

static_assert(RADIO_BAND_AM == static_cast<int>(Band::AM),
              "AM band mismatch with legacy");
static_assert(RADIO_BAND_FM == static_cast<int>(Band::FM),
              "FM band mismatch with legacy");
static_assert(RADIO_BAND_AM_HD == static_cast<int>(Band::AM_HD),
              "AM HD band mismatch with legacy");
static_assert(RADIO_BAND_FM_HD == static_cast<int>(Band::FM_HD),
              "FM HD band mismatch with legacy");

static_assert(RADIO_RDS_NONE == static_cast<int>(Rds::NONE),
              "RDS NONE mismatch with legacy");
static_assert(RADIO_RDS_WORLD == static_cast<int>(Rds::WORLD),
              "RDS WORLD mismatch with legacy");
static_assert(RADIO_RDS_US == static_cast<int>(Rds::US),
              "RDS US mismatch with legacy");

static_assert(RADIO_DEEMPHASIS_50 == static_cast<int>(Deemphasis::D50),
              "De-emphasis 50 mismatch with legacy");
static_assert(RADIO_DEEMPHASIS_75 == static_cast<int>(Deemphasis::D75),
              "De-emphasis 75 mismatch with legacy");

static_assert(RADIO_DIRECTION_UP == static_cast<int>(Direction::UP),
              "Direction Up mismatch with legacy");
static_assert(RADIO_DIRECTION_DOWN == static_cast<int>(Direction::DOWN),
              "Direction Up mismatch with legacy");

static_assert(RADIO_METADATA_TYPE_INVALID == static_cast<int>(MetadataType::INVALID),
              "Metadata type INVALID mismatch with legacy");
static_assert(RADIO_METADATA_TYPE_INT == static_cast<int>(MetadataType::INT),
              "Metadata type INT mismatch with legacy");
static_assert(RADIO_METADATA_TYPE_TEXT == static_cast<int>(MetadataType::TEXT),
              "Metadata type TEXT mismatch with legacy");
static_assert(RADIO_METADATA_TYPE_RAW == static_cast<int>(MetadataType::RAW),
              "Metadata type RAW mismatch with legacy");
static_assert(RADIO_METADATA_TYPE_CLOCK == static_cast<int>(MetadataType::CLOCK),
              "Metadata type CLOCK mismatch with legacy");

static_assert(RADIO_METADATA_KEY_INVALID == static_cast<int>(MetadataKey::INVALID),
              "Metadata key INVALID mismatch with legacy");
static_assert(RADIO_METADATA_KEY_RDS_PI == static_cast<int>(MetadataKey::RDS_PI),
              "Metadata key RDS_PI mismatch with legacy");
static_assert(RADIO_METADATA_KEY_RDS_PS == static_cast<int>(MetadataKey::RDS_PS),
              "Metadata key RDS_PS mismatch with legacy");
static_assert(RADIO_METADATA_KEY_RDS_PTY == static_cast<int>(MetadataKey::RDS_PTY),
              "Metadata key RDS_PTY mismatch with legacy");
static_assert(RADIO_METADATA_KEY_RBDS_PTY == static_cast<int>(MetadataKey::RBDS_PTY),
              "Metadata key RBDS_PTY mismatch with legacy");
static_assert(RADIO_METADATA_KEY_RDS_RT == static_cast<int>(MetadataKey::RDS_RT),
              "Metadata key RDS_RT mismatch with legacy");
static_assert(RADIO_METADATA_KEY_TITLE == static_cast<int>(MetadataKey::TITLE),
              "Metadata key TITLE mismatch with legacy");
static_assert(RADIO_METADATA_KEY_ARTIST == static_cast<int>(MetadataKey::ARTIST),
              "Metadata key ARTIST mismatch with legacy");
static_assert(RADIO_METADATA_KEY_ALBUM == static_cast<int>(MetadataKey::ALBUM),
              "Metadata key ALBUM mismatch with legacy");
static_assert(RADIO_METADATA_KEY_GENRE == static_cast<int>(MetadataKey::GENRE),
              "Metadata key GENRE mismatch with legacy");
static_assert(RADIO_METADATA_KEY_ICON == static_cast<int>(MetadataKey::ICON),
              "Metadata key ICON mismatch with legacy");
static_assert(RADIO_METADATA_KEY_ART == static_cast<int>(MetadataKey::ART),
              "Metadata key ART mismatch with legacy");
static_assert(RADIO_METADATA_KEY_CLOCK == static_cast<int>(MetadataKey::CLOCK),
              "Metadata key CLOCK mismatch with legacy");


//static
const char * Utils::getClassString(Class ClassId)
{
    int id = static_cast<int>(ClassId);

    if ((id < 0) ||
            (id >= NELEM(sClassModuleNames))) {
        ALOGE("invalid class ID %d", id);
        return NULL;
    }
    return sClassModuleNames[id];
}

//static
Result Utils::convertHalResult(int rc)
{
    switch (rc) {
        case 0:
            return Result::OK;
        case -EINVAL:
            return Result::INVALID_ARGUMENTS;
        case -ENOSYS:
            return Result::INVALID_STATE;
        case -ETIMEDOUT:
            return Result::TIMEOUT;
        case -ENODEV:
        default:
            return Result::NOT_INITIALIZED;
    }
}

//static
void Utils::convertBandConfigFromHal(
        BandConfig *config,
        const radio_hal_band_config_t *halConfig)
{

    config->type = static_cast<Band>(halConfig->type);
    config->antennaConnected = halConfig->antenna_connected;
    config->lowerLimit = halConfig->lower_limit;
    config->upperLimit = halConfig->upper_limit;
    config->spacings.setToExternal(const_cast<unsigned int *>(&halConfig->spacings[0]),
                                       halConfig->num_spacings * sizeof(uint32_t));
    // FIXME: transfer buffer ownership. should have a method for that in hidl_vec
    config->spacings.resize(halConfig->num_spacings);

    if (config->type == Band::FM) {
        config->ext.fm.deemphasis = static_cast<Deemphasis>(halConfig->fm.deemphasis);
        config->ext.fm.stereo = halConfig->fm.stereo;
        config->ext.fm.rds = static_cast<Rds>(halConfig->fm.rds);
        config->ext.fm.ta = halConfig->fm.ta;
        config->ext.fm.af = halConfig->fm.af;
        config->ext.fm.ea = halConfig->fm.ea;
    } else {
        config->ext.am.stereo = halConfig->am.stereo;
    }
}

//static
void Utils::convertPropertiesFromHal(
        Properties *properties,
        const radio_hal_properties_t *halProperties)
{
    properties->classId = static_cast<Class>(halProperties->class_id);
    properties->implementor.setToExternal(halProperties->implementor, strlen(halProperties->implementor));
    properties->product.setToExternal(halProperties->product, strlen(halProperties->product));
    properties->version.setToExternal(halProperties->version, strlen(halProperties->version));
    properties->serial.setToExternal(halProperties->serial, strlen(halProperties->serial));
    properties->numTuners = halProperties->num_tuners;
    properties->numAudioSources = halProperties->num_audio_sources;
    properties->supportsCapture = halProperties->supports_capture;

    BandConfig *bands =
            new BandConfig[halProperties->num_bands];
    for (size_t i = 0; i < halProperties->num_bands; i++) {
        convertBandConfigFromHal(&bands[i], &halProperties->bands[i]);
    }
    properties->bands.setToExternal(bands, halProperties->num_bands);
    // FIXME: transfer buffer ownership. should have a method for that in hidl_vec
    properties->bands.resize(halProperties->num_bands);
    delete[] bands;
}

//static
void Utils::convertBandConfigToHal(
        radio_hal_band_config_t *halConfig,
        const BandConfig *config)
{

    halConfig->type = static_cast<radio_band_t>(config->type);
    halConfig->antenna_connected = config->antennaConnected;
    halConfig->lower_limit = config->lowerLimit;
    halConfig->upper_limit = config->upperLimit;
    halConfig->num_spacings = config->spacings.size();
    if (halConfig->num_spacings > RADIO_NUM_SPACINGS_MAX) {
        halConfig->num_spacings = RADIO_NUM_SPACINGS_MAX;
    }
    memcpy(halConfig->spacings, config->spacings.data(),
           sizeof(uint32_t) * halConfig->num_spacings);

    if (config->type == Band::FM) {
        halConfig->fm.deemphasis = static_cast<radio_deemphasis_t>(config->ext.fm.deemphasis);
        halConfig->fm.stereo = config->ext.fm.stereo;
        halConfig->fm.rds = static_cast<radio_rds_t>(config->ext.fm.rds);
        halConfig->fm.ta = config->ext.fm.ta;
        halConfig->fm.af = config->ext.fm.af;
        halConfig->fm.ea = config->ext.fm.ea;
    } else {
        halConfig->am.stereo = config->ext.am.stereo;
    }
}


//static
void Utils::convertProgramInfoFromHal(ProgramInfo *info,
                                      radio_program_info_t *halInfo)
{
    info->channel = halInfo->channel;
    info->subChannel = halInfo->sub_channel;
    info->tuned = halInfo->tuned;
    info->stereo = halInfo->stereo;
    info->digital = halInfo->digital;
    info->signalStrength = halInfo->signal_strength;
    convertMetaDataFromHal(info->metadata, halInfo->metadata);
}

//static
int Utils::convertMetaDataFromHal(hidl_vec<MetaData>& metadata,
                                   radio_metadata_t *halMetadata)
{
    if (halMetadata == NULL) {
        ALOGE("Invalid argument: halMetadata is NULL");
        return 0;
    }

    int count = radio_metadata_get_count(halMetadata);
    if (count <= 0) {
        return count;
    }
    MetaData *newMetadata =
            new MetaData[count];
    int outCount = 0;
    for (int i = 0; i < count; i++) {
        radio_metadata_key_t key;
        radio_metadata_type_t type;
        void *value;
        size_t size;
        if (radio_metadata_get_at_index(halMetadata, i , &key, &type, &value, &size) != 0 ||
                size == 0) {
            continue;
        }
        switch (type) {
            case RADIO_METADATA_TYPE_INT: {
                newMetadata[outCount].intValue = *(static_cast<int32_t *>(value));
            } break;
            case RADIO_METADATA_TYPE_TEXT: {
                newMetadata[outCount].stringValue = static_cast<char *>(value);
            } break;
            case RADIO_METADATA_TYPE_RAW: {
                newMetadata[outCount].rawValue.setToExternal(static_cast<uint8_t *>(value), size);
                // FIXME: transfer buffer ownership. should have a method for that in hidl_vec
                newMetadata[outCount].rawValue.resize(size);
            } break;
            case RADIO_METADATA_TYPE_CLOCK: {
                  radio_metadata_clock_t *clock = static_cast<radio_metadata_clock_t *>(value);
                  newMetadata[outCount].clockValue.utcSecondsSinceEpoch =
                          clock->utc_seconds_since_epoch;
                  newMetadata[outCount].clockValue.timezoneOffsetInMinutes =
                          clock->timezone_offset_in_minutes;
            } break;
        }
        newMetadata[outCount].type = static_cast<MetadataType>(type);
        newMetadata[outCount].key = static_cast<MetadataKey>(key);
        outCount++;
    }
    metadata.setToExternal(newMetadata, outCount);
    // FIXME: transfer buffer ownership. should have a method for that in hidl_vec
    metadata.resize(outCount);
    return outCount;
}

} // namespace implementation
}  // namespace V1_0
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android
