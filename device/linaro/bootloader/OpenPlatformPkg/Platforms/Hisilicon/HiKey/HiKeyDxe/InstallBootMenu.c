/** @file
*
*  Copyright (c) 2015, Linaro Ltd. All rights reserved.
*  Copyright (c) 2015, Hisilicon Ltd. All rights reserved.
*
*  This program and the accompanying materials
*  are licensed and made available under the terms and conditions of the BSD License
*  which accompanies this distribution.  The full text of the license may be found at
*  http://opensource.org/licenses/bsd-license.php
*
*  THE PROGRAM IS DISTRIBUTED UNDER THE BSD LICENSE ON AN "AS IS" BASIS,
*  WITHOUT WARRANTIES OR REPRESENTATIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED.
*
**/

#include <Library/BaseMemoryLib.h>
#include <Library/BdsLib.h>
#include <Library/CacheMaintenanceLib.h>
#include <Library/DevicePathLib.h>
#include <Library/DxeServicesLib.h>
#include <Library/IoLib.h>
#include <Library/MemoryAllocationLib.h>
#include <Library/PrintLib.h>
#include <Library/UefiBootServicesTableLib.h>
#include <Library/UefiLib.h>
#include <Library/UefiRuntimeServicesTableLib.h>

#include <Protocol/BlockIo.h>
#include <Protocol/DevicePathFromText.h>
#include <Protocol/DevicePathToText.h>
#include <Protocol/DwUsb.h>
#include <Protocol/EmbeddedGpio.h>
#include <Protocol/SimpleFileSystem.h>

#include <Guid/Fdt.h>
#include <Guid/FileInfo.h>
#include <Guid/EventGroup.h>
#include <Guid/GlobalVariable.h>
#include <Guid/HiKeyVariable.h>
#include <Guid/VariableFormat.h>

#include "HiKeyDxeInternal.h"

typedef enum {
  HIKEY_DTB_ANDROID = 0,	/* DTB is attached at the end of boot.img */
  HIKEY_DTB_LINUX = 1,		/* DTB is in partition */
  HIKEY_DTB_SD = 2,		/* DTB is already in SD Card */
} HiKeyDtbType;

#define HIKEY_IO_BLOCK_SIZE      512

#define MAX_BOOT_ENTRIES         16
// Jumper on pin5-6 of J15 determines whether boot to fastboot
#define DETECT_J15_FASTBOOT      24    // GPIO 3_0

#define USER_LED1                32    // GPIO 4_0
#define USER_LED2                33    // GPIO 4_1
#define USER_LED3                34    // GPIO 4_2
#define USER_LED4                35    // GPIO 4_3

struct HiKeyBootEntry {
  CHAR16    *Path;
  CHAR16    *Args;
  CHAR16    *Description;
  UINT16     LoadType;
};

STATIC CONST BOOLEAN mIsEndOfDxeEvent = TRUE;
STATIC UINT16 *mBootOrder = NULL;
STATIC UINT16 mBootCount = 0;
STATIC UINT16 mBootIndex = 0;

#define HIKEY_BOOT_ENTRY_FASTBOOT          0
#define HIKEY_BOOT_ENTRY_BOOT_EMMC         1    /* boot from eMMC */
#define HIKEY_BOOT_ENTRY_BOOT_SD           2    /* boot from SD card */

STATIC struct HiKeyBootEntry LinuxEntries[] = {
  [HIKEY_BOOT_ENTRY_FASTBOOT] = {
    L"FvFile(9588502a-5370-11e3-8631-d7c5951364c8)",
    //L"VenHw(B549F005-4BD4-4020-A0CB-06F42BDA68C3)/HD(6,GPT,5C0F213C-17E1-4149-88C8-8B50FB4EC70E,0x7000,0x20000)/\\EFI\\BOOT\\FASTBOOT.EFI",
    NULL,
    L"fastboot",
    LOAD_OPTION_CATEGORY_APP
  },
  [HIKEY_BOOT_ENTRY_BOOT_EMMC] = {
    L"VenHw(B549F005-4BD4-4020-A0CB-06F42BDA68C3)/HD(6,GPT,5C0F213C-17E1-4149-88C8-8B50FB4EC70E,0x7000,0x20000)/\\EFI\\BOOT\\GRUBAA64.EFI",
    NULL,
    L"boot from eMMC",
    LOAD_OPTION_CATEGORY_APP
  },
  [HIKEY_BOOT_ENTRY_BOOT_SD] = {
    L"VenHw(594BFE73-5E18-4F12-8119-19DB8C5FC849)/HD(1,MBR,0x00000000,0x3F,0x21FC0)/\\EFI\\BOOT\\BOOTAA64.EFI",
    NULL,
    L"boot from SD card",
    LOAD_OPTION_CATEGORY_BOOT
  }
};

