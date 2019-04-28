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
#include <string.h>
#include <pthread.h>

#define LOG_TAG  "WifiHAL"

#include <utils/Log.h>

typedef unsigned char u8;
typedef uint16_t u16;
typedef uint32_t u32;
typedef uint64_t u64;

#include "ring_buffer.h"

enum rb_bool {
    RB_TRUE = 0,
    RB_FALSE = 1
};

typedef struct rb_entry_s {
    u8 *data;
    unsigned int last_wr_index;
    u8 full;
} rb_entry_t;

typedef struct ring_buf_cb {
    unsigned int rd_buf_no; // Current buffer number to be read from
    unsigned int wr_buf_no; // Current buffer number to be written into
    unsigned int cur_rd_buf_idx; // Read index within the current read buffer
    unsigned int cur_wr_buf_idx; // Write index within the current write buffer
    rb_entry_t *bufs; // Array of buffer pointers

    unsigned int max_num_bufs; // Maximum number of buffers that should be used
    size_t each_buf_size; // Size of each buffer in bytes

    pthread_mutex_t rb_rw_lock;

    /* Threshold vars */
    unsigned int num_min_bytes;
    void (*threshold_cb)(void *);
    void *cb_ctx;

    u32 total_bytes_written;
    u32 total_bytes_read;
    u32 total_bytes_overwritten;
    u32 cur_valid_bytes;
    enum rb_bool threshold_reached;
} rbc_t;


#define RB_MIN(x, y) ((x) < (y)?(x):(y))
inline void rb_lock(pthread_mutex_t *lock)
{
    int error = pthread_mutex_lock(lock);

    if (error)
        ALOGE("Failed to acquire lock with err %d", error);
    // TODO Handle the lock failure
}

inline void rb_unlock(pthread_mutex_t *lock)
{
    int error = pthread_mutex_unlock(lock);

    if (error)
        ALOGE("Failed to release lock with err %d", error);
    // TODO Handle the unlock failure
}

void * ring_buffer_init(size_t size_of_buf, int num_bufs)
{
    struct ring_buf_cb *rbc;
    int status;

    rbc = (struct ring_buf_cb *)malloc(sizeof(struct ring_buf_cb));
    if (rbc == NULL) {
        ALOGE("Failed to alloc rbc");
        return NULL;
    }
    memset(rbc, 0, sizeof(struct ring_buf_cb));

    rbc->bufs = (rb_entry_t *)malloc(num_bufs * sizeof(rb_entry_t));
    if (rbc->bufs == NULL) {
        free(rbc);
        ALOGE("Failed to alloc rbc->bufs");
        return NULL;
    }
    memset(rbc->bufs, 0, (num_bufs * sizeof(rb_entry_t)));

    rbc->each_buf_size = size_of_buf;
    rbc->max_num_bufs = num_bufs;

    status = pthread_mutex_init(&rbc->rb_rw_lock, NULL);
    if (status != 0) {
        ALOGE("Failed to initialize rb_rw_lock");
        // TODO handle lock initialization failure
    }
    rbc->threshold_reached = RB_FALSE;
    return rbc;
}

void ring_buffer_deinit(void *ctx)
{
    rbc_t *rbc = (rbc_t *)ctx;
    int status;
    unsigned int buf_no;

    status = pthread_mutex_destroy(&rbc->rb_rw_lock);
    if (status != 0) {
        ALOGE("Failed to destroy rb_rw_lock");
        // TODO handle the lock destroy failure
    }
    for (buf_no = 0; buf_no < rbc->max_num_bufs; buf_no++) {
        free(rbc->bufs[buf_no].data);
    }
    free(rbc->bufs);
    free(rbc);
}

/*
 * record_length : 0  - byte boundary
 *               : >0 - Ensures to write record_length no.of bytes to the same buffer.
 */
