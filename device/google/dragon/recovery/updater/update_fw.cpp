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
/* Firmware update flow */

#define LOG_TAG "fwtool"

#include "errno.h"
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "flash_device.h"
#include "update_log.h"
#include "vboot_interface.h"
#include "update_fw.h"

int check_compatible_keys(struct flash_device *img, struct flash_device *spi)
{
	size_t img_size = 0, spi_size = 0;
	uint8_t *img_rootkey = gbb_get_rootkey(img, &img_size);
	uint8_t *spi_rootkey = gbb_get_rootkey(spi, &spi_size);

	if (!img_rootkey || !spi_rootkey || img_size != spi_size) {
		ALOGD("Invalid root key SPI %zd IMG %zd\n", spi_size, img_size);
		return 0;
	}

	if (memcmp(img_rootkey, spi_rootkey, img_size)) {
		ALOGD("Incompatible root keys\n");
		return 0;
	}

	/* TODO: check RW signature and TPM compatibility */

	return 1;
}

static int update_partition(struct flash_device *src, struct flash_device *dst,
			    const char *name)
{
	int res;
	void *content;
	size_t size;
	off_t offset;
	const char *display_name = name ? name : "<flash>";

	content = fmap_read_section(src, name, &size, &offset);
	if (!content) {
		ALOGW("Cannot read firmware image partition %s\n",
		      display_name);
		return -EIO;
	}
	ALOGD("Erasing partition '%s' ...\n", display_name);
	res = flash_erase(dst, offset, size);
	if (res) {
		ALOGW("Cannot erase flash\n");
		goto out_free;
	}
	ALOGD("Writing partition '%s' ...\n", display_name);
	res = flash_write(dst, offset, content, size);
	if (res)
		ALOGW("Cannot write flash\n");

out_free:
	free(content);
	return 0;
}

static int update_recovery_fw(struct flash_device *spi, struct flash_device *ec,
			      struct flash_device *img, const Value *ec_file)
{
	int res, ra, rb, rs;
	int wp = 1; /* TODO: read SPI read-write */

	if (wp) { /* Update only RW */
		ALOGD("RW Recovery\n");

		if (!check_compatible_keys(img, spi))
			return -EINVAL;

		ra = update_partition(img, spi, "RW_SECTION_A");
		rb = update_partition(img, spi, "RW_SECTION_B");
		rs = update_partition(img, spi, "RW_SHARED");
		res = ra || rb || rs ? -EIO : 0;
	} else { /* Update both RO & RW on SPI + EC */
		ALOGD("RO+RW Recovery\n");
		// TODO Preserve VPD + GBB
		// TODO write full SPI flash with "img"
		// TODO Update EC with ec_file
		(void)ec;
		(void)ec_file;
		res = -ENOENT;
	}

	/* Go back to a sane state for the firmware update */
	//VBNV: fwupdate_tries = 0;

	return res;
}

static int update_rw_fw(struct flash_device *spi, struct flash_device *img,
			char cur_part)
{
	int res;
	/* Update part A if we are running on B, write B in all other cases */
	const char *rw_name = cur_part == 'B' ? "RW_SECTION_A" : "RW_SECTION_B";
	int try_next = cur_part == 'B' ? 0 : 1;

	ALOGD("RW Update of firmware '%s'\n", rw_name);

	if (!check_compatible_keys(img, spi))
		return -EINVAL;

	res = update_partition(img, spi, rw_name);
	if (!res) {
		/* We have updated the SPI flash */
		vbnv_set_flag(spi, "fw_try_next", try_next);
		vbnv_set_flag(spi, "try_count", 6);
	}

	return res;
}

/*
 * Provide RO and RW updates until RO FW is changing in dogfood.
 * TODO (Change this to only RW update and call update_rw_fw instead.)
 */
static int update_ap_fw(struct flash_device *spi, struct flash_device *img)
{
	int res = -EINVAL;

	/*
	 * Save serial number. VPD changed in fmap. Dogfooders need serial
	 * number for future OTAs.
	 */
	size_t rovpd_sz, new_rovpd_sz;
	off_t rovpd_off, new_rovpd_off;
	void *rovpd = fmap_read_section(spi, "RO_VPD", &rovpd_sz, &rovpd_off);
	void *newvpd = fmap_read_section(img, "RO_VPD", &new_rovpd_sz,
					 &new_rovpd_off);

	res = update_partition(img, spi, NULL);

	res = flash_erase(spi, new_rovpd_off, new_rovpd_sz);
	if (res)
		return res;

	res = flash_write(spi, new_rovpd_off, rovpd, new_rovpd_sz);
	if (res)
		return res;

	return res;
}

int update_fw(const Value *fw_file, const Value *ec_file, int force)
{
	int res = -EINVAL;
	struct flash_device *img, *spi, *ec;
	size_t size;
	char *fwid;
	char cur_part = vboot_get_mainfw_act();
	char *version = fdt_read_string("firmware-version");
	if (!version) {
		ALOGW("Cannot read firmware version from FDT\n");
		return -EIO;
	}
	ALOGD("Running firmware: %s / partition %c\n", version, cur_part);

	img = flash_open("file", fw_file);
	if (!img)
		goto out_free;
	fwid = reinterpret_cast<char*>(fmap_read_section(img, "RW_FWID_A", &size, NULL));

	if (!fwid) {
		ALOGD("Cannot find firmware image version\n");
		goto out_close_img;
	}

	/* TODO: force update if keyblock does not match */
	if (!strncmp(version, fwid, size) && !force) {
		ALOGI("Firmware already up-to-date: %s\n", version);
		free(fwid);
		res = 0;
		goto out_close_img;
	}
	free(fwid);

	ec = flash_open("ec", NULL);
	if (!ec)
		goto out_close_img;
	spi = flash_open("spi", NULL);
	if (!spi)
		goto out_close_ec;

	if (0)
		res = update_ap_fw(spi, img);

	if (cur_part == 'R') /* Recovery mode */
		res = update_recovery_fw(spi, ec, img, ec_file);
	else /* Normal mode */
		res = update_rw_fw(spi, img, cur_part);

	if (!res) /* successful update : record it */
		res = 1;

	flash_close(spi);
out_close_ec:
	flash_close(ec);
out_close_img:
	flash_close(img);

out_free:
	free(version);
	return res;
}

