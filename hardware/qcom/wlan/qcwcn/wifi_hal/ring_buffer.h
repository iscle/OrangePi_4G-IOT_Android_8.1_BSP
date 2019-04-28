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

#ifndef __RING_BUFFER_H
#define __RING_BUFFER_H

/* Ring buffer status codes */
enum rb_status {
    RB_SUCCESS = 0,
    RB_FAILURE = 1,
    RB_FULL = 2,
    RB_RETRY = 3,
};

struct rb_stats {
    u32 total_bytes_written;
    u32 total_bytes_read;
    u32 cur_valid_bytes;
    unsigned int max_num_bufs;
    size_t each_buf_size;
};

typedef void (*threshold_call_back) (void *cb_ctx);

/* intiitalizes the ring buffer and returns the context to it */
void * ring_buffer_init(size_t size_of_buf, int num_bufs);

/* Frees up the mem allocated for this ring buffer operation */
void ring_buffer_deinit(void *ctx);

/* Writes writes length of bytes from buf to ring buffer */
enum rb_status rb_write(void *ctx, u8 *buf, size_t length, int overwrite,
                        size_t record_length);

/* Tries to read max_length of bytes from ring buffer to buf
 * and returns actual length of bytes read from ring buffer
 */
size_t rb_read(void *ctx, u8 *buf, size_t max_length);

/* A buffer with possible maximum of bytes that can be read
 * from a single buffer of ring buffer
 * Ring buffer module looses the ownership of the buffer returned by this api,
 * which means the caller has to make sure to free the buffer returned.
 */
u8 *rb_get_read_buf(void *ctx, size_t *length);

/* calls callback whenever ring_buffer reaches percent percentage of it'ss
 * full size
 */
void rb_config_threshold(void *ctx,
                         unsigned int num_min_bytes,
                         threshold_call_back callback,
                         void *cb_ctx);

/* Get the current status of ring buffer */
void rb_get_stats(void *ctx, struct rb_stats *rbs);

#endif /* __RING_BUFFER_H */
