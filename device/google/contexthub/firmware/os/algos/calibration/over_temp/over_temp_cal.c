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

#include "calibration/over_temp/over_temp_cal.h"

#include <float.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

#include "calibration/util/cal_log.h"
#include "common/math/macros.h"
#include "util/nano_assert.h"

/////// DEFINITIONS AND MACROS ////////////////////////////////////////////////

// Value used to check whether OTC model data is near zero.
#define OTC_MODELDATA_NEAR_ZERO_TOL (1e-7f)

// Defines the default weighting function for the linear model fit routine.
// Weighting = 10.0; for offsets newer than 5 minutes.
static const struct OverTempCalWeightPt kOtcDefaultWeight0 = {
    .offset_age_nanos = MIN_TO_NANOS(5),
    .weight = 10.0f,
};

// Weighting = 0.1; for offsets newer than 15 minutes.
static const struct OverTempCalWeightPt kOtcDefaultWeight1 = {
    .offset_age_nanos = MIN_TO_NANOS(15),
    .weight = 0.1f,
};

// The default weighting used for all older offsets.
#define OTC_MIN_WEIGHT_VALUE  (0.04f)

#ifdef OVERTEMPCAL_DBG_ENABLED
// A debug version label to help with tracking results.
#define OTC_DEBUG_VERSION_STRING "[July 05, 2017]"

// The time interval used to throttle debug messaging (100msec).
#define OTC_WAIT_TIME_NANOS (SEC_TO_NANOS(0.1))

// The time interval used to throttle temperture print messaging (1 second).
#define OTC_PRINT_TEMP_NANOS (SEC_TO_NANOS(1))

// Sensor axis label definition with index correspondence: 0=X, 1=Y, 2=Z.
static const char  kDebugAxisLabel[3] = "XYZ";
#endif  // OVERTEMPCAL_DBG_ENABLED

/////// FORWARD DECLARATIONS //////////////////////////////////////////////////

// Updates the latest received model estimate data.
static void setLatestEstimate(struct OverTempCal *over_temp_cal,
                              const float *offset, float offset_temp_celsius,
                              uint64_t timestamp_nanos);

/*
 * Determines if a new over-temperature model fit should be performed, and then
 * updates the model as needed.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp data structure.
 *   timestamp_nanos:  Current timestamp for the model update.
 */
static void computeModelUpdate(struct OverTempCal *over_temp_cal,
                               uint64_t timestamp_nanos);

/*
 * Searches 'model_data' for the sensor offset estimate closest to the specified
 * temperature. Sets the 'nearest_offset' pointer to the result.
 */
static void findNearestEstimate(struct OverTempCal *over_temp_cal,
                                float temperature_celsius);

/*
 * Removes the "old" offset estimates from 'model_data' (i.e., eliminates the
 * drift-compromised data).
 */
static void removeStaleModelData(struct OverTempCal *over_temp_cal,
                                 uint64_t timestamp_nanos);

/*
 * Removes the offset estimates from 'model_data' at index, 'model_index'.
 * Returns 'true' if data was removed.
 */
static bool removeModelDataByIndex(struct OverTempCal *over_temp_cal,
                                   size_t model_index);

/*
 * Since it may take a while for an empty model to build up enough data to start
 * producing new model parameter updates, the model collection can be
 * jump-started by using the new model parameters to insert "fake" data in place
 * of actual sensor offset data. 'timestamp_nanos' sets the timestamp for the
 * new model data.
 */
static bool jumpStartModelData(struct OverTempCal *over_temp_cal,
                               uint64_t timestamp_nanos);

/*
 * Computes a new model fit and provides updated model parameters for the
 * over-temperature model data. Uses a simple weighting function determined from
 * the age of the model data.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp data structure.
 *   timestamp_nanos:  Current timestamp for the model update.
 * OUTPUTS:
 *   temp_sensitivity: Updated modeled temperature sensitivity (array).
 *   sensor_intercept: Updated model intercept (array).
 *
 * NOTE: Arrays are all 3-dimensional with indices: 0=x, 1=y, 2=z.
 *
 * Reference: Press, William H. "15.2 Fitting Data to a Straight Line."
 * Numerical Recipes: The Art of Scientific Computing. Cambridge, 1992.
 */
static void updateModel(const struct OverTempCal *over_temp_cal,
                        uint64_t timestamp_nanos, float *temp_sensitivity,
                        float *sensor_intercept);

/*
 * Computes a new over-temperature compensated offset estimate based on the
 * temperature specified by, 'temperature_celsius'.
 *
 * INPUTS:
 *   over_temp_cal:        Over-temp data structure.
 *   timestamp_nanos:      The current system timestamp.
 *   temperature_celsius:  The sensor temperature to compensate the offset for.
 */
static void updateCalOffset(struct OverTempCal *over_temp_cal,
                            uint64_t timestamp_nanos,
                            float temperature_celsius);

/*
 * Sets the new over-temperature compensated offset estimate vector and
 * timestamp.
 *
 * INPUTS:
 *   over_temp_cal:        Over-temp data structure.
 *   compensated_offset:   The new temperature compensated offset array.
 *   timestamp_nanos:      The current system timestamp.
 *   temperature_celsius:  The sensor temperature to compensate the offset for.
 */
static void setCompensatedOffset(struct OverTempCal *over_temp_cal,
                                 const float *compensated_offset,
                                 uint64_t timestamp_nanos,
                                 float temperature_celsius);

/*
 * Checks new offset estimates to determine if they could be an outlier that
 * should be rejected. Operates on a per-axis basis determined by 'axis_index'.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp data structure.
 *   offset:           Offset array.
 *   axis_index:       Index of the axis to check (0=x, 1=y, 2=z).
 *
 * Returns 'true' if the deviation of the offset value from the linear model
 * exceeds 'outlier_limit'.
 */
static bool outlierCheck(struct OverTempCal *over_temp_cal, const float *offset,
                         size_t axis_index, float temperature_celsius);

// Sets the OTC model parameters to an "initialized" state.
static void resetOtcLinearModel(struct OverTempCal *over_temp_cal);

// Checks that the input temperature value is within the valid range. If outside
// of range, then 'temperature_celsius' is coerced to within the limits.
static bool checkAndEnforceTemperatureRange(float *temperature_celsius);

// Returns "true" if the candidate linear model parameters are within the valid
// range, and not all zeros.
static bool isValidOtcLinearModel(const struct OverTempCal *over_temp_cal,
                   float temp_sensitivity, float sensor_intercept);

// Returns "true" if 'offset' and 'offset_temp_celsius' is valid.
static bool isValidOtcOffset(const float *offset, float offset_temp_celsius);

// Returns the least-squares weight based on the age of a particular offset
// estimate.
static float evaluateWeightingFunction(const struct OverTempCal *over_temp_cal,
                                       uint64_t offset_timestamp_nanos,
                                       uint64_t current_timestamp_nanos);

// Updates 'compensated_offset' using the linear OTC model.
static void compensateWithLinearModel(struct OverTempCal *over_temp_cal,
                                      uint64_t timestamp_nanos,
                                      float temperature_celsius);

// Adds a linear extrapolated term to 'compensated_offset' (3-element array)
// based on the linear OTC model and 'delta_temp_celsius' (the difference
// between the current sensor temperature and the offset temperature associated
// with 'compensated_offset').
static void addLinearTemperatureExtrapolation(struct OverTempCal *over_temp_cal,
                                              float *compensated_offset,
                                              float delta_temp_celsius);

// Provides an over-temperature compensated offset based on the 'estimate'.
static void compensateWithEstimate(
    struct OverTempCal *over_temp_cal, uint64_t timestamp_nanos,
    struct OverTempCalDataPt *estimate, float temperature_celsius);

// Evaluates the nearest-temperature compensation (with linear extrapolation
// term due to temperature), and compares it with the compensation due to
// just the linear model when 'compare_with_linear_model' is true, otherwise
// the comparison will be made with an extrapolated version of the current
// compensation value. The comparison tests whether the nearest-temperature
// estimate deviates from the linear-model (or current-compensated) value by
// more than 'jump_tolerance'. If a "jump" is detected, then it keeps the
// linear-model (or current-compensated) value.
static void compareAndCompensateWithNearest(struct OverTempCal *over_temp_cal,
                                            uint64_t timestamp_nanos,
                                            float temperature_celsius,
                                            bool compare_to_linear_model);

// Refreshes the OTC model to ensure that the most relevant model weighting is
// being used.
static void refreshOtcModel(struct OverTempCal *over_temp_cal,
                            uint64_t timestamp_nanos);

#ifdef OVERTEMPCAL_DBG_ENABLED
// This helper function stores all of the debug tracking information necessary
// for printing log messages.
static void updateDebugData(struct OverTempCal* over_temp_cal);

// Helper function that creates tag strings useful for identifying specific
// debug output data (embedded system friendly; not all systems have 'sprintf').
// 'new_debug_tag' is any null-terminated string. Respect the total allowed
// length of the 'otc_debug_tag' string.
//   Constructs: "[" + <otc_debug_tag> + <new_debug_tag>
//   Example,
//     otc_debug_tag = "OVER_TEMP_CAL"
//     new_debug_tag = "INIT]"
//   Output: "[OVER_TEMP_CAL:INIT]"
static void createDebugTag(struct OverTempCal *over_temp_cal,
                           const char *new_debug_tag);
#endif  // OVERTEMPCAL_DBG_ENABLED

/////// FUNCTION DEFINITIONS //////////////////////////////////////////////////

