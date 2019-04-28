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
#ifndef __AUDIO_SLIMSLAVE_H__
#define __AUDIO_SLIMSLAVE_H__
#include <linux/types.h>
#include <linux/ioctl.h>
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define AUDIO_SLIMSLAVE_IOCTL_NAME "audio_slimslave"
#define AUDIO_SLIMSLAVE_MAGIC 'S'
#define AUDIO_SLIMSLAVE_IOCTL_UNVOTE _IO(AUDIO_SLIMSLAVE_MAGIC, 0x00)
#define AUDIO_SLIMSLAVE_IOCTL_VOTE _IO(AUDIO_SLIMSLAVE_MAGIC, 0x01)
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
enum {
  AUDIO_SLIMSLAVE_UNVOTE,
  AUDIO_SLIMSLAVE_VOTE
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#endif

