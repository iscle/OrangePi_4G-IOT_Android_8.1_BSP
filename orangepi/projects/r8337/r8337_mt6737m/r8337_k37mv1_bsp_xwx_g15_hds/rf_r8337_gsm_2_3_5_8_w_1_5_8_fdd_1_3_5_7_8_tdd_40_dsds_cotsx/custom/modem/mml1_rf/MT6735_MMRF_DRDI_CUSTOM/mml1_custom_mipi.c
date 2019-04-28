/*****************************************************************************
*  Copyright Statement:
*  --------------------
*  This software is protected by Copyright and the information contained
*  herein is confidential. The software may not be copied and the information
*  contained herein may not be used or disclosed except with the written
*  permission of MediaTek Inc. (C) 2014
*
*******************************************************************************/

/*******************************************************************************
 *
 * Filename:
 * ---------
 * mml1_custom_mipi.c
 *
 * Project:
 * --------
 *   MT6735 EVB
 *
 * Description:
 * ------------
 *   Multi-Mode RF Central Functions
 *
 * Author:
 * -------
 *
 *
 *==============================================================================
 *******************************************************************************/

#include "kal_general_types.h"
#include "mml1_custom_mipi.h"
#include "mml1_custom_drdi.h"

/***************************************************************************
 * MML1 MIPI Initial CW Table Data
 ***************************************************************************/
