#Setup for newer devices

if [[ "`id -u`" -ne "0" ]]; then
  echo "WARNING: running as non-root, proceeding anyways..."
fi

stop thermal-engine
stop perfd

echo 0 > /sys/devices/system/cpu/cpu0/online
echo 0 > /sys/devices/system/cpu/cpu1/online

echo performance  > /sys/devices/system/cpu/cpu2/cpufreq/scaling_governor
echo 2150400 > /sys/devices/system/cpu/cpu2/cpufreq/scaling_max_freq

echo 13763 > /sys/class/devfreq/soc:qcom,gpubw/max_freq
echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor
echo -n 624000000 > /sys/class/kgsl/kgsl-3d0/devfreq/max_freq
