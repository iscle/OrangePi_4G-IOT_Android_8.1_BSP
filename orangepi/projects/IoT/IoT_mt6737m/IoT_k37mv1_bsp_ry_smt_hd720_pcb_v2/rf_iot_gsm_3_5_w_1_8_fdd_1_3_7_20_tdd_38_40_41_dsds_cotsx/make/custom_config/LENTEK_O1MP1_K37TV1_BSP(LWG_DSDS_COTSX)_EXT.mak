#
#  Copyright Statement:
#  ---------------------------
#  This software/firmware and related documentation ("MediaTek Software") are
#  protected under relevant copyright laws. The information contained herein 
#  is confidential and proprietary to MediaTek Inc. and/or its licensors.  
#  Without the prior written permission of MediaTek inc. and/or its licensors,
#  any reproduction,modification, use or disclosure of MediaTek Software, and
#  information contained herein, in whole or in part, shall be strictly prohibited.
#
#  MediaTek Inc.(C)2016.All rights reserved.
#
#  BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND
#  AGREES THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK 
#  SOFTWARE") RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED 
#  TO RECEIVER ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL 
#  WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED 
#  WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR 
#  NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER 
#  WITH RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, 
#  INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER 
#  AGREES TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING 
#  THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE 
#  RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES 
#  CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR 
#  ANY MEDIATEK SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO 
#  CONFORM TO A PARTICULAR STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND 
#  EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT 
#  TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,AT MEDIATEK'S OPTION, 
#  TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,OR REFUND ANY SOFTWARE 
#  LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK 
#  SOFTWARE AT ISSUE. 
#
# *************************************************************************

# Generated at 2018-02-05 17:10:52

CHK_ENV_FLAG = TRUE
  # Description:
  #   to check build environment and tool chain version
  # Option Values:
  #   FALSE: need to check build environment and tool chain version;
  #          show WARNING if the environment is not recommended.
  #   TRUE: need to check build environment and tool chain version;
  #         show error if the environment is not recommended.
  # Switch-ability:
  #   Switchable

DUAL_SIM_HOT_SWAP_CO_DECK = TRUE
  # Description:
  #   support SIM hot swap in the SAME deck
  # Option Values:
  #   FALSE: support dual SIM card hot plug in different deck
  #   TRUE: Support dual SIM card hot plug in the same deck
  # Switch-ability:
  #   Switchable

IMS_OTA_MESSAGE_SUPPORT = FALSE
  # Description:
  #   IMS_OTA_MESSAGE_SUPPORT is used to control SIP view in ELT and this function is only limited provision to authorized customers
  # Option Values:
  #   FALSE: Disable this function in MD load then ELT can't show send/recv SIP messages in ELT
  #          This is default behavior to generic customer
  #   TRUE: Enable this function in MD load then ELT can show send/recv SIP messages in ELT
  # Switch-ability:
  #   Switchable

NOT_BENEFIT_FROM_BATTERY_CONSUMPTION_OPTIMISATION = FALSE
  # Description:
  #   FALSE: UE benefits from battery consumption optimisation mechanism from network, e.g. mobile phone   # TRUE: UE doesn't benefit from battery consumption optimisation mechanism from network, e.g. data card
  # Option Values:
  #   FALSE: UE benefits from battery consumption optimisation mechanism from network, e.g. mobile phone
  #   TRUE: UE doesn't benefit from battery consumption optimisation mechanism from network, e.g. data card
  # Switch-ability:
  #   Switchable

SECURE_CUSTOM_NAME = MTK
  # Description:
  #   Support security system
  # Option Values:
  #   JRD: .
  #   MTK: Customer name for MTK
  #   NONE: No customer name specified, non secure load only
  # Switch-ability:
  #   Switchable


# =========================================================================
# Non-Switchable Feature Option
# =========================================================================
3G_NEW_DATA_PLANE_ARCH = TRUE
  # Description:
  #   The option is to enable new data plane architecture
  # Option Values:
  #   FALSE: disable new data plane architecture
  #   TRUE: enable new data plane architecture
  # Switch-ability:
  #   Non-switchable

3G_VIDEO_CALL = FALSE
  # Description:
  #   Enable 3g video call
  # Option Values:
  #   FALSE: Turn off 3G video call on feature phone (for data card or 3G video call on smart phone)
  #   TRUE: 3G Video Telephony on feature phone is supported
  # Switch-ability:
  #   Non-switchable

ACC_NC_AFC_DB_UPDATE_SUPPORT = FALSE
  # Description:
  #   Enable/Disable accelerating neighboring cell AFC database updating using serving cell AFC tracking results
  #   This feature will improve AFC tracking ability of neighboring cells during temperature change rapidly.
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

AGPS_CP_SIB15_SUPPORT = TRUE
  # Description:
  #   To enable UMTS RRC processing on SIB15 for AGPS
  # Option Values:
  #   FALSE: SIB15 series messages can not be supported
  #   TRUE: SIB15 series messages can be supported
  # Switch-ability:
  #   Non-switchable

AOC_SUPPORT = TRUE
  # Description:
  #   3GPP feature. TS.22.024, 22.086 
  #   User can see the call cost in the MENU, and if call cost is larger than the upper limit stored in the SIM card , call will be ended.  (Under the constrient that NW. has sent AOCC in the peer MSG. )   . AOCC is a information about how to charge the call per uint.
  # Option Values:
  #   FALSE: Turn off AOC
  #   TRUE: Turn on AOC
  # Switch-ability:
  #   Non-switchable

AUTOMOTIVE_TELEMATICS = FALSE
  # Description:
  #   ECNR for Automobile
  # Option Values:
  #   FALSE: Not Support ECNR for Automobile
  #   TRUE: Support ECNR for Automobile
  # Switch-ability:
  #   Non-switchable

BIP_SCWS_SUPPORT = FALSE
  # Description:
  #   BIP SCWS feature:
  #   Server mode: To establish channel between Browser and UICC
  #   Client mode: To establish channel between UICC and network
  # Option Values:
  #   FALSE: Disable BIP support(default)
  #   TRUE: Enable BIP support
  # Switch-ability:
  #   Non-switchable

BTT_AGENT_ENABLE = TRUE
  # Description:
  #   For Baseband Testing Tool (BTT) support
  # Option Values:
  #   FALSE: Don't support BTT in this load
  #   TRUE: Support BTT in this load
  # Switch-ability:
  #   Non-switchable

BYTECOPY_SUPPORT = TRUE
  # Description:
  #   Enable/Disable BYTECOPY HW support
  # Option Values:
  #   FALSE: Disable BYTECOPY HW driver
  #   TRUE: Enable BYTECOPY HW driver
  # Switch-ability:
  #   Non-switchable

CCCI_DEV_SUPPORT = TRUE
  # Description:
  #   Used to enable CCCI device related components, CCCITTY, CCCI_CCMNI, CCCI_FS.
  # Option Values:
  #   FALSE: CCCI device related components will not be built.
  #   TRUE: CCCI device related components will be built.
  # Switch-ability:
  #   Non-switchable

CCCI_FS_SUPPORT = TRUE
  # Description:
  #   Use to enable CCCI FS related component
  # Option Values:
  #   FALSE: CCCI FS related components will not be built.
  #   TRUE: CCCI FS related components will be built.
  # Switch-ability:
  #   Non-switchable

CNAP_SUPPORT = FALSE
  # Description:
  #   Enable/Disable Name identification supplementary services feature (TS22.096) support
  # Option Values:
  #   FALSE: Turn off CNAP
  #   TRUE: Turn on CNAP
  # Switch-ability:
  #   Non-switchable

COMPILER = GCC
  # Description:
  #   RVCT or ADS
  # Option Values:
  #   ADS: Use ARM ADS tool chain.
  #   GCC: Use GNU GCC tool chain (Mentor Sourcery 2010.09 , 4.5.1)
  #   RVCT: Use ARM RVCT tool chain
  # Switch-ability:
  #   Non-switchable

CUSTOM_FLAVOR = LWG_DSDS_COTSX
  # Description:
  #   custom folder flavor setting
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

DHL_DNT_LOGGING = FALSE
  # Description:
  #   Enable SIB logging (High speed logging interface) for DHL based load
  # Option Values:
  #   FALSE: Disable DNT logging
  #   TRUE: Enable DNT logging
  # Switch-ability:
  #   Non-switchable

DHL_SET_LOG_BUF_SIZ = 4096
  # Description:
  #   This option allow users to set the desired logging buffer size.
  #   With this option, user could have a large logging buffer and reduce the probability of log drop.
  #   It's useful for a feaure testing under QC.
  # Option Values:
  #   512-131072: Set the logging buffer in KB
  #   NONE: Invalidate this setting
  # Switch-ability:
  #   Non-switchable

DHL_SUPPORT = TRUE
  # Description:
  #   debug and high performance logging
  # Option Values:
  #   FALSE: Disable DHL support
  #   TRUE: Enable DHL support
  # Switch-ability:
  #   Non-switchable

DMA_EMI_TYPE_RESTRICT = NON_CACHEABLE
  # Description:
  #   Change different EMI type restrictions between chips' DMA and bus architecture.
  # Option Values:
  #   NONE: HW IP supports cacheable and non-cacheable region. No restriction.
  #   NON_CACHEABLE: HW IP only supports non-cacheable region because we put non-cacheable on bank 0 or somewhere else.
  # Switch-ability:
  #   Non-switchable

DUAL_EXT_BL = FALSE
  # Description:
  #   Used to enable the dual ext-bootloader feature
  # Option Values:
  #   FALSE: Disable dual ext-bootloader
  #   TRUE: Enable dual ext-bootloader
  # Switch-ability:
  #   Non-switchable

DUAL_LTE_DSP = FALSE
  # Description:
  #   Used to enable the dual dsp image feature
  # Option Values:
  #   FALSE: Disable dual LTE dsp image
  #   TRUE: Enable dual LTE dsp image
  # Switch-ability:
  #   Non-switchable

DUAL_PRIMARY_ROM = FALSE
  # Description:
  #   Used to enable the dual primary rom feature
  # Option Values:
  #   FALSE: Disable dual primary rom
  #   TRUE: Enable dual primary rom
  # Switch-ability:
  #   Non-switchable

DUAL_TALK_RX_GAIN_TABLE_CO_BAND_SUPPORT = FALSE
  # Description:
  #   This option is used for switching MT6166 RX gain table as dual talk co band support
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

DUMMY_SCATTER_ENABLE = FALSE
  # Description:
  #   This option allows the generation of large dummy scatter file in order to build pass for trace-full or ZIMAGE projects.
  #   
  #   (Please note that this option is only for internal use only.)
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

ECC_RETRY_ENHANCEMENT = TRUE
  # Description:
  #   If this feature option is turned on, L4C would retry ECC in other RAT when the first ECC attemp is fail.
  # Option Values:
  #   FALSE: Modem will not retry fail ECC on the other RAT
  #   TRUE: Modem will retry fail ECC on the other RAT
  # Switch-ability:
  #   Non-switchable

EDGE_CLASS_10 = FALSE
  # Description:
  #   This is for AT&T requirement that Handset can only have multislot class 10. However our default setting is multielost class 12. So we need this feature option to let customer open this feature if they want to pass AT&T cases
  # Option Values:
  #   FALSE: GPRS Multislot Class 12
  #   TRUE: GPRS Multislot Class 10
  # Switch-ability:
  #   Non-switchable

ETSTM_EN = FALSE
  # Description:
  #   Enable LTE test mode (Factory mode).
  #   Because we don't merge them in the same load yet, so need this option to change load mode.
  #   This feature can be removed when boot flow is ready.
  #   Currently test mode and normal mode are 2 different images
  # Option Values:
  #   FALSE: The load is normal mode
  #   TRUE: The load is test mode(meta mode or factory mode)
  # Switch-ability:
  #   Non-switchable

EXT_MODEM_SUPPORT = FALSE
  # Description:
  #   If this option is enabled, the phone can connect to the internet via the other modem (ex. CDMA or PHS or GSM)
  # Option Values:
  #   FALSE: Disable external modem support
  #   TRUE: Enable external modem support
  # Switch-ability:
  #   Non-switchable

FDD_EDCH_PHYSICAL_CATEGORY = 7
  # Description:
  #   Define the HSUPA category in makefile. However, the actual category UE uses is the minimum of this one and the one queried from HW
  # Option Values:
  #   1: EDCH category = 1
  #   2: EDCH category = 2
  #   3: EDCH category = 3
  #   4: EDCH category = 4
  #   5: EDCH category = 5
  #   6: EDCH category = 6
  # Switch-ability:
  #   Non-switchable

FDD_HSDSCH_PHYSICAL_CATEGORY = 24
  # Description:
  #   Define the HSDPA category in makefile. However, the actual category UE uses is the minimum of this one and the one queried from HW
  # Option Values:
  #   1: HSDSCH category = 1
  #   10: HSDSCH category = 10
  #   11: HSDSCH category = 11
  #   12: HSDSCH category = 12
  #   2: HSDSCH category = 2
  #   3: HSDSCH category = 3
  #   4: HSDSCH category = 4
  #   5: HSDSCH category = 5
  #   6: HSDSCH category = 6
  #   7: HSDSCH category = 7
  #   8: HSDSCH category = 8
  #   9: HSDSCH category = 9
  # Switch-ability:
  #   Non-switchable

FLASH_DRV_IN_BL = NONE
  # Description:
  #   Define the flash driver support in bootloader.
  # Option Values:
  #   EMMC: EMMC flash booting
  #   NAND: NAND flash booting
  #   NONE: System is not bootup by modem's bootloader.
  #   NOR: NOR flash booting
  #   SF: Serial flash booting
  # Switch-ability:
  #   Non-switchable

