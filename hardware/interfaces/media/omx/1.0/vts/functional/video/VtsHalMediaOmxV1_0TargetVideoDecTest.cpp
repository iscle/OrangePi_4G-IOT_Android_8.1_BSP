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

#define LOG_TAG "media_omx_hidl_video_dec_test"
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
#include <media/hardware/HardwareAPI.h>
#include <media_hidl_test_common.h>
#include <media_video_hidl_test_common.h>
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

// video decoder test fixture class
class VideoDecHidlTest : public ::testing::VtsHalHidlTargetTestBase {
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
            {"h263", h263}, {"avc", avc}, {"mpeg2", mpeg2}, {"mpeg4", mpeg4},
            {"hevc", hevc}, {"vp8", vp8}, {"vp9", vp9},
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
        struct CompToCompression {
            standardComp CompName;
            OMX_VIDEO_CODINGTYPE eCompressionFormat;
        };
        static const CompToCompression kCompToCompression[] = {
            {h263, OMX_VIDEO_CodingH263},   {avc, OMX_VIDEO_CodingAVC},
            {mpeg2, OMX_VIDEO_CodingMPEG2}, {mpeg4, OMX_VIDEO_CodingMPEG4},
            {hevc, OMX_VIDEO_CodingHEVC},   {vp8, OMX_VIDEO_CodingVP8},
            {vp9, OMX_VIDEO_CodingVP9},
        };
        static const size_t kNumCompToCompression =
            sizeof(kCompToCompression) / sizeof(kCompToCompression[0]);
        size_t i;
        for (i = 0; i < kNumCompToCompression; ++i) {
            if (kCompToCompression[i].CompName == compName) {
                eCompressionFormat = kCompToCompression[i].eCompressionFormat;
                break;
            }
        }
        if (i == kNumCompToCompression) disableTest = true;
        portMode[0] = portMode[1] = PortMode::PRESET_BYTE_BUFFER;
        eosFlag = false;
        framesReceived = 0;
        timestampUs = 0;
        timestampDevTest = false;
        isSecure = false;
        portSettingsChange = false;
        size_t suffixLen = strlen(".secure");
        if (strlen(gEnv->getComponent().c_str()) >= suffixLen) {
            isSecure =
                !strcmp(gEnv->getComponent().c_str() +
                            strlen(gEnv->getComponent().c_str()) - suffixLen,
                        ".secure");
        }
        if (isSecure) disableTest = true;
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
                if (ofp != nullptr &&
                    portMode[1] == PortMode::PRESET_BYTE_BUFFER) {
                    fwrite(static_cast<void*>(buffer->mMemory->getPointer()),
                           sizeof(char),
                           msg.data.extendedBufferData.rangeLength, ofp);
                    fclose(ofp);
                    count++;
                }
#endif
            }
        } else if (msg.type == Message::Type::EVENT) {
            if (msg.data.eventData.event == OMX_EventPortSettingsChanged) {
                if ((msg.data.eventData.data2 == OMX_IndexParamPortDefinition ||
                     msg.data.eventData.data2 == 0)) {
                    portSettingsChange = true;
                }
            }
        }
    }

    enum standardComp {
        h263,
        avc,
        mpeg2,
        mpeg4,
        hevc,
        vp8,
        vp9,
        unknown_comp,
    };

    sp<IOmx> omx;
    sp<CodecObserver> observer;
    sp<IOmxNode> omxNode;
    standardComp compName;
    OMX_VIDEO_CODINGTYPE eCompressionFormat;
    bool disableTest;
    PortMode portMode[2];
    bool eosFlag;
    uint32_t framesReceived;
    uint64_t timestampUs;
    ::android::List<uint64_t> timestampUslist;
    bool timestampDevTest;
    bool isSecure;
    bool portSettingsChange;

   protected:
    static void description(const std::string& description) {
        RecordProperty("description", description);
    }
};

// Set Default port param.
void setDefaultPortParam(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                         OMX_VIDEO_CODINGTYPE eCompressionFormat,
                         OMX_COLOR_FORMATTYPE eColorFormat,
                         OMX_U32 nFrameWidth = 352, OMX_U32 nFrameHeight = 288,
                         OMX_U32 nBitrate = 0,
                         OMX_U32 xFramerate = (24U << 16)) {
    switch ((int)eCompressionFormat) {
        case OMX_VIDEO_CodingUnused:
            setupRAWPort(omxNode, portIndex, nFrameWidth, nFrameHeight,
                         nBitrate, xFramerate, eColorFormat);
            break;
        default:
            break;
    }
}

// In decoder components, often the input port parameters get updated upon
// parsing the header of elementary stream. Client needs to collect this
// information to reconfigure other ports that share data with this input
// port.
void getInputChannelInfo(sp<IOmxNode> omxNode, OMX_U32 kPortIndexInput,
                         uint32_t* nFrameWidth, uint32_t* nFrameHeight,
                         uint32_t* xFramerate) {
    android::hardware::media::omx::V1_0::Status status;
    *nFrameWidth = 352;
    *nFrameHeight = 288;
    *xFramerate = (24U << 16);

    OMX_PARAM_PORTDEFINITIONTYPE portDef;
    status = getPortParam(omxNode, OMX_IndexParamPortDefinition,
                          kPortIndexInput, &portDef);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    if (status == ::android::hardware::media::omx::V1_0::Status::OK) {
        *nFrameWidth = portDef.format.video.nFrameWidth;
        *nFrameHeight = portDef.format.video.nFrameHeight;
        *xFramerate = portDef.format.video.xFramerate;
    }
}

