#!/usr/local/bin/perl -w
#
#****************************************************************************/
#* This script will generate partition layout files
#* Author: Kai Zhu (MTK81086)
#* 
#****************************************************************************/

#****************************************************************************
# Included Modules
#****************************************************************************
use File::Basename;
use File::Path qw(mkpath);
use Data::Dumper;
my $Version=3.6;
#my $ChangeHistory="3.1 AutoDetect eMMC Chip and Set MBR_Start_Address_KB\n";
#my $ChangeHistory = "3.2 Support OTP\n";
#my $ChangeHistory = "3.3 Support Shared SD Card\n";
#my $ChangeHistory = "3.4 Get partition table from project config path first\n";
#my $ChangeHistory = "3.5 CIP support\n";
my $ChangeHistory = "3.6 change output file path\n";
# Partition_table.xls arrays and columns
my @PARTITION_FIELD ;
my @START_FIELD_Byte ;
my @START_ADDR_PHY_Byte_HEX;
my @START_FIELD_Byte_HEX;
my @SIZE_FIELD_KB ;
my @TYPE_FIELD;
my @DL_FIELD ;
my @OTA_FIELD ;
my @PARTITION_IDX_FIELD ;
my @REGION_FIELD ;
my @RESERVED_FIELD;
my @BR_INDEX;
my @FB_ERASE_FIELD;
my @FB_DL_FIELD;
my @DL_TYPE;
my @OUTPUT_FILES;

my $COLUMN_PARTITION                = 1 ;
my $COLUMN_TYPE                     = $COLUMN_PARTITION + 1 ;
my $COLUMN_SIZE                     = $COLUMN_TYPE + 1 ;
my $COLUMN_SIZEKB                   = $COLUMN_SIZE + 1 ;
my $COLUMN_SIZE2                    = $COLUMN_SIZEKB + 1 ;
my $COLUMN_SIZE3                    = $COLUMN_SIZE2 + 1 ;
my $COLUMN_DL                       = $COLUMN_SIZE3 + 1 ;
my $COLUMN_FB_ERASE                 = $COLUMN_DL + 1;  # fastboot support
my $COLUMN_FB_DL                    = $COLUMN_FB_ERASE + 1;  # fastboot support
my $COLUMN_DT                       = $COLUMN_FB_DL + 1;
my $COLUMN_OTA                      = $COLUMN_DT + 1 ;
# emmc support
my $COLUMN_REGION		    = $COLUMN_FB_DL + 1;
my $COLUMN_RESERVED		    = $COLUMN_REGION + 1;

my $PMT_END_NAME;  #PMT_END_NAME
#eMMC
#EXT4 Partition Size
my $SECRO_SIZE;
my $USERDATA_SIZE;
my $SYSTEM_SIZE;
my $CACHE_SIZE;

my $total_rows = 0 ; #total_rows in partition_table
my $User_Region_Size_KB; #emmc USER region start_address
my $Page_Size	=2; # default NAND page_size of nand
my $AutoModify	=0;
my $DebugPrint    = 1; # 1 for debug; 0 for non-debug
my $LOCAL_PATH;
my $SCAT_NAME;
my $SHEET_NAME;
my $Min_user_region = 0;
my %preloader_alias; #alias for preloader c and h files modify
my %kernel_alias;	#alias for kernel c  file modify
my $FAT_SIZE_KB = 0; #for FAT merge
my $IPOH_SIZE_KB = 0; #for IPOH
my $CACHE_SIZE_KB = 0; #for IPOH and MNTL system
my $NVDATA_SIZE_KB = 0;	#for MNTL system
my $SYSTEM_SIZE_KB = 0;	#for MNTL system
my @MemoryDeviceList;
my @PartNumbers; #Part numbers got from custom_MemoryDevice.h
my @GPT;

my $psize=2048;
my $bsize=64;
my $csize=512;
my @tlc_blk, @slc_blk, @tlc_android = (0, 0);
my @MTK_TLC_SLC_RATIO = (12, 12);
my $vendor_partition_exists = "N";
BEGIN
{
  $LOCAL_PATH = dirname($0);
}
my $CD_ALPS;
$CD_ALPS="$LOCAL_PATH/../../../../../../..";
print "LOCAL_PATH: $LOCAL_PATH\n";
print "CD_ALPS: $CD_ALPS\n";
use lib "$LOCAL_PATH/../../Spreadsheet";
use lib "$LOCAL_PATH/../../";
require 'ParseExcel.pm';
use pack_dep_gen;

#parse argv from alps/mediatek/config/{project}/ProjectConfig.mk
$PLATFORM = $ENV{PLATFORM};
$platform = lc($PLATFORM);
$PROJECT = $ENV{PROJECT};
#overwrite project name
if(exists $ENV{MTK_TARGET_PROJECT})
{
	$PROJECT= $ENV{MTK_TARGET_PROJECT};
}
$PAGE_SIZE = $ENV{MTK_NAND_PAGE_SIZE};
$EMMC_SUPPORT= $ENV{MTK_EMMC_SUPPORT};
$MLC_NAND_SUPPORT= $ENV{MTK_MLC_NAND_SUPPORT};
$TLC_NAND_SUPPORT= $ENV{MTK_TLC_NAND_SUPPORT};
$MTK_TLC_SLC_RATIO= $ENV{MTK_TLC_SLC_RATIO};
$MTK_HALF_NAND_SUPPORT= $ENV{MTK_HALF_NAND_SUPPORT};
$MTK_TLC_SLC_RATIO[0] = $MTK_TLC_SLC_RATIO;
$MTK_TLC_SLC_RATIO[1] = $MTK_TLC_SLC_RATIO*2;
$RAWFS_NAND_SUPPORT = $ENV{MTK_RAWFS_NAND_SUPPORT};
$LDVT_SUPPORT= $ENV{MTK_LDVT_SUPPORT};
$OPERATOR_SPEC = $ENV{OPTR_SPEC_SEG_DEF};
$MTK_EMMC_OTP_SUPPORT= $ENV{MTK_EMMC_SUPPORT_OTP};
$MTK_SHARED_SDCARD=$ENV{MTK_SHARED_SDCARD};
$TARGET_BUILD_VARIANT=$ENV{TARGET_BUILD_VARIANT};
$MTK_CIP_SUPPORT = $ENV{MTK_CIP_SUPPORT};
$MTK_FAT_ON_NAND=$ENV{MTK_FAT_ON_NAND};
$MTK_IPOH_SUPPORT=$ENV{MTK_IPOH_SUPPORT};
$MTK_NAND_UBIFS_SUPPORT=$ENV{MTK_NAND_UBIFS_SUPPORT};
$MTK_NAND_UBIFS_FASTMAP_SUPPORT=$ENV{MTK_NAND_UBIFS_FASTMAP_SUPPORT};
$MTK_NAND_MTK_FTL_SUPPORT       = $ENV{MTK_NAND_MTK_FTL_SUPPORT};
$MNTL_SUPPORT = $ENV{MNTL_SUPPORT};
$YAML_SUPPORT=$ENV{MTK_YAML_SCATTER_FILE_SUPPORT};
$SPI_NAND_SUPPORT=$ENV{MTK_SPI_NAND_SUPPORT};
$FACTORY_RESET_PROTECTION_SUPPORT = $ENV{MTK_FACTORY_RESET_PROTECTION_SUPPORT};
my $COMBO_NAND_SUPPORT =$ENV{MTK_COMBO_NAND_SUPPORT};
my $PAGE_PER_BLOCK = 0;
$PL_MODE=$ENV{PL_MODE};
$EFUSE_WRITER_SUPPORT=$ENV{MTK_EFUSE_WRITER_SUPPORT};
$SPM_FW_USE_PARTITION=$ENV{SPM_FW_USE_PARTITION};
$MCUPM_FW_USE_PARTITION=$ENV{MCUPM_FW_USE_PARTITION};
$MTK_DTBO_FEATURE=$ENV{MTK_DTBO_FEATURE};

my $CUSTOM_BASEPROJECT;
my $PTGEN_ENV;
my $PTGEN_XLS=$ENV{PTGEN_XLS};

if(exists $ENV{PTGEN_ENV})
{
	$PTGEN_ENV= $ENV{PTGEN_ENV};
}
else
{
	$PTGEN_ENV= "GEN_ALL";
}
if (exists $ENV{BASEPROJECT})
{
	if($ENV{BASEPROJECT} eq "")
	{
		$CUSTOM_BASEPROJECT = $PROJECT;
	}
	else
	{
		$CUSTOM_BASEPROJECT = $ENV{BASEPROJECT};
	}
}
elsif (exists $ENV{MTK_BASE_PROJECT})
{
	$CUSTOM_BASEPROJECT = $ENV{MTK_BASE_PROJECT};
}
else
{
	$CUSTOM_BASEPROJECT = $PROJECT;
}
#inputfiles

my $PART_TABLE_FILENAME  = "$CD_ALPS/device/mediatek/build/build/tools/ptgen/$PLATFORM/partition_table_nand_${PLATFORM}.xls"; # excel file name
my $PROJECT_PART_TABLE_FILENAME;
my $REGION_TABLE_FILENAME = "$CD_ALPS/device/mediatek/build/build/tools/emigen/$PLATFORM/MemoryDeviceList_${PLATFORM}.xls";  #eMMC region information
my $EMMC_COMPO	= "$CD_ALPS/device/mediatek/build/build/tools/ptgen/MT6739/mbr_addr.pl" ;
my $CUSTOM_MEMORYDEVICE_H_NAME  = "$CD_ALPS/vendor/mediatek/proprietary/bootable/bootloader/preloader/custom/$CUSTOM_BASEPROJECT/inc/custom_MemoryDevice.h";

# specify output path of intermedia files.
my $PRODUCT_OUT;
# temp output path
my $TMP_OUT_PATH;
#copy path for different project setting
my $PTGEN_PRELOADER_OUT;
my $PTGEN_LK_OUT;
my $PTGEN_KERNEL_OUT;
my $PTGEN_PROJECT_OUT;
my $PTGEN_MK_OUT;
my $COPY_PATH_KERNEL_PARTITION_H;
my $COPY_PATH_PARTITION_DEFINE_H_NAME_PL;
my $COPY_PATH_PARTITION_DEFINE_H_NAME_LK;
my $COPY_PATH_PARTITION_DEFINE_H_NAME_K;
my $COPY_PATH_PARTITION_DEFINE_C_NAME;
my $COPY_PATH_PART_SIZE_LOCATION;
my $COPY_PATH_PMT_H_NAME_PL;
my $COPY_PATH_PMT_H_NAME_LK;
my $COPY_PATH_PMT_H_NAME_K;
my $COPY_PATH_PreloaderC;
my $COPY_PATH_LK_MT_PartitionH;
my $COPY_PATH_LK_PartitionC;
my $COPY_SCATTER_BR_FILES_PATH;
my $COPY_PATH_COMBO_NAND_KERNELH;
my $COPY_PATH_COMBO_NAND_TOOLH;

my %mountPointMapList;

if (exists $ENV{OUT_DIR})
{
	print "!!!!!!!!!!!OUT_DIR=$ENV{OUT_DIR} PROJECT = $PROJECT\n";
	$PRODUCT_OUT = "$ENV{OUT_DIR}/target/product/$PROJECT";
}

else
{
    $PRODUCT_OUT = "$CD_ALPS/out/target/product/$PROJECT";
}
print "PRODUCT_OUT=$PRODUCT_OUT\n";

if($ENV{PTGEN_CHECKOUT} eq "yes")
{
# temp output path
	$TMP_OUT_PATH="device/mediatek/build/build/tools/ptgen/$PLATFORM/out";
#copy path for different project setting
	$COPY_PATH_PARTITION_DEFINE_H_NAME_PL="vendor/mediatek/proprietary/bootable/bootloader/preloader/custom/$PROJECT/inc";
	$COPY_PATH_PMT_H_NAME_PL="vendor/mediatek/proprietary/bootable/bootloader/preloader/custom/$PROJECT/inc";
	$COPY_PATH_PreloaderC="vendor/mediatek/proprietary/bootable/bootloader/preloader/custom/$PROJECT/inc";

	$COPY_PATH_PARTITION_DEFINE_H_NAME_LK="vendor/mediatek/proprietary/bootable/bootloader/lk/target/$PROJECT/inc";
	$COPY_PATH_PMT_H_NAME_LK="vendor/mediatek/proprietary/bootable/bootloader/lk/target/$PROJECT/inc";
	$COPY_PATH_LK_MT_PartitionH="vendor/mediatek/proprietary/bootable/bootloader/lk/target/$PROJECT/inc";
	$COPY_PATH_LK_PartitionC="vendor/mediatek/proprietary/bootable/bootloader/lk/target/$PROJECT/inc";

	$COPY_PATH_KERNEL_PARTITION_H="kernel-4.4/arch/arm/mach-mt6739/$PROJECT/common";
	$COPY_PATH_PARTITION_DEFINE_H_NAME_K="kernel-4.4/arch/arm/mach-mt6739/$PROJECT/common";
	$COPY_PATH_PARTITION_DEFINE_C_NAME  ="kernel-4.4/arch/arm/mach-mt6739/$PROJECT/common";
	$COPY_PATH_PMT_H_NAME_K="kernel-4.4/arch/arm/mach-mt6739/$PROJECT/common";
	$COPY_PATH_COMBO_NAND_KERNELH="kernel-4.4/arch/arm/mach-mt6739/$PROJECT/common";

	$COPY_SCATTER_BR_FILES_PATH="device/mediatek/$PROJECT";
	$COPY_PATH_PART_SIZE_LOCATION="device/mediatek/$PROJECT";

	$COPY_PATH_COMBO_NAND_TOOLH="vendor/mediatek/proprietary/external/mtd-utils/ubi-utils";
}
else
{
# temp output path
	if(exists $ENV{TMP_OUT_PATH})
	{
		$TMP_OUT_PATH = $ENV{TMP_OUT_PATH};
	}
	else
	{
		$TMP_OUT_PATH="$CD_ALPS/device/mediatek/build/build/tools/ptgen/$PLATFORM/out";
	}
#copy path for different project setting
	$COPY_PATH_PARTITION_DEFINE_H_NAME_PL="$CD_ALPS/vendor/mediatek/proprietary/bootable/bootloader/preloader/custom/$PROJECT/inc";
	$COPY_PATH_PMT_H_NAME_PL="$CD_ALPS/vendor/mediatek/proprietary/bootable/bootloader/preloader/custom/$PROJECT/inc";
	$COPY_PATH_PreloaderC="$CD_ALPS/vendor/mediatek/proprietary/bootable/bootloader/preloader/custom/$PROJECT/inc";

	$COPY_PATH_PARTITION_DEFINE_H_NAME_LK="$CD_ALPS/vendor/mediatek/proprietary/bootable/bootloader/lk/target/$PROJECT/inc";
	$COPY_PATH_PMT_H_NAME_LK="$CD_ALPS/vendor/mediatek/proprietary/bootable/bootloader/lk/target/$PROJECT/inc";
	$COPY_PATH_LK_MT_PartitionH="$CD_ALPS/vendor/mediatek/proprietary/bootable/bootloader/lk/target/$PROJECT/inc";
	$COPY_PATH_LK_PartitionC="$CD_ALPS/bootable/bootloader/lk/target/$PROJECT/inc";

	$COPY_PATH_KERNEL_PARTITION_H="$CD_ALPS/kernel-4.4/arch/arm/mach-mt6739/$PROJECT/common";
	$COPY_PATH_PARTITION_DEFINE_H_NAME_K="$CD_ALPS/kernel-4.4/arch/arm/mach-mt6739/$PROJECT/common";
	$COPY_PATH_PARTITION_DEFINE_C_NAME  ="$CD_ALPS/kernel-4.4/arch/arm/mach-mt6739/$PROJECT/common";
	$COPY_PATH_PMT_H_NAME_K="$CD_ALPS/kernel-4.4/arch/arm/mach-mt6739/$PROJECT/common";
	$COPY_PATH_COMBO_NAND_KERNELH="$CD_ALPS/kernel-4.4/arch/arm/mach-mt6739/$PROJECT/common";
	$COPY_PATH_COMBO_NAND_TOOLH="$CD_ALPS/vendor/mediatek/proprietary/external/mtd-utils/ubi-utils";

	$COPY_SCATTER_BR_FILES_PATH="$CD_ALPS/device/mediatek/$PROJECT";
	$COPY_PATH_PART_SIZE_LOCATION="$CD_ALPS/device/mediatek/$PROJECT";

	if(exists $ENV{PTGEN_PRELOADER_OUT})
	{
		$PTGEN_PRELOADER_OUT = $ENV{PTGEN_PRELOADER_OUT};
		$COPY_PATH_PARTITION_DEFINE_H_NAME_PL="$PTGEN_PRELOADER_OUT";
		$COPY_PATH_PMT_H_NAME_PL="$PTGEN_PRELOADER_OUT";
		$COPY_PATH_PreloaderC="$PTGEN_PRELOADER_OUT";
	}
	if(exists $ENV{PTGEN_LK_OUT})
	{
		$PTGEN_LK_OUT = $ENV{PTGEN_LK_OUT};
		$COPY_PATH_PARTITION_DEFINE_H_NAME_LK="$PTGEN_LK_OUT";
		$COPY_PATH_PMT_H_NAME_LK="$PTGEN_LK_OUT";
		$COPY_PATH_LK_MT_PartitionH="$PTGEN_LK_OUT";
		$COPY_PATH_LK_PartitionC="$PTGEN_LK_OUT";
	}
	if(exists $ENV{PTGEN_KERNEL_OUT})
	{
		$PTGEN_KERNEL_OUT = $ENV{PTGEN_KERNEL_OUT};
		$COPY_PATH_KERNEL_PARTITION_H="$PTGEN_KERNEL_OUT";
		$COPY_PATH_PARTITION_DEFINE_H_NAME_K="$PTGEN_KERNEL_OUT";
		$COPY_PATH_PARTITION_DEFINE_C_NAME  ="$PTGEN_KERNEL_OUT";
		$COPY_PATH_PMT_H_NAME_K="$PTGEN_KERNEL_OUT";
		$COPY_PATH_COMBO_NAND_KERNELH="$PTGEN_KERNEL_OUT";
		$COPY_PATH_COMBO_NAND_TOOLH="$PTGEN_KERNEL_OUT";
	}
	if(exists $ENV{PTGEN_PROJECT_OUT})
	{
		print "!!!!!!!!!$ENV{PTGEN_PROJECT_OUT}\n";
		$PTGEN_PROJECT_OUT=$ENV{PTGEN_PROJECT_OUT};
		$COPY_SCATTER_BR_FILES_PATH="$PTGEN_PROJECT_OUT";
	}
	else
	{
		$PTGEN_PROJECT_OUT=$PRODUCT_OUT;
		$COPY_SCATTER_BR_FILES_PATH="$PTGEN_PROJECT_OUT";
	}
	print "!!!!!!!!!!!PTGEN_PROJECT_OUT=$PTGEN_PROJECT_OUT PRODUCT_OUT = $ENV{PRODUCT_OUT}\n";
	print "COPY_SCATTER_BR_FILES_PATH=$COPY_SCATTER_BR_FILES_PATH\n";
	if(exists $ENV{PTGEN_MK_OUT})
	{
		$PTGEN_MK_OUT=$ENV{PTGEN_MK_OUT};
		$COPY_PATH_PART_SIZE_LOCATION="$PTGEN_MK_OUT";
	}
}
#output filelist
my $PMT_H_NAME          = "$TMP_OUT_PATH/pmt.h";
my $PARTITION_DEFINE_H_NAME     = "$TMP_OUT_PATH/partition_define.h"; #
my $PARTITION_DEFINE_C_NAME    = "$TMP_OUT_PATH/partition_dumchar.h";
my $PART_SIZE_LOCATION		= "$TMP_OUT_PATH/partition_size.mk" ; # store the partition size for ext4 buil
my $PART_XML_LOCATION		= "$TMP_OUT_PATH/partition_nand.xml" ; # store the partition size for ext4 buil
my $PreloaderC	="$TMP_OUT_PATH/custpart_private.h";
my $KernelH 	="$TMP_OUT_PATH/partition.h";
my $LK_MT_PartitionH = "$TMP_OUT_PATH/mt_partition.h";
my $LK_PartitionC = "$TMP_OUT_PATH/part_private.h";
my $COMBO_NAND_KERNELH = "$TMP_OUT_PATH/combo_nand.h";
my $COMBO_NAND_TOOLH = "$TMP_OUT_PATH/combo_nand.h";
my $AUTO_CHECK_OUT_FILES="$TMP_OUT_PATH/auto_check_out.txt";
#create output path
if (-e $TMP_OUT_PATH) {
	`rm -fr $TMP_OUT_PATH`;
}
#Set SCAT_NAME
my $SCAT_NAME_DIR   = "$TMP_OUT_PATH";
$SCAT_NAME = "${SCAT_NAME_DIR}/${PLATFORM}_Android_scatter.txt";
print "SCAT_NAME=$SCAT_NAME\n";

