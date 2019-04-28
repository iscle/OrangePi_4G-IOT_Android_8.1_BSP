/*
 * Copyright 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <media/stagefright/foundation/ABase.h>
#include <media/stagefright/foundation/AString.h>
#include <utils/KeyedVector.h>
#include <utils/RefBase.h>

namespace android {

// Encapsulates an "HTTP/RTSP style" response, i.e. a status line,
// key/value pairs making up the headers and an optional body/content.
struct ParsedMessage : public RefBase {
    static sp<ParsedMessage> Parse(
            const char *data, size_t size, bool noMoreData, size_t *length);

    bool findString(const char *name, AString *value) const;
    bool findInt32(const char *name, int32_t *value) const;

    const char *getContent() const;

    bool getRequestField(size_t index, AString *field) const;
    bool getStatusCode(int32_t *statusCode) const;

    AString debugString() const;

    static bool GetAttribute(const char *s, const char *key, AString *value);

    static bool GetInt32Attribute(
            const char *s, const char *key, int32_t *value);


protected:
    virtual ~ParsedMessage();

private:
    KeyedVector<AString, AString> mDict;
    AString mContent;

    ParsedMessage();

    ssize_t parse(const char *data, size_t size, bool noMoreData);

    DISALLOW_EVIL_CONSTRUCTORS(ParsedMessage);
};
///M: Add by MTK03594 @{
#define WFD_TESTMODE            "wlan.mircast.mode"
#define WFD_TESTMODE_TRUE       "true"
#define WFD_TESTMODE_PORT       20002
#define WFD_MAX_BUFFER_SIZE     1024
#define WFD_UIBC_SERVER_PORT    19283


#define WFD_AUDIO_CODECS            "wfd_audio_codecs"
#define WFD_VIDEO_FORMATS           "wfd_video_formats"
#define WFD_COUPLE_SINK             "wfd_coupled_sink"
#define WFD_TRIGGER_METHOD          "wfd_trigger_method"
#define WFD_PRESENTATION_URL        "wfd_presentation_URL"
#define WFD_CLIENT_RTP_PORTS        "wfd_client_rtp_ports"
#define WFD_STANDBY                 "wfd_standby"
#define WFD_IDR_REQUEST             "wfd_idr_request"
#define WFD_UIBC_CAPABILITY         "wfd_uibc_capability"
#define WFD_UIBC_SETTING            "wfd_uibc_setting"

#define PLAY_STATE_REASON           "in-play-state"
#define PAUSE_STATE_REASON          "in-pause-state"


/// @}
}  // namespace android
