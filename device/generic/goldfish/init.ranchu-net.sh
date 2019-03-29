#!/vendor/bin/sh

# Setup networking when boot starts
ifconfig eth0 10.0.2.15 netmask 255.255.255.0 up
route add default gw 10.0.2.2 dev eth0


# Setup additionnal DNS servers if needed
num_dns=`getprop ro.kernel.ndns`
case "$num_dns" in
    2) setprop net.eth0.dns2 10.0.2.4
       ;;
    3) setprop net.eth0.dns2 10.0.2.4
       setprop net.eth0.dns3 10.0.2.5
       ;;
    4) setprop net.eth0.dns2 10.0.2.4
       setprop net.eth0.dns3 10.0.2.5
       setprop net.eth0.dns4 10.0.2.6
       ;;
esac


# set up the second interface (for inter-emulator connections)
# if required
my_ip=`getprop net.shared_net_ip`
case "$my_ip" in
    "")
    ;;
    *) ifconfig eth1 "$my_ip" netmask 255.255.255.0 up
    ;;
esac

