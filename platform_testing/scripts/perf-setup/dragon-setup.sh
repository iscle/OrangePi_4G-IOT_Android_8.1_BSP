# performance testing setup script for dragon device

if [[ "`id -u`" -ne "0" ]]; then
  echo "WARNING: running as non-root, proceeding anyways..."
fi

# locking CPU frequency

# note: locking cpu0 is sufficent to cover other cores as well
echo userspace > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor
echo 1530000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq
echo 1530000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq
echo 1530000 > /sys/devices/system/cpu/cpu0/cpufreq/scaling_setspeed

# locking GPU frequency

# note: frequency choices can be found in:
# cat /sys/class/drm/card0/device/pstate

# select 768 MHz
# 0a: core 768 MHz emc 1600 MHz
echo 0a > /sys/class/drm/card0/device/pstate
