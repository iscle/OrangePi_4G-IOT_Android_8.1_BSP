#!/bin/bash

# Kernel configuration options.
OPTIONS=" DEBUG_SPINLOCK DEBUG_ATOMIC_SLEEP DEBUG_MUTEXES DEBUG_RT_MUTEXES"
OPTIONS="$OPTIONS IPV6 IPV6_ROUTER_PREF IPV6_MULTIPLE_TABLES IPV6_ROUTE_INFO"
OPTIONS="$OPTIONS TUN SYN_COOKIES IP_ADVANCED_ROUTER IP_MULTIPLE_TABLES"
OPTIONS="$OPTIONS NETFILTER NETFILTER_ADVANCED NETFILTER_XTABLES"
OPTIONS="$OPTIONS NETFILTER_XT_MARK NETFILTER_XT_TARGET_MARK"
OPTIONS="$OPTIONS IP_NF_IPTABLES IP_NF_MANGLE IP_NF_FILTER"
OPTIONS="$OPTIONS IP6_NF_IPTABLES IP6_NF_MANGLE IP6_NF_FILTER INET6_IPCOMP"
OPTIONS="$OPTIONS IPV6_PRIVACY IPV6_OPTIMISTIC_DAD"
OPTIONS="$OPTIONS CONFIG_IPV6_ROUTE_INFO CONFIG_IPV6_ROUTER_PREF"
OPTIONS="$OPTIONS CONFIG_NETFILTER_XT_TARGET_NFLOG"
OPTIONS="$OPTIONS CONFIG_NETFILTER_XT_MATCH_QUOTA"
OPTIONS="$OPTIONS CONFIG_NETFILTER_XT_MATCH_QUOTA2"
OPTIONS="$OPTIONS CONFIG_NETFILTER_XT_MATCH_QUOTA2_LOG"
OPTIONS="$OPTIONS CONFIG_NETFILTER_TPROXY"
OPTIONS="$OPTIONS CONFIG_NETFILTER_XT_MATCH_SOCKET"
OPTIONS="$OPTIONS CONFIG_NETFILTER_XT_MATCH_QTAGUID"
OPTIONS="$OPTIONS CONFIG_INET_UDP_DIAG CONFIG_INET_DIAG_DESTROY"
OPTIONS="$OPTIONS IP_SCTP INET_SCTP_DIAG"
OPTIONS="$OPTIONS CONFIG_IP_NF_TARGET_REJECT CONFIG_IP_NF_TARGET_REJECT_SKERR"
OPTIONS="$OPTIONS CONFIG_IP6_NF_TARGET_REJECT CONFIG_IP6_NF_TARGET_REJECT_SKERR"
OPTIONS="$OPTIONS BPF_SYSCALL NET_KEY XFRM_USER XFRM_STATISTICS CRYPTO_CBC"
OPTIONS="$OPTIONS CRYPTO_CTR CRYPTO_HMAC CRYPTO_AES CRYPTO_SHA1 CRYPTO_SHA256"
OPTIONS="$OPTIONS CRYPTO_SHA12 CRYPTO_USER INET_AH INET_ESP INET_XFRM_MODE"
OPTIONS="$OPTIONS TRANSPORT INET_XFRM_MODE_TUNNEL INET6_AH INET6_ESP"
OPTIONS="$OPTIONS INET6_XFRM_MODE_TRANSPORT INET6_XFRM_MODE_TUNNEL"
OPTIONS="$OPTIONS CRYPTO_SHA256 CRYPTO_SHA512 CRYPTO_AES_X86_64"
OPTIONS="$OPTIONS CRYPTO_ECHAINIV NET_IPVTI IP6_VTI"
OPTIONS="$OPTIONS SOCK_CGROUP_DATA CGROUP_BPF"

# For 3.1 kernels, where devtmpfs is not on by default.
OPTIONS="$OPTIONS DEVTMPFS DEVTMPFS_MOUNT"

# These two break the flo kernel due to differences in -Werror on recent GCC.
DISABLE_OPTIONS=" CONFIG_REISERFS_FS CONFIG_ANDROID_PMEM"
# This one breaks the fugu kernel due to a nonexistent sem_wait_array.
DISABLE_OPTIONS="$DISABLE_OPTIONS CONFIG_SYSVIPC"

# How many TAP interfaces to create to provide the VM with real network access
# via the host. This requires privileges (e.g., root access) on the host.
#
# This is not needed to run the tests, but can be used, for example, to allow
# the VM to update system packages, or to write tests that need access to a
# real network. The VM does not set up networking by default, but it contains a
# DHCP client and has the ability to use IPv6 autoconfiguration. This script
# does not perform any host-level setup beyond configuring tap interfaces;
# configuring IPv4 NAT and/or IPv6 router advertisements or ND proxying must
# be done separately.
NUMTAPINTERFACES=0

# The root filesystem disk image we'll use.
ROOTFS=net_test.rootfs.20150203
COMPRESSED_ROOTFS=$ROOTFS.xz
URL=https://dl.google.com/dl/android/$COMPRESSED_ROOTFS

# Parse arguments and figure out which test to run.
J=${J:-64}
MAKE="make"
OUT_DIR=$(readlink -f ${OUT_DIR:-.})
KERNEL_DIR=$(readlink -f ${KERNEL_DIR:-.})
if [ "$OUT_DIR" != "$KERNEL_DIR" ]; then
    MAKE="$MAKE O=$OUT_DIR"