// number of elementary streams per component
#define STREAM_COUNT 2
// LookUpTable of clips and metadata for component testing
void GetURLForComponent(VideoDecHidlTest::standardComp comp, char* mURL,
                        char* info, size_t streamIndex = 1) {
    struct CompToURL {
        VideoDecHidlTest::standardComp comp;
        const char mURL[STREAM_COUNT][512];
        const char info[STREAM_COUNT][512];
    };
    ASSERT_TRUE(streamIndex < STREAM_COUNT);

    static const CompToURL kCompToURL[] = {
        {VideoDecHidlTest::standardComp::avc,
         {"bbb_avc_176x144_300kbps_60fps.h264",
          "bbb_avc_1920x1080_5000kbps_30fps.h264"},
         {"bbb_avc_176x144_300kbps_60fps.info",
          "bbb_avc_1920x1080_5000kbps_30fps.info"}},
        {VideoDecHidlTest::standardComp::hevc,
         {"bbb_hevc_176x144_176kbps_60fps.hevc",
          "bbb_hevc_640x360_1600kbps_30fps.hevc"},
         {"bbb_hevc_176x144_176kbps_60fps.info",
          "bbb_hevc_640x360_1600kbps_30fps.info"}},
        {VideoDecHidlTest::standardComp::mpeg2,
         {"bbb_mpeg2_176x144_105kbps_25fps.m2v",
          "bbb_mpeg2_352x288_1mbps_60fps.m2v"},
         {"bbb_mpeg2_176x144_105kbps_25fps.info",
          "bbb_mpeg2_352x288_1mbps_60fps.info"}},
        {VideoDecHidlTest::standardComp::h263,
         {"", "bbb_h263_352x288_300kbps_12fps.h263"},
         {"", "bbb_h263_352x288_300kbps_12fps.info"}},
        {VideoDecHidlTest::standardComp::mpeg4,
         {"", "bbb_mpeg4_1280x720_1000kbps_25fps.m4v"},
         {"", "bbb_mpeg4_1280x720_1000kbps_25fps.info"}},
        {VideoDecHidlTest::standardComp::vp8,
         {"bbb_vp8_176x144_240kbps_60fps.vp8",
          "bbb_vp8_640x360_2mbps_30fps.vp8"},
         {"bbb_vp8_176x144_240kbps_60fps.info",
          "bbb_vp8_640x360_2mbps_30fps.info"}},
        {VideoDecHidlTest::standardComp::vp9,
         {"bbb_vp9_176x144_285kbps_60fps.vp9",
          "bbb_vp9_640x360_1600kbps_30fps.vp9"},
         {"bbb_vp9_176x144_285kbps_60fps.info",
          "bbb_vp9_640x360_1600kbps_30fps.info"}},
    };

    for (size_t i = 0; i < sizeof(kCompToURL) / sizeof(kCompToURL[0]); ++i) {
        if (kCompToURL[i].comp == comp) {
            strcat(mURL, kCompToURL[i].mURL[streamIndex]);
            strcat(info, kCompToURL[i].info[streamIndex]);
            return;
        }
    }
}

// port settings reconfiguration during runtime. reconfigures frame dimensions
void portReconfiguration(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
                         android::Vector<BufferInfo>* iBuffer,
                         android::Vector<BufferInfo>* oBuffer,
                         OMX_U32 kPortIndexInput, OMX_U32 kPortIndexOutput,
                         Message msg, PortMode oPortMode, void* args) {
    android::hardware::media::omx::V1_0::Status status;
    (void)args;

    if (msg.data.eventData.event == OMX_EventPortSettingsChanged) {
        ASSERT_EQ(msg.data.eventData.data1, kPortIndexOutput);
        if (msg.data.eventData.data2 == OMX_IndexParamPortDefinition ||
            msg.data.eventData.data2 == 0) {
            // Components can send various kinds of port settings changed events
            // all at once. Before committing to a full port reconfiguration,
            // defer any events waiting in the queue to be addressed to a later
            // point.
            android::List<Message> msgQueueDefer;
            while (1) {
                status = observer->dequeueMessage(&msg, DEFAULT_TIMEOUT,
                                                  iBuffer, oBuffer);
                if (status !=
                    android::hardware::media::omx::V1_0::Status::TIMED_OUT) {
                    msgQueueDefer.push_back(msg);
                    continue;
                } else
                    break;
            }
            status = omxNode->sendCommand(
                toRawCommandType(OMX_CommandPortDisable), kPortIndexOutput);
            ASSERT_EQ(status, android::hardware::media::omx::V1_0::Status::OK);

            status = observer->dequeueMessage(&msg, DEFAULT_TIMEOUT, iBuffer,
                                              oBuffer);
            if (status ==
                android::hardware::media::omx::V1_0::Status::TIMED_OUT) {
                for (size_t i = 0; i < oBuffer->size(); ++i) {
                    // test if client got all its buffers back
                    EXPECT_EQ((*oBuffer)[i].owner, client);
                    // free the buffers
                    status =
                        omxNode->freeBuffer(kPortIndexOutput, (*oBuffer)[i].id);
                    ASSERT_EQ(status,
                              android::hardware::media::omx::V1_0::Status::OK);
                }
                status = observer->dequeueMessage(&msg, DEFAULT_TIMEOUT,
                                                  iBuffer, oBuffer);
                ASSERT_EQ(status,
                          android::hardware::media::omx::V1_0::Status::OK);
                ASSERT_EQ(msg.type, Message::Type::EVENT);
                ASSERT_EQ(msg.data.eventData.event, OMX_EventCmdComplete);
                ASSERT_EQ(msg.data.eventData.data1, OMX_CommandPortDisable);
                ASSERT_EQ(msg.data.eventData.data2, kPortIndexOutput);

                // set Port Params
                uint32_t nFrameWidth, nFrameHeight, xFramerate;
                getInputChannelInfo(omxNode, kPortIndexInput, &nFrameWidth,
                                    &nFrameHeight, &xFramerate);
                // get configured color format
                OMX_PARAM_PORTDEFINITIONTYPE portDef;
                status = getPortParam(omxNode, OMX_IndexParamPortDefinition,
                                      kPortIndexOutput, &portDef);
                setDefaultPortParam(omxNode, kPortIndexOutput,
                                    OMX_VIDEO_CodingUnused,
                                    portDef.format.video.eColorFormat,
                                    nFrameWidth, nFrameHeight, 0, xFramerate);

                // If you can disable a port, then you should be able to
                // enable it as well
                status = omxNode->sendCommand(
                    toRawCommandType(OMX_CommandPortEnable), kPortIndexOutput);
                ASSERT_EQ(status,
                          android::hardware::media::omx::V1_0::Status::OK);

                // do not enable the port until all the buffers are supplied
                status = observer->dequeueMessage(&msg, DEFAULT_TIMEOUT,
                                                  iBuffer, oBuffer);
                ASSERT_EQ(
                    status,
                    android::hardware::media::omx::V1_0::Status::TIMED_OUT);

                ASSERT_NO_FATAL_FAILURE(allocatePortBuffers(
                    omxNode, oBuffer, kPortIndexOutput, oPortMode, true));
                status = observer->dequeueMessage(&msg, DEFAULT_TIMEOUT,
                                                  iBuffer, oBuffer);
                ASSERT_EQ(status,
                          android::hardware::media::omx::V1_0::Status::OK);
                ASSERT_EQ(msg.type, Message::Type::EVENT);
                ASSERT_EQ(msg.data.eventData.data1, OMX_CommandPortEnable);
                ASSERT_EQ(msg.data.eventData.data2, kPortIndexOutput);

                // Push back deferred messages to the list
                android::List<Message>::iterator it = msgQueueDefer.begin();
                while (it != msgQueueDefer.end()) {
                    status = omxNode->dispatchMessage(*it);
                    ASSERT_EQ(
                        status,
                        ::android::hardware::media::omx::V1_0::Status::OK);
                    it++;
                }

                // dispatch output buffers
                for (size_t i = 0; i < oBuffer->size(); i++) {
                    ASSERT_NO_FATAL_FAILURE(
                        dispatchOutputBuffer(omxNode, oBuffer, i, oPortMode));
                }
            } else {
                ASSERT_TRUE(false);
            }
        } else if (msg.data.eventData.data2 ==
                   OMX_IndexConfigCommonOutputCrop) {
            std::cout << "[   INFO   ] OMX_EventPortSettingsChanged/ "
                         "OMX_IndexConfigCommonOutputCrop not handled \n";
        } else if (msg.data.eventData.data2 == OMX_IndexVendorStartUnused + 3) {
            std::cout << "[   INFO   ] OMX_EventPortSettingsChanged/ "
                         "kDescribeColorAspectsIndex not handled \n";
        }
    } else if (msg.data.eventData.event == OMX_EventError) {
        std::cerr << "[   ERROR   ] OMX_EventError/ "
                     "Decode Frame Call might be failed \n";
        ASSERT_TRUE(false);
    } else {
        // something unexpected happened
        ASSERT_TRUE(false);
    }
}

