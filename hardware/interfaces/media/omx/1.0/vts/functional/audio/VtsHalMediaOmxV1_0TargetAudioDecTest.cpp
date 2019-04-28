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

#define LOG_TAG "media_omx_hidl_audio_dec_test"
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

// audio decoder test fixture class
class AudioDecHidlTest : public ::testing::VtsHalHidlTargetTestBase {
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
            {"mp3", mp3}, {"amrnb", amrnb},       {"amrwb", amrwb},
            {"aac", aac}, {"vorbis", vorbis},     {"opus", opus},
            {"pcm", pcm}, {"g711alaw", g711alaw}, {"g711mlaw", g711mlaw},
            {"gsm", gsm}, {"raw", raw},           {"flac", flac},
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
            {mp3, OMX_AUDIO_CodingMP3},
            {amrnb, OMX_AUDIO_CodingAMR},
            {amrwb, OMX_AUDIO_CodingAMR},
            {aac, OMX_AUDIO_CodingAAC},
            {vorbis, OMX_AUDIO_CodingVORBIS},
            {pcm, OMX_AUDIO_CodingPCM},
            {opus, (OMX_AUDIO_CODINGTYPE)OMX_AUDIO_CodingAndroidOPUS},
            {g711alaw, OMX_AUDIO_CodingG711},
            {g711mlaw, OMX_AUDIO_CodingG711},
            {gsm, OMX_AUDIO_CodingGSMFR},
            {raw, OMX_AUDIO_CodingPCM},
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
        framesReceived = 0;
        timestampUs = 0;
        timestampDevTest = false;
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
                framesReceived += 1;
                // For decoder components current timestamp always exceeds
                // previous timestamp
                EXPECT_GE(msg.data.extendedBufferData.timestampUs, timestampUs);
                timestampUs = msg.data.extendedBufferData.timestampUs;
                // Test if current timestamp is among the list of queued
                // timestamps
                if (timestampDevTest) {
                    bool tsHit = false;
                    android::List<uint64_t>::iterator it =
                        timestampUslist.begin();
                    while (it != timestampUslist.end()) {
                        if (*it == timestampUs) {
                            timestampUslist.erase(it);
                            tsHit = true;
                            break;
                        }
                        it++;
                    }
                    if (tsHit == false) {
                        if (timestampUslist.empty() == false) {
                            EXPECT_EQ(tsHit, true)
                                << "TimeStamp not recognized";
                        } else {
                            std::cout << "[   INFO   ] Received non-zero "
                                         "output / TimeStamp not recognized \n";
                        }
                    }
                }
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
        mp3,
        amrnb,
        amrwb,
        aac,
        vorbis,
        opus,
        pcm,
        g711alaw,
        g711mlaw,
        gsm,
        raw,
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
    uint32_t framesReceived;
    uint64_t timestampUs;
    ::android::List<uint64_t> timestampUslist;
    bool timestampDevTest;

   protected:
    static void description(const std::string& description) {
        RecordProperty("description", description);
    }
};

// Set Default port param.
void setDefaultPortParam(
    sp<IOmxNode> omxNode, OMX_U32 portIndex, OMX_AUDIO_CODINGTYPE eEncoding,
    int32_t nChannels = 2, int32_t nSampleRate = 44100,
    OMX_AUDIO_PCMMODETYPE ePCMMode = OMX_AUDIO_PCMModeLinear,
    OMX_NUMERICALDATATYPE eNumData = OMX_NumericalDataSigned,
    int32_t nBitPerSample = 16) {
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

    switch ((int)eEncoding) {
        case OMX_AUDIO_CodingPCM:
            setupPCMPort(omxNode, portIndex, nChannels, eNumData, nBitPerSample,
                         nSampleRate, ePCMMode);
            break;
        case OMX_AUDIO_CodingAAC:
            setupAACPort(omxNode, portIndex, OMX_AUDIO_AACObjectNull,
                         OMX_AUDIO_AACStreamFormatMP4FF, nChannels, 0,
                         nSampleRate);
        default:
            break;
    }
}