void overTempCalInit(struct OverTempCal *over_temp_cal,
                     size_t min_num_model_pts,
                     uint64_t min_temp_update_period_nanos,
                     float delta_temp_per_bin, float jump_tolerance,
                     float outlier_limit, uint64_t age_limit_nanos,
                     float temp_sensitivity_limit, float sensor_intercept_limit,
                     float significant_offset_change, bool over_temp_enable) {
  ASSERT_NOT_NULL(over_temp_cal);

  // Clears OverTempCal memory.
  memset(over_temp_cal, 0, sizeof(struct OverTempCal));

  // Initializes the pointers to important sensor offset estimates.
  over_temp_cal->nearest_offset = &over_temp_cal->model_data[0];
  over_temp_cal->latest_offset  = NULL;

  // Initializes the OTC linear model parameters.
  resetOtcLinearModel(over_temp_cal);

  // Initializes the model identification parameters.
  over_temp_cal->new_overtemp_model_available = false;
  over_temp_cal->new_overtemp_offset_available = false;
  over_temp_cal->min_num_model_pts = min_num_model_pts;
  over_temp_cal->min_temp_update_period_nanos = min_temp_update_period_nanos;
  over_temp_cal->delta_temp_per_bin = delta_temp_per_bin;
  over_temp_cal->jump_tolerance = jump_tolerance;
  over_temp_cal->outlier_limit = outlier_limit;
  over_temp_cal->age_limit_nanos = age_limit_nanos;
  over_temp_cal->temp_sensitivity_limit = temp_sensitivity_limit;
  over_temp_cal->sensor_intercept_limit = sensor_intercept_limit;
  over_temp_cal->significant_offset_change = significant_offset_change;
  over_temp_cal->over_temp_enable = over_temp_enable;

  // Initializes the over-temperature compensated offset temperature.
  over_temp_cal->compensated_offset.offset_temp_celsius =
      OTC_TEMP_INVALID_CELSIUS;

  // Defines the default weighting function for the linear model fit routine.
  overTempSetWeightingFunction(over_temp_cal, 0, &kOtcDefaultWeight0);
  overTempSetWeightingFunction(over_temp_cal, 1, &kOtcDefaultWeight1);

#ifdef OVERTEMPCAL_DBG_ENABLED
  // Sets the default sensor descriptors for debugging.
  overTempCalDebugDescriptors(over_temp_cal, "OVER_TEMP_CAL", "mDPS",
                              RAD_TO_MDEG);

  createDebugTag(over_temp_cal, ":INIT]");
  if (over_temp_cal->over_temp_enable) {
    CAL_DEBUG_LOG(over_temp_cal->otc_debug_tag,
                  "Over-temperature compensation ENABLED.");
  } else {
    CAL_DEBUG_LOG(over_temp_cal->otc_debug_tag,
                  "Over-temperature compensation DISABLED.");
  }
#endif  // OVERTEMPCAL_DBG_ENABLED
}

void overTempCalSetModel(struct OverTempCal *over_temp_cal, const float *offset,
                         float offset_temp_celsius, uint64_t timestamp_nanos,
                         const float *temp_sensitivity,
                         const float *sensor_intercept, bool jump_start_model) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(offset);
  ASSERT_NOT_NULL(temp_sensitivity);
  ASSERT_NOT_NULL(sensor_intercept);

  // Initializes the OTC linear model parameters.
  resetOtcLinearModel(over_temp_cal);

  // Sets the model parameters if they are within the acceptable limits.
  // Includes a check to reject input model parameters that may have been passed
  // in as all zeros.
  for (size_t i = 0; i < 3; i++) {
    if (isValidOtcLinearModel(over_temp_cal, temp_sensitivity[i],
                              sensor_intercept[i])) {
      over_temp_cal->temp_sensitivity[i] = temp_sensitivity[i];
      over_temp_cal->sensor_intercept[i] = sensor_intercept[i];
    }
  }

  // Model "Jump-Start".
  const bool model_jump_started =
      (jump_start_model) ? jumpStartModelData(over_temp_cal, timestamp_nanos)
                         : false;

  if (!model_jump_started) {
    // Checks that the new offset data is valid.
    if (isValidOtcOffset(offset, offset_temp_celsius)) {
      // Sets the initial over-temp calibration estimate.
      memcpy(over_temp_cal->model_data[0].offset, offset,
             sizeof(over_temp_cal->model_data[0].offset));
      over_temp_cal->model_data[0].offset_temp_celsius = offset_temp_celsius;
      over_temp_cal->model_data[0].timestamp_nanos = timestamp_nanos;
      over_temp_cal->num_model_pts = 1;
    } else {
      // No valid offset data to load.
      over_temp_cal->num_model_pts = 0;
#ifdef OVERTEMPCAL_DBG_ENABLED
      createDebugTag(over_temp_cal, ":RECALL]");
      CAL_DEBUG_LOG(over_temp_cal->otc_debug_tag,
                    "No valid sensor offset vector to load.");
#endif  // OVERTEMPCAL_DBG_ENABLED
    }
  }

  // If the new offset is valid, then it will be used as the current compensated
  // offset, otherwise the current value will be kept.
  if (isValidOtcOffset(offset, offset_temp_celsius)) {
    memcpy(over_temp_cal->compensated_offset.offset, offset,
           sizeof(over_temp_cal->compensated_offset.offset));
    over_temp_cal->compensated_offset.offset_temp_celsius = offset_temp_celsius;
    over_temp_cal->compensated_offset.timestamp_nanos = timestamp_nanos;
  }

  // Resets the latest offset pointer. There are no new offset estimates to
  // track yet.
  over_temp_cal->latest_offset = NULL;

  // Sets the model and offset update times to the current timestamp.
  over_temp_cal->last_offset_update_nanos = timestamp_nanos;
  over_temp_cal->last_model_update_nanos = timestamp_nanos;

#ifdef OVERTEMPCAL_DBG_ENABLED
  // Prints the recalled model data.
  createDebugTag(over_temp_cal, ":SET MODEL]");
  CAL_DEBUG_LOG(
      over_temp_cal->otc_debug_tag,
      "Offset|Temp [%s|C]: " CAL_FORMAT_3DIGITS_TRIPLET
      " | " CAL_FORMAT_3DIGITS,
      over_temp_cal->otc_unit_tag,
      CAL_ENCODE_FLOAT(offset[0] * over_temp_cal->otc_unit_conversion, 3),
      CAL_ENCODE_FLOAT(offset[1] * over_temp_cal->otc_unit_conversion, 3),
      CAL_ENCODE_FLOAT(offset[2] * over_temp_cal->otc_unit_conversion, 3),
      CAL_ENCODE_FLOAT(offset_temp_celsius, 3));

  CAL_DEBUG_LOG(
      over_temp_cal->otc_debug_tag,
      "Sensitivity|Intercept [%s/C|%s]: " CAL_FORMAT_3DIGITS_TRIPLET
      " | " CAL_FORMAT_3DIGITS_TRIPLET,
      over_temp_cal->otc_unit_tag, over_temp_cal->otc_unit_tag,
      CAL_ENCODE_FLOAT(temp_sensitivity[0] * over_temp_cal->otc_unit_conversion,
                       3),
      CAL_ENCODE_FLOAT(temp_sensitivity[1] * over_temp_cal->otc_unit_conversion,
                       3),
      CAL_ENCODE_FLOAT(temp_sensitivity[2] * over_temp_cal->otc_unit_conversion,
                       3),
      CAL_ENCODE_FLOAT(sensor_intercept[0] * over_temp_cal->otc_unit_conversion,
                       3),
      CAL_ENCODE_FLOAT(sensor_intercept[1] * over_temp_cal->otc_unit_conversion,
                       3),
      CAL_ENCODE_FLOAT(sensor_intercept[2] * over_temp_cal->otc_unit_conversion,
                       3));

  // Resets the debug print machine to ensure that updateDebugData() can
  // produce a debug report and interupt any ongoing report.
  over_temp_cal->debug_state = OTC_IDLE;

  // Triggers a debug print out to view the new model parameters.
  updateDebugData(over_temp_cal);
#endif  // OVERTEMPCAL_DBG_ENABLED
}

void overTempCalGetModel(struct OverTempCal *over_temp_cal, float *offset,
                         float *offset_temp_celsius, uint64_t *timestamp_nanos,
                         float *temp_sensitivity, float *sensor_intercept) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(offset);
  ASSERT_NOT_NULL(offset_temp_celsius);
  ASSERT_NOT_NULL(timestamp_nanos);
  ASSERT_NOT_NULL(temp_sensitivity);
  ASSERT_NOT_NULL(sensor_intercept);

  // Gets the latest over-temp calibration model data.
  memcpy(temp_sensitivity, over_temp_cal->temp_sensitivity,
         sizeof(over_temp_cal->temp_sensitivity));
  memcpy(sensor_intercept, over_temp_cal->sensor_intercept,
         sizeof(over_temp_cal->sensor_intercept));
  *timestamp_nanos = over_temp_cal->last_model_update_nanos;

  // Gets the latest temperature compensated offset estimate.
  overTempCalGetOffset(over_temp_cal, offset_temp_celsius, offset);
}

