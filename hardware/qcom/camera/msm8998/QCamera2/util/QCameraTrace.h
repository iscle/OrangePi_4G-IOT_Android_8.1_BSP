/*
 * Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef __QCAMERATRACE_H__
#define __QCAMERATRACE_H__
#include <stdlib.h>
#include <utils/Trace.h>
#include "camscope_packet_type.h"

#ifdef QCAMERA_REDEFINE_LOG
#define CAM_MODULE CAM_HAL_MODULE
extern "C" {
#include "mm_camera_dbg.h"
}
#endif

#undef ATRACE_CALL
#undef ATRACE_NAME
#undef ATRACE_BEGIN
#undef ATRACE_INT
#undef ATRACE_END
#undef ATRACE_BEGIN_SNPRINTF
#undef KPI_ATRACE_BEGIN
#undef KPI_ATRACE_END
#undef KPI_ATRACE_INT
#undef ATRACE_TAG
#undef ATRACE_BEGIN_DBG
#undef ATRACE_INT_DBG
#undef ATRACE_END_DBG

#define KPI_ONLY 1
#define KPI_DBG 2

#define CAMERA_TRACE_BUF 32

#define ATRACE_TAG ATRACE_TAG_ALWAYS

//to enable only KPI logs
#define KPI_ATRACE_BEGIN(name) ({\
if (gKpiDebugLevel >= KPI_ONLY) { \
     atrace_begin(ATRACE_TAG, name); \
}\
})

#define KPI_ATRACE_END() ({\
if (gKpiDebugLevel >= KPI_ONLY) { \
     atrace_end(ATRACE_TAG); \
}\
})

#define KPI_ATRACE_INT(name,val) ({\
if (gKpiDebugLevel >= KPI_ONLY) { \
     atrace_int(ATRACE_TAG, name, val); \
}\
})


#define ATRACE_BEGIN_SNPRINTF(fmt_str, ...) \
 if (gKpiDebugLevel >= KPI_DBG) { \
   char trace_tag[CAMERA_TRACE_BUF]; \
   snprintf(trace_tag, CAMERA_TRACE_BUF, fmt_str, ##__VA_ARGS__); \
   ATRACE_BEGIN(trace_tag); \
}

#define ATRACE_BEGIN_DBG(name) ({\
if (gKpiDebugLevel >= KPI_DBG) { \
     atrace_begin(ATRACE_TAG, name); \
}\
})

#define ATRACE_END_DBG() ({\
if (gKpiDebugLevel >= KPI_DBG) { \
     atrace_end(ATRACE_TAG); \
}\
})

#define ATRACE_INT_DBG(name,val) ({\
if (gKpiDebugLevel >= KPI_DBG) { \
     atrace_int(ATRACE_TAG, name, val); \
}\
})

#define ATRACE_BEGIN ATRACE_BEGIN_DBG
#define ATRACE_INT ATRACE_INT_DBG
#define ATRACE_END ATRACE_END_DBG

#define CAMSCOPE_MAX_STRING_LENGTH 64

/* Initializes CameraScope tool */
void camscope_init(camscope_section_type camscope_section);

/* Cleans up CameraScope tool */
void camscope_destroy(camscope_section_type camscope_section);

/* Reserves a number of bytes on the memstore flushing to the
 * file system if remaining space is insufficient */
uint32_t camscope_reserve(camscope_section_type camscope_section,
                                 uint32_t num_bytes_to_reserve);

/* Store the data to the memstore and calculate remaining space */
void camscope_store_data(camscope_section_type camscope_section,
                       void* data, uint32_t size);

/* Lock the camscope mutex lock for the given camscope section */
void camscope_mutex_lock(camscope_section_type camscope_section);

/* Unlock the camscope mutex lock for the given camscope section */
void camscope_mutex_unlock(camscope_section_type camscope_section);

