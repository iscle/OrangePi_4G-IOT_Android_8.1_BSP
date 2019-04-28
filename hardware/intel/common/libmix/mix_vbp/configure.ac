AC_INIT("", "", [linda.s.cline@intel.com])

AC_CONFIG_MACRO_DIR(m4)

AS_MIX_VERSION(mixvbp, MIXVBP, 0, 1, 15)

dnl AM_MAINTAINER_MODE provides the option to enable maintainer mode
AM_MAINTAINER_MODE

AM_INIT_AUTOMAKE($PACKAGE, $VERSION)

dnl make aclocal work in maintainer mode
AC_SUBST(ACLOCAL_AMFLAGS, "-I m4")

AM_CONFIG_HEADER(config.h)

dnl check for tools
AC_PROG_CC
AC_PROG_LIBTOOL

MIX_CFLAGS="-Wall -Werror"

dnl decide on error flags
dnl AS_COMPILER_FLAG(-Wall, MIX_ERROR="$MIX_ERROR -Wall", MIX_ERROR="$MIX_ERROR")
dnl AS_COMPILER_FLAG(-Werror,MIX_ERROR="$MIX_ERROR -Werror",MIX_ERROR="$MIX_ERROR")

dnl Check for pkgconfig first
AC_CHECK_PROG(HAVE_PKGCONFIG, pkg-config, yes, no)

dnl Give error and exit if we don't have pkgconfig
if test "x$HAVE_PKGCONFIG" = "xno"; then
  AC_MSG_ERROR(you need to have pkgconfig installed !)
fi

dnl GLib
dnl FIXME: need to align with moblin glib version
dnl FIXME: currently using an earlier version so it can be built on dev box.
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

PKG_CHECK_MODULES(GTHREAD, gthread-2.0 >= $GLIB_REQ,HAVE_GTHREAD=yes,HAVE_GTHREAD=no)
if test "x$HAVE_GTHREAD" = "xno"; then
  AC_MSG_ERROR(You need glib development packages installed !)
fi

dnl Check for documentation xrefs
dnl GLIB_PREFIX="`$PKG_CONFIG --variable=prefix glib-2.0`"
dnl AC_SUBST(GLIB_PREFIX)

AC_SUBST(GLIB_CFLAGS)
AC_SUBST(GLIB_LIBS)
AC_SUBST(GOBJECT_CFLAGS)
AC_SUBST(GOBJECT_LIBS)
AC_SUBST(MIX_CFLAGS)
AC_SUBST(GTHREAD_CFLAGS)
AC_SUBST(GTHREAD_LIBS)

AC_CONFIG_FILES([
mixvbp.pc
Makefile
viddec_fw/fw/parser/Makefile
])

AC_OUTPUT


