#INTEL CONFIDENTIAL
#Copyright 2009 Intel Corporation All Rights Reserved. 
#The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

#No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
#
VC1PATH=./../codecs/vc1/parser
MP2PATH=./../codecs/mp2/parser
MP4PATH=./../codecs/mp4/parser
H264PATH=./../codecs/h264/parser

PARSER_INCLUDE_PATH=-I./include \ 
			 		-I../include \
			 		-I../../include \
			 		-I./vc1/include \
			 		-I../codecs/vc1/include \
			 		-I../codecs/mp2/include \
			 		-I../codecs/mp4/include \
			 		-I../codecs/h264/include \
			 		-I../codecs/vc1/parser 
			 		
			 		
PARSER_MACROS=		-DVBP \
			  		-DHOST_ONLY \
			  		-DG_LOG_DOMAIN=\"vbp\"
			  
					
la_CFLAGS = 	$(GLIB_CFLAGS) \
				$(MIX_CFLAGS) \
				$(GOBJECT_CFLAGS) \
				$(GTHREAD_CFLAGS) \
				$(PARSER_INCLUDE_PATH) \
				$(PARSER_MACROS) \
				-DMIXVBP_CURRENT=@MIXVBP_CURRENT@ \
				-DMIXVBP_AGE=@MIXVBP_AGE@ \
				-DMIXVBP_REVISION=@MIXVBP_REVISION@
					
la_LIBADD =		$(GLIB_LIBS) \
				$(GOBJECT_LIBS) \
				$(GTHREAD_LIBS)

la_LDFLAGS =	$(GLIB_LIBS) \
				$(GOBJECT_LIBS) \
				$(GTHREAD_LIBS) \
				-version-info @MIXVBP_CURRENT@:@MIXVBP_REVISION@:@MIXVBP_AGE@

lib_LTLIBRARIES = 	libmixvbp.la \
					libmixvbp_vc1.la \
					libmixvbp_mpeg2.la \
					libmixvbp_mpeg4.la \
					libmixvbp_h264.la
								  

######################################  vbp loader ########################################

# sources used to compile
libmixvbp_la_SOURCES =	vbp_loader.c \
					vbp_utils.c \
					vbp_trace.c \
					vbp_h264_parser.c \
					vbp_vc1_parser.c \
					vbp_mp42_parser.c \
					viddec_pm.c \
					viddec_pm_stubs.c \
					viddec_pm_parser_ops.c \
					viddec_pm_utils_bstream.c \
					viddec_pm_tags.c \
					viddec_emit.c \
					viddec_pm_utils_list.c \
					viddec_parse_sc.c \
					viddec_parse_sc_stub.c

libmixvbp_la_CFLAGS = 	$(la_CFLAGS)
libmixvbp_la_LIBADD =	$(la_LIBADD)
libmixvbp_la_LDFLAGS =	$(la_LDFLAGS)	
libmixvbp_la_LIBTOOLFLAGS = --tag=disable-static

######################################  VC-1 parser ########################################

libmixvbp_vc1_la_SOURCES =	$(VC1PATH)/vc1parse.c \
							$(VC1PATH)/vc1parse_bitplane.c \
							$(VC1PATH)/vc1parse_bpic.c \
							$(VC1PATH)/vc1parse_bpic_adv.c \
							$(VC1PATH)/vc1parse_common_tables.c \
							$(VC1PATH)/vc1parse_huffman.c \
							$(VC1PATH)/vc1parse_ipic.c \
							$(VC1PATH)/vc1parse_ipic_adv.c \
							$(VC1PATH)/vc1parse_mv_com.c \
							$(VC1PATH)/vc1parse_pic_com.c \
							$(VC1PATH)/vc1parse_pic_com_adv.c \
							$(VC1PATH)/vc1parse_ppic.c \
							$(VC1PATH)/vc1parse_ppic_adv.c \
							$(VC1PATH)/vc1parse_vopdq.c \
							$(VC1PATH)/viddec_vc1_parse.c \
							$(VC1PATH)/mix_vbp_vc1_stubs.c

libmixvbp_vc1_la_CFLAGS =	$(la_CFLAGS)		
libmixvbp_vc1_la_LIBADD =	$(la_LIBADD) libmixvbp.la
libmixvbp_vc1_la_LDFLAGS = 	$(la_LDFLAGS) 
libmixvbp_vc1_la_LIBTOOLFLAGS = --tag=disable-static

######################################  MPEG-2 parser ########################################

libmixvbp_mpeg2_la_SOURCES =	$(MP2PATH)/viddec_mpeg2_metadata.c \
	   							$(MP2PATH)/viddec_mpeg2_parse.c \
	   							$(MP2PATH)/mix_vbp_mpeg2_stubs.c

libmixvbp_mpeg2_la_CFLAGS = 	$(la_CFLAGS)			
libmixvbp_mpeg2_la_LIBADD = 	$(la_LIBADD) libmixvbp.la
libmixvbp_mpeg2_la_LDFLAGS =	$(la_LDFLAGS)
libmixvbp_mpeg2_la_LIBTOOLFLAGS = --tag=disable-static