void overTempCalSetModelData(struct OverTempCal *over_temp_cal,
                             size_t data_length, uint64_t timestamp_nanos,
                             const struct OverTempCalDataPt *model_data) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(model_data);

  // Load only "good" data from the input 'model_data'.
  over_temp_cal->num_model_pts = NANO_MIN(data_length, OTC_MODEL_SIZE);
  size_t valid_data_count = 0;
  for (size_t i = 0; i < over_temp_cal->num_model_pts; i++) {
    if (isValidOtcOffset(model_data[i].offset,
                         model_data[i].offset_temp_celsius)) {
      memcpy(&over_temp_cal->model_data[i], &model_data[i],
             sizeof(struct OverTempCalDataPt));

      // Updates the model time stamps to the current load time.
      over_temp_cal->model_data[i].timestamp_nanos = timestamp_nanos;

      valid_data_count++;
    }
  }
  over_temp_cal->num_model_pts = valid_data_count;

  // Initializes the OTC linear model parameters.
  resetOtcLinearModel(over_temp_cal);

  // Computes and replaces the model fit parameters.
  computeModelUpdate(over_temp_cal, timestamp_nanos);

  // Resets the latest offset pointer. There are no new offset estimates to
  // track yet.
  over_temp_cal->latest_offset = NULL;

  // Searches for the sensor offset estimate closest to the current temperature.
  findNearestEstimate(over_temp_cal,
                      over_temp_cal->compensated_offset.offset_temp_celsius);

  // Updates the current over-temperature compensated offset estimate.
  updateCalOffset(over_temp_cal, timestamp_nanos,
                  over_temp_cal->compensated_offset.offset_temp_celsius);

#ifdef OVERTEMPCAL_DBG_ENABLED
  // Prints the updated model data.
  createDebugTag(over_temp_cal, ":SET MODEL DATA SET]");
  CAL_DEBUG_LOG(over_temp_cal->otc_debug_tag,
                "Over-temperature full model data set recalled.");

  // Resets the debug print machine to ensure that a new debug report will
  // interupt any ongoing report.
  over_temp_cal->debug_state = OTC_IDLE;

  // Triggers a log printout to show the updated sensor offset estimate.
  updateDebugData(over_temp_cal);
#endif  // OVERTEMPCAL_DBG_ENABLED
}

void overTempCalGetModelData(struct OverTempCal *over_temp_cal,
                             size_t *data_length,
                             struct OverTempCalDataPt *model_data) {
  ASSERT_NOT_NULL(over_temp_cal);
  *data_length = over_temp_cal->num_model_pts;
  memcpy(model_data, over_temp_cal->model_data,
         over_temp_cal->num_model_pts * sizeof(struct OverTempCalDataPt));
}

void overTempCalGetOffset(struct OverTempCal *over_temp_cal,
                          float *compensated_offset_temperature_celsius,
                          float *compensated_offset) {
  memcpy(compensated_offset, over_temp_cal->compensated_offset.offset,
         sizeof(over_temp_cal->compensated_offset.offset));
  *compensated_offset_temperature_celsius =
      over_temp_cal->compensated_offset.offset_temp_celsius;
}

void overTempCalRemoveOffset(struct OverTempCal *over_temp_cal,
                             uint64_t timestamp_nanos, float xi, float yi,
                             float zi, float *xo, float *yo, float *zo) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(xo);
  ASSERT_NOT_NULL(yo);
  ASSERT_NOT_NULL(zo);

  // Determines whether over-temp compensation will be applied.
  if (over_temp_cal->over_temp_enable) {
    // Removes the over-temperature compensated offset from the input sensor
    // data.
    *xo = xi - over_temp_cal->compensated_offset.offset[0];
    *yo = yi - over_temp_cal->compensated_offset.offset[1];
    *zo = zi - over_temp_cal->compensated_offset.offset[2];
  } else {
    *xo = xi;
    *yo = yi;
    *zo = zi;
  }
}

bool overTempCalNewModelUpdateAvailable(struct OverTempCal *over_temp_cal) {
  ASSERT_NOT_NULL(over_temp_cal);
  const bool update_available = over_temp_cal->new_overtemp_model_available &&
                                over_temp_cal->over_temp_enable;

  // The 'new_overtemp_model_available' flag is reset when it is read here.
  over_temp_cal->new_overtemp_model_available = false;

  return update_available;
}

bool overTempCalNewOffsetAvailable(struct OverTempCal *over_temp_cal) {
  ASSERT_NOT_NULL(over_temp_cal);
  const bool update_available = over_temp_cal->new_overtemp_offset_available &&
                                over_temp_cal->over_temp_enable;

  // The 'new_overtemp_offset_available' flag is reset when it is read here.
  over_temp_cal->new_overtemp_offset_available = false;

  return update_available;
}

void overTempCalUpdateSensorEstimate(struct OverTempCal *over_temp_cal,
                                     uint64_t timestamp_nanos,
                                     const float *offset,
                                     float temperature_celsius) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(offset);
  ASSERT(over_temp_cal->delta_temp_per_bin > 0);

  // Checks that the new offset data is valid, returns if bad.
  if (!isValidOtcOffset(offset, temperature_celsius)) {
    return;
  }

  // Prevent a divide by zero below.
  if (over_temp_cal->delta_temp_per_bin <= 0) {
    return;
  }

  // Ensures that the most relevant model weighting is being used.
  refreshOtcModel(over_temp_cal, timestamp_nanos);

  // Checks whether this offset estimate is a likely outlier. A limit is placed
  // on 'num_outliers', the previous number of successive rejects, to prevent
  // too many back-to-back rejections.
  if (over_temp_cal->num_outliers < OTC_MAX_OUTLIER_COUNT) {
    if (outlierCheck(over_temp_cal, offset, 0, temperature_celsius) ||
        outlierCheck(over_temp_cal, offset, 1, temperature_celsius) ||
        outlierCheck(over_temp_cal, offset, 2, temperature_celsius)) {
      // Increments the count of rejected outliers.
      over_temp_cal->num_outliers++;

#ifdef OVERTEMPCAL_DBG_ENABLED
      createDebugTag(over_temp_cal, ":OUTLIER]");
      CAL_DEBUG_LOG(
          over_temp_cal->otc_debug_tag,
          "Offset|Temperature|Time [%s|C|nsec]: "
          CAL_FORMAT_3DIGITS_TRIPLET ", " CAL_FORMAT_3DIGITS ", %llu",
          over_temp_cal->otc_unit_tag,
          CAL_ENCODE_FLOAT(offset[0] * over_temp_cal->otc_unit_conversion, 3),
          CAL_ENCODE_FLOAT(offset[1] * over_temp_cal->otc_unit_conversion, 3),
          CAL_ENCODE_FLOAT(offset[2] * over_temp_cal->otc_unit_conversion, 3),
          CAL_ENCODE_FLOAT(temperature_celsius, 3),
          (unsigned long long int)timestamp_nanos);
#endif  // OVERTEMPCAL_DBG_ENABLED

      return;  // Outlier detected: skips adding this offset to the model.
    } else {
      // Resets the count of rejected outliers.
      over_temp_cal->num_outliers = 0;
    }
  } else {
    // Resets the count of rejected outliers.
    over_temp_cal->num_outliers = 0;
  }

  // Computes the temperature bin range data.
  const int32_t bin_num =
      CAL_FLOOR(temperature_celsius / over_temp_cal->delta_temp_per_bin);
  const float temp_lo_check = bin_num * over_temp_cal->delta_temp_per_bin;
  const float temp_hi_check = (bin_num + 1) * over_temp_cal->delta_temp_per_bin;

  // The rules for accepting new offset estimates into the 'model_data'
  // collection:
  //    1) The temperature domain is divided into bins each spanning
  //       'delta_temp_per_bin'.
  //    2) Find and replace the i'th 'model_data' estimate data if:
  //          Let, bin_num = floor(temperature_celsius / delta_temp_per_bin)
  //          temp_lo_check = bin_num * delta_temp_per_bin
  //          temp_hi_check = (bin_num + 1) * delta_temp_per_bin
  //          Check condition:
  //          temp_lo_check <= model_data[i].offset_temp_celsius < temp_hi_check
  bool replaced_one = false;
  for (size_t i = 0; i < over_temp_cal->num_model_pts; i++) {
    if (over_temp_cal->model_data[i].offset_temp_celsius < temp_hi_check &&
        over_temp_cal->model_data[i].offset_temp_celsius >= temp_lo_check) {
      // NOTE - The pointer to the new model data point is set here; the offset
      // data is set below in the call to 'setLatestEstimate'.
      over_temp_cal->latest_offset = &over_temp_cal->model_data[i];
      replaced_one = true;
      break;
    }
  }

  // NOTE - The pointer to the new model data point is set here; the offset
  // data is set below in the call to 'setLatestEstimate'.
  if (!replaced_one) {
    if (over_temp_cal->num_model_pts < OTC_MODEL_SIZE) {
      // 3) If nothing was replaced, and the 'model_data' buffer is not full
      //    then add the estimate data to the array.
      over_temp_cal->latest_offset =
          &over_temp_cal->model_data[over_temp_cal->num_model_pts];
      over_temp_cal->num_model_pts++;
    } else {
      // 4) Otherwise (nothing was replaced and buffer is full), replace the
      //    oldest data with the incoming one.
      over_temp_cal->latest_offset = &over_temp_cal->model_data[0];
      for (size_t i = 1; i < over_temp_cal->num_model_pts; i++) {
        if (over_temp_cal->latest_offset->timestamp_nanos <
            over_temp_cal->model_data[i].timestamp_nanos) {
          over_temp_cal->latest_offset = &over_temp_cal->model_data[i];
        }
      }
    }
  }

  // Updates the latest model estimate data.
  setLatestEstimate(over_temp_cal, offset, temperature_celsius,
                    timestamp_nanos);

  // The latest offset estimate is the nearest temperature offset.
  over_temp_cal->nearest_offset = over_temp_cal->latest_offset;

  // The rules for determining whether a new model fit is computed are:
  //    1) A minimum number of data points must have been collected:
  //          num_model_pts >= min_num_model_pts
  //       NOTE: Collecting 'num_model_pts' and given that only one point is
  //       kept per temperature bin (spanning a thermal range specified by
  //       'delta_temp_per_bin') implies that model data covers at least,
  //          model_temperature_span >= 'num_model_pts' * delta_temp_per_bin
  //    2) ...shown in 'computeModelUpdate'.
  if (over_temp_cal->num_model_pts >= over_temp_cal->min_num_model_pts) {
    computeModelUpdate(over_temp_cal, timestamp_nanos);
  }

  // Updates the current over-temperature compensated offset estimate.
  updateCalOffset(over_temp_cal, timestamp_nanos, temperature_celsius);

