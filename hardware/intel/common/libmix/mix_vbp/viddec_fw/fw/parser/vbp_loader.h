/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef VBP_LOADER_H
#define VBP_LOADER_H

#include <va/va.h>

#ifndef TRUE
#define TRUE 1
#endif

#ifndef FALSE
#define FALSE 0
#endif


#ifndef uint8
typedef unsigned char uint8;
#endif
#ifndef uint16
typedef unsigned short uint16;
#endif
#ifndef uint32
typedef unsigned int uint32;
#endif
#ifndef bool
typedef int bool;
#endif

typedef void *Handle;

/*
 * MPEG-4 Part 2 data structure
 */

typedef struct _vbp_codec_data_mp42 
{
    uint8  profile_and_level_indication;
} vbp_codec_data_mp42;

typedef struct _vbp_slice_data_mp42 
{
	uint8* buffer_addr;
	uint32 slice_offset;
	uint32 slice_size;
	VASliceParameterBufferMPEG4 slice_param;
} vbp_slice_data_mp42;

typedef struct _vbp_picture_data_mp42 
{
	uint8 vop_coded;
	VAPictureParameterBufferMPEG4 picture_param;
	VAIQMatrixBufferMPEG4 iq_matrix_buffer;

	uint32 number_slices;
	vbp_slice_data_mp42 *slice_data;

} vbp_picture_data_mp42;

typedef struct _vbp_data_mp42 
{
	vbp_codec_data_mp42 codec_data;

	uint32 number_pictures;
	vbp_picture_data_mp42 *picture_data;

} vbp_data_mp42;

/*
 * H.264 data structure
 */

typedef struct _vbp_codec_data_h264
{
	uint8		pic_parameter_set_id;
	uint8	 	seq_parameter_set_id;
	
	uint8 		profile_idc;
	uint8 		level_idc;
	uint8		constraint_set1_flag;

	uint8	 	num_ref_frames; 
	uint8	 	gaps_in_frame_num_value_allowed_flag;

	uint8	 	frame_mbs_only_flag;                                                            
	uint8	 	mb_adaptive_frame_field_flag;
		   
	int			frame_width;
	int			frame_height;
		                   
	uint8	 	frame_cropping_flag;                     
	int 		frame_crop_rect_left_offset;
	int			frame_crop_rect_right_offset;                 
	int 		frame_crop_rect_top_offset;                
	int 		frame_crop_rect_bottom_offset; 

	uint8	 	vui_parameters_present_flag;
	/* aspect ratio */
	uint8  		aspect_ratio_info_present_flag;
	uint8  		aspect_ratio_idc;   
	uint16		sar_width;                                    
	uint16		sar_height;
	
	/* video fromat */
	uint8   	video_signal_type_present_flag; 	
	uint8  		video_format;  		
		
} vbp_codec_data_h264;

typedef struct _vbp_slice_data_h264
{
     uint8* buffer_addr;

     uint32 slice_offset; /* slice data offset */

     uint32 slice_size; /* slice data size */

     VASliceParameterBufferH264 slc_parms;

} vbp_slice_data_h264;
 
 
 typedef struct _vbp_picture_data_h264
 {
     VAPictureParameterBufferH264* pic_parms;

     uint32 num_slices;           

     vbp_slice_data_h264* slc_data; 	
               
 } vbp_picture_data_h264;
 

typedef struct _vbp_data_h264
{
     /* rolling counter of buffers sent by vbp_parse */
     uint32 buf_number;

	 uint32 num_pictures;
	 
	 vbp_picture_data_h264* pic_data;
	      
     /** 
	 * do we need to send matrix to VA for each picture? If not, we need
     * a flag indicating whether it is updated.
	 */
     VAIQMatrixBufferH264* IQ_matrix_buf;

     vbp_codec_data_h264* codec_data;

} vbp_data_h264; 

/*
 * vc1 data structure
 */
