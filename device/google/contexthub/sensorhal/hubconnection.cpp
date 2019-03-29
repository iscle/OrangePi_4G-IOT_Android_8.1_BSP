/*
 * Copyright (C) 2015 The Android Open Source Project
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

#define LOG_TAG "nanohub"

#include "hubconnection.h"

// TODO: remove the includes that introduce LIKELY and UNLIKELY (firmware/os/inc/toolchain.h)
#undef LIKELY
#undef UNLIKELY

#include "file.h"
#include "JSONObject.h"

#include <errno.h>
#include <unistd.h>
#include <math.h>
#include <inttypes.h>
#include <sched.h>
#include <sys/inotify.h>

#include <linux/input.h>
#include <linux/uinput.h>

#include <android/frameworks/schedulerservice/1.0/ISchedulingPolicyService.h>
#include <cutils/ashmem.h>
#include <cutils/properties.h>
#include <hardware_legacy/power.h>
#include <media/stagefright/foundation/ADebug.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include <algorithm>
#include <cmath>
#include <sstream>
#include <vector>

#define APP_ID_GET_VENDOR(appid)       ((appid) >> 24)
#define APP_ID_MAKE(vendor, app)       ((((uint64_t)(vendor)) << 24) | ((app) & 0x00FFFFFF))
#define APP_ID_VENDOR_GOOGLE           0x476f6f676cULL // "Googl"
#define APP_ID_APP_BMI160              2
#define APP_ID_APP_WRIST_TILT_DETECT   0x1005
#define APP_ID_APP_GAZE_DETECT         0x1009
#define APP_ID_APP_UNGAZE_DETECT       0x100a

#define SENS_TYPE_TO_EVENT(_sensorType) (EVT_NO_FIRST_SENSOR_EVENT + (_sensorType))

#define NANOHUB_FILE_PATH       "/dev/nanohub"
#define NANOHUB_LOCK_DIR        "/data/vendor/sensor/nanohub_lock"
#define NANOHUB_LOCK_FILE       NANOHUB_LOCK_DIR "/lock"
#define MAG_BIAS_FILE_PATH      "/sys/class/power_supply/battery/compass_compensation"
#define DOUBLE_TOUCH_FILE_PATH  "/sys/android_touch/synaptics_rmi4_dsx/wake_event"

#define NANOHUB_LOCK_DIR_PERMS  (S_IRUSR | S_IWUSR | S_IXUSR)

#define SENSOR_RATE_ONCHANGE    0xFFFFFF01UL
#define SENSOR_RATE_ONESHOT     0xFFFFFF02UL

#define MIN_MAG_SQ              (10.0f * 10.0f)
#define MAX_MAG_SQ              (80.0f * 80.0f)

#define OS_LOG_EVENT            0x474F4C41  // ascii: ALOG

#define MAX_RETRY_CNT           5

#ifdef LID_STATE_REPORTING_ENABLED
const char LID_STATE_PROPERTY[] = "sensors.contexthub.lid_state";
const char LID_STATE_UNKNOWN[]  = "unknown";
const char LID_STATE_OPEN[]     = "open";
const char LID_STATE_CLOSED[]   = "closed";
#endif  // LID_STATE_REPORTING_ENABLED

static const uint32_t delta_time_encoded = 1;
static const uint32_t delta_time_shift_table[2] = {9, 0};

#ifdef USE_SENSORSERVICE_TO_GET_FIFO
// TODO(b/35219747): retain sched_fifo before eval is done to avoid
// performance regression.
const char SCHED_FIFO_PRIOIRTY[] = "sensor.hubconnection.sched_fifo";
#endif

namespace android {

// static
Mutex HubConnection::sInstanceLock;

// static
HubConnection *HubConnection::sInstance = NULL;

HubConnection *HubConnection::getInstance()
{
    Mutex::Autolock autoLock(sInstanceLock);
    if (sInstance == NULL) {
        sInstance = new HubConnection;
    }
    return sInstance;
}

static bool isActivitySensor(int sensorIndex) {
    return sensorIndex >= COMMS_SENSOR_ACTIVITY_FIRST
        && sensorIndex <= COMMS_SENSOR_ACTIVITY_LAST;
}

static bool isWakeEvent(int32_t sensor)
{
    switch (sensor) {
    case COMMS_SENSOR_DOUBLE_TOUCH:
    case COMMS_SENSOR_DOUBLE_TWIST:
    case COMMS_SENSOR_GESTURE:
    case COMMS_SENSOR_PROXIMITY:
    case COMMS_SENSOR_SIGNIFICANT_MOTION:
    case COMMS_SENSOR_TILT:
        return true;
    default:
        return false;
    }
}

HubConnection::HubConnection()
    : Thread(false /* canCallJava */),
      mRing(10 *1024),
      mActivityEventHandler(NULL),
      mScaleAccel(1.0f),
      mScaleMag(1.0f),
      mStepCounterOffset(0ull),
      mLastStepCount(0ull)
{
    mMagBias[0] = mMagBias[1] = mMagBias[2] = 0.0f;
    mMagAccuracy = SENSOR_STATUS_UNRELIABLE;
    mMagAccuracyRestore = SENSOR_STATUS_UNRELIABLE;
    mGyroBias[0] = mGyroBias[1] = mGyroBias[2] = 0.0f;
    mAccelBias[0] = mAccelBias[1] = mAccelBias[2] = 0.0f;
    memset(&mGyroOtcData, 0, sizeof(mGyroOtcData));

    mLefty.accel = false;
    mLefty.gyro = false;
    mLefty.hub = false;

    memset(&mSensorState, 0x00, sizeof(mSensorState));
    mFd = open(NANOHUB_FILE_PATH, O_RDWR);
    mPollFds[0].fd = mFd;
    mPollFds[0].events = POLLIN;
    mPollFds[0].revents = 0;
    mNumPollFds = 1;

    mWakelockHeld = false;
    mWakeEventCount = 0;
    mWriteFailures = 0;

    initNanohubLock();

#ifdef USB_MAG_BIAS_REPORTING_ENABLED
    mUsbMagBias = 0;
    mMagBiasPollIndex = -1;
    int magBiasFd = open(MAG_BIAS_FILE_PATH, O_RDONLY);
    if (magBiasFd < 0) {
        ALOGW("Mag bias file open failed: %s", strerror(errno));
    } else {
        mPollFds[mNumPollFds].fd = magBiasFd;
        mPollFds[mNumPollFds].events = 0;
        mPollFds[mNumPollFds].revents = 0;
        mMagBiasPollIndex = mNumPollFds;
        mNumPollFds++;
    }
#endif  // USB_MAG_BIAS_REPORTING_ENABLED

#ifdef DOUBLE_TOUCH_ENABLED
    mDoubleTouchPollIndex = -1;
    int doubleTouchFd = open(DOUBLE_TOUCH_FILE_PATH, O_RDONLY);
    if (doubleTouchFd < 0) {
        ALOGW("Double touch file open failed: %s", strerror(errno));
    } else {
        mPollFds[mNumPollFds].fd = doubleTouchFd;
        mPollFds[mNumPollFds].events = 0;
        mPollFds[mNumPollFds].revents = 0;
        mDoubleTouchPollIndex = mNumPollFds;
        mNumPollFds++;
    }
#endif  // DOUBLE_TOUCH_ENABLED

    mSensorState[COMMS_SENSOR_ACCEL].sensorType = SENS_TYPE_ACCEL;
    mSensorState[COMMS_SENSOR_ACCEL].alt[0] = COMMS_SENSOR_ACCEL_UNCALIBRATED;
    mSensorState[COMMS_SENSOR_ACCEL].alt[1] = COMMS_SENSOR_ACCEL_WRIST_AWARE;
    mSensorState[COMMS_SENSOR_ACCEL_UNCALIBRATED].sensorType = SENS_TYPE_ACCEL;
    mSensorState[COMMS_SENSOR_ACCEL_UNCALIBRATED].primary = COMMS_SENSOR_ACCEL;
    mSensorState[COMMS_SENSOR_ACCEL_UNCALIBRATED].alt[0] = COMMS_SENSOR_ACCEL;
    mSensorState[COMMS_SENSOR_ACCEL_UNCALIBRATED].alt[1] = COMMS_SENSOR_ACCEL_WRIST_AWARE;
    mSensorState[COMMS_SENSOR_ACCEL_WRIST_AWARE].sensorType = SENS_TYPE_ACCEL;
    mSensorState[COMMS_SENSOR_ACCEL_WRIST_AWARE].primary = COMMS_SENSOR_ACCEL;
    mSensorState[COMMS_SENSOR_ACCEL_WRIST_AWARE].alt[0] = COMMS_SENSOR_ACCEL;
    mSensorState[COMMS_SENSOR_ACCEL_WRIST_AWARE].alt[1] = COMMS_SENSOR_ACCEL_UNCALIBRATED;
    mSensorState[COMMS_SENSOR_GYRO].sensorType = SENS_TYPE_GYRO;
    mSensorState[COMMS_SENSOR_GYRO].alt[0] = COMMS_SENSOR_GYRO_UNCALIBRATED;
    mSensorState[COMMS_SENSOR_GYRO].alt[1] = COMMS_SENSOR_GYRO_WRIST_AWARE;
    mSensorState[COMMS_SENSOR_GYRO_UNCALIBRATED].sensorType = SENS_TYPE_GYRO;
    mSensorState[COMMS_SENSOR_GYRO_UNCALIBRATED].primary = COMMS_SENSOR_GYRO;
    mSensorState[COMMS_SENSOR_GYRO_UNCALIBRATED].alt[0] = COMMS_SENSOR_GYRO;
    mSensorState[COMMS_SENSOR_GYRO_UNCALIBRATED].alt[1] = COMMS_SENSOR_GYRO_WRIST_AWARE;
    mSensorState[COMMS_SENSOR_GYRO_WRIST_AWARE].sensorType = SENS_TYPE_GYRO;
    mSensorState[COMMS_SENSOR_GYRO_WRIST_AWARE].primary = COMMS_SENSOR_GYRO;
    mSensorState[COMMS_SENSOR_GYRO_WRIST_AWARE].alt[0] = COMMS_SENSOR_GYRO;
    mSensorState[COMMS_SENSOR_GYRO_WRIST_AWARE].alt[1] = COMMS_SENSOR_GYRO_UNCALIBRATED;
    mSensorState[COMMS_SENSOR_MAG].sensorType = SENS_TYPE_MAG;
    mSensorState[COMMS_SENSOR_MAG].alt[0] = COMMS_SENSOR_MAG_UNCALIBRATED;
    mSensorState[COMMS_SENSOR_MAG_UNCALIBRATED].sensorType = SENS_TYPE_MAG;
    mSensorState[COMMS_SENSOR_MAG_UNCALIBRATED].primary = COMMS_SENSOR_MAG;
    mSensorState[COMMS_SENSOR_MAG_UNCALIBRATED].alt[0] = COMMS_SENSOR_MAG;
    mSensorState[COMMS_SENSOR_LIGHT].sensorType = SENS_TYPE_ALS;
    mSensorState[COMMS_SENSOR_PROXIMITY].sensorType = SENS_TYPE_PROX;
    mSensorState[COMMS_SENSOR_PRESSURE].sensorType = SENS_TYPE_BARO;
    mSensorState[COMMS_SENSOR_TEMPERATURE].sensorType = SENS_TYPE_TEMP;
    mSensorState[COMMS_SENSOR_ORIENTATION].sensorType = SENS_TYPE_ORIENTATION;
    mSensorState[COMMS_SENSOR_WINDOW_ORIENTATION].sensorType = SENS_TYPE_WIN_ORIENTATION;
    mSensorState[COMMS_SENSOR_WINDOW_ORIENTATION].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_STEP_DETECTOR].sensorType = SENS_TYPE_STEP_DETECT;
    mSensorState[COMMS_SENSOR_STEP_DETECTOR].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_STEP_COUNTER].sensorType = SENS_TYPE_STEP_COUNT;
    mSensorState[COMMS_SENSOR_SIGNIFICANT_MOTION].sensorType = SENS_TYPE_SIG_MOTION;
    mSensorState[COMMS_SENSOR_SIGNIFICANT_MOTION].rate = SENSOR_RATE_ONESHOT;
    mSensorState[COMMS_SENSOR_GRAVITY].sensorType = SENS_TYPE_GRAVITY;
    mSensorState[COMMS_SENSOR_LINEAR_ACCEL].sensorType = SENS_TYPE_LINEAR_ACCEL;
    mSensorState[COMMS_SENSOR_ROTATION_VECTOR].sensorType = SENS_TYPE_ROTATION_VECTOR;
    mSensorState[COMMS_SENSOR_GEO_MAG].sensorType = SENS_TYPE_GEO_MAG_ROT_VEC;
    mSensorState[COMMS_SENSOR_GAME_ROTATION_VECTOR].sensorType = SENS_TYPE_GAME_ROT_VECTOR;
    mSensorState[COMMS_SENSOR_HALL].sensorType = SENS_TYPE_HALL;
    mSensorState[COMMS_SENSOR_HALL].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_SYNC].sensorType = SENS_TYPE_VSYNC;
    mSensorState[COMMS_SENSOR_SYNC].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_TILT].sensorType = SENS_TYPE_TILT;
    mSensorState[COMMS_SENSOR_TILT].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_GESTURE].sensorType = SENS_TYPE_GESTURE;
    mSensorState[COMMS_SENSOR_GESTURE].rate = SENSOR_RATE_ONESHOT;
    mSensorState[COMMS_SENSOR_DOUBLE_TWIST].sensorType = SENS_TYPE_DOUBLE_TWIST;
    mSensorState[COMMS_SENSOR_DOUBLE_TWIST].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_DOUBLE_TAP].sensorType = SENS_TYPE_DOUBLE_TAP;
    mSensorState[COMMS_SENSOR_DOUBLE_TAP].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_WRIST_TILT].sensorType = SENS_TYPE_WRIST_TILT;
    mSensorState[COMMS_SENSOR_WRIST_TILT].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_DOUBLE_TOUCH].sensorType = SENS_TYPE_DOUBLE_TOUCH;
    mSensorState[COMMS_SENSOR_DOUBLE_TOUCH].rate = SENSOR_RATE_ONESHOT;
    mSensorState[COMMS_SENSOR_ACTIVITY_IN_VEHICLE_START].sensorType = SENS_TYPE_ACTIVITY_IN_VEHICLE_START;
    mSensorState[COMMS_SENSOR_ACTIVITY_IN_VEHICLE_START].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_ACTIVITY_IN_VEHICLE_STOP].sensorType = SENS_TYPE_ACTIVITY_IN_VEHICLE_STOP;
    mSensorState[COMMS_SENSOR_ACTIVITY_IN_VEHICLE_STOP].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_ACTIVITY_ON_BICYCLE_START].sensorType = SENS_TYPE_ACTIVITY_ON_BICYCLE_START;
    mSensorState[COMMS_SENSOR_ACTIVITY_ON_BICYCLE_START].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_ACTIVITY_ON_BICYCLE_STOP].sensorType = SENS_TYPE_ACTIVITY_ON_BICYCLE_STOP;
    mSensorState[COMMS_SENSOR_ACTIVITY_ON_BICYCLE_STOP].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_ACTIVITY_WALKING_START].sensorType = SENS_TYPE_ACTIVITY_WALKING_START;
    mSensorState[COMMS_SENSOR_ACTIVITY_WALKING_START].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_ACTIVITY_WALKING_STOP].sensorType = SENS_TYPE_ACTIVITY_WALKING_STOP;
    mSensorState[COMMS_SENSOR_ACTIVITY_WALKING_STOP].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_ACTIVITY_RUNNING_START].sensorType = SENS_TYPE_ACTIVITY_RUNNING_START;
    mSensorState[COMMS_SENSOR_ACTIVITY_RUNNING_START].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_ACTIVITY_RUNNING_STOP].sensorType = SENS_TYPE_ACTIVITY_RUNNING_STOP;
    mSensorState[COMMS_SENSOR_ACTIVITY_RUNNING_STOP].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_ACTIVITY_STILL_START].sensorType = SENS_TYPE_ACTIVITY_STILL_START;
    mSensorState[COMMS_SENSOR_ACTIVITY_STILL_START].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_ACTIVITY_STILL_STOP].sensorType = SENS_TYPE_ACTIVITY_STILL_STOP;
    mSensorState[COMMS_SENSOR_ACTIVITY_STILL_STOP].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_ACTIVITY_TILTING].sensorType = SENS_TYPE_ACTIVITY_TILTING;
    mSensorState[COMMS_SENSOR_ACTIVITY_TILTING].rate = SENSOR_RATE_ONCHANGE;
    mSensorState[COMMS_SENSOR_GAZE].sensorType = SENS_TYPE_GAZE;
    mSensorState[COMMS_SENSOR_GAZE].rate = SENSOR_RATE_ONESHOT;
    mSensorState[COMMS_SENSOR_UNGAZE].sensorType = SENS_TYPE_UNGAZE;
    mSensorState[COMMS_SENSOR_UNGAZE].rate = SENSOR_RATE_ONESHOT;
    mSensorState[COMMS_SENSOR_HUMIDITY].sensorType = SENS_TYPE_HUMIDITY;