GDB_STUB_SUPPORT = FALSE
  # Description:
  #   To Enable or Disable GDB_STUB_SUPPORT
  # Option Values:
  #   FALSE: To disable GDB_STUB_SUPPORT, which means GDB STUB codes will not be built
  #   TRUE: To Enable GDB_STUB_SUPPORT, which means you can use GDB to debug MOLY
  # Switch-ability:
  #   Non-switchable

GEMINI_VERSION = V2
  # Description:
  #   This option is for Gemini version control.
  #   V1: Gemini1.0
  #   V2: Gemini2.0 (Only for dual SIM)
  # Option Values:
  #   V1: Gemini 1.0
  #   V2: Gemini 2.0
  # Switch-ability:
  #   Non-switchable

GPRS_CLASS_10 = FALSE
  # Description:
  #   This is for AT&T requirement that Handset can only have multislot class 10. However our default setting is multielost class 12. So we need this feature option to let customer open this feature if they want to pass AT&T cases
  # Option Values:
  #   FALSE: GPRS Multislot Class 12
  #   TRUE: EDGE Multislot Class 10
  # Switch-ability:
  #   Non-switchable

GPRS_DIALUP_PPP_DROP_PACKETS_WHEN_2G_PS_SUSPEND = FALSE
  # Description:
  #   This feature makes PPP process UART data even if there is no FLC buffer available (ex: In 2G CS Call suspend PS Transfer, so FLC buffer run out)
  #   In this situation, PPP start to decode the UART data into a temp buffer, and if the decoded packet is a IP pac
  # Option Values:
  #   FALSE: Disable CS suspend PS in 2G.
  #   TRUE: Enable CS suspend PS in 2G.
  # Switch-ability:
  #   Non-switchable

GPRS_DIALUP_PPP_SUPPORT_ESCAPE_ATO = FALSE
  # Description:
  #   The fature enable the switch between command mode and data mode during GPRS dialup
  #   User can switch from data mode to command mode by using ESCAPE sequence (+++)
  #   User can then switch from command mode to data mode by using ATO command
  # Option Values:
  #   FALSE: Disable support for ATO +++ cmmands.
  #   TRUE: Enable support for ATO +++ cmmands.
  # Switch-ability:
  #   Non-switchable

HIF_CCCI_SUPPORT = TRUE
  # Description:
  #   Used to enable CCCI Core related components.
  # Option Values:
  #   FALSE: CCCI core related components wiil not be built
  #   TRUE: CCCI core related components will be built.
  # Switch-ability:
  #   Non-switchable

HIF_CCIF_SUPPORT = FALSE
  # Description:
  #   For support hardware interface CCIF
  # Option Values:
  #   FALSE: Not support CCIF interface
  #   TRUE: Support CCIF interface
  # Switch-ability:
  #   Non-switchable

HIF_SDIO_SUPPORT = FALSE
  # Description:
  #   Used to enable SDIO Core related components, SDIOCORE.
  # Option Values:
  #   FALSE: SDIO core related components wiil not be built.
  #   TRUE: SDIO core related components will be built.
  # Switch-ability:
  #   Non-switchable

HIF_UART_SUPPORT = TRUE
  # Description:
  #   description:
  #   
  #   used to enable UART Core related components, UARTCORE.
  #   
  #   Option Values:
  #   
  #   TRUE: UART core related components will be built.
  #   
  #   FALSE: UARTcore related components will not be built.
  # Option Values:
  #   no: auto created - value no
  #   yes: auto created - value yes
  # Switch-ability:
  #   Non-switchable

HIF_USB30_SUPPORT = FALSE
  # Description:
  #   Enable HIF USB 3.0 feature
  # Option Values:
  #   FALSE: Disable USB 3.0 -> run in USB 2.0
  #   TRUE: Enable USB 3.0
  # Switch-ability:
  #   Non-switchable

HIF_USB_SUPPORT = FALSE
  # Description:
  #   Enable HIF USB feature
  # Option Values:
  #   FALSE: Disable HIF USB
  #   TRUE: Enable HIF USB
  # Switch-ability:
  #   Non-switchable

HIF_WCCIF_SUPPORT = TRUE
  # Description:
  #   For support hardware interface WCCIF (CCIF of WR8)
  # Option Values:
  #   FALSE: Not support WCCIF interface
  #   TRUE: Support WCCIF interface
  # Switch-ability:
  #   Non-switchable

HSUPA_CAPABILITY_NOT_SUPPORT = FALSE
  # Description:
  #   For TDD128 project, this FO determine whether UE report HSUPA Capability when HSUPA_SUPPORT = TRUE. 
  #   The main purpose of this FO is to build HSUPA modem Load without reporting HSUPA Capability for TDD128 test, i.e. UPA OFF Load.
  #   
  #   Default Value should be FALSE
  # Option Values:
  #   FALSE: FALSE, HSUPA capability will reported
  #   TRUE: TRUE, HSUPA capability will NOT reported
  # Switch-ability:
  #   Non-switchable

IC_TEST_TYPE = NONE
  # Description:
  #   N/A
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

IMS_GEN_CFG = GEN90_USER
  # Description:
  #   IMS_GEN_CFG is IMS gen config.
  # Option Values:
  #   GEN90_ENG: 90 generation eng
  #   GEN90_USER: 90 generation user
  #   GEN91_ENG: 91 generation eng
  #   GEN91_USER: 91 generation user
  #   GEN92_ENG: 92 generation eng
  #   GEN92_USER: 92 generation user
  #   GEN93_ENG: 93 generation eng
  #   GEN93_USER: 93 generation user
  #   NONE: no ims
  # Switch-ability:
  #   Non-switchable

IMS_SUPPORT = TRUE
  # Description:
  #   Description: 
  #   This is for VoLTE project
  #   Option Values:
  #   NONE: Not support for IMS feature
  #   TRUE: support for IMS feature
  #   Switch-ability:
  #   Non-switchable
  # Option Values:
  #   FALSE: Disable IMS capability
  #   TRUE: Enable IMS capability
  # Switch-ability:
  #   Non-switchable

L1D_LOOPBACK = 0
  # Description:
  #   enable WCDMA phase 3 co-sim load
  # Option Values:
  #   0: internal use
  #   2: internal use
  # Switch-ability:
  #   Non-switchable

L1_3GSOLUTION = MTK_UL1_FDD
  # Description:
  #   Add an option to select 3G L1 solution in WCDMA project
  # Option Values:
  #   AST_TL1_TDD: AST TL1 solution is selected
  #   MA_L1: Modem Art 3G L1 solution is selected
  #   MTK_TL1_TDD: MTK TL1 solution is selected
  #   MTK_UL1_FDD: MTK UL1 solution is selected
  #   NONE: Disable this feature(non-3G project)
  # Switch-ability:
  #   Non-switchable

L1_CATCHER = TRUE
  # Description:
  #   to enable L1 logging.
  # Option Values:
  #   FALSE: Disable
  #   TRUE: Enable
  # Switch-ability:
  #   Non-switchable

L1_GPS_REF_TIME_SUPPORT = TRUE
  # Description:
  #   To enable GPS-L1 reference fine time measurement
  # Option Values:
  #   FALSE: LI GPS timing synch feature is not supported
  #   TRUE: LI GPS timing synch feature is supported
  # Switch-ability:
  #   Non-switchable

LTE_MAX_EPSB_SUPPORT = 8
  # Description:
  #   LTE bearer number, MAX_EXT_PDP_CONTEXT = max(GPRS_MAX_PDP_SUPPORT, LTE_MAX_EPSB_SUPPORT)
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

LTE_TX_PATH_SWITCH_SUPPORT = FALSE
  # Description:
  #   It is for the DSDA project 
  #   LTE_TX_PATH_SWITCH_SUPPORT = TRUE # if it is a DSDA project 
  #   LTE_TX_PATH_SWITCH_SUPPORT = FALSE # if is is not a DSDA project
  # Option Values:
  #   FALSE: Set to FALSE if it is not a DSDA project
  #   TRUE: Set to TRUE if it is a DSDA project
  # Switch-ability:
  #   Non-switchable

MCD_CODESET_SHIFT_SUPPORT = 440000
  # Description:
  #   NONE: if Codeset Shift not support
  #   440000: if Codeset shift support by Docomo (Plmn ID: 440000)                                      You can enumerate all suitable support plmn
  # Option Values:
  #   440000: not support codec shift
  #   NONE: support codec shift for DOCOMO LAB test
  # Switch-ability:
  #   Non-switchable

MCU_DORMANT_MODE = TRUE
  # Description:
  #   MD MCU power off if it enters sleep mode. Need to back up some register to non-sleep memory before MD MCU enters WFI and restore these register after MD MCU leaves WFI. OSTD and system service need this feature option. First chip with this feature is MT6575E1.
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

MD_OFFLOAD_COPRO = NONE
  # Description:
  #   This feature option is to set whether there is an MD offload coprocessor or not
  # Option Values:
  #   ARM7EJ-S: Support building the image of ARM7EJ-S copro for offloading MD
  #   NONE: No offload copro
  # Switch-ability:
  #   Non-switchable

MM_RF_MODULE = MT6735_MMRF_CUSTOM
  # Description:
  #   Describe the Multi-Mode RF module used in this project for Customization
  # Option Values:
  #   NONE: Default settings if there is no need to define
  # Switch-ability:
  #   Non-switchable

MSDC_CARD_SUPPORT_TYPE = NONE
  # Description:
  #   to support various memory card type
  # Option Values:
  #   MSDC_SD_MMC: Enable SD/MMC card support
  #   NONE: Disable memory card support
  # Switch-ability:
  #   Non-switchable

MTK_AUDIO_HIERARCHICAL_PARAM_SUPPORT = FALSE
  # Description:
  #   NEW SPE architecture
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

MULTIPLE_PPP_DIALUP_SUPPORT = FALSE
  # Description:
  #   This feature support multiple(2~3) dialups on the same UART/USB
  # Option Values:
  #   FALSE: Disable multiple dialup for IOT.
  #   TRUE: Enable multiple dialup for IOT.
  # Switch-ability:
  #   Non-switchable

NVRAM_SEC_KEY_RUNTIME_GEN = TRUE
  # Description:
  #   This feature is to protect nvram secret key by runtime generate it.
  #   This feature is use while secure ro is not enabled (no matter dongle or smart phone)
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

NVRAM_SUPPORT = TRUE
  # Description:
  #   To turn on/off NVRAM module.
  # Option Values:
  #   FALSE: To disable NVRAM module
  #   TRUE: To enable NVRAM module
  # Switch-ability:
  #   Non-switchable

ORIGINAL_FLAVOR = LWG_DSDS_COTSX
  # Description:
  #   Project original flavor setting (for customer project reference only)
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

ORIGINAL_PROJECT_NAME = LENTEK_O1MP1_K37TV1_BSP
  # Description:
  #   Project original name setting (for customer project reference only)
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

PBCCH_SUPPORT = FALSE
  # Description:
  #   Indicate whether MS supports PBCCH capability
  # Option Values:
  #   FALSE: Disable PBCCH capability
  #   TRUE: Enable PBCCH capability
  # Switch-ability:
  #   Non-switchable

PLMN_LIST_PREF_SUPPORT = DEFAULT
  # Description:
  #   PLMN List EM mode setting
  # Option Values:
  #   DEFAULT: PLMN list mode can be selected except low cost, L1S project.
  #   OFF: PLMN List mode can not be selected by EM mode
  #   ON: Let PLMN List mode, accuracy or speed, can be selected by EM mode
  # Switch-ability:
  #   Non-switchable

PPP_TYPE_PDP_DIALUP_SUPPORT = FALSE
  # Description:
  #   The feature is used to create PPP tunnel instead of IP tunnel in PS transfer.
  # Option Values:
  #   FALSE: Disable PPP type PDP context.
  #   TRUE: Enable PPP type PDP context.
  # Switch-ability:
  #   Non-switchable

PRODUCTION_RELEASE = TRUE
  # Description:
  #   Specify if production released is turned on. 
  #   If it is enabled, some system code will be effected, like
  #   1. Watchdog enabled, and must be restared each time context switch;
  #   2. Handset silent reboot if system comes across with exception;
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Disable this feature
  # Switch-ability:
  #   Non-switchable

RF_DRDI_SPECIAL_MEMORY_SUPPORT = FALSE
  # Description:
  #   this feature option is for extend DRDI to support HTC request
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

RRC_PAYLOAD_FOR_3G_UP_SUPPORT = FALSE
  # Description:
  #   Depend on this option to differentiate if UE supports 3G RRC payload type in SUPL POS message or not
  # Option Values:
  #   FALSE: RRC payload can not be supported for SUPL
  #   TRUE: RRC payload can be supported for SUPL
  # Switch-ability:
  #   Non-switchable

RRLP_VER_SUPPORT = R7
  # Description:
  #   To change RRLP protocol version for both AGPS user plane and/or control plane.
  # Option Values:
  #   NONE: Doesn't support RRLP (it also means both AGPS user plane and control plane are not supported)
  #   R5: Support RRLP for 3GPP R5
  #   R7: Support RRLP for 3GPP R7
  # Switch-ability:
  #   Non-switchable

RTOS = NUCLEUS_V2
  # Description:
  #   The RTOS used in the load
  # Option Values:
  #   NUCLEUS: Use Nucles 1.13 as the OS
  #   NUCLEUS_V2: Use Nucleus 2.x as the OX
  # Switch-ability:
  #   Non-switchable