// In decoder components, often the input port parameters get updated upon
// parsing the header of elementary stream. Client needs to collect this
// information to reconfigure other ports that share data with this input
// port.
void getInputChannelInfo(sp<IOmxNode> omxNode, OMX_U32 kPortIndexInput,
                         OMX_AUDIO_CODINGTYPE eEncoding, int32_t* nChannels,
                         int32_t* nSampleRate) {
    android::hardware::media::omx::V1_0::Status status;
    *nChannels = 0;
    *nSampleRate = 0;

    switch ((int)eEncoding) {
        case OMX_AUDIO_CodingGSMFR:
        case OMX_AUDIO_CodingG711:
        case OMX_AUDIO_CodingPCM: {
            OMX_AUDIO_PARAM_PCMMODETYPE param;
            status = getPortParam(omxNode, OMX_IndexParamAudioPcm,
                                  kPortIndexInput, &param);
            ASSERT_EQ(status,
                      ::android::hardware::media::omx::V1_0::Status::OK);
            *nChannels = param.nChannels;
            *nSampleRate = param.nSamplingRate;
            break;
        }
        case OMX_AUDIO_CodingMP3: {
            OMX_AUDIO_PARAM_MP3TYPE param;
            status = getPortParam(omxNode, OMX_IndexParamAudioMp3,
                                  kPortIndexInput, &param);
            ASSERT_EQ(status,
                      ::android::hardware::media::omx::V1_0::Status::OK);
            *nChannels = param.nChannels;
            *nSampleRate = param.nSampleRate;
            break;
        }
        case OMX_AUDIO_CodingAndroidOPUS: {
            OMX_AUDIO_PARAM_ANDROID_OPUSTYPE param;
            status = getPortParam(omxNode,
                                  (OMX_INDEXTYPE)OMX_IndexParamAudioAndroidOpus,
                                  kPortIndexInput, &param);
            ASSERT_EQ(status,
                      ::android::hardware::media::omx::V1_0::Status::OK);
            *nChannels = param.nChannels;
            *nSampleRate = param.nSampleRate;
            break;
        }
        case OMX_AUDIO_CodingVORBIS: {
            OMX_AUDIO_PARAM_VORBISTYPE param;
            status = getPortParam(omxNode, OMX_IndexParamAudioVorbis,
                                  kPortIndexInput, &param);
            ASSERT_EQ(status,
                      ::android::hardware::media::omx::V1_0::Status::OK);
            *nChannels = param.nChannels;
            *nSampleRate = param.nSampleRate;
            break;
        }
        case OMX_AUDIO_CodingAMR: {
            OMX_AUDIO_PARAM_AMRTYPE param;
            status = getPortParam(omxNode, OMX_IndexParamAudioAmr,
                                  kPortIndexInput, &param);
            ASSERT_EQ(status,
                      ::android::hardware::media::omx::V1_0::Status::OK);
            *nChannels = param.nChannels;
            // NOTE: For amrnb sample rate is 8k and amrwb sample rate is 16k.
            // There is no nSampleRate field in OMX_AUDIO_PARAM_AMRTYPE. Just
            // return 8k to avoid returning uninit variable.
            *nSampleRate = 8000;
            break;
        }
        case OMX_AUDIO_CodingAAC: {
            OMX_AUDIO_PARAM_AACPROFILETYPE param;
            status = getPortParam(omxNode, OMX_IndexParamAudioAac,
                                  kPortIndexInput, &param);
            ASSERT_EQ(status,
                      ::android::hardware::media::omx::V1_0::Status::OK);
            *nChannels = param.nChannels;
            *nSampleRate = param.nSampleRate;
            break;
        }
        case OMX_AUDIO_CodingFLAC: {
            OMX_AUDIO_PARAM_FLACTYPE param;
            status = getPortParam(omxNode, OMX_IndexParamAudioFlac,
                                  kPortIndexInput, &param);
            ASSERT_EQ(status,
                      ::android::hardware::media::omx::V1_0::Status::OK);
            *nChannels = param.nChannels;
            *nSampleRate = param.nSampleRate;
            break;
        }
        default:
            ASSERT_TRUE(false);
            break;
    }
}

// LookUpTable of clips and metadata for component testing
void GetURLForComponent(AudioDecHidlTest::standardComp comp, char* mURL,
                        char* info) {
    struct CompToURL {
        AudioDecHidlTest::standardComp comp;
        const char* mURL;
        const char* info;
    };
    static const CompToURL kCompToURL[] = {
        {AudioDecHidlTest::standardComp::mp3,
         "bbb_mp3_stereo_192kbps_48000hz.mp3",
         "bbb_mp3_stereo_192kbps_48000hz.info"},
        {AudioDecHidlTest::standardComp::aac,
         "bbb_aac_stereo_128kbps_48000hz.aac",
         "bbb_aac_stereo_128kbps_48000hz.info"},
        {AudioDecHidlTest::standardComp::amrnb,
         "sine_amrnb_1ch_12kbps_8000hz.amrnb",
         "sine_amrnb_1ch_12kbps_8000hz.info"},
        {AudioDecHidlTest::standardComp::amrwb,
         "bbb_amrwb_1ch_14kbps_16000hz.amrwb",
         "bbb_amrwb_1ch_14kbps_16000hz.info"},
        {AudioDecHidlTest::standardComp::vorbis,
         "bbb_vorbis_stereo_128kbps_48000hz.vorbis",
         "bbb_vorbis_stereo_128kbps_48000hz.info"},
        {AudioDecHidlTest::standardComp::opus,
         "bbb_opus_stereo_128kbps_48000hz.opus",
         "bbb_opus_stereo_128kbps_48000hz.info"},
        {AudioDecHidlTest::standardComp::g711alaw, "bbb_g711alaw_1ch_8khz.raw",
         "bbb_g711alaw_1ch_8khz.info"},
        {AudioDecHidlTest::standardComp::g711mlaw, "bbb_g711mulaw_1ch_8khz.raw",
         "bbb_g711mulaw_1ch_8khz.info"},
        {AudioDecHidlTest::standardComp::gsm, "bbb_gsm_1ch_8khz_13kbps.raw",
         "bbb_gsm_1ch_8khz_13kbps.info"},
        {AudioDecHidlTest::standardComp::raw, "bbb_raw_1ch_8khz_s32le.raw",
         "bbb_raw_1ch_8khz_s32le.info"},
        {AudioDecHidlTest::standardComp::flac,
         "bbb_flac_stereo_680kbps_48000hz.flac",
         "bbb_flac_stereo_680kbps_48000hz.info"},
    };

    for (size_t i = 0; i < sizeof(kCompToURL) / sizeof(kCompToURL[0]); ++i) {
        if (kCompToURL[i].comp == comp) {
            strcat(mURL, kCompToURL[i].mURL);
            strcat(info, kCompToURL[i].info);
            return;
        }
    }
}

