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

/*
 * [OTC Sensor Offset Calibration]
 * This module implements a runtime algorithm for provisioning over-temperature
 * compensated (OTC) estimates of a 3-axis sensor's offset (i.e., bias):
 *
 *   1) Estimates of sensor offset with associated temperature are consumed,
 *      {offset, offset_temperature}.
 *
 *   2) A linear temperature dependence model is extracted from the collected
 *      set of data pairs.
 *
 *   3) The linear model is used for compensation when no other model points
 *      (e.g., nearest-temperature, or the latest received offset estimate) can
 *      be used as a better reference to construct the OTC offset.
 *
 *   4) The linear model is used as an extrapolator to provide better
 *      compensated offset estimates with rapid changes in temperature.
 *
 *   5) Other key features of this algorithm:
 *        a) Jump Detection - The model may contain old data having a variety of
 *           different thermal histories (hysteresis) which could produce
 *           discontinuities when using nearest-temperature compensation. If a
 *           "jump" is detected in comparison to the linear model (or current
 *           compensation vector, depending on the age of the model), then the
 *           discontinuity may be minimized by selecting the alternative.
 *
 *        b) Outlier Detection - This checks new offset estimates against the
 *           available linear model. If deviations exceeed a specified limit,
 *           then the estimate is rejected.
 *
 *        c) Model Data Pruning - Old model data that age beyond a specified
 *           limit is eventually removed from the data set.
 *
 *        d) Model Parameter Limits - Bounds on the linear model parameters may
 *           be specified to qualify acceptable models.
 *
 *        e) Offset Update Rate Limits - To minimize computational burden, a
 *           temporal limit is placed on offset updates prompted from an
 *           arbitrarily high temperature sampling rate; and a minimum offset
 *           change is applied to gate small variations in offset during stable
 *           periods.
 *
 *        f) Model-Weighting Based on Age - The least-squares fit uses a
 *           weighting function based on the age of the model estimate data to
 *           favor recent estimates and emphasize localized OTC model fitting
 *           when new updates arrive.
 *
 * General Compensation Model Equation:
 *   sensor_out = sensor_in - compensated_offset
 *
 *   When the linear model is used,
 *     compensated_offset = (temp_sensitivity * current_temp + sensor_intercept)
 *
 *   NOTE - 'current_temp' is the current measured temperature.
 *     'temp_sensitivity' is the modeled temperature sensitivity (i.e., linear
 *     slope). 'sensor_intercept' is linear model intercept.
 *
 *   When the nearest-temperature or latest-offset is used as a "reference",
 *     delta_temp = current_temp - reference_offset_temperature
 *     extrapolation_term = temp_sensitivity * delta_temp
 *     compensated_offset = reference_offset + extrapolation_term
 *
 * Assumptions:
 *   1) Sensor offset temperature dependence is sufficiently "linear".
 *   2) Impact of sensor hysteresis is small relative to thermal sensitivity.
 *   3) The impact of long-term offset drift/aging compared to the magnitude of
 *      deviation resulting from the thermal sensitivity of the offset is
 *      relatively small.
 *
 * Sensor Input and Units:
 *       - General 3-axis sensor data.
 *       - Temperature measurements [Celsius].
 *
 * NOTE: Arrays are all 3-dimensional with indices: 0=x, 1=y, 2=z.
 *
 * #define OVERTEMPCAL_DBG_ENABLED to enable debug printout statements.
 * #define OVERTEMPCAL_DBG_LOG_TEMP to periodically printout sensor temperature.
 */

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_OVER_TEMP_OVER_TEMP_CAL_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_OVER_TEMP_OVER_TEMP_CAL_H_

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Defines the maximum size of the 'model_data' array.
#define OTC_MODEL_SIZE (40)

// A common sensor operating temperature at which to begin the model jump-start
// data.
#define JUMPSTART_START_TEMP_CELSIUS (30.0f)

