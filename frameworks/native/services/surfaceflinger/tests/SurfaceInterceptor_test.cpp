/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <frameworks/native/cmds/surfacereplayer/proto/src/trace.pb.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>

#include <gtest/gtest.h>

#include <android/native_window.h>

#include <gui/ISurfaceComposer.h>
#include <gui/Surface.h>
#include <gui/SurfaceComposerClient.h>
#include <private/gui/ComposerService.h>
#include <private/gui/LayerState.h>
#include <ui/DisplayInfo.h>

#include <fstream>
#include <random>
#include <thread>

namespace android {

constexpr int32_t SCALING_UPDATE = 1;
constexpr uint32_t BUFFER_UPDATES = 18;
constexpr uint32_t LAYER_UPDATE = INT_MAX - 2;
constexpr uint32_t SIZE_UPDATE = 134;
constexpr uint32_t STACK_UPDATE = 1;
constexpr uint64_t DEFERRED_UPDATE = 13;
constexpr float ALPHA_UPDATE = 0.29f;
constexpr float POSITION_UPDATE = 121;
const Rect CROP_UPDATE(16, 16, 32, 32);

const String8 DISPLAY_NAME("SurfaceInterceptor Display Test");
constexpr auto LAYER_NAME = "Layer Create and Delete Test";

constexpr auto DEFAULT_FILENAME = "/data/SurfaceTrace.dat";

// Fill an RGBA_8888 formatted surface with a single color.
static void fillSurfaceRGBA8(const sp<SurfaceControl>& sc, uint8_t r, uint8_t g, uint8_t b) {
    ANativeWindow_Buffer outBuffer;
    sp<Surface> s = sc->getSurface();
    ASSERT_TRUE(s != nullptr);
    ASSERT_EQ(NO_ERROR, s->lock(&outBuffer, nullptr));
    uint8_t* img = reinterpret_cast<uint8_t*>(outBuffer.bits);
    for (int y = 0; y < outBuffer.height; y++) {
        for (int x = 0; x < outBuffer.width; x++) {
            uint8_t* pixel = img + (4 * (y*outBuffer.stride + x));
            pixel[0] = r;
            pixel[1] = g;
            pixel[2] = b;
            pixel[3] = 255;
        }
    }
    ASSERT_EQ(NO_ERROR, s->unlockAndPost());
}

static status_t readProtoFile(Trace* trace) {
    status_t err = NO_ERROR;

    int fd = open(DEFAULT_FILENAME, O_RDONLY);
    {
        google::protobuf::io::FileInputStream f(fd);
        if (fd && !trace->ParseFromZeroCopyStream(&f)) {
            err = PERMISSION_DENIED;
        }
    }
    close(fd);

    return err;
}

static void enableInterceptor() {
    system("service call SurfaceFlinger 1020 i32 1 > /dev/null");
}

static void disableInterceptor() {
    system("service call SurfaceFlinger 1020 i32 0 > /dev/null");
}

int32_t getSurfaceId(const std::string& surfaceName) {
    enableInterceptor();
    disableInterceptor();
    Trace capturedTrace;
    readProtoFile(&capturedTrace);
    int32_t layerId = 0;
    for (const auto& increment : *capturedTrace.mutable_increment()) {
        if (increment.increment_case() == increment.kSurfaceCreation) {
            if (increment.surface_creation().name() == surfaceName) {
                layerId = increment.surface_creation().id();
                break;
            }
        }
    }
    return layerId;
}

int32_t getDisplayId(const std::string& displayName) {
    enableInterceptor();
    disableInterceptor();
    Trace capturedTrace;
    readProtoFile(&capturedTrace);
    int32_t displayId = 0;
    for (const auto& increment : *capturedTrace.mutable_increment()) {
        if (increment.increment_case() == increment.kDisplayCreation) {
            if (increment.display_creation().name() == displayName) {
                displayId = increment.display_creation().id();
                break;
            }
        }
    }
    return displayId;
}

class SurfaceInterceptorTest : public ::testing::Test {
protected:
    virtual void SetUp() {
        // Allow SurfaceInterceptor write to /data
        system("setenforce 0");

        mComposerClient = new SurfaceComposerClient;
        ASSERT_EQ(NO_ERROR, mComposerClient->initCheck());

        sp<IBinder> display(SurfaceComposerClient::getBuiltInDisplay(
                ISurfaceComposer::eDisplayIdMain));
        DisplayInfo info;
        SurfaceComposerClient::getDisplayInfo(display, &info);
        ssize_t displayWidth = info.w;
        ssize_t displayHeight = info.h;

        // Background surface
        mBGSurfaceControl = mComposerClient->createSurface(
                String8("BG Interceptor Test Surface"), displayWidth, displayHeight,
                PIXEL_FORMAT_RGBA_8888, 0);
        ASSERT_TRUE(mBGSurfaceControl != NULL);
        ASSERT_TRUE(mBGSurfaceControl->isValid());
        mBGLayerId = getSurfaceId("BG Interceptor Test Surface");

        SurfaceComposerClient::openGlobalTransaction();
        mComposerClient->setDisplayLayerStack(display, 0);
        ASSERT_EQ(NO_ERROR, mBGSurfaceControl->setLayer(INT_MAX-3));
        ASSERT_EQ(NO_ERROR, mBGSurfaceControl->show());
        SurfaceComposerClient::closeGlobalTransaction(true);
    }