fi
SCRIPT_DIR=$(dirname $(readlink -f $0))
CONFIG_SCRIPT=${KERNEL_DIR}/scripts/config
CONFIG_FILE=${OUT_DIR}/.config
consolemode=
testmode=
blockdevice=ubda
nobuild=0
norun=0

while [ -n "$1" ]; do
  if [ "$1" = "--builder" ]; then
    consolemode="con=null,fd:1"
    testmode=builder
    shift
  elif [ "$1" == "--readonly" ]; then
    blockdevice="${blockdevice}r"
    shift
  elif [ "$1" == "--nobuild" ]; then
    nobuild=1
    shift
  elif [ "$1" == "--norun" ]; then
    norun=1
    shift
  else
    test=$1
    break  # Arguments after the test file are passed to the test itself.
  fi
done

# Check that test file exists and is readable
test_file=$SCRIPT_DIR/$test
if [[ ! -e $test_file ]]; then
  echo "test file '${test_file}' does not exist"
  exit 1
fi

if [[ ! -x $test_file ]]; then
  echo "test file '${test_file}' is not executable"
  exit 1
fi

# Collect trailing arguments to pass to $test
test_args=${@:2}

function isRunningTest() {
  [[ -n "$test" ]] && ! (( norun ))
}

function isBuildOnly() {
  [[ -z "$test" ]] && (( norun )) && ! (( nobuild ))
}

if ! isRunningTest && ! isBuildOnly; then
  echo "Usage:" >&2
  echo "  $0 [--builder] [--readonly] [--nobuild] <test>" >&2
  echo "  $0 --norun" >&2
  exit 1
fi

cd $OUT_DIR
echo Running tests from: `pwd`

set -e

# Check if we need to uncompress the disk image.
# We use xz because it compresses better: to 42M vs 72M (gzip) / 62M (bzip2).
cd $SCRIPT_DIR
if [ ! -f $ROOTFS ]; then
  echo "Deleting $COMPRESSED_ROOTFS" >&2
  rm -f $COMPRESSED_ROOTFS
  echo "Downloading $URL" >&2
  wget -nv $URL
  echo "Uncompressing $COMPRESSED_ROOTFS" >&2
  unxz $COMPRESSED_ROOTFS
fi
echo "Using $ROOTFS"
cd -

# If network access was requested, create NUMTAPINTERFACES tap interfaces on
# the host, and prepare UML command line params to use them. The interfaces are
# called <user>TAP0, <user>TAP1, on the host, and eth0, eth1, ..., in the VM.
if (( $NUMTAPINTERFACES > 0 )); then
  user=${USER:0:10}
  tapinterfaces=
  netconfig=
  for id in $(seq 0 $(( NUMTAPINTERFACES - 1 )) ); do
    tap=${user}TAP$id
    tapinterfaces="$tapinterfaces $tap"
    mac=$(printf fe:fd:00:00:00:%02x $id)
    netconfig="$netconfig eth$id=tuntap,$tap,$mac"
  done

  for tap in $tapinterfaces; do
    if ! ip link list $tap > /dev/null; then
      echo "Creating tap interface $tap" >&2
      sudo tunctl -u $USER -t $tap
      sudo ip link set $tap up
    fi
  done
fi

if [ -n "$KERNEL_BINARY" ]; then
  nobuild=1
else
  KERNEL_BINARY=./linux
fi

if ((nobuild == 0)); then
  # Exporting ARCH=um SUBARCH=x86_64 doesn't seem to work, as it "sometimes"
  # (?) results in a 32-bit kernel.

  # If there's no kernel config at all, create one or UML won't work.
  [ -f $CONFIG_FILE ] || (cd $KERNEL_DIR && $MAKE defconfig ARCH=um SUBARCH=x86_64)

  # Enable the kernel config options listed in $OPTIONS.
  cmdline=${OPTIONS// / -e }
  $CONFIG_SCRIPT --file $CONFIG_FILE $cmdline

  # Disable the kernel config options listed in $DISABLE_OPTIONS.
  cmdline=${DISABLE_OPTIONS// / -d }
  $CONFIG_SCRIPT --file $CONFIG_FILE $cmdline

  # olddefconfig doesn't work on old kernels.
  if ! $MAKE olddefconfig ARCH=um SUBARCH=x86_64 CROSS_COMPILE= ; then
    cat >&2 << EOF

Warning: "make olddefconfig" failed.
Perhaps this kernel is too old to support it.
You may get asked lots of questions.
Keep enter pressed to accept the defaults.

EOF
  fi

  # Compile the kernel.
  $MAKE -j$J linux ARCH=um SUBARCH=x86_64 CROSS_COMPILE=
fi

if (( norun == 1 )); then
  exit 0
fi

# Get the absolute path to the test file that's being run.
dir=/host$SCRIPT_DIR

# Start the VM.
exec $KERNEL_BINARY umid=net_test $blockdevice=$SCRIPT_DIR/$ROOTFS \
    mem=512M init=/sbin/net_test.sh net_test=$dir/$test \
    net_test_args=\"$test_args\" \
    net_test_mode=$testmode $netconfig $consolemode >&2
