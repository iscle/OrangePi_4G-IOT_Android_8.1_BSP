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

#include <VtsHalHidlTargetTestBase.h>

#include "VtsHalGraphicsComposerTestUtils.h"

namespace android {
namespace hardware {
namespace graphics {
namespace composer {
namespace V2_1 {
namespace tests {

Composer::Composer() {
  mComposer = ::testing::VtsHalHidlTargetTestBase::getService<IComposer>();
  init();
}

Composer::Composer(const std::string& name) {
  mComposer = ::testing::VtsHalHidlTargetTestBase::getService<IComposer>(name);
  init();
}

void Composer::init() {
  ASSERT_NE(nullptr, mComposer.get()) << "failed to get composer service";

  std::vector<IComposer::Capability> capabilities = getCapabilities();
  mCapabilities.insert(capabilities.begin(), capabilities.end());
}

sp<IComposer> Composer::getRaw() const { return mComposer; }

bool Composer::hasCapability(IComposer::Capability capability) const {
  return mCapabilities.count(capability) > 0;
}

std::vector<IComposer::Capability> Composer::getCapabilities() {
  std::vector<IComposer::Capability> capabilities;
  mComposer->getCapabilities(
      [&](const auto& tmpCapabilities) { capabilities = tmpCapabilities; });

  return capabilities;
}

std::string Composer::dumpDebugInfo() {
  std::string debugInfo;
  mComposer->dumpDebugInfo(
      [&](const auto& tmpDebugInfo) { debugInfo = tmpDebugInfo.c_str(); });

  return debugInfo;
}

std::unique_ptr<ComposerClient> Composer::createClient() {
  std::unique_ptr<ComposerClient> client;
  mComposer->createClient([&](const auto& tmpError, const auto& tmpClient) {
    ASSERT_EQ(Error::NONE, tmpError) << "failed to create client";
    client = std::make_unique<ComposerClient>(tmpClient);
  });

  return client;
}

ComposerClient::ComposerClient(const sp<IComposerClient>& client)
    : mClient(client) {}

ComposerClient::~ComposerClient() {
  for (auto it : mDisplayResources) {
    Display display = it.first;
    DisplayResource& resource = it.second;

    for (auto layer : resource.layers) {
      EXPECT_EQ(Error::NONE, mClient->destroyLayer(display, layer))
          << "failed to destroy layer " << layer;
    }

    if (resource.isVirtual) {
      EXPECT_EQ(Error::NONE, mClient->destroyVirtualDisplay(display))
          << "failed to destroy virtual display " << display;
    }
  }
  mDisplayResources.clear();
}

sp<IComposerClient> ComposerClient::getRaw() const { return mClient; }

void ComposerClient::registerCallback(const sp<IComposerCallback>& callback) {
  mClient->registerCallback(callback);
}

uint32_t ComposerClient::getMaxVirtualDisplayCount() {
  return mClient->getMaxVirtualDisplayCount();
}

Display ComposerClient::createVirtualDisplay(uint32_t width, uint32_t height,
                                             PixelFormat formatHint,
                                             uint32_t outputBufferSlotCount,
                                             PixelFormat* outFormat) {
  Display display = 0;
  mClient->createVirtualDisplay(
      width, height, formatHint, outputBufferSlotCount,
      [&](const auto& tmpError, const auto& tmpDisplay, const auto& tmpFormat) {
        ASSERT_EQ(Error::NONE, tmpError) << "failed to create virtual display";
        display = tmpDisplay;
        *outFormat = tmpFormat;

        ASSERT_TRUE(
            mDisplayResources.insert({display, DisplayResource(true)}).second)
            << "duplicated virtual display id " << display;
      });

  return display;
}

void ComposerClient::destroyVirtualDisplay(Display display) {
  Error error = mClient->destroyVirtualDisplay(display);
  ASSERT_EQ(Error::NONE, error)
      << "failed to destroy virtual display " << display;

  mDisplayResources.erase(display);
}

Layer ComposerClient::createLayer(Display display, uint32_t bufferSlotCount) {
  Layer layer = 0;
  mClient->createLayer(
      display, bufferSlotCount,
      [&](const auto& tmpError, const auto& tmpLayer) {
        ASSERT_EQ(Error::NONE, tmpError) << "failed to create layer";
        layer = tmpLayer;

        auto resourceIt = mDisplayResources.find(display);
        if (resourceIt == mDisplayResources.end()) {
          resourceIt =
              mDisplayResources.insert({display, DisplayResource(false)}).first;
        }

        ASSERT_TRUE(resourceIt->second.layers.insert(layer).second)
            << "duplicated layer id " << layer;
      });

  return layer;
}

void ComposerClient::destroyLayer(Display display, Layer layer) {
  Error error = mClient->destroyLayer(display, layer);
  ASSERT_EQ(Error::NONE, error) << "failed to destroy layer " << layer;

  auto resourceIt = mDisplayResources.find(display);
  ASSERT_NE(mDisplayResources.end(), resourceIt);
  resourceIt->second.layers.erase(layer);
}

Config ComposerClient::getActiveConfig(Display display) {
  Config config = 0;
  mClient->getActiveConfig(
      display, [&](const auto& tmpError, const auto& tmpConfig) {
        ASSERT_EQ(Error::NONE, tmpError) << "failed to get active config";
        config = tmpConfig;
      });

  return config;
}

bool ComposerClient::getClientTargetSupport(Display display, uint32_t width,
                                            uint32_t height, PixelFormat format,
                                            Dataspace dataspace) {
  Error error = mClient->getClientTargetSupport(display, width, height, format,
                                                dataspace);
  return error == Error::NONE;
}

std::vector<ColorMode> ComposerClient::getColorModes(Display display) {
  std::vector<ColorMode> modes;
  mClient->getColorModes(
      display, [&](const auto& tmpError, const auto& tmpMode) {
        ASSERT_EQ(Error::NONE, tmpError) << "failed to get color mode";
        modes = tmpMode;
      });

  return modes;
}

int32_t ComposerClient::getDisplayAttribute(
    Display display, Config config, IComposerClient::Attribute attribute) {
  int32_t value = 0;
  mClient->getDisplayAttribute(display, config, attribute,
                               [&](const auto& tmpError, const auto& tmpValue) {
                                 ASSERT_EQ(Error::NONE, tmpError)
                                     << "failed to get display attribute";
                                 value = tmpValue;
                               });

  return value;
}

std::vector<Config> ComposerClient::getDisplayConfigs(Display display) {
  std::vector<Config> configs;
  mClient->getDisplayConfigs(
      display, [&](const auto& tmpError, const auto& tmpConfigs) {
        ASSERT_EQ(Error::NONE, tmpError) << "failed to get display configs";
        configs = tmpConfigs;
      });

  return configs;
}

std::string ComposerClient::getDisplayName(Display display) {
  std::string name;
  mClient->getDisplayName(
      display, [&](const auto& tmpError, const auto& tmpName) {
        ASSERT_EQ(Error::NONE, tmpError) << "failed to get display name";
        name = tmpName.c_str();
      });

  return name;
}

IComposerClient::DisplayType ComposerClient::getDisplayType(Display display) {
  IComposerClient::DisplayType type = IComposerClient::DisplayType::INVALID;
  mClient->getDisplayType(
      display, [&](const auto& tmpError, const auto& tmpType) {
        ASSERT_EQ(Error::NONE, tmpError) << "failed to get display type";
        type = tmpType;
      });

  return type;
}

bool ComposerClient::getDozeSupport(Display display) {
  bool support = false;
  mClient->getDozeSupport(
      display, [&](const auto& tmpError, const auto& tmpSupport) {
        ASSERT_EQ(Error::NONE, tmpError) << "failed to get doze support";
        support = tmpSupport;
      });

  return support;
}

std::vector<Hdr> ComposerClient::getHdrCapabilities(
    Display display, float* outMaxLuminance, float* outMaxAverageLuminance,
    float* outMinLuminance) {
  std::vector<Hdr> types;
  mClient->getHdrCapabilities(
      display,
      [&](const auto& tmpError, const auto& tmpTypes,
          const auto& tmpMaxLuminance, const auto& tmpMaxAverageLuminance,
          const auto& tmpMinLuminance) {
        ASSERT_EQ(Error::NONE, tmpError) << "failed to get HDR capabilities";
        types = tmpTypes;
        *outMaxLuminance = tmpMaxLuminance;
        *outMaxAverageLuminance = tmpMaxAverageLuminance;
        *outMinLuminance = tmpMinLuminance;
      });

  return types;
}

void ComposerClient::setClientTargetSlotCount(Display display,
                                              uint32_t clientTargetSlotCount) {
  Error error =
      mClient->setClientTargetSlotCount(display, clientTargetSlotCount);
  ASSERT_EQ(Error::NONE, error) << "failed to set client target slot count";
}

void ComposerClient::setActiveConfig(Display display, Config config) {
  Error error = mClient->setActiveConfig(display, config);
  ASSERT_EQ(Error::NONE, error) << "failed to set active config";
}

void ComposerClient::setColorMode(Display display, ColorMode mode) {
  Error error = mClient->setColorMode(display, mode);
  ASSERT_EQ(Error::NONE, error) << "failed to set color mode";
}

void ComposerClient::setPowerMode(Display display,
                                  IComposerClient::PowerMode mode) {
  Error error = mClient->setPowerMode(display, mode);
  ASSERT_EQ(Error::NONE, error) << "failed to set power mode";
}

void ComposerClient::setVsyncEnabled(Display display, bool enabled) {
  IComposerClient::Vsync vsync = (enabled) ? IComposerClient::Vsync::ENABLE
                                           : IComposerClient::Vsync::DISABLE;
  Error error = mClient->setVsyncEnabled(display, vsync);
  ASSERT_EQ(Error::NONE, error) << "failed to set vsync mode";

  // give the hwbinder thread some time to handle any pending vsync callback
  if (!enabled) {
      usleep(5 * 1000);
  }
}

void ComposerClient::execute(TestCommandReader* reader,
                             CommandWriterBase* writer) {
  bool queueChanged = false;
  uint32_t commandLength = 0;
  hidl_vec<hidl_handle> commandHandles;
  ASSERT_TRUE(
      writer->writeQueue(&queueChanged, &commandLength, &commandHandles));

  if (queueChanged) {
    auto ret = mClient->setInputCommandQueue(*writer->getMQDescriptor());
    ASSERT_EQ(Error::NONE, static_cast<Error>(ret));
    return;
  }

  mClient->executeCommands(
      commandLength, commandHandles,
      [&](const auto& tmpError, const auto& tmpOutQueueChanged,
          const auto& tmpOutLength, const auto& tmpOutHandles) {
        ASSERT_EQ(Error::NONE, tmpError);

        if (tmpOutQueueChanged) {
          mClient->getOutputCommandQueue(
              [&](const auto& tmpError, const auto& tmpDescriptor) {
                ASSERT_EQ(Error::NONE, tmpError);
                reader->setMQDescriptor(tmpDescriptor);
              });
        }

        ASSERT_TRUE(reader->readQueue(tmpOutLength, tmpOutHandles));
        reader->parse();
      });
}

}  // namespace tests
}  // namespace V2_1
}  // namespace composer
}  // namespace graphics
}  // namespace hardware
}  // namespace android
