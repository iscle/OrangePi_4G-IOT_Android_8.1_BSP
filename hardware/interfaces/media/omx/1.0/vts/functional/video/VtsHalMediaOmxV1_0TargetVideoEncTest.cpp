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

#define LOG_TAG "media_omx_hidl_video_enc_test"
#ifdef __LP64__
#define OMX_ANDROID_COMPILE_AS_32BIT_ON_64BIT_PLATFORMS
#endif

#include <android-base/logging.h>

#include <android/hardware/graphics/bufferqueue/1.0/IGraphicBufferProducer.h>
#include <android/hardware/graphics/bufferqueue/1.0/IProducerListener.h>
#include <android/hardware/graphics/mapper/2.0/IMapper.h>
#include <android/hardware/graphics/mapper/2.0/types.h>
#include <android/hardware/media/omx/1.0/IGraphicBufferSource.h>
#include <android/hardware/media/omx/1.0/IOmx.h>
#include <android/hardware/media/omx/1.0/IOmxBufferSource.h>
#include <android/hardware/media/omx/1.0/IOmxNode.h>
#include <android/hardware/media/omx/1.0/IOmxObserver.h>
#include <android/hardware/media/omx/1.0/types.h>
#include <android/hidl/allocator/1.0/IAllocator.h>
#include <android/hidl/memory/1.0/IMapper.h>
#include <android/hidl/memory/1.0/IMemory.h>

using ::android::hardware::graphics::bufferqueue::V1_0::IGraphicBufferProducer;
using ::android::hardware::graphics::bufferqueue::V1_0::IProducerListener;
using ::android::hardware::graphics::common::V1_0::BufferUsage;
using ::android::hardware::graphics::common::V1_0::PixelFormat;
using ::android::hardware::media::omx::V1_0::IGraphicBufferSource;
using ::android::hardware::media::omx::V1_0::IOmxBufferSource;
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
#include <system/window.h>
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

// video encoder test fixture class
class VideoEncHidlTest : public ::testing::VtsHalHidlTargetTestBase {
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
            {"h263", h263}, {"avc", avc}, {"mpeg4", mpeg4},
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
            {mpeg4, OMX_VIDEO_CodingMPEG4}, {hevc, OMX_VIDEO_CodingHEVC},
            {vp8, OMX_VIDEO_CodingVP8},     {vp9, OMX_VIDEO_CodingVP9},
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
        eosFlag = false;
        prependSPSPPS = false;
        timestampDevTest = false;
        producer = nullptr;
        source = nullptr;
        isSecure = false;
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
                // Test if current timestamp is among the list of queued
                // timestamps
                if (timestampDevTest && ((msg.data.extendedBufferData.flags &
                                          OMX_BUFFERFLAG_CODECCONFIG) == 0)) {
                    bool tsHit = false;
                    android::List<uint64_t>::iterator it =
                        timestampUslist.begin();
                    while (it != timestampUslist.end()) {
                        if (*it == msg.data.extendedBufferData.timestampUs) {
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
        h263,
        avc,
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
    bool eosFlag;
    bool prependSPSPPS;
    ::android::List<uint64_t> timestampUslist;
    bool timestampDevTest;
    bool isSecure;
    sp<IGraphicBufferProducer> producer;
    sp<IGraphicBufferSource> source;

   protected:
    static void description(const std::string& description) {
        RecordProperty("description", description);
    }
};

// CodecProducerListener class
struct CodecProducerListener : public IProducerListener {
   public:
    CodecProducerListener(int a, int b)
        : freeBuffers(a), minUnDequeuedCount(b) {}
    virtual ::android::hardware::Return<void> onBufferReleased() override {
        android::Mutex::Autolock autoLock(bufferLock);
        freeBuffers += 1;
        return Void();
    }
    virtual ::android::hardware::Return<bool> needsReleaseNotify() override {
        return true;
    }
    void reduceCount() {
        android::Mutex::Autolock autoLock(bufferLock);
        freeBuffers -= 1;
        EXPECT_GE(freeBuffers, minUnDequeuedCount);
    }

    size_t freeBuffers;
    size_t minUnDequeuedCount;
    android::Mutex bufferLock;
};

// Mock IOmxBufferSource class. GraphicBufferSource.cpp in libstagefright/omx/
// implements this class. Below is dummy class introduced to test if callback
// functions are actually being called or not
struct DummyBufferSource : public IOmxBufferSource {
   public:
    DummyBufferSource(sp<IOmxNode> node) {
        callback = 0;
        executing = false;
        omxNode = node;
    }
    virtual Return<void> onOmxExecuting();
    virtual Return<void> onOmxIdle();
    virtual Return<void> onOmxLoaded();
    virtual Return<void> onInputBufferAdded(uint32_t buffer);
    virtual Return<void> onInputBufferEmptied(
        uint32_t buffer, const ::android::hardware::hidl_handle& fence);

    int callback;
    bool executing;
    sp<IOmxNode> omxNode;
    android::Vector<BufferInfo> iBuffer, oBuffer;
};

Return<void> DummyBufferSource::onOmxExecuting() {
    executing = true;
    callback |= 0x1;
    size_t index;
    // Fetch a client owned input buffer and send an EOS
    if ((index = getEmptyBufferID(&iBuffer)) < iBuffer.size()) {
        android::hardware::media::omx::V1_0::Status status;
        CodecBuffer t = iBuffer[index].omxBuffer;
        t.type = CodecBuffer::Type::ANW_BUFFER;
        native_handle_t* fenceNh = native_handle_create(0, 0);
        EXPECT_NE(fenceNh, nullptr);
        status = omxNode->emptyBuffer(iBuffer[index].id, t, OMX_BUFFERFLAG_EOS,
                                      0, fenceNh);
        native_handle_close(fenceNh);
        native_handle_delete(fenceNh);
        EXPECT_EQ(status, android::hardware::media::omx::V1_0::Status::OK);
        iBuffer.editItemAt(index).owner = component;
    }
    return Void();
};

Return<void> DummyBufferSource::onOmxIdle() {
    callback |= 0x2;
    executing = false;
    return Void();
};

Return<void> DummyBufferSource::onOmxLoaded() {
    callback |= 0x4;
    return Void();
};

Return<void> DummyBufferSource::onInputBufferAdded(uint32_t buffer) {
    (void)buffer;
    EXPECT_EQ(executing, false);
    callback |= 0x8;
    return Void();
};

Return<void> DummyBufferSource::onInputBufferEmptied(
    uint32_t buffer, const ::android::hardware::hidl_handle& fence) {
    (void)fence;
    callback |= 0x10;
    size_t i;
    for (i = 0; i < iBuffer.size(); i++) {
        if (iBuffer[i].id == buffer) {
            iBuffer.editItemAt(i).owner = client;
            break;
        }
    }
    return Void();
};

// request VOP refresh
void requestIDR(sp<IOmxNode> omxNode, OMX_U32 portIndex) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_CONFIG_INTRAREFRESHVOPTYPE param;
    param.IntraRefreshVOP = OMX_TRUE;
    status = setPortConfig(omxNode, OMX_IndexConfigVideoIntraVOPRefresh,
                           portIndex, &param);
    if (status != ::android::hardware::media::omx::V1_0::Status::OK)
        std::cout << "[   INFO   ] unable to request IDR \n";
}

// modify bitrate
void changeBitrate(sp<IOmxNode> omxNode, OMX_U32 portIndex, uint32_t nBitrate) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_VIDEO_CONFIG_BITRATETYPE param;
    param.nEncodeBitrate = nBitrate;
    status =
        setPortConfig(omxNode, OMX_IndexConfigVideoBitrate, portIndex, &param);
    if (status != ::android::hardware::media::omx::V1_0::Status::OK)
        std::cout << "[   INFO   ] unable to change Bitrate \n";
}

// modify framerate
Return<android::hardware::media::omx::V1_0::Status> changeFrameRate(
    sp<IOmxNode> omxNode, OMX_U32 portIndex, uint32_t xFramerate) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_CONFIG_FRAMERATETYPE param;
    param.xEncodeFramerate = xFramerate;
    status = setPortConfig(omxNode, OMX_IndexConfigVideoFramerate, portIndex,
                           &param);
    if (status != ::android::hardware::media::omx::V1_0::Status::OK)
        std::cout << "[   INFO   ] unable to change Framerate \n";
    return status;
}

