#INTEL CONFIDENTIAL
#Copyright 2009 Intel Corporation All Rights Reserved. 
#The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

#No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
#

noinst_PROGRAMS = test_framemanager

##############################################################################
# sources used to compile
test_framemanager_SOURCES = test_framemanager.c

test_framemanager_CFLAGS = $(GLIB_CFLAGS) $(GOBJECT_CFLAGS) $(MIXVIDEO_CFLAGS)
test_framemanager_LDADD = $(GLIB_LIBS) $(GOBJECT_LIBS) $(MIXVIDEO_LIBS)
test_framemanager_LIBTOOLFLAGS = --tag=disable-static

# headers we need but don't want installed
noinst_HEADERS =