RTOS_DEBUG = TRUE
  # Description:
  #   We will support two RTOS: NUCLEUS and THREADX. The users can select one of them in their proejcts.
  # Option Values:
  #   FALSE: Disable OnlineSST OS diagnosis
  #   TRUE: Enable OnlineSST OS diagnosis
  # Switch-ability:
  #   Non-switchable

RVCT_PARTIAL_LINK = FALSE
  # Description:
  #   True:turn on partial link; False:turn off partial link
  # Option Values:
  #   FALSE: In linking process, normal link only
  #   TRUE: In linking process, try normal link firstly, if OOM, try partiallink next
  # Switch-ability:
  #   Non-switchable

RVCT_VERSION = NONE
  # Description:
  #   ARM RVCT Version
  # Option Values:
  #   V22: ARM RVCT Version 2.2
  #   V31: ARM RVCT Version 3.1
  # Switch-ability:
  #   Non-switchable

SIM_HOT_SWAP = SIM_SLOT_2
  # Description:
  #   support SIM hot swap, only MT6253D enable now
  # Option Values:
  #   NULL: not support SIM hot swap
  #   SIM_SLOT_1: single SIM project, support sim hot swap from SLOT 1
  #   SIM_SLOT_2: Dual SIM  project, support sim hot swap from SLOT 2
  #   SIM_SLOT_3: 3 SIM  project, support sim hot swap from SLOT 3
  #   SIM_SLOT_4: 4 SIM  project, support sim hot swap from SLOT 4
  # Switch-ability:
  #   Non-switchable

SP_VIDEO_CALL_SUPPORT = TRUE
  # Description:
  #   Enable Video Call Modem Capability for Smartphone project
  # Option Values:
  #   FALSE: Turn off Video Call Support for Smartphone
  #   TRUE: Turn on Video Call Support for Smartphone
  #         (conflict w/ 3G_VIDEO_CALL)
  # Switch-ability:
  #   Non-switchable

TDD_EDCH_PHYSICAL_CATEGORY = 6
  # Description:
  #   Define the HSUPA category in makefile.
  # Option Values:
  #   1: EDCH Physical category = 1
  #   2: EDCH Physical category = 2
  #   3: EDCH Physical category 3
  #   4: EDCH Physical category = 4
  #   5: EDCH Physical category = 5
  #   6: EDCH Physical category = 6
  # Switch-ability:
  #   Non-switchable

TST_DNT_LOGGING = FALSE
  # Description:
  #   Enable SIB logging (High speed logging interface)
  # Option Values:
  #   FALSE: Disable
  #   TRUE: Enable
  # Switch-ability:
  #   Non-switchable

TST_LOGACC_SUPPORT = FALSE
  # Description:
  #   to enable the LogAcc HW Support for TST.
  # Option Values:
  #   FALSE: Disable
  #   TRUE: Enable
  # Switch-ability:
  #   Non-switchable

TST_MALMO_SUPPORT = FALSE
  # Description:
  #   Support advanced logging acceleration system, Maimo.
  #   It's HW dependent and only supported on certain platforms.
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

TST_SUPPORT = FALSE
  # Description:
  #   to enable message logging and debug information tracing mechanism.
  # Option Values:
  #   FALSE: Disable
  #   TRUE: Enable
  # Switch-ability:
  #   Non-switchable

UART3_SUPPORT = FALSE
  # Description:
  #   Support UART3
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

ULCS_ASN_SUPPORT_VERSION = ULCS_ASN_SUPPORT_R99
  # Description:
  #   Depend on this option to differentiate NW IE supported vesions of 3G RRC payload type in SUPL POS message
  # Option Values:
  #   ULCS_ASN_SUPPORT_R6: The ASN codec supports R6 version LCS IEs
  #   ULCS_ASN_SUPPORT_R99: The ASN codec supports R99 version LCS IEs
  # Switch-ability:
  #   Non-switchable

USB_ACM_SUPPORT = FALSE
  # Description:
  #   Used to enable USB CDC/ACM device
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

USB_ECM_SUPPORT = FALSE
  # Description:
  #   Used to enable USB CDC/ECM device
  # Option Values:
  #   FALSE: Disable USB CDC/ECM device
  #   TRUE: Enable USB CDC/ECM device
  # Switch-ability:
  #   Non-switchable

USB_TETHERING = FALSE
  # Description:
  #   To replace ppp dialup.  However, almost no set-up is required.  User simply connects a USB cable from PC to the phone.  And then he can connect to the network.
  # Option Values:
  #   FALSE: Disable USB tethering function
  #   TRUE: Enable USB tethering function
  # Switch-ability:
  #   Non-switchable

UT_FLUSH_LOG_ON_ASSERT = FALSE
  # Description:
  #   Enable log flush on assert in MoDIS-like environment
  # Option Values:
  #   FALSE: Disable
  #   TRUE: Enable
  # Switch-ability:
  #   Non-switchable

VENDOR = NONE
  # Description:
  #   If not NONE, the project is a vendor project or is doing vendor release
  # Option Values:
  #   (other than NONE): This project is for the specified VENDOR
  #   NONE: Not a vendor project
  # Switch-ability:
  #   Non-switchable

VOHSPA_SUPPORT = FALSE
  # Description:
  #   Enable or disable Voice over HSPA
  # Option Values:
  #   FALSE: Note support VoHSPA
  #   TRUE: Support the VoHSPA
  # Switch-ability:
  #   Non-switchable

VOLTE_SUPPORT = TRUE
  # Description:
  #   Enable or disable Voice over LTE
  # Option Values:
  #   FALSE: Note support VoLTE
  #   TRUE: Support the VoLTE
  # Switch-ability:
  #   Non-switchable

WFC_SUPPORT = TRUE
  # Description:
  #   Description: 
  #   This is for WFC project. WFC is part of IMS. So WFC_SUPPORT can only effective when IMS_SUPPORT is set to TRUE also.
  #   Option Values: 
  #   NONE: Not support for IMS-WFC feature 
  #   TRUE: support for IMS-WFC feature 
  #   Switch-ability: 
  #   Non-switchable
  # Option Values:
  #   FALSE: Disable WFC capability
  #   TRUE: Enable WFC capability
  # Switch-ability:
  #   Non-switchable

X_BOOTING = NONE
  # Description:
  #   Define the device for X-booting
  # Option Values:
  #   NONE: Disable X-booting
  #   SDIO: booting from SDIO
  #   UART: booting from UART
  #   USB: booting from USB
  # Switch-ability:
  #   Non-switchable

ZIMAGE_FAVOR_ROM = FALSE
  # Description:
  #   It's an accessory feature option of ZIMAGE_SUPPORT, and is used in zImage auto configuration mechanism.
  #   If SWLA or SWTR is required while ZIMAGE_SUPPORT = TRUE, this feature should also be enabled. Otherwise SWLA and SWTR will not be able to work.
  #   When this feature is enabled, zImage will try to reserve maximum free RAM while keeping the load can be built pass at the same time. And thus the remaining free RAM can be used by SWLA and SWTR.
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

ZIMAGE_SUPPORT = FALSE
  # Description:
  #   Please refer to CR MAUI_02832638 which was applied to 10AW1041OFD_53EL_SLIM
  # Option Values:
  #   FALSE: Disable code compression mechanism
  #   TRUE: Enable code compression to utilize the free RAM space and save ROM space
  # Switch-ability:
  #   Non-switchable


# =========================================================================
# Feature Options which customer can NOT modify
# =========================================================================
2G_BPI_PT3A_SUPPORT = FALSE
  # Description:
  #   TRUE: Support the PT3A event
  #   FALSE: NOT support the PT3A event
  # Option Values:
  #   FALSE: NOT support the PT3A event
  #   TRUE: Support the PT3A event
  # Switch-ability:
  #   Non-switchable

2G_MIPI_INTERSLOT_RAMPING_OPTIMIZE_SUPPORT = TRUE
  # Description:
  #   Improve 2G MIPI interslot ramping adjustability by 2G modulation type.
  # Option Values:
  #   FALSE: Disable 2G_MIPI_INTERSLOT_RAMPING_OPTIMIZE.
  #   TRUE: Enable 2G_MIPI_INTERSLOT_RAMPING_OPTIMIZE.
  # Switch-ability:
  #   Non-switchable

2G_MIPI_SUPPORT = TRUE
  # Description:
  #   TRUE: Support 2G MIPI Control
  #   FALSE: NOT support 2G MIPI Control
  # Option Values:
  #   FALSE: NOT support 2G MIPI Control
  #   TRUE: Support 2G MIPI Control
  # Switch-ability:
  #   Non-switchable

2G_RF_CUSTOM_TOOL_SUPPORT = TRUE
  # Description:
  #   2G_RF_CUSTOM_TOOL_SUPPORT set to TRUE is to enable the feature of 2G RF custom setting modified by modem bin update tool and can modify the value by NVRAM editor
  #   For UMTS FDD dual mode projects, if user want to enable this feature,  2G_RF_CUSTOM_TOOL_SUPPORT need to set to TRUE. And customer can not modify it by himself, needs to ask for flavor build (lock build)
  #   
  #   2G_RF_CUSTOM_TOOL_SUPPORT feature options are default disabled on 11AW1112MP and 11AW1112_MOTO_ODM_SP (due to it is a patch back feature), but is default enabled on 11AMDW1119SP and 11A (thus default enabled on 11B)
  # Option Values:
  #   FALSE: Disable the feature of 2G/3G RF custom setting and band support setting modified by modem bin update tool and also can not modify the value by NVRAM editor
  #   TRUE: Enable the feature of 2G/3G RF custom setting and band support setting modified by modem bin update tool and can modify the value by NVRAM editor
  # Switch-ability:
  #   Non-switchable

2G_TX_VOLTAGE_COMPENSATION_SUPPORT = TRUE
  # Description:
  #   This feature option is to turn on/off the voltage compensation on 2G TX power
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

32K_XOSC_REMOVE = TRUE
  # Description:
  #   support 32k crystal removal
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

3G_DATA_PLANE_MEMORY_SHRINK = FALSE
  # Description:
  #   The option is to enable 3G data plane memory shrink by data buffer number reduction
  # Option Values:
  #   FALSE: Disable 3G data plane memory shrink
  #   TRUE: Enable 3G data plane memory shrink
  # Switch-ability:
  #   Non-switchable

AEC_ENABLE = TRUE
  # Description:
  #   For Acoustic Echo Cancellation (AEC)
  # Option Values:
  #   FALSE: Not Support AEC in handset mode
  #   TRUE: Support AEC in handset mode
  # Switch-ability:
  #   Non-switchable

AFC_VCXO_TYPE_2G = VCTCXO
  # Description:
  #   the 2G oscillator type used in this project
  # Option Values:
  #   VCTCXO: VCTCXO clk
  #   VCXO_2G: VCXO clk
  # Switch-ability:
  #   Switchable

AGPS_SUPPORT = CONTROL_PLANE
  # Description:
  #   Description:
  #   It decides control plane is used.
  #   Option Values:
  #   NONE: Disable AGPS feature.
  #   CP_AGPS: Enable AGPS control-plane on 2/3/4G. (only used for the modem load on smart phone project not support GLONASS)
  #   CP_AGPS_AGLONASS: Enable AGPS and AGLONASS control-plane on 2/3/4G. (only used for the modem load on smart phone project supported GLONASS)
  #   CP_AGPS_AGLONASS_ABDS: Enable AGPS , AGLONASS and ABDS control-plane on 2/3/4G. (only used for the modem load on smart phone project supported GLONASS and BDS)
  #   Switch-ability:
  #   Non-switchable
  # Option Values:
  #   CONTROL_PLANE: Enable AGPS control-plane. (only used for the modem load on smart phone project)
  #   CP_AGPS: Enable AGPS control-plane on 2/3G.
  #   CP_AGPS_AGLONASS: Enable AGPS and AGLONASS control-plane on 2/3G.
  #   NONE: Disable AGPS feature.
  # Switch-ability:
  #   Non-switchable

AMRWB_LINK_SUPPORT = TRUE
  # Description:
  #   If the compile option is true, our BB chip will support AMRWB voice call. If false, we won't support this function.
  # Option Values:
  #   FALSE: Not Support WB-AMR link
  #   TRUE: Support WB-AMR link
  # Switch-ability:
  #   Switchable

AMR_LINK_SUPPORT = TRUE
  # Description:
  #   Support AMR link, channel codec and RATSCCH procedure, with base station.
  # Option Values:
  #   FALSE: AMR disable
  #   TRUE: AMR enable
  # Switch-ability:
  #   Non-switchable

AT_COMMAND_SET = FULL
  # Description:
  #   set AT_COMMAND_SET = FULL for all projects
  #   FULL means the same setting in current existed project.s 
  #   So it will not impact any existed project.
  # Option Values:
  #   FULL: all AT commands are supported
  #   SLIM: support BT HF/DUN commands, phonesuite commands, dial up programs, and EM AT commands
  #   ULC: support BT HF commands and EM AT commands
  # Switch-ability:
  #   Non-switchable

BACKGROUND_SOUND = FALSE
  # Description:
  #   To enable the calling background sound feature.
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Switchable

BOOT_CERT_SUPPORT = FALSE
  # Description:
  #   Make hacker can not abuse download the binary and boot-up successfully
  # Option Values:
  #   BOOT_CERT_V1: BOOT_CERT_V1 make the built binary must combine with a BOOT_CERT file. Hacker can not download stolen binary into a empty phone and boot-up successfully.
  #   BOOT_CERT_V2: BOOT_CERT_V2 will cover BOOT_ECRT_V1 scope plus read back attack
  #   NONE: Disable this feature
  # Switch-ability:
  #   Non-switchable

