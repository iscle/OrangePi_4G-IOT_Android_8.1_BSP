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
#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#ifndef _RECOVERY_FLASH_DEVICE_H_
#define _RECOVERY_FLASH_DEVICE_H_

#include <stdint.h>

struct flash_device_ops {
	const char * const name;
	void *(*open)(const void *params);
	void (*close)(void *hnd);
	int (*read)(void *hnd, off_t offset, void *buffer, size_t count);
	int (*write)(void *hnd, off_t offset, void *buffer, size_t count);
	int (*erase)(void *hnd, off_t offset, size_t count);
	size_t (*get_size)(void *hnd);
	size_t (*get_write_size)(void *hnd);
	size_t (*get_erase_size)(void *hnd);
	off_t (*get_fmap_offset)(void *hnd);
	int (*cmd)(void *hnd, int cmd, int ver, const void *odata, int osize,
		   void *idata, int isize);
};

struct flash_device;

struct flash_device *flash_open(const char *name, const void *params);
void flash_close(struct flash_device *dev);
int flash_read(struct flash_device *dev, off_t off, void *buff, size_t len);
int flash_write(struct flash_device *dev, off_t off, void *buff, size_t len);
int flash_erase(struct flash_device *dev, off_t off, size_t len);
size_t flash_get_size(struct flash_device *dev);
int flash_cmd(struct flash_device *dev, int cmd, int ver,
	      const void *odata, int osize, void *idata, int isize);

struct fmap *flash_get_fmap(struct flash_device *dev);
uint8_t *flash_get_gbb(struct flash_device *dev, size_t *size);

/* Available flash devices */
extern const struct flash_device_ops flash_mtd_ops;
extern const struct flash_device_ops flash_ec_ops;
extern const struct flash_device_ops flash_file_ops;

#endif /* _RECOVERY_FLASH_DEVICE_H_ */

#ifdef __cplusplus
} /* extern "C" */
#endif /* __cplusplus */