#ifdef LID_STATE_REPORTING_ENABLED
    initializeUinputNode();

    // set initial lid state
    if (property_set(LID_STATE_PROPERTY, LID_STATE_UNKNOWN) < 0) {
        ALOGW("could not set lid_state property");
    }

    // enable hall sensor for folio
    if (mFd >= 0) {
        queueActivate(COMMS_SENSOR_HALL, true /* enable */);
    }
#endif  // LID_STATE_REPORTING_ENABLED

#ifdef DIRECT_REPORT_ENABLED
    mDirectChannelHandle = 1;
    mSensorToChannel.emplace(COMMS_SENSOR_ACCEL,
                             std::unordered_map<int32_t, DirectChannelTimingInfo>());
    mSensorToChannel.emplace(COMMS_SENSOR_GYRO,
                             std::unordered_map<int32_t, DirectChannelTimingInfo>());
    mSensorToChannel.emplace(COMMS_SENSOR_MAG,
                             std::unordered_map<int32_t, DirectChannelTimingInfo>());
    mSensorToChannel.emplace(COMMS_SENSOR_ACCEL_UNCALIBRATED,
                             std::unordered_map<int32_t, DirectChannelTimingInfo>());
    mSensorToChannel.emplace(COMMS_SENSOR_GYRO_UNCALIBRATED,
                             std::unordered_map<int32_t, DirectChannelTimingInfo>());
    mSensorToChannel.emplace(COMMS_SENSOR_MAG_UNCALIBRATED,
                             std::unordered_map<int32_t, DirectChannelTimingInfo>());
#endif // DIRECT_REPORT_ENABLED
}

HubConnection::~HubConnection()
{
    close(mFd);
}

void HubConnection::onFirstRef()
{
    run("HubConnection", PRIORITY_URGENT_DISPLAY);
#ifdef USE_SENSORSERVICE_TO_GET_FIFO
    if (property_get_bool(SCHED_FIFO_PRIOIRTY, true)) {
        ALOGV("Try activate sched-fifo priority for HubConnection thread");
        mEnableSchedFifoThread = std::thread(enableSchedFifoMode, this);
    }
#else
    enableSchedFifoMode(this);
#endif
}

// Set main thread to SCHED_FIFO to lower sensor event latency when system is under load
void HubConnection::enableSchedFifoMode(sp<HubConnection> hub) {
#ifdef USE_SENSORSERVICE_TO_GET_FIFO
    using ::android::frameworks::schedulerservice::V1_0::ISchedulingPolicyService;
    using ::android::hardware::Return;

    // SchedulingPolicyService will not start until system server start.
    // Thus, cannot block on this.
    sp<ISchedulingPolicyService> scheduler = ISchedulingPolicyService::getService();

    if (scheduler == nullptr) {
        ALOGW("Couldn't get scheduler scheduler to set SCHED_FIFO.");
    } else {
        Return<int32_t> max = scheduler->getMaxAllowedPriority();
        if (!max.isOk()) {
            ALOGW("Failed to retrieve maximum allowed priority for HubConnection.");
            return;
        }
        Return<bool> ret = scheduler->requestPriority(::getpid(), hub->getTid(), max);
        if (!ret.isOk() || !ret) {
            ALOGW("Failed to set SCHED_FIFO for HubConnection.");
        } else {
            ALOGV("Enabled sched fifo thread mode (prio %d)", static_cast<int32_t>(max));
        }
    }
#else
#define HUBCONNECTION_SCHED_FIFO_PRIORITY 10
    struct sched_param param = {0};
    param.sched_priority = HUBCONNECTION_SCHED_FIFO_PRIORITY;
    if (sched_setscheduler(hub->getTid(), SCHED_FIFO | SCHED_RESET_ON_FORK, &param) != 0) {
        ALOGW("Couldn't set SCHED_FIFO for HubConnection thread");
    }
#endif
}

status_t HubConnection::initCheck() const
{
    return mFd < 0 ? UNKNOWN_ERROR : OK;
}

status_t HubConnection::getAliveCheck()
{
    return OK;
}

static sp<JSONObject> readSettings(File *file) {
    off64_t size = file->seekTo(0, SEEK_END);
    file->seekTo(0, SEEK_SET);

    sp<JSONObject> root;

    if (size > 0) {
        char *buf = (char *)malloc(size);
        CHECK_EQ(file->read(buf, size), (ssize_t)size);
        file->seekTo(0, SEEK_SET);

        sp<JSONCompound> in = JSONCompound::Parse(buf, size);
        free(buf);
        buf = NULL;

        if (in != NULL && in->isObject()) {
            root = (JSONObject *)in.get();
        }
    }

    if (root == NULL) {
        root = new JSONObject;
    }

    return root;
}

static bool getCalibrationInt32(
        const sp<JSONObject> &settings, const char *key, int32_t *out,
        size_t numArgs) {
    sp<JSONArray> array;
    for (size_t i = 0; i < numArgs; i++) {
        out[i] = 0;
    }
    if (!settings->getArray(key, &array)) {
        return false;
    } else {
        for (size_t i = 0; i < numArgs; i++) {
            if (!array->getInt32(i, &out[i])) {
                return false;
            }
        }
    }
    return true;
}

static bool getCalibrationFloat(
        const sp<JSONObject> &settings, const char *key, float out[3]) {
    sp<JSONArray> array;
    for (size_t i = 0; i < 3; i++) {
        out[i] = 0.0f;
    }
    if (!settings->getArray(key, &array)) {
        return false;
    } else {
        for (size_t i = 0; i < 3; i++) {
            if (!array->getFloat(i, &out[i])) {
                return false;
            }
        }
    }
    return true;
}

static std::vector<int32_t> getInt32Setting(const sp<JSONObject> &settings, const char *key) {
    std::vector<int32_t> ret;

    sp<JSONArray> array;
    if (settings->getArray(key, &array)) {
        ret.resize(array->size());
        for (size_t i = 0; i < array->size(); ++i) {
            array->getInt32(i, &ret[i]);
        }
    }
    return ret;
}

static std::vector<float> getFloatSetting(const sp<JSONObject> &settings, const char *key) {
    std::vector<float> ret;

    sp<JSONArray> array;
    if (settings->getArray(key, &array)) {
        ret.resize(array->size());
        for (size_t i = 0; i < array->size(); ++i) {
            array->getFloat(i, &ret[i]);
        }
    }
    return ret;
}

static void loadSensorSettings(sp<JSONObject>* settings,
                               sp<JSONObject>* saved_settings) {
    File settings_file(CONTEXTHUB_SETTINGS_PATH, "r");
    File saved_settings_file(CONTEXTHUB_SAVED_SETTINGS_PATH, "r");

    status_t err;
    if ((err = settings_file.initCheck()) != OK) {
        ALOGW("settings file open failed: %d (%s)",
              err,
              strerror(-err));

        *settings = new JSONObject;
    } else {
        *settings = readSettings(&settings_file);
    }

    if ((err = saved_settings_file.initCheck()) != OK) {
        ALOGW("saved settings file open failed: %d (%s)",
              err,
              strerror(-err));
        *saved_settings = new JSONObject;
    } else {
        *saved_settings = readSettings(&saved_settings_file);
    }
}