#define CAMSCOPE_SYSTRACE_TIME_MARKER() { \
    if (kpi_camscope_frame_count != 0) { \
        if (kpi_camscope_flags & CAMSCOPE_ON_FLAG) { \
            struct timeval t_domain; \
            char trace_time_conv[CAMSCOPE_MAX_STRING_LENGTH]; \
            gettimeofday(&t_domain, NULL); \
            snprintf(trace_time_conv, sizeof(trace_time_conv), \
                     "_CAMSCOPE_TIME_CONV_:%ld:%ld", t_domain.tv_sec, \
                     t_domain.tv_usec); \
            atrace_int(ATRACE_TAG_ALWAYS, trace_time_conv, 0); \
        } \
    } \
}

#define CAMSCOPE_MASK(mask) { \
    char prop[PROPERTY_VALUE_MAX]; \
    property_get("persist.camera.kpi.camscope", prop, "0"); \
    mask = atoi(prop); \
}

#define CAMSCOPE_FRAME_COUNT_MASK(mask) { \
    char prop[PROPERTY_VALUE_MAX]; \
    property_get("persist.camera.kpi.camscope_cnt", prop, "0"); \
    mask = atoi(prop); \
}

#define CAMSCOPE_UPDATE_FLAGS(camscope_section, camscope_prop) { \
    if (kpi_camscope_frame_count != 0) { \
        static uint32_t camscope_frame_counter = 0; \
        if (camscope_frame_counter >= kpi_camscope_frame_count) { \
            uint32_t prev_prop = camscope_prop; \
            CAMSCOPE_MASK(camscope_prop); \
            uint32_t is_prev_prop_on = (prev_prop & CAMSCOPE_ON_FLAG) \
                                        ? 1 : 0; \
            uint32_t is_prop_on = (camscope_prop & CAMSCOPE_ON_FLAG) \
                                   ? 1 : 0; \
            if (is_prev_prop_on ^ is_prop_on) { \
                if (is_prop_on) { \
                    camscope_init(camscope_section); \
                } else { \
                    camscope_destroy(camscope_section); \
                } \
            } \
            CAMSCOPE_SYSTRACE_TIME_MARKER(); \
            camscope_frame_counter = 0; \
        } \
        else { \
            ++camscope_frame_counter; \
        } \
    } \
}

#define CAMSCOPE_INIT(camscope_section) { \
    CAMSCOPE_FRAME_COUNT_MASK(kpi_camscope_frame_count); \
    if (kpi_camscope_frame_count != 0) { \
        CAMSCOPE_MASK(kpi_camscope_flags); \
        if (kpi_camscope_flags & CAMSCOPE_ON_FLAG) { \
            camscope_init(camscope_section); \
            CAMSCOPE_SYSTRACE_TIME_MARKER(); \
        } \
    } \
}

#define CAMSCOPE_DESTROY(camscope_section) { \
    if (kpi_camscope_frame_count != 0) { \
        if (kpi_camscope_flags & CAMSCOPE_ON_FLAG) { \
            camscope_destroy(camscope_section); \
        } \
    } \
}

#define KPI_ATRACE_CAMSCOPE_BEGIN(camscope_name) ({\
if (camscope_name < CAMSCOPE_EVENT_NAME_SIZE && \
    camscope_name >= 0) { \
    KPI_ATRACE_BEGIN(camscope_atrace_names[camscope_name]); \
} \
camscope_sw_base_log((uint32_t)CAMSCOPE_SECTION_HAL, \
                     CAMSCOPE_KPI_MASK, \
                     CAMSCOPE_SYNC_BEGIN, \
                     camscope_name); \
})

#define KPI_ATRACE_CAMSCOPE_END(camscope_name) ({\
KPI_ATRACE_END(); \
camscope_sw_base_log((uint32_t)CAMSCOPE_SECTION_HAL, \
                     CAMSCOPE_KPI_MASK, \
                     CAMSCOPE_SYNC_END, \
                     camscope_name); \
})

// This macro only works with counter values that act like begin/end
#define KPI_ATRACE_CAMSCOPE_INT(name, camscope_name, counter) ({\
KPI_ATRACE_INT(name, counter); \
camscope_timing_log((uint32_t)CAMSCOPE_SECTION_HAL, \
                     CAMSCOPE_KPI_MASK, \
                     counter ? CAMSCOPE_ASYNC_BEGIN : CAMSCOPE_ASYNC_END, \
                     camscope_name, 0); \
})

