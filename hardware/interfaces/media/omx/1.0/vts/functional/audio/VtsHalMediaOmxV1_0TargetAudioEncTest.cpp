/*
 * Copyright (C) 2017 The Android Open Source Project
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

#define LOG_TAG "media_omx_hidl_audio_enc_test"
#ifdef __LP64__
#define OMX_ANDROID_COMPILE_AS_32BIT_ON_64BIT_PLATFORMS
#endif

#include <android-base/logging.h>

#include <android/hardware/media/omx/1.0/IOmx.h>
#include <android/hardware/media/omx/1.0/IOmxNode.h>
#include <android/hardware/media/omx/1.0/IOmxObserver.h>
#include <android/hardware/media/omx/1.0/types.h>
#include <android/hidl/allocator/1.0/IAllocator.h>
#include <android/hidl/memory/1.0/IMapper.h>
#include <android/hidl/memory/1.0/IMemory.h>

using ::android::hardware::media::omx::V1_0::IOmx;
using ::android::hardware::media::omx::V1_0::IOmxObserver;
using ::android::hardware::media::omx::V1_0::IOmxNode;
using ::android::hardware::media::omx::V1_0::Message;
using ::android::hardware::media::omx::V1_0::CodecBuffer;
using ::android::hardware::media::omx::V1_0::PortMode;
using ::android::hidl::allocator::V1_0::IAllocator;
using ::android::hidl::memory::V1_0::IMemory;
using ::android::hidl::memory::V1_0::IMapper;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

#include <VtsHalHidlTargetTestBase.h>
#include <getopt.h>
#include <media_audio_hidl_test_common.h>
#include <media_hidl_test_common.h>
#include <fstream>

// A class for test environment setup
class ComponentTestEnvironment : public ::testing::Environment {
   public:
    virtual void SetUp() {}
    virtual void TearDown() {}

    ComponentTestEnvironment() : instance("default"), res("/sdcard/media/") {}

    void setInstance(const char* _instance) { instance = _instance; }

    void setComponent(const char* _component) { component = _component; }

    void setRole(const char* _role) { role = _role; }

    void setRes(const char* _res) { res = _res; }

    const hidl_string getInstance() const { return instance; }

    const hidl_string getComponent() const { return component; }

    const hidl_string getRole() const { return role; }

    const hidl_string getRes() const { return res; }

    int initFromOptions(int argc, char** argv) {
        static struct option options[] = {
            {"instance", required_argument, 0, 'I'},
            {"component", required_argument, 0, 'C'},
            {"role", required_argument, 0, 'R'},
            {"res", required_argument, 0, 'P'},
            {0, 0, 0, 0}};

        while (true) {
            int index = 0;
            int c = getopt_long(argc, argv, "I:C:R:P:", options, &index);
            if (c == -1) {
                break;
            }

            switch (c) {
                case 'I':
                    setInstance(optarg);
                    break;
                case 'C':
                    setComponent(optarg);
                    break;
                case 'R':
                    setRole(optarg);
                    break;
                case 'P':
                    setRes(optarg);
                    break;
                case '?':
                    break;
            }
        }

        if (optind < argc) {
            fprintf(stderr,
                    "unrecognized option: %s\n\n"
                    "usage: %s <gtest options> <test options>\n\n"
                    "test options are:\n\n"
                    "-I, --instance: HAL instance to test\n"
                    "-C, --component: OMX component to test\n"
                    "-R, --role: OMX component Role\n"
                    "-P, --res: Resource files directory location\n",
                    argv[optind ?: 1], argv[0]);
            return 2;
        }
        return 0;
    }

   private:
    hidl_string instance;
    hidl_string component;
    hidl_string role;
    hidl_string res;
};

static ComponentTestEnvironment* gEnv = nullptr;

// audio encoder test fixture class
class AudioEncHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   private:
    typedef ::testing::VtsHalHidlTargetTestBase Super;
   public:
    ::std::string getTestCaseInfo() const override {
        return ::std::string() +
                "Component: " + gEnv->getComponent().c_str() + " | " +
                "Role: " + gEnv->getRole().c_str() + " | " +
                "Instance: " + gEnv->getInstance().c_str() + " | " +
                "Res: " + gEnv->getRes().c_str();
    }

    virtual void SetUp() override {
        Super::SetUp();
        disableTest = false;
        android::hardware::media::omx::V1_0::Status status;
        omx = Super::getService<IOmx>(gEnv->getInstance());
        ASSERT_NE(omx, nullptr);
        observer =
            new CodecObserver([this](Message msg, const BufferInfo* buffer) {
                handleMessage(msg, buffer);
            });
        ASSERT_NE(observer, nullptr);
        if (strncmp(gEnv->getComponent().c_str(), "OMX.", 4) != 0)
            disableTest = true;
        EXPECT_TRUE(omx->allocateNode(
                           gEnv->getComponent(), observer,
                           [&](android::hardware::media::omx::V1_0::Status _s,
                               sp<IOmxNode> const& _nl) {
                               status = _s;
                               this->omxNode = _nl;
                           })
                        .isOk());
        ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
        ASSERT_NE(omxNode, nullptr);
        ASSERT_NE(gEnv->getRole().empty(), true) << "Invalid Component Role";
        struct StringToName {
            const char* Name;
            standardComp CompName;
        };
        const StringToName kStringToName[] = {
            {"amrnb", amrnb}, {"amrwb", amrwb}, {"aac", aac}, {"flac", flac},
        };
        const size_t kNumStringToName =
            sizeof(kStringToName) / sizeof(kStringToName[0]);
        const char* pch;
        char substring[OMX_MAX_STRINGNAME_SIZE];
        strcpy(substring, gEnv->getRole().c_str());
        pch = strchr(substring, '.');
        ASSERT_NE(pch, nullptr);
        compName = unknown_comp;
        for (size_t i = 0; i < kNumStringToName; ++i) {
            if (!strcasecmp(pch + 1, kStringToName[i].Name)) {
                compName = kStringToName[i].CompName;
                break;
            }
        }
        if (compName == unknown_comp) disableTest = true;
        struct CompToCoding {
            standardComp CompName;
            OMX_AUDIO_CODINGTYPE eEncoding;
        };
        static const CompToCoding kCompToCoding[] = {
            {amrnb, OMX_AUDIO_CodingAMR},
            {amrwb, OMX_AUDIO_CodingAMR},
            {aac, OMX_AUDIO_CodingAAC},
            {flac, OMX_AUDIO_CodingFLAC},
        };
        static const size_t kNumCompToCoding =
            sizeof(kCompToCoding) / sizeof(kCompToCoding[0]);
        size_t i;
        for (i = 0; i < kNumCompToCoding; ++i) {
            if (kCompToCoding[i].CompName == compName) {
                eEncoding = kCompToCoding[i].eEncoding;
                break;
            }
        }
        if (i == kNumCompToCoding) disableTest = true;
        eosFlag = false;
        if (disableTest) std::cout << "[   WARN   ] Test Disabled \n";
    }

    virtual void TearDown() override {
        if (omxNode != nullptr) {
            // If you have encountered a fatal failure, it is possible that
            // freeNode() will not go through. Instead of hanging the app.
            // let it pass through and report errors
            if (::testing::Test::HasFatalFailure()) return;
            EXPECT_TRUE((omxNode->freeNode()).isOk());
            omxNode = nullptr;
        }
        Super::TearDown();
    }

    // callback function to process messages received by onMessages() from IL
    // client.
    void handleMessage(Message msg, const BufferInfo* buffer) {
        (void)buffer;

        if (msg.type == Message::Type::FILL_BUFFER_DONE) {
            if (msg.data.extendedBufferData.flags & OMX_BUFFERFLAG_EOS) {
                eosFlag = true;
            }
            if (msg.data.extendedBufferData.rangeLength != 0) {
#define WRITE_OUTPUT 0
#if WRITE_OUTPUT
                static int count = 0;
                FILE* ofp = nullptr;
                if (count)
                    ofp = fopen("out.bin", "ab");
                else
                    ofp = fopen("out.bin", "wb");
                if (ofp != nullptr) {
                    fwrite(static_cast<void*>(buffer->mMemory->getPointer()),
                           sizeof(char),
                           msg.data.extendedBufferData.rangeLength, ofp);
                    fclose(ofp);
                    count++;
                }
#endif
            }
        }
    }

    enum standardComp {
        amrnb,
        amrwb,
        aac,
        flac,
        unknown_comp,
    };

    sp<IOmx> omx;
    sp<CodecObserver> observer;
    sp<IOmxNode> omxNode;
    standardComp compName;
    OMX_AUDIO_CODINGTYPE eEncoding;
    bool disableTest;
    bool eosFlag;

   protected:
    static void description(const std::string& description) {
        RecordProperty("description", description);
    }
};

// Set Default port param.
void setDefaultPortParam(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                         OMX_AUDIO_CODINGTYPE eEncoding,
                         AudioEncHidlTest::standardComp comp, int32_t nChannels,
                         int32_t nSampleRate, int32_t nBitRate) {
    android::hardware::media::omx::V1_0::Status status;

    OMX_PARAM_PORTDEFINITIONTYPE portDef;
    status = getPortParam(omxNode, OMX_IndexParamPortDefinition, portIndex,
                          &portDef);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    portDef.format.audio.bFlagErrorConcealment = OMX_TRUE;
    portDef.format.audio.eEncoding = eEncoding;
    status = setPortParam(omxNode, OMX_IndexParamPortDefinition, portIndex,
                          &portDef);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    std::vector<int32_t> arrProfile;
    int32_t profile;
    if ((int)eEncoding == OMX_AUDIO_CodingAAC) {
        enumerateProfile(omxNode, portIndex, &arrProfile);
        if (arrProfile.empty() == true) ASSERT_TRUE(false);
        profile = arrProfile[0];
    }

    switch ((int)eEncoding) {
        case OMX_AUDIO_CodingFLAC:
            setupFLACPort(omxNode, portIndex, nChannels, nSampleRate,
                          5 /* nCompressionLevel */);
            break;
        case OMX_AUDIO_CodingAMR:
            setupAMRPort(omxNode, portIndex, nBitRate,
                         (comp == AudioEncHidlTest::standardComp::amrwb));
            break;
        case OMX_AUDIO_CodingAAC:
            setupAACPort(omxNode, portIndex,
                         static_cast<OMX_AUDIO_AACPROFILETYPE>(profile),
                         OMX_AUDIO_AACStreamFormatMP4FF, nChannels, nBitRate,
                         nSampleRate);
            break;
        default:
            break;
    }
}

