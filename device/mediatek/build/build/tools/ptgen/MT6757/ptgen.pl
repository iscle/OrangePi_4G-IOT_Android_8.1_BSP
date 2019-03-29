#!/usr/local/bin/perl -w
#****************************************************************************
# Included Modules
#****************************************************************************
use strict;
use File::Basename;
use File::Path;
my $LOCAL_PATH;

BEGIN
{
    $LOCAL_PATH = dirname($0);
}
use lib "$LOCAL_PATH/../../Spreadsheet";
use lib "$LOCAL_PATH/../../";
require 'ParseExcel.pm';
use lib "$LOCAL_PATH/../../YAML/lib";
use YAML qw(LoadFile DumpFile Load Dump);

#****************************************************************************
# Global Variables
#****************************************************************************
my $Version = 4.0;

#my $ChangeHistory="3.1 AutoDetect eMMC Chip and Set MBR_Start_Address_KB\n";
#my $ChangeHistory = "3.2 Support OTP\n";
#my $ChangeHistory = "3.3 Support Shared SD Card\n";
#my $ChangeHistory = "3.4 CIP support\n";
#my $ChangeHistory = "3.5 Fix bug\n";
#my $ChangeHistory = "3.6 Support YAML format scatter file\n";
my $ChangeHistory = "\t1.Support YAML partition table
\t2.redesign xls partition table
\t3.Set partition size by project
\t4.auto align system/cache/userdata partition start address
\t5.revise all the code";

my @partition_layout_raw;
my @partition_layout_cooked;
my @BR_INDEX = ();
my %download_files;
my %sepcial_operation_type;
my %kernel_alias;       #alias for kernel c  file modify
my %region_map;
my %ArgList;
my %AlignPartList;
my @GeneratedFile;
my $Used_Size = 0;
my $PMT_END_NAME;       #end partition of no reserved partition
my $DebugPrint = "no";  # yes for debug; no for non-debug
my $PRODUCT_OUT;
my $ptgen_location;
my $Partition_layout_xls;
my $custom_out_prefix;
my $configs_out_prefix;
#pre-gen PGPT vraiables
my %SkipPartList;
my %mountPointMapList;

#****************************************************************************
# main flow : init_filepath
#****************************************************************************
&InitGlobalValue();
my $Partition_layout_yaml = "$ptgen_location/$ArgList{SHEET_NAME}.yaml";
my $COMBO_NAND_TOOLH      = "mediatek/external/mtd-utils/ubi-utils/combo_nand.h";

my $KernelH            = "$custom_out_prefix/kernel/partition.h";
my $LK_PartitionC      = "$custom_out_prefix/lk/partition.c";
my $PART_SIZE_LOCATION = "$configs_out_prefix/partition_size.mk";
my $COMBO_NAND_KERNELH = "$custom_out_prefix/common/combo_nand.h";

my $SCAT_FILE = "$PRODUCT_OUT/$ArgList{PLATFORM}_Android_scatter.txt";
my $PGPT_FILE = "$PRODUCT_OUT/PGPT";

#****************************************************************************
# main flow
#****************************************************************************
&ShowInfo(\%ArgList);
&clear_files();
if ($ArgList{PARTITION_TABLE_PLAIN_TEXT} ne "yes")
{
    &ReadExcelFile($Partition_layout_xls);
    &debug_print_layout("./out/1.log", \@partition_layout_raw);
    $Partition_layout_yaml = "$configs_out_prefix/$ArgList{SHEET_NAME}.yaml";
    mkpath($configs_out_prefix) if (! -d $configs_out_prefix);
    &GenPlainText($Partition_layout_yaml, \@partition_layout_raw);
}
@partition_layout_raw = @{&ReadPlainText($Partition_layout_yaml)};
&debug_print_layout("./out/2.log", \@partition_layout_raw);
@partition_layout_cooked = @{&ProcessRawPartitionLayoutData(\@partition_layout_raw)};
print "###################Final Data###################\n";
print Dump(\@partition_layout_cooked);

&GenYAMLScatFile();

if ($ArgList{EMMC_SUPPORT} eq "yes" || $ArgList{NAND_UBIFS_SUPPORT} eq "yes")
{
    &GenPartSizeFile_iniFile($PART_SIZE_LOCATION, $PRODUCT_OUT);
}

if ($ArgList{EMMC_SUPPORT} eq "yes")
{
   &GenPGPTFile();
}

if ($ArgList{EMMC_SUPPORT} ne "yes")
{
    &GenLK_PartitionC();
    &GenKernel_PartitionC();
}
if ($DebugPrint eq "yes")
{
    &print_rm_script();
}
print "^_^**********Ptgen Done********** ^_^\n";
printf "Generated files list:\n" . ("%s\n" x @GeneratedFile), @GeneratedFile;
exit 0;

#****************************************************************************
# subroutine:  InitAlians
# return:NONE
#****************************************************************************
sub InitGlobalValue
{
=alias
    #alias
    $preloader_alias{"SECCFG"}  = "SECURE";
    $preloader_alias{"SEC_RO"}  = "SECSTATIC";
    $preloader_alias{"ANDROID"} = "ANDSYSIMG";
    $preloader_alias{"USRDATA"} = "USER";

    $lk_xmodule_alias{"DSP_BL"}  = "DSP_DL";
    $lk_xmodule_alias{"SECCFG"}  = "SECURE";
    $lk_xmodule_alias{"SEC_RO"}  = "SECSTATIC";
    $lk_xmodule_alias{"EXPDB"}   = "APANIC";
    $lk_xmodule_alias{"ANDROID"} = "ANDSYSIMG";
    $lk_xmodule_alias{"USRDATA"} = "USER";

    $lk_alias{"BOOTIMG"} = "boot";
    $lk_alias{"ANDROID"} = "system";
    $lk_alias{"USRDATA"} = "userdata";

    $kernel_alias{"SECCFG"}  = "seccnfg";
    $kernel_alias{"BOOTIMG"} = "boot";
    $kernel_alias{"SEC_RO"}  = "secstatic";
    $kernel_alias{"ANDROID"} = "system";
    $kernel_alias{"USRDATA"} = "userdata";
=cut
    %region_map = (
                   EMMC_USER   => "EMMC_PART_USER",
                   EMMC_BOOT_1 => "EMMC_PART_BOOT1",
                   EMMC_BOOT_2 => "EMMC_PART_BOOT2",
                   EMMC_RPMB   => "EMMC_PART_RPMB",
                   EMMC_GP_1   => "EMMC_PART_GP1",
                   EMMC_GP_2   => "EMMC_PART_GP2",
                   EMMC_GP_3   => "EMMC_PART_GP3",
                   EMMC_GP_4   => "EMMC_PART_GP4",
                  );

    #Feature Options:parse argv from alps/vendor/mediatek/config/{project}/ProjectConfig.mk
    $ArgList{mtk_platform}               = lc($ENV{MTK_PLATFORM});
    $ArgList{PLATFORM}                   = $ENV{PLATFORM};
    $ArgList{platform}                   = lc($ENV{PLATFORM});
    $ArgList{BASE_PROJECT}               = $ENV{MTK_BASE_PROJECT};
    $ArgList{PROJECT}                    = $ENV{MTK_TARGET_PROJECT};
    $ArgList{FULL_PROJECT}               = $ENV{MTK_TARGET_PROJECT};
    $ArgList{PAGE_SIZE}                  = $ENV{MTK_NAND_PAGE_SIZE};
    ##for FPGA SD bootup(MTK_EMMC_SUPPORT=no) build pass
    #$ArgList{EMMC_SUPPORT}               = $ENV{MTK_EMMC_SUPPORT};
    $ArgList{EMMC_SUPPORT}               = "yes";
    $ArgList{EMMC_SUPPORT_OTP}           = $ENV{MTK_EMMC_SUPPORT_OTP};
    $ArgList{SHARED_SDCARD}              = $ENV{MTK_SHARED_SDCARD};
    $ArgList{CIP_SUPPORT}                = $ENV{MTK_CIP_SUPPORT};
    $ArgList{FAT_ON_NAND}                = $ENV{MTK_FAT_ON_NAND};
    $ArgList{NAND_UBIFS_SUPPORT}         = $ENV{MTK_NAND_UBIFS_SUPPORT};
    $ArgList{COMBO_NAND_SUPPORT}         = $ENV{MTK_COMBO_NAND_SUPPORT};
    $ArgList{PL_MODE}                    = $ENV{PL_MODE};
    $ArgList{PARTITION_TABLE_PLAIN_TEXT} = $ENV{MTK_PARTITION_TABLE_PLAIN_TEXT};
    $ArgList{TRUSTONIC_TEE_ATF_SUPPORT}  = $ENV{MTK_ATF_SUPPORT};
    $ArgList{TRUSTONIC_TEE_TEE_SUPPORT}  = $ENV{MTK_TEE_SUPPORT};
    $ArgList{PERSIST_PARTITION_SUPPORT}	 = $ENV{MTK_PERSIST_PARTITION_SUPPORT};
    $ArgList{DRM_KEY_MNG_SUPPORT}	       = $ENV{MTK_DRM_KEY_MNG_SUPPORT};
    $ArgList{PRELOADER_PROJECT}           = $ENV{PRELOADER_TARGET};
    $ArgList{FACTORY_RESET_PROTECTION_SUPPORT} = $ENV{MTK_FACTORY_RESET_PROTECTION_SUPPORT};
    $ArgList{EFUSE_WRITER_SUPPORT} = $ENV{MTK_EFUSE_WRITER_SUPPORT};
    $ArgList{TINYSYS_SCP_SUPPORT} = $ENV{MTK_TINYSYS_SCP_SUPPORT};
    $ArgList{SIM_LOCK_POWER_ON_WRITE_PROTECT} = $ENV{MTK_SIM_LOCK_POWER_ON_WRITE_PROTECT};
    $ArgList{CAM_SW_VERSION} = $ENV{MTK_CAM_SW_VERSION};
    $ArgList{DRAMC_BOOT_OPT} = $ENV{MTK_DRAMC_BOOT_OPT};
    $ArgList{DTBO_FEATURE}            = $ENV{MTK_DTBO_FEATURE};
    $ArgList{MTK_AB_OTA_UPDATER}         = $ENV{MTK_AB_OTA_UPDATER};
    $ArgList{ODM_SUPPORT}                = $ENV{TARGET_COPY_OUT_ODM};
    $ArgList{MTK_COMMON}                 = "common";
    $ArgList{MTK_GMO_RAM_OPTIMIZE}       = $ENV{MTK_GMO_RAM_OPTIMIZE};

    if ($ArgList{EMMC_SUPPORT} eq "yes")
    {
        $ArgList{PAGE_SIZE} = 2;
    }
    else
    {
        if ($ArgList{COMBO_NAND_SUPPORT} eq "yes")
        {
            $ArgList{PAGE_SIZE} = 4;
        }
        else
        {
            if ($ENV{MTK_NAND_PAGE_SIZE} =~ /(\d)K/)
            {
                $ArgList{PAGE_SIZE} = $1;
            }
        }
    }
    if (!$ENV{TARGET_BUILD_VARIANT})
    {
        $ArgList{TARGET_BUILD_VARIANT} = "eng";
    }
    else
    {
        $ArgList{TARGET_BUILD_VARIANT} = $ENV{TARGET_BUILD_VARIANT};
    }

    #filepath;
    $ptgen_location = "device/mediatek/build/build/tools/ptgen/$ArgList{PLATFORM}";
    if ($ArgList{PL_MODE})
    {
        $Partition_layout_xls = "$ptgen_location/test_partition_table_internal_$ArgList{PLATFORM}.xls";
        $ArgList{SHEET_NAME} = $ArgList{PL_MODE};
    }
    else
    {
        $Partition_layout_xls = "$ptgen_location/partition_table_$ArgList{PLATFORM}.xls";
        if ($ArgList{EMMC_SUPPORT} eq "yes")
        {
            $ArgList{SHEET_NAME} = "emmc";
            if ($ArgList{MTK_AB_OTA_UPDATER} eq "yes")
            {
                $ArgList{SHEET_NAME} = "emmc_ab";
            }
        }
        else
        {
            $ArgList{SHEET_NAME} = "nand";
        }
    }
    if (exists $ENV{"PTGEN_MK_OUT"})
    {
        $PRODUCT_OUT = $ENV{"PTGEN_MK_OUT"} . "/../..";
    }
    elsif (exists $ENV{OUT_DIR})
    {
        $PRODUCT_OUT = "$ENV{OUT_DIR}/target/product/$ArgList{PROJECT}";
    }
    else
    {
        $PRODUCT_OUT = "out/target/product/$ArgList{PROJECT}";
    }
    print "PRODUCT_OUT=$PRODUCT_OUT\n";

    $custom_out_prefix  = "$ENV{PTGEN_MK_OUT}";#"mediatek/custom/$ArgList{PROJECT}";
    $configs_out_prefix = "$ENV{PTGEN_MK_OUT}";#"mediatek/config/$ArgList{PROJECT}";

    #download files
    %download_files = (
                       preloader  => "preloader_$ArgList{PRELOADER_PROJECT}.bin",
                       srampreld => "sram_preloader_$ArgList{BASE_PROJECT}.bin",
                       mempreld  => "mem_preloader_$ArgList{BASE_PROJECT}.bin",
                       lk      => "lk.bin",
                       boot    => "boot.img",
                       recovery   => "recovery.img",
                       logo       => "logo.bin",
                       odmdtbo   => "dtbo.img",
                       vendor    => "vendor.img",
                       system    => "system.img",
                       cache      => "cache.img",
                       userdata    => "userdata.img",
                       custom     => "custom.img"
                      );

    #operation type
    %sepcial_operation_type = (
                               preloader => "BOOTLOADERS",
                               nvram     => "BINREGION",
                               proinfo  => "PROTECTED",
                               protect1 => "PROTECTED",
                               protect2 => "PROTECTED",
                               otp       => "RESERVED",
                               flashinfo   => "RESERVED",
                              );

    #1MB=1048576 byte align
    #8MB=8388608 byte align
    %AlignPartList = (
                      protect1 => 8388608,
                      persist => 8388608,
                      sec1 => 8388608,
                      seccfg => 8388608,
                      odm => 8388608,
                      vendor => 8388608,
                      system => 8388608,
                      cache   => 8388608,
                      userdata => 8388608,
                      intsd     => 8388608
                     );
    #skip entries in PGPT binary
    %SkipPartList = (
                      preloader => 1,
                      pgpt => 2,
                      sgpt => 3
                    );
    #mount point map table for partitions where mount point does not the same as partition name
    %mountPointMapList = (
                        protect1 => "protect_f",
                        protect2 => "protect_s",
                        odm => "",
                        system => "",
                        vendor => "",
                        userdata => "",
                     );
}

