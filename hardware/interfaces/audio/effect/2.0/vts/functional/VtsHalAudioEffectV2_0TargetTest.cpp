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

#define LOG_TAG "AudioEffectHidlHalTest"
#include <android-base/logging.h>
#include <system/audio.h>

#include <android/hardware/audio/effect/2.0/IEffect.h>
#include <android/hardware/audio/effect/2.0/IEffectsFactory.h>
#include <android/hardware/audio/effect/2.0/IEqualizerEffect.h>
#include <android/hardware/audio/effect/2.0/ILoudnessEnhancerEffect.h>
#include <android/hardware/audio/effect/2.0/types.h>
#include <android/hidl/allocator/1.0/IAllocator.h>
#include <android/hidl/memory/1.0/IMemory.h>

#include <VtsHalHidlTargetTestBase.h>

using android::hardware::audio::common::V2_0::AudioDevice;
using android::hardware::audio::common::V2_0::AudioHandleConsts;
using android::hardware::audio::common::V2_0::AudioMode;
using android::hardware::audio::common::V2_0::AudioSource;
using android::hardware::audio::common::V2_0::Uuid;
using android::hardware::audio::effect::V2_0::AudioBuffer;
using android::hardware::audio::effect::V2_0::EffectAuxChannelsConfig;
using android::hardware::audio::effect::V2_0::EffectBufferConfig;
using android::hardware::audio::effect::V2_0::EffectConfig;
using android::hardware::audio::effect::V2_0::EffectDescriptor;
using android::hardware::audio::effect::V2_0::EffectOffloadParameter;
using android::hardware::audio::effect::V2_0::IEffect;
using android::hardware::audio::effect::V2_0::IEffectsFactory;
using android::hardware::audio::effect::V2_0::IEqualizerEffect;
using android::hardware::audio::effect::V2_0::ILoudnessEnhancerEffect;
using android::hardware::audio::effect::V2_0::Result;
using android::hardware::MQDescriptorSync;
using android::hardware::Return;
using android::hardware::Void;
using android::hardware::hidl_handle;
using android::hardware::hidl_memory;
using android::hardware::hidl_string;
using android::hardware::hidl_vec;
using android::hidl::allocator::V1_0::IAllocator;
using android::hidl::memory::V1_0::IMemory;
using android::sp;

#ifndef ARRAY_SIZE
#define ARRAY_SIZE(a) (sizeof(a) / sizeof(*(a)))
#endif

// The main test class for Audio Effects Factory HIDL HAL.
class AudioEffectsFactoryHidlTest : public ::testing::VtsHalHidlTargetTestBase {
 public:
  void SetUp() override {
    effectsFactory =
        ::testing::VtsHalHidlTargetTestBase::getService<IEffectsFactory>();
    ASSERT_NE(effectsFactory, nullptr);
  }

  void TearDown() override { effectsFactory.clear(); }

 protected:
  static void description(const std::string& description) {
    RecordProperty("description", description);
  }

  sp<IEffectsFactory> effectsFactory;
};

TEST_F(AudioEffectsFactoryHidlTest, EnumerateEffects) {
  description("Verify that EnumerateEffects returns at least one effect");
  Result retval = Result::NOT_INITIALIZED;
  size_t effectCount = 0;
  Return<void> ret = effectsFactory->getAllDescriptors(
      [&](Result r, const hidl_vec<EffectDescriptor>& result) {
        retval = r;
        effectCount = result.size();
      });
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::OK, retval);
  EXPECT_GT(effectCount, 0u);
}

