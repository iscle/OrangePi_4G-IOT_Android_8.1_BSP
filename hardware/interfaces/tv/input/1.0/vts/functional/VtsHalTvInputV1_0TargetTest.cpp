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

#define LOG_TAG "tv_input_hidl_hal_test"
#include <android-base/logging.h>

#include <android/hardware/tv/input/1.0/types.h>
#include <android/hardware/tv/input/1.0/ITvInput.h>
#include <android/hardware/tv/input/1.0/ITvInputCallback.h>

#include <VtsHalHidlTargetTestBase.h>
#include <utils/KeyedVector.h>
#include <mutex>
#include <vector>

using ::android::hardware::tv::input::V1_0::ITvInput;
using ::android::hardware::tv::input::V1_0::ITvInputCallback;
using ::android::hardware::tv::input::V1_0::Result;
using ::android::hardware::tv::input::V1_0::TvInputType;
using ::android::hardware::tv::input::V1_0::TvInputDeviceInfo;
using ::android::hardware::tv::input::V1_0::TvInputEventType;
using ::android::hardware::tv::input::V1_0::TvInputEvent;
using ::android::hardware::tv::input::V1_0::TvStreamConfig;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::sp;

#define WAIT_FOR_EVENT_TIMEOUT 5
#define DEFAULT_ID INT32_MIN

/* The main test class for TV Input HIDL HAL. */
class TvInputHidlTest : public ::testing::VtsHalHidlTargetTestBase {
 public:
  virtual void SetUp() override {
    tv_input_ = ::testing::VtsHalHidlTargetTestBase::getService<ITvInput>();
    ASSERT_NE(tv_input_, nullptr);
    tv_input_callback_ = new TvInputCallback(*this);
    ASSERT_NE(tv_input_callback_, nullptr);
    tv_input_->setCallback(tv_input_callback_);
    // All events received within the timeout should be handled.
    sleep(WAIT_FOR_EVENT_TIMEOUT);
  }

  virtual void TearDown() override {}

  /* Called when a DEVICE_AVAILABLE event is received. */
  void onDeviceAvailable(const TvInputDeviceInfo& deviceInfo) {
    device_info_.add(deviceInfo.deviceId, deviceInfo);
  }

  /* Called when a DEVICE_UNAVAILABLE event is received. */
  void onDeviceUnavailable(int32_t deviceId) {
    device_info_.removeItem(deviceId);
  }

  /* Called when a DEVICE_CONFIGURATIONS_CHANGED event is received. */
  Result onStreamConfigurationsChanged(int32_t deviceId) {
    return updateStreamConfigurations(deviceId);
  }

  /* Gets and updates the stream configurations for a device. */
  Result updateStreamConfigurations(int32_t deviceId) {
    stream_config_.removeItem(deviceId);
    Result result = Result::UNKNOWN;
    hidl_vec<TvStreamConfig> list;
    tv_input_->getStreamConfigurations(deviceId,
        [&result, &list](Result res, hidl_vec<TvStreamConfig> configs) {
          result = res;
          if (res == Result::OK) {
            list = configs;
          }
        });
    if (result == Result::OK) {
      stream_config_.add(deviceId, list);
    }
    return result;
  }

  /* Gets and updates the stream configurations for all existing devices. */
  void updateAllStreamConfigurations() {
    for (size_t i = 0; i < device_info_.size(); i++) {
      int32_t device_id = device_info_.keyAt(i);
      updateStreamConfigurations(device_id);
    }
  }

  /* Returns a list of indices of stream_config_ whose corresponding values are not empty. */
  std::vector<size_t> getConfigIndices() {
    std::vector<size_t> indices;
    for (size_t i = 0; i < stream_config_.size(); i++) {
      if (stream_config_.valueAt(i).size() != 0) {
        indices.push_back(i);
      }
    }
    return indices;
  }

  /*
   * Returns DEFAULT_ID if there is no missing integer in the range [0, the size of nums).
   * Otherwise, returns the smallest missing non-negative integer.
   */
  int32_t getNumNotIn(std::vector<int32_t>& nums) {
    int32_t result = DEFAULT_ID;
    int32_t size = static_cast<int32_t>(nums.size());
    for (int32_t i = 0; i < size; i++) {
      // Put every element to its target position, if possible.
      int32_t target_pos = nums[i];
      while (target_pos >= 0 && target_pos < size && i != target_pos && nums[i] != nums[target_pos]) {
        std::swap(nums[i], nums[target_pos]);
        target_pos = nums[i];
      }
    }

    for (int32_t i = 0; i < size; i++) {
      if (nums[i] != i) {
        return i;
      }
    }
    return result;
  }

