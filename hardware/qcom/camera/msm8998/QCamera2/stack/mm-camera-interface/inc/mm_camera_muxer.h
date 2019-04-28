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

#ifndef __MM_CAMERA_MUXER_H__
#define __MM_CAMERA_MUXER_H__

// System dependencies
#include <media/msmb_camera.h>

// Camera dependencies
#include "cam_intf.h"
#include "mm_camera.h"

/*Frame sync node structure*/
typedef struct mm_frame_sync_queue_node {
    /*Number of objects*/
    uint8_t num_objs;
    /*Super buffer for different objects*/
    mm_camera_super_buf_t super_buf[MAX_OBJS_FOR_FRAME_SYNC];
    /*FrameID of these super buffers*/
    uint32_t frame_idx;
    /*Is this matched?*/
    uint8_t matched;
} mm_frame_sync_queue_node_t;


/*Utility Functions for dual camera*/
uint8_t mm_camera_util_get_index_by_num(uint8_t cam_num, uint32_t handler);
uint32_t mm_camera_util_get_handle_by_num(uint8_t num1, uint32_t handle);
uint32_t mm_camera_util_generate_handler_by_num(uint8_t cam_num, uint8_t index);
const char *mm_camera_util_get_dev_name_by_num(uint8_t cam_num, uint32_t cam_handle);

/*Function to handle command from client for Auxillary Cameras*/
int32_t mm_camera_muxer_camera_open(uint8_t camera_idx,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_query_capability(uint32_t camera_handle,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_register_event_notify(uint32_t camera_handle,
        mm_camera_event_notify_t evt_cb,
        void *user_data, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_close_camera(uint32_t camera_handle,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_map_buf(uint32_t camera_handle, uint8_t buf_type,
        int fd, size_t size, void *buffer, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_map_bufs(uint32_t camera_handle,
        const cam_buf_map_type_list *buf_map_list,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_unmap_buf(uint32_t camera_handle,
        uint8_t buf_type, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_set_parms(uint32_t camera_handle,
        parm_buffer_t *parms, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_get_parms(uint32_t camera_handle,
        parm_buffer_t *parms, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_do_auto_focus(uint32_t camera_handle,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_cancel_auto_focus(uint32_t camera_handle,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_prepare_snapshot(uint32_t camera_handle,
        int32_t do_af_flag, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_start_zsl_snapshot(uint32_t camera_handle,
        uint32_t ch_id, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_stop_zsl_snapshot(uint32_t camera_handle,
        uint32_t ch_id, mm_camera_obj_t *cam_obj);
uint32_t mm_camera_muxer_add_channel(uint32_t camera_handle,
        mm_camera_channel_attr_t *attr, mm_camera_buf_notify_t channel_cb,
        void *userdata, uint32_t src_id, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_delete_channel(uint32_t camera_handle, uint32_t ch_id,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_get_bundle_info(uint32_t camera_handle, uint32_t ch_id,
        cam_bundle_config_t *bundle_info, mm_camera_obj_t *cam_obj);
uint32_t mm_camera_muxer_add_stream(uint32_t camera_handle,
        uint32_t ch_id, uint32_t src__ch_id, uint32_t src_stream_id,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_delete_stream(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_link_stream(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id, uint32_t linked_ch_id,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_config_stream(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id, mm_camera_stream_config_t *config,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_map_stream_buf(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id,
        uint8_t buf_type, uint32_t buf_idx, int32_t plane_idx, int fd,
        size_t size, void *buffer, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_map_stream_bufs(uint32_t camera_handle,
        uint32_t ch_id, const cam_buf_map_type_list *buf_map_list,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_unmap_stream_buf(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id,
        uint8_t buf_type, uint32_t buf_idx,
        int32_t plane_idx, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_set_stream_parms(uint32_t camera_handle,
        uint32_t ch_id, uint32_t s_id, cam_stream_parm_buffer_t *parms,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_get_stream_parms(uint32_t camera_handle,
        uint32_t ch_id, uint32_t s_id, cam_stream_parm_buffer_t *parms,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_start_channel(uint32_t camera_handle,
        uint32_t ch_id, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_stop_channel(uint32_t camera_handle,
        uint32_t ch_id, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_qbuf(uint32_t camera_handle, uint32_t ch_id,
        mm_camera_buf_def_t *buf, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_get_queued_buf_count(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_request_super_buf(uint32_t ch_id,
        mm_camera_req_buf_t *buf, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_cancel_super_buf_request(uint32_t camera_handle,
        uint32_t ch_id,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_flush_super_buf_queue(uint32_t camera_handle,
        uint32_t ch_id,
        uint32_t frame_idx, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_configure_notify_mode(uint32_t camera_handle,
        uint32_t ch_id, mm_camera_super_buf_notify_mode_t notify_mode,
        mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_process_advanced_capture(uint32_t camera_handle,
         uint32_t ch_id, mm_camera_advanced_capture_t type,
         int8_t start_flag, void *in_value, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_get_session_id(uint32_t camera_handle,
        uint32_t* sessionid, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_flush(uint32_t camera_handle, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_register_stream_buf_cb(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id, mm_camera_buf_notify_t buf_cb,
        mm_camera_stream_cb_type cb_type, void *userdata, mm_camera_obj_t *cam_obj);
int32_t mm_camera_muxer_reg_frame_sync(mm_camera_obj_t *cam_obj,
        uint32_t ch_id, uint32_t stream_id,
        mm_camera_intf_frame_sync_t *sync_attr);
int32_t mm_camera_muxer_set_dual_cam_cmd(uint32_t camera_handle,
        mm_camera_obj_t *cam_obj);

/*Muxer internal functions*/
void mm_camera_muxer_stream_frame_sync(mm_camera_super_buf_t *super_buf,
        void *user_data);
void mm_camera_muxer_channel_frame_sync(mm_camera_super_buf_t *super_buf,
        void *user_data);
int32_t mm_camera_muxer_do_frame_sync(
        mm_frame_sync_queue_t *queue, mm_camera_super_buf_t *buffer,
        mm_frame_sync_queue_node_t *dispatch_buf);
void mm_camera_muxer_buf_done(mm_camera_super_buf_t *buffer);
int32_t mm_muxer_frame_sync_queue_init(mm_frame_sync_queue_t *queue);
int32_t mm_muxer_frame_sync_queue_deinit(mm_frame_sync_queue_t *queue);
int32_t mm_camera_muxer_get_stream_bufs(mm_stream_t *my_obj);
int32_t mm_camera_muxer_put_stream_bufs(mm_stream_t *my_obj);
int32_t mm_camera_muxer_stream_frame_sync_flush(mm_stream_t *str_obj);
int32_t mm_camera_muxer_channel_frame_sync_flush(mm_channel_t *my_obj);
mm_frame_sync_queue_node_t *mm_camera_muxer_frame_sync_dequeue(
        mm_frame_sync_queue_t *queue, uint8_t matched_only);
int32_t mm_camera_muxer_channel_req_data_cb(mm_camera_req_buf_t *req_buf,
        mm_channel_t *ch_obj);
int32_t mm_camera_map_stream_buf_ops(uint32_t buf_idx,
        int32_t plane_idx, int fd, size_t size,
        void *buffer, cam_mapping_buf_type type,
        void *userdata);
int32_t mm_camera_bundled_map_stream_buf_ops(
        const cam_buf_map_type_list *buf_map_list,
        void *userdata);
int32_t mm_camera_unmap_stream_buf_ops(uint32_t buf_idx,
        int32_t plane_idx, cam_mapping_buf_type type, void *userdata);

#endif /*__MM_CAMERA_MUXER_H */
