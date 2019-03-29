/** @file

  Copyright (c) 2013-2015, ARM Ltd. All rights reserved.<BR>

  This program and the accompanying materials
  are licensed and made available under the terms and conditions of the BSD License
  which accompanies this distribution.  The full text of the license may be found at
  http://opensource.org/licenses/bsd-license.php

  THE PROGRAM IS DISTRIBUTED UNDER THE BSD LICENSE ON AN "AS IS" BASIS,
  WITHOUT WARRANTIES OR REPRESENTATIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED.

**/

#include "AndroidFastbootApp.h"

#include <Protocol/DevicePath.h>

#include <Library/BaseMemoryLib.h>
#include <Library/BdsLib.h>
#include <Library/DevicePathLib.h>
#include <Library/PrintLib.h>
#include <Library/UefiBootServicesTableLib.h>
#include <Library/UefiRuntimeServicesTableLib.h>
#include <Library/UefiLib.h>

#define LINUX_LOADER_COMMAND_LINE       L"%s -f %s -c %s"

// This GUID is defined in the INGF file of ArmPkg/Application/LinuxLoader
CONST EFI_GUID mLinuxLoaderAppGuid = { 0x701f54f2, 0x0d70, 0x4b89, { 0xbc, 0x0a, 0xd9, 0xca, 0x25, 0x37, 0x90, 0x59 }};

// Device Path representing an image in memory
#pragma pack(1)
typedef struct {
  MEMMAP_DEVICE_PATH                      Node1;
  EFI_DEVICE_PATH_PROTOCOL                End;
} MEMORY_DEVICE_PATH;
#pragma pack()

STATIC CONST MEMORY_DEVICE_PATH MemoryDevicePathTemplate =
{
  {
    {
      HARDWARE_DEVICE_PATH,
      HW_MEMMAP_DP,
      {
        (UINT8)(sizeof (MEMMAP_DEVICE_PATH)),
        (UINT8)((sizeof (MEMMAP_DEVICE_PATH)) >> 8),
      },
    }, // Header
    0, // StartingAddress (set at runtime)
    0  // EndingAddress   (set at runtime)
  }, // Node1
  {
    END_DEVICE_PATH_TYPE,
    END_ENTIRE_DEVICE_PATH_SUBTYPE,
    { sizeof (EFI_DEVICE_PATH_PROTOCOL), 0 }
  } // End
};

#define KERNEL_IMAGE_STEXT_OFFSET     0x12C
#define KERNEL_IMAGE_RAW_SIZE_OFFSET  0x130

