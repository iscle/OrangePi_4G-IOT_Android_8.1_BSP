#!/bin/bash
#
# Builds Trusted OS, mainly for ARM Trusted Firmware.
# Calls $ATF_SPD-build.sh for the actual build of a specific Trusted OS.
# Not intended to be called directly, invoked from uefi-build.sh.
#
# Board configuration is extracted from
# parse-platforms.py and platforms.config.
#

TOOLS_DIR="`dirname $0`"
. "$TOOLS_DIR"/common-functions

function usage
{
	echo "usage:"
	echo "tos-build.sh -e <EDK2 source directory> -t <UEFI build profile/toolchain> <platform>"
	echo
}

function build_platform
{
	if [ X"$EDK2_DIR" = X"" ];then
		echo "EDK2_DIR not set!" >&2
		return 1
	fi

	if [ X"`$TOOLS_DIR/parse-platforms.py $PLATFORM_CONFIG -p $1 get -o build_tos`" = X"" ]; then
		echo "Platform '$1' is not configured to build Trusted OS."
		return 0
	fi

	#
	# Build Trusted OS
	#
	ATF_SPD="`$TOOLS_DIR/parse-platforms.py $PLATFORM_CONFIG -p $1 get -o atf_spd`"
	if [ -f $TOOLS_DIR/$ATF_SPD-build.sh ]; then
		echo "Building $ATF_SPD Trusted OS"
		if [ $VERBOSE -eq 1 ]; then
			echo "$TOOLS_DIR/$ATF_SPD-build.sh -e "$EDK2_DIR" -t "$BUILD_PROFILE" $build"
		fi
		$TOOLS_DIR/$ATF_SPD-build.sh -e "$EDK2_DIR" -t "$BUILD_PROFILE" $build
		return $?
	else
		echo "ERROR: missing Trusted OS build script."
		echo "       Or build script not named $ATF_SPD-build.sh"
		return 1
	fi
}

build=

if [ $# = 0 ]
then
	usage
	exit 1
else
	while [ "$1" != "" ]; do
		case $1 in
			"-e" )
				shift
				EDK2_DIR="$1"
				;;
			"/h" | "/?" | "-?" | "-h" | "--help" )
				usage
				exit
				;;
			"-t" )
				shift
				BUILD_PROFILE="$1"
				;;
			* )
				build="$1"
				;;
		esac
		shift
	done
fi

if [ X"$build" = X"" ]; then
	echo "No platform specified!" >&2
	echo
	usage
	exit 1
fi

build_platform $build
exit $?
