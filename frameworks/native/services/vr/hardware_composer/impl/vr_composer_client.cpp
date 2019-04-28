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

#include <android/frameworks/vr/composer/1.0/IVrComposerClient.h>
#include <hardware/gralloc.h>
#include <hardware/gralloc1.h>
#include <log/log.h>

#include "impl/vr_hwc.h"
#include "impl/vr_composer_client.h"

namespace android {
namespace dvr {
namespace {

using android::hardware::graphics::common::V1_0::PixelFormat;
using android::frameworks::vr::composer::V1_0::IVrComposerClient;

class ComposerClientImpl : public ComposerClient {
 public:
  ComposerClientImpl(android::dvr::VrHwc& hal);
  virtual ~ComposerClientImpl();

 private:
  class VrCommandReader : public ComposerClient::CommandReader {
   public:
    VrCommandReader(ComposerClientImpl& client);
    ~VrCommandReader() override;

    bool parseCommand(IComposerClient::Command command,
                      uint16_t length) override;

   private:
    bool parseSetLayerInfo(uint16_t length);
    bool parseSetClientTargetMetadata(uint16_t length);
    bool parseSetLayerBufferMetadata(uint16_t length);

    IVrComposerClient::BufferMetadata readBufferMetadata();

    ComposerClientImpl& mVrClient;
    android::dvr::VrHwc& mVrHal;

    VrCommandReader(const VrCommandReader&) = delete;
    void operator=(const VrCommandReader&) = delete;
  };

  std::unique_ptr<CommandReader> createCommandReader() override;

  dvr::VrHwc& mVrHal;

