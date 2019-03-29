#!usr/bin/perl
# Copyright Statement:
#
# This software/firmware and related documentation ("MediaTek Software") are
# protected under relevant copyright laws. The information contained herein
# is confidential and proprietary to MediaTek Inc. and/or its licensors.
# Without the prior written permission of MediaTek inc. and/or its licensors,
# any reproduction, modification, use or disclosure of MediaTek Software,
# and information contained herein, in whole or in part, shall be strictly prohibited.
#
# MediaTek Inc. (C) 2010. All rights reserved.
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

my $prj = $ARGV[0];
my $custom_dir = $ARGV[1];
my $secro_ac = $ARGV[2];
my $nand_page_size = $ARGV[3];
my $fb_signature = "FB_SIG";
my $dir = "out/target/product/$prj";
my $cfg_dir = "device/mediatek/$prj/custom/security/image_auth";
my $cfg_def = "IMG_AUTH_CFG.ini";
my $cfg = "$cfg_dir/$cfg_def";
my $key = "$cfg_dir/IMG_AUTH_KEY.ini";
my $BUILD_UBOOT = "no";
$ENV{PROJECT}=$prj;

##########################################################
# Dump Parameter
##########################################################
print "\n\n";
print "********************************************\n";
print " Dump Paramter \n";
print "********************************************\n";
print " Project          : $prj\n";
print " Custom Directory : $custom_dir\n";
print " SECRO AC         : $secro_ac\n";
print " NAND Page Size   : $nand_page_size\n";
print " BUILD_UBOOT      : $BUILD_UBOOT\n";

##########################################################
# Create Folder
##########################################################
print "\n\n";
print "********************************************\n";
print " Create Folder \n";
print "********************************************\n";
`mkdir $dir/signed_bin` if ( ! -d "$dir/signed_bin" );
print "Image Dir '$dir'\n";
my $command = "device/mediatek/build/build/tools/SignTool/SignTool.sh";

##########################################################
# File Check
##########################################################
my @imgs_need_sign = ("boot.img", "logo.bin", "recovery.img", "secro.img", "system.img", "userdata.img", "lk.bin");
if(${BUILD_UBOOT} eq "yes") {
        push (@imgs_need_sign, "uboot_${prj}.bin");
}

foreach my $img (@imgs_need_sign) {
	push (@miss_img, $img) if ( ! -e "$dir/$img");
}
die "@miss_img\nall the imgs above is NOT exsit\n" if (@miss_img > 0);

##########################################################
# BACKUP SECRO
##########################################################
my $secro_out = "out/target/product/$prj/secro.img";
my $secro_bak = "out/target/product/$prj/secro_bak.img";
system("cp -rf $secro_out $secro_bak") == 0 or die "backup SECRO fail\n";

##########################################################
# SECRO POST PROCESS
##########################################################
print "\n\n";
print "********************************************\n";
print " SecRo Post Processing \n";
print "********************************************\n";

my $secro_def_cfg = "device/mediatek/common/secro/SECRO_DEFAULT_LOCK_CFG.ini";
my $secro_fac_lock_cfg = "device/mediatek/common/secro/SECRO_FACTORY_LOCK_CFG.ini";
my $secro_unlock_cfg = "device/mediatek/common/secro/SECRO_UNLOCK_CFG.ini";
my $secro_def_out = "out/target/product/$prj/secro.img";
my $secro_fac_lock_out = "out/target/product/$prj/sro-lock.img";
my $secro_unlock_out = "out/target/product/$prj/sro-unlock.img";
my $secro_script = "device/mediatek/build/build/tools/secro/secro_post.pl";
if (${secro_ac} eq "yes")
{
	system("./$secro_script $secro_def_cfg $custom_dir $prj $secro_ac $secro_def_out") == 0 or die "SECRO post process return error\n";
	system("./$secro_script $secro_fac_lock_cfg $custom_dir $prj $secro_ac $secro_fac_lock_out") == 0 or die "SECRO post process return error\n";
	system("./$secro_script $secro_unlock_cfg $custom_dir $prj $secro_ac $secro_unlock_out") == 0 or die "SECRO post process return error\n";
}