STATIC struct HiKeyBootEntry AndroidEntries[] = {
  [HIKEY_BOOT_ENTRY_FASTBOOT] = {
    L"FvFile(9588502a-5370-11e3-8631-d7c5951364c8)",
    //L"VenHw(B549F005-4BD4-4020-A0CB-06F42BDA68C3)/HD(6,GPT,5C0F213C-17E1-4149-88C8-8B50FB4EC70E,0x7000,0x20000)/\\EFI\\BOOT\\FASTBOOT.EFI",
    NULL,
    L"fastboot",
    LOAD_OPTION_CATEGORY_APP
  },
  [HIKEY_BOOT_ENTRY_BOOT_EMMC] = {
    L"VenHw(B549F005-4BD4-4020-A0CB-06F42BDA68C3)/HD(6,GPT,5C0F213C-17E1-4149-88C8-8B50FB4EC70E,0x7000,0x20000)/Offset(0x0000,0x20000)",
    L"console=ttyAMA3,115200 earlycon=pl011,0xf7113000 root=/dev/mmcblk0p9 rw rootwait efi=noruntime",
    L"boot from eMMC",
    LOAD_OPTION_CATEGORY_BOOT
  },
  [HIKEY_BOOT_ENTRY_BOOT_SD] = {
    L"VenHw(594BFE73-5E18-4F12-8119-19DB8C5FC849)/HD(1,MBR,0x00000000,0x3F,0x21FC0)/Image",
    L"dtb=hi6220-hikey.dtb console=ttyAMA3,115200 earlycon=pl011,0xf7113000 root=/dev/mmcblk1p2 rw rootwait initrd=initrd.img efi=noruntime",
    L"boot from SD card",
    LOAD_OPTION_CATEGORY_BOOT
  }
};


STATIC
BOOLEAN
EFIAPI
HiKeyVerifyBootEntry (
  IN CHAR16          *BootVariableName,
  IN CHAR16          *BootDevicePathText,
  IN CHAR16          *BootArgs,
  IN CHAR16          *BootDescription,
  IN UINT16           LoadOptionAttr
  )
{
  EFI_DEVICE_PATH_TO_TEXT_PROTOCOL   *DevicePathToTextProtocol;
  CHAR16                             *DevicePathText;
  UINTN                               EfiLoadOptionSize;
  EFI_LOAD_OPTION                    *EfiLoadOption;
  BDS_LOAD_OPTION                    *LoadOption;
  EFI_STATUS                          Status;
  UINTN                               DescriptionLength;

  Status = GetGlobalEnvironmentVariable (BootVariableName, NULL, &EfiLoadOptionSize, (VOID**)&EfiLoadOption);
  if (EFI_ERROR (Status)) {
    return FALSE;
  }
  if (EfiLoadOption == NULL) {
    return FALSE;
  }
  if (EfiLoadOptionSize < sizeof(UINT32) + sizeof(UINT16) + sizeof(CHAR16) + sizeof(EFI_DEVICE_PATH_PROTOCOL)) {
    return FALSE;
  }
  LoadOption = (BDS_LOAD_OPTION*)AllocateZeroPool (sizeof(BDS_LOAD_OPTION));
  if (LoadOption == NULL) {
    return FALSE;
  }

  LoadOption->LoadOption         = EfiLoadOption;
  LoadOption->Attributes         = *(UINT32*)EfiLoadOption;
  LoadOption->FilePathListLength = *(UINT16*)(EfiLoadOption + sizeof(UINT32));
  LoadOption->Description        = (CHAR16*)(EfiLoadOption + sizeof(UINT32) + sizeof(UINT16));
  DescriptionLength              = StrSize (LoadOption->Description);
  LoadOption->FilePathList       = (EFI_DEVICE_PATH_PROTOCOL*)(EfiLoadOption + sizeof(UINT32) + sizeof(UINT16) + DescriptionLength);
  if ((UINTN)((UINTN)LoadOption->FilePathList + LoadOption->FilePathListLength - (UINTN)EfiLoadOption) == EfiLoadOptionSize) {
    LoadOption->OptionalData     = NULL;
    LoadOption->OptionalDataSize = 0;
  } else {
    LoadOption->OptionalData     = (VOID*)((UINTN)(LoadOption->FilePathList) + LoadOption->FilePathListLength);
    LoadOption->OptionalDataSize = EfiLoadOptionSize - ((UINTN)LoadOption->OptionalData - (UINTN)EfiLoadOption);
  }

  if (((BootArgs == NULL) && (LoadOption->OptionalDataSize)) ||
      (BootArgs && (LoadOption->OptionalDataSize == 0))) {
    return FALSE;
  } else if (BootArgs && LoadOption->OptionalDataSize) {
    if (StrCmp (BootArgs, LoadOption->OptionalData) != 0)
      return FALSE;
  }
  if ((LoadOption->Description == NULL) || (BootDescription == NULL)) {
    return FALSE;
  }
  if (StrCmp (BootDescription, LoadOption->Description) != 0) {
    return FALSE;
  }
  if ((LoadOption->Attributes & LOAD_OPTION_CATEGORY) != (LoadOptionAttr & LOAD_OPTION_CATEGORY)) {
    return FALSE;
  }

  Status = gBS->LocateProtocol (&gEfiDevicePathToTextProtocolGuid, NULL, (VOID **)&DevicePathToTextProtocol);
  ASSERT_EFI_ERROR(Status);
  DevicePathText = DevicePathToTextProtocol->ConvertDevicePathToText(LoadOption->FilePathList, TRUE, TRUE);
  if (StrCmp (DevicePathText, BootDevicePathText) != 0) {
    return FALSE;
  }

  FreePool (LoadOption);
  return TRUE;
}

