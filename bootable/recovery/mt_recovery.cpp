/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#include <errno.h>
#include "mt_recovery.h"
#include "mt_partition.h"
#include "install.h"

/* (MUST SYNC) Android default variables from recovery.cpp */
static const char *MT_UPDATE_STAGE_FILE = "/cache/recovery/last_mtupdate_stage";

const char *MOTA_RESULT_FILE = "/data/data/com.mediatek.systemupdate/files/updateResult";

bool remove_mota_file(const char *file_name)
{
    int ret = 0;

    //LOG_INFO("[%s] %s\n", __func__, file_name);

    ret = unlink(file_name);

    if (ret == 0) {
        return true;
    }

    if (ret < 0 && errno == ENOENT) {
        return true;
    }

    return false;
}

void write_result_file(const char *file_name, int result)
{
    if (INSTALL_SUCCESS == result) {
        set_ota_result(1);
    } else {
        set_ota_result(0);
    }

}

void mt_reset_mtupdate_stage(void)
{
    if ((unlink(MT_UPDATE_STAGE_FILE) < 0) && (errno != ENOENT))
        printf("unlink %s failed: %s\n", MT_UPDATE_STAGE_FILE, strerror(errno));
}

int mt_main_write_result(int &status, const char *update_package)
{
      if (update_package) {
          if ((status == INSTALL_SUCCESS) && update_package) {
              fprintf(stdout, "write result : remove_mota_file\n");
              remove_mota_file(update_package);
          }
          write_result_file(MOTA_RESULT_FILE, status);
      }
  return 0;
}