##########################################################
# Process Common Files
##########################################################
print "\n\n";
print "********************************************\n";
print " Sign Common Images \n";
print "********************************************\n";
foreach my $img (@imgs_need_sign) {
	if ( ! -e "$dir/$img") {
		warn "the $img is NOT exsit, please check\n";
		next;
	}
	my $signed_img = $img;
	$signed_img =~ s/\./-sign\./;
	my $signed_cfg = "$cfg_dir/$img.ini";
	if ( ! -e "$signed_cfg" ) {
		$signed_cfg = $cfg;
	}
	print "Sign Image '$dir/$img' with cfg '$signed_cfg'...\n";
	
	system("$command $key $signed_cfg $dir/$img $dir/signed_bin/$signed_img $nand_page_size $fb_signature") == 0 or die "sign image fail";
}

sub print_system {
	my $command = $_[0];
	my $rslt = system($command);
	print "$command: $rslt\n";
	die "Failed to execute $command" if ($rslt != 0);
}

##########################################################
# Process EMMC Files
##########################################################
print "\n\n";
print "********************************************\n";
print " Sign EMMC Images \n";
print "********************************************\n";

my @imgs_need_sign = ("MBR", "EBR1", "EBR2");

foreach my $img (@imgs_need_sign) {
	if (-e "$dir/$img") {		
		my $signed_cfg = "$cfg_dir/$img.ini";
        	if ( ! -e "$signed_cfg" ) {
                	$signed_cfg = $cfg;
        	}
        	print "Sign Image '$dir/$img' with cfg '$signed_cfg'...\n";
		system("$command $key $signed_cfg $dir/$img $dir/signed_bin/${img}-sign $nand_page_size $fb_signature") == 0 or die "sign EMMC image fail";
	}
}

my @imgs_need_sign = ("cache.img");

foreach my $img (@imgs_need_sign) {
	if (-e "$dir/$img") {		
		my $signed_img = $img;
		$signed_img =~ s/\./-sign\./;
        	my $signed_cfg = "$cfg_dir/$img.ini";
        	if ( ! -e "$signed_cfg" ) {
                	$signed_cfg = $cfg;
        	}
        	print "Sign Image '$dir/$img' with cfg '$signed_cfg'...\n";
		system("$command $key $signed_cfg $dir/$img $dir/signed_bin/$signed_img $nand_page_size $fb_signature") == 0 or die "sign EMMC image fail";
	}
}

##########################################################
# Process SECRO Files
##########################################################
print "\n\n";
print "********************************************\n";
print " Sign SECRO Images \n";
print "********************************************\n";

my @imgs_need_sign = ("sro-lock.img", "sro-unlock.img");

if (${secro_ac} eq "yes")
{
	foreach my $img (@imgs_need_sign) {
		if (-e "$dir/$img") {		
			my $signed_img = $img;
			$signed_img =~ s/\./-sign\./;
		        my $signed_cfg = "$cfg_dir/$img.ini";
        		if ( ! -e "$signed_cfg" ) {
                		$signed_cfg = $cfg;
        		}
        		print "Sign Image '$dir/$img' with cfg '$signed_cfg'...\n";
			system("$command $key $signed_cfg $dir/$img $dir/signed_bin/$signed_img $nand_page_size $fb_signature") == 0 or die "sign SECRO image fail";
		}
	}
}

##########################################################
# RESTORE SECRO
##########################################################
my $secro_out = "out/target/product/$prj/secro.img";
my $secro_bak = "out/target/product/$prj/secro_bak.img";
system("cp -rf $secro_bak $secro_out") == 0 or die "restore SECRO fail\n";
system("rm -rf $secro_bak") == 0 or die "remove backup SECRO fail\n";