// modify intra refresh interval
void changeRefreshPeriod(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                         uint32_t nRefreshPeriod) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_VIDEO_CONFIG_ANDROID_INTRAREFRESHTYPE param;
    param.nRefreshPeriod = nRefreshPeriod;
    status = setPortConfig(omxNode,
                           (OMX_INDEXTYPE)OMX_IndexConfigAndroidIntraRefresh,
                           portIndex, &param);
    if (status != ::android::hardware::media::omx::V1_0::Status::OK)
        std::cout << "[   INFO   ] unable to change Refresh Period\n";
}

// set intra refresh interval
void setRefreshPeriod(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                      uint32_t nRefreshPeriod) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_VIDEO_PARAM_INTRAREFRESHTYPE param;
    param.eRefreshMode = OMX_VIDEO_IntraRefreshCyclic;
    param.nCirMBs = 0;
    if (nRefreshPeriod == 0)
        param.nCirMBs = 0;
    else {
        OMX_PARAM_PORTDEFINITIONTYPE portDef;
        status = getPortParam(omxNode, OMX_IndexParamPortDefinition, portIndex,
                              &portDef);
        if (status == ::android::hardware::media::omx::V1_0::Status::OK) {
            param.nCirMBs =
                ((portDef.format.video.nFrameWidth + 15) >>
                 4 * (portDef.format.video.nFrameHeight + 15) >> 4) /
                nRefreshPeriod;
        }
    }
    status = setPortParam(omxNode, OMX_IndexParamVideoIntraRefresh, portIndex,
                          &param);
    if (status != ::android::hardware::media::omx::V1_0::Status::OK)
        std::cout << "[   INFO   ] unable to set Refresh Period \n";
}

void setLatency(sp<IOmxNode> omxNode, OMX_U32 portIndex, uint32_t latency) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_PARAM_U32TYPE param;
    param.nU32 = (OMX_U32)latency;
    status = setPortConfig(omxNode, (OMX_INDEXTYPE)OMX_IndexConfigLatency,
                           portIndex, &param);
    if (status != ::android::hardware::media::omx::V1_0::Status::OK)
        std::cout << "[   INFO   ] unable to set latency\n";
}

void getLatency(sp<IOmxNode> omxNode, OMX_U32 portIndex, uint32_t* latency) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_PARAM_U32TYPE param;
    status = getPortConfig(omxNode, (OMX_INDEXTYPE)OMX_IndexConfigLatency,
                           portIndex, &param);
    if (status != ::android::hardware::media::omx::V1_0::Status::OK)
        std::cout << "[   INFO   ] unable to get latency\n";
    else
        *latency = param.nU32;
}

// Set Default port param.
void setDefaultPortParam(sp<IOmxNode> omxNode, OMX_U32 portIndex,
                         OMX_VIDEO_CODINGTYPE eCompressionFormat,
                         OMX_U32 nFrameWidth, OMX_U32 nFrameHeight,
                         OMX_U32 nBitrate, OMX_U32 xFramerate) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_PARAM_PORTDEFINITIONTYPE portDef;
    status = getPortParam(omxNode, OMX_IndexParamPortDefinition, portIndex,
                          &portDef);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    portDef.format.video.nFrameWidth = nFrameWidth;
    portDef.format.video.nFrameHeight = nFrameHeight;
    portDef.format.video.nBitrate = nBitrate;
    portDef.format.video.xFramerate = xFramerate;
    portDef.format.video.bFlagErrorConcealment = OMX_TRUE;
    portDef.format.video.eCompressionFormat = eCompressionFormat;
    portDef.format.video.eColorFormat = OMX_COLOR_FormatUnused;
    status = setPortParam(omxNode, OMX_IndexParamPortDefinition, portIndex,
                          &portDef);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    std::vector<int32_t> arrProfile;
    std::vector<int32_t> arrLevel;
    enumerateProfileAndLevel(omxNode, portIndex, &arrProfile, &arrLevel);
    if (arrProfile.empty() == true || arrLevel.empty() == true)
        ASSERT_TRUE(false);
    int32_t profile = arrProfile[0];
    int32_t level = arrLevel[0];

    switch ((int)eCompressionFormat) {
        case OMX_VIDEO_CodingAVC:
            setupAVCPort(omxNode, portIndex,
                         static_cast<OMX_VIDEO_AVCPROFILETYPE>(profile),
                         static_cast<OMX_VIDEO_AVCLEVELTYPE>(level),
                         xFramerate);
            break;
        case OMX_VIDEO_CodingHEVC:
            setupHEVCPort(omxNode, portIndex,
                          static_cast<OMX_VIDEO_HEVCPROFILETYPE>(profile),
                          static_cast<OMX_VIDEO_HEVCLEVELTYPE>(level));
            break;
        case OMX_VIDEO_CodingH263:
            setupH263Port(omxNode, portIndex,
                          static_cast<OMX_VIDEO_H263PROFILETYPE>(profile),
                          static_cast<OMX_VIDEO_H263LEVELTYPE>(level),
                          xFramerate);
            break;
        case OMX_VIDEO_CodingMPEG4:
            setupMPEG4Port(omxNode, portIndex,
                           static_cast<OMX_VIDEO_MPEG4PROFILETYPE>(profile),
                           static_cast<OMX_VIDEO_MPEG4LEVELTYPE>(level),
                           xFramerate);
            break;
        case OMX_VIDEO_CodingVP8:
            setupVPXPort(omxNode, portIndex, xFramerate);
            setupVP8Port(omxNode, portIndex,
                         static_cast<OMX_VIDEO_VP8PROFILETYPE>(profile),
                         static_cast<OMX_VIDEO_VP8LEVELTYPE>(level));
            break;
        case OMX_VIDEO_CodingVP9:
            setupVPXPort(omxNode, portIndex, xFramerate);
            setupVP9Port(omxNode, portIndex,
                         static_cast<OMX_VIDEO_VP9PROFILETYPE>(profile),
                         static_cast<OMX_VIDEO_VP9LEVELTYPE>(level));
            break;
        default:
            break;
    }
}

