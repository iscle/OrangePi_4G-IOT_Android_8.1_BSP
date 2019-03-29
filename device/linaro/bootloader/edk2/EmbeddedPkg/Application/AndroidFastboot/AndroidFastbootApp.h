/** @file

  Copyright (c) 2013-2014, ARM Ltd. All rights reserved.<BR>

  This program and the accompanying materials
  are licensed and made available under the terms and conditions of the BSD License
  which accompanies this distribution.  The full text of the license may be found at
  http://opensource.org/licenses/bsd-license.php

  THE PROGRAM IS DISTRIBUTED UNDER THE BSD LICENSE ON AN "AS IS" BASIS,
  WITHOUT WARRANTIES OR REPRESENTATIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED.

**/

#ifndef __ANDROID_FASTBOOT_APP_H__
#define __ANDROID_FASTBOOT_APP_H__

#include <Library/BaseLib.h>
#include <Library/DebugLib.h>
#include <Library/MemoryAllocationLib.h>

#define BOOTIMG_KERNEL_ARGS_SIZE 512

#define ANDROID_FASTBOOT_VERSION "0.4"

#define BOOT_MAGIC        "ANDROID!"
#define BOOT_MAGIC_LENGTH sizeof(BOOT_MAGIC) - 1
#pragma pack(1)
typedef struct {
  CHAR8   BootMagic[BOOT_MAGIC_LENGTH];
  UINT32  KernelSize;
  UINT32  KernelAddress;
  UINT32  RamdiskSize;
  UINT32  RamdiskAddress;
  UINT32  SecondStageBootloaderSize;
  UINT32  SecondStageBootloaderAddress;
  UINT32  KernelTaggsAddress;
  UINT32  PageSize;
  UINT32  Reserved[2];
  CHAR8   ProductName[16];
  CHAR8   KernelArgs[BOOTIMG_KERNEL_ARGS_SIZE];
  UINT32  Id[32];
} ANDROID_BOOTIMG_HEADER;
#pragma pack()

EFI_STATUS
BootAndroidBootImg (
  IN  UINTN    BufferSize,
  IN  VOID    *Buffer
  );

EFI_STATUS
ParseAndroidBootImg (
  IN  VOID    *BootImg,
  OUT VOID   **Kernel,
  OUT UINTN   *KernelSize,
  OUT VOID   **Ramdisk,
  OUT UINTN   *RamdiskSize,
  OUT CHAR8   *KernelArgs
  );

#endif //ifdef __ANDROID_FASTBOOT_APP_H__
