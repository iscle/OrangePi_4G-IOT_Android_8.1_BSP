#INTEL CONFIDENTIAL
#Copyright 2009 Intel Corporation All Rights Reserved. 
#The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

#No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
#

lib_LTLIBRARIES = libmixaudio.la 
#noinst_LTLIBRARIES = libmixaudio_stub.la

##############################################################################
# sources used to compile
libmixaudio_la_SOURCES = mixaudio.c \
	sst_proxy.c \
	mixaip.c \
	mixacp.c \
	mixacpmp3.c \
	mixacpwma.c \
	mixacpaac.c 

# flags used to compile this plugin
# add other _CFLAGS and _LIBS as needed
libmixaudio_la_CFLAGS = $(DBUS_GLIB_CFLAGS) $(GLIB_CFLAGS) $(MIX_CFLAGS) $(GOBJECT_CFLAGS) $(GTHREAD_CFLAGS) -DMIXAUDIO_CURRENT=@MIXAUDIO_CURRENT@ -DMIXAUDIO_AGE=@MIXAUDIO_AGE@ -DMIXAUDIO_REVISION=@MIXAUDIO_REVISION@ $(MIXCOMMON_CFLAGS) -DMIX_LOG_ENABLE
libmixaudio_la_LIBADD = $(DBUS_GLIB_LIBS) $(GLIB_LIBS) $(GOBJECT_LIBS) $(GTHREAD_LIBS) $(MIXCOMMON_LIBS)
libmixaudio_la_LDFLAGS = $(DBUS_GLIB_LIBS)$(GLIB_LIBS) $(GOBJECT_LIBS) $(GTHREAD_LIBS) -version-info @MIXAUDIO_CURRENT@:@MIXAUDIO_REVISION@:@MIXAUDIO_AGE@ $(MIXCOMMON_LIBS)

libmixaudio_la_LIBTOOLFLAGS = --tag=disable-static 

# additional flags to enable backdoor or workaround
if LPESTUB
libmixaudio_la_CFLAGS += -DLPESTUB
endif

if WORKAROUND
libmixaudio_la_CFLAGS += -DDROP_WORKAROUND
endif

#libmixaudio_stub_la_SOURCES = $(libmixaudio_la_SOURCES)
#libmixaudio_stub_la_CFLAGS = $(libmixaudio_la_CFLAGS) -DLPESTUB
#libmixaudio_stub_la_LIBADD = $(libmixaudio_la_LIBADD)
#libmixaudio_stub_la_LDFLAGS = $(libmixaudio_la_LDFLAGS)
#libmixaudio_stub_la_LIBTOOLFLAGS = $(libmixaudio_la_LIBTOOLFLAGS)

# headers we need but don't want installed
noinst_HEADERS = intel_sst_ioctl.h sst_proxy.h pvt.h amhelper.h

# TODO: decide whehter a /usr/include/mix is needed for mix headers
include_HEADERS = mixaudio.h \
	mixaudiotypes.h \
	mixaip.h \
	mixacp.h \
	mixacpmp3.h \
	mixacpwma.h \
	mixacpaac.h 

if AUDIO_MANAGER
libmixaudio_la_CFLAGS += -DAUDIO_MANAGER
libmixaudio_la_SOURCES += amhelper.c 
#include_HEADERS += amhelper.h
endif

