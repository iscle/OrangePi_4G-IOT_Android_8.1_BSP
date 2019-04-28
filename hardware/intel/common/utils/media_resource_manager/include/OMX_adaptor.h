/*
* Copyright (c) 2015 Intel Corporation.  All rights reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/


#ifndef OMX_WRAPPER_H_
#define OMX_WRAPPER_H_

#include <unistd.h>
#include <OMX_Core.h>
#include <OMX_Component.h>
#include <utils/threads.h>
#include <utils/KeyedVector.h>
#include <utils/String8.h>
#include "MediaResourceArbitrator.h"

using namespace android;

typedef KeyedVector <OMX_HANDLETYPE, String8> ComponentNameMap;
typedef KeyedVector <OMX_HANDLETYPE, uint> ComponentFramerateMap;

typedef struct _AdaptorCodecInfo {
    CodecType codecType;
    bool isEncoder;
    bool isSecured;
    ResolutionType resolution;
    uint frameRate;
} AdaptorCodecInfo;

typedef KeyedVector <OMX_HANDLETYPE, AdaptorCodecInfo> ComponentInfoMap;

class MRM_OMX_Adaptor {
public:
    // Returns the singleton instance
    static MRM_OMX_Adaptor* getInstance();

    ~MRM_OMX_Adaptor() {
        if (sInstance) {
            delete sInstance;
            sInstance = NULL;
        }
    };

    // create and configure the MRM arbitrator
    OMX_ERRORTYPE MRM_OMX_Init(void);


    // check with MRM arbitrator if codec resource
    // is under full load status.
    // this should be called before OMX_GetHandle
    // return OMX_ErrorInsufficientResources if true.
    OMX_ERRORTYPE MRM_OMX_CheckIfFullLoad(OMX_STRING cComponentName);


    // Set component name and component handle
    // keeps this mapping but not adds resource yet.
    // this intends to be called after OMX_GetHandle
    void MRM_OMX_SetComponent(
                         OMX_HANDLETYPE pComponentHandle,
                         OMX_STRING cComponentName);


    // handle the index 'OMX_IndexParamPortDefinition'
    // when codec is configured, with resolution and
    // frame rate. this actually adds resource
    // to the MRM arbitrator.
    // return OMX_ErrorInsufficientResources if failed.
    OMX_ERRORTYPE MRM_OMX_SetParameter(
                         OMX_HANDLETYPE hComponent,
                         OMX_INDEXTYPE nIndex,
                         OMX_PTR pComponentParameterStructure);


    // check grahpic buffer resource
    // return OMX_ErrorInsufficientResources if under full load status.
    OMX_ERRORTYPE MRM_OMX_UseBuffer(
                         OMX_HANDLETYPE hComponent,
                         OMX_BUFFERHEADERTYPE **ppBufferHdr,
                         OMX_U32 nPortIndex,
                         OMX_PTR pAppPrivate,
                         OMX_U32 nSizeBytes,
                         OMX_U8 *pBuffer);


    // Remove the component
    OMX_ERRORTYPE MRM_OMX_RemoveComponent(OMX_HANDLETYPE pComponentHandle);

private:
    MRM_OMX_Adaptor() { mArbitrator = new MediaResourceArbitrator(); }
    MRM_OMX_Adaptor& operator=(const MRM_OMX_Adaptor&);  // Don't call me
    MRM_OMX_Adaptor(const MRM_OMX_Adaptor&);             // Don't call me


    void ParseCodecInfoFromComponentName(const char* componentName,
                                         AdaptorCodecInfo* codecInfo);

    MediaResourceArbitrator* mArbitrator;
    static Mutex sLock;
    static MRM_OMX_Adaptor* sInstance;

    ComponentNameMap mComponentNameMap;
    ComponentFramerateMap mComponentFramerateMap;
    ComponentInfoMap mComponentInfoMap;
};
#endif /* OMX_WRAPPER_H_ */
