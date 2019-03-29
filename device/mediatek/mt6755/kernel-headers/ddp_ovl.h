#ifndef _DDP_OVL_H_
#define _DDP_OVL_H_
#include "ddp_hal.h"
#include "ddp_data_type.h"

#define OVL_CASCADE_SUPPORT
#define OVL_MAX_WIDTH  (4095)
#define OVL_MAX_HEIGHT (4095)
#ifdef OVL_CASCADE_SUPPORT
#define OVL_LAYER_NUM  (8)
#else
#define OVL_LAYER_NUM  (4)
#endif

#define OVL_LAYER_NUM_PER_OVL 4

#endif