#Set SHEET_NAME
if($MLC_NAND_SUPPORT eq "yes"){
	$SHEET_NAME = "mlc";
} elsif($TLC_NAND_SUPPORT eq "yes"){
	$SHEET_NAME = "tlc";
} else {
	if($COMBO_NAND_SUPPORT eq "yes") {
#TODO: 4K is only for SLC comboNAND, not for MLC
		$PAGE_SIZE = "4K";
	}
	$SHEET_NAME = "nand " . $PAGE_SIZE ;
	if($PAGE_SIZE=~/(\d)K/){
		$Page_Size=$1;
	}else{
		$Page_Size=2;
	}
}

if ($MTK_NAND_MTK_FTL_SUPPORT eq "yes") {
	$SHEET_NAME = "ftl";
} elsif ($MNTL_SUPPORT eq "yes") {
	$SHEET_NAME = "mntl";
}

if(!defined $TARGET_BUILD_VARIANT || $TARGET_BUILD_VARIANT eq ""){
	$SHEET_NAME = $SHEET_NAME . " eng";
}else{
	if($TARGET_BUILD_VARIANT eq "eng"){
		$SHEET_NAME = $SHEET_NAME . " eng";
	}else{
		$SHEET_NAME = $SHEET_NAME . " user";
	}
}

if($LDVT_SUPPORT eq "yes"){
	$SHEET_NAME = "ldvt";
}

if($PL_MODE ne ""){
	$SHEET_NAME = lc($PL_MODE);
}

#****************************************************************************
# main thread
#****************************************************************************
# get already active Excel application or open new
PrintDependModule($0);
print "*******************Arguments*********************\n" ;
print "Version=$Version ChangeHistory:$ChangeHistory\n";
print "PLATFORM = $ENV{PLATFORM};
PROJECT = $PROJECT;
PAGE_SIZE = $ENV{MTK_NAND_PAGE_SIZE};
EMMC_SUPPORT= $ENV{MTK_EMMC_SUPPORT};
LDVT_SUPPORT= $ENV{MTK_LDVT_SUPPORT};
TARGET_BUILD_VARIANT= $ENV{TARGET_BUILD_VARIANT};
MTK_EMMC_OTP_SUPPORT= $ENV{MTK_EMMC_OTP_SUPPORT};
MTK_SHARED_SDCARD=$ENV{MTK_SHARED_SDCARD};
MTK_CIP_SUPPORT=$ENV{MTK_CIP_SUPPORT};
MTK_NAND_UBIFS_SUPPORT=$ENV{MTK_NAND_UBIFS_SUPPORT};
MTK_NAND_MTK_FTL_SUPPORT=$ENV{MTK_NAND_MTK_FTL_SUPPORT};
MNTL_SUPPORT=$ENV{MNTL_SUPPORT};
MTK_YAML_SCATTER_FILE_SUPPORT=$ENV{MTK_YAML_SCATTER_FILE_SUPPORT};
COMBO_NAND_SUPPORT=$COMBO_NAND_SUPPORT
BASEPROJECT = $CUSTOM_BASEPROJECT;
PTGEN_ENV=${PTGEN_ENV};
PTGEN_XLS=${PTGEN_XLS};
PL_MODE=$ENV{PL_MODE};
\n";
print "MLC_NAND_SUPPORT=$MLC_NAND_SUPPORT\n";
print "MNTL_SUPPORT=$MNTL_SUPPORT\n";
print "TLC_NAND_SUPPORT=$TLC_NAND_SUPPORT\n";
print "MTK_TLC_SLC_RATIO=$MTK_TLC_SLC_RATIO\n";
print "SHEET_NAME=$SHEET_NAME\n";
print "SCAT_NAME=$SCAT_NAME\n" ;
print "IPOH_SUPPORT=$MTK_IPOH_SUPPORT\n";

print "*******************Arguments*********************\n\n\n\n" ;

#$PartitonBook = Spreadsheet::ParseExcel->new()->Parse($PART_TABLE_FILENAME);

&InitAlians();

&ReadNANDExcelFile();
&ReadCustomMemoryDeviceFile();
&GenNANDInfo();
print "PAGE_SIZE=$psize\n" ;
&check_vendor_partition_config($platform);
&check_vendor_partition_config($ENV{MTK_BASE_PROJECT});
if ($ENV{MTK_BASE_PROJECT} ne $ENV{MTK_TARGET_PROJECT})
{
	&check_vendor_partition_config($ENV{MTK_TARGET_PROJECT});
}

&ReadExcelFile () ;
&check_boardconfig_partition_info();

&GenHeaderFile () ;

&GenYAMLScatFile();

&GenPartSizeFile ();
&GenPerloaderCust_partC();
&GenPmt_H();

if($PL_MODE ne ""){
	print "**********Ptgen Done********** ^_^\n" ;

	print "\n\nPtgen Generated files list:\n$SCAT_NAME\n$PARTITION_DEFINE_H_NAME\n$PreloaderC\n$PMT_H_NAME  \n\n\n\n\n";

	exit ;
}

&GenLK_PartitionC();
&GenLK_MT_PartitionH();
&GenKernel_PartitionC();
&do_copy_files();
if($ENV{PTGEN_CHECKOUT} eq "yes")
{
	#do nothing
}
else
{
	if (-e $TMP_OUT_PATH) {
		`rm -fr $TMP_OUT_PATH`;
	}
}
print "**********Ptgen Done********** ^_^\n" ;

print "\n\nPtgen modified or Generated files list:\n$SCAT_NAME\n$PARTITION_DEFINE_H_NAME\n$PART_SIZE_LOCATION\n/out/MBR EBR1 EBR2 \n\n\n\n\n";
foreach my $t (@OUTPUT_FILES){
	print "$t\n";
}
exit ;

#****************************************************************************
# subroutine:  InitAlians
# return:
#****************************************************************************
sub InitAlians(){
	$preloader_alias{"SECCFG"}="SECURE";
	$preloader_alias{"SEC_RO"}="SECSTATIC";
	$preloader_alias{"ANDROID"}="ANDSYSIMG";
	$preloader_alias{"USRDATA"}="USER";

	$lk_xmodule_alias{"DSP_BL"}="DSP_DL";
	$lk_xmodule_alias{"SECCFG"}="SECURE";
	$lk_xmodule_alias{"SEC_RO"}="SECSTATIC";
	$lk_xmodule_alias{"EXPDB"}="APANIC";
	$lk_xmodule_alias{"ANDROID"}="ANDSYSIMG";
	$lk_xmodule_alias{"USRDATA"}="USER";
	$kernel_alias{"SECCFG"}="seccnfg";
	$kernel_alias{"BOOTIMG"}="boot";
	$kernel_alias{"SEC_RO"}="secstatic";
	$kernel_alias{"ANDROID"}="system";
	$kernel_alias{"USRDATA"}="userdata";

	$lk_alias{"BOOTIMG"}="boot";
	$lk_alias{"ANDROID"}="system";
	$lk_alias{"USRDATA"}="userdata";
	%mountPointMapList = (
		protect_f => "protect_s",
		protect_s => "protect_f",
		metadata => "",
		odm => "",
		system => "",
		cache => "",
		vendor => "",
		userdata => "",
	);
}

#****************************************************************************
# subroutine:  ReadExcelFile
# return:
#****************************************************************************

