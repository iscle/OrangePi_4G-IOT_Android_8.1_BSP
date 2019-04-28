/* Copyright (c) 2016, The Linux Foundation. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are
* met:
*     * Redistributions of source code must retain the above copyright
*       notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above
*       copyright notice, this list of conditions and the following
*       disclaimer in the documentation and/or other materials provided
*       with the distribution.
*     * Neither the name of The Linux Foundation nor the names of its
*       contributors may be used to endorse or promote products derived
*       from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
* ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
* BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
* BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
* WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
* OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
* IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*
*/

#include "camscope_packet_type.h"
#include "QCameraTrace.h"

#if defined(__linux__) && !defined(__ANDROID__)
#include <unistd.h>
#include <sys/syscall.h>
#endif

const char * camscope_atrace_names[CAMSCOPE_EVENT_NAME_SIZE] = {
    "Mct_Sof",
    "Mct_super_params",
    "Mct_special_event",
    "Mct_process_bus_msg",
    "Camera:AFD",
    "Camera:ASD",
    "Camera:AEC",
    "Camera:AWB",
    "Camera:AF",
    "CPP",
    "CPP_Capture",
    "CPP_clock_request",
    "CPP_Holding_Time",
    "CPP_Hardware_On",
    "Snapshot",
    "ISP_Hardware_Update",
    "JPEG",
    "FaceProc",
    "Sensor_process_event",
    "FD_num_faces_detected",
    "Camera:alloc",
    "iface:streamon_fwd",
    "iface:streamon_to_thread",
    "iface:streamoff_fwd",
    "iface:streamoff_to_thread",
    "iface:config_ISP",
    "iface:hw_config",
    "iface:create_axi_hw",
    "iface:config_axi_hw",
    "iface:streamon",
    "iface:streamoff",
    "AF_START",
    "AF_SET",
    "Camera:IS",
    "ISP:streamon",
    "ISP:streamoff",
    "ISP:set_Strm_config",
    "VFE_HW_UPDATE",
    "ISP:streamon_fwd",
    "SENSOR_SD_OPEN",
    "SENSOR_START_SESSION",
    "SENSOR_SET_RESOLUTION",
    "SENSOR_SET_STREAM_CONFIG",
    "SENSOR_CONFIG_PDAF",
    "SENSOR_LOAD_CHROMATIX",
    "SENSOR_START_STREAM",
    "SENSOR_SET_FPS",
    "SENSOR_STREAMOFF",
    "Camera:WNR",
    "Camera:WNR:memcpy",
    "PPROC_streamoff",
    "CPP:Streamon",
    "Camera:CAC",
    "CPP_create_hw_frame",
    "CPP_set_Strm_config",
    "Mct_start_session",
    "Mct_stop_session",
    "IMG:streamon",
    "MCT:create_buf",
    "start_preview",
    "stop_preview",
    "take_picture",
    "close_camera_device",
    "openCamera",
    "startPreview",
    "stopPreview",
    "capture_channel_cb_routine",
    "preview_stream_cb_routine",
    "SNAPSHOT",
    "getStreamBufs",
    "openCamera",
    "closeCamera",
    "flush",
    "zsl_channel_cb",
    "postproc_channel_cb_routine",
    "synchronous_stream_cb_routine",
    "nodisplay_preview_stream_cb_routine",
    "rdi_mode_stream_cb_routine",
    "postview_stream_cb_routine",
    "video_stream_cb_routine",
    "snapshot_channel_cb_routine",
    "raw_stream_cb_routine",
    "raw_channel_cb_routine",
    "preview_raw_stream_cb_routine",
    "snapshot_raw_stream_cb_routine",
    "metadata_stream_cb_routine",
    "reprocess_stream_cb_routine",
    "callback_stream_cb_routine",
    "set_preview_window",
    "set_CallBacks",
    "enable_msg_type",
    "disable_msg_type",
    "msg_type_enabled",
    "prepare_preview",
    "preview_enabled",
    "restart_start_preview",
    "restart_stop_preview",
    "pre_start_recording",
    "start_recording",
    "stop_recording",
    "recording_enabled",
    "release_recording_frame",
    "cancel_auto_focus",
    "pre_take_picture",
    "cancel_picture",
    "set_parameters",
    "stop_after_set_params",
    "commit_params",
    "restart_after_set_params",
    "get_parameters",
    "put_parameters",
    "send_command",
    "send_command_restart",
    "release",
    "register_face_image",
    "prepare_snapshot",
    "QCamera2HardwareInterface",
    "initCapabilities",
    "getCapabilities",
    "preparePreview",
    "prepareHardwareForSnapshot",
    "initialize",
    "configureStreams",
    "configureStreamsPerfLocked",
    "handleBatchMetadata",
    "handleMetadataWithLock",
    "handleInputBufferWithLock",
    "handleBufferWithLock",
    "processCaptureRequest",
    "flushPerf",
    "getCamInfo",
    "dynamicUpdateMetaStreamInfo",
    "start",
    "stop",
    "flush",
    "streamCbRoutine",
    "registerBuffer",
    "reprocessCbRoutine",
    "initialize",
    "request",
    "initialize",
    "streamCbRoutine",
    "initialize",
    "streamCbRoutine",
    "jpegEvtHandle",
    "request",
    "dataNotifyCB",
    "streamCbRoutine",
    "registerBuffer",
    "start",
    "stop",
    "init",
    "initJpeg",
    "releaseJpegJobData",
    "releasePPJobData",
    "encodeData",
    "preview_stream_cb_routine",
    "stop_preview",
    "capture_channel_cb_routine"
};