enum rb_status rb_write (void *ctx, u8 *buf, size_t length, int overwrite,
                         size_t record_length)
{
    rbc_t *rbc = (rbc_t *)ctx;
    unsigned int bytes_written = 0; // bytes written into rb so far
    unsigned int push_in_rd_ptr = 0; // push required in read pointer because of
                                     // write in current buffer
    unsigned int total_push_in_rd_ptr = 0; // Total amount of push in read pointer in this write

    if (record_length > rbc->each_buf_size) {
        return RB_FAILURE;
    }

    if (overwrite == 0) {
        /* Check if the complete RB is full. If the current wr_buf is also
         * full, it indicates that the complete RB is full
         */
        if (rbc->bufs[rbc->wr_buf_no].full == 1)
            return RB_FULL;
        /* Check whether record fits in current buffer */
        if (rbc->wr_buf_no == rbc->rd_buf_no) {
            if ((rbc->cur_wr_buf_idx == rbc->cur_rd_buf_idx) &&
                rbc->cur_valid_bytes) {
                return RB_FULL;
            } else if (rbc->cur_wr_buf_idx < rbc->cur_rd_buf_idx) {
                if (record_length >
                    (rbc->cur_rd_buf_idx - rbc->cur_wr_buf_idx)) {
                    return RB_FULL;
                }
            } else {
                if (record_length > (rbc->each_buf_size - rbc->cur_wr_buf_idx)) {
                    /* Check if the next buffer is not full to write this record into
                     * next buffer
                     */
                    unsigned int next_buf_no = rbc->wr_buf_no + 1;

                    if (next_buf_no >= rbc->max_num_bufs) {
                        next_buf_no = 0;
                    }
                    if (rbc->bufs[next_buf_no].full == 1) {
                        return RB_FULL;
                    }
                }
            }
        } else if (record_length > (rbc->each_buf_size - rbc->cur_wr_buf_idx)) {
            /* Check if the next buffer is not full to write this record into
             * next buffer
             */
            unsigned int next_buf_no = rbc->wr_buf_no + 1;

            if (next_buf_no >= rbc->max_num_bufs) {
                next_buf_no = 0;
            }
            if (rbc->bufs[next_buf_no].full == 1) {
                return RB_FULL;
            }
        }
    }

    /* Go to next buffer if the current buffer is not enough to write the
     * complete record
     */
    if (record_length > (rbc->each_buf_size - rbc->cur_wr_buf_idx)) {
        rbc->bufs[rbc->wr_buf_no].full = 1;
        rbc->bufs[rbc->wr_buf_no].last_wr_index = rbc->cur_wr_buf_idx;
        rbc->wr_buf_no++;
        if (rbc->wr_buf_no == rbc->max_num_bufs) {
            rbc->wr_buf_no = 0;
        }
        rbc->cur_wr_buf_idx = 0;
    }


    /* In each iteration of below loop, the data that can be fit into
     * buffer @wr_buf_no will be copied from input buf */
    while (bytes_written < length) {
        unsigned int cur_copy_len;

        /* Allocate a buffer if no buf available @ wr_buf_no */
        if (rbc->bufs[rbc->wr_buf_no].data == NULL) {
            rbc->bufs[rbc->wr_buf_no].data = (u8 *)malloc(rbc->each_buf_size);
            if (rbc->bufs[rbc->wr_buf_no].data == NULL) {
                ALOGE("Failed to alloc write buffer");
                return RB_RETRY;
            }
        }

        /* Take the minimum of the remaining length that needs to be written
         * from buf and the maximum length that can be written into current
         * buffer in ring buffer
         */
        cur_copy_len = RB_MIN((rbc->each_buf_size - rbc->cur_wr_buf_idx),
                              (length - bytes_written));

        rb_lock(&rbc->rb_rw_lock);

        /* Push the read pointer in case of overrun */
        if (rbc->rd_buf_no == rbc->wr_buf_no) {
            if ((rbc->cur_rd_buf_idx > rbc->cur_wr_buf_idx) ||
                ((rbc->cur_rd_buf_idx == rbc->cur_wr_buf_idx) &&
                 rbc->cur_valid_bytes)) {
                /* If read ptr is ahead of write pointer and if the
                 * gap is not enough to fit the cur_copy_len bytes then
                 * push the read pointer so that points to the start of
                 * old bytes after this write
                 */
                if ((rbc->cur_rd_buf_idx - rbc->cur_wr_buf_idx) <
                    cur_copy_len) {
                    push_in_rd_ptr += cur_copy_len -
                                    (rbc->cur_rd_buf_idx - rbc->cur_wr_buf_idx);
                    rbc->cur_rd_buf_idx = rbc->cur_wr_buf_idx + cur_copy_len;
                    if (rbc->cur_rd_buf_idx >=
                        rbc->bufs[rbc->rd_buf_no].last_wr_index) {
                        rbc->cur_rd_buf_idx = 0;
                        rbc->rd_buf_no++;
                        if (rbc->rd_buf_no == rbc->max_num_bufs) {
                            rbc->rd_buf_no = 0;
                            ALOGV("Pushing read to the start of ring buffer");
                        }
                        /* the previous buffer might have little more empty room
                         * after overwriting the remaining bytes
                         */
                        rbc->bufs[rbc->wr_buf_no].full = 0;
                    }
                }
            }
        }
        rb_unlock(&rbc->rb_rw_lock);

        /* don't use lock while doing memcpy, so that we don't block the read
         * context for too long. There is no harm while writing the memory if
         * locking is properly done while upgrading the pointers */
        memcpy((rbc->bufs[rbc->wr_buf_no].data + rbc->cur_wr_buf_idx),
               (buf + bytes_written),
               cur_copy_len);

        rb_lock(&rbc->rb_rw_lock);
        /* Update the write idx by the amount of write done in this iteration */
        rbc->cur_wr_buf_idx += cur_copy_len;
        if (rbc->cur_wr_buf_idx == rbc->each_buf_size) {
            /* Increment the wr_buf_no as the current buffer is full */
            rbc->bufs[rbc->wr_buf_no].full = 1;
            rbc->bufs[rbc->wr_buf_no].last_wr_index = rbc->cur_wr_buf_idx;
            rbc->wr_buf_no++;
            if (rbc->wr_buf_no == rbc->max_num_bufs) {
                ALOGV("Write rolling over to the start of ring buffer");
                rbc->wr_buf_no = 0;
            }
            /* Reset the write index to zero as this is a new buffer */
            rbc->cur_wr_buf_idx = 0;
        }

        if ((rbc->cur_valid_bytes + (cur_copy_len - push_in_rd_ptr)) >
            (rbc->max_num_bufs * rbc->each_buf_size)) {
            /* The below is only a precautionary print and ideally should never
             * come */
            ALOGE("Something going wrong in ring buffer");
        } else {
            /* Increase the valid bytes count by number of bytes written without
             * overwriting the old bytes */
            rbc->cur_valid_bytes += cur_copy_len - push_in_rd_ptr;
        }
        total_push_in_rd_ptr += push_in_rd_ptr;
        push_in_rd_ptr = 0;
        rb_unlock(&rbc->rb_rw_lock);
        bytes_written += cur_copy_len;
    }

    rb_lock(&rbc->rb_rw_lock);
    rbc->total_bytes_written += bytes_written - total_push_in_rd_ptr;
    rbc->total_bytes_overwritten += total_push_in_rd_ptr;

    /* check if valid bytes is going more than threshold */
    if ((rbc->threshold_reached == RB_FALSE) &&
        (rbc->cur_valid_bytes >= rbc->num_min_bytes) &&
        ((length == record_length) || !record_length) &&
        rbc->threshold_cb) {
        /* Release the lock before calling threshold_cb as it might call rb_read
         * in this same context in order to avoid dead lock
         */
        rbc->threshold_reached = RB_TRUE;
        rb_unlock(&rbc->rb_rw_lock);
        rbc->threshold_cb(rbc->cb_ctx);
    } else {
        rb_unlock(&rbc->rb_rw_lock);
    }
    return RB_SUCCESS;
}

