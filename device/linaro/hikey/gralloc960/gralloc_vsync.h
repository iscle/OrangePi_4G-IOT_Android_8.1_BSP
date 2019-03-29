/*
 * Copyright (C) 2014 ARM Limited. All rights reserved.
 *
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

#ifndef _GRALLOC_VSYNC_H_
#define _GRALLOC_VSYNC_H_

struct framebuffer_device_t;

/* Enables vsync interrupt. */
int gralloc_vsync_enable(struct framebuffer_device_t* dev);
/* Disables vsync interrupt. */
int gralloc_vsync_disable(struct framebuffer_device_t* dev);
/* Waits for the vsync interrupt. */
int gralloc_wait_for_vsync(struct framebuffer_device_t* dev);

#endif /* _GRALLOC_VSYNC_H_ */