// port settings reconfiguration during runtime. reconfigures sample rate and
// number
typedef struct {
    OMX_AUDIO_CODINGTYPE eEncoding;
    AudioDecHidlTest::standardComp comp;
} packedArgs;
void portReconfiguration(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
                         android::Vector<BufferInfo>* iBuffer,
                         android::Vector<BufferInfo>* oBuffer,
                         OMX_U32 kPortIndexInput, OMX_U32 kPortIndexOutput,
                         Message msg, PortMode oPortMode, void* args) {
    android::hardware::media::omx::V1_0::Status status;
    packedArgs* audioArgs = static_cast<packedArgs*>(args);
    OMX_AUDIO_CODINGTYPE eEncoding = audioArgs->eEncoding;
    AudioDecHidlTest::standardComp comp = audioArgs->comp;
    (void)oPortMode;

    if (msg.data.eventData.event == OMX_EventPortSettingsChanged) {
        ASSERT_EQ(msg.data.eventData.data1, kPortIndexOutput);

        status = omxNode->sendCommand(toRawCommandType(OMX_CommandPortDisable),
                                      kPortIndexOutput);
        ASSERT_EQ(status, android::hardware::media::omx::V1_0::Status::OK);

        status =
            observer->dequeueMessage(&msg, DEFAULT_TIMEOUT, iBuffer, oBuffer);
        if (status == android::hardware::media::omx::V1_0::Status::TIMED_OUT) {
            for (size_t i = 0; i < oBuffer->size(); ++i) {
                // test if client got all its buffers back
                EXPECT_EQ((*oBuffer)[i].owner, client);
                // free the buffers
                status =
                    omxNode->freeBuffer(kPortIndexOutput, (*oBuffer)[i].id);
                ASSERT_EQ(status,
                          android::hardware::media::omx::V1_0::Status::OK);
            }
            status = observer->dequeueMessage(&msg, DEFAULT_TIMEOUT, iBuffer,
                                              oBuffer);
            ASSERT_EQ(status, android::hardware::media::omx::V1_0::Status::OK);
            ASSERT_EQ(msg.type, Message::Type::EVENT);
            ASSERT_EQ(msg.data.eventData.event, OMX_EventCmdComplete);
            ASSERT_EQ(msg.data.eventData.data1, OMX_CommandPortDisable);
            ASSERT_EQ(msg.data.eventData.data2, kPortIndexOutput);

            // set Port Params
            int32_t nChannels;
            int32_t nSampleRate;
            ASSERT_NO_FATAL_FAILURE(getInputChannelInfo(
                omxNode, kPortIndexInput, eEncoding, &nChannels, &nSampleRate));
            // Configure output port
            // SPECIAL CASE: Soft Vorbis, Opus and Raw Decoders do not offer way
            // to configure output PCM port. The port undergoes auto
            // configuration internally basing on parsed elementary stream
            // information.
            if (comp != AudioDecHidlTest::standardComp::vorbis &&
                comp != AudioDecHidlTest::standardComp::opus &&
                comp != AudioDecHidlTest::standardComp::raw) {
                setDefaultPortParam(omxNode, kPortIndexOutput,
                                    OMX_AUDIO_CodingPCM, nChannels,
                                    nSampleRate);
            }

            // If you can disable a port, then you should be able to enable it
            // as well
            status = omxNode->sendCommand(
                toRawCommandType(OMX_CommandPortEnable), kPortIndexOutput);
            ASSERT_EQ(status, android::hardware::media::omx::V1_0::Status::OK);

            // do not enable the port until all the buffers are supplied
            status = observer->dequeueMessage(&msg, DEFAULT_TIMEOUT, iBuffer,
                                              oBuffer);
            ASSERT_EQ(status,
                      android::hardware::media::omx::V1_0::Status::TIMED_OUT);

            ASSERT_NO_FATAL_FAILURE(
                allocatePortBuffers(omxNode, oBuffer, kPortIndexOutput));
            status = observer->dequeueMessage(&msg, DEFAULT_TIMEOUT, iBuffer,
                                              oBuffer);
            ASSERT_EQ(status, android::hardware::media::omx::V1_0::Status::OK);
            ASSERT_EQ(msg.type, Message::Type::EVENT);
            ASSERT_EQ(msg.data.eventData.data1, OMX_CommandPortEnable);
            ASSERT_EQ(msg.data.eventData.data2, kPortIndexOutput);

            // dispatch output buffers
            for (size_t i = 0; i < oBuffer->size(); i++) {
                ASSERT_NO_FATAL_FAILURE(
                    dispatchOutputBuffer(omxNode, oBuffer, i));
            }
        } else {
            ASSERT_TRUE(false);
        }
    } else {
        ASSERT_TRUE(false);
    }
}