// LookUpTable of clips and metadata for component testing
void GetURLForComponent(AudioEncHidlTest::standardComp comp, char* mURL) {
    struct CompToURL {
        AudioEncHidlTest::standardComp comp;
        const char* mURL;
    };
    static const CompToURL kCompToURL[] = {
        {AudioEncHidlTest::standardComp::aac, "bbb_raw_2ch_48khz_s16le.raw"},
        {AudioEncHidlTest::standardComp::amrnb, "bbb_raw_1ch_8khz_s16le.raw"},
        {AudioEncHidlTest::standardComp::amrwb, "bbb_raw_1ch_16khz_s16le.raw"},
        {AudioEncHidlTest::standardComp::flac, "bbb_raw_2ch_48khz_s16le.raw"},
    };

    for (size_t i = 0; i < sizeof(kCompToURL) / sizeof(kCompToURL[0]); ++i) {
        if (kCompToURL[i].comp == comp) {
            strcat(mURL, kCompToURL[i].mURL);
            return;
        }
    }
}

// blocking call to ensures application to Wait till all the inputs are consumed
void waitOnInputConsumption(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
                            android::Vector<BufferInfo>* iBuffer,
                            android::Vector<BufferInfo>* oBuffer) {
    android::hardware::media::omx::V1_0::Status status;
    Message msg;
    int timeOut = TIMEOUT_COUNTER_Q;

    while (timeOut--) {
        size_t i = 0;
        status =
            observer->dequeueMessage(&msg, DEFAULT_TIMEOUT_Q, iBuffer, oBuffer);
        ASSERT_EQ(status,
                  android::hardware::media::omx::V1_0::Status::TIMED_OUT);
        // status == TIMED_OUT, it could be due to process time being large
        // than DEFAULT_TIMEOUT or component needs output buffers to start
        // processing.
        for (; i < iBuffer->size(); i++) {
            if ((*iBuffer)[i].owner != client) break;
        }
        if (i == iBuffer->size()) break;

        // Dispatch an output buffer assuming outQueue.empty() is true
        size_t index;
        if ((index = getEmptyBufferID(oBuffer)) < oBuffer->size()) {
            ASSERT_NO_FATAL_FAILURE(
                dispatchOutputBuffer(omxNode, oBuffer, index));
            timeOut = TIMEOUT_COUNTER_Q;
        }
    }
}