// The maximum number of successive outliers that may be rejected.
#define OTC_MAX_OUTLIER_COUNT (3)

// The 'temp_sensitivity' parameters are set to this value to indicate that the
// model is in its initial state.
#define OTC_INITIAL_SENSITIVITY (1e6f)

// Valid sensor temperature operating range.
#define OTC_TEMP_MIN_CELSIUS (-40.0f)
#define OTC_TEMP_MAX_CELSIUS (85.0f)

// Invalid sensor temperature.
#define OTC_TEMP_INVALID_CELSIUS (-274.0f)

// Number of time-interval levels used to define the least-squares weighting
// function.
#define OTC_NUM_WEIGHT_LEVELS (2)

// Rate-limits the check of old data to every 2 hours.
#define OTC_STALE_CHECK_TIME_NANOS (HRS_TO_NANOS(2))

// Time duration in which to enforce using the last offset estimate for
// compensation (30 seconds).
#define OTC_USE_RECENT_OFFSET_TIME_NANOS (SEC_TO_NANOS(30))

// The age at which an offset estimate is considered stale (30 minutes).
#define OTC_OFFSET_IS_STALE_NANOS (MIN_TO_NANOS(30))

// The refresh interval for the OTC model (30 seconds).
#define OTC_REFRESH_MODEL_NANOS (SEC_TO_NANOS(30))

// Defines a weighting function value for the linear model fit routine.
struct OverTempCalWeightPt {
  // The age limit below which an offset will use this weight value.
  uint64_t offset_age_nanos;

  // The weighting applied (>0).
  float weight;
};

// Over-temperature sensor offset estimate structure.
struct OverTempCalDataPt {
  // Sensor offset estimate, temperature, and timestamp.
  uint64_t timestamp_nanos;   // [nanoseconds]
  float offset_temp_celsius;  // [Celsius]
  float offset[3];
};

#ifdef OVERTEMPCAL_DBG_ENABLED
// Debug printout state enumeration.
enum OverTempCalDebugState {
  OTC_IDLE = 0,
  OTC_WAIT_STATE,
  OTC_PRINT_OFFSET,
  OTC_PRINT_MODEL_PARAMETERS,
  OTC_PRINT_MODEL_ERROR,
  OTC_PRINT_MODEL_DATA
};

// OverTempCal debug information/data tracking structure.
struct DebugOverTempCal {
  // The latest received offset estimate data.
  struct OverTempCalDataPt latest_offset;

  // The maximum model error over all model_data points.
  float max_error[3];

  float temp_sensitivity[3];
  float sensor_intercept[3];
  size_t num_model_pts;
};
#endif  // OVERTEMPCAL_DBG_ENABLED

// The following data structure contains all of the necessary components for
// modeling a sensor's temperature dependency and providing over-temperature
// offset corrections.
struct OverTempCal {
  // Storage for over-temperature model data.
  struct OverTempCalDataPt model_data[OTC_MODEL_SIZE];

  // Implements a weighting function to emphasize fitting a linear model to
  // younger offset estimates.
  struct OverTempCalWeightPt weighting_function[OTC_NUM_WEIGHT_LEVELS];

  // The active over-temperature compensated offset estimate data. Contains the
  // current sensor temperature at which offset compensation is performed.
  struct OverTempCalDataPt compensated_offset;

  // Timer used to limit the rate at which old estimates are removed from
  // the 'model_data' collection.
  uint64_t stale_data_timer;             // [nanoseconds]

  // Duration beyond which data will be removed to avoid corrupting the model
  // with drift-compromised data.
  uint64_t age_limit_nanos;              // [nanoseconds]

  // Timestamp of the last OTC offset compensation update.
  uint64_t last_offset_update_nanos;     // [nanoseconds]

  // Timestamp of the last OTC model update.
  uint64_t last_model_update_nanos;      // [nanoseconds]

