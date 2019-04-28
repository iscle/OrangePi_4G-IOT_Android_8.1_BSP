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

#define LOG_TAG "sensors_hidl_hal_test"
#include <VtsHalHidlTargetTestBase.h>
#include <android-base/logging.h>
#include <android/hardware/sensors/1.0/ISensors.h>
#include <android/hardware/sensors/1.0/types.h>
#include <cutils/ashmem.h>
#include <hardware/sensors.h>  // for sensor type strings
#include <log/log.h>
#include <utils/SystemClock.h>

#include <algorithm>
#include <cinttypes>
#include <cmath>
#include <memory>
#include <mutex>
#include <thread>
#include <unordered_set>
#include <vector>

#include <sys/mman.h>
#include <unistd.h>

using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_string;
using ::android::sp;
using namespace ::android::hardware::sensors::V1_0;

// Test environment for sensors
class SensorsHidlTest;
class SensorsHidlEnvironment : public ::testing::Environment {
 public:
  // get the test environment singleton
  static SensorsHidlEnvironment* Instance() {
    static SensorsHidlEnvironment* instance = new SensorsHidlEnvironment;
    return instance;
  }

  virtual void SetUp();
  virtual void TearDown();

  // Get and clear all events collected so far (like "cat" shell command).
  // If output is nullptr, it clears all collected events.
  void catEvents(std::vector<Event>* output);

  // set sensor event collection status
  void setCollection(bool enable);

 private:
  friend SensorsHidlTest;
  // sensors hidl service
  sp<ISensors> sensors;

  SensorsHidlEnvironment() {}

  void addEvent(const Event& ev);
  void startPollingThread();
  void resetHal();
  static void pollingThread(SensorsHidlEnvironment* env, std::shared_ptr<bool> stop);

  bool collectionEnabled;
  std::shared_ptr<bool> stopThread;
  std::thread pollThread;
  std::vector<Event> events;
  std::mutex events_mutex;

  GTEST_DISALLOW_COPY_AND_ASSIGN_(SensorsHidlEnvironment);
};

void SensorsHidlEnvironment::SetUp() {
  resetHal();

  ASSERT_NE(sensors, nullptr) << "sensors is nullptr, cannot get hidl service";

  collectionEnabled = false;
  startPollingThread();

  // In case framework just stopped for test and there is sensor events in the pipe,
  // wait some time for those events to be cleared to avoid them messing up the test.
  std::this_thread::sleep_for(std::chrono::seconds(3));
}

void SensorsHidlEnvironment::TearDown() {
  if (stopThread) {
    *stopThread = true;
  }
  pollThread.detach();
}

void SensorsHidlEnvironment::resetHal() {
  // wait upto 100ms * 10 = 1s for hidl service.
  constexpr auto RETRY_DELAY = std::chrono::milliseconds(100);

  std::string step;
  bool succeed = false;
  for (size_t retry = 10; retry > 0; --retry) {
    // this do ... while is for easy error handling
    do {
      step = "getService()";
      sensors = ISensors::getService();
      if (sensors == nullptr) {
        break;
      }

      step = "poll() check";
      // Poke ISensor service. If it has lingering connection from previous generation of
      // system server, it will kill itself. There is no intention to handle the poll result,
      // which will be done since the size is 0.
      if(!sensors->poll(0, [](auto, const auto &, const auto &) {}).isOk()) {
        break;
      }

      step = "getSensorList";
      std::vector<SensorInfo> sensorList;
      if (!sensors->getSensorsList(
          [&] (const ::android::hardware::hidl_vec<SensorInfo> &list) {
            sensorList.reserve(list.size());
            for (size_t i = 0; i < list.size(); ++i) {
              sensorList.push_back(list[i]);
            }
          }).isOk()) {
        break;
      }

      // stop each sensor individually
      step = "stop each sensor";
      bool ok = true;
      for (const auto &i : sensorList) {
        if (!sensors->activate(i.sensorHandle, false).isOk()) {
          ok = false;
          break;
        }
      }
      if (!ok) {
        break;
      }

      // mark it done
      step = "done";
      succeed = true;
    } while(0);

    if (succeed) {
      return;
    }

    // Delay 100ms before retry, hidl service is expected to come up in short time after crash.
    ALOGI("%s unsuccessful, try again soon (remaining retry %zu).", step.c_str(), retry - 1);
    std::this_thread::sleep_for(RETRY_DELAY);
  }

  sensors = nullptr;
}

void SensorsHidlEnvironment::catEvents(std::vector<Event>* output) {
  std::lock_guard<std::mutex> lock(events_mutex);
  if (output) {
    output->insert(output->end(), events.begin(), events.end());
  }
  events.clear();
}

void SensorsHidlEnvironment::setCollection(bool enable) {
  std::lock_guard<std::mutex> lock(events_mutex);
  collectionEnabled = enable;
}

void SensorsHidlEnvironment::addEvent(const Event& ev) {
  std::lock_guard<std::mutex> lock(events_mutex);
  if (collectionEnabled) {
    events.push_back(ev);
  }
}

void SensorsHidlEnvironment::startPollingThread() {
  stopThread = std::shared_ptr<bool>(new bool(false));
  pollThread = std::thread(pollingThread, this, stopThread);
  events.reserve(128);
}

void SensorsHidlEnvironment::pollingThread(
    SensorsHidlEnvironment* env, std::shared_ptr<bool> stop) {
  ALOGD("polling thread start");
  bool needExit = *stop;

  while(!needExit) {
      env->sensors->poll(64, [&](auto result, const auto& events, const auto& dynamicSensorsAdded) {
          if (result != Result::OK
              || (events.size() == 0 && dynamicSensorsAdded.size() == 0)
              || *stop) {
              needExit = true;
              return;
          }

          for (const auto& e : events) {
              env->addEvent(e);
          }
      });
  }
  ALOGD("polling thread end");
}

class SensorsTestSharedMemory {
 public:
  static SensorsTestSharedMemory* create(SharedMemType type, size_t size);
  SharedMemInfo getSharedMemInfo() const;
  char * getBuffer() const;
  std::vector<Event> parseEvents(int64_t lastCounter = -1, size_t offset = 0) const;
  virtual ~SensorsTestSharedMemory();
 private:
  SensorsTestSharedMemory(SharedMemType type, size_t size);

  SharedMemType mType;
  native_handle_t* mNativeHandle;
  size_t mSize;
  char* mBuffer;

  DISALLOW_COPY_AND_ASSIGN(SensorsTestSharedMemory);
};