sub ReadExcelFile()
{
	my $sheet = load_partition_info($SHEET_NAME);
	my $row_t = 1;
	my $row = 1 ;
	my $pt_name = &xls_cell_value($sheet, $row, $COLUMN_PARTITION,$SHEET_NAME);
	my $px_index = 1;
	my $px_index_t = 1;
	my $br_index = 0;
	my $p_count = 0;
	my $br_count =0;
	while($pt_name ne "END"){
		$type		 = &xls_cell_value($sheet, $row, $COLUMN_TYPE,$SHEET_NAME) ;
		if($type eq "EXT4" || $type eq "FAT" )
		{
			if($pt_name eq "FAT" && $MTK_SHARED_SDCARD eq "yes"){
				print "Skip FAT because of MTK_SHARED_SDCARD On\n";
	  		}elsif($pt_name eq "CUSTOM" && $MTK_CIP_SUPPORT ne "yes"){
		  		print "Skip CUSTOM because of MTK_CIP_SUPPORT off\n";
	  		}else{
				$p_count++;
			}
		}
		$row++;
		$pt_name = &xls_cell_value($sheet, $row, $COLUMN_PARTITION,$SHEET_NAME);
	}
	$br_count = int(($p_count+2)/3)-1;

	$row =1;
	$pt_name = &xls_cell_value($sheet, $row, $COLUMN_PARTITION,$SHEET_NAME);
	# remove leading and tailing spaces @TT
	$pt_name =~ s/^\s+// ;
	$pt_name =~ s/\s+$// ;

	my $tmp_index=1;
	my $skip_fat=0;

	if($EMMC_SUPPORT eq "yes"){
		$skip_fat=0;
		if($MTK_SHARED_SDCARD eq "yes"){
			$skip_fat=1;
		}
	}else{
		$skip_fat=1;
		if($MTK_FAT_ON_NAND eq "yes"){
			$skip_fat=0;
		}
	}
	$gpt_idx = 0;
	$SECTOR_SIZE = 4096;
	while($pt_name ne "END"){
		$type		 = &xls_cell_value($sheet, $row, $COLUMN_TYPE,$SHEET_NAME) ;
		if($pt_name eq "FAT" && $skip_fat==1 ){
			print "Skip FAT because of MTK_SHARED_SDCARD or MTK_FAT_ON_NAND\n";
			if($MTK_FAT_ON_NAND eq "yes"){
				if($MLC_NAND_SUPPORT eq "yes"){
					$FAT_SIZE_KB=&xls_cell_value($sheet, $row, $COLUMN_SIZEKB,$SHEET_NAME) ;
				}
			}
		}elsif($pt_name eq "IPOH"){
			if($MTK_IPOH_SUPPORT eq "yes"){
				$IPOH_SIZE_KB=&xls_cell_value($sheet, $row, $COLUMN_SIZEKB,$SHEET_NAME) ;
			}
		}elsif($pt_name eq "CUSTOM" && $MTK_CIP_SUPPORT ne "yes"){
			print "Skip CUSTOM because of MTK_CIP_SUPPORT off\n";
		}
		elsif($pt_name eq "PROTECT_S" and $type ne "MNTL")
		{
			print "Skip PROTECT_S because of MTK_RAWFS_NAND_SUPPORT is on\n";
		}
		elsif($pt_name eq "FRP" && $FACTORY_RESET_PROTECTION_SUPPORT ne "yes")
		{
			print "Skip FRP because of FACTORY_RESET_PROTECTION_SUPPORT off\n";
		}
		elsif($pt_name eq "VENDOR" && $vendor_partition_exists ne "Y")
		{
			print "Skip VENDOR because of vendor_partition_exists \"N\"\n";
		}
		else{
			print "type $type.\n";
			if($type eq "MNTL"){
				$SIZE_KB=&xls_cell_value($sheet, $row, $COLUMN_SIZEKB,$SHEET_NAME) ;
				if ($SIZE_KB == 0){
					$SIZE_KB = 2048 * 1024;
				}
				if ($pt_name eq "ANDROID") {
					$GPT[$gpt_idx]{"NAME"} = "SYSTEM";
				} elsif ($pt_name eq "USRDATA") {
					$GPT[$gpt_idx]{"NAME"} = "USERDATA";
				} else {
					$GPT[$gpt_idx]{"NAME"} = $pt_name;
				}
				if ($gpt_idx == 0){
					$GPT[$gpt_idx]{"START_SECTOR"} = 4;
				} else {
					$GPT[$gpt_idx]{"START_SECTOR"} = $GPT[$gpt_idx-1]{"END_SECTOR"} + 1;
				}
				$GPT[$gpt_idx]{"END_SECTOR"} = $GPT[$gpt_idx]{"START_SECTOR"} + $SIZE_KB * 1024 / $SECTOR_SIZE - 1;
				$gpt_idx++;
				print "gpt_idx $gpt_idx\n";
				if ($pt_name eq "USRDATA") {
					$PARTITION_FIELD[$row_t -1] = $pt_name;
					$SIZE_FIELD_KB[$row_t -1]    = &xls_cell_value($sheet, $row, $COLUMN_SIZEKB,$SHEET_NAME) ;
					$DL_FIELD[$row_t-1]        = &xls_cell_value($sheet, $row, $COLUMN_DL,$SHEET_NAME) ;
					$TYPE_FIELD[$row_t -1]		 = &xls_cell_value($sheet, $row, $COLUMN_TYPE,$SHEET_NAME) ;
					$FB_DL_FIELD[$row_t-1]    = &xls_cell_value($sheet, $row, $COLUMN_FB_DL,$SHEET_NAME) ;
		 			$FB_ERASE_FIELD[$row_t-1]    = &xls_cell_value($sheet, $row, $COLUMN_FB_ERASE,$SHEET_NAME) ;
					$DL_TYPE[$row_t-1]    = &xls_cell_value($sheet, $row, $COLUMN_DT,$SHEET_NAME) ; 
					$OTA_FIELD[$row_t-1]        = &xls_cell_value($sheet, $row, $COLUMN_OTA,$SHEET_NAME) ;
					$row_t++;
				}
	
#			}
#			if($MNTL_SUPPORT eq "yes" && $pt_name eq "ANDROID"){
#				$SYSTEM_SIZE_KB=&xls_cell_value($sheet, $row, $COLUMN_SIZEKB,$SHEET_NAME) ;
#				print "Skip ANDROID for MNTL_SUPPORT and SYSTEM_SIZE_KB=$SYSTEM_SIZE_KB\n";
#			}elsif($MNTL_SUPPORT eq "yes" && $pt_name eq "CACHE"){
#				$CACHE_SIZE_KB=&xls_cell_value($sheet, $row, $COLUMN_SIZEKB,$SHEET_NAME) ;
#				print "Skip CACHE for MNTL_SUPPORT and CACHE_SIZE_KB=$CACHE_SIZE_KB\n";
#			}elsif($MNTL_SUPPORT eq "yes" && $pt_name eq "NVDATA"){
#				$NVDATA_SIZE_KB=&xls_cell_value($sheet, $row, $COLUMN_SIZEKB,$SHEET_NAME) ;
#				print "Skip NVDATA for MNTL_SUPPORT and NVDATA_SIZE_KB=$NVDATA_SIZE_KB\n";
			}else{
				$PARTITION_FIELD[$row_t -1] = $pt_name;
				$SIZE_FIELD_KB[$row_t -1]    = &xls_cell_value($sheet, $row, $COLUMN_SIZEKB,$SHEET_NAME) ;
				$DL_FIELD[$row_t-1]        = &xls_cell_value($sheet, $row, $COLUMN_DL,$SHEET_NAME) ;
				$TYPE_FIELD[$row_t -1]		 = &xls_cell_value($sheet, $row, $COLUMN_TYPE,$SHEET_NAME) ;
				$FB_DL_FIELD[$row_t-1]    = &xls_cell_value($sheet, $row, $COLUMN_FB_DL,$SHEET_NAME) ;
		 		$FB_ERASE_FIELD[$row_t-1]    = &xls_cell_value($sheet, $row, $COLUMN_FB_ERASE,$SHEET_NAME) ;
				$DL_TYPE[$row_t-1]    = &xls_cell_value($sheet, $row, $COLUMN_DT,$SHEET_NAME) ;
				$OTA_FIELD[$row_t-1]        = &xls_cell_value($sheet, $row, $COLUMN_OTA,$SHEET_NAME) ;

				if($SPI_NAND_SUPPORT eq "yes" && $pt_name eq "PRELOADER")
				{
					if($SIZE_FIELD_KB[$row_t -1] != 1024)
					{
						my $error_msg="ERROR:Ptgen Preloader size must be 1024KB on SPI-NAND, please modify partition table!\n";
						print $error_msg;
						die $error_msg;
					}
				}
				$row_t++;
			}
		}
		$row++;

		$pt_name = &xls_cell_value($sheet, $row, $COLUMN_PARTITION,$SHEET_NAME);
		# remove leading and tailing spaces @TT
		$pt_name =~ s/^\s+// ;
		$pt_name =~ s/\s+$// ;
	}
	print Dumper(@GPT);
	&check_boardconfig_partition_info();
#init start_address of partition
	$START_FIELD_Byte[0] = 0;
	$PARTITION_IDX_FIELD[0] = 0;
	my $otp_row;
	my $reserve_size;
	my $first_user = 1;
	my $page;
	my $page_p_b;
	my $zero_size_idx = -1;
	my $Total_Size = get_chip_size() * 1024 * 1024;
	($page, $page_p_b) =  get_page_size();
	my $PEB = $page * $page_p_b;
	my $idx;
	my $LOW_PAGE_RATIO = 2;
	my $BMT_POOL = int($Total_Size / $PEB / 100) * 6 * $PEB;
	printf "Total_size $Total_Size\n";
	printf "BMT pool size $BMT_POOL REB= $PEB\n";
	#PMT need 2 block
	$Total_Size -= $PEB*2;
	if ($MLC_NAND_SUPPORT eq "yes") {
		$LOW_PAGE_RATIO = 2;
	} elsif ($TLC_NAND_SUPPORT eq "yes") { #FIXME
		$LOW_PAGE_RATIO = 3;
	}
	for($row=0;$row < @PARTITION_FIELD;$row++){
		$idx = part_grp($PARTITION_FIELD[$row]);
		if($SIZE_FIELD_KB[$row] == 0) {
			$zero_size_idx = $row;
		}
		if(($PARTITION_FIELD[$row] eq "EFUSE") && ($EFUSE_WRITER_SUPPORT ne "yes")){
			splice @PARTITION_FIELD, $row, 1;
			splice @SIZE_FIELD_KB, $row, 1;
			splice @DL_FIELD, $row, 1;
			splice @TYPE_FIELD, $row, 1;
			splice @FB_DL_FIELD, $row, 1;
			splice @FB_ERASE_FIELD, $row, 1;
			splice @DL_TYPE, $row, 1;
			splice @OTA_FIELD, $row, 1;
		}
		if(($PARTITION_FIELD[$row] eq "SPMFW") && ($SPM_FW_USE_PARTITION ne "yes")){
			splice @PARTITION_FIELD, $row, 1;
			splice @SIZE_FIELD_KB, $row, 1;
			splice @DL_FIELD, $row, 1;
			splice @TYPE_FIELD, $row, 1;
			splice @FB_DL_FIELD, $row, 1;
			splice @FB_ERASE_FIELD, $row, 1;
			splice @DL_TYPE, $row, 1;
			splice @OTA_FIELD, $row, 1;
		}
		if (($PARTITION_FIELD[$row] eq "MCUPMFW") && ($MCUPM_FW_USE_PARTITION ne "yes")){
			splice @PARTITION_FIELD, $row, 1;
			splice @SIZE_FIELD_KB, $row, 1;
			splice @DL_FIELD, $row, 1;
			splice @TYPE_FIELD, $row, 1;
			splice @FB_DL_FIELD, $row, 1;
			splice @FB_ERASE_FIELD, $row, 1;
			splice @DL_TYPE, $row, 1;
			splice @OTA_FIELD, $row, 1;
		}
		if (($PARTITION_FIELD[$row] eq "ODMDTBO") && ($MTK_DTBO_FEATURE ne "yes")){
			splice @PARTITION_FIELD, $row, 1;
			splice @SIZE_FIELD_KB, $row, 1;
			splice @DL_FIELD, $row, 1;
			splice @TYPE_FIELD, $row, 1;
			splice @FB_DL_FIELD, $row, 1;
			splice @FB_ERASE_FIELD, $row, 1;
			splice @DL_TYPE, $row, 1;
			splice @OTA_FIELD, $row, 1;
		}
		if($PARTITION_FIELD[$row] eq "BMTPOOL"){
			$START_FIELD_Byte[$row] = $SIZE_FIELD_KB[$row]*1024;
			$SIZE_FIELD_KB[$row] = $BMT_POOL / 1024;
			$Total_Size -= $BMT_POOL;
			next;
		}
#		if($PARTITION_FIELD[$row] eq "BMTPOOL" || $PARTITION_FIELD[$row] eq "OTP"){
#		#	$START_FIELD_Byte[$row] = &xls_cell_value($sheet, $row+1, $COLUMN_START,$SHEET_NAME);
#			$START_FIELD_Byte[$row] = $SIZE_FIELD_KB[$row]*1024;
#			$Total_Size -= $SIZE_FIELD_KB[$row]*1024;
#			if($PARTITION_FIELD[$row] eq "OTP"){
#					$otp_row = $row;
#				}
#			next;
#		}
		if($DL_TYPE[$row] eq "LOW_PAGE") {
			$Total_Size -= $SIZE_FIELD_KB[$row]*1024*$LOW_PAGE_RATIO;
		} else {
			$Total_Size -= $SIZE_FIELD_KB[$row]*1024;
			$tlc_android[$idx] += $SIZE_FIELD_KB[$row]*1024;
		}
		if($row == 0) {
			next;
		}
		if($PARTITION_FIELD[$row-1] eq "CACHE"){
			if($MNTL_SUPPORT ne "yes") {
				$CACHE_SIZE_KB = $SIZE_FIELD_KB[$row-1];
				$START_FIELD_Byte[$row] = $START_FIELD_Byte[$row-2]+$SIZE_FIELD_KB[$row-2]*1024;
			}
			next;
		}
		if($DL_TYPE[$row-1] eq "LOW_PAGE") {
			$START_FIELD_Byte[$row] = $START_FIELD_Byte[$row-1]+$SIZE_FIELD_KB[$row-1]*1024*$LOW_PAGE_RATIO;
		} else {
			$START_FIELD_Byte[$row] = $START_FIELD_Byte[$row-1]+$SIZE_FIELD_KB[$row-1]*1024;
		}
	}
	if($zero_size_idx != -1) {
		$idx = part_grp($PARTITION_FIELD[$zero_size_idx]);
		$SIZE_FIELD_KB[$zero_size_idx] = $Total_Size/1024;
		$tlc_android[$idx] += $SIZE_FIELD_KB[$zero_size_idx]*1024;
		print "size of $PARTITION_FIELD[$zero_size_idx] is $SIZE_FIELD_KB[$zero_size_idx] KB / $tlc_android[$idx] KB\n";
	}
#convert dec start_address to hex start_address
	$START_FIELD_Byte_HEX[0]=0;
	for($row=1;$row < @PARTITION_FIELD;$row++){
		if($PARTITION_FIELD[$row] eq "BMTPOOL" || $PARTITION_FIELD[$row] eq "OTP"){
			$START_FIELD_Byte_HEX[$row] = sprintf("FFFF%04x",$START_FIELD_Byte[$row]/($page_p_b*$Page_Size*1024));#$START_FIELD_Byte[$row];
		}else{
			$START_FIELD_Byte_HEX[$row] = sprintf("%x",$START_FIELD_Byte[$row]);
		}
	}

	if($DebugPrint eq 1){
		for($row=0;$row < @PARTITION_FIELD;$row++){
			print "START=0x$START_FIELD_Byte_HEX[$row],	Partition=$PARTITION_FIELD[$row],	SIZE=$SIZE_FIELD_KB[$row],	DL_=$DL_FIELD[$row]" ;
			print "\n";
		}

	}

    $total_rows = @PARTITION_FIELD ;

	if ($total_rows == 0)
    {
        die "error in excel file no data!\n" ;
    }
    print "There are $total_rows Partition totally!.\n" ;
}
#****************************************************************************
# subroutine:  GenHeaderFile
# return:
#****************************************************************************
sub GenHeaderFile ()
{
    my $iter = 0 ;
    my $temp ;
	my $t;
	my $partition_define_h = &open_for_rw($PARTITION_DEFINE_H_NAME);


#write header
    print $partition_define_h "\n#ifndef __PARTITION_DEFINE_H__\n#define __PARTITION_DEFINE_H__\n\n" ;
    print $partition_define_h "\n\n\n#define KB  (1024)\n#define MB  (1024 * KB)\n#define GB  (1024 * MB)\n\n" ;
#write part_name define
 	for ($iter=0; $iter< $total_rows; $iter++){
		$temp = "#define PART_$PARTITION_FIELD[$iter] \"$PARTITION_FIELD[$iter]\" \n";
		print $partition_define_h $temp ;
 	}
#preloader re-name
	print $partition_define_h "/*preloader re-name*/\n";
	for ($iter=0; $iter< $total_rows; $iter++){
		if($preloader_alias{$PARTITION_FIELD[$iter]}){
			$temp = "#define PART_$preloader_alias{$PARTITION_FIELD[$iter]} \"$preloader_alias{$PARTITION_FIELD[$iter]}\" \n";
			print $partition_define_h $temp ;
		}
	}
#Uboot re-name
	print $partition_define_h "/*Uboot re-name*/\n";
	for ($iter=0; $iter< $total_rows; $iter++){
		if($lk_xmodule_alias{$PARTITION_FIELD[$iter]}&&($lk_xmodule_alias{$PARTITION_FIELD[$iter]} ne $preloader_alias{$PARTITION_FIELD[$iter]})){
			$temp = "#define PART_$lk_xmodule_alias{$PARTITION_FIELD[$iter]} \"$lk_xmodule_alias{$PARTITION_FIELD[$iter]}\" \n";
			print $partition_define_h $temp ;
		}
	}
    print $partition_define_h "\n#define PART_FLAG_NONE              0 \n";
    print $partition_define_h "#define PART_FLAG_LEFT             0x1 \n";
    print $partition_define_h "#define PART_FLAG_END              0x2 \n";
    print $partition_define_h "#define PART_MAGIC              0x58881688 \n\n";
    for ($iter=0; $iter< $total_rows; $iter++)
    {
        if($PARTITION_FIELD[$iter] eq "BMTPOOL")
        {
        		if($COMBO_NAND_SUPPORT eq "yes")
        		{
			my $page;
			my $p_per_block;
			($page, $p_per_block) =  get_page_size();
        			# TODO: Max BMT count is 0x80.
			my $bmtpool=$SIZE_FIELD_KB[$iter]/$p_per_block/($page/1024);   # 2Kx64Page
        			if($bmtpool > 128)
        			{
								my $error_msg="ERROR:Ptgen BMT block count > 128, please decrease BMTPOOL size $page $SIZE_FIELD_KB[$iter] $bmtpool $p_per_block\n";
								print $error_msg;
								die $error_msg;
        			}
			    		$temp = "#define PART_SIZE_$PARTITION_FIELD[$iter]\t\t\t($SIZE_FIELD_KB[$iter]*KB)\n" ;
							print $partition_define_h $temp ;
        		}
        		else
        		{
			my $page;
			my $p_per_block;
			($page, $p_per_block) =  get_page_size();
			my $bmtpool=sprintf("%x",$SIZE_FIELD_KB[$iter]/$p_per_block/($page/1024));
							$temp = "#define PART_SIZE_$PARTITION_FIELD[$iter]\t\t\t(0x$bmtpool)\n" ;
			    		print $partition_define_h $temp ;
				    }
        }else
        {
    		$temp = "#define PART_SIZE_$PARTITION_FIELD[$iter]\t\t\t($SIZE_FIELD_KB[$iter]*KB)\n" ;
			print $partition_define_h $temp ;
        }
	#if($PARTITION_FIELD[$iter] eq "SECCFG" || $PARTITION_FIELD[$iter] eq "SEC_RO"){
		$temp = "#define PART_OFFSET_$PARTITION_FIELD[$iter]\t\t\t(0x$START_FIELD_Byte_HEX[$iter])\n";
		print $partition_define_h $temp ;
	#}

    }
    for ($iter=0; $iter< $total_rows; $iter++)
    {
	    if($PARTITION_FIELD[$iter] eq "PRELOADER")
			{
				my $page;
				my $page_p_b;
				($page, $page_p_b) =  get_page_size();
				$page_p_b = $SIZE_FIELD_KB[$iter]/$page*1024;
				$temp = "#ifndef RAND_START_ADDR\n#define RAND_START_ADDR   $page_p_b\n#endif\n";
				print $partition_define_h $temp ;
	}
    }

    print $partition_define_h "\n\n#define PART_NUM\t\t\t$total_rows\n\n";
    print $partition_define_h "\n\n#define PART_MAX_COUNT\t\t\t 40\n\n";
	print $partition_define_h "#define MBR_START_ADDRESS_BYTE\t\t\t($MBR_Start_Address_KB*KB)\n\n";
	if($EMMC_SUPPORT eq "yes"){
		print $partition_define_h "#define WRITE_SIZE_Byte		512\n";
	}elsif($COMBO_NAND_SUPPORT ne "yes") {
		print $partition_define_h "#define WRITE_SIZE_Byte		($Page_Size*1024)\n";
	}
	my $ExcelStruct = <<"__TEMPLATE";
typedef enum  {
	EMMC = 1,
	NAND = 2,
} dev_type;

typedef enum {
	USER = 0,
	BOOT_1,
	BOOT_2,
	RPMB,
	GP_1,
	GP_2,
	GP_3,
	GP_4,
} Region;


struct excel_info{
	char * name;
	unsigned long long size;
	unsigned long long start_address;
	dev_type type ;
	unsigned int partition_idx;
	Region region;
};
#if defined(MTK_EMMC_SUPPORT) || defined(CONFIG_MTK_EMMC_SUPPORT)
/*MBR or EBR struct*/
#define SLOT_PER_MBR 4
#define MBR_COUNT 8

struct MBR_EBR_struct{
	char part_name[8];
	int part_index[SLOT_PER_MBR];
};

extern struct MBR_EBR_struct MBR_EBR_px[MBR_COUNT];
#endif
__TEMPLATE

	print $partition_define_h $ExcelStruct;
	print $partition_define_h "extern struct excel_info PartInfo[PART_NUM];\n";
	print $partition_define_h "\n\n#endif\n" ;
   	close $partition_define_h ;

	my $partition_define_c = &open_for_rw($PARTITION_DEFINE_C_NAME);
	print  $partition_define_c "#include <linux/module.h>\n";
	print  $partition_define_c "#include \"partition_define.h\"\n";
	print  $partition_define_c "struct excel_info PartInfo[PART_NUM]={\n";

	for ($iter=0; $iter<$total_rows; $iter++)
    {
    	$t = lc($PARTITION_FIELD[$iter]);
		$temp = "\t\t\t{\"$t\",";
		$t = ($SIZE_FIELD_KB[$iter])*1024;
		$temp .= "$t,0x$START_FIELD_Byte_HEX[$iter]";

		if($EMMC_SUPPORT eq "yes"){
			$temp .= ", EMMC, $PARTITION_IDX_FIELD[$iter],$REGION_FIELD[$iter]";
		}else{
			$temp .= ", NAND";
		}
		$temp .= "},\n";
		print $partition_define_c $temp;
	}
	print $partition_define_c " };\n";
	print $partition_define_c "EXPORT_SYMBOL(PartInfo);\n";
#generate MBR struct
	print $partition_define_c "\n#if defined(MTK_EMMC_SUPPORT) || defined(CONFIG_MTK_EMMC_SUPPORT)\n";
	print $partition_define_c "struct MBR_EBR_struct MBR_EBR_px[MBR_COUNT]={\n";
	my $iter_p=0;
	my $iter_c = @BR_INDEX;
	print "BR COUNT is $iter_c $BR_INDEX[$iter_c-1]\n";
	for($iter_p=0;$iter_p<=$BR_INDEX[$iter_c-1];$iter_p++){
		if($iter_p ==0){
			print $partition_define_c "\t{\"mbr\", {";
		}else{
			print $partition_define_c "\t{\"ebr$iter_p\", {";
		}
		for ($iter=1; $iter<$iter_c; $iter++){
			if($iter == 1){
				$BR_INDEX[$iter] = 0;
			}
			if($iter_p == $BR_INDEX[$iter]){
				print $partition_define_c "$iter, ";
			}
		}
		print $partition_define_c "}},\n";
	}
	print $partition_define_c "};\n\n";
	print $partition_define_c "EXPORT_SYMBOL(MBR_EBR_px);\n";
	print $partition_define_c "#endif\n\n";
   	close $partition_define_c ;
}
#****************************************************************************
# subroutine:  GenScatFile
# return:
#****************************************************************************
sub GenScatFile ()
{

}
sub GenYAMLScatFile(){
	my $iter = 0 ;
	my $scatter_fd=&open_for_rw($SCAT_NAME);
	my %fileHash=(
		PRELOADER=>"preloader_$ENV{PRELOADER_TARGET}.bin",
		DSP_BL=>"DSP_BL",
		SRAM_PRELD=>"sram_preloader_$CUSTOM_BASEPROJECT.bin",
		MEM_PRELD=>"mem_preloader_$CUSTOM_BASEPROJECT.bin",
		UBOOT=>"lk.img",
		UBOOT2=>"lk.img",
		LOADER_EXT1=>"loader_ext.img",
		LOADER_EXT2=>"loader_ext.img",
		MNB=>"mnb.img",
		BOOTIMG=>"boot.img",
		RECOVERY=>"recovery.img",
		SEC_RO=>"secro.img",
		LOGO=>"logo.bin",
		ODMDTBO=>"odmdtbo.img",
		EFUSE=>"efuse.img",
		MD1IMG=>"md1img.img",
		MD1DSP=>"md1dsp.img",
		SPMFW=>"spmfw.img",
		MCUPMFW=>"mcupmfw.img",
		TEE1=>"tee.img",
		TEE2=>"tee.img",
		ANDROID=>"system.img",
		CACHE=>"cache.img",
		USRDATA=>($MNTL_SUPPORT eq "yes")?"mntl.img":"userdata.img",
		CUSTOM=>"custom.img"
	);
	my %sepcial_operation_type=(
		PRELOADER=>"BOOTLOADERS",
		DSP_BL=>"BOOTLOADERS",
		NVRAM=>"BINREGION",
		PRO_INFO=>"PROTECTED",
		PROTECT_F=>"PROTECTED",
		PROTECT_S=>"PROTECTED",
		OTP=>"RESERVED",
		PMT=>"RESERVED",
		BMTPOOL=>"RESERVED",
	);
	my %protect=(PRO_INFO=>"TRUE",NVRAM=>"TRUE",PROTECT_F=>"TRUE",PROTECT_S=>"TRUE",FAT=>"KEEPVISIBLE",FAT=>"INVISIBLE",BMTPOOL=>"INVISIBLE");
	my %Scatter_Info={};
	for ($iter=0; $iter<$total_rows; $iter++){
		$Scatter_Info{$PARTITION_FIELD[$iter]}={partition_index=>$iter,physical_start_addr=>sprintf("0x%x",$START_ADDR_PHY_Byte_DEC[$iter]),linear_start_addr=>"0x$START_FIELD_Byte_HEX[$iter]",partition_size=>sprintf("0x%x",${SIZE_FIELD_KB[$iter]}*1024)};

		if(exists $fileHash{$PARTITION_FIELD[$iter]}){
			$Scatter_Info{$PARTITION_FIELD[$iter]}{file_name}=$fileHash{$PARTITION_FIELD[$iter]};
		}else{
			$Scatter_Info{$PARTITION_FIELD[$iter]}{file_name}="NONE";
		}

		if($PARTITION_FIELD[$iter]=~/MBR/ || $PARTITION_FIELD[$iter]=~/EBR/){
			$Scatter_Info{$PARTITION_FIELD[$iter]}{file_name}=$PARTITION_FIELD[$iter];
		}

		if($DL_FIELD[$iter] == 0){
			$Scatter_Info{$PARTITION_FIELD[$iter]}{type}="NONE";
		}else{
			if($EMMC_SUPPORT eq "yes"){
			  if($TYPE_FIELD[$iter] eq "Raw data"){
			    $Scatter_Info{$PARTITION_FIELD[$iter]}{type}="NORMAL_ROM";
			  }
			  else{
			    $Scatter_Info{$PARTITION_FIELD[$iter]}{type}="YAFFS_IMG";
			  }
			}
			else{
			  if($TYPE_FIELD[$iter] eq "Raw data"){
  				$Scatter_Info{$PARTITION_FIELD[$iter]}{type}="NORMAL_ROM";
	  		}else{
		  		if($MTK_NAND_UBIFS_SUPPORT eq "yes" || $MTK_NAND_MTK_FTL_SUPPORT eq "yes"){
			  		$Scatter_Info{$PARTITION_FIELD[$iter]}{type}="UBI_IMG";
  				}else{
  					if($MNTL_SUPPORT eq "yes"){
  						$Scatter_Info{$PARTITION_FIELD[$iter]}{type}="FTL20_IMG";
  					} else {
						$Scatter_Info{$PARTITION_FIELD[$iter]}{type}="YAFFS_IMG";
					}
				}
			  }
			}
		}
		if($PARTITION_FIELD[$iter]=~/MBR/ || $PARTITION_FIELD[$iter]=~/EBR/){
			#$Scatter_Info{$PARTITION_FIELD[$iter]}{type}="MBR_BIN";
		}
		if($PARTITION_FIELD[$iter]=~/PRELOADER/ || $PARTITION_FIELD[$iter]=~/DSP_BL/)
		{
			$Scatter_Info{$PARTITION_FIELD[$iter]}{type}="SV5_BL_BIN";
		}
		if(exists $sepcial_operation_type{$PARTITION_FIELD[$iter]}){
			$Scatter_Info{$PARTITION_FIELD[$iter]}{operation_type}=$sepcial_operation_type{$PARTITION_FIELD[$iter]};
		}else{
			if($DL_FIELD[$iter] == 0){
				$Scatter_Info{$PARTITION_FIELD[$iter]}{operation_type}="INVISIBLE";
			}else{
				$Scatter_Info{$PARTITION_FIELD[$iter]}{operation_type}="UPDATE";
			}
		}
		if($EMMC_SUPPORT eq "yes"){
			$Scatter_Info{$PARTITION_FIELD[$iter]}{region}="EMMC_$REGION_FIELD[$iter]";
		}else{
			$Scatter_Info{$PARTITION_FIELD[$iter]}{region}="NONE";
		}

		if($EMMC_SUPPORT eq "yes"){
			$Scatter_Info{$PARTITION_FIELD[$iter]}{storage}="HW_STORAGE_EMMC";
		}else{
			$Scatter_Info{$PARTITION_FIELD[$iter]}{storage}="HW_STORAGE_NAND";
		}

		if($EMMC_SUPPORT eq "yes"){
			$Scatter_Info{$PARTITION_FIELD[$iter]}{dtype}="FALSE";
		}else{
			$Scatter_Info{$PARTITION_FIELD[$iter]}{dtype}=$DL_TYPE[$iter];
		}

		if($PARTITION_FIELD[$iter]=~/BMTPOOL/ || $PARTITION_FIELD[$iter]=~/OTP/){
			$Scatter_Info{$PARTITION_FIELD[$iter]}{boundary_check}="false";
		}else{
			$Scatter_Info{$PARTITION_FIELD[$iter]}{boundary_check}="true";
		}

		if ($DL_FIELD[$iter] == 0){
			$Scatter_Info{$PARTITION_FIELD[$iter]}{is_download}="false";
		}else{
			$Scatter_Info{$PARTITION_FIELD[$iter]}{is_download}="true";
		}

		if ($OTA_FIELD[$iter] == 0){
			$Scatter_Info{$PARTITION_FIELD[$iter]}{is_upgradable}="false";
		}else{
			$Scatter_Info{$PARTITION_FIELD[$iter]}{is_upgradable}="true";
		}

		if($PARTITION_FIELD[$iter]=~/BMTPOOL/ || $PARTITION_FIELD[$iter]=~/OTP/){
			$Scatter_Info{$PARTITION_FIELD[$iter]}{is_reserved}="true";
		}else{
			$Scatter_Info{$PARTITION_FIELD[$iter]}{is_reserved}="false";
		}

		if($MTK_SHARED_SDCARD eq "yes" && $PARTITION_FIELD[$iter] =~ /USRDATA/){
			$PMT_END_NAME = $PARTITION_FIELD[$iter];
		}elsif($PARTITION_FIELD[$iter] =~ /FAT/){
			$PMT_END_NAME = $PARTITION_FIELD[$iter];
		}
	}
my $Head1 = <<"__TEMPLATE";
############################################################################################################
#
#  General Setting
#
############################################################################################################
__TEMPLATE

my $Head2=<<"__TEMPLATE";
############################################################################################################
#
#  Layout Setting
#
############################################################################################################
__TEMPLATE

	my ${FirstDashes}="- ";
	my ${FirstSpaceSymbol}="  ";
	my ${SecondSpaceSymbol}="      ";
	my ${SecondDashes}="    - ";
	my ${colon}=": ";
	my $page;
	my $p_b_block;
	($page, $PAGE_PER_BLOCK) = get_page_size();
	print $scatter_fd $Head1;
	print $scatter_fd "${FirstDashes}general${colon}MTK_PLATFORM_CFG\n";
	print $scatter_fd "${FirstSpaceSymbol}info${colon}\n";
	print $scatter_fd "${SecondDashes}config_version${colon}V1.1.5\n";
	print $scatter_fd "${SecondSpaceSymbol}platform${colon}${PLATFORM}\n";
	print $scatter_fd "${SecondSpaceSymbol}project${colon}${PROJECT}\n";
	if($EMMC_SUPPORT eq "yes"){
		print $scatter_fd "${SecondSpaceSymbol}storage${colon}EMMC\n";
		print $scatter_fd "${SecondSpaceSymbol}boot_channel${colon}MSDC_0\n";
		printf $scatter_fd ("${SecondSpaceSymbol}block_size${colon}0x%x\n",2*64*1024);
	}else{
		print $scatter_fd "${SecondSpaceSymbol}storage${colon}NAND\n";
		print $scatter_fd "${SecondSpaceSymbol}boot_channel${colon}NONE\n";
		printf $scatter_fd ("${SecondSpaceSymbol}block_size${colon}0x%x\n",${page}*${PAGE_PER_BLOCK});
	}
	print $scatter_fd $Head2;
	if($TLC_NAND_SUPPORT eq  "yes"){
                $PEB = $page*$PAGE_PER_BLOCK;
		for (my $i=0; $i<2; $i++){
			$slc_blk[$i]=int((($tlc_android[$i]/$PEB)*$MTK_TLC_SLC_RATIO[$i])/100);
			if(($slc_blk[$i]%3) > 0)
			{
				$slc_blk[$i] += (3-$slc_blk[$i]%3);
			}
			$tlc_blk[$i]=($tlc_android[$i]/$PEB)-$slc_blk[$i];
			$slc_blk[$i]= $slc_blk[$i]/3;
			$tlc_android[$i]=($tlc_blk[$i]+$slc_blk[$i])*$PEB;
			print "[Linger]tlc_android $tlc_android[$i], ratio $MTK_TLC_SLC_RATIO[$i], tlc_blk $tlc_blk[$i], slc_blk $slc_blk[$i], PEB $PEB\n";
		}
        } elsif ($MLC_NAND_SUPPORT eq "yes"){
                $PEB = $page*$PAGE_PER_BLOCK;
		for (my $i=0; $i<2; $i++){
			$slc_blk[$i]=int((($tlc_android[$i]/$PEB)*$MTK_TLC_SLC_RATIO[$i])/100);
			if(($slc_blk[$i]%2) > 0)
			{
				$slc_blk[$i] += (2-$slc_blk[$i]%2);
			}
			$tlc_blk[$i]=($tlc_android[$i]/$PEB)-$slc_blk[$i];
			$slc_blk[$i]= $slc_blk[$i]/2;
			$tlc_android[$i]=($tlc_blk[$i]+$slc_blk[$i])*$PEB;
			print "[Linger]tlc_android $tlc_android[$i], ratio $MTK_TLC_SLC_RATIO[$i], tlc_blk $tlc_blk[$i], slc_blk $slc_blk[$i], PEB $PEB\n";
		}
        }
	for ($iter=0; $iter<$total_rows; $iter++){
		my $slc_percentage = 0;
		if($MLC_NAND_SUPPORT eq "yes" || $TLC_NAND_SUPPORT eq  "yes"){
                        if($PARTITION_FIELD[$iter] eq "ANDROID") {
                                $Scatter_Info{$PARTITION_FIELD[$iter]}{partition_size} = sprintf("0x%x",$tlc_android[1]);
				$slc_percentage = $MTK_TLC_SLC_RATIO[1];
			} elsif ($PARTITION_FIELD[$iter] eq "USRDATA") {
                                $Scatter_Info{$PARTITION_FIELD[$iter]}{partition_size} = sprintf("0x%x",$tlc_android[0]);
				$slc_percentage = $MTK_TLC_SLC_RATIO[0];
                        } elsif ($Scatter_Info{$PARTITION_FIELD[$iter]}{dtype} eq "FULL_PAGE") {
                                print("[Linger scatter]skip FULL_PAGE DL $PARTITION_FIELD[$iter]\n");
                                next;
                        }
                        if($PARTITION_FIELD[$iter] eq "BMTPOOL")
                        {
                                $Scatter_Info{$PARTITION_FIELD[$iter]}{partition_index} -= 2;
                                $Scatter_Info{$PARTITION_FIELD[$iter]}{physical_start_addr} = sprintf("0x%x",$Total_Size);
                        }
                }
		print $scatter_fd "${FirstDashes}partition_index${colon}SYS$Scatter_Info{$PARTITION_FIELD[$iter]}{partition_index}\n";
		print $scatter_fd "${FirstSpaceSymbol}partition_name${colon}${PARTITION_FIELD[$iter]}\n";
		print $scatter_fd "${FirstSpaceSymbol}file_name${colon}$Scatter_Info{$PARTITION_FIELD[$iter]}{file_name}\n";
		print $scatter_fd "${FirstSpaceSymbol}is_download${colon}$Scatter_Info{$PARTITION_FIELD[$iter]}{is_download}\n";
		print $scatter_fd "${FirstSpaceSymbol}type${colon}$Scatter_Info{$PARTITION_FIELD[$iter]}{type}\n";
		print $scatter_fd "${FirstSpaceSymbol}linear_start_addr${colon}$Scatter_Info{$PARTITION_FIELD[$iter]}{linear_start_addr}\n";
		print $scatter_fd "${FirstSpaceSymbol}physical_start_addr${colon}$Scatter_Info{$PARTITION_FIELD[$iter]}{linear_start_addr}\n";
		print $scatter_fd "${FirstSpaceSymbol}partition_size${colon}$Scatter_Info{$PARTITION_FIELD[$iter]}{partition_size}\n";
		print $scatter_fd "${FirstSpaceSymbol}region${colon}$Scatter_Info{$PARTITION_FIELD[$iter]}{region}\n";
		print $scatter_fd "${FirstSpaceSymbol}storage${colon}$Scatter_Info{$PARTITION_FIELD[$iter]}{storage}\n";
		print $scatter_fd "${FirstSpaceSymbol}boundary_check${colon}$Scatter_Info{$PARTITION_FIELD[$iter]}{boundary_check}\n";
		print $scatter_fd "${FirstSpaceSymbol}is_reserved${colon}$Scatter_Info{$PARTITION_FIELD[$iter]}{is_reserved}\n";
		print $scatter_fd "${FirstSpaceSymbol}operation_type${colon}$Scatter_Info{$PARTITION_FIELD[$iter]}{operation_type}\n";
		print $scatter_fd "${FirstSpaceSymbol}is_upgradable${colon}$Scatter_Info{$PARTITION_FIELD[$iter]}{is_upgradable}\n";
		print $scatter_fd "${FirstSpaceSymbol}d_type${colon}$Scatter_Info{$PARTITION_FIELD[$iter]}{dtype}\n";
		print $scatter_fd "${FirstSpaceSymbol}slc_percentage${colon}${slc_percentage}\n";
		print $scatter_fd "${FirstSpaceSymbol}reserve${colon}0x00\n\n";
	}
	close $scatter_fd;
}