  // Limit on the minimum interval for offset update calculations resulting from
  // an arbitrarily high temperature sampling rate.
  uint64_t min_temp_update_period_nanos;    // [nanoseconds]

  ///// Online Model Identification Parameters ////////////////////////////////
  //
  // The rules for determining whether a new model fit is computed and the
  // resulting fit parameters are accepted are:
  //    1) A minimum number of data points must have been collected:
  //          num_model_pts >= min_num_model_pts
  //       NOTE: Collecting 'num_model_pts' and given that only one point is
  //       kept per temperature bin (spanning a thermal range specified by
  //       'delta_temp_per_bin'), implies that model data covers at least,
  //          model_temp_span >= 'num_model_pts' * delta_temp_per_bin
  //    2) A new set of model parameters are accepted if:
  //         i. The model fit parameters must be within certain absolute bounds:
  //              a. ABS(temp_sensitivity) < temp_sensitivity_limit
  //              b. ABS(sensor_intercept) < sensor_intercept_limit
  float temp_sensitivity_limit;        // [sensor units/Celsius]
  float sensor_intercept_limit;        // [sensor units]
  size_t min_num_model_pts;

  // Pointer to the offset estimate closest to the current sensor temperature.
  struct OverTempCalDataPt *nearest_offset;

  // Pointer to the most recent offset estimate.
  struct OverTempCalDataPt *latest_offset;

  // Modeled temperature sensitivity, dOffset/dTemp [sensor_units/Celsius].
  float temp_sensitivity[3];

  // Sensor model equation intercept [sensor_units].
  float sensor_intercept[3];

  // A limit on the error between nearest-temperature estimate and the model fit
  // above which the model fit is preferred for providing offset compensation
  // (also applies to checks between the nearest-temperature and the current
  // compensated estimate).
  float jump_tolerance;                // [sensor units]

  // A limit used to reject new offset estimates that deviate from the current
  // model fit.
  float outlier_limit;                 // [sensor units]

  // This parameter is used to detect offset changes that require updates to
  // system calibration and persistent memory storage.
  float significant_offset_change;     // [sensor units]

  // Used to track the previous significant change in temperature.
  float last_temp_check_celsius;

  // The rules for accepting new offset estimates into the 'model_data'
  // collection:
  //    1) The temperature domain is divided into bins each spanning
  //       'delta_temp_per_bin'.
  //    2) Find and replace the i'th 'model_data' estimate data if:
  //          Let, bin_num = floor(current_temp / delta_temp_per_bin)
  //          temp_lo_check = bin_num * delta_temp_per_bin
  //          temp_hi_check = (bin_num + 1) * delta_temp_per_bin
  //          Check condition:
  //          temp_lo_check <= model_data[i].offset_temp_celsius < temp_hi_check
  //    3) If nothing was replaced, and the 'model_data' buffer is not full then
  //       add the sensor offset estimate to the array.
  //    4) Otherwise (nothing was replaced and buffer is full), replace the
  //       oldest data with the incoming one.
  // This approach ensures a uniform spread of collected data, keeps the most
  // recent estimates in cases where they arrive frequently near a given
  // temperature, and prevents model oversampling (i.e., dominance of estimates
  // concentrated at a given set of temperatures).
  float delta_temp_per_bin;            // [Celsius/bin]

  // Total number of model data points collected.
  size_t num_model_pts;

  // The number of successive outliers rejected in a row. This is used to
  // prevent the possibility of a bad state where an initial poor model fit
  // causes good data to be continually rejected.
  size_t num_outliers;

  // Flag set by user to control whether over-temp compensation is used.
  bool over_temp_enable;

  // True when new compensation model values have been computed; and reset when
  // overTempCalNewModelUpdateAvailable() is called. This variable indicates
  // that the following should be stored in persistent system memory:
  //    1) 'temp_sensitivity' and 'sensor_intercept'.
  //    2) The 'compensated_offset' offset data
  //       (saving timestamp information is not required).
  bool new_overtemp_model_available;

