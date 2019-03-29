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

#include "calibrationfile.h"

#include "file.h"
#include "log.h"

namespace android {

constexpr char kCalibrationFile[] = "/persist/sensorcal.json";

std::shared_ptr<CalibrationFile> CalibrationFile::instance_;

std::shared_ptr<CalibrationFile> CalibrationFile::Instance() {
    if (!CalibrationFile::instance_) {
        auto inst = std::shared_ptr<CalibrationFile>(new CalibrationFile());
        if (inst->Initialize()) {
            CalibrationFile::instance_ = inst;
        }
    }

    return CalibrationFile::instance_;
}

bool CalibrationFile::Initialize() {
    file_ = std::unique_ptr<File>(new File(kCalibrationFile, "rw"));

    status_t err = file_->initCheck();
    if (err != OK) {
        LOGE("Couldn't open calibration file: %d (%s)", err, strerror(-err));
        return false;
    }

    off64_t file_size = file_->seekTo(0, SEEK_END);
    if (file_size > 0) {
        auto file_data = std::vector<char>(file_size);
        file_->seekTo(0, SEEK_SET);
        ssize_t bytes_read = file_->read(file_data.data(), file_size);
        if (bytes_read != file_size) {
            LOGE("Read of configuration file returned %zd, expected %" PRIu64,
                 bytes_read, file_size);
            return false;
        }

        sp<JSONCompound> json = JSONCompound::Parse(file_data.data(), file_size);
        if (json == nullptr || !json->isObject()) {
            // If there's an existing file and we couldn't parse it, or it
            // parsed to something unexpected, then we don't want to wipe out
            // the file - the user needs to decide what to do, e.g. they can
            // manually edit to fix corruption, or delete it, etc.
            LOGE("Couldn't parse sensor calibration file (requires manual "
                 "resolution)");
            return false;
        } else {
            json_root_ = reinterpret_cast<JSONObject*>(json.get());
            LOGD("Parsed JSONObject from file:\n%s",
                 json_root_->toString().c_str());
        }
    }

    // No errors, but there was no existing calibration data so construct a new
    // object
    if (json_root_ == nullptr) {
        json_root_ = new JSONObject();
    }

    return true;
}

const sp<JSONObject> CalibrationFile::GetJSONObject() const {
    return json_root_;
}

bool CalibrationFile::SetSingleAxis(const char *key, int32_t value) {
    json_root_->setInt32(key, value);
    return true;
}

bool CalibrationFile::SetSingleAxis(const char *key, float value) {
    json_root_->setFloat(key, value);
    return true;
}

bool CalibrationFile::SetTripleAxis(const char *key, int32_t x, int32_t y,
        int32_t z) {
    sp<JSONArray> json_array = new JSONArray();
    json_array->addInt32(x);
    json_array->addInt32(y);
    json_array->addInt32(z);
    json_root_->setArray(key, json_array);
    return true;
}

bool CalibrationFile::SetFourAxis(const char *key, int32_t x, int32_t y,
        int32_t z, int32_t w) {
    sp<JSONArray> json_array = new JSONArray();
    json_array->addInt32(x);
    json_array->addInt32(y);
    json_array->addInt32(z);
    json_array->addInt32(w);
    json_root_->setArray(key, json_array);
    return true;
}

bool CalibrationFile::Save() {
    AString json_str = json_root_->toString();
    LOGD("Saving JSONObject to file (%zd bytes):\n%s", json_str.size(),
         json_str.c_str());
    file_->seekTo(0, SEEK_SET);
    ssize_t bytes_written = file_->write(json_str.c_str(), json_str.size());
    if (bytes_written < 0 || static_cast<size_t>(bytes_written) != json_str.size()) {
        LOGE("Write returned %zd, expected %zu", bytes_written, json_str.size());
        return false;
    }
    return true;
}

}  // namespace android
