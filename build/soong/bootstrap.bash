#!/bin/bash

set -e

ORIG_SRCDIR=$(dirname "${BASH_SOURCE[0]}")
if [[ "$ORIG_SRCDIR" != "." ]]; then
  if [[ ! -z "$BUILDDIR" ]]; then
    echo "error: To use BUILDDIR, run from the source directory"
    exit 1
  fi
  export BUILDDIR=$("${ORIG_SRCDIR}/build/soong/scripts/reverse_path.py" "$ORIG_SRCDIR")
  cd $ORIG_SRCDIR
fi
if [[ -z "$BUILDDIR" ]]; then
  echo "error: Run ${BASH_SOURCE[0]} from the build output directory"
  exit 1
fi
export SRCDIR="."
export BOOTSTRAP="${SRCDIR}/bootstrap.bash"

export TOPNAME="Android.bp"
export BOOTSTRAP_MANIFEST="${SRCDIR}/build/soong/build.ninja.in"
export RUN_TESTS="-t"

case $(uname) in
    Linux)
	export GOOS="linux"
	export PREBUILTOS="linux-x86"
	;;
    Darwin)
	export GOOS="darwin"
	export PREBUILTOS="darwin-x86"
	;;
    *) echo "unknown OS:" $(uname) && exit 1;;
esac
export GOROOT="${SRCDIR}/prebuilts/go/$PREBUILTOS/"
export GOARCH="amd64"
export GOCHAR="6"

if [[ $# -eq 0 ]]; then
    mkdir -p $BUILDDIR

    if [[ $(find $BUILDDIR -maxdepth 1 -name Android.bp) ]]; then
      echo "FAILED: The build directory must not be a source directory"
      exit 1
    fi

    export SRCDIR_FROM_BUILDDIR=$(build/soong/scripts/reverse_path.py "$BUILDDIR")

    sed -e "s|@@BuildDir@@|${BUILDDIR}|" \
        -e "s|@@SrcDirFromBuildDir@@|${SRCDIR_FROM_BUILDDIR}|" \
        -e "s|@@PrebuiltOS@@|${PREBUILTOS}|" \
        "$SRCDIR/build/soong/soong.bootstrap.in" > $BUILDDIR/.soong.bootstrap
    ln -sf "${SRCDIR_FROM_BUILDDIR}/build/soong/soong.bash" $BUILDDIR/soong
fi

"$SRCDIR/build/blueprint/bootstrap.bash" "$@"