// LookUpTable of clips and metadata for component testing
void GetURLForComponent(char* URL) {
    strcat(URL, "bbb_352x288_420p_30fps_32frames.yuv");
}

// blocking call to ensures application to Wait till all the inputs are consumed
void waitOnInputConsumption(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
                            android::Vector<BufferInfo>* iBuffer,
                            android::Vector<BufferInfo>* oBuffer,
                            bool inputDataIsMeta = false,
                            sp<CodecProducerListener> listener = nullptr) {
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
        if (inputDataIsMeta) {
            if (listener->freeBuffers == iBuffer->size()) break;
        } else {
            for (; i < iBuffer->size(); i++) {
                if ((*iBuffer)[i].owner != client) break;
            }
            if (i == iBuffer->size()) break;
        }

        // Dispatch an output buffer assuming outQueue.empty() is true
        size_t index;
        if ((index = getEmptyBufferID(oBuffer)) < oBuffer->size()) {
            ASSERT_NO_FATAL_FAILURE(
                dispatchOutputBuffer(omxNode, oBuffer, index));
            timeOut = TIMEOUT_COUNTER_Q;
        }
    }
}

int colorFormatConversion(BufferInfo* buffer, void* buff, PixelFormat format,
                          std::ifstream& eleStream) {
    sp<android::hardware::graphics::mapper::V2_0::IMapper> mapper =
        android::hardware::graphics::mapper::V2_0::IMapper::getService();
    EXPECT_NE(mapper.get(), nullptr);
    if (mapper.get() == nullptr) return 1;

    android::hardware::hidl_handle fence;
    android::hardware::graphics::mapper::V2_0::IMapper::Rect rect;
    android::hardware::graphics::mapper::V2_0::YCbCrLayout ycbcrLayout;
    android::hardware::graphics::mapper::V2_0::Error error;
    rect.left = 0;
    rect.top = 0;
    rect.width = buffer->omxBuffer.attr.anwBuffer.width;
    rect.height = buffer->omxBuffer.attr.anwBuffer.height;

    if (format == PixelFormat::YV12 || format == PixelFormat::YCRCB_420_SP ||
        format == PixelFormat::YCBCR_420_888) {
        mapper->lockYCbCr(
            buff, buffer->omxBuffer.attr.anwBuffer.usage, rect, fence,
            [&](android::hardware::graphics::mapper::V2_0::Error _e,
                android::hardware::graphics::mapper::V2_0::YCbCrLayout _n1) {
                error = _e;
                ycbcrLayout = _n1;
            });
        EXPECT_EQ(error,
                  android::hardware::graphics::mapper::V2_0::Error::NONE);
        if (error != android::hardware::graphics::mapper::V2_0::Error::NONE)
            return 1;

        int size = ((rect.width * rect.height * 3) >> 1);
        char* img = new char[size];
        if (img == nullptr) return 1;
        eleStream.read(img, size);
        if (eleStream.gcount() != size) {
            delete[] img;
            return 1;
        }

        char* imgTmp = img;
        char* ipBuffer = static_cast<char*>(ycbcrLayout.y);
        for (size_t y = rect.height; y > 0; --y) {
            memcpy(ipBuffer, imgTmp, rect.width);
            ipBuffer += ycbcrLayout.yStride;
            imgTmp += rect.width;
        }

        if (format == PixelFormat::YV12)
            EXPECT_EQ(ycbcrLayout.chromaStep, 1U);
        else if (format == PixelFormat::YCRCB_420_SP)
            EXPECT_EQ(ycbcrLayout.chromaStep, 2U);

        ipBuffer = static_cast<char*>(ycbcrLayout.cb);
        for (size_t y = rect.height >> 1; y > 0; --y) {
            for (int32_t x = 0; x < (rect.width >> 1); ++x) {
                ipBuffer[ycbcrLayout.chromaStep * x] = *imgTmp++;
            }
            ipBuffer += ycbcrLayout.cStride;
        }
        ipBuffer = static_cast<char*>(ycbcrLayout.cr);
        for (size_t y = rect.height >> 1; y > 0; --y) {
            for (int32_t x = 0; x < (rect.width >> 1); ++x) {
                ipBuffer[ycbcrLayout.chromaStep * x] = *imgTmp++;
            }
            ipBuffer += ycbcrLayout.cStride;
        }

        delete[] img;

        mapper->unlock(buff,
                       [&](android::hardware::graphics::mapper::V2_0::Error _e,
                           android::hardware::hidl_handle _n1) {
                           error = _e;
                           fence = _n1;
                       });
        EXPECT_EQ(error,
                  android::hardware::graphics::mapper::V2_0::Error::NONE);
        if (error != android::hardware::graphics::mapper::V2_0::Error::NONE)
            return 1;
    } else {
        void* data;
        mapper->lock(buff, buffer->omxBuffer.attr.anwBuffer.usage, rect, fence,
                     [&](android::hardware::graphics::mapper::V2_0::Error _e,
                         void* _n1) {
                         error = _e;
                         data = _n1;
                     });
        EXPECT_EQ(error,
                  android::hardware::graphics::mapper::V2_0::Error::NONE);
        if (error != android::hardware::graphics::mapper::V2_0::Error::NONE)
            return 1;

        if (format == PixelFormat::BGRA_8888) {
            char* ipBuffer = static_cast<char*>(data);
            for (size_t y = rect.height; y > 0; --y) {
                eleStream.read(ipBuffer, rect.width * 4);
                if (eleStream.gcount() != rect.width * 4) return 1;
                ipBuffer += buffer->omxBuffer.attr.anwBuffer.stride * 4;
            }
        } else {
            EXPECT_TRUE(false) << "un expected pixel format";
            return 1;
        }

        mapper->unlock(buff,
                       [&](android::hardware::graphics::mapper::V2_0::Error _e,
                           android::hardware::hidl_handle _n1) {
                           error = _e;
                           fence = _n1;
                       });
        EXPECT_EQ(error,
                  android::hardware::graphics::mapper::V2_0::Error::NONE);
        if (error != android::hardware::graphics::mapper::V2_0::Error::NONE)
            return 1;
    }

    return 0;
}

