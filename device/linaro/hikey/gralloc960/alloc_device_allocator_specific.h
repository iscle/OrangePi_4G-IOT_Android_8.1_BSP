/*
 * Copyright (C) 2013 ARM Limited. All rights reserved.
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

int alloc_backend_alloc(alloc_device_t* dev, size_t size, int usage, buffer_handle_t* pHandle, uint64_t fmt, int w, int h);

int alloc_backend_alloc_framebuffer(struct private_module_t* m, struct private_handle_t* hnd);

void alloc_backend_alloc_free(struct private_handle_t const* hnd, struct private_module_t* m);

int alloc_backend_open(alloc_device_t *dev);

int alloc_backend_close(struct hw_device_t *device);
