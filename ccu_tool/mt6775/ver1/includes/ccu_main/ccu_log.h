#ifndef __CCU_LOG_H__
#define __CCU_LOG_H__

#include "ccu_ext_interface/ccu_ext_interface.h"

extern MUINT32 g_pmu_overflow_cnt;

void log_initialize();
void log(char *msg);
void log_value(char *name, MINT32 value);
void log_flush_log_to_dram();

void log_log_test();

#endif