#ifdef OVERTEMPCAL_DBG_ENABLED
  // Updates the total number of received sensor offset estimates.
  over_temp_cal->debug_num_estimates++;

  // Triggers a log printout to show the updated sensor offset estimate.
  updateDebugData(over_temp_cal);
#endif  // OVERTEMPCAL_DBG_ENABLED
}

void overTempCalSetTemperature(struct OverTempCal *over_temp_cal,
                               uint64_t timestamp_nanos,
                               float temperature_celsius) {
  ASSERT_NOT_NULL(over_temp_cal);

#ifdef OVERTEMPCAL_DBG_ENABLED
#ifdef OVERTEMPCAL_DBG_LOG_TEMP
  // Prints the sensor temperature trajectory for debugging purposes. This
  // throttles the print statements (1Hz).
  if (NANO_TIMER_CHECK_T1_GEQUAL_T2_PLUS_DELTA(
          timestamp_nanos, over_temp_cal->temperature_print_timer,
          OTC_PRINT_TEMP_NANOS)) {
    over_temp_cal->temperature_print_timer =
        timestamp_nanos;  // Starts the wait timer.

    // Prints out temperature and the current timestamp.
    createDebugTag(over_temp_cal, ":TEMP]");
    CAL_DEBUG_LOG(over_temp_cal->otc_debug_tag,
                  "Temperature|Time [C|nsec] = " CAL_FORMAT_3DIGITS ", %llu",
                  CAL_ENCODE_FLOAT(temperature_celsius, 3),
                  (unsigned long long int)timestamp_nanos);
  }
#endif  // OVERTEMPCAL_DBG_LOG_TEMP
#endif  // OVERTEMPCAL_DBG_ENABLED

  // This check throttles new OTC offset compensation updates so that high data
  // rate temperature samples do not cause excessive computational burden. Note,
  // temperature sensor updates are expected to potentially increase the data
  // processing load, however, computational load from new offset estimates is
  // not a concern as they are a typically provided at a very low rate (< 1 Hz).
  if (!NANO_TIMER_CHECK_T1_GEQUAL_T2_PLUS_DELTA(
          timestamp_nanos, over_temp_cal->last_offset_update_nanos,
          over_temp_cal->min_temp_update_period_nanos)) {
    return; // Time interval too short, skip further data processing.
  }

  // Checks that the offset temperature is within a valid range, saturates if
  // outside.
  checkAndEnforceTemperatureRange(&temperature_celsius);

  // Searches for the sensor offset estimate closest to the current temperature
  // when the temperature has changed by more than +/-10% of the
  // 'delta_temp_per_bin'.
  if (over_temp_cal->num_model_pts > 0) {
    if (NANO_ABS(over_temp_cal->last_temp_check_celsius - temperature_celsius) >
        0.1f * over_temp_cal->delta_temp_per_bin) {
      findNearestEstimate(over_temp_cal, temperature_celsius);
      over_temp_cal->last_temp_check_celsius = temperature_celsius;
    }
  }

  // Updates the current over-temperature compensated offset estimate.
  updateCalOffset(over_temp_cal, timestamp_nanos, temperature_celsius);

  // Sets the OTC offset compensation time check.
  over_temp_cal->last_offset_update_nanos = timestamp_nanos;
}

void overTempGetModelError(const struct OverTempCal *over_temp_cal,
                   const float *temp_sensitivity, const float *sensor_intercept,
                   float *max_error) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(temp_sensitivity);
  ASSERT_NOT_NULL(sensor_intercept);
  ASSERT_NOT_NULL(max_error);

  float max_error_test;
  memset(max_error, 0, 3 * sizeof(float));

  for (size_t i = 0; i < over_temp_cal->num_model_pts; i++) {
    for (size_t j = 0; j < 3; j++) {
      max_error_test =
          NANO_ABS(over_temp_cal->model_data[i].offset[j] -
                   (temp_sensitivity[j] *
                        over_temp_cal->model_data[i].offset_temp_celsius +
                    sensor_intercept[j]));
      if (max_error_test > max_error[j]) {
        max_error[j] = max_error_test;
      }
    }
  }
}

// TODO(davejacobs): Refactor to implement a compliance check on the storage of
// 'offset_age_nanos' to ensure a monotonically increasing order with index.
void overTempSetWeightingFunction(
    struct OverTempCal *over_temp_cal, size_t index,
    const struct OverTempCalWeightPt *new_otc_weight) {
  if (index < OTC_NUM_WEIGHT_LEVELS) {
    over_temp_cal->weighting_function[index] = *new_otc_weight;
  }
}

/////// LOCAL HELPER FUNCTION DEFINITIONS /////////////////////////////////////

void compensateWithLinearModel(struct OverTempCal *over_temp_cal,
                               uint64_t timestamp_nanos,
                               float temperature_celsius) {
  ASSERT_NOT_NULL(over_temp_cal);

  // Defaults to using the current compensated offset value.
  float compensated_offset[3];
  memcpy(compensated_offset, over_temp_cal->compensated_offset.offset,
         sizeof(over_temp_cal->compensated_offset.offset));

  for (size_t index = 0; index < 3; index++) {
    if (over_temp_cal->temp_sensitivity[index] < OTC_INITIAL_SENSITIVITY) {
      // If a valid axis model is defined then the default compensation will
      // use the linear model:
      //   compensated_offset = (temp_sensitivity * temperature +
      //   sensor_intercept)
      compensated_offset[index] =
          over_temp_cal->temp_sensitivity[index] * temperature_celsius +
          over_temp_cal->sensor_intercept[index];
    }
  }

  // Sets the offset compensation vector, temperature, and timestamp.
  setCompensatedOffset(over_temp_cal, compensated_offset, timestamp_nanos,
                       temperature_celsius);
}

void addLinearTemperatureExtrapolation(struct OverTempCal *over_temp_cal,
                                       float *compensated_offset,
                                       float delta_temp_celsius) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(compensated_offset);

  // Adds a delta term to the 'compensated_offset' using the temperature
  // difference defined by 'delta_temp_celsius'.
  for (size_t index = 0; index < 3; index++) {
    if (over_temp_cal->temp_sensitivity[index] < OTC_INITIAL_SENSITIVITY) {
      // If a valid axis model is defined, then use the linear model to assist
      // with computing an extrapolated compensation term.
      compensated_offset[index] +=
          over_temp_cal->temp_sensitivity[index] * delta_temp_celsius;
    }
  }
}

void compensateWithEstimate(
    struct OverTempCal *over_temp_cal, uint64_t timestamp_nanos,
    struct OverTempCalDataPt *estimate, float temperature_celsius) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(estimate);

  // Uses the most recent offset estimate for offset compensation.
  float compensated_offset[3];
  memcpy(compensated_offset, estimate->offset, sizeof(compensated_offset));

  // Checks that the offset temperature is valid.
  if (estimate->offset_temp_celsius > OTC_TEMP_INVALID_CELSIUS) {
    const float delta_temp_celsius =
        temperature_celsius - estimate->offset_temp_celsius;

    // Adds a delta term to the compensated offset using the temperature
    // difference defined by 'delta_temp_celsius'.
    addLinearTemperatureExtrapolation(over_temp_cal, compensated_offset,
                                      delta_temp_celsius);
  }

  // Sets the offset compensation vector, temperature, and timestamp.
  setCompensatedOffset(over_temp_cal, compensated_offset, timestamp_nanos,
                       temperature_celsius);
}

void compareAndCompensateWithNearest(struct OverTempCal *over_temp_cal,
                                     uint64_t timestamp_nanos,
                                     float temperature_celsius,
                                     bool compare_to_linear_model) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(over_temp_cal->nearest_offset);

  // The default compensated offset is the nearest-temperature offset vector.
  float compensated_offset[3];
  memcpy(compensated_offset, over_temp_cal->nearest_offset->offset,
         sizeof(compensated_offset));
  const float compensated_offset_temperature_celsius =
      over_temp_cal->nearest_offset->offset_temp_celsius;

  for (size_t index = 0; index < 3; index++) {
    if (over_temp_cal->temp_sensitivity[index] < OTC_INITIAL_SENSITIVITY) {
      // If a valid axis model is defined, then use the linear model to assist
      // with computing an extrapolated compensation term.
      float delta_temp_celsius =
          temperature_celsius - compensated_offset_temperature_celsius;
      compensated_offset[index] +=
          over_temp_cal->temp_sensitivity[index] * delta_temp_celsius;

      // Computes the test offset (based on the linear model or current offset).
      float test_offset;
      if (compare_to_linear_model) {
        test_offset =
            over_temp_cal->temp_sensitivity[index] * temperature_celsius +
            over_temp_cal->sensor_intercept[index];
      } else {
        // Adds a delta term to the compensated offset using the temperature
        // difference defined by 'delta_temp_celsius'.
        if (over_temp_cal->compensated_offset.offset_temp_celsius <=
            OTC_TEMP_INVALID_CELSIUS) {
          // If temperature is invalid, then skip further processing.
          break;
        }
        delta_temp_celsius =
            temperature_celsius -
            over_temp_cal->compensated_offset.offset_temp_celsius;
        test_offset =
            over_temp_cal->compensated_offset.offset[index] +
            over_temp_cal->temp_sensitivity[index] * delta_temp_celsius;
      }

      // Checks for "jumps" in the candidate compensated offset. If detected,
      // then 'test_offset' is used for the offset update.
      if (NANO_ABS(test_offset - compensated_offset[index]) >=
          over_temp_cal->jump_tolerance) {
        compensated_offset[index] = test_offset;
      }
    }
  }

  // Sets the offset compensation vector, temperature, and timestamp.
  setCompensatedOffset(over_temp_cal, compensated_offset, timestamp_nanos,
                       temperature_celsius);
}