void HubConnection::saveSensorSettings() const {
    File saved_settings_file(CONTEXTHUB_SAVED_SETTINGS_PATH, "w");
    sp<JSONObject> settingsObject = new JSONObject;

    status_t err;
    if ((err = saved_settings_file.initCheck()) != OK) {
        ALOGW("saved settings file open failed %d (%s)",
              err,
              strerror(-err));
        return;
    }

    // Build a settings object.
    sp<JSONArray> magArray = new JSONArray;
#ifdef USB_MAG_BIAS_REPORTING_ENABLED
    magArray->addFloat(mMagBias[0] + mUsbMagBias);
#else
    magArray->addFloat(mMagBias[0]);
#endif  // USB_MAG_BIAS_REPORTING_ENABLED
    magArray->addFloat(mMagBias[1]);
    magArray->addFloat(mMagBias[2]);
    settingsObject->setArray(MAG_BIAS_TAG, magArray);

    // Add gyro settings
    sp<JSONArray> gyroArray = new JSONArray;
    gyroArray->addFloat(mGyroBias[0]);
    gyroArray->addFloat(mGyroBias[1]);
    gyroArray->addFloat(mGyroBias[2]);
    settingsObject->setArray(GYRO_SW_BIAS_TAG, gyroArray);

    // Add accel settings
    sp<JSONArray> accelArray = new JSONArray;
    accelArray->addFloat(mAccelBias[0]);
    accelArray->addFloat(mAccelBias[1]);
    accelArray->addFloat(mAccelBias[2]);
    settingsObject->setArray(ACCEL_SW_BIAS_TAG, accelArray);

    // Add overtemp calibration values for gyro
    sp<JSONArray> gyroOtcDataArray = new JSONArray;
    const float *f;
    size_t i;
    for (f = reinterpret_cast<const float *>(&mGyroOtcData), i = 0;
            i < sizeof(mGyroOtcData)/sizeof(float); ++i, ++f) {
        gyroOtcDataArray->addFloat(*f);
    }
    settingsObject->setArray(GYRO_OTC_DATA_TAG, gyroOtcDataArray);

    // Write the JSON string to disk.
    AString serializedSettings = settingsObject->toString();
    size_t size = serializedSettings.size();
    if ((err = saved_settings_file.write(serializedSettings.c_str(), size)) != (ssize_t)size) {
        ALOGW("saved settings file write failed %d (%s)",
              err,
              strerror(-err));
    }
}

ssize_t HubConnection::sendCmd(const void *buf, size_t count)
{
    ssize_t ret;
    int retryCnt = 0;

    do {
        ret = TEMP_FAILURE_RETRY(::write(mFd, buf, count));
    } while (ret == 0 && retryCnt++ < MAX_RETRY_CNT);

    if (retryCnt > 0)
        ALOGW("sendCmd: retry: count=%zu, ret=%zd, retryCnt=%d",
              count, ret, retryCnt);
    else if (ret < 0 || static_cast<size_t>(ret) != count)
        ALOGW("sendCmd: failed: count=%zu, ret=%zd, errno=%d",
              count, ret, errno);

    return ret;
}

void HubConnection::setLeftyMode(bool enable) {
    struct MsgCmd *cmd;
    size_t ret;

    Mutex::Autolock autoLock(mLock);

    if (enable == mLefty.hub) return;

    cmd = (struct MsgCmd *)malloc(sizeof(struct MsgCmd) + sizeof(bool));

    if (cmd) {
        cmd->evtType = EVT_APP_FROM_HOST;
        cmd->msg.appId = APP_ID_MAKE(APP_ID_VENDOR_GOOGLE, APP_ID_APP_GAZE_DETECT);
        cmd->msg.dataLen = sizeof(bool);
        memcpy((bool *)(cmd+1), &enable, sizeof(bool));

        ret = sendCmd(cmd, sizeof(*cmd) + sizeof(bool));
        if (ret == sizeof(*cmd) + sizeof(bool))
            ALOGV("setLeftyMode: lefty (gaze) = %s\n",
                  (enable ? "true" : "false"));
        else
            ALOGE("setLeftyMode: failed to send command lefty (gaze) = %s\n",
                  (enable ? "true" : "false"));

        cmd->msg.appId = APP_ID_MAKE(APP_ID_VENDOR_GOOGLE, APP_ID_APP_UNGAZE_DETECT);

        ret = sendCmd(cmd, sizeof(*cmd) + sizeof(bool));
        if (ret == sizeof(*cmd) + sizeof(bool))
            ALOGV("setLeftyMode: lefty (ungaze) = %s\n",
                  (enable ? "true" : "false"));
        else
            ALOGE("setLeftyMode: failed to send command lefty (ungaze) = %s\n",
                  (enable ? "true" : "false"));

        cmd->msg.appId = APP_ID_MAKE(APP_ID_VENDOR_GOOGLE, APP_ID_APP_WRIST_TILT_DETECT);

        ret = sendCmd(cmd, sizeof(*cmd) + sizeof(bool));
        if (ret == sizeof(*cmd) + sizeof(bool))
            ALOGV("setLeftyMode: lefty (tilt) = %s\n",
                  (enable ? "true" : "false"));
        else
            ALOGE("setLeftyMode: failed to send command lefty (tilt) = %s\n",
                  (enable ? "true" : "false"));

        free(cmd);
    } else {
        ALOGE("setLeftyMode: failed to allocate command\n");
        return;
    }

    queueFlushInternal(COMMS_SENSOR_ACCEL_WRIST_AWARE, true);
    queueFlushInternal(COMMS_SENSOR_GYRO_WRIST_AWARE, true);

    mLefty.hub = enable;
}

sensors_event_t *HubConnection::initEv(sensors_event_t *ev, uint64_t timestamp, uint32_t type, uint32_t sensor)
{
    memset(ev, 0x00, sizeof(sensors_event_t));
    ev->version = sizeof(sensors_event_t);
    ev->timestamp = timestamp;
    ev->type = type;
    ev->sensor = sensor;

    return ev;
}

ssize_t HubConnection::decrementIfWakeEventLocked(int32_t sensor)
{
    if (isWakeEvent(sensor)) {
        if (mWakeEventCount > 0)
            mWakeEventCount--;
        else
            ALOGW("%s: sensor=%d, unexpected count=%d, no-op",
                  __FUNCTION__, sensor, mWakeEventCount);
    }

    return mWakeEventCount;
}

void HubConnection::protectIfWakeEventLocked(int32_t sensor)
{
    if (isWakeEvent(sensor)) {
        if (mWakelockHeld == false) {
            acquire_wake_lock(PARTIAL_WAKE_LOCK, WAKELOCK_NAME);
            mWakelockHeld = true;
        }
        mWakeEventCount++;
    }
}

void HubConnection::releaseWakeLockIfAppropriate()
{
    Mutex::Autolock autoLock(mLock);

    if (mWakelockHeld && (mWakeEventCount == 0)) {
        mWakelockHeld = false;
        release_wake_lock(WAKELOCK_NAME);
    }
}

void HubConnection::processSample(uint64_t timestamp, uint32_t type, uint32_t sensor, struct OneAxisSample *sample, __attribute__((unused)) bool highAccuracy)
{
    sensors_event_t nev[1];
    int cnt = 0;

    switch (sensor) {
    case COMMS_SENSOR_ACTIVITY_IN_VEHICLE_START:
    case COMMS_SENSOR_ACTIVITY_IN_VEHICLE_STOP:
    case COMMS_SENSOR_ACTIVITY_ON_BICYCLE_START:
    case COMMS_SENSOR_ACTIVITY_ON_BICYCLE_STOP:
    case COMMS_SENSOR_ACTIVITY_WALKING_START:
    case COMMS_SENSOR_ACTIVITY_WALKING_STOP:
    case COMMS_SENSOR_ACTIVITY_RUNNING_START:
    case COMMS_SENSOR_ACTIVITY_RUNNING_STOP:
    case COMMS_SENSOR_ACTIVITY_STILL_START:
    case COMMS_SENSOR_ACTIVITY_STILL_STOP:
    case COMMS_SENSOR_ACTIVITY_TILTING:
        if (mActivityEventHandler != NULL) {
            mActivityEventHandler->OnActivityEvent(sensor, sample->idata & 0xff,
                                                   timestamp);
        }
        break;
    case COMMS_SENSOR_PRESSURE:
        initEv(&nev[cnt++], timestamp, type, sensor)->pressure = sample->fdata;
        break;
    case COMMS_SENSOR_HUMIDITY:
        initEv(&nev[cnt++], timestamp, type, sensor)->relative_humidity = sample->fdata;
        break;
    case COMMS_SENSOR_TEMPERATURE:
        initEv(&nev[cnt++], timestamp, type, sensor)->temperature = sample->fdata;
        break;
    case COMMS_SENSOR_PROXIMITY:
        initEv(&nev[cnt++], timestamp, type, sensor)->distance = sample->fdata;
        break;
    case COMMS_SENSOR_LIGHT:
        initEv(&nev[cnt++], timestamp, type, sensor)->light = sample->fdata;
        break;
    case COMMS_SENSOR_STEP_COUNTER:
        // We'll stash away the last step count in case we need to reset
        // the hub. This last step count would then become the new offset.
        mLastStepCount = mStepCounterOffset + sample->idata;
        initEv(&nev[cnt++], timestamp, type, sensor)->u64.step_counter = mLastStepCount;
        break;
    case COMMS_SENSOR_STEP_DETECTOR:
    case COMMS_SENSOR_SIGNIFICANT_MOTION:
    case COMMS_SENSOR_TILT:
    case COMMS_SENSOR_DOUBLE_TWIST:
    case COMMS_SENSOR_WRIST_TILT:
        initEv(&nev[cnt++], timestamp, type, sensor)->data[0] = 1.0f;
        break;
    case COMMS_SENSOR_GAZE:
    case COMMS_SENSOR_UNGAZE:
    case COMMS_SENSOR_GESTURE:
    case COMMS_SENSOR_SYNC:
    case COMMS_SENSOR_DOUBLE_TOUCH:
        initEv(&nev[cnt++], timestamp, type, sensor)->data[0] = sample->idata;
        break;
    case COMMS_SENSOR_HALL:
#ifdef LID_STATE_REPORTING_ENABLED
        sendFolioEvent(sample->idata);
#endif  // LID_STATE_REPORTING_ENABLED
        break;
    case COMMS_SENSOR_WINDOW_ORIENTATION:
        initEv(&nev[cnt++], timestamp, type, sensor)->data[0] = sample->idata;
        break;
    default:
        break;
    }

    if (cnt > 0)
        write(nev, cnt);
}

uint8_t HubConnection::magAccuracyUpdate(sensors_vec_t *sv)
{
    float magSq = sv->x * sv->x + sv->y * sv->y + sv->z * sv->z;

    if (magSq < MIN_MAG_SQ || magSq > MAX_MAG_SQ) {
        // save last good accuracy (either MEDIUM or HIGH)
        if (mMagAccuracy != SENSOR_STATUS_UNRELIABLE)
            mMagAccuracyRestore = mMagAccuracy;
        mMagAccuracy = SENSOR_STATUS_UNRELIABLE;
    } else if (mMagAccuracy == SENSOR_STATUS_UNRELIABLE) {
        // restore
        mMagAccuracy = mMagAccuracyRestore;
    }

    return mMagAccuracy;
}

