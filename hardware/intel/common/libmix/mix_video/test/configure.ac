
AC_INIT([testmixvideo], [0.1], [tao.q.tao@intel.com])

dnl AC_CONFIG_MACRO_DIR([m4])

AM_INIT_AUTOMAKE($PACKAGE, $VERSION)
AM_INIT_AUTOMAKE([-Wall -Werror foreign])

AC_PROG_CC
AC_PROG_LIBTOOL

AC_CHECK_PROG(HAVE_PKGCONFIG, pkg-config, yes , no)

dnl Give error and exit if we don't have pkgconfig
if test "x$HAVE_PKGCONFIG" = "xno"; then
  AC_MSG_ERROR(you need to have pkgconfig installed !)
fi

GLIB_REQ=2.18
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

MIXVIDEO_REQ=0.5
PKG_CHECK_MODULES(MIXVIDEO, mixvideo >= $MIXVIDEO_REQ,HAVE_MIXVIDEO=yes,HAVE_MIXVIDEO=no)
if test "x$HAVE_MIXVIDEO" = "xno"; then
  AC_MSG_ERROR(You need mixvideo development packages installed !)
fi

AC_ARG_ENABLE(optimization,     AC_HELP_STRING([  --disable-optimization], [Do not optimize the library for speed. Might be required for debugging.]))
AC_ARG_ENABLE(debuginfo,        AC_HELP_STRING([  --enable-debuginfo    ],  [add -g to the compiler flags (to create debug information)]))

if test "$enable_optimization" = "no" ; then
        DEBUG=true
else
        DEBUG=false
fi


AC_CONFIG_HEADERS([config.h])
AC_CONFIG_FILES([
	Makefile
	src/Makefile
])
AC_OUTPUT