#****************************************************************************************
# subroutine:  GenPartSizeFile;
# return:
#****************************************************************************************

sub GenPartSizeFile
{
	my $part_size_fh = &open_for_rw($PART_SIZE_LOCATION);
    my $part_xml_fh;
	my $Total_Size=512*1024*1024; #Hard Code 512MB for 4+2 project FIX ME!!!!!
	my $temp;
	my $index=0;
	my $vol_size;
	my $fat_size;
	my $ipoh_size;
	my $cache_size;
	my $min_ubi_vol_size;
	my $page;
	my $page_p_b;
	my %PSalias=(
		SEC_RO=>SECRO,
		ANDROID=>SYSTEM,
		USRDATA=>USERDATA,
	);
	my %ubialias=(
		SEC_RO=>SECRO,
		ANDROID=>SYSTEM,
		USRDATA=>USERDATA,
	);
	my %MNTL_img =(
		mntlblk_d1=>"system.img",
		mntlblk_d2=>"vendor.img",
		VENDOR=>"vendor.img",
		cache=>"cache.img",
		userdata=>"userdata.img",
		custom=>"custom.img"
	);
	my $PEB;
	my $LEB;
	my $IOSIZE;
	if($MLC_NAND_SUPPORT eq "yes" || $TLC_NAND_SUPPORT eq  "yes")
	{
		$Total_Size = get_chip_size();
		$Total_Size = $Total_Size*1024*1024;
	}
	if($MTK_NAND_UBIFS_SUPPORT eq "yes"){
		($page, $PAGE_PER_BLOCK) =  get_page_size();
		$IOSIZE=$page;
		$PEB=$IOSIZE*$PAGE_PER_BLOCK;
		$LEB=$IOSIZE*($PAGE_PER_BLOCK-2);
		$min_ubi_vol_size = $PEB*28;
		print "PEB=$PEB, LEB=$LEB,IOSIZE=$IOSIZE,PAGE_PER_BLOCK=$PAGE_PER_BLOCK \n" ;
		printf $part_size_fh ("BOARD_UBIFS_MIN_IO_SIZE:=%d\n",$IOSIZE);
		printf $part_size_fh ("BOARD_FLASH_BLOCK_SIZE:=%d\n",$PEB);
		printf $part_size_fh ("BOARD_UBIFS_VID_HDR_OFFSET:=%d\n",$IOSIZE);
		printf $part_size_fh ("BOARD_UBIFS_LOGICAL_ERASEBLOCK_SIZE:=%d\n",$LEB);

		if($COMBO_NAND_SUPPORT eq "yes") {
			my $combo_nand_h_fd=&open_for_rw($COMBO_NAND_KERNELH);
			printf $combo_nand_h_fd ("#define COMBO_NAND_BLOCK_SIZE %d\n", $PEB);
			printf $combo_nand_h_fd ("#define COMBO_NAND_PAGE_SIZE %d\n", $IOSIZE);
    		close $combo_nand_h_fd;
		}
	}
	if($MTK_NAND_MTK_FTL_SUPPORT eq "yes"){
		($page, $PAGE_PER_BLOCK) =  get_page_size();
		$IOSIZE=$page;
		$PEB=$IOSIZE*$PAGE_PER_BLOCK;
		$LEB=$IOSIZE*($PAGE_PER_BLOCK-2);
		$min_ubi_vol_size = $PEB*28;
		print "PEB=$PEB, LEB=$LEB,IOSIZE=$IOSIZE,PAGE_PER_BLOCK=$PAGE_PER_BLOCK \n" ;
		printf $part_size_fh ("BOARD_MTFTL_MIN_IO_SIZE:=%d\n",$IOSIZE);
		printf $part_size_fh ("BOARD_FLASH_BLOCK_SIZE:=%d\n",$PEB);
		printf $part_size_fh ("BOARD_MTFTL_VID_HDR_OFFSET:=%d\n",$IOSIZE);
		printf $part_size_fh ("BOARD_MTFTL_LOGICAL_ERASEBLOCK_SIZE:=%d\n",$LEB);

		if($COMBO_NAND_SUPPORT eq "yes") {
			my $combo_nand_h_fd=&open_for_rw($COMBO_NAND_KERNELH);
			printf $combo_nand_h_fd ("#define COMBO_NAND_BLOCK_SIZE %d\n", $PEB);
			printf $combo_nand_h_fd ("#define COMBO_NAND_PAGE_SIZE %d\n", $IOSIZE);
			close $combo_nand_h_fd;
		}
	}
	if($MNTL_SUPPORT eq "yes"){
		$part_xml_fh = &open_for_rw($PART_XML_LOCATION);	
		($page, $PAGE_PER_BLOCK) =  get_page_size();
		$IOSIZE=16384;
		$USERDATA_PARTITION_SIZE_KB = 2048 * 1024;
		$USERDATA_SIZE_KB = $USERDATA_PARTITION_SIZE_KB * 8/10;
		$SECTOR_SIZE = 4096;
		$PEB=$IOSIZE*$PAGE_PER_BLOCK;
		printf $part_size_fh ("BOARD_FLASH_BLOCK_SIZE:=%d\n",$PEB);
		printf $part_size_fh ("BOARD_FLASH_PAGE_SIZE:=%d\n",$IOSIZE);
		printf $part_size_fh ("BOARD_SLC_RATIO:=%d\n",$MTK_TLC_SLC_RATIO);

		for($i = 0 ; $i < @GPT ; $i++) {
			$size = ($GPT[$i]{"END_SECTOR"} - $GPT[$i]{"START_SECTOR"} + 1) * $SECTOR_SIZE;
			$name = "BOARD_".$GPT[$i]{"NAME"}."IMAGE_";
			$vdr_name = lc($GPT[$i]{"NAME"});
			printf $part_size_fh ($name."PARTITION_SIZE:=%d\n",$size);
			printf $part_size_fh ($name."START_SECTOR:=%d\n",$GPT[$i]{"START_SECTOR"});
			printf $part_size_fh ($name."END_SECTOR:=%d\n",$GPT[$i]{"END_SECTOR"});
			if (exists $mountPointMapList{$vdr_name}) {
				if ($mountPointMapList{$vdr_name} ne "") {
					printf $part_size_fh ("MTK_BOARD_ROOT_EXTRA_FOLDERS += %s\n", $mountPointMapList{$vdr_name});
				}
			} else {
				printf $part_size_fh ("MTK_BOARD_ROOT_EXTRA_FOLDERS += %s\n", $vdr_name);
			}
			$TOTAL_SECTORS= $GPT[$i]{"END_SECTOR"} + 4;
		}
		printf $part_xml_fh ('<?xml version="1.0" encoding="utf-8"?>'."\n");
		printf $part_xml_fh ("<partition lba=\"$TOTAL_SECTORS\">\n");
		for($i = 0 ; $i < @GPT ; $i++) {
			my $pt_name;
			if (lc($GPT[$i]{"NAME"}) eq "system") {
				$pt_name = "mntlblk_d1";
			} elsif (lc($GPT[$i]{"NAME"}) eq "vendor") {
				$pt_name = "mntlblk_d2";
			} else {
				$pt_name = lc $GPT[$i]{"NAME"};
			}
			printf $part_xml_fh ("\t<entry type=\"{0FC63DAF-8483-4772-8E79-3D69D8477DE4}\" ");
			printf $part_xml_fh ("start=\"%d\" end=\"%d\" name=\"". $pt_name."\"", $GPT[$i]{"START_SECTOR"}, $GPT[$i]{"END_SECTOR"});
			if (exists $MNTL_img{$pt_name}) {
				printf $part_xml_fh (" file_name=\"". $MNTL_img{$pt_name}."\"");
			}
			printf $part_xml_fh (" />\n");
		}
		printf $part_xml_fh ("</partition>\n");

		#printf $part_size_fh ("BOARD_SYSTEMIMAGE_PARTITION_SIZE:=%d\n",$SYSTEM_SIZE_KB*1024);
		#printf $part_size_fh ("BOARD_CACHEIMAGE_PARTITION_SIZE:=%d\n",$CACHE_SIZE_KB*1024);
		#printf $part_size_fh ("BOARD_NVDATAIMAGE_PARTITION_SIZE:=%d\n",$NVDATA_SIZE_KB*1024);
		#printf $part_size_fh ("BOARD_USERDATAIMAGE_PARTITION_SIZE:=%d\n", $USERDATA_SIZE_KB*1024);

		#$SYSTEM_START_SECTOR = 4;#sector 0:MBR; sector 1:Header;sector 2,3: entries
		#$SYSTEM_END_SECTOR = $SYSTEM_START_SECTOR + $SYSTEM_SIZE_KB * 1024/$SECTOR_SIZE - 1;
		#$CACHE_START_SECTOR = $SYSTEM_END_SECTOR + 1;
		#$CACHE_END_SECTOR = $CACHE_START_SECTOR + $CACHE_SIZE_KB * 1024/$SECTOR_SIZE - 1;
		#$NVDATA_START_SECTOR = $CACHE_END_SECTOR + 1;
		#$NVDATA_END_SECTOR = $NVDATA_START_SECTOR + $NVDATA_SIZE_KB * 1024/$SECTOR_SIZE - 1;
		#$USERDATA_START_SECTOR= $NVDATA_END_SECTOR + 1;
		#$USERDATA_END_SECTOR= $USERDATA_START_SECTOR + $USERDATA_PARTITION_SIZE_KB * 1024/$SECTOR_SIZE - 1;
		#$TOTAL_SECTORS= $USERDATA_END_SECTOR + $SYSTEM_START_SECTOR;
		#printf $part_size_fh ("BOARD_SYSTEMIMAGE_START_SECTOR:=%d\n",$SYSTEM_START_SECTOR);
		#printf $part_size_fh ("BOARD_SYSTEMIMAGE_END_SECTOR:=%d\n",$SYSTEM_END_SECTOR);
		#printf $part_size_fh ("BOARD_CACHEIMAGE_START_SECTOR:=%d\n",$CACHE_START_SECTOR);
		#printf $part_size_fh ("BOARD_CACHEIMAGE_END_SECTOR:=%d\n",$CACHE_END_SECTOR);
		#printf $part_size_fh ("BOARD_NVDATAIMAGE_START_SECTOR:=%d\n",$NVDATA_START_SECTOR);
		#printf $part_size_fh ("BOARD_NVDATAIMAGE_END_SECTOR:=%d\n",$NVDATA_END_SECTOR);
		#printf $part_size_fh ("BOARD_USERDATAIMAGE_START_SECTOR:=%d\n", $USERDATA_START_SECTOR);
		#printf $part_size_fh ("BOARD_USERDATAIMAGE_END_SECTOR:=%d\n", $USERDATA_END_SECTOR);
		printf $part_size_fh ("BOARD_TOTAL_SECTORS:=%d\n", $TOTAL_SECTORS);

	}

	print Dumper(@SIZE_FIELD_KB);
	my @ini_files;
	if($MLC_NAND_SUPPORT eq "yes" || $TLC_NAND_SUPPORT eq  "yes") {
		my $ocp_slc=10;
		for($i=0;$i<2;$i++){
			$INIFILE="$TMP_OUT_PATH/tmp_ubi_info$i.ini";
			if($slc_blk[$i] == 0){
				next;
			}
			$ini_fd=&open_for_rw($INIFILE);
			print $ini_fd "[tlc_info]\n";
			print $ini_fd "mode=info\n";
			print $ini_fd "slc_blk=$slc_blk[$i]\n";
			print $ini_fd "tlc_blk=$tlc_blk[$i]\n";
			print $ini_fd "ocp_slc=$ocp_slc\n";
			close $ini_fd;
			push(@{$ini_files[$i]}, $INIFILE);
		}
	}
	for($index=0;$index < $total_rows;$index++){
		$vdr_name = lc($PARTITION_FIELD[$index]);
			print "[JP] $vdr_name = $PARTITION_FIELD[$index]";
			if (exists $mountPointMapList{$vdr_name}) {
					printf $part_size_fh ("MTK_BOARD_ROOT_EXTRA_FOLDERS += %s\n", $vdr_name);
					printf $part_size_fh ("MTK_BOARD_ROOT_EXTRA_FOLDERS += %s\n", $mountPointMapList{$vdr_name});
			}
		if(($MTK_NAND_UBIFS_SUPPORT eq "yes" && $TYPE_FIELD[$index] eq "UBIFS/YAFFS2") || ($MTK_NAND_MTK_FTL_SUPPORT eq "yes" && $TYPE_FIELD[$index] eq "EXT4" && $EMMC_SUPPORT ne "yes")){
			# UBI reserve 6 block
			my $ubi_used_blk=6;
			my $part_name=lc($PARTITION_FIELD[$index]);
			my $INIFILE;
			my $ini_grp = part_grp($PARTITION_FIELD[$index]);

			if($MLC_NAND_SUPPORT eq "yes" || $TLC_NAND_SUPPORT eq  "yes"){
				$INIFILE="$TMP_OUT_PATH/tmp_ubi_${part_name}.ini";
			} else {
				$INIFILE="$TMP_OUT_PATH/ubi_${part_name}.ini";
			}
			if($SIZE_FIELD_KB[$index] == 0){
				&error_handler("zero size of partition $part_name");
			}
			if($MLC_NAND_SUPPORT eq "yes") {
				# UBI Backup LSB need 2 block
				$ubi_used_blk+=2;
			}
			if($MTK_NAND_UBIFS_FASTMAP_SUPPORT eq "yes") {
				# FASTMAP reserve more 2 block
				$ubi_used_blk+=2;
			}
			if($PARTITION_FIELD[$index] eq "USRDATA" || $PARTITION_FIELD[$index] eq "ANDROID") {
                        	$vol_size = (int($tlc_android[$ini_grp]/${PEB})-$ubi_used_blk)*$LEB;
			} elsif($PARTITION_FIELD[$index] eq "CACHE") {
                        	$vol_size = (int($SIZE_FIELD_KB[$index]*1024/${PEB}))*$LEB;
			} else {
                        	$vol_size = (int($SIZE_FIELD_KB[$index]*1024/${PEB})-$ubi_used_blk)*$LEB;
			}

			if($min_ubi_vol_size > $SIZE_FIELD_KB[$index]*1024){
				&error_handler("$PARTITION_FIELD[$index] is too small, UBI partition is at least $min_ubi_vol_size byte, Now it is $SIZE_FIELD_KB[$index] KiB", __FILE__, __LINE__);
			}

			if($PARTITION_FIELD[$index] eq "USRDATA") {
				if($MTK_FAT_ON_NAND eq "yes" && $MLC_NAND_SUPPORT eq "yes"){
					$fat_size=(int($FAT_SIZE_KB*1024/${PEB}))*$LEB;
					$vol_size -= $fat_size; # reserve for fat
				}
				if($MTK_IPOH_SUPPORT eq "yes") {
					$ipoh_size=(int($IPOH_SIZE_KB*1024/${PEB}))*$LEB;
					$vol_size -= $ipoh_size; # reserve for ipoh
				}
				print "ipoh size $ipoh - $IPOH_SIZE_KB\n";
				$cache_size=(int($CACHE_SIZE_KB*1024/${PEB}))*$LEB;
				$vol_size -= $cache_size;
				printf $part_size_fh ("BOARD_UBIFS_CACHE_VOLUME_SIZE:=%d\n",int($cache_size));
			}
			my $ini_fd=&open_for_rw($INIFILE);
			print $ini_fd "[$part_name]\n";
                        if($MTK_NAND_UBIFS_FASTMAP_SUPPORT eq "yes") {
				print $ini_fd "ubi_device_size=" . $SIZE_FIELD_KB[$index]*1024 . "\n";
			}
			print $ini_fd "mode=ubi\n";
			if($PARTITION_FIELD[$index] eq "USRDATA") {
				if($MTK_NAND_MTK_FTL_SUPPORT eq "yes") {
					print $ini_fd "image=mtftl.${part_name}.img\n";
				} else {
					print $ini_fd "image=ubifs.${part_name}.img\n";
				}
			}
			if($PARTITION_FIELD[$index] eq "ANDROID") {
				if($MTK_NAND_MTK_FTL_SUPPORT eq "yes") {
					print $ini_fd "image=mtftl.${part_name}.img\n";
				} else {
					print $ini_fd "image=ubifs.${part_name}.img\n";
				}
			}
			print $ini_fd "vol_id=".vol_id($PARTITION_FIELD[$index])."\n";
			print $ini_fd "vol_size=$vol_size\n";
			print $ini_fd "vol_type=dynamic\n";
			if(exists $kernel_alias{$PARTITION_FIELD[$index]}){
				print $ini_fd "vol_name=$kernel_alias{$PARTITION_FIELD[$index]}\n";
			}else{
				print $ini_fd "vol_name=${part_name}\n";
			}
			if($MLC_NAND_SUPPORT eq "yes" || $TLC_NAND_SUPPORT eq  "yes"){
				if($PARTITION_FIELD[$index] eq "USRDATA") {
					print $ini_fd "vol_flags=autoresize\n";
				}elsif($PARTITION_FIELD[$index] eq "ANDROID") {
					print $ini_fd "vol_flags=autoresize\n";
				}
			} else {
#Disable autoresize when fastmap enabled
				if($MTK_NAND_UBIFS_FASTMAP_SUPPORT ne "yes" && $PARTITION_FIELD[$index] ne "SEC_RO"){
					print $ini_fd "vol_flags=autoresize\n";
				}
			}
			print $ini_fd "vol_alignment=1\n";


			if($PARTITION_FIELD[$index] eq "USRDATA") {
				if(0 && $MTK_FAT_ON_NAND eq "yes" && $MLC_NAND_SUPPORT eq "yes"){
#for fat volume
					print $ini_fd "[fat]\n";
					print $ini_fd "mode=ubi\n";
					print $ini_fd "vol_id=".vol_id("FAT")."\n";
					print $ini_fd "vol_size=$fat_size\n";
					print $ini_fd "vol_type=dynamic\n";
					print $ini_fd "vol_name=fat\n";
					print $ini_fd "vol_alignment=1\n";
					printf $part_size_fh ("BOARD_UBIFS_FAT_MERGE_VOLUME_SIZE:=%d\n",int($fat_size));
				}
				if($MTK_IPOH_SUPPORT eq "yes" && ($MLC_NAND_SUPPORT eq "yes" || $TLC_NAND_SUPPORT eq "yes")){
#for ipoh volume
					if($ipoh_size > 0) {
						print $ini_fd "[ipoh]\n";
						print $ini_fd "mode=ubi\n";
						print $ini_fd "vol_id=".vol_id("IPOH")."\n";
						print $ini_fd "vol_size=$ipoh_size\n";
						print $ini_fd "vol_type=dynamic\n";
						print $ini_fd "vol_name=ipoh\n";
						print $ini_fd "vol_alignment=1\n";
					}
					printf $part_size_fh ("BOARD_UBIFS_IPOH_VOLUME_SIZE:=%d\n",int($ipoh_size));
				}
			}
			if($MLC_NAND_SUPPORT eq "yes" || $TLC_NAND_SUPPORT eq "yes") {
				push(@{$ini_files[$ini_grp]}, $INIFILE);
			}
        		close $ini_fd;
			if($MTK_NAND_MTK_FTL_SUPPORT eq "yes"){
				printf $part_size_fh ("BOARD_MTFTL_%s_MAX_LOGICAL_ERASEBLOCK_COUNT:=%d\n",$PARTITION_FIELD[$index],int((${vol_size}/$LEB)*1.5));
			}else{
				printf $part_size_fh ("BOARD_UBIFS_%s_MAX_LOGICAL_ERASEBLOCK_COUNT:=%d\n",$PARTITION_FIELD[$index],int((${vol_size}/$LEB)*1.5));
			}
		}

		if($TYPE_FIELD[$index] eq "EXT4" || $TYPE_FIELD[$index] eq "FAT"){
			$temp = $SIZE_FIELD_KB[$index]/1024;
			if($MTK_NAND_MTK_FTL_SUPPORT eq "yes"){
				if(($PARTITION_FIELD[$index] eq "USRDATA") && ($temp == 0)){
					$temp = int((int($Total_Size/${PEB})-12)*$LEB/1024/1024); # Userdata Reserved 12
				}else{
					$temp = int($vol_size/1024/1024);
					if ($PARTITION_FIELD[$index] eq "CACHE") {
						$temp -= 30*${PEB}/1024/1024; #Cache Reserved UBI more than 30 for FTL ext4
					}else{
						if($PARTITION_FIELD[$index] eq "USRDATA") {
							$temp -= 70*${PEB}/1024/1024; #Userdata Reserved UBI more than 70 for FTL ext4
						}else{
							$temp -= 23*${PEB}/1024/1024; #System Reserved UBI more than 23 for FTL ext4
						}
					}
				}
				if(exists($PSalias{$PARTITION_FIELD[$index]})){
					print $part_size_fh "BOARD_$PSalias{$PARTITION_FIELD[$index]}IMAGE_PARTITION_SIZE:=$temp". "M\n" ;
				}else{
					print $part_size_fh "BOARD_$PARTITION_FIELD[$index]IMAGE_PARTITION_SIZE:=$temp". "M\n" ;
				}
			}else{
				if($PARTITION_FIELD[$index] eq "USRDATA"){
					$temp -=1;
				}
				if(exists($PSalias{$PARTITION_FIELD[$index]})){
					print $part_size_fh "BOARD_$PSalias{$PARTITION_FIELD[$index]}IMAGE_PARTITION_SIZE:=$temp". "M\n" ;
				}else{
					print $part_size_fh "BOARD_$PARTITION_FIELD[$index]IMAGE_PARTITION_SIZE:=$temp". "M\n" ;
				}
			}
		}
	}

	if($MLC_NAND_SUPPORT eq "yes" || $TLC_NAND_SUPPORT eq  "yes"){
		print "ini group 0 ".scalar(@{$ini_files[0]}."\n");
		print "ini group 1 ".scalar(@{$ini_files[1]}."\n");
		if(scalar(@{$ini_files[0]}) > 0) {
			my $inifile="$TMP_OUT_PATH/ubi_usrdata.ini";
			append_files($inifile, @{$ini_files[0]});
		}
		if(scalar(@{$ini_files[1]}) > 0) {
			my $inifile="$TMP_OUT_PATH/ubi_android.ini";
			append_files($inifile, @{$ini_files[1]});
		} else {
			my $dst="$TMP_OUT_PATH/ubi_usrdata.ini";
			my $src=("$TMP_OUT_PATH/tmp_ubi_usrdata.ini");
			append_files($dst, $src);
		}
		print Dumper(@ini_files);
	}

 	#print $part_size_fh "endif \n" ;
    close $part_size_fh ;
}

