/*
 * * Copyright (c) 2015 Intel Corporation.  All rights reserved.
 * *
 * * Licensed under the Apache License, Version 2.0 (the "License");
 * * you may not use this file except in compliance with the License.
 * * You may obtain a copy of the License at
 * *
 * * http://www.apache.org/licenses/LICENSE-2.0
 * *
 * * Unless required by applicable law or agreed to in writing, software
 * * distributed under the License is distributed on an "AS IS" BASIS,
 * * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * * See the License for the specific language governing permissions and
 * * limitations under the License.
 * */


//#define LOG_NDEBUG 0
#define LOG_TAG "MRM_OMX_Adaptor"

#include <utils/Log.h>
#include <utils/threads.h>
#include "OMX_adaptor.h"

const char* CODECS_LIMITATION_FILE = "/etc/codec_resources_limitation.xml";

using namespace android;

// static member declare
MRM_OMX_Adaptor* MRM_OMX_Adaptor::sInstance = NULL;
Mutex MRM_OMX_Adaptor::sLock;

typedef enum {
    kPortIndexInput  = 0,
    kPortIndexOutput = 1
} PORT_INDEX;


// case insensitive string finding
static const char* strstri(const char* str, const char* subStr) {
    int len = strlen(subStr);
    if (len == 0) {
        return NULL;
    }

    while(*str) {
        if(strncasecmp(str, subStr, len) == 0) {
            return str;
        }
        ++str;
    }
    return NULL;
}


//static
MRM_OMX_Adaptor* MRM_OMX_Adaptor::getInstance() {
    ALOGV("getInstance()");
    Mutex::Autolock lock(sLock);

    if (sInstance == NULL) {
        sInstance = new MRM_OMX_Adaptor();
    }

    return sInstance;
}


OMX_ERRORTYPE MRM_OMX_Adaptor::MRM_OMX_Init(void) {
    ALOGV("MRM_OMX_Init");
    OMX_ERRORTYPE err = OMX_ErrorNone;
    if (mArbitrator != NULL) {
        err = (OMX_ERRORTYPE)mArbitrator->Config(CODECS_LIMITATION_FILE);
    }
    return err;
}


OMX_ERRORTYPE MRM_OMX_Adaptor::MRM_OMX_CheckIfFullLoad(OMX_STRING cComponentName) {
    ALOGV("MRM_OMX_CheckIfFullLoad");
    Mutex::Autolock lock(sLock);

    String8 sComponentName(cComponentName);
    AdaptorCodecInfo codecInfo;
    ParseCodecInfoFromComponentName(sComponentName.string(), &codecInfo);

    if (codecInfo.isEncoder) {
        ALOGV("Checking full load status of encoder.");
        if (mArbitrator->CheckIfFullLoad(true/*encoder*/)) {
            ALOGV("encoder in full load status. return OMX_ErrorInsufficientResources");
            return OMX_ErrorInsufficientResources;
        } else {
            return OMX_ErrorNone;
        }
    } else {
        ALOGV("Checking full load status of decoder.");
        if (mArbitrator->CheckIfFullLoad(false/*decoder*/)) {
            ALOGV("decoder in full load status. return OMX_ErrorInsufficientResources");
            return OMX_ErrorInsufficientResources;
        } else {
            return OMX_ErrorNone;
        }
    }
}


void MRM_OMX_Adaptor::MRM_OMX_SetComponent(
                          OMX_HANDLETYPE pComponentHandle,
                          OMX_STRING cComponentName) {
    ALOGV("MRM_OMX_SetComponent: %s", cComponentName);
    String8 sComponentName(cComponentName);
    ALOGV("pComponentHandle = %p, componentName = %s", pComponentHandle, sComponentName.string());
    mComponentNameMap.add(pComponentHandle, sComponentName);
}


OMX_ERRORTYPE MRM_OMX_Adaptor::MRM_OMX_SetParameter(
                         OMX_HANDLETYPE hComponent,
                         OMX_INDEXTYPE nIndex,
                         OMX_PTR pComponentParameterStructure) {
    ALOGV("MRM_OMX_SetParameter");
    ALOGV("hComponent = %p", hComponent);
    OMX_ERRORTYPE err = OMX_ErrorNone;

    Mutex::Autolock lock(sLock);

    if (nIndex == OMX_IndexParamPortDefinition) {
        OMX_PARAM_PORTDEFINITIONTYPE *def =
            static_cast<OMX_PARAM_PORTDEFINITIONTYPE*>(pComponentParameterStructure);

        if (def->nPortIndex == kPortIndexInput) {
            ALOGV("MRM_OMX_SetParameter for inport param def");
            if (mComponentFramerateMap.indexOfKey(hComponent) >= 0) {
                ALOGV("setParameter is called again for component %p inport", hComponent);
                return OMX_ErrorNone;
            }

            OMX_VIDEO_PORTDEFINITIONTYPE *video_def = &def->format.video;
            uint frameRate = (uint)(video_def->xFramerate/65536);
            ALOGV("frame rate from inport = %d", frameRate);
            mComponentFramerateMap.add(hComponent, frameRate);
        }

        if (def->nPortIndex == kPortIndexOutput) {
            OMX_VIDEO_PORTDEFINITIONTYPE *video_def = &def->format.video;

            // if setParameter is not first called to this component's outport
            // do not try to record its info for the second time
            if (mComponentInfoMap.indexOfKey(hComponent) >= 0) {
                ALOGV("setParameter is called again for component %p outport", hComponent);
                return OMX_ErrorNone;
            }

            String8 sComponentName = mComponentNameMap.valueFor(hComponent);
            ALOGV("component name from component map is %s", sComponentName.string());

            AdaptorCodecInfo codecInfo;
            ParseCodecInfoFromComponentName(sComponentName.string(), &codecInfo);

            if (mArbitrator->CheckIfFullLoad(codecInfo.isEncoder)) {
                return OMX_ErrorInsufficientResources;
            }

            ResolutionType resolution = Resolution_CIF;
            unsigned int height = video_def->nFrameHeight;
            ALOGV("video resulotion = %d x %d", video_def->nFrameWidth, video_def->nFrameHeight);
            if (height <= 480) {
                resolution = Resolution_480;
            } else if (height <= 720) {
                resolution = Resolution_720;
            } else if (height <= 1080) {
                resolution = Resolution_1080;
            } else if (height <= 1440) {
                resolution = Resolution_2K;
            } else if (height <= 2160) {
                resolution = Resolution_4K;
            } else {
                ALOGE("resolution > 4K is not supported!");
            }
            codecInfo.resolution = resolution;

            unsigned int frameRate = 0;
            if (mComponentFramerateMap.indexOfKey(hComponent) >= 0) {
                frameRate = mComponentFramerateMap.valueFor(hComponent);
            } else {
                ALOGW("frame rate was not set in inport def...");
            }

            ALOGV("frame rate from inport def = %d", frameRate);
            if ((frameRate > 55) && (frameRate < 65)) {
                frameRate = 60;
            // This is a w/a to set default frame rate as 30 in case it is not
            // set from framewrok.
            } else {
                frameRate = 30;
            }
            codecInfo.frameRate = frameRate;
            err = (OMX_ERRORTYPE)mArbitrator->AddResource(codecInfo.codecType,
                                                          codecInfo.isEncoder,
                                                          codecInfo.isSecured,
                                                          codecInfo.resolution,
                                                          codecInfo.frameRate);

            mComponentInfoMap.add(hComponent, codecInfo);
        }
    }
    return err;
}