  ComposerClientImpl(const ComposerClientImpl&) = delete;
  void operator=(const ComposerClientImpl&) = delete;
};

ComposerClientImpl::ComposerClientImpl(android::dvr::VrHwc& hal)
    : ComposerClient(hal), mVrHal(hal) {}

ComposerClientImpl::~ComposerClientImpl() {}

std::unique_ptr<ComposerClient::CommandReader>
ComposerClientImpl::createCommandReader() {
  return std::unique_ptr<CommandReader>(new VrCommandReader(*this));
}

ComposerClientImpl::VrCommandReader::VrCommandReader(ComposerClientImpl& client)
    : CommandReader(client), mVrClient(client), mVrHal(client.mVrHal) {}

ComposerClientImpl::VrCommandReader::~VrCommandReader() {}

bool ComposerClientImpl::VrCommandReader::parseCommand(
    IComposerClient::Command command, uint16_t length) {
  IVrComposerClient::VrCommand vrCommand =
      static_cast<IVrComposerClient::VrCommand>(command);
  switch (vrCommand) {
    case IVrComposerClient::VrCommand::SET_LAYER_INFO:
      return parseSetLayerInfo(length);
    case IVrComposerClient::VrCommand::SET_CLIENT_TARGET_METADATA:
      return parseSetClientTargetMetadata(length);
    case IVrComposerClient::VrCommand::SET_LAYER_BUFFER_METADATA:
      return parseSetLayerBufferMetadata(length);
    default:
      return CommandReader::parseCommand(command, length);
  }
}

bool ComposerClientImpl::VrCommandReader::parseSetLayerInfo(uint16_t length) {
  if (length != 2) {
    return false;
  }

  auto err = mVrHal.setLayerInfo(mDisplay, mLayer, read(), read());
  if (err != Error::NONE) {
    mWriter.setError(getCommandLoc(), err);
  }

  return true;
}

bool ComposerClientImpl::VrCommandReader::parseSetClientTargetMetadata(
    uint16_t length) {
  if (length != 7)
    return false;

  auto err = mVrHal.setClientTargetMetadata(mDisplay, readBufferMetadata());
  if (err != Error::NONE)
    mWriter.setError(getCommandLoc(), err);

  return true;
}

bool ComposerClientImpl::VrCommandReader::parseSetLayerBufferMetadata(
    uint16_t length) {
  if (length != 7)
    return false;

  auto err = mVrHal.setLayerBufferMetadata(mDisplay, mLayer,
                                           readBufferMetadata());
  if (err != Error::NONE)
    mWriter.setError(getCommandLoc(), err);

  return true;
}

IVrComposerClient::BufferMetadata
ComposerClientImpl::VrCommandReader::readBufferMetadata() {
  IVrComposerClient::BufferMetadata metadata = {
    .width = read(),
    .height = read(),
    .stride = read(),
    .layerCount = read(),
    .format = static_cast<PixelFormat>(readSigned()),
    .usage = read64(),
  };
  return metadata;
}

}  // namespace

VrComposerClient::VrComposerClient(dvr::VrHwc& hal)
    : client_(new ComposerClientImpl(hal)) {
  client_->initialize();
}

VrComposerClient::~VrComposerClient() {}

void VrComposerClient::onHotplug(Display display,
    IComposerCallback::Connection connected) {
  client_->onHotplug(display, connected);
}

void VrComposerClient::onRefresh(Display display) {
  client_->onRefresh(display);
}

Return<void> VrComposerClient::registerCallback(
    const sp<IComposerCallback>& callback) {
  return client_->registerCallback(callback);
}

Return<uint32_t> VrComposerClient::getMaxVirtualDisplayCount() {
  return client_->getMaxVirtualDisplayCount();
}

Return<void> VrComposerClient::createVirtualDisplay(uint32_t width,
    uint32_t height, PixelFormat formatHint, uint32_t outputBufferSlotCount,
    createVirtualDisplay_cb hidl_cb) {
  return client_->createVirtualDisplay(
      width, height, formatHint, outputBufferSlotCount, hidl_cb);
}

Return<Error> VrComposerClient::destroyVirtualDisplay(Display display) {
  return client_->destroyVirtualDisplay(display);
}

Return<void> VrComposerClient::createLayer(Display display,
    uint32_t bufferSlotCount, createLayer_cb hidl_cb) {
  return client_->createLayer(display, bufferSlotCount, hidl_cb);
}

Return<Error> VrComposerClient::destroyLayer(Display display, Layer layer) {
  return client_->destroyLayer(display, layer);
}

Return<void> VrComposerClient::getActiveConfig(Display display,
    getActiveConfig_cb hidl_cb) {
  return client_->getActiveConfig(display, hidl_cb);
}

Return<Error> VrComposerClient::getClientTargetSupport(Display display,
    uint32_t width, uint32_t height, PixelFormat format, Dataspace dataspace) {
  return client_->getClientTargetSupport(display, width, height, format,
                                         dataspace);
}

Return<void> VrComposerClient::getColorModes(Display display,
    getColorModes_cb hidl_cb) {
  return client_->getColorModes(display, hidl_cb);
}

Return<void> VrComposerClient::getDisplayAttribute(Display display,
    Config config, Attribute attribute, getDisplayAttribute_cb hidl_cb) {
  return client_->getDisplayAttribute(display, config, attribute, hidl_cb);
}

Return<void> VrComposerClient::getDisplayConfigs(Display display,
    getDisplayConfigs_cb hidl_cb) {
  return client_->getDisplayConfigs(display, hidl_cb);
}

Return<void> VrComposerClient::getDisplayName(Display display,
    getDisplayName_cb hidl_cb) {
  return client_->getDisplayName(display, hidl_cb);
}

Return<void> VrComposerClient::getDisplayType(Display display,
    getDisplayType_cb hidl_cb) {
  return client_->getDisplayType(display, hidl_cb);
}

Return<void> VrComposerClient::getDozeSupport(
    Display display, getDozeSupport_cb hidl_cb) {
  return client_->getDozeSupport(display, hidl_cb);
}

Return<void> VrComposerClient::getHdrCapabilities(
    Display display, getHdrCapabilities_cb hidl_cb) {
  return client_->getHdrCapabilities(display, hidl_cb);
}

Return<Error> VrComposerClient::setActiveConfig(Display display,
    Config config) {
  return client_->setActiveConfig(display, config);
}

Return<Error> VrComposerClient::setColorMode(Display display, ColorMode mode) {
  return client_->setColorMode(display, mode);
}

Return<Error> VrComposerClient::setPowerMode(Display display, PowerMode mode) {
  return client_->setPowerMode(display, mode);
}

Return<Error> VrComposerClient::setVsyncEnabled(Display display,
    Vsync enabled) {
  return client_->setVsyncEnabled(display, enabled);
}

Return<Error> VrComposerClient::setClientTargetSlotCount(
    Display display, uint32_t clientTargetSlotCount) {
  return client_->setClientTargetSlotCount(display, clientTargetSlotCount);
}

Return<Error> VrComposerClient::setInputCommandQueue(
    const hardware::MQDescriptorSync<uint32_t>& descriptor) {
  return client_->setInputCommandQueue(descriptor);
}

Return<void> VrComposerClient::getOutputCommandQueue(
    getOutputCommandQueue_cb hidl_cb) {
  return client_->getOutputCommandQueue(hidl_cb);
}

Return<void> VrComposerClient::executeCommands(uint32_t inLength,
    const hidl_vec<hidl_handle>& inHandles, executeCommands_cb hidl_cb) {
  return client_->executeCommands(inLength, inHandles, hidl_cb);
}

}  // namespace dvr
}  // namespace android