TEST_F(AudioEffectsFactoryHidlTest, CreateEffect) {
  description("Verify that an effect can be created via CreateEffect");
  bool gotEffect = false;
  Uuid effectUuid;
  Return<void> ret = effectsFactory->getAllDescriptors(
      [&](Result r, const hidl_vec<EffectDescriptor>& result) {
        if (r == Result::OK && result.size() > 0) {
          gotEffect = true;
          effectUuid = result[0].uuid;
        }
      });
  ASSERT_TRUE(ret.isOk());
  ASSERT_TRUE(gotEffect);
  Result retval = Result::NOT_INITIALIZED;
  sp<IEffect> effect;
  ret = effectsFactory->createEffect(
      effectUuid, 1 /*session*/, 1 /*ioHandle*/,
      [&](Result r, const sp<IEffect>& result, uint64_t /*effectId*/) {
        retval = r;
        if (r == Result::OK) {
          effect = result;
        }
      });
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::OK, retval);
  EXPECT_NE(nullptr, effect.get());
}

TEST_F(AudioEffectsFactoryHidlTest, GetDescriptor) {
  description(
      "Verify that effects factory can provide an effect descriptor via "
      "GetDescriptor");
  hidl_vec<EffectDescriptor> allDescriptors;
  Return<void> ret = effectsFactory->getAllDescriptors(
      [&](Result r, const hidl_vec<EffectDescriptor>& result) {
        if (r == Result::OK) {
          allDescriptors = result;
        }
      });
  ASSERT_TRUE(ret.isOk());
  ASSERT_GT(allDescriptors.size(), 0u);
  for (size_t i = 0; i < allDescriptors.size(); ++i) {
    ret = effectsFactory->getDescriptor(
        allDescriptors[i].uuid, [&](Result r, const EffectDescriptor& result) {
          EXPECT_EQ(r, Result::OK);
          EXPECT_EQ(result, allDescriptors[i]);
        });
  }
  EXPECT_TRUE(ret.isOk());
}

TEST_F(AudioEffectsFactoryHidlTest, DebugDumpInvalidArgument) {
    description("Verify that debugDump doesn't crash on invalid arguments");
    Return<void> ret = effectsFactory->debugDump(hidl_handle());
    ASSERT_TRUE(ret.isOk());
}

// Equalizer effect is required by CDD, but only the type is fixed.
// This is the same UUID as AudioEffect.EFFECT_TYPE_EQUALIZER in Java.
static const Uuid EQUALIZER_EFFECT_TYPE = {
    0x0bed4300, 0xddd6, 0x11db, 0x8f34,
    std::array<uint8_t, 6>{{0x00, 0x02, 0xa5, 0xd5, 0xc5, 0x1b}}};
// Loudness Enhancer effect is required by CDD, but only the type is fixed.
// This is the same UUID as AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER in Java.
static const Uuid LOUDNESS_ENHANCER_EFFECT_TYPE = {
    0xfe3199be, 0xaed0, 0x413f, 0x87bb,
    std::array<uint8_t, 6>{{0x11, 0x26, 0x0e, 0xb6, 0x3c, 0xf1}}};

// The main test class for Audio Effect HIDL HAL.
class AudioEffectHidlTest : public ::testing::VtsHalHidlTargetTestBase {
 public:
  void SetUp() override {
    effectsFactory =
        ::testing::VtsHalHidlTargetTestBase::getService<IEffectsFactory>();
    ASSERT_NE(nullptr, effectsFactory.get());

    findAndCreateEffect(getEffectType());
    ASSERT_NE(nullptr, effect.get());

    Return<Result> ret = effect->init();
    ASSERT_TRUE(ret.isOk());
    ASSERT_EQ(Result::OK, ret);
  }

  void TearDown() override {
    effect.clear();
    effectsFactory.clear();
  }

 protected:
  static void description(const std::string& description) {
    RecordProperty("description", description);
  }

  virtual Uuid getEffectType() { return EQUALIZER_EFFECT_TYPE; }

  void findAndCreateEffect(const Uuid& type);
  void findEffectInstance(const Uuid& type, Uuid* uuid);
  void getChannelCount(uint32_t* channelCount);

  sp<IEffectsFactory> effectsFactory;
  sp<IEffect> effect;
};

