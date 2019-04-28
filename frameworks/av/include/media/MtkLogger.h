/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/

#ifndef MTK_AUDIO_LOGGER_H
#define MTK_AUDIO_LOGGER_H

#include <utils/Log.h>
#include <android/log.h>
#include <cutils/properties.h>
#include <stdlib.h> /* atoi */

/***
 * Include this file can use MTK log function.
 *
 * Put the InitializeMTKLogLevel() to the constructor(or any initialize function) of your file.
 * You can use "setprop property log_level" to define the log level.
 * ex:
 *    "setprop af.tracks.log 3"  // this will enable all MTK_ALOGV in tracks.cpp even in user load
 *
 * After kill AudioServer(or call InitializeMTKLogLevel() again) the log level will work.
 *
 * _log_level: 0 - Disable
 *             1 - user load only
 *             2 - user + userdebug
 *             3 - eng (always print)
 *
 * If your log_level >= _log_level the log will print.
 *
 *
 * You can use specified number for special purpose.
 * ex:
 *   MTK_ALOGS(4, "Only enable this log if property >=4");
 *
 ***/

#define MT_AUDIO_ENG_BUILD_LEVEL 3
#define MT_AUDIO_USERDEBUG_BUILD_LEVEL 2
#define MT_AUDIO_DEFAULT_BUILD_LEVEL 1

#if defined(MTK_AUDIO)

#if defined(CONFIG_MT_ENG_BUILD) //eng load
#define _BUILD_LOG_LEVEL MT_AUDIO_ENG_BUILD_LEVEL
static int _log_level = MT_AUDIO_ENG_BUILD_LEVEL;
#elif defined(CONFIG_MT_USERDEBUG_BUILD) // userdebug load
#define _BUILD_LOG_LEVEL MT_AUDIO_USERDEBUG_BUILD_LEVEL
static int _log_level = MT_AUDIO_USERDEBUG_BUILD_LEVEL;
#else // user load
#define _BUILD_LOG_LEVEL MT_AUDIO_DEFAULT_BUILD_LEVEL
static int _log_level = MT_AUDIO_DEFAULT_BUILD_LEVEL;
#endif // CONFIG_MT_ENG_BUILD

#define MTK_ALOGV(...) ALOGD_IF(_log_level >= MT_AUDIO_ENG_BUILD_LEVEL, __VA_ARGS__) // eng
#define MTK_ALOGD(...) ALOGD_IF(_log_level >= MT_AUDIO_USERDEBUG_BUILD_LEVEL, __VA_ARGS__) // userdebug/eng
#define MTK_ALOGI(...) ALOGD_IF(_log_level >= MT_AUDIO_DEFAULT_BUILD_LEVEL, __VA_ARGS__) // user/userdebug/eng
#define MTK_ALOGW(...) ALOGW(__VA_ARGS__) // warning will always print
#define MTK_ALOGE(...) ALOGE(__VA_ARGS__) // error will always print
#define MTK_ALOGS(level, ...) ALOGD_IF(_log_level >= level, __VA_ARGS__) // specified level

#define MTK_ALOGV_IF(...) if (_log_level >= MT_AUDIO_ENG_BUILD_LEVEL) ALOGD_IF(__VA_ARGS__) // eng
#define MTK_ALOGD_IF(...) if (_log_level >= MT_AUDIO_USERDEBUG_BUILD_LEVEL) ALOGD_IF(__VA_ARGS__) // userdebug/eng
#define MTK_ALOGI_IF(...) if (_log_level >= MT_AUDIO_DEFAULT_BUILD_LEVEL) ALOGD_IF(__VA_ARGS__) // user/userdebug/eng
#define MTK_ALOGW_IF(...) ALOGW_IF(__VA_ARGS__) // warning will always print
#define MTK_ALOGE_IF(...) ALOGE_IF(__VA_ARGS__) // error will always print
#define MTK_ALOGS_IF(level, ...) if (_log_level >= level) ALOGD_IF(__VA_ARGS__) // specified level