OMX_ERRORTYPE MRM_OMX_Adaptor::MRM_OMX_UseBuffer(
                         OMX_HANDLETYPE hComponent,
                         OMX_BUFFERHEADERTYPE **ppBufferHdr,
                         OMX_U32 nPortIndex,
                         OMX_PTR pAppPrivate,
                         OMX_U32 nSizeBytes,
                         OMX_U8 *pBuffer) {
    ALOGV("MRM_OMX_UseBuffer");
    if(pBuffer == 0 || ppBufferHdr == 0) {
        ALOGV("%s: Null buffer. hComponent:%p, ppBufferHdr:%p, "
            "nPortIndex:%d, pAppPrivate:%p, nSizeBytes:%d, pBuffer:%p",
            __FUNCTION__,
            hComponent,
            ppBufferHdr,
            nPortIndex,
            pAppPrivate,
            nSizeBytes,
            pBuffer);
    }

    OMX_ERRORTYPE err = OMX_ErrorNone;
    return err;
}


OMX_ERRORTYPE MRM_OMX_Adaptor::MRM_OMX_RemoveComponent(
                                   OMX_HANDLETYPE pComponentHandle) {
    ALOGV("MRM_OMX_RemoveComponent %p", pComponentHandle);
    OMX_ERRORTYPE err = OMX_ErrorNone;

    if (mComponentInfoMap.indexOfKey(pComponentHandle) < 0) {
        ALOGE("component %p was not added by setParameter before! something is wrong?", pComponentHandle);
        return OMX_ErrorNone; // TODO: change to specific error.
    }

    const AdaptorCodecInfo& codecInfo = mComponentInfoMap.valueFor(pComponentHandle);

    err = (OMX_ERRORTYPE)mArbitrator->RemoveResource(codecInfo.codecType,
                                                  codecInfo.isEncoder,
                                                  codecInfo.isSecured,
                                                  codecInfo.resolution,
                                                  codecInfo.frameRate);
    mComponentInfoMap.removeItem(pComponentHandle);
    return err;
}




void MRM_OMX_Adaptor::ParseCodecInfoFromComponentName(
                                         const char* componentName,
                                         AdaptorCodecInfo* codecInfo) {
    ALOGV("ParseCodecInfoFromComponentName");
    ALOGV("componentName = %s", componentName);
    bool isSecured = false;
    if (strstri(componentName,"SECURE") != NULL) {
        isSecured = true;
    }
    codecInfo->isSecured = isSecured;

    bool isEncoder = false;
    if ((strstri(componentName, "ENCODER") != NULL) ||
        (strstri(componentName, "sw_ve") != NULL)) {
        isEncoder = true;
    }
    codecInfo->isEncoder = isEncoder;

    CodecType codecType = CODEC_TYPE_MAX;
    if (strstri(componentName, "AVC") != NULL) {
        codecType = CODEC_TYPE_AVC;
    } else if (strstri(componentName, "VP8") != NULL) {
        codecType = CODEC_TYPE_VP8;
    } else if (strstri(componentName, "VP9") != NULL) {
        codecType = CODEC_TYPE_VP9;
    } else if (strstri(componentName, "MPEG4") != NULL) {
        codecType = CODEC_TYPE_MPEG4;
    } else if (strstri(componentName, "MPEG2") != NULL) {
        codecType = CODEC_TYPE_MPEG2;
    } else if (strstri(componentName, "H263") != NULL) {
        codecType = CODEC_TYPE_H263;
    } else if (strstri(componentName, "H265") != NULL) {
        codecType = CODEC_TYPE_HEVC;
    } else if (strstri(componentName, "WMV") != NULL) {
        codecType = CODEC_TYPE_WMV;
    }
    ALOGV("video codec type = %d", codecType);
    codecInfo->codecType = codecType;
}


