AC_INIT("", "", [linda.s.cline@intel.com])

AC_CONFIG_MACRO_DIR(m4)

AS_MIX_VERSION(mixvideo, MIXVIDEO, 0, 1, 14)

dnl AM_MAINTAINER_MODE provides the option to enable maintainer mode
AM_MAINTAINER_MODE

AM_INIT_AUTOMAKE($PACKAGE, $VERSION)
#AM_INIT_AUTOMAKE([-Wall -Werror foreign])

dnl make aclocal work in maintainer mode
AC_SUBST(ACLOCAL_AMFLAGS, "-I m4")

AM_CONFIG_HEADER(config.h)

dnl check for tools
AC_PROG_CC
AC_PROG_LIBTOOL

#MIX_CFLAGS="-Wall -Werror"
MIX_CFLAGS="-Wall"

AC_ARG_ENABLE(mixlog, 
	AS_HELP_STRING([--enable-mixlog],
		[Enable mixlog (default=no)]),
	[mixlog_enabled=$enableval],
	[mixlog_enabled=no])
AC_MSG_RESULT($mixlog_enabled)
AM_CONDITIONAL(MIXLOG_ENABLED, test "$mixlog_enabled" = "yes")


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

LIBVA_REQ=0.31
PKG_CHECK_MODULES(LIBVA, libva >= $LIBVA_REQ,HAVE_LIBVA=yes,HAVE_LIBVA=no)
if test "x$HAVE_LIBVA" = "xno"; then
  AC_MSG_ERROR(You need libva development package installed !)
fi
#LIBVA_CFLAGS="-I/usr/local/include"
#LIBVA_LIBS="-lva"


LIBVA_X11_REQ=0.31
PKG_CHECK_MODULES(LIBVA_X11, libva-x11 >= $LIBVA_X11_REQ,HAVE_LIBVA_X11=yes,HAVE_LIBVA_X11=no)
if test "x$HAVE_LIBVA_X11" = "xno"; then
  AC_MSG_ERROR(You need libva development package installed !)
fi
#LIBVA_X11_CFLAGS="-I/usr/local/include"
#LIBVA_X11LIBS="-lva-x11"


MIXCOMMON_REQ=0.1
PKG_CHECK_MODULES(MIXCOMMON, mixcommon >= $MIXCOMMON_REQ, HAVE_MIXCOMMON=yes, HAVE_MIXCOMMON=no)
if test "x$HAVE_MIXCOMMON" = "xno"; then
  AC_MSG_ERROR(You need mixcommon development package installed !)
fi

MIXVBP_REQ=0.1
PKG_CHECK_MODULES(MIXVBP, mixvbp >= $MIXVBP_REQ, HAVE_MIXVBP=yes, HAVE_MIXVBP=no)
if test "x$HAVE_MIXVBP" = "xno"; then
  AC_MSG_ERROR(You need mixvbp development package installed !)
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
AC_SUBST(LIBVA_CFLAGS)
AC_SUBST(LIBVA_LIBS)
AC_SUBST(MIXCOMMON_CFLAGS)
AC_SUBST(MIXCOMMON_LIBS)
AC_SUBST(MIXVBP_CFLAGS)
AC_SUBST(MIXVBP_LIBS)


#check for gtk-doc
#GTK_DOC_CHECK(1.9)

AC_CONFIG_FILES([
mixvideo.pc
mixvideoint.pc
Makefile
src/Makefile
])

dnl Additional Makefiles if we are building document with gtkdoc.
dnl Un-comment this section to enable building of documentation.
dnl AC_CONFIG_FILES(
dnl docs/Makefile
dnl docs/reference/Makefile
#dnl docs/reference/MixVideo/Makefile
dnl )

AC_OUTPUT


