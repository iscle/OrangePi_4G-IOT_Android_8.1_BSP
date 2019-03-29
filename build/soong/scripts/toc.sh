#!/bin/bash -eu

# Script to handle generating a .toc file from a .so file
# Inputs:
#  Environment:
#   CROSS_COMPILE: prefix added to readelf tool
#  Arguments:
#   -i ${file}: input file (required)
#   -o ${file}: output file (required)
#   -d ${file}: deps file (required)

OPTSTRING=d:i:o:-:

usage() {
    cat <<EOF
Usage: toc.sh [options] -i in-file -o out-file -d deps-file
Options:
EOF
    exit 1
}

do_elf() {
    ("${CROSS_COMPILE}readelf" -d "${infile}" | grep SONAME || echo "No SONAME for ${infile}") > "${outfile}.tmp"
    "${CROSS_COMPILE}readelf" --dyn-syms "${infile}" | awk '{$2=""; $3=""; print}' >> "${outfile}.tmp"
}

do_macho() {
    otool -l "${infile}" | grep LC_ID_DYLIB -A 5 > "${outfile}.tmp"
    nm -gP "${infile}" | cut -f1-2 -d" " | grep -v 'U$' >> "${outfile}.tmp"
}


while getopts $OPTSTRING opt; do
    case "$opt" in
        d) depsfile="${OPTARG}" ;;
        i) infile="${OPTARG}" ;;
        o) outfile="${OPTARG}" ;;
        -)
            case "${OPTARG}" in
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

rm -f "${outfile}.tmp"

cat <<EOF > "${depsfile}"
${outfile}: \\
  ${CROSS_COMPILE}readelf \\
EOF

do_elf

if cmp "${outfile}" "${outfile}.tmp" > /dev/null 2> /dev/null; then
    rm -f "${outfile}.tmp"
else
    mv -f "${outfile}.tmp" "${outfile}"
fi
