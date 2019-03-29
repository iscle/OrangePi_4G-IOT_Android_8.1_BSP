#!/vendor/bin/sh

# Convert ro.boot.mid to ro.boot.hardware.sku format

SKU=`getprop ro.boot.mid`
if [ -z "$SKU" ]; then
    SKU=unknown
fi
setprop ro.boot.hardware.sku ${SKU//-/}