void AudioEffectHidlTest::findAndCreateEffect(const Uuid& type) {
  Uuid effectUuid;
  findEffectInstance(type, &effectUuid);
  Return<void> ret = effectsFactory->createEffect(
      effectUuid, 1 /*session*/, 1 /*ioHandle*/,
      [&](Result r, const sp<IEffect>& result, uint64_t /*effectId*/) {
        if (r == Result::OK) {
          effect = result;
        }
      });
  ASSERT_TRUE(ret.isOk());
}

void AudioEffectHidlTest::findEffectInstance(const Uuid& type, Uuid* uuid) {
  bool effectFound = false;
  Return<void> ret = effectsFactory->getAllDescriptors(
      [&](Result r, const hidl_vec<EffectDescriptor>& result) {
        if (r == Result::OK) {
          for (const auto& desc : result) {
            if (desc.type == type) {
              effectFound = true;
              *uuid = desc.uuid;
              break;
            }
          }
        }
      });
  ASSERT_TRUE(ret.isOk());
  ASSERT_TRUE(effectFound);
}

void AudioEffectHidlTest::getChannelCount(uint32_t* channelCount) {
  Result retval;
  EffectConfig currentConfig;
  Return<void> ret = effect->getConfig([&](Result r, const EffectConfig& conf) {
    retval = r;
    if (r == Result::OK) {
      currentConfig = conf;
    }
  });
  ASSERT_TRUE(ret.isOk());
  ASSERT_EQ(Result::OK, retval);
  ASSERT_TRUE(audio_channel_mask_is_valid(
      static_cast<audio_channel_mask_t>(currentConfig.outputCfg.channels)));
  *channelCount = audio_channel_count_from_out_mask(
      static_cast<audio_channel_mask_t>(currentConfig.outputCfg.channels));
}

TEST_F(AudioEffectHidlTest, Close) {
  description("Verify that an effect can be closed");
  Return<Result> ret = effect->close();
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::OK, ret);
}

TEST_F(AudioEffectHidlTest, GetDescriptor) {
  description(
      "Verify that an effect can return its own descriptor via GetDescriptor");
  Result retval = Result::NOT_INITIALIZED;
  Uuid actualType;
  Return<void> ret =
      effect->getDescriptor([&](Result r, const EffectDescriptor& desc) {
        retval = r;
        if (r == Result::OK) {
          actualType = desc.type;
        }
      });
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::OK, retval);
  EXPECT_EQ(getEffectType(), actualType);
}

TEST_F(AudioEffectHidlTest, GetSetConfig) {
  description(
      "Verify that it is possible to manipulate effect config via Get / "
      "SetConfig");
  Result retval = Result::NOT_INITIALIZED;
  EffectConfig currentConfig;
  Return<void> ret = effect->getConfig([&](Result r, const EffectConfig& conf) {
    retval = r;
    if (r == Result::OK) {
      currentConfig = conf;
    }
  });
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::OK, retval);
  Return<Result> ret2 = effect->setConfig(currentConfig, nullptr, nullptr);
  EXPECT_TRUE(ret2.isOk());
  EXPECT_EQ(Result::OK, ret2);
}

TEST_F(AudioEffectHidlTest, GetConfigReverse) {
  description("Verify that GetConfigReverse does not crash");
  Return<void> ret =
      effect->getConfigReverse([&](Result, const EffectConfig&) {});
  EXPECT_TRUE(ret.isOk());
}

TEST_F(AudioEffectHidlTest, GetSupportedAuxChannelsConfigs) {
  description("Verify that GetSupportedAuxChannelsConfigs does not crash");
  Return<void> ret = effect->getSupportedAuxChannelsConfigs(
      0, [&](Result, const hidl_vec<EffectAuxChannelsConfig>&) {});
  EXPECT_TRUE(ret.isOk());
}

TEST_F(AudioEffectHidlTest, GetAuxChannelsConfig) {
  description("Verify that GetAuxChannelsConfig does not crash");
  Return<void> ret = effect->getAuxChannelsConfig(
      [&](Result, const EffectAuxChannelsConfig&) {});
  EXPECT_TRUE(ret.isOk());
}