size_t rb_read (void *ctx, u8 *buf, size_t max_length)
{
    rbc_t *rbc = (rbc_t *)ctx;
    unsigned int bytes_read = 0;
    unsigned int no_more_bytes_available = 0;

    rb_lock(&rbc->rb_rw_lock);
    while (bytes_read < max_length) {
        unsigned int cur_cpy_len;

        if (rbc->bufs[rbc->rd_buf_no].data == NULL) {
            break;
        }

        /* if read and write are on same buffer, work with rd, wr indices */
        if (rbc->rd_buf_no == rbc->wr_buf_no) {
            if (rbc->cur_rd_buf_idx < rbc->cur_wr_buf_idx) {
                /* Check if all the required bytes are available, if not
                 * read only the available bytes in the current buffer and
                 * break out after reading current buffer
                 */
                if ((rbc->cur_wr_buf_idx - rbc->cur_rd_buf_idx) <
                        (max_length - bytes_read)) {
                    cur_cpy_len = rbc->cur_wr_buf_idx - rbc->cur_rd_buf_idx;
                    no_more_bytes_available = 1;
                } else {
                    cur_cpy_len = max_length - bytes_read;
                }
            } else {
                /* When there are no bytes available to read cur_rd_buf_idx
                 * will be euqal to cur_wr_buf_idx. Handle this scenario using
                 * cur_valid_bytes */
                if (rbc->cur_valid_bytes <= bytes_read) {
                    /* Suppress possible static analyzer's warning */
                    cur_cpy_len = 0;
                    break;
                }
                cur_cpy_len = RB_MIN((rbc->each_buf_size - rbc->cur_rd_buf_idx),
                                     (max_length - bytes_read));
            }
        } else {
            /* Check if all remaining_length bytes can be read from this
             * buffer, if not read only the available bytes in the current
             * buffer and go to next buffer using the while loop.
             */
            cur_cpy_len = RB_MIN((rbc->each_buf_size - rbc->cur_rd_buf_idx),
                                 (max_length - bytes_read));
        }

        memcpy((buf + bytes_read),
               (rbc->bufs[rbc->rd_buf_no].data + rbc->cur_rd_buf_idx),
               cur_cpy_len);

        /* Update the read index */
        rbc->cur_rd_buf_idx += cur_cpy_len;
        if (rbc->cur_rd_buf_idx == rbc->each_buf_size) {
            /* Increment rd_buf_no as the current buffer is completely read */
            if (rbc->rd_buf_no != rbc->wr_buf_no) {
                free(rbc->bufs[rbc->rd_buf_no].data);
                rbc->bufs[rbc->rd_buf_no].data = NULL;
            }
            rbc->rd_buf_no++;
            if (rbc->rd_buf_no == rbc->max_num_bufs) {
                ALOGV("Read rolling over to the start of ring buffer");
                rbc->rd_buf_no = 0;
            }
            /* Reset the read index as this is a new buffer */
            rbc->cur_rd_buf_idx = 0;
        }

        bytes_read += cur_cpy_len;
        if (no_more_bytes_available) {
            break;
        }
    }

    rbc->total_bytes_read += bytes_read;
    if (rbc->cur_valid_bytes < bytes_read) {
        /* The below is only a precautionary print and ideally should never
         * come */
        ALOGE("Something going wrong in ring buffer");
    } else {
        rbc->cur_valid_bytes -= bytes_read;
    }

    /* check if valid bytes is going less than threshold */
    if (rbc->threshold_reached == RB_TRUE) {
        if (rbc->cur_valid_bytes < rbc->num_min_bytes) {
            rbc->threshold_reached = RB_FALSE;
        }
    }
    rb_unlock(&rbc->rb_rw_lock);
    return bytes_read;
}