void HubConnection::processSample(uint64_t timestamp, uint32_t type, uint32_t sensor, struct RawThreeAxisSample *sample, __attribute__((unused)) bool highAccuracy)
{
    sensors_vec_t *sv;
    uncalibrated_event_t *ue;
    sensors_event_t nev[3];
    int cnt = 0;

    switch (sensor) {
    case COMMS_SENSOR_ACCEL:
        sv = &initEv(&nev[cnt], timestamp, type, sensor)->acceleration;
        sv->x = sample->ix * mScaleAccel;
        sv->y = sample->iy * mScaleAccel;
        sv->z = sample->iz * mScaleAccel;
        sv->status = SENSOR_STATUS_ACCURACY_HIGH;

        sendDirectReportEvent(&nev[cnt], 1);
        if (mSensorState[sensor].enable && isSampleIntervalSatisfied(sensor, timestamp)) {
            ++cnt;
        }

        ue = &initEv(&nev[cnt], timestamp,
            SENSOR_TYPE_ACCELEROMETER_UNCALIBRATED,
            COMMS_SENSOR_ACCEL_UNCALIBRATED)->uncalibrated_accelerometer;
        ue->x_uncalib = sample->ix * mScaleAccel + mAccelBias[0];
        ue->y_uncalib = sample->iy * mScaleAccel + mAccelBias[1];
        ue->z_uncalib = sample->iz * mScaleAccel + mAccelBias[2];
        ue->x_bias = mAccelBias[0];
        ue->y_bias = mAccelBias[1];
        ue->z_bias = mAccelBias[2];

        sendDirectReportEvent(&nev[cnt], 1);
        if (mSensorState[COMMS_SENSOR_ACCEL_UNCALIBRATED].enable
                && isSampleIntervalSatisfied(COMMS_SENSOR_ACCEL_UNCALIBRATED, timestamp)) {
            ++cnt;
        }

        if (mSensorState[COMMS_SENSOR_ACCEL_WRIST_AWARE].enable
                && isSampleIntervalSatisfied(COMMS_SENSOR_ACCEL_WRIST_AWARE, timestamp)) {
            sv = &initEv(&nev[cnt++], timestamp,
                SENSOR_TYPE_ACCELEROMETER_WRIST_AWARE,
                COMMS_SENSOR_ACCEL_WRIST_AWARE)->acceleration;
            sv->x = sample->ix * mScaleAccel;
            sv->y = (mLefty.accel ? -sample->iy : sample->iy) * mScaleAccel;
            sv->z = sample->iz * mScaleAccel;
            sv->status = SENSOR_STATUS_ACCURACY_HIGH;
        }
        break;
    case COMMS_SENSOR_MAG:
        sv = &initEv(&nev[cnt], timestamp, type, sensor)->magnetic;
        sv->x = sample->ix * mScaleMag;
        sv->y = sample->iy * mScaleMag;
        sv->z = sample->iz * mScaleMag;
        sv->status = magAccuracyUpdate(sv);

        sendDirectReportEvent(&nev[cnt], 1);
        if (mSensorState[sensor].enable && isSampleIntervalSatisfied(sensor, timestamp)) {
            ++cnt;
        }

        ue = &initEv(&nev[cnt], timestamp,
            SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED,
            COMMS_SENSOR_MAG_UNCALIBRATED)->uncalibrated_magnetic;
        ue->x_uncalib = sample->ix * mScaleMag + mMagBias[0];
        ue->y_uncalib = sample->iy * mScaleMag + mMagBias[1];
        ue->z_uncalib = sample->iz * mScaleMag + mMagBias[2];
        ue->x_bias = mMagBias[0];
        ue->y_bias = mMagBias[1];
        ue->z_bias = mMagBias[2];

        sendDirectReportEvent(&nev[cnt], 1);
        if (mSensorState[COMMS_SENSOR_MAG_UNCALIBRATED].enable
                && isSampleIntervalSatisfied(COMMS_SENSOR_MAG_UNCALIBRATED, timestamp)) {
            ++cnt;
        }
    default:
        break;
    }

    if (cnt > 0)
        write(nev, cnt);
}

void HubConnection::processSample(uint64_t timestamp, uint32_t type, uint32_t sensor, struct ThreeAxisSample *sample, bool highAccuracy)
{
    sensors_vec_t *sv;
    uncalibrated_event_t *ue;
    sensors_event_t *ev;
    sensors_event_t nev[3];
    static const float heading_accuracy = M_PI / 6.0f;
    float w;
    int cnt = 0;

    switch (sensor) {
    case COMMS_SENSOR_ACCEL:
        sv = &initEv(&nev[cnt], timestamp, type, sensor)->acceleration;
        sv->x = sample->x;
        sv->y = sample->y;
        sv->z = sample->z;
        sv->status = SENSOR_STATUS_ACCURACY_HIGH;

        sendDirectReportEvent(&nev[cnt], 1);
        if (mSensorState[sensor].enable && isSampleIntervalSatisfied(sensor, timestamp)) {
            ++cnt;
        }

        ue = &initEv(&nev[cnt], timestamp,
            SENSOR_TYPE_ACCELEROMETER_UNCALIBRATED,
            COMMS_SENSOR_ACCEL_UNCALIBRATED)->uncalibrated_accelerometer;
        ue->x_uncalib = sample->x + mAccelBias[0];
        ue->y_uncalib = sample->y + mAccelBias[1];
        ue->z_uncalib = sample->z + mAccelBias[2];
        ue->x_bias = mAccelBias[0];
        ue->y_bias = mAccelBias[1];
        ue->z_bias = mAccelBias[2];

        sendDirectReportEvent(&nev[cnt], 1);
        if (mSensorState[COMMS_SENSOR_ACCEL_UNCALIBRATED].enable
                && isSampleIntervalSatisfied(COMMS_SENSOR_ACCEL_UNCALIBRATED, timestamp)) {
            ++cnt;
        }

        if (mSensorState[COMMS_SENSOR_ACCEL_WRIST_AWARE].enable
                && isSampleIntervalSatisfied(COMMS_SENSOR_ACCEL_WRIST_AWARE, timestamp)) {
            sv = &initEv(&nev[cnt], timestamp,
                SENSOR_TYPE_ACCELEROMETER_WRIST_AWARE,
                COMMS_SENSOR_ACCEL_WRIST_AWARE)->acceleration;
            sv->x = sample->x;
            sv->y = (mLefty.accel ? -sample->y : sample->y);
            sv->z = sample->z;
            sv->status = SENSOR_STATUS_ACCURACY_HIGH;
            ++cnt;
        }
        break;
    case COMMS_SENSOR_GYRO:
        sv = &initEv(&nev[cnt], timestamp, type, sensor)->gyro;
        sv->x = sample->x;
        sv->y = sample->y;
        sv->z = sample->z;
        sv->status = SENSOR_STATUS_ACCURACY_HIGH;

        sendDirectReportEvent(&nev[cnt], 1);
        if (mSensorState[sensor].enable && isSampleIntervalSatisfied(sensor, timestamp)) {
            ++cnt;
        }

        ue = &initEv(&nev[cnt], timestamp,
            SENSOR_TYPE_GYROSCOPE_UNCALIBRATED,
            COMMS_SENSOR_GYRO_UNCALIBRATED)->uncalibrated_gyro;
        ue->x_uncalib = sample->x + mGyroBias[0];
        ue->y_uncalib = sample->y + mGyroBias[1];
        ue->z_uncalib = sample->z + mGyroBias[2];
        ue->x_bias = mGyroBias[0];
        ue->y_bias = mGyroBias[1];
        ue->z_bias = mGyroBias[2];
        sendDirectReportEvent(&nev[cnt], 1);

        if (mSensorState[COMMS_SENSOR_GYRO_UNCALIBRATED].enable
                && isSampleIntervalSatisfied(COMMS_SENSOR_GYRO_UNCALIBRATED, timestamp)) {
            ++cnt;
        }

        if (mSensorState[COMMS_SENSOR_GYRO_WRIST_AWARE].enable
                && isSampleIntervalSatisfied(COMMS_SENSOR_GYRO_WRIST_AWARE, timestamp)) {
            sv = &initEv(&nev[cnt], timestamp,
                SENSOR_TYPE_GYROSCOPE_WRIST_AWARE,
                COMMS_SENSOR_GYRO_WRIST_AWARE)->gyro;
            sv->x = (mLefty.gyro ? -sample->x : sample->x);
            sv->y = sample->y;
            sv->z = (mLefty.gyro ? -sample->z : sample->z);
            sv->status = SENSOR_STATUS_ACCURACY_HIGH;
            ++cnt;
        }
        break;
    case COMMS_SENSOR_ACCEL_BIAS:
        mAccelBias[0] = sample->x;
        mAccelBias[1] = sample->y;
        mAccelBias[2] = sample->z;
        saveSensorSettings();
        break;
    case COMMS_SENSOR_GYRO_BIAS:
        mGyroBias[0] = sample->x;
        mGyroBias[1] = sample->y;
        mGyroBias[2] = sample->z;
        saveSensorSettings();
        break;
    case COMMS_SENSOR_MAG:
        sv = &initEv(&nev[cnt], timestamp, type, sensor)->magnetic;
        sv->x = sample->x;
        sv->y = sample->y;
        sv->z = sample->z;
        sv->status = magAccuracyUpdate(sv);
        sendDirectReportEvent(&nev[cnt], 1);

        if (mSensorState[sensor].enable && isSampleIntervalSatisfied(sensor, timestamp)) {
            ++cnt;
        }

        ue = &initEv(&nev[cnt], timestamp,
            SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED,
            COMMS_SENSOR_MAG_UNCALIBRATED)->uncalibrated_magnetic;
        ue->x_uncalib = sample->x + mMagBias[0];
        ue->y_uncalib = sample->y + mMagBias[1];
        ue->z_uncalib = sample->z + mMagBias[2];
        ue->x_bias = mMagBias[0];
        ue->y_bias = mMagBias[1];
        ue->z_bias = mMagBias[2];
        sendDirectReportEvent(&nev[cnt], 1);

        if (mSensorState[COMMS_SENSOR_MAG_UNCALIBRATED].enable
                && isSampleIntervalSatisfied(COMMS_SENSOR_MAG_UNCALIBRATED, timestamp)) {
            ++cnt;
        }
        break;
    case COMMS_SENSOR_MAG_BIAS:
        mMagAccuracy = highAccuracy ? SENSOR_STATUS_ACCURACY_HIGH : SENSOR_STATUS_ACCURACY_MEDIUM;
        mMagBias[0] = sample->x;
        mMagBias[1] = sample->y;
        mMagBias[2] = sample->z;

        saveSensorSettings();
        break;
    case COMMS_SENSOR_ORIENTATION:
    case COMMS_SENSOR_LINEAR_ACCEL:
    case COMMS_SENSOR_GRAVITY:
        sv = &initEv(&nev[cnt++], timestamp, type, sensor)->orientation;
        sv->x = sample->x;
        sv->y = sample->y;
        sv->z = sample->z;
        sv->status = mMagAccuracy;
        break;
    case COMMS_SENSOR_DOUBLE_TAP:
        ev = initEv(&nev[cnt++], timestamp, type, sensor);
        ev->data[0] = sample->x;
        ev->data[1] = sample->y;
        ev->data[2] = sample->z;
        break;
    case COMMS_SENSOR_ROTATION_VECTOR:
        ev = initEv(&nev[cnt++], timestamp, type, sensor);
        w = sample->x * sample->x + sample->y * sample->y + sample->z * sample->z;
        if (w < 1.0f)
            w = sqrt(1.0f - w);
        else
            w = 0.0f;
        ev->data[0] = sample->x;
        ev->data[1] = sample->y;
        ev->data[2] = sample->z;
        ev->data[3] = w;
        ev->data[4] = (4 - mMagAccuracy) * heading_accuracy;
        break;
    case COMMS_SENSOR_GEO_MAG:
    case COMMS_SENSOR_GAME_ROTATION_VECTOR:
        ev = initEv(&nev[cnt++], timestamp, type, sensor);
        w = sample->x * sample->x + sample->y * sample->y + sample->z * sample->z;
        if (w < 1.0f)
            w = sqrt(1.0f - w);
        else
            w = 0.0f;
        ev->data[0] = sample->x;
        ev->data[1] = sample->y;
        ev->data[2] = sample->z;
        ev->data[3] = w;
        break;
    default:
        break;
    }

    if (cnt > 0)
        write(nev, cnt);
}

