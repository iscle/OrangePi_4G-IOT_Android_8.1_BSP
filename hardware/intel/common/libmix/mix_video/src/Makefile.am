#INTEL CONFIDENTIAL
#Copyright 2009 Intel Corporation All Rights Reserved. 
#The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

#No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
#

lib_LTLIBRARIES = libmixvideo.la

##############################################################################
# sources used to compile
libmixvideo_la_SOURCES = mixdisplay.c \
			mixdrmparams.c \
			mixvideo.c \
			mixvideoconfigparams.c \
			mixvideoconfigparamsdec.c \
			mixvideoconfigparamsdec_vc1.c \
			mixvideoconfigparamsdec_h264.c \
			mixvideoconfigparamsdec_mp42.c \
			mixvideoframe.c \
			mixvideorenderparams.c \
			mixdisplayx11.c \
			mixvideocaps.c \
			mixvideodecodeparams.c \
			mixvideoinitparams.c \
			mixvideoformat.c \
			mixvideoformat_h264.c \
			mixvideoformat_vc1.c \
			mixvideoformat_mp42.c \
			mixsurfacepool.c \
			mixframemanager.c \
			mixbufferpool.c \
			mixbuffer.c \
			mixvideoformatenc.c \
			mixvideoformatenc_h264.c \
			mixvideoformatenc_mpeg4.c \
			mixvideoformatenc_preview.c \
			mixvideoconfigparamsenc.c \
			mixvideoconfigparamsenc_h264.c \
			mixvideoconfigparamsenc_mpeg4.c \
			mixvideoconfigparamsenc_preview.c \
			mixvideoencodeparams.c

if MIXLOG_ENABLED
MIXLOG_CFLAGS = -DMIX_LOG_ENABLE
else
MIXLOG_CFLAGS =
endif


# flags used to compile this plugin
# add other _CFLAGS and _LIBS as needed
libmixvideo_la_CFLAGS = $(GLIB_CFLAGS) \
			$(MIX_CFLAGS) \
			$(MIXLOG_CFLAGS) \
			$(GOBJECT_CFLAGS) \
			$(GTHREAD_CFLAGS) \
			$(LIBVA_CFLAGS) \
			$(LIBVA_X11_CFLAGS) \
			$(MIXCOMMON_CFLAGS) \
			$(MIXVBP_CFLAGS) \
			-DMIXVIDEO_CURRENT=@MIXVIDEO_CURRENT@ \
			-DMIXVIDEO_AGE=@MIXVIDEO_AGE@ \
			-DMIXVIDEO_REVISION=@MIXVIDEO_REVISION@

libmixvideo_la_LIBADD = $(GLIB_LIBS) \
			$(GOBJECT_LIBS) \
			$(GTHREAD_LIBS) \
			$(LIBVA_LIBS) \
			$(LIBVA_X11_LIBS) \
			$(MIXCOMMON_LIBS) \
			$(MIXVBP_LIBS)

libmixvideo_la_LDFLAGS = $(GLIB_LIBS) \
			$(GOBJECT_LIBS) \
			$(GTHREAD_LIBS) \
			$(LIBVA_LIBS) \
			$(LIBVA_X11_LIBS) \
			$(MIXCOMMON_LIBS) \
			$(MIXVBP_LIBS) \
			-version-info @MIXVIDEO_CURRENT@:@MIXVIDEO_REVISION@:@MIXVIDEO_AGE@
	
libmixvideo_la_LIBTOOLFLAGS = --tag=disable-static

# headers we need but don't want installed
noinst_HEADERS = mixvideoformat.h \
		mixvideoformat_h264.h \
		mixvideoformat_vc1.h \
		mixvideoformat_mp42.h \
		mixsurfacepool.h \
		mixvideoframe_private.h \
		mixbuffer_private.h \
		mixframemanager.h \
		mixbufferpool.h \
		mixvideoformatqueue.h \
		mixvideo_private.h \
		mixvideorenderparams_internal.h \
		mixvideoformatenc_h264.h \
		mixvideoformatenc_mpeg4.h \
		mixvideoformatenc_preview.h \
		mixvideoformatenc.h \
		mixvideolog.h

# TODO: decide whehter a /usr/include/mix is needed for mix headers
mixincludedir=$(includedir)/mix
mixinclude_HEADERS = mixvideodef.h \
			mixdisplayx11.h \
			mixvideoconfigparams.h \
			mixvideoconfigparamsdec.h \
			mixvideoconfigparamsdec_vc1.h \
			mixvideoconfigparamsdec_h264.h \
			mixvideoconfigparamsdec_mp42.h \
			mixvideoframe.h \
			mixvideoinitparams.h \
			mixdisplay.h \
			mixdrmparams.h \
			mixvideocaps.h \
			mixvideodecodeparams.h \
			mixvideoencodeparams.h \
			mixvideo.h \
			mixvideorenderparams.h \
			mixbuffer.h \
			mixvideoconfigparamsenc_h264.h \
			mixvideoconfigparamsenc_mpeg4.h \
			mixvideoconfigparamsenc_preview.h \
			mixvideoconfigparamsenc.h


mixintincludedir=$(includedir)/mixvideoint
mixintinclude_HEADERS = mixvideoformat.h \
                        mixframemanager.h \
                        mixsurfacepool.h \
                        mixbufferpool.h \
                        mixvideoformatqueue.h \
                        mixvideoframe_private.h \
			mixvideoformat_vc1.h