  // True when a new offset estimate has been computed and registers as a
  // significant change (i.e., any of the axis offsets change by more than
  // 'significant_offset_change'); and reset when
  // overTempCalNewOffsetAvailable() is called. This variable indicates that new
  // offset data should be stored in persistent system memory.
  bool new_overtemp_offset_available;

#ifdef OVERTEMPCAL_DBG_ENABLED
  struct DebugOverTempCal debug_overtempcal;  // Debug data structure.
  enum OverTempCalDebugState debug_state;     // Debug printout state machine.
  enum OverTempCalDebugState next_state;      // Debug state machine next state.
  uint64_t wait_timer_nanos;                  // Debug message throttle timer.

#ifdef OVERTEMPCAL_DBG_LOG_TEMP
  uint64_t temperature_print_timer;
#endif  // OVERTEMPCAL_DBG_LOG_TEMP

  size_t model_counter;                // Model output print counter.
  float otc_unit_conversion;           // Unit conversion for debug display.
  char otc_unit_tag[16];               // Unit descriptor (e.g., "mDPS").
  char otc_sensor_tag[16];             // OTC sensor descriptor (e.g., "GYRO").
  char otc_debug_tag[32];              // Temporary string descriptor.
  size_t debug_num_model_updates;      // Total number of model updates.
  size_t debug_num_estimates;          // Total number of offset estimates.
  bool debug_print_trigger;            // Flag used to trigger data printout.
#endif  // OVERTEMPCAL_DBG_ENABLED
};

/////// FUNCTION PROTOTYPES ///////////////////////////////////////////////////

/*
 * Initializes the over-temp calibration model identification parameters.
 *
 * INPUTS:
 *   over_temp_cal:             Over-temp main data structure.
 *   min_num_model_pts:         Minimum number of model points per model
 *                              calculation update.
 *   min_temp_update_period_nanos: Limits the rate of offset updates due to an
 *                                 arbitrarily high temperature sampling rate.
 *   delta_temp_per_bin:        Temperature span that defines the spacing of
 *                              collected model estimates.
 *   jump_tolerance:            Tolerance on acceptable jumps in offset updates.
 *   outlier_limit:             Outlier offset estimate rejection tolerance.
 *   age_limit_nanos:           Sets the age limit beyond which a offset
 *                              estimate is removed from 'model_data'.
 *   temp_sensitivity_limit:    Values that define the upper limits for the
 *   sensor_intercept_limit:    model parameters. The acceptance of new model
 *                              parameters must satisfy:
 *                          i.  ABS(temp_sensitivity) < temp_sensitivity_limit
 *                          ii. ABS(sensor_intercept) < sensor_intercept_limit
 *   significant_offset_change  Minimum limit that triggers offset updates.
 *   over_temp_enable:          Flag that determines whether over-temp sensor
 *                              offset compensation is applied.
 */
void overTempCalInit(struct OverTempCal *over_temp_cal,
                     size_t min_num_model_pts,
                     uint64_t min_temp_update_period_nanos,
                     float delta_temp_per_bin, float jump_tolerance,
                     float outlier_limit, uint64_t age_limit_nanos,
                     float temp_sensitivity_limit, float sensor_intercept_limit,
                     float significant_offset_change, bool over_temp_enable);

/*
 * Sets the over-temp calibration model parameters.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp main data structure.
 *   offset:           Update values for the latest offset estimate (array).
 *   offset_temp_celsius: Measured temperature for the offset estimate.
 *   timestamp_nanos:  Timestamp for the offset estimate [nanoseconds].
 *   temp_sensitivity: Modeled temperature sensitivity (array).
 *   sensor_intercept: Linear model intercept for the over-temp model (array).
 *   jump_start_model: When 'true' populates an empty 'model_data' array using
 *                     valid input model parameters.
 *
 * NOTE: Arrays are all 3-dimensional with indices: 0=x, 1=y, 2=z.
 */
