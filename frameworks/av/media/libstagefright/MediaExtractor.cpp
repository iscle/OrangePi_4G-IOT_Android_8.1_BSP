/*
 * Copyright (C) 2009 The Android Open Source Project
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
#define LOG_TAG "MediaExtractor"
#include <utils/Log.h>
#include <inttypes.h>
#include <pwd.h>

#include "include/AMRExtractor.h"
#include "include/MP3Extractor.h"
#include "include/MPEG4Extractor.h"
#include "include/WAVExtractor.h"
#include "include/OggExtractor.h"
#include "include/MPEG2PSExtractor.h"
#include "include/MPEG2TSExtractor.h"
#include "include/FLACExtractor.h"
#include "include/AACExtractor.h"
#include "include/MidiExtractor.h"

#include "matroska/MatroskaExtractor.h"

#include <binder/IServiceManager.h>
#include <binder/MemoryDealer.h>

#include <media/MediaAnalyticsItem.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaExtractor.h>
#include <media/stagefright/MetaData.h>
#include <media/IMediaExtractorService.h>
#include <cutils/properties.h>
#include <utils/String8.h>
#include <private/android_filesystem_config.h>
#include <stagefright/AVStageExtensions.h>

// still doing some on/off toggling here.
#define MEDIA_LOG       1


namespace android {

// key for media statistics
static const char *kKeyExtractor = "extractor";
// attrs for media statistics
static const char *kExtractorMime = "android.media.mediaextractor.mime";
static const char *kExtractorTracks = "android.media.mediaextractor.ntrk";
static const char *kExtractorFormat = "android.media.mediaextractor.fmt";

MediaExtractor::MediaExtractor() {
    if (!LOG_NDEBUG) {
        uid_t uid = getuid();
        struct passwd *pw = getpwuid(uid);
        ALOGI("extractor created in uid: %d (%s)", getuid(), pw->pw_name);
    }

    mAnalyticsItem = NULL;
    if (MEDIA_LOG) {
        mAnalyticsItem = new MediaAnalyticsItem(kKeyExtractor);
        (void) mAnalyticsItem->generateSessionID();
    }
}

MediaExtractor::~MediaExtractor() {

    // log the current record, provided it has some information worth recording
    if (MEDIA_LOG) {
        if (mAnalyticsItem != NULL) {
            if (mAnalyticsItem->count() > 0) {
                mAnalyticsItem->setFinalized(true);
                mAnalyticsItem->selfrecord();
            }
        }
    }
    if (mAnalyticsItem != NULL) {
        delete mAnalyticsItem;
        mAnalyticsItem = NULL;
    }
}

sp<MetaData> MediaExtractor::getMetaData() {
    return new MetaData;
}

status_t MediaExtractor::getMetrics(Parcel *reply) {

    if (mAnalyticsItem == NULL || reply == NULL) {
        return UNKNOWN_ERROR;
    }

    populateMetrics();
    mAnalyticsItem->writeToParcel(reply);

    return OK;
}

void MediaExtractor::populateMetrics() {
    ALOGV("MediaExtractor::populateMetrics");
    // normally overridden in subclasses
}

uint32_t MediaExtractor::flags() const {
    return CAN_SEEK_BACKWARD | CAN_SEEK_FORWARD | CAN_PAUSE | CAN_SEEK;
}

// static
sp<IMediaExtractor> MediaExtractor::Create(
        const sp<DataSource> &source, const char *mime) {
    ALOGV("MediaExtractor::Create %s", mime);

    if (!property_get_bool("media.stagefright.extractremote", true)) {
        // local extractor
        ALOGW("creating media extractor in calling process");
        return CreateFromService(source, mime);
    } else {
// Do DrmInitialization before creating mediaextractor from MediaExtractorService,
// otherwise current Calling Pid will change to Media Extractor Pid.
// For some DRM scanerio we need AP's PID to pass some DRM plugin's permission check.
#ifdef MTK_DRM_APP
        source->DrmInitialization(nullptr /* mime */);
#endif
        // remote extractor
        ALOGV("get service manager");
        sp<IBinder> binder = defaultServiceManager()->getService(String16("media.extractor"));

        if (binder != 0) {
            sp<IMediaExtractorService> mediaExService(interface_cast<IMediaExtractorService>(binder));
            sp<IMediaExtractor> ex = mediaExService->makeExtractor(source->asIDataSource(), mime);
            return ex;
        } else {
            ALOGE("extractor service not running");
            return NULL;
        }
    }
    return NULL;
}

