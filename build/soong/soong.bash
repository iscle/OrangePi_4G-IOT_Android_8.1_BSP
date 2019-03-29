#!/bin/bash

set -e

# Switch to the build directory
cd $(dirname "${BASH_SOURCE[0]}")

# The source directory path and operating system will get written to
# .soong.bootstrap by the bootstrap script.

BOOTSTRAP=".soong.bootstrap"
if [ ! -f "${BOOTSTRAP}" ]; then
    echo "Error: soong script must be located in a directory created by bootstrap.bash"
    exit 1
fi

source "${BOOTSTRAP}"

# Now switch to the source directory so that all the relative paths from
# $BOOTSTRAP are correct
cd ${SRCDIR_FROM_BUILDDIR}

# Ninja can't depend on environment variables, so do a manual comparison
# of the relevant environment variables from the last build using the
# soong_env tool and trigger a build manifest regeneration if necessary
ENVFILE="${BUILDDIR}/.soong.environment"
ENVTOOL="${BUILDDIR}/.bootstrap/bin/soong_env"
if [ -f "${ENVFILE}" ]; then
    if [ -x "${ENVTOOL}" ]; then
        if ! "${ENVTOOL}" "${ENVFILE}"; then
            echo "forcing build manifest regeneration"
            rm -f "${ENVFILE}"
        fi
    else
        echo "Missing soong_env tool, forcing build manifest regeneration"
        rm -f "${ENVFILE}"
    fi
fi

BUILDDIR="${BUILDDIR}" NINJA="prebuilts/build-tools/${PREBUILTOS}/bin/ninja" build/blueprint/blueprint.bash "$@"