CCBS_SUPPORT = FALSE
  # Description:
  #   Enable/Disable Completion of Calls to Busy Subscriber (CCBS) feature (TS22.093) support
  # Option Values:
  #   FALSE: Disable this feature
  # Switch-ability:
  #   Non-switchable

CCCI_CCMNI_SUPPORT = TRUE
  # Description:
  #   Used to enable CCCI_CCMNI Device,
  # Option Values:
  #   FALSE: CCCI_CCMNI Device will not be built.
  #   TRUE: CCCI_CCMI Device will be built.
  # Switch-ability:
  #   Non-switchable

CENTRALIZED_SLEEP_MANAGER = TRUE
  # Description:
  #   This is a Chip Plaform Dependent feature.
  #   Currently, only MT6276 and MT6573 support this kind of
  #   "Centralized Sleep Mode Manager Architecture".
  # Option Values:
  #   FALSE: sleep mode enable
  #   TRUE: sleep mode enable
  # Switch-ability:
  #   Non-switchable

CHIP_VERSION_CHECK = FALSE
  # Description:
  #   Enable/Disable HW ID check and supported in all projects with all current online project
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Switchable

CLIB_TIME_SUPPORT = TRUE
  # Description:
  #   Enable clib time function e.g. time(), gmtime_r(), localtime_r()...
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

CMUX_SUPPORT = TRUE
  # Description:
  #   Support 07.10 multiplexer feature: TRUE or FALSE
  # Option Values:
  #   FALSE: Disable 27.010 multiplexer
  #   TRUE: Enable 27.010 multiplexer
  # Switch-ability:
  #   Non-switchable

COMBO_MEMORY_SUPPORT = FALSE
  # Description:
  #   To support different memory part in the same load.
  # Option Values:
  #   FALSE: Disable combo memory support.
  #          Default setting is FALSE.
  #   TRUE: Enable combo memory support
  # Switch-ability:
  #   Non-switchable

CSD_SUPPORT = NONE
  # Description:
  #   for CSD support
  # Option Values:
  #   FALSE: CSD disable
  #   NONE: .
  #   NT_ONLY: .
  #   TRUE: CSD enable
  #   T_NT: .
  # Switch-ability:
  #   Non-switchable

CTA_ECC_SUPPORT = TRUE
  # Description:
  #   To support ECC 122 for CTA
  # Option Values:
  #   FALSE: Disable ECC 122 support for CTA
  #   TRUE: Enable ECC 122 support for CTA
  # Switch-ability:
  #   Switchable

CTM_SUPPORT = TRUE
  # Description:
  #   To turn on/off CTM, TTY  feature
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

CUSTOMER_SPECIFIC_FACTORY_DETECTION = NONE
  # Description:
  #   Customer specific factory mode detection mechanism
  #   Some customers may have their own factory mode definition, the mechanism can help to mapping customer specific factory mode to system mode
  # Option Values:
  #   CS_FAC_DET_TYPE_1: Use this type of factoty detection algorithm
  #   NONE: .
  # Switch-ability:
  #   Non-switchable

CUSTOM_DEBUG_MODULES = NVRAM SYS_SVC SYS_DRV CONFIG DEVDRV
  # Description:
  #   CUSTOM_DEBUG_MODULES means these modules will be built with debug information.
  # Option Values:
  #   ALL: all
  #   NONE: Disable this feature
  #   module_names: The modules' name that you want to turn on debug information, seperated by space
  # Switch-ability:
  #   Non-switchable

CUSTOM_NON_DEBUG_MODULES = NONE
  # Description:
  #   CUSTOM_NON_DEBUG_MODULES means it will be built without debug information after custom release
  # Option Values:
  #   ALL: .
  #   NONE: .
  #   module_names: Module name list using space seperated
  # Switch-ability:
  #   Switchable

DATA_CARD_SPEECH = FALSE
  # Description:
  #   Enable speech capablility via data card tool  through PC speaker and microphone
  # Option Values:
  #   FALSE: Disable Datacrad speech
  #   TRUE: Enable data card speech
  # Switch-ability:
  #   Non-switchable

DCS_TX_NOTCH_SWITCH_SUPPORT = FALSE
  # Description:
  #   This option is used for turing on DCS TX path switch for dual talk project
  # Option Values:
  #   FALSE: Not support DCS TX NOTCH SWITCH
  #   TRUE: Support DCS TX NOTCH SWITCH
  # Switch-ability:
  #   Non-switchable

DEMO_PROJECT = FALSE
  # Description:
  #   To define projects that are experimental or for demo purposes only. It should NEVER be enabled on MP projects, and it is used by MTK internally. Customer does not need to change it
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

DHL_MALMO_SUPPORT = FALSE
  # Description:
  #   Support advanced logging acceleration system, Maimo.
  #   It's HW dependent and only supported on certain platforms.
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

DHL_MONITOR_MODE_SUPPORT = TRUE
  # Description:
  #   Collect customer subscribe info to monitor channel
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

DISABLE_A5_2 = TRUE
  # Description:
  #   Disable A5/2 cipering algo
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

DMA_UART_VFIFO_TUNNEL_SUPPORT = FALSE
  # Description:
  #   enable the VFIFO support UART for separate tx or rx
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

DMA_UART_VIRTUAL_FIFO = FALSE
  # Description:
  #   Support UART Virtual FIFO
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

DRV_CUSTOM_TOOL_SUPPORT = FALSE
  # Description:
  #   To enable using driver customization tool for GPIO/EINT/ADC/UEM/Keypad/PMU setting.
  # Option Values:
  #   FALSE: Disable
  #   TRUE: Enable
  # Switch-ability:
  #   Switchable

DRV_DEBUG_MEMORY_TRACE_SUPPORT = NONE
  # Description:
  #   Driver do not want to depend on MMI_FEATURES_SWITCH because sometimes we do need to use memory trace debug on memory monitored project.
  # Option Values:
  #   FALSE: driver won't log the trace in memory
  #   TRUE: driver will log the trace in memory
  # Switch-ability:
  #   Switchable

DSPIRDBG = FALSE
  # Description:
  #   To enable IRDBG interface for dsp debugging
  # Option Values:
  #   FALSE: .
  #   TRUE: .
  # Switch-ability:
  #   Non-switchable

DSP_IMG_ON_EMI = TRUE
  # Description:
  #   DSP image is loaded by AP and placed on EMI before MD ungated
  # Option Values:
  #   FALSE: DSP is on-demand loading during MD runtime
  #   TRUE: DSP image is loaded by AP and placed on EMI before MD ungated
  # Switch-ability:
  #   Non-switchable

DSP_SOLUTION = NONE
  # Description:
  #   This option is used to configure whether DSP core is enabled by ARM. If  DSP core functionality is required, specify the suitable DSP type for this option. Otherwise, use NONE for this option
  # Option Values:
  #   DUALMACDSP: FCore related chips.
  #   NONE: .
  # Switch-ability:
  #   Non-switchable

DT_SUPPORT = FALSE
  # Description:
  #   This option is used to Turn on/off PC tool Phonesuite File manager related functions. If set FALSE, Phonesuite File manager will not available.
  # Option Values:
  #   FLASE: Turn off DT_SUPPORT, In this case PC tool "phonesuite" can use only limited functions(function like file manager is disabled)
  #   TRUE: Turn on DT_SUPPORT, So MediaTek PC tool "Phonesutie" file manager related functions can be used
  # Switch-ability:
  #   Non-switchable

DUALTALK = FALSE
  # Description:
  #   DUALTALK = FALSE
  #   Description:
  #   Dual talk modem definition 
  #   Option Values:
  #   TRUE: Dual talk
  #   FALSE: Single talk
  #   Switch-ability:
  #   Switchable in dual talk modem chip. ex. MT6589
  # Option Values:
  #   FALSE: Single talk
  #   TRUE: Dual talk
  # Switch-ability:
  #   Non-switchable

DUAL_MODEM_SUPPORT = FALSE
  # Description:
  #   This option is used for support dual modem.
  # Option Values:
  #   FALSE: Not support dual modem
  #   TRUE: Support dual modem
  # Switch-ability:
  #   Switchable

DUAL_TALK_RFIC2_SUPPORT = FALSE
  # Description:
  #   This option is used for dual talk 2nd RF
  # Option Values:
  #   FALSE: Support CSFB
  #   TRUE: Support dual talk
  # Switch-ability:
  #   Non-switchable

ECALL_SUPPORT = FALSE
  # Description:
  #   Enable/disable eCall support
  # Option Values:
  #   FALSE: FALSE: eCall disable
  #   TRUE: TRUE : eCall enable
  # Switch-ability:
  #   Non-switchable

EDGE_SUPPORT = TRUE
  # Description:
  #   Enable EDGE support Only applicable to 6229 latter version
  # Option Values:
  #   FALSE: EGPRS enable
  #   TRUE: EGPRS enable
  # Switch-ability:
  #   Non-switchable

EMMC_BOOTING = NONE
  # Description:
  #   eMMC booting (similar to NAND_FLASH_BOOTING)
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Switchable

ENHANCED_SINGLE_BANK_NOR_FLASH_SUPPORT = FALSE
  # Description:
  #   to enable enhanced single bank NOR flash support.
  # Option Values:
  #   FALSE: Disable Single Bank support
  #   TRUE: Enable Single Bank support
  # Switch-ability:
  #   Non-switchable

EXT_BL_UPDATE_SUPPORT = FALSE
  # Description:
  #   This option is to control if we can use USBDL in Bootloader to update ExtBootloader.
  #   If this option is TRUE, bootloader will take more space as backup to make sure all download process will be done in a safe way
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Switchable

EXT_CLOCK = EXT_26M
  # Description:
  #   Specify external clock rate.
  # Option Values:
  #   EXT_13M: .
  #   EXT_26M: .
  # Switch-ability:
  #   Non-switchable

FAST_DORMANCY_SUPPORT = TRUE
  # Description:
  #   This feature option is used to enable/disable 3G R8 Fast Dormancy feature.
  # Option Values:
  #   FALSE: Support Legacy FD
  #   TRUE: Support both R8 & Legacy FD
  #   not define the feature option: modem will not support R8 nor Legacy FD
  # Switch-ability:
  #   Switchable

FAX_SUPPORT = FALSE
  # Description:
  #   Support FAX or not, the value should be:
  #   TRUE, FALSE
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

FEATURE_OVERLOAD = FALSE
  # Description:
  #   Enabling this option will allow a project configured with more features than its physical constraints. This option just bypass some building error checkings and generate the .bin. However, the .bin is still can NOT work in target if the physical constrain
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Switchable

FORCE_MEMORY_DUMP = FALSE
  # Description:
  #   force memory dump in production release to prevent from silent reboot..
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Switchable

FS_DEDICATED_BUFFER = FALSE
  # Description:
  #   If this feature is enabled, FS will allocate a dedicated buffer to enhance file system performance. The improved operations are file copy, file move (to differenct drive), disk mount, disk format and memory card hot-plug-in.
  # Option Values:
  #   FALSE: Disable FS Dedicated Buffer
  #   TRUE: Enable FS Dedicated Buffer
  # Switch-ability:
  #   Switchable

FS_OPEN_HINT_SUPPORT = FALSE
  # Description:
  #   FS will try to preserve several recently found locations of files or folders internally. If FS user wants to open those files or folders again later, the opening time could be minimized.
  # Option Values:
  #   FALSE: Disable FS Internal Open Hint
  #   TRUE: Enable FS Internal Open Hint
  # Switch-ability:
  #   Switchable

FS_RAMDISK = FALSE
  # Description:
  #   Enable RAM disk to replace NOR flash disk.
  #   NOTE. To enable FS_RAMDISK, please configure this project as file system on NOR flash.
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: RAM Disk will replace flash disk (e.g., NOR, NAND, eMMC...etc) on file system. This feature is used for SP modem-only project only.
  # Switch-ability:
  #   Non-switchable

FS_TRACE_SUPPORT = FALSE
  # Description:
  #   File System will provide run-time trace information with proper configure. The trace information is very helpful to identify problem source.
  # Option Values:
  #   FALSE: To disable FS debug messange
  #   TRUE: To enable FS debug messange
  # Switch-ability:
  #   Switchable

GCC_WARN_AS_ERROR = FALSE
  # Description:
  #   This option will regard build warnings as errors for GCC.
  # Option Values:
  #   FALSE: Not support warnings as errors
  #   TRUE: Support warnings as errors
  # Switch-ability:
  #   Non-switchable

GEMINI = 2
  # Description:
  #   To identify if this project uses a &quot;Single chip-dual SIM&quot; solution.
  # Option Values:
  #   2: Enable 2 SIMs, single talk.
  #   3: Enable 3 SIMs, single talk.
  #   4: Enable 4 SIMs, single talk.
  #   FALSE: Disable this feature
  # Switch-ability:
  #   Non-switchable

GERAN_RELEASE_SUPPORT = GERAN_R9_SUPPORT
  # Description:
  #   The option is to switch different version of release for 2G modem
  # Option Values:
  #   GERAN_R4_SUPPORT: set the 2G MODEM to 3GPP R4 baseline
  #   GERAN_R5_SUPPORT: set the 2G MODEM to 3GPP R5 baseline
  #   GERAN_R6_SUPPORT: set the 2G MODEM to 3GPP R6 baseline
  #   GERAN_R99_SUPPORT: set the 2G MODEM to 3GPP R99 baseline
  # Switch-ability:
  #   Non-switchable