TEST_F(AudioEffectHidlTest, SetAuxChannelsConfig) {
  description("Verify that SetAuxChannelsConfig does not crash");
  Return<Result> ret = effect->setAuxChannelsConfig(EffectAuxChannelsConfig());
  EXPECT_TRUE(ret.isOk());
}

// Not generated automatically because AudioBuffer contains
// instances of hidl_memory which can't be compared properly
// in general case due to presence of handles.
//
// However, in this particular case, handles must not present
// thus comparison is possible.
//
// operator== must be defined in the same namespace as the structures.
namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
inline bool operator==(const AudioBuffer& lhs, const AudioBuffer& rhs) {
  return lhs.id == rhs.id && lhs.frameCount == rhs.frameCount &&
         lhs.data.handle() == nullptr && rhs.data.handle() == nullptr;
}

inline bool operator==(const EffectBufferConfig& lhs,
                       const EffectBufferConfig& rhs) {
  return lhs.buffer == rhs.buffer && lhs.samplingRateHz == rhs.samplingRateHz &&
         lhs.channels == rhs.channels && lhs.format == rhs.format &&
         lhs.accessMode == rhs.accessMode && lhs.mask == rhs.mask;
}

inline bool operator==(const EffectConfig& lhs, const EffectConfig& rhs) {
  return lhs.inputCfg == rhs.inputCfg && lhs.outputCfg == rhs.outputCfg;
}
}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android

TEST_F(AudioEffectHidlTest, Reset) {
  description("Verify that Reset preserves effect configuration");
  Result retval = Result::NOT_INITIALIZED;
  EffectConfig originalConfig;
  Return<void> ret = effect->getConfig([&](Result r, const EffectConfig& conf) {
    retval = r;
    if (r == Result::OK) {
      originalConfig = conf;
    }
  });
  ASSERT_TRUE(ret.isOk());
  ASSERT_EQ(Result::OK, retval);
  Return<Result> ret2 = effect->reset();
  EXPECT_TRUE(ret2.isOk());
  EXPECT_EQ(Result::OK, ret2);
  EffectConfig configAfterReset;
  ret = effect->getConfig([&](Result r, const EffectConfig& conf) {
    retval = r;
    if (r == Result::OK) {
      configAfterReset = conf;
    }
  });
  EXPECT_EQ(originalConfig, configAfterReset);
}

TEST_F(AudioEffectHidlTest, DisableEnableDisable) {
  description("Verify Disable -> Enable -> Disable sequence for an effect");
  Return<Result> ret = effect->disable();
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::INVALID_ARGUMENTS, ret);
  ret = effect->enable();
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::OK, ret);
  ret = effect->disable();
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::OK, ret);
}

TEST_F(AudioEffectHidlTest, SetDevice) {
  description("Verify that SetDevice works for an output chain effect");
  Return<Result> ret = effect->setDevice(AudioDevice::OUT_SPEAKER);
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::OK, ret);
}

TEST_F(AudioEffectHidlTest, SetAndGetVolume) {
  description("Verify that SetAndGetVolume method works for an effect");
  uint32_t channelCount;
  getChannelCount(&channelCount);
  hidl_vec<uint32_t> volumes;
  volumes.resize(channelCount);
  for (uint32_t i = 0; i < channelCount; ++i) {
    volumes[i] = 0;
  }
  Result retval = Result::NOT_INITIALIZED;
  Return<void> ret = effect->setAndGetVolume(
      volumes, [&](Result r, const hidl_vec<uint32_t>&) { retval = r; });
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::OK, retval);
}

TEST_F(AudioEffectHidlTest, VolumeChangeNotification) {
  description("Verify that effect accepts VolumeChangeNotification");
  uint32_t channelCount;
  getChannelCount(&channelCount);
  hidl_vec<uint32_t> volumes;
  volumes.resize(channelCount);
  for (uint32_t i = 0; i < channelCount; ++i) {
    volumes[i] = 0;
  }
  Return<Result> ret = effect->volumeChangeNotification(volumes);
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::OK, ret);
}

