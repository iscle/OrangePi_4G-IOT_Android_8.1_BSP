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

#define LOG_TAG "SoundTriggerHidlHalTest"
#include <stdlib.h>
#include <time.h>

#include <condition_variable>
#include <mutex>

#include <android/log.h>
#include <cutils/native_handle.h>
#include <log/log.h>

#include <android/hardware/audio/common/2.0/types.h>
#include <android/hardware/soundtrigger/2.0/ISoundTriggerHw.h>
#include <android/hardware/soundtrigger/2.0/types.h>

#include <VtsHalHidlTargetTestBase.h>

#define SHORT_TIMEOUT_PERIOD (1)

using ::android::hardware::audio::common::V2_0::AudioDevice;
using ::android::hardware::soundtrigger::V2_0::SoundModelHandle;
using ::android::hardware::soundtrigger::V2_0::SoundModelType;
using ::android::hardware::soundtrigger::V2_0::RecognitionMode;
using ::android::hardware::soundtrigger::V2_0::PhraseRecognitionExtra;
using ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw;
using ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

/**
 * Test code uses this class to wait for notification from callback.
 */
class Monitor {
 public:
  Monitor() : mCount(0) {}

  /**
   * Adds 1 to the internal counter and unblocks one of the waiting threads.
   */
  void notify() {
    std::unique_lock<std::mutex> lock(mMtx);
    mCount++;
    mCv.notify_one();
  }

  /**
   * Blocks until the internal counter becomes greater than 0.
   *
   * If notified, this method decreases the counter by 1 and returns true.
   * If timeout, returns false.
   */
  bool wait(int timeoutSeconds) {
    std::unique_lock<std::mutex> lock(mMtx);
    auto deadline = std::chrono::system_clock::now() +
        std::chrono::seconds(timeoutSeconds);
    while (mCount == 0) {
      if (mCv.wait_until(lock, deadline) == std::cv_status::timeout) {
        return false;
      }
    }
    mCount--;
    return true;
  }

 private:
  std::mutex mMtx;
  std::condition_variable mCv;
  int mCount;
};

// The main test class for Sound Trigger HIDL HAL.
class SoundTriggerHidlTest : public ::testing::VtsHalHidlTargetTestBase {
 public:
  virtual void SetUp() override {
      mSoundTriggerHal =
          ::testing::VtsHalHidlTargetTestBase::getService<ISoundTriggerHw>();
      ASSERT_NE(nullptr, mSoundTriggerHal.get());
      mCallback = new SoundTriggerHwCallback(*this);
      ASSERT_NE(nullptr, mCallback.get());
  }

  static void SetUpTestCase() {
    srand(time(nullptr));
  }

  class SoundTriggerHwCallback : public ISoundTriggerHwCallback {
   private:
    SoundTriggerHidlTest& mParent;

   public:
    SoundTriggerHwCallback(SoundTriggerHidlTest& parent) : mParent(parent) {}

    virtual Return<void> recognitionCallback(
        const ISoundTriggerHwCallback::RecognitionEvent& event __unused,
        int32_t cookie __unused) {
      ALOGI("%s", __FUNCTION__);
      return Void();
    }

    virtual Return<void> phraseRecognitionCallback(
        const ISoundTriggerHwCallback::PhraseRecognitionEvent& event __unused,
        int32_t cookie __unused) {
      ALOGI("%s", __FUNCTION__);
      return Void();
    }

    virtual Return<void> soundModelCallback(
        const ISoundTriggerHwCallback::ModelEvent& event,
        int32_t cookie __unused) {
      ALOGI("%s", __FUNCTION__);
      mParent.lastModelEvent = event;
      mParent.monitor.notify();
      return Void();
    }
  };

  virtual void TearDown() override {}

  Monitor monitor;
  // updated by soundModelCallback()
  ISoundTriggerHwCallback::ModelEvent lastModelEvent;

 protected:
  sp<ISoundTriggerHw> mSoundTriggerHal;
  sp<SoundTriggerHwCallback> mCallback;
};

// A class for test environment setup (kept since this file is a template).
class SoundTriggerHidlEnvironment : public ::testing::Environment {
 public:
  virtual void SetUp() {}
  virtual void TearDown() {}

 private:
};

/**
 * Test ISoundTriggerHw::getProperties() method
 *
 * Verifies that:
 *  - the implementation implements the method
 *  - the method returns 0 (no error)
 *  - the implementation supports at least one sound model and one key phrase
 *  - the implementation supports at least VOICE_TRIGGER recognition mode
 */
TEST_F(SoundTriggerHidlTest, GetProperties) {
  ISoundTriggerHw::Properties halProperties;
  Return<void> hidlReturn;
  int ret = -ENODEV;

  hidlReturn = mSoundTriggerHal->getProperties([&](int rc, auto res) {
      ret = rc;
      halProperties = res;
  });

  EXPECT_TRUE(hidlReturn.isOk());
  EXPECT_EQ(0, ret);
  EXPECT_GT(halProperties.maxSoundModels, 0u);
  EXPECT_GT(halProperties.maxKeyPhrases, 0u);
  EXPECT_NE(0u, (halProperties.recognitionModes & (uint32_t)RecognitionMode::VOICE_TRIGGER));
}

