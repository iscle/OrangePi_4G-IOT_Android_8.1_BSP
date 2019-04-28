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

// To remove
#include <cutils/properties.h>

// System dependencies
#include <pthread.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <dlfcn.h>
#define IOCTL_H <SYSTEM_HEADER_PREFIX/ioctl.h>
#include IOCTL_H

// Camera dependencies
#include "cam_semaphore.h"
#include "mm_camera_dbg.h"
#include "mm_camera_sock.h"
#include "mm_camera_interface.h"
#include "mm_camera_muxer.h"

#define MAX_UNMATCHED_FOR_FRAME_SYNC 0

extern mm_camera_obj_t* mm_camera_util_get_camera_by_handler(uint32_t cam_handler);
extern mm_channel_t * mm_camera_util_get_channel_by_handler(mm_camera_obj_t *cam_obj,
        uint32_t handler);
extern mm_stream_t *mm_channel_util_get_stream_by_handler(mm_channel_t *ch_obj,
        uint32_t handler);
extern int32_t mm_camera_util_set_camera_object(uint8_t cam_idx, mm_camera_obj_t *obj);


/*===========================================================================
 * FUNCTION   : mm_camera_util_get_index_by_num
 *
 * DESCRIPTION: utility function to get index from handle
 *
 * PARAMETERS :
 *   @cam_num : Camera number
 *   @handler: object handle
 *
 * RETURN     : uint8_t type of index derived from handle
 *==========================================================================*/
uint8_t mm_camera_util_get_index_by_num(uint8_t cam_num, uint32_t handler)
{
    uint8_t idx = 0;
    idx = ((mm_camera_util_get_handle_by_num(cam_num, handler) >>
            (MM_CAMERA_HANDLE_SHIFT_MASK * cam_num))
            & 0x000000ff);
    return idx;
}

/*===========================================================================
 * FUNCTION   : mm_camera_util_get_handle_by_num
 *
 * DESCRIPTION: utility function to get handle for specific camera
 *
 * PARAMETERS :
 *   @cam_num : Camera number
 *   @handler : object handle
 *
 * RETURN     : return proper handle based on the object num
 *==========================================================================*/
uint32_t mm_camera_util_get_handle_by_num(uint8_t cam_num, uint32_t handler)
{
    return (handler & (MM_CAMERA_HANDLE_BIT_MASK <<
            (MM_CAMERA_HANDLE_SHIFT_MASK * cam_num)));
}

/*===========================================================================
 * FUNCTION   : mm_camera_util_generate_handler_by_num
 *
 * DESCRIPTION: utility function to generate handler for camera/channel/stream
 *
 * PARAMETERS :
 *   @cam_num : Camera number
 *   @index   : index of the object to have handler
 *
 * RETURN     : uint32_t type of handle that uniquely identify the object
 *==========================================================================*/
uint32_t mm_camera_util_generate_handler_by_num(uint8_t cam_num, uint8_t index)
{
    uint32_t handler = mm_camera_util_generate_handler(index);
    handler = (handler << (MM_CAMERA_HANDLE_SHIFT_MASK * cam_num));
    return handler;
}

/*===========================================================================
 * FUNCTION   : mm_camera_util_get_dev_name_by_num
 *
 * DESCRIPTION: utility function to get device name from camera handle
 *
 * PARAMETERS :
 *   @cam_handle: camera handle
 *
 * RETURN     : char ptr to the device name stored in global variable
 * NOTE       : caller should not free the char ptr
 *==========================================================================*/
const char *mm_camera_util_get_dev_name_by_num(uint8_t cam_num, uint32_t cam_handle)
{
    uint32_t handle = (cam_handle >> (cam_num * MM_CAMERA_HANDLE_SHIFT_MASK));
    return mm_camera_util_get_dev_name(handle);
}

/*===========================================================================
 * FUNCTION   : mm_muxer_util_get_camera_by_obj
 *
 * DESCRIPTION: utility function to get camera object from object list
 *
 * PARAMETERS :
 *   @cam_handle: camera handle
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : ptr to the camera object stored in global variable
 * NOTE       : caller should not free the camera object ptr
 *==========================================================================*/
mm_camera_obj_t* mm_muxer_util_get_camera_by_obj(uint32_t cam_handle,
        mm_camera_obj_t *cam_obj)
{
    mm_camera_obj_t *obj = cam_obj;
    uint8_t i = 0;

    if (cam_handle == cam_obj->my_hdl) {
        return cam_obj;
    }

    if (obj->master_cam_obj != NULL) {
        obj = obj->master_cam_obj;
    }
    for (i = 0; i < obj->num_s_cnt; i++) {
        if (cam_handle == obj->aux_cam_obj[i]->my_hdl) {
            obj = obj->aux_cam_obj[i];
            break;
        }
    }
    return obj;
}

/*===========================================================================
 * FUNCTION   : mm_muxer_util_get_channel_by_obj
 *
 * DESCRIPTION: utility function to get channel object from camera
 *
 * PARAMETERS :
 *   @ch_id: channel handle
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : ptr to the camera object stored in global variable
 * NOTE       : caller should not free the camera object ptr
 *==========================================================================*/
mm_channel_t *mm_muxer_util_get_channel_by_obj(uint32_t ch_id,
        mm_camera_obj_t *cam_obj)
{
    mm_camera_obj_t *obj = cam_obj;
    mm_channel_t *ch_obj = NULL;
    uint8_t i = 0;

    if (obj->master_cam_obj != NULL) {
        obj = obj->master_cam_obj;
    }
    while (obj != NULL) {
        ch_obj = mm_camera_util_get_channel_by_handler(obj, ch_id);
        if (ch_obj != NULL) {
            break;
        }
        obj = obj->aux_cam_obj[i++];
    }
    return ch_obj;
}

/*===========================================================================
 * FUNCTION   : mm_muxer_util_get_stream_by_obj
 *
 * DESCRIPTION: utility function to get stream object from camera
 *
 * PARAMETERS :
 *   @stream_id: stream handle
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : ptr to the camera object stored in global variable
 * NOTE       : caller should not free the camera object ptr
 *==========================================================================*/