GPRS_MAX_PDP_SUPPORT = 6
  # Description:
  #   Maximum simultaneous PDP contexts (2~7)
  # Option Values:
  #   1: Maximum simultaneous PDP contexts is one
  #   2: Maximum simultaneous PDP contexts is two
  #   3: Maximum simultaneous PDP contexts is three
  #   4: Maximum simultaneous PDP contexts is four
  #   5: Maximum simultaneous PDP contexts is five
  #   6: Maximum simultaneous PDP contexts is six
  #   7: Maximum simultaneous PDP contexts is seven
  # Switch-ability:
  #   Non-switchable

GPS_SUPPORT = SMART_PHONE_MODEM
  # Description:
  #   Mapping with SMART PHONE MODEM      # NONE, SMART_PHONE_MODEM
  # Option Values:
  #   NONE: disable release GPS for smart phone modem
  #   SMART_PHONE_MODEM: enable release GPS for smart phone modem
  # Switch-ability:
  #   Non-switchable

HIF_AOMGR_SUPPORT = FALSE
  # Description:
  #   this feature is used to offload task to AP side or not.
  # Option Values:
  #   FALSE: Disable module AOMGR
  #   TRUE: Enable module "AOMGR"
  # Switch-ability:
  #   Non-switchable

HIF_CLDMA_SUPPORT = TRUE
  # Description:
  #   Used to enable CLDMA Core related components, CLDMACORE.
  # Option Values:
  #   FALSE: CLDMA core related components wiil not be built.
  #   TRUE: CLDMA core related components will be built.
  # Switch-ability:
  #   Non-switchable

HW_PFC_SUPPORT = FALSE
  # Description:
  #   PPP Framer use HW or SW
  # Option Values:
  #   FALSE: use SW PFC
  #   TRUE: use HW PFC
  # Switch-ability:
  #   Non-switchable

HW_VER = LENTEK_O1MP1_K37TV1_BSP_HW

ICUSB_SUPPORT = TRUE
  # Description:
  #   ICUSB feature is to support SIM+ card with CCID/MSDC/EEM class ability.
  #   ICUSB_SUPPORT = TRUE; enable ICUSB feature support
  #   ICUSB_SUPPORT = FALSE; disable ICUSB feature support
  # Option Values:
  #   FALSE: disable ICUSB feature support
  #   TRUE: enable ICUSB feature support
  # Switch-ability:
  #   Non-switchable

IPV6_PDP_SUPPORT = TRUE
  # Description:
  #   Description:
  #   IPv6 PDP Context Support
  #   Option Values:
  #   TRUE: support IPv6 PDP Context, support IPv4v6(3GPP Rel8) PDP Context
  #   FALSE: not support IPv6 PDP Context
  #   Switch-ability:
  #   N/A
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

KAL_ASSERT_LEVEL = KAL_ASSERT_LEVEL3
  # Description:
  #   Setup KAL assert level
  #   3: for full debugging facility support
  #   0: for minimal debugging facility support
  # Option Values:
  #   KAL_ASSERT_LEVEL0: .
  #   KAL_ASSERT_LEVEL1: .
  #   KAL_ASSERT_LEVEL2: .
  #   KAL_ASSERT_LEVEL3: .
  #   KAL_ASSERT_LEVEL4: save entire CPU registers
  # Switch-ability:
  #   Non-switchable

KAL_DEBUG_LEVEL = NORMAL_DEBUG_KAL
  # Description:
  #   Control the major KAL debugging facility availability and behavior. More debugging facility consume more extra RAM footprint.
  #   
  #   Less debugging facility may suffer extra effort to locate root cause of issue.
  #   
  #   RICH_DEBUG_KAL > NORMAL_DEBUG_KAL > SLIM_DEBUG_KAL > RELEASE_KAL.
  #   
  #   Default Setting :  NORMAL_DEBUG_KAL
  # Option Values:
  #   NORMAL_DEBUG_KAL: .
  #   RELEASE_KAL: .
  #   RICH_DEBUG_KAL: .
  #   SLIM_DEBUG_KAL: .
  # Switch-ability:
  #   Non-switchable

KAL_RECORD_BOOTUP_LOG = FALSE
  # Description:
  #   The goal is to print out log via Catcher at the initialization stage. We supply the API for customers and print out each initialization milistone by default. With this facility, customers who do not have Trace32 can easily locat where the problem is via UART at MAUI initialization stage.
  # Option Values:
  #   FALSE: disable boot up trace
  #   TRUE: enable boot up trace
  # Switch-ability:
  #   Switchable

KAL_TRACE_OUTPUT = FULL
  # Description:
  #   This option is used to enable or disable the trace output for the concern of saving ROM size
  # Option Values:
  #   CUST_PARTIAL: Enable partial traces
  #   FULL: Enable all trace
  #   NONE: Disable all trace
  # Switch-ability:
  #   Switchable

L1_EPSK_TX = TRUE
  # Description:
  #   Turn on/off this compile option may switch the function of EPSK modulation transmitter
  # Option Values:
  #   FALSE: EPSK TX disable
  #   TRUE: EPSK TX enable
  # Switch-ability:
  #   Non-switchable

L1_GPS_CO_CLOCK_SUPPORT = TRUE
  # Description:
  #   Depend on this option to differentiate if Fixed AFC is supported.
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

L1_GPS_FREQ_AIDING_SUPPORT = TRUE
  # Description:
  #   Depend on this option to differentiate if MD provides Freq. Aiding support to GPS
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

L1_LOCK_AFCDAC_AT_STARTUP_SUPPORT = TRUE
  # Description:
  #   Depend on this option to set AFC DAC is locked or unlocked at start-up, and this status can also be modified through NVRAM after 1st booting.
  # Option Values:
  #   FALSE: UnLock AFC DAC at start-up
  #   TRUE: Lock AFC DAC at start-up
  # Switch-ability:
  #   Non-switchable

LOW_COST_SUPPORT = NONE
  # Description:
  #   General compile option for Low Cost features. Please not that a module can define its own low-cost levels, or it can just adopt
  # Option Values:
  #   COMMON: .
  #   FALSE: .
  #   NONE: .
  #   TRUE: .
  #   ULC: .
  # Switch-ability:
  #   Non-switchable

LPP_SUPPORT = CONTROL_PLANE
  # Description:
  #   Determine if LPP protocol is supported.
  #   Note: LPP_SUPPORT can be turned on only if EUTRAN_MODE_SUPPORT and at least one of AGPS_SUPPORT/LTE_OTDOA_SUPPORT/LTE_ECID_SUPPORT is defined.
  # Option Values:
  #   CONTROL_PLANE: Enable LPP protocol control plane
  #   NONE: Disable LPP feature
  # Switch-ability:
  #   Non-switchable

LTE_BPI_TIMING_DRDI_SUPPORT = FALSE
  # Description:
  #   It is for the BPI Timing DRDI 
  #   LTE_BPI_TIMING_DRDI_SUPPORT= TRUE  # if it Support BPI Timing DRDI 
  #   LTE_BPI_TIMING_DRDI_SUPPORT = FALSE # if it doesn't Support BPI Timing DRDI
  # Option Values:
  #   FALSE: NOT SUPPORT BPI TIMING DRDI
  #   TRUE: SUPPORT BPI TIMING DRDI
  # Switch-ability:
  #   Non-switchable

LTE_DSP_FLAVOR = MP
  # Description:
  #   LTE_DSP_PROJECT is LTE DSP project name.
  #   LTE_DSP_FLAVOR is LTE DSP flavor name.
  #   
  #   It can be used to extace LTE DSP bin files from LTE DSP bin packaged file
  #   "LTE_DSP_BIN_PACKAGE_%LTE_DSP_PROJECT%_%LTE_DSP_FLAVOR%.zip"
  #   in the path of /mcu/modem/dsp_bin/
  #   
  #   Ex: 
  #   LTE_DSP_PROJECT = MT6290E1
  #   LTE_DSP_FLAVOR = NORFC
  #   LTE DSP bin packaged file is "LTE_DSP_BIN_PACKAGE_MT6290E1_NORFC.zip"
  # Option Values:
  #   NORFC: Module RFC is OFF to save PM size
  #   RFC: Module RFC is ON
  # Switch-ability:
  #   Non-switchable

LTE_DSP_PROJECT = MT6735
  # Description:
  #   LTE_DSP_PROJECT is LTE DSP project name.
  #   LTE_DSP_FLAVOR is LTE DSP flavor name.
  #   
  #   It can be used to extace LTE DSP bin files from LTE DSP bin packaged file
  #   "LTE_DSP_BIN_PACKAGE_%LTE_DSP_PROJECT%_%LTE_DSP_FLAVOR%.zip"
  #   in the path of /mcu/modem/dsp_bin/
  #   
  #   Ex: 
  #   LTE_DSP_PROJECT = MT6290E1
  #   LTE_DSP_FLAVOR = NORFC
  #   LTE DSP bin packaged file is "LTE_DSP_BIN_PACKAGE_MT6290E1_NORFC.zip"
  # Option Values:
  #   MT6290E1: For chip MT6290E1
  # Switch-ability:
  #   Non-switchable

LTE_ECID_SUPPORT = TRUE
  # Description:
  #   Determine if LTE ECID positioning is supported.
  #   Note: LTE_ECID_SUPPORT can be turned on only if EUTRAN_MODE_SUPPORT and LPP_SUPPORT is defined.
  # Option Values:
  #   FALSE: Disable LTE ECID positioning feature
  #   TRUE: Enable LTE ECID positioning feature
  # Switch-ability:
  #   Non-switchable

MAX_NUM_OF_NDIS_SUPPORT = 3
  # Description:
  #   support multiple(2~3) NDIS channels
  # Option Values:
  #   2: Configure the maximal number of NDIS channels
  #   3: Configure the maximal number of NDIS channels
  # Switch-ability:
  #   Non-switchable

MCD_SUPPORT = TRUE
  # Description:
  #   to enable MediaTek Message Coder Decoder feature for GSM/GPRS/EDGE project
  # Option Values:
  #   FALSE: Disable MCD
  #   TRUE: Enable MCD (default)
  # Switch-ability:
  #   Non-switchable

MCU_CLOCK = MCU_572M
  # Description:
  #   Specify MCU clock rate,
  #   MT6205B = 26MHz (MCU_26M)
  #   MT6218B/19, MT6226/7, MT6223 = 52MHz (MCU_52M)
  #   MT6228, MT6229, MT6230, MT6225 = 104MHz (MCU_104M)
  #   MT6235, MT6238, MT6239 = 208MHz (MCU_208M)
  # Option Values:
  #   MCU_104M: CPU normal operation frequency: 104MHz
  #   MCU_122_88M: CPU normal operation frequency: 122.88MHz
  #   MCU_13M: CPU normal operation frequency:13MHz
  #   MCU_208M: CPU normal operation frequency: 208MHz
  #   MCU_245_76M: CPU normal operation frequency:245.76MHz
  #   MCU_26M: CPU normal operation frequency:26MHz
  #   MCU_312M: CPU normal operation frequency: 312MHz
  #   MCU_39M: CPU normal operation frequency:39MHz
  #   MCU_416M: CPU normal operation frequency: 416Mhz
  #   MCU_520M: CPU normal operation frequency: 520MHz
  #   MCU_52M: CPU normal operation frequency:52MHz
  #   MCU_650M: CPU normal operation frequency: 650MHz
  # Switch-ability:
  #   Non-switchable

MCU_DCM = DCM_ENABLE
  # Description:
  #   to enable Dynamic Clock Switching (MCU_DCM) function for saving the current consumption. Please set DCM_ENABLE to turn on this feature. (DCM_ENABLE <-> DCM_DISABLE) DCM_ENABLE: Need correct EMI configurations.
  # Option Values:
  #   DCM_DISABLE: Disable DCM support
  #   DCM_ENABLE: Enable DCM support
  # Switch-ability:
  #   Non-switchable

MDSYS = MD1
  # Description:
  #   Dual talk modem definition
  # Option Values:
  #   MD1: WCDMA/EDGE modem
  #   MD2: TDSCDMA/EDGE modem
  #   NONE: Not dual talk modem
  # Switch-ability:
  #   Non-switchable

MOBILE_BROADBAND = NONE
  # Description:
  #   Mobile boradband support for Network Driver Interface Specification
  #   e.g. Windows 8 , Windows 7
  # Option Values:
  #   MBIM: Enable Mobile broadband with MBIM protocol
  #   NONE: Disable Mobile broadband
  # Switch-ability:
  #   Non-switchable

MODEMRESERVEDSIZE_AUTOCONFIG = TRUE
  # Description:
  #   To define if image layout is able to be auto-adjusted
  # Option Values:
  #   FALSE: image layout is not able to be adjusted
  #   TRUE: image layout is able to be adjusted
  # Switch-ability:
  #   Non-switchable

MODEM_3G_LOGGING = FALSE
  # Description:
  #   Enable 3G modem logging feature
  # Option Values:
  #   FALSE: internal use
  #   TRUE: internal use
  # Switch-ability:
  #   Non-switchable

MODEM_CARD = NONE
  # Description:
  #   Target is data card module.
  # Option Values:
  #   CS_CALL_DISABLE: Data card project, has ability to disable CS call
  #   FULL: Data card project
  #   NONE: Disable this feature
  # Switch-ability:
  #   Non-switchable

MODEM_NFC_SUPPORT = TRUE
  # Description:
  #   To support NFC application on smart phone, modem need to support AT+CGLA/+CCHO/+CCHC for SIM access.
  # Option Values:
  #   FALSE: Disable modem support for smart phone NFC application
  #   TRUE: Enable modem support for smart phone NFC application
  # Switch-ability:
  #   Non-switchable

