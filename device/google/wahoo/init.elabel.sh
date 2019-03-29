#! /system/bin/sh

if [ -d /persist/elabel ]; then
    if [ ! -f /data/misc/elabel/elabels_copied ]; then
        cp /persist/elabel/* /data/misc/elabel/
        echo 1 > /data/misc/elabel/elabels_copied
        chown system.system /data/misc/elabel/*
        chmod 400 /data/misc/elabel/*
    fi
fi
