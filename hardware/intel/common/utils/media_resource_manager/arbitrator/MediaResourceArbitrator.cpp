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
#define LOG_TAG "MRM_Arbitrator"

#include <expat.h>
#include <string.h>
#include <stdio.h>
#include <utils/Log.h>
#include <unistd.h>
#include "MediaResourceArbitrator.h"

using namespace android;


MediaResourceArbitrator::MediaResourceArbitrator()
    : mIsEncoderUnderFullLoad (false),
      mIsDecoderUnderFullLoad (false) {
    ALOGV("construct MediaResourceArbitrator");
    pthread_mutex_init(&mArbitratorLock, NULL);
    //InitializeCodecNameTypeMap();
    //InitializeResolutionNameTypeMap();
}


MediaResourceArbitrator::~MediaResourceArbitrator() {}


ArbitratorErrorType MediaResourceArbitrator::Config(const char* configFilePath) {
    FILE *fp = NULL;

    fp = ::fopen(configFilePath, "r");
    if (fp == NULL) {
        ALOGV("%s: can not open config xml file.\
               try to set up default codec limitation", __FUNCTION__);
        SetupDefaultCodecLimitation();
        return ArbitratorErrorNone;
    }

    ParseXMLFile(fp);
    return ArbitratorErrorNone;
}


bool MediaResourceArbitrator::CheckIfFullLoad(bool isEncoder) {
    if (isEncoder) {
        return mIsEncoderUnderFullLoad;
    } else {
        return mIsDecoderUnderFullLoad;
    }
}


ArbitratorErrorType MediaResourceArbitrator::AddResource(
                    /* in */  CodecType codecType,
                    /* in */  bool isEncoder,
                    /* in */  bool isSecured,
                    /* in */  ResolutionType resolution,
                    /* in */  uint frameRate) {
    ALOGV("MediaResourceArbitrator::AddResource ++");
    pthread_mutex_lock(&mArbitratorLock);

    ArbitratorErrorType err = ArbitratorErrorNone;

    if (CheckIfFullLoad(isEncoder) == true) {
        pthread_mutex_unlock(&mArbitratorLock);
        return ArbitratorErrorInsufficientResources;
    }

    CodecInfo resource;
    resource.codecType = codecType;
    resource.isEncoder = isEncoder;
    resource.isSecured = isSecured;
    resource.resolution = resolution;
    resource.frameRate = frameRate;

    ALOGV("Adding resource: codecType = %d, isEncoder = %d, isSecured = %d, resolution = %d, frameRate = %d",
          codecType, isEncoder, isSecured, resolution, frameRate);

    if (isEncoder) {
        mLivingEncodersTable.livingEncoders.push_back(resource);
        if (resolution > mLivingEncodersTable.maxResolution) {
            mLivingEncodersTable.maxResolution = resolution;
        }
        if (frameRate > mLivingEncodersTable.maxFrameRate) {
            mLivingEncodersTable.maxFrameRate = frameRate;
        }
    } else { // decoder
         mLivingDecodersTable.livingDecoders.push_back(resource);
        if (resolution > mLivingDecodersTable.maxResolution) {
            mLivingDecodersTable.maxResolution = resolution;
        }
        if (frameRate > mLivingDecodersTable.maxFrameRate) {
            mLivingDecodersTable.maxFrameRate = frameRate;
        }
    }

    err = ArbitrateFullLoad(resource);
    pthread_mutex_unlock(&mArbitratorLock);

    ALOGV("AddResource --");
    return err;
}


uint MediaResourceArbitrator::GetLivingCodecsNum(void) {
    return mLivingDecodersTable.livingDecoders.size() +
           mLivingEncodersTable.livingEncoders.size();
}



