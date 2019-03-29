/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/

#include <fs_mgr.h>
#include <cutils/properties.h>
#include <errno.h>
#include <stdlib.h>
#include <ctype.h>
#include <string.h>
#include <sys/mount.h>
#include "roots.h"
#include "common.h"
#include "mt_partition.h"

static int num_volumes = 0;
static Volume* device_volumes = NULL;
static const char* gpt_path_prefix = NULL;

static int parse_options(char* options, Volume* volume) {
    char* option;
    while ((option = strtok(options, ","))) {
        options = NULL;

        if (strncmp(option, "length=", 7) == 0) {
            volume->length = strtoll(option+7, NULL, 10);
        } else {
            LOGE("bad option \"%s\"\n", option);
            return -1;
        }
    }
    return 0;
}

Volume* mt_volume_for_path(const char* path) {
    int i;
    for (i = 0; i < num_volumes; ++i) {
        Volume* v = device_volumes+i;
        int len = strlen(v->mount_point);
        if (strncmp(path, v->mount_point, len) == 0 &&
            (path[len] == '\0' || path[len] == '/')) {
            return v;
        }
    }
    return NULL;
}

char *mt_get_gpt_path(const char *mount_point)
{
    char *gpt_path = NULL;
    Volume* v = volume_for_mount_point(mount_point);
    if (v == NULL) {
        v = volume_for_mount_point("/boot");
        if (v == NULL) {
            LOGE("incorrect fstab information, /boot not find\n");
            return NULL;
        }
        int gpt_path_prefix_len = strlen(v->blk_device) - strlen("/boot") + strlen(mount_point);
        gpt_path = (char*) malloc(gpt_path_prefix_len + 1);
        strncpy(gpt_path, v->blk_device, strlen(v->blk_device) - strlen("/boot"));
        strncpy(gpt_path + strlen(v->blk_device) - strlen("/boot"), mount_point, strlen(mount_point));
        gpt_path[gpt_path_prefix_len] = '\0';
    } else {
        gpt_path = strdup(v->blk_device);
    }
    return gpt_path;
}

int mt_load_volume_table(struct fstab *fstab)
{
    int ret = 0;

    ret = set_fs_type_from_property();

    if (ret < 0 ) {
        LOGE("failed to load fs_type from property\n");
        return ret;
    }

    if (mt_get_phone_type() == FS_TYPE_EMMC) {
#if defined(MTK_GMO_ROM_OPTIMIZE)
        ret = fs_mgr_add_entry(fstab, "/sdcard", "vfat", "/dev/block/mmcblk1p1") | fs_mgr_add_entry(fstab, "/sdcard_dev2", "vfat", "/dev/block/mmcblk1");
#else
        char *intsd_path = mt_get_gpt_path("/intsd");
        ret = fs_mgr_add_entry(fstab, "/sdcard", "vfat", "/dev/block/mmcblk1p1");
        if (intsd_path) {
            ret |= fs_mgr_add_entry(fstab, "/sdcard_dev2", "vfat", intsd_path);
            free(intsd_path);
        }
#endif
    } else { // for non emmc platform
        ret = fs_mgr_add_entry(fstab, "/sdcard", "vfat", "/dev/block/mmcblk0p1") | fs_mgr_add_entry(fstab, "/sdcard_dev2", "vfat", "/dev/block/mmcblk0");
    }

    if (ret < 0 ) {
        LOGE("failed to add /sdcard entry to fstab\n");
        return ret;
    }

    return ret;
}

int mt_mount_sdcard(const char *target_blk_device, Volume* v) {
    int result = 0;
    result = mount(target_blk_device, v->mount_point, v->fs_type, v->flags, v->fs_options);
    if (result != 0) {
        //tonykuo 2014-04-11 Try mount mmcblk0 in case mmcblk0p1 failed
        if (strstr(target_blk_device, "/dev/block/mmcblk0"))
            result = mount("/dev/block/mmcblk0", v->mount_point, v->fs_type, v->flags, v->fs_options);
        else if (strstr(target_blk_device, "/dev/block/mmcblk1")) //Try mount mmcblk1 in case mmcblk1p1 failed
            result = mount("/dev/block/mmcblk1", v->mount_point, v->fs_type, v->flags, v->fs_options);
    }
    return result;
}

int mt_ensure_path_mounted(Volume* v) {
    int result = -1;
    //wschen 2013-05-03 workaround for slowly SD
    if (strstr(v->mount_point, "/sdcard") && (strstr(v->blk_device, "/dev/block/mmcblk1") || strstr(v->blk_device, "/dev/block/mmcblk0"))) {
        int retry = 0;
        for (; retry <= 3; retry++) {
            result = mt_mount_sdcard(v->blk_device, v);
            if (result == 0)
                return result;
            else
                sleep(1);
        }
        printf("Slowly SD retry failed (%s)\n", v->blk_device);
    }
    // mount sdcard fail, try to mount another blk_dev
    if (!strcmp(v->mount_point, "/sdcard")) {
        Volume* v_dev2 = volume_for_mount_point("/sdcard_dev2");
        if (v_dev2) {
            LOGE("failed to mount %s (%s); trying %s\n", v->blk_device, strerror(errno), v_dev2->blk_device);
            result = mt_mount_sdcard(v_dev2->blk_device, v);
        }
    }
    return result;
}

void mt_ensure_dev_ready(const char *mount_point) {
    int count=0;
    Volume* v = volume_for_mount_point(mount_point);
    if (v) {
        while ((count++ < 5) && (access(v->blk_device, R_OK) != 0)) {
            printf("no %s entry %s, count = %d\n", mount_point, v->blk_device, count);
            sleep(1);
        }
    } else {
        printf("Error: Retry fail %s partition not found\n",mount_point);
    }
}

void mt_fstab_translation_NAND(struct fstab *fstab) {
    int i;
    char *dev_name = NULL;
    Volume* v = volume_for_mount_point("/misc");
    if (v && (!strcmp(v->fs_type, "emmc")))
        return;
    printf("storage is NAND, need to translate fstab\n");
    for (i = 0; i < fstab->num_entries; ++i) {
        Volume* v = &fstab->recs[i];
        if (!strcmp(v->fs_type, "ubifs") || !strcmp(v->fs_type, "yaffs2") || !strcmp(v->fs_type, "mtd")) {
          if (v->blk_device) {
            dev_name = strrchr(v->blk_device, '@');
            dev_name = strdup(dev_name ? dev_name + 1 : v->blk_device);
            free(v->blk_device);
            v->blk_device = dev_name;
          }
          else  {
            printf("mt_fstab_translation_NAND fail\n");
          }
        }

    }
}