int fillGraphicBuffer(BufferInfo* buffer, PixelFormat format,
                      std::ifstream& eleStream) {
    sp<android::hardware::graphics::mapper::V2_0::IMapper> mapper =
        android::hardware::graphics::mapper::V2_0::IMapper::getService();
    EXPECT_NE(mapper.get(), nullptr);
    if (mapper.get() == nullptr) return 1;

    void* buff = nullptr;
    android::hardware::graphics::mapper::V2_0::Error error;
    mapper->importBuffer(
        buffer->omxBuffer.nativeHandle,
        [&](android::hardware::graphics::mapper::V2_0::Error _e, void* _n1) {
            error = _e;
            buff = _n1;
        });
    EXPECT_EQ(error, android::hardware::graphics::mapper::V2_0::Error::NONE);
    if (error != android::hardware::graphics::mapper::V2_0::Error::NONE)
        return 1;

    if (colorFormatConversion(buffer, buff, format, eleStream)) return 1;

    error = mapper->freeBuffer(buff);
    EXPECT_EQ(error, android::hardware::graphics::mapper::V2_0::Error::NONE);
    if (error != android::hardware::graphics::mapper::V2_0::Error::NONE)
        return 1;

    return 0;
}

int dispatchGraphicBuffer(sp<IOmxNode> omxNode,
                          sp<IGraphicBufferProducer> producer,
                          sp<CodecProducerListener> listener,
                          android::Vector<BufferInfo>* buffArray,
                          OMX_U32 portIndex, std::ifstream& eleStream,
                          uint64_t timestamp) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_PARAM_PORTDEFINITIONTYPE portDef;

    status = getPortParam(omxNode, OMX_IndexParamPortDefinition, portIndex,
                          &portDef);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    if (status != ::android::hardware::media::omx::V1_0::Status::OK) return 1;

    enum {
        // A flag returned by dequeueBuffer when the client needs to call
        // requestBuffer immediately thereafter.
        BUFFER_NEEDS_REALLOCATION = 0x1,
        // A flag returned by dequeueBuffer when all mirrored slots should be
        // released by the client. This flag should always be processed first.
        RELEASE_ALL_BUFFERS = 0x2,
    };

    int32_t slot;
    int32_t result;
    ::android::hardware::hidl_handle fence;
    IGraphicBufferProducer::FrameEventHistoryDelta outTimestamps;
    ::android::hardware::media::V1_0::AnwBuffer AnwBuffer;
    PixelFormat format = PixelFormat::YCBCR_420_888;
    producer->dequeueBuffer(
        portDef.format.video.nFrameWidth, portDef.format.video.nFrameHeight,
        format, BufferUsage::CPU_READ_OFTEN | BufferUsage::CPU_WRITE_OFTEN,
        true, [&](int32_t _s, int32_t const& _n1,
                  ::android::hardware::hidl_handle const& _n2,
                  IGraphicBufferProducer::FrameEventHistoryDelta const& _n3) {
            result = _s;
            slot = _n1;
            fence = _n2;
            outTimestamps = _n3;
        });
    if (result & BUFFER_NEEDS_REALLOCATION) {
        producer->requestBuffer(
            slot, [&](int32_t _s,
                      ::android::hardware::media::V1_0::AnwBuffer const& _n1) {
                result = _s;
                AnwBuffer = _n1;
            });
        EXPECT_EQ(result, 0);
        if (result != 0) return 1;
        size_t i;
        for (i = 0; i < buffArray->size(); i++) {
            if ((*buffArray)[i].slot == -1) {
                buffArray->editItemAt(i).slot = slot;
                buffArray->editItemAt(i).omxBuffer.nativeHandle =
                    AnwBuffer.nativeHandle;
                buffArray->editItemAt(i).omxBuffer.attr.anwBuffer =
                    AnwBuffer.attr;
                break;
            }
        }
        EXPECT_NE(i, buffArray->size());
        if (i == buffArray->size()) return 1;
    }
    EXPECT_EQ(result, 0);
    if (result != 0) return 1;

    // fill Buffer
    BufferInfo buffer;
    size_t i;
    for (i = 0; i < buffArray->size(); i++) {
        if ((*buffArray)[i].slot == slot) {
            buffer = (*buffArray)[i];
            break;
        }
    }
    EXPECT_NE(i, buffArray->size());
    if (i == buffArray->size()) return 1;
    if (fillGraphicBuffer(&buffer, format, eleStream)) return 1;

    // queue Buffer
    IGraphicBufferProducer::QueueBufferOutput output;
    IGraphicBufferProducer::QueueBufferInput input;
    android::hardware::media::V1_0::Rect rect;
    rect.left = 0;
    rect.top = 0;
    rect.right = buffer.omxBuffer.attr.anwBuffer.width;
    rect.bottom = buffer.omxBuffer.attr.anwBuffer.height;
    input.timestamp = timestamp;
    input.isAutoTimestamp = false;
    input.dataSpace =
        android::hardware::graphics::common::V1_0::Dataspace::UNKNOWN;
    input.crop = rect;
    input.scalingMode = 0;
    input.transform = 0;
    input.stickyTransform = 0;
    input.fence = android::hardware::hidl_handle();
    input.surfaceDamage =
        android::hardware::hidl_vec<android::hardware::media::V1_0::Rect>{rect};
    input.getFrameTimestamps = false;
    producer->queueBuffer(
        buffer.slot, input,
        [&](int32_t _s, const IGraphicBufferProducer::QueueBufferOutput& _n1) {
            result = _s;
            output = _n1;
        });
    EXPECT_EQ(result, 0);
    if (result != 0) return 1;

    listener->reduceCount();

    return 0;
}

