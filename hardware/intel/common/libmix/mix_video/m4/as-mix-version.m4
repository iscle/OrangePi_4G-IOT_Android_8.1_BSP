dnl as-mix-version.m4 

dnl AS_MIX_VERSION(PACKAGE, PREFIX, MAJOR, MINOR, RELEASE)

dnl example
dnl AS_MIX_VERSION(mixvideo,MIXVIDEO, 0, 3, 2,)
dnl for a 0.3.2 release version

dnl this macro
dnl - defines [$PREFIX]_MAJOR, MINOR and REVISION, CURRENT, AGE
dnl - defines [$PREFIX], VERSION
dnl - AC_SUBST's all defined vars

AC_DEFUN([AS_MIX_VERSION],
[
  PACKAGE=[$1]
  [$2]_MAJOR=[$3]
  [$2]_MINOR=[$4]
  [$2]_REVISION=[$5]
  [$2]_CURRENT=m4_eval([$3] + [$4])
  [$2]_AGE=[$4]
  VERSION=[$3].[$4].[$5]

  AC_SUBST([$2]_MAJOR)
  AC_SUBST([$2]_MINOR)
  AC_SUBST([$2]_REVISION)
  AC_SUBST([$2]_CURRENT)
  AC_SUBST([$2]_AGE)

  AC_DEFINE_UNQUOTED(PACKAGE, "$PACKAGE", [Define the package name])
  AC_SUBST(PACKAGE)
  AC_DEFINE_UNQUOTED(VERSION, "$VERSION", [Define the version])
  AC_SUBST(VERSION)
  
])