TEST_F(AudioEffectHidlTest, SetAudioMode) {
  description("Verify that SetAudioMode works for an effect");
  Return<Result> ret = effect->setAudioMode(AudioMode::NORMAL);
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::OK, ret);
}

TEST_F(AudioEffectHidlTest, SetConfigReverse) {
  description("Verify that SetConfigReverse does not crash");
  Return<Result> ret =
      effect->setConfigReverse(EffectConfig(), nullptr, nullptr);
  EXPECT_TRUE(ret.isOk());
}

TEST_F(AudioEffectHidlTest, SetInputDevice) {
  description("Verify that SetInputDevice does not crash");
  Return<Result> ret = effect->setInputDevice(AudioDevice::IN_BUILTIN_MIC);
  EXPECT_TRUE(ret.isOk());
}

TEST_F(AudioEffectHidlTest, SetAudioSource) {
  description("Verify that SetAudioSource does not crash");
  Return<Result> ret = effect->setAudioSource(AudioSource::MIC);
  EXPECT_TRUE(ret.isOk());
}

TEST_F(AudioEffectHidlTest, Offload) {
  description("Verify that calling Offload method does not crash");
  EffectOffloadParameter offloadParam;
  offloadParam.isOffload = false;
  offloadParam.ioHandle =
      static_cast<int>(AudioHandleConsts::AUDIO_IO_HANDLE_NONE);
  Return<Result> ret = effect->offload(offloadParam);
  EXPECT_TRUE(ret.isOk());
}

TEST_F(AudioEffectHidlTest, PrepareForProcessing) {
  description("Verify that PrepareForProcessing method works for an effect");
  Result retval = Result::NOT_INITIALIZED;
  Return<void> ret = effect->prepareForProcessing(
      [&](Result r, const MQDescriptorSync<Result>&) { retval = r; });
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::OK, retval);
}

TEST_F(AudioEffectHidlTest, SetProcessBuffers) {
  description("Verify that SetProcessBuffers works for an effect");
  sp<IAllocator> ashmem = IAllocator::getService("ashmem");
  ASSERT_NE(nullptr, ashmem.get());
  bool success = false;
  AudioBuffer buffer;
  Return<void> ret =
      ashmem->allocate(1024, [&](bool s, const hidl_memory& memory) {
        success = s;
        if (s) {
          buffer.data = memory;
        }
      });
  ASSERT_TRUE(ret.isOk());
  ASSERT_TRUE(success);
  Return<Result> ret2 = effect->setProcessBuffers(buffer, buffer);
  EXPECT_TRUE(ret2.isOk());
  EXPECT_EQ(Result::OK, ret2);
}

TEST_F(AudioEffectHidlTest, Command) {
  description("Verify that Command does not crash");
  Return<void> ret = effect->command(0, hidl_vec<uint8_t>(), 0,
                                     [&](int32_t, const hidl_vec<uint8_t>&) {});
  EXPECT_TRUE(ret.isOk());
}

TEST_F(AudioEffectHidlTest, SetParameter) {
  description("Verify that SetParameter does not crash");
  Return<Result> ret =
      effect->setParameter(hidl_vec<uint8_t>(), hidl_vec<uint8_t>());
  EXPECT_TRUE(ret.isOk());
}

TEST_F(AudioEffectHidlTest, GetParameter) {
  description("Verify that GetParameter does not crash");
  Return<void> ret = effect->getParameter(
      hidl_vec<uint8_t>(), 0, [&](Result, const hidl_vec<uint8_t>&) {});
  EXPECT_TRUE(ret.isOk());
}

TEST_F(AudioEffectHidlTest, GetSupportedConfigsForFeature) {
  description("Verify that GetSupportedConfigsForFeature does not crash");
  Return<void> ret = effect->getSupportedConfigsForFeature(
      0, 0, 0, [&](Result, uint32_t, const hidl_vec<uint8_t>&) {});
  EXPECT_TRUE(ret.isOk());
}