void updateCalOffset(struct OverTempCal *over_temp_cal,
                     uint64_t timestamp_nanos, float temperature_celsius) {
  ASSERT_NOT_NULL(over_temp_cal);

  // If 'temperature_celsius' is invalid, then no changes to the compensated
  // offset are computed.
  if (temperature_celsius <= OTC_TEMP_INVALID_CELSIUS) {
    return;
  }

  // Removes very old data from the collected model estimates (i.e.,
  // eliminates drift-compromised data). Only does this when there is more
  // than one estimate in the model (i.e., don't want to remove all data, even
  // if it is very old [something is likely better than nothing]).
  if ((timestamp_nanos >=
       OTC_STALE_CHECK_TIME_NANOS + over_temp_cal->stale_data_timer) &&
      over_temp_cal->num_model_pts > 1) {
    over_temp_cal->stale_data_timer = timestamp_nanos;  // Resets timer.
    removeStaleModelData(over_temp_cal, timestamp_nanos);
  }

  // Ensures that the most relevant model weighting is being used.
  refreshOtcModel(over_temp_cal, timestamp_nanos);

  // ---------------------------------------------------------------------------
  // The following boolean expressions help determine how OTC offset updates
  // are computed below.

  // The nearest-temperature offset estimate is valid if the model data set is
  // not empty.
  const bool model_points_available = (over_temp_cal->num_model_pts > 0);

  // True when the latest offset estimate will be used to compute a sensor
  // offset calibration estimate.
  const bool use_latest_offset_compensation =
      over_temp_cal->latest_offset && model_points_available &&
      timestamp_nanos < over_temp_cal->latest_offset->timestamp_nanos +
                            OTC_USE_RECENT_OFFSET_TIME_NANOS;

  // True when the conditions are met to use the nearest-temperature offset to
  // compute a sensor offset calibration estimate.
  //  The nearest-temperature offset:
  //    i.  Must be defined.
  //    ii. Offset temperature must be within a small neighborhood of the
  //        current measured temperature (+/- 'delta_temp_per_bin').
  const bool can_compensate_with_nearest =
      model_points_available && over_temp_cal->nearest_offset &&
      NANO_ABS(temperature_celsius -
               over_temp_cal->nearest_offset->offset_temp_celsius) <
          over_temp_cal->delta_temp_per_bin;

  // True if the last received sensor offset estimate is old or non-existent.
  const bool latest_model_point_not_relevant =
      (over_temp_cal->latest_offset == NULL) ||
      (over_temp_cal->latest_offset &&
       NANO_TIMER_CHECK_T1_GEQUAL_T2_PLUS_DELTA(
           timestamp_nanos, over_temp_cal->latest_offset->timestamp_nanos,
           OTC_OFFSET_IS_STALE_NANOS));

  // True if the nearest-temperature offset estimate is old or non-existent.
  const bool nearest_model_point_not_relevant =
      (over_temp_cal->nearest_offset == NULL) ||
      (over_temp_cal->nearest_offset &&
       NANO_TIMER_CHECK_T1_GEQUAL_T2_PLUS_DELTA(
           timestamp_nanos, over_temp_cal->nearest_offset->timestamp_nanos,
           OTC_OFFSET_IS_STALE_NANOS));

  // ---------------------------------------------------------------------------
  // The following conditional expressions govern new OTC offset updates.

  if (!model_points_available) {
    // Computes the compensation using just the linear model if available,
    // otherwise the current compensated offset vector will be kept.
    compensateWithLinearModel(over_temp_cal, timestamp_nanos,
                              temperature_celsius);
    return;  // no further calculations, exit early.
  }

  if (use_latest_offset_compensation) {
    // Computes the compensation using the latest received offset estimate plus
    // a term based on linear extrapolation from the offset temperature to the
    // current measured temperature (if a linear model is defined).
    compensateWithEstimate(over_temp_cal, timestamp_nanos,
                           over_temp_cal->latest_offset, temperature_celsius);
    return;  // no further calculations, exit early.
  }

  if (can_compensate_with_nearest) {
    // Evaluates the nearest-temperature compensation (with a linear
    // extrapolation term), and compares it with the compensation due to just
    // the linear model, when 'compare_with_linear_model' is true. Otherwise,
    // the comparison will be made with an extrapolated version of the current
    // compensation value. The comparison determines whether the
    // nearest-temperature estimate deviates from the linear-model (or
    // current-compensated) value by more than 'jump_tolerance'. If a "jump" is
    // detected, then it keeps the linear-model (or current-compensated) value.
    const bool compare_with_linear_model = nearest_model_point_not_relevant;
    compareAndCompensateWithNearest(over_temp_cal, timestamp_nanos,
                                    temperature_celsius,
                                    compare_with_linear_model);
  } else {
    if (latest_model_point_not_relevant) {
      // If the nearest-temperature offset can't be used for compensation and
      // the latest offset is stale (in this case, the overall model trend may
      // be more useful for compensation than extending the most recent vector),
      // then this resorts to using only the linear model (if defined).
      compensateWithLinearModel(over_temp_cal, timestamp_nanos,
                                temperature_celsius);
    } else {
      // If the nearest-temperature offset can't be used for compensation and
      // the latest offset is fairly recent, then the compensated offset is
      // based on the linear extrapolation of the current compensation vector.
      compensateWithEstimate(over_temp_cal, timestamp_nanos,
                             &over_temp_cal->compensated_offset,
                             temperature_celsius);
    }
  }
}

void setCompensatedOffset(struct OverTempCal *over_temp_cal,
                          const float *compensated_offset,
                          uint64_t timestamp_nanos, float temperature_celsius) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(compensated_offset);

  // If the 'compensated_offset' value has changed significantly, then set
  // 'new_overtemp_offset_available' true.
  bool new_overtemp_offset_available = false;
  for (size_t i = 0; i < 3; i++) {
    if (NANO_ABS(over_temp_cal->compensated_offset.offset[i] -
                 compensated_offset[i]) >=
        over_temp_cal->significant_offset_change) {
      new_overtemp_offset_available |= true;
      break;
    }
  }
  over_temp_cal->new_overtemp_offset_available |= new_overtemp_offset_available;

  // If the offset has changed significantly, then the offset compensation
  // vector and timestamp are updated.
  if (new_overtemp_offset_available) {
    memcpy(over_temp_cal->compensated_offset.offset, compensated_offset,
           sizeof(over_temp_cal->compensated_offset.offset));
    over_temp_cal->compensated_offset.timestamp_nanos = timestamp_nanos;
    over_temp_cal->compensated_offset.offset_temp_celsius = temperature_celsius;
  }
}

void setLatestEstimate(struct OverTempCal *over_temp_cal, const float *offset,
                       float offset_temp_celsius, uint64_t timestamp_nanos) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(offset);

  if (over_temp_cal->latest_offset) {
    // Sets the latest over-temp calibration estimate.
    memcpy(over_temp_cal->latest_offset->offset, offset,
           sizeof(over_temp_cal->latest_offset->offset));
    over_temp_cal->latest_offset->offset_temp_celsius = offset_temp_celsius;
    over_temp_cal->latest_offset->timestamp_nanos = timestamp_nanos;
  }
}

void refreshOtcModel(struct OverTempCal *over_temp_cal,
                     uint64_t timestamp_nanos) {
  ASSERT_NOT_NULL(over_temp_cal);
  if (NANO_TIMER_CHECK_T1_GEQUAL_T2_PLUS_DELTA(
          timestamp_nanos, over_temp_cal->last_model_update_nanos,
          OTC_REFRESH_MODEL_NANOS)) {
    // Checks the time since the last computed model and recalculates the model
    // if necessary. This ensures that waking up after a long period of time
    // allows the properly weighted OTC model to be used. As the estimates age,
    // the weighting will become more uniform and the model will fit the whole
    // set uniformly as a better approximation to the expected temperature
    // sensitivity; Younger estimates will fit tighter to emphasize a more
    // localized fit of the temp sensitivity function.
    computeModelUpdate(over_temp_cal, timestamp_nanos);
    over_temp_cal->last_model_update_nanos = timestamp_nanos;
  }
}