// Log with function name and line number
#define MTK_MALOGV(fmt, arg...) ALOGD_IF(_log_level >= MT_AUDIO_ENG_BUILD_LEVEL, "[%s]line:%d " fmt, __FUNCTION__, __LINE__, ##arg) // eng
#define MTK_MALOGD(fmt, arg...) ALOGD_IF(_log_level >= MT_AUDIO_USERDEBUG_BUILD_LEVEL, "[%s]line:%d " fmt, __FUNCTION__, __LINE__, ##arg) // userdebug/eng
#define MTK_MALOGI(fmt, arg...) ALOGD_IF(_log_level >= MT_AUDIO_DEFAULT_BUILD_LEVEL, "[%s]line:%d " fmt, __FUNCTION__, __LINE__, ##arg) // user/userdebug/eng
#define MTK_MALOGW(fmt, arg...) ALOGW("[%s]line:%d " fmt, __FUNCTION__, __LINE__, ##arg) // warning will always print
#define MTK_MALOGE(fmt, arg...) ALOGE("[%s]line:%d " fmt, __FUNCTION__, __LINE__, ##arg) // warning will always print
#define MTK_MALOGS(level, fmt, arg...) ALOGD_IF(_log_level >= level, "[%s]line:%d " fmt, __FUNCTION__, __LINE__, ##arg) // specified level

#define MTK_MALOGV_IF(cond, fmt, arg...) if (_log_level >= MT_AUDIO_ENG_BUILD_LEVEL) ALOGD_IF(cond, "[%s]line:%d " fmt, __FUNCTION__, __LINE__, ##arg) // eng
#define MTK_MALOGD_IF(cond, fmt, arg...) if (_log_level >= MT_AUDIO_USERDEBUG_BUILD_LEVEL) ALOGD_IF(cond, "[%s]line:%d " fmt, __FUNCTION__, __LINE__, ##arg) // userdebug/eng
#define MTK_MALOGI_IF(cond, fmt, arg...) if (_log_level >= MT_AUDIO_DEFAULT_BUILD_LEVEL) ALOGD_IF(cond, "[%s]line:%d " fmt, __FUNCTION__, __LINE__, ##arg) // user/userdebug/eng
#define MTK_MALOGW_IF(cond, fmt, arg...) ALOGW_IF(cond, "[%s]line:%d " fmt, __FUNCTION__, __LINE__, ##arg) // warning will always print
#define MTK_MALOGE_IF(cond, fmt, arg...) ALOGE_IF(cond, "[%s]line:%d " fmt, __FUNCTION__, __LINE__, ##arg) // warning will always print
#define MTK_MALOGS_IF(level, cond, fmt, arg...) if (_log_level >= level) ALOGD_IF(cond, "[%s]line:%d " fmt, __FUNCTION__, __LINE__, ##arg) // specified level

static void InitializeMTKLogLevel(const char * property) {
    char value[PROPERTY_VALUE_MAX];
    property_get(property, value, "-1");
    _log_level = atoi(value);

    // If log level is not specified, use build config
    if (_log_level == -1) {
        _log_level = _BUILD_LOG_LEVEL;
    }
    ALOGD_IF(_log_level >= MT_AUDIO_USERDEBUG_BUILD_LEVEL,"%s: default level[%d]", __FUNCTION__, _log_level); // user/debug/eng
};
#else
#define MTK_ALOGV(...) do { } while(0)
#define MTK_ALOGD(...) do { } while(0)
#define MTK_ALOGI(...) do { } while(0)
#define MTK_ALOGW(...) do { } while(0)
#define MTK_ALOGE(...) do { } while(0)
#define MTK_ALOGS(...) do { } while(0)

#define MTK_ALOGV_IF(...) do { } while(0)
#define MTK_ALOGD_IF(...) do { } while(0)
#define MTK_ALOGI_IF(...) do { } while(0)
#define MTK_ALOGW_IF(...) do { } while(0)
#define MTK_ALOGE_IF(...) do { } while(0)
#define MTK_ALOGS_IF(...) do { } while(0)

#define MTK_MALOGV_IF(...) do { } while(0)
#define MTK_MALOGD_IF(...) do { } while(0)
#define MTK_MALOGI_IF(...) do { } while(0)
#define MTK_MALOGW_IF(...) do { } while(0)
#define MTK_MALOGE_IF(...) do { } while(0)
#define MTK_MALOGS_IF(...) do { } while(0)

#define MTK_MALOGV_IF(...) do { } while(0)
#define MTK_MALOGD_IF(...) do { } while(0)
#define MTK_MALOGI_IF(...) do { } while(0)
#define MTK_MALOGW_IF(...) do { } while(0)
#define MTK_MALOGE_IF(...) do { } while(0)
#define MTK_MALOGS_IF(...) do { } while(0)

static void InitializeMTKLogLevel(const char * property __unused) {};
#endif // MTK_AUDIO

#endif // MTK_AUDIO_LOGGER_H