sp<MediaExtractor> MediaExtractor::CreateFromService(
        const sp<DataSource> &source, const char *mime) {

    ALOGV("MediaExtractor::CreateFromService %s", mime);
    RegisterDefaultSniffers();

    // initialize source decryption if needed
    source->DrmInitialization(nullptr /* mime */);

    sp<AMessage> meta;

    String8 tmp;
    if (mime == NULL) {
        float confidence;
        if (!sniff(source, &tmp, &confidence, &meta)) {
            ALOGW("FAILED to autodetect media content.");

            return NULL;
        }

        mime = tmp.string();
        ALOGD("Autodetected media content as '%s' with confidence %.2f",
             mime, confidence);
    }

    MediaExtractor *ret = NULL;
    if ((ret = AVStageFactory::get()->createExtractorExt(source, mime, meta)) != NULL) {
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_MPEG4)
            || !strcasecmp(mime, "audio/mp4")) {
        ret = new MPEG4Extractor(source);
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MPEG)) {
        ret = new MP3Extractor(source, meta);
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AMR_NB)
            || !strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AMR_WB)) {
        ret = new AMRExtractor(source);
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_FLAC)) {
        ret = new FLACExtractor(source);
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_WAV)) {
        ret = new WAVExtractor(source);
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_OGG)) {
        ret = new OggExtractor(source);
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_MATROSKA)) {
        ret = new MatroskaExtractor(source);
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_MPEG2TS)) {
        ret = new MPEG2TSExtractor(source);
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AAC_ADTS)) {
        ret = new AACExtractor(source, meta);
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_CONTAINER_MPEG2PS)) {
        ret = new MPEG2PSExtractor(source);
    } else if (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MIDI)) {
        ret = new MidiExtractor(source);
    }

    if (ret != NULL) {
       // track the container format (mpeg, aac, wvm, etc)
       if (MEDIA_LOG) {
          if (ret->mAnalyticsItem != NULL) {
              size_t ntracks = ret->countTracks();
              ret->mAnalyticsItem->setCString(kExtractorFormat,  ret->name());
              // tracks (size_t)
              ret->mAnalyticsItem->setInt32(kExtractorTracks,  ntracks);
              // metadata
              sp<MetaData> pMetaData = ret->getMetaData();
              if (pMetaData != NULL) {
                String8 xx = pMetaData->toString();
                // 'titl' -- but this verges into PII
                // 'mime'
                const char *mime = NULL;
                if (pMetaData->findCString(kKeyMIMEType, &mime)) {
                    ret->mAnalyticsItem->setCString(kExtractorMime,  mime);
                }
                // what else is interesting and not already available?
              }
	  }
       }
    }

    return ret;
}

Mutex MediaExtractor::gSnifferMutex;
List<MediaExtractor::SnifferFunc> MediaExtractor::gSniffers;
bool MediaExtractor::gSniffersRegistered = false;

// static
bool MediaExtractor::sniff(
        const sp<DataSource> &source, String8 *mimeType, float *confidence, sp<AMessage> *meta) {
    *mimeType = "";
    *confidence = 0.0f;
    meta->clear();

    {
        Mutex::Autolock autoLock(gSnifferMutex);
        if (!gSniffersRegistered) {
            return false;
        }
    }

    for (List<SnifferFunc>::iterator it = gSniffers.begin();
         it != gSniffers.end(); ++it) {
        String8 newMimeType;
        float newConfidence;
        sp<AMessage> newMeta;
        if ((*it)(source, &newMimeType, &newConfidence, &newMeta)) {
            if (newConfidence > *confidence) {
                *mimeType = newMimeType;
                *confidence = newConfidence;
                *meta = newMeta;
            }
        }
    }

    return *confidence > 0.0;
}

// static
void MediaExtractor::RegisterSniffer_l(SnifferFunc func) {
    for (List<SnifferFunc>::iterator it = gSniffers.begin();
         it != gSniffers.end(); ++it) {
        if (*it == func) {
            return;
        }
    }

    gSniffers.push_back(func);
}

// static
void MediaExtractor::RegisterDefaultSniffers() {
    Mutex::Autolock autoLock(gSnifferMutex);
    if (gSniffersRegistered) {
        return;
    }

    RegisterSniffer_l(SniffMPEG4);
    RegisterSniffer_l(SniffMatroska);
    RegisterSniffer_l(SniffOgg);
    RegisterSniffer_l(SniffWAV);
    RegisterSniffer_l(SniffFLAC);
    RegisterSniffer_l(SniffAMR);
    RegisterSniffer_l(SniffMPEG2TS);
    RegisterSniffer_l(SniffMP3);
    RegisterSniffer_l(SniffAAC);
    RegisterSniffer_l(SniffMPEG2PS);
    RegisterSniffer_l(SniffMidi);
    AVStageUtils::get()->RegisterSniffer_ext();

    gSniffersRegistered = true;
}


}  // namespace android