ArbitratorErrorType MediaResourceArbitrator::RemoveResource(
                                   CodecType codecType,
                                   bool isEncoder,
                                   bool isSecured,
                                   ResolutionType resolution,
                                   uint frameRate) {
    ALOGV("MediaResourceArbitrator::RemoveResource");

    uint i;
    ArbitratorErrorType err = ArbitratorErrorNone;

    pthread_mutex_lock(&mArbitratorLock);

    if (isEncoder) {
        for(i=0; i<mLivingEncodersTable.livingEncoders.size(); i++) {
            const CodecInfo& livingCodec = mLivingEncodersTable.livingEncoders[i];
            if ((livingCodec.codecType == codecType) &&
                (livingCodec.resolution == resolution) &&
                (livingCodec.frameRate == frameRate)) {
                mLivingEncodersTable.livingEncoders.removeAt(i);
                break;
            }
        }
        mIsEncoderUnderFullLoad = false;
    } else {
        for(i=0; i<mLivingDecodersTable.livingDecoders.size(); i++) {
            const CodecInfo& livingCodec = mLivingDecodersTable.livingDecoders[i];
            if ((livingCodec.codecType == codecType) &&
                (livingCodec.resolution == resolution) &&
                (livingCodec.isSecured == isSecured) &&
                (livingCodec.frameRate == frameRate)) {
                mLivingDecodersTable.livingDecoders.removeAt(i);
                break;
            }
        }
        mIsDecoderUnderFullLoad = false;
    }
    pthread_mutex_unlock(&mArbitratorLock);
    return err;
}


void MediaResourceArbitrator::ParseXMLFile(FILE* fp) {
    ALOGV("MediaResourceArbitrator::ParseXMLFile");

    int done;
    void *pBuf = NULL;

    XML_Parser parser = ::XML_ParserCreate(NULL);
    if (NULL == parser) {
        ALOGE("@%s, line:%d, parser is NULL", __func__, __LINE__);
        goto exit;
    }
    ::XML_SetUserData(parser, this);
    ::XML_SetElementHandler(parser, startElement, endElement);

    pBuf = malloc(mBufSize);
    if (NULL == pBuf) {
        ALOGE("@%s, line:%d, failed to malloc buffer", __func__, __LINE__);
        goto exit;
    }

    do {
        int len = (int)::fread(pBuf, 1, mBufSize, fp);
        if (!len) {
            if (ferror(fp)) {
                clearerr(fp);
                goto exit;
            }
        }
        done = len < mBufSize;
        if (XML_Parse(parser, (const char *)pBuf, len, done) == XML_STATUS_ERROR) {
            ALOGE("@%s, line:%d, XML_Parse error", __func__, __LINE__);
            goto exit;
        }
    } while (!done);

exit:
    if (parser)
        ::XML_ParserFree(parser);
    if (pBuf)
        free(pBuf);
    if (fp)
    ::fclose(fp);

}


ArbitratorErrorType MediaResourceArbitrator::ArbitrateFullLoad(CodecInfo& codec) {
    ALOGV("MediaResourceArbitrator::ArbitrateFullLoad");
    ALOGV("giving codec type :%d, isEncoder = %d, frameRate = %d",
           codec.codecType, codec.isEncoder, codec.frameRate);
    ArbitratorErrorType err = ArbitratorErrorNone;
    int livingInstanceNum = 0;

    if (codec.isEncoder == true) {
        livingInstanceNum = mLivingEncodersTable.livingEncoders.size();
    } else {
        livingInstanceNum = mLivingDecodersTable.livingDecoders.size();
    }

    ALOGV("current living codec number of %s is %d",
           codec.isEncoder ? "encoder" : "decoder", livingInstanceNum);

    // check if living instance number reaches the limitation
    int targetInstanceLimit = 5; // most optimistic
    uint i,j;

    if (codec.isEncoder == false) { // decoder
        for (i=0; i<mLivingDecodersTable.livingDecoders.size(); i++) {
            const CodecInfo& livingCodec = mLivingDecodersTable.livingDecoders[i];
            for (j=0; j<mDecoderLimitInfos.size(); j++) {
                const CodecInfo& targetCodec = mDecoderLimitInfos[j].codecInfo;
                ALOGV("%dth codec in DecoderLimitInfos.",j);
                if (CheckCodecMatched(livingCodec, targetCodec) == true) {
                    if (targetInstanceLimit > mDecoderLimitInfos[j].instanceLimit) {
                        targetInstanceLimit = mDecoderLimitInfos[j].instanceLimit;
                        break;
                    }
                }
            }
        }
        ALOGV("Go through decoder limit table and get current instance limit = %d",
              targetInstanceLimit);
        if (livingInstanceNum >= targetInstanceLimit) {
            ALOGV("setting full load flag to true.");
            mIsDecoderUnderFullLoad = true;
        } else {
            ALOGV("setting full load flag to false.");
            mIsDecoderUnderFullLoad = false;
        }
    } else { // encoder
        for(i=0; i<mLivingEncodersTable.livingEncoders.size(); i++) {
            const CodecInfo& livingCodec = mLivingEncodersTable.livingEncoders[i];
            for (j=0; j<mEncoderLimitInfos.size(); j++) {
                const CodecInfo& targetCodec = mEncoderLimitInfos[j].codecInfo;
                if (CheckCodecMatched(livingCodec, targetCodec) == true) {
                    if (targetInstanceLimit > mEncoderLimitInfos[j].instanceLimit) {
                        targetInstanceLimit = mEncoderLimitInfos[j].instanceLimit;
                        break;
                    }
                }
            }
        }
        ALOGV("Go through encoder limit table and get current instance limit = %d",
              targetInstanceLimit);
        if (livingInstanceNum >= targetInstanceLimit) {
            ALOGV("setting full load flag to true.");
            mIsEncoderUnderFullLoad = true;
        } else {
            ALOGV("setting full load flag to false.");
            mIsEncoderUnderFullLoad = false;
        }
    }

    return err;
}