/*===========================================================================
 * FUNCTION       : get_thread_id
 *
 * DESCRIPTION    : helper function to get the current thread ID
 *
 * PARAMETERS     : N/A
 *
 * RETURN         : the thread ID
 *==========================================================================*/
 pid_t get_thread_id() {
#if defined(__linux__) && !defined(__ANDROID__)
    return syscall(__NR_gettid);
#else
    return gettid();
#endif
}

/*===========================================================================
 * FUNCTION       : fill_camscope_base
 *
 * DESCRIPTION    : helper function to set the struct's data with the given
 *                  parameters
 *
 * PARAMETERS     :
 *   @scope_struct: struct to fill out
 *   @packet_type : packet_type data value to set
 *   @size        : size data value to set
 *
 * RETURN         : void
 *==========================================================================*/
void fill_camscope_base(camscope_base *scope_struct, uint32_t packet_type,
                        uint32_t size) {
    scope_struct->packet_type = packet_type;
    scope_struct->size = size;
}

/*===========================================================================
 * FUNCTION       : fill_camscope_sw_base
 *
 * DESCRIPTION    : helper function to set the struct's data with the given
 *                  parameters
 *
 * PARAMETERS     :
 *   @scope_struct: struct to fill out
 *   @packet_type : packet_type data value to set
 *   @size        : size data value to set
 *   @timestamp   : timestamp value to store
 *   @thread_id   : identifier of where the packet originates from
 *   @event_name  : name of the event to store
 *
 * RETURN         : void
 *==========================================================================*/
void fill_camscope_sw_base(camscope_sw_base *scope_struct,
                           uint32_t packet_type, uint32_t size,
                           struct timeval timestamp,
                           int32_t thread_id, uint32_t event_name) {
    fill_camscope_base(&(scope_struct->base), packet_type, size);
    scope_struct->timestamp = timestamp;
    scope_struct->thread_id = thread_id;
    scope_struct->event_name = event_name;
}

/*===========================================================================
 * FUNCTION       : fill_camscope_timing
 *
 * DESCRIPTION    : helper function to set the struct's data with the given
 *                  parameters
 *
 * PARAMETERS     :
 *   @scope_struct: struct to fill out
 *   @packet_type : packet_type data value to set
 *   @size        : size data value to set
 *   @timestamp   : timestamp value to store
 *   @thread_id   : identifier of where the packet originates from
 *   @event_name  : name of the event to store
 *   @frame_id    : frame identifier of which frame the packet originates from
 *
 * RETURN         : void
 *==========================================================================*/
