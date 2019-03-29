/**
 * Copyright (C) 2017 The Android Open Source Project
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


#ifndef __KILROY_H__
#define __KILROY_H__

typedef int ion_user_handle_t;

enum ion_heap_ids {
  INVALID_HEAP_ID = -1,
  ION_CP_MM_HEAP_ID = 8,
  ION_CP_MFC_HEAP_ID = 12,
  ION_CP_WB_HEAP_ID = 16,  /* 8660 only */
  ION_CAMERA_HEAP_ID = 20, /* 8660 only */
  ION_SYSTEM_CONTIG_HEAP_ID = 21,
  ION_ADSP_HEAP_ID = 22,
  ION_PIL1_HEAP_ID = 23, /* Currently used for other PIL images */
  ION_SF_HEAP_ID = 24,
  ION_IOMMU_HEAP_ID = 25,
  ION_PIL2_HEAP_ID = 26, /* Currently used for modem firmware images */
  ION_QSECOM_HEAP_ID = 27,
  ION_AUDIO_HEAP_ID = 28,

  ION_MM_FIRMWARE_HEAP_ID = 29,
  ION_SYSTEM_HEAP_ID = 30,

  ION_HEAP_ID_RESERVED = 31 /** Bit reserved for ION_FLAG_SECURE flag */
};

/**
 * Flag to use when allocating to indicate that a heap is secure.
 */
#define ION_FLAG_SECURE (1 << ION_HEAP_ID_RESERVED)

/**
 * Flag for clients to force contiguous memort allocation
 *
 * Use of this flag is carefully monitored!
 */
#define ION_FLAG_FORCE_CONTIGUOUS (1 << 30)

/**
 * Deprecated! Please use the corresponding ION_FLAG_*
 */
#define ION_SECURE ION_FLAG_SECURE
#define ION_FORCE_CONTIGUOUS ION_FLAG_FORCE_CONTIGUOUS

/**
 * Macro should be used with ion_heap_ids defined above.
 */
#define ION_HEAP(bit) (1 << (bit))

#define ION_IOC_MAGIC 'I'

/**
 * DOC: ION_IOC_ALLOC - allocate memory
 *
 * Takes an ion_allocation_data struct and returns it with the handle field
 * populated with the opaque handle for the allocation.
 */
#define ION_IOC_ALLOC _IOWR(ION_IOC_MAGIC, 0, struct ion_allocation_data)

/**
 * DOC: ION_IOC_FREE - free memory
 *
 * Takes an ion_handle_data struct and frees the handle.
 */
#define ION_IOC_FREE _IOWR(ION_IOC_MAGIC, 1, struct ion_handle_data)

/**
 * DOC: ION_IOC_MAP - get a file descriptor to mmap
 *
 * Takes an ion_fd_data struct with the handle field populated with a valid
 * opaque handle.  Returns the struct with the fd field set to a file
 * descriptor open in the current address space.  This file descriptor
 * can then be used as an argument to mmap.
 */
#define ION_IOC_MAP _IOWR(ION_IOC_MAGIC, 2, struct ion_fd_data)

/**
 * struct ion_allocation_data - metadata passed from userspace for allocations
 * @len:		size of the allocation
 * @align:		required alignment of the allocation
 * @heap_id_mask:	mask of heap ids to allocate from
 * @flags:		flags passed to heap
 * @handle:		pointer that will be populated with a cookie to use to
 *			refer to this allocation
 *
 * Provided by userspace as an argument to the ioctl
 */
struct ion_allocation_data {
  size_t len;
  size_t align;
  unsigned int heap_id_mask;
  unsigned int flags;
  ion_user_handle_t handle;
};

/**
 * struct ion_fd_data - metadata passed to/from userspace for a handle/fd pair
 * @handle:	a handle
 * @fd:		a file descriptor representing that handle
 *
 * For ION_IOC_SHARE or ION_IOC_MAP userspace populates the handle field with
 * the handle returned from ion alloc, and the kernel returns the file
 * descriptor to share or map in the fd field.  For ION_IOC_IMPORT, userspace
 * provides the file descriptor and the kernel returns the handle.
 */
struct ion_fd_data {
  ion_user_handle_t handle;
  int fd;
};

/**
 * struct ion_handle_data - a handle passed to/from the kernel
 * @handle:	a handle
 */
struct ion_handle_data {
  ion_user_handle_t handle;
};

#endif /* __KILROY_H__ */
