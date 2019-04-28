if [[ "`id -u`" -ne "0" ]]; then
  echo "WARNING: running as non-root, proceeding anyways..."
fi

stop thermal-engine
stop perfd

cpubase=/sys/devices/system/cpu
gov=cpufreq/scaling_governor

cpu=0
S=960000
while [ $((cpu < 4)) -eq 1 ]; do
    echo 1 > $cpubase/cpu${cpu}/online
    echo userspace > $cpubase/cpu${cpu}/$gov
    echo $S > $cpubase/cpu${cpu}/cpufreq/scaling_max_freq
    echo $S > $cpubase/cpu${cpu}/cpufreq/scaling_min_freq
    echo $S > $cpubase/cpu${cpu}/cpufreq/scaling_setspeed
    cpu=$(($cpu + 1))
done

echo -n 0 > /sys/devices/system/cpu/cpu4/online
echo -n 0 > /sys/devices/system/cpu/cpu5/online
echo -n 0 > /sys/devices/system/cpu/cpu6/online
echo -n 0 > /sys/devices/system/cpu/cpu7/online

echo 0 > /sys/class/kgsl/kgsl-3d0/bus_split
echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on
echo 10000 > /sys/class/kgsl/kgsl-3d0/idle_timer

echo 11863 > /sys/class/devfreq/qcom,gpubw.70/min_freq

echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor
echo 305000000 > /sys/class/kgsl/kgsl-3d0/devfreq/min_freq
echo 305000000 > /sys/class/kgsl/kgsl-3d0/devfreq/max_freq

echo 4 > /sys/class/kgsl/kgsl-3d0/min_pwrlevel
echo 4 > /sys/class/kgsl/kgsl-3d0/max_pwrlevel

