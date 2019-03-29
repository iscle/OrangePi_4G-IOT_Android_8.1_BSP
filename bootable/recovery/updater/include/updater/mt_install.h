/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
#ifndef MT_INSTALL_H
#define MT_INSTALL_H

#include <time.h>
#include <stdbool.h>
//#include "libubi.h"
//#include "ubiutils-common.h"
#include "edify/expr.h"

/* Link to bionic/libc/include/util.h but it is empty file */
//#include "util.h"
#include "roots.h"
#include "common.h"

#define CACHE_INDEX  0
#define DATA_INDEX   1
#define SYSTEM_INDEX 2
#define CUSTOM_INDEX 3

#define MT_FN_SUCCESS_EXIT      (1 << 0)
#define MT_FN_FAIL_EXIT         (1 << 1)
#define MT_FN_SUCCESS_CONTINUE  (1 << 2)
#define MT_FN_FAIL_CONTINUE     (1 << 3)
#define MT_FN_CONTINUE          (MT_FN_SUCCESS_CONTINUE | MT_FN_FAIL_CONTINUE)
#define MT_FN_EXIT              (MT_FN_SUCCESS_EXIT | MT_FN_FAIL_EXIT)

/* Handle sparse image state machine */
#define HANDL_SPARESE_INIT_STATE   0x1000
#define HANDLE_CHUNK_HEADER        0x1001
#define PREPARE_BLOCK_SIZE_BUFFER  0x1002
#define READ_BLOCK_SIZE_DATA_DONE  0x1003
#define WRITE_BLOCK_SIZE_DATA_DONE 0x1004
#define DONT_CARE_DATA             0x1005
#define WRITE_DONE                 0x1006

/* Common */
int remove_dir(const char *dirname);
void mt_init_partition_type(void);
char *mt_get_location(char *mount_point);

/* UnmountFn() */
int mt_UnmountFn_ubifs(char *mount_point);

/* MountFn() */
int mt_MountFn(char **mount_point, char **result, char **fs_type, char **location,
        char **partition_type, const char *name, char* mount_options, bool has_mount_options, State* state);

/* FormatFn() */
int mt_FormatFn(char **mount_point, char **result, char **fs_type, char **location,
        char **partition_type, const char *name, const char *fs_size);

/* RebootNowFn() */
Value* mt_RebootNowFn(const char* name, State* state, char** filename);

/* SetStageFn(), GetStageFn() */
Value* mt_SetStageFn(const char* name, State* state, char** filename, char** stagestr);
Value* mt_GetStageFn(const char* name, State* state, char** filename, char *buffer);

/* Register Functions */
void mt_RegisterInstallFunctions(void);

#endif