// blocking call to ensures application to Wait till all the inputs are consumed
void waitOnInputConsumption(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
                            android::Vector<BufferInfo>* iBuffer,
                            android::Vector<BufferInfo>* oBuffer,
                            OMX_U32 kPortIndexInput, OMX_U32 kPortIndexOutput,
                            PortMode oPortMode) {
    android::hardware::media::omx::V1_0::Status status;
    Message msg;
    int timeOut = TIMEOUT_COUNTER_Q;

    while (timeOut--) {
        size_t i = 0;
        status =
            observer->dequeueMessage(&msg, DEFAULT_TIMEOUT_Q, iBuffer, oBuffer);
        if (status == android::hardware::media::omx::V1_0::Status::OK) {
            ASSERT_EQ(msg.type, Message::Type::EVENT);
            ASSERT_NO_FATAL_FAILURE(portReconfiguration(
                omxNode, observer, iBuffer, oBuffer, kPortIndexInput,
                kPortIndexOutput, msg, oPortMode, nullptr));
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
                dispatchOutputBuffer(omxNode, oBuffer, index, oPortMode));
            timeOut = TIMEOUT_COUNTER_Q;
        }
    }
}

// Decode N Frames
void decodeNFrames(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
                   android::Vector<BufferInfo>* iBuffer,
                   android::Vector<BufferInfo>* oBuffer,
                   OMX_U32 kPortIndexInput, OMX_U32 kPortIndexOutput,
                   std::ifstream& eleStream, android::Vector<FrameData>* Info,
                   int offset, int range, PortMode oPortMode,
                   bool signalEOS = true) {
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
            ASSERT_NO_FATAL_FAILURE(portReconfiguration(
                omxNode, observer, iBuffer, oBuffer, kPortIndexInput,
                kPortIndexOutput, msg, oPortMode, nullptr));
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
                dispatchOutputBuffer(omxNode, oBuffer, index, oPortMode));
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

// DescribeColorFormatParams Copy Constructor (Borrowed from OMXUtils.cpp)
android::DescribeColorFormatParams::DescribeColorFormatParams(
    const android::DescribeColorFormat2Params& params) {
    eColorFormat = params.eColorFormat;
    nFrameWidth = params.nFrameWidth;
    nFrameHeight = params.nFrameHeight;
    nStride = params.nStride;
    nSliceHeight = params.nSliceHeight;
    bUsingNativeBuffers = params.bUsingNativeBuffers;
};