#****************************************************************************
# sub functions
#****************************************************************************
sub ShowInfo
{
    my $arglist = shift @_;
    printf "Partition Table Generator: Version=%.1f \nChangeHistory:\n%s\n", $Version, $ChangeHistory;
    print "*******************Arguments*********************\n";
    foreach my $key (sort keys %{$arglist})
    {
        if (!$arglist->{$key})
        {
            print "\t$key \t= -NO VALUE-\n";
        }
        else
        {
            print "\t$key \t= $arglist->{$key}\n";
        }
    }
    print "*******************Arguments*********************\n";
}

sub ReadPlainText
{
    my $filepath = shift @_;
    my $yaml;
    eval { $yaml = LoadFile($filepath); };
    if ($@)
    {
        &error_handler("Read YAML Partition layout fail", __FILE__, __LINE__, $@);
    }
    return $yaml;
}

sub GenPlainText
{
    my ($filepath, $list) = @_;
    if (-e $filepath)
    {
        my $dirpath = substr($filepath, 0, rindex($filepath, "/"));
        chmod(0777, $dirpath) or &error_handler("chmod 0777 $dirpath fail", __FILE__, __LINE__);
        if (!unlink $filepath)
        {
            &error_handler("remove $filepath fail ", __FILE__, __LINE__);
        }
    }
    eval { DumpFile($filepath, $list) };
    if ($@)
    {
        &error_handler("DumpFile from YAML fail ", __FILE__, __LINE__, $@);
    }
}

sub GetABPartBaseName
{
    my $original_name = shift @_;
    my $new_name;
    if ($original_name  =~ /^(.*)_[ab]$/)
    {
        $new_name = $1;
#        printf "Find AB partition named %s, using %s for auto info update\n", $original_name, $new_name;
    }
    else
    {
        $new_name = $original_name;
    }
    return $new_name;
}

#****************************************************************************
# subroutine:  ReadExcelFile
# return:
#****************************************************************************

sub ReadExcelFile
{
    my $excelFilePath = shift @_;
    my @col_name      = ();
    my @col_sub_name  = ();
    my $PartitonBook  = Spreadsheet::ParseExcel->new()->Parse($excelFilePath);
    my $sheet         = $PartitonBook->Worksheet($ArgList{SHEET_NAME});
    if (!$sheet)
    {
        &error_handler("ptgen open sheet=$ArgList{SHEET_NAME} fail in $excelFilePath ", __FILE__, __LINE__);
    }
    my ($row_min, $row_max) = $sheet->row_range();    #$row_min=0
    my ($col_min, $col_max) = $sheet->col_range();    #$col_min=0
    my $row_cur = $row_min;
    foreach my $col_idx ($col_min .. $col_max)
    {
        push(@col_name,     &xls_cell_value($sheet, $row_cur,     $col_idx));
        push(@col_sub_name, &xls_cell_value($sheet, $row_cur + 1, $col_idx));
    }
    $row_cur += 2;
    foreach my $col_idx ($col_min .. $col_max)
    {
        foreach my $row_idx ($row_cur .. $row_max)
        {
            my $value = &xls_cell_value($sheet, $row_idx, $col_idx);
            if ($col_name[$col_idx - $col_min])
            {
                $partition_layout_raw[$row_idx - $row_cur]->{$col_name[$col_idx - $col_min]} = $value;
            }
            else
            {
                my $pre_col_value = $partition_layout_raw[$row_idx - $row_cur]->{$col_name[$col_idx - $col_min - 1]};
                if (!$value)
                {
                    $value = $pre_col_value;
                }
                $partition_layout_raw[$row_idx - $row_cur]->{$col_name[$col_idx - $col_min - 1]} = {$col_sub_name[$col_idx - $col_min - 1] => $pre_col_value, $col_sub_name[$col_idx - $col_min] => $value};
            }
        }
    }
}

