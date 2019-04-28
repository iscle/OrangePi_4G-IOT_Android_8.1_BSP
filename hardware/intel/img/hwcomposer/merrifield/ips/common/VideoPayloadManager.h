/*
// Copyright (c) 2014 Intel Corporation 
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/
#ifndef VIDEO_PAYLOAD_MANAGER_H
#define VIDEO_PAYLOAD_MANAGER_H

#include <IVideoPayloadManager.h>

namespace android {
namespace intel {

class BufferMapper;

class VideoPayloadManager : public IVideoPayloadManager {

public:
    VideoPayloadManager();
    virtual ~VideoPayloadManager();

    // IVideoPayloadManager
public:
    virtual bool getMetaData(BufferMapper *mapper, MetaData *metadata);
    virtual bool setRenderStatus(BufferMapper *mapper, bool renderStatus);

}; // class VideoPayloadManager

} // namespace intel
} // namespace android

#endif /* VIDEO_PAYLOAD_MANAGER_H */