SharedMemInfo SensorsTestSharedMemory::getSharedMemInfo() const {
  SharedMemInfo mem = {
    .type = mType,
    .format = SharedMemFormat::SENSORS_EVENT,
    .size = static_cast<uint32_t>(mSize),
    .memoryHandle = mNativeHandle
  };
  return mem;
}

char * SensorsTestSharedMemory::getBuffer() const {
  return mBuffer;
}

std::vector<Event> SensorsTestSharedMemory::parseEvents(int64_t lastCounter, size_t offset) const {

  constexpr size_t kEventSize = static_cast<size_t>(SensorsEventFormatOffset::TOTAL_LENGTH);
  constexpr size_t kOffsetSize = static_cast<size_t>(SensorsEventFormatOffset::SIZE_FIELD);
  constexpr size_t kOffsetToken = static_cast<size_t>(SensorsEventFormatOffset::REPORT_TOKEN);
  constexpr size_t kOffsetType = static_cast<size_t>(SensorsEventFormatOffset::SENSOR_TYPE);
  constexpr size_t kOffsetAtomicCounter =
      static_cast<size_t>(SensorsEventFormatOffset::ATOMIC_COUNTER);
  constexpr size_t kOffsetTimestamp = static_cast<size_t>(SensorsEventFormatOffset::TIMESTAMP);
  constexpr size_t kOffsetData = static_cast<size_t>(SensorsEventFormatOffset::DATA);

  std::vector<Event> events;
  std::vector<float> data(16);

  while (offset + kEventSize <= mSize) {
    int64_t atomicCounter = *reinterpret_cast<uint32_t *>(mBuffer + offset + kOffsetAtomicCounter);
    if (atomicCounter <= lastCounter) {
      break;
    }

    int32_t size = *reinterpret_cast<int32_t *>(mBuffer + offset + kOffsetSize);
    if (size != kEventSize) {
      // unknown error, events parsed may be wrong, remove all
      events.clear();
      break;
    }

    int32_t token = *reinterpret_cast<int32_t *>(mBuffer + offset + kOffsetToken);
    int32_t type = *reinterpret_cast<int32_t *>(mBuffer + offset + kOffsetType);
    int64_t timestamp = *reinterpret_cast<int64_t *>(mBuffer + offset + kOffsetTimestamp);

    ALOGV("offset = %zu, cnt %" PRId64 ", token %" PRId32 ", type %" PRId32 ", timestamp %" PRId64,
        offset, atomicCounter, token, type, timestamp);

    Event event = {
      .timestamp = timestamp,
      .sensorHandle = token,
      .sensorType = static_cast<SensorType>(type),
    };
    event.u.data = android::hardware::hidl_array<float, 16>
        (reinterpret_cast<float*>(mBuffer + offset + kOffsetData));

    events.push_back(event);

    lastCounter = atomicCounter;
    offset += kEventSize;
  }

  return events;
}

SensorsTestSharedMemory::SensorsTestSharedMemory(SharedMemType type, size_t size)
    : mType(type), mSize(0), mBuffer(nullptr) {
  native_handle_t *handle = nullptr;
  char *buffer = nullptr;
  switch(type) {
    case SharedMemType::ASHMEM: {
      int fd;
      handle = ::native_handle_create(1 /*nFds*/, 0/*nInts*/);
      if (handle != nullptr) {
        handle->data[0] = fd = ::ashmem_create_region("SensorsTestSharedMemory", size);
        if (handle->data[0] > 0) {
          // memory is pinned by default
          buffer = static_cast<char *>
              (::mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0));
          if (buffer != reinterpret_cast<char*>(MAP_FAILED)) {
            break;
          }
          ::native_handle_close(handle);
        }
        ::native_handle_delete(handle);
        handle = nullptr;
      }
      break;
    }
    case SharedMemType::GRALLOC: {

      break;
    }
    default:
      break;
  }

  if (buffer != nullptr) {
    mNativeHandle = handle;
    mSize = size;
    mBuffer = buffer;
  }
}

SensorsTestSharedMemory::~SensorsTestSharedMemory() {
  switch(mType) {
    case SharedMemType::ASHMEM: {
      if (mSize != 0) {
        ::munmap(mBuffer, mSize);
        mBuffer = nullptr;

        ::native_handle_close(mNativeHandle);
        ::native_handle_delete(mNativeHandle);

        mNativeHandle = nullptr;
        mSize = 0;
      }
      break;
    }
    default: {
      if (mNativeHandle != nullptr || mSize != 0 || mBuffer != nullptr) {
        ALOGE("SensorsTestSharedMemory %p not properly destructed: "
            "type %d, native handle %p, size %zu, buffer %p",
            this, static_cast<int>(mType), mNativeHandle, mSize, mBuffer);
      }
      break;
    }
  }
}

SensorsTestSharedMemory* SensorsTestSharedMemory::create(SharedMemType type, size_t size) {
  constexpr size_t kMaxSize = 128*1024*1024; // sensor test should not need more than 128M
  if (size == 0 || size >= kMaxSize) {
    return nullptr;
  }

  auto m = new SensorsTestSharedMemory(type, size);
  if (m->mSize != size || m->mBuffer == nullptr) {
    delete m;
    m = nullptr;
  }
  return m;
}

class SensorEventsChecker {
 public:
  virtual bool check(const std::vector<Event> &events, std::string *out) const = 0;
  virtual ~SensorEventsChecker() {}
};

class NullChecker : public SensorEventsChecker {
 public:
  virtual bool check(const std::vector<Event> &, std::string *) const {
    return true;
  }
};

class SensorEventPerEventChecker : public SensorEventsChecker {
 public:
  virtual bool checkEvent(const Event &event, std::string *out) const = 0;
  virtual bool check(const std::vector<Event> &events, std::string *out) const {
    for (const auto &e : events) {
      if (!checkEvent(e, out)) {
        return false;
      }
    }
    return true;
  }
};

class Vec3NormChecker : public SensorEventPerEventChecker {
 public:
  Vec3NormChecker(float min, float max) : mRange(min, max) {}
  static Vec3NormChecker byNominal(float nominal, float allowedError) {
    return Vec3NormChecker(nominal - allowedError, nominal + allowedError);
  }

  virtual bool checkEvent(const Event &event, std::string *out) const {
    Vec3 v = event.u.vec3;
    float norm = std::sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
    if (norm < mRange.first || norm > mRange.second) {
      if (out != nullptr) {
        std::ostringstream ss;
        ss << "Event @ " << event.timestamp << " (" << v.x << ", " << v.y << ", " << v.z << ")"
           << " has norm " << norm << ", which is beyond range"
           << " [" << mRange.first << ", " << mRange.second << "]";
        *out = ss.str();
      }
      return false;
    }
    return true;
  }
 protected:
  std::pair<float, float> mRange;
};