sub ProcessRawPartitionLayoutData
{
    my @partition_layout_process = @{shift @_};
    my $partition_idx            = 0;
    #chose part attribute
    for ($partition_idx = 0 ; $partition_idx < @partition_layout_process ; $partition_idx++)
    {
        foreach my $col_name_idx (keys %{$partition_layout_process[$partition_idx]})
        {
            if (ref $partition_layout_process[$partition_idx]->{$col_name_idx})
            {
                if ($ArgList{TARGET_BUILD_VARIANT} eq "eng")
                {
                    $partition_layout_process[$partition_idx]->{$col_name_idx} = $partition_layout_process[$partition_idx]->{$col_name_idx}->{eng};
                }
                else
                {
                    $partition_layout_process[$partition_idx]->{$col_name_idx} = $partition_layout_process[$partition_idx]->{$col_name_idx}->{user};
                }
            }
        }
    }
    &debug_print_layout("./out/3.log", \@partition_layout_process);

    #modify size for some part by device/mediatek/common/boardconfig
    my $board_config_path = load_boardconfig_partition_info($ArgList{MTK_COMMON});
    my $board_config = &open_for_read("$board_config_path");
    my $vendor_partition_exists = "N";
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
                    print "by common enable vendor partition\n";
                }
                else
                {
                    $vendor_partition_exists = "N";
                }
            }
            foreach my $part (@partition_layout_process)
            {
                my $part_name = GetABPartBaseName($part->{Partition_Name});
                if ($line =~ /\A\s*BOARD_MTK_${part_name}_SIZE_KB\s*:=\s*(\d+)/i || $line =~ /\A\s*BOARD_MTK_${part_name}_SIZE_KB\s*:=\s*(NA)/i)
                {
                    $part->{Size_KB} = $1;
                    print "by common size $part->{Partition_Name} = $1 KB\n";
                }
                if ($ArgList{MTK_GMO_RAM_OPTIMIZE} eq "yes")
                {
                    if ($line =~ /\A\s*BOARD_MTK_GMO_${part_name}_SIZE_KB\s*:=\s*(\d+)/i || $line =~ /\A\s*BOARD_MTK_GMO_${part_name}_SIZE_KB\s*:=\s*(NA)/i)
                    {
                        $part->{Size_KB} = $1;
                        print "by common size $part->{Partition_Name} (GMO) = $1 KB\n";
                    }
                }
            }
        }
        close $board_config;
    }else{
        print "This Common has no BoardConfig.mk \n";
    }

    #modify size for some part by project
    $board_config_path = load_boardconfig_partition_info($ArgList{mtk_platform});
    $board_config = &open_for_read("$board_config_path");
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
                    print "by platform enable vendor partition\n";
                }
                else
                {
                    $vendor_partition_exists = "N";
                }
            }
            foreach my $part (@partition_layout_process)
            {
                my $part_name = GetABPartBaseName($part->{Partition_Name});
                if ($line =~ /\A\s*BOARD_MTK_${part_name}_SIZE_KB\s*:=\s*(\d+)/i || $line =~ /\A\s*BOARD_MTK_${part_name}_SIZE_KB\s*:=\s*(NA)/i)
                {
                    $part->{Size_KB} = $1;
                    print "by platform size $part->{Partition_Name} = $1 KB\n";
                }
                if ($ArgList{MTK_GMO_RAM_OPTIMIZE} eq "yes")
                {
                    if ($line =~ /\A\s*BOARD_MTK_GMO_${part_name}_SIZE_KB\s*:=\s*(\d+)/i || $line =~ /\A\s*BOARD_MTK_GMO_${part_name}_SIZE_KB\s*:=\s*(NA)/i)
                    {
                        $part->{Size_KB} = $1;
                        print "by platform size $part->{Partition_Name} (GMO) = $1 KB\n";
                    }
                }
            }
        }
        close $board_config;
    }else{
        print "This Platform has no BoardConfig.mk \n";
    }


    $board_config_path = load_boardconfig_partition_info($ArgList{BASE_PROJECT});
    $board_config = &open_for_read("$board_config_path");
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
                    print "by project enable vendor partition\n";
                }
                else
                {
                    $vendor_partition_exists = "N";
                    print "by project disable vendor partition\n";
                }
            }
            foreach my $part (@partition_layout_process)
            {
                my $part_name = GetABPartBaseName($part->{Partition_Name});
                if ($line =~ /\A\s*BOARD_MTK_${part_name}_SIZE_KB\s*:=\s*(\d+)/i || $line =~ /\A\s*BOARD_MTK_${part_name}_SIZE_KB\s*:=\s*(NA)/i)
                {
                    $part->{Size_KB} = $1;
                    print "by project size $part->{Partition_Name} = $1 KB\n";
                }
                if ($ArgList{MTK_GMO_RAM_OPTIMIZE} eq "yes")
                {
                    if ($line =~ /\A\s*BOARD_MTK_GMO_${part_name}_SIZE_KB\s*:=\s*(\d+)/i || $line =~ /\A\s*BOARD_MTK_GMO_${part_name}_SIZE_KB\s*:=\s*(NA)/i)
                    {
                        $part->{Size_KB} = $1;
                        print "by project size $part->{Partition_Name} (GMO) = $1 KB\n";
                    }
                }
            }
        }
        close $board_config;
    }else{
        print "This Project has no BoardConfig.mk \n";
    }

    if ($ArgList{FULL_PROJECT} ne $ArgList{BASE_PROJECT})
    {
        my $flavor_board_config_path = load_boardconfig_partition_info($ArgList{FULL_PROJECT});
        my $flavor_board_config = &open_for_read("$flavor_board_config_path");
        if ($flavor_board_config)
        {
            my $line;
            while (defined($line = <$flavor_board_config>))
            {
                if ($line =~ /\A\s*TARGET_COPY_OUT_VENDOR\s*:=\s*(.*)/i)
                {
                    if ($1 eq "vendor")
                    {
                        $vendor_partition_exists = "Y";
                        print "by project enable vendor partition\n";
                    }
                    else
                    {
                        $vendor_partition_exists = "N";
                        print "by project disable vendor partition\n";
                    }
                }
                foreach my $part (@partition_layout_process)
                {
                    my $part_name = GetABPartBaseName($part->{Partition_Name});
                    if ($line =~ /\A\s*BOARD_MTK_${part_name}_SIZE_KB\s*:=\s*(\d+)/i || $line =~ /\A\s*BOARD_MTK_${part_name}_SIZE_KB\s*:=\s*(NA)/i)
                    {
                        $part->{Size_KB} = $1;
                        print "by flavor project size $part->{Partition_Name} = $1 KB\n";
                    }
                    if ($ArgList{MTK_GMO_RAM_OPTIMIZE} eq "yes")
                    {
                        if ($line =~ /\A\s*BOARD_MTK_GMO_${part_name}_SIZE_KB\s*:=\s*(\d+)/i || $line =~ /\A\s*BOARD_MTK_GMO_${part_name}_SIZE_KB\s*:=\s*(NA)/i)
                        {
                            $part->{Size_KB} = $1;
                            print "by flavor project size $part->{Partition_Name} (GMO) = $1 KB\n";
                        }
                    }
                }
            }
            close $flavor_board_config;
        }
    }else{
        print "This flavor Project has no BoardConfig.mk \n";
    }
    &debug_print_layout("./out/5.log", \@partition_layout_process);

    #delete some partitions
    for ($partition_idx = 0 ; $partition_idx < @partition_layout_process ; $partition_idx++)
    {
        if ($partition_layout_process[$partition_idx]->{Partition_Name} eq "intsd")
        {
            if (($ArgList{EMMC_SUPPORT} eq "yes" && $ArgList{SHARED_SDCARD} eq "yes") || ($ArgList{EMMC_SUPPORT} ne "yes" && $ArgList{FAT_ON_NAND} ne "yes"))
            {
                splice @partition_layout_process, $partition_idx, 1;
                $partition_idx--;
            }
        }
        if ($partition_layout_process[$partition_idx]->{Partition_Name} eq "otp")
        {
            if ($ArgList{EMMC_SUPPORT} eq "yes" && $ArgList{EMMC_SUPPORT_OTP} ne "yes")
            {
                splice @partition_layout_process, $partition_idx, 1;
                $partition_idx--;
            }
        }
        if ($partition_layout_process[$partition_idx]->{Partition_Name} eq "custom")
        {
            if ($ArgList{CIP_SUPPORT} ne "yes")
            {
                splice @partition_layout_process, $partition_idx, 1;
                $partition_idx--;
            }
        }
        if ($partition_layout_process[$partition_idx]->{Partition_Name} eq "persist")
        {
            if ($ArgList{PERSIST_PARTITION_SUPPORT} ne "yes" && $ArgList{DRM_KEY_MNG_SUPPORT} ne "yes")
            {
                splice @partition_layout_process, $partition_idx, 1;
                $partition_idx--;
            }
        }
        if ($partition_layout_process[$partition_idx]->{Partition_Name} =~ /^tee([12]|_[ab])$/i)
        {
            if ($ArgList{TRUSTONIC_TEE_ATF_SUPPORT} ne "yes" && $ArgList{TRUSTONIC_TEE_TEE_SUPPORT} ne "yes")
            {
                splice @partition_layout_process, $partition_idx, 1;
                $partition_idx--;
            }
        }
        if ($partition_layout_process[$partition_idx]->{Partition_Name} eq "frp")
        {
            if ($ArgList{FACTORY_RESET_PROTECTION_SUPPORT} ne "yes")
            {
                splice @partition_layout_process, $partition_idx, 1;
                $partition_idx--;
            }
        }
        if ($partition_layout_process[$partition_idx]->{Partition_Name} eq "efuse")
        {
            if ($ArgList{EFUSE_WRITER_SUPPORT} ne "yes")
            {
                splice @partition_layout_process, $partition_idx, 1;
                $partition_idx--;
            }
        }
        if ($partition_layout_process[$partition_idx]->{Partition_Name} =~ /^scp([12]|_[ab])$/i)
        {
            if ($ArgList{TINYSYS_SCP_SUPPORT} ne "yes")
            {
                splice @partition_layout_process, $partition_idx, 1;
                $partition_idx--;
            }
        }
        if ($partition_layout_process[$partition_idx]->{Partition_Name} eq "boot_para")
        {
            if ($ArgList{DRAMC_BOOT_OPT} ne "yes")
            {
                splice @partition_layout_process, $partition_idx, 1;
                $partition_idx--;
            }
        }
        if ($partition_layout_process[$partition_idx]->{Partition_Name} =~ /^odm(_[ab])?$/i)
        {
            if ($ArgList{ODM_SUPPORT} ne "yes")
            {
                splice @partition_layout_process, $partition_idx, 1;
                $partition_idx--;
            }
        }
        if ($partition_layout_process[$partition_idx]->{Partition_Name} =~ /^vendor(_[ab])?$/i)
        {
            if ($vendor_partition_exists eq "N")
            {
                splice @partition_layout_process, $partition_idx, 1;
                $partition_idx--;
            }
        }
        if ($partition_layout_process[$partition_idx]->{Partition_Name} =~ /^odmdtbo(_[ab])?$/i)
        {
            if ($ArgList{DTBO_FEATURE} ne "yes")
            {
                splice @partition_layout_process, $partition_idx, 1;
                $partition_idx--;
            }
        }
        if ($partition_layout_process[$partition_idx]->{Partition_Name} =~ /^(cache|recovery)$/i)
        {
            if ($ArgList{MTK_AB_OTA_UPDATER} eq "yes")
            {
                splice @partition_layout_process, $partition_idx, 1;
                $partition_idx--;
            }
        }
        if ($partition_layout_process[$partition_idx]->{Size_KB} eq "NA")
        {
            splice @partition_layout_process, $partition_idx, 1;
            $partition_idx--;
        }
    }
    &debug_print_layout("./out/4.log", \@partition_layout_process);

    #calculate start_address of partition $partition_layout_process[$partition_idx]->{Start_Addr} by Byte
    #$partition_layout_process[$partition_idx]->{Start_Addr_Text} by byte in 0x format
    for ($partition_idx = @partition_layout_process - 1 ; $partition_idx >= 0 ; $partition_idx--)
    {
        if ($partition_layout_process[$partition_idx]->{Reserved} eq "Y")
        {
            if ($partition_idx != @partition_layout_process - 1)
            {
                $partition_layout_process[$partition_idx]->{Start_Addr} = $partition_layout_process[$partition_idx + 1]->{Start_Addr} + $partition_layout_process[$partition_idx]->{Size_KB} * 1024;
            }
            else
            {
                $partition_layout_process[$partition_idx]->{Start_Addr} = $partition_layout_process[$partition_idx]->{Size_KB} * 1024;
            }
        }
        else
        {
            $PMT_END_NAME = $partition_layout_process[$partition_idx]->{Partition_Name};
            last;
        }
    }

    for ($partition_idx = 0 ; $partition_idx < @partition_layout_process ; $partition_idx++)
    {
        if ($partition_layout_process[$partition_idx]->{Reserved} eq "N")
        {
            if ($partition_idx != 0 && $partition_layout_process[$partition_idx]->{Region} eq $partition_layout_process[$partition_idx - 1]->{Region})
            {
                my $st_addr = $partition_layout_process[$partition_idx - 1]->{Start_Addr} + $partition_layout_process[$partition_idx - 1]->{Size_KB} * 1024;
                my $raw_name = GetABPartBaseName($partition_layout_process[$partition_idx]->{Partition_Name});

                #auto adjust to alignment
                if (exists $AlignPartList{$raw_name})
                {
                    #if MTK_SIM_LOCK_POWER_ON_WRITE_PROTECT=no, do not make protect1 ~ protect2 8MB aligned for power on write protect to save space
                    #if MTK_PERSIST_PARTITION_SUPPORT=no & MTK_DRM_KEY_MNG_SUPPORT=no, do not make persist 8MB aligned for power on write protect to save space
                    if(($partition_layout_process[$partition_idx]->{Partition_Name} eq "protect1" && $ArgList{MTK_SIM_LOCK_POWER_ON_WRITE_PROTECT} ne "yes") ||($partition_layout_process[$partition_idx]->{Partition_Name} eq "persist" && $ArgList{PERSIST_PARTITION_SUPPORT} ne "yes" && $ArgList{DRM_KEY_MNG_SUPPORT} ne "yes"))
                    {
                        #skip 8MB aligned adjustment if not necessary
                    }
                    else
                    {
                        # print "got aligned partition $partition_layout_process[$partition_idx]->{Partition_Name}\n";
                        if ($st_addr % scalar($AlignPartList{$raw_name}) != 0)
                        {
                             printf("Need adjust start address for %s, because it is 0x%x now. ", $partition_layout_process[$partition_idx]->{Partition_Name}, $st_addr % scalar($AlignPartList{$raw_name}));
                             my $pad_size = $AlignPartList{$raw_name} - $st_addr % scalar($AlignPartList{$raw_name});
                             if ($pad_size % 1024 != 0)
                             {
                                 &error_handler("pad size is not KB align,please review the size of $AlignPartList{$raw_name}", __FILE__, __LINE__);
                             }
                             $partition_layout_process[$partition_idx - 1]->{Size_KB} = $partition_layout_process[$partition_idx - 1]->{Size_KB} + $pad_size / 1024;
                             $st_addr = $partition_layout_process[$partition_idx - 1]->{Start_Addr} + $partition_layout_process[$partition_idx - 1]->{Size_KB} * 1024;
                             printf("pad size is 0x%x, and pre part [%s]size is 0x%x \n", $pad_size, $partition_layout_process[$partition_idx - 1]->{Partition_Name}, $partition_layout_process[$partition_idx - 1]->{Size_KB} * 1024);
                        }
                    }
                }
                $partition_layout_process[$partition_idx]->{Start_Addr} = $st_addr;
            }
            else
            {
                $partition_layout_process[$partition_idx]->{Start_Addr} = 0;
            }
            $partition_layout_process[$partition_idx]->{Start_Addr_Text} = sprintf("0x%x", $partition_layout_process[$partition_idx]->{Start_Addr});
        }
        else
        {
            if ($ArgList{EMMC_SUPPORT} eq "yes")
            {
                $partition_layout_process[$partition_idx]->{Start_Addr_Text} = sprintf("0xFFFF%04x", $partition_layout_process[$partition_idx]->{Start_Addr} / (128 * 1024));
            }
            else
            {
                $partition_layout_process[$partition_idx]->{Start_Addr_Text} = sprintf("0xFFFF%04x", $partition_layout_process[$partition_idx]->{Start_Addr} / (64 * $ArgList{PAGE_SIZE} * 1024));
            }
        }
        $Used_Size += $partition_layout_process[$partition_idx]->{Size_KB};
    }
    &debug_print_layout("./out/7.log", \@partition_layout_process);
    printf "\$Used_Size=0x%x KB = %d KB =%.2f MB\n", $Used_Size, $Used_Size, $Used_Size / (1024);

    #process AUTO flag and add index
    my $part;
    for ($partition_idx = 0 ; $partition_idx < @partition_layout_process ; $partition_idx++)
    {
        $part=$partition_layout_process[$partition_idx];
        my $raw_name = GetABPartBaseName($part->{Partition_Name});
        if ($part->{Download} eq "N")
        {
            $part->{Download_File} = "NONE";
        }
        elsif ($part->{Download_File} eq "AUTO")
        {
            if (exists $download_files{$raw_name})
            {
                $part->{Download_File} = $download_files{$raw_name};
            }
            else
            {
                if ($part->{Type} eq "Raw data")
                {
                    $part->{Download_File} = sprintf("%s.bin", lc($raw_name));
                }
                else
                {
                    $part->{Download_File} = sprintf("%s.img", lc($raw_name));
                }
            }
        }
        if ($part->{Operation_Type} eq "AUTO")
        {
            if (exists $sepcial_operation_type{$raw_name})
            {
                $part->{Operation_Type} = $sepcial_operation_type{$raw_name};
            }
            elsif ($part->{Reserved} eq "Y")
            {
                $part->{Operation_Type} = "RESERVED";
            }
            else
            {
                if ($part->{Download} eq "N")
                {
                    $part->{Operation_Type} = "INVISIBLE";
                }
                else
                {
                    $part->{Operation_Type} = "UPDATE";
                }
            }
        }
    }
    &debug_print_layout("./out/8.log", \@partition_layout_process);
    return \@partition_layout_process;
}