MML1_MIPI_INITIAL_CW_T MML1_MIPI_INITIAL_CW[MML1_MIPI_MAX_INITIAL_CW_NUM] =
{
   // elm type    , port_sel       , data_seq  ,    USID          , addr , data  , wait_time(us)
   {MML1_MIPI_ASM , MML1_MIPI_PORT0, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_ASM , MML1_MIPI_PORT1, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
};

//For DRDI different set
#if (IS_MML1_DRDI_ENABLE && IS_MML1_DRDI_REMAP_ENABLE)
MML1_MIPI_INITIAL_CW_T MML1_MIPI_INITIAL_CW_ReMap_set0[MML1_MIPI_MAX_INITIAL_CW_NUM] =
{
   // elm type    , port_sel       , data_seq  ,    USID          , addr , data  , wait_time(us)
   {MML1_MIPI_ASM , MML1_MIPI_PORT0, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_ASM , MML1_MIPI_PORT1, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
};
#else
MML1_MIPI_INITIAL_CW_T MML1_MIPI_INITIAL_CW_set0[MML1_MIPI_MAX_INITIAL_CW_NUM] =
{
   // elm type    , port_sel       , data_seq  ,    USID          , addr , data  , wait_time(us)
   {MML1_MIPI_ASM , MML1_MIPI_PORT0, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_ASM , MML1_MIPI_PORT1, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
};

MML1_MIPI_INITIAL_CW_T MML1_MIPI_INITIAL_CW_set1[MML1_MIPI_MAX_INITIAL_CW_NUM] =
{
   // elm type    , port_sel       , data_seq  ,    USID          , addr , data  , wait_time(us)
   {MML1_MIPI_ASM , MML1_MIPI_PORT0, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_ASM , MML1_MIPI_PORT1, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
};

MML1_MIPI_INITIAL_CW_T MML1_MIPI_INITIAL_CW_set2[MML1_MIPI_MAX_INITIAL_CW_NUM] =
{
   // elm type    , port_sel       , data_seq  ,    USID          , addr , data  , wait_time(us)
   {MML1_MIPI_ASM , MML1_MIPI_PORT0, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_ASM , MML1_MIPI_PORT1, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
};

MML1_MIPI_INITIAL_CW_T MML1_MIPI_INITIAL_CW_set3[MML1_MIPI_MAX_INITIAL_CW_NUM] =
{
   // elm type    , port_sel       , data_seq  ,    USID          , addr , data  , wait_time(us)
   {MML1_MIPI_ASM , MML1_MIPI_PORT0, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_ASM , MML1_MIPI_PORT1, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
};

MML1_MIPI_INITIAL_CW_T MML1_MIPI_INITIAL_CW_set4[MML1_MIPI_MAX_INITIAL_CW_NUM] =
{
   // elm type    , port_sel       , data_seq  ,    USID          , addr , data  , wait_time(us)
   {MML1_MIPI_ASM , MML1_MIPI_PORT0, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_ASM , MML1_MIPI_PORT1, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
};

MML1_MIPI_INITIAL_CW_T MML1_MIPI_INITIAL_CW_set5[MML1_MIPI_MAX_INITIAL_CW_NUM] =
{
   // elm type    , port_sel       , data_seq  ,    USID          , addr , data  , wait_time(us)
   {MML1_MIPI_ASM , MML1_MIPI_PORT0, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_ASM , MML1_MIPI_PORT1, MML1_REG_W, MIPI_USID_INIT0  , {0x1C, 0x38} , 0 }, // Broadcast ID, Standard MIPI, PM_TRIG = normal mode
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
   {MML1_MIPI_END_PATTERN,0,0,0,{0,0},0},
};
#endif

/***************************************************************************
 * MML1 MIPI Change USDI Table Data
 ***************************************************************************/
MML1_MIPI_USID_CHANGE_T MML1_MIPI_USID_CHANGE_TABLE[MML1_MIPI_MAX_USID_CHANGE_NUM] =
{
   // USID change type   , port_sel        , current USID , PRODUCT_ID , MANUFACTORY_ID   new USID
   {USID_REG_W           , MML1_MIPI_PORT0 , 0xF          , 0x85        , 0x1A5        , 0xE     },
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
};

//For DRDI different set
#if (IS_MML1_DRDI_ENABLE && IS_MML1_DRDI_REMAP_ENABLE)
MML1_MIPI_USID_CHANGE_T MML1_MIPI_USID_CHANGE_TABLE_ReMap_set0[MML1_MIPI_MAX_USID_CHANGE_NUM] =
{
   // USID change type   , port_sel        , current USID , PRODUCT_ID , MANUFACTORY_ID   new USID
   {USID_REG_W           , MML1_MIPI_PORT0 , 0xF          , 0x85        , 0x1A5        , 0xE     },
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
};
#else
MML1_MIPI_USID_CHANGE_T MML1_MIPI_USID_CHANGE_TABLE_set0[MML1_MIPI_MAX_USID_CHANGE_NUM] =
{
   // USID change type   , port_sel        , current USID , PRODUCT_ID , MANUFACTORY_ID   new USID
   {USID_REG_W           , MML1_MIPI_PORT0 , 0xF          , 0x85        , 0x1A5        , 0xE     },
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
};

MML1_MIPI_USID_CHANGE_T MML1_MIPI_USID_CHANGE_TABLE_set1[MML1_MIPI_MAX_USID_CHANGE_NUM] =
{
   // USID change type   , port_sel        , current USID , PRODUCT_ID , MANUFACTORY_ID   new USID
   {USID_REG_W           , MML1_MIPI_PORT0 , 0xF          , 0x85        , 0x1A5        , 0xE     },
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
};

MML1_MIPI_USID_CHANGE_T MML1_MIPI_USID_CHANGE_TABLE_set2[MML1_MIPI_MAX_USID_CHANGE_NUM] =
{
   // USID change type   , port_sel        , current USID , PRODUCT_ID , MANUFACTORY_ID   new USID
   {USID_REG_W           , MML1_MIPI_PORT0 , 0xF          , 0x85        , 0x1A5        , 0xE     },
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
};

MML1_MIPI_USID_CHANGE_T MML1_MIPI_USID_CHANGE_TABLE_set3[MML1_MIPI_MAX_USID_CHANGE_NUM] =
{
   // USID change type   , port_sel        , current USID , PRODUCT_ID , MANUFACTORY_ID   new USID
   {USID_REG_W           , MML1_MIPI_PORT0 , 0xF          , 0x85        , 0x1A5        , 0xE     },
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
};

MML1_MIPI_USID_CHANGE_T MML1_MIPI_USID_CHANGE_TABLE_set4[MML1_MIPI_MAX_USID_CHANGE_NUM] =
{
   // USID change type   , port_sel        , current USID , PRODUCT_ID , MANUFACTORY_ID   new USID
   {USID_REG_W           , MML1_MIPI_PORT0 , 0xF          , 0x85        , 0x1A5        , 0xE     },
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
};

MML1_MIPI_USID_CHANGE_T MML1_MIPI_USID_CHANGE_TABLE_set5[MML1_MIPI_MAX_USID_CHANGE_NUM] =
{
   // USID change type   , port_sel        , current USID , PRODUCT_ID , MANUFACTORY_ID   new USID
   {USID_REG_W           , MML1_MIPI_PORT0 , 0xF          , 0x85        , 0x1A5        , 0xE     },
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
   {USID_NULL,0,0,0,0,0},
};
#endif

