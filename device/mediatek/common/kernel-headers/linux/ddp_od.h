#ifndef __DDP_OD_H__
#define __DDP_OD_H__

#include <linux/ioctl.h>

/* OD */
typedef struct {
    unsigned int size;
    unsigned int type;
    unsigned int ret;
    unsigned int param0;
    unsigned int param1;
    unsigned int param2;
    unsigned int param3;
} DISP_OD_CMD;

typedef enum{
	OD_CTL_READ_REG,
	OD_CTL_WRITE_REG,
	OD_CTL_ENABLE_DEMO_MODE,
	OD_CTL_RUN_TEST,
	OD_CTL_WRITE_TABLE,
	OD_CTL_CMD_NUM,
	OD_CTL_ENABLE
} DISP_OD_CMD_TYPE;

typedef enum{
	OD_CTL_ENABLE_OFF,
	OD_CTL_ENABLE_ON
} DISP_OD_ENABLE_STAGE;

#define OD_CTL_ENABLE_DELAY 3

/* OD */
#define DISP_IOCTL_MAGIC        'x'
#define DISP_IOCTL_OD_CTL           _IOWR    (DISP_IOCTL_MAGIC, 80 , DISP_OD_CMD)
#define DISP_IOCTL_OD_SET_ENABLED   _IOWR    (DISP_IOCTL_MAGIC, 81 , int)

#endif