bool MediaResourceArbitrator::CheckCodecMatched(
                                  const CodecInfo& sourceCodec,
                                  const CodecInfo& targetCodec) {
    ALOGV("CheckCodecMatched");
    return ((sourceCodec.codecType == targetCodec.codecType) &&
            (sourceCodec.isSecured == targetCodec.isSecured) &&
            (sourceCodec.resolution == targetCodec.resolution) &&
            (sourceCodec.frameRate == targetCodec.frameRate));
}


void MediaResourceArbitrator::DumpCodecTypeFromVector(void) {
    unsigned int i;
    ALOGV("MediaResourceArbitrator::DumpCodecTypeFromVector");
    for (i=0; i<mCodecNameTypeMap.size(); i++) {
        ALOGV("codec type in vector %s : %d",
               mCodecNameTypeMap.keyAt(i), mCodecNameTypeMap.valueAt(i));
    }
}


CodecType MediaResourceArbitrator::MapCodecTypeFromName(const char* name) {
    if (strcmp(name, "CODEC_TYPE_AVC") == 0) {
        return CODEC_TYPE_AVC;
    } else if (strcmp(name, "CODEC_TYPE_HEVC") == 0) {
        return CODEC_TYPE_HEVC;
    } else if (strcmp(name, "CODEC_TYPE_VP8") == 0) {
        return CODEC_TYPE_VP8;
    } else if (strcmp(name, "CODEC_TYPE_VP9") == 0) {
        return CODEC_TYPE_VP9;
    } else if (strcmp(name, "CODEC_TYPE_MPEG2") == 0) {
        return CODEC_TYPE_MPEG2;
    } else if (strcmp(name, "CODEC_TYPE_MPEG4") == 0){
        return CODEC_TYPE_MPEG4;
    } else if (strcmp(name, "CODEC_TYPE_H263") == 0) {
        return CODEC_TYPE_H263;
    } else if (strcmp(name, "CODEC_TYPE_WMV") == 0) {
        return CODEC_TYPE_WMV;
    } else if (strcmp(name, "CODEC_TYPE_VC1") == 0) {
        return CODEC_TYPE_VC1;
    } else {
        ALOGE("unknown codec name: %s, try to return avc", name);
        return CODEC_TYPE_AVC;
    }
}


ResolutionType MediaResourceArbitrator::
                   MapResolutionTypeFromName(const char* name) {
    if (strcmp(name, "480") == 0) {
        return Resolution_480;
    } else if (strcmp(name, "720") == 0) {
        return Resolution_720;
    } else if (strcmp(name, "1080") == 0) {
        return Resolution_1080;
    } else if (strcmp(name, "2K") == 0) {
        return Resolution_2K;
    } else if (strcmp(name, "4K") == 0) {
        return Resolution_4K;
    } else {
        ALOGE("unkown resolution name: %s, try to return 1080", name);
        return Resolution_1080;
    }
}


