/*
* Copyright (C) 2016 MediaTek Inc.
* This program is free software; you can redistribute it and/or modify
* it under the terms of the GNU General Public License version 2 as
* published by the Free Software Foundation.
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
* See http://www.gnu.org/licenses/gpl-2.0.html for more details.
*/

#ifndef UFS_MTK_IOCTL_PRIVATE_H_
#define UFS_MTK_IOCTL_PRIVATE_H_

/* UTP QUERY Transaction Specific Fields OpCode */
enum query_opcode {
	UPIU_QUERY_OPCODE_READ_DESC	 = 0x1,
	UPIU_QUERY_OPCODE_READ_ATTR	 = 0x3,
	UPIU_QUERY_OPCODE_WRITE_ATTR = 0x4,
	UPIU_QUERY_OPCODE_READ_FLAG	 = 0x5,
};

/* UFS attribute idn for Query requests */
enum attr_idn {
	QUERY_ATTR_IDN_BOOT_LUN_EN       = 0x00,
	QUERY_ATTR_IDN_DEVICE_FFU_STATUS = 0x14,
};

/* UFS flag idn for Query Requests*/
enum flag_idn {
	QUERY_FLAG_IDN_PERMANENTLY_DISABLE_FW_UPDATE = 0xB,
};

/* UFS descriptor idn for Query requests */
enum desc_idn {
	QUERY_DESC_IDN_DEVICE		= 0x0,
	QUERY_DESC_IDN_STRING		= 0x5,
};

/* UFS descriptor size */
enum ufs_desc_max_size {
	QUERY_DESC_DEVICE_MAX_SIZE  = 0x40,
};

enum ufs_feature_support_list {
	UFS_FEATURES_FFU = 0x1,	/* bit 0 */
};

/* UFS device descriptor parameters */
enum device_desc_param {
	DEVICE_DESC_PARAM_MANF_ID        = 0x18,
	DEVICE_DESC_UFS_FEATURES_SUPPORT = 0x1F,
	DEVICE_DESC_PARAM_PRL            = 0x2A, /* Product Revision Level index in String Descriptor */
};

#endif /* UFS_MTK_IOCTL_PRIVATE_H_ */

