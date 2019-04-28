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

#define LOG_TAG "GnssHAL_GnssMeasurementInterface"

#include "GnssMeasurement.h"

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {
namespace implementation {

sp<IGnssMeasurementCallback> GnssMeasurement::sGnssMeasureCbIface = nullptr;
GpsMeasurementCallbacks GnssMeasurement::sGnssMeasurementCbs = {
    .size = sizeof(GpsMeasurementCallbacks),
    .measurement_callback = gpsMeasurementCb,
    .gnss_measurement_callback = gnssMeasurementCb
};

GnssMeasurement::GnssMeasurement(const GpsMeasurementInterface* gpsMeasurementIface)
    : mGnssMeasureIface(gpsMeasurementIface) {}

void GnssMeasurement::gnssMeasurementCb(LegacyGnssData* legacyGnssData) {
    if (sGnssMeasureCbIface == nullptr) {
        ALOGE("%s: GNSSMeasurement Callback Interface configured incorrectly", __func__);
        return;
    }

    if (legacyGnssData == nullptr) {
        ALOGE("%s: Invalid GnssData from GNSS HAL", __func__);
        return;
    }

    IGnssMeasurementCallback::GnssData gnssData;
    gnssData.measurementCount = std::min(legacyGnssData->measurement_count,
                                         static_cast<size_t>(GnssMax::SVS_COUNT));

    for (size_t i = 0; i < gnssData.measurementCount; i++) {
        auto entry = legacyGnssData->measurements[i];
        auto state = static_cast<GnssMeasurementState>(entry.state);
        if (state & IGnssMeasurementCallback::GnssMeasurementState::STATE_TOW_DECODED) {
          state |= IGnssMeasurementCallback::GnssMeasurementState::STATE_TOW_KNOWN;
        }
        if (state & IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_TOD_DECODED) {
          state |= IGnssMeasurementCallback::GnssMeasurementState::STATE_GLO_TOD_KNOWN;
        }
        gnssData.measurements[i] = {
            .flags = entry.flags,
            .svid = entry.svid,
            .constellation = static_cast<GnssConstellationType>(entry.constellation),
            .timeOffsetNs = entry.time_offset_ns,
            .state = state,
            .receivedSvTimeInNs = entry.received_sv_time_in_ns,
            .receivedSvTimeUncertaintyInNs = entry.received_sv_time_uncertainty_in_ns,
            .cN0DbHz = entry.c_n0_dbhz,
            .pseudorangeRateMps = entry.pseudorange_rate_mps,
            .pseudorangeRateUncertaintyMps = entry.pseudorange_rate_uncertainty_mps,
            .accumulatedDeltaRangeState = entry.accumulated_delta_range_state,
            .accumulatedDeltaRangeM = entry.accumulated_delta_range_m,
            .accumulatedDeltaRangeUncertaintyM = entry.accumulated_delta_range_uncertainty_m,
            .carrierFrequencyHz = entry.carrier_frequency_hz,
            .carrierCycles = entry.carrier_cycles,
            .carrierPhase = entry.carrier_phase,
            .carrierPhaseUncertainty = entry.carrier_phase_uncertainty,
            .multipathIndicator = static_cast<IGnssMeasurementCallback::GnssMultipathIndicator>(
                    entry.multipath_indicator),
            .snrDb = entry.snr_db
        };
    }

    auto clockVal = legacyGnssData->clock;
    gnssData.clock = {
        .gnssClockFlags = clockVal.flags,
        .leapSecond = clockVal.leap_second,
        .timeNs = clockVal.time_ns,
        .timeUncertaintyNs = clockVal.time_uncertainty_ns,
        .fullBiasNs = clockVal.full_bias_ns,
        .biasNs = clockVal.bias_ns,
        .biasUncertaintyNs = clockVal.bias_uncertainty_ns,
        .driftNsps = clockVal.drift_nsps,
        .driftUncertaintyNsps = clockVal.drift_uncertainty_nsps,
        .hwClockDiscontinuityCount = clockVal.hw_clock_discontinuity_count
    };

    auto ret = sGnssMeasureCbIface->GnssMeasurementCb(gnssData);
    if (!ret.isOk()) {
        ALOGE("%s: Unable to invoke callback", __func__);
    }
}

/*
 * The code in the following method has been moved here from GnssLocationProvider.
 * It converts GpsData to GnssData. This code is no longer required in
 * GnssLocationProvider since GpsData is deprecated and no longer part of the
 * GNSS interface.
 */
void GnssMeasurement::gpsMeasurementCb(GpsData* gpsData) {
    if (sGnssMeasureCbIface == nullptr) {
        ALOGE("%s: GNSSMeasurement Callback Interface configured incorrectly", __func__);
        return;
    }

    if (gpsData == nullptr) {
        ALOGE("%s: Invalid GpsData from GNSS HAL", __func__);
        return;
    }

    IGnssMeasurementCallback::GnssData gnssData;
    gnssData.measurementCount = std::min(gpsData->measurement_count,
                                         static_cast<size_t>(GnssMax::SVS_COUNT));


    for (size_t i = 0; i < gnssData.measurementCount; i++) {
        auto entry = gpsData->measurements[i];
        gnssData.measurements[i].flags = entry.flags;
        gnssData.measurements[i].svid = static_cast<int32_t>(entry.prn);
        if (entry.prn >= 1 && entry.prn <= 32) {
            gnssData.measurements[i].constellation = GnssConstellationType::GPS;
        } else {
            gnssData.measurements[i].constellation =
                  GnssConstellationType::UNKNOWN;
        }

        gnssData.measurements[i].timeOffsetNs = entry.time_offset_ns;
        gnssData.measurements[i].state = entry.state;
        gnssData.measurements[i].receivedSvTimeInNs = entry.received_gps_tow_ns;
        gnssData.measurements[i].receivedSvTimeUncertaintyInNs =
            entry.received_gps_tow_uncertainty_ns;
        gnssData.measurements[i].cN0DbHz = entry.c_n0_dbhz;
        gnssData.measurements[i].pseudorangeRateMps = entry.pseudorange_rate_mps;
        gnssData.measurements[i].pseudorangeRateUncertaintyMps =
                entry.pseudorange_rate_uncertainty_mps;
        gnssData.measurements[i].accumulatedDeltaRangeState =
                entry.accumulated_delta_range_state;
        gnssData.measurements[i].accumulatedDeltaRangeM =
                entry.accumulated_delta_range_m;
        gnssData.measurements[i].accumulatedDeltaRangeUncertaintyM =
                entry.accumulated_delta_range_uncertainty_m;

        if (entry.flags & GNSS_MEASUREMENT_HAS_CARRIER_FREQUENCY) {
            gnssData.measurements[i].carrierFrequencyHz = entry.carrier_frequency_hz;
        } else {
            gnssData.measurements[i].carrierFrequencyHz = 0;
        }

        if (entry.flags & GNSS_MEASUREMENT_HAS_CARRIER_PHASE) {
            gnssData.measurements[i].carrierPhase = entry.carrier_phase;
        } else {
            gnssData.measurements[i].carrierPhase = 0;
        }

        if (entry.flags & GNSS_MEASUREMENT_HAS_CARRIER_PHASE_UNCERTAINTY) {
            gnssData.measurements[i].carrierPhaseUncertainty = entry.carrier_phase_uncertainty;
        } else {
            gnssData.measurements[i].carrierPhaseUncertainty = 0;
        }

        gnssData.measurements[i].multipathIndicator =
                static_cast<IGnssMeasurementCallback::GnssMultipathIndicator>(
                        entry.multipath_indicator);

        if (entry.flags & GNSS_MEASUREMENT_HAS_SNR) {
            gnssData.measurements[i].snrDb = entry.snr_db;
        } else {
            gnssData.measurements[i].snrDb = 0;
        }
    }

    auto clockVal = gpsData->clock;
    static uint32_t discontinuity_count_to_handle_old_clock_type = 0;

    gnssData.clock.leapSecond = clockVal.leap_second;
    /*
     * GnssClock only supports the more effective HW_CLOCK type, so type
     * handling and documentation complexity has been removed.  To convert the
     * old GPS_CLOCK types (active only in a limited number of older devices),
     * the GPS time information is handled as an always discontinuous HW clock,
     * with the GPS time information put into the full_bias_ns instead - so that
     * time_ns - full_bias_ns = local estimate of GPS time. Additionally, the
     * sign of full_bias_ns and bias_ns has flipped between GpsClock &
     * GnssClock, so that is also handled below.
     */
    switch (clockVal.type) {
        case GPS_CLOCK_TYPE_UNKNOWN:
            // Clock type unsupported.
            ALOGE("Unknown clock type provided.");
            break;
        case GPS_CLOCK_TYPE_LOCAL_HW_TIME:
            // Already local hardware time. No need to do anything.
            break;
        case GPS_CLOCK_TYPE_GPS_TIME:
            // GPS time, need to convert.
            clockVal.flags |= GPS_CLOCK_HAS_FULL_BIAS;
            clockVal.full_bias_ns = clockVal.time_ns;
            clockVal.time_ns = 0;
            gnssData.clock.hwClockDiscontinuityCount =
                    discontinuity_count_to_handle_old_clock_type++;
            break;
    }

    gnssData.clock.timeNs = clockVal.time_ns;
    gnssData.clock.timeUncertaintyNs = clockVal.time_uncertainty_ns;
    /*
     * Definition of sign for full_bias_ns & bias_ns has been changed since N,
     * so flip signs here.
     */
    gnssData.clock.fullBiasNs = -(clockVal.full_bias_ns);
    gnssData.clock.biasNs = -(clockVal.bias_ns);
    gnssData.clock.biasUncertaintyNs = clockVal.bias_uncertainty_ns;
    gnssData.clock.driftNsps = clockVal.drift_nsps;
    gnssData.clock.driftUncertaintyNsps = clockVal.drift_uncertainty_nsps;
    gnssData.clock.gnssClockFlags = clockVal.flags;

    auto ret = sGnssMeasureCbIface->GnssMeasurementCb(gnssData);
    if (!ret.isOk()) {
        ALOGE("%s: Unable to invoke callback", __func__);
    }
}

// Methods from ::android::hardware::gnss::V1_0::IGnssMeasurement follow.
Return<GnssMeasurement::GnssMeasurementStatus> GnssMeasurement::setCallback(
        const sp<IGnssMeasurementCallback>& callback)  {
    if (mGnssMeasureIface == nullptr) {
        ALOGE("%s: GnssMeasure interface is unavailable", __func__);
        return GnssMeasurementStatus::ERROR_GENERIC;
    }
    sGnssMeasureCbIface = callback;

    return static_cast<GnssMeasurement::GnssMeasurementStatus>(
            mGnssMeasureIface->init(&sGnssMeasurementCbs));
}

Return<void> GnssMeasurement::close()  {
    if (mGnssMeasureIface == nullptr) {
        ALOGE("%s: GnssMeasure interface is unavailable", __func__);
    } else {
        mGnssMeasureIface->close();
    }
    return Void();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android