######################################  MPEG-4 parser ########################################

libmixvbp_mpeg4_la_SOURCES = 	$(MP4PATH)/viddec_mp4_parse.c \
	   							$(MP4PATH)/viddec_parse_sc_mp4.c \
	   							$(MP4PATH)/viddec_mp4_visualobject.c \
	   							$(MP4PATH)/viddec_mp4_videoobjectplane.c \
	   							$(MP4PATH)/viddec_mp4_shortheader.c \
	   							$(MP4PATH)/viddec_mp4_videoobjectlayer.c \
	   							$(MP4PATH)/viddec_mp4_decodevideoobjectplane.c

libmixvbp_mpeg4_la_CFLAGS =		$(la_CFLAGS)			
libmixvbp_mpeg4_la_LIBADD =		$(la_LIBADD) libmixvbp.la
libmixvbp_mpeg4_la_LDFLAGS =	$(la_LDFLAGS)
libmixvbp_mpeg4_la_LIBTOOLFLAGS = --tag=disable-static

######################################  H.264 parser ########################################

libmixvbp_h264_la_SOURCES =	$(H264PATH)/h264parse.c \
							$(H264PATH)/h264parse_bsd.c \
							$(H264PATH)/h264parse_math.c \
							$(H264PATH)/h264parse_mem.c \
							$(H264PATH)/h264parse_sei.c \
							$(H264PATH)/h264parse_sh.c \
							$(H264PATH)/h264parse_pps.c \
							$(H264PATH)/h264parse_sps.c \
							$(H264PATH)/h264parse_dpb.c \
							$(H264PATH)/viddec_h264_parse.c \
							$(H264PATH)/mix_vbp_h264_stubs.c

libmixvbp_h264_la_CFLAGS =		$(la_CFLAGS)			
libmixvbp_h264_la_LIBADD =		$(la_LIBADD) libmixvbp.la
libmixvbp_h264_la_LDFLAGS =		$(la_LDFLAGS)
libmixvbp_h264_la_LIBTOOLFLAGS = --tag=disable-static

##############################################################################################

# headers we need but don't want installed
noinst_HEADERS = ./vbp_h264_parser.h \
		 ./vbp_mp42_parser.h \
	./vbp_vc1_parser.h \
	./vbp_trace.h \
	./vbp_loader.h \
	./vbp_utils.h \
	./include/fw_pvt.h \
	./include/ipc_fw_custom.h \
	./include/viddec_emitter.h \
	./include/viddec_fw_debug.h \
	./include/viddec_fw_parser_fw_ipc.h \
	./include/viddec_h264_parse.h \
	./include/viddec_mp4_parse.h \
	./include/viddec_mpeg2_parse.h \
	./include/viddec_parser_ops.h \
	./include/viddec_pm.h \
	./include/viddec_pm_parse.h \
	./include/viddec_pm_tags.h \
	./include/viddec_pm_utils_bstream.h \
	./include/viddec_pm_utils_list.h \
	./include/viddec_vc1_parse.h \
	../include/stdint.h \
	../include/viddec_debug.h \
	../include/viddec_fw_version.h \
	../../include/viddec_fw_common_defs.h \
	../../include/viddec_fw_decoder_host.h \
	../../include/viddec_fw_frame_attr.h \
	../../include/viddec_fw_item_types.h \
	../../include/viddec_fw_parser_host.h \
	../../include/viddec_fw_workload.h \
	../../fw/include/stdint.h \
	../../fw/include/viddec_debug.h \
	../../fw/include/viddec_fw_version.h \
	../../fw/codecs/h264/include/h264.h \
	../../fw/codecs/h264/include/h264parse.h \
	../../fw/codecs/h264/include/h264parse_dpb.h \
	../../fw/codecs/h264/include/h264parse_sei.h \
	../../fw/codecs/mp2/include/mpeg2.h \
	../../fw/codecs/mp2/include/viddec_mpeg2.h \
	../../fw/codecs/mp4/include/viddec_fw_mp4.h \
	../../fw/codecs/mp4/parser/viddec_mp4_decodevideoobjectplane.h \
	../../fw/codecs/mp4/parser/viddec_mp4_parse.h \
	../../fw/codecs/mp4/parser/viddec_mp4_shortheader.h \
	../../fw/codecs/mp4/parser/viddec_mp4_videoobjectlayer.h \
	../../fw/codecs/mp4/parser/viddec_mp4_videoobjectplane.h \
	../../fw/codecs/mp4/parser/viddec_mp4_visualobject.h \
	../../fw/codecs/vc1/include/vc1common.h \
	../../fw/codecs/vc1/parser/vc1.h \
	../../fw/codecs/vc1/parser/vc1parse.h \
	../../fw/codecs/vc1/parser/vc1parse_common_defs.h


mixincludedir=$(includedir)/mixvbp
mixinclude_HEADERS = vbp_loader.h

##############################################################################################
