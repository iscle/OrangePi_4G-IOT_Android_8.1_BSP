/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/

#include <stdio.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <stdbool.h>
#include <string.h>
#include <errno.h>
#include <mtd/mtd-user.h>
#if !defined(ARCH_X86)
#include <linux/mmc/sd_misc.h>
#include <linux/scsi/ufs/ufs-mtk-ioctl.h>
#endif
#include "edify/expr.h"
#include "bootloader.h"
#include "updater/updater.h"
#include "updater/mt_install.h"
#include "mt_gpt.h"
#include "mt_pmt.h"
#include "mt_partition.h"
#include "otautil/DirUtil.h"


#include <memory>
#include <string>
#include <vector>
#include <ext4_utils/make_ext4fs.h>
#include <ext4_utils/wipe.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parsedouble.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <selinux/label.h>
#include <selinux/selinux.h>
#include <ziparchive/zip_archive.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

#define PRELOADER_OFFSET_EMMC   (0x800)
#define PRELOADER_OFFSET_UFS   (0x1000)
#define MAX_LOADER_SIZE (512*1024)

static int preloader_header_size;
static char *preloader_dev_path = NULL;
static char *preloader2_dev_path = NULL;

/* external functions defined in install.c */
extern void uiPrintf(State* state, const char* format, ...);

static bool mt_is_mountable(const char *mount_point)
{
    return (
        !strcmp(mount_point, "/system") ||
        !strcmp(mount_point, "/data") ||
        !strcmp(mount_point, "/cache") ||
        !strcmp(mount_point, "/custom"));
}

// mount/attach ubifs/yaffs volumes
static int mt_MountFn_NAND(char **mount_point, char **result, char **fs_type, char **location,
    char **partition_type, const char *name, char* mount_options, bool has_mount_options, State* state)
{
#if defined(UBIFS_SUPPORT)
    //Attatch UBI device & Make UBI volum
    int n = -1;
    int ret;
    n = ubi_attach_mtd_user(*mount_point);

    if ((n != -1) && (n < 4)) {
      //attached successful, do nothing
    } else {
      ErrorAbort(state, "failed to attach %s\n", *location);
      return MT_FN_FAIL_EXIT;
    }

    //Mount UBI volume
    const unsigned long flags = MS_NOATIME | MS_NODEV | MS_NODIRATIME;
    char tmp[64];
    sprintf(tmp, "/dev/ubi%d_0", n);
    wait_for_file(tmp, 5);
    ret = mount(tmp, *mount_point, *fs_type, flags, "");
    if (ret < 0) {
      ubi_detach_dev(n);
      ErrorAbort(state, "failed to mount %s\n", *mount_point);
      *result = strdup("");
      return MT_FN_FAIL_EXIT;
    } else if (ret == 0) {
      *result = *mount_point;
    }
    //Volume  successfully mounted
    fprintf(stderr, "UBI mount successful %s\n", *mount_point);
#endif
    return MT_FN_SUCCESS_EXIT;
}
static int mt_MountFn_eMMC(char **mount_point, char **result, char **fs_type, char **location,
    char **partition_type, const char *name, char* mount_options, bool has_mount_options, State* state)
{
    *fs_type = strdup("ext4");
    free(*partition_type);
    *partition_type = strdup("EMMC");

    if (mount(*location, *mount_point, *fs_type, MS_NOATIME | MS_NODEV | MS_NODIRATIME, has_mount_options ? mount_options : "") < 0) {
        uiPrintf(state, "%s: failed to mount %s at %s: %s\n", name, *location, *mount_point, strerror(errno));
        *result = strdup("");
        return MT_FN_FAIL_EXIT;
    } else {
        *result = *mount_point;
    }
    return MT_FN_SUCCESS_EXIT;
}

