#!/system/bin/sh

#
# Copy qcril.db if needed for RIL
#
if [ -f /vendor/qcril.db -a ! -f /data/misc/radio/qcril.db ]; then
    cp /vendor/qcril.db /data/misc/radio/qcril.db
    chown -h radio.radio /data/misc/radio/qcril.db
fi
echo 1 > /data/misc/radio/db_check_done
