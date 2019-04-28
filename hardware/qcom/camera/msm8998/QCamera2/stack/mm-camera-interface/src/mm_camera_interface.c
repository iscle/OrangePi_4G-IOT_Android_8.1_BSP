/* Copyright (c) 2012-2016, The Linux Foundation. All rights reserved.
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
#include <linux/media.h>
#include <media/msm_cam_sensor.h>
#include <dlfcn.h>
#include <unistd.h>

#define IOCTL_H <SYSTEM_HEADER_PREFIX/ioctl.h>
#include IOCTL_H

// Camera dependencies
#include "mm_camera_dbg.h"
#include "mm_camera_interface.h"
#include "mm_camera.h"
#include "mm_camera_muxer.h"

static pthread_mutex_t g_intf_lock = PTHREAD_MUTEX_INITIALIZER;
static mm_camera_ctrl_t g_cam_ctrl;

static pthread_mutex_t g_handler_lock = PTHREAD_MUTEX_INITIALIZER;
static uint8_t g_handler_history_count = 0; /* history count for handler */

// 16th (starting from 0) bit tells its a BACK or FRONT camera
#define CAM_SENSOR_FACING_MASK       (1U<<16)
#define CAM_SENSOR_TYPE_MASK         (1U<<24)
#define CAM_SENSOR_FORMAT_MASK       (1U<<25)
#define CAM_SENSOR_SECURE_MASK       (1U<<26)

/*===========================================================================
 * FUNCTION   : mm_camera_util_generate_handler
 *
 * DESCRIPTION: utility function to generate handler for camera/channel/stream
 *
 * PARAMETERS :
 *   @index: index of the object to have handler
 *
 * RETURN     : uint32_t type of handle that uniquely identify the object
 *==========================================================================*/
uint32_t mm_camera_util_generate_handler(uint8_t index)
{
    uint32_t handler = 0;
    pthread_mutex_lock(&g_handler_lock);
    g_handler_history_count++;
    if (0 == g_handler_history_count) {
        g_handler_history_count++;
    }
    handler = g_handler_history_count;
    handler = (handler<<8) | index;
    pthread_mutex_unlock(&g_handler_lock);
    return handler;
}

/*===========================================================================
 * FUNCTION   : mm_camera_util_get_index_by_handler
 *
 * DESCRIPTION: utility function to get index from handle
 *
 * PARAMETERS :
 *   @handler: object handle
 *
 * RETURN     : uint8_t type of index derived from handle
 *==========================================================================*/
uint8_t mm_camera_util_get_index_by_handler(uint32_t handler)
{
    return (handler & 0x000000ff);
}

/*===========================================================================
 * FUNCTION   : mm_camera_util_get_dev_name
 *
 * DESCRIPTION: utility function to get device name from camera handle
 *
 * PARAMETERS :
 *   @cam_handle: camera handle
 *
 * RETURN     : char ptr to the device name stored in global variable
 * NOTE       : caller should not free the char ptr
 *==========================================================================*/
const char *mm_camera_util_get_dev_name(uint32_t cam_handle)
{
    char *dev_name = NULL;
    uint8_t cam_idx = mm_camera_util_get_index_by_handler(cam_handle);
    if(cam_idx < MM_CAMERA_MAX_NUM_SENSORS) {
        dev_name = g_cam_ctrl.video_dev_name[cam_idx];
    }
    return dev_name;
}

/*===========================================================================
 * FUNCTION   : mm_camera_util_get_camera_by_handler
 *
 * DESCRIPTION: utility function to get camera object from camera handle
 *
 * PARAMETERS :
 *   @cam_handle: camera handle
 *
 * RETURN     : ptr to the camera object stored in global variable
 * NOTE       : caller should not free the camera object ptr
 *==========================================================================*/
mm_camera_obj_t* mm_camera_util_get_camera_by_handler(uint32_t cam_handle)
{
    mm_camera_obj_t *cam_obj = NULL;
    uint8_t cam_idx = 0;

    for (cam_idx = 0; cam_idx < MM_CAMERA_MAX_NUM_SENSORS; cam_idx++) {
         if ((NULL != g_cam_ctrl.cam_obj[cam_idx]) &&
                (cam_handle == (uint32_t)g_cam_ctrl.cam_obj[cam_idx]->my_hdl)) {
            cam_obj = g_cam_ctrl.cam_obj[cam_idx];
            break;
        }
    }
    return cam_obj;
}


/*===========================================================================
 * FUNCTION   : mm_camera_util_set_camera_object
 *
 * DESCRIPTION: utility function to set camera object to global structure
 *
 * PARAMETERS :
 *   @cam_idx : index to store cambera object
 *   @obj     : Camera object to store
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
int32_t mm_camera_util_set_camera_object(uint8_t cam_idx, mm_camera_obj_t *obj)
{
    int32_t rc = 0;
    pthread_mutex_lock(&g_intf_lock);
    if (cam_idx < MM_CAMERA_MAX_NUM_SENSORS) {
        g_cam_ctrl.cam_obj[cam_idx] = obj;
    } else {
        rc = -1;
    }
    pthread_mutex_unlock(&g_intf_lock);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_util_get_camera_head_obj
 *
 * DESCRIPTION: utility function to get camera object from camera handle
 *
 * PARAMETERS :
 *   @cam_handle: camera handle
 *
 * RETURN     : ptr to the master/primary camera object
 *==========================================================================*/
mm_camera_obj_t* mm_camera_util_get_camera_head(uint32_t cam_handle)
{
    mm_camera_obj_t *cam_obj = NULL;

    cam_obj = mm_camera_util_get_camera_by_handler(cam_handle);
    if (cam_obj != NULL && cam_obj->master_cam_obj != NULL) {
        cam_obj = cam_obj->master_cam_obj;
    }
    return cam_obj;
}

/*===========================================================================
 * FUNCTION   : mm_camera_util_get_camera_by_session_id
 *
 * DESCRIPTION: utility function to get camera object from camera sessionID
 *
 * PARAMETERS :
 *   @session_id: sessionid for which cam obj mapped
 *
 * RETURN     : ptr to the camera object stored in global variable
 * NOTE       : caller should not free the camera object ptr
 *==========================================================================*/