// Encode N Frames
void encodeNFrames(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
                   android::Vector<BufferInfo>* iBuffer,
                   android::Vector<BufferInfo>* oBuffer, uint32_t nFrames,
                   int32_t samplesPerFrame, int32_t nChannels,
                   int32_t nSampleRate, std::ifstream& eleStream,
                   bool signalEOS = true) {
    android::hardware::media::omx::V1_0::Status status;
    Message msg;
    size_t index;
    int bytesCount = samplesPerFrame * nChannels * 2;
    int32_t timestampIncr =
        (int)(((float)samplesPerFrame / nSampleRate) * 1000000);
    uint64_t timestamp = 0;
    uint32_t flags = 0;
    int timeOut = TIMEOUT_COUNTER_Q;
    bool iQueued, oQueued;

    while (1) {
        iQueued = oQueued = false;
        status =
            observer->dequeueMessage(&msg, DEFAULT_TIMEOUT_Q, iBuffer, oBuffer);
        if (status == android::hardware::media::omx::V1_0::Status::OK)
            ASSERT_TRUE(false);

        if (nFrames == 0) break;

        // Dispatch input buffer
        if ((index = getEmptyBufferID(iBuffer)) < iBuffer->size()) {
            char* ipBuffer = static_cast<char*>(
                static_cast<void*>((*iBuffer)[index].mMemory->getPointer()));
            ASSERT_LE(bytesCount,
                      static_cast<int>((*iBuffer)[index].mMemory->getSize()));
            eleStream.read(ipBuffer, bytesCount);
            if (eleStream.gcount() != bytesCount) break;
            flags = OMX_BUFFERFLAG_ENDOFFRAME;
            if (signalEOS && (nFrames == 1)) flags |= OMX_BUFFERFLAG_EOS;
            ASSERT_NO_FATAL_FAILURE(dispatchInputBuffer(
                omxNode, iBuffer, index, bytesCount, flags, timestamp));
            timestamp += timestampIncr;
            nFrames--;
            iQueued = true;
        }
        // Dispatch output buffer
        if ((index = getEmptyBufferID(oBuffer)) < oBuffer->size()) {
            ASSERT_NO_FATAL_FAILURE(
                dispatchOutputBuffer(omxNode, oBuffer, index));
            oQueued = true;
        }
        // Reset Counters when either input or output buffer is dispatched
        if (iQueued || oQueued)
            timeOut = TIMEOUT_COUNTER_Q;
        else
            timeOut--;
        if (timeOut == 0) {
            ASSERT_TRUE(false) << "Wait on Input/Output is found indefinite";
        }
    }
}

