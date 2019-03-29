#!/bin/bash -e

OUT=$1
shift
LIBPATH=$($@)
cp -f $LIBPATH $OUT
echo "$OUT: $LIBPATH" > ${OUT}.d
