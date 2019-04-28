/* For MTK android platform.
 *
 * mir3da.h - Linux kernel modules for 3-Axis Accelerometer
 *
 * Copyright (C) 2011-2013 MiraMEMS Sensing Technology Co., Ltd.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

#ifndef __MIR3DA_H__
#define __MIR3DA_H__
	 
#include <linux/ioctl.h>
#include <linux/kernel.h>
#include "mir3da_core.h"

#define DRI_VER                  		        "1.0"

#define MIR3DA_I2C_ADDR		                    0x4c//0x26<-> SD0=GND;0x27<-> SD0=High

#define MTK_ANDROID_M							1

#define MIR3DA_ACC_IOCTL_BASE                   88
#define IOCTL_INDEX_BASE                        0x00

#define MIR3DA_ACC_IOCTL_SET_DELAY              _IOW(MIR3DA_ACC_IOCTL_BASE, IOCTL_INDEX_BASE, int)
#define MIR3DA_ACC_IOCTL_GET_DELAY              _IOR(MIR3DA_ACC_IOCTL_BASE, IOCTL_INDEX_BASE+1, int)
#define MIR3DA_ACC_IOCTL_SET_ENABLE             _IOW(MIR3DA_ACC_IOCTL_BASE, IOCTL_INDEX_BASE+2, int)
#define MIR3DA_ACC_IOCTL_GET_ENABLE             _IOR(MIR3DA_ACC_IOCTL_BASE, IOCTL_INDEX_BASE+3, int)
#define MIR3DA_ACC_IOCTL_SET_G_RANGE            _IOW(MIR3DA_ACC_IOCTL_BASE, IOCTL_INDEX_BASE+4, int)
#define MIR3DA_ACC_IOCTL_GET_G_RANGE            _IOR(MIR3DA_ACC_IOCTL_BASE, IOCTL_INDEX_BASE+5, int)

#define MIR3DA_ACC_IOCTL_GET_COOR_XYZ           _IOW(MIR3DA_ACC_IOCTL_BASE, IOCTL_INDEX_BASE+22, int)
#define MIR3DA_ACC_IOCTL_CALIBRATION            _IOW(MIR3DA_ACC_IOCTL_BASE, IOCTL_INDEX_BASE+23, int)
#define MIR3DA_ACC_IOCTL_UPDATE_OFFSET          _IOW(MIR3DA_ACC_IOCTL_BASE, IOCTL_INDEX_BASE+24, int)

#endif /* !__MIR3DA_H__ */