int fillByteBuffer(sp<IOmxNode> omxNode, char* ipBuffer, OMX_U32 portIndexInput,
                   std::ifstream& eleStream) {
    android::hardware::media::omx::V1_0::Status status;
    OMX_PARAM_PORTDEFINITIONTYPE portDef;
    uint32_t i, j;

    status = getPortParam(omxNode, OMX_IndexParamPortDefinition, portIndexInput,
                          &portDef);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    int size = ((portDef.format.video.nFrameWidth *
                 portDef.format.video.nFrameHeight * 3) >>
                1);
    char* img = new char[size];
    if (img == nullptr) return 1;
    eleStream.read(img, size);
    if (eleStream.gcount() != size) {
        delete[] img;
        return 1;
    }

    char* Y = ipBuffer;
    char* imgTmp = img;
    for (j = 0; j < portDef.format.video.nFrameHeight; ++j) {
        memcpy(Y, imgTmp, portDef.format.video.nFrameWidth);
        Y += portDef.format.video.nStride;
        imgTmp += portDef.format.video.nFrameWidth;
    }

    if (portDef.format.video.eColorFormat == OMX_COLOR_FormatYUV420SemiPlanar) {
        char* Cb = ipBuffer + (portDef.format.video.nFrameHeight *
                               portDef.format.video.nStride);
        char* Cr = Cb + 1;
        for (j = 0; j<portDef.format.video.nFrameHeight>> 1; ++j) {
            for (i = 0; i < (portDef.format.video.nFrameWidth >> 1); ++i) {
                Cb[2 * i] = *imgTmp++;
            }
            Cb += portDef.format.video.nStride;
        }
        for (j = 0; j<portDef.format.video.nFrameHeight>> 1; ++j) {
            for (i = 0; i < (portDef.format.video.nFrameWidth >> 1); ++i) {
                Cr[2 * i] = *imgTmp++;
            }
            Cr += portDef.format.video.nStride;
        }
    } else if (portDef.format.video.eColorFormat ==
               OMX_COLOR_FormatYUV420Planar) {
        char* Cb = ipBuffer + (portDef.format.video.nFrameHeight *
                               portDef.format.video.nStride);
        char* Cr = Cb + ((portDef.format.video.nFrameHeight *
                          portDef.format.video.nStride) >>
                         2);
        for (j = 0; j<portDef.format.video.nFrameHeight>> 1; ++j) {
            memcpy(Cb, imgTmp, (portDef.format.video.nFrameWidth >> 1));
            Cb += (portDef.format.video.nStride >> 1);
            imgTmp += (portDef.format.video.nFrameWidth >> 1);
        }
        for (j = 0; j<portDef.format.video.nFrameHeight>> 1; ++j) {
            memcpy(Cr, imgTmp, (portDef.format.video.nFrameWidth >> 1));
            Cr += (portDef.format.video.nStride >> 1);
            imgTmp += (portDef.format.video.nFrameWidth >> 1);
        }
    }

    delete[] img;
    return 0;
}

