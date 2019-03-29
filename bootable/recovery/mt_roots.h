/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/

#ifndef MT_ROOTS_H_
#define MT_ROOTS_H_

#include "common.h"

int mt_setup_install_mounts(void);
int mt_load_volume_table(struct fstab *fstab);
Volume* mt_volume_for_path(const char* path);
int mt_ensure_path_mounted(Volume* v);
void mt_ensure_dev_ready(const char *mount_point);
void mt_fstab_translation_NAND(struct fstab *fstab);
#endif

