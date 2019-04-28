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
#ifndef _DEV_DEP_H
#define _DEV_DEP_H
struct dolby_param_data {
  int32_t version;
  int32_t device_id;
  int32_t be_id;
  int32_t param_id;
  int32_t length;
  int32_t * data;
};
struct dolby_param_license {
  int32_t dmid;
  int32_t license_key;
};
#define SNDRV_DEVDEP_DAP_IOCTL_SET_PARAM _IOWR('U', 0x10, struct dolby_param_data)
#define SNDRV_DEVDEP_DAP_IOCTL_GET_PARAM _IOR('U', 0x11, struct dolby_param_data)
#define SNDRV_DEVDEP_DAP_IOCTL_DAP_COMMAND _IOWR('U', 0x13, struct dolby_param_data)
#define SNDRV_DEVDEP_DAP_IOCTL_DAP_LICENSE _IOWR('U', 0x14, struct dolby_param_license)
#define SNDRV_DEVDEP_DAP_IOCTL_GET_VISUALIZER _IOR('U', 0x15, struct dolby_param_data)
#define DTS_EAGLE_MODULE 0x00005000
#define DTS_EAGLE_MODULE_ENABLE 0x00005001
#define EAGLE_DRIVER_ID 0xF2
#define DTS_EAGLE_IOCTL_GET_CACHE_SIZE _IOR(EAGLE_DRIVER_ID, 0, int)
#define DTS_EAGLE_IOCTL_SET_CACHE_SIZE _IOW(EAGLE_DRIVER_ID, 1, int)
#define DTS_EAGLE_IOCTL_GET_PARAM _IOR(EAGLE_DRIVER_ID, 2, void *)
#define DTS_EAGLE_IOCTL_SET_PARAM _IOW(EAGLE_DRIVER_ID, 3, void *)
#define DTS_EAGLE_IOCTL_SET_CACHE_BLOCK _IOW(EAGLE_DRIVER_ID, 4, void *)
#define DTS_EAGLE_IOCTL_SET_ACTIVE_DEVICE _IOW(EAGLE_DRIVER_ID, 5, void *)
#define DTS_EAGLE_IOCTL_GET_LICENSE _IOR(EAGLE_DRIVER_ID, 6, void *)
#define DTS_EAGLE_IOCTL_SET_LICENSE _IOW(EAGLE_DRIVER_ID, 7, void *)
#define DTS_EAGLE_IOCTL_SEND_LICENSE _IOW(EAGLE_DRIVER_ID, 8, int)
#define DTS_EAGLE_IOCTL_SET_VOLUME_COMMANDS _IOW(EAGLE_DRIVER_ID, 9, void *)
#define DTS_EAGLE_FLAG_IOCTL_PRE (1 << 30)
#define DTS_EAGLE_FLAG_IOCTL_JUSTSETCACHE (1 << 31)
#define DTS_EAGLE_FLAG_IOCTL_GETFROMCORE DTS_EAGLE_FLAG_IOCTL_JUSTSETCACHE
#define DTS_EAGLE_FLAG_IOCTL_MASK (~(DTS_EAGLE_FLAG_IOCTL_PRE | DTS_EAGLE_FLAG_IOCTL_JUSTSETCACHE))
#define DTS_EAGLE_FLAG_ALSA_GET (1 << 31)
struct dts_eagle_param_desc {
  uint32_t id;
  uint32_t size;
  int32_t offset;
  uint32_t device;
} __attribute__((packed));
#define HWDEP_FE_BASE 3000
struct snd_pcm_mmap_fd {
  int32_t dir;
  int32_t fd;
  int32_t size;
  int32_t actual_size;
};
#define SNDRV_PCM_IOCTL_MMAP_DATA_FD _IOWR('U', 0xd2, struct snd_pcm_mmap_fd)
#endif