STATIC
EFI_STATUS
EFIAPI
HiKeyCreateBootEntry (
  IN CHAR16          *DevicePathText,
  IN CHAR16          *BootArgs,
  IN CHAR16          *BootDescription,
  IN UINT16           LoadOption
  )
{
  BDS_LOAD_OPTION                    *BdsLoadOption;
  EFI_STATUS                          Status;
  UINTN                               DescriptionSize;
  UINTN                               BootOrderSize;
  CHAR16                              BootVariableName[9];
  UINT8                              *EfiLoadOptionPtr;
  EFI_DEVICE_PATH_PROTOCOL           *DevicePathNode;
  UINTN                               NodeLength;
  EFI_DEVICE_PATH_FROM_TEXT_PROTOCOL *DevicePathFromTextProtocol;

  if ((DevicePathText == NULL) || (BootDescription == NULL)) {
    DEBUG ((EFI_D_ERROR, "%a: Invalid Parameters\n", __func__));
    return EFI_INVALID_PARAMETER;
  }

  UnicodeSPrint (BootVariableName, 9 * sizeof(CHAR16), L"Boot%04X", mBootCount);
  if (HiKeyVerifyBootEntry (BootVariableName, DevicePathText, BootArgs, BootDescription, LoadOption) == TRUE) {
    // The boot entry is already created.
    Status = EFI_SUCCESS;
    goto done;
  }

  BdsLoadOption = (BDS_LOAD_OPTION*)AllocateZeroPool (sizeof(BDS_LOAD_OPTION));
  ASSERT (BdsLoadOption != NULL);

  Status = gBS->LocateProtocol (
                  &gEfiDevicePathFromTextProtocolGuid,
                  NULL,
                  (VOID**)&DevicePathFromTextProtocol
                  );
  ASSERT_EFI_ERROR(Status);

  BdsLoadOption->FilePathList = DevicePathFromTextProtocol->ConvertTextToDevicePath (DevicePathText);
  ASSERT (BdsLoadOption->FilePathList != NULL);
  BdsLoadOption->FilePathListLength = GetDevicePathSize (BdsLoadOption->FilePathList);
  BdsLoadOption->Attributes = LOAD_OPTION_ACTIVE | (LoadOption & LOAD_OPTION_CATEGORY);

  if (BootArgs) {
    /* Always force the BootArgs to save 512 characters. */
    ASSERT (StrSize(BootArgs) <= 512);
    BdsLoadOption->OptionalDataSize = 512;
    BdsLoadOption->OptionalData = (CHAR16*)AllocateZeroPool (BdsLoadOption->OptionalDataSize);
    ASSERT (BdsLoadOption->OptionalData != NULL);
    StrCpy (BdsLoadOption->OptionalData, BootArgs);
  }

  BdsLoadOption->LoadOptionIndex = mBootCount;
  DescriptionSize = StrSize (BootDescription);
  BdsLoadOption->Description = (VOID*)AllocateZeroPool (DescriptionSize);
  StrCpy (BdsLoadOption->Description, BootDescription);

  BdsLoadOption->LoadOptionSize = sizeof(UINT32) + sizeof(UINT16) + DescriptionSize + BdsLoadOption->FilePathListLength + BdsLoadOption->OptionalDataSize;
  BdsLoadOption->LoadOption = (EFI_LOAD_OPTION*)AllocateZeroPool (BdsLoadOption->LoadOptionSize);
  ASSERT (BdsLoadOption->LoadOption != NULL);

  EfiLoadOptionPtr = (VOID*)BdsLoadOption->LoadOption;

  //
  // Populate the EFI Load Option and BDS Boot Option structures
  //

  // Attributes fields
  *(UINT32*)EfiLoadOptionPtr = BdsLoadOption->Attributes;
  EfiLoadOptionPtr += sizeof(UINT32);

  // FilePath List fields
  *(UINT16*)EfiLoadOptionPtr = BdsLoadOption->FilePathListLength;
  EfiLoadOptionPtr += sizeof(UINT16);

  // Boot description fields
  CopyMem (EfiLoadOptionPtr, BdsLoadOption->Description, DescriptionSize);
  EfiLoadOptionPtr += DescriptionSize;

  // File path fields
  DevicePathNode = BdsLoadOption->FilePathList;
  while (!IsDevicePathEndType (DevicePathNode)) {
    NodeLength = DevicePathNodeLength(DevicePathNode);
    CopyMem (EfiLoadOptionPtr, DevicePathNode, NodeLength);
    EfiLoadOptionPtr += NodeLength;
    DevicePathNode = NextDevicePathNode (DevicePathNode);
  }

  // Set the End Device Path Type
  SetDevicePathEndNode (EfiLoadOptionPtr);
  EfiLoadOptionPtr += sizeof(EFI_DEVICE_PATH);

  // Fill the Optional Data
  if (BdsLoadOption->OptionalDataSize > 0) {
    CopyMem (EfiLoadOptionPtr, BdsLoadOption->OptionalData, BdsLoadOption->OptionalDataSize);
  }

  Status = gRT->SetVariable (
                  BootVariableName,
                  &gEfiGlobalVariableGuid,
                  EFI_VARIABLE_NON_VOLATILE | EFI_VARIABLE_BOOTSERVICE_ACCESS | EFI_VARIABLE_RUNTIME_ACCESS,
                  BdsLoadOption->LoadOptionSize,
                  BdsLoadOption->LoadOption
                  );
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "%a: failed to set BootVariable\n", __func__));
    return Status;
  }