//  Mount ext4/ubifs/yaffs volumes - /system, /data, /cache, /custom
int mt_MountFn(char **mount_point, char **result, char **fs_type, char **location,
    char **partition_type, const char *name, char* mount_options, bool has_mount_options, State* state)
{
    int ret = MT_FN_SUCCESS_CONTINUE;
    char *dev_path = NULL;
    if (mt_is_mountable(*mount_point)) {
        dev_path=get_partition_path(*location);
        free(*location);
        *location = dev_path;
        if (mt_get_phone_type() == FS_TYPE_MTD) {
            ret=mt_MountFn_NAND(mount_point, result, fs_type, location,
                partition_type, name, mount_options, has_mount_options, state);
        } else {
            ret=mt_MountFn_eMMC(mount_point, result, fs_type, location,
                partition_type, name, mount_options, has_mount_options, state);
        }
    }
    return ret;
}

int mt_UnmountFn_ubifs(char *mount_point)
{
#if defined(UBIFS_SUPPORT)
    int ubi_num;

    if (!(!strcmp(mount_point, "/system") || !strcmp(mount_point, "/data") || !strcmp(mount_point, "/cache") || !strcmp(mount_point, "/.cache") || !strcmp(mount_point, "/custom"))) {
        LOGE("Invalid mount_point: %s\n", mount_point);
        return MT_FN_FAIL_EXIT;
    }

    if (!strcmp(mount_point, "/system")) {
        ubi_num = 0;
    }

    if (!strcmp(mount_point, "/data")) {
        ubi_num = 1;
    }

    if (!strcmp(mount_point, "/cache") || !strcmp(mount_point, "/.cache")) {
        ubi_num = 2;
    }

    if (!strcmp(mount_point, "/custom")) {
        //ubi_num = 3;
    }
    fprintf(stderr, "detaching ubi%d\n", ubi_num);
    if (ubi_detach_dev(ubi_num) == -1) {
        fprintf(stderr, "detaching ubi%d failed\n", ubi_num);
        return MT_FN_FAIL_CONTINUE;
    }
#endif
    return MT_FN_SUCCESS_CONTINUE;
}

//  Format ext4/ubifs/yaffs volumes - /system, /data, /cache, /custom
int mt_FormatFn(char **mount_point, char **result, char **fs_type, char **location,
    char **partition_type, const char *name, const char *fs_size)
{
    if (mt_is_mountable(*mount_point)) {
        // wschen 2014-02-21
        // call umount first, this will prevent last time FULL OTA upgrade fail, if already mount /system
        if (umount(*mount_point) == -1) {
            fprintf(stderr, "umount %s fail(%s)\n", *mount_point, strerror(errno));
        }

        char *dev_path=get_partition_path(*location);
        free(*location);
        *location = dev_path;
        int status = make_ext4fs(*location, atoll(fs_size), *mount_point, sehandle);
        if (status != 0) {
            fprintf(stderr, "%s: make_ext4fs failed (%d) on %s", name, status, *location);
            *result = strdup("");
            return MT_FN_FAIL_EXIT;
        }
        *result = *location;
    } else {
        if (strcmp(*fs_type, "ext4") == 0) {
            int status = make_ext4fs(*location, atoll(fs_size), *mount_point, sehandle);
            if (status != 0) {
                fprintf(stderr, "%s: make_ext4fs failed (%d) on %s", name, status, *location);
                *result = strdup("");
                return MT_FN_FAIL_EXIT;
            }
            *result = *location;
        }
        else {
            fprintf(stderr, "%s: unsupported fs_type \"%s\" partition_type \"%s\"", name, *fs_type, *partition_type);
        }
    }
    return MT_FN_SUCCESS_CONTINUE;
}

Value* mtDeleteFn(const char* name, State* state, const std::vector<std::unique_ptr<Expr>>& argv) {
    std::string path;
    if(!Evaluate(state, argv[0], &path)) {
       fprintf(stderr, "mtDeleteFn fail name is %s: ,path is %s\n", name, path.c_str());
       return NULL;
    }

    int success = 0;
    if (unlink(path.c_str()) == 0)
        ++success;

    char buffer[10];
    snprintf(buffer, sizeof(buffer), "%d", success);
    return StringValue(strdup(buffer));
}


