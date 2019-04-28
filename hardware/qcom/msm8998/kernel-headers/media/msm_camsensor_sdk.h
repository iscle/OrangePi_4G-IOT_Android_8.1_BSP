/****************************************************************************
 ****************************************************************************
 ***
 ***   This header was automatically generated from a Linux kernel header
 ***   of the same name, to make information necessary for userspace to
 ***   call into the kernel available to libc.  It contains only constants,
 ***   structures, and macros generated from the original header, and thus,
 ***   contains no copyrightable information.
 ***
 ***   To edit the content of this header, modify the corresponding
 ***   source file (e.g. under external/kernel-headers/original/) then
 ***   run bionic/libc/kernel/tools/update_all.py
 ***
 ***   Any manual change here will be lost the next time this script will
 ***   be run. You've been warned!
 ***
 ****************************************************************************
 ****************************************************************************/
#ifndef __UAPI_LINUX_MSM_CAMSENSOR_SDK_H
#define __UAPI_LINUX_MSM_CAMSENSOR_SDK_H
#include <linux/videodev2.h>
#define KVERSION 0x1
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MAX_POWER_CONFIG 12
#define GPIO_OUT_LOW (0 << 1)
#define GPIO_OUT_HIGH (1 << 1)
#define CSI_EMBED_DATA 0x12
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define CSI_RESERVED_DATA_0 0x13
#define CSI_YUV422_8 0x1E
#define CSI_RAW8 0x2A
#define CSI_RAW10 0x2B
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define CSI_RAW12 0x2C
#define CSI_DECODE_6BIT 0
#define CSI_DECODE_8BIT 1
#define CSI_DECODE_10BIT 2
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define CSI_DECODE_12BIT 3
#define CSI_DECODE_DPCM_10_6_10 4
#define CSI_DECODE_DPCM_10_8_10 5
#define MAX_CID 16
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define I2C_SEQ_REG_DATA_MAX 1024
#define I2C_REG_DATA_MAX (8 * 1024)
#define MSM_V4L2_PIX_FMT_META v4l2_fourcc('M', 'E', 'T', 'A')
#define MSM_V4L2_PIX_FMT_SBGGR14 v4l2_fourcc('B', 'G', '1', '4')
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_V4L2_PIX_FMT_SGBRG14 v4l2_fourcc('G', 'B', '1', '4')
#define MSM_V4L2_PIX_FMT_SGRBG14 v4l2_fourcc('B', 'A', '1', '4')
#define MSM_V4L2_PIX_FMT_SRGGB14 v4l2_fourcc('R', 'G', '1', '4')
#define MAX_ACTUATOR_REG_TBL_SIZE 8
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MAX_ACTUATOR_REGION 5
#define NUM_ACTUATOR_DIR 2
#define MAX_ACTUATOR_SCENARIO 8
#define MAX_ACT_MOD_NAME_SIZE 32
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MAX_ACT_NAME_SIZE 32
#define MAX_ACTUATOR_INIT_SET 120
#define MAX_I2C_REG_SET 12
#define MAX_LED_TRIGGERS 3
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define MSM_EEPROM_MEMORY_MAP_MAX_SIZE 80
#define MSM_EEPROM_MAX_MEM_MAP_CNT 20
enum msm_sensor_camera_id_t {
  CAMERA_0,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CAMERA_1,
  CAMERA_2,
  CAMERA_3,
  MAX_CAMERAS,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
enum i2c_freq_mode_t {
  I2C_STANDARD_MODE,
  I2C_FAST_MODE,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  I2C_CUSTOM_MODE,
  I2C_FAST_PLUS_MODE,
  I2C_MAX_MODES,
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum camb_position_t {
  BACK_CAMERA_B,
  FRONT_CAMERA_B,
  AUX_CAMERA_B = 0x100,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  INVALID_CAMERA_B,
};
enum msm_sensor_power_seq_type_t {
  SENSOR_CLK,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  SENSOR_GPIO,
  SENSOR_VREG,
  SENSOR_I2C_MUX,
  SENSOR_I2C,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
enum msm_camera_i2c_reg_addr_type {
  MSM_CAMERA_I2C_BYTE_ADDR = 1,
  MSM_CAMERA_I2C_WORD_ADDR,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  MSM_CAMERA_I2C_3B_ADDR,
  MSM_CAMERA_I2C_ADDR_TYPE_MAX,
};
enum msm_camera_i2c_data_type {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  MSM_CAMERA_I2C_BYTE_DATA = 1,
  MSM_CAMERA_I2C_WORD_DATA,
  MSM_CAMERA_I2C_DWORD_DATA,
  MSM_CAMERA_I2C_SET_BYTE_MASK,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  MSM_CAMERA_I2C_UNSET_BYTE_MASK,
  MSM_CAMERA_I2C_SET_WORD_MASK,
  MSM_CAMERA_I2C_UNSET_WORD_MASK,
  MSM_CAMERA_I2C_SET_BYTE_WRITE_MASK_DATA,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  MSM_CAMERA_I2C_DATA_TYPE_MAX,
};
enum msm_sensor_power_seq_gpio_t {
  SENSOR_GPIO_RESET,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  SENSOR_GPIO_STANDBY,
  SENSOR_GPIO_AF_PWDM,
  SENSOR_GPIO_VIO,
  SENSOR_GPIO_VANA,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  SENSOR_GPIO_VDIG,
  SENSOR_GPIO_VAF,
  SENSOR_GPIO_FL_EN,
  SENSOR_GPIO_FL_NOW,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  SENSOR_GPIO_FL_RESET,
  SENSOR_GPIO_CUSTOM1,
  SENSOR_GPIO_CUSTOM2,
  SENSOR_GPIO_MAX,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
enum msm_ir_cut_filter_gpio_t {
  IR_CUT_FILTER_GPIO_P = 0,
  IR_CUT_FILTER_GPIO_M,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  IR_CUT_FILTER_GPIO_MAX,
};
#define IR_CUT_FILTER_GPIO_P IR_CUT_FILTER_GPIO_P
#define IR_CUT_FILTER_GPIO_M IR_CUT_FILTER_GPIO_M
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define R_CUT_FILTER_GPIO_MAX IR_CUT_FILTER_GPIO_MAX
enum msm_camera_vreg_name_t {
  CAM_VDIG,
  CAM_VIO,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CAM_VANA,
  CAM_VAF,
  CAM_V_CUSTOM1,
  CAM_V_CUSTOM2,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CAM_VREG_MAX,
};
enum msm_sensor_clk_type_t {
  SENSOR_CAM_MCLK,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  SENSOR_CAM_CLK,
  SENSOR_CAM_CLK_MAX,
};
enum camerab_mode_t {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CAMERA_MODE_2D_B = (1 << 0),
  CAMERA_MODE_3D_B = (1 << 1),
  CAMERA_MODE_INVALID = (1 << 2),
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum msm_actuator_data_type {
  MSM_ACTUATOR_BYTE_DATA = 1,
  MSM_ACTUATOR_WORD_DATA,
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum msm_actuator_addr_type {
  MSM_ACTUATOR_BYTE_ADDR = 1,
  MSM_ACTUATOR_WORD_ADDR,
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum msm_actuator_write_type {
  MSM_ACTUATOR_WRITE_HW_DAMP,
  MSM_ACTUATOR_WRITE_DAC,
  MSM_ACTUATOR_WRITE,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  MSM_ACTUATOR_WRITE_DIR_REG,
  MSM_ACTUATOR_POLL,
  MSM_ACTUATOR_READ_WRITE,
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum msm_actuator_i2c_operation {
  MSM_ACT_WRITE = 0,
  MSM_ACT_POLL,
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum actuator_type {
  ACTUATOR_VCM,
  ACTUATOR_PIEZO,
  ACTUATOR_HVCM,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  ACTUATOR_BIVCM,
};
enum msm_flash_driver_type {
  FLASH_DRIVER_PMIC,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  FLASH_DRIVER_I2C,
  FLASH_DRIVER_GPIO,
  FLASH_DRIVER_DEFAULT
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum msm_flash_cfg_type_t {
  CFG_FLASH_INIT,
  CFG_FLASH_RELEASE,
  CFG_FLASH_OFF,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CFG_FLASH_LOW,
  CFG_FLASH_HIGH,
};
enum msm_ir_led_cfg_type_t {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CFG_IR_LED_INIT = 0,
  CFG_IR_LED_RELEASE,
  CFG_IR_LED_OFF,
  CFG_IR_LED_ON,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
#define CFG_IR_LED_INIT CFG_IR_LED_INIT
#define CFG_IR_LED_RELEASE CFG_IR_LED_RELEASE
#define CFG_IR_LED_OFF CFG_IR_LED_OFF
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define CFG_IR_LED_ON CFG_IR_LED_ON
enum msm_ir_cut_cfg_type_t {
  CFG_IR_CUT_INIT = 0,
  CFG_IR_CUT_RELEASE,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  CFG_IR_CUT_OFF,
  CFG_IR_CUT_ON,
};
#define CFG_IR_CUT_INIT CFG_IR_CUT_INIT
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define CFG_IR_CUT_RELEASE CFG_IR_CUT_RELEASE
#define CFG_IR_CUT_OFF CFG_IR_CUT_OFF
#define CFG_IR_CUT_ON CFG_IR_CUT_ON
enum msm_sensor_output_format_t {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  MSM_SENSOR_BAYER,
  MSM_SENSOR_YCBCR,
  MSM_SENSOR_META,
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct msm_sensor_power_setting {
  enum msm_sensor_power_seq_type_t seq_type;
  unsigned short seq_val;
  long config_val;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned short delay;
  void * data[10];
};
struct msm_sensor_power_setting_array {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct msm_sensor_power_setting power_setting_a[MAX_POWER_CONFIG];
  struct msm_sensor_power_setting * power_setting;
  unsigned short size;
  struct msm_sensor_power_setting power_down_setting_a[MAX_POWER_CONFIG];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct msm_sensor_power_setting * power_down_setting;
  unsigned short size_down;
};
enum msm_camera_i2c_operation {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  MSM_CAM_WRITE = 0,
  MSM_CAM_POLL,
  MSM_CAM_READ,
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct msm_sensor_i2c_sync_params {
  unsigned int cid;
  int csid;
  unsigned short line;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned short delay;
};
struct msm_camera_reg_settings_t {
  uint16_t reg_addr;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum msm_camera_i2c_reg_addr_type addr_type;
  uint16_t reg_data;
  enum msm_camera_i2c_data_type data_type;
  enum msm_camera_i2c_operation i2c_operation;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  uint16_t delay;
};
struct msm_eeprom_mem_map_t {
  int slave_addr;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct msm_camera_reg_settings_t mem_settings[MSM_EEPROM_MEMORY_MAP_MAX_SIZE];
  int memory_map_size;
};
struct msm_eeprom_memory_map_array {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct msm_eeprom_mem_map_t memory_map[MSM_EEPROM_MAX_MEM_MAP_CNT];
  uint32_t msm_size_of_max_mappings;
};
struct msm_sensor_init_params {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  int modes_supported;
  enum camb_position_t position;
  unsigned int sensor_mount_angle;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct msm_sensor_id_info_t {
  unsigned short sensor_id_reg_addr;
  unsigned short sensor_id;
  unsigned short sensor_id_mask;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct msm_camera_sensor_slave_info {
  char sensor_name[32];
  char eeprom_name[32];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  char actuator_name[32];
  char ois_name[32];
  char flash_name[32];
  enum msm_sensor_camera_id_t camera_id;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned short slave_addr;
  enum i2c_freq_mode_t i2c_freq_mode;
  enum msm_camera_i2c_reg_addr_type addr_type;
  struct msm_sensor_id_info_t sensor_id_info;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct msm_sensor_power_setting_array power_setting_array;
  unsigned char is_init_params_valid;
  struct msm_sensor_init_params sensor_init_params;
  enum msm_sensor_output_format_t output_format;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct msm_camera_i2c_reg_array {
  unsigned short reg_addr;
  unsigned short reg_data;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned int delay;
};
struct msm_camera_i2c_reg_setting {
  struct msm_camera_i2c_reg_array * reg_setting;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned short size;
  enum msm_camera_i2c_reg_addr_type addr_type;
  enum msm_camera_i2c_data_type data_type;
  unsigned short delay;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct msm_camera_csid_vc_cfg {
  unsigned char cid;
  unsigned char dt;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned char decode_format;
};
struct msm_camera_csid_lut_params {
  unsigned char num_cid;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct msm_camera_csid_vc_cfg vc_cfg_a[MAX_CID];
  struct msm_camera_csid_vc_cfg * vc_cfg[MAX_CID];
};
struct msm_camera_csid_params {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned char lane_cnt;
  unsigned short lane_assign;
  unsigned char phy_sel;
  unsigned int csi_clk;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct msm_camera_csid_lut_params lut_params;
  unsigned char csi_3p_sel;
};
struct msm_camera_csid_testmode_parms {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned int num_bytes_per_line;
  unsigned int num_lines;
  unsigned int h_blanking_count;
  unsigned int v_blanking_count;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned int payload_mode;
};
struct msm_camera_csiphy_params {
  unsigned char lane_cnt;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned char settle_cnt;
  unsigned short lane_mask;
  unsigned char combo_mode;
  unsigned char csid_core;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned int csiphy_clk;
  unsigned char csi_3phase;
};
struct msm_camera_i2c_seq_reg_array {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned short reg_addr;
  unsigned char reg_data[I2C_SEQ_REG_DATA_MAX];
  unsigned short reg_data_size;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct msm_camera_i2c_seq_reg_setting {
  struct msm_camera_i2c_seq_reg_array * reg_setting;
  unsigned short size;
  enum msm_camera_i2c_reg_addr_type addr_type;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned short delay;
};
struct msm_actuator_reg_params_t {
  enum msm_actuator_write_type reg_write_type;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned int hw_mask;
  unsigned short reg_addr;
  unsigned short hw_shift;
  unsigned short data_shift;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned short data_type;
  unsigned short addr_type;
  unsigned short reg_data;
  unsigned short delay;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct damping_params_t {
  unsigned int damping_step;
  unsigned int damping_delay;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned int hw_params;
};
struct region_params_t {
  unsigned short step_bound[2];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned short code_per_step;
  unsigned int qvalue;
};
struct reg_settings_t {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned short reg_addr;
  enum msm_camera_i2c_reg_addr_type addr_type;
  unsigned short reg_data;
  enum msm_camera_i2c_data_type data_type;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  enum msm_actuator_i2c_operation i2c_operation;
  unsigned int delay;
};
struct msm_camera_i2c_reg_setting_array {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  struct msm_camera_i2c_reg_array reg_setting_a[MAX_I2C_REG_SET];
  unsigned short size;
  enum msm_camera_i2c_reg_addr_type addr_type;
  enum msm_camera_i2c_data_type data_type;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  unsigned short delay;
};
#endif

