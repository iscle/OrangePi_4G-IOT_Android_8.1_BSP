AC_INIT("", "", [khanh.v.nguyen@intel.com])

AC_CONFIG_MACRO_DIR(m4)

AS_MIX_VERSION(mixcommon, MIXCOMMON, 0, 1, 7)

AM_INIT_AUTOMAKE($PACKAGE, $VERSION)
#AM_INIT_AUTOMAKE([-Wall -Werror foreign])

AC_PROG_CC
AC_PROG_LIBTOOL

AC_CHECK_PROG(HAVE_PKGCONFIG, pkg-config, yes , no)

dnl Give error and exit if we don't have pkgconfig
if test "x$HAVE_PKGCONFIG" = "xno"; then
  AC_MSG_ERROR(you need to have pkgconfig installed !)
fi

GLIB_REQ=2.16
dnl Check for glib2 without extra fat, useful for the unversioned tool frontends
dnl PKG_CHECK_MODULES(GLIB_ONLY, glib-2.0 >= $GLIB_REQ,HAVE_GLIB=yes,HAVE_GLIB=no)
PKG_CHECK_MODULES(GLIB, glib-2.0 >= $GLIB_REQ,HAVE_GLIB=yes,HAVE_GLIB=no)
if test "x$HAVE_GLIB" = "xno"; then
  AC_MSG_ERROR(You need glib development packages installed !)
fi

PKG_CHECK_MODULES(GOBJECT, gobject-2.0 >= $GLIB_REQ,HAVE_GOBJECT=yes,HAVE_GOBJECT=no)
if test "x$HAVE_GOBJECT" = "xno"; then
  AC_MSG_ERROR(You need glib development packages installed !)
fi

AC_CONFIG_HEADERS([config.h])
AC_CONFIG_FILES([
	mixcommon.pc
	Makefile
	src/Makefile
])
AC_OUTPUT