// Encode N Frames
void encodeNFrames(sp<IOmxNode> omxNode, sp<CodecObserver> observer,
                   OMX_U32 portIndexInput, OMX_U32 portIndexOutput,
                   android::Vector<BufferInfo>* iBuffer,
                   android::Vector<BufferInfo>* oBuffer, uint32_t nFrames,
                   uint32_t xFramerate, int bytesCount,
                   std::ifstream& eleStream,
                   ::android::List<uint64_t>* timestampUslist = nullptr,
                   bool signalEOS = true, bool inputDataIsMeta = false,
                   sp<IGraphicBufferProducer> producer = nullptr,
                   sp<CodecProducerListener> listener = nullptr) {
    android::hardware::media::omx::V1_0::Status status;
    Message msg;
    uint64_t timestamp = 0;
    uint32_t flags = 0;
    int timeOut = TIMEOUT_COUNTER_Q;
    bool iQueued, oQueued;

    uint32_t ipCount = 0;
    if (ipCount == 0) {
        status = changeFrameRate(omxNode, portIndexOutput, (24U << 16));
        if (status == ::android::hardware::media::omx::V1_0::Status::OK)
            xFramerate = (24U << 16);
    }
    int32_t timestampIncr = (int)((float)1000000 / (xFramerate >> 16));
    if (inputDataIsMeta) timestampIncr *= 1000;  // timestamp scale: Nano sec

    while (1) {
        iQueued = oQueued = false;
        status =
            observer->dequeueMessage(&msg, DEFAULT_TIMEOUT_Q, iBuffer, oBuffer);
        // Port Reconfiguration
        if (status == android::hardware::media::omx::V1_0::Status::OK) {
            ASSERT_EQ(msg.type, Message::Type::EVENT);
            if (msg.data.eventData.event == OMX_EventPortSettingsChanged) {
                ASSERT_EQ(msg.data.eventData.data1, portIndexOutput);
                ASSERT_EQ(msg.data.eventData.data2,
                          OMX_IndexConfigAndroidIntraRefresh);
            } else if (msg.data.eventData.event == OMX_EventError) {
                ASSERT_TRUE(false) << "Received OMX_EventError, not sure why";
            } else if (msg.data.eventData.event == OMX_EventDataSpaceChanged) {
                // TODO: how am i supposed to respond now?
                std::cout << "[   INFO   ] OMX_EventDataSpaceChanged \n";
            } else {
                ASSERT_TRUE(false);
            }
        }

        if (nFrames == 0) break;

        // Dispatch input buffer
        size_t index = 0;
        if (inputDataIsMeta) {
            if (listener->freeBuffers > listener->minUnDequeuedCount) {
                if (dispatchGraphicBuffer(omxNode, producer, listener, iBuffer,
                                          portIndexInput, eleStream,
                                          timestamp)) {
                    if (::testing::Test::HasFailure())
                        ASSERT_TRUE(false);
                    else
                        break;
                }
                timestamp += timestampIncr;
                nFrames--;
                ipCount++;
                iQueued = true;
            }
        } else {
            if ((index = getEmptyBufferID(iBuffer)) < iBuffer->size()) {
                char* ipBuffer = static_cast<char*>(static_cast<void*>(
                    (*iBuffer)[index].mMemory->getPointer()));
                ASSERT_LE(
                    bytesCount,
                    static_cast<int>((*iBuffer)[index].mMemory->getSize()));
                if (fillByteBuffer(omxNode, ipBuffer, portIndexInput,
                                   eleStream))
                    break;
                flags = OMX_BUFFERFLAG_ENDOFFRAME;
                if (signalEOS && (nFrames == 1)) flags |= OMX_BUFFERFLAG_EOS;
                ASSERT_NO_FATAL_FAILURE(dispatchInputBuffer(
                    omxNode, iBuffer, index, bytesCount, flags, timestamp));
                if (timestampUslist) timestampUslist->push_back(timestamp);
                timestamp += timestampIncr;
                nFrames--;
                ipCount++;
                iQueued = true;
            }
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
        // Runtime Param Configuration
        if (ipCount == 15) {
            changeBitrate(omxNode, portIndexOutput, 768000);
            requestIDR(omxNode, portIndexOutput);
            changeRefreshPeriod(omxNode, portIndexOutput, 15);
        }
    }
}

// set component role
TEST_F(VideoEncHidlTest, SetRole) {
    description("Test Set Component Role");
    if (disableTest) return;
    android::hardware::media::omx::V1_0::Status status;
    status = setRole(omxNode, gEnv->getRole().c_str());
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

// port format enumeration
TEST_F(VideoEncHidlTest, EnumeratePortFormat) {
    description("Test Component on Mandatory Port Parameters (Port Format)");
    if (disableTest) return;
    android::hardware::media::omx::V1_0::Status status;
    uint32_t kPortIndexInput = 0, kPortIndexOutput = 1;
    OMX_COLOR_FORMATTYPE eColorFormat = OMX_COLOR_FormatYUV420Planar;
    OMX_U32 xFramerate = (30U << 16);
    status = setRole(omxNode, gEnv->getRole().c_str());
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    OMX_PORT_PARAM_TYPE params;
    status = getParam(omxNode, OMX_IndexParamVideoInit, &params);
    if (status == ::android::hardware::media::omx::V1_0::Status::OK) {
        ASSERT_EQ(params.nPorts, 2U);
        kPortIndexInput = params.nStartPortNumber;
        kPortIndexOutput = kPortIndexInput + 1;
    }
    status =
        setVideoPortFormat(omxNode, kPortIndexInput, OMX_VIDEO_CodingUnused,
                           eColorFormat, xFramerate);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    status = setVideoPortFormat(omxNode, kPortIndexOutput, eCompressionFormat,
                                OMX_COLOR_FormatUnused, 0U);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
}

// Test IOmxBufferSource CallBacks
TEST_F(VideoEncHidlTest, BufferSourceCallBacks) {
    description("Test IOmxBufferSource CallBacks");
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

    // Configure input port
    uint32_t nFrameWidth = 352;
    uint32_t nFrameHeight = 288;
    uint32_t xFramerate = (30U << 16);
    OMX_COLOR_FORMATTYPE eColorFormat = OMX_COLOR_FormatAndroidOpaque;
    setupRAWPort(omxNode, kPortIndexInput, nFrameWidth, nFrameHeight, 0,
                 xFramerate, eColorFormat);

    sp<DummyBufferSource> buffersource = new DummyBufferSource(omxNode);
    ASSERT_NE(buffersource, nullptr);
    status = omxNode->setInputSurface(buffersource);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    // set port mode
    PortMode portMode[2];
    portMode[0] = PortMode::DYNAMIC_ANW_BUFFER;
    portMode[1] = PortMode::PRESET_BYTE_BUFFER;
    status = omxNode->setPortMode(kPortIndexInput, portMode[0]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = omxNode->setPortMode(kPortIndexOutput, portMode[1]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(changeStateLoadedtoIdle(
        omxNode, observer, &buffersource->iBuffer, &buffersource->oBuffer,
        kPortIndexInput, kPortIndexOutput, portMode));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));
    ASSERT_NO_FATAL_FAILURE(testEOS(omxNode, observer, &buffersource->iBuffer,
                                    &buffersource->oBuffer, false, eosFlag));
    // set state to idle
    ASSERT_NO_FATAL_FAILURE(changeStateExecutetoIdle(
        omxNode, observer, &buffersource->iBuffer, &buffersource->oBuffer));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoLoaded(
        omxNode, observer, &buffersource->iBuffer, &buffersource->oBuffer,
        kPortIndexInput, kPortIndexOutput));
    // test for callbacks
    EXPECT_EQ(buffersource->callback, 31);
}

// test raw stream encode (input is byte buffers)
TEST_F(VideoEncHidlTest, EncodeTest) {
    description("Test Encode");
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
    char mURL[512];
    strcpy(mURL, gEnv->getRes().c_str());
    GetURLForComponent(mURL);

    std::ifstream eleStream;

    timestampDevTest = true;

    // Configure input port
    uint32_t nFrameWidth = 352;
    uint32_t nFrameHeight = 288;
    uint32_t xFramerate = (30U << 16);
    OMX_COLOR_FORMATTYPE eColorFormat = OMX_COLOR_FormatUnused;
    OMX_VIDEO_PARAM_PORTFORMATTYPE portFormat;
    portFormat.nIndex = 0;
    while (1) {
        status = getPortParam(omxNode, OMX_IndexParamVideoPortFormat,
                              kPortIndexInput, &portFormat);
        if (status != ::android::hardware::media::omx::V1_0::Status::OK) break;
        EXPECT_EQ(portFormat.eCompressionFormat, OMX_VIDEO_CodingUnused);
        if (OMX_COLOR_FormatYUV420SemiPlanar == portFormat.eColorFormat ||
            OMX_COLOR_FormatYUV420Planar == portFormat.eColorFormat) {
            eColorFormat = portFormat.eColorFormat;
            break;
        }
        portFormat.nIndex++;
        if (portFormat.nIndex == 512) break;
    }
    ASSERT_NE(eColorFormat, OMX_COLOR_FormatUnused);
    setupRAWPort(omxNode, kPortIndexInput, nFrameWidth, nFrameHeight, 0,
                 xFramerate, eColorFormat);

    // Configure output port
    uint32_t nBitRate = 512000;
    ASSERT_NO_FATAL_FAILURE(
        setDefaultPortParam(omxNode, kPortIndexOutput, eCompressionFormat,
                            nFrameWidth, nFrameHeight, nBitRate, xFramerate));
    setRefreshPeriod(omxNode, kPortIndexOutput, 0);

    unsigned int index;
    omxNode->getExtensionIndex(
        "OMX.google.android.index.prependSPSPPSToIDRFrames",
        [&status, &index](android::hardware::media::omx::V1_0::Status _s,
                          unsigned int _nl) {
            status = _s;
            index = _nl;
        });
    if (status == ::android::hardware::media::omx::V1_0::Status::OK) {
        android::PrependSPSPPSToIDRFramesParams param;
        param.bEnable = OMX_TRUE;
        status = setParam(omxNode, static_cast<OMX_INDEXTYPE>(index), &param);
    }
    if (status != ::android::hardware::media::omx::V1_0::Status::OK)
        std::cout << "[   INFO   ] unable to prependSPSPPSToIDRFrames\n";
    else
        prependSPSPPS = true;

    // set port mode
    PortMode portMode[2];
    portMode[0] = portMode[1] = PortMode::PRESET_BYTE_BUFFER;
    status = omxNode->setPortMode(kPortIndexInput, portMode[0]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = omxNode->setPortMode(kPortIndexOutput, portMode[1]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    uint32_t latency = 0;
    setLatency(omxNode, kPortIndexInput, latency);
    getLatency(omxNode, kPortIndexInput, &latency);

    android::Vector<BufferInfo> iBuffer, oBuffer;

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(
        changeStateLoadedtoIdle(omxNode, observer, &iBuffer, &oBuffer,
                                kPortIndexInput, kPortIndexOutput, portMode));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));

    eleStream.open(mURL, std::ifstream::binary);
    ASSERT_EQ(eleStream.is_open(), true);
    ASSERT_NO_FATAL_FAILURE(encodeNFrames(
        omxNode, observer, kPortIndexInput, kPortIndexOutput, &iBuffer,
        &oBuffer, 32, xFramerate, (nFrameWidth * nFrameHeight * 3) >> 1,
        eleStream, &timestampUslist));
    eleStream.close();
    ASSERT_NO_FATAL_FAILURE(
        waitOnInputConsumption(omxNode, observer, &iBuffer, &oBuffer));
    ASSERT_NO_FATAL_FAILURE(
        testEOS(omxNode, observer, &iBuffer, &oBuffer, false, eosFlag));
    if (timestampDevTest) EXPECT_EQ(timestampUslist.empty(), true);

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(
        changeStateExecutetoIdle(omxNode, observer, &iBuffer, &oBuffer));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoLoaded(omxNode, observer, &iBuffer,
                                                    &oBuffer, kPortIndexInput,
                                                    kPortIndexOutput));
}

