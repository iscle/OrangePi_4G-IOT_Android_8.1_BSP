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
/* Read/write/erase Embedded Controller integrated flash */

#define LOG_TAG "fwtool"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <errno.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/param.h>
#include <sys/stat.h>
#include <sys/types.h>

#include "ec_commands.h"
#include "flash_device.h"
#include "update_log.h"

#define CROS_EC_DEV_NAME "/dev/cros_ec"

struct cros_ec_command {
	uint32_t version;
	uint32_t command;
	uint8_t *outdata;
	uint32_t outsize;
	uint8_t *indata;
	uint32_t insize;
	uint32_t result;
};

#define CROS_EC_DEV_IOCXCMD    _IOWR(':', 0, struct cros_ec_command)
#define CROS_EC_DEV_IOCRDMEM   _IOWR(':', 1, struct cros_ec_readmem)

struct ec_data {
	int fd;
	struct ec_response_get_protocol_info proto;
	struct ec_response_flash_info_1 info;
	struct ec_response_flash_region_info ro_region;
};

static int ec_command(void *hnd, int command, int version,
		const void *outdata, int outsize, void *indata, int insize)
{
	struct ec_data *ec = hnd;
	struct cros_ec_command s_cmd;
	int r;

	if (ec->fd < 0)
		return -ENODEV;

	s_cmd.command = command;
	s_cmd.version = version;
	s_cmd.result = 0xff;
	s_cmd.outsize = outsize;
	s_cmd.outdata = (uint8_t *)outdata;
	s_cmd.insize = insize;
	s_cmd.indata = indata;

	r = ioctl(ec->fd, CROS_EC_DEV_IOCXCMD, &s_cmd);
	if (r < 0) {
		ALOGD("Cmd 0x%x failed %d\n", command, errno);
		return -errno;
	} else if (s_cmd.result != EC_RES_SUCCESS) {
		ALOGD("Cmd 0x%x error %d\n", command, s_cmd.result);
		return s_cmd.result;
	}

	return 0;
}

static void *ec_open(const void *params)
{
	int res;
	struct ec_params_flash_region_info region;
	const char *path = params ? params : CROS_EC_DEV_NAME;
	struct ec_data *dev = calloc(1, sizeof(struct ec_data));
	if (!dev)
		return NULL;

	dev->fd = open(path, O_RDWR);
	if (dev->fd == -1) {
		ALOGE("Cannot open EC device %s : %d\n", path, errno);
		goto out_free;
	}

	res = ec_command(dev, EC_CMD_GET_PROTOCOL_INFO, 0, NULL, 0,
			 &dev->proto, sizeof(dev->proto));
	if (res) {
		ALOGE("Cannot get EC protocol info for %s : %d\n", path, res);
		goto out_close;
	}

	res = ec_command(dev, EC_CMD_FLASH_INFO, 1, NULL, 0,
			 &dev->info, sizeof(dev->info));
	if (res) {
		ALOGE("Cannot get EC flash info for %s : %d\n", path, res);
		goto out_close;
	}

	region.region = EC_FLASH_REGION_RO;
	res = ec_command(dev, EC_CMD_FLASH_REGION_INFO, 1,
			 &region, sizeof(region),
			 &dev->ro_region, sizeof(dev->ro_region));
	if (res) {
		ALOGE("Cannot get EC RO info for %s : %d\n", path, res);
		goto out_close;
	}

	ALOGD("EC %s: size %d erase_block_size %d write_ideal_size %d\n",
		path, dev->info.flash_size, dev->info.erase_block_size,
		dev->info.write_ideal_size);

	return dev;

out_close:
	close(dev->fd);
	dev->fd = -1;
out_free:
	free(dev);

	return NULL;
}

static void ec_close(void *hnd)
{
	struct ec_data *dev = hnd;

	close(dev->fd);
	free(dev);
}

static int ec_read(void *hnd, off_t offset, void *buffer, size_t count)
{
	struct ec_data *dev = hnd;
	ssize_t res;
	struct ec_params_flash_read p;
	uint8_t *ptr = buffer;
	uint32_t read_size = dev->proto.max_response_packet_size
				- sizeof(struct ec_host_response);

	while (count) {
		p.offset = offset;
		p.size = MIN(read_size, count);
		res = ec_command(dev, EC_CMD_FLASH_READ, 0, &p, sizeof(p),
			 ptr, read_size);
		if (res) {
			ALOGW("Cannot read at %ld : %zd\n", offset, res);
			return res;
		}
		count -= p.size;
		ptr += p.size;
		offset += p.size;
	}
	return 0;
}

static int ec_write(void *hnd, off_t offset, void *buffer, size_t count)
{
	struct ec_data *dev = hnd;
	ssize_t res;
	struct ec_params_flash_write *p;
	uint8_t *packet_data;
	uint8_t *ptr = buffer;
	uint32_t write_size = dev->info.write_ideal_size;
	uint32_t total_size = sizeof(*p) +  write_size;

	p = malloc(total_size);
	if (!p)
		return -ENOMEM;
	packet_data = (uint8_t *)p + sizeof(*p);

	while (count) {
		p->offset = offset;
		p->size = write_size;
		memcpy(packet_data, ptr, write_size);
		res = ec_command(dev, EC_CMD_FLASH_WRITE, 1, p, total_size,
			 NULL, 0);
		if (res) {
			ALOGW("Cannot write at %ld : %zd\n", offset, res);
			return res;
		}
		count -= write_size;
		ptr += write_size;
		offset += write_size;
	}
	return 0;
}

static int ec_erase(void *hnd, off_t offset, size_t count)
{
	struct ec_data *dev = hnd;
	int res;
	struct ec_params_flash_erase erase;

	erase.offset = offset;
	erase.size = count;
	res = ec_command(dev, EC_CMD_FLASH_ERASE, 0, &erase, sizeof(erase),
			 NULL, 0);
	if (res) {
		ALOGW("Cannot erase at %ld : %d\n", offset, res);
		return res;
	}

	return 0;
}

static size_t ec_get_size(void *hnd)
{
	struct ec_data *dev = hnd;

	return dev && dev->fd > 0 ? dev->info.flash_size : 0;
}

static size_t ec_get_write_size(void *hnd)
{
	struct ec_data *dev = hnd;

	return dev && dev->fd > 0 ? dev->info.write_ideal_size : 0;
}

static size_t ec_get_erase_size(void *hnd)
{
	struct ec_data *dev = hnd;

	return dev && dev->fd > 0 ? dev->info.erase_block_size : 0;
}

static off_t ec_get_fmap_offset(void *hnd)
{
	struct ec_data *dev = hnd;

	if (!hnd)
		return 0;

	/*
	 * Try to find the FMAP signature at 64-byte boundaries
         * from the end of the RO region.
	 */
	return dev->ro_region.offset + dev->ro_region.size;
}

const struct flash_device_ops flash_ec_ops = {
	.name = "ec",
	.open = ec_open,
	.close = ec_close,
	.read = ec_read,
	.write = ec_write,
	.erase = ec_erase,
	.get_size = ec_get_size,
	.get_write_size = ec_get_write_size,
	.get_erase_size = ec_get_erase_size,
	.get_fmap_offset = ec_get_fmap_offset,
	.cmd = ec_command,
};
