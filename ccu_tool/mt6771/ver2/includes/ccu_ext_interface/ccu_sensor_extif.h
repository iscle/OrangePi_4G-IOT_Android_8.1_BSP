#ifndef __CCU_SENSOR_INTERFACE__
#define __CCU_SENSOR_INTERFACE__

#include "ccu_ext_interface/ccu_types.h"

/******************************************************************************
******************************************************************************/
typedef enum SENSOR_SCENARIO
{
    SENSOR_SCENARIO_PREVIEW             = 0x00,
    SENSOR_SCENARIO_CAPTURE             = 0x01,
    SENSOR_SCENARIO_NORMAL_VIDEO        = 0x02,     // a.k.a. Video Preview on CPU
    SENSOR_SCENARIO_HIGH_SPEED_VIDEO    = 0x03,
    SENSOR_SCENARIO_SLIM_VIDEO          = 0x04,
    SENSOR_SCENARIO_MAX                 = 0xFF      // Not a scenario value!
} SENSOR_SCENARIO_T;

typedef struct {
    MUINT32 i4OffsetX;      /* start offset of first PD block */
    MUINT32 i4OffsetY;
    MUINT32 i4PitchX;       /* PD block pitch */
    MUINT32 i4PitchY;
    MUINT32 i4PairNum;      /* PD pair num in one block */
    MUINT32 i4SubBlkW;      /* sub block width (one PD pair in one sub block) */
    MUINT32 i4SubBlkH;      /* sub block height */
    MUINT32 i4PosL[16][2];  /* left pd pixel position in one block*/
    MUINT32 i4PosR[16][2];  /* right pd pixel position in one block*/
    MUINT32 iMirrorFlip;    /* 0:IMAGE_NORMAL,1:IMAGE_H_MIRROR,2:IMAGE_V_MIRROR,3:IMAGE_HV_MIRROR*/
    MUINT32 i4BlockNumX;
    MUINT32 i4BlockNumY;
    MUINT32 i4LeFirst;     /* 1: 1st line is long exposure, 0: 1st line is short exposure*/
    MUINT32 i4Crop[10][2]; /* [scenario][crop] -> (xcrop, ycrop) */
} SET_PD_BLOCK_INFO_T, *PSET_PD_BLOCK_INFO_T;

/******************************************************************************
* To save code size, set WHICH_SENSOR_MAIN and WHICH_SENSOR_SUB to 0 and 1
* respectively, and remove the switch-case block that converts them to
* SENSOR_IDX_MAIN and SENSOR_IDX_SUB.
******************************************************************************/
typedef enum WHICH_SENSOR
{
    WHICH_SENSOR_NONE       = 0x00,
    WHICH_SENSOR_MAIN       = 0x01,     // Main sensor: rear camera
    WHICH_SENSOR_MAIN_2     = 0x02,     // Not used for now
    WHICH_SENSOR_SUB        = 0x10,     // Sub sensor: front camera
    WHICH_SENSOR_MAX        = 0xFF
} WHICH_SENSOR_T;


typedef struct SENSOR_INFO_IN
{
    //U32 u32SensorId;
    WHICH_SENSOR_T eWhichSensor;    // Main/Sub sensor
    U16 u16FPS;                     // Current FPS used by set_mode()
    SENSOR_SCENARIO_T eScenario;    // Preview, Video, HS Video, and etc.
    U8 *pu8BufferVAddr;             // virtual address of I2C DMA buffer
    U16 u16BufferLen;               // length of I2C DMA buffer
    U32 sensorI2cSlaveAddr;
} SENSOR_INFO_IN_T;

typedef struct SENSOR_INFO_OUT
{
    U16 u16TransferLen;             // I2C one-shot transfer length
    //U8 u8SlaveAddr;                 // I2C slave address of the image sensor
    U8 u8SupportedByCCU;            // 0: not supported, 1: supported
} SENSOR_INFO_OUT_T;

#endif
