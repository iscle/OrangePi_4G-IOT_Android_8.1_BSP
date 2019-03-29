#!/system/bin/sh

ORIG_ARGS="$@"

if [ ! -n "$1" ]
then
	echo "Usage: $0 tz-name gov-name par1 v1 par2 v2 ..."
	echo "Example: $0 skin-therm pd_thermal_gov max_err_temp 5000"
	exit 0;
fi

# find thermal zone
for tz in $(ls -d /sys/class/thermal/thermal_zone?)
do
	type=$(cat $tz/type)
	if [ "$type" = "$1" ]
	then
		break
	fi
	tz=""
done

if [ ! -n "$tz" ]
then
	echo "can't find thermal zone "$1
	exit 0;
fi

# set governor
for gov in $(cat $tz/available_policies)
do
	if [ "$gov" = "$2" ]
	then
		echo "$2" > $tz/policy
		break
	fi
	gov=""
done

if [ ! -n "$gov" ]
then
	echo $2 "is not a available policy"
	exit 0
fi

update_par() {
	if [ ! -f "$tz/$gov/$1" ]
	then
		echo $gov "doesn't have" $1
		return 1;
	fi

	echo $2 > $tz/$gov/$1
	echo "set $tz/$gov/$1 to $2"
	return 0
}

shift
shift
if [ ! -n "$1" ]
then
	exit 0
fi

if [ -n "$2" ]
then
	if [ -d "$tz/$gov" ]
	then
		while [ -n "$2" ]
		do
			update_par $1 $2
			if [ $? -eq 1 ]
			then
				exit 0
			fi

			shift
			shift
		done
	else
		echo $gov "doesn't support setting parameters"
		exit 0
	fi
else
	echo "wrong governor parameters"
fi

exit 0