typedef struct _vbp_codec_data_vc1 
{
	/* Sequence layer. */
	uint8  PROFILE;
	uint8  LEVEL;
	uint8  POSTPROCFLAG;
	uint8  PULLDOWN;
	uint8  INTERLACE;
	uint8  TFCNTRFLAG;
	uint8  FINTERPFLAG;
	uint8  PSF;

	/* Entry point layer. */
	uint8  BROKEN_LINK;
	uint8  CLOSED_ENTRY;
	uint8  PANSCAN_FLAG;
	uint8  REFDIST_FLAG;
	uint8  LOOPFILTER;
	uint8  FASTUVMC;
	uint8  EXTENDED_MV;
	uint8  DQUANT;
	uint8  VSTRANSFORM;
	uint8  OVERLAP;
	uint8  QUANTIZER;
	uint16 CODED_WIDTH;
	uint16 CODED_HEIGHT;
	uint8  EXTENDED_DMV;
	uint8  RANGE_MAPY_FLAG;
	uint8  RANGE_MAPY;
	uint8  RANGE_MAPUV_FLAG;
	uint8  RANGE_MAPUV;

	/* Others. */
	uint8  RANGERED;
	uint8  MAXBFRAMES;
	uint8  MULTIRES;
	uint8  SYNCMARKER;
	uint8  RNDCTRL;
	uint8  REFDIST;
	uint16 widthMB;
	uint16 heightMB;

	uint8  INTCOMPFIELD;
	uint8  LUMSCALE2;
	uint8  LUMSHIFT2;
} vbp_codec_data_vc1;

typedef struct _vbp_slice_data_vc1 
{
	uint8 *buffer_addr;
	uint32 slice_offset;
	uint32 slice_size;
	VASliceParameterBufferVC1 slc_parms;     /* pointer to slice parms */
} vbp_slice_data_vc1;


typedef struct _vbp_picture_data_vc1
{
	uint32 picture_is_skipped;                /* VC1_PTYPE_SKIPPED is PTYPE is skipped. */
	VAPictureParameterBufferVC1 *pic_parms;   /* current parsed picture header */
	uint32 size_bitplanes;                    /* based on number of MBs */
	uint8 *packed_bitplanes;                  /* contains up to three bitplanes packed for libVA */
	uint32 num_slices;                        /* number of slices.  always at least one */
	vbp_slice_data_vc1 *slc_data;             /* pointer to array of slice data */
} vbp_picture_data_vc1;
	
typedef struct _vbp_data_vc1 
{
	uint32 buf_number;                        /* rolling counter of buffers sent by vbp_parse */
	vbp_codec_data_vc1 *se_data;              /* parsed SH/EPs */
	
	uint32 num_pictures;
	 
	vbp_picture_data_vc1* pic_data;	      	
} vbp_data_vc1;

enum _picture_type
{
	VC1_PTYPE_I,
	VC1_PTYPE_P,
	VC1_PTYPE_B,
	VC1_PTYPE_BI,
	VC1_PTYPE_SKIPPED
};

enum _vbp_parser_error
{
	VBP_OK,
	VBP_TYPE,
	VBP_LOAD,
	VBP_UNLOAD,
	VBP_INIT,
	VBP_DATA,
	VBP_DONE,
	VBP_GLIB,
	VBP_MEM,
	VBP_PARM,
	VBP_CXT,
	VBP_IMPL
};

enum _vbp_parser_type
{
	VBP_VC1,
	VBP_MPEG2,
	VBP_MPEG4,
	VBP_H264
};

/*
 * open video bitstream parser to parse a specific media type.
 * @param  parser_type: one of the types defined in #vbp_parser_type
 * @param  hcontext: pointer to hold returned VBP context handle.
 * @return VBP_OK on success, anything else on failure.
 * 
 */
uint32 vbp_open(uint32 parser_type, Handle *hcontext);

/*
 * close video bitstream parser.
 * @param hcontext: VBP context handle.
 * @returns VBP_OK on success, anything else on failure.
 * 
 */
uint32 vbp_close(Handle hcontext);

/*
 * parse bitstream.
 * @param hcontext: handle to VBP context.
 * @param data: pointer to bitstream buffer.
 * @param size: size of bitstream buffer.
 * @param init_flag: 1 if buffer contains bitstream configuration data, 0 otherwise.
 * @return VBP_OK on success, anything else on failure.
 * 
 */
uint32 vbp_parse(Handle hcontext, uint8 *data, uint32 size, uint8 init_data_flag);

/*
 * query parsing result.
 * @param hcontext: handle to VBP context.
 * @param data: pointer to hold a data blob that contains parsing result. 
 * 				Structure of data blob is determined by the media type.
 * @return VBP_OK on success, anything else on failure.
 * 
 */
uint32 vbp_query(Handle hcontext, void **data);


/*
 * flush any un-parsed bitstream.
 * @param hcontext: handle to VBP context.
 * @returns VBP_OK on success, anything else on failure.
 * 
 */
uint32 vbp_flush(Handle hcontent);

#endif /* VBP_LOADER_H */