bool isColorFormatFlexibleYUV(sp<IOmxNode> omxNode,
                              OMX_COLOR_FORMATTYPE eColorFormat) {
    android::hardware::media::omx::V1_0::Status status;
    unsigned int index = OMX_IndexMax, index2 = OMX_IndexMax;
    omxNode->getExtensionIndex(
        "OMX.google.android.index.describeColorFormat",
        [&index](android::hardware::media::omx::V1_0::Status _s,
                          unsigned int _nl) {
            if (_s == ::android::hardware::media::omx::V1_0::Status::OK)
                index = _nl;
        });
    omxNode->getExtensionIndex(
        "OMX.google.android.index.describeColorFormat2",
        [&index2](android::hardware::media::omx::V1_0::Status _s,
                           unsigned int _nl) {
            if (_s == ::android::hardware::media::omx::V1_0::Status::OK)
                index2 = _nl;
        });

    android::DescribeColorFormat2Params describeParams;
    describeParams.eColorFormat = eColorFormat;
    describeParams.nFrameWidth = 128;
    describeParams.nFrameHeight = 128;
    describeParams.nStride = 128;
    describeParams.nSliceHeight = 128;
    describeParams.bUsingNativeBuffers = OMX_FALSE;
    if (index != OMX_IndexMax) {
        android::DescribeColorFormatParams describeParamsV1(describeParams);
        status = getParam(omxNode, static_cast<OMX_INDEXTYPE>(index),
                          &describeParamsV1);
        if (status == ::android::hardware::media::omx::V1_0::Status::OK) {
            android::MediaImage& img = describeParamsV1.sMediaImage;
            if (img.mType == android::MediaImage::MEDIA_IMAGE_TYPE_YUV) {
                if (img.mNumPlanes == 3 &&
                    img.mPlane[img.Y].mHorizSubsampling == 1 &&
                    img.mPlane[img.Y].mVertSubsampling == 1) {
                    if (img.mPlane[img.U].mHorizSubsampling == 2 &&
                        img.mPlane[img.U].mVertSubsampling == 2 &&
                        img.mPlane[img.V].mHorizSubsampling == 2 &&
                        img.mPlane[img.V].mVertSubsampling == 2) {
                        if (img.mBitDepth <= 8) {
                            return true;
                        }
                    }
                }
            }
        }
    } else if (index2 != OMX_IndexMax) {
        status = getParam(omxNode, static_cast<OMX_INDEXTYPE>(index2),
                          &describeParams);
        android::MediaImage2& img = describeParams.sMediaImage;
        if (img.mType == android::MediaImage2::MEDIA_IMAGE_TYPE_YUV) {
            if (img.mNumPlanes == 3 &&
                img.mPlane[img.Y].mHorizSubsampling == 1 &&
                img.mPlane[img.Y].mVertSubsampling == 1) {
                if (img.mPlane[img.U].mHorizSubsampling == 2 &&
                    img.mPlane[img.U].mVertSubsampling == 2 &&
                    img.mPlane[img.V].mHorizSubsampling == 2 &&
                    img.mPlane[img.V].mVertSubsampling == 2) {
                    if (img.mBitDepth <= 8) {
                        return true;
                    }
                }
            }
        }
    }
    return false;
}

// get default color format for output port
void getDefaultColorFormat(sp<IOmxNode> omxNode, OMX_U32 kPortIndexOutput,
                           PortMode oPortMode,
                           OMX_COLOR_FORMATTYPE* eColorFormat) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_VIDEO_PARAM_PORTFORMATTYPE portFormat;
    *eColorFormat = OMX_COLOR_FormatUnused;
    portFormat.nIndex = 0;
    while (portFormat.nIndex < 512) {
        status = getPortParam(omxNode, OMX_IndexParamVideoPortFormat,
                              kPortIndexOutput, &portFormat);
        if (status != ::android::hardware::media::omx::V1_0::Status::OK) break;
        EXPECT_EQ(portFormat.eCompressionFormat, OMX_VIDEO_CodingUnused);
        if (oPortMode != PortMode::PRESET_BYTE_BUFFER) {
            *eColorFormat = portFormat.eColorFormat;
            break;
        }
        if (isColorFormatFlexibleYUV(omxNode, portFormat.eColorFormat)) {
            *eColorFormat = portFormat.eColorFormat;
            break;
        }
        if (OMX_COLOR_FormatYUV420SemiPlanar == portFormat.eColorFormat ||
            OMX_COLOR_FormatYUV420Planar == portFormat.eColorFormat ||
            OMX_COLOR_FormatYUV420PackedPlanar == portFormat.eColorFormat ||
            OMX_COLOR_FormatYUV420PackedSemiPlanar == portFormat.eColorFormat) {
            *eColorFormat = portFormat.eColorFormat;
            break;
        }
        portFormat.nIndex++;
    }
}