TEST_F(AudioEffectHidlTest, GetCurrentConfigForFeature) {
  description("Verify that GetCurrentConfigForFeature does not crash");
  Return<void> ret = effect->getCurrentConfigForFeature(
      0, 0, [&](Result, const hidl_vec<uint8_t>&) {});
  EXPECT_TRUE(ret.isOk());
}

TEST_F(AudioEffectHidlTest, SetCurrentConfigForFeature) {
  description("Verify that SetCurrentConfigForFeature does not crash");
  Return<Result> ret =
      effect->setCurrentConfigForFeature(0, hidl_vec<uint8_t>());
  EXPECT_TRUE(ret.isOk());
}


// The main test class for Equalizer Audio Effect HIDL HAL.
class EqualizerAudioEffectHidlTest : public AudioEffectHidlTest {
 public:
  void SetUp() override {
    AudioEffectHidlTest::SetUp();
    equalizer = IEqualizerEffect::castFrom(effect);
    ASSERT_NE(nullptr, equalizer.get());
  }

 protected:
  Uuid getEffectType() override { return EQUALIZER_EFFECT_TYPE; }
  void getNumBands(uint16_t* numBands);
  void getLevelRange(int16_t* minLevel, int16_t* maxLevel);
  void getBandFrequencyRange(uint16_t band, uint32_t* minFreq,
                             uint32_t* centerFreq, uint32_t* maxFreq);
  void getPresetCount(size_t* count);

  sp<IEqualizerEffect> equalizer;
};

void EqualizerAudioEffectHidlTest::getNumBands(uint16_t* numBands) {
  Result retval = Result::NOT_INITIALIZED;
  Return<void> ret = equalizer->getNumBands([&](Result r, uint16_t b) {
    retval = r;
    if (retval == Result::OK) {
      *numBands = b;
    }
  });
  ASSERT_TRUE(ret.isOk());
  ASSERT_EQ(Result::OK, retval);
}

void EqualizerAudioEffectHidlTest::getLevelRange(int16_t* minLevel,
                                                 int16_t* maxLevel) {
  Result retval = Result::NOT_INITIALIZED;
  Return<void> ret =
      equalizer->getLevelRange([&](Result r, int16_t min, int16_t max) {
        retval = r;
        if (retval == Result::OK) {
          *minLevel = min;
          *maxLevel = max;
        }
      });
  ASSERT_TRUE(ret.isOk());
  ASSERT_EQ(Result::OK, retval);
}

void EqualizerAudioEffectHidlTest::getBandFrequencyRange(uint16_t band,
                                                         uint32_t* minFreq,
                                                         uint32_t* centerFreq,
                                                         uint32_t* maxFreq) {
  Result retval = Result::NOT_INITIALIZED;
  Return<void> ret = equalizer->getBandFrequencyRange(
      band, [&](Result r, uint32_t min, uint32_t max) {
        retval = r;
        if (retval == Result::OK) {
          *minFreq = min;
          *maxFreq = max;
        }
      });
  ASSERT_TRUE(ret.isOk());
  ASSERT_EQ(Result::OK, retval);
  ret = equalizer->getBandCenterFrequency(band, [&](Result r, uint32_t center) {
    retval = r;
    if (retval == Result::OK) {
      *centerFreq = center;
    }
  });
  ASSERT_TRUE(ret.isOk());
  ASSERT_EQ(Result::OK, retval);
}

void EqualizerAudioEffectHidlTest::getPresetCount(size_t* count) {
  Result retval = Result::NOT_INITIALIZED;
  Return<void> ret = equalizer->getPresetNames(
      [&](Result r, const hidl_vec<hidl_string>& names) {
        retval = r;
        if (retval == Result::OK) {
          *count = names.size();
        }
      });
  ASSERT_TRUE(ret.isOk());
  ASSERT_EQ(Result::OK, retval);
}

TEST_F(EqualizerAudioEffectHidlTest, GetNumBands) {
  description("Verify that Equalizer effect reports at least one band");
  uint16_t numBands = 0;
  getNumBands(&numBands);
  EXPECT_GT(numBands, 0);
}

