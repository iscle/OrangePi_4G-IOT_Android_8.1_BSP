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

#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include "edify/expr.h"
#include "flash_device.h"
#include "update_log.h"

struct file_data {
	int fd;
	uint8_t *data;
	struct stat info;
};

static void *file_blob_open(struct file_data *dev, const Value *param)
{
	dev->fd = -1; /* No backing file */
	dev->data = const_cast<uint8_t*>(reinterpret_cast<const uint8_t*>(&param->data[0]));

	dev->info.st_size = param->data.size();

	return dev;
}

static void *file_open(const void *params)
{
	struct file_data *dev = static_cast<struct file_data*>(calloc(1, sizeof(struct file_data)));
	if (!dev)
		return NULL;

	const Value *value = static_cast<const Value*>(params);
	if (value->type == VAL_BLOB)
		return file_blob_open(dev, value);

	if (value->type != VAL_STRING)
		goto out_free;

	dev->fd = open(value->data.c_str(), O_RDWR);
	if (dev->fd == -1) {
		ALOGE("Cannot open file %s : %d\n", value->data.c_str(), errno);
		goto out_free;
	}

	if (fstat(dev->fd, &dev->info)) {
		ALOGE("Cannot get file info for %s : %d\n", value->data.c_str(), errno);
		goto out_close;
	}

	dev->data = static_cast<uint8_t*>(mmap(NULL, dev->info.st_size, PROT_READ | PROT_WRITE,
			 MAP_SHARED, dev->fd, 0));
	if (dev->data == (void *)-1) {
		ALOGE("Cannot mmap %s : %d\n", value->data.c_str(), errno);
		goto out_close;
	}

	ALOGD("File %s: size %lld blksize %ld\n", value->data.c_str(),
		(long long)dev->info.st_size, (long)dev->info.st_blksize);

	return dev;

out_close:
	close(dev->fd);
out_free:
	free(dev);

	return NULL;
}

static void file_close(void *hnd)
{
	struct file_data *dev = static_cast<struct file_data*>(hnd);

	if (dev->fd > 0) {
		munmap(dev->data, dev->info.st_size);
		close(dev->fd);
	}
	free(dev);
}

static int file_read(void *hnd, off_t offset, void *buffer, size_t count)
{
	struct file_data *dev = static_cast<struct file_data*>(hnd);

	if (offset + (off_t)count > dev->info.st_size) {
		ALOGW("Invalid offset/size %ld + %zd > %lld\n",
			offset, count, (long long)dev->info.st_size);
		return -EINVAL;
	}

	memcpy(buffer, dev->data + offset, count);

	return 0;
}

static int file_write(void *hnd, off_t offset, void *buffer, size_t count)
{
	struct file_data *dev = static_cast<struct file_data*>(hnd);

	if (offset + (off_t)count > dev->info.st_size) {
		ALOGW("Invalid offset/size %ld + %zd > %lld\n",
			offset, count, (long long)dev->info.st_size);
		return -EINVAL;
	}

	memcpy(dev->data + offset, buffer, count);

	return 0;
}

static int file_erase(void *hnd, off_t offset, size_t count)
{
	struct file_data *dev = static_cast<struct file_data*>(hnd);

	if (offset + (off_t)count > dev->info.st_size) {
		ALOGW("Invalid offset/size %ld + %zd > %lld\n",
			offset, count, (long long)dev->info.st_size);
		return -EINVAL;
	}

	memset(dev->data + offset, '\xff', count);

	return 0;
}

static size_t file_get_size(void *hnd)
{
	struct file_data *dev = static_cast<struct file_data*>(hnd);

	return dev ? dev->info.st_size : 0;
}

static size_t file_get_write_size(void *hnd)
{
	struct file_data *dev = static_cast<struct file_data*>(hnd);

	return dev && dev->fd > 0 ? dev->info.st_blksize : 0;
}

static size_t file_get_erase_size(void *hnd)
{
	struct file_data *dev = static_cast<struct file_data*>(hnd);

	return dev && dev->fd > 0 ? dev->info.st_blksize : 0;
}

static off_t file_get_fmap_offset(void *hnd)
{
	struct file_data *dev = static_cast<struct file_data*>(hnd);

	return dev->info.st_size;
}

const struct flash_device_ops flash_file_ops = {
	.name = "file",
	.open = file_open,
	.close = file_close,
	.read = file_read,
	.write = file_write,
	.erase = file_erase,
	.get_size = file_get_size,
	.get_write_size = file_get_write_size,
	.get_erase_size = file_get_erase_size,
	.get_fmap_offset = file_get_fmap_offset,
};