sub append_files
{
	$dst = shift;
	@src = @_;
	my $ini_fd=&open_for_rw($dst);
	foreach $f (@src) {
		my $src_fd = &open_for_read($f);
		while (my $row = <$src_fd>) {
			print $ini_fd $row;
		}
	}
	#print Dumper(@src);
}

sub part_grp()
{
	my $name = shift;
	my $ret = 1;
	if($name ne "ANDROID") { $ret = 0 }
	#printf "name $name ret $ret\n";
	return $ret;
}
sub vol_id()
{
	my $name = shift;
	my $ret = 0, $adj = -1;
	#if($MLC_NAND_SUPPORT eq "yes") {
	#	if($name eq "IPOH") { $ret = 1; }
	#	return $ret;
	#} elsif($TLC_NAND_SUPPORT eq "yes") {
	if(1) {
		if($name eq "ANDROID") { $ret = 0;; }
		elsif($name eq "USRDATA") { $ret = 1+$adj; }
		elsif($name eq "CACHE") { $ret = 2+$adj; }
		elsif($name eq "FAT") { $ret = 3+$adj; }
		elsif($name eq "IPOH") { $ret = 4+$adj; }
		else {$ret = 5+$adj;}
		return $ret;
	}
}

#****************************************************************************
# subroutine:  error_handler
# input:       $error_msg:     error message
#****************************************************************************
sub error_handler()
{
	   my ($error_msg, $file, $line_no) = @_;
	   my $final_error_msg = "Ptgen ERROR: $error_msg at $file line $line_no\n";
	   print $final_error_msg;
	   die $final_error_msg;
}

