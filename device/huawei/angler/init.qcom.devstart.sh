#!/system/bin/sh

echo 1 > /sys/kernel/boot_adsp/boot
setprop sys.qcom.devup 1