sub GenYAMLScatFile()
{
    my $ScatterFileFH = &open_for_rw($SCAT_FILE);
    my %Scatter_Info;
    my $iter;
    for ($iter = 0 ; $iter < @partition_layout_cooked ; $iter++)
    {
        my $part = $partition_layout_cooked[$iter];
        $Scatter_Info{$part->{Partition_Name}} = {
                                                  partition_index     => $iter,
                                                  physical_start_addr => $part->{Start_Addr_Text},
                                                  linear_start_addr   => $part->{Start_Addr_Text},
                                                  partition_size      => sprintf("0x%x", $part->{Size_KB} * 1024),
                                                  file_name           => $part->{Download_File},
                                                  operation_type      => $part->{Operation_Type}
                                                 };

        if ($ArgList{EMMC_SUPPORT} eq "yes")
        {
            if ($part->{Type} eq "Raw data")
            {
                $Scatter_Info{$part->{Partition_Name}}{type} = "NORMAL_ROM";
            }
            else
            {
                $Scatter_Info{$part->{Partition_Name}}{type} = "EXT4_IMG";
            }
        }
        else
        {
            if ($part->{Type} eq "Raw data")
            {
                $Scatter_Info{$part->{Partition_Name}}{type} = "NORMAL_ROM";
            }
            else
            {
                if ($ArgList{NAND_UBIFS_SUPPORT} eq "yes")
                {
                    $Scatter_Info{$part->{Partition_Name}}{type} = "UBI_IMG";
                }
                else
                {
                    $Scatter_Info{$part->{Partition_Name}}{type} = "YAFFS_IMG";
                }
            }
        }
        if ($part->{Partition_Name} eq "preloader")
        {
            $Scatter_Info{$part->{Partition_Name}}{type} = "SV5_BL_BIN";
        }

        if ($ArgList{EMMC_SUPPORT} eq "yes")
        {
            $Scatter_Info{$part->{Partition_Name}}{region} = $part->{Region};
        }
        else
        {
            $Scatter_Info{$part->{Partition_Name}}{region} = "NONE";
        }

        if ($ArgList{EMMC_SUPPORT} eq "yes")
        {
            $Scatter_Info{$part->{Partition_Name}}{storage} = "HW_STORAGE_EMMC";
        }
        else
        {
            $Scatter_Info{$part->{Partition_Name}}{storage} = "HW_STORAGE_NAND";
        }

        if ($part->{Reserved} eq "Y")
        {
            $Scatter_Info{$part->{Partition_Name}}{boundary_check} = "false";
            $Scatter_Info{$part->{Partition_Name}}{is_reserved}    = "true";
        }
        else
        {
            $Scatter_Info{$part->{Partition_Name}}{boundary_check} = "true";
            $Scatter_Info{$part->{Partition_Name}}{is_reserved}    = "false";
        }

        if ($part->{Download} eq "N")
        {
            $Scatter_Info{$part->{Partition_Name}}{is_download} = "false";
        }
        else
        {
            $Scatter_Info{$part->{Partition_Name}}{is_download} = "true";
        }

        if ($part->{OTA_Update} eq "N")
        {
            $Scatter_Info{$part->{Partition_Name}}{is_upgradable} = "false";
        }
        else
        {
            $Scatter_Info{$part->{Partition_Name}}{is_upgradable} = "true";
        }

        if ($part->{EmptyBoot_Needed} eq "N")
        {
            $Scatter_Info{$part->{Partition_Name}}{empty_boot_needed} = "false";
        }
        else
        {
            $Scatter_Info{$part->{Partition_Name}}{empty_boot_needed} = "true";
        }
    }
    my $Head1 = <<"__TEMPLATE";
############################################################################################################
#
#  General Setting
#
############################################################################################################
__TEMPLATE

    my $Head2 = <<"__TEMPLATE";
############################################################################################################
#
#  Layout Setting
#
############################################################################################################
__TEMPLATE

    my ${FirstDashes}       = "- ";
    my ${FirstSpaceSymbol}  = "  ";
    my ${SecondSpaceSymbol} = "      ";
    my ${SecondDashes}      = "    - ";
    my ${colon}             = ": ";
    print $ScatterFileFH $Head1;
    print $ScatterFileFH "${FirstDashes}general${colon}MTK_PLATFORM_CFG\n";
    print $ScatterFileFH "${FirstSpaceSymbol}info${colon}\n";
    print $ScatterFileFH "${SecondDashes}config_version${colon}V1.1.2\n";
    if ($ArgList{CAM_SW_VERSION} eq "ver2")
    {
        print $ScatterFileFH "${SecondSpaceSymbol}platform${colon}$ArgList{PLATFORM}D\n";
    }
    else
    {
        print $ScatterFileFH "${SecondSpaceSymbol}platform${colon}$ArgList{PLATFORM}\n";
    }
    print $ScatterFileFH "${SecondSpaceSymbol}project${colon}$ArgList{FULL_PROJECT}\n";

    if ($ArgList{EMMC_SUPPORT} eq "yes")
    {
        print $ScatterFileFH "${SecondSpaceSymbol}storage${colon}EMMC\n";
        print $ScatterFileFH "${SecondSpaceSymbol}boot_channel${colon}MSDC_0\n";
        printf $ScatterFileFH ("${SecondSpaceSymbol}block_size${colon}0x%x\n", 2 * 64 * 1024);
    }
    else
    {
        print $ScatterFileFH "${SecondSpaceSymbol}storage${colon}NAND\n";
        print $ScatterFileFH "${SecondSpaceSymbol}boot_channel${colon}NONE\n";
        printf $ScatterFileFH ("${SecondSpaceSymbol}block_size${colon}0x%x\n", $ArgList{PAGE_SIZE} * 64 * 1024);
    }
    print $ScatterFileFH $Head2;
    foreach my $part (@partition_layout_cooked)
    {
        print $ScatterFileFH "${FirstDashes}partition_index${colon}SYS$Scatter_Info{$part->{Partition_Name}}{partition_index}\n";
        print $ScatterFileFH "${FirstSpaceSymbol}partition_name${colon}$part->{Partition_Name}\n";
        print $ScatterFileFH "${FirstSpaceSymbol}file_name${colon}$Scatter_Info{$part->{Partition_Name}}{file_name}\n";
        print $ScatterFileFH "${FirstSpaceSymbol}is_download${colon}$Scatter_Info{$part->{Partition_Name}}{is_download}\n";
        print $ScatterFileFH "${FirstSpaceSymbol}type${colon}$Scatter_Info{$part->{Partition_Name}}{type}\n";
        print $ScatterFileFH "${FirstSpaceSymbol}linear_start_addr${colon}$Scatter_Info{$part->{Partition_Name}}{linear_start_addr}\n";
        print $ScatterFileFH "${FirstSpaceSymbol}physical_start_addr${colon}$Scatter_Info{$part->{Partition_Name}}{physical_start_addr}\n";
        print $ScatterFileFH "${FirstSpaceSymbol}partition_size${colon}$Scatter_Info{$part->{Partition_Name}}{partition_size}\n";
        print $ScatterFileFH "${FirstSpaceSymbol}region${colon}$Scatter_Info{$part->{Partition_Name}}{region}\n";
        print $ScatterFileFH "${FirstSpaceSymbol}storage${colon}$Scatter_Info{$part->{Partition_Name}}{storage}\n";
        print $ScatterFileFH "${FirstSpaceSymbol}boundary_check${colon}$Scatter_Info{$part->{Partition_Name}}{boundary_check}\n";
        print $ScatterFileFH "${FirstSpaceSymbol}is_reserved${colon}$Scatter_Info{$part->{Partition_Name}}{is_reserved}\n";
        print $ScatterFileFH "${FirstSpaceSymbol}operation_type${colon}$Scatter_Info{$part->{Partition_Name}}{operation_type}\n";
        print $ScatterFileFH "${FirstSpaceSymbol}is_upgradable${colon}$Scatter_Info{$part->{Partition_Name}}{is_upgradable}\n";
        print $ScatterFileFH "${FirstSpaceSymbol}empty_boot_needed${colon}$Scatter_Info{$part->{Partition_Name}}{empty_boot_needed}\n";
        print $ScatterFileFH "${FirstSpaceSymbol}reserve${colon}0x00\n\n";
    }
    close $ScatterFileFH;
}

