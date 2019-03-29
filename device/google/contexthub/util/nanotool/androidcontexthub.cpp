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

#include "androidcontexthub.h"

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <time.h>
#include <unistd.h>
#include <sys/stat.h>

#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <thread>
#include <vector>

#include "calibrationfile.h"
#include "log.h"

namespace android {

constexpr char kSensorDeviceFile[] = "/dev/nanohub";
constexpr char kCommsDeviceFile[] = "/dev/nanohub_comms";
constexpr char kLockDirectory[] = "/data/vendor/sensor/nanohub_lock";
constexpr char kLockFile[] = "/data/vendor/sensor/nanohub_lock/lock";

constexpr mode_t kLockDirPermissions = (S_IRUSR | S_IWUSR | S_IXUSR);

constexpr auto kLockDelay = std::chrono::milliseconds(100);

constexpr int kDeviceFileCount = 2;
constexpr int kPollNoTimeout = -1;

static const std::vector<std::tuple<const char *, SensorType>> kCalibrationKeys = {
    std::make_tuple("accel",     SensorType::Accel),
    std::make_tuple("gyro",      SensorType::Gyro),
    std::make_tuple("mag",       SensorType::Magnetometer),
    std::make_tuple("proximity", SensorType::Proximity),
    std::make_tuple("barometer", SensorType::Barometer),
    std::make_tuple("light",     SensorType::AmbientLightSensor),
};

static void AppendBytes(const void *data, size_t length, std::vector<uint8_t>& buffer) {
    const uint8_t *bytes = (const uint8_t *) data;
    for (size_t i = 0; i < length; i++) {
        buffer.push_back(bytes[i]);
    }
}

static bool CopyInt32Array(const char *key,
        sp<JSONObject> json, std::vector<uint8_t>& bytes) {
    sp<JSONArray> array;
    if (json->getArray(key, &array)) {
        for (size_t i = 0; i < array->size(); i++) {
            int32_t val = 0;
            array->getInt32(i, &val);
            AppendBytes(&val, sizeof(uint32_t), bytes);
        }

        return true;
    }
    return false;
}

static bool CopyFloatArray(const char *key,
        sp<JSONObject> json, std::vector<uint8_t>& bytes) {
    sp<JSONArray> array;
    if (json->getArray(key, &array)) {
        for (size_t i = 0; i < array->size(); i++) {
            float val = 0;
            array->getFloat(i, &val);
            AppendBytes(&val, sizeof(float), bytes);
        }

        return true;
    }
    return false;
}

static bool GetCalibrationBytes(const char *key, SensorType sensor_type,
        std::vector<uint8_t>& bytes) {
    bool success = true;
    std::shared_ptr<CalibrationFile> cal_file = CalibrationFile::Instance();
    if (!cal_file) {
        return false;
    }
    auto json = cal_file->GetJSONObject();

    switch (sensor_type) {
      case SensorType::Accel:
      case SensorType::Gyro:
        success = CopyInt32Array(key, json, bytes);
        break;

      case SensorType::Magnetometer:
        success = CopyFloatArray(key, json, bytes);
        break;

      case SensorType::AmbientLightSensor:
      case SensorType::Barometer: {
        float value = 0;
        success = json->getFloat(key, &value);
        if (success) {
            AppendBytes(&value, sizeof(float), bytes);
        }
        break;
      }

      case SensorType::Proximity: {
        // Proximity might be an int32 array with 4 values (CRGB) or a single
        // int32 value - try both
        success = CopyInt32Array(key, json, bytes);
        if (!success) {
            int32_t value = 0;
            success = json->getInt32(key, &value);
            if (success) {
                AppendBytes(&value, sizeof(int32_t), bytes);
            }
        }
        break;
      }

      default:
        // If this log message gets printed, code needs to be added in this
        // switch statement
        LOGE("Missing sensor type to calibration data mapping sensor %d",
             static_cast<int>(sensor_type));
        success = false;
    }

    return success;
}

AndroidContextHub::~AndroidContextHub() {
    if (unlink(kLockFile) < 0) {
        LOGE("Couldn't remove lock file: %s", strerror(errno));
    }
    if (sensor_fd_ >= 0) {
        DisableActiveSensors();
        (void) close(sensor_fd_);
    }
    if (comms_fd_ >= 0) {
        (void) close(comms_fd_);
    }
}

void AndroidContextHub::TerminateHandler() {
    (void) unlink(kLockFile);
}

bool AndroidContextHub::Initialize() {
    // Acquire a lock on nanohub, so the HAL read threads won't take our events.
    // We need to delay after creating the file to have good confidence that
    // the HALs noticed the lock file creation.
    if (access(kLockDirectory, F_OK) < 0) {
        if (mkdir(kLockDirectory, kLockDirPermissions) < 0 && errno != EEXIST) {
            LOGE("Couldn't create lock directory: %s", strerror(errno));
        }
    }
    int lock_fd = open(kLockFile, O_CREAT | O_EXCL, S_IRUSR | S_IWUSR);
    if (lock_fd < 0) {
        LOGE("Couldn't create lock file: %s", strerror(errno));
        if (errno != EEXIST) {
            return false;
        }
    } else {
        close(lock_fd);
        std::this_thread::sleep_for(kLockDelay);
        LOGD("Lock sleep complete");
    }

    // Sensor device file is used for sensor requests, e.g. configure, etc., and
    // returns sensor events
    sensor_fd_ = open(kSensorDeviceFile, O_RDWR);
    if (sensor_fd_ < 0) {
        LOGE("Couldn't open device file: %s", strerror(errno));
        return false;
    }

    // The comms device file is used for more generic communication with
    // nanoapps. Calibration results are returned through this channel.
    comms_fd_ = open(kCommsDeviceFile, O_RDONLY);
    if (comms_fd_ < 0) {
        // TODO(bduddie): Currently informational only, as the kernel change
        // that adds this device file is not available/propagated yet.
        // Eventually this should be an error.
        LOGI("Couldn't open comms device file: %s", strerror(errno));
    }

    return true;
}

void AndroidContextHub::SetLoggingEnabled(bool logging_enabled) {
    if (logging_enabled) {
        LOGE("Logging is not supported on this platform");
    }
}

ContextHub::TransportResult AndroidContextHub::WriteEvent(
        const std::vector<uint8_t>& message) {
    ContextHub::TransportResult result;

    LOGD("Writing %zu bytes", message.size());
    LOGD_BUF(message.data(), message.size());
    int ret = write(sensor_fd_, message.data(), message.size());
    if (ret == -1) {
        LOGE("Couldn't write %zu bytes to device file: %s", message.size(),
             strerror(errno));
        result = TransportResult::GeneralFailure;
    } else if (ret != (int) message.size()) {
        LOGW("Write returned %d, expected %zu", ret, message.size());
        result = TransportResult::GeneralFailure;
    } else {
        LOGD("Successfully sent event");
        result = TransportResult::Success;
    }

    return result;
}

ContextHub::TransportResult AndroidContextHub::ReadEvent(
        std::vector<uint8_t>& message, int timeout_ms) {
    ContextHub::TransportResult result = TransportResult::GeneralFailure;

    struct pollfd pollfds[kDeviceFileCount];
    int fd_count = ResetPollFds(pollfds, kDeviceFileCount);

    int timeout = timeout_ms > 0 ? timeout_ms : kPollNoTimeout;
    int ret = poll(pollfds, fd_count, timeout);
    if (ret < 0) {
        LOGE("Polling failed: %s", strerror(errno));
        if (errno == EINTR) {
            result = TransportResult::Canceled;
        }
    } else if (ret == 0) {
        LOGD("Poll timed out");
        result = TransportResult::Timeout;
    } else {
        int read_fd = -1;
        for (int i = 0; i < kDeviceFileCount; i++) {
            if (pollfds[i].revents & POLLIN) {
                read_fd = pollfds[i].fd;
                break;
            }
        }

        if (read_fd == sensor_fd_) {
            LOGD("Data ready on sensors device file");
        } else if (read_fd == comms_fd_) {
            LOGD("Data ready on comms device file");
        }

        if (read_fd >= 0) {
            result = ReadEventFromFd(read_fd, message);
        } else {
            LOGE("Poll returned but none of expected files are ready");
        }
    }

    return result;
}

bool AndroidContextHub::FlashSensorHub(const std::vector<uint8_t>& bytes) {
    (void)bytes;
    LOGE("Flashing is not supported on this platform");
    return false;
}

bool AndroidContextHub::LoadCalibration() {
    std::vector<uint8_t> cal_data;
    bool success = true;

    for (size_t i = 0; success && i < kCalibrationKeys.size(); i++) {
        std::string key;
        SensorType sensor_type;

        std::tie(key, sensor_type) = kCalibrationKeys[i];
        if (GetCalibrationBytes(key.c_str(), sensor_type, cal_data)) {
            success = SendCalibrationData(sensor_type, cal_data);
        }

        cal_data.clear();
    }

    return success;
}

bool AndroidContextHub::SetCalibration(SensorType sensor_type, int32_t data) {
    LOGI("Setting calibration for sensor %d (%s) to %d",
         static_cast<int>(sensor_type),
         ContextHub::SensorTypeToAbbrevName(sensor_type).c_str(), data);
    auto cal_file = CalibrationFile::Instance();
    const char *key = AndroidContextHub::SensorTypeToCalibrationKey(sensor_type);
    if (cal_file && key) {
        return cal_file->SetSingleAxis(key, data);
    }
    return false;
}

bool AndroidContextHub::SetCalibration(SensorType sensor_type, float data) {
    LOGI("Setting calibration for sensor %d (%s) to %f",
         static_cast<int>(sensor_type),
         ContextHub::SensorTypeToAbbrevName(sensor_type).c_str(), data);
    auto cal_file = CalibrationFile::Instance();
    const char *key = AndroidContextHub::SensorTypeToCalibrationKey(sensor_type);
    if (cal_file && key) {
        return cal_file->SetSingleAxis(key, data);
    }
    return false;
}

bool AndroidContextHub::SetCalibration(SensorType sensor_type, int32_t x,
        int32_t y, int32_t z) {
    LOGI("Setting calibration for %d to %d %d %d", static_cast<int>(sensor_type),
         x, y, z);
    auto cal_file = CalibrationFile::Instance();
    const char *key = AndroidContextHub::SensorTypeToCalibrationKey(sensor_type);
    if (cal_file && key) {
        return cal_file->SetTripleAxis(key, x, y, z);
    }
    return false;
}

bool AndroidContextHub::SetCalibration(SensorType sensor_type, int32_t x,
        int32_t y, int32_t z, int32_t w) {
    LOGI("Setting calibration for %d to %d %d %d %d", static_cast<int>(sensor_type),
         x, y, z, w);
    auto cal_file = CalibrationFile::Instance();
    const char *key = AndroidContextHub::SensorTypeToCalibrationKey(sensor_type);
    if (cal_file && key) {
        return cal_file->SetFourAxis(key, x, y, z, w);
    }
    return false;
}

bool AndroidContextHub::SaveCalibration() {
    LOGI("Saving calibration data");
    auto cal_file = CalibrationFile::Instance();
    if (cal_file) {
        return cal_file->Save();
    }
    return false;
}

ContextHub::TransportResult AndroidContextHub::ReadEventFromFd(
        int fd, std::vector<uint8_t>& message) {
    ContextHub::TransportResult result = TransportResult::GeneralFailure;

    // Set the size to the maximum, so when we resize later, it's always a
    // shrink (otherwise it will end up clearing the bytes)
    message.resize(message.capacity());

    LOGD("Calling into read()");
    int ret = read(fd, message.data(), message.capacity());
    if (ret < 0) {
        LOGE("Couldn't read from device file: %s", strerror(errno));
        if (errno == EINTR) {
            result = TransportResult::Canceled;
        }
    } else if (ret == 0) {
        // We might need to handle this specially, if the driver implements this
        // to mean something specific
        LOGE("Read unexpectedly returned 0 bytes");
    } else {
        message.resize(ret);
        LOGD_VEC(message);
        result = TransportResult::Success;
    }

    return result;
}

int AndroidContextHub::ResetPollFds(struct pollfd *pfds, size_t count) {
    memset(pfds, 0, sizeof(struct pollfd) * count);
    pfds[0].fd = sensor_fd_;
    pfds[0].events = POLLIN;

    int nfds = 1;
    if (count > 1 && comms_fd_ >= 0) {
        pfds[1].fd = comms_fd_;
        pfds[1].events = POLLIN;
        nfds++;
    }
    return nfds;
}

const char *AndroidContextHub::SensorTypeToCalibrationKey(SensorType sensor_type) {
    for (size_t i = 0; i < kCalibrationKeys.size(); i++) {
        const char *key;
        SensorType sensor_type_for_key;

        std::tie(key, sensor_type_for_key) = kCalibrationKeys[i];
        if (sensor_type == sensor_type_for_key) {
            return key;
        }
    }

    LOGE("No calibration key mapping for sensor type %d",
         static_cast<int>(sensor_type));
    return nullptr;
}

}  // namespace android
