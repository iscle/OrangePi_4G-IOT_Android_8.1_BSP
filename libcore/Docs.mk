# -*- mode: makefile -*-
# List of libcore directories to include in documentation.
# Shared between libcore and frameworks/base.
# Exports: libcore_to_document as a list of .java files relative to libcore/.

ifndef libcore_docs_include_once

include libcore/openjdk_java_files.mk
include libcore/non_openjdk_java_files.mk


# List of libcore javadoc source files
_libcore_files := $(openjdk_javadoc_files) $(non_openjdk_javadoc_files)
_libcore_files := $(addprefix libcore/, $(_libcore_files))

_icu_files := \
 $(call find-files-in-subdirs, external/icu, \
   "*.java", \
   android_icu4j/src/main/java/android/icu/lang \
   android_icu4j/src/main/java/android/icu/math \
   android_icu4j/src/main/java/android/icu/text \
   android_icu4j/src/main/java/android/icu/util \
   )
_icu_files := $(addprefix external/icu/, $(_icu_files))

# List of libcore-related javadoc source files
#
# NOTE: Because libcore-related source spans modules (not just libcore!), files names here are
# returned that are relative to the build root / $(TOPDIR) and not libcore.
# BUILD_DROIDDOC requires file names that are relative the *current* LOCAL_DIR so users must account
# for this.
libcore_to_document := $(_libcore_files) $(_icu_files)

libcore_docs_include_once := 1
endif # libcore_docs_include_once
