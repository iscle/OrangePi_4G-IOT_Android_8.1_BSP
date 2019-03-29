#!/bin/bash -e

# Script to handle the various ways soong may need to strip binaries
# Inputs:
#  Environment:
#   CROSS_COMPILE: prefix added to readelf, objcopy tools
#  Arguments:
#   -i ${file}: input file (required)
#   -o ${file}: output file (required)
#   -d ${file}: deps file (required)
#   --keep-symbols
#   --keep-mini-debug-info
#   --add-gnu-debuglink

OPTSTRING=d:i:o:-:

usage() {
    cat <<EOF
Usage: strip.sh [options] -i in-file -o out-file -d deps-file
Options:
        --keep-symbols          Keep symbols in out-file
        --keep-mini-debug-info  Keep compressed debug info in out-file
        --add-gnu-debuglink     Add a gnu-debuglink section to out-file
EOF
    exit 1
}

do_strip() {
    "${CROSS_COMPILE}strip" --strip-all "${infile}" -o "${outfile}.tmp"
}

do_strip_keep_symbols() {
    "${CROSS_COMPILE}objcopy" "${infile}" "${outfile}.tmp" \
	`"${CROSS_COMPILE}readelf" -S "${infile}" | awk '/.debug_/ {print "-R " $2}' | xargs`
}

do_strip_keep_mini_debug_info() {
    rm -f "${outfile}.dynsyms" "${outfile}.funcsyms" "${outfile}.keep_symbols" "${outfile}.debug" "${outfile}.mini_debuginfo" "${outfile}.mini_debuginfo.xz"
    if "${CROSS_COMPILE}strip" --strip-all -R .comment "${infile}" -o "${outfile}.tmp"; then
        "${CROSS_COMPILE}objcopy" --only-keep-debug "${infile}" "${outfile}.debug"
        "${CROSS_COMPILE}nm" -D "${infile}" --format=posix --defined-only | awk '{ print $$1 }' | sort >"${outfile}.dynsyms"
        "${CROSS_COMPILE}nm" "${infile}" --format=posix --defined-only | awk '{ if ($$2 == "T" || $$2 == "t" || $$2 == "D") print $$1 }' | sort > "${outfile}.funcsyms"
        comm -13 "${outfile}.dynsyms" "${outfile}.funcsyms" > "${outfile}.keep_symbols"
        "${CROSS_COMPILE}objcopy" --rename-section .debug_frame=saved_debug_frame "${outfile}.debug" "${outfile}.mini_debuginfo"
        "${CROSS_COMPILE}objcopy" -S --remove-section .gdb_index --remove-section .comment --keep-symbols="${outfile}.keep_symbols" "${outfile}.mini_debuginfo"
        "${CROSS_COMPILE}objcopy" --rename-section saved_debug_frame=.debug_frame "${outfile}.mini_debuginfo"
        xz "${outfile}.mini_debuginfo"
        "${CROSS_COMPILE}objcopy" --add-section .gnu_debugdata="${outfile}.mini_debuginfo.xz" "${outfile}.tmp"
    else
        cp -f "${infile}" "${outfile}.tmp"
    fi
}

do_add_gnu_debuglink() {
    "${CROSS_COMPILE}objcopy" --add-gnu-debuglink="${infile}" "${outfile}.tmp"
}

while getopts $OPTSTRING opt; do
    case "$opt" in
	d) depsfile="${OPTARG}" ;;
	i) infile="${OPTARG}" ;;
	o) outfile="${OPTARG}" ;;
	-)
	    case "${OPTARG}" in
		keep-symbols) keep_symbols=true ;;
		keep-mini-debug-info) keep_mini_debug_info=true ;;
		add-gnu-debuglink) add_gnu_debuglink=true ;;
		*) echo "Unknown option --${OPTARG}"; usage ;;
	    esac;;
	?) usage ;;
	*) echo "'${opt}' '${OPTARG}'"
    esac
done

if [ -z "${infile}" ]; then
    echo "-i argument is required"
    usage
fi

if [ -z "${outfile}" ]; then
    echo "-o argument is required"
    usage
fi

if [ -z "${depsfile}" ]; then
    echo "-d argument is required"
    usage
fi

if [ ! -z "${keep_symbols}" -a ! -z "${keep_mini_debug_info}" ]; then
    echo "--keep-symbols and --keep-mini-debug-info cannot be used together"
    usage
fi

if [ ! -z "${add_gnu_debuglink}" -a ! -z "${keep_mini_debug_info}" ]; then
    echo "--add-gnu-debuglink cannot be used with --keep-mini-debug-info"
    usage
fi

rm -f "${outfile}.tmp"

if [ ! -z "${keep_symbols}" ]; then
    do_strip_keep_symbols
elif [ ! -z "${keep_mini_debug_info}" ]; then
    do_strip_keep_mini_debug_info
else
    do_strip
fi

if [ ! -z "${add_gnu_debuglink}" ]; then
    do_add_gnu_debuglink
fi

rm -f "${outfile}"
mv "${outfile}.tmp" "${outfile}"

cat <<EOF > "${depsfile}"
${outfile}: \
  ${infile} \
  ${CROSS_COMPILE}nm \
  ${CROSS_COMPILE}objcopy \
  ${CROSS_COMPILE}readelf \
  ${CROSS_COMPILE}strip

EOF
