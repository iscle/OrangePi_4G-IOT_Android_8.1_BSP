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

#ifndef CALIBRATION_FILE_H_
#define CALIBRATION_FILE_H_

#include <inttypes.h>
#include <unistd.h>

#include <memory>

#include "file.h"
#include "noncopyable.h"
#include "JSONObject.h"
#include <utils/RefBase.h>

namespace android {

class CalibrationFile : public NonCopyable {
  public:
    // Get a pointer to the singleton instance
    static std::shared_ptr<CalibrationFile> Instance();

    const sp<JSONObject> GetJSONObject() const;

    bool SetSingleAxis(const char *key, int32_t value);
    bool SetSingleAxis(const char *key, float value);
    bool SetTripleAxis(const char *key, int32_t x, int32_t y, int32_t z);
    bool SetFourAxis(const char *key, int32_t x, int32_t y, int32_t z,
                     int32_t w);

    bool Save();

  private:
    CalibrationFile() : file_(nullptr), json_root_(nullptr) {}

    static std::shared_ptr<CalibrationFile> instance_;
    bool Initialize();

    std::unique_ptr<File> file_;
    sp<JSONObject> json_root_;

    bool Read();
};

}  // namespace android

#endif  // CALIBRATION_FILE_H_
