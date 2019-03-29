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
#define LOG_TAG "android.hardware.vibrator@1.1-service.wahoo"

#include <android/hardware/vibrator/1.1/IVibrator.h>
#include <hidl/HidlSupport.h>
#include <hidl/HidlTransportSupport.h>
#include <utils/Errors.h>
#include <utils/StrongPointer.h>

#include "Vibrator.h"

using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::hardware::vibrator::V1_1::IVibrator;
using android::hardware::vibrator::V1_1::implementation::Vibrator;
using namespace android;

// Refer to Documentation/ABI/testing/sysfs-class-led-driver-drv2624
// kernel documentation on the detail usages for ABIs below
static constexpr char ACTIVATE_PATH[] = "/sys/class/leds/vibrator/activate";
static constexpr char DURATION_PATH[] = "/sys/class/leds/vibrator/duration";
static constexpr char STATE_PATH[] = "/sys/class/leds/vibrator/state";
static constexpr char RTP_INPUT_PATH[] = "/sys/class/leds/vibrator/device/rtp_input";
static constexpr char MODE_PATH[] = "/sys/class/leds/vibrator/device/mode";
static constexpr char SEQUENCER_PATH[] = "/sys/class/leds/vibrator/device/set_sequencer";
static constexpr char SCALE_PATH[] = "/sys/class/leds/vibrator/device/scale";
static constexpr char CTRL_LOOP_PATH[] = "/sys/class/leds/vibrator/device/ctrl_loop";
static constexpr char LP_TRIGGER_PATH[] = "/sys/class/leds/vibrator/device/lp_trigger_effect";

// File path to the calibration file
static constexpr char CALIBRATION_FILEPATH[] = "/persist/haptics/drv2624.cal";

// Kernel ABIs for updating the calibration data
static constexpr char AUTOCAL_CONFIG[] = "autocal";
static constexpr char LRA_PERIOD_CONFIG[] = "lra_period";
static constexpr char AUTOCAL_FILEPATH[] = "/sys/class/leds/vibrator/device/autocal";
static constexpr char OL_LRA_PERIOD_FILEPATH[] = "/sys/class/leds/vibrator/device/ol_lra_period";

static std::string trim(const std::string& str,
        const std::string& whitespace = " \t") {
    const auto str_begin = str.find_first_not_of(whitespace);
    if (str_begin == std::string::npos) {
        return "";
    }

    const auto str_end = str.find_last_not_of(whitespace);
    const auto str_range = str_end - str_begin + 1;

    return str.substr(str_begin, str_range);
}

static bool loadCalibrationData() {
    std::map<std::string, std::string> config_data;

    std::ofstream autocal{AUTOCAL_FILEPATH};
    if (!autocal) {
        int error = errno;
        ALOGE("Failed to open %s (%d): %s", AUTOCAL_FILEPATH, error,
                strerror(error));
        return false;
    }

    std::ofstream ol_lra_period{OL_LRA_PERIOD_FILEPATH};
    if (!ol_lra_period) {
        int error = errno;
        ALOGE("Failed to open %s (%d): %s", OL_LRA_PERIOD_FILEPATH, error,
                strerror(error));
        return false;
    }

    std::ifstream cal_data{CALIBRATION_FILEPATH};
    if (!cal_data) {
        int error = errno;
        ALOGE("Failed to open %s (%d): %s", CALIBRATION_FILEPATH, error,
                strerror(error));
        return false;
    }

    std::string line;

    while (std::getline(cal_data, line)) {
        if (line.empty() || line[0] == '#') {
            continue;
        }
        std::istringstream is_line(line);
        std::string key;
        if (std::getline(is_line, key, ':')) {
            std::string value;

            if (std::getline(is_line, value)) {
                config_data[trim(key)] = trim(value);
            }
        }
    }

    if(config_data.find(AUTOCAL_CONFIG) != config_data.end()) {
        autocal << config_data[AUTOCAL_CONFIG] << std::endl;
    }

    if(config_data.find(LRA_PERIOD_CONFIG) != config_data.end()) {
        ol_lra_period << config_data[LRA_PERIOD_CONFIG] << std::endl;
    }

    return true;
}

status_t registerVibratorService() {
    // ostreams below are required
    std::ofstream activate{ACTIVATE_PATH};
    if (!activate) {
        int error = errno;
        ALOGE("Failed to open %s (%d): %s", ACTIVATE_PATH, error, strerror(error));
        return -error;
    }

    std::ofstream duration{DURATION_PATH};
    if (!duration) {
        int error = errno;
        ALOGE("Failed to open %s (%d): %s", DURATION_PATH, error, strerror(error));
        return -error;
    }

    std::ofstream state{STATE_PATH};
    if (!state) {
        int error = errno;
        ALOGE("Failed to open %s (%d): %s", STATE_PATH, error, strerror(error));
        return -error;
    }

    state << 1 << std::endl;
    if (!state) {
        int error = errno;
        ALOGE("Failed to set state (%d): %s", errno, strerror(errno));
        return -error;
    }

    // ostreams below are optional
    std::ofstream rtpinput{RTP_INPUT_PATH};
    if (!rtpinput) {
        int error = errno;
        ALOGW("Failed to open %s (%d): %s", RTP_INPUT_PATH, error, strerror(error));
    }

    std::ofstream mode{MODE_PATH};
    if (!mode) {
        int error = errno;
        ALOGW("Failed to open %s (%d): %s", MODE_PATH, error, strerror(error));
    }

    std::ofstream sequencer{SEQUENCER_PATH};
    if (!sequencer) {
        int error = errno;
        ALOGW("Failed to open %s (%d): %s", SEQUENCER_PATH, error, strerror(error));
    }

    std::ofstream scale{SCALE_PATH};
    if (!scale) {
        int error = errno;
        ALOGW("Failed to open %s (%d): %s", SCALE_PATH, error, strerror(error));
    }

    std::ofstream ctrlloop{CTRL_LOOP_PATH};
    if (!ctrlloop) {
        int error = errno;
        ALOGW("Failed to open %s (%d): %s", CTRL_LOOP_PATH, error, strerror(error));
    }

    std::ofstream lptrigger{LP_TRIGGER_PATH};
    if (!lptrigger) {
        int error = errno;
        ALOGW("Failed to open %s (%d): %s", LP_TRIGGER_PATH, error, strerror(error));
    }

    if (!loadCalibrationData()) {
        ALOGW("Failed load calibration data");
    }

    sp<IVibrator> vibrator = new Vibrator(std::move(activate), std::move(duration),
            std::move(state), std::move(rtpinput), std::move(mode),
            std::move(sequencer), std::move(scale), std::move(ctrlloop), std::move(lptrigger));

    vibrator->registerAsService();

    return OK;
}

int main() {
    configureRpcThreadpool(1, true);
    status_t status = registerVibratorService();

    if (status != OK) {
        return status;
    }

    joinRpcThreadpool();
}