Value* mtGetUpdateStageFn(const char* name, State* state, const std::vector<std::unique_ptr<Expr>>& argv) {
    char buf[64];
    UpdaterInfo* ui = (UpdaterInfo*)(state->cookie);
    std::vector<std::string> args;
    if (!ReadArgs(state, argv, &args))
        return NULL;
    const std::string& filename = args[0];
    FILE *fp = fopen(filename.c_str(), "r");
    strncpy(buf, "0", sizeof(buf)-1);
    buf[sizeof(buf)-1] = '\0';
    if (fp) {
        fgets(buf, sizeof(buf), fp);
        fclose(fp);
    }
    return StringValue(strdup(buf));
}

Value* mtSetUpdateStageFn(const char* name, State* state, const std::vector<std::unique_ptr<Expr>>& argv) {
    UpdaterInfo* ui = (UpdaterInfo*)(state->cookie);
    std::vector<std::string> args;
    if (!ReadArgs(state, argv, &args))
        return NULL;
    const std::string& filename = args[0];
    const std::string& buf = args[1];
    FILE *fp = fopen(filename.c_str(), "w+");
    if (fp) {
        fputs(buf.c_str(), fp);
        fclose(fp);
    }
    return StringValue(filename);
}

static int mt_update_erase_part(State* state, const char *partition_name) {
    char* dev_name = get_partition_path(partition_name);

    int fd = open(dev_name, O_WRONLY | O_SYNC);
    if (fd != -1) {
        char *buf = (char *)malloc(1024);
        memset(buf, 0, 1024);
        if (write(fd, buf, 1024) == -1) {
            fprintf(stderr, "write to %s fail\n", dev_name);
            close(fd);
            free(dev_name);
            free(buf);
            return 1;
        }
        printf("write done\n");
        close(fd);
        free(buf);
    } else {
        fprintf(stderr, "open %s fail\n", dev_name);
        free(dev_name);
        return 1;
    }
    free(dev_name);
    return 0;
}

int ufs_set_active_boot_part(int boot)
{
    struct ufs_ioctl_query_data idata;
    unsigned char buf[1];
    int fd, ret = 0;

    fd = open("/dev/block/sdc", O_RDWR);

    if (fd < 0) {
        printf("%s: open device failed, err: %d\n", __func__, fd);
        ret = -1;
        goto out;
    }

    buf[0] = boot;           /* 1: BootLU A, 2: BootLU B */

    idata.opcode = UPIU_QUERY_OPCODE_WRITE_ATTR;
    idata.idn = QUERY_ATTR_IDN_BOOT_LUN_EN;
    idata.idx = 0;
    idata.buf_ptr = &buf[0];
    idata.buf_byte = 1;

    ret = ioctl(fd, UFS_IOCTL_QUERY, &idata);
    if(ret < 0)
        printf("ufs_set boot_part fail: %s\n", strerror(errno));
out:
    if(fd >= 0)
        close(fd);
    return ret;
}

