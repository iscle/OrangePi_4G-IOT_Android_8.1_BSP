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

#ifndef MEDIA_EXTRACTOR_H_

#define MEDIA_EXTRACTOR_H_

#include <media/IMediaExtractor.h>
#include <media/IMediaSource.h>
#include <media/MediaAnalyticsItem.h>

namespace android {
class DataSource;
struct MediaSource;
class MetaData;

class MediaExtractor : public BnMediaExtractor {
public:
    static sp<IMediaExtractor> Create(
            const sp<DataSource> &source, const char *mime = NULL);
    static sp<MediaExtractor> CreateFromService(
            const sp<DataSource> &source, const char *mime = NULL);

    virtual size_t countTracks() = 0;
    virtual sp<IMediaSource> getTrack(size_t index) = 0;

    enum GetTrackMetaDataFlags {
        kIncludeExtensiveMetaData = 1,
        //mtkadd+
        kIncludeMp3LowPowerInfo
    };
    virtual sp<MetaData> getTrackMetaData(
            size_t index, uint32_t flags = 0) = 0;

    // Return container specific meta-data. The default implementation
    // returns an empty metadata object.
    virtual sp<MetaData> getMetaData();

    status_t getMetrics(Parcel *reply);

    enum Flags {
        CAN_SEEK_BACKWARD  = 1,  // the "seek 10secs back button"
        CAN_SEEK_FORWARD   = 2,  // the "seek 10secs forward button"
        CAN_PAUSE          = 4,
        CAN_SEEK           = 8,  // the "seek bar"
    };

    // If subclasses do _not_ override this, the default is
    // CAN_SEEK_BACKWARD | CAN_SEEK_FORWARD | CAN_SEEK | CAN_PAUSE
    virtual uint32_t flags() const;

    // for DRM
    virtual char* getDrmTrackInfo(size_t /*trackID*/, int * /*len*/) {
        return NULL;
    }
    virtual void setUID(uid_t /*uid*/) {
    }
    virtual status_t setMediaCas(const HInterfaceToken &/*casToken*/) override {
        return INVALID_OPERATION;
    }

    virtual const char * name() { return "<unspecified>"; }

    virtual void release() {}

protected:
    MediaExtractor();
    virtual ~MediaExtractor();

    MediaAnalyticsItem *mAnalyticsItem;

    virtual void populateMetrics();

private:

    typedef bool (*SnifferFunc)(
            const sp<DataSource> &source, String8 *mimeType,
            float *confidence, sp<AMessage> *meta);

    static Mutex gSnifferMutex;
    static List<SnifferFunc> gSniffers;
    static bool gSniffersRegistered;

    static bool sniff(const sp<DataSource> &source,
            String8 *mimeType, float *confidence, sp<AMessage> *meta);

    static void RegisterDefaultSniffers();

    MediaExtractor(const MediaExtractor &);
    MediaExtractor &operator=(const MediaExtractor &);

public:
    // mtk: change google's RegisterSniffer_l from private to public
    // The sniffer can optionally fill in "meta" with an AMessage containing
    // a dictionary of values that helps the corresponding extractor initialize
    // its state without duplicating effort already exerted by the sniffer.
    static void RegisterSniffer_l(SnifferFunc func);
};

}  // namespace android

#endif  // MEDIA_EXTRACTOR_H_