#****************************************************************************
# subroutine:  copyright_file_header_for_c
# return:      file header -- copyright
#****************************************************************************
sub copyright_file_header_for_c()
{
    my $template = <<"__TEMPLATE";
/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2012. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
__TEMPLATE

   return $template;
}
#****************************************************************************
# subroutine:  copyright_file_header_for_shell
# return:      file header -- copyright
#****************************************************************************
sub copyright_file_header_for_shell()
{
    my $template = <<"__TEMPLATE";
 # Copyright Statement:
 #
 # This software/firmware and related documentation ("MediaTek Software") are
 # protected under relevant copyright laws. The information contained herein
 # is confidential and proprietary to MediaTek Inc. and/or its licensors.
 # Without the prior written permission of MediaTek inc. and/or its licensors,
 # any reproduction, modification, use or disclosure of MediaTek Software,
 # and information contained herein, in whole or in part, shall be strictly prohibited.
 #
 # MediaTek Inc. (C) 2012. All rights reserved.
 #
 # BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 # THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 # RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 # AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 # EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 # MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 # NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 # SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 # SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 # THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 # THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 # CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 # SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 # STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 # CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 # AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 # OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 # MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 #
 # The following software/firmware and/or related documentation ("MediaTek Software")
 # have been modified by MediaTek Inc. All revisions are subject to any receiver's
 # applicable license agreements with MediaTek Inc.
 #/
__TEMPLATE

   return $template;
}

