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

#include <Library/IoLib.h>
#include <Library/TimerLib.h>
#include <Library/UefiLib.h>
#include <Library/UefiRuntimeServicesTableLib.h>

#include <Protocol/EmbeddedGpio.h>
#include <Protocol/DwUsb.h>

#include <Hi6220.h>

#include "Hi6220RegsPeri.h"
#include "HiKeyDxeInternal.h"

#define USB_SEL_GPIO0_3          3     // GPIO 0_3
#define USB_5V_HUB_EN            7     // GPIO 0_7
#define USB_ID_DET_GPIO2_5       21    // GPIO 2_5
#define USB_VBUS_DET_GPIO2_6     22    // GPIO 2_6

// Jumper on pin5-6 of J15 determines whether boot to fastboot
#define DETECT_J15_FASTBOOT      24    // GPIO 3_0

STATIC EMBEDDED_GPIO *mGpio;

STATIC
VOID
HiKeyDetectUsbModeInit (
  IN VOID
  )
{
  EFI_STATUS     Status;

  /* set pullup on both GPIO2_5 & GPIO2_6. It's required for inupt. */
  MmioWrite32 (0xf8001864, 1);
  MmioWrite32 (0xf8001868, 1);

  Status = gBS->LocateProtocol (&gEmbeddedGpioProtocolGuid, NULL, (VOID **)&mGpio);
  ASSERT_EFI_ERROR (Status);
  Status = mGpio->Set (mGpio, USB_SEL_GPIO0_3, GPIO_MODE_OUTPUT_0);
  ASSERT_EFI_ERROR (Status);
  Status = mGpio->Set (mGpio, USB_5V_HUB_EN, GPIO_MODE_OUTPUT_0);
  ASSERT_EFI_ERROR (Status);
  MicroSecondDelay (1000);

  Status = mGpio->Set (mGpio, USB_ID_DET_GPIO2_5, GPIO_MODE_INPUT);
  ASSERT_EFI_ERROR (Status);
  Status = mGpio->Set (mGpio, USB_VBUS_DET_GPIO2_6, GPIO_MODE_INPUT);
  ASSERT_EFI_ERROR (Status);
}

UINTN
HiKeyGetUsbMode (
  IN VOID
  )
{
  EFI_STATUS     Status;
  UINTN          GpioId, GpioVbus;
  UINTN          Value;

  Status = mGpio->Get (mGpio, USB_ID_DET_GPIO2_5, &Value);
  ASSERT_EFI_ERROR (Status);
  GpioId = Value;
  Status = mGpio->Get (mGpio, USB_VBUS_DET_GPIO2_6, &Value);
  ASSERT_EFI_ERROR (Status);
  GpioVbus = Value;

  if ((GpioId == 1) && (GpioVbus == 0))
    return USB_DEVICE_MODE;
  else if ((GpioId == 0) && (GpioVbus == 1))
    return USB_CABLE_NOT_ATTACHED;
  return USB_HOST_MODE;
}