// set component role
TEST_F(VideoDecHidlTest, SetRole) {
    description("Test Set Component Role");
    if (disableTest) return;
    android::hardware::media::omx::V1_0::Status status;
    status = setRole(omxNode, gEnv->getRole().c_str());
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

// port format enumeration
TEST_F(VideoDecHidlTest, EnumeratePortFormat) {
    description("Test Component on Mandatory Port Parameters (Port Format)");
    if (disableTest) return;
    android::hardware::media::omx::V1_0::Status status;
    uint32_t kPortIndexInput = 0, kPortIndexOutput = 1;
    OMX_COLOR_FORMATTYPE eColorFormat = OMX_COLOR_FormatYUV420Planar;
    OMX_U32 xFramerate = (24U << 16);
    status = setRole(omxNode, gEnv->getRole().c_str());
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    OMX_PORT_PARAM_TYPE params;
    status = getParam(omxNode, OMX_IndexParamVideoInit, &params);
    if (status == ::android::hardware::media::omx::V1_0::Status::OK) {
        ASSERT_EQ(params.nPorts, 2U);
        kPortIndexInput = params.nStartPortNumber;
        kPortIndexOutput = kPortIndexInput + 1;
    }
    status = setVideoPortFormat(omxNode, kPortIndexInput, eCompressionFormat,
                                OMX_COLOR_FormatUnused, 0U);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status =
        setVideoPortFormat(omxNode, kPortIndexOutput, OMX_VIDEO_CodingUnused,
                           eColorFormat, xFramerate);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

// test port settings reconfiguration, elementary stream decode and timestamp
// deviation
TEST_F(VideoDecHidlTest, DecodeTest) {
    description("Tests Port Reconfiguration, Decode and timestamp deviation");
    if (disableTest) return;
    android::hardware::media::omx::V1_0::Status status;
    uint32_t kPortIndexInput = 0, kPortIndexOutput = 1;
    status = setRole(omxNode, gEnv->getRole().c_str());
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    OMX_PORT_PARAM_TYPE params;
    status = getParam(omxNode, OMX_IndexParamVideoInit, &params);
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
    int bytesCount = 0, maxBytesCount = 0;
    uint32_t flags = 0;
    uint32_t timestamp = 0;
    timestampDevTest = true;
    while (1) {
        if (!(eleInfo >> bytesCount)) break;
        eleInfo >> flags;
        eleInfo >> timestamp;
        Info.push_back({bytesCount, flags, timestamp});
        if (timestampDevTest && (flags != OMX_BUFFERFLAG_CODECCONFIG))
            timestampUslist.push_back(timestamp);
        if (maxBytesCount < bytesCount) maxBytesCount = bytesCount;
    }
    eleInfo.close();

    // As the frame sizes are known ahead, use it to configure i/p buffer size
    maxBytesCount = ALIGN_POWER_OF_TWO(maxBytesCount, 10);
    status = setPortBufferSize(omxNode, kPortIndexInput, maxBytesCount);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    // set port mode
    portMode[0] = PortMode::PRESET_BYTE_BUFFER;
    portMode[1] = PortMode::DYNAMIC_ANW_BUFFER;
    status = omxNode->setPortMode(kPortIndexInput, portMode[0]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = omxNode->setPortMode(kPortIndexOutput, portMode[1]);
    if (status != ::android::hardware::media::omx::V1_0::Status::OK) {
        portMode[1] = PortMode::PRESET_BYTE_BUFFER;
        status = omxNode->setPortMode(kPortIndexOutput, portMode[1]);
        ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    }

    // set Port Params
    uint32_t nFrameWidth, nFrameHeight, xFramerate;
    getInputChannelInfo(omxNode, kPortIndexInput, &nFrameWidth, &nFrameHeight,
                        &xFramerate);
    // get default color format
    OMX_COLOR_FORMATTYPE eColorFormat = OMX_COLOR_FormatUnused;
    getDefaultColorFormat(omxNode, kPortIndexOutput, portMode[1],
                          &eColorFormat);
    ASSERT_NE(eColorFormat, OMX_COLOR_FormatUnused);
    status =
        setVideoPortFormat(omxNode, kPortIndexOutput, OMX_VIDEO_CodingUnused,
                           eColorFormat, xFramerate);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    setDefaultPortParam(omxNode, kPortIndexOutput, OMX_VIDEO_CodingUnused,
                        eColorFormat, nFrameWidth, nFrameHeight, 0, xFramerate);

    android::Vector<BufferInfo> iBuffer, oBuffer;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(changeStateLoadedtoIdle(
        omxNode, observer, &iBuffer, &oBuffer, kPortIndexInput,
        kPortIndexOutput, portMode, true));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));

    // Port Reconfiguration
    eleStream.open(mURL, std::ifstream::binary);
    ASSERT_EQ(eleStream.is_open(), true);
    ASSERT_NO_FATAL_FAILURE(decodeNFrames(
        omxNode, observer, &iBuffer, &oBuffer, kPortIndexInput,
        kPortIndexOutput, eleStream, &Info, 0, (int)Info.size(), portMode[1]));
    eleStream.close();
    ASSERT_NO_FATAL_FAILURE(
        waitOnInputConsumption(omxNode, observer, &iBuffer, &oBuffer,
                               kPortIndexInput, kPortIndexOutput, portMode[1]));
    ASSERT_NO_FATAL_FAILURE(testEOS(
        omxNode, observer, &iBuffer, &oBuffer, false, eosFlag, portMode,
        portReconfiguration, kPortIndexInput, kPortIndexOutput, nullptr));
    if (timestampDevTest) EXPECT_EQ(timestampUslist.empty(), true);
    // set state to idle
    ASSERT_NO_FATAL_FAILURE(
        changeStateExecutetoIdle(omxNode, observer, &iBuffer, &oBuffer));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoLoaded(omxNode, observer, &iBuffer,
                                                    &oBuffer, kPortIndexInput,
                                                    kPortIndexOutput));
}

