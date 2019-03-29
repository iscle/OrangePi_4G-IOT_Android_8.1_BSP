/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* Vboot/crossystem interface */

#define LOG_TAG "fwtool"

#include <endian.h>
#include <errno.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "ec_commands.h"
#include "flash_device.h"
#include "fmap.h"
#include "update_log.h"
#include "vboot_struct.h"
#include "gbb_header.h"

/* ---- VBoot information passed by the firmware through the device-tree ---- */

/* Base name for firmware FDT files */
#define FDT_BASE_PATH "/proc/device-tree/firmware/chromeos"

char *fdt_read_string(const char *prop)
{
	char filename[PATH_MAX];
	FILE *file;
	size_t size;
	char *data;

	snprintf(filename, sizeof(filename), FDT_BASE_PATH "/%s", prop);
	file = fopen(filename, "r");
	if (!file) {
		ALOGD("Unable to open FDT property %s\n", prop);
		return NULL;
	}
	fseek(file, 0, SEEK_END);
	size = ftell(file);
	data = malloc(size + 1);
	if (!data) {
		goto out_close;
	}
	data[size] = '\0';

	rewind(file);
	if (fread(data, 1, size, file) != size) {
		ALOGD("Unable to read FDT property %s\n", prop);
		goto out_free;
	}
	fclose(file);

	return data;

out_free:
	free(data);

out_close:
	fclose(file);
	return NULL;
}

uint32_t fdt_read_u32(const char *prop)
{
	char filename[PATH_MAX];
	FILE *file;
	int data = 0;

	snprintf(filename, sizeof(filename), FDT_BASE_PATH "/%s", prop);
	file = fopen(filename, "r");
	if (!file) {
		ALOGD("Unable to open FDT property %s\n", prop);
		return -1U;
	}
	if (fread(&data, 1, sizeof(data), file) != sizeof(data)) {
		ALOGD("Unable to read FDT property %s\n", prop);
		fclose(file);
		return -1U;
	}
	fclose(file);

	return ntohl(data); /* FDT is network byte order */
}

char vboot_get_mainfw_act(void)
{
	VbSharedDataHeader *shd = (void *)fdt_read_string("vboot-shared-data");
	char v;

	if (!shd || shd->magic != VB_SHARED_DATA_MAGIC) {
		ALOGD("Cannot retrieve VBoot shared data\n");
		if (shd)
			free(shd);
		return 'E'; /* Error */
	}

	switch(shd->firmware_index) {
	case 0:
		v = 'A'; /* RW_A in use */
		break;
	case 1:
		v = 'B'; /* RW_B in use */
		break;
	case 0xFF:
		v = 'R'; /* Recovery/RO in use */
		break;
	default:
		ALOGD("Invalid firmware index : %02x\n", shd->firmware_index);
		v = 'E'; /* Error */
	}

	free(shd);
	return v;
}

/* ---- Flash Maps handling ---- */

off_t fmap_scan_offset(struct flash_device *dev, off_t end)
{
	struct fmap h;
	uint32_t off = end - (end % 64); /* start on a 64-byte boundary */
	int res;

	/*
	 * Try to find the FMAP signature at 64-byte boundaries
         * starting from the end.
	 */
	do {
		off -= 64;
		res = flash_read(dev, off, &h, sizeof(h.signature));
		if (res)
			break;
		if (!memcmp(&h.signature, FMAP_SIGNATURE, sizeof(h.signature)))
			break;
	} while (off);

	return off;
}

struct fmap *fmap_load(struct flash_device *dev, off_t offset)
{
	struct fmap hdr;
	struct fmap *fmap;
	size_t size;
	int res;

	ALOGD("Searching FMAP @0x%08lx\n", offset);
	res = flash_read(dev, offset, &hdr, sizeof(hdr));
	if (res) {
		ALOGD("Cannot read FMAP header\n");
		return NULL;
	}

	if (memcmp(&hdr.signature, FMAP_SIGNATURE, sizeof(hdr.signature))) {
		ALOGD("Cannot find FMAP\n");
		return NULL;
	}

	size = sizeof(struct fmap) + hdr.nareas * sizeof(struct fmap_area);
	fmap = malloc(size);

	res = flash_read(dev, offset, fmap, size);
	if (res) {
		ALOGD("Cannot read FMAP\n");
		free(fmap);
		return NULL;
	}

	return fmap;
}

int fmap_get_section_offset(struct flash_device *dev, const char *name,
			    off_t *offset)
{
	int i;
	struct fmap *fmap = flash_get_fmap(dev);
	if (!fmap)
		return -1;

	if (name) {
		for (i = 0; i < fmap->nareas; i++)
			if (!strcmp(name, (const char*)fmap->areas[i].name))
				break;

		if (i == fmap->nareas) {
			ALOGD("Cannot find section '%s'\n", name);
			return -1;
		}

		*offset = fmap->areas[i].offset;
	} else {
		*offset = 0;
	}

	return 0;
}