u8 *rb_get_read_buf(void *ctx, size_t *length)
{
    rbc_t *rbc = (rbc_t *)ctx;
    unsigned int cur_read_len = 0;
    u8 *buf;

    /* If no buffer is available for reading */
    if (rbc->bufs[rbc->rd_buf_no].data == NULL) {
        *length = 0;
        return NULL;
    }

    rb_lock(&rbc->rb_rw_lock);
    if ((rbc->bufs[rbc->rd_buf_no].full == 1) &&
        (rbc->cur_rd_buf_idx == rbc->bufs[rbc->rd_buf_no].last_wr_index)) {
        if (rbc->wr_buf_no != rbc->rd_buf_no) {
            free(rbc->bufs[rbc->rd_buf_no].data);
            rbc->bufs[rbc->rd_buf_no].data = NULL;
        }
        rbc->bufs[rbc->rd_buf_no].full = 0;
        rbc->rd_buf_no++;
        if (rbc->rd_buf_no == rbc->max_num_bufs) {
            rbc->rd_buf_no = 0;
        }
        rbc->cur_rd_buf_idx = 0;
    }

    if (rbc->wr_buf_no == rbc->rd_buf_no) {
        /* If read and write are happening on the same buffer currently, use
         * rd and wr indices within the buffer */
        if ((rbc->cur_rd_buf_idx == rbc->cur_wr_buf_idx) &&
            (rbc->cur_valid_bytes == 0)) {
            /* No bytes available for reading */
            *length = 0;
            rb_unlock(&rbc->rb_rw_lock);
            return NULL;
        } else if (rbc->cur_rd_buf_idx < rbc->cur_wr_buf_idx) {
            /* write is just ahead of read in this buffer */
            cur_read_len = rbc->cur_wr_buf_idx - rbc->cur_rd_buf_idx;
        } else {
            /* write is rolled over and just behind the read */
            cur_read_len = rbc->bufs[rbc->rd_buf_no].last_wr_index - rbc->cur_rd_buf_idx;
        }
    } else {
        if (rbc->cur_rd_buf_idx == 0) {
            /* The complete buffer can be read out */
            cur_read_len = rbc->bufs[rbc->rd_buf_no].last_wr_index;
        } else {
            /* Read the remaining bytes in this buffer */
            cur_read_len = rbc->bufs[rbc->rd_buf_no].last_wr_index - rbc->cur_rd_buf_idx;
        }
    }

    if ((rbc->bufs[rbc->rd_buf_no].full == 1) &&
         (rbc->cur_rd_buf_idx == 0)) {
        /* Pluck out the complete buffer and send it out */
        buf = rbc->bufs[rbc->rd_buf_no].data;
        rbc->bufs[rbc->rd_buf_no].data = NULL;

        /* Move to the next buffer */
        rbc->bufs[rbc->rd_buf_no].full = 0;
        rbc->rd_buf_no++;
        if (rbc->rd_buf_no == rbc->max_num_bufs) {
            ALOGV("Read rolling over to the start of ring buffer");
            rbc->rd_buf_no = 0;
        }
    } else {
        /* We cannot give out the complete buffer, so allocate a new memory and
         * and copy the data into it.
         */
        buf = (u8 *)malloc(cur_read_len);
        if (buf == NULL) {
            ALOGE("Failed to alloc buffer for partial buf read");
            *length = 0;
            rb_unlock(&rbc->rb_rw_lock);
            return NULL;
        }
        memcpy(buf,
               (rbc->bufs[rbc->rd_buf_no].data + rbc->cur_rd_buf_idx),
               cur_read_len);

        /* Update the read index */
        if (rbc->bufs[rbc->rd_buf_no].full == 1) {
            if (rbc->wr_buf_no != rbc->rd_buf_no) {
                free(rbc->bufs[rbc->rd_buf_no].data);
                rbc->bufs[rbc->rd_buf_no].data = NULL;
            }
            rbc->bufs[rbc->rd_buf_no].full = 0;
            rbc->rd_buf_no++;
            if (rbc->rd_buf_no == rbc->max_num_bufs) {
                rbc->rd_buf_no = 0;
            }
            rbc->cur_rd_buf_idx = 0;
        } else {
            rbc->cur_rd_buf_idx += cur_read_len;
        }
    }

    rbc->total_bytes_read += cur_read_len;
    if (rbc->cur_valid_bytes < cur_read_len) {
        /* The below is only a precautionary print and ideally should never
         * come */
        ALOGE("Something going wrong in ring buffer");
    } else {
        rbc->cur_valid_bytes -= cur_read_len;
    }

    /* check if valid bytes is going less than threshold */
    if (rbc->threshold_reached == RB_TRUE) {
        if (rbc->cur_valid_bytes < rbc->num_min_bytes) {
            rbc->threshold_reached = RB_FALSE;
        }
    }
    rb_unlock(&rbc->rb_rw_lock);

    *length = cur_read_len;
    return buf;
}

void rb_config_threshold(void *ctx,
                         unsigned int num_min_bytes,
                         threshold_call_back callback,
                         void *cb_ctx)
{
    rbc_t *rbc = (rbc_t *)ctx;

    rbc->num_min_bytes = num_min_bytes;
    rbc->threshold_cb = callback;
    rbc->cb_ctx = cb_ctx;
}

void rb_get_stats(void *ctx, struct rb_stats *rbs)
{
    rbc_t *rbc = (rbc_t *)ctx;

    rbs->total_bytes_written = rbc->total_bytes_written;
    rbs->total_bytes_read = rbc->total_bytes_read;
    rbs->cur_valid_bytes = rbc->cur_valid_bytes;
    rbs->each_buf_size = rbc->each_buf_size;
    rbs->max_num_bufs = rbc->max_num_bufs;
}