// Test for adaptive playback support
TEST_F(VideoDecHidlTest, AdaptivePlaybackTest) {
    description("Tests for Adaptive Playback support");
    if (disableTest) return;
    if (!(compName == avc || compName == hevc || compName == vp8 ||
          compName == vp9 || compName == mpeg2))
        return;
    android::hardware::media::omx::V1_0::Status status;
    uint32_t kPortIndexInput = 0, kPortIndexOutput = 1;
    status = setRole(omxNode, gEnv->getRole().c_str());
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    OMX_PORT_PARAM_TYPE params;
    status = getParam(omxNode, OMX_IndexParamVideoInit, &params);
    if (status == ::android::hardware::media::omx::V1_0::Status::OK) {
        ASSERT_EQ(params.nPorts, 2U);
        kPortIndexInput = params.nStartPortNumber;
        kPortIndexOutput = kPortIndexInput + 1;
    }

    // set port mode
    portMode[0] = PortMode::PRESET_BYTE_BUFFER;
    portMode[1] = PortMode::DYNAMIC_ANW_BUFFER;
    status = omxNode->setPortMode(kPortIndexInput, portMode[0]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = omxNode->setPortMode(kPortIndexOutput, portMode[1]);
    if (status != ::android::hardware::media::omx::V1_0::Status::OK) {
        portMode[1] = PortMode::PRESET_BYTE_BUFFER;
        status = omxNode->setPortMode(kPortIndexOutput, portMode[1]);
        ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    }

    // prepare for adaptive playback
    uint32_t adaptiveMaxWidth = 320;
    uint32_t adaptiveMaxHeight = 240;
    status = omxNode->prepareForAdaptivePlayback(
        kPortIndexOutput, true, adaptiveMaxWidth, adaptiveMaxHeight);
    if (strncmp(gEnv->getComponent().c_str(), "OMX.google.", 11) == 0) {
        // SoftOMX Decoders donot support graphic buffer modes. So for them
        // support for adaptive play back is mandatory in Byte Buffer mode
        ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    } else {
        // for vendor codecs, support for adaptive play back is optional
        // in byte buffer mode.
        if (portMode[1] == PortMode::PRESET_BYTE_BUFFER) return;
        if (status != ::android::hardware::media::omx::V1_0::Status::OK) return;
    }

    // TODO: Handle this better !!!
    // Without the knowledge of the maximum resolution of the frame to be
    // decoded it is not possible to choose the size of the input buffer.
    // The value below is based on the info. files of clips in res folder.
    status = setPortBufferSize(omxNode, kPortIndexInput, 482304);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    // set Port Params
    uint32_t nFrameWidth, nFrameHeight, xFramerate;
    getInputChannelInfo(omxNode, kPortIndexInput, &nFrameWidth, &nFrameHeight,
                        &xFramerate);
    // get default color format
    OMX_COLOR_FORMATTYPE eColorFormat = OMX_COLOR_FormatUnused;
    getDefaultColorFormat(omxNode, kPortIndexOutput, portMode[1],
                          &eColorFormat);
    ASSERT_NE(eColorFormat, OMX_COLOR_FormatUnused);
    status =
        setVideoPortFormat(omxNode, kPortIndexOutput, OMX_VIDEO_CodingUnused,
                           eColorFormat, xFramerate);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    setDefaultPortParam(omxNode, kPortIndexOutput, OMX_VIDEO_CodingUnused,
                        eColorFormat, nFrameWidth, nFrameHeight, 0, xFramerate);

    android::Vector<BufferInfo> iBuffer, oBuffer;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(changeStateLoadedtoIdle(
        omxNode, observer, &iBuffer, &oBuffer, kPortIndexInput,
        kPortIndexOutput, portMode, true));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));

    timestampDevTest = true;
    uint32_t timestampOffset = 0;
    for (uint32_t i = 0; i < STREAM_COUNT * 2; i++) {
        std::ifstream eleStream, eleInfo;
        char mURL[512], info[512];
        android::Vector<FrameData> Info;
        strcpy(mURL, gEnv->getRes().c_str());
        strcpy(info, gEnv->getRes().c_str());
        GetURLForComponent(compName, mURL, info, i % STREAM_COUNT);
        eleInfo.open(info);
        ASSERT_EQ(eleInfo.is_open(), true);
        int bytesCount = 0;
        uint32_t flags = 0;
        uint32_t timestamp = 0;
        uint32_t timestampMax = 0;
        while (1) {
            if (!(eleInfo >> bytesCount)) break;
            eleInfo >> flags;
            eleInfo >> timestamp;
            timestamp += timestampOffset;
            Info.push_back({bytesCount, flags, timestamp});
            if (timestampDevTest && (flags != OMX_BUFFERFLAG_CODECCONFIG))
                timestampUslist.push_back(timestamp);
            if (timestampMax < timestamp) timestampMax = timestamp;
        }
        timestampOffset = timestampMax;
        eleInfo.close();

        // Port Reconfiguration
        eleStream.open(mURL, std::ifstream::binary);
        ASSERT_EQ(eleStream.is_open(), true);
        ASSERT_NO_FATAL_FAILURE(
            decodeNFrames(omxNode, observer, &iBuffer, &oBuffer,
                          kPortIndexInput, kPortIndexOutput, eleStream, &Info,
                          0, (int)Info.size(), portMode[1], false));
        eleStream.close();

        getInputChannelInfo(omxNode, kPortIndexInput, &nFrameWidth,
                            &nFrameHeight, &xFramerate);
        if ((nFrameWidth > adaptiveMaxWidth) ||
            (nFrameHeight > adaptiveMaxHeight)) {
            if (nFrameWidth > adaptiveMaxWidth) adaptiveMaxWidth = nFrameWidth;
            if (nFrameHeight > adaptiveMaxHeight)
                adaptiveMaxHeight = nFrameHeight;
            EXPECT_TRUE(portSettingsChange);
        } else {
            // In DynamicANW Buffer mode, its ok to do a complete
            // reconfiguration even if a partial reconfiguration is sufficient.
            if (portMode[1] != PortMode::DYNAMIC_ANW_BUFFER)
                EXPECT_FALSE(portSettingsChange);
        }
        portSettingsChange = false;
    }
    ASSERT_NO_FATAL_FAILURE(
        waitOnInputConsumption(omxNode, observer, &iBuffer, &oBuffer,
                               kPortIndexInput, kPortIndexOutput, portMode[1]));
    ASSERT_NO_FATAL_FAILURE(testEOS(
        omxNode, observer, &iBuffer, &oBuffer, true, eosFlag, portMode,
        portReconfiguration, kPortIndexInput, kPortIndexOutput, nullptr));
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
TEST_F(VideoDecHidlTest, EOSTest_M) {
    description("Test End of stream monkeying");
    if (disableTest) return;
    android::hardware::media::omx::V1_0::Status status;
    uint32_t kPortIndexInput = 0, kPortIndexOutput = 1;
    status = setRole(omxNode, gEnv->getRole().c_str());
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    OMX_PORT_PARAM_TYPE params;
    status = getParam(omxNode, OMX_IndexParamVideoInit, &params);
    if (status == ::android::hardware::media::omx::V1_0::Status::OK) {
        ASSERT_EQ(params.nPorts, 2U);
        kPortIndexInput = params.nStartPortNumber;
        kPortIndexOutput = kPortIndexInput + 1;
    }

    // set port mode
    status = omxNode->setPortMode(kPortIndexInput, portMode[0]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = omxNode->setPortMode(kPortIndexOutput, portMode[1]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    // set Port Params
    uint32_t nFrameWidth, nFrameHeight, xFramerate;
    getInputChannelInfo(omxNode, kPortIndexInput, &nFrameWidth, &nFrameHeight,
                        &xFramerate);
    // get default color format
    OMX_COLOR_FORMATTYPE eColorFormat = OMX_COLOR_FormatUnused;
    getDefaultColorFormat(omxNode, kPortIndexOutput, portMode[1],
                          &eColorFormat);
    ASSERT_NE(eColorFormat, OMX_COLOR_FormatUnused);
    status =
        setVideoPortFormat(omxNode, kPortIndexOutput, OMX_VIDEO_CodingUnused,
                           eColorFormat, xFramerate);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    setDefaultPortParam(omxNode, kPortIndexOutput, OMX_VIDEO_CodingUnused,
                        eColorFormat, nFrameWidth, nFrameHeight, 0, xFramerate);

    android::Vector<BufferInfo> iBuffer, oBuffer;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(changeStateLoadedtoIdle(
        omxNode, observer, &iBuffer, &oBuffer, kPortIndexInput,
        kPortIndexOutput, portMode, true));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));

    // request EOS at the start
    ASSERT_NO_FATAL_FAILURE(testEOS(
        omxNode, observer, &iBuffer, &oBuffer, true, eosFlag, portMode,
        portReconfiguration, kPortIndexInput, kPortIndexOutput, nullptr));
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
TEST_F(VideoDecHidlTest, ThumbnailTest) {
    description("Test Request for thumbnail");
    if (disableTest) return;
    android::hardware::media::omx::V1_0::Status status;
    uint32_t kPortIndexInput = 0, kPortIndexOutput = 1;
    status = setRole(omxNode, gEnv->getRole().c_str());
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    OMX_PORT_PARAM_TYPE params;
    status = getParam(omxNode, OMX_IndexParamVideoInit, &params);
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
    int bytesCount = 0, maxBytesCount = 0;
    uint32_t flags = 0;
    uint32_t timestamp = 0;
    while (1) {
        if (!(eleInfo >> bytesCount)) break;
        eleInfo >> flags;
        eleInfo >> timestamp;
        Info.push_back({bytesCount, flags, timestamp});
        if (maxBytesCount < bytesCount) maxBytesCount = bytesCount;
    }
    eleInfo.close();

    // As the frame sizes are known ahead, use it to configure i/p buffer size
    maxBytesCount = ALIGN_POWER_OF_TWO(maxBytesCount, 10);
    status = setPortBufferSize(omxNode, kPortIndexInput, maxBytesCount);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    // set port mode
    status = omxNode->setPortMode(kPortIndexInput, portMode[0]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = omxNode->setPortMode(kPortIndexOutput, portMode[1]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    // set Port Params
    uint32_t nFrameWidth, nFrameHeight, xFramerate;
    getInputChannelInfo(omxNode, kPortIndexInput, &nFrameWidth, &nFrameHeight,
                        &xFramerate);
    // get default color format
    OMX_COLOR_FORMATTYPE eColorFormat = OMX_COLOR_FormatUnused;
    getDefaultColorFormat(omxNode, kPortIndexOutput, portMode[1],
                          &eColorFormat);
    ASSERT_NE(eColorFormat, OMX_COLOR_FormatUnused);
    status =
        setVideoPortFormat(omxNode, kPortIndexOutput, OMX_VIDEO_CodingUnused,
                           eColorFormat, xFramerate);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    setDefaultPortParam(omxNode, kPortIndexOutput, OMX_VIDEO_CodingUnused,
                        eColorFormat, nFrameWidth, nFrameHeight, 0, xFramerate);

    android::Vector<BufferInfo> iBuffer, oBuffer;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(changeStateLoadedtoIdle(
        omxNode, observer, &iBuffer, &oBuffer, kPortIndexInput,
        kPortIndexOutput, portMode, true));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));

    // request EOS for thumbnail
    size_t i = 0;
    while (!(Info[i].flags & OMX_BUFFERFLAG_SYNCFRAME)) i++;
    eleStream.open(mURL, std::ifstream::binary);
    ASSERT_EQ(eleStream.is_open(), true);
    ASSERT_NO_FATAL_FAILURE(decodeNFrames(
        omxNode, observer, &iBuffer, &oBuffer, kPortIndexInput,
        kPortIndexOutput, eleStream, &Info, 0, i + 1, portMode[1]));
    eleStream.close();
    ASSERT_NO_FATAL_FAILURE(
        waitOnInputConsumption(omxNode, observer, &iBuffer, &oBuffer,
                               kPortIndexInput, kPortIndexOutput, portMode[1]));
    ASSERT_NO_FATAL_FAILURE(testEOS(
        omxNode, observer, &iBuffer, &oBuffer, false, eosFlag, portMode,
        portReconfiguration, kPortIndexInput, kPortIndexOutput, nullptr));
    ASSERT_NO_FATAL_FAILURE(flushPorts(omxNode, observer, &iBuffer, &oBuffer,
                                       kPortIndexInput, kPortIndexOutput));
    EXPECT_GE(framesReceived, 1U);
    framesReceived = 0;
    timestampUs = 0;

    eleStream.open(mURL, std::ifstream::binary);
    ASSERT_EQ(eleStream.is_open(), true);
    ASSERT_NO_FATAL_FAILURE(decodeNFrames(
        omxNode, observer, &iBuffer, &oBuffer, kPortIndexInput,
        kPortIndexOutput, eleStream, &Info, 0, i + 1, portMode[1], false));
    eleStream.close();
    ASSERT_NO_FATAL_FAILURE(
        waitOnInputConsumption(omxNode, observer, &iBuffer, &oBuffer,
                               kPortIndexInput, kPortIndexOutput, portMode[1]));
    ASSERT_NO_FATAL_FAILURE(testEOS(
        omxNode, observer, &iBuffer, &oBuffer, true, eosFlag, portMode,
        portReconfiguration, kPortIndexInput, kPortIndexOutput, nullptr));
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
TEST_F(VideoDecHidlTest, SimpleEOSTest) {
    description("Test End of stream");
    if (disableTest) return;
    android::hardware::media::omx::V1_0::Status status;
    uint32_t kPortIndexInput = 0, kPortIndexOutput = 1;
    status = setRole(omxNode, gEnv->getRole().c_str());
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    OMX_PORT_PARAM_TYPE params;
    status = getParam(omxNode, OMX_IndexParamVideoInit, &params);
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
    int bytesCount = 0, maxBytesCount = 0;
    uint32_t flags = 0;
    uint32_t timestamp = 0;
    while (1) {
        if (!(eleInfo >> bytesCount)) break;
        eleInfo >> flags;
        eleInfo >> timestamp;
        Info.push_back({bytesCount, flags, timestamp});
        if (maxBytesCount < bytesCount) maxBytesCount = bytesCount;
    }
    eleInfo.close();

    // As the frame sizes are known ahead, use it to configure i/p buffer size
    maxBytesCount = ALIGN_POWER_OF_TWO(maxBytesCount, 10);
    status = setPortBufferSize(omxNode, kPortIndexInput, maxBytesCount);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    // set port mode
    portMode[0] = PortMode::PRESET_BYTE_BUFFER;
    portMode[1] = PortMode::PRESET_ANW_BUFFER;
    status = omxNode->setPortMode(kPortIndexInput, portMode[0]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = omxNode->setPortMode(kPortIndexOutput, portMode[1]);
    if (status != ::android::hardware::media::omx::V1_0::Status::OK) {
        portMode[1] = PortMode::PRESET_BYTE_BUFFER;
        status = omxNode->setPortMode(kPortIndexOutput, portMode[1]);
        ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    }

    // set Port Params
    uint32_t nFrameWidth, nFrameHeight, xFramerate;
    getInputChannelInfo(omxNode, kPortIndexInput, &nFrameWidth, &nFrameHeight,
                        &xFramerate);
    // get default color format
    OMX_COLOR_FORMATTYPE eColorFormat = OMX_COLOR_FormatUnused;
    getDefaultColorFormat(omxNode, kPortIndexOutput, portMode[1],
                          &eColorFormat);
    ASSERT_NE(eColorFormat, OMX_COLOR_FormatUnused);
    status =
        setVideoPortFormat(omxNode, kPortIndexOutput, OMX_VIDEO_CodingUnused,
                           eColorFormat, xFramerate);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    setDefaultPortParam(omxNode, kPortIndexOutput, OMX_VIDEO_CodingUnused,
                        eColorFormat, nFrameWidth, nFrameHeight, 0, xFramerate);

    android::Vector<BufferInfo> iBuffer, oBuffer;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(changeStateLoadedtoIdle(
        omxNode, observer, &iBuffer, &oBuffer, kPortIndexInput,
        kPortIndexOutput, portMode, true));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));

    // request EOS at the end
    eleStream.open(mURL, std::ifstream::binary);
    ASSERT_EQ(eleStream.is_open(), true);
    ASSERT_NO_FATAL_FAILURE(decodeNFrames(omxNode, observer, &iBuffer, &oBuffer,
                                          kPortIndexInput, kPortIndexOutput,
                                          eleStream, &Info, 0, (int)Info.size(),
                                          portMode[1], false));
    eleStream.close();
    ASSERT_NO_FATAL_FAILURE(
        waitOnInputConsumption(omxNode, observer, &iBuffer, &oBuffer,
                               kPortIndexInput, kPortIndexOutput, portMode[1]));
    ASSERT_NO_FATAL_FAILURE(testEOS(
        omxNode, observer, &iBuffer, &oBuffer, true, eosFlag, portMode,
        portReconfiguration, kPortIndexInput, kPortIndexOutput, nullptr));
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
TEST_F(VideoDecHidlTest, FlushTest) {
    description("Test Flush");
    if (disableTest) return;
    android::hardware::media::omx::V1_0::Status status;
    uint32_t kPortIndexInput = 0, kPortIndexOutput = 1;
    status = setRole(omxNode, gEnv->getRole().c_str());
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    OMX_PORT_PARAM_TYPE params;
    status = getParam(omxNode, OMX_IndexParamVideoInit, &params);
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
    int bytesCount = 0, maxBytesCount = 0;
    uint32_t flags = 0;
    uint32_t timestamp = 0;
    while (1) {
        if (!(eleInfo >> bytesCount)) break;
        eleInfo >> flags;
        eleInfo >> timestamp;
        Info.push_back({bytesCount, flags, timestamp});
        if (maxBytesCount < bytesCount) maxBytesCount = bytesCount;
    }
    eleInfo.close();

    // As the frame sizes are known ahead, use it to configure i/p buffer size
    maxBytesCount = ALIGN_POWER_OF_TWO(maxBytesCount, 10);
    status = setPortBufferSize(omxNode, kPortIndexInput, maxBytesCount);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    // set port mode
    status = omxNode->setPortMode(kPortIndexInput, portMode[0]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = omxNode->setPortMode(kPortIndexOutput, portMode[1]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    // set Port Params
    uint32_t nFrameWidth, nFrameHeight, xFramerate;
    getInputChannelInfo(omxNode, kPortIndexInput, &nFrameWidth, &nFrameHeight,
                        &xFramerate);
    // get default color format
    OMX_COLOR_FORMATTYPE eColorFormat = OMX_COLOR_FormatUnused;
    getDefaultColorFormat(omxNode, kPortIndexOutput, portMode[1],
                          &eColorFormat);
    ASSERT_NE(eColorFormat, OMX_COLOR_FormatUnused);
    status =
        setVideoPortFormat(omxNode, kPortIndexOutput, OMX_VIDEO_CodingUnused,
                           eColorFormat, xFramerate);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    setDefaultPortParam(omxNode, kPortIndexOutput, OMX_VIDEO_CodingUnused,
                        eColorFormat, nFrameWidth, nFrameHeight, 0, xFramerate);

    android::Vector<BufferInfo> iBuffer, oBuffer;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(changeStateLoadedtoIdle(
        omxNode, observer, &iBuffer, &oBuffer, kPortIndexInput,
        kPortIndexOutput, portMode, true));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));

    // Decode 128 frames and flush. here 128 is chosen to ensure there is a key
    // frame after this so that the below section can be convered for all
    // components
    int nFrames = 128;
    eleStream.open(mURL, std::ifstream::binary);
    ASSERT_EQ(eleStream.is_open(), true);
    ASSERT_NO_FATAL_FAILURE(decodeNFrames(
        omxNode, observer, &iBuffer, &oBuffer, kPortIndexInput,
        kPortIndexOutput, eleStream, &Info, 0, nFrames, portMode[1], false));
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
            decodeNFrames(omxNode, observer, &iBuffer, &oBuffer,
                          kPortIndexInput, kPortIndexOutput, eleStream, &Info,
                          index, Info.size() - index, portMode[1], false));
    }
    eleStream.close();
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