done:
  BootOrderSize = mBootCount * sizeof (UINT16);
  mBootOrder = ReallocatePool (BootOrderSize, BootOrderSize + sizeof (UINT16), mBootOrder);
  mBootOrder[mBootCount] = mBootCount;
  mBootCount++;
  return Status;
}

STATIC
EFI_STATUS
EFIAPI
HiKeyCreateBootOrder (
  IN    VOID
  )
{
  UINT16             *BootOrder;
  UINTN               BootOrderSize;
  UINTN               Index;
  EFI_STATUS          Status;

  Status = GetGlobalEnvironmentVariable (L"BootOrder", NULL, &BootOrderSize, (VOID**)&BootOrder);
  if (EFI_ERROR(Status) == 0) {
    if (BootOrderSize == mBootCount) {
      for (Index = 0; Index < mBootCount; Index++) {
        if (BootOrder[Index] != mBootOrder[Index]) {
          break;
        }
      }
      if (Index == mBootCount) {
        // Found BootOrder variable with expected value.
        return EFI_SUCCESS;
      }
    }
  }

  Status = gRT->SetVariable (
                  (CHAR16*)L"BootOrder",
                  &gEfiGlobalVariableGuid,
                  EFI_VARIABLE_NON_VOLATILE | EFI_VARIABLE_BOOTSERVICE_ACCESS | EFI_VARIABLE_RUNTIME_ACCESS,
                  mBootCount * sizeof(UINT16),
                  mBootOrder
                  );
  return Status;
}