void *fmap_read_section(struct flash_device *dev,
			const char *name, size_t *size, off_t *offset)
{
	int i, r;
	struct fmap *fmap = flash_get_fmap(dev);
	void *data;
	off_t start_offset;

	if (!fmap)
		return NULL;

	if (name) {
		for (i = 0; i < fmap->nareas; i++)
			if (!strcmp(name, (const char*)fmap->areas[i].name))
				break;
		if (i == fmap->nareas) {
			ALOGD("Cannot find section '%s'\n", name);
			return NULL;
		}
		*size = fmap->areas[i].size;
		start_offset = fmap->areas[i].offset;
	} else {
		*size = flash_get_size(dev);
		start_offset = 0;
	}

	data = malloc(*size);
	if (!data)
		return NULL;

	r = flash_read(dev, start_offset, data, *size);
	if (r) {
		ALOGD("Cannot read section '%s'\n", name);
		free(data);
		return NULL;
	}
	if (offset)
		*offset = start_offset;

	return data;
}

/* ---- Google Binary Block (GBB) ---- */

uint8_t *gbb_get_rootkey(struct flash_device *dev, size_t *size)
{
	size_t gbb_size;
	uint8_t *gbb = flash_get_gbb(dev, &gbb_size);
	GoogleBinaryBlockHeader *hdr = (void *)gbb;

	if (!gbb || memcmp(hdr->signature, GBB_SIGNATURE, GBB_SIGNATURE_SIZE) ||
	    gbb_size < sizeof(*hdr))
		return NULL;

	if (hdr->rootkey_offset + hdr->rootkey_size > gbb_size)
		return NULL;

	if (size)
		*size = hdr->rootkey_size;

	return gbb + hdr->rootkey_offset;
}

/* ---- VBoot NVRAM (stored in SPI flash) ---- */

/* bits definition in NVRAM */

enum {
	VB_HEADER_OFFSET			= 0,
	VB_BOOT_OFFSET				= 1,
	VB_RECOVERY_OFFSET			= 2,
	VB_LOCALIZATION_OFFSET			= 3,
	VB_DEV_OFFSET				= 4,
	VB_TPM_OFFSET				= 5,
	VB_RECVOERY_SUBCODE_OFFSET		= 6,
	VB_BOOT2_OFFSET			= 7,
	VB_MISC_OFFSET				= 8,
	VB_KERNEL_OFFSET			= 11,
	VB_CRC_OFFSET				= 15,
	VB_NVDATA_SIZE				= 16
};

#define VB_DEFAULT_MASK			0x01

/* HEADER_OFFSET */
#define VB_HEADER_WIPEOUT_SHIFT		3
#define VB_HEADER_KERNEL_SETTINGS_RESET_SHIFT	4
#define VB_HEADER_FW_SETTINGS_RESET_SHIFT	5
#define VB_HEADER_SIGNATURE_SHIFT		6

/* BOOT_OFFSET */
#define VB_BOOT_TRY_COUNT_MASK			0xf
#define VB_BOOT_TRY_COUNT_SHIFT		0
#define VB_BOOT_BACKUP_NVRAM_SHIFT		4
#define VB_BOOT_OPROM_NEEDED_SHIFT		5
#define VB_BOOT_DISABLE_DEV_SHIFT		6
#define VB_BOOT_DEBUG_RESET_SHIFT		7

/* RECOVERY_OFFSET */
#define VB_RECOVERY_REASON_SHIFT		0
#define VB_RECOVERY_REASON_MASK		0xff

/* BOOT2_OFFSET */
#define VB_BOOT2_RESULT_MASK			0x3
#define VB_BOOT2_RESULT_SHIFT			0
#define VB_BOOT2_TRIED_SHIFT			2
#define VB_BOOT2_TRY_NEXT_SHIFT		3
#define VB_BOOT2_PREV_RESULT_MASK		0x3
#define VB_BOOT2_PREV_RESULT_SHIFT		4
#define VB_BOOT2_PREV_TRIED_SHIFT		6

/* DEV_OFFSET */
#define VB_DEV_FLAG_USB_SHIFT			0
#define VB_DEV_FLAG_SIGNED_ONLY_SHIFT		1
#define VB_DEV_FLAG_LEGACY_SHIFT		2
#define VB_DEV_FLAG_FASTBOOT_FULL_CAP_SHIFT	3

/* TPM_OFFSET */
#define VB_TPM_CLEAR_OWNER_REQUEST_SHIFT	0
#define VB_TPM_CLEAR_OWNER_DONE_SHIFT		1

