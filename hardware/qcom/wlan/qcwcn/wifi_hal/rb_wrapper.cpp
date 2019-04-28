/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
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
 */

#include <stdint.h>
#include <stdlib.h>

#include "sync.h"
#include "wifi_hal.h"
#include "common.h"

#include "ring_buffer.h"
#include "rb_wrapper.h"

#define LOG_TAG  "WifiHAL"

wifi_error rb_init(hal_info *info, struct rb_info *rb_info, int id,
                   size_t size_of_buf, int num_bufs, char *name)
{
    rb_info->rb_ctx = ring_buffer_init(size_of_buf, num_bufs);
    if (rb_info->rb_ctx == NULL) {
        ALOGE("Failed to init ring buffer");
        return WIFI_ERROR_OUT_OF_MEMORY;
    }
    strlcpy(rb_info->name, name, MAX_RB_NAME_SIZE);
    rb_info->ctx = info;
    rb_info->id = id;

    /* Initialize last_push_time */
    gettimeofday(&rb_info->last_push_time, NULL);
    return WIFI_SUCCESS;
}

void rb_deinit(struct rb_info *rb_info)
{
    if (rb_info->rb_ctx) {
        ring_buffer_deinit(rb_info->rb_ctx);
        rb_info->rb_ctx = NULL;
    }
    rb_info->name[0] = '\0';
}

void get_rb_status(struct rb_info *rb_info, wifi_ring_buffer_status *rbs)
{
    struct rb_stats rb_stats;

    strlcpy((char *)rbs->name, rb_info->name, MAX_RB_NAME_SIZE);
    rbs->flags = rb_info->flags;
    rbs->ring_id = rb_info->id;
    rbs->verbose_level = rb_info->verbose_level;
    rb_get_stats(rb_info->rb_ctx, &rb_stats);
    rbs->ring_buffer_byte_size = rb_stats.max_num_bufs *
                                 rb_stats.each_buf_size;
    rbs->written_bytes = rb_stats.total_bytes_written;
    rbs->read_bytes = rb_stats.total_bytes_read;
    rbs->written_records = rb_info->written_records;
}

int is_rb_name_match(struct rb_info *rb_info, char *name)
{
    return (strncmp(rb_info->name, name, MAX_RB_NAME_SIZE) == 0);
}

wifi_error ring_buffer_write(struct rb_info *rb_info, u8 *buf, size_t length,
                             int no_of_records, size_t record_length)
{
    enum rb_status status;

    status = rb_write(rb_info->rb_ctx, buf, length, 0, record_length);
    if ((status == RB_FULL) || (status == RB_RETRY)) {
         push_out_rb_data(rb_info);
         /* Try writing the data after reading it out */
        status = rb_write(rb_info->rb_ctx, buf, length, 0, record_length);
        if (status != RB_SUCCESS) {
            ALOGE("Failed to rewrite %zu bytes to rb %s with error %d", length,
                  rb_info->name, status);
            return WIFI_ERROR_UNKNOWN;
        }
    } else if (status == RB_FAILURE) {
        ALOGE("Failed to write %zu bytes to rb %s with error %d", length,
              rb_info->name, status);
        return WIFI_ERROR_UNKNOWN;
    }

    rb_info->written_records += no_of_records;
    return WIFI_SUCCESS;
}

void push_out_rb_data(void *cb_ctx)
{
    struct rb_info *rb_info = (struct rb_info *)cb_ctx;
    hal_info *info = (hal_info *)rb_info->ctx;
    wifi_ring_buffer_status rbs;
    wifi_ring_buffer_data_handler handler;

    while (1) {
        size_t length = 0;
        u8 *buf;

        buf = rb_get_read_buf(rb_info->rb_ctx, &length);
        if (buf == NULL) {
            break;
        }
        get_rb_status(rb_info, &rbs);
        pthread_mutex_lock(&info->lh_lock);
        handler.on_ring_buffer_data = info->on_ring_buffer_data;
        pthread_mutex_unlock(&info->lh_lock);
        if (handler.on_ring_buffer_data) {
            handler.on_ring_buffer_data(rb_info->name, (char *)buf,
                                        length, &rbs);
        }
        free(buf);
    };
    gettimeofday(&rb_info->last_push_time, NULL);
}

wifi_error rb_start_logging(struct rb_info *rb_info, u32 verbose_level,
                            u32 flags, u32 max_interval_sec, u32 min_data_size)
{
    rb_info->verbose_level = verbose_level;
    rb_info->flags = flags;
    rb_info->max_interval_sec = max_interval_sec;

    rb_config_threshold(rb_info->rb_ctx, min_data_size, push_out_rb_data, rb_info);
    return WIFI_SUCCESS;
}

void rb_check_for_timeout(struct rb_info *rb_info, struct timeval *now)
{
    if (rb_info->max_interval_sec == 0) {
        return;
    }
    if (now->tv_sec >=
        (rb_info->last_push_time.tv_sec +
         (__kernel_time_t)rb_info->max_interval_sec)) {
        push_out_rb_data(rb_info);
    }
}