STATIC
EFI_STATUS
EFIAPI
HiKeyCreateBootNext (
  IN     VOID
  )
{
  EFI_STATUS          Status;
  UINT16             *BootNext;
  UINTN               BootNextSize;

  BootNextSize = sizeof(UINT16);
  Status = GetGlobalEnvironmentVariable (L"BootNext", NULL, &BootNextSize, (VOID**)&BootNext);
  if (EFI_ERROR(Status) == 0) {
    if (BootNextSize == sizeof (UINT16)) {
      if (*BootNext == mBootOrder[mBootIndex]) {
        // Found the BootNext variable with expected value.
        return EFI_SUCCESS;
      }
    }
  }
  BootNext = &mBootOrder[mBootIndex];
  Status = gRT->SetVariable (
                  (CHAR16*)L"BootNext",
                  &gEfiGlobalVariableGuid,
                  EFI_VARIABLE_NON_VOLATILE | EFI_VARIABLE_BOOTSERVICE_ACCESS | EFI_VARIABLE_RUNTIME_ACCESS,
                  sizeof (UINT16),
                  BootNext
                  );
  return Status;
}

STATIC
VOID
EFIAPI
HiKeyTestLed (
  IN     EMBEDDED_GPIO   *Gpio
  )
{
  EFI_STATUS             Status;

  Status = Gpio->Set (Gpio, USER_LED1, GPIO_MODE_OUTPUT_0);
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "%a: failed to set LED1\n", __func__));
    return;
  }
  Status = Gpio->Set (Gpio, USER_LED2, GPIO_MODE_OUTPUT_1);
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "%a: failed to set LED2\n", __func__));
    return;
  }
  Status = Gpio->Set (Gpio, USER_LED3, GPIO_MODE_OUTPUT_0);
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "%a: failed to set LED3\n", __func__));
    return;
  }
  Status = Gpio->Set (Gpio, USER_LED4, GPIO_MODE_OUTPUT_1);
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "%a: failed to set LED4\n", __func__));
    return;
  }
}


#define REBOOT_REASON_ADDR		0x05F01000
#define REBOOT_REASON_BOOTLOADER	0x77665500
#define REBOOT_REASON_NONE		0x77665501
STATIC
BOOLEAN
EFIAPI
HiKeyDetectRebootReason (
  IN     VOID
  )
{
   UINT32 *addr = (UINT32*)REBOOT_REASON_ADDR;
   UINT32  val;

   val = *addr;
   /* Write NONE to the reason address to clear the state */
   *addr = REBOOT_REASON_NONE;
   /* Check to see if "reboot booloader" was specified */
   if (val == REBOOT_REASON_BOOTLOADER)
     return TRUE;

   return FALSE;
}

STATIC
BOOLEAN
EFIAPI
HiKeyIsJumperConnected (
  IN     VOID
  )
{
  EMBEDDED_GPIO         *Gpio;
  EFI_STATUS             Status;
  UINTN                  Value;

  Status = gBS->LocateProtocol (&gEmbeddedGpioProtocolGuid, NULL, (VOID **)&Gpio);
  ASSERT_EFI_ERROR (Status);

  Status = Gpio->Set (Gpio, DETECT_J15_FASTBOOT, GPIO_MODE_INPUT);
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "%a: failed to set jumper as gpio input\n", __func__));
    return FALSE;
  }
  Status = Gpio->Get (Gpio, DETECT_J15_FASTBOOT, &Value);
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "%a: failed to get value from jumper\n", __func__));
    return FALSE;
  }

  HiKeyTestLed (Gpio);
  if (Value != 0)
    return FALSE;
  return TRUE;
}

STATIC
BOOLEAN
EFIAPI
HiKeySDCardIsPresent (
  IN      VOID
  )
{
  UINT32    Value;

  /*
   * FIXME
   * At first, reading GPIO pin shouldn't exist in SD driver. We need to
   * add some callbacks to handle settings for hardware platform.
   * In the second, reading GPIO pin should be based on GPIO driver. Now
   * GPIO driver could only be used for one PL061 gpio controller. And it's
   * used to detect jumper setting. As a workaround, we have to read the gpio
   * register instead at here.
   *
   */
  Value = MmioRead32 (0xf8012000 + (1 << 2));
  if (Value)
    return FALSE;
  return TRUE;
}

#define BOOT_MAGIC        "ANDROID!"
#define BOOT_MAGIC_LENGTH sizeof (BOOT_MAGIC) - 1

/*
 * Check which boot type is valid for eMMC.
 */