void computeModelUpdate(struct OverTempCal *over_temp_cal,
                        uint64_t timestamp_nanos) {
  ASSERT_NOT_NULL(over_temp_cal);

  // Ensures that the minimum number of points required for a model fit has been
  // satisfied.
  if (over_temp_cal->num_model_pts < over_temp_cal->min_num_model_pts)
      return;

  // Updates the linear model fit.
  float temp_sensitivity[3];
  float sensor_intercept[3];
  updateModel(over_temp_cal, timestamp_nanos, temp_sensitivity,
              sensor_intercept);

  //    2) A new set of model parameters are accepted if:
  //         i. The model fit parameters must be within certain absolute bounds:
  //              a. NANO_ABS(temp_sensitivity) < temp_sensitivity_limit
  //              b. NANO_ABS(sensor_intercept) < sensor_intercept_limit
  // NOTE: Model parameter updates are not qualified against model fit error
  // here to protect against the case where there is large change in the
  // temperature characteristic either during runtime (e.g., temperature
  // conditioning due to hysteresis) or as a result of loading a poor model data
  // set. Otherwise, a lockout condition could occur where the entire model
  // data set would need to be replaced in order to bring the model fit error
  // below the error limit and allow a successful model update.
  bool updated_one = false;
  for (size_t i = 0; i < 3; i++) {
    if (isValidOtcLinearModel(over_temp_cal, temp_sensitivity[i],
                              sensor_intercept[i])) {
      over_temp_cal->temp_sensitivity[i] = temp_sensitivity[i];
      over_temp_cal->sensor_intercept[i] = sensor_intercept[i];
      updated_one = true;
    } else {
#ifdef OVERTEMPCAL_DBG_ENABLED
      createDebugTag(over_temp_cal, ":REJECT]");
      CAL_DEBUG_LOG(
          over_temp_cal->otc_debug_tag,
          "%c-Axis Parameters|Time [%s/C|%s|nsec]: " CAL_FORMAT_3DIGITS
          ", " CAL_FORMAT_3DIGITS ", %llu",
          kDebugAxisLabel[i], over_temp_cal->otc_unit_tag,
          over_temp_cal->otc_unit_tag,
          CAL_ENCODE_FLOAT(
              temp_sensitivity[i] * over_temp_cal->otc_unit_conversion, 3),
          CAL_ENCODE_FLOAT(
              sensor_intercept[i] * over_temp_cal->otc_unit_conversion, 3),
          (unsigned long long int)timestamp_nanos);
#endif  // OVERTEMPCAL_DBG_ENABLED
    }
  }

  // If at least one axis updated, then consider this a valid model update.
  if (updated_one) {
    // Resets the OTC model compensation update time and sets the update flag.
    over_temp_cal->last_model_update_nanos = timestamp_nanos;
    over_temp_cal->new_overtemp_model_available = true;

#ifdef OVERTEMPCAL_DBG_ENABLED
    // Updates the total number of model updates.
    over_temp_cal->debug_num_model_updates++;
#endif  // OVERTEMPCAL_DBG_ENABLED
  }
}

void findNearestEstimate(struct OverTempCal *over_temp_cal,
                         float temperature_celsius) {
  ASSERT_NOT_NULL(over_temp_cal);

  // If 'temperature_celsius' is invalid, then do not search.
  if (temperature_celsius <= OTC_TEMP_INVALID_CELSIUS) {
    return;
  }

  // Performs a brute force search for the estimate nearest
  // 'temperature_celsius'.
  float dtemp_new = 0.0f;
  float dtemp_old = FLT_MAX;
  over_temp_cal->nearest_offset = &over_temp_cal->model_data[0];
  for (size_t i = 0; i < over_temp_cal->num_model_pts; i++) {
    dtemp_new = NANO_ABS(over_temp_cal->model_data[i].offset_temp_celsius -
                         temperature_celsius);
    if (dtemp_new < dtemp_old) {
      over_temp_cal->nearest_offset = &over_temp_cal->model_data[i];
      dtemp_old = dtemp_new;
    }
  }
}

void removeStaleModelData(struct OverTempCal *over_temp_cal,
                          uint64_t timestamp_nanos) {
  ASSERT_NOT_NULL(over_temp_cal);

  bool removed_one = false;
  for (size_t i = 0; i < over_temp_cal->num_model_pts; i++) {
    if (timestamp_nanos > over_temp_cal->model_data[i].timestamp_nanos &&
        timestamp_nanos > over_temp_cal->age_limit_nanos +
                              over_temp_cal->model_data[i].timestamp_nanos) {
      // If the latest offset was removed, then indicate this by setting it to
      // NULL.
      if (over_temp_cal->latest_offset == &over_temp_cal->model_data[i]) {
        over_temp_cal->latest_offset = NULL;
      }
      removed_one |= removeModelDataByIndex(over_temp_cal, i);
    }
  }

  if (removed_one) {
    // If anything was removed, then this attempts to recompute the model.
    computeModelUpdate(over_temp_cal, timestamp_nanos);

    // Searches for the sensor offset estimate closest to the current
    // temperature.
    findNearestEstimate(over_temp_cal,
                        over_temp_cal->compensated_offset.offset_temp_celsius);
  }
}

bool removeModelDataByIndex(struct OverTempCal *over_temp_cal,
                            size_t model_index) {
  ASSERT_NOT_NULL(over_temp_cal);

  // This function will not remove all of the model data. At least one model
  // sample will be left.
  if (over_temp_cal->num_model_pts <= 1) {
    return false;
  }

#ifdef OVERTEMPCAL_DBG_ENABLED
  createDebugTag(over_temp_cal, ":REMOVE]");
  CAL_DEBUG_LOG(
      over_temp_cal->otc_debug_tag,
      "Offset|Temp|Time [%s|C|nsec]: " CAL_FORMAT_3DIGITS_TRIPLET
      ", " CAL_FORMAT_3DIGITS ", %llu",
      over_temp_cal->otc_unit_tag,
      CAL_ENCODE_FLOAT(over_temp_cal->model_data[model_index].offset[0] *
                           over_temp_cal->otc_unit_conversion,
                       3),
      CAL_ENCODE_FLOAT(over_temp_cal->model_data[model_index].offset[1] *
                           over_temp_cal->otc_unit_conversion,
                       3),
      CAL_ENCODE_FLOAT(over_temp_cal->model_data[model_index].offset[1] *
                           over_temp_cal->otc_unit_conversion,
                       3),
      CAL_ENCODE_FLOAT(
          over_temp_cal->model_data[model_index].offset_temp_celsius, 3),
      (unsigned long long int)over_temp_cal->model_data[model_index]
          .timestamp_nanos);
#endif  // OVERTEMPCAL_DBG_ENABLED

  // Remove the model data at 'model_index'.
  for (size_t i = model_index; i < over_temp_cal->num_model_pts - 1; i++) {
    memcpy(&over_temp_cal->model_data[i], &over_temp_cal->model_data[i + 1],
           sizeof(struct OverTempCalDataPt));
  }
  over_temp_cal->num_model_pts--;

  return true;
}

bool jumpStartModelData(struct OverTempCal *over_temp_cal,
                        uint64_t timestamp_nanos) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT(over_temp_cal->delta_temp_per_bin > 0);

  // Prevent a divide by zero below.
  if (over_temp_cal->delta_temp_per_bin <= 0) {
    return false;
  }

  // In normal operation the offset estimates enter into the 'model_data' array
  // complete (i.e., x, y, z values are all provided). Therefore, the jumpstart
  // data produced here requires that the model parameters have all been fully
  // defined and are all within the valid range.
  for (size_t i = 0; i < 3; i++) {
    if (!isValidOtcLinearModel(over_temp_cal,
                               over_temp_cal->temp_sensitivity[i],
                               over_temp_cal->sensor_intercept[i])) {
      return false;
    }
  }

  // Any pre-existing model data points will be overwritten.
  over_temp_cal->num_model_pts = 0;

  // This defines the minimum contiguous set of points to allow a model update
  // when the next offset estimate is received. They are placed at a common
  // temperature range that is likely to get replaced with actual data soon.
  const int32_t start_bin_num = CAL_FLOOR(JUMPSTART_START_TEMP_CELSIUS /
                                          over_temp_cal->delta_temp_per_bin);
  float offset_temp_celsius =
      (start_bin_num + 0.5f) * over_temp_cal->delta_temp_per_bin;

  for (size_t i = 0; i < over_temp_cal->min_num_model_pts; i++) {
    for (size_t j = 0; j < 3; j++) {
      over_temp_cal->model_data[i].offset[j] =
          over_temp_cal->temp_sensitivity[j] * offset_temp_celsius +
          over_temp_cal->sensor_intercept[j];
    }
    over_temp_cal->model_data[i].offset_temp_celsius = offset_temp_celsius;
    over_temp_cal->model_data[i].timestamp_nanos = timestamp_nanos;

    offset_temp_celsius += over_temp_cal->delta_temp_per_bin;
    over_temp_cal->num_model_pts++;
  }

#ifdef OVERTEMPCAL_DBG_ENABLED
  createDebugTag(over_temp_cal, ":INIT]");
  if (over_temp_cal->num_model_pts > 0) {
    CAL_DEBUG_LOG(over_temp_cal->otc_debug_tag,
                  "Model Jump-Start:  #Points = %lu.",
                  (unsigned long int)over_temp_cal->num_model_pts);
  }
#endif  // OVERTEMPCAL_DBG_ENABLED

  return (over_temp_cal->num_model_pts > 0);
}

