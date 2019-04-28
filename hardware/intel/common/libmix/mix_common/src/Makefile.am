#INTEL CONFIDENTIAL
#Copyright 2009 Intel Corporation All Rights Reserved. 
#The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

#No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
#

lib_LTLIBRARIES = libmixcommon.la

##############################################################################
# sources used to compile
libmixcommon_la_SOURCES = mixparams.c mixlog.c mixdrmparams.c

# flags used to compile this plugin
# add other _CFLAGS and _LIBS as needed
libmixcommon_la_CFLAGS = $(GLIB_CFLAGS) $(GOBJECT_CFLAGS) 
libmixcommon_la_LIBADD = $(GLIB_LIBS) $(GOBJECT_LIBS) 
libmixcommon_la_LDFLAGS = $(GLIB_LIBS) $(GOBJECT_LIBS) -version-info @MIXCOMMON_CURRENT@:@MIXCOMMON_REVISION@:@MIXCOMMON_AGE@ 
libmixcommon_la_LIBTOOLFLAGS = --tag=disable-static

include_HEADERS = mixparams.h mixresult.h mixlog.h mixdrmparams.h
#mixcommonincludedir = $(includedir)
#mixcommoninclude_HEADERS = mixparams.h mixresult.h