#****************************************************************************************
# subroutine:  GenPartSizeFile;
# return:
#****************************************************************************************

sub GenPartSizeFile_iniFile
{
    my ($part_size, $inifilepath) = @_;
    my $part_size_fh = &open_for_rw($part_size);

    my $Total_Size = 512 * 1024 * 1024;    #Hard Code 512MB for 4+2 project FIX ME!!!!!
    my $temp;
    my $index = 0;
    my $vol_size;
    my $min_ubi_vol_size;
    my $PEB;
    my $LEB;
    my $IOSIZE;

    if ($ArgList{NAND_UBIFS_SUPPORT} eq "yes" && $ArgList{EMMC_SUPPORT} ne "yes")
    {
        $IOSIZE           = $ArgList{PAGE_SIZE} * 1024;
        $PEB              = $IOSIZE * 64;
        $LEB              = $IOSIZE * 62;
        $min_ubi_vol_size = $PEB * 28;
        printf $part_size_fh ("BOARD_UBIFS_MIN_IO_SIZE:=%d\n",             $IOSIZE);
        printf $part_size_fh ("BOARD_FLASH_BLOCK_SIZE:=%d\n",              $PEB);
        printf $part_size_fh ("BOARD_UBIFS_VID_HDR_OFFSET:=%d\n",          $IOSIZE);
        printf $part_size_fh ("BOARD_UBIFS_LOGICAL_ERASEBLOCK_SIZE:=%d\n", $LEB);

        if ($ArgList{COMBO_NAND_SUPPORT} eq "yes")
        {
            my $combo_nand_kernel = &open_for_rw($COMBO_NAND_KERNELH);
            printf $combo_nand_kernel ("#define COMBO_NAND_BLOCK_SIZE %d\n", $PEB);
            printf $combo_nand_kernel ("#define COMBO_NAND_PAGE_SIZE %d\n",  $IOSIZE);
            close $combo_nand_kernel;

            my $combo_nand_tool = &open_for_rw($COMBO_NAND_TOOLH);
            printf $combo_nand_tool ("#define COMBO_NAND_BLOCK_SIZE %d\n", $PEB);
            printf $combo_nand_tool ("#define COMBO_NAND_PAGE_SIZE %d\n",  $IOSIZE);
            close $combo_nand_tool;
        }
        $Total_Size = $Total_Size - $Used_Size - $PEB * 2;    #PMT need 2 block
        print "In UBIFS, auto size partition have byte\n";
    }

    foreach my $part (@partition_layout_cooked)
    {
        if ($part->{Type} eq "EXT4" || $part->{Type} eq "FAT")
        {
            $temp = $part->{Size_KB} * 1024;
            my $raw_name = GetABPartBaseName($part->{Partition_Name});
            if ($part->{Partition_Name} ne "${raw_name}_b")   # do not print for _b partition, its redundant setting
            {
                printf $part_size_fh ("BOARD_%sIMAGE_PARTITION_SIZE:=%d\n",uc($raw_name),$temp);
                if (exists $mountPointMapList{$raw_name})
                {
                    if ($mountPointMapList{$raw_name} ne "")
                    {
                        printf $part_size_fh ("MTK_BOARD_ROOT_EXTRA_FOLDERS += %s\n", $mountPointMapList{$raw_name});
                    }
                }
                else
                {
                    printf $part_size_fh ("MTK_BOARD_ROOT_EXTRA_FOLDERS += %s\n", $raw_name);
                }
            }
        }

        if ($ArgList{NAND_UBIFS_SUPPORT} eq "yes" && $part->{Type} eq "UBIFS" && $ArgList{EMMC_SUPPORT} ne "yes")
        {
            my $part_name = lc($part->{Partition_Name});
            if ($part->{Size_KB} == 0)
            {
                $part->{Size_KB} = $Total_Size / 1024;
            }

            # UBI reserve 6 block
            $vol_size = (int($part->{Size_KB} * 1024 / ${PEB}) - 6) * $LEB;
            if ($min_ubi_vol_size > $part->{Size_KB} * 1024)
            {
                &error_handler("$part->{Partition_Name} is too small, UBI partition is at least $min_ubi_vol_size byte, Now it is $part->{Size_KB} KiB", __FILE__, __LINE__);
            }
            my $inifd = &open_for_rw("$inifilepath/ubi_${part_name}.ini");
            print $inifd "[ubifs]\n";
            print $inifd "mode=ubi\n";
            print $inifd "image=$inifilepath/ubifs.${part_name}.img\n";
            print $inifd "vol_id=0\n";
            print $inifd "vol_size=$vol_size\n";
            print $inifd "vol_type=dynamic\n";

            if (exists $kernel_alias{$part->{Partition_Name}})
            {
                print $inifd "vol_name=$kernel_alias{$part->{Partition_Name}}\n";
            }
            else
            {
                print $inifd "vol_name=${part_name}\n";
            }
            if ($part->{Partition_Name} ne "secro")
            {
                print $inifd "vol_flags=autoresize\n";
            }
            print $inifd "vol_alignment=1\n";
            close $inifd;
            printf $part_size_fh ("BOARD_UBIFS_%s_MAX_LOGICAL_ERASEBLOCK_COUNT:=%d\n", $part->{Partition_Name}, int((${vol_size} / $LEB) * 1.1));
        }
    }
    close $part_size_fh;
}