EFI_STATUS
BootAndroidBootImg (
  IN UINTN    BufferSize,
  IN VOID    *Buffer
  )
{
  EFI_STATUS                          Status;
  CHAR8                               KernelArgs[BOOTIMG_KERNEL_ARGS_SIZE];
  VOID                               *Kernel;
  UINTN                               KernelSize;
  VOID                               *Ramdisk;
  UINTN                               RamdiskSize;
  MEMORY_DEVICE_PATH                  KernelDevicePath;
  MEMORY_DEVICE_PATH*                 RamdiskDevicePath;
  EFI_PHYSICAL_ADDRESS                FdtBase;
  CHAR16                              UnicodeArgs[BOOTIMG_KERNEL_ARGS_SIZE];
  CHAR16                              InitrdArgs[64];
  BDS_LOAD_OPTION                    *BdsLoadOptions;
  UINTN                               VariableSize;
  CHAR16                              SerialNoArgs[40], DataUnicode[17];

  Status = ParseAndroidBootImg (
            Buffer,
            &Kernel,
            &KernelSize,
            &Ramdisk,
            &RamdiskSize,
            KernelArgs
            );
  if (EFI_ERROR (Status)) {
    return Status;
  }

  KernelDevicePath = MemoryDevicePathTemplate;

  KernelSize = *(UINT32 *)(Kernel + KERNEL_IMAGE_STEXT_OFFSET) +
               *(UINT32 *)(Kernel + KERNEL_IMAGE_RAW_SIZE_OFFSET);
  FdtBase = (EFI_PHYSICAL_ADDRESS)(UINTN)Kernel + KernelSize;
  Status = gBS->InstallConfigurationTable (&gFdtTableGuid, (VOID *)FdtBase);
  ASSERT_EFI_ERROR (Status);

  // Have to cast to UINTN before casting to EFI_PHYSICAL_ADDRESS in order to
  // appease GCC.
  KernelDevicePath.Node1.StartingAddress = (EFI_PHYSICAL_ADDRESS)(UINTN) Kernel;
  KernelDevicePath.Node1.EndingAddress   = (EFI_PHYSICAL_ADDRESS)(UINTN) Kernel + KernelSize;

  RamdiskDevicePath = NULL;
  if (RamdiskSize != 0) {
    RamdiskDevicePath = (MEMORY_DEVICE_PATH*)DuplicateDevicePath ((EFI_DEVICE_PATH_PROTOCOL*) &MemoryDevicePathTemplate);

    RamdiskDevicePath->Node1.StartingAddress = (EFI_PHYSICAL_ADDRESS)(UINTN) Ramdisk;
    RamdiskDevicePath->Node1.EndingAddress   = ((EFI_PHYSICAL_ADDRESS)(UINTN) Ramdisk) + RamdiskSize;
  }

  BdsLoadOptions = (BDS_LOAD_OPTION *)AllocateZeroPool (sizeof(BDS_LOAD_OPTION));
  ASSERT (BdsLoadOptions != NULL);
  BdsLoadOptions->FilePathList = &KernelDevicePath.Node1.Header;
  ASSERT (BdsLoadOptions->FilePathList != NULL);
  BdsLoadOptions->FilePathListLength = GetDevicePathSize (BdsLoadOptions->FilePathList);
  BdsLoadOptions->Attributes = LOAD_OPTION_ACTIVE | LOAD_OPTION_CATEGORY_BOOT;

  AsciiStrToUnicodeStr (KernelArgs, UnicodeArgs);
  UnicodeSPrint (InitrdArgs, 64 * sizeof(CHAR16), L" initrd=0x%x,0x%x",
		 (EFI_PHYSICAL_ADDRESS)(UINTN) Ramdisk, RamdiskSize);
  StrCat (UnicodeArgs, InitrdArgs);

  VariableSize = 17 * sizeof(CHAR16);
  Status = gRT->GetVariable ((CHAR16 *)L"SerialNo", &gHiKeyVariableGuid, NULL,
				&VariableSize, &DataUnicode);
  if (!EFI_ERROR (Status)) {
    DataUnicode[(VariableSize / sizeof(CHAR16)) - 1] = '\0';
    ZeroMem (SerialNoArgs, 40 * sizeof (CHAR16));
    UnicodeSPrint (SerialNoArgs, 40 * sizeof(CHAR16), L" androidboot.serialno=%s", DataUnicode);
    StrCat (UnicodeArgs, SerialNoArgs);
  }

  BdsLoadOptions->OptionalDataSize = StrSize (UnicodeArgs);
  BdsLoadOptions->OptionalData = UnicodeArgs;
  BdsLoadOptions->Description = NULL;


  Status = BdsStartEfiApplication (gImageHandle, BdsLoadOptions->FilePathList, BdsLoadOptions->OptionalDataSize, BdsLoadOptions->OptionalData);
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "Couldn't Boot Linux: %d\n", Status));
    return EFI_DEVICE_ERROR;
  }

  if (RamdiskDevicePath) {
    FreePool (RamdiskDevicePath);
  }

  // If we got here we do a confused face because BootLinuxFdt returned,
  // reporting success.
  DEBUG ((EFI_D_ERROR, "WARNING: BdsBootLinuxFdt returned EFI_SUCCESS.\n"));
  return EFI_SUCCESS;
}
