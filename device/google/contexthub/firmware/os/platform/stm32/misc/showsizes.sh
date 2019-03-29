#!/bin/bash

# Exit in error if we use an undefined variable (i.e. commit a typo).
set -u

function getvar {
	hex=$(nm $1 | grep -v "U" | grep "$2" |awk '{print "16#" $1 }')
	echo $(($hex))
}


heap_start=$(getvar $1 __heap_start)
heap_end=$(getvar $1 __heap_end)
heap_sz=$(($heap_end-$heap_start))

bss_start=$(getvar $1 __bss_start)
bss_end=$(getvar $1 __bss_end)
bss_sz=$(($bss_end-$bss_start))

data_start=$(getvar $1 __data_start)
data_end=$(getvar $1 __data_end)
data_sz=$(($data_end-$data_start))

stack_start=$(getvar $1 __stack_bottom)
stack_end=$(getvar $1 __stack_top)
stack_sz=$(($stack_end-$stack_start))

code_start=$(getvar $1 __code_start)
code_end=$(getvar $1 __text_end)
code_sz=$(($code_end-$code_start))

echo
echo "SIZES:"

printf "  BSS SIZE:         %6d bytes\n" $bss_sz
printf "  DATA SIZE:        %6d bytes\n" $data_sz
printf "  STACK SIZE:       %6d bytes\n" $stack_sz
printf "  HEAP SIZE:        %6d bytes\n" $heap_sz
printf "  CODE SIZE:        %6d bytes\n" $code_sz

flash_use=$(($code_sz+$data_sz))
ram_use=$(($heap_sz+$bss_sz+$data_sz+$stack_sz))

echo
printf "  OS RAM USE:       %6d bytes\n" $ram_use
printf "  OS FLASH USE:     %6d bytes\n" $flash_use