#****************************************************************************************
# subroutine:  GenPGPTFile;
# return:
#****************************************************************************************
sub GenPGPTFile
{
   my $LBA_size = 512;
   my $piter=0;
   my $MAX_GPT_PARTITION_NUM = 128;
   my $GPT_ENTRY_SIZE = 128;
   my $FirstUsableLBA; #equal to PGPT size
   my @PARTITION_BASIC_DATA_GUID = ("EBD0A0A2", "B9E5", "4433", "87", "C0", "68", "B6", "B7", "26", "99", "C7");
   my @PARTITION_GUID_TABLES = (
    ["F57AD330", "39C2", "4488", "9B", "B0", "00", "CB", "43", "C9", "CC", "D4"],
    ["FE686D97", "3544", "4A41", "BE", "21", "16", "7E", "25", "B6", "1B", "6F"],
    ["1CB143A8", "B1A8", "4B57", "B2", "51", "94", "5C", "51", "19", "E8", "FE"],
    ["3B9E343B", "CDC8", "4D7F", "9F", "A6", "B6", "81", "2E", "50", "AB", "62"],
    ["5F6A2C79", "6617", "4B85", "AC", "02", "C2", "97", "5A", "14", "D2", "D7"],
    ["4AE2050B", "5DB5", "4FF7", "AA", "D3", "57", "30", "53", "4B", "E6", "3D"],
    ["1F9B0939", "E16B", "4BC9", "A5", "BC", "DC", "2E", "E9", "69", "D8", "01"],
    ["D722C721", "0DEE", "4CB8", "8A", "83", "2C", "63", "CD", "13", "93", "C7"],
    ["E02179A8", "CEB5", "48A9", "88", "31", "4F", "1C", "9C", "5A", "86", "95"],
    ["84B09A81", "FAD2", "41AC", "89", "0E", "40", "7C", "24", "97", "5E", "74"],
    ["E8F0A5EF", "8D1B", "42EA", "9C", "2A", "83", "5C", "D7", "7D", "E3", "63"],
    ["D5F0E175", "A6E1", "4DB7", "94", "C0", "F8", "2A", "D0", "32", "95", "0B"],
    ["1D9056E1", "E139", "4FCA", "8C", "0B", "B7", "5F", "D7", "4D", "81", "C6"],
    ["7792210B", "B6A8", "45D5", "AD", "91", "33", "61", "ED", "14", "C6", "08"],
    ["138A6DB9", "1032", "451D", "91", "E9", "0F", "A3", "8F", "F9", "4F", "BB"],
    ["756D934C", "50E3", "4C91", "AF", "46", "02", "D8", "24", "16", "9C", "A7"],
    ["A3F3C267", "5521", "42DD", "A7", "24", "3B", "DE", "C2", "0C", "7C", "6F"],
    ["8C68CD2A", "CCC9", "4C5D", "8B", "57", "34", "AE", "9B", "2D", "D4", "81"],
    ["6A5CEBF8", "54A7", "4B89", "8D", "1D", "C5", "EB", "14", "0B", "09", "5B"],
    ["A0D65BF8", "E8DE", "4107", "94", "34", "1D", "31", "8C", "84", "3D", "37"],
    ["46F0C0BB", "F227", "4EB6", "B8", "2F", "66", "40", "8E", "13", "E3", "6D"],
    ["FBC2C131", "6392", "4217", "B5", "1E", "54", "8A", "6E", "DB", "03", "D0"],
    ["E195A981", "E285", "4734", "80", "25", "EC", "32", "3E", "95", "89", "D9"],
    ["E29052F8", "5D3A", "4E97", "AD", "B5", "5F", "31", "2C", "E6", "61", "0A"],
    ["9C3CABD7", "A35D", "4B45", "8C", "57", "B8", "07", "75", "42", "6B", "35"],
    ["E7099731", "95A6", "45A6", "A1", "E5", "1B", "6A", "BA", "03", "2C", "F1"],
    ["8273E1AB", "846F", "4468", "B9", "99", "EE", "2E", "A8", "E5", "0A", "16"],
    ["D26472F1", "9EBC", "421D", "BA", "14", "31", "12", "96", "45", "7C", "90"],
    ["B72CCBE9", "2055", "46F4", "A1", "67", "4A", "06", "9C", "20", "17", "38"],
    ["9C1520F3", "C2C5", "4B89", "82", "42", "FE", "4C", "61", "20", "8A", "9E"],
    ["902D5F3F", "434A", "4DE7", "89", "88", "32", "1E", "88", "C9", "B8", "AA"],
    ["BECE74C8", "D8E2", "4863", "9B", "FE", "5B", "0B", "66", "BB", "92", "0F"],
    ["FF1342CF", "B7BE", "44D5", "A2", "5E", "A4", "35", "AD", "DD", "27", "02"],
    ["A4DA8F1B", "FE07", "433B", "95", "CB", "84", "A5", "F2", "3E", "47", "7B"],
    ["C2635E15", "61AA", "454E", "9C", "40", "EB", "E1", "BD", "F1", "9B", "9B"],
    ["4D2D1290", "36A3", "4F5D", "AF", "B4", "31", "9F", "8A", "B6", "DC", "D8"],
    ["FDCE12F0", "A7EB", "40F7", "83", "50", "96", "09", "72", "E6", "CB", "57"],
    ["0FBBAFA2", "4AA9", "4490", "89", "83", "53", "29", "32", "85", "05", "FD"],
    ["A76E4B2F", "31CB", "40BA", "82", "6A", "C0", "CB", "0B", "73", "C8", "56"],
    ["F54AC030", "7004", "4D02", "94", "81", "BB", "F9", "82", "03", "68", "07"],
    ["C4C310E2", "4A7E", "77D3", "48", "18", "61", "E2", "D8", "BB", "5E", "86"],
    ["3734710F", "0F13", "1AB9", "73", "4C", "12", "A0", "8E", "C5", "08", "37"],
    ["85A5B02F", "3773", "18B3", "10", "49", "71", "8C", "DE", "95", "10", "7E"],
    ["6FCE83A6", "5273", "4748", "11", "45", "C2", "05", "EB", "B4", "B8", "AD"],
    ["3645E6A3", "A7E3", "19B2", "49", "41", "17", "2C", "10", "19", "0E", "EF"],
    ["F60B92B4", "0E2F", "91B6", "FB", "4A", "2D", "0B", "64", "3B", "AB", "4B"],
    ["D05CEDFE", "20B7", "68B3", "59", "F2", "4F", "E2", "53", "17", "F2", "FC"],
    ["54060B67", "FA74", "5C82", "61", "16", "44", "82", "1E", "54", "DC", "ED"],
    ["C0A128FB", "59D1", "0A9E", "F0", "E8", "EA", "3D", "36", "A9", "D6", "58"],
    ["F5F5A7F8", "F8EC", "3BA1", "CB", "C9", "4E", "2A", "7C", "BC", "7D", "07"],
    ["736D618A", "5B79", "530B", "9D", "AC", "89", "53", "39", "69", "06", "13"],
    ["A2342AC6", "4C40", "B02C", "51", "4F", "E9", "0D", "46", "F9", "34", "54"],
    ["339D6F74", "BCB8", "F02B", "69", "4A", "AE", "01", "52", "BC", "AD", "C0"],
    ["2CD122A3", "6142", "31A9", "12", "88", "CB", "60", "FB", "EA", "D9", "BE"],
    ["C09B7BD1", "5E46", "450C", "97", "D5", "AD", "F4", "07", "2A", "8D", "6B"],
    ["448A65B8", "A5D4", "BDB0", "DD", "EB", "BF", "4E", "A3", "CE", "AF", "C6"],
    ["B1B1E374", "8C7F", "B48A", "D5", "75", "CE", "0C", "A2", "0F", "11", "83"],
    ["24D37DD4", "CAF8", "1A6E", "BE", "45", "89", "E4", "B0", "70", "58", "43"],
    ["37957B10", "67EF", "1440", "89", "3B", "62", "00", "48", "43", "11", "B6"],
    ["441C978B", "5309", "1FAA", "3C", "91", "32", "C5", "F0", "87", "E9", "AE"],
    ["575FB526", "8D23", "61C4", "D5", "FE", "E9", "E0", "40", "EB", "07", "41"],
    ["D8BBFDDF", "8E4C", "8CF5", "CA", "E3", "FC", "4F", "02", "E1", "C4", "9A"],
    ["7C881E59", "5172", "82A8", "30", "5C", "42", "AB", "BD", "46", "7B", "7D"],
    ["AA224EB0", "1781", "C4D2", "D8", "1E", "B3", "9E", "64", "81", "0D", "F6"],
    ["A03B5E9F", "674C", "998F", "F4", "FB", "E2", "4B", "17", "1A", "35", "F3"],
    ["DC83390E", "BDCA", "FF71", "9D", "1B", "92", "D5", "E1", "82", "F0", "92"],
    ["2B938AC7", "D28C", "091E", "01", "13", "AF", "BE", "49", "49", "7C", "0A"],
    ["DA38A12D", "1146", "2277", "14", "E8", "4D", "84", "00", "D8", "4F", "75"],
    ["8E905218", "EAD7", "FABA", "1F", "55", "B8", "1D", "1E", "1A", "D1", "2F"],
    ["9B943967", "7C67", "0B69", "94", "79", "D4", "06", "71", "0B", "64", "AA"],
    ["69EE88A0", "4229", "96A0", "CC", "72", "86", "91", "B4", "52", "7D", "F8"],
    ["B53E48FD", "4CC7", "5CC1", "C1", "88", "6D", "C5", "70", "9B", "C6", "56"],
    ["1FFBF267", "BA52", "9AA7", "65", "9B", "A7", "D9", "2B", "EE", "D5", "FA"],
    ["BD5AF0C2", "83AD", "6A0E", "7D", "66", "EB", "37", "CB", "2D", "00", "03"],
    ["0ECAA08B", "65C9", "C44D", "D8", "8D", "A6", "89", "55", "4A", "05", "79"],
    ["B9A0FE86", "15E8", "EBE0", "85", "FB", "A2", "DF", "B4", "DA", "2A", "03"],
    ["F28FE445", "2C27", "CB6C", "5B", "9C", "32", "D4", "27", "DF", "02", "AB"],
    ["7AE83206", "07EC", "825F", "C1", "34", "C5", "0C", "7A", "CB", "0A", "DC"],
    ["43980F00", "C1D9", "A666", "D4", "12", "CE", "97", "AA", "B8", "38", "B3"],
    ["506CC41B", "2908", "FAB5", "84", "66", "BA", "8C", "EF", "56", "04", "FA"],
    ["7B758318", "BDE4", "9B51", "CD", "03", "F5", "D1", "9B", "BA", "23", "65"],
    ["97B09AEF", "27DA", "BACC", "AD", "C8", "CC", "3D", "C5", "F1", "57", "A4"],
    ["E854B3A3", "CDEA", "6520", "B1", "EB", "B3", "3C", "06", "B8", "C5", "B2"],
    ["485114D0", "A6F9", "A086", "72", "B5", "D6", "56", "77", "F1", "CC", "75"],
    ["0CC6FA07", "F896", "57ED", "51", "1C", "DF", "80", "8D", "7D", "CD", "0F"],
    ["0322D70E", "A68E", "B9C3", "ED", "B3", "30", "FF", "8B", "E6", "D9", "CE"],
    ["17702E2B", "71D3", "FA32", "12", "0F", "46", "72", "54", "3B", "5A", "FE"],
    ["4B1B0B9A", "304B", "1CA7", "39", "BE", "9A", "FA", "66", "33", "50", "EA"],
    ["AB285018", "30D0", "8C86", "76", "62", "30", "4F", "9F", "51", "29", "8A"],
    ["2FB6998A", "C7F7", "B7C6", "0B", "27", "85", "65", "D8", "C1", "14", "B3"],
    ["B943CD27", "07CF", "97A2", "AB", "EF", "E0", "36", "65", "C7", "FF", "EA"],
    ["416BBDE5", "D063", "9F75", "18", "6F", "37", "85", "F3", "34", "E9", "8E"],
    ["6BFE9E5D", "EA58", "A6E0", "9C", "36", "A0", "5D", "01", "2C", "D2", "62"],
    ["2CD691BF", "3EDF", "D421", "E0", "68", "61", "ED", "5B", "BF", "1F", "0D"],
    ["BFE2B2AA", "554B", "503B", "22", "84", "C6", "63", "AA", "21", "1C", "32"],
    ["BC6196FE", "DCC3", "2C5D", "D7", "CA", "1D", "49", "93", "D4", "D7", "7D"],
    ["C1F5ACE9", "A0BD", "CA33", "36", "85", "C3", "C5", "27", "39", "E2", "2E"],
    ["C7BDA00D", "BEFF", "443F", "8A", "1F", "49", "26", "AF", "49", "05", "6F"],
    ["08EFAD12", "DBAF", "3DF0", "8C", "91", "D2", "97", "D3", "67", "2D", "1F"],
    ["ECB1D2A4", "D582", "765E", "27", "25", "80", "67", "42", "F1", "58", "1D"],
    ["C8071B98", "1BBF", "8410", "C9", "2D", "D9", "DE", "9E", "95", "ED", "94"],
    ["84AB3B6A", "0E28", "85B9", "FE", "0A", "74", "72", "4B", "67", "8A", "DF"],
    ["D3E63ADE", "1B33", "83BD", "5D", "31", "52", "AD", "68", "89", "B1", "F5"],
    ["C55A3619", "A57F", "4495", "7D", "99", "60", "78", "3F", "DD", "E0", "03"],
    ["0F52CA87", "79CC", "3A25", "6C", "FA", "F8", "50", "AC", "12", "BD", "6E"],
    ["C0087739", "7885", "AEA2", "82", "5D", "60", "ED", "E0", "86", "9F", "2C"],
    ["09A725A9", "26BA", "4CFA", "8B", "86", "1D", "3C", "DE", "A3", "98", "E8"],
    ["CD5B7E7D", "1900", "9C44", "60", "1C", "A1", "8F", "FA", "FA", "E3", "26"],
    ["E15F44B5", "7A6C", "6A13", "DF", "B5", "5B", "89", "EC", "48", "E0", "2D"],
    ["F54E1A6F", "58D6", "0A75", "82", "19", "84", "C4", "CE", "89", "AD", "4D"],
    ["8623FFF5", "5B9C", "417A", "BE", "C4", "FD", "E5", "33", "56", "94", "60"],
    ["2E1054D0", "DFA8", "6E4D", "64", "89", "35", "AA", "97", "72", "0F", "28"],
    ["0062575C", "9DF6", "73F2", "38", "BC", "0A", "F1", "0C", "23", "51", "4F"],
    ["02B27A1D", "981D", "176A", "6F", "93", "59", "9A", "0F", "D7", "BA", "73"],
    ["8EFC8CD9", "B516", "25FC", "7A", "F9", "F9", "50", "10", "F9", "0A", "3E"],
    ["183A46D4", "F075", "EA74", "8C", "5F", "11", "BF", "D9", "82", "C4", "94"],
    ["0C1A02AA", "2BCA", "9E47", "FD", "7D", "E3", "7D", "6B", "6B", "9F", "F8"],
    ["CA4BE765", "300C", "1072", "E6", "97", "8C", "2C", "D8", "57", "88", "6C"],
    ["289FA349", "28FC", "9902", "DA", "8F", "EB", "38", "2C", "89", "20", "7F"],
    ["958AF858", "F269", "8567", "A7", "FE", "85", "D0", "DA", "07", "FA", "56"],
    ["38C14DEE", "9901", "561B", "47", "E4", "91", "66", "75", "B1", "D7", "9A"],
    ["CF8A6CC7", "E9A5", "B039", "1D", "1F", "D2", "E1", "9D", "BF", "40", "8C"],
    ["7507A207", "7648", "F674", "F6", "4E", "81", "A6", "6E", "93", "CE", "D8"],
    ["AE0DC798", "407F", "2827", "19", "C3", "E3", "14", "E5", "22", "C6", "93"],
    ["42960C34", "1E45", "9E8B", "EF", "7B", "4E", "2F", "32", "F6", "9F", "02"],
    ["2358F8AD", "85C7", "6E1D", "30", "AB", "FC", "7E", "F2", "AF", "0B", "16"],
    ["91013B77", "F8E5", "29BA", "AA", "E0", "EA", "81", "18", "E5", "7A", "89"],
    ["73D97DC3", "2390", "D401", "EC", "AC", "9C", "47", "71", "45", "39", "B8"],
);
   my $i;
   my $j;
   my $TEMP_PARTITION_BASIC_DATA_GUID;
   my @TEMP_PARTITION_GUID_TABLES;
   my $Wide_Partition_Name;
   my $counter;
   # Generate PGPT binary file -----------------------------------------------------
   print("Generate $PGPT_FILE bin file\n");

   #create file
   open(FH,">$PGPT_FILE")|| die "create $PGPT_FILE file failed\n";
   print FH pack("C16896",0x0); #pgpt current max size = (34 - 1[PMBR]) LBA = 33*512 = 16896 bytes

   #Construct Partition table header(LBA1)
   seek(FH,0,0);
   print FH pack("C8",0x45,0x46,0x49,0x20,0x50,0x41,0x52,0x54);
   seek(FH,0x08,0);
   print FH pack("C4",0x00,0x00,0x01,0x00);
   seek(FH,0x0C,0);
   print FH pack("C4",0x5c,0x00,0x00,0x00);
   seek(FH,0x18,0);
   print FH pack("C8",0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00);
   seek(FH,0x20,0); #Put sgpt partition size here for lk update usage, new design->16.5KB   old-> 512KB
   for ($piter = 0 ; $piter < @partition_layout_cooked ; $piter++)
   {
      my $part = $partition_layout_cooked[$piter];
      if($part->{Partition_Name} eq "sgpt")
      {
         print FH pack("Q",$part->{Size_KB}*1024/512);
      }
   }
   seek(FH,0x28,0);
   $FirstUsableLBA = ($LBA_size*2 + $MAX_GPT_PARTITION_NUM*$GPT_ENTRY_SIZE)/$LBA_size;
   print FH pack("Q",$FirstUsableLBA);
   seek(FH,0x48,0);
   print FH pack("C8",0x02,0x00,0x00,0x00,0x00,0x00,0x00,0x00);
   seek(FH,0x50,0);
   print FH pack("L",$MAX_GPT_PARTITION_NUM);
   seek(FH,0x54,0);
   print FH pack("L",$GPT_ENTRY_SIZE);

   #Construct Partition entries

   seek(FH,0x200,0);
   for ($i = 0; $i < @PARTITION_BASIC_DATA_GUID ; $i++)
   {
      $TEMP_PARTITION_BASIC_DATA_GUID .= join '', reverse split /(..)/, $PARTITION_BASIC_DATA_GUID[$i];
   }
   for ($i = 0; $i < @PARTITION_GUID_TABLES ; $i++)
   {
      for ($j = 0; $j < @{$PARTITION_GUID_TABLES[$i]} ; $j++)
      {
         $TEMP_PARTITION_GUID_TABLES[$i] .= join '', reverse split /(..)/, $PARTITION_GUID_TABLES[$i][$j];
      }
   }
   for ($piter = 0 ; $piter < @partition_layout_cooked ; $piter++)
   {
      my $part = $partition_layout_cooked[$piter];
      if (exists $SkipPartList{$part->{Partition_Name}}) #skip partitions not in gpt table
      {
         if($part->{Partition_Name} ne "sgpt")
         {$counter++;} #pgpt and preloader should be ignored to get PARTITION_GUID_TABLES entries
         next;
      }

      print FH pack("H32",$TEMP_PARTITION_BASIC_DATA_GUID);
      print FH pack("H32",$TEMP_PARTITION_GUID_TABLES[$piter-$counter]);
      if ($part->{Reserved} eq "Y" || $partition_layout_cooked[$piter+1]->{Reserved} eq "Y")
      {
         #Partition right before reserved partition (ex: userdata) do not have start lba/ end lba in pre-gen pgpt
         if ($part->{Reserved} ne "Y" )
         {
            print FH pack("Q",0x0);
            print FH pack("Q",0x0);
         }
         #Reserved partition only write size in end lba and will be used in lk
         else
         {
            print FH pack("Q",0x0);
            print FH pack("Q",$part->{Size_KB}*1024/512);
         }
      }
      else
      {
         print FH pack("Q",$part->{Start_Addr}/512);
         print FH pack("Q",($part->{Start_Addr} + $part->{Size_KB}*1024 - 1)/512);
      }
      print FH pack("Q",0x0);
      #Construct Wide char Partition Name
      $Wide_Partition_Name = "";
      for my $i (split('', $part->{Partition_Name}))
      {
         $Wide_Partition_Name .= $i;
         $Wide_Partition_Name .= "\0";
      }
      print FH pack("a72",$Wide_Partition_Name);
   }

   close(FH);
}