// test raw stream encode (input is ANW buffers)
TEST_F(VideoEncHidlTest, EncodeTestBufferMetaModes) {
    description("Test Encode Input buffer metamodes");
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

    // Configure input port
    uint32_t nFrameWidth = 352;
    uint32_t nFrameHeight = 288;
    uint32_t xFramerate = (30U << 16);
    OMX_COLOR_FORMATTYPE eColorFormat = OMX_COLOR_FormatAndroidOpaque;
    setupRAWPort(omxNode, kPortIndexInput, nFrameWidth, nFrameHeight, 0,
                 xFramerate, eColorFormat);

    // Configure output port
    uint32_t nBitRate = 512000;
    ASSERT_NO_FATAL_FAILURE(
        setDefaultPortParam(omxNode, kPortIndexOutput, eCompressionFormat,
                            nFrameWidth, nFrameHeight, nBitRate, xFramerate));
    // CreateInputSurface
    EXPECT_TRUE(omx->createInputSurface(
                       [&](android::hardware::media::omx::V1_0::Status _s,
                           sp<IGraphicBufferProducer> const& _nl,
                           sp<IGraphicBufferSource> const& _n2) {
                           status = _s;
                           producer = _nl;
                           source = _n2;
                       })
                    .isOk());
    ASSERT_NE(producer, nullptr);
    ASSERT_NE(source, nullptr);

    // setMaxDequeuedBufferCount
    int32_t returnval;
    int32_t value;
    producer->query(NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS,
                    [&returnval, &value](int32_t _s, int32_t _n1) {
                        returnval = _s;
                        value = _n1;
                    });
    ASSERT_EQ(returnval, 0);
    OMX_PARAM_PORTDEFINITIONTYPE portDef;
    status = getPortParam(omxNode, OMX_IndexParamPortDefinition,
                          kPortIndexInput, &portDef);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    ASSERT_EQ(::android::OK,
              producer->setMaxDequeuedBufferCount(portDef.nBufferCountActual));

    // Connect :: Mock Producer Listener
    IGraphicBufferProducer::QueueBufferOutput qbo;
    sp<CodecProducerListener> listener =
        new CodecProducerListener(portDef.nBufferCountActual + value, value);
    producer->connect(
        listener, NATIVE_WINDOW_API_CPU, false,
        [&](int32_t _s, IGraphicBufferProducer::QueueBufferOutput const& _n1) {
            returnval = _s;
            qbo = _n1;
        });
    ASSERT_EQ(returnval, 0);

    portDef.nBufferCountActual = portDef.nBufferCountActual + value;
    status = setPortParam(omxNode, OMX_IndexParamPortDefinition,
                          kPortIndexInput, &portDef);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    // Do setInputSurface()
    // enable MetaMode on input port
    status = source->configure(
        omxNode, android::hardware::graphics::common::V1_0::Dataspace::UNKNOWN);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    // set port mode
    PortMode portMode[2];
    portMode[0] = PortMode::DYNAMIC_ANW_BUFFER;
    portMode[1] = PortMode::PRESET_BYTE_BUFFER;
    status = omxNode->setPortMode(kPortIndexInput, portMode[0]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = omxNode->setPortMode(kPortIndexOutput, portMode[1]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    char mURL[512];
    strcpy(mURL, gEnv->getRes().c_str());
    GetURLForComponent(mURL);

    uint32_t latency = 0;
    setLatency(omxNode, kPortIndexInput, latency);
    getLatency(omxNode, kPortIndexInput, &latency);

    std::ifstream eleStream;

    status = source->setSuspend(false, 0);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = source->setRepeatPreviousFrameDelayUs(100000);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = source->setMaxFps(24.0f);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = source->setTimeLapseConfig(24.0, 24.0);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = source->setTimeOffsetUs(-100);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = source->setStartTimeUs(10);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = source->setStopTimeUs(1000000);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    ::android::hardware::media::omx::V1_0::ColorAspects aspects;
    aspects.range =
        ::android::hardware::media::omx::V1_0::ColorAspects::Range::UNSPECIFIED;
    aspects.primaries = ::android::hardware::media::omx::V1_0::ColorAspects::
        Primaries::UNSPECIFIED;
    aspects.transfer = ::android::hardware::media::omx::V1_0::ColorAspects::
        Transfer::UNSPECIFIED;
    aspects.matrixCoeffs = ::android::hardware::media::omx::V1_0::ColorAspects::
        MatrixCoeffs::UNSPECIFIED;
    status = source->setColorAspects(aspects);
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    int64_t stopTimeOffsetUs;
    source->getStopTimeOffsetUs(
        [&](android::hardware::media::omx::V1_0::Status _s, int64_t _n1) {
            status = _s;
            stopTimeOffsetUs = _n1;
        });
    EXPECT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    android::Vector<BufferInfo> iBuffer, oBuffer;
    // set state to idle
    ASSERT_NO_FATAL_FAILURE(
        changeStateLoadedtoIdle(omxNode, observer, &iBuffer, &oBuffer,
                                kPortIndexInput, kPortIndexOutput, portMode));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));

    eleStream.open(mURL, std::ifstream::binary);
    ASSERT_EQ(eleStream.is_open(), true);
    ASSERT_NO_FATAL_FAILURE(encodeNFrames(
        omxNode, observer, kPortIndexInput, kPortIndexOutput, &iBuffer,
        &oBuffer, 1024, xFramerate, (nFrameWidth * nFrameHeight * 3) >> 1,
        eleStream, nullptr, false, true, producer, listener));
    eleStream.close();
    ASSERT_NO_FATAL_FAILURE(waitOnInputConsumption(omxNode, observer, &iBuffer,
                                                   &oBuffer, true, listener));
    ASSERT_NO_FATAL_FAILURE(
        testEOS(omxNode, observer, &iBuffer, &oBuffer, false, eosFlag));

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(
        changeStateExecutetoIdle(omxNode, observer, &iBuffer, &oBuffer));
    EXPECT_EQ(portDef.nBufferCountActual, listener->freeBuffers);
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoLoaded(omxNode, observer, &iBuffer,
                                                    &oBuffer, kPortIndexInput,
                                                    kPortIndexOutput));

    returnval = producer->disconnect(
        NATIVE_WINDOW_API_CPU, IGraphicBufferProducer::DisconnectMode::API);
    ASSERT_EQ(returnval, 0);
}