// The main test class for SENSORS HIDL HAL.
class SensorsHidlTest : public ::testing::VtsHalHidlTargetTestBase {
 public:
  virtual void SetUp() override {
  }

  virtual void TearDown() override {
    // stop all sensors
    for (auto s : mSensorHandles) {
      S()->activate(s, false);
    }
    mSensorHandles.clear();

    // stop all direct report and channels
    for (auto c : mDirectChannelHandles) {
      // disable all reports
      S()->configDirectReport(-1, c, RateLevel::STOP, [] (auto, auto){});
      S()->unregisterDirectChannel(c);
    }
    mDirectChannelHandles.clear();
  }

 protected:
  SensorInfo defaultSensorByType(SensorType type);
  std::vector<SensorInfo> getSensorsList();
  std::vector<Event> collectEvents(useconds_t timeLimitUs, size_t nEventLimit,
        bool clearBeforeStart = true, bool changeCollection = true);

  // implementation wrapper
  Return<void> getSensorsList(ISensors::getSensorsList_cb _hidl_cb) {
    return S()->getSensorsList(_hidl_cb);
  }

  Return<Result> activate(
          int32_t sensorHandle, bool enabled);

  Return<Result> batch(
          int32_t sensorHandle,
          int64_t samplingPeriodNs,
          int64_t maxReportLatencyNs) {
    return S()->batch(sensorHandle, samplingPeriodNs, maxReportLatencyNs);
  }

  Return<Result> flush(int32_t sensorHandle) {
    return S()->flush(sensorHandle);
  }

  Return<Result> injectSensorData(const Event& event) {
    return S()->injectSensorData(event);
  }

  Return<void> registerDirectChannel(
          const SharedMemInfo& mem, ISensors::registerDirectChannel_cb _hidl_cb);

  Return<Result> unregisterDirectChannel(int32_t channelHandle) {
    return S()->unregisterDirectChannel(channelHandle);
  }

  Return<void> configDirectReport(
          int32_t sensorHandle, int32_t channelHandle, RateLevel rate,
          ISensors::configDirectReport_cb _hidl_cb) {
    return S()->configDirectReport(sensorHandle, channelHandle, rate, _hidl_cb);
  }

  inline sp<ISensors>& S() {
    return SensorsHidlEnvironment::Instance()->sensors;
  }

  inline static SensorFlagBits extractReportMode(uint64_t flag) {
    return (SensorFlagBits) (flag
        & ((uint64_t) SensorFlagBits::CONTINUOUS_MODE
          | (uint64_t) SensorFlagBits::ON_CHANGE_MODE
          | (uint64_t) SensorFlagBits::ONE_SHOT_MODE
          | (uint64_t) SensorFlagBits::SPECIAL_REPORTING_MODE));
  }

  inline static bool isMetaSensorType(SensorType type) {
    return (type == SensorType::META_DATA
            || type == SensorType::DYNAMIC_SENSOR_META
            || type == SensorType::ADDITIONAL_INFO);
  }

  inline static bool isValidType(SensorType type) {
    return (int32_t) type > 0;
  }

  void testStreamingOperation(SensorType type,
                              std::chrono::nanoseconds samplingPeriod,
                              std::chrono::seconds duration,
                              const SensorEventsChecker &checker);
  void testSamplingRateHotSwitchOperation(SensorType type);
  void testBatchingOperation(SensorType type);
  void testDirectReportOperation(
      SensorType type, SharedMemType memType, RateLevel rate, const SensorEventsChecker &checker);

  static void assertTypeMatchStringType(SensorType type, const hidl_string& stringType);
  static void assertTypeMatchReportMode(SensorType type, SensorFlagBits reportMode);
  static void assertDelayMatchReportMode(
          int32_t minDelay, int32_t maxDelay, SensorFlagBits reportMode);
  static SensorFlagBits expectedReportModeForType(SensorType type);
  static bool isDirectReportRateSupported(SensorInfo sensor, RateLevel rate);
  static bool isDirectChannelTypeSupported(SensorInfo sensor, SharedMemType type);

  // checkers
  static const Vec3NormChecker sAccelNormChecker;
  static const Vec3NormChecker sGyroNormChecker;

  // all sensors and direct channnels used
  std::unordered_set<int32_t> mSensorHandles;
  std::unordered_set<int32_t> mDirectChannelHandles;
};

const Vec3NormChecker SensorsHidlTest::sAccelNormChecker(
        Vec3NormChecker::byNominal(GRAVITY_EARTH, 1.0f/*m/s^2*/));
const Vec3NormChecker SensorsHidlTest::sGyroNormChecker(
        Vec3NormChecker::byNominal(0.f, 0.1f/*rad/s*/));

Return<Result> SensorsHidlTest::activate(int32_t sensorHandle, bool enabled) {
  // If activating a sensor, add the handle in a set so that when test fails it can be turned off.
  // The handle is not removed when it is deactivating on purpose so that it is not necessary to
  // check the return value of deactivation. Deactivating a sensor more than once does not have
  // negative effect.
  if (enabled) {
    mSensorHandles.insert(sensorHandle);
  }
  return S()->activate(sensorHandle, enabled);
}

Return<void> SensorsHidlTest::registerDirectChannel(
    const SharedMemInfo& mem, ISensors::registerDirectChannel_cb cb) {
  // If registeration of a channel succeeds, add the handle of channel to a set so that it can be
  // unregistered when test fails. Unregister a channel does not remove the handle on purpose.
  // Unregistering a channel more than once should not have negative effect.
  S()->registerDirectChannel(mem,
      [&] (auto result, auto channelHandle) {
        if (result == Result::OK) {
          mDirectChannelHandles.insert(channelHandle);
        }
        cb(result, channelHandle);
      });
  return Void();
}