#****************************************************************************
# subroutine:  error_handler
# input:       $error_msg:     error message
#****************************************************************************
sub error_handler
{
    my ($error_msg, $file, $line_no, $sys_msg) = @_;
    if (!$sys_msg)
    {
        $sys_msg = $!;
    }
    print "Fatal error: $error_msg <file: $file,line: $line_no> : $sys_msg";
    die;
}

#****************************************************************************
# subroutine:  copyright_file_header_for_c
# return:      file header -- copyright
#****************************************************************************
sub copyright_file_header_for_c
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
/* MediaTek Inc. (C) 2013. All rights reserved.
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
 # MediaTek Inc. (C) 2013. All rights reserved.
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
sub GenKernel_PartitionC()
{
    my $temp;
    my $kernel_h_fd = &open_for_rw($KernelH);
    print $kernel_h_fd &copyright_file_header_for_c();
    my $template = <<"__TEMPLATE";

#include <linux/mtd/mtd.h>
#include <linux/mtd/nand.h>
#include <linux/mtd/partitions.h>
#include "partition_define.h"


/*=======================================================================*/
/* NAND PARTITION Mapping                                                  */
/*=======================================================================*/
static struct mtd_partition g_pasStatic_Partition[] = {

__TEMPLATE
    print $kernel_h_fd $template;
    foreach my $part (@partition_layout_cooked)
    {
        last if ($part->{Reserved} eq "Y");
        print $kernel_h_fd "\t{\n";
        if (exists $kernel_alias{$part->{Partition_Name}})
        {
            print $kernel_h_fd "\t\t.name = \"$kernel_alias{$part->{Partition_Name}}\",\n";
        }
        else
        {
            printf $kernel_h_fd "\t\t.name = \"%s\",\n", lc($part->{Partition_Name});
        }
        if ($part->{Start_Addr} == 0)
        {
            print $kernel_h_fd "\t\t.offset = 0x0,\n";
        }
        else
        {
            print $kernel_h_fd "\t\t.offset = MTDPART_OFS_APPEND,\n";
        }
        if ($part->{Size_KB} != 0)
        {
            print $kernel_h_fd "\t\t.size = PART_SIZE_$part->{Partition_Name},\n";
        }
        else
        {
            print $kernel_h_fd "\t\t.size = MTDPART_SIZ_FULL,\n";
        }
        if ($part->{Partition_Name} eq "PRELOADER" || $part->{Partition_Name} eq "DSP_BL" || $part->{Partition_Name} eq "UBOOT" || $part->{Partition_Name} eq "SEC_RO")
        {
            print $kernel_h_fd "\t\t.mask_flags  = MTD_WRITEABLE,\n";
        }
        print $kernel_h_fd "\t},\n";
    }
    print $kernel_h_fd "};\n";