    virtual void TearDown() {
        mComposerClient->dispose();
        mBGSurfaceControl.clear();
        mComposerClient.clear();
    }

    sp<SurfaceComposerClient> mComposerClient;
    sp<SurfaceControl> mBGSurfaceControl;
    int32_t mBGLayerId;
    // Used to verify creation and destruction of surfaces and displays
    int32_t mTargetId;

public:
    void captureTest(void (SurfaceInterceptorTest::* action)(void),
            bool (SurfaceInterceptorTest::* verification)(Trace *));
    void captureTest(void (SurfaceInterceptorTest::* action)(void),
            SurfaceChange::SurfaceChangeCase changeCase);
    void captureTest(void (SurfaceInterceptorTest::* action)(void),
            Increment::IncrementCase incrementCase);
    void runInTransaction(void (SurfaceInterceptorTest::* action)(void), bool intercepted = false);

    // Verification of changes to a surface
    bool positionUpdateFound(const SurfaceChange& change, bool foundPosition);
    bool sizeUpdateFound(const SurfaceChange& change, bool foundSize);
    bool alphaUpdateFound(const SurfaceChange& change, bool foundAlpha);
    bool layerUpdateFound(const SurfaceChange& change, bool foundLayer);
    bool cropUpdateFound(const SurfaceChange& change, bool foundCrop);
    bool finalCropUpdateFound(const SurfaceChange& change, bool foundFinalCrop);
    bool matrixUpdateFound(const SurfaceChange& change, bool foundMatrix);
    bool scalingModeUpdateFound(const SurfaceChange& change, bool foundScalingMode);
    bool transparentRegionHintUpdateFound(const SurfaceChange& change, bool foundTransparentRegion);
    bool layerStackUpdateFound(const SurfaceChange& change, bool foundLayerStack);
    bool hiddenFlagUpdateFound(const SurfaceChange& change, bool foundHiddenFlag);
    bool opaqueFlagUpdateFound(const SurfaceChange& change, bool foundOpaqueFlag);
    bool secureFlagUpdateFound(const SurfaceChange& change, bool foundSecureFlag);
    bool deferredTransactionUpdateFound(const SurfaceChange& change, bool foundDeferred);
    bool surfaceUpdateFound(Trace* trace, SurfaceChange::SurfaceChangeCase changeCase);
    void assertAllUpdatesFound(Trace* trace);

    // Verification of creation and deletion of a surface
    bool surfaceCreationFound(const Increment& increment, bool foundSurface);
    bool surfaceDeletionFound(const Increment& increment, bool foundSurface);
    bool displayCreationFound(const Increment& increment, bool foundDisplay);
    bool displayDeletionFound(const Increment& increment, bool foundDisplay);
    bool singleIncrementFound(Trace* trace, Increment::IncrementCase incrementCase);

    // Verification of buffer updates
    bool bufferUpdatesFound(Trace* trace);