TEST_F(EqualizerAudioEffectHidlTest, GetLevelRange) {
  description("Verify that Equalizer effect reports adequate band level range");
  int16_t minLevel = 0x7fff, maxLevel = 0;
  getLevelRange(&minLevel, &maxLevel);
  EXPECT_GT(maxLevel, minLevel);
}

TEST_F(EqualizerAudioEffectHidlTest, GetSetBandLevel) {
  description(
      "Verify that manipulating band levels works for Equalizer effect");
  uint16_t numBands = 0;
  getNumBands(&numBands);
  ASSERT_GT(numBands, 0);
  int16_t levels[3]{0x7fff, 0, 0};
  getLevelRange(&levels[0], &levels[2]);
  ASSERT_GT(levels[2], levels[0]);
  levels[1] = (levels[2] + levels[0]) / 2;
  for (uint16_t i = 0; i < numBands; ++i) {
    for (size_t j = 0; j < ARRAY_SIZE(levels); ++j) {
      Return<Result> ret = equalizer->setBandLevel(i, levels[j]);
      EXPECT_TRUE(ret.isOk());
      EXPECT_EQ(Result::OK, ret);
      Result retval = Result::NOT_INITIALIZED;
      int16_t actualLevel;
      Return<void> ret2 = equalizer->getBandLevel(i, [&](Result r, int16_t l) {
        retval = r;
        if (retval == Result::OK) {
          actualLevel = l;
        }
      });
      EXPECT_TRUE(ret2.isOk());
      EXPECT_EQ(Result::OK, retval);
      EXPECT_EQ(levels[j], actualLevel);
    }
  }
}

TEST_F(EqualizerAudioEffectHidlTest, GetBandCenterFrequencyAndRange) {
  description(
      "Verify that Equalizer effect reports adequate band frequency range");
  uint16_t numBands = 0;
  getNumBands(&numBands);
  ASSERT_GT(numBands, 0);
  for (uint16_t i = 0; i < numBands; ++i) {
    uint32_t minFreq = 0xffffffff, centerFreq = 0xffffffff,
             maxFreq = 0xffffffff;
    getBandFrequencyRange(i, &minFreq, &centerFreq, &maxFreq);
    // Note: NXP legacy implementation reports "1" as upper bound for last band,
    // so this check fails.
    EXPECT_GE(maxFreq, centerFreq);
    EXPECT_GE(centerFreq, minFreq);
  }
}

TEST_F(EqualizerAudioEffectHidlTest, GetBandForFrequency) {
  description(
      "Verify that Equalizer effect supports GetBandForFrequency correctly");
  uint16_t numBands = 0;
  getNumBands(&numBands);
  ASSERT_GT(numBands, 0);
  for (uint16_t i = 0; i < numBands; ++i) {
    uint32_t freqs[3]{0, 0, 0};
    getBandFrequencyRange(i, &freqs[0], &freqs[1], &freqs[2]);
    // NXP legacy implementation reports "1" as upper bound for last band, some
    // of the checks fail.
    for (size_t j = 0; j < ARRAY_SIZE(freqs); ++j) {
      if (j == 0) {
        freqs[j]++;
      }  // Min frequency is an open interval.
      Result retval = Result::NOT_INITIALIZED;
      uint16_t actualBand = numBands + 1;
      Return<void> ret =
          equalizer->getBandForFrequency(freqs[j], [&](Result r, uint16_t b) {
            retval = r;
            if (retval == Result::OK) {
              actualBand = b;
            }
          });
      EXPECT_TRUE(ret.isOk());
      EXPECT_EQ(Result::OK, retval);
      EXPECT_EQ(i, actualBand) << "Frequency: " << freqs[j];
    }
  }
}

TEST_F(EqualizerAudioEffectHidlTest, GetPresetNames) {
  description("Verify that Equalizer effect reports at least one preset");
  size_t presetCount;
  getPresetCount(&presetCount);
  EXPECT_GT(presetCount, 0u);
}

