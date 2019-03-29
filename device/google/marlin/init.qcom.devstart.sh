#!/vendor/bin/sh

echo 1 > /sys/kernel/boot_adsp/boot
echo 1 > /sys/kernel/boot_slpi/boot
setprop sys.qcom.devup 1

version_strings=($(grep -ao -e "QC_IMAGE_VERSION_STRING[ -~]*" \
                            -e "OEM_IMAGE_UUID_STRING[ -~]*" \
                        /vendor/firmware/slpi.b04))
version1=${version_strings[0]/QC_IMAGE_VERSION_STRING=/}
version2=${version_strings[1]/OEM_IMAGE_UUID_STRING=Q_SENTINEL_/}
setprop sys.slpi.firmware.version "$version1 $version2"
