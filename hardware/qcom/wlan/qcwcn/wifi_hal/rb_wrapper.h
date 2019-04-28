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

#ifndef __RB_WRAPPER_H
#define __RB_WRAPPER_H

#define MAX_RB_NAME_SIZE 32

struct rb_info {
    void *rb_ctx;
    char name[MAX_RB_NAME_SIZE];
    u32 flags;
    u32 verbose_level;
    u32 written_records;
    u32 max_interval_sec;
    int id;
    void *ctx;
    struct timeval last_push_time;
};
struct hal_info_s;
wifi_error rb_init(struct hal_info_s *info, struct rb_info *rb_info, int id,
                   size_t size_of_buf, int num_bufs, char *name);
void rb_deinit(struct rb_info *rb_info);
void get_rb_status(struct rb_info *rb_info, wifi_ring_buffer_status *rbs);
void rb_check_for_timeout(struct rb_info *rb_info, struct timeval *now);
wifi_error rb_start_logging(struct rb_info *rb_info, u32 verbose_level,
                            u32 flags, u32 max_interval_sec, u32 min_data_size);
int is_rb_name_match(struct rb_info *rb_info, char *name);
wifi_error ring_buffer_write(struct rb_info *rb_info, u8 *buf, size_t length,
                             int no_of_records, size_t record_length);
void push_out_rb_data(void *cb_ctx);
#endif /* __RB_WRAPPER_H */