void MediaResourceArbitrator::InitializeCodecNameTypeMap(void) {
    ALOGV("MediaResourceArbitrator::InitializeCodecNameTypeMap");
    mCodecNameTypeMap.add("CODEC_TYPE_AVC", CODEC_TYPE_AVC);
    mCodecNameTypeMap.add("CODEC_TYPE_HEVC", CODEC_TYPE_HEVC);
    mCodecNameTypeMap.add("CODEC_TYPE_VP8", CODEC_TYPE_VP8);
    mCodecNameTypeMap.add("CODEC_TYPE_VP9", CODEC_TYPE_VP9);
    mCodecNameTypeMap.add("CODEC_TYPE_MPEG4", CODEC_TYPE_MPEG4);
    mCodecNameTypeMap.add("CODEC_TYPE_MPEG2", CODEC_TYPE_MPEG2);
    mCodecNameTypeMap.add("CODEC_TYPE_H263", CODEC_TYPE_H263);
    mCodecNameTypeMap.add("CODEC_TYPE_VC1", CODEC_TYPE_VC1);
    mCodecNameTypeMap.add("CODEC_TYPE_WMV", CODEC_TYPE_WMV);
    //DumpCodecTypeFromVector();
}


void MediaResourceArbitrator::InitializeResolutionNameTypeMap(void) {
    ALOGV("MediaResourceArbitrator::InitializeResolutionNameTypeMap");
    mResolutionNameTypeMap.add("480", Resolution_480);
    mResolutionNameTypeMap.add("720", Resolution_720);
    mResolutionNameTypeMap.add("1080", Resolution_1080);
    mResolutionNameTypeMap.add("2K", Resolution_2K);
    mResolutionNameTypeMap.add("4K", Resolution_4K);
}

// Hard coded limitation
void MediaResourceArbitrator::SetupDefaultCodecLimitation(void) {
    ALOGV("MediaResourceArbitrator::SetupDefaultCodecLimitation");
    uint i,j,k;
    CodecType codecType;
    ResolutionType resolutionType;
    uint frameRate;

    // non-secure decoders
    for (i=(int)CODEC_TYPE_AVC; i<(int)CODEC_TYPE_MAX; i++) {
            codecType = (CodecType)i;
        for (j=(int)Resolution_CIF; j<(int)Resolution_MAX; j++) {
            resolutionType = (ResolutionType)j;
            for (k=0; k<2; k++) {
                frameRate = (k+1)*30;
                bool isSecured = false;
                CodecLimitInfo codecLimitInfo;
                codecLimitInfo.codecInfo.codecType = codecType;
                codecLimitInfo.codecInfo.resolution = resolutionType;
                codecLimitInfo.codecInfo.isSecured = isSecured;
                codecLimitInfo.codecInfo.isEncoder = false;
                codecLimitInfo.codecInfo.frameRate = frameRate;
                codecLimitInfo.instanceLimit = 2;
                mDecoderLimitInfos.add(codecLimitInfo);
            }
        }
    }

    // secure avc decoder
    codecType = CODEC_TYPE_AVC;
    for (j=(int)Resolution_CIF; j<(int)Resolution_MAX; j++) {
        resolutionType = (ResolutionType)j;
        for (k=0; k<2; k++) {
            frameRate = (k+1)*30;
            bool isSecured = true;
            CodecLimitInfo codecLimitInfo;
            codecLimitInfo.codecInfo.codecType = codecType;
            codecLimitInfo.codecInfo.resolution = resolutionType;
            codecLimitInfo.codecInfo.isSecured = isSecured;
            codecLimitInfo.codecInfo.isEncoder = false;
            codecLimitInfo.instanceLimit = 2;
            mDecoderLimitInfos.add(codecLimitInfo);
        }
    }

    // Encoder limitation Map
    for (i=(int)CODEC_TYPE_AVC; i<(int)CODEC_TYPE_MAX; i++) {
            codecType = (CodecType)i;
        for (j=(int)Resolution_CIF; j<(int)Resolution_MAX; j++) {
            resolutionType = (ResolutionType)j;
            for (k=0; k<2; k++) {
                frameRate = (k+1)*30;
                bool isSecured = false;
                CodecLimitInfo codecLimitInfo;
                codecLimitInfo.codecInfo.codecType = codecType;
                codecLimitInfo.codecInfo.resolution = resolutionType;
                codecLimitInfo.codecInfo.isSecured = isSecured;
                codecLimitInfo.codecInfo.isEncoder = true;
                codecLimitInfo.instanceLimit = 2;
                mEncoderLimitInfos.add(codecLimitInfo);
            }
        }
    }
}