#****************************************************************************************
# subroutine:  GenPerloaderCust_partC
# return:		Gen custpart_private.h in Preloader
# input:       no input
#****************************************************************************************
sub GenPerloaderCust_partC{
 	my $iter = 0 ;
 	my $temp;
 	my $type;
 	my $SOURCE=&open_for_rw($PreloaderC);
	print $SOURCE &copyright_file_header_for_c();
	print $SOURCE "\n#include \"typedefs.h\"\n";
	print $SOURCE "#include \"platform.h\"\n";
	print $SOURCE "#include \"blkdev.h\"\n";
	print $SOURCE "#include \"cust_part.h\"\n";
#static part_t platform_parts[PART_MAX_COUNT];
	print $SOURCE "\nstatic part_t platform_parts[PART_MAX_COUNT] = {\n";

	for ($iter=0; $iter< $total_rows; $iter++){
		last if($PARTITION_FIELD[$iter] eq "BMTPOOL" ||$PARTITION_FIELD[$iter] eq "OTP" );
		if($DL_TYPE[$iter] eq "LOW_PAGE")
		{
			$type = "TYPE_LOW";
		}else{
			$type = "TYPE_FULL";
		}
		if($preloader_alias{$PARTITION_FIELD[$iter]}){
			$temp = "\t{PART_$preloader_alias{$PARTITION_FIELD[$iter]}, 0, PART_SIZE_$PARTITION_FIELD[$iter], 0,PART_FLAG_NONE,$type},\n";
			print $SOURCE $temp;
		}else{
			$temp = "\t{PART_$PARTITION_FIELD[$iter], 0, PART_SIZE_$PARTITION_FIELD[$iter], 0,PART_FLAG_NONE,$type},\n";
			print $SOURCE $temp;
		}
	}
	$temp = "\t{NULL,0,0,0,PART_FLAG_END},\n};\n\n";
	print $SOURCE $temp;

#fuction

#   print $SOURCE  "void cust_part_init(void){}\n\n";

 #  print $SOURCE  "part_t *cust_part_tbl(void)\n";
#   print $SOURCE "{\n";
 #  print $SOURCE "\t return &platform_parts[0];\n";
  # print $SOURCE "}\n";
	 my $template = <<"__TEMPLATE";
void cust_part_init(void){}

part_t *cust_part_tbl(void)
{
	 return &platform_parts[0];
}

__TEMPLATE
	print $SOURCE $template;
	close $SOURCE;
}
#****************************************************************************************
sub GenKernel_PartitionC(){
	my $iter = 0;
 	my $temp;
 	my $device = "FALSE";
 	my $ind = index($SHEET_NAME, "mlc");
 	if ($ind ne -1)
 	{
 		$device = "TRUE";
 	}
 	my $SOURCE=&open_for_rw($KernelH);

	print $SOURCE &copyright_file_header_for_c();
	my $template = <<"__TEMPLATE";

#include <linux/mtd/mtd.h>
#include <linux/mtd/nand.h>
#include <linux/mtd/partitions.h>
#include "partition_define.h"


/*=======================================================================*/
/* NAND PARTITION Mapping                                                  */
/*=======================================================================*/
enum partition_type
{
	TYPE_RAW,
	TYPE_UBIFS,
	TYPE_YAFFS,
	TYPE_MTFTL,
};

static struct mtd_partition g_pasStatic_Partition[] = {

__TEMPLATE
	print $SOURCE $template;
	for ($iter=0; $iter< $total_rows; $iter++){
		$temp = lc($PARTITION_FIELD[$iter]);
		last if($PARTITION_FIELD[$iter] eq "BMTPOOL" || $PARTITION_FIELD[$iter] eq "OTP");
		print $SOURCE "\t{\n";
		if($kernel_alias{$PARTITION_FIELD[$iter]}){
			print $SOURCE "\t\t.name = \"$kernel_alias{$PARTITION_FIELD[$iter]}\",\n";
		}
		else{
			print $SOURCE "\t\t.name = \"$temp\",\n";
		}
		if($iter == 0){
			print $SOURCE "\t\t.offset = 0x0,\n";
		}else{
			print $SOURCE "\t\t.offset = PART_OFFSET_$PARTITION_FIELD[$iter],\n";
		}
		if($PARTITION_FIELD[$iter] ne "USRDATA"){
			print $SOURCE "\t\t.size = PART_SIZE_$PARTITION_FIELD[$iter],\n";
		}else{
			print $SOURCE "\t\t.size = MTDPART_SIZ_FULL,\n";
		}
		if($PARTITION_FIELD[$iter] eq "PRELOADER" ||$PARTITION_FIELD[$iter] eq "DSP_BL" ||$PARTITION_FIELD[$iter] eq "UBOOT" || $PARTITION_FIELD[$iter] eq "SEC_RO"){
			print $SOURCE "\t\t.mask_flags  = MTD_WRITEABLE,\n";
		}
		print $SOURCE "\t},\n";
	}
	print $SOURCE "};\n";
        print $SOURCE "static enum partition_type partition_type_array[] = {\n";
	for ($iter=0; $iter< $total_rows; $iter++){
		my $temp_s;
		if($TYPE_FIELD[$iter] eq "UBIFS/YAFFS2"){
			if($MTK_NAND_UBIFS_SUPPORT eq "yes"){
				$temp_s = "TYPE_UBIFS,";
			}else{
				$temp_s = "TYPE_YAFFS,";
			}
		}elsif ($TYPE_FIELD[$iter] eq "YAFFS2"){
			$temp_s = "TYPE_YAFFS,";
		}
		else{
			if($MTK_NAND_MTK_FTL_SUPPORT eq "yes"){
				$temp_s = "TYPE_MTFTL,"
			}else{
				$temp_s = "TYPE_RAW,"
			}
		}
		print $SOURCE "\t$temp_s\n";
	}
	print $SOURCE "};\n";

	$template = <<"__TEMPLATE";
#define NUM_PARTITIONS ARRAY_SIZE(g_pasStatic_Partition)
extern int part_num;	// = NUM_PARTITIONS;
__TEMPLATE
	print $SOURCE $template;
	print $SOURCE "static bool MLC_DEVICE = $device;";
	close $SOURCE;


}
#****************************************************************************************
# subroutine:  GenPmt_H
# return: 
#****************************************************************************************
sub GenPmt_H(){
	my $pmt_h_fd = &open_for_rw($PMT_H_NAME);
	print $pmt_h_fd &copyright_file_header_for_c();

    my $template = <<"__TEMPLATE";

#ifndef _PMT_H
#define _PMT_H

#include "partition_define.h"

//mt6516_partition.h has defination
//mt6516_download.h define again, both is 20

#define MAX_PARTITION_NAME_LEN 64
#ifdef MTK_EMMC_SUPPORT
/*64bit*/
typedef struct
{
    unsigned char name[MAX_PARTITION_NAME_LEN];     /* partition name */
    unsigned long long size;     						/* partition size */
    unsigned long long offset;       					/* partition start */
    unsigned long long mask_flags;       				/* partition flags */

} pt_resident;
/*32bit*/
typedef struct
{
    unsigned char name[MAX_PARTITION_NAME_LEN];     /* partition name */
    unsigned long  size;     						/* partition size */
    unsigned long  offset;       					/* partition start */
    unsigned long mask_flags;       				/* partition flags */

} pt_resident32;
#else

typedef struct
{
    unsigned char name[MAX_PARTITION_NAME_LEN];     /* partition name */
    unsigned long long size;     						/* partition size */
    unsigned long long offset;       					/* partition start */
    unsigned long long mask_flags;       				/* partition flags */

} pt_resident;
#endif


#define DM_ERR_OK 0
#define DM_ERR_NO_VALID_TABLE 9
#define DM_ERR_NO_SPACE_FOUND 10
#define ERR_NO_EXIST  1

//Sequnce number


//#define PT_LOCATION          4090      // (4096-80)
//#define MPT_LOCATION        4091            // (4096-81)
#define PT_SIG      0x50547632            //"PTv1"
#define MPT_SIG    0x4D505432           //"MPT1"
#define PT_SIG_SIZE 4
#define is_valid_mpt(buf) ((*(u32 *)(buf))==MPT_SIG)
#define is_valid_pt(buf) ((*(u32 *)(buf))==PT_SIG)
#define RETRY_TIMES 5


typedef struct _DM_PARTITION_INFO
{
    char part_name[MAX_PARTITION_NAME_LEN];             /* the name of partition */
    unsigned long long start_addr;                                  /* the start address of partition */
    unsigned long long part_len;                                    /* the length of partition */
    unsigned char part_visibility;                              /* part_visibility is 0: this partition is hidden and CANNOT download */
                                                        /* part_visibility is 1: this partition is visible and can download */
    unsigned char dl_selected;                                  /* dl_selected is 0: this partition is NOT selected to download */
                                                        /* dl_selected is 1: this partition is selected to download */
} DM_PARTITION_INFO;

typedef struct {
    unsigned int pattern;
    unsigned int part_num;                              /* The actual number of partitions */
    DM_PARTITION_INFO part_info[PART_MAX_COUNT];
} DM_PARTITION_INFO_PACKET;

typedef struct {
	int sequencenumber:8;
	int tool_or_sd_update:8;
	int mirror_pt_dl:4;   //mirror download OK
	int mirror_pt_has_space:4;
	int pt_changed:4;
	int pt_has_space:4;
} pt_info;

#endif

__TEMPLATE
	print $pmt_h_fd $template;
	close $pmt_h_fd;
}

#****************************************************************************************
# subroutine:  GenLK_PartitionC
# return:
#****************************************************************************************
sub GenLK_PartitionC(){
	my $iter = 0;
 	my $temp;
 	my $type;
 	my $SOURCE=open_for_rw($LK_PartitionC);
	print $SOURCE &copyright_file_header_for_c();

	print $SOURCE "#include \"mt_partition.h\"\n";

	if($PLATFORM eq "MT6575"){
		$temp = lc($PLATFORM);
		print $SOURCE "\npart_t $temp"."_parts[] = {\n";
	}else{
		print $SOURCE "\npart_t partition_layout[] = {\n";
	}
	for ($iter=0; $iter< $total_rows; $iter++){
		last if($PARTITION_FIELD[$iter] eq "BMTPOOL" ||$PARTITION_FIELD[$iter] eq "OTP");
		if($DL_TYPE[$iter] eq "LOW_PAGE")
		{
			$type = "TYPE_LOW";
		}else{
			$type = "TYPE_FULL";
		}
		if($lk_xmodule_alias{$PARTITION_FIELD[$iter]}){
			$temp = "\t{PART_$lk_xmodule_alias{$PARTITION_FIELD[$iter]}, PART_BLKS_$lk_xmodule_alias{$PARTITION_FIELD[$iter]}, 0, PART_FLAG_NONE, $type},\n";
			print $SOURCE $temp;
		}else{
			$temp = "\t{PART_$PARTITION_FIELD[$iter], PART_BLKS_$PARTITION_FIELD[$iter], PART_FLAG_NONE,0, $type},\n";
			print $SOURCE $temp;
		}
	}
	$temp = "\t{NULL, 0, PART_FLAG_END, 0},\n};";
	print $SOURCE $temp;
	print $SOURCE "\n\nstruct part_name_map g_part_name_map[PART_MAX_COUNT] = {\n";
	for ($iter=0; $iter< $total_rows; $iter++){
		last if($PARTITION_FIELD[$iter] eq "BMTPOOL" || $PARTITION_FIELD[$iter] eq "OTP");
		if($TYPE_FIELD[$iter] eq "UBIFS/YAFFS2"){
			if($MTK_NAND_UBIFS_SUPPORT eq "yes"){
				$temp_t = "ubifs";
			}else{
				$temp_t = "yaffs2";
			}
		}else{
			if($MTK_NAND_MTK_FTL_SUPPORT eq "yes"){
				$temp_t = "mtftl";
			}else{
				$temp_t = lc($TYPE_FIELD[$iter]);
			}
		}
		if($lk_alias{$PARTITION_FIELD[$iter]}){
			if($lk_xmodule_alias{$PARTITION_FIELD[$iter]}){
				$temp = "\t{\"$lk_alias{$PARTITION_FIELD[$iter]}\",\tPART_$lk_xmodule_alias{$PARTITION_FIELD[$iter]},\t\"$temp_t\",\t$iter,\t$FB_ERASE_FIELD[$iter],\t$FB_DL_FIELD[$iter]},\n";
			}else{
				$temp = "\t{\"$lk_alias{$PARTITION_FIELD[$iter]}\",\tPART_$PARTITION_FIELD[$iter],\t\"$temp_t\",\t$iter,\t$FB_ERASE_FIELD[$iter],\t$FB_DL_FIELD[$iter]},\n";
			}
			print $SOURCE $temp;
		}else{
			$temp = lc($PARTITION_FIELD[$iter]);
			if($lk_xmodule_alias{$PARTITION_FIELD[$iter]}){
				$temp = "\t{\"$temp\",\tPART_$lk_xmodule_alias{$PARTITION_FIELD[$iter]},\t\"$temp_t\",\t$iter,\t$FB_ERASE_FIELD[$iter],\t$FB_DL_FIELD[$iter]},\n";
			}else{
				$temp = "\t{\"$temp\",\tPART_$PARTITION_FIELD[$iter],\t\"$temp_t\",\t$iter,\t$FB_ERASE_FIELD[$iter],\t$FB_DL_FIELD[$iter]},\n";
			}

			print $SOURCE $temp;
		}
	}
	print $SOURCE "};\n";
	close $SOURCE;
}
#****************************************************************************************
# subroutine:  GenLK_MT_ParitionH
# return:
#****************************************************************************************
sub GenLK_MT_PartitionH(){
	my $iter = 0;
	my $SOURCE=&open_for_rw($LK_MT_PartitionH);
	print $SOURCE &copyright_file_header_for_c();

	my $template = <<"__TEMPLATE";

#ifndef __MT_PARTITION_H__
#define __MT_PARTITION_H__


#include <platform/part.h>
#include "partition_define.h"
#include <platform/mt_typedefs.h>

#define NAND_WRITE_SIZE	 2048

#define BIMG_HEADER_SZ				(0x800)
#define MKIMG_HEADER_SZ				(0x200)

#define BLK_BITS         (9)
#define BLK_SIZE         (1 << BLK_BITS)
#ifdef MTK_EMMC_SUPPORT
#define BLK_NUM(size)    ((unsigned long long)(size) / BLK_SIZE)
#else
#define BLK_NUM(size)    ((unsigned long long)(size) / BLK_SIZE)
#endif
#define PART_KERNEL     "KERNEL"
#define PART_ROOTFS     "ROOTFS"
enum partition_type{
	TYPE_LOW,
	TYPE_FULL,
};
__TEMPLATE
	print $SOURCE $template;
	for ($iter=0; $iter< $total_rows; $iter++){
		last if($PARTITION_FIELD[$iter] eq "BMTPOOL" || $PARTITION_FIELD[$iter] eq "OTP");
		if($lk_xmodule_alias{$PARTITION_FIELD[$iter]}){
			$temp = "#define PART_BLKS_$lk_xmodule_alias{$PARTITION_FIELD[$iter]}   BLK_NUM(PART_SIZE_$PARTITION_FIELD[$iter])\n";
			print $SOURCE $temp;
		}else{
			$temp = "#define PART_BLKS_$PARTITION_FIELD[$iter]   BLK_NUM(PART_SIZE_$PARTITION_FIELD[$iter])\n";
			print $SOURCE $temp;
		}
	}
	for ($iter=0; $iter< $total_rows; $iter++){
		if($PARTITION_FIELD[$iter] eq "PRELOADER")
		{
			my $page;
			my $page_p_b;
			($page, $page_p_b) =  get_page_size();
			$page_p_b = $SIZE_FIELD_KB[$iter]/$page*1024;
			$temp = "#ifndef RAND_START_ADDR\n#define RAND_START_ADDR   $page_p_b\n#endif\n";
			print $SOURCE $temp;
		}
	}
	print $SOURCE "\n\n#define PMT_END_NAME \"$PMT_END_NAME\"";
	print $SOURCE "\n\nstruct NAND"."_CMD\{\n";

	$template = <<"__TEMPLATE";
	u32	u4ColAddr;
	u32 u4RowAddr;
	u32 u4OOBRowAddr;
	u8	au1OOB[64];
	u8*	pDataBuf;
};

typedef union {
    struct {
        unsigned int magic;        /* partition magic */
        unsigned int dsize;        /* partition data size */
        char         name[32];     /* partition name */
        unsigned int maddr;        /* partition memory address */
    } info;
    unsigned char data[BLK_SIZE];
} part_hdr_t;

typedef struct {
    unsigned char *name;        /* partition name */
    unsigned long long  blknum;      /* partition blks */
    unsigned long long  flags;       /* partition flags */
    unsigned long long  startblk;    /* partition start blk */
    enum partition_type type;
} part_t;

struct part_name_map{
	char fb_name[32]; 	/*partition name used by fastboot*/
	char r_name[32];  	/*real partition name*/
	char *partition_type;	/*partition_type*/
	int partition_idx;	/*partition index*/
	int is_support_erase;	/*partition support erase in fastboot*/
	int is_support_dl;	/*partition support download in fastboot*/
};

typedef struct part_dev part_dev_t;

struct part_dev {
    int init;
    int id;
    block_dev_desc_t *blkdev;
    int (*init_dev) (int id);
#if defined(MTK_EMMC_SUPPORT) || defined(CONFIG_MTK_EMMC_SUPPORT)
	int (*read)  (part_dev_t *dev, u64 src, uchar *dst, int size);
    int (*write) (part_dev_t *dev, uchar *src, u64 dst, int size);
#else
    int (*read)  (part_dev_t *dev, u64 src, uchar *dst, int size);
    int (*write) (part_dev_t *dev, uchar *src, u64 dst, int size);
#endif
};
enum{
	RAW_DATA_IMG,
	YFFS2_IMG,
	UBIFS_IMG,
	EXT4_IMG,
	FAT_IMG,
	UNKOWN_IMG,
};
extern struct part_name_map g_part_name_map[];
extern int mt_part_register_device(part_dev_t *dev);
extern part_t* mt_part_get_partition(char *name);
extern part_dev_t* mt_part_get_device(void);
extern void mt_part_init(unsigned long totalblks);
extern void mt_part_dump(void);
extern int partition_get_index(const char * name);
extern u64 partition_get_offset(int index);
extern u64 partition_get_size(int index);
extern int partition_get_type(int index, char **p_type);
extern int partition_get_name(int index, char **p_name);
extern int is_support_erase(int index);
extern int is_support_flash(int index);
extern u64 emmc_write(u64 offset, void *data, u64 size);
extern u64 emmc_read(u64 offset, void *data, u64 size);
extern int emmc_erase(u64 offset, u64 size);
extern unsigned long partition_reserve_size(void);
#endif /* __MT_PARTITION_H__ */

__TEMPLATE
	print $SOURCE $template;
	close $SOURCE;
}
#****************************************************************************************
# subroutine:  get_sheet
# return:      Excel worksheet no matter it's in merge area or not, and in windows or not
# input:       Specified Excel Sheetname
#****************************************************************************************
sub get_sheet {
  my ($sheetName,$Book) = @_;
  return $Book->Worksheet($sheetName);
}
#****************************************************************************************
# subroutine:  get_partition_sheet
# return:      Excel worksheet no matter it's in merge area or not, and in windows or not
# input:       Specified Excel Sheetname
# input:       Excel filename
#****************************************************************************************
sub get_partition_sheet {
	my ($sheetName, $fileName) = @_;
	my $parser = Spreadsheet::ParseExcel->new();
	my $workbook = $parser->Parse($fileName);
	my $sheet;

	if(!defined $workbook) {
		print "get workbook from $fileName failed, error: ", $parser->error, ".\n";
		return undef;
	} else {
		$sheet = get_sheet($sheetName, $workbook);
		if(!defined $sheet) {
			print "get $sheetName sheet failed.\n";
			return undef;
		}
		return $sheet;
	}
}
#****************************************************************************************
# subroutine:  load_partition_info
# return:      Excel worksheet no matter it's in merge area or not, and in windows or not
# input:       Specified Excel Sheetname
#****************************************************************************************
sub load_partition_info {
	my ($sheetName) = @_;
	my $sheet;
	if (exists $ENV{PTGEN_XLS})
	{
		$PROJECT_PART_TABLE_FILENAME=$ENV{PTGEN_XLS};
	}
	else
	{
		my $dir1 = "$CD_ALPS/device";		#Project maynot @ /device/mediatek but /device/companyA.
		my @arrayOfFirstLevelDirs;
		my $SearchFile = 'partition_table_nand_MT6739.xls'; #Search File Name

		opendir(DIR, $dir1) or die $!;
		#Search First Level path of the dir and save dirs in this path to @arrayOfFirstLevelDirs
		while (my $file = readdir(DIR))
		{
			# A file test to check that it is a directory
			next unless (-d "$dir1/$file");
			next unless ( $file !~ m/^\./); #ignore dir prefixed with .
			push @arrayOfFirstLevelDirs, "$dir1/$file";
		}

		closedir(DIR);
		foreach $i (@arrayOfFirstLevelDirs)
		{
	    	#search folder list+{project}/partition_table_nand_MT6739.xls existence
	    	$PROJECT_PART_TABLE_FILENAME = $i."\/".$PROJECT."\/".$SearchFile;
			if( -e $PROJECT_PART_TABLE_FILENAME)
			{
				print "Find: $PROJECT_PART_TABLE_FILENAME \n";
				last;
			}
		}
	}

	# get from project path
	$sheet = get_partition_sheet($sheetName, $PROJECT_PART_TABLE_FILENAME);
	if(!defined $sheet)
	{
		print "get partition sheet from $PROJECT_PART_TABLE_FILENAME failed, try $PART_TABLE_FILENAME...\n";
		# get from default platform path
		$sheet = get_partition_sheet($sheetName, $PART_TABLE_FILENAME);
		if(!defined $sheet)
		{
			my $error_msg = "Ptgen CAN NOT find sheet=$SHEET_NAME in $PART_TABLE_FILENAME\n";
			print $error_msg;
			die $error_msg;
		}
	}
	print "PROJECT_PART_TABLE_FILENAME=$PROJECT_PART_TABLE_FILENAME\n";
	return $sheet;
}


#****************************************************************************************
# subroutine:  xls_cell_value
# return:      Excel cell value no matter it's in merge area or not, and in windows or not
# input:       $Sheet:  Specified Excel Sheet
# input:       $row:    Specified row number
# input:       $col:    Specified column number
#****************************************************************************************
sub xls_cell_value {
	my ($Sheet, $row, $col,$SheetName) = @_;
	my $cell = $Sheet->get_cell($row, $col);
	if(defined $cell){
		return  $cell->Value();
  	}else{
		my $error_msg="ERROR in ptgen.pl: (row=$row,col=$col) undefine in $SheetName!\n";
		print $error_msg;
		die $error_msg;
	}
}
sub open_for_rw
{
    my $filepath = shift @_;
    if (-e $filepath)
    {
        chmod(0777, $filepath) or &error_handler_2("chmod 0777 $filepath fail", __FILE__, __LINE__);
        if (!unlink $filepath)
        {
            &error_handler("remove $filepath fail ", __FILE__, __LINE__);
        }
    }
    else
    {
        my $dirpath = substr($filepath, 0, rindex($filepath, "/"));
        eval { mkpath($dirpath) };
        if ($@)
        {
            &error_handler_2("Can not make dir $dirpath", __FILE__, __LINE__, $@);
        }
    }
    open my $filehander, "> $filepath" or &error_handler(" Can not open $filepath for read and write", __FILE__, __LINE__);
	push @OUTPUT_FILES,$filepath;
    return $filehander;
}
sub open_for_read
{
    my $filepath = shift @_;
    if (-e $filepath)
    {
        chmod(0777, $filepath) or &error_handler_2("chmod 777 $filepath fail", __FILE__, __LINE__);
    }
    else
    {
        print "No such file : $filepath\n";
        return undef;
    }
    open my $filehander, "< $filepath" or &error_handler_2(" Can not open $filepath for read", __FILE__, __LINE__);
    return $filehander;
}
sub error_handler_2
{
    my ($error_msg, $file, $line_no, $sys_msg) = @_;
    if (!$sys_msg)
    {
        $sys_msg = $!;
    }
    print "Fatal error: $error_msg <file: $file,line: $line_no> : $sys_msg";
    die;
}
#delete some obsolete file
sub clear_files
{
	my @ObsoleteFile;
    opendir (DIR,"mediatek/custom/$PROJECT/common");
	push @ObsoleteFile,readdir(DIR);
    close DIR;

    my $custom_out_prefix_obsolete  = "mediatek/custom/$PROJECT";
    my $configs_out_prefix_obsolete = "mediatek/config/$PROJECT";
    push @ObsoleteFile, "$configs_out_prefix_obsolete/configs/EMMC_partition_size.mk";
    push @ObsoleteFile, "$configs_out_prefix_obsolete/configs/partition_size.mk";;
    push @ObsoleteFile, "$custom_out_prefix_obsolete/preloader/cust_part.c";
    push @ObsoleteFile, "mediatek/kernel/drivers/dum-char/partition_define.c";
	push @ObsoleteFile, "mediatek/external/mtd-utils/ubi-utils/combo_nand.h";
	push @ObsoleteFile, "$custom_out_prefix_obsolete/kernel/core/src/partition.h";
	push @ObsoleteFile, "$custom_out_prefix_obsolete/lk/inc/mt_partition.h";
	push @ObsoleteFile, "$custom_out_prefix_obsolete/lk/partition.c";
	push @ObsoleteFile, "$custom_out_prefix_obsolete/common/pmt.h";
	push @ObsoleteFile, "$custom_out_prefix_obsolete/common/partition_define.h";
	push @ObsoleteFile, "$custom_out_prefix_obsolete/common/combo_nand.h";

	foreach my $filepath (@ObsoleteFile){
   		if (-e $filepath && !-d $filepath){
   	    	if (!unlink $filepath)
        	{
            	&error_handler("remove $filepath fail ", __FILE__, __LINE__);
        	}else{
   				print "clean $filepath: clean done \n"
   			}
  		}
	}
}