STATIC
EFI_STATUS
EFIAPI
HiKeyCheckEmmcDtbType (
  OUT UINTN       *DtbType
  )
{
  EFI_DEVICE_PATH_PROTOCOL        *BlockDevicePath;
  EFI_BLOCK_IO_PROTOCOL           *BlockIoProtocol;
  EFI_HANDLE                      Handle;
  EFI_STATUS                      Status;
  VOID                            *DataPtr, *AlignedPtr;

  /* Check boot image */
  BlockDevicePath = ConvertTextToDevicePath ((CHAR16*)FixedPcdGetPtr (PcdBootImagePath));
  Status = gBS->LocateDevicePath (&gEfiBlockIoProtocolGuid, &BlockDevicePath, &Handle);
  ASSERT_EFI_ERROR (Status);

  Status = gBS->OpenProtocol (
                      Handle,
                      &gEfiBlockIoProtocolGuid,
                      (VOID **) &BlockIoProtocol,
                      gImageHandle,
                      NULL,
                      EFI_OPEN_PROTOCOL_GET_PROTOCOL
                      );
  ASSERT_EFI_ERROR (Status);

  /* Read the header of boot image. */
  DataPtr = AllocateZeroPool (HIKEY_IO_BLOCK_SIZE * 2);
  ASSERT (DataPtr != 0);
  AlignedPtr = (VOID *)(((UINTN)DataPtr + HIKEY_IO_BLOCK_SIZE - 1) & ~(HIKEY_IO_BLOCK_SIZE - 1));
  InvalidateDataCacheRange (AlignedPtr, HIKEY_IO_BLOCK_SIZE);
  /* TODO: Update 0x7000 by LBA what is fetched from partition. */
  Status = BlockIoProtocol->ReadBlocks (BlockIoProtocol, BlockIoProtocol->Media->MediaId,
                                        0x7000, HIKEY_IO_BLOCK_SIZE, AlignedPtr);
  ASSERT_EFI_ERROR (Status);
  if (AsciiStrnCmp ((CHAR8 *)AlignedPtr, BOOT_MAGIC, BOOT_MAGIC_LENGTH) != 0) {
    /* It's debian boot image. */
    *DtbType = HIKEY_DTB_LINUX;
  } else {
    /* It's android boot image. */
    *DtbType = HIKEY_DTB_ANDROID;
  }
  FreePool (DataPtr);
  return Status;
}