void MediaResourceArbitrator::getConfigData(const char *name,
                                            const char **atts) {
    ALOGV("MediaResourceArbitrator::getConfigData");
    int attIndex = 0;
    if (strcmp(name, "CodecResourcesLimitation") == 0) {
        return;
    } else if (strcmp(name, "Codec") == 0) {
        if (strcmp(atts[attIndex], "name") == 0) {
            ALOGV("Parsing codec %s", atts[attIndex+1]);
            mIfParsingCodec = true;
        } else {
            ALOGE("Codec tag with no name, anything wrong?");
        }
    } else if (strcmp(name, "codecType") == 0) {
        ALOGV("parse tag codecType");
        if (mIfParsingCodec) {
            if (strcmp(atts[attIndex], "value") == 0) {
                //DumpCodecTypeFromVector();
                mParsingCodecLimitInfo.codecInfo.codecType =
                    MapCodecTypeFromName((const char*)atts[attIndex+1]);
            }
        } else {
            ALOGE("Skip this element(%s) becaue this codec couldn't be supported\n", name);
        }
    } else if (strcmp(name, "isEncoder") == 0) {
        ALOGV("parse tag isEncoder");
        if (mIfParsingCodec && !strcmp(atts[attIndex], "value")) {
            if (!strcmp(atts[attIndex + 1], "false"))
                mParsingCodecLimitInfo.codecInfo.isEncoder = false;
            else {
                mParsingCodecLimitInfo.codecInfo.isEncoder = true;
            }
        } else {
            ALOGE("Skip this element(%s) becaue this tag couldn't be supported\n", name);
        }
    } else if (strcmp(name, "isSecured") == 0) {
        ALOGV("parse tag isSecured");
        if (mIfParsingCodec && !strcmp(atts[attIndex], "value")) {
            if (!strcmp(atts[attIndex + 1], "false"))
                mParsingCodecLimitInfo.codecInfo.isSecured = false;
            else {
                mParsingCodecLimitInfo.codecInfo.isSecured = true;
            }
        } else {
            ALOGE("Skip this element(%s) becaue this tag couldn't be supported\n", name);
        }
    } else if (strcmp(name, "resolutionType") == 0) {
        ALOGV("parse tag resolutionType");
        if (mIfParsingCodec) {
            if (strcmp(atts[attIndex], "value") == 0) {
                mParsingCodecLimitInfo.codecInfo.resolution =
                    MapResolutionTypeFromName((const char*)atts[attIndex+1]);
                    //mResolutionNameTypeMap.valueFor(atts[attIndex+1]);
            }
        } else {
            ALOGE("Skip this element(%s) becaue this codec couldn't be supported\n", name);
        }
    } else if (strcmp(name, "frameRate") == 0) {
        ALOGV("parse tag frameRate");
        if (mIfParsingCodec) {
            if (strcmp(atts[attIndex], "value") == 0) {
                mParsingCodecLimitInfo.codecInfo.frameRate = atoi(atts[attIndex+1]);
            }
        } else {
            ALOGE("Skip this element(%s) becaue this codec couldn't be supported\n", name);
        }
    } else if (strcmp(name, "instanceLimit") == 0) {
        ALOGV("parse tag instanceLimit");
        if (mIfParsingCodec) {
            if (strcmp(atts[attIndex], "value") == 0) {
                mParsingCodecLimitInfo.instanceLimit = atoi(atts[attIndex+1]);
            }
        } else {
            ALOGE("Skip this element(%s) becaue this codec couldn't be supported\n", name);
        }
    }
}

// Start tag
void MediaResourceArbitrator::startElement(void *userData,
                                           const char *name,
                                           const char **atts) {
    MediaResourceArbitrator* arbitrator = (MediaResourceArbitrator*)userData;
    arbitrator->getConfigData(name, atts);
}


// End tag
void MediaResourceArbitrator::endElement(void *userData, const char *name) {
    MediaResourceArbitrator* arbitrator = (MediaResourceArbitrator*)userData;
    if (strcmp(name, "Codec") == 0) {
        if (arbitrator->mParsingCodecLimitInfo.codecInfo.isEncoder == true) {
            arbitrator->mEncoderLimitInfos.push_back(arbitrator->mParsingCodecLimitInfo);
        } else {
            arbitrator->mDecoderLimitInfos.push_back(arbitrator->mParsingCodecLimitInfo);
        }
        arbitrator->mIfParsingCodec = false;
    }
}
