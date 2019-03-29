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
/* Read/write/erase SPI flash through Linux kernel MTD interface */

#define LOG_TAG "fwtool"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/types.h>

#include <mtd/mtd-user.h>

#include "flash_device.h"
#include "update_log.h"
#include "vboot_interface.h"

static const char * const DEFAULT_MTD_FILE = "/dev/mtd/mtd0";

struct mtd_data {
	int fd;
	struct mtd_info_user info;
};

static void *mtd_open(const void *params)
{
	const char *path = params ? params : DEFAULT_MTD_FILE;
	struct mtd_data *dev = calloc(1, sizeof(struct mtd_data));
	if (!dev)
		return NULL;

	dev->fd = open(path, O_RDWR);
	if (dev->fd == -1) {
		ALOGE("No MTD device %s : %d\n", path, errno);
		goto out_free;
	}

	if (ioctl(dev->fd, MEMGETINFO, &dev->info)) {
		ALOGE("Cannot get MTD info for %s : %d\n", path, errno);
		goto out_close;
	}

	if (dev->info.type != MTD_NORFLASH) {
		ALOGE("Unsupported MTD device type: %d\n", dev->info.type);
		goto out_close;
	}

	ALOGD("MTD %s: size %d erasesize %d min_io_size %d\n",
		path, dev->info.size, dev->info.erasesize, dev->info.writesize);

	return dev;

out_close:
	close(dev->fd);
	dev->fd = -1;
out_free:
	free(dev);

	return NULL;
}

static void mtd_close(void *hnd)
{
	struct mtd_data *dev = hnd;

	close(dev->fd);
	free(dev);
}

static int mtd_read(void *hnd, off_t offset, void *buffer, size_t count)
{
	struct mtd_data *dev = hnd;
	ssize_t res;
	uint8_t *ptr = buffer;

	if (lseek(dev->fd, offset, SEEK_SET) != offset) {
		ALOGW("Cannot seek to %ld\n", offset);
		return errno;

	}

	while (count) {
		res = read(dev->fd, ptr, count);
		if (res < 0) {
			ALOGW("Cannot read at %ld : %zd\n", offset, res);
			return errno;
		}
		count -= res;
		ptr += res;
	}
	return 0;
}

static int mtd_write(void *hnd, off_t offset, void *buffer, size_t count)
{
	struct mtd_data *dev = hnd;
	ssize_t res;
	uint8_t *ptr = buffer;

	if (lseek(dev->fd, offset, SEEK_SET) != offset) {
		ALOGW("Cannot seek to %ld\n", offset);
		return errno;
	}

	while (count) {
		res = write(dev->fd, ptr, count);
		if (res < 0) {
			ALOGW("Cannot write at %ld : %zd\n", offset, res);
			return errno;
		}
		count -= res;
		ptr += res;
	}
	return 0;
}

static int mtd_erase(void *hnd, off_t offset, size_t count)
{
	struct mtd_data *dev = hnd;
	int res;
	struct erase_info_user ei;

	ei.start = offset;
	ei.length = count;
	res = ioctl(dev->fd, MEMERASE, &ei);
	if (res < 0) {
		ALOGW("Cannot erase at %ld : %d\n", offset, res);
		return errno;
	}

	return 0;
}

/*
 * Write Protect handling :
 * struct erase_info_user ei;
 *
 * ei.start = eb * info.erasesize;
 * ei.length = info.erasesize;
 * ret = ioctl(fd, MEMISLOCKED, &ei);
 * ret = ioctl(fd, MEMLOCK, &ei);
 * ret = ioctl(fd, MEMUNLOCK, &ei);
 */

static size_t mtd_get_size(void *hnd)
{
	struct mtd_data *dev = hnd;

	return dev && dev->fd > 0 ? dev->info.size : 0;
}

static size_t mtd_get_write_size(void *hnd)
{
	struct mtd_data *dev = hnd;

	return dev && dev->fd > 0 ? dev->info.writesize : 0;
}

static size_t mtd_get_erase_size(void *hnd)
{
	struct mtd_data *dev = hnd;

	return dev && dev->fd > 0 ? dev->info.erasesize : 0;
}

static off_t mtd_get_fmap_offset(void *hnd __attribute__((unused)))
{
	/* Get the SPI FMAP offset passed by the firmware in the device-tree */
	return fdt_read_u32("fmap-offset") + 64;
}

const struct flash_device_ops flash_mtd_ops = {
	.name = "spi",
	.open = mtd_open,
	.close = mtd_close,
	.read = mtd_read,
	.write = mtd_write,
	.erase = mtd_erase,
	.get_size = mtd_get_size,
	.get_write_size = mtd_get_write_size,
	.get_erase_size = mtd_get_erase_size,
	.get_fmap_offset = mtd_get_fmap_offset,
};