TEST_F(EqualizerAudioEffectHidlTest, GetSetCurrentPreset) {
  description(
      "Verify that manipulating the current preset for Equalizer effect");
  size_t presetCount;
  getPresetCount(&presetCount);
  ASSERT_GT(presetCount, 0u);
  for (uint16_t i = 0; i < presetCount; ++i) {
    Return<Result> ret = equalizer->setCurrentPreset(i);
    EXPECT_TRUE(ret.isOk());
    EXPECT_EQ(Result::OK, ret);
    Result retval = Result::NOT_INITIALIZED;
    uint16_t actualPreset = 0xffff;
    Return<void> ret2 = equalizer->getCurrentPreset([&](Result r, uint16_t p) {
      retval = r;
      if (retval == Result::OK) {
        actualPreset = p;
      }
    });
    EXPECT_TRUE(ret2.isOk());
    EXPECT_EQ(Result::OK, retval);
    EXPECT_EQ(i, actualPreset);
  }
}

TEST_F(EqualizerAudioEffectHidlTest, GetSetAllProperties) {
  description(
      "Verify that setting band levels and presets works via Get / "
      "SetAllProperties for Equalizer effect");
  using AllProperties =
      android::hardware::audio::effect::V2_0::IEqualizerEffect::AllProperties;
  uint16_t numBands = 0;
  getNumBands(&numBands);
  ASSERT_GT(numBands, 0);
  AllProperties props;
  props.bandLevels.resize(numBands);
  for (size_t i = 0; i < numBands; ++i) {
    props.bandLevels[i] = 0;
  }

  AllProperties actualProps;
  Result retval = Result::NOT_INITIALIZED;

  // Verify setting of the band levels via properties.
  props.curPreset = -1;
  Return<Result> ret = equalizer->setAllProperties(props);
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::OK, ret);
  Return<void> ret2 =
      equalizer->getAllProperties([&](Result r, AllProperties p) {
        retval = r;
        if (retval == Result::OK) {
          actualProps = p;
        }
      });
  EXPECT_TRUE(ret2.isOk());
  EXPECT_EQ(Result::OK, retval);
  EXPECT_EQ(props.bandLevels, actualProps.bandLevels);

  // Verify setting of the current preset via properties.
  props.curPreset = 0;  // Assuming there is at least one preset.
  ret = equalizer->setAllProperties(props);
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::OK, ret);
  ret2 = equalizer->getAllProperties([&](Result r, AllProperties p) {
    retval = r;
    if (retval == Result::OK) {
      actualProps = p;
    }
  });
  EXPECT_TRUE(ret2.isOk());
  EXPECT_EQ(Result::OK, retval);
  EXPECT_EQ(props.curPreset, actualProps.curPreset);
}

// The main test class for Equalizer Audio Effect HIDL HAL.
class LoudnessEnhancerAudioEffectHidlTest : public AudioEffectHidlTest {
 public:
  void SetUp() override {
    AudioEffectHidlTest::SetUp();
    enhancer = ILoudnessEnhancerEffect::castFrom(effect);
    ASSERT_NE(nullptr, enhancer.get());
  }

 protected:
  Uuid getEffectType() override { return LOUDNESS_ENHANCER_EFFECT_TYPE; }

  sp<ILoudnessEnhancerEffect> enhancer;
};

TEST_F(LoudnessEnhancerAudioEffectHidlTest, GetSetTargetGain) {
  description(
      "Verify that manipulating the target gain works for Loudness Enhancer "
      "effect");
  const int32_t gain = 100;
  Return<Result> ret = enhancer->setTargetGain(gain);
  EXPECT_TRUE(ret.isOk());
  EXPECT_EQ(Result::OK, ret);
  int32_t actualGain = 0;
  Result retval;
  Return<void> ret2 = enhancer->getTargetGain([&](Result r, int32_t g) {
    retval = r;
    if (retval == Result::OK) {
      actualGain = g;
    }
  });
  EXPECT_TRUE(ret2.isOk());
  EXPECT_EQ(Result::OK, retval);
  EXPECT_EQ(gain, actualGain);
}
