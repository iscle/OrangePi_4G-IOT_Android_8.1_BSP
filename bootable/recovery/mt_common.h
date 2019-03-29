/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
#ifndef MT_COMMON_H_
#define MT_COMMON_H_

#include <fcntl.h>
#include <unistd.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#undef LOGE
// TODO: restore ui_print for LOGE
#define LOGE(...) fprintf(stdout, "E:" __VA_ARGS__)

#define PRELOADER_PART "/dev/block/mmcblk0boot0"
#define PRELOADER2_PART "/dev/block/mmcblk0boot1"
#define UFS_PRELOADER_PART "/dev/block/sda"
#define UFS_PRELOADER2_PART "/dev/block/sdb"
#define BOOT_PART      "/dev/block/platform/bootdevice/by-name/boot"
#define CACHE_PART     "/dev/block/platform/bootdevice/by-name/cache"
#define FAT_PART       "/dev/block/platform/bootdevice/by-name/intsd"
#define SYSTEM_PART    "/dev/block/platform/bootdevice/by-name/system"
#define DATA_PART      "/dev/block/platform/bootdevice/by-name/userdata"
#define MISC_PART      "/dev/block/platform/bootdevice/by-name/para"
#define RECOVERY_PART  "/dev/block/platform/bootdevice/by-name/recovery"
#define CUSTOM_PART    "/dev/block/platform/bootdevice/by-name/custom"
#define VENDOR_PART    "/dev/block/platform/bootdevice/by-name/vendor"
#define LOGO_PART      "/dev/block/platform/bootdevice/by-name/logo"
#define LK_PART        "/dev/block/platform/bootdevice/by-name/lk"
#define TEE1_PART      "/dev/block/platform/bootdevice/by-name/tee1"
#define TEE2_PART      "/dev/block/platform/bootdevice/by-name/tee2"
#define PERSIST_PART   "/dev/block/platform/bootdevice/by-name/persist"
#define NVDATA_PART    "/dev/block/platform/bootdevice/by-name/nvdata"
#define MT_GPT_PART    "/dev/block/platform/bootdevice/by-name"

static inline bool support_gpt(void) {\
    return true;\
}
#define EMMC_PART_GPT_PREFIX     "/dev/block/platform/bootdevice"
#define UFS_PART_GPT_PREFIX     "/dev/block/platform/bootdevice"

#ifdef __cplusplus
}
#endif

#endif