void fill_camscope_timing(camscope_timing *scope_struct, uint32_t packet_type,
                          uint32_t size, struct timeval timestamp,
                          int32_t thread_id, uint32_t event_name,
                          uint32_t frame_id) {
    fill_camscope_sw_base(&(scope_struct->sw_base), packet_type, size,
                          timestamp, thread_id, event_name);
    scope_struct->frame_id = frame_id;
}

/*===========================================================================
 * FUNCTION        : fill_camscope_in_out_timing
 *
 * DESCRIPTION     : helper function to set the struct's data with the given
 *                   parameters
 *
 * PARAMETERS      :
 *   @scope_struct : struct to fill out
 *   @packet_type  : packet_type data value to set
 *   @size         : size data value to set
 *   @timestamp    : timestamp value to store
 *   @thread_id    : identifier of where the packet originates from
 *   @event_name   : name of the event to store
 *   @in_timestamp : timestamp of when start of event occurred
 *   @out_timestamp: timestamp of when end of event occurred
 *   @frame_id     : frame identifier of which frame the packet
 *                   originates from
 *
 * RETURN          : void
 *==========================================================================*/
void fill_camscope_in_out_timing(camscope_in_out_timing *scope_struct,
                                 uint32_t packet_type, uint32_t size,
                                 struct timeval timestamp,
                                 int32_t thread_id, uint32_t event_name,
                                 struct timeval in_timestamp,
                                 struct timeval out_timestamp,
                                 uint32_t frame_id) {
    fill_camscope_sw_base(&(scope_struct->sw_base), packet_type, size,
                          timestamp, thread_id, event_name);
    scope_struct->in_timestamp = in_timestamp;
    scope_struct->out_timestamp = out_timestamp;
    scope_struct->frame_id = frame_id;
}

/*===========================================================================
 * FUNCTION               : camscope_base_log
 *
 * DESCRIPTION            : CameraScope Base logging function that stores
 *                          the base amount of data for a camscope packet
 *
 * PARAMETERS             :
 *   @camscope_section    : section of code where this log is being called
 *   @camscope_enable_mask: Enable/Disable mask
 *   @packet_type         : camscope packet_type
 *
 * RETURN                 : void
 *==========================================================================*/
void camscope_base_log(uint32_t camscope_section,
                       uint32_t camscope_enable_mask, uint32_t packet_type) {
    if (kpi_camscope_frame_count != 0) {
        if (kpi_camscope_flags & camscope_enable_mask) {
            struct timeval timestamp;
            gettimeofday(&timestamp, NULL);
            camscope_mutex_lock((camscope_section_type)camscope_section);
            camscope_base scope_struct;
            uint32_t size = sizeof(scope_struct);
            uint32_t total_size =
              camscope_reserve((camscope_section_type)camscope_section, size);
            if (size == total_size) {
                fill_camscope_base(&scope_struct, packet_type, size);
                camscope_store_data((camscope_section_type)camscope_section,
                                    &scope_struct, size);
            }
            camscope_mutex_unlock((camscope_section_type)camscope_section);
        }
    }
}

/*===========================================================================
 * FUNCTION               : camscope_sw_base_log
 *
 * DESCRIPTION            : CameraScope Software Base logging function that
 *                          stores the minimum amount of data for tracing
 *
 * PARAMETERS             :
 *   @camscope_section    : section of code where this log is being called
 *   @camscope_enable_mask: enable/disable mask
 *   @packet_type         : camscope packet_type
 *   @event_name          : name of the event that the packet is storing
 *
 * RETURN                 : void
 *==========================================================================*/