/* MISC_OFFSET */
#define VB_MISC_UNLOCK_FASTBOOT_SHIFT		0
#define VB_MISC_BOOT_ON_AC_DETECT_SHIFT	1

typedef enum {
	VBNV_DEFAULT_FLAG = 0x00,
	VBNV_WRITABLE = 0x01,
} vbnv_param_flags_t;

typedef struct vbnv_param {
	char *name;
	vbnv_param_flags_t flags;
	int offset;
	int shift;
	int mask;
} vbnv_param_t;

static const vbnv_param_t param_table[] = {
	{"try_count", VBNV_WRITABLE, VB_BOOT_OFFSET, VB_BOOT_TRY_COUNT_SHIFT,
	 VB_BOOT_TRY_COUNT_MASK},
	{"backup_nvram", VBNV_WRITABLE, VB_BOOT_OFFSET,
	 VB_BOOT_BACKUP_NVRAM_SHIFT, VB_DEFAULT_MASK},
	{"oprom_needed", VBNV_WRITABLE, VB_BOOT_OFFSET,
	 VB_BOOT_OPROM_NEEDED_SHIFT, VB_DEFAULT_MASK},
	{"disable_dev", VBNV_WRITABLE, VB_BOOT_OFFSET,
	 VB_BOOT_DISABLE_DEV_SHIFT, VB_DEFAULT_MASK},
	{"debug_reset", VBNV_WRITABLE, VB_BOOT_OFFSET,
	 VB_BOOT_DEBUG_RESET_SHIFT, VB_DEFAULT_MASK},
	{"boot_result", VBNV_WRITABLE, VB_BOOT2_OFFSET, VB_BOOT2_RESULT_SHIFT,
	 VB_BOOT2_RESULT_MASK},
	{"fw_tried", VBNV_DEFAULT_FLAG, VB_BOOT2_OFFSET, VB_BOOT2_TRIED_SHIFT,
	 VB_DEFAULT_MASK},
	{"fw_try_next", VBNV_WRITABLE, VB_BOOT2_OFFSET, VB_BOOT2_TRY_NEXT_SHIFT,
	 VB_DEFAULT_MASK},
	{"fw_prev_result", VBNV_DEFAULT_FLAG, VB_BOOT2_OFFSET,
	 VB_BOOT2_PREV_RESULT_SHIFT, VB_BOOT2_PREV_RESULT_MASK},
	{"prev_tried", VBNV_DEFAULT_FLAG, VB_BOOT2_OFFSET,
	 VB_BOOT2_PREV_TRIED_SHIFT, VB_DEFAULT_MASK},
	{"dev_boot_usb", VBNV_WRITABLE, VB_DEV_OFFSET, VB_DEV_FLAG_USB_SHIFT,
	 VB_DEFAULT_MASK},
	{"dev_boot_signed_only", VBNV_WRITABLE, VB_DEV_OFFSET,
	 VB_DEV_FLAG_SIGNED_ONLY_SHIFT, VB_DEFAULT_MASK},
	{"dev_boot_legacy", VBNV_WRITABLE, VB_DEV_OFFSET,
	 VB_DEV_FLAG_LEGACY_SHIFT, VB_DEFAULT_MASK},
	{"dev_boot_fastboot_full_cap", VBNV_WRITABLE, VB_DEV_OFFSET,
	 VB_DEV_FLAG_FASTBOOT_FULL_CAP_SHIFT, VB_DEFAULT_MASK},
	{"tpm_clear_owner_request", VBNV_WRITABLE, VB_TPM_OFFSET,
	 VB_TPM_CLEAR_OWNER_REQUEST_SHIFT, VB_DEFAULT_MASK},
	{"tpm_clear_owner_done", VBNV_WRITABLE, VB_TPM_OFFSET,
	 VB_TPM_CLEAR_OWNER_DONE_SHIFT, VB_DEFAULT_MASK},
	{"unlock_fastboot", VBNV_WRITABLE, VB_MISC_OFFSET,
	 VB_MISC_UNLOCK_FASTBOOT_SHIFT, VB_DEFAULT_MASK},
	{"boot_on_ac_detect", VBNV_WRITABLE, VB_MISC_OFFSET,
	 VB_MISC_BOOT_ON_AC_DETECT_SHIFT, VB_DEFAULT_MASK},
	{"recovery_reason", VBNV_WRITABLE, VB_RECOVERY_OFFSET,
	 VB_RECOVERY_REASON_SHIFT, VB_RECOVERY_REASON_MASK},
};