void updateModel(const struct OverTempCal *over_temp_cal,
                 uint64_t timestamp_nanos, float *temp_sensitivity,
                 float *sensor_intercept) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(temp_sensitivity);
  ASSERT_NOT_NULL(sensor_intercept);
  ASSERT(over_temp_cal->num_model_pts > 0);

  float sw = 0.0f;
  float st = 0.0f, stt = 0.0f;
  float sx = 0.0f, stsx = 0.0f;
  float sy = 0.0f, stsy = 0.0f;
  float sz = 0.0f, stsz = 0.0f;
  float weight = 1.0f;

  // First pass computes the weighted mean values.
  const size_t n = over_temp_cal->num_model_pts;
  for (size_t i = 0; i < n; ++i) {
    weight = evaluateWeightingFunction(
        over_temp_cal, over_temp_cal->model_data[i].timestamp_nanos,
        timestamp_nanos);

    sw += weight;
    st += over_temp_cal->model_data[i].offset_temp_celsius * weight;
    sx += over_temp_cal->model_data[i].offset[0] * weight;
    sy += over_temp_cal->model_data[i].offset[1] * weight;
    sz += over_temp_cal->model_data[i].offset[2] * weight;
  }

  // Second pass computes the mean corrected second moment values.
  ASSERT(sw > 0.0f);
  const float inv_sw = 1.0f / sw;
  for (size_t i = 0; i < n; ++i) {
    weight = evaluateWeightingFunction(
        over_temp_cal, over_temp_cal->model_data[i].timestamp_nanos,
        timestamp_nanos);

    const float t =
        over_temp_cal->model_data[i].offset_temp_celsius -
        st * inv_sw;
    stt +=  weight * t * t;
    stsx += t * over_temp_cal->model_data[i].offset[0] * weight;
    stsy += t * over_temp_cal->model_data[i].offset[1] * weight;
    stsz += t * over_temp_cal->model_data[i].offset[2] * weight;
  }

  // Calculates the linear model fit parameters.
  ASSERT(stt > 0.0f);
  const float inv_stt = 1.0f / stt;
  temp_sensitivity[0] = stsx * inv_stt;
  sensor_intercept[0] = (sx - st * temp_sensitivity[0]) * inv_sw;
  temp_sensitivity[1] = stsy * inv_stt;
  sensor_intercept[1] = (sy - st * temp_sensitivity[1]) * inv_sw;
  temp_sensitivity[2] = stsz * inv_stt;
  sensor_intercept[2] = (sz - st * temp_sensitivity[2]) * inv_sw;
}

bool outlierCheck(struct OverTempCal *over_temp_cal, const float *offset,
                  size_t axis_index, float temperature_celsius) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(offset);

  // If a model has been defined, then check to see if this offset could be a
  // potential outlier:
  if (over_temp_cal->temp_sensitivity[axis_index] < OTC_INITIAL_SENSITIVITY) {
    const float outlier_test = NANO_ABS(
        offset[axis_index] -
        (over_temp_cal->temp_sensitivity[axis_index] * temperature_celsius +
         over_temp_cal->sensor_intercept[axis_index]));

    if (outlier_test > over_temp_cal->outlier_limit) {
      return true;
    }
  }

  return false;
}

void resetOtcLinearModel(struct OverTempCal *over_temp_cal) {
  ASSERT_NOT_NULL(over_temp_cal);

  // Sets the temperature sensitivity model parameters to
  // OTC_INITIAL_SENSITIVITY to indicate that the model is in an "initial"
  // state.
  over_temp_cal->temp_sensitivity[0] = OTC_INITIAL_SENSITIVITY;
  over_temp_cal->temp_sensitivity[1] = OTC_INITIAL_SENSITIVITY;
  over_temp_cal->temp_sensitivity[2] = OTC_INITIAL_SENSITIVITY;
  memset(over_temp_cal->sensor_intercept, 0,
         sizeof(over_temp_cal->sensor_intercept));
}

bool checkAndEnforceTemperatureRange(float *temperature_celsius) {
  if (*temperature_celsius > OTC_TEMP_MAX_CELSIUS) {
    *temperature_celsius = OTC_TEMP_MAX_CELSIUS;
    return false;
  }
  if (*temperature_celsius < OTC_TEMP_MIN_CELSIUS) {
    *temperature_celsius = OTC_TEMP_MIN_CELSIUS;
    return false;
  }
  return true;
}

bool isValidOtcLinearModel(const struct OverTempCal *over_temp_cal,
                           float temp_sensitivity, float sensor_intercept) {
  ASSERT_NOT_NULL(over_temp_cal);

  return NANO_ABS(temp_sensitivity) < over_temp_cal->temp_sensitivity_limit &&
         NANO_ABS(sensor_intercept) < over_temp_cal->sensor_intercept_limit &&
         NANO_ABS(temp_sensitivity) > OTC_MODELDATA_NEAR_ZERO_TOL &&
         NANO_ABS(sensor_intercept) > OTC_MODELDATA_NEAR_ZERO_TOL;
}

bool isValidOtcOffset(const float *offset, float offset_temp_celsius) {
  ASSERT_NOT_NULL(offset);

  // Simple check to ensure that:
  //   1. All of the input data is non "zero".
  //   2. The offset temperature is within the valid range.
  if (NANO_ABS(offset[0]) < OTC_MODELDATA_NEAR_ZERO_TOL &&
      NANO_ABS(offset[1]) < OTC_MODELDATA_NEAR_ZERO_TOL &&
      NANO_ABS(offset[2]) < OTC_MODELDATA_NEAR_ZERO_TOL &&
      NANO_ABS(offset_temp_celsius) < OTC_MODELDATA_NEAR_ZERO_TOL) {
    return false;
  }

  // Only returns the "check" result. Don't care about coercion.
  return checkAndEnforceTemperatureRange(&offset_temp_celsius);
}

float evaluateWeightingFunction(const struct OverTempCal *over_temp_cal,
                                uint64_t offset_timestamp_nanos,
                                uint64_t current_timestamp_nanos) {
  ASSERT_NOT_NULL(over_temp_cal);
  for (size_t i = 0; i < OTC_NUM_WEIGHT_LEVELS; i++) {
    if (current_timestamp_nanos <=
        offset_timestamp_nanos +
            over_temp_cal->weighting_function[i].offset_age_nanos) {
      return over_temp_cal->weighting_function[i].weight;
    }
  }

  // Returning the default weight for all older offsets.
  return OTC_MIN_WEIGHT_VALUE;
}

/////// DEBUG FUNCTION DEFINITIONS ////////////////////////////////////////////

#ifdef OVERTEMPCAL_DBG_ENABLED
void createDebugTag(struct OverTempCal *over_temp_cal,
                    const char *new_debug_tag) {
  over_temp_cal->otc_debug_tag[0] = '[';
  memcpy(over_temp_cal->otc_debug_tag + 1, over_temp_cal->otc_sensor_tag,
         strlen(over_temp_cal->otc_sensor_tag));
  memcpy(
      over_temp_cal->otc_debug_tag + strlen(over_temp_cal->otc_sensor_tag) + 1,
      new_debug_tag, strlen(new_debug_tag) + 1);
}

void updateDebugData(struct OverTempCal* over_temp_cal) {
  ASSERT_NOT_NULL(over_temp_cal);

  // Only update this data if debug printing is not currently in progress
  // (i.e., don't want to risk overwriting debug information that is actively
  // being reported).
  if (over_temp_cal->debug_state != OTC_IDLE) {
    return;
  }

  // Triggers a debug log printout.
  over_temp_cal->debug_print_trigger = true;

  // Initializes the debug data structure.
  memset(&over_temp_cal->debug_overtempcal, 0, sizeof(struct DebugOverTempCal));

  // Copies over the relevant data.
  for (size_t i = 0; i < 3; i++) {
    if (isValidOtcLinearModel(over_temp_cal, over_temp_cal->temp_sensitivity[i],
                              over_temp_cal->sensor_intercept[i])) {
      over_temp_cal->debug_overtempcal.temp_sensitivity[i] =
          over_temp_cal->temp_sensitivity[i];
      over_temp_cal->debug_overtempcal.sensor_intercept[i] =
          over_temp_cal->sensor_intercept[i];
    } else {
      // If the model is not valid then just set the debug information so that
      // zeros are printed.
      over_temp_cal->debug_overtempcal.temp_sensitivity[i] = 0.0f;
      over_temp_cal->debug_overtempcal.sensor_intercept[i] = 0.0f;
    }
  }

  // If 'latest_offset' is defined the copy the data for debug printing.
  // Otherwise, the current compensated offset will be printed.
  if (over_temp_cal->latest_offset) {
    memcpy(&over_temp_cal->debug_overtempcal.latest_offset,
           over_temp_cal->latest_offset, sizeof(struct OverTempCalDataPt));
  } else {
    memcpy(&over_temp_cal->debug_overtempcal.latest_offset,
           &over_temp_cal->compensated_offset,
           sizeof(struct OverTempCalDataPt));
  }

  // Total number of OTC model data points.
  over_temp_cal->debug_overtempcal.num_model_pts = over_temp_cal->num_model_pts;

  // Computes the maximum error over all of the model data.
  overTempGetModelError(over_temp_cal,
                over_temp_cal->debug_overtempcal.temp_sensitivity,
                over_temp_cal->debug_overtempcal.sensor_intercept,
                over_temp_cal->debug_overtempcal.max_error);
}

