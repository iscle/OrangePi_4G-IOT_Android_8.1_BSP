#!/bin/bash
#
# Builds ARM Trusted Firmware, and generates FIPs with UEFI and optionally
# Trusted OS for the supported platforms.
# Not intended to be called directly, invoked from uefi-build.sh.
#
# Board configuration is extracted from
# parse-platforms.py and platforms.config.
#

TOOLS_DIR="`dirname $0`"
. "$TOOLS_DIR"/common-functions
OUTPUT_DIR="$PWD"/uefi-build

ATF_BUILDVER=1

function usage
{
	echo "usage:"
	echo "atf-build.sh -e <EDK2 source directory> -t <UEFI build profile/toolchain> <platform>"
	echo
}

function check_atf_buildver
{
	MAJOR=`grep "^VERSION_MAJOR" Makefile | sed 's/.*:= *\([0-9]*\).*/\1/'`
	[ $? -ne 0 ] && return 1
	MINOR=`grep "^VERSION_MINOR" Makefile | sed 's/.*:= *\([0-9]*\).*/\1/'`
	[ $? -ne 0 ] && return 1

	if [ "$MAJOR" -eq 1 -a "$MINOR" -ge 2 ]; then
		ATF_BUILDVER=2
	fi
}

function build_platform
{
	if [ X"$EDK2_DIR" = X"" ];then
		echo "EDK2_DIR not set!" >&2
		return 1
	fi

	check_atf_buildver || return 1

	BUILD_ATF="`$TOOLS_DIR/parse-platforms.py $PLATFORM_CONFIG -p $1 get -o build_atf`"
	if [ X"$BUILD_ATF" = X"" ]; then
		echo "Platform '$1' is not configured to build ARM Trusted Firmware."
		return 0
	fi

	ATF_PLATFORM="`$TOOLS_DIR/parse-platforms.py $PLATFORM_CONFIG -p $1 get -o atf_platform`"
	if [ X"$ATF_PLATFORM" = X"" ]; then
		ATF_PLATFORM=$1
	fi

	#
	# Read platform configuration
	#
	PLATFORM_NAME="`$TOOLS_DIR/parse-platforms.py $PLATFORM_CONFIG -p $1 get -o longname`"
	PLATFORM_ARCH="`$TOOLS_DIR/parse-platforms.py $PLATFORM_CONFIG -p $1 get -o arch`"
	PLATFORM_IMAGE_DIR="`$TOOLS_DIR/parse-platforms.py $PLATFORM_CONFIG -p $1 get -o uefi_image_dir`"
	PLATFORM_BUILDFLAGS="`$TOOLS_DIR/parse-platforms.py $PLATFORM_CONFIG -p $1 get -o atf_buildflags`"

	if [ $VERBOSE -eq 1 ]; then
		echo "PLATFORM_NAME=$PLATFORM_NAME"
		echo "PLATFORM_ARCH=$PLATFORM_ARCH"
		echo "PLATFORM_IMAGE_DIR=$PLATFORM_IMAGE_DIR"
		echo "PLATFORM_BUILDFLAGS=$PLATFORM_BUILDFLAGS"
	fi

	unset BL30 BL31 BL32 BL33
	BL30="`$TOOLS_DIR/parse-platforms.py $PLATFORM_CONFIG -p $1 get -o scp_bin`"
	if [ $ATF_BUILDVER -gt 1 ]; then
		unset SCP_BL2
		SCP_BL2="$EDK2_DIR/$BL30"
	fi
	BL31="`$TOOLS_DIR/parse-platforms.py $PLATFORM_CONFIG -p $1 get -o el3_bin`"
	BL33="$EDK2_DIR/Build/$PLATFORM_IMAGE_DIR/$BUILD_PROFILE/FV/`$TOOLS_DIR/parse-platforms.py $PLATFORM_CONFIG -p $1 get -o uefi_bin`"

	#
	# Set up cross compilation variables (if applicable)
	#
	set_cross_compile
	CROSS_COMPILE="$TEMP_CROSS_COMPILE"
	echo "Building ARM Trusted Firmware for $PLATFORM_NAME - $BUILD_PROFILE"
	echo "CROSS_COMPILE=\"$TEMP_CROSS_COMPILE\""

	if [ X"$BL30" != X"" ]; then
		BL30="${EDK2_DIR}"/"${BL30}"
	fi
	if [ X"$BL31" != X"" ]; then
		BL31="${EDK2_DIR}"/"${BL31}"
	fi

	#
	# BL32 requires more attention
	# If TOS_DIR is not set, we assume user does not want a Trusted OS,
	# even if the source directory and/or binary for it exists
	#
	if [ X"$TOS_DIR" != X"" ]; then
		SPD="`$TOOLS_DIR/parse-platforms.py $PLATFORM_CONFIG -p $1 get -o atf_spd`"

		TOS_BIN="`$TOOLS_DIR/parse-platforms.py $PLATFORM_CONFIG -p $1 get -o tos_bin`"
		if [ X"$TOS_BIN" != X"" ]; then
			BL32=$EDK2_DIR/Build/$PLATFORM_IMAGE_DIR/$BUILD_PROFILE/FV/$TOS_BIN
		fi

		if [ X"$SPD" != X"" ] && [ X"$BL32" != X"" ]; then
			#
			# Since SPD cannot be exported or undefined,
			# we parametrise it here
			#
			SPD_OPTION="SPD=$SPD"
		else
			echo "WARNING:	Proceeding without Trusted OS!"
			echo "		Please specify both ATF_SPD and TOS_BIN"
			echo "		if you wish to use a Trusted OS!"
		fi
	fi

	#
	# Debug extraction handling
	#
	case "$BUILD_ATF" in
	debug*)
		DEBUG=1
		BUILD_TYPE="debug"
		;;
	*)
		DEBUG=0
		BUILD_TYPE="release"
		;;
	esac

	export BL30 BL31 BL32 BL33

	echo "BL30=$BL30"
	if [ $ATF_BUILDVER -gt 1 ]; then
		export SCP_BL2
		echo "SCP_BL2=$BL30"
	fi
	echo "BL31=$BL31"
	echo "BL32=$BL32"
	echo "BL33=$BL33"
	echo "$SPD_OPTION"
	echo "BUILD_TYPE=$BUILD_TYPE"

	#
	# If a build was done with BL32, and followed by another without,
	# the BL32 component remains in fip.bin, so we delete the build dir
	# contents before calling make
	#
	rm -rf build/"$ATF_PLATFORM/$BUILD_TYPE"/*

	#
	# Build ARM Trusted Firmware and create FIP
	#
	if [ $VERBOSE -eq 1 ]; then
		echo "Calling ARM Trusted Firmware build:"
		echo "CROSS_COMPILE="$CROSS_COMPILE" make -j$NUM_THREADS PLAT="$ATF_PLATFORM" $SPD_OPTION DEBUG=$DEBUG ${PLATFORM_BUILDFLAGS} all fip"
	fi
	CROSS_COMPILE="$CROSS_COMPILE" make -j$NUM_THREADS PLAT="$ATF_PLATFORM" $SPD_OPTION DEBUG=$DEBUG ${PLATFORM_BUILDFLAGS} all fip
	if [ $? -eq 0 ]; then
		#
		# Copy resulting images to UEFI image dir
		#
		if [ $VERBOSE -eq 1 ]; then
			echo "Copying bl1.bin and fip.bin to "$EDK2_DIR/Build/$PLATFORM_IMAGE_DIR/$BUILD_PROFILE/FV/""
		fi
		cp -a build/"$ATF_PLATFORM/$BUILD_TYPE"/{bl1,fip}.bin "$EDK2_DIR/Build/$PLATFORM_IMAGE_DIR/$BUILD_PROFILE/FV/"
	else
		return 1
	fi
}

# Check to see if we are in a trusted firmware directory
# refuse to continue if we aren't
if [ ! -d bl32 ]
then
	echo "ERROR: we aren't in the arm-trusted-firmware directory."
	usage
	exit 1
fi

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