sub do_copy_files {
	print $AUTO_CHECK_OUT_FILES;
	if (-e $AUTO_CHECK_OUT_FILES) {
		`rm $AUTO_CHECK_OUT_FILES`;
	}
#	open (AUTO_CHECK_OUT_FILES, ">$AUTO_CHECK_OUT_FILES") or &error_handler("Ptgen open $AUTO_CHECK_OUT_FILES Fail!", __FILE__, __LINE__) ;
  open_for_rw($AUTO_CHECK_OUT_FILES);
print "PTGEN_ENV=$PTGEN_ENV\n";
  if(($PTGEN_ENV eq "ALL") || ($PTGEN_ENV eq "ANDROID") || ($PTGEN_ENV eq "PROJECT"))
  {
		if (-e "$SCAT_NAME_DIR/MBR") {
			copy_file("$SCAT_NAME_DIR/MBR",$COPY_SCATTER_BR_FILES_PATH);
		}
		if (-e "$SCAT_NAME_DIR/EBR1") {
			copy_file("$SCAT_NAME_DIR/EBR1",$COPY_SCATTER_BR_FILES_PATH);
		}
		if (-e "$SCAT_NAME_DIR/EBR2") {
			copy_file("$SCAT_NAME_DIR/EBR2",$COPY_SCATTER_BR_FILES_PATH);
		}

		printf "check $SCAT_NAME_DIR/MT6739_Android_scatter.txt $COPY_SCATTER_BR_FILES_PATH\n";
		if (-e "$SCAT_NAME_DIR/MT6739_Android_scatter.txt") {
			printf "Copy $SCAT_NAME_DIR/MT6739_Android_scatter.txt $COPY_SCATTER_BR_FILES_PATH\n";
			copy_file("$SCAT_NAME_DIR/MT6739_Android_scatter.txt",$COPY_SCATTER_BR_FILES_PATH);
		}
		if (-e $PART_SIZE_LOCATION) {
			printf "Copy $PART_SIZE_LOCATION to $COPY_PATH_PART_SIZE_LOCATION\n";
			copy_file($PART_SIZE_LOCATION,$COPY_PATH_PART_SIZE_LOCATION);
		}
		if (-e $PART_XML_LOCATION) {
			printf "Copy $PART_XML_LOCATION to $COPY_PATH_PART_SIZE_LOCATION\n";
			copy_file($PART_XML_LOCATION,$COPY_PATH_PART_SIZE_LOCATION);
		}
#  Add for UBI.ini
		my $AndroidIni="${TMP_OUT_PATH}/ubi_android.ini";
		my $CacheIni="${TMP_OUT_PATH}/ubi_cache.ini";
		my $DataIni="${TMP_OUT_PATH}/ubi_usrdata.ini";
		my $FatIni="${TMP_OUT_PATH}/ubi_fat.ini";

		if (-e $AndroidIni) {
			copy_file($AndroidIni,$COPY_SCATTER_BR_FILES_PATH);
		}
		if (-e $CacheIni) {
			copy_file($CacheIni,$COPY_SCATTER_BR_FILES_PATH);
		}
		if (-e $DataIni) {
			copy_file($DataIni,$COPY_SCATTER_BR_FILES_PATH);
		}
		if (-e $FatIni) {
			copy_file($FatIni,$COPY_SCATTER_BR_FILES_PATH);
		}
# end of Add of UBI.ini

  }
  if(($PTGEN_ENV eq "ALL") || ($PTGEN_ENV eq "KERNEL"))
  {
		if (-e  $PMT_H_NAME) {
			copy_file($PMT_H_NAME,$COPY_PATH_PMT_H_NAME_K);
		}
		if (-e $PARTITION_DEFINE_H_NAME) {
			copy_file($PARTITION_DEFINE_H_NAME,$COPY_PATH_PARTITION_DEFINE_H_NAME_K);
		}

		if (-e $PARTITION_DEFINE_C_NAME) {
			copy_file($PARTITION_DEFINE_C_NAME,$COPY_PATH_PARTITION_DEFINE_C_NAME);
		}
		if (-e $KernelH) {
			copy_file($KernelH,$COPY_PATH_KERNEL_PARTITION_H);
		}
		if (-e $COMBO_NAND_KERNELH) {
			copy_file($COMBO_NAND_KERNELH,$COPY_PATH_COMBO_NAND_KERNELH);
		}
		if (-e $COMBO_NAND_TOOLH) {
			copy_file($COMBO_NAND_TOOLH,$COPY_PATH_COMBO_NAND_TOOLH);
		}

	}
	if(($PTGEN_ENV eq "ALL") || ($PTGEN_ENV eq "PRELOADER"))
	{
		if (-e  $PMT_H_NAME) {
			copy_file($PMT_H_NAME,$COPY_PATH_PMT_H_NAME_PL);
		}
		if (-e $PARTITION_DEFINE_H_NAME) {
			copy_file($PARTITION_DEFINE_H_NAME,$COPY_PATH_PARTITION_DEFINE_H_NAME_PL);
		}
		if (-e $PreloaderC) {
			copy_file($PreloaderC,$COPY_PATH_PreloaderC);
		}
	}
	if(($PTGEN_ENV eq "ALL") || ($PTGEN_ENV eq "LK"))
	{
		if (-e  $PMT_H_NAME) {
			copy_file($PMT_H_NAME,$COPY_PATH_PMT_H_NAME_LK);
		}
		if (-e $PARTITION_DEFINE_H_NAME) {
			copy_file($PARTITION_DEFINE_H_NAME,$COPY_PATH_PARTITION_DEFINE_H_NAME_LK);
		}
		if (-e  $LK_MT_PartitionH) {
			copy_file($LK_MT_PartitionH,$COPY_PATH_LK_MT_PartitionH);
		}
		if (-e  $LK_PartitionC) {
			copy_file($LK_PartitionC,$COPY_PATH_LK_PartitionC);
		}
  }

	close(AUTO_CHECK_OUT_FILES);

}
#****************************************************************************
# subroutine:  copy_file
# input:
#****************************************************************************
sub copy_file()
{
	   my ($src_file, $dst_path) = @_;
	   my $file_name;
	   $file_name = substr($src_file, rindex($src_file, "/"),length($src_file));
	   if (-e "$dst_path/$file_name")
	   {
	   	    `chmod 777 $dst_path/$file_name`;
	   }
	   else
	   {
        eval { mkpath($dst_path) };
        if ($@)
        {
            &error_handler_2("Can not make dir $dirpath", __FILE__, __LINE__, $@);
        }
	   }
	   `cp $src_file $dst_path`;
	   print AUTO_CHECK_OUT_FILES "$dst_path/$file_name\n";
}
sub ReadCustomMemoryDeviceFile
{
    #my $CUSTOM_MEMORYDEVICE_H_NAME  = "mediatek/custom/$PROJECT/preloader/inc/custom_MemoryDevice.h";
    if (-e $CUSTOM_MEMORYDEVICE_H_NAME) {
        #`chmod 777 $CUSTOM_MEMORYDEVICE_H_NAME`;
    }
    open (CUSTOM_MEMORYDEVICE_H_NAME, "<$CUSTOM_MEMORYDEVICE_H_NAME") or &error_handler("ptgen open CUSTOM_MEMORYDEVICE_H_NAME fail!\n", __FILE__, __LINE__);
    my $iter = 0;
    my %hash;
    while (<CUSTOM_MEMORYDEVICE_H_NAME>) {
        my($line) = $_;
        chomp($line);
        if($MLC_NAND_SUPPORT eq "yes" || $TLC_NAND_SUPPORT eq "yes")
        {
            if ($line =~ /^#define\s(NAND_PART_NUMBER\[[0-9]\])/) {
                $hash{$1}++;
                $PartNumbers[$iter] = $'; #'
                $PartNumbers[$iter] =~ s/\s+//g;
                if ($PartNumbers[$iter] =~ /(.*)\/\/(.*)/) { #skip //
                    $PartNumbers[$iter] =$1;
                }
                $iter ++;
            }
        }
        else
        {
            if ($line =~ /^#define\s(CS_PART_NUMBER\[[0-9]\])/) {
                $hash{$1}++;
                $PartNumbers[$iter] = $'; #'
                $PartNumbers[$iter] =~ s/\s+//g;
                if ($PartNumbers[$iter] =~ /(.*)\/\/(.*)/) { #skip //
                    $PartNumbers[$iter] =$1;
                }
                $iter ++;
            }
        }
    }
    while(($key,$value)=each(%hash))
    {
        &error_handler("Part Number: $key duplicates in custom_MemoryDevice.h\n", __FILE__, __LINE__) if($value >= 2);
    }
    my @array = sort keys(%hash);
    for($i=0;$i<@array;$i++)
    {
        &error_handler("CS_PART_NUMBER[$i] order error\n", __FILE__, __LINE__) unless( $array[$i] =~ /\[$i\]/);
    }

}

sub ReadNANDExcelFile
{	my @all_column=[];#=qw(Vendor Part_Number Nand_ID AddrCycle IOWidth TotalSize_MB BlockSize_KB PageSize_B SpareSize_B Timing S_Timing S_Timing1 Freq  CacheRead RandomRead Set_Feature	Get_Feature	Int_Address	Sync	Async	Rrtry_Addr	Rrtry_Number	Rrtry_Default	Rrtry_Start	PP_Function);
    my $MEMORY_DEVICE_LIST_XLS = "$CD_ALPS/device/mediatek/build/build/tools/emigen/$PLATFORM/MemoryDeviceList_${PLATFORM}.xls";

    my $SheetName = "NAND";
    if($SPI_NAND_SUPPORT eq "yes")
    {
        $SheetName = "SPI_NAND";
    }
    if($MLC_NAND_SUPPORT eq "yes")
    {
    	$SheetName = "NAND_MLC";
    }
    if($TLC_NAND_SUPPORT eq "yes")
    {
    	$SheetName = "NAND_MLC"; #FIXME
    }
    my $parser = Spreadsheet::ParseExcel->new();
    my $Book = $parser->Parse($MEMORY_DEVICE_LIST_XLS);
    my $sheet = $Book->Worksheet($SheetName);
    my %COLUMN_LIST;
    my $tmp;
    my $row;
    my $col;

    for($col = 0, $row = 0,$tmp = &xls_cell_value_nand($sheet, $row, $col); $tmp; $col++, $tmp = &xls_cell_value_nand($sheet, $row, $col))
    {
        $COLUMN_LIST{$tmp} = $col;
    }
    @all_column=sort (keys(%COLUMN_LIST));
    print "@all_column\n";

    for($row = 1,$tmp = &xls_cell_value_nand($sheet, $row, $COLUMN_LIST{Part_Number});$tmp;$row++,$tmp = &xls_cell_value_nand($sheet, $row, $COLUMN_LIST{Part_Number}))
    {
        foreach $i (@all_column){
            $MemoryDeviceList[$row-1]{$i}=&xls_cell_value_nand($sheet, $row, $COLUMN_LIST{$i});
        }
    }

    #if($DebugPrint eq "yes")
    {
        print "~~~~~~~~~EXCEL INFO~~~~~~~~~~~\n";
        for($index=0;$index<@MemoryDeviceList;$index++){
            print "index:$index\n";
            foreach $i (@all_column){
                printf ("%-15s:%-20s ",$i,$MemoryDeviceList[$index]->{$i});
            }
            print "\n";
        }
        print "~~~~~~~~~There are $index Nand Chips~~~~~~~~~~~\n";
    }
}

sub check_PartNumber()
{
    #1. check @PartNumbers do not have duplicate member
    my %hash;
    my $get = 0;
    my $input_part;
    foreach $i (@PartNumbers)
    {
        $hash{$i}++;
    }
    while(($key,$value) = each(%hash))
    {
        if($value >= 2)
        {&error_handler("Part Number: $key duplicates in custom_MemoryDevice.h\n", __FILE__, __LINE__);}
    }
    #2. check member of @PartNumber exists in MDL
    foreach $i (@PartNumbers)
    {
        $get =0;
        for($j=0;$j<@MemoryDeviceList;$j++){
            $input_part = $MemoryDeviceList[$j]->{Part_Number};
            $input_part =~ s/\s+//g;
            $get =1 if($input_part eq $i );
        }
        &error_handler("Part Number: $i not exist in MDL\n", __FILE__, __LINE__) if($get==0);
    }
}

sub GenNANDInfo()
{
    check_PartNumber(); #check PartNumber valid in custom_MemoryDevice.h

    for($iter=0;$iter<@MemoryDeviceList;$iter++){
        if(search_PartNumber( $MemoryDeviceList[$iter]->{Part_Number} ) )
	{
		my $ID_length=0;
		my $advance_option=0;
		my $ID=$MemoryDeviceList[$iter]->{Nand_ID};
		if(!exists($InFileChip{$ID})){
			if(length($ID)%2){
				print "The chip:$ID have wrong number!\n";
			}else{
				if($MemoryDeviceList[$iter]->{PageSize_B} > $psize)
				{
					$psize = $MemoryDeviceList[$iter]->{PageSize_B};
				}
				if((($MemoryDeviceList[$iter]->{BlockSize_KB}*1024)/$MemoryDeviceList[$iter]->{PageSize_B}) > $bsize)
				{
					$bsize = (($MemoryDeviceList[$iter]->{BlockSize_KB}*1024)/$MemoryDeviceList[$iter]->{PageSize_B});
					print "~~~~~~~~~~~~~~~~~~bsize = $bsize, $MemoryDeviceList[$iter]->{BlockSize_KB} ,$MemoryDeviceList[$iter]->{PageSize_B}~~~~~~~~~~~~~";
				}
				if($MemoryDeviceList[$iter]->{TotalSize_MB} > $csize)
				{
					$csize = $MemoryDeviceList[$iter]->{TotalSize_MB};
					if($MTK_HALF_NAND_SUPPORT eq "yes")
					{
						$csize = $csize/2;
					}
					print "~~~~~~~~~~~~~~~~~~csize = $csize, $MemoryDeviceList[$iter]->{TotalSize_MB} ,$MemoryDeviceList[$iter]->{TotalSize_MB}~~~~~~~~~~~~~";
				}
			}
		}
	}
    }
}

sub search_PartNumber()
{
    my $get=0;
    print "!!!!!!!!!!!!@_";
    print "\n";
    my ($input_part) = @_;
    $input_part =~ s/\s+//g;
    foreach $i (@PartNumbers)
    {
	    chomp($i);
	    if($i eq $input_part)
	    { $get =1 };
    }
    return $get;
}

sub check_boardconfig_partition_info {
	my $board_config_path = load_boardconfig_partition_info($CUSTOM_BASEPROJECT);
	my $board_config = &open_for_read("$board_config_path");
	my $idx;
	print "board_config_path = $board_config_path, $CUSTOM_BASEPROJECT\n";
	if ($board_config)
	{
		my $line;
		while (defined($line = <$board_config>))
		{
			$idx = 0;
			foreach my $part_name (@PARTITION_FIELD)
			{
				if ($line =~ /\A\s*BOARD_MTK_${part_name}_SIZE_KB\s*:=\s*(\d+)/i || $line =~ /\A\s*BOARD_MTK_${part_name}_SIZE_KB\s*:=\s*(NA)/i)
				{
					$SIZE_FIELD_KB[$idx] = $1;
					print "by project size $part_name = $1 KB\n";
				}
				if ($line =~ /\A\s*BOARD_MTK_MTK_TLC_SLC_RATIO_SIZE_KB\s*:=\s*(\d+)/i || $line =~ /\A\s*BOARD_MTK_MTK_TLC_SLC_RATIO_SIZE_KB\s*:=\s*(NA)/i)
				{
					$MTK_TLC_SLC_RATIO[1] = $1;
					$MTK_TLC_SLC_RATIO[0] = $MTK_TLC_SLC_RATIO[1] * 2;
					print "by project size MTK_TLC_SLC_RATIO[0] = $MTK_TLC_SLC_RATIO[0] MTK_TLC_SLC_RATIO[1] = $MTK_TLC_SLC_RATIO[1]\n";
				}
				$idx = $idx + 1;
			}
		}
		close $board_config;
	}else{
		print "This Project has no BoardConfig.mk \n";
	}
}

#****************************************************************************************
# subroutine:  load_boardconfig_partition_info
# return:      BoardConfig.mk path
# input:       BaseProject or Project name
#****************************************************************************************
sub load_boardconfig_partition_info {
	my ($Project_temp) = @_;
	my $PROJECT_BOARDCONFIG_FILENAME;

  {
		my $dir1 = "device";		#Project maynot @ /device/mediatek but /device/companyA.
	  my @arrayOfFirstLevelDirs;
	  my $SearchFile = 'BoardConfig.mk'; #Search File Name
	  my $i;
	  opendir(DIR, $dir1) or die $!;
	  #Search First Level path of the dir and save dirs in this path to @arrayOfFirstLevelDirs
	  while (my $file = readdir(DIR)) {
	  # A file test to check that it is a directory
	    next unless (-d "$dir1/$file");
	    next unless ( $file !~ m/^\./); #ignore dir prefixed with .
	    push @arrayOfFirstLevelDirs, "$dir1/$file";
	  }
	  closedir(DIR);
		foreach $i (@arrayOfFirstLevelDirs)
	  {
	    #search folder list+{project}/BoardConfig.mk existence
	    $PROJECT_BOARDCONFIG_FILENAME = $i."\/".$Project_temp."\/".$SearchFile;
	    if( -e $PROJECT_BOARDCONFIG_FILENAME)
	    {
	        print "Find: $PROJECT_BOARDCONFIG_FILENAME \n";
	        last;
	    }
	  }
	}
	return $PROJECT_BOARDCONFIG_FILENAME;
}

sub check_vendor_partition_config {
	my ($arg) = @_;
	my $board_config_path = load_boardconfig_partition_info($arg);
    my $board_config = &open_for_read("$board_config_path");
    if ($board_config)
    {
        my $line;
        while (defined($line = <$board_config>))
        {
            if ($line =~ /\A\s*TARGET_COPY_OUT_VENDOR\s*:=\s*(.*)/i)
            {
                if ($1 eq "vendor")
                {
                    $vendor_partition_exists = "Y";
                    print "by $arg enable vendor partition\n";
                }
                else
                {
                    $vendor_partition_exists = "N";
					print "by $arg disable vendor partition\n";
                }
            }
        }
        close $board_config;
    }else{
        print "This $arg has no BoardConfig.mk \n";
    }
}

sub xls_cell_value_nand()
{
    my($Sheet, $row, $col) = @_;
    my $cell = $Sheet->get_cell($row, $col);
    if (defined $cell)
    {
        return $cell->Value();
    } else
    {
        print "$Sheet: row=$row, col=$col undefined\n";
        return;
    }
}

sub get_chip_size {
    return $csize;
}

sub get_page_size {
    return ($psize, $bsize);
}