mm_camera_obj_t* mm_camera_util_get_camera_by_session_id(uint32_t session_id)
{
   int cam_idx = 0;
   mm_camera_obj_t *cam_obj = NULL;
   for (cam_idx = 0; cam_idx < MM_CAMERA_MAX_NUM_SENSORS; cam_idx++) {
        if ((NULL != g_cam_ctrl.cam_obj[cam_idx]) &&
                (session_id == (uint32_t)g_cam_ctrl.cam_obj[cam_idx]->sessionid)) {
            LOGD("session id:%d match idx:%d\n", session_id, cam_idx);
            cam_obj = g_cam_ctrl.cam_obj[cam_idx];
        }
    }
    return cam_obj;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_query_capability
 *
 * DESCRIPTION: query camera capability
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
static int32_t mm_camera_intf_query_capability(uint32_t camera_handle)
{
    int32_t rc = -1;
    mm_camera_obj_t *my_obj = NULL;
    uint32_t handle = 0;
    uint32_t aux_handle = 0;

    LOGD("E: camera_handler = %d ", camera_handle);

    pthread_mutex_lock(&g_intf_lock);
    handle = get_main_camera_handle(camera_handle);
    aux_handle = get_aux_camera_handle(camera_handle);

    if (handle) {
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_query_capability(my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    } else {
        pthread_mutex_unlock(&g_intf_lock);
    }

    if (aux_handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_query_capability(aux_handle, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    LOGH("camera_handle = %u rc = %u X", camera_handle, rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_set_parms
 *
 * DESCRIPTION: set parameters per camera
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @parms        : ptr to a param struct to be set to server
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 * NOTE       : Assume the parms struct buf is already mapped to server via
 *              domain socket. Corresponding fields of parameters to be set
 *              are already filled in by upper layer caller.
 *==========================================================================*/
static int32_t mm_camera_intf_set_parms(uint32_t camera_handle,
                                        parm_buffer_t *parms)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;

    uint32_t handle = get_main_camera_handle(camera_handle);
    uint32_t aux_handle = get_aux_camera_handle(camera_handle);

    if (aux_handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_set_parms(aux_handle,
                    parms, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_set_parms(my_obj, parms);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_get_parms
 *
 * DESCRIPTION: get parameters per camera
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @parms        : ptr to a param struct to be get from server
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 * NOTE       : Assume the parms struct buf is already mapped to server via
 *              domain socket. Parameters to be get from server are already
 *              filled in by upper layer caller. After this call, corresponding
 *              fields of requested parameters will be filled in by server with
 *              detailed information.
 *==========================================================================*/
static int32_t mm_camera_intf_get_parms(uint32_t camera_handle,
                                        parm_buffer_t *parms)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t handle = get_main_camera_handle(camera_handle);
    uint32_t aux_handle = get_aux_camera_handle(camera_handle);

    if (aux_handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_get_parms(aux_handle,
                    parms, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_get_parms(my_obj, parms);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    return rc;

}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_do_auto_focus
 *
 * DESCRIPTION: performing auto focus
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 * NOTE       : if this call success, we will always assume there will
 *              be an auto_focus event following up.
 *==========================================================================*/
static int32_t mm_camera_intf_do_auto_focus(uint32_t camera_handle)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t handle = get_main_camera_handle(camera_handle);
    uint32_t aux_handle = get_aux_camera_handle(camera_handle);

    if (aux_handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_do_auto_focus(aux_handle, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_do_auto_focus(my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGH("rc = %d camera_handle = %u X", rc, camera_handle);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_cancel_auto_focus
 *
 * DESCRIPTION: cancel auto focus
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
static int32_t mm_camera_intf_cancel_auto_focus(uint32_t camera_handle)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t handle = get_main_camera_handle(camera_handle);
    uint32_t aux_handle = get_aux_camera_handle(camera_handle);

    if (aux_handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_cancel_auto_focus(aux_handle, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_cancel_auto_focus(my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGH("rc = %d camera_handle = %u X", rc, camera_handle);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_prepare_snapshot
 *
 * DESCRIPTION: prepare hardware for snapshot
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @do_af_flag   : flag indicating if AF is needed
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
static int32_t mm_camera_intf_prepare_snapshot(uint32_t camera_handle,
                                               int32_t do_af_flag)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t handle = get_main_camera_handle(camera_handle);
    uint32_t aux_handle = get_aux_camera_handle(camera_handle);

    if (aux_handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_prepare_snapshot(aux_handle,
                    do_af_flag, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);

            rc = mm_camera_prepare_snapshot(my_obj, do_af_flag);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
        return rc;
    }
    LOGH("rc = %d camera_handle = %u X", rc, camera_handle);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_flush
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
static int32_t mm_camera_intf_flush(uint32_t camera_handle)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t handle = get_main_camera_handle(camera_handle);
    uint32_t aux_handle = get_aux_camera_handle(camera_handle);

    if (aux_handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_head(aux_handle);

        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_flush(aux_handle, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_by_handler(camera_handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_flush(my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_close
 *
 * DESCRIPTION: close a camera by its handle
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
static int32_t mm_camera_intf_close(uint32_t camera_handle)
{
    int32_t rc = -1;
    uint8_t cam_idx = -1;
    mm_camera_obj_t *my_obj = NULL;

    LOGD("E: camera_handler = %d ", camera_handle);

    uint32_t handle = get_main_camera_handle(camera_handle);
    uint32_t aux_handle = get_aux_camera_handle(camera_handle);
    if (aux_handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_close_camera(aux_handle, my_obj);
        }
    }

    if (handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if (my_obj){
            if (my_obj->aux_cam_obj[0] != NULL) {
                /*Close aux cameras*/
                pthread_mutex_lock(&my_obj->muxer_lock);
                pthread_mutex_unlock(&g_intf_lock);
                rc = mm_camera_muxer_close_camera(
                        my_obj->aux_cam_obj[0]->my_hdl, my_obj);
                pthread_mutex_lock(&g_intf_lock);
            }

            cam_idx = mm_camera_util_get_index_by_num(
                    my_obj->my_num, my_obj->my_hdl);
            my_obj->ref_count--;
            if(my_obj->ref_count > 0) {
                /* still have reference to obj, return here */
                LOGD("ref_count=%d\n", my_obj->ref_count);
                pthread_mutex_unlock(&g_intf_lock);
                rc = 0;
            } else {
                /* need close camera here as no other reference
                 * first empty g_cam_ctrl's referent to cam_obj */
                g_cam_ctrl.cam_obj[cam_idx] = NULL;
                pthread_mutex_lock(&my_obj->cam_lock);
                pthread_mutex_unlock(&g_intf_lock);
                rc = mm_camera_close(my_obj);
                pthread_mutex_destroy(&my_obj->cam_lock);
                pthread_mutex_destroy(&my_obj->muxer_lock);
                free(my_obj);
                my_obj = NULL;
            }
        } else {
             pthread_mutex_unlock(&g_intf_lock);
        }
    } else {
        pthread_mutex_unlock(&g_intf_lock);
    }

    LOGH("camera_handler = %u rc = %d", camera_handle, rc);
#ifdef QCAMERA_REDEFINE_LOG
    mm_camera_debug_close();
#endif

    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_add_channel
 *
 * DESCRIPTION: add a channel
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @attr         : bundle attribute of the channel if needed
 *   @channel_cb   : callback function for bundle data notify
 *   @userdata     : user data ptr
 *
 * RETURN     : uint32_t type of channel handle
 *              0  -- invalid channel handle, meaning the op failed
 *              >0 -- successfully added a channel with a valid handle
 * NOTE       : if no bundle data notify is needed, meaning each stream in the
 *              channel will have its own stream data notify callback, then
 *              attr, channel_cb, and userdata can be NULL. In this case,
 *              no matching logic will be performed in channel for the bundling.
 *==========================================================================*/
static uint32_t mm_camera_intf_add_channel(uint32_t camera_handle,
                                           mm_camera_channel_attr_t *attr,
                                           mm_camera_buf_notify_t channel_cb,
                                           void *userdata)
{
    uint32_t ch_id = 0, aux_ch_id = 0;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t handle = get_main_camera_handle(camera_handle);
    uint32_t aux_handle = get_aux_camera_handle(camera_handle);

    LOGD("E camera_handler = %d", camera_handle);
    if (handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            ch_id = mm_camera_add_channel(my_obj, attr, channel_cb, userdata);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (aux_handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            aux_ch_id = mm_camera_muxer_add_channel(aux_handle, attr,
                    channel_cb, userdata, ch_id, my_obj);
            if (aux_ch_id <= 0) {
                pthread_mutex_lock(&my_obj->cam_lock);
                mm_camera_del_channel(my_obj, ch_id);
            } else {
                ch_id |= aux_ch_id;
           }
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGH("camera_handle = %u ch_id = %u X", camera_handle, ch_id);
    return ch_id;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_del_channel
 *
 * DESCRIPTION: delete a channel by its handle
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 * NOTE       : all streams in the channel should be stopped already before
 *              this channel can be deleted.
 *==========================================================================*/
static int32_t mm_camera_intf_del_channel(uint32_t camera_handle,
                                          uint32_t ch_id)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t m_chid = get_main_camera_handle(ch_id);
    uint32_t aux_chid = get_aux_camera_handle(ch_id);

    LOGD("E ch_id = %d", ch_id);

    if (aux_chid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            mm_camera_muxer_delete_channel(aux_handle, aux_chid, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (m_chid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_del_channel(my_obj, m_chid);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGH("rc = %d ch_id = %u X", rc, ch_id);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_get_bundle_info
 *
 * DESCRIPTION: query bundle info of the channel
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @bundle_info  : bundle info to be filled in
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 * NOTE       : all streams in the channel should be stopped already before
 *              this channel can be deleted.
 *==========================================================================*/
static int32_t mm_camera_intf_get_bundle_info(uint32_t camera_handle,
                                              uint32_t ch_id,
                                              cam_bundle_config_t *bundle_info)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t m_chid = get_main_camera_handle(ch_id);
    uint32_t aux_chid = get_aux_camera_handle(ch_id);

    LOGD("E ch_id = %d", ch_id);

    if (aux_chid && m_chid) {
        LOGE("Does not support 2 channels for bundle info");
        return rc;
    }

    if (aux_chid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_get_bundle_info(aux_handle, aux_chid,
                    bundle_info, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    } else if (m_chid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_get_bundle_info(my_obj, m_chid, bundle_info);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGD("rc = %d ch_id = %d X", rc, ch_id);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_register_event_notify
 *
 * DESCRIPTION: register for event notify
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @evt_cb       : callback for event notify
 *   @user_data    : user data ptr
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
static int32_t mm_camera_intf_register_event_notify(uint32_t camera_handle,
                                                    mm_camera_event_notify_t evt_cb,
                                                    void * user_data)
{
    int32_t rc = -1;
    mm_camera_obj_t *my_obj = NULL;
    LOGD("E ");

    uint32_t handle = get_main_camera_handle(camera_handle);
    uint32_t aux_handle = get_aux_camera_handle(camera_handle);

    if (handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_register_event_notify(my_obj, evt_cb, user_data);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (aux_handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_register_event_notify(aux_handle,
                    evt_cb, user_data, my_obj);
        }
    }
    LOGD("E rc = %d", rc);
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
static int32_t mm_camera_intf_qbuf(uint32_t camera_handle,
                                    uint32_t ch_id,
                                    mm_camera_buf_def_t *buf)
{
    int32_t rc = -1;
    mm_camera_obj_t *my_obj = NULL;
    uint32_t strid = 0;
    uint32_t aux_strid = 0;

    if (buf != NULL) {
        strid = get_main_camera_handle(buf->stream_id);
        aux_strid = get_aux_camera_handle(buf->stream_id);
    }

    if (strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        uint32_t chid = get_main_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_qbuf(my_obj, chid, buf);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (aux_strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        uint32_t aux_chid = get_aux_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_qbuf(aux_handle, aux_chid, buf, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGD("X evt_type = %d",rc);
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
static int32_t mm_camera_intf_cancel_buf(uint32_t camera_handle, uint32_t ch_id, uint32_t stream_id,
                     uint32_t buf_idx)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;

    pthread_mutex_lock(&g_intf_lock);
    my_obj = mm_camera_util_get_camera_by_handler(camera_handle);

    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&g_intf_lock);
        rc = mm_camera_cancel_buf(my_obj, ch_id, stream_id, buf_idx);
    } else {
        pthread_mutex_unlock(&g_intf_lock);
    }
    LOGD("X evt_type = %d",rc);
    return rc;
}


/*===========================================================================
 * FUNCTION   : mm_camera_intf_get_queued_buf_count
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
static int32_t mm_camera_intf_get_queued_buf_count(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t strid = get_main_camera_handle(stream_id);
    uint32_t aux_strid = get_aux_camera_handle(stream_id);

    if (strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        uint32_t chid = get_main_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_get_queued_buf_count(my_obj, chid, strid);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    } else if (aux_strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        uint32_t aux_chid = get_aux_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_get_queued_buf_count(aux_handle,
                    aux_chid, aux_strid, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGD("X queued buffer count = %d",rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_link_stream
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
static int32_t mm_camera_intf_link_stream(uint32_t camera_handle,
        uint32_t ch_id,
        uint32_t stream_id,
        uint32_t linked_ch_id)
{
    uint32_t id = 0;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t strid = get_main_camera_handle(stream_id);
    uint32_t aux_strid = get_aux_camera_handle(stream_id);
    uint32_t linked_chid = get_main_camera_handle(linked_ch_id);
    uint32_t aux_linked_chid = get_aux_camera_handle(linked_ch_id);

    LOGD("E handle = %u ch_id = %u",
          camera_handle, ch_id);

    if (strid && linked_chid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        uint32_t m_chid = get_main_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            id = mm_camera_link_stream(my_obj, m_chid, strid, linked_chid);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (aux_strid && aux_linked_chid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        uint32_t aux_chid = get_aux_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            id = mm_camera_muxer_link_stream(aux_handle, aux_chid,
                    aux_strid, aux_linked_chid, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    LOGH("X ch_id = %u stream_id = %u linked_ch_id = %u id = %u",
            ch_id, stream_id, linked_ch_id, id);
    return (int32_t)id;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_add_stream
 *
 * DESCRIPTION: add a stream into a channel
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *
 * RETURN     : uint32_t type of stream handle
 *              0  -- invalid stream handle, meaning the op failed
 *              >0 -- successfully added a stream with a valid handle
 *==========================================================================*/
static uint32_t mm_camera_intf_add_stream(uint32_t camera_handle,
                                          uint32_t ch_id)
{
    uint32_t stream_id = 0, aux_stream_id;
    mm_camera_obj_t *my_obj = NULL;
    uint32_t m_ch_id = get_main_camera_handle(ch_id);
    uint32_t aux_chid = get_aux_camera_handle(ch_id);

    LOGD("E handle = %d ch_id = %d",
          camera_handle, ch_id);
    if (m_ch_id) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            stream_id = mm_camera_add_stream(my_obj, m_ch_id);
       } else {
            pthread_mutex_unlock(&g_intf_lock);
       }
    }

    if (aux_chid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            aux_stream_id = mm_camera_muxer_add_stream(aux_handle, aux_chid,
                    m_ch_id, stream_id, my_obj);
            if (aux_stream_id <= 0) {
                LOGE("Failed to add stream");
                pthread_mutex_lock(&my_obj->cam_lock);
                mm_camera_del_stream(my_obj, m_ch_id, stream_id);
            } else {
                stream_id = stream_id | aux_stream_id;
            }
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGH("X ch_id = %u stream_id = %u", ch_id, stream_id);
    return stream_id;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_del_stream
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
 * NOTE       : stream should be stopped already before it can be deleted.
 *==========================================================================*/
static int32_t mm_camera_intf_del_stream(uint32_t camera_handle,
                                         uint32_t ch_id,
                                         uint32_t stream_id)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t m_strid = get_main_camera_handle(stream_id);
    uint32_t aux_strid = get_aux_camera_handle(stream_id);

    LOGD("E handle = %d ch_id = %d stream_id = %d",
          camera_handle, ch_id, stream_id);

    if (aux_strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        uint32_t aux_chid = get_aux_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            mm_camera_muxer_delete_stream(aux_handle, aux_chid,
                    aux_strid, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (m_strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        uint32_t m_chid = get_main_camera_handle(ch_id);

        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_del_stream(my_obj, m_chid, m_strid);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGH("X stream_id = %u rc = %d", stream_id, rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_config_stream
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
static int32_t mm_camera_intf_config_stream(uint32_t camera_handle,
                                            uint32_t ch_id,
                                            uint32_t stream_id,
                                            mm_camera_stream_config_t *config)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t strid = get_main_camera_handle(stream_id);
    uint32_t aux_strid = get_aux_camera_handle(stream_id);

    LOGD("E handle = %d, ch_id = %d,stream_id = %d",
          camera_handle, ch_id, stream_id);

    if (strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        uint32_t chid = get_main_camera_handle(ch_id);

        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_config_stream(my_obj, chid, strid, config);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (aux_strid && rc == 0) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        uint32_t aux_chid = get_aux_camera_handle(ch_id);

        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_config_stream(aux_handle,
                    aux_chid, aux_strid, config, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGH("X stream_id = %u rc = %d", stream_id, rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_start_channel
 *
 * DESCRIPTION: start a channel, which will start all streams in the channel
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @start_sensor_streaming: whether to start sensor streaming.
 *                            If false, start_sensor_streaming() must be
 *                            called to start sensor streaming.
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
static int32_t mm_camera_intf_start_channel(uint32_t camera_handle,
                                            uint32_t ch_id,
                                            bool start_sensor_streaming)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t chid = get_main_camera_handle(ch_id);
    uint32_t aux_chid = get_aux_camera_handle(ch_id);

    if (chid) {
        uint32_t handle = get_main_camera_handle(camera_handle);
        pthread_mutex_lock(&g_intf_lock);

        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_start_channel(my_obj, chid);
            // Start sensor streaming now if needed.
            if (rc == 0 && start_sensor_streaming) {
                rc = mm_camera_start_sensor_stream_on(my_obj, ch_id);
            }
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (aux_chid && rc == 0) {
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        pthread_mutex_lock(&g_intf_lock);

        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_start_channel(aux_handle, aux_chid, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGH("X ch_id = %u rc = %d", ch_id, rc);
    return rc;
}

static int32_t mm_camera_intf_start_sensor_streaming(uint32_t camera_handle,
                                            uint32_t ch_id)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t chid = get_main_camera_handle(ch_id);

    if (chid) {
        uint32_t handle = get_main_camera_handle(camera_handle);
        pthread_mutex_lock(&g_intf_lock);

        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_start_sensor_stream_on(my_obj, ch_id);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    LOGH("X ch_id = %u rc = %d", ch_id, rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_stop_channel
 *
 * DESCRIPTION: stop a channel, which will stop all streams in the channel
 *
 * PARAMETERS :
 *   @camera_handle   : camera handle
 *   @ch_id           : channel handle
 *   @stop_immediately: stop immediately without waiting for frame boundary.
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
static int32_t mm_camera_intf_stop_channel(uint32_t camera_handle,
                                           uint32_t ch_id,
                                           bool stop_immediately)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t chid = get_main_camera_handle(ch_id);
    uint32_t aux_chid = get_aux_camera_handle(ch_id);

    if (aux_chid) {
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        pthread_mutex_lock(&g_intf_lock);

        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_stop_channel(aux_handle, aux_chid, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    if (chid) {
        uint32_t handle = get_main_camera_handle(camera_handle);
        pthread_mutex_lock(&g_intf_lock);

        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_stop_channel(my_obj, chid, stop_immediately);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGH("X ch_id = %u rc = %d", ch_id, rc);
    return rc;

}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_request_super_buf
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
static int32_t mm_camera_intf_request_super_buf(uint32_t camera_handle,
        uint32_t ch_id, mm_camera_req_buf_t *buf)
{
    int32_t rc = -1;
    LOGD("E camera_handler = %d,ch_id = %d",
          camera_handle, ch_id);
    mm_camera_obj_t * my_obj = NULL;
    uint32_t chid = get_main_camera_handle(ch_id);
    uint32_t aux_chid = get_aux_camera_handle(ch_id);

    pthread_mutex_lock(&g_intf_lock);
    if (aux_chid && chid) {
        uint32_t handle = get_main_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if (my_obj && buf) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_request_super_buf(
                    ch_id, buf, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    } else if (chid) {
        uint32_t handle = get_main_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj && buf) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_request_super_buf (my_obj, chid, buf);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    } else if (aux_chid) {
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_by_handler(aux_handle);

        if(my_obj && buf) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_request_super_buf (my_obj, aux_chid, buf);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    LOGH("X ch_id = %u rc = %d", ch_id, rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_cancel_super_buf_request
 *
 * DESCRIPTION: for burst mode in bundle, cancel the reuqest for certain amount
 *              of matched frames from superbuf queue
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
static int32_t mm_camera_intf_cancel_super_buf_request(uint32_t camera_handle,
                                                       uint32_t ch_id)
{
    int32_t rc = -1;
    LOGD("E camera_handler = %d,ch_id = %d",
          camera_handle, ch_id);
    mm_camera_obj_t * my_obj = NULL;
    uint32_t chid = get_main_camera_handle(ch_id);
    uint32_t aux_chid = get_aux_camera_handle(ch_id);

    pthread_mutex_lock(&g_intf_lock);
    if (aux_chid && chid) {
        my_obj = mm_camera_util_get_camera_head(camera_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_cancel_super_buf_request(
                    camera_handle, ch_id, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    } else if (aux_chid) {
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_by_handler(aux_handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_cancel_super_buf_request(my_obj, chid);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    } else if (chid) {
        uint32_t handle = get_main_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_cancel_super_buf_request(my_obj, chid);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    LOGH("X ch_id = %u rc = %d", ch_id, rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_flush_super_buf_queue
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
static int32_t mm_camera_intf_flush_super_buf_queue(uint32_t camera_handle,
                                                    uint32_t ch_id, uint32_t frame_idx)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t chid = get_main_camera_handle(ch_id);
    uint32_t aux_chid = get_aux_camera_handle(ch_id);

    LOGD("E camera_handler = %d,ch_id = %d",
          camera_handle, ch_id);
    if (chid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_flush_super_buf_queue(my_obj, chid, frame_idx);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (aux_chid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_flush_super_buf_queue(aux_handle,
                    aux_chid, frame_idx, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    LOGH("X ch_id = %u rc = %d", ch_id, rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_start_zsl_snapshot
 *
 * DESCRIPTION: Starts zsl snapshot
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
static int32_t mm_camera_intf_start_zsl_snapshot(uint32_t camera_handle,
        uint32_t ch_id)
{
    int32_t rc = -1;
    mm_camera_obj_t *my_obj = NULL;
    uint32_t m_chid = get_main_camera_handle(ch_id);
    uint32_t aux_ch_id = get_aux_camera_handle(ch_id);

    LOGD("E camera_handler = %d,ch_id = %d",
          camera_handle, ch_id);

    if (aux_ch_id) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_start_zsl_snapshot(aux_handle,
                    aux_ch_id, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (m_chid) {
        uint32_t m_handle = get_main_camera_handle(camera_handle);
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_by_handler(m_handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_start_zsl_snapshot_ch(my_obj, m_chid);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGD("X rc = %d", rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_stop_zsl_snapshot
 *
 * DESCRIPTION: Stops zsl snapshot
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
static int32_t mm_camera_intf_stop_zsl_snapshot(uint32_t camera_handle,
        uint32_t ch_id)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t m_chid = get_main_camera_handle(ch_id);
    uint32_t aux_ch_id = get_aux_camera_handle(ch_id);

    LOGD("E camera_handler = %d,ch_id = %d",
          camera_handle, ch_id);

    if (aux_ch_id) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_stop_zsl_snapshot(aux_handle, aux_ch_id, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (ch_id) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_stop_zsl_snapshot_ch(my_obj, m_chid);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    LOGD("X rc = %d", rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_configure_notify_mode
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
static int32_t mm_camera_intf_configure_notify_mode(uint32_t camera_handle,
                                                    uint32_t ch_id,
                                                    mm_camera_super_buf_notify_mode_t notify_mode)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t chid = get_main_camera_handle(ch_id);
    uint32_t aux_ch_id = get_aux_camera_handle(ch_id);

    LOGD("E camera_handler = %d,ch_id = %d",
          camera_handle, ch_id);

    if (aux_ch_id) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_configure_notify_mode(aux_handle, aux_ch_id,
                    notify_mode, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (chid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_config_channel_notify(my_obj, chid,
                    notify_mode);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGD("X rc = %d", rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_map_buf
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
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
static int32_t mm_camera_intf_map_buf(uint32_t camera_handle,
    uint8_t buf_type, int fd, size_t size, void *buffer)
{
    int32_t rc = -1;
    mm_camera_obj_t *my_obj = NULL;
    uint32_t handle = get_main_camera_handle(camera_handle);
    uint32_t aux_handle = get_aux_camera_handle(camera_handle);

    if (handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_map_buf(my_obj, buf_type, fd, size, buffer);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    } else if (aux_handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_map_buf(aux_handle, buf_type,
                    fd, size, buffer, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_map_bufs
 *
 * DESCRIPTION: mapping camera buffer via domain socket to server
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @buf_type     : type of buffer to be mapped. could be following values:
 *                   CAM_MAPPING_BUF_TYPE_CAPABILITY
 *                   CAM_MAPPING_BUF_TYPE_SETPARM_BUF
 *                   CAM_MAPPING_BUF_TYPE_GETPARM_BUF
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
static int32_t mm_camera_intf_map_bufs(uint32_t camera_handle,
        const cam_buf_map_type_list *buf_map_list)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t handle = get_main_camera_handle(camera_handle);
    uint32_t aux_handle = get_aux_camera_handle(camera_handle);

    if (handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_map_bufs(my_obj, buf_map_list);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    } else if (aux_handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_map_bufs(aux_handle, buf_map_list, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_unmap_buf
 *
 * DESCRIPTION: unmapping camera buffer via domain socket to server
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @buf_type     : type of buffer to be unmapped. could be following values:
 *                   CAM_MAPPING_BUF_TYPE_CAPABILITY
 *                   CAM_MAPPING_BUF_TYPE_SETPARM_BUF
 *                   CAM_MAPPING_BUF_TYPE_GETPARM_BUF
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 *==========================================================================*/
static int32_t mm_camera_intf_unmap_buf(uint32_t camera_handle,
                                        uint8_t buf_type)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t handle = get_main_camera_handle(camera_handle);
    uint32_t aux_handle = get_aux_camera_handle(camera_handle);

    if (handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_unmap_buf(my_obj, buf_type);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (aux_handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_unmap_buf(aux_handle, buf_type, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_set_stream_parms
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
 * NOTE       : Assume the parms struct buf is already mapped to server via
 *              domain socket. Corresponding fields of parameters to be set
 *              are already filled in by upper layer caller.
 *==========================================================================*/
static int32_t mm_camera_intf_set_stream_parms(uint32_t camera_handle,
                                               uint32_t ch_id,
                                               uint32_t s_id,
                                               cam_stream_parm_buffer_t *parms)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t strid = get_main_camera_handle(s_id);
    uint32_t aux_strid = get_aux_camera_handle(s_id);

    LOGD("E camera_handle = %d,ch_id = %d,s_id = %d",
          camera_handle, ch_id, s_id);
    if (strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        uint32_t chid = get_main_camera_handle(ch_id);

        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_set_stream_parms(my_obj, chid, strid, parms);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (aux_strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        uint32_t aux_chid = get_aux_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_head(aux_handle);

        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_set_stream_parms(aux_handle, aux_chid,
                    aux_strid, parms, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGD("X rc = %d", rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_get_stream_parms
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
 * NOTE       : Assume the parms struct buf is already mapped to server via
 *              domain socket. Parameters to be get from server are already
 *              filled in by upper layer caller. After this call, corresponding
 *              fields of requested parameters will be filled in by server with
 *              detailed information.
 *==========================================================================*/
static int32_t mm_camera_intf_get_stream_parms(uint32_t camera_handle,
                                               uint32_t ch_id,
                                               uint32_t s_id,
                                               cam_stream_parm_buffer_t *parms)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t strid = get_main_camera_handle(s_id);
    uint32_t aux_strid = get_aux_camera_handle(s_id);

    LOGD("E camera_handle = %d,ch_id = %d,s_id = %d",
          camera_handle, ch_id, s_id);
    if (strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        uint32_t chid = get_main_camera_handle(ch_id);

        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_get_stream_parms(my_obj, chid, strid, parms);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (aux_strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        uint32_t aux_chid = get_aux_camera_handle(ch_id);

        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_get_stream_parms(aux_handle, aux_chid,
                    aux_strid, parms, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGD("X rc = %d", rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_map_stream_buf
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
static int32_t mm_camera_intf_map_stream_buf(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id, uint8_t buf_type,
        uint32_t buf_idx, int32_t plane_idx, int fd,
        size_t size, void *buffer)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t strid = get_main_camera_handle(stream_id);
    uint32_t aux_strid = get_aux_camera_handle(stream_id);

    LOGD("E camera_handle = %d, ch_id = %d, s_id = %d, buf_idx = %d, plane_idx = %d",
            camera_handle, ch_id, stream_id, buf_idx, plane_idx);

    if (strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        uint32_t chid = get_main_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_map_stream_buf(my_obj, chid, strid,
                    buf_type, buf_idx, plane_idx,
                    fd, size, buffer);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (aux_strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        uint32_t aux_chid = get_aux_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_map_stream_buf(aux_handle, aux_chid,
                    aux_strid, buf_type, buf_idx, plane_idx, fd, size,
                    buffer, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    LOGD("X rc = %d", rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_map_stream_bufs
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
static int32_t mm_camera_intf_map_stream_bufs(uint32_t camera_handle,
                                              uint32_t ch_id,
                                              const cam_buf_map_type_list *buf_map_list)
{
    int32_t rc = -1;
    uint32_t i;
    mm_camera_obj_t * my_obj = NULL;
    cam_buf_map_type_list m_buf_list, aux_buf_list;

    LOGD("E camera_handle = %d, ch_id = %d",
          camera_handle, ch_id);

    memset(&m_buf_list, 0, sizeof(m_buf_list));
    memset(&aux_buf_list, 0, sizeof(m_buf_list));
    for (i = 0; i < buf_map_list->length; i++) {
        uint32_t strid = get_main_camera_handle(buf_map_list->buf_maps[i].stream_id);
        uint32_t aux_strid = get_aux_camera_handle(buf_map_list->buf_maps[i].stream_id);
        if (strid) {
            m_buf_list.buf_maps[aux_buf_list.length] = buf_map_list->buf_maps[i];
            m_buf_list.buf_maps[aux_buf_list.length].stream_id = strid;
            m_buf_list.length++;
        }
        if (aux_strid) {
            aux_buf_list.buf_maps[aux_buf_list.length] = buf_map_list->buf_maps[i];
            aux_buf_list.buf_maps[aux_buf_list.length].stream_id = aux_strid;
            aux_buf_list.length++;
        }
    }

    if(m_buf_list.length != 0) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        uint32_t chid = get_main_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_map_stream_bufs(my_obj, chid, &m_buf_list);
        }else{
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if(aux_buf_list.length != 0) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        uint32_t aux_chid = get_aux_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj != NULL) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_map_stream_bufs(aux_handle,aux_chid,
                    &aux_buf_list, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGD("X rc = %d", rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_unmap_stream_buf
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
static int32_t mm_camera_intf_unmap_stream_buf(uint32_t camera_handle,
                                               uint32_t ch_id,
                                               uint32_t stream_id,
                                               uint8_t buf_type,
                                               uint32_t buf_idx,
                                               int32_t plane_idx)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t strid = get_main_camera_handle(stream_id);
    uint32_t aux_strid = get_aux_camera_handle(stream_id);


    LOGD("E camera_handle = %d, ch_id = %d, s_id = %d, buf_idx = %d, plane_idx = %d",
              camera_handle, ch_id, stream_id, buf_idx, plane_idx);

    if (aux_strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        uint32_t aux_chid = get_aux_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_unmap_stream_buf(aux_handle, aux_chid,
                   aux_strid, buf_type, buf_idx,
                   plane_idx, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        uint32_t chid = get_main_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_by_handler(handle);
        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_unmap_stream_buf(my_obj, chid, strid,
                    buf_type, buf_idx, plane_idx);
        }else{
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    LOGD("X rc = %d", rc);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_get_session_id
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
 * NOTE       : if this call succeeds, we will get a valid session id.
 *==========================================================================*/
static int32_t mm_camera_intf_get_session_id(uint32_t camera_handle,
                                                       uint32_t* sessionid)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t handle = get_main_camera_handle(camera_handle);
    uint32_t aux_handle = get_aux_camera_handle(camera_handle);

    if (handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            *sessionid = my_obj->sessionid;
            pthread_mutex_unlock(&my_obj->cam_lock);
            rc = 0;
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    } else if (aux_handle){
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_get_session_id(aux_handle, sessionid, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_set_dual_cam_cmd
 *
 * DESCRIPTION: retrieve the session ID from the kernel for this HWI instance
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @related_cam_info: pointer to the related cam info to be sent to the server
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              -1 -- failure
 * NOTE       : if this call succeeds, we will get linking established in back end
 *==========================================================================*/
static int32_t mm_camera_intf_set_dual_cam_cmd(uint32_t camera_handle)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t handle = get_main_camera_handle(camera_handle);
    uint32_t aux_handle = get_aux_camera_handle(camera_handle);

    if (handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_set_dual_cam_cmd(my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (aux_handle) {
        pthread_mutex_lock(&g_intf_lock);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_set_dual_cam_cmd(
                    aux_handle, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : get_sensor_info
 *
 * DESCRIPTION: get sensor info like facing(back/front) and mount angle
 *
 * PARAMETERS :
 *
 * RETURN     :
 *==========================================================================*/
void get_sensor_info()
{
    int rc = 0;
    int dev_fd = -1;
    struct media_device_info mdev_info;
    int num_media_devices = 0;
    size_t num_cameras = 0;

    LOGD("E");
    while (1) {
        char dev_name[32];
        snprintf(dev_name, sizeof(dev_name), "/dev/media%d", num_media_devices);
        dev_fd = open(dev_name, O_RDWR | O_NONBLOCK);
        if (dev_fd < 0) {
            LOGD("Done discovering media devices\n");
            break;
        }
        num_media_devices++;
        memset(&mdev_info, 0, sizeof(mdev_info));
        rc = ioctl(dev_fd, MEDIA_IOC_DEVICE_INFO, &mdev_info);
        if (rc < 0) {
            LOGE("Error: ioctl media_dev failed: %s\n", strerror(errno));
            close(dev_fd);
            dev_fd = -1;
            num_cameras = 0;
            break;
        }

        if(strncmp(mdev_info.model,  MSM_CONFIGURATION_NAME, sizeof(mdev_info.model)) != 0) {
            close(dev_fd);
            dev_fd = -1;
            continue;
        }

        unsigned int num_entities = 1;
        while (1) {
            struct media_entity_desc entity;
            uint32_t temp;
            uint32_t mount_angle;
            uint32_t facing;
            int32_t type = 0;
            uint8_t is_yuv;
            uint8_t is_secure;

            memset(&entity, 0, sizeof(entity));
            entity.id = num_entities++;
            rc = ioctl(dev_fd, MEDIA_IOC_ENUM_ENTITIES, &entity);
            if (rc < 0) {
                LOGD("Done enumerating media entities\n");
                rc = 0;
                break;
            }
            if(entity.type == MEDIA_ENT_T_V4L2_SUBDEV &&
                entity.group_id == MSM_CAMERA_SUBDEV_SENSOR) {
                temp = entity.flags >> 8;
                mount_angle = (temp & 0xFF) * 90;
                facing = ((entity.flags & CAM_SENSOR_FACING_MASK) ?
                        CAMERA_FACING_FRONT:CAMERA_FACING_BACK);

                if (entity.flags & CAM_SENSOR_TYPE_MASK) {
                    type = CAM_TYPE_AUX;
                } else {
                    type = CAM_TYPE_MAIN;
                }

                is_yuv = ((entity.flags & CAM_SENSOR_FORMAT_MASK) ?
                        CAM_SENSOR_YUV:CAM_SENSOR_RAW);
                is_secure = ((entity.flags & CAM_SENSOR_SECURE_MASK) ?
                        CAM_TYPE_SECURE:0);
                LOGL("index = %u flag = %x mount_angle = %u "
                        "facing = %u type: %u is_yuv = %u\n",
                        (unsigned int)num_cameras, (unsigned int)temp,
                        (unsigned int)mount_angle, (unsigned int)facing,
                        (unsigned int)type, (uint8_t)is_yuv);
                g_cam_ctrl.info[num_cameras].facing = (int)facing;
                g_cam_ctrl.info[num_cameras].orientation = (int)mount_angle;
                g_cam_ctrl.cam_type[num_cameras] = type | is_secure;
                g_cam_ctrl.is_yuv[num_cameras] = is_yuv;
                LOGD("dev_info[id=%zu,name='%s', facing = %d, angle = %d type = %d]\n",
                         num_cameras, g_cam_ctrl.video_dev_name[num_cameras],
                         g_cam_ctrl.info[num_cameras].facing,
                         g_cam_ctrl.info[num_cameras].orientation,
                         g_cam_ctrl.cam_type[num_cameras]);
                num_cameras++;
                continue;
            }
        }
        close(dev_fd);
        dev_fd = -1;
    }

    LOGD("num_cameras=%d\n", g_cam_ctrl.num_cam);
    return;
}

/*===========================================================================
 * FUNCTION   : sort_camera_info
 *
 * DESCRIPTION: sort camera info to keep back cameras idx is smaller than front cameras idx
 *
 * PARAMETERS : number of cameras
 *
 * RETURN     :
 *==========================================================================*/
void sort_camera_info(int num_cam)
{
    int idx = 0, i;
    int8_t is_secure = 0;
    struct camera_info temp_info[MM_CAMERA_MAX_NUM_SENSORS];
    cam_sync_type_t temp_type[MM_CAMERA_MAX_NUM_SENSORS];
    cam_sync_mode_t temp_mode[MM_CAMERA_MAX_NUM_SENSORS];
    uint8_t temp_is_yuv[MM_CAMERA_MAX_NUM_SENSORS];
    char temp_dev_name[MM_CAMERA_MAX_NUM_SENSORS][MM_CAMERA_DEV_NAME_LEN];
    uint32_t cam_idx[MM_CAMERA_MAX_NUM_SENSORS] = {0};
    uint8_t b_prime_idx = 0, b_aux_idx = 0, f_prime_idx = 0, f_aux_idx = 0;
    int8_t expose_aux = 0;
    char prop[PROPERTY_VALUE_MAX];

    memset(temp_info, 0, sizeof(temp_info));
    memset(temp_dev_name, 0, sizeof(temp_dev_name));
    memset(temp_type, 0, sizeof(temp_type));
    memset(temp_mode, 0, sizeof(temp_mode));
    memset(temp_is_yuv, 0, sizeof(temp_is_yuv));

    memset(prop, 0, sizeof(prop));
    property_get("persist.camera.expose.aux", prop, "0");
    expose_aux = atoi(prop);

    /* Order of the camera exposed is
        0  - Back Main Camera
        1  - Front Main Camera
        ++  - Back Aux Camera
        ++  - Front Aux Camera
        ++  - Back Main + Back Aux camera
        ++  - Front Main + Front Aux camera
        ++  - Secure Camera
       */
    for (i = 0; i < num_cam; i++) {
        if ((g_cam_ctrl.info[i].facing == CAMERA_FACING_BACK) &&
            (g_cam_ctrl.cam_type[i] == CAM_TYPE_MAIN)) {
            temp_info[idx] = g_cam_ctrl.info[i];
            temp_type[idx] = CAM_TYPE_MAIN;
            temp_mode[idx] = g_cam_ctrl.cam_mode[i];
            temp_is_yuv[idx] = g_cam_ctrl.is_yuv[i];
            cam_idx[idx] = idx;
            b_prime_idx = idx;
            LOGH("Found Back Main Camera: i: %d idx: %d", i, idx);
            memcpy(temp_dev_name[idx],g_cam_ctrl.video_dev_name[i],
                MM_CAMERA_DEV_NAME_LEN);
            idx++;
        }
    }

    for (i = 0; i < num_cam; i++) {
        if ((g_cam_ctrl.info[i].facing == CAMERA_FACING_FRONT) &&
            (g_cam_ctrl.cam_type[i] == CAM_TYPE_MAIN)) {
            temp_info[idx] = g_cam_ctrl.info[i];
            temp_type[idx] = CAM_TYPE_MAIN;
            temp_mode[idx] = g_cam_ctrl.cam_mode[i];
            temp_is_yuv[idx] = g_cam_ctrl.is_yuv[i];
            cam_idx[idx] = idx;
            f_prime_idx = idx;
            LOGH("Found Front Main Camera: i: %d idx: %d", i, idx);
            memcpy(temp_dev_name[idx],g_cam_ctrl.video_dev_name[i],
                MM_CAMERA_DEV_NAME_LEN);
            idx++;
        }
    }

    for (i = 0; i < num_cam; i++) {
        if ((g_cam_ctrl.info[i].facing == CAMERA_FACING_BACK) &&
            (g_cam_ctrl.cam_type[i] & CAM_TYPE_AUX)
            && expose_aux) {
            temp_info[idx] = g_cam_ctrl.info[i];
            temp_type[idx] = CAM_TYPE_MAIN;
            temp_mode[idx] = g_cam_ctrl.cam_mode[i];
            temp_is_yuv[idx] = g_cam_ctrl.is_yuv[i];
            cam_idx[idx] = idx;
            b_aux_idx = idx;
            LOGH("Found Back Aux Camera: i: %d idx: %d", i, idx);
            memcpy(temp_dev_name[idx],g_cam_ctrl.video_dev_name[i],
                MM_CAMERA_DEV_NAME_LEN);
            idx++;
        }
    }

    for (i = 0; i < num_cam; i++) {
        if ((g_cam_ctrl.info[i].facing == CAMERA_FACING_FRONT) &&
            (g_cam_ctrl.cam_type[i] & CAM_TYPE_AUX)
            && expose_aux) {
            temp_info[idx] = g_cam_ctrl.info[i];
            temp_type[idx] = CAM_TYPE_MAIN;
            temp_mode[idx] = g_cam_ctrl.cam_mode[i];
            temp_is_yuv[idx] = g_cam_ctrl.is_yuv[i];
            cam_idx[idx] = idx;
            f_aux_idx = idx;
            LOGH("Found front Aux Camera: i: %d idx: %d", i, idx);
            memcpy(temp_dev_name[idx],g_cam_ctrl.video_dev_name[i],
                MM_CAMERA_DEV_NAME_LEN);
            idx++;
        }
    }

    for (i = 0; i < num_cam; i++) {
        if ((g_cam_ctrl.info[i].facing == CAMERA_FACING_BACK) &&
            (g_cam_ctrl.cam_type[i] & CAM_TYPE_AUX)
            && expose_aux) { // Need Main check here after sensor change
            temp_info[idx] = g_cam_ctrl.info[i];
            temp_type[idx] = CAM_TYPE_MAIN | CAM_TYPE_AUX;
            temp_mode[idx] = g_cam_ctrl.cam_mode[i];
            temp_is_yuv[idx] = g_cam_ctrl.is_yuv[i];
            cam_idx[idx] = (b_aux_idx << MM_CAMERA_HANDLE_SHIFT_MASK) | b_prime_idx;
            LOGH("Found Back Main+AUX Camera: i: %d idx: %d", i, idx);
            memcpy(temp_dev_name[idx],g_cam_ctrl.video_dev_name[i],
                MM_CAMERA_DEV_NAME_LEN);
            idx++;
        }
    }

    for (i = 0; i < num_cam; i++) {
        if ((g_cam_ctrl.info[i].facing == CAMERA_FACING_FRONT) &&
            (g_cam_ctrl.cam_type[i] & CAM_TYPE_AUX)
            &&expose_aux) { // Need Main check here after sensor change
            temp_info[idx] = g_cam_ctrl.info[i];
            temp_type[idx] = CAM_TYPE_MAIN | CAM_TYPE_AUX;
            temp_mode[idx] = g_cam_ctrl.cam_mode[i];
            temp_is_yuv[idx] = g_cam_ctrl.is_yuv[i];
            cam_idx[idx] = (f_aux_idx << MM_CAMERA_HANDLE_SHIFT_MASK) | f_prime_idx;
            LOGH("Found Back Main Camera: i: %d idx: %d", i, idx);
            memcpy(temp_dev_name[idx],g_cam_ctrl.video_dev_name[i],
                MM_CAMERA_DEV_NAME_LEN);
            idx++;
        }
    }

   /*secure camera*/
   for (i = 0; i < num_cam; i++) {
       if (g_cam_ctrl.cam_type[i] & CAM_TYPE_SECURE) {
           temp_info[idx] = g_cam_ctrl.info[i];
           temp_type[idx] = g_cam_ctrl.cam_type[i];
           temp_mode[idx] = g_cam_ctrl.cam_mode[i];
           temp_is_yuv[idx] = g_cam_ctrl.is_yuv[i];
           LOGD("Found Secure Camera: i: %d idx: %d", i, idx);
           memcpy(temp_dev_name[idx++],g_cam_ctrl.video_dev_name[i],
               MM_CAMERA_DEV_NAME_LEN);
           is_secure++;
       }
   }

    /*NOTE: Add logic here to modify cameraID again here*/

    if (idx != 0) {
        memcpy(g_cam_ctrl.info, temp_info, sizeof(temp_info));
        memcpy(g_cam_ctrl.cam_type, temp_type, sizeof(temp_type));
        memcpy(g_cam_ctrl.cam_mode, temp_mode, sizeof(temp_mode));
        memcpy(g_cam_ctrl.is_yuv, temp_is_yuv, sizeof(temp_is_yuv));
        memcpy(g_cam_ctrl.video_dev_name, temp_dev_name, sizeof(temp_dev_name));
        memcpy(g_cam_ctrl.cam_index, cam_idx, (sizeof(uint32_t) * MM_CAMERA_MAX_NUM_SENSORS));
        //Set num cam based on the cameras exposed finally via dual/aux properties.
        g_cam_ctrl.num_cam = idx;
        for (i = 0; i < idx; i++) {
            LOGI("Camera id: %d facing: %d, type: %d is_yuv: %d",
                i, g_cam_ctrl.info[i].facing, g_cam_ctrl.cam_type[i], g_cam_ctrl.is_yuv[i]);
        }

        //control camera exposing here.
        g_cam_ctrl.num_cam_to_expose = g_cam_ctrl.num_cam - is_secure;
    }
    LOGI("Number of cameras %d sorted %d", num_cam, idx);
    return;
}

/*===========================================================================
 * FUNCTION   : get_num_of_cameras
 *
 * DESCRIPTION: get number of cameras
 *
 * PARAMETERS :
 *
 * RETURN     : number of cameras supported
 *==========================================================================*/
uint8_t get_num_of_cameras()
{
    int rc = 0;
    int dev_fd = -1;
    struct media_device_info mdev_info;
    int num_media_devices = 0;
    int8_t num_cameras = 0;
    char subdev_name[32];
    char prop[PROPERTY_VALUE_MAX];
#ifdef DAEMON_PRESENT
    int32_t sd_fd = -1;
    struct sensor_init_cfg_data cfg;
#endif

    LOGD("E");

    property_get("vold.decrypt", prop, "0");
    int decrypt = atoi(prop);
    if (decrypt == 1)
     return 0;
    pthread_mutex_lock(&g_intf_lock);

    memset (&g_cam_ctrl, 0, sizeof (g_cam_ctrl));
#ifndef DAEMON_PRESENT
    if (mm_camera_load_shim_lib() < 0) {
        LOGE ("Failed to module shim library");
        return 0;
    }
#endif /* DAEMON_PRESENT */

    while (1) {
        uint32_t num_entities = 1U;
        char dev_name[32];

        snprintf(dev_name, sizeof(dev_name), "/dev/media%d", num_media_devices);
        dev_fd = open(dev_name, O_RDWR | O_NONBLOCK);
        if (dev_fd < 0) {
            LOGD("Done discovering media devices\n");
            break;
        }
        num_media_devices++;
        rc = ioctl(dev_fd, MEDIA_IOC_DEVICE_INFO, &mdev_info);
        if (rc < 0) {
            LOGE("Error: ioctl media_dev failed: %s\n", strerror(errno));
            close(dev_fd);
            dev_fd = -1;
            break;
        }

        if (strncmp(mdev_info.model, MSM_CONFIGURATION_NAME,
          sizeof(mdev_info.model)) != 0) {
            close(dev_fd);
            dev_fd = -1;
            continue;
        }

        while (1) {
            struct media_entity_desc entity;
            memset(&entity, 0, sizeof(entity));
            entity.id = num_entities++;
            LOGD("entity id %d", entity.id);
            rc = ioctl(dev_fd, MEDIA_IOC_ENUM_ENTITIES, &entity);
            if (rc < 0) {
                LOGD("Done enumerating media entities");
                rc = 0;
                break;
            }
            LOGD("entity name %s type %d group id %d",
                entity.name, entity.type, entity.group_id);
            if (entity.type == MEDIA_ENT_T_V4L2_SUBDEV &&
                entity.group_id == MSM_CAMERA_SUBDEV_SENSOR_INIT) {
                snprintf(subdev_name, sizeof(dev_name), "/dev/%s", entity.name);
                break;
            }
        }
        close(dev_fd);
        dev_fd = -1;
    }

#ifdef DAEMON_PRESENT
    /* Open sensor_init subdev */
    sd_fd = open(subdev_name, O_RDWR);
    if (sd_fd < 0) {
        LOGE("Open sensor_init subdev failed");
        return FALSE;
    }

    cfg.cfgtype = CFG_SINIT_PROBE_WAIT_DONE;
    cfg.cfg.setting = NULL;
    if (ioctl(sd_fd, VIDIOC_MSM_SENSOR_INIT_CFG, &cfg) < 0) {
        LOGE("failed");
    }
    close(sd_fd);
#endif

    num_media_devices = 0;
    while (1) {
        uint32_t num_entities = 1U;
        char dev_name[32];

        snprintf(dev_name, sizeof(dev_name), "/dev/media%d", num_media_devices);
        dev_fd = open(dev_name, O_RDWR | O_NONBLOCK);
        if (dev_fd < 0) {
            LOGD("Done discovering media devices: %s\n", strerror(errno));
            break;
        }
        num_media_devices++;
        memset(&mdev_info, 0, sizeof(mdev_info));
        rc = ioctl(dev_fd, MEDIA_IOC_DEVICE_INFO, &mdev_info);
        if (rc < 0) {
            LOGE("Error: ioctl media_dev failed: %s\n", strerror(errno));
            close(dev_fd);
            dev_fd = -1;
            num_cameras = 0;
            break;
        }

        if(strncmp(mdev_info.model, MSM_CAMERA_NAME, sizeof(mdev_info.model)) != 0) {
            close(dev_fd);
            dev_fd = -1;
            continue;
        }

        while (1) {
            struct media_entity_desc entity;
            memset(&entity, 0, sizeof(entity));
            entity.id = num_entities++;
            rc = ioctl(dev_fd, MEDIA_IOC_ENUM_ENTITIES, &entity);
            if (rc < 0) {
                LOGD("Done enumerating media entities\n");
                rc = 0;
                break;
            }
            if(entity.type == MEDIA_ENT_T_DEVNODE_V4L && entity.group_id == QCAMERA_VNODE_GROUP_ID) {
                strlcpy(g_cam_ctrl.video_dev_name[num_cameras],
                     entity.name, sizeof(entity.name));
                LOGI("dev_info[id=%d,name='%s']\n",
                    (int)num_cameras, g_cam_ctrl.video_dev_name[num_cameras]);
                num_cameras++;
                break;
            }
        }
        close(dev_fd);
        dev_fd = -1;
        if (num_cameras >= MM_CAMERA_MAX_NUM_SENSORS) {
            LOGW("Maximum number of camera reached %d", num_cameras);
            break;
        }
    }
    g_cam_ctrl.num_cam = num_cameras;

    get_sensor_info();
    sort_camera_info(g_cam_ctrl.num_cam);
    /* unlock the mutex */
    pthread_mutex_unlock(&g_intf_lock);
    LOGI("num_cameras=%d\n", (int)g_cam_ctrl.num_cam);
    return(uint8_t)g_cam_ctrl.num_cam;
}

/*===========================================================================
 * FUNCTION   : get_num_of_cameras_to_expose
 *
 * DESCRIPTION: get number of cameras to expose
 *
 * PARAMETERS :
 *
 * RETURN     : number of cameras to expose to application
 *==========================================================================*/
uint8_t get_num_of_cameras_to_expose()
{
    if (g_cam_ctrl.num_cam == 0) {
        get_num_of_cameras();
    }
    return g_cam_ctrl.num_cam_to_expose;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_process_advanced_capture
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
static int32_t mm_camera_intf_process_advanced_capture(uint32_t camera_handle,
        uint32_t ch_id, mm_camera_advanced_capture_t type,
        int8_t trigger, void *in_value)
{
    int32_t rc = -1;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t chid = get_main_camera_handle(ch_id);
    uint32_t aux_chid = get_aux_camera_handle(ch_id);

    LOGD("E camera_handler = %d,ch_id = %d",
          camera_handle, ch_id);

    if (chid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_channel_advanced_capture(my_obj, chid, type,
                    (uint32_t)trigger, in_value);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (aux_chid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        my_obj = mm_camera_util_get_camera_head(aux_handle);
        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_process_advanced_capture(aux_handle,
                    aux_chid, type, (uint32_t)trigger, in_value, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    LOGH("X rc = %d ch_id = %u", rc, ch_id);
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_register_stream_buf_cb
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
static int32_t mm_camera_intf_register_stream_buf_cb(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id, mm_camera_buf_notify_t buf_cb,
        mm_camera_stream_cb_type cb_type, void *userdata)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;
    uint32_t strid = get_main_camera_handle(stream_id);
    uint32_t aux_strid = get_aux_camera_handle(stream_id);

    LOGD("E handle = %u ch_id = %u",
          camera_handle, ch_id);

    if (strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t handle = get_main_camera_handle(camera_handle);
        uint32_t chid = get_main_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_by_handler(handle);

        if(my_obj) {
            pthread_mutex_lock(&my_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_reg_stream_buf_cb(my_obj, chid, strid,
                    buf_cb, cb_type, userdata);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }

    if (aux_strid) {
        pthread_mutex_lock(&g_intf_lock);
        uint32_t aux_handle = get_aux_camera_handle(camera_handle);
        uint32_t aux_chid = get_aux_camera_handle(ch_id);
        my_obj = mm_camera_util_get_camera_head(aux_handle);

        if (my_obj) {
            pthread_mutex_lock(&my_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_register_stream_buf_cb(aux_handle,
                    aux_chid, aux_strid,
                    buf_cb, cb_type, userdata, my_obj);
        } else {
            pthread_mutex_unlock(&g_intf_lock);
        }
    }
    return (int32_t)rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_register_frame_sync
 *
 * DESCRIPTION: start frame buffer sync for the stream
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @stream_id    : stream handle
 *   @sync_attr     : frame sync attr
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              1 -- failure
 *==========================================================================*/
static int32_t mm_camera_intf_reg_frame_sync(uint32_t camera_handle,
            uint32_t ch_id, uint32_t stream_id,
            mm_camera_intf_frame_sync_t *sync_attr)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;

    LOGD("E handle = %u ch_id = %u stream_id = %u", camera_handle, ch_id, stream_id);

    pthread_mutex_lock(&g_intf_lock);
    uint32_t handle = get_main_camera_handle(camera_handle);
    my_obj = mm_camera_util_get_camera_by_handler(handle);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->muxer_lock);
        pthread_mutex_unlock(&g_intf_lock);
        rc = mm_camera_muxer_reg_frame_sync(my_obj,
                 ch_id, stream_id, sync_attr);
    } else {
        pthread_mutex_unlock(&g_intf_lock);
    }
    return (int32_t)rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_intf_handle_frame_sync_cb
 *
 * DESCRIPTION: Handle callback request type incase of frame sync mode
 *
 * PARAMETERS :
 *   @camera_handle: camera handle
 *   @ch_id        : channel handle
 *   @stream_id    : stream handle
 *   @req_type    : callback request type
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              1 -- failure
 *==========================================================================*/
static int32_t mm_camera_intf_handle_frame_sync_cb(uint32_t camera_handle,
        uint32_t ch_id, uint32_t stream_id, mm_camera_cb_req_type req_type)
{
    int32_t rc = 0;
    mm_camera_obj_t * my_obj = NULL;

    uint32_t handle = get_main_camera_handle(camera_handle);
    uint32_t m_chid = get_main_camera_handle(ch_id);
    uint32_t m_strid = get_main_camera_handle(stream_id);
    LOGD("E handle = %u ch_id = %u stream_id = %u",
            camera_handle, ch_id, stream_id);

    pthread_mutex_lock(&g_intf_lock);
    my_obj = mm_camera_util_get_camera_by_handler(handle);
    if(my_obj) {
        pthread_mutex_lock(&my_obj->cam_lock);
        pthread_mutex_unlock(&g_intf_lock);
        rc = mm_camera_handle_frame_sync_cb(my_obj, m_chid, m_strid, req_type);
    } else {
        pthread_mutex_unlock(&g_intf_lock);
    }
    LOGH("stream_id = %u rc = %d", stream_id, rc);
    return (int32_t)rc;
}

struct camera_info *get_cam_info(uint32_t camera_id, cam_sync_type_t *pCamType)
{
    *pCamType = g_cam_ctrl.cam_type[camera_id];
    return &g_cam_ctrl.info[camera_id];
}

uint8_t is_dual_camera_by_idx(uint32_t camera_id)
{
    return ((g_cam_ctrl.cam_type[camera_id] & CAM_TYPE_MAIN)
            && (g_cam_ctrl.cam_type[camera_id] & CAM_TYPE_AUX));
}

uint8_t is_dual_camera_by_handle(uint32_t handle)
{
    return ((handle >> MM_CAMERA_HANDLE_SHIFT_MASK) &&
            (handle & (MM_CAMERA_HANDLE_BIT_MASK)) ? 1 : 0);
}

uint32_t get_aux_camera_handle(uint32_t handle)
{
    return mm_camera_util_get_handle_by_num(1, handle);
}

uint32_t get_main_camera_handle(uint32_t handle)
{
    return mm_camera_util_get_handle_by_num(0, handle);
}

cam_sync_type_t get_cam_type(uint32_t camera_id)
{
    return  g_cam_ctrl.cam_type[camera_id];
}

uint8_t is_yuv_sensor(uint32_t camera_id)
{
    return g_cam_ctrl.is_yuv[camera_id];
}

uint8_t validate_handle(uint32_t src_handle, uint32_t handle)
{
    if ((src_handle == 0) || (handle == 0)) {
        return 0;
    }
    return ((src_handle == handle)
            || (get_main_camera_handle(src_handle) == handle)
            || (get_aux_camera_handle(src_handle) == handle)
            || (get_main_camera_handle(handle) == src_handle)
            || (get_aux_camera_handle(handle) == src_handle));
}

/* camera ops v-table */
static mm_camera_ops_t mm_camera_ops = {
    .query_capability = mm_camera_intf_query_capability,
    .register_event_notify = mm_camera_intf_register_event_notify,
    .close_camera = mm_camera_intf_close,
    .set_parms = mm_camera_intf_set_parms,
    .get_parms = mm_camera_intf_get_parms,
    .do_auto_focus = mm_camera_intf_do_auto_focus,
    .cancel_auto_focus = mm_camera_intf_cancel_auto_focus,
    .prepare_snapshot = mm_camera_intf_prepare_snapshot,
    .start_zsl_snapshot = mm_camera_intf_start_zsl_snapshot,
    .stop_zsl_snapshot = mm_camera_intf_stop_zsl_snapshot,
    .map_buf = mm_camera_intf_map_buf,
    .map_bufs = mm_camera_intf_map_bufs,
    .unmap_buf = mm_camera_intf_unmap_buf,
    .add_channel = mm_camera_intf_add_channel,
    .delete_channel = mm_camera_intf_del_channel,
    .get_bundle_info = mm_camera_intf_get_bundle_info,
    .add_stream = mm_camera_intf_add_stream,
    .link_stream = mm_camera_intf_link_stream,
    .delete_stream = mm_camera_intf_del_stream,
    .config_stream = mm_camera_intf_config_stream,
    .qbuf = mm_camera_intf_qbuf,
    .cancel_buffer = mm_camera_intf_cancel_buf,
    .get_queued_buf_count = mm_camera_intf_get_queued_buf_count,
    .map_stream_buf = mm_camera_intf_map_stream_buf,
    .map_stream_bufs = mm_camera_intf_map_stream_bufs,
    .unmap_stream_buf = mm_camera_intf_unmap_stream_buf,
    .set_stream_parms = mm_camera_intf_set_stream_parms,
    .get_stream_parms = mm_camera_intf_get_stream_parms,
    .start_channel = mm_camera_intf_start_channel,
    .start_sensor_streaming = mm_camera_intf_start_sensor_streaming,
    .stop_channel = mm_camera_intf_stop_channel,
    .request_super_buf = mm_camera_intf_request_super_buf,
    .cancel_super_buf_request = mm_camera_intf_cancel_super_buf_request,
    .flush_super_buf_queue = mm_camera_intf_flush_super_buf_queue,
    .configure_notify_mode = mm_camera_intf_configure_notify_mode,
    .process_advanced_capture = mm_camera_intf_process_advanced_capture,
    .get_session_id = mm_camera_intf_get_session_id,
    .set_dual_cam_cmd = mm_camera_intf_set_dual_cam_cmd,
    .flush = mm_camera_intf_flush,
    .register_stream_buf_cb = mm_camera_intf_register_stream_buf_cb,
    .register_frame_sync = mm_camera_intf_reg_frame_sync,
    .handle_frame_sync_cb = mm_camera_intf_handle_frame_sync_cb
};

/*===========================================================================
 * FUNCTION   : camera_open
 *
 * DESCRIPTION: open a camera by camera index
 *
 * PARAMETERS :
 *   @camera_idx  : camera index. should within range of 0 to num_of_cameras
 *   @camera_vtbl : ptr to a virtual table containing camera handle and operation table.
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              non-zero error code -- failure
 *==========================================================================*/
int32_t camera_open(uint8_t camera_idx, mm_camera_vtbl_t **camera_vtbl)
{
    int32_t rc = 0;
    mm_camera_obj_t *cam_obj = NULL;
    uint32_t cam_idx = camera_idx;
    uint32_t aux_idx = 0;
    uint8_t is_multi_camera = 0;

#ifdef QCAMERA_REDEFINE_LOG
    mm_camera_debug_open();
#endif

    LOGD("E camera_idx = %d\n", camera_idx);
    if (is_dual_camera_by_idx(camera_idx)) {
        is_multi_camera = 1;
        cam_idx = mm_camera_util_get_handle_by_num(0,
                g_cam_ctrl.cam_index[camera_idx]);
        aux_idx = (get_aux_camera_handle(g_cam_ctrl.cam_index[camera_idx])
                >> MM_CAMERA_HANDLE_SHIFT_MASK);
        LOGH("Dual Camera: Main ID = %d Aux ID = %d", cam_idx, aux_idx);
    }

    if (cam_idx >= (uint32_t)g_cam_ctrl.num_cam || cam_idx >=
        MM_CAMERA_MAX_NUM_SENSORS || aux_idx >= MM_CAMERA_MAX_NUM_SENSORS) {
        LOGE("Invalid camera_idx (%d)", cam_idx);
        return -EINVAL;
    }

    pthread_mutex_lock(&g_intf_lock);
    /* opened already */
    if(NULL != g_cam_ctrl.cam_obj[cam_idx] &&
            g_cam_ctrl.cam_obj[cam_idx]->ref_count != 0) {
        pthread_mutex_unlock(&g_intf_lock);
        LOGE("Camera %d is already open", cam_idx);
        return -EBUSY;
    }

    cam_obj = (mm_camera_obj_t *)malloc(sizeof(mm_camera_obj_t));
    if(NULL == cam_obj) {
        pthread_mutex_unlock(&g_intf_lock);
        LOGE("no mem");
        return -EINVAL;
    }

    /* initialize camera obj */
    memset(cam_obj, 0, sizeof(mm_camera_obj_t));
    cam_obj->ctrl_fd = -1;
    cam_obj->ds_fd = -1;
    cam_obj->ref_count++;
    cam_obj->my_num = 0;
    cam_obj->my_hdl = mm_camera_util_generate_handler(cam_idx);
    cam_obj->vtbl.camera_handle = cam_obj->my_hdl; /* set handler */
    cam_obj->vtbl.ops = &mm_camera_ops;
    pthread_mutex_init(&cam_obj->cam_lock, NULL);
    pthread_mutex_init(&cam_obj->muxer_lock, NULL);
    /* unlock global interface lock, if not, in dual camera use case,
      * current open will block operation of another opened camera obj*/
    pthread_mutex_lock(&cam_obj->cam_lock);
    pthread_mutex_unlock(&g_intf_lock);

    rc = mm_camera_open(cam_obj);
    if (rc != 0) {
        LOGE("mm_camera_open err = %d", rc);
        pthread_mutex_destroy(&cam_obj->cam_lock);
        pthread_mutex_lock(&g_intf_lock);
        g_cam_ctrl.cam_obj[cam_idx] = NULL;
        free(cam_obj);
        cam_obj = NULL;
        pthread_mutex_unlock(&g_intf_lock);
        *camera_vtbl = NULL;
        return rc;
    }

    if (is_multi_camera) {
        /*Open Aux camer's*/
        pthread_mutex_lock(&g_intf_lock);
        if(NULL != g_cam_ctrl.cam_obj[aux_idx] &&
                g_cam_ctrl.cam_obj[aux_idx]->ref_count != 0) {
            pthread_mutex_unlock(&g_intf_lock);
            LOGE("Camera %d is already open", aux_idx);
            rc = -EBUSY;
        } else {
            pthread_mutex_lock(&cam_obj->muxer_lock);
            pthread_mutex_unlock(&g_intf_lock);
            rc = mm_camera_muxer_camera_open(aux_idx, cam_obj);
        }
        if (rc != 0) {
            int32_t temp_rc = 0;
            LOGE("muxer open err = %d", rc);
            pthread_mutex_lock(&g_intf_lock);
            g_cam_ctrl.cam_obj[cam_idx] = NULL;
            pthread_mutex_lock(&cam_obj->cam_lock);
            pthread_mutex_unlock(&g_intf_lock);
            temp_rc = mm_camera_close(cam_obj);
            pthread_mutex_destroy(&cam_obj->cam_lock);
            pthread_mutex_destroy(&cam_obj->muxer_lock);
            free(cam_obj);
            cam_obj = NULL;
            *camera_vtbl = NULL;
            // Propagate the original error to caller
            return rc;
        }
    }

    LOGH("Open succeded: handle = %d", cam_obj->vtbl.camera_handle);
    g_cam_ctrl.cam_obj[cam_idx] = cam_obj;
    *camera_vtbl = &cam_obj->vtbl;
    return 0;
}

/*===========================================================================
 * FUNCTION   : mm_camera_load_shim_lib
 *
 * DESCRIPTION: Load shim layer library
 *
 * PARAMETERS :
 *
 * RETURN     : status of load shim library
 *==========================================================================*/
int32_t mm_camera_load_shim_lib()
{
    const char* error = NULL;
    void *qdaemon_lib = NULL;

    LOGD("E");
    qdaemon_lib = dlopen(SHIMLAYER_LIB, RTLD_NOW);
    if (!qdaemon_lib) {
        error = dlerror();
        LOGE("dlopen failed with error %s", error ? error : "");
        return -1;
    }

    *(void **)&mm_camera_shim_module_init =
            dlsym(qdaemon_lib, "mct_shimlayer_process_module_init");
    if (!mm_camera_shim_module_init) {
        error = dlerror();
        LOGE("dlsym failed with error code %s", error ? error: "");
        dlclose(qdaemon_lib);
        return -1;
    }

    return mm_camera_shim_module_init(&g_cam_ctrl.cam_shim_ops);
}

/*===========================================================================
 * FUNCTION   : mm_camera_module_open_session
 *
 * DESCRIPTION: wrapper function to call shim layer API to open session.
 *
 * PARAMETERS :
 *   @sessionid  : sessionID to open session
 *   @evt_cb     : Event callback function
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              non-zero error code -- failure
 *==========================================================================*/
cam_status_t mm_camera_module_open_session(int sessionid,
        mm_camera_shim_event_handler_func evt_cb)
{
    cam_status_t rc = -1;
    if(g_cam_ctrl.cam_shim_ops.mm_camera_shim_open_session) {
        rc = g_cam_ctrl.cam_shim_ops.mm_camera_shim_open_session(
                sessionid, evt_cb);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_module_close_session
 *
 * DESCRIPTION: wrapper function to call shim layer API to close session
 *
 * PARAMETERS :
 *   @sessionid  : sessionID to open session
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              non-zero error code -- failure
 *==========================================================================*/
int32_t mm_camera_module_close_session(int session)
{
    int32_t rc = -1;
    if(g_cam_ctrl.cam_shim_ops.mm_camera_shim_close_session) {
        rc = g_cam_ctrl.cam_shim_ops.mm_camera_shim_close_session(session);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_module_open_session
 *
 * DESCRIPTION: wrapper function to call shim layer API
 *
 * PARAMETERS :
 *   @sessionid  : sessionID to open session
 *   @evt_cb     : Event callback function
 *
 * RETURN     : int32_t type of status
 *              0  -- success
 *              non-zero error code -- failure
 *==========================================================================*/
int32_t mm_camera_module_send_cmd(cam_shim_packet_t *event)
{
    int32_t rc = -1;
    if(g_cam_ctrl.cam_shim_ops.mm_camera_shim_send_cmd) {
        rc = g_cam_ctrl.cam_shim_ops.mm_camera_shim_send_cmd(event);
    }
    return rc;
}

/*===========================================================================
 * FUNCTION   : mm_camera_module_event_handler
 *
 * DESCRIPTION: call back function for shim layer
 *
 * PARAMETERS :
 *
 * RETURN     : status of call back function
 *==========================================================================*/
int mm_camera_module_event_handler(uint32_t session_id, cam_event_t *event)
{
    if (!event) {
        LOGE("null event");
        return FALSE;
    }
    mm_camera_event_t evt;

    LOGD("session_id:%d, cmd:0x%x", session_id, event->server_event_type);
    memset(&evt, 0, sizeof(mm_camera_event_t));

    evt = *event;
    mm_camera_obj_t *my_obj =
         mm_camera_util_get_camera_by_session_id(session_id);
    if (!my_obj) {
        LOGE("my_obj:%p", my_obj);
        return FALSE;
    }
    switch( evt.server_event_type) {
       case CAM_EVENT_TYPE_DAEMON_PULL_REQ:
       case CAM_EVENT_TYPE_CAC_DONE:
       case CAM_EVENT_TYPE_DAEMON_DIED:
       case CAM_EVENT_TYPE_INT_TAKE_JPEG:
       case CAM_EVENT_TYPE_INT_TAKE_RAW:
           mm_camera_enqueue_evt(my_obj, &evt);
           break;
       default:
           LOGE("cmd:%x from shim layer is not handled", evt.server_event_type);
           break;
   }
   return TRUE;
}