void HubConnection::discardInotifyEvent() {
    // Read & discard an inotify event. We only use the presence of an event as
    // a trigger to perform the file existence check (for simplicity)
    if (mInotifyPollIndex >= 0) {
        char buf[sizeof(struct inotify_event) + NAME_MAX + 1];
        int ret = ::read(mPollFds[mInotifyPollIndex].fd, buf, sizeof(buf));
        ALOGV("Discarded %d bytes of inotify data", ret);
    }
}

void HubConnection::waitOnNanohubLock() {
    if (mInotifyPollIndex < 0) {
        return;
    }
    struct pollfd *pfd = &mPollFds[mInotifyPollIndex];

    // While the lock file exists, poll on the inotify fd (with timeout)
    while (access(NANOHUB_LOCK_FILE, F_OK) == 0) {
        ALOGW("Nanohub is locked; blocking read thread");
        int ret = poll(pfd, 1, 5000);
        if ((ret > 0) && (pfd->revents & POLLIN)) {
            discardInotifyEvent();
        }
    }
}

void HubConnection::restoreSensorState()
{
    Mutex::Autolock autoLock(mLock);

    sendCalibrationOffsets();

    for (int i = 0; i < NUM_COMMS_SENSORS_PLUS_1; i++) {
        if (mSensorState[i].sensorType && mSensorState[i].enable) {
            struct ConfigCmd cmd;

            initConfigCmd(&cmd, i);

            ALOGV("restoring: sensor=%d, handle=%d, enable=%d, period=%" PRId64 ", latency=%" PRId64,
                  cmd.sensorType, i, mSensorState[i].enable, frequency_q10_to_period_ns(mSensorState[i].rate),
                  mSensorState[i].latency);

            int ret = sendCmd(&cmd, sizeof(cmd));
            if (ret != sizeof(cmd)) {
                ALOGW("failed to send config command to restore sensor %d\n", cmd.sensorType);
            }

            cmd.cmd = CONFIG_CMD_FLUSH;

            for (auto iter = mFlushesPending[i].cbegin(); iter != mFlushesPending[i].cend(); ++iter) {
                for (int j = 0; j < iter->count; j++) {
                    int ret = sendCmd(&cmd, sizeof(cmd));
                    if (ret != sizeof(cmd)) {
                        ALOGW("failed to send flush command to sensor %d\n", cmd.sensorType);
                    }
                }
            }
        }
    }

    mStepCounterOffset = mLastStepCount;

    if (mActivityEventHandler != NULL) {
        mActivityEventHandler->OnSensorHubReset();
    }
}

void HubConnection::postOsLog(uint8_t *buf, ssize_t len)
{
    // if len is less than 6, it's either an invalid or an empty log message.
    if (len < 6)
        return;

    buf[len] = 0x00;
    switch (buf[4]) {
    case 'E':
        ALOGE("osLog: %s", &buf[5]);
        break;
    case 'W':
        ALOGW("osLog: %s", &buf[5]);
        break;
    case 'I':
        ALOGI("osLog: %s", &buf[5]);
        break;
    case 'D':
        ALOGD("osLog: %s", &buf[5]);
        break;
    case 'V':
        ALOGV("osLog: %s", &buf[5]);
        break;
    default:
        break;
    }
}

void HubConnection::processAppData(uint8_t *buf, ssize_t len) {
    if (len < static_cast<ssize_t>(sizeof(AppToSensorHalDataBuffer)))
        return;

    AppToSensorHalDataPayload *data =
            &(reinterpret_cast<AppToSensorHalDataBuffer *>(buf)->payload);
    if (data->size + sizeof(AppToSensorHalDataBuffer) != len) {
        ALOGW("Received corrupted data update packet, len %zd, size %u", len, data->size);
        return;
    }

    switch (data->type & APP_TO_SENSOR_HAL_TYPE_MASK) {
    case HALINTF_TYPE_GYRO_OTC_DATA:
        if (data->size != sizeof(GyroOtcData)) {
            ALOGW("Corrupted HALINTF_TYPE_GYRO_OTC_DATA with size %u", data->size);
            return;
        }
        mGyroOtcData = data->gyroOtcData[0];
        saveSensorSettings();
        break;
    default:
        ALOGW("Unknown app to hal data type 0x%04x", data->type);
        break;
    }
}

ssize_t HubConnection::processBuf(uint8_t *buf, size_t len)
{
    struct nAxisEvent *data = (struct nAxisEvent *)buf;
    uint32_t type, sensor, bias, currSensor;
    int i, numSamples;
    bool one, rawThree, three;
    sensors_event_t ev;
    uint64_t timestamp;
    ssize_t ret = 0;
    uint32_t primary;

    if (len >= sizeof(data->evtType)) {
        ret = sizeof(data->evtType);
        one = three = rawThree = false;
        bias = 0;
        switch (data->evtType) {
        case OS_LOG_EVENT:
            postOsLog(buf, len);
            return 0;
        case EVT_APP_TO_SENSOR_HAL_DATA:
            processAppData(buf, len);
            return 0;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ACCEL):
            type = SENSOR_TYPE_ACCELEROMETER;
            sensor = COMMS_SENSOR_ACCEL;
            bias = COMMS_SENSOR_ACCEL_BIAS;
            three = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ACCEL_RAW):
            type = SENSOR_TYPE_ACCELEROMETER;
            sensor = COMMS_SENSOR_ACCEL;
            rawThree = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_GYRO):
            type = SENSOR_TYPE_GYROSCOPE;
            sensor = COMMS_SENSOR_GYRO;
            bias = COMMS_SENSOR_GYRO_BIAS;
            three = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_MAG):
            type = SENSOR_TYPE_MAGNETIC_FIELD;
            sensor = COMMS_SENSOR_MAG;
            bias = COMMS_SENSOR_MAG_BIAS;
            three = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_MAG_RAW):
            type = SENSOR_TYPE_MAGNETIC_FIELD;
            sensor = COMMS_SENSOR_MAG;
            rawThree = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ALS):
            type = SENSOR_TYPE_LIGHT;
            sensor = COMMS_SENSOR_LIGHT;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_PROX):
            type = SENSOR_TYPE_PROXIMITY;
            sensor = COMMS_SENSOR_PROXIMITY;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_BARO):
            type = SENSOR_TYPE_PRESSURE;
            sensor = COMMS_SENSOR_PRESSURE;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_HUMIDITY):
            type = SENSOR_TYPE_RELATIVE_HUMIDITY;
            sensor = COMMS_SENSOR_HUMIDITY;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_TEMP):
            // nanohub only has one temperature sensor type, which is mapped to
            // internal temp because we currently don't have ambient temp
            type = SENSOR_TYPE_INTERNAL_TEMPERATURE;
            sensor = COMMS_SENSOR_TEMPERATURE;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ORIENTATION):
            type = SENSOR_TYPE_ORIENTATION;
            sensor = COMMS_SENSOR_ORIENTATION;
            three = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_WIN_ORIENTATION):
            type = SENSOR_TYPE_DEVICE_ORIENTATION;
            sensor = COMMS_SENSOR_WINDOW_ORIENTATION;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_STEP_DETECT):
            type = SENSOR_TYPE_STEP_DETECTOR;
            sensor = COMMS_SENSOR_STEP_DETECTOR;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_STEP_COUNT):
            type = SENSOR_TYPE_STEP_COUNTER;
            sensor = COMMS_SENSOR_STEP_COUNTER;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_SIG_MOTION):
            type = SENSOR_TYPE_SIGNIFICANT_MOTION;
            sensor = COMMS_SENSOR_SIGNIFICANT_MOTION;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_GRAVITY):
            type = SENSOR_TYPE_GRAVITY;
            sensor = COMMS_SENSOR_GRAVITY;
            three = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_LINEAR_ACCEL):
            type = SENSOR_TYPE_LINEAR_ACCELERATION;
            sensor = COMMS_SENSOR_LINEAR_ACCEL;
            three = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ROTATION_VECTOR):
            type = SENSOR_TYPE_ROTATION_VECTOR;
            sensor = COMMS_SENSOR_ROTATION_VECTOR;
            three = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_GEO_MAG_ROT_VEC):
            type = SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR;
            sensor = COMMS_SENSOR_GEO_MAG;
            three = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_GAME_ROT_VECTOR):
            type = SENSOR_TYPE_GAME_ROTATION_VECTOR;
            sensor = COMMS_SENSOR_GAME_ROTATION_VECTOR;
            three = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_HALL):
            type = 0;
            sensor = COMMS_SENSOR_HALL;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_VSYNC):
            type = SENSOR_TYPE_SYNC;
            sensor = COMMS_SENSOR_SYNC;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_TILT):
            type = SENSOR_TYPE_TILT_DETECTOR;
            sensor = COMMS_SENSOR_TILT;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_GESTURE):
            type = SENSOR_TYPE_PICK_UP_GESTURE;
            sensor = COMMS_SENSOR_GESTURE;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_DOUBLE_TWIST):
            type = SENSOR_TYPE_DOUBLE_TWIST;
            sensor = COMMS_SENSOR_DOUBLE_TWIST;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_DOUBLE_TAP):
            type = SENSOR_TYPE_DOUBLE_TAP;
            sensor = COMMS_SENSOR_DOUBLE_TAP;
            three = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_WRIST_TILT):
            type = SENSOR_TYPE_WRIST_TILT_GESTURE;
            sensor = COMMS_SENSOR_WRIST_TILT;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_DOUBLE_TOUCH):
            type = SENSOR_TYPE_DOUBLE_TOUCH;
            sensor = COMMS_SENSOR_DOUBLE_TOUCH;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ACTIVITY_IN_VEHICLE_START):
            type = 0;
            sensor = COMMS_SENSOR_ACTIVITY_IN_VEHICLE_START;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ACTIVITY_IN_VEHICLE_STOP):
            type = 0;
            sensor = COMMS_SENSOR_ACTIVITY_IN_VEHICLE_STOP;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ACTIVITY_ON_BICYCLE_START):
            type = 0;
            sensor = COMMS_SENSOR_ACTIVITY_ON_BICYCLE_START;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ACTIVITY_ON_BICYCLE_STOP):
            type = 0;
            sensor = COMMS_SENSOR_ACTIVITY_ON_BICYCLE_STOP;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ACTIVITY_WALKING_START):
            type = 0;
            sensor = COMMS_SENSOR_ACTIVITY_WALKING_START;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ACTIVITY_WALKING_STOP):
            type = 0;
            sensor = COMMS_SENSOR_ACTIVITY_WALKING_STOP;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ACTIVITY_RUNNING_START):
            type = 0;
            sensor = COMMS_SENSOR_ACTIVITY_RUNNING_START;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ACTIVITY_RUNNING_STOP):
            type = 0;
            sensor = COMMS_SENSOR_ACTIVITY_RUNNING_STOP;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ACTIVITY_STILL_START):
            type = 0;
            sensor = COMMS_SENSOR_ACTIVITY_STILL_START;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ACTIVITY_STILL_STOP):
            type = 0;
            sensor = COMMS_SENSOR_ACTIVITY_STILL_STOP;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_ACTIVITY_TILTING):
            type = 0;
            sensor = COMMS_SENSOR_ACTIVITY_TILTING;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_GAZE):
            type = SENSOR_TYPE_GAZE;
            sensor = COMMS_SENSOR_GAZE;
            one = true;
            break;
        case SENS_TYPE_TO_EVENT(SENS_TYPE_UNGAZE):
            type = SENSOR_TYPE_UNGAZE;
            sensor = COMMS_SENSOR_UNGAZE;
            one = true;
            break;
        case EVT_RESET_REASON:
            uint32_t resetReason;
            memcpy(&resetReason, data->buffer, sizeof(resetReason));
            ALOGI("Observed hub reset: 0x%08" PRIx32, resetReason);
            restoreSensorState();
            return 0;
        default:
            ALOGW("unknown evtType: 0x%08x len: %zu\n", data->evtType, len);
            return -1;
        }
    } else {
        ALOGW("too little data: len=%zu\n", len);
        return -1;
    }

    if (len >= sizeof(data->evtType) + sizeof(data->referenceTime) + sizeof(data->firstSample)) {
        ret += sizeof(data->referenceTime);
        timestamp = data->referenceTime;
        numSamples = data->firstSample.numSamples;
        for (i=0; i<numSamples; i++) {
            if (data->firstSample.biasPresent && data->firstSample.biasSample == i)
                currSensor = bias;
            else
                currSensor = sensor;

            if (one) {
                if (ret + sizeof(data->oneSamples[i]) > len) {
                    ALOGW("sensor %d (one): ret=%zd, numSamples=%d, i=%d\n", currSensor, ret, numSamples, i);
                    return -1;
                }
                if (i > 0)
                    timestamp += ((uint64_t)data->oneSamples[i].deltaTime) << delta_time_shift_table[data->oneSamples[i].deltaTime & delta_time_encoded];
                processSample(timestamp, type, currSensor, &data->oneSamples[i], data->firstSample.highAccuracy);
                ret += sizeof(data->oneSamples[i]);
            } else if (rawThree) {
                if (ret + sizeof(data->rawThreeSamples[i]) > len) {
                    ALOGW("sensor %d (rawThree): ret=%zd, numSamples=%d, i=%d\n", currSensor, ret, numSamples, i);
                    return -1;
                }
                if (i > 0)
                    timestamp += ((uint64_t)data->rawThreeSamples[i].deltaTime) << delta_time_shift_table[data->rawThreeSamples[i].deltaTime & delta_time_encoded];
                processSample(timestamp, type, currSensor, &data->rawThreeSamples[i], data->firstSample.highAccuracy);
                ret += sizeof(data->rawThreeSamples[i]);
            } else if (three) {
                if (ret + sizeof(data->threeSamples[i]) > len) {
                    ALOGW("sensor %d (three): ret=%zd, numSamples=%d, i=%d\n", currSensor, ret, numSamples, i);
                    return -1;
                }
                if (i > 0)
                    timestamp += ((uint64_t)data->threeSamples[i].deltaTime) << delta_time_shift_table[data->threeSamples[i].deltaTime & delta_time_encoded];
                processSample(timestamp, type, currSensor, &data->threeSamples[i], data->firstSample.highAccuracy);
                ret += sizeof(data->threeSamples[i]);
            } else {
                ALOGW("sensor %d (unknown): cannot processSample\n", currSensor);
                return -1;
            }
        }

        if (!numSamples)
            ret += sizeof(data->firstSample);

        // If no primary sensor type is specified,
        // then 'sensor' is the primary sensor type.
        primary = mSensorState[sensor].primary;
        primary = (primary ? primary : sensor);

        for (i=0; i<data->firstSample.numFlushes; i++) {
            if (isActivitySensor(sensor) && mActivityEventHandler != NULL) {
                mActivityEventHandler->OnFlush();
            } else {
                struct Flush& flush = mFlushesPending[primary].front();
                memset(&ev, 0x00, sizeof(sensors_event_t));
                ev.version = META_DATA_VERSION;
                ev.timestamp = 0;
                ev.type = SENSOR_TYPE_META_DATA;
                ev.sensor = 0;
                ev.meta_data.what = META_DATA_FLUSH_COMPLETE;
                ev.meta_data.sensor = flush.handle;

                if (flush.internal) {
                    if (flush.handle == COMMS_SENSOR_ACCEL_WRIST_AWARE)
                        mLefty.accel = !mLefty.accel;
                    else if (flush.handle == COMMS_SENSOR_GYRO_WRIST_AWARE)
                        mLefty.gyro = !mLefty.gyro;
                } else
                    write(&ev, 1);

                if (--flush.count == 0)
                    mFlushesPending[primary].pop_front();

                ALOGV("flushing %d", ev.meta_data.sensor);
            }
        }
    } else {
        ALOGW("too little data for sensor %d: len=%zu\n", sensor, len);
        return -1;
    }

    return ret;
}