// blocking call to ensures application to Wait till all the inputs are consumed
void waitOnInputConsumption(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
                            android::Vector<BufferInfo>* iBuffer,
                            android::Vector<BufferInfo>* oBuffer,
                            OMX_AUDIO_CODINGTYPE eEncoding,
                            OMX_U32 kPortIndexInput, OMX_U32 kPortIndexOutput,
                            AudioDecHidlTest::standardComp comp) {
    android::hardware::media::omx::V1_0::Status status;
    Message msg;
    int timeOut = TIMEOUT_COUNTER_Q;

    while (timeOut--) {
        size_t i = 0;
        status =
            observer->dequeueMessage(&msg, DEFAULT_TIMEOUT_Q, iBuffer, oBuffer);
        if (status == android::hardware::media::omx::V1_0::Status::OK) {
            ASSERT_EQ(msg.type, Message::Type::EVENT);
            packedArgs audioArgs = {eEncoding, comp};
            ASSERT_NO_FATAL_FAILURE(
                portReconfiguration(omxNode, observer, iBuffer, oBuffer,
                                    kPortIndexInput, kPortIndexOutput, msg,
                                    PortMode::PRESET_BYTE_BUFFER, &audioArgs));
        }
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

// Decode N Frames
void decodeNFrames(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
                   android::Vector<BufferInfo>* iBuffer,
                   android::Vector<BufferInfo>* oBuffer,
                   OMX_AUDIO_CODINGTYPE eEncoding, OMX_U32 kPortIndexInput,
                   OMX_U32 kPortIndexOutput, std::ifstream& eleStream,
                   android::Vector<FrameData>* Info, int offset, int range,
                   AudioDecHidlTest::standardComp comp, bool signalEOS = true) {
    android::hardware::media::omx::V1_0::Status status;
    Message msg;
    size_t index;
    uint32_t flags = 0;
    int frameID = offset;
    int timeOut = TIMEOUT_COUNTER_Q;
    bool iQueued, oQueued;

    while (1) {
        iQueued = oQueued = false;
        status =
            observer->dequeueMessage(&msg, DEFAULT_TIMEOUT_Q, iBuffer, oBuffer);
        // Port Reconfiguration
        if (status == android::hardware::media::omx::V1_0::Status::OK &&
            msg.type == Message::Type::EVENT) {
            packedArgs audioArgs = {eEncoding, comp};
            ASSERT_NO_FATAL_FAILURE(
                portReconfiguration(omxNode, observer, iBuffer, oBuffer,
                                    kPortIndexInput, kPortIndexOutput, msg,
                                    PortMode::PRESET_BYTE_BUFFER, &audioArgs));
        }

        if (frameID == (int)Info->size() || frameID == (offset + range)) break;

        // Dispatch input buffer
        if ((index = getEmptyBufferID(iBuffer)) < iBuffer->size()) {
            char* ipBuffer = static_cast<char*>(
                static_cast<void*>((*iBuffer)[index].mMemory->getPointer()));
            ASSERT_LE((*Info)[frameID].bytesCount,
                      static_cast<int>((*iBuffer)[index].mMemory->getSize()));
            eleStream.read(ipBuffer, (*Info)[frameID].bytesCount);
            ASSERT_EQ(eleStream.gcount(), (*Info)[frameID].bytesCount);
            flags = (*Info)[frameID].flags;
            // Indicate to omx core that the buffer contains a full frame worth
            // of data
            flags |= OMX_BUFFERFLAG_ENDOFFRAME;
            // Indicate the omx core that this is the last buffer it needs to
            // process
            if (signalEOS && ((frameID == (int)Info->size() - 1) ||
                              (frameID == (offset + range - 1))))
                flags |= OMX_BUFFERFLAG_EOS;
            ASSERT_NO_FATAL_FAILURE(dispatchInputBuffer(
                omxNode, iBuffer, index, (*Info)[frameID].bytesCount, flags,
                (*Info)[frameID].timestamp));
            frameID++;
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
TEST_F(AudioDecHidlTest, SetRole) {
    description("Test Set Component Role");
    if (disableTest) return;
    android::hardware::media::omx::V1_0::Status status;
    status = setRole(omxNode, gEnv->getRole().c_str());
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

// port format enumeration
TEST_F(AudioDecHidlTest, EnumeratePortFormat) {
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
    status = setAudioPortFormat(omxNode, kPortIndexInput, eEncoding);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = setAudioPortFormat(omxNode, kPortIndexOutput, OMX_AUDIO_CodingPCM);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

// test port settings reconfiguration, elementary stream decode and timestamp
// deviation
TEST_F(AudioDecHidlTest, DecodeTest) {
    description("Tests Port Reconfiguration, Decode and timestamp deviation");
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
    char mURL[512], info[512];
    strcpy(mURL, gEnv->getRes().c_str());
    strcpy(info, gEnv->getRes().c_str());
    GetURLForComponent(compName, mURL, info);

    std::ifstream eleStream, eleInfo;

    eleInfo.open(info);
    ASSERT_EQ(eleInfo.is_open(), true);
    android::Vector<FrameData> Info;
    int bytesCount = 0;
    uint32_t flags = 0;
    uint32_t timestamp = 0;
    timestampDevTest = false;
    while (1) {
        if (!(eleInfo >> bytesCount)) break;
        eleInfo >> flags;
        eleInfo >> timestamp;
        Info.push_back({bytesCount, flags, timestamp});
        if (timestampDevTest && (flags != OMX_BUFFERFLAG_CODECCONFIG))
            timestampUslist.push_back(timestamp);
    }
    eleInfo.close();

    int32_t nChannels, nSampleRate;
    // Configure input port
    setDefaultPortParam(omxNode, kPortIndexInput, eEncoding);
    if (compName == raw)
        setDefaultPortParam(omxNode, kPortIndexInput, eEncoding, 1, 8000,
                            OMX_AUDIO_PCMModeLinear, OMX_NumericalDataSigned,
                            32);
    ASSERT_NO_FATAL_FAILURE(getInputChannelInfo(
        omxNode, kPortIndexInput, eEncoding, &nChannels, &nSampleRate));
    // Configure output port
    // SPECIAL CASE: Soft Vorbis, Opus and Raw Decoders do not offer way to
    // configure output PCM port. The port undergoes auto configuration
    // internally basing on parsed elementary stream information.
    if (compName != vorbis && compName != opus && compName != raw) {
        setDefaultPortParam(omxNode, kPortIndexOutput, OMX_AUDIO_CodingPCM,
                            nChannels, nSampleRate);
    }

    android::Vector<BufferInfo> iBuffer, oBuffer;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(changeStateLoadedtoIdle(omxNode, observer, &iBuffer,
                                                    &oBuffer, kPortIndexInput,
                                                    kPortIndexOutput));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));
    // Port Reconfiguration
    eleStream.open(mURL, std::ifstream::binary);
    ASSERT_EQ(eleStream.is_open(), true);
    ASSERT_NO_FATAL_FAILURE(decodeNFrames(
        omxNode, observer, &iBuffer, &oBuffer, eEncoding, kPortIndexInput,
        kPortIndexOutput, eleStream, &Info, 0, (int)Info.size(), compName));
    eleStream.close();
    ASSERT_NO_FATAL_FAILURE(
        waitOnInputConsumption(omxNode, observer, &iBuffer, &oBuffer, eEncoding,
                               kPortIndexInput, kPortIndexOutput, compName));
    packedArgs audioArgs = {eEncoding, compName};
    ASSERT_NO_FATAL_FAILURE(testEOS(
        omxNode, observer, &iBuffer, &oBuffer, false, eosFlag, nullptr,
        portReconfiguration, kPortIndexInput, kPortIndexOutput, &audioArgs));
    if (timestampDevTest) EXPECT_EQ(timestampUslist.empty(), true);
    // set state to idle
    ASSERT_NO_FATAL_FAILURE(
        changeStateExecutetoIdle(omxNode, observer, &iBuffer, &oBuffer));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoLoaded(omxNode, observer, &iBuffer,
                                                    &oBuffer, kPortIndexInput,
                                                    kPortIndexOutput));
}

// end of sequence test
TEST_F(AudioDecHidlTest, EOSTest_M) {
    description("Test end of stream monkeying");
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

    int32_t nChannels, nSampleRate;
    // Configure input port
    setDefaultPortParam(omxNode, kPortIndexInput, eEncoding);
    if (compName == raw)
        setDefaultPortParam(omxNode, kPortIndexInput, eEncoding, 1, 8000,
                            OMX_AUDIO_PCMModeLinear, OMX_NumericalDataSigned,
                            32);
    ASSERT_NO_FATAL_FAILURE(getInputChannelInfo(
        omxNode, kPortIndexInput, eEncoding, &nChannels, &nSampleRate));
    // Configure output port
    // SPECIAL CASE: Soft Vorbis, Opus and Raw Decoders do not offer way to
    // configure output PCM port. The port undergoes auto configuration
    // internally basing on parsed elementary stream information.
    if (compName != vorbis && compName != opus && compName != raw) {
        setDefaultPortParam(omxNode, kPortIndexOutput, OMX_AUDIO_CodingPCM,
                            nChannels, nSampleRate);
    }

    android::Vector<BufferInfo> iBuffer, oBuffer;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(changeStateLoadedtoIdle(omxNode, observer, &iBuffer,
                                                    &oBuffer, kPortIndexInput,
                                                    kPortIndexOutput));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));

    // request EOS at the start
    packedArgs audioArgs = {eEncoding, compName};
    ASSERT_NO_FATAL_FAILURE(testEOS(
        omxNode, observer, &iBuffer, &oBuffer, true, eosFlag, nullptr,
        portReconfiguration, kPortIndexInput, kPortIndexOutput, &audioArgs));
    ASSERT_NO_FATAL_FAILURE(flushPorts(omxNode, observer, &iBuffer, &oBuffer,
                                       kPortIndexInput, kPortIndexOutput));
    EXPECT_GE(framesReceived, 0U);
    framesReceived = 0;
    timestampUs = 0;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(
        changeStateExecutetoIdle(omxNode, observer, &iBuffer, &oBuffer));

    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoLoaded(omxNode, observer, &iBuffer,
                                                    &oBuffer, kPortIndexInput,
                                                    kPortIndexOutput));
}

// end of sequence test
TEST_F(AudioDecHidlTest, ThumbnailTest) {
    description("Test Request for thumbnail");
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
    char mURL[512], info[512];
    strcpy(mURL, gEnv->getRes().c_str());
    strcpy(info, gEnv->getRes().c_str());
    GetURLForComponent(compName, mURL, info);

    std::ifstream eleStream, eleInfo;

    eleInfo.open(info);
    ASSERT_EQ(eleInfo.is_open(), true);
    android::Vector<FrameData> Info;
    int bytesCount = 0;
    uint32_t flags = 0;
    uint32_t timestamp = 0;
    while (1) {
        if (!(eleInfo >> bytesCount)) break;
        eleInfo >> flags;
        eleInfo >> timestamp;
        Info.push_back({bytesCount, flags, timestamp});
    }
    eleInfo.close();

    int32_t nChannels, nSampleRate;
    // Configure input port
    setDefaultPortParam(omxNode, kPortIndexInput, eEncoding);
    if (compName == raw)
        setDefaultPortParam(omxNode, kPortIndexInput, eEncoding, 1, 8000,
                            OMX_AUDIO_PCMModeLinear, OMX_NumericalDataSigned,
                            32);
    ASSERT_NO_FATAL_FAILURE(getInputChannelInfo(
        omxNode, kPortIndexInput, eEncoding, &nChannels, &nSampleRate));
    // Configure output port
    // SPECIAL CASE: Soft Vorbis, Opus and Raw Decoders do not offer way to
    // configure output PCM port. The port undergoes auto configuration
    // internally basing on parsed elementary stream information.
    if (compName != vorbis && compName != opus && compName != raw) {
        setDefaultPortParam(omxNode, kPortIndexOutput, OMX_AUDIO_CodingPCM,
                            nChannels, nSampleRate);
    }

    android::Vector<BufferInfo> iBuffer, oBuffer;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(changeStateLoadedtoIdle(omxNode, observer, &iBuffer,
                                                    &oBuffer, kPortIndexInput,
                                                    kPortIndexOutput));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));

    // request EOS for thumbnail
    // signal EOS flag with last frame
    size_t i = 0;
    while (!(Info[i].flags & OMX_BUFFERFLAG_SYNCFRAME)) i++;
    eleStream.open(mURL, std::ifstream::binary);
    ASSERT_EQ(eleStream.is_open(), true);
    ASSERT_NO_FATAL_FAILURE(decodeNFrames(
        omxNode, observer, &iBuffer, &oBuffer, eEncoding, kPortIndexInput,
        kPortIndexOutput, eleStream, &Info, 0, i + 1, compName));
    eleStream.close();
    ASSERT_NO_FATAL_FAILURE(
        waitOnInputConsumption(omxNode, observer, &iBuffer, &oBuffer, eEncoding,
                               kPortIndexInput, kPortIndexOutput, compName));
    packedArgs audioArgs = {eEncoding, compName};
    ASSERT_NO_FATAL_FAILURE(testEOS(
        omxNode, observer, &iBuffer, &oBuffer, false, eosFlag, nullptr,
        portReconfiguration, kPortIndexInput, kPortIndexOutput, &audioArgs));
    ASSERT_NO_FATAL_FAILURE(flushPorts(omxNode, observer, &iBuffer, &oBuffer,
                                       kPortIndexInput, kPortIndexOutput));
    EXPECT_GE(framesReceived, 1U);
    framesReceived = 0;
    timestampUs = 0;

    // signal EOS flag after last frame
    eleStream.open(mURL, std::ifstream::binary);
    ASSERT_EQ(eleStream.is_open(), true);
    ASSERT_NO_FATAL_FAILURE(decodeNFrames(
        omxNode, observer, &iBuffer, &oBuffer, eEncoding, kPortIndexInput,
        kPortIndexOutput, eleStream, &Info, 0, i + 1, compName, false));
    eleStream.close();
    ASSERT_NO_FATAL_FAILURE(
        waitOnInputConsumption(omxNode, observer, &iBuffer, &oBuffer, eEncoding,
                               kPortIndexInput, kPortIndexOutput, compName));
    ASSERT_NO_FATAL_FAILURE(testEOS(
        omxNode, observer, &iBuffer, &oBuffer, true, eosFlag, nullptr,
        portReconfiguration, kPortIndexInput, kPortIndexOutput, &audioArgs));
    ASSERT_NO_FATAL_FAILURE(flushPorts(omxNode, observer, &iBuffer, &oBuffer,
                                       kPortIndexInput, kPortIndexOutput));
    EXPECT_GE(framesReceived, 1U);
    framesReceived = 0;
    timestampUs = 0;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(
        changeStateExecutetoIdle(omxNode, observer, &iBuffer, &oBuffer));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoLoaded(omxNode, observer, &iBuffer,
                                                    &oBuffer, kPortIndexInput,
                                                    kPortIndexOutput));
}

