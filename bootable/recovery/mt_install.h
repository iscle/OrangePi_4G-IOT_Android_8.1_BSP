/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/

#ifndef MT_INSTALL_H_
#define MT_INSTALL_H_

#include "bootloader.h"
#include <ziparchive/zip_archive.h>
#include "otautil/SysUtil.h"

int mt_really_install_package_check_part_size(int &ret, const char *path, bool needs_mount, ZipArchiveHandle zip);

#endif