    // Perform each of the possible changes to a surface
    void positionUpdate();
    void sizeUpdate();
    void alphaUpdate();
    void layerUpdate();
    void cropUpdate();
    void finalCropUpdate();
    void matrixUpdate();
    void overrideScalingModeUpdate();
    void transparentRegionHintUpdate();
    void layerStackUpdate();
    void hiddenFlagUpdate();
    void opaqueFlagUpdate();
    void secureFlagUpdate();
    void deferredTransactionUpdate();
    void runAllUpdates();
    void surfaceCreation();
    void nBufferUpdates();
    void displayCreation();
    void displayDeletion();
};

void SurfaceInterceptorTest::captureTest(void (SurfaceInterceptorTest::* action)(void),
        bool (SurfaceInterceptorTest::* verification)(Trace *))
{
    runInTransaction(action, true);
    Trace capturedTrace;
    ASSERT_EQ(NO_ERROR, readProtoFile(&capturedTrace));
    ASSERT_TRUE((this->*verification)(&capturedTrace));
}

void SurfaceInterceptorTest::captureTest(void (SurfaceInterceptorTest::* action)(void),
        Increment::IncrementCase incrementCase)
{
    runInTransaction(action, true);
    Trace capturedTrace;
    ASSERT_EQ(NO_ERROR, readProtoFile(&capturedTrace));
    ASSERT_TRUE(singleIncrementFound(&capturedTrace, incrementCase));
}

void SurfaceInterceptorTest::captureTest(void (SurfaceInterceptorTest::* action)(void),
        SurfaceChange::SurfaceChangeCase changeCase)
{
    runInTransaction(action, true);
    Trace capturedTrace;
    ASSERT_EQ(NO_ERROR, readProtoFile(&capturedTrace));
    ASSERT_TRUE(surfaceUpdateFound(&capturedTrace, changeCase));
}

void SurfaceInterceptorTest::runInTransaction(void (SurfaceInterceptorTest::* action)(void),
        bool intercepted)
{
    if (intercepted) {
        enableInterceptor();
    }
    SurfaceComposerClient::openGlobalTransaction();
    (this->*action)();
    SurfaceComposerClient::closeGlobalTransaction(true);
    if (intercepted) {
        disableInterceptor();
    }
}

void SurfaceInterceptorTest::positionUpdate() {
    mBGSurfaceControl->setPosition(POSITION_UPDATE, POSITION_UPDATE);
}

void SurfaceInterceptorTest::sizeUpdate() {
    mBGSurfaceControl->setSize(SIZE_UPDATE, SIZE_UPDATE);
}

void SurfaceInterceptorTest::alphaUpdate() {
    mBGSurfaceControl->setAlpha(ALPHA_UPDATE);
}

void SurfaceInterceptorTest::layerUpdate() {
    mBGSurfaceControl->setLayer(LAYER_UPDATE);
}

void SurfaceInterceptorTest::cropUpdate() {
    mBGSurfaceControl->setCrop(CROP_UPDATE);
}

void SurfaceInterceptorTest::finalCropUpdate() {
    mBGSurfaceControl->setFinalCrop(CROP_UPDATE);
}

void SurfaceInterceptorTest::matrixUpdate() {
    mBGSurfaceControl->setMatrix(M_SQRT1_2, M_SQRT1_2, -M_SQRT1_2, M_SQRT1_2);
}

void SurfaceInterceptorTest::overrideScalingModeUpdate() {
    mBGSurfaceControl->setOverrideScalingMode(SCALING_UPDATE);
}

void SurfaceInterceptorTest::transparentRegionHintUpdate() {
    Region region(CROP_UPDATE);
    mBGSurfaceControl->setTransparentRegionHint(region);
}

void SurfaceInterceptorTest::layerStackUpdate() {
    mBGSurfaceControl->setLayerStack(STACK_UPDATE);
}

void SurfaceInterceptorTest::hiddenFlagUpdate() {
    mBGSurfaceControl->setFlags(layer_state_t::eLayerHidden, layer_state_t::eLayerHidden);
}

void SurfaceInterceptorTest::opaqueFlagUpdate() {
    mBGSurfaceControl->setFlags(layer_state_t::eLayerOpaque, layer_state_t::eLayerOpaque);
}

void SurfaceInterceptorTest::secureFlagUpdate() {
    mBGSurfaceControl->setFlags(layer_state_t::eLayerSecure, layer_state_t::eLayerSecure);
}

void SurfaceInterceptorTest::deferredTransactionUpdate() {
    mBGSurfaceControl->deferTransactionUntil(mBGSurfaceControl->getHandle(), DEFERRED_UPDATE);
}

void SurfaceInterceptorTest::displayCreation() {
    sp<IBinder> testDisplay = SurfaceComposerClient::createDisplay(DISPLAY_NAME, true);
    SurfaceComposerClient::destroyDisplay(testDisplay);
}

void SurfaceInterceptorTest::displayDeletion() {
    sp<IBinder> testDisplay = SurfaceComposerClient::createDisplay(DISPLAY_NAME, false);
    mTargetId = getDisplayId(DISPLAY_NAME.string());
    SurfaceComposerClient::destroyDisplay(testDisplay);
}

void SurfaceInterceptorTest::runAllUpdates() {
    runInTransaction(&SurfaceInterceptorTest::positionUpdate);
    runInTransaction(&SurfaceInterceptorTest::sizeUpdate);
    runInTransaction(&SurfaceInterceptorTest::alphaUpdate);
    runInTransaction(&SurfaceInterceptorTest::layerUpdate);
    runInTransaction(&SurfaceInterceptorTest::cropUpdate);
    runInTransaction(&SurfaceInterceptorTest::finalCropUpdate);
    runInTransaction(&SurfaceInterceptorTest::matrixUpdate);
    runInTransaction(&SurfaceInterceptorTest::overrideScalingModeUpdate);
    runInTransaction(&SurfaceInterceptorTest::transparentRegionHintUpdate);
    runInTransaction(&SurfaceInterceptorTest::layerStackUpdate);
    runInTransaction(&SurfaceInterceptorTest::hiddenFlagUpdate);
    runInTransaction(&SurfaceInterceptorTest::opaqueFlagUpdate);
    runInTransaction(&SurfaceInterceptorTest::secureFlagUpdate);
    runInTransaction(&SurfaceInterceptorTest::deferredTransactionUpdate);
}

void SurfaceInterceptorTest::surfaceCreation() {
    mComposerClient->createSurface(String8(LAYER_NAME), SIZE_UPDATE, SIZE_UPDATE,
            PIXEL_FORMAT_RGBA_8888, 0);
}

void SurfaceInterceptorTest::nBufferUpdates() {
    std::random_device rd;
    std::mt19937_64 gen(rd());
    // This makes testing fun
    std::uniform_int_distribution<uint8_t> dis;
    for (uint32_t i = 0; i < BUFFER_UPDATES; ++i) {
        fillSurfaceRGBA8(mBGSurfaceControl, dis(gen), dis(gen), dis(gen));
    }
}

bool SurfaceInterceptorTest::positionUpdateFound(const SurfaceChange& change, bool foundPosition) {
    // There should only be one position transaction with x and y = POSITION_UPDATE
    bool hasX(change.position().x() == POSITION_UPDATE);
    bool hasY(change.position().y() == POSITION_UPDATE);
    if (hasX && hasY && !foundPosition) {
        foundPosition = true;
    }
    // Failed because the position update was found a second time
    else if (hasX && hasY && foundPosition) {
        [] () { FAIL(); }();
    }
    return foundPosition;
}

bool SurfaceInterceptorTest::sizeUpdateFound(const SurfaceChange& change, bool foundSize) {
    bool hasWidth(change.size().h() == SIZE_UPDATE);
    bool hasHeight(change.size().w() == SIZE_UPDATE);
    if (hasWidth && hasHeight && !foundSize) {
        foundSize = true;
    }
    else if (hasWidth && hasHeight && foundSize) {
        [] () { FAIL(); }();
    }
    return foundSize;
}

bool SurfaceInterceptorTest::alphaUpdateFound(const SurfaceChange& change, bool foundAlpha) {
    bool hasAlpha(change.alpha().alpha() == ALPHA_UPDATE);
    if (hasAlpha && !foundAlpha) {
        foundAlpha = true;
    }
    else if (hasAlpha && foundAlpha) {
        [] () { FAIL(); }();
    }
    return foundAlpha;
}

bool SurfaceInterceptorTest::layerUpdateFound(const SurfaceChange& change, bool foundLayer) {
    bool hasLayer(change.layer().layer() == LAYER_UPDATE);
    if (hasLayer && !foundLayer) {
        foundLayer = true;
    }
    else if (hasLayer && foundLayer) {
        [] () { FAIL(); }();
    }
    return foundLayer;
}

bool SurfaceInterceptorTest::cropUpdateFound(const SurfaceChange& change, bool foundCrop) {
    bool hasLeft(change.crop().rectangle().left() == CROP_UPDATE.left);
    bool hasTop(change.crop().rectangle().top() == CROP_UPDATE.top);
    bool hasRight(change.crop().rectangle().right() == CROP_UPDATE.right);
    bool hasBottom(change.crop().rectangle().bottom() == CROP_UPDATE.bottom);
    if (hasLeft && hasRight && hasTop && hasBottom && !foundCrop) {
        foundCrop = true;
    }
    else if (hasLeft && hasRight && hasTop && hasBottom && foundCrop) {
        [] () { FAIL(); }();
    }
    return foundCrop;
}

bool SurfaceInterceptorTest::finalCropUpdateFound(const SurfaceChange& change,
        bool foundFinalCrop)
{
    bool hasLeft(change.final_crop().rectangle().left() == CROP_UPDATE.left);
    bool hasTop(change.final_crop().rectangle().top() == CROP_UPDATE.top);
    bool hasRight(change.final_crop().rectangle().right() == CROP_UPDATE.right);
    bool hasBottom(change.final_crop().rectangle().bottom() == CROP_UPDATE.bottom);
    if (hasLeft && hasRight && hasTop && hasBottom && !foundFinalCrop) {
        foundFinalCrop = true;
    }
    else if (hasLeft && hasRight && hasTop && hasBottom && foundFinalCrop) {
        [] () { FAIL(); }();
    }
    return foundFinalCrop;
}

bool SurfaceInterceptorTest::matrixUpdateFound(const SurfaceChange& change, bool foundMatrix) {
    bool hasSx((float)change.matrix().dsdx() == (float)M_SQRT1_2);
    bool hasTx((float)change.matrix().dtdx() == (float)M_SQRT1_2);
    bool hasSy((float)change.matrix().dsdy() == (float)-M_SQRT1_2);
    bool hasTy((float)change.matrix().dtdy() == (float)M_SQRT1_2);
    if (hasSx && hasTx && hasSy && hasTy && !foundMatrix) {
        foundMatrix = true;
    }
    else if (hasSx && hasTx && hasSy && hasTy && foundMatrix) {
        [] () { FAIL(); }();
    }
    return foundMatrix;
}

bool SurfaceInterceptorTest::scalingModeUpdateFound(const SurfaceChange& change,
        bool foundScalingMode)
{
    bool hasScalingUpdate(change.override_scaling_mode().override_scaling_mode() == SCALING_UPDATE);
    if (hasScalingUpdate && !foundScalingMode) {
        foundScalingMode = true;
    }
    else if (hasScalingUpdate && foundScalingMode) {
        [] () { FAIL(); }();
    }
    return foundScalingMode;
}

bool SurfaceInterceptorTest::transparentRegionHintUpdateFound(const SurfaceChange& change,
        bool foundTransparentRegion)
{
    auto traceRegion = change.transparent_region_hint().region(0);
    bool hasLeft(traceRegion.left() == CROP_UPDATE.left);
    bool hasTop(traceRegion.top() == CROP_UPDATE.top);
    bool hasRight(traceRegion.right() == CROP_UPDATE.right);
    bool hasBottom(traceRegion.bottom() == CROP_UPDATE.bottom);
    if (hasLeft && hasRight && hasTop && hasBottom && !foundTransparentRegion) {
        foundTransparentRegion = true;
    }
    else if (hasLeft && hasRight && hasTop && hasBottom && foundTransparentRegion) {
        [] () { FAIL(); }();
    }
    return foundTransparentRegion;
}

bool SurfaceInterceptorTest::layerStackUpdateFound(const SurfaceChange& change,
        bool foundLayerStack)
{
    bool hasLayerStackUpdate(change.layer_stack().layer_stack() == STACK_UPDATE);
    if (hasLayerStackUpdate && !foundLayerStack) {
        foundLayerStack = true;
    }
    else if (hasLayerStackUpdate && foundLayerStack) {
        [] () { FAIL(); }();
    }
    return foundLayerStack;
}

bool SurfaceInterceptorTest::hiddenFlagUpdateFound(const SurfaceChange& change,
        bool foundHiddenFlag)
{
    bool hasHiddenFlag(change.hidden_flag().hidden_flag());
    if (hasHiddenFlag && !foundHiddenFlag) {
        foundHiddenFlag = true;
    }
    else if (hasHiddenFlag && foundHiddenFlag) {
        [] () { FAIL(); }();
    }
    return foundHiddenFlag;
}

bool SurfaceInterceptorTest::opaqueFlagUpdateFound(const SurfaceChange& change,
        bool foundOpaqueFlag)
{
    bool hasOpaqueFlag(change.opaque_flag().opaque_flag());
    if (hasOpaqueFlag && !foundOpaqueFlag) {
        foundOpaqueFlag = true;
    }
    else if (hasOpaqueFlag && foundOpaqueFlag) {
        [] () { FAIL(); }();
    }
    return foundOpaqueFlag;
}

bool SurfaceInterceptorTest::secureFlagUpdateFound(const SurfaceChange& change,
        bool foundSecureFlag)
{
    bool hasSecureFlag(change.secure_flag().secure_flag());
    if (hasSecureFlag && !foundSecureFlag) {
        foundSecureFlag = true;
    }
    else if (hasSecureFlag && foundSecureFlag) {
        [] () { FAIL(); }();
    }
    return foundSecureFlag;
}

bool SurfaceInterceptorTest::deferredTransactionUpdateFound(const SurfaceChange& change,
        bool foundDeferred)
{
    bool hasId(change.deferred_transaction().layer_id() == mBGLayerId);
    bool hasFrameNumber(change.deferred_transaction().frame_number() == DEFERRED_UPDATE);
    if (hasId && hasFrameNumber && !foundDeferred) {
        foundDeferred = true;
    }
    else if (hasId && hasFrameNumber && foundDeferred) {
        [] () { FAIL(); }();
    }
    return foundDeferred;
}

bool SurfaceInterceptorTest::surfaceUpdateFound(Trace* trace,
        SurfaceChange::SurfaceChangeCase changeCase)
{
    bool foundUpdate = false;
    for (const auto& increment : *trace->mutable_increment()) {
        if (increment.increment_case() == increment.kTransaction) {
            for (const auto& change : increment.transaction().surface_change()) {
                if (change.id() == mBGLayerId && change.SurfaceChange_case() == changeCase) {
                    switch (changeCase) {
                        case SurfaceChange::SurfaceChangeCase::kPosition:
                            // foundUpdate is sent for the tests to fail on duplicated increments
                            foundUpdate = positionUpdateFound(change, foundUpdate);
                            break;
                        case SurfaceChange::SurfaceChangeCase::kSize:
                            foundUpdate = sizeUpdateFound(change, foundUpdate);
                            break;
                        case SurfaceChange::SurfaceChangeCase::kAlpha:
                            foundUpdate = alphaUpdateFound(change, foundUpdate);
                            break;
                        case SurfaceChange::SurfaceChangeCase::kLayer:
                            foundUpdate = layerUpdateFound(change, foundUpdate);
                            break;
                        case SurfaceChange::SurfaceChangeCase::kCrop:
                            foundUpdate = cropUpdateFound(change, foundUpdate);
                            break;
                        case SurfaceChange::SurfaceChangeCase::kFinalCrop:
                            foundUpdate = finalCropUpdateFound(change, foundUpdate);
                            break;
                        case SurfaceChange::SurfaceChangeCase::kMatrix:
                            foundUpdate = matrixUpdateFound(change, foundUpdate);
                            break;
                        case SurfaceChange::SurfaceChangeCase::kOverrideScalingMode:
                            foundUpdate = scalingModeUpdateFound(change, foundUpdate);
                            break;
                        case SurfaceChange::SurfaceChangeCase::kTransparentRegionHint:
                            foundUpdate = transparentRegionHintUpdateFound(change, foundUpdate);
                            break;
                        case SurfaceChange::SurfaceChangeCase::kLayerStack:
                            foundUpdate = layerStackUpdateFound(change, foundUpdate);
                            break;
                        case SurfaceChange::SurfaceChangeCase::kHiddenFlag:
                            foundUpdate = hiddenFlagUpdateFound(change, foundUpdate);
                            break;
                        case SurfaceChange::SurfaceChangeCase::kOpaqueFlag:
                            foundUpdate = opaqueFlagUpdateFound(change, foundUpdate);
                            break;
                        case SurfaceChange::SurfaceChangeCase::kSecureFlag:
                            foundUpdate = secureFlagUpdateFound(change, foundUpdate);
                            break;
                        case SurfaceChange::SurfaceChangeCase::kDeferredTransaction:
                            foundUpdate = deferredTransactionUpdateFound(change, foundUpdate);
                            break;
                        case SurfaceChange::SurfaceChangeCase::SURFACECHANGE_NOT_SET:
                            break;
                    }
                }
            }
        }
    }
    return foundUpdate;
}

void SurfaceInterceptorTest::assertAllUpdatesFound(Trace* trace) {
    ASSERT_TRUE(surfaceUpdateFound(trace, SurfaceChange::SurfaceChangeCase::kPosition));
    ASSERT_TRUE(surfaceUpdateFound(trace, SurfaceChange::SurfaceChangeCase::kSize));
    ASSERT_TRUE(surfaceUpdateFound(trace, SurfaceChange::SurfaceChangeCase::kAlpha));
    ASSERT_TRUE(surfaceUpdateFound(trace, SurfaceChange::SurfaceChangeCase::kLayer));
    ASSERT_TRUE(surfaceUpdateFound(trace, SurfaceChange::SurfaceChangeCase::kCrop));
    ASSERT_TRUE(surfaceUpdateFound(trace, SurfaceChange::SurfaceChangeCase::kFinalCrop));
    ASSERT_TRUE(surfaceUpdateFound(trace, SurfaceChange::SurfaceChangeCase::kMatrix));
    ASSERT_TRUE(surfaceUpdateFound(trace, SurfaceChange::SurfaceChangeCase::kOverrideScalingMode));
    ASSERT_TRUE(surfaceUpdateFound(trace, SurfaceChange::SurfaceChangeCase::kTransparentRegionHint));
    ASSERT_TRUE(surfaceUpdateFound(trace, SurfaceChange::SurfaceChangeCase::kLayerStack));
    ASSERT_TRUE(surfaceUpdateFound(trace, SurfaceChange::SurfaceChangeCase::kHiddenFlag));
    ASSERT_TRUE(surfaceUpdateFound(trace, SurfaceChange::SurfaceChangeCase::kOpaqueFlag));
    ASSERT_TRUE(surfaceUpdateFound(trace, SurfaceChange::SurfaceChangeCase::kSecureFlag));
    ASSERT_TRUE(surfaceUpdateFound(trace, SurfaceChange::SurfaceChangeCase::kDeferredTransaction));
}

bool SurfaceInterceptorTest::surfaceCreationFound(const Increment& increment, bool foundSurface) {
    bool isMatch(increment.surface_creation().name() == LAYER_NAME &&
            increment.surface_creation().w() == SIZE_UPDATE &&
            increment.surface_creation().h() == SIZE_UPDATE);
    if (isMatch && !foundSurface) {
        foundSurface = true;
    }
    else if (isMatch && foundSurface) {
        [] () { FAIL(); }();
    }
    return foundSurface;
}

bool SurfaceInterceptorTest::surfaceDeletionFound(const Increment& increment, bool foundSurface) {
    bool isMatch(increment.surface_deletion().id() == mTargetId);
    if (isMatch && !foundSurface) {
        foundSurface = true;
    }
    else if (isMatch && foundSurface) {
        [] () { FAIL(); }();
    }
    return foundSurface;
}

bool SurfaceInterceptorTest::displayCreationFound(const Increment& increment, bool foundDisplay) {
    bool isMatch(increment.display_creation().name() == DISPLAY_NAME.string() &&
            increment.display_creation().is_secure());
    if (isMatch && !foundDisplay) {
        foundDisplay = true;
    }
    else if (isMatch && foundDisplay) {
        [] () { FAIL(); }();
    }
    return foundDisplay;
}

bool SurfaceInterceptorTest::displayDeletionFound(const Increment& increment, bool foundDisplay) {
    bool isMatch(increment.display_deletion().id() == mTargetId);
    if (isMatch && !foundDisplay) {
        foundDisplay = true;
    }
    else if (isMatch && foundDisplay) {
        [] () { FAIL(); }();
    }
    return foundDisplay;
}

bool SurfaceInterceptorTest::singleIncrementFound(Trace* trace,
        Increment::IncrementCase incrementCase)
{
    bool foundIncrement = false;
    for (const auto& increment : *trace->mutable_increment()) {
        if (increment.increment_case() == incrementCase) {
            switch (incrementCase) {
                case Increment::IncrementCase::kSurfaceCreation:
                    foundIncrement = surfaceCreationFound(increment, foundIncrement);
                    break;
                case Increment::IncrementCase::kSurfaceDeletion:
                    foundIncrement = surfaceDeletionFound(increment, foundIncrement);
                    break;
                case Increment::IncrementCase::kDisplayCreation:
                    foundIncrement = displayCreationFound(increment, foundIncrement);
                    break;
                case Increment::IncrementCase::kDisplayDeletion:
                    foundIncrement = displayDeletionFound(increment, foundIncrement);
                    break;
                default:
                    /* code */
                    break;
            }
        }
    }
    return foundIncrement;
}

bool SurfaceInterceptorTest::bufferUpdatesFound(Trace* trace) {
    uint32_t updates = 0;
    for (const auto& inc : *trace->mutable_increment()) {
        if (inc.increment_case() == inc.kBufferUpdate && inc.buffer_update().id() == mBGLayerId) {
            updates++;
        }
    }
    return updates == BUFFER_UPDATES;
}

TEST_F(SurfaceInterceptorTest, InterceptPositionUpdateWorks) {
    captureTest(&SurfaceInterceptorTest::positionUpdate,
            SurfaceChange::SurfaceChangeCase::kPosition);
}

TEST_F(SurfaceInterceptorTest, InterceptSizeUpdateWorks) {
    captureTest(&SurfaceInterceptorTest::sizeUpdate, SurfaceChange::SurfaceChangeCase::kSize);
}

TEST_F(SurfaceInterceptorTest, InterceptAlphaUpdateWorks) {
    captureTest(&SurfaceInterceptorTest::alphaUpdate, SurfaceChange::SurfaceChangeCase::kAlpha);
}

TEST_F(SurfaceInterceptorTest, InterceptLayerUpdateWorks) {
    captureTest(&SurfaceInterceptorTest::layerUpdate, SurfaceChange::SurfaceChangeCase::kLayer);
}

TEST_F(SurfaceInterceptorTest, InterceptCropUpdateWorks) {
    captureTest(&SurfaceInterceptorTest::cropUpdate, SurfaceChange::SurfaceChangeCase::kCrop);
}

TEST_F(SurfaceInterceptorTest, InterceptFinalCropUpdateWorks) {
    captureTest(&SurfaceInterceptorTest::finalCropUpdate,
            SurfaceChange::SurfaceChangeCase::kFinalCrop);
}

TEST_F(SurfaceInterceptorTest, InterceptMatrixUpdateWorks) {
    captureTest(&SurfaceInterceptorTest::matrixUpdate, SurfaceChange::SurfaceChangeCase::kMatrix);
}

TEST_F(SurfaceInterceptorTest, InterceptOverrideScalingModeUpdateWorks) {
    captureTest(&SurfaceInterceptorTest::overrideScalingModeUpdate,
            SurfaceChange::SurfaceChangeCase::kOverrideScalingMode);
}

TEST_F(SurfaceInterceptorTest, InterceptTransparentRegionHintUpdateWorks) {
    captureTest(&SurfaceInterceptorTest::transparentRegionHintUpdate,
            SurfaceChange::SurfaceChangeCase::kTransparentRegionHint);
}

TEST_F(SurfaceInterceptorTest, InterceptLayerStackUpdateWorks) {
    captureTest(&SurfaceInterceptorTest::layerStackUpdate,
            SurfaceChange::SurfaceChangeCase::kLayerStack);
}

TEST_F(SurfaceInterceptorTest, InterceptHiddenFlagUpdateWorks) {
    captureTest(&SurfaceInterceptorTest::hiddenFlagUpdate,
            SurfaceChange::SurfaceChangeCase::kHiddenFlag);
}

TEST_F(SurfaceInterceptorTest, InterceptOpaqueFlagUpdateWorks) {
    captureTest(&SurfaceInterceptorTest::opaqueFlagUpdate,
            SurfaceChange::SurfaceChangeCase::kOpaqueFlag);
}

TEST_F(SurfaceInterceptorTest, InterceptSecureFlagUpdateWorks) {
    captureTest(&SurfaceInterceptorTest::secureFlagUpdate,
            SurfaceChange::SurfaceChangeCase::kSecureFlag);
}

TEST_F(SurfaceInterceptorTest, InterceptDeferredTransactionUpdateWorks) {
    captureTest(&SurfaceInterceptorTest::deferredTransactionUpdate,
            SurfaceChange::SurfaceChangeCase::kDeferredTransaction);
}

TEST_F(SurfaceInterceptorTest, InterceptAllUpdatesWorks) {
    enableInterceptor();
    runAllUpdates();
    disableInterceptor();

    // Find all of the updates in the single trace
    Trace capturedTrace;
    ASSERT_EQ(NO_ERROR, readProtoFile(&capturedTrace));
    assertAllUpdatesFound(&capturedTrace);
}

TEST_F(SurfaceInterceptorTest, InterceptSurfaceCreationWorks) {
    captureTest(&SurfaceInterceptorTest::surfaceCreation,
            Increment::IncrementCase::kSurfaceCreation);
}

TEST_F(SurfaceInterceptorTest, InterceptSurfaceDeletionWorks) {
    sp<SurfaceControl> layerToDelete = mComposerClient->createSurface(String8(LAYER_NAME),
            SIZE_UPDATE, SIZE_UPDATE, PIXEL_FORMAT_RGBA_8888, 0);
    this->mTargetId = getSurfaceId(LAYER_NAME);
    enableInterceptor();
    mComposerClient->destroySurface(layerToDelete->getHandle());
    disableInterceptor();

    Trace capturedTrace;
    ASSERT_EQ(NO_ERROR, readProtoFile(&capturedTrace));
    ASSERT_TRUE(singleIncrementFound(&capturedTrace, Increment::IncrementCase::kSurfaceDeletion));
}

TEST_F(SurfaceInterceptorTest, InterceptDisplayCreationWorks) {
    captureTest(&SurfaceInterceptorTest::displayCreation,
            Increment::IncrementCase::kDisplayCreation);
}

TEST_F(SurfaceInterceptorTest, InterceptDisplayDeletionWorks) {
    captureTest(&SurfaceInterceptorTest::displayDeletion,
            Increment::IncrementCase::kDisplayDeletion);
}

TEST_F(SurfaceInterceptorTest, InterceptBufferUpdateWorks) {
    captureTest(&SurfaceInterceptorTest::nBufferUpdates,
            &SurfaceInterceptorTest::bufferUpdatesFound);
}

// If the interceptor is enabled while buffer updates are being pushed, the interceptor should
// first create a snapshot of the existing displays and surfaces and then start capturing
// the buffer updates
TEST_F(SurfaceInterceptorTest, InterceptWhileBufferUpdatesWorks) {
    std::thread bufferUpdates(&SurfaceInterceptorTest::nBufferUpdates, this);
    enableInterceptor();
    disableInterceptor();
    bufferUpdates.join();

    Trace capturedTrace;
    ASSERT_EQ(NO_ERROR, readProtoFile(&capturedTrace));
    const auto& firstIncrement = capturedTrace.mutable_increment(0);
    ASSERT_EQ(firstIncrement->increment_case(), Increment::IncrementCase::kDisplayCreation);
}

TEST_F(SurfaceInterceptorTest, InterceptSimultaneousUpdatesWorks) {
    enableInterceptor();
    std::thread bufferUpdates(&SurfaceInterceptorTest::nBufferUpdates, this);
    std::thread surfaceUpdates(&SurfaceInterceptorTest::runAllUpdates, this);
    runInTransaction(&SurfaceInterceptorTest::surfaceCreation);
    bufferUpdates.join();
    surfaceUpdates.join();
    disableInterceptor();

    Trace capturedTrace;
    ASSERT_EQ(NO_ERROR, readProtoFile(&capturedTrace));

    assertAllUpdatesFound(&capturedTrace);
    ASSERT_TRUE(bufferUpdatesFound(&capturedTrace));
    ASSERT_TRUE(singleIncrementFound(&capturedTrace, Increment::IncrementCase::kSurfaceCreation));
}

}
