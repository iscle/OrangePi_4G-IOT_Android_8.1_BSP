#!/system/bin/sh


# ro.kernel.android.qemud is normally set when we
# want the RIL (radio interface layer) to talk to
# the emulated modem through qemud.
#
# However, this will be undefined in two cases:
#
# - When we want the RIL to talk directly to a guest
#   serial device that is connected to a host serial
#   device by the emulator.
#
# - We don't want to use the RIL but the VM-based
#   modem emulation that runs inside the guest system
#   instead.
#
# The following detects the latter case and sets up the
# system for it.
#
qemud=`getprop ro.kernel.android.qemud`
case "$qemud" in
    "")
    radio_ril=`getprop ro.kernel.android.ril`
    case "$radio_ril" in
        "")
        # no need for the radio interface daemon
        # telephony is entirely emulated in Java
        setprop ro.radio.noril yes
        stop ril-daemon
        ;;
    esac
    ;;
esac


# disable boot animation for a faster boot sequence when needed
boot_anim=`getprop ro.kernel.android.bootanim`
case "$boot_anim" in
    0)  setprop debug.sf.nobootanimation 1
    ;;
esac


# take the wake lock
echo "emulator_wake_lock" > /sys/power/wake_lock