STATIC
BOOLEAN
EFIAPI
HiKeyIsSdBoot (
  IN struct HiKeyBootEntry    *Entry
  )
{
  CHAR16                           *Path;
  EFI_DEVICE_PATH                  *DevicePath, *NextDevicePath;
  EFI_STATUS                        Status;
  EFI_HANDLE                        Handle;
  EFI_SIMPLE_FILE_SYSTEM_PROTOCOL  *FsProtocol;
  EFI_FILE_PROTOCOL                *Fs;
  EFI_FILE_INFO                    *FileInfo;
  EFI_FILE_PROTOCOL                *File;
  FILEPATH_DEVICE_PATH             *FilePathDevicePath;
  UINTN                             Index, Size;
  UINTN                             HandleCount;
  EFI_HANDLE                       *HandleBuffer;
  EFI_DEVICE_PATH_PROTOCOL         *DevicePathProtocol;
  BOOLEAN                           Found = FALSE, Result = FALSE;

  if (HiKeySDCardIsPresent () == FALSE)
    return FALSE;
  Path = Entry[HIKEY_BOOT_ENTRY_BOOT_SD].Path;
  ASSERT (Path != NULL);

  DevicePath = ConvertTextToDevicePath (Path);
  if (DevicePath == NULL) {
    DEBUG ((EFI_D_ERROR, "Warning: Couldn't get device path\n"));
    return FALSE;
  }

  /* Connect handles to drivers. Since simple filesystem driver is loaded later by default. */
  do {
    // Locate all the driver handles
    Status = gBS->LocateHandleBuffer (
                AllHandles,
                NULL,
                NULL,
                &HandleCount,
                &HandleBuffer
                );
    if (EFI_ERROR (Status)) {
      break;
    }

    // Connect every handles
    for (Index = 0; Index < HandleCount; Index++) {
      gBS->ConnectController (HandleBuffer[Index], NULL, NULL, TRUE);
    }

    if (HandleBuffer != NULL) {
      FreePool (HandleBuffer);
    }

    // Check if new handles have been created after the start of the previous handles
    Status = gDS->Dispatch ();
  } while (!EFI_ERROR(Status));

  // List all the Simple File System Protocols
  Status = gBS->LocateHandleBuffer (ByProtocol, &gEfiSimpleFileSystemProtocolGuid, NULL, &HandleCount, &HandleBuffer);
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "Warning: Failed to list all the simple filesystem protocols (status:%r)\n", Status));
    return FALSE;
  }
  for (Index = 0; Index < HandleCount; Index++) {
    Status = gBS->HandleProtocol (HandleBuffer[Index], &gEfiDevicePathProtocolGuid, (VOID **)&DevicePathProtocol);
    if (EFI_ERROR(Status))
      continue;
    NextDevicePath = NextDevicePathNode (DevicePath);
    Size = (UINTN)NextDevicePath - (UINTN)DevicePath;
    if (Size <= GetDevicePathSize (DevicePath)) {
      if ((CompareMem (DevicePath, DevicePathProtocol, Size)) == 0) {
	Found = TRUE;
        break;
      }
    }
  }
  if (!Found) {
    DEBUG ((EFI_D_ERROR, "Warning: Failed to find valid device path\n"));
    return FALSE;
  }

  Handle = HandleBuffer[Index];
  Status = gBS->LocateDevicePath (&gEfiDevicePathProtocolGuid, &DevicePath, &Handle);
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "Warning: Couldn't locate device (status: %r)\n", Status));
    return FALSE;
  }
  FilePathDevicePath = (FILEPATH_DEVICE_PATH*)DevicePath;

  Status = gBS->OpenProtocol (
                  Handle,
                  &gEfiSimpleFileSystemProtocolGuid,
                  (VOID**)&FsProtocol,
                  gImageHandle,
                  Handle,
                  EFI_OPEN_PROTOCOL_BY_DRIVER
                  );
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "Warning: Failedn't to mount as Simple Filrsystem (status: %r)\n", Status));
    return FALSE;
  }
  Status = FsProtocol->OpenVolume (FsProtocol, &Fs);
  if (EFI_ERROR (Status)) {
    goto CLOSE_PROTOCOL;
  }

  Status = Fs->Open (Fs, &File, FilePathDevicePath->PathName, EFI_FILE_MODE_READ, 0);
  if (EFI_ERROR (Status)) {
    goto CLOSE_PROTOCOL;
  }

  Size = 0;
  File->GetInfo (File, &gEfiFileInfoGuid, &Size, NULL);
  FileInfo = AllocatePool (Size);
  Status = File->GetInfo (File, &gEfiFileInfoGuid, &Size, FileInfo);
  if (EFI_ERROR (Status)) {
    goto CLOSE_FILE;
  }

  // Get the file size
  Size = FileInfo->FileSize;
  FreePool (FileInfo);
  if (Size != 0) {
    Result = TRUE;
  }

CLOSE_FILE:
  File->Close (File);
CLOSE_PROTOCOL:
  gBS->CloseProtocol (
         Handle,
         &gEfiSimpleFileSystemProtocolGuid,
         gImageHandle,
         Handle);
  return Result;
}

STATIC
EFI_STATUS
EFIAPI
HiKeyInstallFdt (
  VOID
  )
{
  EFI_STATUS              Status;
  VOID                   *Image;
  UINTN                   ImageSize, NumPages;
  EFI_GUID               *Guid;
  EFI_PHYSICAL_ADDRESS    FdtConfigurationTableBase;

  Guid = &gHiKeyTokenSpaceGuid;
  Status = GetSectionFromAnyFv (Guid, EFI_SECTION_RAW, 0, &Image, &ImageSize);
  if (EFI_ERROR (Status))
    return Status;
  NumPages = EFI_SIZE_TO_PAGES (ImageSize);
  Status = gBS->AllocatePages (
		  AllocateAnyPages, EfiRuntimeServicesData,
		  NumPages, &FdtConfigurationTableBase
		  );
  if (EFI_ERROR (Status))
    return Status;
  CopyMem ((VOID *)(UINTN)FdtConfigurationTableBase, Image, ImageSize);
  Status = gBS->InstallConfigurationTable (
		  &gFdtTableGuid,
		  (VOID *)(UINTN)FdtConfigurationTableBase
		  );
  if (EFI_ERROR (Status)) {
    gBS->FreePages (FdtConfigurationTableBase, NumPages);
  }
  return Status;
}

