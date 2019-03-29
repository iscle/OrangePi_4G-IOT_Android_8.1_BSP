#!/system/bin/sh

LOG_FILE="/data/misc/touchfwup/rmi4update.txt"

echo -n "Updater date/time: " > ${LOG_FILE}
date >> ${LOG_FILE}
echo "Pre-update Firmware version:" >> ${LOG_FILE}
/system/bin/rmi4update -d /dev/hidraw0 -p >> ${LOG_FILE} 2>&1

/system/bin/rmi4update -d /dev/hidraw0 /vendor/firmware/synaptics.img >> ${LOG_FILE} 2>&1

echo "Post-update Firmware version:" >> ${LOG_FILE}
/system/bin/rmi4update -d /dev/hidraw0 -p >> ${LOG_FILE} 2>&1

chmod a+r ${LOG_FILE}
