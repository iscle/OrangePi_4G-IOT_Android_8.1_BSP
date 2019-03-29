#!/system/bin/sh

registered="`getprop sys.listeners.registered`"
while [ "$registered" != "true" ]
do
    sleep 0.1
    registered="`getprop sys.listeners.registered`"
done
