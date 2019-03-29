#! /vendor/bin/sh

#########################################
### init.insmod.cfg format:           ###
### --------------------------------- ###
### [insmod|setprop] [path|prop name] ###
### ...                               ###
#########################################

if [[ -e "/vendor/etc/init.insmod_charger.cfg" && "$(getprop ro.boot.mode)" == "charger" ]]; then
  cfg_file="/vendor/etc/init.insmod_charger.cfg"
else
  cfg_file="/vendor/etc/init.insmod.cfg"
fi

if [ -f $cfg_file ]; then
  while IFS=" " read -r action name
  do
    case $action in
      "insmod") insmod $name ;;
      "setprop") setprop $name 1 ;;
    esac
  done < $cfg_file
fi

# set property even if there is no insmod config
# as property value "1" is expected in early-boot trigger
setprop sys.all.modules.ready 1
