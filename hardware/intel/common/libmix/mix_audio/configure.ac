AC_INIT("","",[echo.choi@intel.com])

AC_CONFIG_MACRO_DIR(m4)

AS_MIX_VERSION(mixaudio, MIXAUDIO, 0, 3, 5)

dnl AM_MAINTAINER_MODE provides the option to enable maintainer mode
AM_MAINTAINER_MODE

AM_INIT_AUTOMAKE($PACKAGE, $VERSION)

MIXAUDIO_PKG_DEPS="glib-2.0, gobject-2.0, gthread-2.0 mixcommon"
AC_SUBST(MIXAUDIO_PKG_DEPS)

dnl make aclocal work in maintainer mode
AC_SUBST(ACLOCAL_AMFLAGS, "-I m4")

AM_CONFIG_HEADER(config.h)

dnl check for tools
AC_PROG_CC_C_O
AC_PROG_CC
AC_PROG_LIBTOOL

MIX_CFLAGS="-Wall -Werror -O"

AC_ARG_ENABLE([lpestub],
    [  --enable-lpestub    Stub LPE methods],
    [case "${enableval}" in
      yes) lpestub=true ;;
      no)  lpestub=false ;;
      *) AC_MSG_ERROR([bad value ${enableval} for --enable-lpestub]) ;;
    esac],[lpestub=false])

AM_CONDITIONAL([LPESTUB], [test x$lpestub = xtrue])

AC_ARG_ENABLE([workaround],
    [  --enable-workaround    Enable workaround for LPE DROP],
    [case "${enableval}" in
      yes) workaround=true ;;
      no)  workaround=false ;;
      *) AC_MSG_ERROR([bad value ${enableval} for --enable-workaround]) ;;
    esac],[workaround=false])

AM_CONDITIONAL([WORKAROUND], [test x$workaround = xtrue])

AC_ARG_ENABLE([audiomanager],
    [  --enable-audiomanager    Audio Manager methods(default=enable)],
    [case "${enableval}" in
      yes) audiomanager=true ;;
      no)  audiomanager=false ;;
      *) AC_MSG_ERROR([bad value ${enableval} for --enable-audiomanager]) ;;
    esac],[audiomanager=true])

AM_CONDITIONAL([AUDIO_MANAGER], [test x$audiomanager = xtrue])

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

PKG_CHECK_MODULES(GTHREAD, gthread-2.0 >= $GLIB_REQ,HAVE_GTHREAD=yes,HAVE_GTHREAD=no)
if test "x$HAVE_GTRHEAD" = "xno"; then
  AC_MSG_ERROR(You need glib development packages installed !)
fi

MIXCOMMON_REQUIRED=0.1.6
PKG_CHECK_MODULES(MIXCOMMON, mixcommon >= $MIXCOMMON_REQUIRED , HAVE_MIXCOMMON=yes, HAVE_MIXCOMMON=no)
if test "x$HAVE_MIXCOMMON" = "xno"; then
  AC_MSG_ERROR(You need mixcommon development package $MIXCOMMON_REQUIRED installed !)
fi

if test "x$audiomanager" = "xtrue"; then
  PKG_CHECK_MODULES(DBUS_GLIB, dbus-glib-1)
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
AC_SUBST(MIXCOMMON_CFLAGS)
AC_SUBST(MIXCOMMON_LIBS)

dnl check for gtk-doc
dnl GTK_DOC_CHECK(1.9)

AC_CONFIG_FILES(
Makefile
src/Makefile
tests/Makefile
tests/smoke/Makefile
pkgconfig/Makefile
pkgconfig/mixaudio.pc
)

dnl Additional Makefiles if we are building document with gtkdoc.
dnl Un-comment this section to enable building of documentation.
dnl AC_CONFIG_FILES(
dnl docs/Makefile
dnl docs/reference/Makefile
dnl docs/reference/MixAudio/Makefile
dnl )

AC_OUTPUT