static int mt_update_active_part(State* state, const char* from_partition, const char *to_partition)
{
    if ((!strncmp(from_partition, "tee", strlen("tee")) && ((mt_get_phone_type() == FS_TYPE_EMMC) || (mt_get_phone_type() == FS_TYPE_UFS))) // tee1 and tee2 on EMMC not use active bit
        || ((mt_get_phone_type() == FS_TYPE_MTD) && (!strncasecmp(from_partition, "preloader", strlen("preloader"))))  // preloader on NAND not use active bit
        ) {
        if (!strcmp(from_partition, "tee1") || !strcasecmp(from_partition, "preloader")) // only do erase when main partition to alt partition
            return mt_update_erase_part(state, from_partition);
    } else if((mt_get_phone_type() == FS_TYPE_UFS) && !strncasecmp(from_partition, "preloader", strlen("preloader")))  {  // preloader on UFS
        unsigned int bootpart = 0;
        if (!strcmp(to_partition, "preloader"))
            bootpart = 1;
        else
            bootpart = 2;
        return ufs_set_active_boot_part(bootpart);
    } else if ((mt_get_phone_type() == FS_TYPE_EMMC) && !strncasecmp(from_partition, "preloader", strlen("preloader"))) { // preloader on EMMC just switch register
        struct msdc_ioctl st_ioctl_arg;
        unsigned int bootpart = 0;
        int fd = open("/dev/misc-sd", O_RDWR);
        if (fd >= 0) {
            memset(&st_ioctl_arg,0,sizeof(struct msdc_ioctl));
            st_ioctl_arg.host_num = 0;
            st_ioctl_arg.opcode = MSDC_SET_BOOTPART;
            st_ioctl_arg.total_size = 1;
            if (!strcmp(to_partition, "preloader"))
                bootpart = EMMC_BOOT1_EN;
            else
                bootpart = EMMC_BOOT2_EN;
            st_ioctl_arg.buffer = &bootpart;
            int ret = ioctl(fd, MSDC_SET_BOOTPART, &st_ioctl_arg);
            if (ret < 0)
                printf("set boot_part fail: %s\n", strerror(errno));
            printf("switch bootpart to  = %d, ret = %d\n", bootpart, ret);
            close(fd);
        } else {
            uiPrintf(state, "set boot part fail, can not open misc-sd\n");
        }
    } else if ((mt_get_phone_type() == FS_TYPE_MNTL) || (mt_get_phone_type() == FS_TYPE_MTD)) {
        // need to set to_partition active bit to 1 and then set from_partition active bit to 0
        int ret = mt_pmt_update_active_part(to_partition, 1) | mt_pmt_update_active_part(from_partition, 0);
        return ret;
    } else if (support_gpt()) {
        // need to set to_partition active bit to 1 and then set from_partition active bit to 0
        int ret = mt_gpt_update_active_part(to_partition, 1) | mt_gpt_update_active_part(from_partition, 0);
        return ret;
    } else {
        // TODO: pmt type active bit switch
    }
    return 1;
}

Value* mtShowUpdateStageFn(const char* name, State* state, const std::vector<std::unique_ptr<Expr>>& argv) {
    Value* retval = mtGetUpdateStageFn(name, state, argv);
    printf("Current Stage is %s\n", retval->data.c_str());
    return retval;
}

Value* mtSwitchActiveFn(const char* name, State* state, const std::vector<std::unique_ptr<Expr>>& argv) {
    if (argv.size() != 2)
        return ErrorAbort(state, kArgsParsingFailure,"%s() expects 2 arg, got %zu", name, argv.size());

    std::vector<std::string> args;
    if (!ReadArgs(state, argv, &args))
         return NULL;
    const std::string& from_partition = args[0];
    const std::string& to_partition = args[1];

    mt_update_active_part(state, from_partition.c_str(), to_partition.c_str());
    printf("Switch %s active to %s\n", from_partition.c_str(), to_partition.c_str());
    return StringValue(from_partition);
}

Value* mtSetEmmcWR(const char * name, State* state, const std::vector<std::unique_ptr<Expr>>& argv)
{
    if(mt_get_phone_type() == FS_TYPE_UFS)
        return StringValue(strdup("t"));

    std::string path;
    if(!Evaluate(state, argv[0], &path)) {
       fprintf(stderr, "mtSetEmmcWR fail name is %s: ,path is %s\n", name, path.c_str());
       return NULL;
    }

    char const *buf = "0\n";
    int ret = 0;
    int fd = open(path.c_str(), O_WRONLY | O_TRUNC);
    if (fd >= 0) {
        write(fd, buf, strlen(buf));
        close(fd);
        ret = 1;
    } else {
        printf("can not open %s to disable force_ro\n", path.c_str());
    }
    return StringValue(ret == 1 ? strdup("t") : strdup(""));
}