    $template = <<"__TEMPLATE";
#define NUM_PARTITIONS ARRAY_SIZE(g_pasStatic_Partition)
extern int part_num;	// = NUM_PARTITIONS;
__TEMPLATE
    print $kernel_h_fd $template;
    close $kernel_h_fd;

}


#****************************************************************************************
# subroutine:  GenLK_PartitionC
# return:
#****************************************************************************************
sub GenLK_PartitionC()
{
    my $iter=0;
    my $lk_part_c_fh = &open_for_rw($LK_PartitionC);
    print $lk_part_c_fh &copyright_file_header_for_c();
    print $lk_part_c_fh "#include \"platform/partition.h\"\n";
    print $lk_part_c_fh "\n\nstruct part_name_map g_part_name_map[] = {\n";
    foreach my $part (@partition_layout_cooked)
    {
        next if ($part->{Partition_Name} eq "pgpt");
        last if ($part->{Reserved} eq "Y");
        printf $lk_part_c_fh (
                              "\t{\"%s\",\t\"%s\",\t\"%s\",\t%d,\t%d,\t%d},\n",
                              ($part->{Partition_Name}),
                              ($part->{Partition_Name}),
                              lc($part->{Type}),
                              $iter,
                              ($part->{FastBoot_Erase} eq "Y")    ? (1) : (0),
                              ($part->{FastBoot_Download} eq "Y") ? (1) : (0)
                             );
        $iter++;
    }
    print $lk_part_c_fh "};\n";
    close $lk_part_c_fh;
}


#delete some obsolete file
sub clear_files
{
	my @ObsoleteFile;

    opendir (DIR,"mediatek/custom/$ArgList{PROJECT}/common");
	push @ObsoleteFile,readdir(DIR);
    close DIR;

    my $custom_out_prefix_obsolete  = "mediatek/custom/$ENV{PROJECT}";
    my $configs_out_prefix_obsolete = "mediatek/config/$ENV{PROJECT}";
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
#****************************************************************************************
# subroutine:  xls_cell_value
# return:      Excel cell value no matter it's in merge area or not, and in windows or not
# input:       $Sheet:  Specified Excel Sheet
# input:       $row:    Specified row number
# input:       $col:    Specified column number
#****************************************************************************************
sub xls_cell_value
{
    my ($Sheet, $row, $col) = @_;
    my $cell = $Sheet->get_cell($row, $col);
    if (defined $cell)
    {
        #return $cell->Value();
        return $cell->unformatted();
    }
    else
    {
        &error_handler("excel read fail,(row=$row,col=$col) undefine", __FILE__, __LINE__);
    }
}

sub open_for_rw
{
    my $filepath = shift @_;
    if (-e $filepath)
    {
        chmod(0777, $filepath) or &error_handler("chmod 0777 $filepath fail", __FILE__, __LINE__);
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
            &error_handler("Can not make dir $dirpath", __FILE__, __LINE__, $@);
        }
    }
    open my $filehander, "> $filepath" or &error_handler(" Can not open $filepath for read and write", __FILE__, __LINE__);
    push @GeneratedFile, $filepath;
    return $filehander;
}

sub open_for_read
{
    my $filepath = shift @_;
    if (-e $filepath)
    {
    	printf ("The file exist!\n");
    }
    else
    {
        printf ("No such file : %s, file: %s, line:%s\n",$filepath,__FILE__,__LINE__);
        return undef;
    }
    open my $filehander, "< $filepath" or &error_handler(" Can not open $filepath for read", __FILE__, __LINE__);
    return $filehander;
}

sub debug_print_layout
{
    if ($DebugPrint eq "yes")
    {
        my ($filepath, $list) = @_;
        my $fd = &open_for_rw($filepath);
        print $fd Dump($list);
        close $fd;
    }
}

sub print_rm_script
{
    my $out = "./remove_ptgen_autogen_files.pl";
    my $fd  = &open_for_rw($out);

    print $fd "#!/usr/local/bin/perl -w\n";
    foreach my $file (@GeneratedFile)
    {
        if ($file ne $out)
        {
            printf $fd "system \"rm -fr --verbose %s\";\n", $file;
        }
    }
    print $fd "system \"rm -fr --verbose mediatek/custom/out \";\n";
    print $fd "system \"rm -fr --verbose mediatek/config/out \";\n";
    print $fd "system \"rm -fr --verbose out\";\n";
    chmod(0777, $out);
    close $fd;
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
