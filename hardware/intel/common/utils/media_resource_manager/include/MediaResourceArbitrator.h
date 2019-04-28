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


#ifndef MEDIA_RESOURCE_ARBITRATOR_H_
#define MEDIA_RESOURCE_ARBITRATOR_H_

#include <unistd.h>
#include <string.h>
#include <utils/KeyedVector.h>
#include <utils/Vector.h>

#define MAX_BUFFER_SIZE (20 * 1024)

using namespace android;

// This error type keeps align with the OMX error type
typedef enum _ArbitratorErrorType {
    ArbitratorErrorNone = 0,

    /** There were insufficient resources to perform the requested operation */
    ArbitratorErrorInsufficientResources = 0x80001000,

    /** There was an error, but the cause of the error could not be determined */
    ArbitratorErrorUndefined = 0x80001001
} ArbitratorErrorType;


typedef enum _ResolutionType {
    Resolution_CIF = 0,
    Resolution_480,
    Resolution_720,
    Resolution_1080,
    Resolution_2K,
    Resolution_4K,
    Resolution_MAX
} ResolutionType;


typedef enum _CodecType {
    CODEC_TYPE_AVC = 0,
    CODEC_TYPE_HEVC,
    CODEC_TYPE_VP8,
    CODEC_TYPE_VP9,
    CODEC_TYPE_MPEG4,
    CODEC_TYPE_MPEG2,
    CODEC_TYPE_H263,
    CODEC_TYPE_VC1,
    CODEC_TYPE_WMV,
    CODEC_TYPE_MAX
} CodecType;


typedef struct _CodecInfo {
    CodecType codecType;
    bool isEncoder;
    bool isSecured;
    ResolutionType resolution;
    uint frameRate;
} CodecInfo;


typedef struct _CodecLimitInfo {
    CodecInfo codecInfo;
    int instanceLimit;
} CodecLimitInfo;


typedef struct _LivingDecodersTable {
    Vector<CodecInfo> livingDecoders;
    uint maxResolution;
    uint maxFrameRate;
} LivingDecodersTable;


typedef struct _LivingEncodersTable {
    Vector<CodecInfo> livingEncoders;
    uint maxResolution;
    uint maxFrameRate;
} LivingEncodersTable;


class MediaResourceArbitrator {
public:
    MediaResourceArbitrator ();
    ~MediaResourceArbitrator ();

    /* Initialize the arbitrator.
       Parse the config XML file if given. */
    ArbitratorErrorType Config(const char* configFilePath);

    /* Check if the resource limitation is hit and
       it is under full load status. In such status, there
       is no room to instantiate codec anymore. */
    bool CheckIfFullLoad(bool isEncoder);

    /* Add codec in the pool.
       Resolution and frame rate must be provided.
       This is not necessarily be called when codec instance
       is constructed when the resolution and frame rate are
       not known yet.
       This may be called when codec is configured,
       for example in OMX set parameter, etc.
       Return value is expected to be as one of:
           ArbitratorErrorNone,
           ArbitratorErrorInsufficientResources
     */
    ArbitratorErrorType AddResource(/* in */  CodecType codecType,
                                    /* in */  bool isEncoder,
                                    /* in */  bool isSecured,
                                    /* in */  ResolutionType resolution,
                                    /* in */  uint frameRate);

    /* Remove codec in the pool.*/
    ArbitratorErrorType RemoveResource(CodecType codecType,
                                       bool isEncoder,
                                       bool isSecured,
                                       ResolutionType resolution,
                                       uint frameRate);

    uint GetLivingCodecsNum(void);

    // XML function
    void ParseXMLFile(FILE* fp);
    static void startElement(void *userData, const char *name, const char **atts);
    static void endElement(void *userData, const char *name);
    void getConfigData(const char *name, const char **atts);

private:
    // a global table stores all codec limit info
    Vector<CodecLimitInfo> mDecoderLimitInfos;
    Vector<CodecLimitInfo> mEncoderLimitInfos;

    // a global talbe stores all living codec info
    LivingDecodersTable mLivingDecodersTable;
    LivingEncodersTable mLivingEncodersTable;

    // arbitrator lock
    pthread_mutex_t mArbitratorLock;

    // indicate whether it is under full load status
    bool mIsEncoderUnderFullLoad;
    bool mIsDecoderUnderFullLoad;

    KeyedVector <const char*, CodecType> mCodecNameTypeMap;
    KeyedVector <const char*, ResolutionType> mResolutionNameTypeMap;

    static const int mBufSize = MAX_BUFFER_SIZE;
    // indicate XML parser is parsing a codec tag
    bool mIfParsingCodec;
    CodecLimitInfo mParsingCodecLimitInfo;

    ArbitratorErrorType ArbitrateFullLoad(CodecInfo& codec);

    bool CheckCodecMatched(const CodecInfo& sourceCodec,
                           const CodecInfo& targetCodec);

    void SetupDefaultCodecLimitation(void);
    void InitializeCodecNameTypeMap();
    void InitializeResolutionNameTypeMap();
    void DumpCodecTypeFromVector(void);
    CodecType MapCodecTypeFromName(const char* name);
    ResolutionType MapResolutionTypeFromName(const char* name);
};

#endif /* MEDIA_RESOURCE_ARBITRATOR_H_ */