std::vector<Event> SensorsHidlTest::collectEvents(useconds_t timeLimitUs, size_t nEventLimit,
      bool clearBeforeStart, bool changeCollection) {
  std::vector<Event> events;
  constexpr useconds_t SLEEP_GRANULARITY = 100*1000; //granularity 100 ms

  ALOGI("collect max of %zu events for %d us, clearBeforeStart %d",
        nEventLimit, timeLimitUs, clearBeforeStart);

  if (changeCollection) {
    SensorsHidlEnvironment::Instance()->setCollection(true);
  }
  if (clearBeforeStart) {
    SensorsHidlEnvironment::Instance()->catEvents(nullptr);
  }

  while (timeLimitUs > 0) {
    useconds_t duration = std::min(SLEEP_GRANULARITY, timeLimitUs);
    usleep(duration);
    timeLimitUs -= duration;

    SensorsHidlEnvironment::Instance()->catEvents(&events);
    if (events.size() >= nEventLimit) {
      break;
    }
    ALOGV("time to go = %d, events to go = %d",
          (int)timeLimitUs, (int)(nEventLimit - events.size()));
  }

  if (changeCollection) {
    SensorsHidlEnvironment::Instance()->setCollection(false);
  }
  return events;
}

void SensorsHidlTest::assertTypeMatchStringType(SensorType type, const hidl_string& stringType) {

  if (type >= SensorType::DEVICE_PRIVATE_BASE) {
    return;
  }

  switch (type) {
#define CHECK_TYPE_STRING_FOR_SENSOR_TYPE(type) \
    case SensorType::type: ASSERT_STREQ(SENSOR_STRING_TYPE_ ## type, stringType.c_str()); break;
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(ACCELEROMETER);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(ACCELEROMETER_UNCALIBRATED);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(ADDITIONAL_INFO);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(AMBIENT_TEMPERATURE);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(DEVICE_ORIENTATION);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(DYNAMIC_SENSOR_META);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(GAME_ROTATION_VECTOR);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(GEOMAGNETIC_ROTATION_VECTOR);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(GLANCE_GESTURE);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(GRAVITY);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(GYROSCOPE);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(GYROSCOPE_UNCALIBRATED);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(HEART_BEAT);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(HEART_RATE);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(LIGHT);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(LINEAR_ACCELERATION);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(LOW_LATENCY_OFFBODY_DETECT);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(MAGNETIC_FIELD);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(MAGNETIC_FIELD_UNCALIBRATED);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(MOTION_DETECT);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(ORIENTATION);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(PICK_UP_GESTURE);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(POSE_6DOF);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(PRESSURE);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(PROXIMITY);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(RELATIVE_HUMIDITY);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(ROTATION_VECTOR);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(SIGNIFICANT_MOTION);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(STATIONARY_DETECT);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(STEP_COUNTER);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(STEP_DETECTOR);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(TEMPERATURE);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(TILT_DETECTOR);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(WAKE_GESTURE);
    CHECK_TYPE_STRING_FOR_SENSOR_TYPE(WRIST_TILT_GESTURE);
    default:
      FAIL() << "Type " << static_cast<int>(type) << " in android defined range is not checked, "
             << "stringType = " << stringType;
#undef CHECK_TYPE_STRING_FOR_SENSOR_TYPE
  }
}

void SensorsHidlTest::assertTypeMatchReportMode(SensorType type, SensorFlagBits reportMode) {
  if (type >= SensorType::DEVICE_PRIVATE_BASE) {
    return;
  }

  SensorFlagBits expected = expectedReportModeForType(type);

  ASSERT_TRUE(expected == (SensorFlagBits) -1 || expected == reportMode)
      << "reportMode=" << static_cast<int>(reportMode)
      << "expected=" << static_cast<int>(expected);
}

void SensorsHidlTest::assertDelayMatchReportMode(
    int32_t minDelay, int32_t maxDelay, SensorFlagBits reportMode) {
  switch(reportMode) {
    case SensorFlagBits::CONTINUOUS_MODE:
      ASSERT_LT(0, minDelay);
      ASSERT_LE(0, maxDelay);
      break;
    case SensorFlagBits::ON_CHANGE_MODE:
      ASSERT_LE(0, minDelay);
      ASSERT_LE(0, maxDelay);
      break;
    case SensorFlagBits::ONE_SHOT_MODE:
      ASSERT_EQ(-1, minDelay);
      ASSERT_EQ(0, maxDelay);
      break;
    case SensorFlagBits::SPECIAL_REPORTING_MODE:
      // do not enforce anything for special reporting mode
      break;
    default:
      FAIL() << "Report mode " << static_cast<int>(reportMode) << " not checked";
  }
}

// return -1 means no expectation for this type
SensorFlagBits SensorsHidlTest::expectedReportModeForType(SensorType type) {
  switch (type) {
    case SensorType::ACCELEROMETER:
    case SensorType::ACCELEROMETER_UNCALIBRATED:
    case SensorType::GYROSCOPE:
    case SensorType::MAGNETIC_FIELD:
    case SensorType::ORIENTATION:
    case SensorType::PRESSURE:
    case SensorType::TEMPERATURE:
    case SensorType::GRAVITY:
    case SensorType::LINEAR_ACCELERATION:
    case SensorType::ROTATION_VECTOR:
    case SensorType::MAGNETIC_FIELD_UNCALIBRATED:
    case SensorType::GAME_ROTATION_VECTOR:
    case SensorType::GYROSCOPE_UNCALIBRATED:
    case SensorType::GEOMAGNETIC_ROTATION_VECTOR:
    case SensorType::POSE_6DOF:
    case SensorType::HEART_BEAT:
      return SensorFlagBits::CONTINUOUS_MODE;

    case SensorType::LIGHT:
    case SensorType::PROXIMITY:
    case SensorType::RELATIVE_HUMIDITY:
    case SensorType::AMBIENT_TEMPERATURE:
    case SensorType::HEART_RATE:
    case SensorType::DEVICE_ORIENTATION:
    case SensorType::STEP_COUNTER:
    case SensorType::LOW_LATENCY_OFFBODY_DETECT:
      return SensorFlagBits::ON_CHANGE_MODE;

    case SensorType::SIGNIFICANT_MOTION:
    case SensorType::WAKE_GESTURE:
    case SensorType::GLANCE_GESTURE:
    case SensorType::PICK_UP_GESTURE:
    case SensorType::MOTION_DETECT:
    case SensorType::STATIONARY_DETECT:
      return SensorFlagBits::ONE_SHOT_MODE;

    case SensorType::STEP_DETECTOR:
    case SensorType::TILT_DETECTOR:
    case SensorType::WRIST_TILT_GESTURE:
    case SensorType::DYNAMIC_SENSOR_META:
      return SensorFlagBits::SPECIAL_REPORTING_MODE;

    default:
      ALOGW("Type %d is not implemented in expectedReportModeForType", (int)type);
      return (SensorFlagBits)-1;
  }
}

bool SensorsHidlTest::isDirectReportRateSupported(SensorInfo sensor, RateLevel rate) {
  unsigned int r =
      static_cast<unsigned int>(sensor.flags & SensorFlagBits::MASK_DIRECT_REPORT)
        >> static_cast<unsigned int>(SensorFlagShift::DIRECT_REPORT);
  return r >= static_cast<unsigned int>(rate);
}

bool SensorsHidlTest::isDirectChannelTypeSupported(SensorInfo sensor, SharedMemType type) {
  switch (type) {
    case SharedMemType::ASHMEM:
      return (sensor.flags & SensorFlagBits::DIRECT_CHANNEL_ASHMEM) != 0;
    case SharedMemType::GRALLOC:
      return (sensor.flags & SensorFlagBits::DIRECT_CHANNEL_GRALLOC) != 0;
    default:
      return false;
  }
}

SensorInfo SensorsHidlTest::defaultSensorByType(SensorType type) {
  SensorInfo ret;

  ret.type = (SensorType) -1;
  S()->getSensorsList(
      [&] (const auto &list) {
        const size_t count = list.size();
        for (size_t i = 0; i < count; ++i) {
          if (list[i].type == type) {
            ret = list[i];
            return;
          }
        }
      });

  return ret;
}

std::vector<SensorInfo> SensorsHidlTest::getSensorsList() {
  std::vector<SensorInfo> ret;

  S()->getSensorsList(
      [&] (const auto &list) {
        const size_t count = list.size();
        ret.reserve(list.size());
        for (size_t i = 0; i < count; ++i) {
          ret.push_back(list[i]);
        }
      });

  return ret;
}

// Test if sensor list returned is valid
TEST_F(SensorsHidlTest, SensorListValid) {
  S()->getSensorsList(
      [&] (const auto &list) {
        const size_t count = list.size();
        for (size_t i = 0; i < count; ++i) {
          const auto &s = list[i];
          SCOPED_TRACE(::testing::Message() << i << "/" << count << ": "
                       << " handle=0x" << std::hex << std::setw(8) << std::setfill('0')
                       << s.sensorHandle << std::dec
                       << " type=" << static_cast<int>(s.type)
                       << " name=" << s.name);

          // Test non-empty type string
          EXPECT_FALSE(s.typeAsString.empty());

          // Test defined type matches defined string type
          EXPECT_NO_FATAL_FAILURE(assertTypeMatchStringType(s.type, s.typeAsString));

          // Test if all sensor has name and vendor
          EXPECT_FALSE(s.name.empty());
          EXPECT_FALSE(s.vendor.empty());

          // Test power > 0, maxRange > 0
          EXPECT_LE(0, s.power);
          EXPECT_LT(0, s.maxRange);

          // Info type, should have no sensor
          EXPECT_FALSE(
              s.type == SensorType::ADDITIONAL_INFO
              || s.type == SensorType::META_DATA);

          // Test fifoMax >= fifoReserved
          EXPECT_GE(s.fifoMaxEventCount, s.fifoReservedEventCount)
              << "max=" << s.fifoMaxEventCount << " reserved=" << s.fifoReservedEventCount;

          // Test Reporting mode valid
          EXPECT_NO_FATAL_FAILURE(assertTypeMatchReportMode(s.type, extractReportMode(s.flags)));

          // Test min max are in the right order
          EXPECT_LE(s.minDelay, s.maxDelay);
          // Test min/max delay matches reporting mode
          EXPECT_NO_FATAL_FAILURE(
              assertDelayMatchReportMode(s.minDelay, s.maxDelay, extractReportMode(s.flags)));
        }
      });
}

// Test if sensor list returned is valid
TEST_F(SensorsHidlTest, SetOperationMode) {
    std::vector<SensorInfo> sensorList = getSensorsList();

    bool needOperationModeSupport =
        std::any_of(sensorList.begin(), sensorList.end(),
                    [] (const auto& s) {
                      return (s.flags & SensorFlagBits::DATA_INJECTION) != 0;
                    });
    if (!needOperationModeSupport) {
      return;
    }

    ASSERT_EQ(Result::OK, S()->setOperationMode(OperationMode::NORMAL));
    ASSERT_EQ(Result::OK, S()->setOperationMode(OperationMode::DATA_INJECTION));
    ASSERT_EQ(Result::OK, S()->setOperationMode(OperationMode::NORMAL));
}

// Test if sensor list returned is valid
TEST_F(SensorsHidlTest, InjectSensorEventData) {
    std::vector<SensorInfo> sensorList = getSensorsList();
    std::vector<SensorInfo> sensorSupportInjection;

    bool needOperationModeSupport =
        std::any_of(sensorList.begin(), sensorList.end(),
                    [&sensorSupportInjection] (const auto& s) {
                      bool ret = (s.flags & SensorFlagBits::DATA_INJECTION) != 0;
                      if (ret) {
                        sensorSupportInjection.push_back(s);
                      }
                      return ret;
                    });
    if (!needOperationModeSupport) {
      return;
    }

    ASSERT_EQ(Result::OK, S()->setOperationMode(OperationMode::NORMAL));
    ASSERT_EQ(Result::OK, S()->setOperationMode(OperationMode::DATA_INJECTION));

    for (const auto &s : sensorSupportInjection) {
      switch (s.type) {
        case SensorType::ACCELEROMETER:
        case SensorType::GYROSCOPE:
        case SensorType::MAGNETIC_FIELD: {
          usleep(100000); // sleep 100ms

          Event dummy;
          dummy.timestamp = android::elapsedRealtimeNano();
          dummy.sensorType = s.type;
          dummy.sensorHandle = s.sensorHandle;
          Vec3 v = {1, 2, 3, SensorStatus::ACCURACY_HIGH};
          dummy.u.vec3 = v;

          EXPECT_EQ(Result::OK, S()->injectSensorData(dummy));
          break;
        }
        default:
          break;
      }
    }
    ASSERT_EQ(Result::OK, S()->setOperationMode(OperationMode::NORMAL));
}

void SensorsHidlTest::testStreamingOperation(SensorType type,
                                             std::chrono::nanoseconds samplingPeriod,
                                             std::chrono::seconds duration,
                                             const SensorEventsChecker &checker) {
  std::vector<Event> events;

  const int64_t samplingPeriodInNs = samplingPeriod.count();
  const int64_t batchingPeriodInNs = 0; // no batching
  const useconds_t minTimeUs = std::chrono::microseconds(duration).count();
  const size_t minNEvent = duration / samplingPeriod;

  SensorInfo sensor = defaultSensorByType(type);

  if (!isValidType(sensor.type)) {
    // no default sensor of this type
    return;
  }

  if (std::chrono::microseconds(sensor.minDelay) > samplingPeriod) {
    // rate not supported
    return;
  }

  int32_t handle = sensor.sensorHandle;

  ASSERT_EQ(batch(handle, samplingPeriodInNs, batchingPeriodInNs), Result::OK);
  ASSERT_EQ(activate(handle, 1), Result::OK);
  events = collectEvents(minTimeUs, minNEvent, true /*clearBeforeStart*/);
  ASSERT_EQ(activate(handle, 0), Result::OK);

  ALOGI("Collected %zu samples", events.size());

  ASSERT_GT(events.size(), 0u);

  size_t nRealEvent = 0;
  bool handleMismatchReported = false;
  bool metaSensorTypeErrorReported = false;
  for (auto & e : events) {
    if (e.sensorType == type) {
      // avoid generating hundreds of error
      if (!handleMismatchReported) {
        EXPECT_EQ(e.sensorHandle, handle)
            << (handleMismatchReported = true,
                "Event of the same type must come from the sensor registered");
      }
      ++ nRealEvent;
    } else {
      // avoid generating hundreds of error
      if (!metaSensorTypeErrorReported) {
        EXPECT_TRUE(isMetaSensorType(e.sensorType))
            << (metaSensorTypeErrorReported = true,
                "Only meta types are allowed besides the type registered");
      }
    }
  }

  std::string s;
  EXPECT_TRUE(checker.check(events, &s)) << s;

  EXPECT_GE(nRealEvent, minNEvent / 2); // make sure returned events are not all meta
}

// Test if sensor hal can do UI speed accelerometer streaming properly
TEST_F(SensorsHidlTest, AccelerometerStreamingOperationSlow) {
  testStreamingOperation(SensorType::ACCELEROMETER,
                         std::chrono::milliseconds(200),
                         std::chrono::seconds(5),
                         sAccelNormChecker);
}

// Test if sensor hal can do normal speed accelerometer streaming properly
TEST_F(SensorsHidlTest, AccelerometerStreamingOperationNormal) {
  testStreamingOperation(SensorType::ACCELEROMETER,
                         std::chrono::milliseconds(20),
                         std::chrono::seconds(5),
                         sAccelNormChecker);
}

// Test if sensor hal can do game speed accelerometer streaming properly
TEST_F(SensorsHidlTest, AccelerometerStreamingOperationFast) {
  testStreamingOperation(SensorType::ACCELEROMETER,
                         std::chrono::milliseconds(5),
                         std::chrono::seconds(5),
                         sAccelNormChecker);
}

// Test if sensor hal can do UI speed gyroscope streaming properly
TEST_F(SensorsHidlTest, GyroscopeStreamingOperationSlow) {
  testStreamingOperation(SensorType::GYROSCOPE,
                         std::chrono::milliseconds(200),
                         std::chrono::seconds(5),
                         sGyroNormChecker);
}

// Test if sensor hal can do normal speed gyroscope streaming properly
TEST_F(SensorsHidlTest, GyroscopeStreamingOperationNormal) {
  testStreamingOperation(SensorType::GYROSCOPE,
                         std::chrono::milliseconds(20),
                         std::chrono::seconds(5),
                         sGyroNormChecker);
}

// Test if sensor hal can do game speed gyroscope streaming properly
TEST_F(SensorsHidlTest, GyroscopeStreamingOperationFast) {
  testStreamingOperation(SensorType::GYROSCOPE,
                         std::chrono::milliseconds(5),
                         std::chrono::seconds(5),
                         sGyroNormChecker);
}

// Test if sensor hal can do UI speed magnetometer streaming properly
TEST_F(SensorsHidlTest, MagnetometerStreamingOperationSlow) {
  testStreamingOperation(SensorType::MAGNETIC_FIELD,
                         std::chrono::milliseconds(200),
                         std::chrono::seconds(5),
                         NullChecker());
}

// Test if sensor hal can do normal speed magnetometer streaming properly
TEST_F(SensorsHidlTest, MagnetometerStreamingOperationNormal) {
  testStreamingOperation(SensorType::MAGNETIC_FIELD,
                         std::chrono::milliseconds(20),
                         std::chrono::seconds(5),
                         NullChecker());
}

// Test if sensor hal can do game speed magnetometer streaming properly
TEST_F(SensorsHidlTest, MagnetometerStreamingOperationFast) {
  testStreamingOperation(SensorType::MAGNETIC_FIELD,
                         std::chrono::milliseconds(5),
                         std::chrono::seconds(5),
                         NullChecker());
}

void SensorsHidlTest::testSamplingRateHotSwitchOperation(SensorType type) {
  std::vector<Event> events1, events2;

  constexpr int64_t batchingPeriodInNs = 0; // no batching
  constexpr size_t minNEvent = 50;

  SensorInfo sensor = defaultSensorByType(type);

  if (!isValidType(sensor.type)) {
    // no default sensor of this type
    return;
  }

  int32_t handle = sensor.sensorHandle;
  int64_t minSamplingPeriodInNs = sensor.minDelay * 1000ll;
  int64_t maxSamplingPeriodInNs = sensor.maxDelay * 1000ll;

  if (minSamplingPeriodInNs == maxSamplingPeriodInNs) {
    // only support single rate
    return;
  }

  ASSERT_EQ(batch(handle, minSamplingPeriodInNs, batchingPeriodInNs), Result::OK);
  ASSERT_EQ(activate(handle, 1), Result::OK);

  usleep(500000); // sleep 0.5 sec to wait for change rate to happen
  events1 = collectEvents(sensor.minDelay * minNEvent, minNEvent, true /*clearBeforeStart*/);

  ASSERT_EQ(batch(handle, maxSamplingPeriodInNs, batchingPeriodInNs), Result::OK);

  usleep(500000); // sleep 0.5 sec to wait for change rate to happen
  events2 = collectEvents(sensor.maxDelay * minNEvent, minNEvent, true /*clearBeforeStart*/);

  ASSERT_EQ(activate(handle, 0), Result::OK);

  ALOGI("Collected %zu fast samples and %zu slow samples", events1.size(), events2.size());

  ASSERT_GT(events1.size(), 0u);
  ASSERT_GT(events2.size(), 0u);

  int64_t minDelayAverageInterval, maxDelayAverageInterval;

  size_t nEvent = 0;
  int64_t prevTimestamp = -1;
  int64_t timestampInterval = 0;
  for (auto & e : events1) {
    if (e.sensorType == type) {
      ASSERT_EQ(e.sensorHandle, handle);
      if (prevTimestamp > 0) {
        timestampInterval += e.timestamp - prevTimestamp;
      }
      prevTimestamp = e.timestamp;
      ++ nEvent;
    }
  }
  ASSERT_GT(nEvent, 2u);
  minDelayAverageInterval = timestampInterval / (nEvent - 1);

  nEvent = 0;
  prevTimestamp = -1;
  timestampInterval = 0;
  for (auto & e : events2) {
    if (e.sensorType == type) {
      ASSERT_EQ(e.sensorHandle, handle);
      if (prevTimestamp > 0) {
        timestampInterval += e.timestamp - prevTimestamp;
      }
      prevTimestamp = e.timestamp;
      ++ nEvent;
    }
  }
  ASSERT_GT(nEvent, 2u);
  maxDelayAverageInterval = timestampInterval / (nEvent - 1);

  // change of rate is significant.
  EXPECT_GT((maxDelayAverageInterval - minDelayAverageInterval), minDelayAverageInterval / 10);

  // fastest rate sampling time is close to spec
  ALOGI("minDelayAverageInterval = %" PRId64, minDelayAverageInterval);
  EXPECT_LT(std::abs(minDelayAverageInterval - minSamplingPeriodInNs),
      minSamplingPeriodInNs / 10);
}

// Test if sensor hal can do accelerometer sampling rate switch properly when sensor is active
TEST_F(SensorsHidlTest, AccelerometerSamplingPeriodHotSwitchOperation) {
  testSamplingRateHotSwitchOperation(SensorType::ACCELEROMETER);
}

// Test if sensor hal can do gyroscope sampling rate switch properly when sensor is active
TEST_F(SensorsHidlTest, GyroscopeSamplingPeriodHotSwitchOperation) {
  testSamplingRateHotSwitchOperation(SensorType::GYROSCOPE);
}

// Test if sensor hal can do magnetometer sampling rate switch properly when sensor is active
TEST_F(SensorsHidlTest, MagnetometerSamplingPeriodHotSwitchOperation) {
  testSamplingRateHotSwitchOperation(SensorType::MAGNETIC_FIELD);
}

void SensorsHidlTest::testBatchingOperation(SensorType type) {
  std::vector<Event> events;

  constexpr int64_t maxBatchingTestTimeNs = 30ull * 1000 * 1000 * 1000;
  constexpr int64_t oneSecondInNs = 1ull * 1000 * 1000 * 1000;

  SensorInfo sensor = defaultSensorByType(type);

  if (!isValidType(sensor.type)) {
    // no default sensor of this type
    return;
  }

  int32_t handle = sensor.sensorHandle;
  int64_t minSamplingPeriodInNs = sensor.minDelay * 1000ll;
  uint32_t minFifoCount = sensor.fifoReservedEventCount;
  int64_t batchingPeriodInNs = minFifoCount * minSamplingPeriodInNs;

  if (batchingPeriodInNs < oneSecondInNs) {
    // batching size too small to test reliably
    return;
  }

  batchingPeriodInNs = std::min(batchingPeriodInNs, maxBatchingTestTimeNs);

  ALOGI("Test batching for %d ms", (int)(batchingPeriodInNs / 1000 / 1000));

  int64_t allowedBatchDeliverTimeNs =
      std::max(oneSecondInNs, batchingPeriodInNs / 10);

  ASSERT_EQ(batch(handle, minSamplingPeriodInNs, INT64_MAX), Result::OK);
  ASSERT_EQ(activate(handle, 1), Result::OK);

  usleep(500000); // sleep 0.5 sec to wait for initialization
  ASSERT_EQ(flush(handle), Result::OK);

  // wait for 80% of the reserved batching period
  // there should not be any significant amount of events
  // since collection is not enabled all events will go down the drain
  usleep(batchingPeriodInNs / 1000 * 8 / 10);

  SensorsHidlEnvironment::Instance()->setCollection(true);
  // clean existing collections
  collectEvents(0 /*timeLimitUs*/, 0/*nEventLimit*/,
        true /*clearBeforeStart*/, false /*change collection*/);

  // 0.8 + 0.2 times the batching period
  usleep(batchingPeriodInNs / 1000 * 8 / 10);
  ASSERT_EQ(flush(handle), Result::OK);

  // plus some time for the event to deliver
  events = collectEvents(allowedBatchDeliverTimeNs / 1000,
        minFifoCount, false /*clearBeforeStart*/, false /*change collection*/);

  SensorsHidlEnvironment::Instance()->setCollection(false);
  ASSERT_EQ(activate(handle, 0), Result::OK);

  size_t nEvent = 0;
  for (auto & e : events) {
    if (e.sensorType == type && e.sensorHandle == handle) {
      ++ nEvent;
    }
  }

  // at least reach 90% of advertised capacity
  ASSERT_GT(nEvent, (size_t)(minFifoCount * 9 / 10));
}

// Test if sensor hal can do accelerometer batching properly
TEST_F(SensorsHidlTest, AccelerometerBatchingOperation) {
  testBatchingOperation(SensorType::ACCELEROMETER);
}

// Test if sensor hal can do gyroscope batching properly
TEST_F(SensorsHidlTest, GyroscopeBatchingOperation) {
  testBatchingOperation(SensorType::GYROSCOPE);
}

// Test if sensor hal can do magnetometer batching properly
TEST_F(SensorsHidlTest, MagnetometerBatchingOperation) {
  testBatchingOperation(SensorType::MAGNETIC_FIELD);
}

void SensorsHidlTest::testDirectReportOperation(
    SensorType type, SharedMemType memType, RateLevel rate, const SensorEventsChecker &checker) {
  constexpr size_t kEventSize = static_cast<size_t>(SensorsEventFormatOffset::TOTAL_LENGTH);
  constexpr size_t kNEvent = 4096;
  constexpr size_t kMemSize = kEventSize * kNEvent;

  constexpr float kNormalNominal = 50;
  constexpr float kFastNominal = 200;
  constexpr float kVeryFastNominal = 800;

  constexpr float kNominalTestTimeSec = 1.f;
  constexpr float kMaxTestTimeSec = kNominalTestTimeSec + 0.5f; // 0.5 second for initialization

  SensorInfo sensor = defaultSensorByType(type);

  if (!isValidType(sensor.type)) {
    // no default sensor of this type
    return;
  }

  if (!isDirectReportRateSupported(sensor, rate)) {
    return;
  }

  if (!isDirectChannelTypeSupported(sensor, memType)) {
    return;
  }

  std::unique_ptr<SensorsTestSharedMemory>
      mem(SensorsTestSharedMemory::create(memType, kMemSize));
  ASSERT_NE(mem, nullptr);

  char* buffer = mem->getBuffer();
  // fill memory with data
  for (size_t i = 0; i < kMemSize; ++i) {
    buffer[i] = '\xcc';
  }

  int32_t channelHandle;
  registerDirectChannel(mem->getSharedMemInfo(),
      [&channelHandle] (auto result, auto channelHandle_) {
          ASSERT_EQ(result, Result::OK);
          channelHandle = channelHandle_;
      });

  // check memory is zeroed
  for (size_t i = 0; i < kMemSize; ++i) {
    ASSERT_EQ(buffer[i], '\0');
  }

  int32_t eventToken;
  configDirectReport(sensor.sensorHandle, channelHandle, rate,
      [&eventToken] (auto result, auto token) {
          ASSERT_EQ(result, Result::OK);
          eventToken = token;
      });

  usleep(static_cast<useconds_t>(kMaxTestTimeSec * 1e6f));
  auto events = mem->parseEvents();

  // find norminal rate
  float nominalFreq = 0.f;
  switch (rate) {
      case RateLevel::NORMAL:
          nominalFreq = kNormalNominal;
          break;
      case RateLevel::FAST:
          nominalFreq = kFastNominal;
          break;
      case RateLevel::VERY_FAST:
          nominalFreq = kVeryFastNominal;
          break;
      case RateLevel::STOP:
          FAIL();
  }

  // allowed to be between 55% and 220% of nominal freq
  ASSERT_GT(events.size(), static_cast<size_t>(nominalFreq * 0.55f * kNominalTestTimeSec));
  ASSERT_LT(events.size(), static_cast<size_t>(nominalFreq * 2.2f * kMaxTestTimeSec));

  int64_t lastTimestamp = 0;
  bool typeErrorReported = false;
  bool tokenErrorReported = false;
  bool timestampErrorReported = false;
  for (auto &e : events) {
    if (!typeErrorReported) {
      EXPECT_EQ(type, e.sensorType)
          << (typeErrorReported = true, "Type in event does not match type of sensor registered.");
    }
    if (!tokenErrorReported) {
      EXPECT_EQ(eventToken, e.sensorHandle)
          << (tokenErrorReported = true,
            "Event token does not match that retured from configDirectReport");
    }
    if (!timestampErrorReported) {
      EXPECT_GT(e.timestamp, lastTimestamp)
          << (timestampErrorReported = true, "Timestamp not monotonically increasing");
    }
    lastTimestamp = e.timestamp;
  }

  std::string s;
  EXPECT_TRUE(checker.check(events, &s)) << s;

  // stop sensor and unregister channel
  configDirectReport(sensor.sensorHandle, channelHandle, RateLevel::STOP,
                     [](auto result, auto) { EXPECT_EQ(result, Result::OK); });
  EXPECT_EQ(unregisterDirectChannel(channelHandle), Result::OK);
}

// Test sensor event direct report with ashmem for accel sensor at normal rate
TEST_F(SensorsHidlTest, AccelerometerAshmemDirectReportOperationNormal) {
  testDirectReportOperation(SensorType::ACCELEROMETER, SharedMemType::ASHMEM, RateLevel::NORMAL,
                            sAccelNormChecker);
}

// Test sensor event direct report with ashmem for accel sensor at fast rate
TEST_F(SensorsHidlTest, AccelerometerAshmemDirectReportOperationFast) {
  testDirectReportOperation(SensorType::ACCELEROMETER, SharedMemType::ASHMEM, RateLevel::FAST,
                            sAccelNormChecker);
}

// Test sensor event direct report with ashmem for accel sensor at very fast rate
TEST_F(SensorsHidlTest, AccelerometerAshmemDirectReportOperationVeryFast) {
  testDirectReportOperation(SensorType::ACCELEROMETER, SharedMemType::ASHMEM, RateLevel::VERY_FAST,
                            sAccelNormChecker);
}

// Test sensor event direct report with ashmem for gyro sensor at normal rate
TEST_F(SensorsHidlTest, GyroscopeAshmemDirectReportOperationNormal) {
  testDirectReportOperation(SensorType::GYROSCOPE, SharedMemType::ASHMEM, RateLevel::NORMAL,
                            sGyroNormChecker);
}

// Test sensor event direct report with ashmem for gyro sensor at fast rate
TEST_F(SensorsHidlTest, GyroscopeAshmemDirectReportOperationFast) {
  testDirectReportOperation(SensorType::GYROSCOPE, SharedMemType::ASHMEM, RateLevel::FAST,
                            sGyroNormChecker);
}

// Test sensor event direct report with ashmem for gyro sensor at very fast rate
TEST_F(SensorsHidlTest, GyroscopeAshmemDirectReportOperationVeryFast) {
  testDirectReportOperation(SensorType::GYROSCOPE, SharedMemType::ASHMEM, RateLevel::VERY_FAST,
                            sGyroNormChecker);
}

// Test sensor event direct report with ashmem for mag sensor at normal rate
TEST_F(SensorsHidlTest, MagnetometerAshmemDirectReportOperationNormal) {
  testDirectReportOperation(SensorType::MAGNETIC_FIELD, SharedMemType::ASHMEM, RateLevel::NORMAL,
                            NullChecker());
}

// Test sensor event direct report with ashmem for mag sensor at fast rate
TEST_F(SensorsHidlTest, MagnetometerAshmemDirectReportOperationFast) {
  testDirectReportOperation(SensorType::MAGNETIC_FIELD, SharedMemType::ASHMEM, RateLevel::FAST,
                            NullChecker());
}

// Test sensor event direct report with ashmem for mag sensor at very fast rate
TEST_F(SensorsHidlTest, MagnetometerAshmemDirectReportOperationVeryFast) {
  testDirectReportOperation(
      SensorType::MAGNETIC_FIELD, SharedMemType::ASHMEM, RateLevel::VERY_FAST, NullChecker());
}

int main(int argc, char **argv) {
  ::testing::AddGlobalTestEnvironment(SensorsHidlEnvironment::Instance());
  ::testing::InitGoogleTest(&argc, argv);
  int status = RUN_ALL_TESTS();
  ALOGI("Test result = %d", status);
  return status;
}
// vim: set ts=2 sw=2