EFI_STATUS
HiKeyUsbPhyInit (
  IN UINT8        Mode
  )
{
  UINTN         Value;
  UINT32        Data;

  HiKeyDetectUsbModeInit ();

  //setup clock
  MmioWrite32 (PERI_CTRL_BASE + SC_PERIPH_CLKEN0, BIT4);
  do {
       Value = MmioRead32 (PERI_CTRL_BASE + SC_PERIPH_CLKSTAT0);
  } while ((Value & BIT4) == 0);

  //setup phy
  Data = RST0_USBOTG_BUS | RST0_POR_PICOPHY |
           RST0_USBOTG | RST0_USBOTG_32K;
  MmioWrite32 (PERI_CTRL_BASE + SC_PERIPH_RSTDIS0, Data);
  do {
    Value = MmioRead32 (PERI_CTRL_BASE + SC_PERIPH_RSTSTAT0);
    Value &= Data;
  } while (Value);

  Value = MmioRead32 (PERI_CTRL_BASE + SC_PERIPH_CTRL4);
  Value &= ~(CTRL4_PICO_SIDDQ | CTRL4_FPGA_EXT_PHY_SEL |
             CTRL4_OTG_PHY_SEL);
  Value |=  CTRL4_PICO_VBUSVLDEXT | CTRL4_PICO_VBUSVLDEXTSEL;
  MmioWrite32 (PERI_CTRL_BASE + SC_PERIPH_CTRL4, Value);
  MicroSecondDelay (1000);

  //If Mode = 1, USB in Device Mode
  //If Mode = 0, USB in Host Mode
  if (Mode == USB_DEVICE_MODE) {
    if (HiKeyGetUsbMode () == USB_DEVICE_MODE) {
      DEBUG ((EFI_D_ERROR, "usb work as device mode.\n"));
    } else {
      return EFI_INVALID_PARAMETER;
    }

     Value = MmioRead32 (PERI_CTRL_BASE + SC_PERIPH_CTRL5);
     Value &= ~CTRL5_PICOPHY_BC_MODE;
     MmioWrite32 (PERI_CTRL_BASE + SC_PERIPH_CTRL5, Value);
     MicroSecondDelay (20000);
  } else {
    if (HiKeyGetUsbMode () == USB_HOST_MODE) {
      DEBUG ((EFI_D_ERROR, "usb work as host mode.\n"));
    } else {
      return EFI_INVALID_PARAMETER;
    }

    /*CTRL5*/
    Data = MmioRead32 (PERI_CTRL_BASE + SC_PERIPH_CTRL5);
    Data &= ~CTRL5_PICOPHY_BC_MODE;
    Data |= CTRL5_USBOTG_RES_SEL | CTRL5_PICOPHY_ACAENB |
            CTRL5_PICOPHY_VDATDETENB | CTRL5_PICOPHY_DCDENB;
    MmioWrite32 (PERI_CTRL_BASE + SC_PERIPH_CTRL5, Data);
    MicroSecondDelay (20000);
    MmioWrite32 (PERI_CTRL_BASE + 0x018, 0x70533483); //EYE_PATTERN

    MicroSecondDelay (5000);
  }

  return EFI_SUCCESS;
}

STATIC
VOID
UartInit (
  IN VOID
  )
{
  UINT32     Val;

  /* make UART1 out of reset */
  MmioWrite32 (PERI_CTRL_BASE + SC_PERIPH_RSTDIS3, PERIPH_RST3_UART1);
  MmioWrite32 (PERI_CTRL_BASE + SC_PERIPH_CLKEN3, PERIPH_RST3_UART1);
  /* make UART2 out of reset */
  MmioWrite32 (PERI_CTRL_BASE + SC_PERIPH_RSTDIS3, PERIPH_RST3_UART2);
  MmioWrite32 (PERI_CTRL_BASE + SC_PERIPH_CLKEN3, PERIPH_RST3_UART2);
  /* make UART3 out of reset */
  MmioWrite32 (PERI_CTRL_BASE + SC_PERIPH_RSTDIS3, PERIPH_RST3_UART3);
  MmioWrite32 (PERI_CTRL_BASE + SC_PERIPH_CLKEN3, PERIPH_RST3_UART3);
  /* make UART4 out of reset */
  MmioWrite32 (PERI_CTRL_BASE + SC_PERIPH_RSTDIS3, PERIPH_RST3_UART4);
  MmioWrite32 (PERI_CTRL_BASE + SC_PERIPH_CLKEN3, PERIPH_RST3_UART4);

  /* make DW_MMC2 out of reset */
  MmioWrite32 (PERI_CTRL_BASE + SC_PERIPH_RSTDIS0, PERIPH_RST0_MMC2);

  /* enable clock for BT/WIFI */
  Val = MmioRead32 (PMUSSI_REG(0x1c)) | 0x40;
  MmioWrite32 (PMUSSI_REG(0x1c), Val);
}

STATIC
VOID
MtcmosInit (
  IN VOID
  )
{
  UINT32     Data;

  /* enable MTCMOS for GPU */
  MmioWrite32 (AO_CTRL_BASE + SC_PW_MTCMOS_EN0, PW_EN0_G3D);
  do {
    Data = MmioRead32 (AO_CTRL_BASE + SC_PW_MTCMOS_ACK_STAT0);
  } while ((Data & PW_EN0_G3D) == 0);
}

EFI_STATUS
HiKeyInitPeripherals (
  IN VOID
  )
{
  UINT32     Data, Bits;

  /* make I2C0/I2C1/I2C2/SPI0 out of reset */
  Bits = PERIPH_RST3_I2C0 | PERIPH_RST3_I2C1 | PERIPH_RST3_I2C2 | \
	 PERIPH_RST3_SSP;
  MmioWrite32 (PERI_CTRL_BASE + SC_PERIPH_RSTDIS3, Bits);

  do {
    Data = MmioRead32 (PERI_CTRL_BASE + SC_PERIPH_RSTSTAT3);
  } while (Data & Bits);

  UartInit ();
  MtcmosInit ();

  return EFI_SUCCESS;
}