STATIC
VOID
EFIAPI
HiKeyOnEndOfDxe (
  EFI_EVENT                               Event,
  VOID                                    *Context
  )
{
  EFI_STATUS          Status;
  UINTN               VariableSize;
  UINT16              AutoBoot, Count, Index;
  UINTN               DtbType;
  struct HiKeyBootEntry *Entry;

  VariableSize = sizeof (UINT16);
  Status = gRT->GetVariable (
                  (CHAR16 *)L"HiKeyAutoBoot",
                  &gHiKeyVariableGuid,
                  NULL,
                  &VariableSize,
                  (VOID*)&AutoBoot
                  );
  if (Status == EFI_NOT_FOUND) {
    AutoBoot = 1;
    Status = gRT->SetVariable (
                    (CHAR16*)L"HiKeyAutoBoot",
                    &gHiKeyVariableGuid,
                    EFI_VARIABLE_NON_VOLATILE       |
                    EFI_VARIABLE_BOOTSERVICE_ACCESS |
                    EFI_VARIABLE_RUNTIME_ACCESS,
                    sizeof (UINT16),
                    &AutoBoot
                    );
    ASSERT_EFI_ERROR (Status);
  } else if (EFI_ERROR (Status) == 0) {
    if (AutoBoot == 0) {
      // Select boot entry by manual.
      // Delete the BootNext environment variable
      gRT->SetVariable (L"BootNext",
             &gEfiGlobalVariableGuid,
             EFI_VARIABLE_NON_VOLATILE | EFI_VARIABLE_BOOTSERVICE_ACCESS | EFI_VARIABLE_RUNTIME_ACCESS,
             0,
             NULL);
      return;
    }
  }

  Status = HiKeyCheckEmmcDtbType (&DtbType);
  ASSERT_EFI_ERROR (Status);

  mBootCount = 0;
  mBootOrder = NULL;

  if (DtbType == HIKEY_DTB_LINUX) {
    Count = sizeof (LinuxEntries) / sizeof (struct HiKeyBootEntry);
    Entry = LinuxEntries;
  } else if (DtbType == HIKEY_DTB_ANDROID) {
    Count = sizeof (AndroidEntries) / sizeof (struct HiKeyBootEntry);
    Entry = AndroidEntries;
  } else {
    ASSERT (0);
  }
  ASSERT (HIKEY_DTB_SD < Count);
  if (HiKeyIsSdBoot (Entry) == TRUE)
    DtbType = HIKEY_DTB_SD;

  for (Index = 0; Index < Count; Index++) {
    Status = HiKeyCreateBootEntry (
               Entry->Path,
               Entry->Args,
               Entry->Description,
               Entry->LoadType
               );
    ASSERT_EFI_ERROR (Status);
    Entry++;
  }

  if ((mBootCount == 0) || (mBootCount >= MAX_BOOT_ENTRIES)) {
    DEBUG ((EFI_D_ERROR, "%a: can't create boot entries\n", __func__));
    return;
  }

  Status = HiKeyCreateBootOrder ();
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "%a: failed to set BootOrder variable\n", __func__));
    return;
  }

  if (DtbType == HIKEY_DTB_SD) {
    mBootIndex = HIKEY_BOOT_ENTRY_BOOT_SD;
  } else {
    mBootIndex = HIKEY_BOOT_ENTRY_BOOT_EMMC;
  }

  if (HiKeyGetUsbMode () == USB_DEVICE_MODE) {
    if (HiKeyIsJumperConnected () == TRUE)
      mBootIndex = HIKEY_BOOT_ENTRY_FASTBOOT;
    /* Set mBootIndex as HIKEY_BOOT_ENTRY_FASTBOOT if adb reboot-bootloader is specified */
    if (HiKeyDetectRebootReason () == TRUE)
      mBootIndex = HIKEY_BOOT_ENTRY_FASTBOOT;
  }

  Status = HiKeyCreateBootNext ();
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "%a: failed to set BootNext variable\n", __func__));
    return;
  }

  /*
   * Priority of Loading DTB file:
   *   1. Load configured DTB file in grub.cfg.
   *   2. Load DTB file in UEFI Fv.
   */
  /* Load DTB file in UEFI Fv. */
  Status = HiKeyInstallFdt ();
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "%a: failed to install Fdt file\n", __func__));
    return;
  }
}

EFI_STATUS
HiKeyBootMenuInstall (
  IN VOID
  )
{
  EFI_STATUS          Status;
  EFI_EVENT           EndOfDxeEvent;

  Status = gBS->CreateEventEx (
                  EVT_NOTIFY_SIGNAL,
                  TPL_CALLBACK,
                  HiKeyOnEndOfDxe,
                  &mIsEndOfDxeEvent,
                  &gEfiEndOfDxeEventGroupGuid,
                  &EndOfDxeEvent
                  );
  ASSERT_EFI_ERROR (Status);
  return Status;
}

