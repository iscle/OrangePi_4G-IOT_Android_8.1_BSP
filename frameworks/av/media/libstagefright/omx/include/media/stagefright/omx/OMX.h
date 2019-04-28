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

#ifndef ANDROID_OMX_H_
#define ANDROID_OMX_H_

#include <media/IOMX.h>
#include <utils/threads.h>
#include <utils/KeyedVector.h>
#include <media/stagefright/xmlparser/MediaCodecsXmlParser.h>
#include "OmxNodeOwner.h"

namespace android {

struct OMXMaster;
struct OMXNodeInstance;

class OMX : public BnOMX,
            public OmxNodeOwner,
            public IBinder::DeathRecipient {
public:
    OMX();

    virtual status_t listNodes(List<ComponentInfo> *list);

    virtual status_t allocateNode(
            const char *name, const sp<IOMXObserver> &observer,
            sp<IOMXNode> *omxNode);

    virtual status_t createInputSurface(
            sp<IGraphicBufferProducer> *bufferProducer,
            sp<IGraphicBufferSource> *bufferSource);

    virtual void binderDied(const wp<IBinder> &the_late_who);

    virtual status_t freeNode(const sp<OMXNodeInstance>& instance);

protected:
    virtual ~OMX();

private:
    Mutex mLock;
    OMXMaster *mMaster;
    MediaCodecsXmlParser mParser;

    KeyedVector<wp<IBinder>, sp<OMXNodeInstance> > mLiveNodes;

    OMX(const OMX &);
    OMX &operator=(const OMX &);
};

}  // namespace android

#endif  // ANDROID_OMX_H_