// set component role
TEST_F(AudioEncHidlTest, SetRole) {
    description("Test Set Component Role");
    if (disableTest) return;
    android::hardware::media::omx::V1_0::Status status;
    status = setRole(omxNode, gEnv->getRole().c_str());
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

// port format enumeration
TEST_F(AudioEncHidlTest, EnumeratePortFormat) {
    description("Test Component on Mandatory Port Parameters (Port Format)");
    if (disableTest) return;
    android::hardware::media::omx::V1_0::Status status;
    uint32_t kPortIndexInput = 0, kPortIndexOutput = 1;
    status = setRole(omxNode, gEnv->getRole().c_str());
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    OMX_PORT_PARAM_TYPE params;
    status = getParam(omxNode, OMX_IndexParamAudioInit, &params);
    if (status == ::android::hardware::media::omx::V1_0::Status::OK) {
        ASSERT_EQ(params.nPorts, 2U);
        kPortIndexInput = params.nStartPortNumber;
        kPortIndexOutput = kPortIndexInput + 1;
    }
    status = setAudioPortFormat(omxNode, kPortIndexInput, OMX_AUDIO_CodingPCM);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = setAudioPortFormat(omxNode, kPortIndexOutput, eEncoding);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

// test raw stream encode
TEST_F(AudioEncHidlTest, SimpleEncodeTest) {
    description("Tests Basic encoding and EOS");
    if (disableTest) return;
    android::hardware::media::omx::V1_0::Status status;
    uint32_t kPortIndexInput = 0, kPortIndexOutput = 1;
    status = setRole(omxNode, gEnv->getRole().c_str());
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    OMX_PORT_PARAM_TYPE params;
    status = getParam(omxNode, OMX_IndexParamAudioInit, &params);
    if (status == ::android::hardware::media::omx::V1_0::Status::OK) {
        ASSERT_EQ(params.nPorts, 2U);
        kPortIndexInput = params.nStartPortNumber;
        kPortIndexOutput = kPortIndexInput + 1;
    }
    char mURL[512];
    strcpy(mURL, gEnv->getRes().c_str());
    GetURLForComponent(compName, mURL);

    std::ifstream eleStream;

    // Configure input port
    int32_t nChannels = 2;
    int32_t nSampleRate = 44100;
    int32_t samplesPerFrame = 1024;
    int32_t nBitRate = 128000;
    switch (compName) {
        case amrnb:
            nChannels = 1;
            nSampleRate = 8000;
            samplesPerFrame = 160;
            nBitRate = 7400;
            break;
        case amrwb:
            nChannels = 1;
            nSampleRate = 16000;
            samplesPerFrame = 160;
            nBitRate = 15850;
            break;
        case aac:
            nChannels = 2;
            nSampleRate = 48000;
            samplesPerFrame = 1024;
            nBitRate = 128000;
            break;
        case flac:
            nChannels = 2;
            nSampleRate = 48000;
            samplesPerFrame = 1152;
            nBitRate = 128000;
            break;
        default:
            ASSERT_TRUE(false);
    }
    setupPCMPort(omxNode, kPortIndexInput, nChannels, OMX_NumericalDataSigned,
                 16, nSampleRate, OMX_AUDIO_PCMModeLinear);

    // Configure output port
    ASSERT_NO_FATAL_FAILURE(setDefaultPortParam(omxNode, kPortIndexOutput,
                                                eEncoding, compName, nChannels,
                                                nSampleRate, nBitRate));

    android::Vector<BufferInfo> iBuffer, oBuffer;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(changeStateLoadedtoIdle(omxNode, observer, &iBuffer,
                                                    &oBuffer, kPortIndexInput,
                                                    kPortIndexOutput));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));

    eleStream.open(mURL, std::ifstream::binary);
    ASSERT_EQ(eleStream.is_open(), true);
    ASSERT_NO_FATAL_FAILURE(encodeNFrames(omxNode, observer, &iBuffer, &oBuffer,
                                          128, samplesPerFrame, nChannels,
                                          nSampleRate, eleStream));
    eleStream.close();

    ASSERT_NO_FATAL_FAILURE(
        waitOnInputConsumption(omxNode, observer, &iBuffer, &oBuffer));
    ASSERT_NO_FATAL_FAILURE(
        testEOS(omxNode, observer, &iBuffer, &oBuffer, false, eosFlag));
    // set state to idle
    ASSERT_NO_FATAL_FAILURE(
        changeStateExecutetoIdle(omxNode, observer, &iBuffer, &oBuffer));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoLoaded(omxNode, observer, &iBuffer,
                                                    &oBuffer, kPortIndexInput,
                                                    kPortIndexOutput));
}

int main(int argc, char** argv) {
    gEnv = new ComponentTestEnvironment();
    ::testing::AddGlobalTestEnvironment(gEnv);
    ::testing::InitGoogleTest(&argc, argv);
    int status = gEnv->initFromOptions(argc, argv);
    if (status == 0) {
        status = RUN_ALL_TESTS();
        ALOGI("Test result = %d", status);
    }
    return status;
}
