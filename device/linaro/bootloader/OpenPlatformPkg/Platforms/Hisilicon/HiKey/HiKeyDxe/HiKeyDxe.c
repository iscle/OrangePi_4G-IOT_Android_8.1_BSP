/** @file
*
*  Copyright (c) 2015-2016, Linaro Ltd. All rights reserved.
*  Copyright (c) 2015-2016, Hisilicon Ltd. All rights reserved.
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
#include <Library/MemoryAllocationLib.h>
#include <Library/UefiBootServicesTableLib.h>
#include <Library/UefiLib.h>
#include <Library/UefiRuntimeServicesTableLib.h>

#include <Guid/HiKeyVariable.h>

#include <Protocol/BlockIo.h>
#include <Protocol/DwUsb.h>

#include "HiKeyDxeInternal.h"

#define SERIAL_NUMBER_LENGTH        17
#define SERIAL_NUMBER_LBA           1024
#define SERIAL_NUMBER_BLOCK_SIZE    512
#define RANDOM_MAGIC                0x9a4dbeaf

struct RandomSerialNo {
  UINTN              Magic;
  UINTN              Data;
  CHAR8              SerialNo[32];
};

STATIC CHAR16 mSerialNo[17];

STATIC
UINTN
EFIAPI
HiKeyInitSerialNo (
  IN   VOID
  )
{
  EFI_STATUS                      Status;
  UINTN                           VariableSize;
  EFI_DEVICE_PATH_PROTOCOL        *BlockDevicePath;
  EFI_BLOCK_IO_PROTOCOL           *BlockIoProtocol;
  EFI_HANDLE                      Handle;
  VOID                            *DataPtr;
  struct RandomSerialNo           *Random;
  CHAR16                          DefaultSerialNo[] = L"0123456789abcdef";
  CHAR16                          SerialNoUnicode[32], DataUnicode[32];

  BlockDevicePath = ConvertTextToDevicePath ((CHAR16*)FixedPcdGetPtr (PcdAndroidFastbootNvmDevicePath));
  Status = gBS->LocateDevicePath (&gEfiBlockIoProtocolGuid, &BlockDevicePath, &Handle);
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "Warning: Couldn't locate block device (status: %r)\n", Status));
    return EFI_INVALID_PARAMETER;
  }
  Status = gBS->OpenProtocol (
		      Handle,
                      &gEfiBlockIoProtocolGuid,
		      (VOID **) &BlockIoProtocol,
                      gImageHandle,
                      NULL,
                      EFI_OPEN_PROTOCOL_GET_PROTOCOL
                      );
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "Warning: Couldn't open block device (status: %r)\n", Status));
    return EFI_DEVICE_ERROR;
  }
  DataPtr = AllocateZeroPool (SERIAL_NUMBER_BLOCK_SIZE);
  WriteBackDataCacheRange (DataPtr, SERIAL_NUMBER_BLOCK_SIZE);
  Status = BlockIoProtocol->ReadBlocks (BlockIoProtocol, BlockIoProtocol->Media->MediaId,
                                        SERIAL_NUMBER_LBA, SERIAL_NUMBER_BLOCK_SIZE,
                                        DataPtr);
  if (EFI_ERROR (Status)) {
    DEBUG ((EFI_D_ERROR, "Warning: failed on reading blocks.\n"));
    goto exit;
  }
  InvalidateDataCacheRange (DataPtr, SERIAL_NUMBER_BLOCK_SIZE);
  Random = (struct RandomSerialNo *)DataPtr;
  if (Random->Magic != RANDOM_MAGIC) {
    VariableSize = SERIAL_NUMBER_LENGTH * sizeof (CHAR16);
    Status = gRT->GetVariable (
                    (CHAR16 *)L"SerialNo",
                    &gHiKeyVariableGuid,
                    NULL,
                    &VariableSize,
                    &DefaultSerialNo
                    );
    if (Status == EFI_NOT_FOUND) {
      Status = gRT->SetVariable (
                      (CHAR16*)L"SerialNo",
                      &gHiKeyVariableGuid,
                      EFI_VARIABLE_NON_VOLATILE       |
                      EFI_VARIABLE_BOOTSERVICE_ACCESS |
                      EFI_VARIABLE_RUNTIME_ACCESS,
                      VariableSize,
                      DefaultSerialNo
                      );
    }
    CopyMem (mSerialNo, DefaultSerialNo, sizeof (DefaultSerialNo));
  } else {
    ZeroMem (DataUnicode, 32 * sizeof(CHAR16));
    ZeroMem (SerialNoUnicode, 32 * sizeof(CHAR16));
    AsciiStrToUnicodeStr (Random->SerialNo, SerialNoUnicode);
    VariableSize = SERIAL_NUMBER_LENGTH * sizeof (CHAR16);
    Status = gRT->GetVariable (
                    (CHAR16 *)L"SerialNo",
                    &gHiKeyVariableGuid,
                    NULL,
                    &VariableSize,
                    &DataUnicode
                    );
    if ((Status == EFI_NOT_FOUND) || StrCmp (DataUnicode, SerialNoUnicode)) {
      Status = gRT->SetVariable (
                      (CHAR16*)L"SerialNo",
                      &gHiKeyVariableGuid,
                      EFI_VARIABLE_NON_VOLATILE       |
                      EFI_VARIABLE_BOOTSERVICE_ACCESS |
                      EFI_VARIABLE_RUNTIME_ACCESS,
                      VariableSize,
                      SerialNoUnicode
                      );
    }
    CopyMem (mSerialNo, SerialNoUnicode, VariableSize);
  }
exit:
  FreePool (DataPtr);
  return Status;
}

EFI_STATUS
EFIAPI
GetSerialNo (
  OUT CHAR16            *SerialNo,
  OUT UINT8             *Length
  )
{
  EFI_STATUS             Status;
  UINTN                  VariableSize;
  CHAR16                 DataUnicode[32];

  if (SerialNo == NULL)
    return EFI_INVALID_PARAMETER;
  VariableSize = SERIAL_NUMBER_LENGTH * sizeof (CHAR16);
  ZeroMem (DataUnicode, 32 * sizeof(CHAR16));
  Status = gRT->GetVariable (
                  (CHAR16 *)L"SerialNo",
                  &gHiKeyVariableGuid,
                  NULL,
                  &VariableSize,
                  &DataUnicode
                  );
  if (EFI_ERROR (Status)) {
    return Status;
  }
  CopyMem (SerialNo, DataUnicode, VariableSize);
  *Length = VariableSize;
  return EFI_SUCCESS;
}

EFI_STATUS
EFIAPI
UsbPhyInit (
  IN UINT8     Mode
  )
{
  return HiKeyUsbPhyInit (Mode);
}

DW_USB_PROTOCOL mDwUsbDevice = {
  GetSerialNo,
  UsbPhyInit
};

EFI_STATUS
EFIAPI
HiKeyEntryPoint (
  IN EFI_HANDLE         ImageHandle,
  IN EFI_SYSTEM_TABLE   *SystemTable
  )
{
  EFI_STATUS           Status;

  Status = gBS->InstallProtocolInterface (
		  &ImageHandle,
		  &gDwUsbProtocolGuid,
		  EFI_NATIVE_INTERFACE,
		  &mDwUsbDevice
		  );
  if (EFI_ERROR (Status)) {
    return Status;
  }

  HiKeyInitSerialNo ();
  HiKeyInitPeripherals ();

  Status = HiKeyBootMenuInstall ();

  return Status;
}