void overTempCalDebugPrint(struct OverTempCal *over_temp_cal,
                           uint64_t timestamp_nanos) {
  ASSERT_NOT_NULL(over_temp_cal);

  // This is a state machine that controls the reporting out of debug data.
  createDebugTag(over_temp_cal, ":REPORT]");
  switch (over_temp_cal->debug_state) {
    case OTC_IDLE:
      // Wait for a trigger and start the debug printout sequence.
      if (over_temp_cal->debug_print_trigger) {
        CAL_DEBUG_LOG(over_temp_cal->otc_debug_tag, "");
        CAL_DEBUG_LOG(over_temp_cal->otc_debug_tag, "Debug Version: %s",
                      OTC_DEBUG_VERSION_STRING);
        over_temp_cal->debug_print_trigger = false;  // Resets trigger.
        over_temp_cal->debug_state = OTC_PRINT_OFFSET;
      } else {
        over_temp_cal->debug_state = OTC_IDLE;
      }
      break;

    case OTC_WAIT_STATE:
      // This helps throttle the print statements.
      if (NANO_TIMER_CHECK_T1_GEQUAL_T2_PLUS_DELTA(
              timestamp_nanos, over_temp_cal->wait_timer_nanos,
              OTC_WAIT_TIME_NANOS)) {
        over_temp_cal->debug_state = over_temp_cal->next_state;
      }
      break;

    case OTC_PRINT_OFFSET:
      // Prints out the latest offset estimate (input data).
      CAL_DEBUG_LOG(
          over_temp_cal->otc_debug_tag,
          "Cal#|Offset|Temp|Time [%s|C|nsec]: %lu, " CAL_FORMAT_3DIGITS_TRIPLET
          ", " CAL_FORMAT_3DIGITS ", %llu",
          over_temp_cal->otc_unit_tag,
          (unsigned long int)over_temp_cal->debug_num_estimates,
          CAL_ENCODE_FLOAT(
              over_temp_cal->debug_overtempcal.latest_offset.offset[0] *
                  over_temp_cal->otc_unit_conversion,
              3),
          CAL_ENCODE_FLOAT(
              over_temp_cal->debug_overtempcal.latest_offset.offset[1] *
                  over_temp_cal->otc_unit_conversion,
              3),
          CAL_ENCODE_FLOAT(
              over_temp_cal->debug_overtempcal.latest_offset.offset[2] *
                  over_temp_cal->otc_unit_conversion,
              3),
          CAL_ENCODE_FLOAT(over_temp_cal->debug_overtempcal.latest_offset
                               .offset_temp_celsius,
                           3),
          (unsigned long long int)
              over_temp_cal->debug_overtempcal.latest_offset.timestamp_nanos);

      over_temp_cal->wait_timer_nanos =
          timestamp_nanos;                          // Starts the wait timer.
      over_temp_cal->next_state =
          OTC_PRINT_MODEL_PARAMETERS;               // Sets the next state.
      over_temp_cal->debug_state = OTC_WAIT_STATE;  // First, go to wait state.
      break;

    case OTC_PRINT_MODEL_PARAMETERS:
      // Prints out the model parameters.
      CAL_DEBUG_LOG(
          over_temp_cal->otc_debug_tag,
          "Cal#|Sensitivity [%s/C]: %lu, " CAL_FORMAT_3DIGITS_TRIPLET,
          over_temp_cal->otc_unit_tag,
          (unsigned long int)over_temp_cal->debug_num_estimates,
          CAL_ENCODE_FLOAT(
              over_temp_cal->debug_overtempcal.temp_sensitivity[0] *
                  over_temp_cal->otc_unit_conversion,
              3),
          CAL_ENCODE_FLOAT(
              over_temp_cal->debug_overtempcal.temp_sensitivity[1] *
                  over_temp_cal->otc_unit_conversion,
              3),
          CAL_ENCODE_FLOAT(
              over_temp_cal->debug_overtempcal.temp_sensitivity[2] *
                  over_temp_cal->otc_unit_conversion,
              3));

      CAL_DEBUG_LOG(over_temp_cal->otc_debug_tag,
                    "Cal#|Intercept [%s]: %lu, " CAL_FORMAT_3DIGITS_TRIPLET,
                    over_temp_cal->otc_unit_tag,
                    (unsigned long int)over_temp_cal->debug_num_estimates,
                    CAL_ENCODE_FLOAT(
                        over_temp_cal->debug_overtempcal.sensor_intercept[0] *
                            over_temp_cal->otc_unit_conversion,
                        3),
                    CAL_ENCODE_FLOAT(
                        over_temp_cal->debug_overtempcal.sensor_intercept[1] *
                            over_temp_cal->otc_unit_conversion,
                        3),
                    CAL_ENCODE_FLOAT(
                        over_temp_cal->debug_overtempcal.sensor_intercept[2] *
                            over_temp_cal->otc_unit_conversion,
                        3));

      over_temp_cal->wait_timer_nanos =
          timestamp_nanos;                          // Starts the wait timer.
      over_temp_cal->next_state = OTC_PRINT_MODEL_ERROR;  // Sets the next state.
      over_temp_cal->debug_state = OTC_WAIT_STATE;  // First, go to wait state.
      break;

    case OTC_PRINT_MODEL_ERROR:
      // Computes the maximum error over all of the model data.
      CAL_DEBUG_LOG(
          over_temp_cal->otc_debug_tag,
          "Cal#|#Updates|#ModelPts|Model Error [%s]: %lu, "
          "%lu, %lu, " CAL_FORMAT_3DIGITS_TRIPLET,
          over_temp_cal->otc_unit_tag,
          (unsigned long int)over_temp_cal->debug_num_estimates,
          (unsigned long int)over_temp_cal->debug_num_model_updates,
          (unsigned long int)over_temp_cal->debug_overtempcal.num_model_pts,
          CAL_ENCODE_FLOAT(over_temp_cal->debug_overtempcal.max_error[0] *
                               over_temp_cal->otc_unit_conversion,
                           3),
          CAL_ENCODE_FLOAT(over_temp_cal->debug_overtempcal.max_error[1] *
                               over_temp_cal->otc_unit_conversion,
                           3),
          CAL_ENCODE_FLOAT(over_temp_cal->debug_overtempcal.max_error[2] *
                               over_temp_cal->otc_unit_conversion,
                           3));

      over_temp_cal->model_counter = 0;  // Resets the model data print counter.
      over_temp_cal->wait_timer_nanos =
          timestamp_nanos;               // Starts the wait timer.
      over_temp_cal->next_state = OTC_PRINT_MODEL_DATA;  // Sets the next state.
      over_temp_cal->debug_state = OTC_WAIT_STATE;  // First, go to wait state.
      break;

    case OTC_PRINT_MODEL_DATA:
      // Prints out all of the model data.
      if (over_temp_cal->model_counter < over_temp_cal->num_model_pts) {
        CAL_DEBUG_LOG(
            over_temp_cal->otc_debug_tag,
            "  Model[%lu] [%s|C|nsec] = " CAL_FORMAT_3DIGITS_TRIPLET
            ", " CAL_FORMAT_3DIGITS ", %llu",
            (unsigned long int)over_temp_cal->model_counter,
            over_temp_cal->otc_unit_tag,
            CAL_ENCODE_FLOAT(
                over_temp_cal->model_data[over_temp_cal->model_counter]
                        .offset[0] *
                    over_temp_cal->otc_unit_conversion,
                3),
            CAL_ENCODE_FLOAT(
                over_temp_cal->model_data[over_temp_cal->model_counter]
                        .offset[1] *
                    over_temp_cal->otc_unit_conversion,
                3),
            CAL_ENCODE_FLOAT(
                over_temp_cal->model_data[over_temp_cal->model_counter]
                        .offset[2] *
                    over_temp_cal->otc_unit_conversion,
                3),
            CAL_ENCODE_FLOAT(
                over_temp_cal->model_data[over_temp_cal->model_counter]
                    .offset_temp_celsius,
                3),
            (unsigned long long int)over_temp_cal
                ->model_data[over_temp_cal->model_counter]
                .timestamp_nanos);

        over_temp_cal->model_counter++;
        over_temp_cal->wait_timer_nanos =
            timestamp_nanos;                        // Starts the wait timer.
        over_temp_cal->next_state =
            OTC_PRINT_MODEL_DATA;                   // Sets the next state.
        over_temp_cal->debug_state =
            OTC_WAIT_STATE;                         // First, go to wait state.
      } else {
        // Sends this state machine to its idle state.
        over_temp_cal->wait_timer_nanos =
            timestamp_nanos;                        // Starts the wait timer.
        over_temp_cal->next_state = OTC_IDLE;       // Sets the next state.
        over_temp_cal->debug_state =
            OTC_WAIT_STATE;                         // First, go to wait state.
      }
      break;

    default:
      // Sends this state machine to its idle state.
      over_temp_cal->wait_timer_nanos =
          timestamp_nanos;                          // Starts the wait timer.
      over_temp_cal->next_state = OTC_IDLE;         // Sets the next state.
      over_temp_cal->debug_state = OTC_WAIT_STATE;  // First, go to wait state.
  }
}

void overTempCalDebugDescriptors(struct OverTempCal *over_temp_cal,
                                 const char *otc_sensor_tag,
                                 const char *otc_unit_tag,
                                 float otc_unit_conversion) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(otc_sensor_tag);
  ASSERT_NOT_NULL(otc_unit_tag);

  // Sets the sensor descriptor, displayed units, and unit conversion factor.
  strcpy(over_temp_cal->otc_sensor_tag, otc_sensor_tag);
  strcpy(over_temp_cal->otc_unit_tag, otc_unit_tag);
  over_temp_cal->otc_unit_conversion = otc_unit_conversion;
}

#endif  // OVERTEMPCAL_DBG_ENABLED
