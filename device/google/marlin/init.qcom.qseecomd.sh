#!/system/bin/sh

registered="$1"

while [ "$registered" != "true" ]
do
    sleep 0.1
    registered="`getprop sys.listeners.registered`"
done