// Test end of stream
TEST_F(VideoEncHidlTest, EncodeTestEOS) {
    description("Test EOS");
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

    // Configure input port
    uint32_t nFrameWidth = 352;
    uint32_t nFrameHeight = 288;
    uint32_t xFramerate = (30U << 16);
    OMX_COLOR_FORMATTYPE eColorFormat = OMX_COLOR_FormatAndroidOpaque;
    setupRAWPort(omxNode, kPortIndexInput, nFrameWidth, nFrameHeight, 0,
                 xFramerate, eColorFormat);

    // CreateInputSurface
    EXPECT_TRUE(omx->createInputSurface(
                       [&](android::hardware::media::omx::V1_0::Status _s,
                           sp<IGraphicBufferProducer> const& _nl,
                           sp<IGraphicBufferSource> const& _n2) {
                           status = _s;
                           producer = _nl;
                           source = _n2;
                       })
                    .isOk());
    ASSERT_NE(producer, nullptr);
    ASSERT_NE(source, nullptr);

    // setMaxDequeuedBufferCount
    int32_t returnval;
    int32_t value;
    producer->query(NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS,
                    [&returnval, &value](int32_t _s, int32_t _n1) {
                        returnval = _s;
                        value = _n1;
                    });
    ASSERT_EQ(returnval, 0);
    OMX_PARAM_PORTDEFINITIONTYPE portDef;
    status = getPortParam(omxNode, OMX_IndexParamPortDefinition,
                          kPortIndexInput, &portDef);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    ASSERT_EQ(::android::OK,
              producer->setMaxDequeuedBufferCount(portDef.nBufferCountActual));

    // Connect :: Mock Producer Listener
    IGraphicBufferProducer::QueueBufferOutput qbo;
    sp<CodecProducerListener> listener =
        new CodecProducerListener(portDef.nBufferCountActual + value, value);
    producer->connect(
        listener, NATIVE_WINDOW_API_CPU, false,
        [&](int32_t _s, IGraphicBufferProducer::QueueBufferOutput const& _n1) {
            returnval = _s;
            qbo = _n1;
        });
    ASSERT_EQ(returnval, 0);

    portDef.nBufferCountActual = portDef.nBufferCountActual + value;
    status = setPortParam(omxNode, OMX_IndexParamPortDefinition,
                          kPortIndexInput, &portDef);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    // Do setInputSurface()
    // enable MetaMode on input port
    status = source->configure(
        omxNode, android::hardware::graphics::common::V1_0::Dataspace::UNKNOWN);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    // set port mode
    PortMode portMode[2];
    portMode[0] = PortMode::DYNAMIC_ANW_BUFFER;
    portMode[1] = PortMode::PRESET_BYTE_BUFFER;
    status = omxNode->setPortMode(kPortIndexInput, portMode[0]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    status = omxNode->setPortMode(kPortIndexOutput, portMode[1]);
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);

    android::Vector<BufferInfo> iBuffer, oBuffer;
    // set state to idle
    ASSERT_NO_FATAL_FAILURE(
        changeStateLoadedtoIdle(omxNode, observer, &iBuffer, &oBuffer,
                                kPortIndexInput, kPortIndexOutput, portMode));
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoExecute(omxNode, observer));

    // send EOS
    status = source->signalEndOfInputStream();
    ASSERT_EQ(status, ::android::hardware::media::omx::V1_0::Status::OK);
    ASSERT_NO_FATAL_FAILURE(waitOnInputConsumption(omxNode, observer, &iBuffer,
                                                   &oBuffer, true, listener));
    ASSERT_NO_FATAL_FAILURE(
        testEOS(omxNode, observer, &iBuffer, &oBuffer, false, eosFlag));

    // set state to idle
    ASSERT_NO_FATAL_FAILURE(
        changeStateExecutetoIdle(omxNode, observer, &iBuffer, &oBuffer));
    EXPECT_EQ(portDef.nBufferCountActual, listener->freeBuffers);
    // set state to executing
    ASSERT_NO_FATAL_FAILURE(changeStateIdletoLoaded(omxNode, observer, &iBuffer,
                                                    &oBuffer, kPortIndexInput,
                                                    kPortIndexOutput));

    returnval = producer->disconnect(
        NATIVE_WINDOW_API_CPU, IGraphicBufferProducer::DisconnectMode::API);
    ASSERT_EQ(returnval, 0);
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