MODIS_FDM = OFF
  # Description:
  #   To enable On-time File System (mcu\fs\fat) on MoDIS.
  # Option Values:
  #   OFF: To selet MTK propietary FS lib on MoDIS
  #   ON: To select On-time FS lib on MoDIS
  # Switch-ability:
  #   Switchable

MTK_BT_CHIP = MTK_CONSYS_MT6735
  # Description:
  #   MTK_BT_CHIP
  # Option Values:
  #   MTK_CONSYS_MT6571: Need to support software BT codec (CVSD / mSBC).
  #   MTK_CONSYS_MT6572: Need to support software BT codec (CVSD / mSBC).
  #   MTK_CONSYS_MT6582: Need to support software BT codec (CVSD / mSBC).
  #   MTK_CONSYS_MT6592: Need to support software BT codec (CVSD / mSBC).
  #   MTK_CONSYS_MT6752: Need to support software BT codec (CVSD / mSBC).
  #   MTK_MT6620: Need to support BT phone call PCM interface.
  #   MTK_MT6622: Need to support BT phone call PCM interface.
  #   MTK_MT6626: Need to support BT phone call PCM interface.
  #   MTK_MT6628: Need to support BT phone call PCM interface.
  #   MTK_MT6630: Need to support BT phone call PCM interface.
  #   NONE: No BT phone call.
  # Switch-ability:
  #   Switchable

MTK_SLEEP_ENABLE = TRUE
  # Description:
  #   Sleep mode enable or not
  # Option Values:
  #   FALSE: sleep mode disable
  #   TRUE: sleep mode enable
  # Switch-ability:
  #   Non-switchable

MULTIPLE_NDIS_SUPPORT = FALSE
  # Description:
  #   support multiple(2~3) NDIS channels
  # Option Values:
  #   FALSE: Single NDIS channel is allowed
  #   TRUE: Support multiple NDIS channels
  # Switch-ability:
  #   Non-switchable

MULTIPLE_TBF = FALSE
  # Description:
  #   REL6 Multiple TBF
  # Option Values:
  #   FALSE: Not support multiple TBF
  #   TRUE: Support multiple TBF
  # Switch-ability:
  #   Non-switchable

NAND_FLASH_BOOTING = NONE
  # Description:
  #   Specify if system has to boot from nand-flash.
  #   If yes, bootloader will be built and MAUI will be splitted into
  #   primary and secondary MAUI.
  # Option Values:
  #   BASIC: Only Primary MAUI and Secondary MAUI exist
  #   NONE: Disable this feature
  #   ONDEMAND: Use demand paging mechanism
  # Switch-ability:
  #   Non-switchable

NAND_SUPPORT = FALSE
  # Description:
  #   To enable NAND flash support, please set NAND_SUPPORT as TRUE.
  # Option Values:
  #   no: auto created - value no
  #   yes: auto created - value yes
  # Switch-ability:
  #   Non-switchable

NAND_SUPPORT_RAW_DISK = FALSE
  # Description:
  #   NAND Disk can be access without file system format
  # Option Values:
  #   FALSE: Disable NAND RAW disk support. Normal project should be FALSE.
  #   TRUE: Enable NAND RAW disk support
  # Switch-ability:
  #   Non-switchable

NDIS_SUPPORT = NONE
  # Description:
  #   to enable NDIS support in modem side
  # Option Values:
  #   NDIS: Enable NDIS over CMUX
  #   NONE: Disable NDIS_SUPPORT
  #   UPS: Enable NDIS over UPS
  # Switch-ability:
  #   Non-switchable

NOR_SUPPORT_RAW_DISK = FALSE
  # Description:
  #   Purpose 
  #   (1) Support direct access API for NOR flash.
  #   (2) Support at most 2 additional raw disk regions on NOR flash.
  #   (3) Support flexible settings of disk base address and length. Only need to have disk range aligned with block size.
  #   Usage 
  #   (1) Set to TRUE.
  #   (2) Should modify custom_memorydevice.h NOR_BOOTING_NOR_DISK_NUM & NOR_BOOTING_NOR_DISKX_BASE_ADDRESS & NOR_BOOTING_NOR_DISKX_SIZE  to assign disk region.
  #   Limitation  
  #   (1) In V32/V33 project, NOR_BOOTING_NOR_DISK_NUM should be 2 and the second disk is treated as Security Partition.
  # Option Values:
  #   FALSE: Disable NOR RAW disk support
  #   TRUE: Enable NOR RAW disk support
  # Switch-ability:
  #   Non-switchable

NVRAM_PSEUDO_MERGE = OFF
  # Description:
  #   NVRAM will combine small files and this will transparent to applications.
  # Option Values:
  #   OFF: To disable NVRAM pseudo merge
  #   ON: To enable NVRAM pseudo merge
  # Switch-ability:
  #   Switchable

OPTIMIZED_GEN90_2G_APC_CONTROL_FLOW_SUPPORT = FALSE
  # Description:
  #   Optimized Generation 90 APC control flow
  # Option Values:
  #   FALSE: Turn off optimization flow
  #   TUE: Turn on optimization flow
  # Switch-ability:
  #   Non-switchable

OPTR_SPEC_MODEM = NONE
  # Description:
  #   This feature option ONLY used for operator modem customized project, which means only turn on some compile options based specific operator's requirement, BUT NO operator specific MMI code involve.
  # Option Values:
  #   ATT_0402_ADAPT: AT&T modem specifc only project
  #   H3G: Hutchison modem specific only project
  #   NONE: Disable this feature
  # Switch-ability:
  #   Non-switchable

ORIGINAL_VERNO = MOLY.LR9.W1444.MD.LWTG.MP.V180

OS_TICK_PERIOD_IN_US = 5000
  # Description:
  #   Set the OS tick period in micro second
  # Option Values:
  #   4615: For backward compatible, not suggested
  #   5000: Default OS Tick Period
  # Switch-ability:
  #   Non-switchable

PACKAGE_SEG = NONE
  # Description:
  #   To identify the package by MCP combination that MTK prepared for difference chipsets. If XX_YY_SEG be configured, then __XX_YY_SEG__ compile option defined for module owners to wrap the code for specific configuration or design for this package.
  # Option Values:
  #   128_32_SEG: for 128+32 segment
  #   16_32_SEG: for 16+32 segment
  #   16_3_SEG: for 16+3 segment
  #   1G_512_SEG: for 1G+512 segment
  #   2G_1G_SEG: for 2G+1G segment
  #   32_32_SEG: for 32+32 segment
  #   32_3_SEG: for 32+3 segment
  #   512_256_SEG: for 512+256 segment
  #   64_32_SEG: for 64+32 segment
  # Switch-ability:
  #   Non-switchable

PARTIAL_TRACE_LIB = NONE
  # Description:
  #   When module was defined in PARTIAL_TRACE_LIB, the compile option, TRACE_MODULE_OFF, will be un-defined, and it will be buit with TRACE information.
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

PHB_ADDITIONAL_SUPPORT = TRUE
  # Description:
  #   USIM Phonebook Support
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Switchable

PHB_FDN_ENTRY = 50
  # Description:
  #   Maximum fixed dial number in SIM card, its value must <=32
  # Option Values:
  #   NA: The value can be 1 to 32, must <=32
  # Switch-ability:
  #   Switchable

PHB_LN_ENTRY = 20
  # Description:
  #   Number of the call log with single log type from single SIM card, it &apos;s a range better no more than 100, suggestion to be 10, 20, 30, 40, 50, 60, 80, 100 and so on. Totol log num = PHB_LN_ENTRY * log type num * sim card num.
  # Option Values:
  #   10: Number of call log with single log type from single SIM card, ex, The dialed call log from SIM1 is 10, total log num = 10 * log type num * sim card num.
  #   100: Number of call log with single log type from single SIM card, ex, The dialed call log from SIM1 is 100, total log num = 100 * log type num * sim card num.
  #   20: Number of call log with single log type from single SIM card, ex, The dialed call log from SIM1 is 20, total log num = 20 * log type num * sim card num.
  #   30: Number of call log with single log type from single SIM card, ex, The dialed call log from SIM1 is 30, total log num = 30 * log type num * sim card num.
  #   40: Number of call log with single log type from single SIM card, ex, The dialed call log from SIM1 is 40, total log num = 40 * log type num * sim card num.
  #   50: Number of call log with single log type from single SIM card, ex, The dialed call log from SIM1 is 50, total log num = 50 * log type num * sim card num.
  #   60: Number of call log with single log type from single SIM card, ex, The dialed call log from SIM1 is 60, total log num = 60 * log type num * sim card num.
  #   80: Number of call log with single log type from single SIM card, ex, The dialed call log from SIM1 is 80, total log num = 80 * log type num * sim card num.
  # Switch-ability:
  #   Non-switchable

PHB_PHONE_ENTRY = 10
  # Description:
  #   Maximum Phonebook entry in Phone,  #Switch-ability: [Any] -> [Any]
  # Option Values:
  #   NA: .
  # Switch-ability:
  #   Switchable

PHB_SIM_ENTRY = 1000
  # Description:
  #   Maximum Phonebook entry in SIM card.
  # Option Values:
  #   NA: .
  # Switch-ability:
  #   Switchable

PHB_SYNC = ON
  # Description:
  #   To enable/disable phonebook sync feature.
  # Option Values:
  #   OFF: Not support Sync from Phonebook
  #   ON: Support Sync from Phonebook
  # Switch-ability:
  #   Switchable

PLATFORM_FOR_CHECK = NONE
  # Description:
  #   Name of BB-chip for tool check.
  # Option Values:
  #   NA: MediaTek setup this name for tool check.
  #   NONE: Disable this feature.
  # Switch-ability:
  #   Non-switchable

PMIC = MT6328
  # Description:
  #   To specify which Power Management IC(PMIC) is used, or no PMIC.
  # Option Values:
  #   MT6223PMU: .
  #   MT6235PMU: .
  #   MT6236PMU: .
  #   MT6238PMU: .
  #   MT6253ELPMU: .
  #   MT6253PMU: .
  #   MT6255PMU: For MT6255 Usage
  #   MT6258PMU: .
  #   MT6276PMU: .
  #   MT6305: .
  #   MT6318: .
  #   MT6326: .
  #   MT6326_CCCI: .
  #   MT6573PMU: .
  #   NONE: No PMU/PMIC on PCB
  # Switch-ability:
  #   Non-switchable

PMIC_INIT_TYPE = PMIC_INIT_NONE
  # Description:
  #   PMIC initial setting for different product type.
  # Option Values:
  #   PMIC_INIT_ALL_ON_EVB: PMIC initial setting for power all enabled on EVB.
  #   PMIC_INIT_DONGLE: PMIC initial setting for dongle.
  #   PMIC_INIT_DONGLE_ON_EVB: PMIC initial setting for dongle on EVB.
  #   PMIC_INIT_NONE: No need apply any PMIC initial setting.
  #   PMIC_INIT_PHONE: PMIC initial setting for phone.
  #   PMIC_INIT_PHONE_ON_EVB: PMIC initial setting for phone on EVB.
  #   PMIC_INIT_ROUTER: PMIC initial setting for router(MIFI).
  #   PMIC_INIT_ROUTER_ON_EVB: PMIC initial setting for router(MIFI) on EVB.
  # Switch-ability:
  #   Switchable

PPP_SUPPORT = NONE
  # Description:
  #   The option is used to define ppp module's function. We can turn off ppp, or set PPP module's function as csd ppp support or gprs ppp support used FP as modem for dialup
  # Option Values:
  #   CSD_PPP_SUPPORT: PPP used for supporting tcpip over csd option
  #   FULL_SUPPORT: PPP used for supporting these two functions
  #   GPRS_PPP_SUPPORT: PPP used for supporting dialup as a modem for PC
  #   NONE: Turn off PPP
  # Switch-ability:
  #   Non-switchable

PS_HANDOVER = FALSE
  # Description:
  #   REL6 PS handover
  # Option Values:
  #   FALSE: Not support PS handover
  #   TRUE: Support PS handover
  # Switch-ability:
  #   Non-switchable

R7R8_FULL_SET_SUPPORT = FALSE
  # Description:
  #   Description:
  #   This feature option is to turn ON some rare R7R8 capability indication, such as MACIIS, DRXInFACH, EnhancedCommonState, COMMON_EDCH, HSDSCH_CELL_CHNAGE_ENHANCED, CSoHS, UEA2_UIA2
  #   Option Values:
  #   ENABLE: Enable these feature's capability
  #   DISABLE: Disable this feature's capability
  #   Switch-ability:
  #   [FALSE] -> [TRUE]
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

RAM_SUPPORT_TYPE = NONE
  # Description:
  #   Define the DRAM supporting type controlled by Modem System
  # Option Values:
  #   DDR: Consumer DDR1
  #   LPDDR: Low Power DDR1
  # Switch-ability:
  #   Non-switchable

REDUCE_HEADER_DEPTH = TRUE
  # Description:
  #   To collect used header files to one folder to reduce compiler's searching time.
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

RES_PROT = FALSE
  # Description:
  #   Description:
  #   RES_PROT is the enhanced anticlone mechenism which not only protect the code, also the data
  #   It's for customer who have concern about the own picture and ringtons that may be stolen by read-out image
  #   Option Values:
  #   TRUE: Enable encryption on some important resource data
  #   FALSE: Resource protection is disabled
  #   Switch-ability:
  #   Non-switchable
  # Option Values:
  #   FALSE: Resource protection is disabled
  #   TRUE: Enable encryption on some important resource data
  # Switch-ability:
  #   Non-switchable

RFC2507_SUPPORT = TRUE
  # Description:
  #   Support RFC2507 IP header compression / decompression in PDCP of 3G modem. (TRUE/FALSE)
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