// end of sequence test
TEST_F(AudioDecHidlTest, SimpleEOSTest) {
    description("Test end of stream");
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
    char mURL[512], info[512];
    strcpy(mURL, gEnv->getRes().c_str());
    strcpy(info, gEnv->getRes().c_str());
    GetURLForComponent(compName, mURL, info);

    std::ifstream eleStream, eleInfo;

    eleInfo.open(info);
    ASSERT_EQ(eleInfo.is_open(), true);
    android::Vector<FrameData> Info;
    int bytesCount = 0;
    uint32_t flags = 0;
    uint32_t timestamp = 0;
    while (1) {
        if (!(eleInfo >> bytesCount)) break;
        eleInfo >> flags;
        eleInfo >> timestamp;
        Info.push_back({bytesCount, flags, timestamp});
    }
    eleInfo.close();

    int32_t nChannels, nSampleRate;
    // Configure input port
    setDefaultPortParam(omxNode, kPortIndexInput, eEncoding);
    if (compName == raw)
        setDefaultPortParam(omxNode, kPortIndexInput, eEncoding, 1, 8000,
                            OMX_AUDIO_PCMModeLinear, OMX_NumericalDataSigned,
                            32);
    ASSERT_NO_FATAL_FAILURE(getInputChannelInfo(
        omxNode, kPortIndexInput, eEncoding, &nChannels, &nSampleRate));
    // Configure output port
    // SPECIAL CASE: Soft Vorbis, Opus and Raw Decoders do not offer way to
    // configure output PCM port. The port undergoes auto configuration
    // internally basing on parsed elementary stream information.
    if (compName != vorbis && compName != opus && compName != raw) {
        setDefaultPortParam(omxNode, kPortIndexOutput, OMX_AUDIO_CodingPCM,
                            nChannels, nSampleRate);
    }

    android::Vector<BufferInfo> iBuffer, oBuffer;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(changeStateLoadedtoIdle(omxNode, observer, &iBuffer,
                                                    &oBuffer, kPortIndexInput,
                                                    kPortIndexOutput));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));

    // request EOS at the end
    eleStream.open(mURL, std::ifstream::binary);
    ASSERT_EQ(eleStream.is_open(), true);
    ASSERT_NO_FATAL_FAILURE(decodeNFrames(omxNode, observer, &iBuffer, &oBuffer,
                                          eEncoding, kPortIndexInput,
                                          kPortIndexOutput, eleStream, &Info, 0,
                                          (int)Info.size(), compName, false));
    eleStream.close();
    ASSERT_NO_FATAL_FAILURE(
        waitOnInputConsumption(omxNode, observer, &iBuffer, &oBuffer, eEncoding,
                               kPortIndexInput, kPortIndexOutput, compName));
    packedArgs audioArgs = {eEncoding, compName};
    ASSERT_NO_FATAL_FAILURE(testEOS(
        omxNode, observer, &iBuffer, &oBuffer, true, eosFlag, nullptr,
        portReconfiguration, kPortIndexInput, kPortIndexOutput, &audioArgs));
    ASSERT_NO_FATAL_FAILURE(flushPorts(omxNode, observer, &iBuffer, &oBuffer,
                                       kPortIndexInput, kPortIndexOutput));
    framesReceived = 0;
    timestampUs = 0;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(
        changeStateExecutetoIdle(omxNode, observer, &iBuffer, &oBuffer));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoLoaded(omxNode, observer, &iBuffer,
                                                    &oBuffer, kPortIndexInput,
                                                    kPortIndexOutput));
}