#define ATRACE_CAMSCOPE_BEGIN(camscope_name) ({\
if (camscope_name < CAMSCOPE_EVENT_NAME_SIZE && \
    camscope_name >= 0) { \
    ATRACE_BEGIN_DBG(camscope_atrace_names[camscope_name]); \
} \
camscope_sw_base_log((uint32_t)CAMSCOPE_SECTION_HAL, \
                     CAMSCOPE_KPI_DBG_MASK, \
                     CAMSCOPE_SYNC_BEGIN, \
                     camscope_name); \
})

#define ATRACE_CAMSCOPE_END(camscope_name) ({\
ATRACE_END_DBG(); \
camscope_sw_base_log(CAMSCOPE_SECTION_HAL, \
                     CAMSCOPE_KPI_DBG_MASK, \
                     CAMSCOPE_SYNC_END, \
                     camscope_name); \
})

#define KPI_ATRACE_CAMSCOPE_NAME(camscope_name) qcamera::CamscopeTraceKpi ___tracer(camscope_name)
#define ATRACE_CAMSCOPE_NAME(camscope_name) qcamera::CamscopeTraceDbg ___tracer(camscope_name)
#define KPI_ATRACE_CAMSCOPE_CALL(camscope_name) KPI_ATRACE_CAMSCOPE_NAME(camscope_name)
#define ATRACE_CAMSCOPE_CALL(camscope_name) ATRACE_CAMSCOPE_NAME(camscope_name)

#define KPI_ATRACE_NAME(name) qcamera::ScopedTraceKpi ___tracer(ATRACE_TAG, name)
#define ATRACE_NAME(name) qcamera::ScopedTraceDbg ___tracer(ATRACE_TAG, name)
#define KPI_ATRACE_CALL() KPI_ATRACE_NAME(__FUNCTION__)
#define ATRACE_CALL() ATRACE_NAME(__FUNCTION__)

namespace qcamera {
extern volatile uint32_t gKpiDebugLevel;
class ScopedTraceKpi {
public:
    inline ScopedTraceKpi(uint64_t tag, const char *name)
    : mTag(tag) {
        if (gKpiDebugLevel >= KPI_ONLY) {
            atrace_begin(mTag,name);
        }
    }

    inline ~ScopedTraceKpi() {
        if (gKpiDebugLevel >= KPI_ONLY) {
            atrace_end(mTag);
        }
    }

    private:
        uint64_t mTag;
};

class ScopedTraceDbg {
public:
    inline ScopedTraceDbg(uint64_t tag, const char *name)
    : mTag(tag) {
        if (gKpiDebugLevel >= KPI_DBG) {
            atrace_begin(mTag,name);
        }
    }

    inline ~ScopedTraceDbg() {
        if (gKpiDebugLevel >= KPI_DBG) {
            atrace_end(mTag);
        }
    }

    private:
        uint64_t mTag;
};

class CamscopeTraceKpi {
public:
    inline CamscopeTraceKpi(const uint32_t camscope_name)
    : mCamscopeName(camscope_name) {
        KPI_ATRACE_CAMSCOPE_BEGIN(mCamscopeName);
    }

    inline ~CamscopeTraceKpi() {
        KPI_ATRACE_CAMSCOPE_END(mCamscopeName);
    }

    private:
        const uint32_t mCamscopeName;
};

class CamscopeTraceDbg {
public:
    inline CamscopeTraceDbg(const uint32_t camscope_name)
    : mCamscopeName(camscope_name) {
        ATRACE_CAMSCOPE_BEGIN(mCamscopeName);
    }

    inline ~CamscopeTraceDbg() {
        ATRACE_CAMSCOPE_END(mCamscopeName);
    }

    private:
        const uint32_t mCamscopeName;
};
};

extern volatile uint32_t gKpiDebugLevel;

#endif /* __QCAMREATRACE_H__ */
