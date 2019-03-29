#!/bin/bash

# script to push new prebuilt kernel from cros builder

show_usage()
{
	echo usage: $0 [artifact_path] [rootdir] [kernel_path]
	echo For: echo Artifacts[smaug]: smaug-release/R45-7199.0.0
	echo artifact_path=smaug-release/R45-7199.0.0
	echo If kernel comes from nvidia-kernel:
	echo kernel_path=src/partner_private/nvidia-kernel
	exit 1
}

artifact_path=$1
TOP="$2"
if [ -z "$TOP" ]; then
	TOP="$(pwd)"
fi
kernel_path="$3"
if [ -z "$kernel_path" ]; then
	kernel_path="src/third_party/kernel/v3.18"
fi

gsbase=gs://chromeos-image-archive
# smaug-release - works well
# smaug-canary - works well - old, no longer works
# smaug-paladin - potentially has kernel changes that are not upstream
# trybot-smaug-paladin - works well with: cbuildbot --remote smaug-paladin
build=smaug-release
built_kernel="$TOP/device/google/dragon-kernel"
kernel="$TOP/kernel/private/dragon"
preamble="dragon: Update prebuilt kernel to"

if [ ! -d "$built_kernel" ]; then
	echo ERROR: missing prebuilt directory $built_kernel
	show_usage
fi

if [ ! -d "$kernel" ]; then
	echo ERROR: missing kernel directory $kernel
	show_usage
fi

if [ -z "$artifact_path" ]; then
	latest=$(gsutil.py cat ${gsbase}/${build}/LATEST-master)
	if [ $? -ne 0 ]; then
		echo ERROR: could not determine artifact_path
		exit 1
	fi
	artifact_path=${build}/${latest}
fi
gspath=${gsbase}/${artifact_path}

echo promoting kernel from $gspath

cd "$built_kernel"
gsutil.py cat ${gspath}/stripped-packages.tar | bsdtar -s '/.*kernel.*tbz2/kernel.tbz2/' -x -f - '*kernel*'
if [ $? -ne 0 ]; then
	echo ERROR: could not retrieve stripped-packages
	exit 1
fi
bsdtar -s '/.*vmlinuz-3.18.*/Image.fit/' -jxf kernel.tbz2 '*vmlinuz-3.18*'
rm kernel.tbz2
newrev=$(gsutil.py cat ${gspath}/manifest.xml | grep "path=\"${kernel_path}\"" | sed -e 's/.*revision="\([0123456789abcdef]\+\).*/\1/')
oldrev=$(git log --oneline | head -1 | sed -e "s/.*${preamble} \(.*\)/\1/")

cd "$kernel"
git remote update
commitmsg=$(mktemp /tmp/msg.XXXXXX)
cat >>$commitmsg <<EOD
$preamble $newrev

Build: $artifact_path

Changes include:
EOD
git log --graph --oneline $oldrev..$newrev >> $commitmsg

cd "$built_kernel"
git add Image.fit
git commit -t $commitmsg
rm $commitmsg