// test input/output port flush
TEST_F(AudioDecHidlTest, FlushTest) {
    description("Test Flush");
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
    char mURL[512], info[512];
    strcpy(mURL, gEnv->getRes().c_str());
    strcpy(info, gEnv->getRes().c_str());
    GetURLForComponent(compName, mURL, info);

    std::ifstream eleStream, eleInfo;

    eleInfo.open(info);
    ASSERT_EQ(eleInfo.is_open(), true);
    android::Vector<FrameData> Info;
    int bytesCount = 0;
    uint32_t flags = 0;
    uint32_t timestamp = 0;
    while (1) {
        if (!(eleInfo >> bytesCount)) break;
        eleInfo >> flags;
        eleInfo >> timestamp;
        Info.push_back({bytesCount, flags, timestamp});
    }
    eleInfo.close();

    int32_t nChannels, nSampleRate;
    // Configure input port
    setDefaultPortParam(omxNode, kPortIndexInput, eEncoding);
    if (compName == raw)
        setDefaultPortParam(omxNode, kPortIndexInput, eEncoding, 1, 8000,
                            OMX_AUDIO_PCMModeLinear, OMX_NumericalDataSigned,
                            32);
    ASSERT_NO_FATAL_FAILURE(getInputChannelInfo(
        omxNode, kPortIndexInput, eEncoding, &nChannels, &nSampleRate));
    // Configure output port
    // SPECIAL CASE: Soft Vorbis, Opus and Raw Decoders do not offer way to
    // configure output PCM port. The port undergoes auto configuration
    // internally basing on parsed elementary stream information.
    if (compName != vorbis && compName != opus && compName != raw) {
        setDefaultPortParam(omxNode, kPortIndexOutput, OMX_AUDIO_CodingPCM,
                            nChannels, nSampleRate);
    }

    android::Vector<BufferInfo> iBuffer, oBuffer;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(changeStateLoadedtoIdle(omxNode, observer, &iBuffer,
                                                    &oBuffer, kPortIndexInput,
                                                    kPortIndexOutput));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));

    // Decode 128 frames and flush. here 128 is chosen to ensure there is a key
    // frame after this so that the below section can be convered for all
    // components
    int nFrames = 128;
    eleStream.open(mURL, std::ifstream::binary);
    ASSERT_EQ(eleStream.is_open(), true);
    ASSERT_NO_FATAL_FAILURE(decodeNFrames(
        omxNode, observer, &iBuffer, &oBuffer, eEncoding, kPortIndexInput,
        kPortIndexOutput, eleStream, &Info, 0, nFrames, compName, false));
    ASSERT_NO_FATAL_FAILURE(flushPorts(omxNode, observer, &iBuffer, &oBuffer,
                                       kPortIndexInput, kPortIndexOutput));
    framesReceived = 0;

    // Seek to next key frame and start decoding till the end
    int index = nFrames;
    bool keyFrame = false;
    while (index < (int)Info.size()) {
        if ((Info[index].flags & OMX_BUFFERFLAG_SYNCFRAME) ==
            OMX_BUFFERFLAG_SYNCFRAME) {
            timestampUs = Info[index - 1].timestamp;
            keyFrame = true;
            break;
        }
        eleStream.ignore(Info[index].bytesCount);
        index++;
    }
    if (keyFrame) {
        ASSERT_NO_FATAL_FAILURE(
            decodeNFrames(omxNode, observer, &iBuffer, &oBuffer, eEncoding,
                          kPortIndexInput, kPortIndexOutput, eleStream, &Info,
                          index, Info.size() - index, compName, false));
    }
    ASSERT_NO_FATAL_FAILURE(flushPorts(omxNode, observer, &iBuffer, &oBuffer,
                                       kPortIndexInput, kPortIndexOutput));
    framesReceived = 0;

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