  /* A simple test implementation of TvInputCallback for TV Input Events. */
  class TvInputCallback : public ITvInputCallback {
    public:
     TvInputCallback(TvInputHidlTest& parent) : parent_(parent){};

     virtual ~TvInputCallback() = default;

     /*
      * Notifies the client that an event has occured. For possible event types,
      * check TvInputEventType.
      */
     Return<void> notify(const TvInputEvent& event) override {
       std::unique_lock<std::mutex> lock(parent_.mutex_);
       switch(event.type) {
         case TvInputEventType::DEVICE_AVAILABLE:
           parent_.onDeviceAvailable(event.deviceInfo);
           break;
         case TvInputEventType::DEVICE_UNAVAILABLE:
           parent_.onDeviceUnavailable(event.deviceInfo.deviceId);
           break;
         case TvInputEventType::STREAM_CONFIGURATIONS_CHANGED:
           parent_.onStreamConfigurationsChanged(event.deviceInfo.deviceId);
           break;
       }
       return Void();
     };
    private:
     /* The test contains this callback instance. */
     TvInputHidlTest& parent_;
  };

  /* The TvInput used for the test. */
  sp<ITvInput> tv_input_;

  /* The TvInputCallback used for the test. */
  sp<ITvInputCallback> tv_input_callback_;

  /*
   * A KeyedVector stores device information of every available device.
   * A key is a device ID and the corresponding value is the TvInputDeviceInfo.
   */
  android::KeyedVector<int32_t, TvInputDeviceInfo> device_info_;

  /*
   * A KeyedVector stores a list of stream configurations of every available device.
   * A key is a device ID and the corresponding value is the stream configuration list.
   */
  android::KeyedVector<int32_t, hidl_vec<TvStreamConfig>> stream_config_;

  /* The mutex controls the access of shared data. */
  std::mutex mutex_;
};


/* A class for test environment setup. */
class TvInputHidlEnvironment : public ::testing::Environment {
 public:
  virtual void SetUp() {}
  virtual void TearDown() {}

 private:
};

/*
 * GetStreamConfigTest:
 * Calls updateStreamConfigurations() for each existing device
 * Checks returned results
 */
TEST_F(TvInputHidlTest, GetStreamConfigTest) {
  std::unique_lock<std::mutex> lock(mutex_);
  for (size_t i = 0; i < device_info_.size(); i++) {
    int32_t device_id = device_info_.keyAt(i);
    Result result = updateStreamConfigurations(device_id);
    EXPECT_EQ(Result::OK, result);
  }
}

/*
 * OpenAndCloseStreamTest:
 * Calls openStream() and then closeStream() for each existing stream
 * Checks returned results
 */
TEST_F(TvInputHidlTest, OpenAndCloseStreamTest) {
  std::unique_lock<std::mutex> lock(mutex_);
  updateAllStreamConfigurations();
  for (size_t j = 0; j < stream_config_.size(); j++) {
    int32_t device_id = stream_config_.keyAt(j);
    hidl_vec<TvStreamConfig> config = stream_config_.valueAt(j);
    for (size_t i = 0; i < config.size(); i++) {
      Result result = Result::UNKNOWN;
      int32_t stream_id = config[i].streamId;
      tv_input_->openStream(device_id, stream_id,
          [&result](Result res, const native_handle_t*) {
              result = res;
          });
      EXPECT_EQ(Result::OK, result);

      result = Result::UNKNOWN;
      result = tv_input_->closeStream(device_id, stream_id);
      EXPECT_EQ(Result::OK, result);
    }
  }
}

/*
 * InvalidDeviceIdTest:
 * Calls updateStreamConfigurations(), openStream(), and closeStream()
 * for a non-existing device
 * Checks returned results
 * The results should be Result::INVALID_ARGUMENTS
 */