RF_DRDI_CAPABILITY_SUPPORT = TRUE
  # Description:
  #   RF_DRDI_CAPABILITY_SUPPORT = TRUE  
  #   Description: 
  #   Option Values: 
  #   TRUE:Set to TRUE if support DRDI. 
  #   FALSE:Set to FALSE if not support DRDI.
  # Option Values:
  #   FALSE: Set to FALSE if not support DRDI.
  #   TRUE: Set to TRUE if support DRDI.
  # Switch-ability:
  #   Non-switchable

RF_DRDI_GPIO_6PIN_SUPPORT = FALSE
  # Description:
  #   This feature option is to extend RF DRDI GPIO pin support from 3pin to 6pin
  # Option Values:
  #   FALSE: Disable RF DRDI GPIO 6pin support
  #   TRUE: Enable RF DRDI GPIO 6pin support
  # Switch-ability:
  #   Non-switchable

RSAT_SUPPORT = MODEM_WITH_RSAT
  # Description:
  #   To enable RSAT(STK AT command) feature
  # Option Values:
  #   MMI_WITH_RSAT: Enable RSAT support in MMI project
  #   MODEM_WITH_RSAT: Enable RSAT support in modem project
  #   NONE: Disable RSAT support(default)
  # Switch-ability:
  #   Non-switchable

SDIO_DEVICE_CONNECTIVITY = DISABLE
  # Description:
  #   For driver of sdio device that provides connectivity, e.g. AP and MD connectivity.
  # Option Values:
  #   DISABLE: Disable this feature
  #   ENABLE: Enable this feature
  # Switch-ability:
  #   Non-switchable

SDS_SUPPORT = FALSE
  # Description:
  #   create a dsecure data storage to make customer can store data without erased by file system format
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

SECURE_JTAG_ENABLE = TRUE
  # Description:
  #   Support security system
  # Option Values:
  #   FALSE: The JTAG on the phone will b off
  #   TRUE: The JTAG on the phone will b on
  # Switch-ability:
  #   Switchable

SECURE_PORT_SUPPORT = FALSE
  # Description:
  #   The feature option define if the sw supports enabling/disabling debug port(such catcher) via a paremater in SECURE_RO or not. TRUE means Support.
  #   It increases the security coverage of our platform.
  # Option Values:
  #   FALSE: To disable debug UART port protection
  #   TRUE: To enable debug UART port protection
  # Switch-ability:
  #   Non-switchable

SECURE_RO_ENABLE = FALSE
  # Description:
  #   Putting default values of SIM-ME-Lock and any other senitive data in put a encrypted RO.
  # Option Values:
  #   FALSE: To disable Secure RO
  #   TRUE: To enable Secure RO
  # Switch-ability:
  #   Non-switchable

SECURE_SUPPORT = FALSE
  # Description:
  #   Support security system
  # Option Values:
  #   FALSE: Non-secure load
  #   TRUE: Secure load
  # Switch-ability:
  #   Non-switchable

SECURE_VERSION = 1
  # Description:
  #   Support security system
  # Option Values:
  #   0: Secure version 0
  #   1: Secure version 1
  # Switch-ability:
  #   Switchable

SERIAL_FLASH_SUPPORT = FALSE
  # Description:
  #   Description:
  #   To enable serial flash support, please set SERIAL_FLASH_SUPPORT as TRUE.
  #   Option Values:
  #   TRUE: 
  #   FALSE: 
  #   Switch-ability:
  #   Non-switchable
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

SET_IMEI_BY_PLMN = FALSE
  # Description:
  #   According to CMCC demand, dual sim card UE should apply two same IMEI when camp on CMCC net. 
  #   and use two differ IMEI when romaing to other country NW.
  #   so we use SET_IMEI_BY_PLMN feture option to control the feture. 
  #   when the SET_IMEI_BY_PLMN is equal to TRUE, modem will enable the feture, and AP should enable feture option "MTK_SINGLE_IMEI" and display one IMEI in *#06#.
  #   when the SET_IMEI_BY_PLMN is equal to FALSE, modem will disable the feture.
  # Option Values:
  #   FALSE: if the value is false , modem will disable compiler option __SET_IMEI_BY_PLMN__, in gemini load, UE will use two differ IMEI
  #          and AP will should display two IMEI in *#06#
  #   TRUE: if the value is true, modem will enable compiler option __SET_IMEI_BY_PLMN__, 
  #         then mm2 module will judge which imei to use accroding to plmn, and send the correct IMEI to gas2,
  #         in home country PS2 use IMEI1, when romaing to other country, PS2 use IMEI2
  #         AP must enable feture option MTK_SINGLE_IMEI. and *#06# should display two differ IMEI
  # Switch-ability:
  #   Non-switchable

SGLTE_DSDS_SUPPORT = FALSE
  # Description:
  #   define UE if supports SGLTE DSDS or not (TRUE/FALSE)
  # Option Values:
  #   FALSE: not support SGLTE DSDS
  #   TRUE: support SGLTE DSDS
  # Switch-ability:
  #   Non-switchable

SIM_RECOVERY_ENHANCEMENT = TRUE
  # Description:
  #   Enable continuous SIM card recovery procedure
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

SIM_SWTICH_CONTROLLER_MT6302 = FALSE
  # Description:
  #   enable MT6302 4SIM feature
  # Option Values:
  #   FALSE: disable 4SIM controller for MT6302
  #   TRUE: enable 4SIM controller for MT6302
  # Switch-ability:
  #   Non-switchable

SIM_SWTICH_CONTROLLER_MT6306 = FALSE
  # Description:
  #   enable MT6306 4SIM feature
  # Option Values:
  #   FALSE: disable 4SIM controller for MT6306
  #   TRUE: enable 4SIM controller for MT6306
  # Switch-ability:
  #   Switchable

SIP_RAM_SIZE = NONE
  # Description:
  #   Set SiP RAM size according to BB chip 
  # Option Values:
  #   128M_BITS: SiP 128M bits RAM
  #   256M_BITS: SiP 256M bits RAM
  #   32M_BITS: SiP 32M bits RAM
  #   64M_BITS: SiP 64M bits RAM
  #   NONE: For Non-SiP RAM
  # Switch-ability:
  #   Non-switchable

SIP_SERIAL_FLASH_SIZE = NONE
  # Description:
  #   MT6252D SIP 16Mb serial flash and MT6250D SIP 32Mb serial flash. This feature option notifies EMI gen to generate SIP serial flash setting.After set this value other than NONE, need to set COMBO_MEMORY_SUPPORT to TRUE also.
  # Option Values:
  #   16M_BITS: SIP 16Mb serial flash
  #   32M_BITS: SIP 32Mb serial flash
  #   NONE: No serial flash SIP inside.
  # Switch-ability:
  #   Non-switchable

SMART_PHONE_CORE = ANDROID_MODEM
  # Description:
  #   To distinguish if it is a smart phone MODEM makefile or a smart phone APP makefile. NONE means it is not a smart-phone make file
  # Option Values:
  #   ANDROID_MODEM: modem processor software load for Android smart phone
  #   MODEM_ONLY: in-house testing load running on modem processor
  #   NONE: .
  # Switch-ability:
  #   Non-switchable

SML_SUPPORT = TRUE
  # Description:
  #   SIM-ME lock support
  # Option Values:
  #   FALSE: Not support SIM-ME lock
  #   TRUE: Support SIM-ME lock
  # Switch-ability:
  #   Non-switchable

SMS_OVER_PS_SUPPORT = TRUE
  # Description:
  #   Wrap the functionality - "SMS over PS" - for switchable
  # Option Values:
  #   FALSE: Disable SMS send over PS
  #   TRUE: Enable SMS send over PS
  # Switch-ability:
  #   Non-switchable

SMS_PHONE_ENTRY = 10
  # Description:
  #   SMS Entry Number in NVRAM
  # Option Values:
  #   NA: SMS entities on NVRAM
  # Switch-ability:
  #   Switchable

SMS_R8_NATION_LANGUAGE = FALSE
  # Description:
  #   The feature option is to support Turkish special character encoding method which is defined in 3GPP 23.038 Release8.
  #   New SMS UDH IEI is introduced to indicate the encoding/decoding alphabet of special Turikish character.
  #   Using the new method, SMS can avoid to use UCS2 coding scheme when the special Turkish character is present in the message.
  # Option Values:
  #   FALSE: Disable SMS R8 Nation Language (ex: Turkey)
  #   TRUE: Enable SMS R8 Nation Language (ex: Turkey)
  # Switch-ability:
  #   Non-switchable

SMS_TOTAL_ENTRY = 100
  # Description:
  #   total number of SMS stored in SIM and NVRAM can be read
  # Option Values:
  #   NA: SMS entities on NVRAM+SIM
  # Switch-ability:
  #   Switchable

SSS_SUPPORT = SSS_LIB
  # Description:
  #   Modem internal security library
  # Option Values:
  #   SSS_LIB: .
  #   SSS_SOURCE: .
  # Switch-ability:
  #   Non-switchable

SUB_BOARD_VER = PCB01
  # Description:
  #   Sub-name of the PCB or EVB.
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

SW_BINDING_SUPPORT = NONE
  # Description:
  #   These 2 options are used to replace original UID_SUPPORT. 
  #   SW_BINDING_SUPPORT specifies what kind of anticlone feature for the main code is enabled. 
  #   BIND_TO_CHIP_BASIC is the same type as original UID_SUPPORT
  #   BIND_TO_CHIP is the new type which can support FOTA
  #   BIND_TO_KEY is the new type which can bind software to a customer defined key in the chip (SV5 only)
  #   BIND_TO_CHIP_AND_KEY is the combination of BIND_TO_CHIP and BIND_TO_KEY
  # Option Values:
  #   BIND_TO_CHIP: Enable software binding which can support FOTA
  #   BIND_TO_CHIP_AND_KEY: BIND_TO_CHIP plus BIND_TO_KEY
  #   BIND_TO_CHIP_BASIC: Enable software binding. is the same type as original UID_SUPPORT
  #   BIND_TO_KEY: Enable software binding which can support ROM writer
  #   NONE: Software bindnig is off
  # Switch-ability:
  #   Non-switchable

SYSDRV_BACKUP_DISK_SUPPORT = NONE
  # Description:
  #   System drive support backup disk option.
  #   Option - RAW, FAT or NONE (MAUI)
  #   Option - TRUE or FALSE (Lipton project)
  #   RAW  : Enable Backup Partition with Raw Disk Format Feature.
  #   FAT/TRUE  : Enable Backup Partition with FAT Format Feature.
  #   NONE/FALSE : Disable Backup Partition for NOR and NAND.
  #   Purpose 
  #   (1) Support NVRAM Data Backup Feature.
  #   Usage 
  #   (1) Set to RAW/FAT.
  #   Limitation  
  #   (1) Currently NOR flash does not support FAT format backup disk.
  #   (2) Currently NAND flash does not support Raw format backup disk.
  #   (3) Must set NOR_SUPPORT_RAW_DISK=TRUE if backup paritition is raw disk format.
  # Option Values:
  #   FAT: System Drive backup to disk is supported, the disk is FAT Format
  #   NONE: System Drive backup to disk is not supported
  #   RAW: System Drive backup to disk is supported, the disk is RAW Format
  # Switch-ability:
  #   Non-switchable

TAS_FOR_C2K_ONOFF_SUPPORT = TRUE
  # Description:
  #   Description:
  #   Option Values:
  #   TRUE: Support TAS for C2K
  #   FALSE: Not Support TAS for C2K
  #   Switch-ability:
  #   Non-switchable
  # Option Values:
  #   FALSE: Not Support TAS for C2K
  #   TRUE: Support TAS for C2K
  # Switch-ability:
  #   Non-switchable

TAS_SUPPORT = FALSE
  # Description:
  #   Transmit Antenna Switch (TAS)
  # Option Values:
  #   FALSE: Not Support TAS
  #   TRUE: Support TAS
  # Switch-ability:
  #   Non-switchable

TEST_LOAD_TYPE = NONE
  # Description:
  #   internal test load type
  # Option Values:
  #   BASIC: BASIC
  #   L1S: L1S
  #   NONE: NONE
  #   UDVT: UDVT
  # Switch-ability:
  #   Non-switchable

TST_SET_LOG_BUF_SIZ = NONE
  # Description:
  #   This option allow users to set the desired logging buffer size.
  #   With this option, user could have a large logging buffer and reduce the probability of log drop.
  #   It's useful for a feaure testing under QC.
  # Option Values:
  #   13-23: Set the logging buffer as (2^n) bytes
  #   NONE: Invalidate this setting
  # Switch-ability:
  #   Non-switchable

TST_WRITE_TO_FILE = FALSE
  # Description:
  #   enable tst outputting traces to files
  # Option Values:
  #   FALSE: Disable
  #   TRUE: Enable
  # Switch-ability:
  #   Switchable

TX_POWER_OFFSET_SUPPORT = FALSE
  # Description:
  #   Depend on this option to distinguish whether TX POWER OFFSET is supported or not.
  # Option Values:
  #   FALSE: TX POWER OFFSET is NOT supported
  #   TRUE: TX POWER OFFSET is supported
  # Switch-ability:
  #   Non-switchable

UNIFIED_ISR_LEVEL = TRUE
  # Description:
  #   Only HISRs are executed in the whole system.
  #   The central controlled interrupt handler activate HISR directly without calling the related LISR in low level interrupt status.
  #   When UNIFIED_ISR_LEVEL is TRUE, add __UNIFIED_ISR_LEVEL__ global compiler option.
  # Option Values:
  #   FALSE: Disable Unified ISR Level Interrupt Architecture
  #   TRUE: Enable Unified ISR Level Interrupt Architecture
  # Switch-ability:
  #   Non-switchable

