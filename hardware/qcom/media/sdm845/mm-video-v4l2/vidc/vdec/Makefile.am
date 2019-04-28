AM_CFLAGS = -Wall
AM_CFLAGS += -Wundef
AM_CFLAGS += -Wstrict-prototypes
AM_CFLAGS += -Wno-trigraphs
AM_CFLAGS += -g -O3

AM_CPPFLAGS := -D__alignx\(x\)=__attribute__\(\(__aligned__\(x\)\)\)
AM_CPPFLAGS += -D__align=__alignx
AM_CPPFLAGS += -Dinline=__inline
AM_CPPFLAGS += -g -O3
AM_CPPFLAGS += -DIMAGE_APPS_PROC
AM_CPPFLAGS += -D_ANDROID_
AM_CPPFLAGS += -DCDECL
AM_CPPFLAGS += -DT_ARM
AM_CPPFLAGS += -DNO_ARM_CLZ
AM_CPPFLAGS += -UENABLE_DEBUG_LOW
AM_CPPFLAGS += -UENABLE_DEBUG_HIGH
AM_CPPFLAGS += -DENABLE_DEBUG_ERROR
AM_CPPFLAGS += -UINPUT_BUFFER_LOG
AM_CPPFLAGS += -UOUTPUT_BUFFER_LOG
AM_CPPFLAGS += -Wno-parentheses
AM_CPPFLAGS += -D_ANDROID_ICS_
AM_CPPFLAGS += -DPROCESS_EXTRADATA_IN_OUTPUT_PORT
#AM_CPPFLAGS += "-include stdint.h"
AM_CPPFLAGS += "-Dstrlcpy=g_strlcpy"
AM_CPPFLAGS += "-Dstrlcat=g_strlcat"
AM_CPPFLAGS += "-std=c++11"
AM_CPPFLAGS += "-DHAVE_ANDROID_OS"
AM_CPPFLAGS += -DSYS_IOCTL

if USE_GLIB
AM_CPPFLAGS += -D_USE_GLIB_
endif

if TARGET_MSM8610
AM_CPPFLAGS += -DVENUS_HEVC
endif

if TARGETS_THAT_SUPPORT_UBWC
AM_CPPFLAGS += -D_UBWC_
endif

if TARGET_USES_ION
AM_CPPFLAGS += -DUSE_ION
endif

if MASTER_SIDE_CP_TARGET_LIST
AM_CPPFLAGS += -DMASTER_SIDE_CP
endif

AM_CPPFLAGS += -I$(top_srcdir)/mm-video-v4l2/vidc/common/inc/
AM_CPPFLAGS += -I$(top_srcdir)/mm-video-v4l2/vidc/vdec/inc/
AM_CPPFLAGS += -I$(top_srcdir)/libc2dcolorconvert/
AM_CPPFLAGS += -I$(top_srcdir)/mm-core/inc/
AM_CPPFLAGS += -I$(top_srcdir)/mm-core/src/common/

c_sources         := src/frameparser.cpp
c_sources         += src/h264_utils.cpp
c_sources         += src/ts_parser.cpp
c_sources         += src/mp4_utils.cpp
c_sources         += src/hevc_utils.cpp
c_sources         += src/omx_vdec_v4l2.cpp

lib_LTLIBRARIES = libOmxVdec.la
libOmxVdec_la_SOURCES = $(c_sources)
libOmxVdec_la_CFLAGS = $(AM_CFLAGS) $(AM_CPPFLAGS) -fPIC
libOmxVdec_la_CFLAGS += ../libc2d2colorconvert/libc2dcolorconvert.la
libOmxVdec_la_LDFLAGS = -lglib-2.0 -shared -version-info 0