/**
 * Test ISoundTriggerHw::loadPhraseSoundModel() method
 *
 * Verifies that:
 *  - the implementation implements the method
 *  - the implementation returns an error when passed a malformed sound model
 *
 * There is no way to verify that implementation actually can load a sound model because each
 * sound model is vendor specific.
 */
TEST_F(SoundTriggerHidlTest, LoadInvalidModelFail) {
  Return<void> hidlReturn;
  int ret = -ENODEV;
  ISoundTriggerHw::PhraseSoundModel model;
  SoundModelHandle handle;

  model.common.type = SoundModelType::UNKNOWN;

  hidlReturn = mSoundTriggerHal->loadPhraseSoundModel(
          model,
          mCallback, 0, [&](int32_t retval, auto res) {
      ret = retval;
      handle = res;
  });

  EXPECT_TRUE(hidlReturn.isOk());
  EXPECT_NE(0, ret);
  EXPECT_FALSE(monitor.wait(SHORT_TIMEOUT_PERIOD));
}

/**
 * Test ISoundTriggerHw::loadSoundModel() method
 *
 * Verifies that:
 *  - the implementation returns error when passed a sound model with random data.
 */
TEST_F(SoundTriggerHidlTest, LoadGenericSoundModelFail) {
  int ret = -ENODEV;
  ISoundTriggerHw::SoundModel model;
  SoundModelHandle handle = 0;

  model.type = SoundModelType::GENERIC;
  model.data.resize(100);
  for (auto& d : model.data) {
    d = rand();
  }

  Return<void> loadReturn = mSoundTriggerHal->loadSoundModel(
      model,
      mCallback, 0, [&](int32_t retval, auto res) {
    ret = retval;
    handle = res;
  });

  EXPECT_TRUE(loadReturn.isOk());
  EXPECT_NE(0, ret);
  EXPECT_FALSE(monitor.wait(SHORT_TIMEOUT_PERIOD));
}

/**
 * Test ISoundTriggerHw::unloadSoundModel() method
 *
 * Verifies that:
 *  - the implementation implements the method
 *  - the implementation returns an error when called without a valid loaded sound model
 *
 */
TEST_F(SoundTriggerHidlTest, UnloadModelNoModelFail) {
  Return<int32_t> hidlReturn(0);
  SoundModelHandle halHandle = 0;

  hidlReturn = mSoundTriggerHal->unloadSoundModel(halHandle);

  EXPECT_TRUE(hidlReturn.isOk());
  EXPECT_NE(0, hidlReturn);
}

/**
 * Test ISoundTriggerHw::startRecognition() method
 *
 * Verifies that:
 *  - the implementation implements the method
 *  - the implementation returns an error when called without a valid loaded sound model
 *
 * There is no way to verify that implementation actually starts recognition because no model can
 * be loaded.
 */
TEST_F(SoundTriggerHidlTest, StartRecognitionNoModelFail) {
    Return<int32_t> hidlReturn(0);
    SoundModelHandle handle = 0;
    PhraseRecognitionExtra phrase;
    ISoundTriggerHw::RecognitionConfig config;

    config.captureHandle = 0;
    config.captureDevice = AudioDevice::IN_BUILTIN_MIC;
    phrase.id = 0;
    phrase.recognitionModes = (uint32_t)RecognitionMode::VOICE_TRIGGER;
    phrase.confidenceLevel = 0;

    config.phrases.setToExternal(&phrase, 1);

    hidlReturn = mSoundTriggerHal->startRecognition(handle, config, mCallback, 0);

    EXPECT_TRUE(hidlReturn.isOk());
    EXPECT_NE(0, hidlReturn);
}

/**
 * Test ISoundTriggerHw::stopRecognition() method
 *
 * Verifies that:
 *  - the implementation implements the method
 *  - the implementation returns an error when called without an active recognition running
 *
 */
TEST_F(SoundTriggerHidlTest, StopRecognitionNoAStartFail) {
    Return<int32_t> hidlReturn(0);
    SoundModelHandle handle = 0;

    hidlReturn = mSoundTriggerHal->stopRecognition(handle);

    EXPECT_TRUE(hidlReturn.isOk());
    EXPECT_NE(0, hidlReturn);
}

/**
 * Test ISoundTriggerHw::stopAllRecognitions() method
 *
 * Verifies that:
 *  - the implementation implements this optional method or indicates it is not support by
 *  returning -ENOSYS
 */
TEST_F(SoundTriggerHidlTest, stopAllRecognitions) {
    Return<int32_t> hidlReturn(0);

    hidlReturn = mSoundTriggerHal->stopAllRecognitions();

    EXPECT_TRUE(hidlReturn.isOk());
    EXPECT_TRUE(hidlReturn == 0 || hidlReturn == -ENOSYS);
}


int main(int argc, char** argv) {
  ::testing::AddGlobalTestEnvironment(new SoundTriggerHidlEnvironment);
  ::testing::InitGoogleTest(&argc, argv);
  int status = RUN_ALL_TESTS();
  ALOGI("Test result = %d", status);
  return status;
}