USB_BOOTUP_TRACE = FALSE
  # Description:
  #   Enable bootup trace via USB
  #   Should enable KEYPAD_DEBUG_TRACE or KAL_RECORD_BOOTUP_LOG as well
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Switchable

USB_COMPORT_PC_DRIVER_SUPPORT = MTK
  # Description:
  #   Only if __TC01__  is defined
  #   then 
  #   USB_COMPORT_PC_DRIVER_SUPPORT = TC01
  # Option Values:
  #   MS: Microsoft comport driver
  #   TC01: TC01 comport driver
  # Switch-ability:
  #   Non-switchable

USB_COM_PORT_SUPPORT = FALSE
  # Description:
  #   Open Com Port function
  # Option Values:
  #   FALSE: Disbale USB comport
  #   TRUE: Enable USB comport
  # Switch-ability:
  #   Non-switchable

USB_DOWNLOAD_IN_BL = NONE
  # Description:
  #   enable factory download / connection via USB cable via Bootloader
  # Option Values:
  #   NONE: USBDL in Bootloader is disabled
  #   NORMAL: USBDL in Bootloader is enabled for non-secure platform
  #   SECURE: USBDL in Bootloader is enabled for secure platform
  # Switch-ability:
  #   Switchable

USB_FW_DL_SUPPORT = FALSE
  # Description:
  #   Used to enable USB firmware download framework support.
  # Option Values:
  #   FALSE: USB FW DL will not be built.
  #   TRUE: USB FW DL will be built into bootloader/MOLY.
  # Switch-ability:
  #   Switchable

USB_HIGH_SPEED_COM_PORT_SUPPORT = FALSE
  # Description:
  #   USB High Speed Interface API usage.
  # Option Values:
  #   FALSE: Disable USB high-speed comport interface API
  #   TRUE: Enable USB high-speed comport interface API
  # Switch-ability:
  #   Switchable

USB_HS_SUPPORT = FALSE
  # Description:
  #   Customer can use this compileoption to decide USB2.0 or USB1.1. TRUE:: work in USB2.0
  # Option Values:
  #   FALSE: Disable USB High-Soeed mode (Run in Full Speed)
  #   TRUE: Enable USB High-Speed
  # Switch-ability:
  #   Non-switchable

USB_IN_META_SUPPORT = FALSE
  # Description:
  #   This option is used to turn on Meta mode bu using USB.
  # Option Values:
  #   FALSE: can only use UART
  #   TRUE: support by using USB
  # Switch-ability:
  #   Non-switchable

USB_IN_NORMAL_MODE_SUPPORT = FALSE
  # Description:
  #   When this option is enable, the USB Mass Storage function could be used in normal mode. That is, the targe doesn't need to reboot when the user select "Mass Storage" in the USB query menu.
  # Option Values:
  #   FALSE: Mass Storage only work on charging boot mode
  #   TRUE: Support USB mass storage in USB normal boot mode
  # Switch-ability:
  #   Switchable

USB_MASS_STORAGE_CDROM_SUPPORT = FALSE
  # Description:
  #   enable USB mass storage feature
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

USB_MASS_STORAGE_SUPPORT = FALSE
  # Description:
  #   enable USB mass storage feature
  # Option Values:
  #   FALSE: doesn't  support Mass Storage  function
  #   TRUE: Support USB mass storage function
  # Switch-ability:
  #   Non-switchable

USB_MBIM_SUPPORT = FALSE
  # Description:
  #   Used to enable USB MBIM device
  # Option Values:
  #   FALSE: Disable USB MBIM device
  #   TRUE: Enable USB MBIM device
  # Switch-ability:
  #   Non-switchable

USB_MSD_SUPPORT = FALSE
  # Description:
  #   Used to enable USB Mass Storage device.
  # Option Values:
  #   FALSE: USB Mass Storage module will not be built.
  #   TRUE: USB Mass Storage module will be built.
  # Switch-ability:
  #   Non-switchable

USB_MULTIPLE_COMPORT_ENABLE = FALSE
  # Description:
  #   To enable USB multiple com port feature
  # Option Values:
  #   FALSE: disable multiple usb comport
  #   TRUE: Enable multiple usb comport
  # Switch-ability:
  #   Switchable

USB_OSD_SUPPORT = FALSE
  # Description:
  #   Enable host OS type detection
  # Option Values:
  #   FALSE: Disable host detection, device switch to constant mode on different OS.
  #   TRUE: Enable host detection, device switch to different mode on different OS.
  # Switch-ability:
  #   Non-switchable

USB_RNDIS_SUPPORT = FALSE
  # Description:
  #   Used to enable USB RNDIS device
  # Option Values:
  #   FALSE: Disable USB CDC/ACM device
  #   TRUE: Enable USB CDC/ACM device
  # Switch-ability:
  #   Non-switchable

USB_SUPPORT = FALSE
  # Description:
  #   to enable USB function
  # Option Values:
  #   FALSE: Disable USB
  #   TRUE: Enable USB
  # Switch-ability:
  #   Switchable

USIM_SUPPORT = TRUE
  # Description:
  #   Support USIM card commands or not(TRUE: support; FALSE: not support)
  # Option Values:
  #   FALSE: Operate USIM as SIM
  #   TRUE: Enable the capability to operate USIM as USIM card, instead of SIM card.
  # Switch-ability:
  #   Non-switchable

UUS_SUPPORT = FALSE
  # Description:
  #   Enable/Disable CC UUS feature (TS22.087) support
  # Option Values:
  #   N/A
  # Switch-ability:
  #   Non-switchable

VAMOS_SUPPORT = TRUE
  # Description:
  #   To enable VAMOS modem capability
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable

WAV_CODEC = FALSE
  # Description:
  #   wav codec is a basic audio capability in general platforms. It supports linear PCM , u-law, a-law, ADPCM wav playabck/record so it should be turned on in most cases. Only turn off it in ultra-low-cost platform to reduce memory usage and it cause not only
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Switchable

WCDMA_PREFERRED = FALSE
  # Description:
  #   enable/disable WCDMA Preferred feature
  # Option Values:
  #   FALSE: Disable this feature
  #   TRUE: Enable this feature
  # Switch-ability:
  #   Non-switchable


2G_STANDBY_DAC_ENHANCEMENT_ENABLE = FALSE
  # Description:
  #   Description:
  #   For 2G standby search NW optimization
  #   Option Values:
  #   TRUE:Support 2G standby search NW optimization
  #   FALSE:Not Support 2G standby search NW optimization
  #   Visible-ability:
  #   Visible
  #   Switch-ability
  #   Non-switchable
  #   Modify-ability
  #   Modifiable
  # Option Values:
  #   FASLE: #Description:
  #          #For 2G standby search NW optimization
  #          #Option Values:
  #          #TRUE:Support 2G standby search NW optimization
  #          #FALSE:Not Support 2G standby search NW optimization
  #          #Visible-ability:
  #          #Visible
  #          #Switch-ability
  #          #Non-switchable
  #          #Modify-ability
  #          #Modifiable
  #   TRUE: #Description:
  #         #For 2G standby search NW optimization
  #         #Option Values:
  #         #TRUE:Support 2G standby search NW optimization
  #         #FALSE:Not Support 2G standby search NW optimization
  #         #Visible-ability:
  #         #Visible
  #         #Switch-ability
  #         #Non-switchable
  #         #Modify-ability
  #         #Modifiable
  # Switch-ability:
  #   Non-switchable

3SET_F8F9_ENABLE = TRUE

C2K_MODE_SUPPORT = C2K_MODE_CT

CUSTOM_CFLAGS = #-gdwarf-2

EES_ENABLE = TRUE

FOTA_ENABLE = NONE

FOTA_UPDATE_PACKAGE_ON_NAND = FALSE

HW_VER = LENTEK_O1MP1_K37TV1_BSP_HW

L1L2_TX_NEW_ARCH = TRUE

LOG_BUF_IN_SHM_SUPPORT = FALSE

LTE_OTDOA_SUPPORT = FALSE

MTK_INTERNAL_NVRAM_VISIBLE = FALSE

PMIC_VRF18_VOLTAGE_FINE_TUNE = TRUE

SLIM_DSP_LOGGING = FALSE

VM_CODEC = TRUE


# *************************************************************************
# TK MD Custom Feature Option Configuration
# *************************************************************************
TK_MD_CUS = NONE

# *************************************************************************
# Release Setting Section
# *************************************************************************
RELEASE_PACKAGE        = REL_CR_BASIC	# REL_CR_MMI_GPRS, REL_CR_MMI_GSM, REL_CR_L4_GPRS, REL_CR_L4_GSM REL_SUB_UAS_UMTS
RELEASE_PACKAGE_SUB_PS = REL_SUB_PROTOCOL

RELEASE_$(strip $(INPUT_METHOD)) = SRC	# MTK/SRC, only works when INPUT_METHOD is turning on
RELEASE_INPUT_METHODS_SRC =		# MMI_ZI, MMI_T9, MMI_ITAP,
RELEASE_TYPE = NONE           # NONE, INTERNAL
############################################################
COM_DEFS_FOR_MT6735_LTE_MT6169_CUSTOM   = MT6169_RF MT6169_LTE_RF MT6735_LTE_MT6169_CUSTOM MT6735_LTE_MT6169_CUSTOM
COM_DEFS_FOR_MT6735_2G_MT6169_CUSTOM    = MT6169_2G_RF  MT6735_2G_MT6169_CUSTOM MT6735_2G_MT6169_CUSTOM
COM_DEFS_FOR_MT6735_UMTS_FDD_MT6169_CUSTOM   = MT6169_RF MT6169_UMTS_FDD MT6735_UMTS_FDD_MT6169_CUSTOM MT6735_UMTS_FDD_MT6169_CUSTOM
# *************************************************************************
# Include MODEM.mak
# *************************************************************************
include make/MODEM.mak

# *************************************************************************
# Common preprocessor definitions
# *************************************************************************
CUSTOM_OPTION += __FLIGHT_MODE_SUPPORT__ __MANUAL_MODE_NW_SEL__ \
                 __USIM_DRV__ __SATC3__  \
                 __DISABLE_A5_2__ \
                 MSDC_MMC40_SUPPORT __DRV_MSDC_LAYOUT_DEFECT__
CUSTOM_OPTION += __HIGH_SPEED_USB_MODEM__

CUSTOM_OPTION += __SMS_OVER_PS_SUPPORT__ __R6_OOS__

CUSTOM_OPTION += __USB_HIGH_SPEED_COM_PORT_ENABLE__ __THREE_PORT_MODULE__

CUSTOM_OPTION += __DRV_NO_USB_CHARGER__


CUSTOM_OPTION += __MSDC_NO_WRITE_PROTECT__

CUSTOM_OPTION += __USB_MODEM_CARD_EINT_RESET__

CUSTOM_OPTION += __NVRAM_TURN_OFF_MSP__

CUSTOM_OPTION += DRV_USB_FORCE_TRIGGER_ONE_EINT

CUSTOM_OPTION += __NBR_CELL_INFO__

CUSTOM_OPTION += __23G_PRI_RESEL_SUPPORT__

CUSTOM_OPTION += __SATCC__ __SATCE__

CUSTOM_OPTION += __BT_SIM_PROFILE__

CUSTOM_OPTION += __FAST_ROAMING_SEARCH__
CUSTOM_OPTION += __SEARCH_NOT_PREFERRED_AVAILABLE_PLMN__
CUSTOM_OPTION += __MCC_ASSIST_FULLBAND_RAT_SELECTION__
CUSTOM_OPTION += __MM_INTEGRITY_PREF__
CUSTOM_OPTION += __CC_IRQ_EXIST__
CUSTOM_OPTION += __SATCL__ __SATCM__ __SATCR__
CUSTOM_OPTION += __FAST_HPPLMN_SEARCH__
CUSTOM_OPTION += __BCSFB__

CUSTOM_OPTION += __CP_HW_CIPHER_ENABLE__
CUSTOM_OPTION += __CP_HW_INTEGRITY_ENABLE__
CUSTOM_OPTION += TK6268_FPGA1
CUSTOM_OPTION +=  __HSPA_DATA_PATH_OPT__
CUSTOM_OPTION += __DYNAMIC_HSPA_PREFERENCE__
CUSTOM_OPTION += __HSPA_PREFERENCE_SETTING__
CUSTOM_OPTION += __SIM_RESET_BY_SIM__
CUSTOM_OPTION += __4G_IDC__
CUSTOM_OPTION += __VSIM__
CUSTOM_OPTION += __L1DMA_DFEDMA_WORKAROUND__
CUSTOM_OPTION += __AT_EPBW_SUPPORT__
CUSTOM_OPTION += __ARA_FOR_CELL_SELECTION__

CUSTOM_OPTION += __ANDROID_TXRX_ACTIVE_TIME__

# *************************************************************************
# Component trace definition header files for custom release only
# *************************************************************************
# Customer can add new trace headers here for new modules
NEW_CUS_REL_TRACE_DEFS_MODEM =



# *************************************************************************
# Custom Release Component Configuration
# *************************************************************************
include make/rel/$(strip $(RELEASE_PACKAGE)).mak

ifeq ($(strip $(CUSTOM_RELEASE)),TRUE)
  ifneq ($(findstring REL_SUB_, $(strip $(RELEASE_PACKAGE))),)
    -include make/rel/sub_ps/$(strip $(RELEASE_PACKAGE_SUB_PS)).mak
  endif
endif