void overTempCalSetModel(struct OverTempCal *over_temp_cal, const float *offset,
                         float offset_temp_celsius, uint64_t timestamp_nanos,
                         const float *temp_sensitivity,
                         const float *sensor_intercept, bool jump_start_model);

/*
 * Gets the over-temp calibration model parameters.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp data structure.
 * OUTPUTS:
 *   offset:           Offset values for the latest offset estimate (array).
 *   offset_temp_celsius: Measured temperature for the offset estimate.
 *   timestamp_nanos:  Timestamp for the offset estimate [nanoseconds].
 *   temp_sensitivity: Modeled temperature sensitivity (array).
 *   sensor_intercept: Linear model intercept for the over-temp model (array).
 *
 * NOTE: Arrays are all 3-dimensional with indices: 0=x, 1=y, 2=z.
 */
void overTempCalGetModel(struct OverTempCal *over_temp_cal, float *offset,
                         float *offset_temp_celsius, uint64_t *timestamp_nanos,
                         float *temp_sensitivity, float *sensor_intercept);

/*
 * Sets the over-temp compensation model data set, and computes new model
 * parameters provided that 'min_num_model_pts' is satisfied.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp main data structure.
 *   model_data:       Array of the new model data set.
 *   data_length:      Number of model data entries in 'model_data'.
 *   timestamp_nanos:  Timestamp for the model estimates [nanoseconds].
 *
 * NOTE: Max array length for 'model_data' is OTC_MODEL_SIZE.
 */
void overTempCalSetModelData(struct OverTempCal *over_temp_cal,
                             size_t data_length, uint64_t timestamp_nanos,
                             const struct OverTempCalDataPt *model_data);

/*
 * Gets the over-temp compensation model data set.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp main data structure.
 * OUTPUTS:
 *   model_data:       Array containing the model data set.
 *   data_length:      Number of model data entries in 'model_data'.
 *
 * NOTE: Max array length for 'model_data' is OTC_MODEL_SIZE.
 */
void overTempCalGetModelData(struct OverTempCal *over_temp_cal,
                             size_t *data_length,
                             struct OverTempCalDataPt *model_data);

/*
 * Gets the current over-temp compensated offset estimate data.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp data structure.
 * OUTPUTS:
 *   compensated_offset: Temperature compensated offset estimate array.
 *   compensated_offset_temperature_celsius: Compensated offset temperature.
 *
 * NOTE: Arrays are all 3-dimensional with indices: 0=x, 1=y, 2=z.
 */
void overTempCalGetOffset(struct OverTempCal *over_temp_cal,
                          float *compensated_offset_temperature_celsius,
                          float *compensated_offset);

/*
 * Removes the over-temp compensated offset from the input sensor data.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp data structure.
 *   timestamp_nanos:  Timestamp of the sensor estimate update.
 *   xi, yi, zi:       3-axis sensor data to be compensated.
 * OUTPUTS:
 *   xo, yo, zo:       3-axis sensor data that has been compensated.
 */
void overTempCalRemoveOffset(struct OverTempCal *over_temp_cal,
                             uint64_t timestamp_nanos, float xi, float yi,
                             float zi, float *xo, float *yo, float *zo);

// Returns true when a new over-temp model update is available; and the
// 'new_overtemp_model_available' flag is reset.
bool overTempCalNewModelUpdateAvailable(struct OverTempCal *over_temp_cal);

// Returns true when a new over-temp over-temperature offset estimate is
// available; and the 'new_overtemp_offset_available' flag is reset.
bool overTempCalNewOffsetAvailable(struct OverTempCal *over_temp_cal);

