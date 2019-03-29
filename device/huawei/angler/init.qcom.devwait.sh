#!/system/bin/sh

up="`getprop sys.qcom.devup`"
while [ "$up" != "1" ]
do
    sleep 0.1
    up="`getprop sys.qcom.devup`"
done
