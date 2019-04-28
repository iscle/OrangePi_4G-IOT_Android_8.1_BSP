/*
 * Copyright 2016 The Android Open Source Project
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

#define LOG_TAG "HwcPassthrough"

#include "Hwc.h"

#include <chrono>
#include <type_traits>
#include <log/log.h>

#include "ComposerClient.h"
#include "hardware/hwcomposer.h"
#include "hwc2on1adapter/HWC2On1Adapter.h"

using namespace std::chrono_literals;

namespace android {
namespace hardware {
namespace graphics {
namespace composer {
namespace V2_1 {
namespace implementation {


HwcHal::HwcHal(const hw_module_t* module)
    : mDevice(nullptr), mDispatch(), mAdapter()
{
    // Determine what kind of module is available (HWC2 vs HWC1.X).
    hw_device_t* device = nullptr;
    int error = module->methods->open(module, HWC_HARDWARE_COMPOSER, &device);
    if (error != 0) {
        ALOGE("Failed to open HWC device (%s), aborting", strerror(-error));
        abort();
    }
    uint32_t majorVersion = (device->version >> 24) & 0xF;

    // If we don't have a HWC2, we need to wrap whatever we have in an adapter.
    if (majorVersion != 2) {
        uint32_t minorVersion = device->version & HARDWARE_API_VERSION_2_MAJ_MIN_MASK;
        minorVersion = (minorVersion >> 16) & 0xF;
        ALOGI("Found HWC implementation v%d.%d", majorVersion, minorVersion);
        if (minorVersion < 1) {
            ALOGE("Cannot adapt to HWC version %d.%d. Minimum supported is 1.1",
                  majorVersion, minorVersion);
            abort();
        }
        mAdapter = std::make_unique<HWC2On1Adapter>(
                reinterpret_cast<hwc_composer_device_1*>(device));

        // Place the adapter in front of the device module.
        mDevice = mAdapter.get();
    } else {
        mDevice = reinterpret_cast<hwc2_device_t*>(device);
    }

    initCapabilities();
    if (majorVersion >= 2 &&
        hasCapability(Capability::PRESENT_FENCE_IS_NOT_RELIABLE)) {
        ALOGE("Present fence must be reliable from HWC2 on.");
        abort();
    }

    initDispatch();
}

HwcHal::~HwcHal()
{
    hwc2_close(mDevice);
}

void HwcHal::initCapabilities()
{
    uint32_t count = 0;
    mDevice->getCapabilities(mDevice, &count, nullptr);

    std::vector<Capability> caps(count);
    mDevice->getCapabilities(mDevice, &count, reinterpret_cast<
              std::underlying_type<Capability>::type*>(caps.data()));
    caps.resize(count);

    mCapabilities.insert(caps.cbegin(), caps.cend());
}

template<typename T>
void HwcHal::initDispatch(hwc2_function_descriptor_t desc, T* outPfn)
{
    auto pfn = mDevice->getFunction(mDevice, desc);
    if (!pfn) {
        LOG_ALWAYS_FATAL("failed to get hwcomposer2 function %d", desc);
    }

    *outPfn = reinterpret_cast<T>(pfn);
}

void HwcHal::initDispatch()
{
    initDispatch(HWC2_FUNCTION_ACCEPT_DISPLAY_CHANGES,
            &mDispatch.acceptDisplayChanges);
    initDispatch(HWC2_FUNCTION_CREATE_LAYER, &mDispatch.createLayer);
    initDispatch(HWC2_FUNCTION_CREATE_VIRTUAL_DISPLAY,
            &mDispatch.createVirtualDisplay);
    initDispatch(HWC2_FUNCTION_DESTROY_LAYER, &mDispatch.destroyLayer);
    initDispatch(HWC2_FUNCTION_DESTROY_VIRTUAL_DISPLAY,
            &mDispatch.destroyVirtualDisplay);
    initDispatch(HWC2_FUNCTION_DUMP, &mDispatch.dump);
    initDispatch(HWC2_FUNCTION_GET_ACTIVE_CONFIG, &mDispatch.getActiveConfig);
    initDispatch(HWC2_FUNCTION_GET_CHANGED_COMPOSITION_TYPES,
            &mDispatch.getChangedCompositionTypes);
    initDispatch(HWC2_FUNCTION_GET_CLIENT_TARGET_SUPPORT,
            &mDispatch.getClientTargetSupport);
    initDispatch(HWC2_FUNCTION_GET_COLOR_MODES, &mDispatch.getColorModes);
    initDispatch(HWC2_FUNCTION_GET_DISPLAY_ATTRIBUTE,
            &mDispatch.getDisplayAttribute);
    initDispatch(HWC2_FUNCTION_GET_DISPLAY_CONFIGS,
            &mDispatch.getDisplayConfigs);
    initDispatch(HWC2_FUNCTION_GET_DISPLAY_NAME, &mDispatch.getDisplayName);
    initDispatch(HWC2_FUNCTION_GET_DISPLAY_REQUESTS,
            &mDispatch.getDisplayRequests);
    initDispatch(HWC2_FUNCTION_GET_DISPLAY_TYPE, &mDispatch.getDisplayType);
    initDispatch(HWC2_FUNCTION_GET_DOZE_SUPPORT, &mDispatch.getDozeSupport);
    initDispatch(HWC2_FUNCTION_GET_HDR_CAPABILITIES,
            &mDispatch.getHdrCapabilities);
    initDispatch(HWC2_FUNCTION_GET_MAX_VIRTUAL_DISPLAY_COUNT,
            &mDispatch.getMaxVirtualDisplayCount);
    initDispatch(HWC2_FUNCTION_GET_RELEASE_FENCES,
            &mDispatch.getReleaseFences);
    initDispatch(HWC2_FUNCTION_PRESENT_DISPLAY, &mDispatch.presentDisplay);
    initDispatch(HWC2_FUNCTION_REGISTER_CALLBACK,
            &mDispatch.registerCallback);
    initDispatch(HWC2_FUNCTION_SET_ACTIVE_CONFIG, &mDispatch.setActiveConfig);
    initDispatch(HWC2_FUNCTION_SET_CLIENT_TARGET, &mDispatch.setClientTarget);
    initDispatch(HWC2_FUNCTION_SET_COLOR_MODE, &mDispatch.setColorMode);
    initDispatch(HWC2_FUNCTION_SET_COLOR_TRANSFORM,
            &mDispatch.setColorTransform);
    initDispatch(HWC2_FUNCTION_SET_CURSOR_POSITION,
            &mDispatch.setCursorPosition);
    initDispatch(HWC2_FUNCTION_SET_LAYER_BLEND_MODE,
            &mDispatch.setLayerBlendMode);
    initDispatch(HWC2_FUNCTION_SET_LAYER_BUFFER, &mDispatch.setLayerBuffer);
    initDispatch(HWC2_FUNCTION_SET_LAYER_COLOR, &mDispatch.setLayerColor);
    initDispatch(HWC2_FUNCTION_SET_LAYER_COMPOSITION_TYPE,
            &mDispatch.setLayerCompositionType);
    initDispatch(HWC2_FUNCTION_SET_LAYER_DATASPACE,
            &mDispatch.setLayerDataspace);
    initDispatch(HWC2_FUNCTION_SET_LAYER_DISPLAY_FRAME,
            &mDispatch.setLayerDisplayFrame);
    initDispatch(HWC2_FUNCTION_SET_LAYER_PLANE_ALPHA,
            &mDispatch.setLayerPlaneAlpha);

    if (hasCapability(Capability::SIDEBAND_STREAM)) {
        initDispatch(HWC2_FUNCTION_SET_LAYER_SIDEBAND_STREAM,
                &mDispatch.setLayerSidebandStream);
    }

    initDispatch(HWC2_FUNCTION_SET_LAYER_SOURCE_CROP,
            &mDispatch.setLayerSourceCrop);
    initDispatch(HWC2_FUNCTION_SET_LAYER_SURFACE_DAMAGE,
            &mDispatch.setLayerSurfaceDamage);
    initDispatch(HWC2_FUNCTION_SET_LAYER_TRANSFORM,
            &mDispatch.setLayerTransform);
    initDispatch(HWC2_FUNCTION_SET_LAYER_VISIBLE_REGION,
            &mDispatch.setLayerVisibleRegion);
    initDispatch(HWC2_FUNCTION_SET_LAYER_Z_ORDER, &mDispatch.setLayerZOrder);
    initDispatch(HWC2_FUNCTION_SET_OUTPUT_BUFFER, &mDispatch.setOutputBuffer);
    initDispatch(HWC2_FUNCTION_SET_POWER_MODE, &mDispatch.setPowerMode);
    initDispatch(HWC2_FUNCTION_SET_VSYNC_ENABLED, &mDispatch.setVsyncEnabled);
    initDispatch(HWC2_FUNCTION_VALIDATE_DISPLAY, &mDispatch.validateDisplay);
}

bool HwcHal::hasCapability(Capability capability) const
{
    return (mCapabilities.count(capability) > 0);
}

Return<void> HwcHal::getCapabilities(getCapabilities_cb hidl_cb)
{
    std::vector<Capability> caps(
            mCapabilities.cbegin(), mCapabilities.cend());

    hidl_vec<Capability> caps_reply;
    caps_reply.setToExternal(caps.data(), caps.size());
    hidl_cb(caps_reply);

    return Void();
}

Return<void> HwcHal::dumpDebugInfo(dumpDebugInfo_cb hidl_cb)
{
    uint32_t len = 0;
    mDispatch.dump(mDevice, &len, nullptr);

    std::vector<char> buf(len + 1);
    mDispatch.dump(mDevice, &len, buf.data());
    buf.resize(len + 1);
    buf[len] = '\0';

    hidl_string buf_reply;
    buf_reply.setToExternal(buf.data(), len);
    hidl_cb(buf_reply);

    return Void();
}

Return<void> HwcHal::createClient(createClient_cb hidl_cb)
{
    Error err = Error::NONE;
    sp<ComposerClient> client;

    {
        std::unique_lock<std::mutex> lock(mClientMutex);

        if (mClient != nullptr) {
            // In surface flinger we delete a composer client on one thread and
            // then create a new client on another thread. Although surface
            // flinger ensures the calls are made in that sequence (destroy and
            // then create), sometimes the calls land in the composer service
            // inverted (create and then destroy). Wait for a brief period to
            // see if the existing client is destroyed.
            ALOGI("HwcHal::createClient: Client already exists. Waiting for"
                    " it to be destroyed.");
            mClientDestroyedWait.wait_for(lock, 1s,
                    [this] { return mClient == nullptr; });
            std::string doneMsg = mClient == nullptr ?
                    "Existing client was destroyed." :
                    "Existing client was never destroyed!";
            ALOGI("HwcHal::createClient: Done waiting. %s", doneMsg.c_str());
        }

        // only one client is allowed
        if (mClient == nullptr) {
            client = new ComposerClient(*this);
            client->initialize();
            mClient = client;
        } else {
            err = Error::NO_RESOURCES;
        }
    }

    hidl_cb(err, client);

    return Void();
}

sp<ComposerClient> HwcHal::getClient()
{
    std::lock_guard<std::mutex> lock(mClientMutex);
    return (mClient != nullptr) ? mClient.promote() : nullptr;
}

void HwcHal::removeClient()
{
    std::lock_guard<std::mutex> lock(mClientMutex);
    mClient = nullptr;
    mClientDestroyedWait.notify_all();
}

void HwcHal::hotplugHook(hwc2_callback_data_t callbackData,
        hwc2_display_t display, int32_t connected)
{
    auto hal = reinterpret_cast<HwcHal*>(callbackData);
    auto client = hal->getClient();
    if (client != nullptr) {
        client->onHotplug(display,
                static_cast<IComposerCallback::Connection>(connected));
    }
}

void HwcHal::refreshHook(hwc2_callback_data_t callbackData,
        hwc2_display_t display)
{
    auto hal = reinterpret_cast<HwcHal*>(callbackData);
    auto client = hal->getClient();
    if (client != nullptr) {
        client->onRefresh(display);
    }
}

void HwcHal::vsyncHook(hwc2_callback_data_t callbackData,
        hwc2_display_t display, int64_t timestamp)
{
    auto hal = reinterpret_cast<HwcHal*>(callbackData);
    auto client = hal->getClient();
    if (client != nullptr) {
        client->onVsync(display, timestamp);
    }
}

void HwcHal::enableCallback(bool enable)
{
    if (enable) {
        mDispatch.registerCallback(mDevice, HWC2_CALLBACK_HOTPLUG, this,
                reinterpret_cast<hwc2_function_pointer_t>(hotplugHook));
        mDispatch.registerCallback(mDevice, HWC2_CALLBACK_REFRESH, this,
                reinterpret_cast<hwc2_function_pointer_t>(refreshHook));
        mDispatch.registerCallback(mDevice, HWC2_CALLBACK_VSYNC, this,
                reinterpret_cast<hwc2_function_pointer_t>(vsyncHook));
    } else {
        mDispatch.registerCallback(mDevice, HWC2_CALLBACK_HOTPLUG, this,
                nullptr);
        mDispatch.registerCallback(mDevice, HWC2_CALLBACK_REFRESH, this,
                nullptr);
        mDispatch.registerCallback(mDevice, HWC2_CALLBACK_VSYNC, this,
                nullptr);
    }
}

uint32_t HwcHal::getMaxVirtualDisplayCount()
{
    return mDispatch.getMaxVirtualDisplayCount(mDevice);
}

Error HwcHal::createVirtualDisplay(uint32_t width, uint32_t height,
    PixelFormat* format, Display* outDisplay)
{
    int32_t hwc_format = static_cast<int32_t>(*format);
    int32_t err = mDispatch.createVirtualDisplay(mDevice, width, height,
            &hwc_format, outDisplay);
    *format = static_cast<PixelFormat>(hwc_format);

    return static_cast<Error>(err);
}

Error HwcHal::destroyVirtualDisplay(Display display)
{
    int32_t err = mDispatch.destroyVirtualDisplay(mDevice, display);
    return static_cast<Error>(err);
}

Error HwcHal::createLayer(Display display, Layer* outLayer)
{
    int32_t err = mDispatch.createLayer(mDevice, display, outLayer);
    return static_cast<Error>(err);
}

Error HwcHal::destroyLayer(Display display, Layer layer)
{
    int32_t err = mDispatch.destroyLayer(mDevice, display, layer);
    return static_cast<Error>(err);
}

Error HwcHal::getActiveConfig(Display display, Config* outConfig)
{
    int32_t err = mDispatch.getActiveConfig(mDevice, display, outConfig);
    return static_cast<Error>(err);
}

Error HwcHal::getClientTargetSupport(Display display,
        uint32_t width, uint32_t height,
        PixelFormat format, Dataspace dataspace)
{
    int32_t err = mDispatch.getClientTargetSupport(mDevice, display,
            width, height, static_cast<int32_t>(format),
            static_cast<int32_t>(dataspace));
    return static_cast<Error>(err);
}

Error HwcHal::getColorModes(Display display, hidl_vec<ColorMode>* outModes)
{
    uint32_t count = 0;
    int32_t err = mDispatch.getColorModes(mDevice, display, &count, nullptr);
    if (err != HWC2_ERROR_NONE) {
        return static_cast<Error>(err);
    }

    outModes->resize(count);
    err = mDispatch.getColorModes(mDevice, display, &count,
            reinterpret_cast<std::underlying_type<ColorMode>::type*>(
                outModes->data()));
    if (err != HWC2_ERROR_NONE) {
        *outModes = hidl_vec<ColorMode>();
        return static_cast<Error>(err);
    }

    return Error::NONE;
}

Error HwcHal::getDisplayAttribute(Display display, Config config,
        IComposerClient::Attribute attribute, int32_t* outValue)
{
    int32_t err = mDispatch.getDisplayAttribute(mDevice, display, config,
            static_cast<int32_t>(attribute), outValue);
    return static_cast<Error>(err);
}

Error HwcHal::getDisplayConfigs(Display display, hidl_vec<Config>* outConfigs)
{
    uint32_t count = 0;
    int32_t err = mDispatch.getDisplayConfigs(mDevice, display,
            &count, nullptr);
    if (err != HWC2_ERROR_NONE) {
        return static_cast<Error>(err);
    }

    outConfigs->resize(count);
    err = mDispatch.getDisplayConfigs(mDevice, display,
            &count, outConfigs->data());
    if (err != HWC2_ERROR_NONE) {
        *outConfigs = hidl_vec<Config>();
        return static_cast<Error>(err);
    }

    return Error::NONE;
}

Error HwcHal::getDisplayName(Display display, hidl_string* outName)
{
    uint32_t count = 0;
    int32_t err = mDispatch.getDisplayName(mDevice, display, &count, nullptr);
    if (err != HWC2_ERROR_NONE) {
        return static_cast<Error>(err);
    }

    std::vector<char> buf(count + 1);
    err = mDispatch.getDisplayName(mDevice, display, &count, buf.data());
    if (err != HWC2_ERROR_NONE) {
        return static_cast<Error>(err);
    }
    buf.resize(count + 1);
    buf[count] = '\0';

    *outName = buf.data();

    return Error::NONE;
}

Error HwcHal::getDisplayType(Display display,
        IComposerClient::DisplayType* outType)
{
    int32_t hwc_type = HWC2_DISPLAY_TYPE_INVALID;
    int32_t err = mDispatch.getDisplayType(mDevice, display, &hwc_type);
    *outType = static_cast<IComposerClient::DisplayType>(hwc_type);

    return static_cast<Error>(err);
}

Error HwcHal::getDozeSupport(Display display, bool* outSupport)
{
    int32_t hwc_support = 0;
    int32_t err = mDispatch.getDozeSupport(mDevice, display, &hwc_support);
    *outSupport = hwc_support;

    return static_cast<Error>(err);
}

Error HwcHal::getHdrCapabilities(Display display, hidl_vec<Hdr>* outTypes,
        float* outMaxLuminance, float* outMaxAverageLuminance,
        float* outMinLuminance)
{
    uint32_t count = 0;
    int32_t err = mDispatch.getHdrCapabilities(mDevice, display, &count,
            nullptr, outMaxLuminance, outMaxAverageLuminance,
            outMinLuminance);
    if (err != HWC2_ERROR_NONE) {
        return static_cast<Error>(err);
    }

    outTypes->resize(count);
    err = mDispatch.getHdrCapabilities(mDevice, display, &count,
            reinterpret_cast<std::underlying_type<Hdr>::type*>(
                outTypes->data()), outMaxLuminance,
            outMaxAverageLuminance, outMinLuminance);
    if (err != HWC2_ERROR_NONE) {
        *outTypes = hidl_vec<Hdr>();
        return static_cast<Error>(err);
    }

    return Error::NONE;
}

Error HwcHal::setActiveConfig(Display display, Config config)
{
    int32_t err = mDispatch.setActiveConfig(mDevice, display, config);
    return static_cast<Error>(err);
}

Error HwcHal::setColorMode(Display display, ColorMode mode)
{
    int32_t err = mDispatch.setColorMode(mDevice, display,
            static_cast<int32_t>(mode));
    return static_cast<Error>(err);
}

Error HwcHal::setPowerMode(Display display, IComposerClient::PowerMode mode)
{
    int32_t err = mDispatch.setPowerMode(mDevice, display,
            static_cast<int32_t>(mode));
    return static_cast<Error>(err);
}

Error HwcHal::setVsyncEnabled(Display display, IComposerClient::Vsync enabled)
{
    int32_t err = mDispatch.setVsyncEnabled(mDevice, display,
            static_cast<int32_t>(enabled));
    return static_cast<Error>(err);
}

Error HwcHal::setColorTransform(Display display, const float* matrix,
        int32_t hint)
{
    int32_t err = mDispatch.setColorTransform(mDevice, display, matrix, hint);
    return static_cast<Error>(err);
}

Error HwcHal::setClientTarget(Display display, buffer_handle_t target,
        int32_t acquireFence, int32_t dataspace,
        const std::vector<hwc_rect_t>& damage)
{
    hwc_region region = { damage.size(), damage.data() };
    int32_t err = mDispatch.setClientTarget(mDevice, display, target,
            acquireFence, dataspace, region);
    return static_cast<Error>(err);
}

Error HwcHal::setOutputBuffer(Display display, buffer_handle_t buffer,
        int32_t releaseFence)
{
    int32_t err = mDispatch.setOutputBuffer(mDevice, display, buffer,
            releaseFence);
    // unlike in setClientTarget, releaseFence is owned by us
    if (err == HWC2_ERROR_NONE && releaseFence >= 0) {
        close(releaseFence);
    }

    return static_cast<Error>(err);
}

Error HwcHal::validateDisplay(Display display,
        std::vector<Layer>* outChangedLayers,
        std::vector<IComposerClient::Composition>* outCompositionTypes,
        uint32_t* outDisplayRequestMask,
        std::vector<Layer>* outRequestedLayers,
        std::vector<uint32_t>* outRequestMasks)
{
    uint32_t types_count = 0;
    uint32_t reqs_count = 0;
    int32_t err = mDispatch.validateDisplay(mDevice, display,
            &types_count, &reqs_count);
    if (err != HWC2_ERROR_NONE && err != HWC2_ERROR_HAS_CHANGES) {
        return static_cast<Error>(err);
    }

    err = mDispatch.getChangedCompositionTypes(mDevice, display,
            &types_count, nullptr, nullptr);
    if (err != HWC2_ERROR_NONE) {
        return static_cast<Error>(err);
    }

    outChangedLayers->resize(types_count);
    outCompositionTypes->resize(types_count);
    err = mDispatch.getChangedCompositionTypes(mDevice, display,
            &types_count, outChangedLayers->data(),
            reinterpret_cast<
            std::underlying_type<IComposerClient::Composition>::type*>(
                outCompositionTypes->data()));
    if (err != HWC2_ERROR_NONE) {
        outChangedLayers->clear();
        outCompositionTypes->clear();
        return static_cast<Error>(err);
    }

    int32_t display_reqs = 0;
    err = mDispatch.getDisplayRequests(mDevice, display, &display_reqs,
            &reqs_count, nullptr, nullptr);
    if (err != HWC2_ERROR_NONE) {
        outChangedLayers->clear();
        outCompositionTypes->clear();
        return static_cast<Error>(err);
    }

    outRequestedLayers->resize(reqs_count);
    outRequestMasks->resize(reqs_count);
    err = mDispatch.getDisplayRequests(mDevice, display, &display_reqs,
            &reqs_count, outRequestedLayers->data(),
            reinterpret_cast<int32_t*>(outRequestMasks->data()));
    if (err != HWC2_ERROR_NONE) {
        outChangedLayers->clear();
        outCompositionTypes->clear();

        outRequestedLayers->clear();
        outRequestMasks->clear();
        return static_cast<Error>(err);
    }

    *outDisplayRequestMask = display_reqs;

    return static_cast<Error>(err);
}

Error HwcHal::acceptDisplayChanges(Display display)
{
    int32_t err = mDispatch.acceptDisplayChanges(mDevice, display);
    return static_cast<Error>(err);
}

Error HwcHal::presentDisplay(Display display, int32_t* outPresentFence,
        std::vector<Layer>* outLayers, std::vector<int32_t>* outReleaseFences)
{
    *outPresentFence = -1;
    int32_t err = mDispatch.presentDisplay(mDevice, display, outPresentFence);
    if (err != HWC2_ERROR_NONE) {
        return static_cast<Error>(err);
    }

    uint32_t count = 0;
    err = mDispatch.getReleaseFences(mDevice, display, &count,
            nullptr, nullptr);
    if (err != HWC2_ERROR_NONE) {
        ALOGW("failed to get release fences");
        return Error::NONE;
    }

    outLayers->resize(count);
    outReleaseFences->resize(count);
    err = mDispatch.getReleaseFences(mDevice, display, &count,
            outLayers->data(), outReleaseFences->data());
    if (err != HWC2_ERROR_NONE) {
        ALOGW("failed to get release fences");
        outLayers->clear();
        outReleaseFences->clear();
        return Error::NONE;
    }

    return static_cast<Error>(err);
}

Error HwcHal::setLayerCursorPosition(Display display, Layer layer,
        int32_t x, int32_t y)
{
    int32_t err = mDispatch.setCursorPosition(mDevice, display, layer, x, y);
    return static_cast<Error>(err);
}

Error HwcHal::setLayerBuffer(Display display, Layer layer,
        buffer_handle_t buffer, int32_t acquireFence)
{
    int32_t err = mDispatch.setLayerBuffer(mDevice, display, layer,
            buffer, acquireFence);
    return static_cast<Error>(err);
}

Error HwcHal::setLayerSurfaceDamage(Display display, Layer layer,
        const std::vector<hwc_rect_t>& damage)
{
    hwc_region region = { damage.size(), damage.data() };
    int32_t err = mDispatch.setLayerSurfaceDamage(mDevice, display, layer,
            region);
    return static_cast<Error>(err);
}

Error HwcHal::setLayerBlendMode(Display display, Layer layer, int32_t mode)
{
    int32_t err = mDispatch.setLayerBlendMode(mDevice, display, layer, mode);
    return static_cast<Error>(err);
}

Error HwcHal::setLayerColor(Display display, Layer layer,
        IComposerClient::Color color)
{
    hwc_color_t hwc_color{color.r, color.g, color.b, color.a};
    int32_t err = mDispatch.setLayerColor(mDevice, display, layer, hwc_color);
    return static_cast<Error>(err);
}

Error HwcHal::setLayerCompositionType(Display display, Layer layer,
        int32_t type)
{
    int32_t err = mDispatch.setLayerCompositionType(mDevice, display, layer,
            type);
    return static_cast<Error>(err);
}

Error HwcHal::setLayerDataspace(Display display, Layer layer,
        int32_t dataspace)
{
    int32_t err = mDispatch.setLayerDataspace(mDevice, display, layer,
            dataspace);
    return static_cast<Error>(err);
}

Error HwcHal::setLayerDisplayFrame(Display display, Layer layer,
        const hwc_rect_t& frame)
{
    int32_t err = mDispatch.setLayerDisplayFrame(mDevice, display, layer,
            frame);
    return static_cast<Error>(err);
}

Error HwcHal::setLayerPlaneAlpha(Display display, Layer layer, float alpha)
{
    int32_t err = mDispatch.setLayerPlaneAlpha(mDevice, display, layer,
            alpha);
    return static_cast<Error>(err);
}

Error HwcHal::setLayerSidebandStream(Display display, Layer layer,
        buffer_handle_t stream)
{
    int32_t err = mDispatch.setLayerSidebandStream(mDevice, display, layer,
            stream);
    return static_cast<Error>(err);
}

Error HwcHal::setLayerSourceCrop(Display display, Layer layer,
        const hwc_frect_t& crop)
{
    int32_t err = mDispatch.setLayerSourceCrop(mDevice, display, layer, crop);
    return static_cast<Error>(err);
}

Error HwcHal::setLayerTransform(Display display, Layer layer,
        int32_t transform)
{
    int32_t err = mDispatch.setLayerTransform(mDevice, display, layer,
            transform);
    return static_cast<Error>(err);
}

Error HwcHal::setLayerVisibleRegion(Display display, Layer layer,
        const std::vector<hwc_rect_t>& visible)
{
    hwc_region_t region = { visible.size(), visible.data() };
    int32_t err = mDispatch.setLayerVisibleRegion(mDevice, display, layer,
            region);
    return static_cast<Error>(err);
}

Error HwcHal::setLayerZOrder(Display display, Layer layer, uint32_t z)
{
    int32_t err = mDispatch.setLayerZOrder(mDevice, display, layer, z);
    return static_cast<Error>(err);
}

IComposer* HIDL_FETCH_IComposer(const char*)
{
    const hw_module_t* module = nullptr;
    int err = hw_get_module(HWC_HARDWARE_MODULE_ID, &module);
    if (err) {
        ALOGE("failed to get hwcomposer module");
        return nullptr;
    }

    return new HwcHal(module);
}

} // namespace implementation
} // namespace V2_1
} // namespace composer
} // namespace graphics
} // namespace hardware
} // namespace android
