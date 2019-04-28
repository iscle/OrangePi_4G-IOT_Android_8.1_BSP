#Setup for 2017 devices

stop thermal-engine
stop mpdecision
stop perfd

cpubase=/sys/devices/system/cpu
gov=cpufreq/scaling_governor

cpu=4
top=8

# Enable the gold cores at max frequency.
# 1248000 1344000 1478400 1555200 1900800 2457600
S=2457600

while [ $((cpu < $top)) -eq 1 ]; do
  echo "setting cpu $cpu to $S kHz"
  echo 1 > $cpubase/cpu${cpu}/online
  echo userspace > $cpubase/cpu${cpu}/$gov
  echo $S > $cpubase/cpu${cpu}/cpufreq/scaling_max_freq
  echo $S > $cpubase/cpu${cpu}/cpufreq/scaling_min_freq
  echo $S > $cpubase/cpu${cpu}/cpufreq/scaling_setspeed
  cat $cpubase/cpu${cpu}/cpufreq/scaling_cur_freq
  cpu=$(($cpu + 1))
done

cpu=0
top=4

# Disable the silver cores.
while [ $((cpu < $top)) -eq 1 ]; do
  echo "disable cpu $cpu"
  echo 0 > $cpubase/cpu${cpu}/online
  cpu=$(($cpu + 1))
done

echo "setting GPU bus split";
echo 0 > /sys/class/kgsl/kgsl-3d0/bus_split;
echo "setting GPU force clocks";
echo 1 > /sys/class/kgsl/kgsl-3d0/force_clk_on;
echo "setting GPU idle timer";
echo 10000 > /sys/class/kgsl/kgsl-3d0/idle_timer;

#0 762 1144 1525 2288 3509 4173 5271 5928 7904 9887 11863 13763
echo "setting GPU bus frequency";
echo 13763 > /sys/class/devfreq/soc:qcom,gpubw/min_freq;
cat /sys/class/devfreq/soc:qcom,gpubw/cur_freq;

# 710000000 600000000 510000000 450000000 390000000 305000000 180000000
echo "GPU performance mode";
G=710000000
echo performance > /sys/class/kgsl/kgsl-3d0/devfreq/governor;
echo $G > /sys/class/kgsl/kgsl-3d0/devfreq/min_freq;
echo $G > /sys/class/kgsl/kgsl-3d0/devfreq/max_freq;

cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq;