void camscope_sw_base_log(uint32_t camscope_section,
                          uint32_t camscope_enable_mask,
                          uint32_t packet_type, uint32_t event_name) {
    if (kpi_camscope_frame_count != 0) {
        if (kpi_camscope_flags & camscope_enable_mask) {
            struct timeval timestamp;
            gettimeofday(&timestamp, NULL);
            camscope_mutex_lock((camscope_section_type)camscope_section);
            camscope_sw_base scope_struct;
            uint32_t size = sizeof(scope_struct);
            int32_t thread_id = (int32_t)get_thread_id();
            uint32_t total_size =
              camscope_reserve((camscope_section_type)camscope_section, size);
            if (size == total_size) {
                fill_camscope_sw_base(&scope_struct, packet_type, size,
                                      timestamp, thread_id, event_name);
                camscope_store_data((camscope_section_type)camscope_section,
                                    &scope_struct, size);
            }
            camscope_mutex_unlock((camscope_section_type)camscope_section);
        }
    }
}

/*===========================================================================
 * FUNCTION               : camscope_timing_log
 *
 * DESCRIPTION            : CameraScope Timing logging function that
 *                          stores data used for the timing of events
 *                          with respect to their frame id
 *
 * PARAMETERS             :
 *   @camscope_section    : section of code where this log is being called
 *   @camscope_enable_mask: enable/Disable mask
 *   @packet_type         : camscope packet_type
 *   @event_name          : name of the event that the packet is storing
 *   @frame_id            : frame id that the packet is logging
 *
 * RETURN                 : void
 *==========================================================================*/
void camscope_timing_log(uint32_t camscope_section,
                         uint32_t camscope_enable_mask, uint32_t packet_type,
                         uint32_t event_name, uint32_t frame_id) {
    if (kpi_camscope_frame_count != 0) {
        if (kpi_camscope_flags & camscope_enable_mask) {
            struct timeval timestamp;
            gettimeofday(&timestamp, NULL);
            camscope_mutex_lock((camscope_section_type)camscope_section);
            camscope_timing scope_struct;
            uint32_t size = sizeof(scope_struct);
            int32_t thread_id = (int32_t)get_thread_id();
            uint32_t total_size =
              camscope_reserve((camscope_section_type)camscope_section, size);
            if (size == total_size) {
                fill_camscope_timing(&scope_struct, packet_type, size,
                                     timestamp, thread_id, event_name,
                                     frame_id);
                camscope_store_data((camscope_section_type)camscope_section,
                                    &scope_struct, size);
            }
            camscope_mutex_unlock((camscope_section_type)camscope_section);
        }
    }
}

/*===========================================================================
 * FUNCTION               : camscope_in_out_timing_log
 *
 * DESCRIPTION            : CameraScope In-Out Timing logging function that
 *                          stores given timestamps with the packet data
 *
 * PARAMETERS             :
 *   @camscope_section    : section of code where this log is being called
 *   @camscope_enable_mask: enable/Disable mask
 *   @packet_type         : camscope packet_type
 *   @event_name          : name of the event that the packet is storing
 *   @frame_id            : frame id that the packet is logging
 *
 * RETURN                 : void
 *==========================================================================*/
void camscope_in_out_timing_log(uint32_t camscope_section,
                                uint32_t camscope_enable_mask,
                                uint32_t packet_type, uint32_t event_name,
                                struct timeval in_timestamp,
                                struct timeval out_timestamp,
                                uint32_t frame_id) {
    if (kpi_camscope_frame_count != 0) {
        if (kpi_camscope_flags & camscope_enable_mask) {
            struct timeval timestamp;
            gettimeofday(&timestamp, NULL);
            camscope_mutex_lock((camscope_section_type)camscope_section);
            camscope_in_out_timing scope_struct;
            uint32_t size = sizeof(scope_struct);
            int32_t thread_id = (int32_t)get_thread_id();
            uint32_t total_size =
              camscope_reserve((camscope_section_type)camscope_section, size);
            if (size == total_size) {
                fill_camscope_in_out_timing(&scope_struct, packet_type, size,
                                            timestamp, thread_id, event_name,
                                            in_timestamp, out_timestamp,
                                            frame_id);
                camscope_store_data((camscope_section_type)camscope_section,
                                    &scope_struct, size);
            }
            camscope_mutex_unlock((camscope_section_type)camscope_section);
        }
    }
}