void HubConnection::sendCalibrationOffsets()
{
    sp<JSONObject> settings;
    sp<JSONObject> saved_settings;
    struct {
        int32_t hw[3];
        float sw[3];
    } accel;

    int32_t proximity, proximity_array[4];
    float barometer, humidity, light;
    bool accel_hw_cal_exists, accel_sw_cal_exists;

    loadSensorSettings(&settings, &saved_settings);

    accel_hw_cal_exists = getCalibrationInt32(settings, ACCEL_BIAS_TAG, accel.hw, 3);
    accel_sw_cal_exists = getCalibrationFloat(saved_settings, ACCEL_SW_BIAS_TAG, accel.sw);
    if (accel_hw_cal_exists || accel_sw_cal_exists) {
        // Store SW bias so we can remove bias for uncal data
        mAccelBias[0] = accel.sw[0];
        mAccelBias[1] = accel.sw[1];
        mAccelBias[2] = accel.sw[2];

        queueDataInternal(COMMS_SENSOR_ACCEL, &accel, sizeof(accel));
    }

    ALOGV("Use new configuration format");
    std::vector<int32_t> hardwareGyroBias = getInt32Setting(settings, GYRO_BIAS_TAG);
    std::vector<float> softwareGyroBias = getFloatSetting(saved_settings, GYRO_SW_BIAS_TAG);
    if (hardwareGyroBias.size() == 3 || softwareGyroBias.size() == 3) {
        struct {
            AppToSensorHalDataPayload header;
            GyroCalBias data;
        } packet = {
            .header = {
                .size = sizeof(GyroCalBias),
                .type = HALINTF_TYPE_GYRO_CAL_BIAS }
        };
        if (hardwareGyroBias.size() == 3) {
            std::copy(hardwareGyroBias.begin(), hardwareGyroBias.end(),
                      packet.data.hardwareBias);
        }
        if (softwareGyroBias.size() == 3) {
            // Store SW bias so we can remove bias for uncal data
            std::copy(softwareGyroBias.begin(), softwareGyroBias.end(),
                      mGyroBias);

            std::copy(softwareGyroBias.begin(), softwareGyroBias.end(),
                      packet.data.softwareBias);
        }
        // send packet to hub
        queueDataInternal(COMMS_SENSOR_GYRO, &packet, sizeof(packet));
    }

    // over temp cal
    std::vector<float> gyroOtcData = getFloatSetting(saved_settings, GYRO_OTC_DATA_TAG);
    if (gyroOtcData.size() == sizeof(GyroOtcData) / sizeof(float)) {
        std::copy(gyroOtcData.begin(), gyroOtcData.end(),
                  reinterpret_cast<float*>(&mGyroOtcData));
        struct {
            AppToSensorHalDataPayload header;
            GyroOtcData data;
        } packet = {
            .header = {
                .size = sizeof(GyroOtcData),
                .type = HALINTF_TYPE_GYRO_OTC_DATA },
            .data = mGyroOtcData
        };

        // send it to hub
        queueDataInternal(COMMS_SENSOR_GYRO, &packet, sizeof(packet));
    } else {
        ALOGW("Illegal otc_gyro data size = %zu", gyroOtcData.size());
    }

    std::vector<float> magBiasData = getFloatSetting(saved_settings, MAG_BIAS_TAG);
    if (magBiasData.size() == 3) {
        // Store SW bias so we can remove bias for uncal data
        std::copy(magBiasData.begin(), magBiasData.end(), mMagBias);

        struct {
            AppToSensorHalDataPayload header;
            MagCalBias mag;
        } packet = {
            .header = {
                .size = sizeof(MagCalBias),
                .type = HALINTF_TYPE_MAG_CAL_BIAS }
        };
        std::copy(magBiasData.begin(), magBiasData.end(), packet.mag.bias);
        queueDataInternal(COMMS_SENSOR_MAG, &packet, sizeof(packet));
    }

    if (settings->getFloat("barometer", &barometer))
        queueDataInternal(COMMS_SENSOR_PRESSURE, &barometer, sizeof(barometer));

    if (settings->getFloat("humidity", &humidity))
        queueDataInternal(COMMS_SENSOR_HUMIDITY, &humidity, sizeof(humidity));

    if (settings->getInt32("proximity", &proximity))
        queueDataInternal(COMMS_SENSOR_PROXIMITY, &proximity, sizeof(proximity));

    if (getCalibrationInt32(settings, "proximity", proximity_array, 4))
        queueDataInternal(COMMS_SENSOR_PROXIMITY, proximity_array, sizeof(proximity_array));

    if (settings->getFloat("light", &light))
        queueDataInternal(COMMS_SENSOR_LIGHT, &light, sizeof(light));
}

bool HubConnection::threadLoop() {
    ALOGV("threadLoop: starting");

    if (mFd < 0) {
        ALOGW("threadLoop: exiting prematurely: nanohub is unavailable");
        return false;
    }
    waitOnNanohubLock();

    sendCalibrationOffsets();

    while (!Thread::exitPending()) {
        ssize_t ret;

        do {
            ret = poll(mPollFds, mNumPollFds, -1);
        } while (ret < 0 && errno == EINTR);

        if (mInotifyPollIndex >= 0 && mPollFds[mInotifyPollIndex].revents & POLLIN) {
            discardInotifyEvent();
            waitOnNanohubLock();
        }

#ifdef USB_MAG_BIAS_REPORTING_ENABLED
        if (mMagBiasPollIndex >= 0 && mPollFds[mMagBiasPollIndex].revents & POLLERR) {
            // Read from mag bias file
            char buf[16];
            lseek(mPollFds[mMagBiasPollIndex].fd, 0, SEEK_SET);
            ::read(mPollFds[mMagBiasPollIndex].fd, buf, 16);
            float bias = atof(buf);
            mUsbMagBias = bias;
            queueUsbMagBias();
        }
#endif // USB_MAG_BIAS_REPORTING_ENABLED

#ifdef DOUBLE_TOUCH_ENABLED
        if (mDoubleTouchPollIndex >= 0 && mPollFds[mDoubleTouchPollIndex].revents & POLLERR) {
            // Read from double touch file
            char buf[16];
            lseek(mPollFds[mDoubleTouchPollIndex].fd, 0, SEEK_SET);
            ::read(mPollFds[mDoubleTouchPollIndex].fd, buf, 16);
            sensors_event_t gestureEvent;
            initEv(&gestureEvent, elapsedRealtimeNano(), SENSOR_TYPE_PICK_UP_GESTURE, COMMS_SENSOR_GESTURE)->data[0] = 8;
            write(&gestureEvent, 1);
        }
#endif // DOUBLE_TOUCH_ENABLED

        if (mPollFds[0].revents & POLLIN) {
            uint8_t recv[256];
            ssize_t len = ::read(mFd, recv, sizeof(recv));

            if (len >= 0) {
                for (ssize_t offset = 0; offset < len;) {
                    ret = processBuf(recv + offset, len - offset);

                    if (ret > 0)
                        offset += ret;
                    else
                        break;
                }
            } else {
                ALOGW("read -1: errno=%d\n", errno);
            }
        }
    }

    return false;
}

void HubConnection::setActivityCallback(ActivityEventHandler *eventHandler)
{
    Mutex::Autolock autoLock(mLock);
    mActivityEventHandler = eventHandler;
}

void HubConnection::initConfigCmd(struct ConfigCmd *cmd, int handle)
{
    memset(cmd, 0x00, sizeof(*cmd));

    cmd->evtType = EVT_NO_SENSOR_CONFIG_EVENT;
    cmd->sensorType = mSensorState[handle].sensorType;
    cmd->cmd = mSensorState[handle].enable ? CONFIG_CMD_ENABLE : CONFIG_CMD_DISABLE;
    cmd->rate = mSensorState[handle].rate;
    cmd->latency = mSensorState[handle].latency;

    for (int i=0; i<MAX_ALTERNATES; ++i) {
        uint8_t alt = mSensorState[handle].alt[i];

        if (alt == COMMS_SENSOR_INVALID) continue;
        if (!mSensorState[alt].enable) continue;

        cmd->cmd = CONFIG_CMD_ENABLE;

        if (mSensorState[alt].rate > cmd->rate) {
            cmd->rate = mSensorState[alt].rate;
        }
        if (mSensorState[alt].latency < cmd->latency) {
            cmd->latency = mSensorState[alt].latency;
        }
    }

    // will be a nop if direct report mode is not enabled
    mergeDirectReportRequest(cmd, handle);
}

