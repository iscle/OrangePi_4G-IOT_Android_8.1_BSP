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
/* Handle read/write/erase of various devices used by the firmware */

#define LOG_TAG "fwtool"

#include <errno.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "flash_device.h"
#include "fmap.h"
#include "update_log.h"
#include "vboot_interface.h"

static const struct flash_device_ops *devices[] = {
	&flash_mtd_ops,
	&flash_ec_ops,
	&flash_file_ops,
};

struct flash_device {
	const struct flash_device_ops *ops;
	void *priv_data;
	struct fmap *fmap;
	uint8_t *gbb;
	size_t gbb_size;
	size_t total_size;
	size_t write_size;
	size_t erase_size;
};

struct flash_device *flash_open(const char *name, const void *params)
{
	const struct flash_device_ops *ops = devices[0];
	struct flash_device *dev;

	if (name) {
		unsigned i;
		for (i = 0; i < sizeof(devices)/sizeof(devices[0]); i++)
			if (!strcmp(devices[i]->name, name)) {
				ops = devices[i];
				break;
			}
	}
	ALOGD("Using flash device '%s'\n", ops->name);

	dev = calloc(1, sizeof(struct flash_device));
	if (!dev)
		return NULL;

	dev->ops = ops;
	dev->priv_data = dev->ops->open(params);
	if (!dev->priv_data)
		goto out_free;

	dev->fmap = NULL;
	dev->gbb = NULL;
	dev->gbb_size = 0;
	dev->total_size = dev->ops->get_size(dev->priv_data);
	dev->write_size = dev->ops->get_write_size(dev->priv_data);
	dev->erase_size = dev->ops->get_erase_size(dev->priv_data);

	return dev;

out_free:
	free(dev);

	return NULL;
}

void flash_close(struct flash_device *dev)
{
	dev->ops->close(dev->priv_data);
	if (dev->gbb)
		free(dev->gbb);
	if (dev->fmap)
		free(dev->fmap);
	free(dev);
}

int flash_read(struct flash_device *dev, off_t off, void *buff, size_t len)
{
	return dev->ops->read(dev->priv_data, off, buff, len);
}

int flash_write(struct flash_device *dev, off_t off, void *buff, size_t len)
{
	if ((off % dev->write_size) || (len % dev->write_size)) {
		ALOGW("Bad write alignment offset %ld size %zd\n",
		      off, len);
		return -EINVAL;
	}
	return dev->ops->write(dev->priv_data, off, buff, len);
}

int flash_erase(struct flash_device *dev, off_t off, size_t len)
{
	if ((off % dev->erase_size) || (len % dev->erase_size)) {
		ALOGW("Bad erase alignment offset %ld size %zd\n",
		      off, len);
		return -EINVAL;
	}

	return dev->ops->erase(dev->priv_data, off, len);
}

size_t flash_get_size(struct flash_device *dev)
{
	return dev->ops->get_size(dev->priv_data);
}

struct fmap *flash_get_fmap(struct flash_device *dev)
{
	if (!dev->fmap) {
		off_t end = dev->ops->get_fmap_offset(dev->priv_data);
		off_t off = fmap_scan_offset(dev, end);
		dev->fmap = fmap_load(dev, off);
	}

	if (!dev->fmap)
		ALOGW("No FMAP found\n");

	return dev->fmap;
}

uint8_t *flash_get_gbb(struct flash_device *dev, size_t *size)
{
	if (!dev->gbb)
		dev->gbb = fmap_read_section(dev, "GBB", &dev->gbb_size, NULL);

	if (!dev->gbb)
		ALOGW("No GBB found\n");
	else if (size)
		*size = dev->gbb_size;

	return dev->gbb;
}

int flash_cmd(struct flash_device *dev, int cmd, int ver,
	      const void *odata, int osize, void *idata, int isize)
{
	if (!dev->ops->cmd)
		return -ENOENT;

	return dev->ops->cmd(dev->priv_data, cmd, ver,
			     odata, osize, idata, isize);
}
