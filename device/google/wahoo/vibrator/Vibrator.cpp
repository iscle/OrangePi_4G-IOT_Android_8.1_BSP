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

#define LOG_TAG "VibratorService"

#include <log/log.h>

#include <hardware/hardware.h>
#include <hardware/vibrator.h>
#include <cutils/properties.h>

#include "Vibrator.h"

#include <cinttypes>
#include <cmath>
#include <iostream>
#include <fstream>


namespace android {
namespace hardware {
namespace vibrator {
namespace V1_1 {
namespace implementation {

static constexpr int8_t MAX_RTP_INPUT = 127;
static constexpr int8_t MIN_RTP_INPUT = 0;

static constexpr char RTP_MODE[] = "rtp";
static constexpr char WAVEFORM_MODE[] = "waveform";

// Use effect #1 in the waveform library
static constexpr char WAVEFORM_CLICK_EFFECT_SEQ[] = "1 0";
static constexpr int32_t WAVEFORM_CLICK_EFFECT_MS = 6;

// Use effect #2 in the waveform library
static constexpr char WAVEFORM_TICK_EFFECT_SEQ[] = "2 0";
static constexpr int32_t WAVEFORM_TICK_EFFECT_MS = 2;

// Use effect #3 in the waveform library
static constexpr char WAVEFORM_DOUBLE_CLICK_EFFECT_SEQ[] = "3 0";
static constexpr uint32_t WAVEFORM_DOUBLE_CLICK_EFFECT_MS = 135;

// Timeout threshold for selecting open or closed loop mode
static constexpr int8_t LOOP_MODE_THRESHOLD_MS = 20;

using Status = ::android::hardware::vibrator::V1_0::Status;
using EffectStrength = ::android::hardware::vibrator::V1_0::EffectStrength;

Vibrator::Vibrator(std::ofstream&& activate, std::ofstream&& duration,
        std::ofstream&& state, std::ofstream&& rtpinput,
        std::ofstream&& mode, std::ofstream&& sequencer,
        std::ofstream&& scale, std::ofstream&& ctrlloop, std::ofstream&& lptrigger) :
    mActivate(std::move(activate)),
    mDuration(std::move(duration)),
    mState(std::move(state)),
    mRtpInput(std::move(rtpinput)),
    mMode(std::move(mode)),
    mSequencer(std::move(sequencer)),
    mScale(std::move(scale)),
    mCtrlLoop(std::move(ctrlloop)),
    mLpTriggerEffect(std::move(lptrigger)) {

    mClickDuration = property_get_int32("ro.vibrator.hal.click.duration", WAVEFORM_CLICK_EFFECT_MS);
    mTickDuration = property_get_int32("ro.vibrator.hal.tick.duration", WAVEFORM_TICK_EFFECT_MS);

    // This enables effect #1 from the waveform library to be triggered by SLPI
    // while the AP is in suspend mode
    mLpTriggerEffect << 1 << std::endl;
    if (!mLpTriggerEffect) {
        ALOGW("Failed to set LP trigger mode (%d): %s", errno, strerror(errno));
    }
}

Return<Status> Vibrator::on(uint32_t timeoutMs, bool forceOpenLoop, bool isWaveform) {
    uint32_t loopMode = 1;

    // Open-loop mode is used for short click for over-drive
    // Close-loop mode is used for long notification for stability
    if (!forceOpenLoop && timeoutMs > LOOP_MODE_THRESHOLD_MS) {
        loopMode = 0;
    }

    mCtrlLoop << loopMode << std::endl;
    mDuration << timeoutMs << std::endl;
    if (!mDuration) {
        ALOGE("Failed to set duration (%d): %s", errno, strerror(errno));
        return Status::UNKNOWN_ERROR;
    }

    if (isWaveform) {
        mMode << WAVEFORM_MODE << std::endl;
    } else {
        mMode << RTP_MODE << std::endl;
    }

    mActivate << 1 << std::endl;
    if (!mActivate) {
        ALOGE("Failed to activate (%d): %s", errno, strerror(errno));
        return Status::UNKNOWN_ERROR;
    }

   return Status::OK;
}

// Methods from ::android::hardware::vibrator::V1_1::IVibrator follow.
Return<Status> Vibrator::on(uint32_t timeoutMs) {
    return on(timeoutMs, false /* forceOpenLoop */, false /* isWaveform */);
}

Return<Status> Vibrator::off()  {
    mActivate << 0 << std::endl;
    if (!mActivate) {
        ALOGE("Failed to turn vibrator off (%d): %s", errno, strerror(errno));
        return Status::UNKNOWN_ERROR;
    }
    return Status::OK;
}

Return<bool> Vibrator::supportsAmplitudeControl()  {
    return (mRtpInput ? true : false);
}

Return<Status> Vibrator::setAmplitude(uint8_t amplitude) {

    if (amplitude == 0) {
        return Status::BAD_VALUE;
    }

    int32_t rtp_input =
            std::round((amplitude - 1) / 254.0 * (MAX_RTP_INPUT - MIN_RTP_INPUT) +
            MIN_RTP_INPUT);

    mRtpInput << rtp_input << std::endl;
    if (!mRtpInput) {
        ALOGE("Failed to set amplitude (%d): %s", errno, strerror(errno));
        return Status::UNKNOWN_ERROR;
    }

    return Status::OK;
}

static uint8_t convertEffectStrength(EffectStrength strength) {
    uint8_t scale;

    switch (strength) {
    case EffectStrength::LIGHT:
        scale = 1; // 50%
        break;
    case EffectStrength::MEDIUM:
    case EffectStrength::STRONG:
        scale = 0; // 100%
        break;
    }

    return scale;
}

Return<void> Vibrator::perform(Effect effect, EffectStrength strength, perform_cb _hidl_cb) {
    Status status = Status::OK;
    uint32_t timeMS;

    if (effect == Effect::CLICK) {
        mSequencer << WAVEFORM_CLICK_EFFECT_SEQ << std::endl;
        timeMS = mClickDuration;
    } else if (effect == Effect::DOUBLE_CLICK) {
        mSequencer << WAVEFORM_DOUBLE_CLICK_EFFECT_SEQ << std::endl;
        timeMS = WAVEFORM_DOUBLE_CLICK_EFFECT_MS;
    } else {
        _hidl_cb(Status::UNSUPPORTED_OPERATION, 0);
        return Void();
    }

    mScale << convertEffectStrength(strength) << std::endl;
    on(timeMS, true /* forceOpenLoop */, true /* isWaveform */);

    _hidl_cb(status, timeMS);
    return Void();
}

Return<void> Vibrator::perform_1_1(Effect_1_1 effect, EffectStrength strength,
        perform_cb _hidl_cb) {
    Status status = Status::OK;
    uint32_t timeMS;

    if (effect == Effect_1_1::TICK) {
        mSequencer << WAVEFORM_TICK_EFFECT_SEQ << std::endl;
        timeMS = mTickDuration;
    } else if (effect < Effect_1_1::TICK) {
        return perform(static_cast<Effect>(effect), strength, _hidl_cb);
    } else {
        _hidl_cb(Status::UNSUPPORTED_OPERATION, 0);
        return Void();
    }

    mScale << convertEffectStrength(strength) << std::endl;
    on(timeMS, true /* forceOpenLoop */, true /* isWaveform */);

    _hidl_cb(status, timeMS);
    return Void();
}

} // namespace implementation
}  // namespace V1_1
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