void HubConnection::queueActivate(int handle, bool enable)
{
    struct ConfigCmd cmd;
    int ret;

    Mutex::Autolock autoLock(mLock);

    if (isValidHandle(handle)) {
        mSensorState[handle].enable = enable;

        initConfigCmd(&cmd, handle);

        ret = sendCmd(&cmd, sizeof(cmd));
        if (ret == sizeof(cmd)) {
            updateSampleRate(handle, enable ? CONFIG_CMD_ENABLE : CONFIG_CMD_DISABLE);
            ALOGV("queueActivate: sensor=%d, handle=%d, enable=%d",
                    cmd.sensorType, handle, enable);
        }
        else
            ALOGW("queueActivate: failed to send command: sensor=%d, handle=%d, enable=%d",
                    cmd.sensorType, handle, enable);
    } else {
        ALOGV("queueActivate: unhandled handle=%d, enable=%d", handle, enable);
    }
}

void HubConnection::queueSetDelay(int handle, nsecs_t sampling_period_ns)
{
    struct ConfigCmd cmd;
    int ret;

    Mutex::Autolock autoLock(mLock);

    if (isValidHandle(handle)) {
        if (sampling_period_ns > 0 &&
                mSensorState[handle].rate != SENSOR_RATE_ONCHANGE &&
                mSensorState[handle].rate != SENSOR_RATE_ONESHOT) {
            mSensorState[handle].rate = period_ns_to_frequency_q10(sampling_period_ns);
        }

        initConfigCmd(&cmd, handle);

        ret = sendCmd(&cmd, sizeof(cmd));
        if (ret == sizeof(cmd))
            ALOGV("queueSetDelay: sensor=%d, handle=%d, period=%" PRId64,
                    cmd.sensorType, handle, sampling_period_ns);
        else
            ALOGW("queueSetDelay: failed to send command: sensor=%d, handle=%d, period=%" PRId64,
                    cmd.sensorType, handle, sampling_period_ns);
    } else {
        ALOGV("queueSetDelay: unhandled handle=%d, period=%" PRId64, handle, sampling_period_ns);
    }
}

void HubConnection::queueBatch(
        int handle,
        nsecs_t sampling_period_ns,
        nsecs_t max_report_latency_ns)
{
    struct ConfigCmd cmd;
    int ret;

    Mutex::Autolock autoLock(mLock);

    if (isValidHandle(handle)) {
        if (sampling_period_ns > 0 &&
                mSensorState[handle].rate != SENSOR_RATE_ONCHANGE &&
                mSensorState[handle].rate != SENSOR_RATE_ONESHOT) {
            mSensorState[handle].rate = period_ns_to_frequency_q10(sampling_period_ns);
        }
        mSensorState[handle].latency = max_report_latency_ns;

        initConfigCmd(&cmd, handle);

        ret = sendCmd(&cmd, sizeof(cmd));
        if (ret == sizeof(cmd)) {
            updateSampleRate(handle, CONFIG_CMD_ENABLE); // batch uses CONFIG_CMD_ENABLE command
            ALOGV("queueBatch: sensor=%d, handle=%d, period=%" PRId64 ", latency=%" PRId64,
                    cmd.sensorType, handle, sampling_period_ns, max_report_latency_ns);
        } else {
            ALOGW("queueBatch: failed to send command: sensor=%d, handle=%d, period=%" PRId64 ", latency=%" PRId64,
                    cmd.sensorType, handle, sampling_period_ns, max_report_latency_ns);
        }
    } else {
        ALOGV("queueBatch: unhandled handle=%d, period=%" PRId64 ", latency=%" PRId64,
                handle, sampling_period_ns, max_report_latency_ns);
    }
}

void HubConnection::queueFlush(int handle)
{
    Mutex::Autolock autoLock(mLock);
    queueFlushInternal(handle, false);
}

void HubConnection::queueFlushInternal(int handle, bool internal)
{
    struct ConfigCmd cmd;
    uint32_t primary;
    int ret;

    if (isValidHandle(handle)) {
        // If no primary sensor type is specified,
        // then 'handle' is the primary sensor type.
        primary = mSensorState[handle].primary;
        primary = (primary ? primary : handle);

        std::list<Flush>& flushList = mFlushesPending[primary];

        if (!flushList.empty() &&
            flushList.back().internal == internal &&
            flushList.back().handle == handle) {
            ++flushList.back().count;
        } else {
            flushList.push_back((struct Flush){handle, 1, internal});
        }

        initConfigCmd(&cmd, handle);
        cmd.cmd = CONFIG_CMD_FLUSH;

        ret = sendCmd(&cmd, sizeof(cmd));
        if (ret == sizeof(cmd)) {
            ALOGV("queueFlush: sensor=%d, handle=%d",
                    cmd.sensorType, handle);
        } else {
            ALOGW("queueFlush: failed to send command: sensor=%d, handle=%d"
                  " with error %s", cmd.sensorType, handle, strerror(errno));
        }
    } else {
        ALOGV("queueFlush: unhandled handle=%d", handle);
    }
}

void HubConnection::queueDataInternal(int handle, void *data, size_t length)
{
    struct ConfigCmd *cmd = (struct ConfigCmd *)malloc(sizeof(struct ConfigCmd) + length);
    size_t ret;

    if (cmd && isValidHandle(handle)) {
        initConfigCmd(cmd, handle);
        memcpy(cmd->data, data, length);
        cmd->cmd = CONFIG_CMD_CFG_DATA;

        ret = sendCmd(cmd, sizeof(*cmd) + length);
        if (ret == sizeof(*cmd) + length)
            ALOGV("queueData: sensor=%d, length=%zu",
                    cmd->sensorType, length);
        else
            ALOGW("queueData: failed to send command: sensor=%d, length=%zu",
                    cmd->sensorType, length);
    } else {
        ALOGV("queueData: unhandled handle=%d", handle);
    }
    free(cmd);
}

void HubConnection::queueData(int handle, void *data, size_t length)
{
    Mutex::Autolock autoLock(mLock);
    queueDataInternal(handle, data, length);
}

void HubConnection::setOperationParameter(const additional_info_event_t &info) {
    switch (info.type) {
        case AINFO_LOCAL_GEOMAGNETIC_FIELD: {
            ALOGV("local geomag field update: strength %fuT, dec %fdeg, inc %fdeg",
                  static_cast<double>(info.data_float[0]),
                  info.data_float[1] * 180 / M_PI,
                  info.data_float[2] * 180 / M_PI);

            struct {
                AppToSensorHalDataPayload header;
                MagLocalField magLocalField;
            } packet = {
                .header = {
                    .size = sizeof(MagLocalField),
                    .type = HALINTF_TYPE_MAG_LOCAL_FIELD },
                .magLocalField = {
                    .strength = info.data_float[0],
                    .declination = info.data_float[1],
                    .inclination = info.data_float[2]}
            };
            queueDataInternal(COMMS_SENSOR_MAG, &packet, sizeof(packet));
            break;
        }
        default:
            break;
    }
}

void HubConnection::initNanohubLock() {
    // Create the lock directory (if it doesn't already exist)
    if (mkdir(NANOHUB_LOCK_DIR, NANOHUB_LOCK_DIR_PERMS) < 0 && errno != EEXIST) {
        ALOGW("Couldn't create Nanohub lock directory: %s", strerror(errno));
        return;
    }

    mInotifyPollIndex = -1;
    int inotifyFd = inotify_init1(IN_NONBLOCK);
    if (inotifyFd < 0) {
        ALOGW("Couldn't initialize inotify: %s", strerror(errno));
    } else if (inotify_add_watch(inotifyFd, NANOHUB_LOCK_DIR, IN_CREATE | IN_DELETE) < 0) {
        ALOGW("Couldn't add inotify watch: %s", strerror(errno));
        close(inotifyFd);
    } else {
        mPollFds[mNumPollFds].fd = inotifyFd;
        mPollFds[mNumPollFds].events = POLLIN;
        mPollFds[mNumPollFds].revents = 0;
        mInotifyPollIndex = mNumPollFds;
        mNumPollFds++;
    }
}

ssize_t HubConnection::read(sensors_event_t *ev, size_t size) {
    ssize_t n = mRing.read(ev, size);

    Mutex::Autolock autoLock(mLock);

    // We log the first failure in write, so only log 2+ errors
    if (mWriteFailures > 1) {
        ALOGW("%s: mRing.write failed %d times",
              __FUNCTION__, mWriteFailures);
        mWriteFailures = 0;
    }

    for (ssize_t i = 0; i < n; i++)
        decrementIfWakeEventLocked(ev[i].sensor);

    return n;
}


ssize_t HubConnection::write(const sensors_event_t *ev, size_t n) {
    ssize_t ret = 0;

    Mutex::Autolock autoLock(mLock);

    for (size_t i=0; i<n; i++) {
        if (mRing.write(&ev[i], 1) == 1) {
            ret++;
            // If event is a wake event, protect it with a wakelock
            protectIfWakeEventLocked(ev[i].sensor);
        } else {
            if (mWriteFailures++ == 0)
                ALOGW("%s: mRing.write failed @ %zu/%zu",
                      __FUNCTION__, i, n);
            break;
        }
    }

    return ret;
}

#ifdef USB_MAG_BIAS_REPORTING_ENABLED
void HubConnection::queueUsbMagBias()
{
    struct MsgCmd *cmd = (struct MsgCmd *)malloc(sizeof(struct MsgCmd) + sizeof(float));
    size_t ret;

    if (cmd) {
        cmd->evtType = EVT_APP_FROM_HOST;
        cmd->msg.appId = APP_ID_MAKE(APP_ID_VENDOR_GOOGLE, APP_ID_APP_BMI160);
        cmd->msg.dataLen = sizeof(float);
        memcpy((float *)(cmd+1), &mUsbMagBias, sizeof(float));

        ret = sendCmd(cmd, sizeof(*cmd) + sizeof(float));
        if (ret == sizeof(*cmd) + sizeof(float))
            ALOGV("queueUsbMagBias: bias=%f\n", mUsbMagBias);
        else
            ALOGW("queueUsbMagBias: failed to send command: bias=%f\n", mUsbMagBias);
        free(cmd);
    }
}
#endif  // USB_MAG_BIAS_REPORTING_ENABLED

#ifdef LID_STATE_REPORTING_ENABLED
status_t HubConnection::initializeUinputNode()
{
    int ret = 0;

    // Open uinput dev node
    mUinputFd = TEMP_FAILURE_RETRY(open("/dev/uinput", O_WRONLY | O_NONBLOCK));
    if (mUinputFd < 0) {
        ALOGW("could not open uinput node: %s", strerror(errno));
        return UNKNOWN_ERROR;
    }

    // Enable SW_LID events
    ret  = TEMP_FAILURE_RETRY(ioctl(mUinputFd, UI_SET_EVBIT, EV_SW));
    ret |= TEMP_FAILURE_RETRY(ioctl(mUinputFd, UI_SET_EVBIT, EV_SYN));
    ret |= TEMP_FAILURE_RETRY(ioctl(mUinputFd, UI_SET_SWBIT, SW_LID));
    if (ret < 0) {
        ALOGW("could not send ioctl to uinput node: %s", strerror(errno));
        return UNKNOWN_ERROR;
    }

    // Create uinput node for SW_LID
    struct uinput_user_dev uidev;
    memset(&uidev, 0, sizeof(uidev));
    snprintf(uidev.name, UINPUT_MAX_NAME_SIZE, "uinput-folio");
    uidev.id.bustype = BUS_SPI;
    uidev.id.vendor  = 0;
    uidev.id.product = 0;
    uidev.id.version = 0;

    ret = TEMP_FAILURE_RETRY(::write(mUinputFd, &uidev, sizeof(uidev)));
    if (ret < 0) {
        ALOGW("write to uinput node failed: %s", strerror(errno));
        return UNKNOWN_ERROR;
    }

    ret = TEMP_FAILURE_RETRY(ioctl(mUinputFd, UI_DEV_CREATE));
    if (ret < 0) {
        ALOGW("could not send ioctl to uinput node: %s", strerror(errno));
        return UNKNOWN_ERROR;
    }

    return OK;
}

