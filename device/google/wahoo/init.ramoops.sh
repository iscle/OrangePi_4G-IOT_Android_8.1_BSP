#!/system/bin/sh

# Decrypt the keys and write them to the kernel
ramoops -D

if [ $? -eq 0 ]; then
    # Pivot (and decrypt)
    echo 1 > /sys/devices/virtual/ramoops/pstore/use_alt
else
    setprop sys.ramoops.decryption.error $?
fi

# Trigger remount of pstore regardless of decryption state
setprop sys.ramoops.decrypted true

# Generate keys (if none exist), and load the keys to carveout
if [[ $(getprop ro.hardware) == "walleye" ]]; then
    ramoops -g -l -c
else
    ramoops -g -l
fi