void mt_RegisterInstallFunctions(void)
{
    mt_init_partition_type();

    RegisterFunction("get_mtupdate_stage", mtGetUpdateStageFn);
    RegisterFunction("set_mtupdate_stage", mtSetUpdateStageFn);
    RegisterFunction("show_mtupdate_stage", mtShowUpdateStageFn);
    RegisterFunction("switch_active", mtSwitchActiveFn);
    RegisterFunction("delete", mtDeleteFn);
    RegisterFunction("set_emmc_writable", mtSetEmmcWR);
}

Value* mt_RebootNowFn(const char* name, State* state, char** filename)
{
    char *dev_path = get_partition_path(*filename);
    free(*filename);
    *filename = dev_path;

    int fd = open(*filename, O_RDWR | O_SYNC);
    struct bootloader_message bm;
    if (fd < 0)  {
        return ErrorAbort(state, kFileOpenFailure,"%s() open %s fail", name, *filename);
    }
    int count = read(fd, &bm, sizeof(bm));
    if (count != sizeof(bm)) {
        close(fd);
        return ErrorAbort(state, kFreadFailure,"%s() read %s fail, count=%d %s", name, *filename, count, strerror(errno));
    }
    memset(bm.command, 0, sizeof(bm.command));
    lseek(fd, 0, SEEK_SET);
    count = write(fd, &bm, sizeof(bm));
    if (count != sizeof(bm)) {
        close(fd);
        return ErrorAbort(state, kFwriteFailure,"%s() write fail, count=%d", name, count);
    }
    if (close(fd) != 0) {
        return ErrorAbort(state, kVendorFailure,"%s() close %s fail", name, *filename);
    }
    sync();

    return NULL;
}

Value* mt_SetStageFn(const char* name, State* state, char** filename, char** stagestr)
{
    char *dev_path = get_partition_path(*filename);
    free(*filename);
    *filename = dev_path;

    //misc write needs aligment
    int fd = open(*filename, O_RDWR | O_SYNC);
    struct bootloader_message bm;
    if (fd < 0)  {
        return ErrorAbort(state, kFileOpenFailure,"%s() open %s fail", name, *filename);
    }
    int count = read(fd, &bm, sizeof(bm));
    if (count != sizeof(bm)) {
        close(fd);
        return ErrorAbort(state, kFreadFailure, "%s() read %s fail, count=%d %s", name, *filename, count, strerror(errno));
    }
    memset(bm.stage, 0, sizeof(bm.stage));
    snprintf(bm.stage, sizeof(bm.stage) - 1, "%s", *stagestr);

    lseek(fd, 0, SEEK_SET);
    count = write(fd, &bm, sizeof(bm));
    if (count != sizeof(bm)) {
        close(fd);
        return ErrorAbort(state, kFwriteFailure, "%s() write %s fail, count=%d %s", name, *filename, count, strerror(errno));
    }
    if (close(fd) != 0) {
        return ErrorAbort(state, kVendorFailure, "%s() close %s fail", name, *filename);
    }
    sync();

    return NULL;
}

Value* mt_GetStageFn(const char* name, State* state, char** filename, char *buffer)
{
    char *dev_path = get_partition_path(*filename);
    free(*filename);
    *filename = dev_path;

    int fd = open(*filename, O_RDONLY);
    struct bootloader_message bm;

    if (fd < 0)
        return ErrorAbort(state, kFileOpenFailure,"%s() open %s fail", name, *filename);

    int count = read(fd, &bm, sizeof(bm));
    if (count != sizeof(bm)) {
        close(fd);
        return ErrorAbort(state, kFreadFailure,"%s() read fail, count=%d", name, count);
    }
    if (close(fd) != 0) {
        return ErrorAbort(state, kVendorFailure,"%s() close %s fail", name, *filename);
    }

    memcpy(buffer, bm.stage, sizeof(bm.stage));

    return NULL;
}