void HubConnection::sendFolioEvent(int32_t data) {
    ssize_t ret = 0;
    struct input_event ev;

    memset(&ev, 0, sizeof(ev));

    ev.type = EV_SW;
    ev.code = SW_LID;
    ev.value =  data;
    ret = TEMP_FAILURE_RETRY(::write(mUinputFd, &ev, sizeof(ev)));
    if (ret < 0) {
        ALOGW("write to uinput node failed: %s", strerror(errno));
        return;
    }

    // Force flush with EV_SYN event
    ev.type = EV_SYN;
    ev.code = SYN_REPORT;
    ev.value =  0;
    ret = TEMP_FAILURE_RETRY(::write(mUinputFd, &ev, sizeof(ev)));
    if (ret < 0) {
        ALOGW("write to uinput node failed: %s", strerror(errno));
        return;
    }

    // Set lid state property
    if (property_set(LID_STATE_PROPERTY,
                     (data ? LID_STATE_CLOSED : LID_STATE_OPEN)) < 0) {
        ALOGW("could not set lid_state property");
    }
}
#endif  // LID_STATE_REPORTING_ENABLED

#ifdef DIRECT_REPORT_ENABLED
void HubConnection::sendDirectReportEvent(const sensors_event_t *nev, size_t n) {
    // short circuit to avoid lock operation
    if (n == 0) {
        return;
    }

    // no intention to block sensor delivery thread. when lock is needed ignore
    // the event (this only happens when the channel is reconfiured, so it's ok
    if (mDirectChannelLock.tryLock() == NO_ERROR) {
        while (n--) {
            auto i = mSensorToChannel.find(nev->sensor);
            if (i != mSensorToChannel.end()) {
                for (auto &j : i->second) {
                    if ((uint64_t)nev->timestamp > j.second.lastTimestamp
                            && intervalLargeEnough(
                                nev->timestamp - j.second.lastTimestamp,
                                rateLevelToDeviceSamplingPeriodNs(
                                        nev->sensor, j.second.rateLevel))) {
                        mDirectChannel[j.first]->write(nev);
                        j.second.lastTimestamp = nev->timestamp;
                    }
                }
            }
            ++nev;
        }
        mDirectChannelLock.unlock();
    }
}

void HubConnection::mergeDirectReportRequest(struct ConfigCmd *cmd, int handle) {
    int maxRateLevel = SENSOR_DIRECT_RATE_STOP;

    auto j = mSensorToChannel.find(handle);
    if (j != mSensorToChannel.end()) {
        for (auto &i : j->second) {
            maxRateLevel = std::max(i.second.rateLevel, maxRateLevel);
        }
    }
    for (auto handle : mSensorState[handle].alt) {
        auto j = mSensorToChannel.find(handle);
        if (j != mSensorToChannel.end()) {
            for (auto &i : j->second) {
                maxRateLevel = std::max(i.second.rateLevel, maxRateLevel);
            }
        }
    }

    uint64_t period = rateLevelToDeviceSamplingPeriodNs(handle, maxRateLevel);
    if (period != INT64_MAX) {
        rate_q10_t rate;
        rate = period_ns_to_frequency_q10(period);

        cmd->rate = (rate > cmd->rate || cmd->cmd == CONFIG_CMD_DISABLE) ? rate : cmd->rate;
        cmd->latency = 0;
        cmd->cmd = CONFIG_CMD_ENABLE;
    }
}

int HubConnection::addDirectChannel(const struct sensors_direct_mem_t *mem) {
    std::unique_ptr<DirectChannelBase> ch;
    int ret = NO_MEMORY;

    Mutex::Autolock autoLock(mDirectChannelLock);
    for (const auto& c : mDirectChannel) {
        if (c.second->memoryMatches(mem)) {
            // cannot reusing same memory
            return BAD_VALUE;
        }
    }
    switch(mem->type) {
        case SENSOR_DIRECT_MEM_TYPE_ASHMEM:
            ch = std::make_unique<AshmemDirectChannel>(mem);
            break;
        case SENSOR_DIRECT_MEM_TYPE_GRALLOC:
            ch = std::make_unique<GrallocDirectChannel>(mem);
            break;
        default:
            ret = INVALID_OPERATION;
    }

    if (ch) {
        if (ch->isValid()) {
            ret = mDirectChannelHandle++;
            mDirectChannel.insert(std::make_pair(ret, std::move(ch)));
        } else {
            ret = ch->getError();
            ALOGW("Direct channel object(type:%d) has error %d upon init", mem->type, ret);
        }
    }

    return ret;
}

int HubConnection::removeDirectChannel(int channel_handle) {
    // make sure no active sensor in this channel
    std::vector<int32_t> activeSensorList;
    stopAllDirectReportOnChannel(channel_handle, &activeSensorList);

    // sensor service is responsible for stop all sensors before remove direct
    // channel. Thus, this is an error.
    if (!activeSensorList.empty()) {
        std::stringstream ss;
        std::copy(activeSensorList.begin(), activeSensorList.end(),
                std::ostream_iterator<int32_t>(ss, ","));
        ALOGW("Removing channel %d when sensors (%s) are not stopped.",
                channel_handle, ss.str().c_str());
    }

    // remove the channel record
    Mutex::Autolock autoLock(mDirectChannelLock);
    mDirectChannel.erase(channel_handle);
    return NO_ERROR;
}

int HubConnection::stopAllDirectReportOnChannel(
        int channel_handle, std::vector<int32_t> *activeSensorList) {
    Mutex::Autolock autoLock(mDirectChannelLock);
    if (mDirectChannel.find(channel_handle) == mDirectChannel.end()) {
        return BAD_VALUE;
    }

    std::vector<int32_t> sensorToStop;
    for (auto &it : mSensorToChannel) {
        auto j = it.second.find(channel_handle);
        if (j != it.second.end()) {
            it.second.erase(j);
            if (it.second.empty()) {
                sensorToStop.push_back(it.first);
            }
        }
    }

    if (activeSensorList != nullptr) {
        *activeSensorList = sensorToStop;
    }

    // re-evaluate and send config for all sensor that need to be stopped
    bool ret = true;
    for (auto sensor_handle : sensorToStop) {
        Mutex::Autolock autoLock2(mLock);
        struct ConfigCmd cmd;
        initConfigCmd(&cmd, sensor_handle);

        int result = sendCmd(&cmd, sizeof(cmd));
        ret = ret && (result == sizeof(cmd));
    }
    return ret ? NO_ERROR : BAD_VALUE;
}

int HubConnection::configDirectReport(int sensor_handle, int channel_handle, int rate_level) {
    if (sensor_handle == -1 && rate_level == SENSOR_DIRECT_RATE_STOP) {
        return stopAllDirectReportOnChannel(channel_handle, nullptr);
    }

    if (!isValidHandle(sensor_handle)) {
        return BAD_VALUE;
    }

    // clamp to fast
    if (rate_level > SENSOR_DIRECT_RATE_FAST) {
        rate_level = SENSOR_DIRECT_RATE_FAST;
    }

    // manage direct channel data structure
    Mutex::Autolock autoLock(mDirectChannelLock);
    auto i = mDirectChannel.find(channel_handle);
    if (i == mDirectChannel.end()) {
        return BAD_VALUE;
    }

    auto j = mSensorToChannel.find(sensor_handle);
    if (j == mSensorToChannel.end()) {
        return BAD_VALUE;
    }

    j->second.erase(channel_handle);
    if (rate_level != SENSOR_DIRECT_RATE_STOP) {
        j->second.insert(std::make_pair(channel_handle, (DirectChannelTimingInfo){0, rate_level}));
    }

    Mutex::Autolock autoLock2(mLock);
    struct ConfigCmd cmd;
    initConfigCmd(&cmd, sensor_handle);

    int ret = sendCmd(&cmd, sizeof(cmd));

    if (rate_level == SENSOR_DIRECT_RATE_STOP) {
        ret = NO_ERROR;
    } else {
        ret = (ret == sizeof(cmd)) ? sensor_handle : BAD_VALUE;
    }
    return ret;
}

bool HubConnection::isDirectReportSupported() const {
    return true;
}

void HubConnection::updateSampleRate(int handle, int reason) {
    bool affected = mSensorToChannel.find(handle) != mSensorToChannel.end();
    for (size_t i = 0; i < MAX_ALTERNATES && !affected; ++i) {
        if (mSensorState[handle].alt[i] != COMMS_SENSOR_INVALID) {
            affected |=
                    mSensorToChannel.find(mSensorState[handle].alt[i]) != mSensorToChannel.end();
        }
    }
    if (!affected) {
        return;
    }

    switch (reason) {
        case CONFIG_CMD_ENABLE: {
            constexpr uint64_t PERIOD_800HZ = 1250000;
            uint64_t period_multiplier =
                    (frequency_q10_to_period_ns(mSensorState[handle].rate) + PERIOD_800HZ / 2)
                        / PERIOD_800HZ;
            uint64_t desiredTSample = PERIOD_800HZ;
            while (period_multiplier /= 2) {
                desiredTSample *= 2;
            }
            mSensorState[handle].desiredTSample = desiredTSample;
            ALOGV("DesiredTSample for handle 0x%x set to %" PRIu64, handle, desiredTSample);
            break;
        }
        case CONFIG_CMD_DISABLE:
            mSensorState[handle].desiredTSample = INT64_MAX;
            ALOGV("DesiredTSample 0x%x set to disable", handle);
            break;
        default:
            ALOGW("%s: unexpected reason = %d, no-op", __FUNCTION__, reason);
            break;
    }
}

bool HubConnection::isSampleIntervalSatisfied(int handle, uint64_t timestamp) {
    if (mSensorToChannel.find(handle) == mSensorToChannel.end()) {
        return true;
    }

    if (mSensorState[handle].lastTimestamp >= timestamp
            || mSensorState[handle].desiredTSample == INT64_MAX) {
        return false;
    } else if (intervalLargeEnough(timestamp - mSensorState[handle].lastTimestamp,
                                   mSensorState[handle].desiredTSample)) {
        mSensorState[handle].lastTimestamp = timestamp;
        return true;
    } else {
        return false;
    }
}

uint64_t HubConnection::rateLevelToDeviceSamplingPeriodNs(int handle, int rateLevel) const {
    if (mSensorToChannel.find(handle) == mSensorToChannel.end()) {
        return INT64_MAX;
    }

    switch (rateLevel) {
        case SENSOR_DIRECT_RATE_VERY_FAST:
            // No sensor support VERY_FAST, fall through
        case SENSOR_DIRECT_RATE_FAST:
            if (handle != COMMS_SENSOR_MAG && handle != COMMS_SENSOR_MAG_UNCALIBRATED) {
                return 2500*1000; // 400Hz
            }
            // fall through
        case SENSOR_DIRECT_RATE_NORMAL:
            return 20*1000*1000; // 50 Hz
            // fall through
        default:
            return INT64_MAX;
    }
}
#else // DIRECT_REPORT_ENABLED
// nop functions if feature is turned off
int HubConnection::addDirectChannel(const struct sensors_direct_mem_t *) {
    return INVALID_OPERATION;
}

int HubConnection::removeDirectChannel(int) {
    return INVALID_OPERATION;
}

int HubConnection::configDirectReport(int, int, int) {
    return INVALID_OPERATION;
}

void HubConnection::sendDirectReportEvent(const sensors_event_t *, size_t) {
}

void HubConnection::mergeDirectReportRequest(struct ConfigCmd *, int) {
}

bool HubConnection::isDirectReportSupported() const {
    return false;
}

void HubConnection::updateSampleRate(int, int) {
}

bool HubConnection::isSampleIntervalSatisfied(int, uint64_t) {
    return true;
}
#endif // DIRECT_REPORT_ENABLED

} // namespace android
