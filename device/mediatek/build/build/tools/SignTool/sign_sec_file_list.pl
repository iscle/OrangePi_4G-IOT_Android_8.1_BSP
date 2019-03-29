#!/usr/bin/perl

use lib "device/mediatek/build/build/tools/SignTool";
use lib "device/mediatek/build/build/tools";
use pack_dep_gen;
PrintDependModule($0);

##########################################################
# Initialize Variables
##########################################################
my $TARGET_PRODUCT = $ENV{"TARGET_PRODUCT"};
my $prj = substr $TARGET_PRODUCT, (index($TARGET_PRODUCT, 'full_') + 5);
my $key_dir = "device/mediatek/$prj/custom/security/image_auth";
my $cfg_dir = "device/mediatek/$prj/custom/security/sec_file_list";
my $cipher_tool = "device/mediatek/build/build/tools/CipherTool/CipherTool";
my $sign_tool = "device/mediatek/build/build/tools/SignTool/SignTool.sh";
my $OUT_DIR = "out";


##########################################################
# Sign ANDROID Secure File List
##########################################################
print "\n\n*** Sign ANDROID Secure File List ***\n\n";

my $and_secfl = "device/mediatek/$prj/custom/security/sec_file_list/ANDRO_SFL.ini";
my $s_andro_fl_dir = "$OUT_DIR/target/product/$prj/system/etc/firmware";
my $s_andro_fl = "$s_andro_fl_dir/S_ANDRO_SFL.ini";

if (! -d "$s_andro_fl_dir"){
	system("mkdir -p $s_andro_fl_dir");
}

if (-e "$and_secfl")
{
	if (-e "$s_andro_fl")
	{
		print "remove old file list (1) ... \n";
		system("rm -f $s_andro_fl");
	}
					
	PrintDependency("$key_dir/IMG_AUTH_KEY.ini");
	PrintDependency("$cfg_dir/SFL_CFG.ini");
	PrintDependency($and_secfl);
	system("./$sign_tool $key_dir/IMG_AUTH_KEY.ini $cfg_dir/SFL_CFG.ini $and_secfl $s_andro_fl");
	
	if (! -e "$s_andro_fl")
	{
		die "sign failed. please check";
	}
}
else
{
	print "file doesn't exist\n";	
}


##########################################################
# Sign SECRO Secure File List
##########################################################
print "\n\n*** Sign SECRO Secure File List ***\n\n";

my $secro_secfl = "device/mediatek/$prj/custom/security/sec_file_list/SECRO_SFL.ini";
my $s_secro_fl_o1 = "$OUT_DIR/target/product/$prj/secro/S_SECRO_SFL.ini";
my $s_secro_fl_o2 = "$OUT_DIR/target/product/$prj/secro/S_SECRO_SFL.ini";

if (-e "$secro_secfl")
{				
	if (-e "$s_secro_fl_o1")
	{
		print "remove old file list (1) ... \n";
		system("rm -f $s_secro_fl_o1");
	}

	if (-e "$s_secro_fl_o2")
	{
		print "remove old file list (2) ... \n";
		system("rm -f $s_secro_fl_o2");
	}

	PrintDependency("$key_dir/IMG_AUTH_KEY.ini");
	PrintDependency("$cfg_dir/SFL_CFG.ini");
	PrintDependency($secro_secfl);
	system("./$sign_tool $key_dir/IMG_AUTH_KEY.ini $cfg_dir/SFL_CFG.ini $secro_secfl $s_secro_fl_o1");

	if (! -e "$s_secro_fl_o1")
	{
		die "sign failed. please check";
	}
	
	system("cp -f $s_secro_fl_o1 $s_secro_fl_o2");	
}
else
{
	print "file doesn't exist\n";
}