mm_stream_t *mm_muxer_util_get_stream_by_obj(uint32_t stream_id,
        mm_camera_obj_t *cam_obj)
{
    mm_camera_obj_t *obj = cam_obj;
    mm_stream_t *stream_obj = NULL;
    uint8_t i = 0, j = 0;

    if (obj->master_cam_obj != NULL) {
        obj = obj->master_cam_obj;
    }

    while ((obj != NULL) && (stream_obj == NULL)) {
        for(i = 0; i < MM_CAMERA_CHANNEL_MAX; i++) {
            stream_obj = mm_channel_util_get_stream_by_handler(
                    &cam_obj->ch[i], stream_id);
            if (stream_obj == NULL) {
                break;
            }
        }
        obj = obj->aux_cam_obj[j++];
    }
    return stream_obj;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_camera_open
 *
 * DESCRIPTION: open a supporting camera by camera index
 *
 * PARAMETERS :
 *   @cam_idx  : camera index. should within range of 0 to num_of_cameras
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              non-zero error code -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_camera_open(uint8_t cam_idx,
        mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t *my_obj = NULL;
    uint8_t my_num = 1;

    my_obj = (mm_camera_obj_t *)malloc(sizeof(mm_camera_obj_t));
    if(NULL == my_obj) {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        LOGE("no mem");
        return -EINVAL;
    }

    /* initialize camera obj */
    memset(my_obj, 0, sizeof(mm_camera_obj_t));
    my_obj->ctrl_fd = -1;
    my_obj->ds_fd = -1;
    my_obj->ref_count++;
    my_obj->my_num = my_num;
    my_obj->my_hdl = mm_camera_util_generate_handler_by_num(my_num, cam_idx);
    pthread_mutex_init(&my_obj->cam_lock, NULL);
    /* unlock global interface lock, if not, in dual camera use case,
      * current open will block operation of another opened camera obj*/
    pthread_mutex_lock(&my_obj->cam_lock);
    pthread_mutex_unlock(&cam_obj->muxer_lock);

    rc = mm_camera_open(my_obj);
    pthread_mutex_lock(&cam_obj->muxer_lock);
    if (rc != 0) {
        LOGE("mm_camera_open err = %d", rc);
        pthread_mutex_destroy(&my_obj->cam_lock);
        free(my_obj);
        my_obj = NULL;
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        return rc;
    } else {
        LOGD("Open succeded\n");
        rc  = mm_camera_util_set_camera_object(cam_idx, my_obj);
        my_obj->vtbl.camera_handle = (cam_obj->my_hdl | my_obj->my_hdl);
        cam_obj->vtbl.camera_handle = my_obj->vtbl.camera_handle;
        cam_obj->aux_cam_obj[cam_obj->num_s_cnt++] = my_obj;
        my_obj->master_cam_obj = cam_obj;
        cam_obj->master_cam_obj = NULL;
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        return rc;
    }
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_query_capability
 *
 * DESCRIPTION: query camera capability
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_query_capability(uint32_t camera_handle,
        mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t *my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_query_capability(my_obj);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    LOGD(" rc = %d", rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_register_event_notify
 *
 * DESCRIPTION: register for event notify
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @evt_cb       : callback for event notify
 *   @user_data    : user data ptr
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_register_event_notify(uint32_t camera_handle,
        mm_camera_event_notify_t evt_cb,
        void *user_data, mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t *my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_register_event_notify(my_obj, evt_cb, user_data);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_close_camera
 *
 * DESCRIPTION: close a camera by its handle
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_close_camera(uint32_t camera_handle,
        mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t *my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if (my_obj){
        uint8_t cam_idx = mm_camera_util_get_index_by_num(
                my_obj->my_num, my_obj->my_hdl);
        my_obj->ref_count--;
        if(my_obj->ref_count > 0) {
            LOGD("ref_count=%d\n", my_obj->ref_count);
            pthread_mutex_unlock(&cam_obj->muxer_lock);
            rc = 0;
        } else {
            rc  = mm_camera_util_set_camera_object(cam_idx, NULL);
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&cam_obj->muxer_lock);
            rc = mm_camera_close(my_obj);
            pthread_mutex_destroy(&my_obj->cam_lock);
            free(my_obj);
            my_obj = NULL;
        }
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_map_buf
 *
 * DESCRIPTION: mapping camera buffer via domain socket to server
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @buf_type     : type of buffer to be mapped. could be following values:
 *                   CAM_MAPPING_BUF_TYPE_CAPABILITY
 *                   CAM_MAPPING_BUF_TYPE_SETPARM_BUF
 *                   CAM_MAPPING_BUF_TYPE_GETPARM_BUF
 *   @fd           : file descriptor of the buffer
 *   @size         : size of the buffer
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_map_buf(uint32_t camera_handle, uint8_t buf_type,
        int fd, size_t size, void *buffer, mm_camera_obj_t *cam_obj)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_map_buf(my_obj, buf_type, fd, size, buffer);
    }else{
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_map_bufs
 *
 * DESCRIPTION: mapping camera buffer via domain socket to server
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @buf_type     : type of buffer to be mapped. could be following values:
 *                   CAM_MAPPING_BUF_TYPE_CAPABILITY
 *                   CAM_MAPPING_BUF_TYPE_SETPARM_BUF
 *                   CAM_MAPPING_BUF_TYPE_GETPARM_BUF
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_map_bufs(uint32_t camera_handle,
        const cam_buf_map_type_list *buf_map_list,
        mm_camera_obj_t *cam_obj)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_map_bufs(my_obj, buf_map_list);
    }else{
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_unmap_buf
 *
 * DESCRIPTION: unmapping camera buffer via domain socket to server
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @buf_type     : type of buffer to be unmapped. could be following values:
 *                   CAM_MAPPING_BUF_TYPE_CAPABILITY
 *                   CAM_MAPPING_BUF_TYPE_SETPARM_BUF
 *                   CAM_MAPPING_BUF_TYPE_GETPARM_BUF
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_unmap_buf(uint32_t camera_handle,
        uint8_t buf_type, mm_camera_obj_t *cam_obj)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_unmap_buf(my_obj, buf_type);
    }else{
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_set_parms
 *
 * DESCRIPTION: set parameters per camera
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @parms        : ptr to a param struct to be set to server
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_set_parms(uint32_t camera_handle,
        parm_buffer_t *parms, mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t *my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_set_parms(my_obj, parms);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_get_parms
 *
 * DESCRIPTION: get parameters per camera
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @parms        : ptr to a param struct to be get from server
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_get_parms(uint32_t camera_handle,
        parm_buffer_t *parms, mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t *my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_get_parms(my_obj, parms);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_do_auto_focus
 *
 * DESCRIPTION: performing auto focus
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_do_auto_focus(uint32_t camera_handle,
        mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t *my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_do_auto_focus(my_obj);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_cancel_auto_focus
 *
 * DESCRIPTION: cancel auto focus
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_cancel_auto_focus(uint32_t camera_handle,
        mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t *my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_cancel_auto_focus(my_obj);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_prepare_snapshot
 *
 * DESCRIPTION: prepare hardware for snapshot
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @do_af_flag   : flag indicating if AF is needed
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_prepare_snapshot(uint32_t camera_handle,
        int32_t do_af_flag, mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t *my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_prepare_snapshot(my_obj, do_af_flag);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_start_zsl_snapshot
 *
 * DESCRIPTION: Starts zsl snapshot
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_start_zsl_snapshot(uint32_t camera_handle,
        uint32_t ch_id, mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t *my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        uint32_t my_ch_id = mm_camera_util_get_handle_by_num(my_obj->my_num, ch_id);
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_start_zsl_snapshot_ch(my_obj, my_ch_id);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_stop_zsl_snapshot
 *
 * DESCRIPTION: Starts zsl snapshot
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_stop_zsl_snapshot(uint32_t camera_handle,
        uint32_t ch_id, mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t *my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        uint32_t my_ch_id = mm_camera_util_get_handle_by_num(my_obj->my_num, ch_id);
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_stop_zsl_snapshot_ch(my_obj, my_ch_id);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_add_channel
 *
 * DESCRIPTION: add a channel
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @attr         : bundle attribute of the channel if needed
 *   @channel_cb   : callback function for bundle data notify
 *   @userdata     : user data ptr
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : uint32_t type of channel handle
 *              0  -- invalid channel handle, meaning the op failed
 *              >0 -- successfully added a channel with a valid handle
 *==========================================================================*/
uint32_t mm_camera_muxer_add_channel(uint32_t camera_handle,
        mm_camera_channel_attr_t *attr, mm_camera_buf_notify_t channel_cb,
        void *userdata, uint32_t m_ch_id, mm_camera_obj_t *cam_obj)
{
    int32_t ch_id = 0;
    mm_camera_obj_t *my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        ch_id = mm_camera_add_channel(my_obj, attr, channel_cb, userdata);

        if (ch_id > 0 && m_ch_id > 0) {
            mm_camera_frame_sync_t frame_sync;
            memset(&frame_sync, 0, sizeof(frame_sync));
            frame_sync.a_cam_obj = my_obj;
            frame_sync.a_ch_id = ch_id;
            frame_sync.userdata = userdata;
            frame_sync.a_stream_id = 0;
            frame_sync.is_res_shared = 1;
            if (attr != NULL) {
                frame_sync.attr = *attr;
                frame_sync.is_active = 1;
            }
            pthread_mutex_lock(&cam_obj->cam_lock);
            mm_camera_reg_frame_sync(cam_obj, m_ch_id,
                    0, &frame_sync);
        }
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return ch_id;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_delete_channel
 *
 * DESCRIPTION: delete a channel by its handle
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_delete_channel(uint32_t camera_handle, uint32_t ch_id,
        mm_camera_obj_t *cam_obj)
{
    int32_t rc = -1;
    mm_camera_obj_t *my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_del_channel(my_obj, ch_id);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_get_bundle_info
 *
 * DESCRIPTION: query bundle info of the channel
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @bundle_info  : bundle info to be filled in
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_get_bundle_info(uint32_t camera_handle, uint32_t ch_id,
        cam_bundle_config_t *bundle_info, mm_camera_obj_t *cam_obj)
{
    int32_t rc = -1;
    mm_camera_obj_t *my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_get_bundle_info(my_obj, ch_id, bundle_info);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}
/*===========================================================================
 * FUNCTION   : mm_camera_muxer_add_stream
 *
 * DESCRIPTION: add a stream into a channel
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @src__ch_id        : src channel handle
 *   @src_stream_id     :  src stream handle
 *   @cam_obj     : ptr to a Parent camera object
 *
 * RETURN     : uint32_t type of stream handle
 *              0  -- invalid stream handle, meaning the op failed
 *              >0 -- successfully added a stream with a valid handle
 *==========================================================================*/
uint32_t mm_camera_muxer_add_stream(uint32_t camera_handle,
        uint32_t ch_id, uint32_t src__ch_id, uint32_t src_stream_id, mm_camera_obj_t *cam_obj)
{
    uint32_t stream_id = 0;
    int32_t rc = 0;
    mm_camera_obj_t *my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        stream_id = mm_camera_add_stream(my_obj, ch_id);
        if (stream_id > 0 && src_stream_id > 0) {
            mm_camera_frame_sync_t frame_sync;
            memset(&frame_sync, 0, sizeof(frame_sync));
            frame_sync.a_cam_obj = my_obj;
            frame_sync.a_ch_id = ch_id;
            frame_sync.userdata = NULL;
            frame_sync.a_stream_id = stream_id;
            frame_sync.buf_cb = NULL;
            frame_sync.is_res_shared = 1;
            frame_sync.is_active = 0;
            pthread_mutex_lock(&cam_obj->cam_lock);
            rc = mm_camera_reg_frame_sync(cam_obj, src__ch_id,
                    src_stream_id, &frame_sync);
            LOGH("Stream frame sync = %d and %d rc = %d",
                    src_stream_id, stream_id, rc);
        }
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return stream_id;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_delete_stream
 *
 * DESCRIPTION: delete a stream by its handle
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @stream_id    : stream handle
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_delete_stream(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id,
        mm_camera_obj_t *cam_obj)
{
    mm_camera_obj_t *my_obj = NULL;
    int32_t rc = 0;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_del_stream(my_obj, ch_id, stream_id);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_link_stream
 *
 * DESCRIPTION: link a stream into a new channel
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @stream_id    : stream id
 *   @linked_ch_id : channel in which the stream will be linked
 *
 * RETURN     : int32_t type of stream handle
 *              0  -- invalid stream handle, meaning the op failed
 *              >0 -- successfully linked a stream with a valid handle
 *==========================================================================*/
int32_t mm_camera_muxer_link_stream(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id, uint32_t linked_ch_id,
        mm_camera_obj_t *cam_obj)
{
    uint32_t id = 0;
    mm_camera_obj_t *my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        id = mm_camera_link_stream(my_obj, ch_id, stream_id, linked_ch_id);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return id;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_config_stream
 *
 * DESCRIPTION: configure a stream
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @stream_id    : stream handle
 *   @config       : stream configuration
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_config_stream(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id, mm_camera_stream_config_t *config,
        mm_camera_obj_t *cam_obj)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    mm_camera_stream_config_t aux_config = *config;
    LOGD("mm_camera_intf_config_stream stream_id = %d",stream_id);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        if (config->stream_info->aux_str_info != NULL) {
            aux_config.stream_info = config->stream_info->aux_str_info;
            aux_config.mem_vtbl.get_bufs = NULL;
            aux_config.mem_vtbl.put_bufs = NULL;
            aux_config.mem_vtbl.set_config_ops = NULL;
        }
        rc = mm_camera_config_stream(my_obj, ch_id, stream_id, &aux_config);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_map_stream_buf
 *
 * DESCRIPTION: mapping stream buffer via domain socket to server
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @s_id         : stream handle
 *   @buf_type     : type of buffer to be mapped. could be following values:
 *                   CAM_MAPPING_BUF_TYPE_STREAM_BUF
 *                   CAM_MAPPING_BUF_TYPE_STREAM_INFO
 *                   CAM_MAPPING_BUF_TYPE_OFFLINE_INPUT_BUF
 *   @buf_idx      : index of buffer within the stream buffers, only valid if
 *                   buf_type is CAM_MAPPING_BUF_TYPE_STREAM_BUF or
 *                   CAM_MAPPING_BUF_TYPE_OFFLINE_INPUT_BUF
 *   @plane_idx    : plane index. If all planes share the same fd,
 *                   plane_idx = -1; otherwise, plean_idx is the
 *                   index to plane (0..num_of_planes)
 *   @fd           : file descriptor of the buffer
 *   @size         : size of the buffer
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_map_stream_buf(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id,
        uint8_t buf_type, uint32_t buf_idx, int32_t plane_idx, int fd,
        size_t size, void *buffer, mm_camera_obj_t *cam_obj)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    LOGD("E camera_handle = %d, ch_id = %d, s_id = %d, buf_idx = %d, plane_idx = %d",
          camera_handle, ch_id, stream_id, buf_idx, plane_idx);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_map_stream_buf(my_obj, ch_id, stream_id,
                                  buf_type, buf_idx, plane_idx,
                                  fd, size, buffer);
    }else{
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_map_stream_bufs
 *
 * DESCRIPTION: mapping stream buffers via domain socket to server
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @buf_map_list : list of buffers to be mapped
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_map_stream_bufs(uint32_t camera_handle,
        uint32_t ch_id, const cam_buf_map_type_list *buf_map_list,
        mm_camera_obj_t *cam_obj)
{
    int32_t rc = -1;
    mm_camera_obj_t *my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    LOGD("E camera_handle = %d, ch_id = %d",
          camera_handle, ch_id);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_map_stream_bufs(my_obj, ch_id, buf_map_list);
    }else{
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_unmap_stream_buf
 *
 * DESCRIPTION: unmapping stream buffer via domain socket to server
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @s_id         : stream handle
 *   @buf_type     : type of buffer to be unmapped. could be following values:
 *                   CAM_MAPPING_BUF_TYPE_STREAM_BUF
 *                   CAM_MAPPING_BUF_TYPE_STREAM_INFO
 *                   CAM_MAPPING_BUF_TYPE_OFFLINE_INPUT_BUF
 *   @buf_idx      : index of buffer within the stream buffers, only valid if
 *                   buf_type is CAM_MAPPING_BUF_TYPE_STREAM_BUF or
 *                   CAM_MAPPING_BUF_TYPE_OFFLINE_INPUT_BUF
 *   @plane_idx    : plane index. If all planes share the same fd,
 *                   plane_idx = -1; otherwise, plean_idx is the
 *                   index to plane (0..num_of_planes)
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_unmap_stream_buf(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id,
        uint8_t buf_type, uint32_t buf_idx,
        int32_t plane_idx, mm_camera_obj_t *cam_obj)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    LOGD("E camera_handle = %d, ch_id = %d, s_id = %d, buf_idx = %d, plane_idx = %d",
          camera_handle, ch_id, stream_id, buf_idx, plane_idx);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_unmap_stream_buf(my_obj, ch_id, stream_id,
                buf_type, buf_idx, plane_idx);
    } else{
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_set_stream_parms
 *
 * DESCRIPTION: set parameters per stream
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @s_id         : stream handle
 *   @parms        : ptr to a param struct to be set to server
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_set_stream_parms(uint32_t camera_handle,
        uint32_t ch_id, uint32_t s_id, cam_stream_parm_buffer_t *parms,
        mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_set_stream_parms(my_obj, ch_id, s_id, parms);
    } else{
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_get_stream_parms
 *
 * DESCRIPTION: get parameters per stream
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @s_id         : stream handle
 *   @parms        : ptr to a param struct to be get from server
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_get_stream_parms(uint32_t camera_handle,
        uint32_t ch_id, uint32_t s_id, cam_stream_parm_buffer_t *parms,
        mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_get_stream_parms(my_obj, ch_id, s_id, parms);
    } else{
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_start_channel
 *
 * DESCRIPTION: start a channel, which will start all streams in the channel
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_start_channel(uint32_t camera_handle,
        uint32_t ch_id, mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_start_channel(my_obj, ch_id);
        if (rc == 0) {
            rc = mm_camera_start_sensor_stream_on(my_obj, ch_id);
        }
    } else{
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_stop_channel
 *
 * DESCRIPTION: stop a channel, which will stop all streams in the channel
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_stop_channel(uint32_t camera_handle,
        uint32_t ch_id, mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_stop_channel(my_obj, ch_id, /*stop_immediately*/FALSE);
    } else{
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_qbuf
 *
 * DESCRIPTION: enqueue buffer back to kernel
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @buf          : buf ptr to be enqueued
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_qbuf(uint32_t camera_handle, uint32_t ch_id,
        mm_camera_buf_def_t *buf, mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_qbuf(my_obj, ch_id, buf);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_get_queued_buf_count
 *
 * DESCRIPTION: returns the queued buffer count
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @stream_id : stream id
 *
 * RETURN     : int32_t - queued buffer count
 *
 *==========================================================================*/
int32_t mm_camera_muxer_get_queued_buf_count(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id,
        mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_get_queued_buf_count(my_obj, ch_id, stream_id);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_request_super_buf
 *
 * DESCRIPTION: for burst mode in bundle, reuqest certain amount of matched
 *              frames from superbuf queue
 *
 * PARAMETERS :
 *   @ch_id             : channel handle
 *   @buf                : request buffer info
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_request_super_buf(uint32_t ch_id,
        mm_camera_req_buf_t *buf, mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = cam_obj;
    uint32_t chID = get_main_camera_handle(ch_id);

    if(my_obj && buf) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        buf->type = MM_CAMERA_REQ_FRAME_SYNC_BUF;
        rc = mm_camera_request_super_buf (my_obj, chID, buf);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_cancel_super_buf_request
 *
 * DESCRIPTION: for burst mode in bundle, reuqest certain amount of matched
 *              frames from superbuf queue
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id             : channel handle
 *   @buf                : request buffer info
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_cancel_super_buf_request(uint32_t camera_handle,
        uint32_t ch_id,
        mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t aux_handle = get_aux_camera_handle(camera_handle);
    uint32_t aux_chID = get_main_camera_handle(ch_id);

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_cancel_super_buf_request(my_obj, ch_id);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }

    my_obj = mm_muxer_util_get_camera_by_obj(aux_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_cancel_super_buf_request(my_obj, aux_chID);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_flush_super_buf_queue
 *
 * DESCRIPTION: flush out all frames in the superbuf queue
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @frame_idx    : frame index
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_flush_super_buf_queue(uint32_t camera_handle,
        uint32_t ch_id,
        uint32_t frame_idx, mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&cam_obj->muxer_lock);
            rc = mm_camera_flush_super_buf_queue(my_obj, ch_id, frame_idx);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_configure_notify_mode
 *
 * DESCRIPTION: Configures channel notification mode
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @notify_mode  : notification mode
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_configure_notify_mode(uint32_t camera_handle,
        uint32_t ch_id, mm_camera_super_buf_notify_mode_t notify_mode,
        mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&cam_obj->muxer_lock);
            rc = mm_camera_config_channel_notify(my_obj, ch_id, notify_mode);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_process_advanced_capture
 *
 * DESCRIPTION: Configures channel advanced capture mode
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @type : advanced capture type
 *   @ch_id        : channel handle
 *   @trigger  : 1 for start and 0 for cancel/stop
 *   @value  : input capture configaration
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_process_advanced_capture(uint32_t camera_handle,
         uint32_t ch_id, mm_camera_advanced_capture_t type,
         int8_t start_flag, void *in_value, mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;

    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_channel_advanced_capture(my_obj, ch_id, type,
                (uint32_t)start_flag, in_value);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_get_session_id
 *
 * DESCRIPTION: retrieve the session ID from the kernel for this HWI instance
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @sessionid: session id to be retrieved from server
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_get_session_id(uint32_t camera_handle,
        uint32_t* sessionid, mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        *sessionid = my_obj->sessionid;
        pthread_mutex_unlock(&my_obj->cam_lock);
        rc = 0;
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

 /*===========================================================================
 * FUNCTION   : mm_camera_muxer_flush
 *
 * DESCRIPTION: flush the current camera state and buffers
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_flush(uint32_t camera_handle, mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_flush(my_obj);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_register_stream_buf_cb
 *
 * DESCRIPTION: Register special callback for stream buffer
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @stream_id    : stream handle
 *   @buf_cb       : callback function
 *   @buf_type     :SYNC/ASYNC
 *   @userdata     : userdata pointer
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_register_stream_buf_cb(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id, mm_camera_buf_notify_t buf_cb,
        mm_camera_stream_cb_type cb_type, void *userdata, mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_reg_stream_buf_cb(my_obj, ch_id, stream_id,
                buf_cb, cb_type, userdata);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_reg_frame_sync
 *
 * DESCRIPTION: Configure for frame sync.
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @stream_id    : stream handle
 *   @sync_attr    : Attributes for frame sync
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_reg_frame_sync(mm_camera_obj_t *cam_obj,
        uint32_t ch_id, uint32_t stream_id,
        mm_camera_intf_frame_sync_t *sync_attr)
{
    int32_t rc = 0;
    mm_camera_obj_t *a_cam_obj = NULL;

    mm_camera_frame_sync_t frame_sync;
    if (sync_attr == NULL || cam_obj == NULL) {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        return rc;
    }

    uint32_t chid = get_main_camera_handle(ch_id);
    uint32_t aux_handle = get_aux_camera_handle(sync_attr->camera_handle);
    uint32_t aux_chid = get_aux_camera_handle(sync_attr->ch_id);
    uint32_t strid = 0;
    uint32_t aux_strid = 0;
    if (stream_id) {
        LOGD("Stream frame sync enabled");
        strid = get_main_camera_handle(stream_id);
        aux_strid = get_aux_camera_handle(sync_attr->stream_id);
        if(aux_strid == 0) {
            aux_handle = get_main_camera_handle(sync_attr->camera_handle);
            aux_chid = get_main_camera_handle(sync_attr->ch_id);
            aux_strid = get_main_camera_handle(sync_attr->stream_id);
        }
    } else {
        LOGD("Channel frame sync enabled");
        if(aux_chid == 0) {
            aux_chid = get_main_camera_handle(sync_attr->ch_id);
        }
    }
    a_cam_obj = mm_muxer_util_get_camera_by_obj(aux_handle, cam_obj);

    if(a_cam_obj) {
        memset(&frame_sync, 0, sizeof(frame_sync));
        frame_sync.a_cam_obj = a_cam_obj;
        frame_sync.a_stream_id = aux_strid;
        frame_sync.a_ch_id = aux_chid;
        frame_sync.userdata = sync_attr->userdata;
        frame_sync.buf_cb = sync_attr->buf_cb;
        frame_sync.attr = sync_attr->attr;
        pthread_mutex_lock(&cam_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_reg_frame_sync(cam_obj, chid, strid, &frame_sync);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_set_dual_cam_cmd
 *
 * DESCRIPTION: send event to trigger read on dual camera cmd buffer
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @cam_obj        : header object
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_set_dual_cam_cmd(uint32_t camera_handle,
        mm_camera_obj_t *cam_obj)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;
    my_obj = mm_muxer_util_get_camera_by_obj(camera_handle, cam_obj);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&cam_obj->muxer_lock);
        rc = mm_camera_set_dual_cam_cmd(my_obj);
    } else {
        pthread_mutex_unlock(&cam_obj->muxer_lock);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_stream_frame_sync
 *
 * DESCRIPTION: Handle stream buffers for frame sync
 *
 * PARAMETERS :
 *   @super_buf: Stream buffers
 *   @user_data        : Stream object
 *
 * RETURN     : none
 *==========================================================================*/
void mm_camera_muxer_stream_frame_sync(mm_camera_super_buf_t *super_buf,
        void *user_data)
{
    int32_t rc = 0, i = 0;
    mm_stream_t *my_obj = (mm_stream_t *)user_data;
    mm_frame_sync_queue_node_t dispatch_buf;

    if ((super_buf == NULL) || (super_buf->num_bufs == 0)) {
        return;
    }

    if (my_obj->master_str_obj != NULL) {
        my_obj = my_obj->master_str_obj;
    }

    memset(&dispatch_buf, 0, sizeof(dispatch_buf));
    rc = mm_camera_muxer_do_frame_sync(&my_obj->frame_sync.superbuf_queue,
            super_buf, &dispatch_buf);
    if (rc < 0) {
        LOGE("frame sync failed");
        return;
    }

    if (my_obj->frame_sync.super_buf_notify_cb && dispatch_buf.num_objs > 0) {
        mm_camera_super_buf_t super_buf;
        memset(&super_buf, 0, sizeof(super_buf));
        for (i = 0; i < MAX_OBJS_FOR_FRAME_SYNC; i++) {
            if (dispatch_buf.super_buf[i].num_bufs == 1) {
                super_buf.bufs[super_buf.num_bufs++] =
                        dispatch_buf.super_buf[i].bufs[0];
                super_buf.camera_handle = my_obj->ch_obj->cam_obj->my_hdl;
                super_buf.ch_id = my_obj->ch_obj->my_hdl;
            }
        }
        pthread_mutex_lock(&my_obj->cb_lock);
        my_obj->frame_sync.super_buf_notify_cb(&super_buf,
                my_obj->frame_sync.user_data);
        pthread_mutex_unlock(&my_obj->cb_lock);
    }
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_channel_frame_sync
 *
 * DESCRIPTION: Handle channel super buffers for frame sync
 *
 * PARAMETERS :
 *   @super_buf: channel buffers
 *   @user_data        : channel object
 *
 * RETURN     : none
 *==========================================================================*/
void mm_camera_muxer_channel_frame_sync(mm_camera_super_buf_t *super_buf,
        void *user_data)
{
    int32_t rc = 0;
    mm_channel_t *ch_obj = (mm_channel_t *)user_data;
    mm_channel_t *m_obj = ch_obj;

    if ((super_buf == NULL) || (super_buf->num_bufs == 0)) {
        return;
    }

    if (m_obj->master_ch_obj != NULL) {
        m_obj = m_obj->master_ch_obj;
    }

    rc = mm_camera_muxer_do_frame_sync(&m_obj->frame_sync.superbuf_queue,
            super_buf, NULL);
    mm_camera_muxer_channel_req_data_cb(NULL,
                ch_obj);
}


/*===========================================================================
 * FUNCTION   : mm_camera_muxer_channel_req_data_cb
 *
 * DESCRIPTION: Issue super buffer callback based on request setting
 *
 * PARAMETERS :
 *   @req_buf: buffer request setting
 *   @ch_obj        : channel object
 *
 * RETURN     : none
 *==========================================================================*/
int32_t mm_camera_muxer_channel_req_data_cb(mm_camera_req_buf_t *req_buf,
        mm_channel_t *ch_obj)
{
    int32_t rc = 0, i;
    mm_channel_t *m_obj = (mm_channel_t *)ch_obj;
    mm_frame_sync_queue_node_t* super_obj = NULL;
    mm_frame_sync_t *frame_sync = NULL;
    uint8_t trigger_cb = 0;

    if (m_obj->master_ch_obj != NULL) {
        m_obj = m_obj->master_ch_obj;
    }

    frame_sync = &m_obj->frame_sync;
    if (req_buf != NULL) {
        frame_sync->req_buf.num_buf_requested +=
                req_buf->num_buf_requested;
        frame_sync->req_buf.type = req_buf->type;
    }

    while ((frame_sync->req_buf.num_buf_requested > 0)
            || (frame_sync->superbuf_queue.attr.notify_mode ==
            MM_CAMERA_SUPER_BUF_NOTIFY_CONTINUOUS)) {
        super_obj = mm_camera_muxer_frame_sync_dequeue(
                &frame_sync->superbuf_queue, frame_sync->req_buf.type);
        if (super_obj == NULL) {
            break;
        }
        if (frame_sync->super_buf_notify_cb && super_obj->num_objs != 0) {
            if (frame_sync->req_buf.type == MM_CAMERA_REQ_FRAME_SYNC_BUF) {
                for (i = 0; i < MAX_OBJS_FOR_FRAME_SYNC; i++) {
                    if (super_obj->super_buf[i].num_bufs != 0) {
                        frame_sync->super_buf_notify_cb(
                                &super_obj->super_buf[i],
                                frame_sync->user_data);
                    }
                }
                trigger_cb = 1;
            } else {
                for (i = 0; i < MAX_OBJS_FOR_FRAME_SYNC; i++) {
                    if (super_obj->super_buf[i].num_bufs != 0) {
                        if (super_obj->super_buf[i].ch_id ==
                                ch_obj->my_hdl) {
                            frame_sync->super_buf_notify_cb(
                                    &super_obj->super_buf[i],
                                    frame_sync->user_data);
                            trigger_cb = 1;
                        } else {
                            mm_camera_muxer_buf_done(&super_obj->super_buf[i]);
                        }
                    }
                }
            }
            if ((m_obj->frame_sync.req_buf.num_buf_requested > 0)
                    && trigger_cb) {
                m_obj->frame_sync.req_buf.num_buf_requested--;
            }
        }
        free(super_obj);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_frame_sync_dequeue
 *
 * DESCRIPTION: dequeue object from frame sync queue
 *
 * PARAMETERS :
 *   @queue: ptr to queue to dequeue object
 *
 * RETURN     : ptr to a node from superbuf queue
 *==========================================================================*/
mm_frame_sync_queue_node_t *mm_camera_muxer_frame_sync_dequeue(
        mm_frame_sync_queue_t *queue, uint8_t matched_only)
{
    cam_node_t* node = NULL;
    struct cam_list *head = NULL;
    struct cam_list *pos = NULL;
    mm_frame_sync_queue_node_t* super_buf = NULL;

    pthread_mutex_lock(&queue->que.lock);
    head = &queue->que.head.list;
    pos = head->next;
    if (pos != head) {
        /* get the first node */
        node = member_of(pos, cam_node_t, list);
        super_buf = (mm_frame_sync_queue_node_t*)node->data;
        if ( (NULL != super_buf) &&
             (matched_only == TRUE) &&
             (super_buf->matched == FALSE) ) {
            super_buf = NULL;
        }

        if (NULL != super_buf) {
            queue->que.size--;
            cam_list_del_node(&node->list);
            free(node);
            if (super_buf->matched) {
                queue->match_cnt--;
            }
        }
    }
    pthread_mutex_unlock(&queue->que.lock);
    return super_buf;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_do_frame_sync
 *
 * DESCRIPTION: function to process object buffers and match with existing frames.
 *
 * PARAMETERS :
 *   @queue: ptr to queue to dequeue object
 *   @buffer: Input buffer to match and insert
 *   @dispatch_buf        : Ptr to carry matched node
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_do_frame_sync(
        mm_frame_sync_queue_t *queue, mm_camera_super_buf_t *buffer,
        mm_frame_sync_queue_node_t *dispatch_buf)
{
    cam_node_t* node = NULL;
    uint8_t buf_s_idx, i, found_super_buf, unmatched_bundles;
    struct cam_list *head = NULL;
    struct cam_list *pos = NULL;
    mm_frame_sync_queue_node_t* super_obj = NULL;
    struct cam_list *last_buf = NULL, *insert_before_buf = NULL;

    if (buffer == NULL || buffer->num_bufs == 0) {
        LOGW("Ivalid Argument");
        return -1;
    }

    for (buf_s_idx = 0; buf_s_idx < queue->num_objs; buf_s_idx++) {
        if ((buffer->ch_id == queue->bundled_objs[buf_s_idx]) ||
                (buffer->bufs[0]->stream_id == queue->bundled_objs[buf_s_idx])) {
            break;
        }
    }
    if (buf_s_idx == queue->num_objs) {
        LOGE("buf from stream (%d) not bundled", buffer->bufs[0]->stream_id);
        mm_camera_muxer_buf_done(buffer);
        return -1;
    }

    if (buffer->bufs[0]->frame_idx <= queue->expected_frame_id) {
        LOGD("old frame. Need to release");
        mm_camera_muxer_buf_done(buffer);
        return 0;
    }

    pthread_mutex_lock(&queue->que.lock);
    head = &queue->que.head.list;
    pos = head->next;
    found_super_buf = 0;
    unmatched_bundles = 0;
    last_buf = NULL;
    insert_before_buf = NULL;

    while (pos != head) {
        node = member_of(pos, cam_node_t, list);
        super_obj = (mm_frame_sync_queue_node_t *)node->data;
        if (NULL != super_obj) {
            if (super_obj->matched == 1) {
                /* find a matched super buf, move to next one */
                pos = pos->next;
                continue;
            } else if (buffer->bufs[0]->frame_idx == super_obj->frame_idx) {
                found_super_buf = 1;
                break;
            } else if ((buffer->bufs[0]->frame_idx >= super_obj->frame_idx)
                    && (queue->attr.priority ==
                    MM_CAMERA_SUPER_BUF_PRIORITY_LOW)) {
                found_super_buf = 1;
                break;
            } else {
                unmatched_bundles++;
                if ( NULL == last_buf ) {
                    if ( super_obj->frame_idx < buffer->bufs[0]->frame_idx) {
                        last_buf = pos;
                    }
                }
                if ( NULL == insert_before_buf ) {
                    if ( super_obj->frame_idx > buffer->bufs[0]->frame_idx) {
                        insert_before_buf = pos;
                    }
                }
                pos = pos->next;
            }
        }
    }

    LOGD("found_super_buf = %d id = %d unmatched cnt = %d match cnt = %d expected = %d max = %d",
            found_super_buf,
            buffer->bufs[0]->frame_idx, unmatched_bundles,
            queue->match_cnt, queue->expected_frame_id,
            queue->attr.max_unmatched_frames);
    if (found_super_buf) {
        super_obj->super_buf[buf_s_idx] = *buffer;
        super_obj->num_objs++;
        if (super_obj->num_objs == queue->num_objs) {
            super_obj->matched = 1;
            queue->expected_frame_id = super_obj->frame_idx;
            if (dispatch_buf != NULL) {
                *dispatch_buf = *super_obj;
                queue->que.size--;
                cam_list_del_node(&node->list);
                free(node);
                free(super_obj);
            } else {
                queue->match_cnt++;
            }
        }
        /* Any older unmatched buffer need to be released */
        if ( last_buf ) {
            while (last_buf != pos ) {
                node = member_of(last_buf, cam_node_t, list);
                super_obj = (mm_frame_sync_queue_node_t*)node->data;
                if (NULL != super_obj) {
                    for (i = 0; i < MAX_OBJS_FOR_FRAME_SYNC; i++) {
                        if (super_obj->super_buf[i].num_bufs != 0) {
                            mm_camera_muxer_buf_done(&super_obj->super_buf[i]);
                        }
                    }
                    queue->que.size--;
                    last_buf = last_buf->next;
                    cam_list_del_node(&node->list);
                    free(node);
                    free(super_obj);
                }
            }
        }
    } else {
        if ((queue->attr.max_unmatched_frames < unmatched_bundles)
                && (NULL == last_buf)) {
            //incoming frame is older than the last bundled one
            mm_camera_muxer_buf_done(buffer);
            pthread_mutex_unlock(&queue->que.lock);
            return 0;
        } else if (queue->attr.max_unmatched_frames < unmatched_bundles) {
            //dispatch old buffer. Cannot sync for configured unmatch value
            node = member_of(last_buf, cam_node_t, list);
            super_obj = (mm_frame_sync_queue_node_t*)node->data;
            if (super_obj != NULL) {
                queue->expected_frame_id = super_obj->frame_idx;
                if (dispatch_buf != NULL) {
                    //Dispatch unmatched buffer
                    *dispatch_buf = *super_obj;
                } else {
                    //release unmatched buffers
                    for (i = 0; i < MAX_OBJS_FOR_FRAME_SYNC; i++) {
                        if (super_obj->super_buf[i].num_bufs != 0) {
                            mm_camera_muxer_buf_done(&super_obj->super_buf[i]);
                        }
                    }
                }
            }
            queue->que.size--;
            cam_list_del_node(&node->list);
            free(node);
            free(super_obj);
            super_obj = NULL;
        }

        //insert the new frame at the appropriate position.
        mm_frame_sync_queue_node_t *new_buf = NULL;
        cam_node_t* new_node = NULL;
        new_buf = (mm_frame_sync_queue_node_t *)
                malloc(sizeof(mm_frame_sync_queue_node_t));
        if (NULL != new_buf) {
            memset(new_buf, 0, sizeof(mm_frame_sync_queue_node_t));
            new_buf->super_buf[buf_s_idx] = *buffer;
            new_buf->num_objs++;
            new_buf->frame_idx = buffer->bufs[0]->frame_idx;
            new_buf->matched = 0;
            if (new_buf->num_objs == queue->num_objs && super_obj) {
                new_buf->matched = 1;
                queue->expected_frame_id = super_obj->frame_idx;
                if (dispatch_buf != NULL) {
                    *dispatch_buf = *new_buf;
                    queue->que.size--;
                    free(new_buf);
                    free(new_node);
                } else {
                    queue->match_cnt++;
                }
            } else {
                /* enqueue */
                new_node = (cam_node_t *)malloc(sizeof(cam_node_t));
                if (new_node != NULL) {
                    memset(new_node, 0, sizeof(cam_node_t));
                    new_node->data = (void *)new_buf;
                    if ( insert_before_buf ) {
                        cam_list_insert_before_node(&new_node->list, insert_before_buf);
                    } else {
                        cam_list_add_tail_node(&new_node->list, &queue->que.head.list);
                    }
                    queue->que.size++;
                } else {
                    LOGE("Out of memory");
                    free(new_buf);
                    mm_camera_muxer_buf_done(buffer);
                }
            }
        } else {
            if (NULL != new_buf) {
                free(new_buf);
            }
            mm_camera_muxer_buf_done(buffer);
        }
    }
    pthread_mutex_unlock(&queue->que.lock);

    /* bufdone overflowed bufs */
    while (queue->match_cnt > queue->attr.water_mark) {
        super_obj = mm_camera_muxer_frame_sync_dequeue(queue, FALSE);
        if (NULL != super_obj) {
            for (i = 0; i < MAX_OBJS_FOR_FRAME_SYNC; i++) {
                if (super_obj->super_buf[i].num_bufs != 0) {
                    mm_camera_muxer_buf_done(&super_obj->super_buf[i]);
                }
            }
            free(super_obj);
        }
    }
    return 0;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_buf_done
 *
 * DESCRIPTION: function release super buffer.
 *
 * PARAMETERS :
 *   @buffer: ptr to super buffer to release.
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              1 -- failure
 *==========================================================================*/
void mm_camera_muxer_buf_done(mm_camera_super_buf_t *buffer)
{
    uint8_t i;
    mm_camera_obj_t *my_obj = NULL;

    if (buffer == NULL) {
        LOGW("Null buffer");
        return;
    }

    my_obj = mm_camera_util_get_camera_by_handler(buffer->camera_handle);
    if (my_obj != NULL) {
        for (i=0; i < buffer->num_bufs; i++) {
            if (buffer->bufs[i] != NULL) {
                pthread_mutex_lock(&my_obj->cam_lock);
                mm_camera_qbuf(my_obj, buffer->ch_id, buffer->bufs[i]);
            }
        }
    }
}

/*===========================================================================
 * FUNCTION   : mm_muxer_frame_sync_queue_init
 *
 * DESCRIPTION: Inittialize frame sync queue
 *
 * PARAMETERS :
 *   @queue: ptr to frame sync queue
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              1 -- failure
 *==========================================================================*/
int32_t mm_muxer_frame_sync_queue_init(mm_frame_sync_queue_t *queue)
{
    int32_t rc = 0;
    queue->expected_frame_id = 0;
    queue->match_cnt = 0;
    queue->num_objs = 0;
    memset(&queue->bundled_objs, 0, sizeof(queue->bundled_objs));
    rc = cam_queue_init(&queue->que);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_muxer_frame_sync_queue_deinit
 *
 * DESCRIPTION: Inittialize frame sync queue
 *
 * PARAMETERS :
 *   @queue: ptr to frame sync queue
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              1 -- failure
 *==========================================================================*/
int32_t mm_muxer_frame_sync_queue_deinit(mm_frame_sync_queue_t *queue)
{
    int32_t rc = 0;
    rc = cam_queue_deinit(&queue->que);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_frame_sync_flush
 *
 * DESCRIPTION: function to flush frame sync queue
 *
 * PARAMETERS :
 *   @queue: ptr to frame sync queue
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_frame_sync_flush(mm_frame_sync_queue_t *queue)
{
    int32_t rc = 0, i = 0;
    mm_frame_sync_queue_node_t *super_obj = NULL;

    super_obj = mm_camera_muxer_frame_sync_dequeue(queue, FALSE);
    while (super_obj != NULL) {
        for (i = 0; i < MAX_OBJS_FOR_FRAME_SYNC; i++) {
            if (super_obj->super_buf[i].num_bufs != 0) {
                mm_camera_muxer_buf_done(&super_obj->super_buf[i]);
            }
        }
        free(super_obj);
        super_obj = NULL;
        super_obj = mm_camera_muxer_frame_sync_dequeue(queue, FALSE);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_stream_frame_sync_flush
 *
 * DESCRIPTION: function to flush frame sync queue
 *
 * PARAMETERS :
 *   @queue: ptr to frame sync queue
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_stream_frame_sync_flush(mm_stream_t *str_obj)
{
    int32_t rc = 0;
    mm_stream_t *my_obj = str_obj;

    if (my_obj->master_str_obj) {
        my_obj = my_obj->master_str_obj;
    }

    rc = mm_camera_muxer_frame_sync_flush(&my_obj->frame_sync.superbuf_queue);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_channel_frame_sync_flush
 *
 * DESCRIPTION: function to flush frame sync queue
 *
 * PARAMETERS :
 *   @queue: ptr to frame sync queue
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_channel_frame_sync_flush(mm_channel_t *ch_obj)
{
    int32_t rc = 0;
    mm_channel_t *my_obj = ch_obj;

    if (ch_obj->master_ch_obj != NULL) {
        my_obj = ch_obj->master_ch_obj;
    }

    rc = mm_camera_muxer_frame_sync_flush(&my_obj->frame_sync.superbuf_queue);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_get_stream_buf_cnt
 *
 * DESCRIPTION: function to calculate buffer count for auxillary streams.
 *
 * PARAMETERS :
 *   @queue: ptr to frame sync queue
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              1 -- failure
 *==========================================================================*/
uint32_t mm_camera_muxer_get_stream_buf_cnt(mm_stream_t *str_obj)
{
    uint32_t buf_cnt = 0;
    uint8_t i = 0;
    mm_stream_t *my_obj = str_obj;

    if (str_obj->master_str_obj != NULL) {
        my_obj = str_obj->master_str_obj;
    }

    buf_cnt = my_obj->buf_num;
    for (i = 0; i < my_obj->num_s_cnt; i++) {
        if (my_obj->aux_str_obj[i]->is_res_shared) {
            buf_cnt += my_obj->aux_str_obj[i]->buf_num;
        }
    }
    if (buf_cnt > CAM_MAX_NUM_BUFS_PER_STREAM) {
        buf_cnt = CAM_MAX_NUM_BUFS_PER_STREAM;
    }
    return buf_cnt;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_get_stream_bufs
 *
 * DESCRIPTION: function to assign buffers for auxillary streams.
 *
 * PARAMETERS :
 *   @queue: ptr to frame sync queue
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_get_stream_bufs(mm_stream_t *my_obj)
{
    int32_t rc = 0;
    uint32_t i = 0;
    mm_stream_t *master_obj = NULL;

    if (my_obj == NULL || my_obj->master_str_obj == NULL
            || !my_obj->is_res_shared) {
        LOGE("Invalid argument");
        return rc;
    }
    master_obj = my_obj->master_str_obj;

    if (master_obj->total_buf_cnt == 0) {
        my_obj->buf_idx = 0;
        return rc;
    }

    if ((master_obj->total_buf_cnt -
            (master_obj->buf_idx + master_obj->buf_num))
            <= 0) {
        LOGE("No enough buffer available %d num_bufs = %d",
                master_obj->total_buf_cnt, master_obj->buf_num);
        return rc;
    }

    my_obj->total_buf_cnt = master_obj->total_buf_cnt;
    my_obj->buf_idx = master_obj->buf_idx + master_obj->buf_num;
    if ((my_obj->buf_idx + my_obj->buf_num) > my_obj->total_buf_cnt) {
        my_obj->buf_num = my_obj->total_buf_cnt - my_obj->buf_idx;
    }

    my_obj->buf = master_obj->buf;
    for (i = my_obj->buf_idx; i < (my_obj->buf_idx + my_obj->buf_num); i++) {
        my_obj->buf_status[i].initial_reg_flag =
                master_obj->buf_status[i].initial_reg_flag;
    }
    LOGH("Buffer total = %d buf_cnt = %d offsset = %d",my_obj->total_buf_cnt,
            my_obj->buf_num, my_obj->buf_idx);
    return 0;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_put_stream_bufs
 *
 * DESCRIPTION: function to release buffers for auxillary streams.
 *
 * PARAMETERS :
 *   @queue: ptr to frame sync queue
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              1 -- failure
 *==========================================================================*/
int32_t mm_camera_muxer_put_stream_bufs(mm_stream_t *my_obj)
{
    int32_t rc = -1;

    if (my_obj == NULL || !my_obj->is_res_shared) {
        LOGE("Invalid argument");
        return rc;
    }
    my_obj->total_buf_cnt = 0;
    return 0;
}

/*===========================================================================
 * FUNCTION   : mm_camera_map_stream_buf_ops
 *
 * DESCRIPTION: ops for mapping stream buffer via domain socket to server.
 *              This function will be passed to upper layer as part of ops table
 *              to be used by upper layer when allocating stream buffers and mapping
 *              buffers to server via domain socket.
 *
 * PARAMETERS :
 *   @frame_idx    : index of buffer within the stream buffers, only valid if
 *                   buf_type is CAM_MAPPING_BUF_TYPE_STREAM_BUF or
 *                   CAM_MAPPING_BUF_TYPE_OFFLINE_INPUT_BUF
 *   @plane_idx    : plane index. If all planes share the same fd,
 *                   plane_idx = -1; otherwise, plean_idx is the
 *                   index to plane (0..num_of_planes)
 *   @fd           : file descriptor of the buffer
 *   @size         : size of the buffer
 *   @userdata     : user data ptr (stream object)
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_map_stream_buf_ops(uint32_t buf_idx,
        int32_t plane_idx, int fd, size_t size,
        void *buffer, cam_mapping_buf_type type,
        void *userdata)
{
    int32_t rc = 0;
    mm_stream_t *my_obj = (mm_stream_t *)userdata;
    int32_t i = 0;

    switch (type) {
        case CAM_MAPPING_BUF_TYPE_STREAM_INFO:
        case CAM_MAPPING_BUF_TYPE_MISC_BUF:
            if (buf_idx != 0 && my_obj->aux_str_obj[buf_idx] != NULL) {
                my_obj = my_obj->aux_str_obj[buf_idx];
            }
            break;
        case CAM_MAPPING_BUF_TYPE_STREAM_BUF:
        case CAM_MAPPING_BUF_TYPE_OFFLINE_INPUT_BUF:
        case CAM_MAPPING_BUF_TYPE_OFFLINE_META_BUF:
        case CAM_MAPPING_BUF_TYPE_STREAM_USER_BUF:
            for(i = 0; i < my_obj->num_s_cnt; i++) {
                if (my_obj->aux_str_obj[i] != NULL) {
                    rc = mm_stream_map_buf(my_obj->aux_str_obj[i],
                            type, buf_idx, plane_idx, fd, size, buffer);
                }
            }
            break;
        default:
            LOGE("Not buffer for stream : %d", type);
            rc = -1;
            break;
    }

    if (rc == 0) {
        rc = mm_stream_map_buf(my_obj,
                type, buf_idx, plane_idx, fd, size, buffer);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_bundled_map_buf_ops
 *
 * DESCRIPTION: ops for mapping bundled stream buffers via domain socket to server.
 *              This function will be passed to upper layer as part of ops table
 *              to be used by upper layer when allocating stream buffers and mapping
 *              buffers to server via domain socket.
 *
 * PARAMETERS :
 *   @buf_map_list : list of buffer mapping information
 *   @userdata     : user data ptr (stream object)
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_bundled_map_stream_buf_ops(
        const cam_buf_map_type_list *buf_map_list,
        void *userdata)
{
    int32_t rc = 0;
    mm_stream_t *my_obj = (mm_stream_t *)userdata;
    uint32_t i = 0;

    if (buf_map_list == NULL || buf_map_list->length == 0) {
        LOGW("Invalid argument");
        return rc;
    }

    uint32_t buf_idx;
    cam_mapping_buf_type type = buf_map_list->buf_maps[0].type;
    switch (type) {
        case CAM_MAPPING_BUF_TYPE_STREAM_INFO:
        case CAM_MAPPING_BUF_TYPE_MISC_BUF:
            buf_idx = buf_map_list->buf_maps[0].frame_idx;
            if (buf_idx == 0) {
                rc = mm_stream_map_buf(my_obj,
                        type, buf_idx,
                        buf_map_list->buf_maps[0].plane_idx,
                        buf_map_list->buf_maps[0].fd,
                        buf_map_list->buf_maps[0].size,
                        buf_map_list->buf_maps[0].buffer);
                if (rc != 0) {
                    LOGE("Failed rc = %d", rc);
                }
            }
            for (i = 1; i < buf_map_list->length; i++) {
                buf_idx = buf_map_list->buf_maps[i].frame_idx;
                if ((i == buf_idx)
                        && (my_obj->aux_str_obj[i] != NULL)) {
                    rc = mm_stream_map_buf(my_obj->aux_str_obj[i],
                            type, buf_idx,
                            buf_map_list->buf_maps[i].plane_idx,
                            buf_map_list->buf_maps[i].fd,
                            buf_map_list->buf_maps[i].size,
                            buf_map_list->buf_maps[i].buffer);
                }
            }
            break;
        case CAM_MAPPING_BUF_TYPE_STREAM_BUF:
        case CAM_MAPPING_BUF_TYPE_OFFLINE_INPUT_BUF:
        case CAM_MAPPING_BUF_TYPE_OFFLINE_META_BUF:
        case CAM_MAPPING_BUF_TYPE_STREAM_USER_BUF:
            rc = mm_stream_map_bufs(my_obj, buf_map_list);
            for(i = 0; i < my_obj->num_s_cnt; i++) {
                if (my_obj->aux_str_obj[i] != NULL) {
                    rc = mm_stream_map_bufs(my_obj->aux_str_obj[i],
                            buf_map_list);
                }
            }
            break;
        default:
            LOGE("Not buffer for stream : %d", type);
            rc = -1;
            break;
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_muxer_unmap_buf_ops
 *
 * DESCRIPTION: ops for unmapping stream buffer via domain socket to server.
 *              This function will be passed to upper layer as part of ops table
 *              to be used by upper layer when allocating stream buffers and unmapping
 *              buffers to server via domain socket.
 *
 * PARAMETERS :
 *   @frame_idx    : index of buffer within the stream buffers, only valid if
 *                   buf_type is CAM_MAPPING_BUF_TYPE_STREAM_BUF or
 *                   CAM_MAPPING_BUF_TYPE_OFFLINE_INPUT_BUF
 *   @plane_idx    : plane index. If all planes share the same fd,
 *                   plane_idx = -1; otherwise, plean_idx is the
 *                   index to plane (0..num_of_planes)
 *   @userdata     : user data ptr (stream object)
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_unmap_stream_buf_ops(uint32_t buf_idx,
           int32_t plane_idx, cam_mapping_buf_type type, void *userdata)
{
    int32_t rc = 0;
    mm_stream_t *my_obj = (mm_stream_t *)userdata;
    int32_t i = 0;

    switch (type) {
        case CAM_MAPPING_BUF_TYPE_STREAM_INFO:
        case CAM_MAPPING_BUF_TYPE_MISC_BUF:
            if (buf_idx != 0 && my_obj->aux_str_obj[buf_idx] != NULL) {
                my_obj = my_obj->aux_str_obj[buf_idx];
            }
            break;
        case CAM_MAPPING_BUF_TYPE_STREAM_BUF:
        case CAM_MAPPING_BUF_TYPE_OFFLINE_INPUT_BUF:
        case CAM_MAPPING_BUF_TYPE_OFFLINE_META_BUF:
        case CAM_MAPPING_BUF_TYPE_STREAM_USER_BUF:
            for(i = 0; i < my_obj->num_s_cnt; i++) {
                if (my_obj->aux_str_obj[i] != NULL) {
                    rc = mm_stream_unmap_buf(my_obj->aux_str_obj[i],
                            type, buf_idx, plane_idx);
                }
            }
            break;
        default:
            LOGE("Not buffer for stream : %d", type);
            rc = -1;
            break;
    }

    if (rc == 0) {
        rc = mm_stream_unmap_buf(my_obj,
                type, buf_idx, plane_idx);
    }
    return rc;
}