TEST_F(TvInputHidlTest, InvalidDeviceIdTest) {
  std::unique_lock<std::mutex> lock(mutex_);

  std::vector<int32_t> device_ids;
  for (size_t i = 0; i < device_info_.size(); i++) {
    device_ids.push_back(device_info_.keyAt(i));
  }
  // Get a non-existing device ID.
  int32_t id = getNumNotIn(device_ids);
  EXPECT_EQ(Result::INVALID_ARGUMENTS, updateStreamConfigurations(id));

  Result result = Result::UNKNOWN;
  int32_t stream_id = 0;
  tv_input_->openStream(id, stream_id,
      [&result](Result res, const native_handle_t*) {
          result = res;
      });
  EXPECT_EQ(Result::INVALID_ARGUMENTS, result);

  result = Result::UNKNOWN;
  result = tv_input_->closeStream(id, stream_id);
  EXPECT_EQ(Result::INVALID_ARGUMENTS, result);
}

/*
 * InvalidStreamIdTest:
 * Calls openStream(), and closeStream() for a non-existing stream
 * Checks returned results
 * The results should be Result::INVALID_ARGUMENTS
 */
TEST_F(TvInputHidlTest, InvalidStreamIdTest) {
  std::unique_lock<std::mutex> lock(mutex_);
  if (device_info_.isEmpty()) {
    return;
  }
  updateAllStreamConfigurations();

  int32_t device_id = device_info_.keyAt(0);
  // Get a non-existing stream ID.
  int32_t id = DEFAULT_ID;
  if (stream_config_.indexOfKey(device_id) >= 0) {
    std::vector<int32_t> stream_ids;
    hidl_vec<TvStreamConfig> config = stream_config_.valueFor(device_id);
    for (size_t i = 0; i < config.size(); i++) {
      stream_ids.push_back(config[i].streamId);
    }
    id = getNumNotIn(stream_ids);
  }

  Result result = Result::UNKNOWN;
  tv_input_->openStream(device_id, id,
      [&result](Result res, const native_handle_t*) {
          result = res;
      });
  EXPECT_EQ(Result::INVALID_ARGUMENTS, result);

  result = Result::UNKNOWN;
  result = tv_input_->closeStream(device_id, id);
  EXPECT_EQ(Result::INVALID_ARGUMENTS, result);
}

/*
 * OpenAnOpenedStreamsTest:
 * Calls openStream() twice for a stream (if any)
 * Checks returned results
 * The result of the second call should be Result::INVALID_STATE
 */
TEST_F(TvInputHidlTest, OpenAnOpenedStreamsTest) {
  std::unique_lock<std::mutex> lock(mutex_);
  updateAllStreamConfigurations();
  std::vector<size_t> indices = getConfigIndices();
  if (indices.empty()) {
    return;
  }
  int32_t device_id = stream_config_.keyAt(indices[0]);
  int32_t stream_id = stream_config_.valueAt(indices[0])[0].streamId;

  Result result = Result::UNKNOWN;
  tv_input_->openStream(device_id, stream_id,
      [&result](Result res, const native_handle_t*) {
          result = res;
      });
  EXPECT_EQ(Result::OK, result);

  tv_input_->openStream(device_id, stream_id,
      [&result](Result res, const native_handle_t*) {
          result = res;
      });
  EXPECT_EQ(Result::INVALID_STATE, result);
}

/*
 * CloseStreamBeforeOpenTest:
 * Calls closeStream() without calling openStream() for a stream (if any)
 * Checks the returned result
 * The result should be Result::INVALID_STATE
 */
TEST_F(TvInputHidlTest, CloseStreamBeforeOpenTest) {
  std::unique_lock<std::mutex> lock(mutex_);
  updateAllStreamConfigurations();
  std::vector<size_t> indices = getConfigIndices();
  if (indices.empty()) {
    return;
  }
  int32_t device_id = stream_config_.keyAt(indices[0]);
  int32_t stream_id = stream_config_.valueAt(indices[0])[0].streamId;
  EXPECT_EQ(Result::INVALID_STATE, tv_input_->closeStream(device_id, stream_id));
}

int main(int argc, char **argv) {
  ::testing::AddGlobalTestEnvironment(new TvInputHidlEnvironment);
  ::testing::InitGoogleTest(&argc, argv);
  int status = RUN_ALL_TESTS();
  ALOGI("Test result = %d", status);
  return status;
}

