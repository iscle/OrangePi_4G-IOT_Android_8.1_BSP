/** @file
  Erase Block Protocol as defined in the UEFI 2.6 specification.

  The Erase Block Protocol is optional provides the ability for a device to expose
  erase functionality.

  Copyright (c) 2016 - 2017, Linaro Limited. All rights reserved.<BR>
  This program and the accompanying materials
  are licensed and made available under the terms and conditions of the BSD License
  which accompanies this distribution.  The full text of the license may be found at
  http://opensource.org/licenses/bsd-license.php

  THE PROGRAM IS DISTRIBUTED UNDER THE BSD LICENSE ON AN "AS IS" BASIS,
  WITHOUT WARRANTIES OR REPRESENTATIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED.

**/

#ifndef __ERASE_BLOCK_H__
#define __ERASE_BLOCK_H__

#define EFI_ERASE_BLOCK_PROTOCOL_GUID \
  { \
    0x95A9A93E, 0xA86E, 0x4926, {0xaa, 0xef, 0x99, 0x18, 0xe7, 0x72, 0xd9, 0x87} \
  }

typedef struct _EFI_ERASE_BLOCK_PROTOCOL EFI_ERASE_BLOCK_PROTOCOL;


#define EFI_ERASE_BLOCK_PROTOCOL_REVISION ((2<<16) | (60))

typedef struct {
  EFI_EVENT       Event;
  EFI_STATUS      TransactionStatus;
} EFI_ERASE_BLOCK_TOKEN;

/**
  Erase a specified number of device blocks.

  @param  This       Indicates a pointer to the calling context.
  @param  MediaId    The media ID that the erase request is for.
  @param  Lba        The starting logical block address to be erased. The caller is
                     responsible for erasing only legitimate locations.
  @param  Token      A pointer to the token associated with the transaction.
  @param  Size       The size in bytes to be erased. This must be a multiple of the
                     physical block size of the device.

  @retval EFI_SUCCESS           The erase request was queued if Event is not NULL. The data was
                                erased correctly to the device if the Event is NULL.
  @retval EFI_WRITE_PROTECTED   The device cannot be erased due to write protection.
  @retval EFI_DEVICE_ERROR      The device reported an error while attempting to perform the erase operation.
  @retval EFI_INVALID_PARAMETER The erase request contain LBAs that are not valid.
  @retval EFI_NO_MEDIA          There is no media in the device.
  @retval EFI_MEDIA_CHANGED     The MediaId is not for the current media.

**/

typedef
EFI_STATUS
(EFIAPI *EFI_BLOCK_ERASE)(
  IN EFI_BLOCK_IO_PROTOCOL          *This,
  IN UINT32                         MediaId,
  IN EFI_LBA                        Lba,
  IN OUT EFI_ERASE_BLOCK_TOKEN      *Token,
  IN UINTN                          Size
  );

///
///  This protocol provides erase functionality
///
struct _EFI_ERASE_BLOCK_PROTOCOL {
  ///
  /// The revision to which the erase block interface adheres. All future
  /// revisions must be backwards compatible. If a future version is not
  /// back wards compatible, it is not the same GUID.
  ///
  UINT64              Revision;
  ///
  /// Returns the erase length granularity as a number of logical
  /// blocks. A value of 1 means the erase granularity is one logical
  /// block.
  ///
  UINT32              EraseLengthGranularity;
  ///
  /// Erase the requested number of blocks from the device.
  ///
  EFI_BLOCK_ERASE     EraseBlocks;
};


extern EFI_GUID gEfiEraseBlockProtocolGuid;

#endif // __ERASE_BLOCK_H__
