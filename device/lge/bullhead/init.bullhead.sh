#!/system/bin/sh

#
# Make modem config folder and copy firmware config to that folder
#
rm -rf /data/misc/radio/modem_config
mkdir /data/misc/radio/modem_config
chmod 770 /data/misc/radio/modem_config
cp -r /firmware/image/modem_pr/mcfg/configs/* /data/misc/radio/modem_config
chown -hR radio.radio /data/misc/radio/modem_config
echo 1 > /data/misc/radio/copy_complete
