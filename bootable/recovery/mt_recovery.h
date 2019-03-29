/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/

#ifndef MT_RECOVERY_H_
#define MT_RECOVERY_H_

#include "device.h"

/* Function body declared in mt_recovery.cpp */
bool remove_mota_file(const char *file_name);
void write_result_file(const char *file_name, int result);

/* main(): MTK turnkey features */
int mt_main_write_result(int &status, const char *update_package);

#endif