/*
 * Updates the sensor's offset estimate and conditionally assimilates it into
 * the over-temp model data set, 'model_data'.
 *
 * INPUTS:
 *   over_temp_cal:       Over-temp data structure.
 *   timestamp_nanos:     Timestamp of the sensor estimate update.
 *   offset:              3-axis sensor data to be compensated (array).
 *   temperature_celsius: Measured temperature for the new sensor estimate.
 *
 * NOTE: Arrays are all 3-dimensional with indices: 0=x, 1=y, 2=z.
 */
void overTempCalUpdateSensorEstimate(struct OverTempCal *over_temp_cal,
                                     uint64_t timestamp_nanos,
                                     const float *offset,
                                     float temperature_celsius);

// Updates the temperature at which the offset compensation is performed (i.e.,
// the current measured temperature value). This function is provided mainly for
// flexibility since temperature updates may come in from a source other than
// the sensor itself, and at a different rate.
void overTempCalSetTemperature(struct OverTempCal *over_temp_cal,
                               uint64_t timestamp_nanos,
                               float temperature_celsius);

/*
 * Computes the maximum absolute error between the 'model_data' estimates and
 * the estimate determined by the input model parameters.
 *   max_error (over all i)
 *     |model_data[i]->offset_xyz -
 *       getCompensatedOffset(model_data[i]->offset_temp_celsius,
 *         temp_sensitivity, sensor_intercept)|
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp data structure.
 *   temp_sensitivity: Model temperature sensitivity to test (array).
 *   sensor_intercept: Model intercept to test (array).
 * OUTPUTS:
 *   max_error:        Maximum absolute error for the candidate model (array).
 *
 * NOTE 1: Arrays are all 3-dimensional with indices: 0=x, 1=y, 2=z.
 * NOTE 2: This function is provided for testing purposes.
 */
void overTempGetModelError(const struct OverTempCal *over_temp_cal,
                           const float *temp_sensitivity,
                           const float *sensor_intercept, float *max_error);

/*
 * Defines an element in the weighting function that is used to control the
 * fitting behavior of the simple linear model regression used in this module.
 * The total number of weighting levels that define this functionality is set by
 * 'OTC_NUM_WEIGHT_LEVELS'. The weight values are expected to be greater than
 * zero. A particular weight is assigned to a given offset estimate when it's
 * age is less than 'offset_age_nanos'. NOTE: The ordering of the
 * 'offset_age_nanos' values in the weight function array should be
 * monotonically increasing from lowest index to highest so that weighting
 * selection can be conveniently evaluated.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp data structure.
 *   index:            Weighting function index.
 *   new_otc_weight:   Pointer to the settings for the new non-zero weighting
 *                     value and corresponding age limit below which an offset
 *                     will use the weight.
 */
void overTempSetWeightingFunction(
    struct OverTempCal *over_temp_cal, size_t index,
    const struct OverTempCalWeightPt *new_otc_weight);

#ifdef OVERTEMPCAL_DBG_ENABLED
// This debug printout function assumes the input sensor data is a gyroscope
// [rad/sec]. 'timestamp_nanos' is the current system time.
void overTempCalDebugPrint(struct OverTempCal *over_temp_cal,
                           uint64_t timestamp_nanos);

/*
 * Call this after calling 'overTempCalInit' to set the debug sensor descriptor,
 * displayed units, and the conversion factor from raw sensor units to the
 * desired display units. Note the maximum string length allocations.
 *
 * INPUTS:
 *   over_temp_cal:       Over-temp data structure.
 *   otc_sensor_tag:      Sensor descriptor prefixes debug output.
 *   otc_unit_tag:        Display unit string.
 *   otc_unit_conversion: Display unit conversion factor from raw sensor units.
 */
void overTempCalDebugDescriptors(struct OverTempCal *over_temp_cal,
                                 const char *otc_sensor_tag,
                                 const char *otc_unit_tag,
                                 float otc_unit_conversion);
#endif  // OVERTEMPCAL_DBG_ENABLED

#ifdef __cplusplus
}
#endif

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_OVER_TEMP_OVER_TEMP_CAL_H_