static uint8_t crc8(const uint8_t *data, int len)
{
	uint32_t crc = 0;
	int i, j;

	for (j = len; j; j--, data++) {
		crc ^= (*data << 8);
		for(i = 8; i; i--) {
			if (crc & 0x8000)
				crc ^= (0x1070 << 3);
			crc <<= 1;
		}
	}

	return (uint8_t)(crc >> 8);
}

static inline int can_overwrite(uint8_t current, uint8_t new)
{
	return (current & new) == new;
}

int vbnv_readwrite(struct flash_device *spi, const vbnv_param_t *param,
		   uint8_t *value, int write)
{
	int i;
	int res;
	size_t size;
	off_t offset;
	uint8_t *block, *nvram, *end, *curr;
	uint8_t dummy[VB_NVDATA_SIZE];

	int off = param->offset;
	uint8_t mask = param->mask << param->shift;

	if (off >= VB_NVDATA_SIZE) {
		ALOGW("ERROR: Incorrect offset %d for NvStorage\n", off);
		return -EIO;
	}

	/* Read NVRAM. */
	nvram = fmap_read_section(spi, "RW_NVRAM", &size, &offset);
	/*
	 * Ensure NVRAM is found, size is at least 1 block and total size is
	 * multiple of VB_NVDATA_SIZE.
	 */
	if ((nvram == NULL) || (size < VB_NVDATA_SIZE) ||
	    (size % VB_NVDATA_SIZE)) {
		ALOGW("ERROR: NVRAM not found\n");
		return -EIO;
	}

	/* Create an empty dummy block to compare. */
	memset(dummy, 0xFF, sizeof(dummy));

	/*
	 * Loop until the last used block in NVRAM.
	 * 1. All blocks will not be empty since we just booted up fine.
	 * 2. If all blocks are used, select the last block.
	 */
	block = nvram;
	end = block + size;
	for (curr = block; curr < end; curr += VB_NVDATA_SIZE) {
		if (memcmp(curr, dummy, VB_NVDATA_SIZE) == 0)
			break;
		block = curr;
	}

	if (write) {
		uint8_t flag_value = (*value & param->mask) << param->shift;

		/* Copy last used block to make modifications. */
		memcpy(dummy, block, VB_NVDATA_SIZE);

		dummy[off] = (dummy[off] & ~mask) | (flag_value & mask);
		dummy[VB_CRC_OFFSET] = crc8(dummy, VB_CRC_OFFSET);

		/* Check if new block can be overwritten */
		for (i = 0; i < VB_NVDATA_SIZE; i++) {
			if (!can_overwrite(block[i], dummy[i])) {
				if (curr != end)
					offset += (curr - nvram);
				else if (flash_erase(spi, offset, size)) {
					ALOGW("ERROR: Cannot erase flash\n");
					return -EIO;
				}
				break;
			}
		}

		/* Block can be overwritten. */
		if (i == VB_NVDATA_SIZE)
			offset += (block - nvram);

		ALOGI("Writing new entry into NVRAM @ 0x%lx\n", offset);

		/* Write new entry into NVRAM. */
		if (flash_write(spi, offset, dummy, VB_NVDATA_SIZE)) {
			ALOGW("ERROR: Cannot update NVRAM\n");
			return -EIO;
		}

		ALOGD("NVRAM updated.\n");
	} else {
		*value = (block[off] & mask) >> param->shift;
	}

	return 0;
}

#define ARRAY_SIZE(arr)		(sizeof(arr)/sizeof(arr[0]))
int vbnv_set_flag(struct flash_device *spi, const char *param, uint8_t value)
{
	size_t i;
	for (i = 0; i < ARRAY_SIZE(param_table); i++) {
		if (!strcmp(param, param_table[i].name)) {
			if (param_table[i].flags & VBNV_WRITABLE)
				return vbnv_readwrite(spi, &param_table[i],
						      &value, 1);

			fprintf(stderr, "ERROR: Cannot write this flag.\n");
			return -EIO;
		}
	}
	fprintf(stderr, "Error: Unknown param\n");
	return -EIO;
}

int vbnv_get_flag(struct flash_device *spi, const char *param, uint8_t *value)
{
	size_t i;
	for (i = 0; i < ARRAY_SIZE(param_table); i++) {
		if (!strcmp(param, param_table[i].name))
			return vbnv_readwrite(spi, &param_table[i], value, 0);
	}
	fprintf(stderr, "Error: Unknown param\n");
	return -EIO;
}

void vbnv_usage(int write)
{
	size_t i;
	for (i = 0; i < ARRAY_SIZE(param_table); i++)
		if ((write == 0) || (write &&
				     (param_table[i].flags & VBNV_WRITABLE)))
		    printf("   %s\n", param_table[i].name);
}

/* ---- Vital Product Data handling ---- */
