package=MixCommon

aclocal -I m4/ $ACLOCAL_FLAGS || exit 1
libtoolize --copy --force || exit 1
autoheader -v || exit 1
autoconf -v || exit 1
automake -a -c -v || exit 1
#autoreconf -v --install
