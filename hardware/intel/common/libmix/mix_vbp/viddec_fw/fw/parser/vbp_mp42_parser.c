/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */


#include <glib.h>
#include <dlfcn.h>

#include <string.h>
#include "vbp_loader.h"
#include "vbp_utils.h"
#include "vbp_mp42_parser.h"
#include "../codecs/mp4/parser/viddec_mp4_parse.h"

#define MIX_VBP_COMP 		"mixvbp"

/*
 * Some divX avi files contains 2 frames in one gstbuffer.
 */
#define MAX_NUM_PICTURES_MP42 8

uint32 vbp_get_sc_pos_mp42(uint8 *buf, uint32 length,
		uint32* sc_phase, uint32 *sc_end_pos, uint8 *is_normal_sc);

void vbp_on_vop_mp42(vbp_context *pcontext, int list_index);
void vbp_on_vop_svh_mp42(vbp_context *pcontext, int list_index);
void vbp_dump_query_data(vbp_context *pcontext, int list_index);

uint32 vbp_process_slices_mp42(vbp_context *pcontext, int list_index);
uint32 vbp_process_slices_svh_mp42(vbp_context *pcontext, int list_index);

/* This is coppied from DHG mp42 parser */
static inline mp4_Status_t
vbp_sprite_trajectory_mp42(void *parent, mp4_VideoObjectLayer_t *vidObjLay,
		mp4_VideoObjectPlane_t *vidObjPlane);

/* This is coppied from DHG mp42 parser */
static inline int32_t vbp_sprite_dmv_length_mp42(void * parent,
		int32_t *dmv_length);

/**
 *
 */
uint32 vbp_init_parser_entries_mp42( vbp_context *pcontext)
{
	if (NULL == pcontext->parser_ops)
	{
		/* absolutely impossible, just sanity check */
		return VBP_PARM;
	}
	pcontext->parser_ops->init = dlsym(pcontext->fd_parser, "viddec_mp4_init");
	if (pcontext->parser_ops->init == NULL)
	{
		ETRACE ("Failed to set entry point." );
		return VBP_LOAD;
	}

	pcontext->parser_ops->parse_sc = dlsym(pcontext->fd_parser, "viddec_parse_sc_mp4");
	if (pcontext->parser_ops->parse_sc == NULL)
	{
		ETRACE ("Failed to set entry point." );
		return VBP_LOAD;
	}

	pcontext->parser_ops->parse_syntax = dlsym(pcontext->fd_parser, "viddec_mp4_parse");
	if (pcontext->parser_ops->parse_syntax == NULL)
	{
		ETRACE ("Failed to set entry point." );
		return VBP_LOAD;
	}

	pcontext->parser_ops->get_cxt_size = dlsym(pcontext->fd_parser, "viddec_mp4_get_context_size");
	if (pcontext->parser_ops->get_cxt_size == NULL)
	{
		ETRACE ("Failed to set entry point." );
		return VBP_LOAD;
	}

	pcontext->parser_ops->is_wkld_done = dlsym(pcontext->fd_parser, "viddec_mp4_wkld_done");
	if (pcontext->parser_ops->is_wkld_done == NULL)
	{
		ETRACE ("Failed to set entry point." );
		return VBP_LOAD;
	}

	return VBP_OK;
}


/*
 * For the codec_data passed by gstreamer
 */
uint32 vbp_parse_init_data_mp42(vbp_context *pcontext)
{
	VTRACE ("begin\n");
	vbp_parse_start_code_mp42(pcontext);
	VTRACE ("end\n");

	return VBP_OK;
}

uint32 vbp_process_parsing_result_mp42(vbp_context *pcontext, int list_index) 
{
	vbp_data_mp42 *query_data = (vbp_data_mp42 *) pcontext->query_data;
	viddec_mp4_parser_t *parser =
			(viddec_mp4_parser_t *) &(pcontext->parser_cxt->codec_data[0]);

	uint8 is_svh = 0;
	uint32 current_sc = parser->current_sc;
	is_svh = parser->cur_sc_prefix ? false : true;

	VTRACE ("begin\n");

	VTRACE ("current_sc = 0x%x  profile_and_level_indication = 0x%x\n",
			parser->current_sc, parser->info.profile_and_level_indication);

	if (!is_svh) 
	{
		/* remove prefix from current_sc */
		current_sc &= 0x0FF;
		switch (current_sc) 
		{
		case MP4_SC_VISUAL_OBJECT_SEQUENCE:
			VTRACE ("MP4_SC_VISUAL_OBJECT_SEQUENCE\n");

			query_data->codec_data.profile_and_level_indication
					= parser->info.profile_and_level_indication;

			break;
		case MP4_SC_VIDEO_OBJECT_PLANE:
			VTRACE ("MP4_SC_VIDEO_OBJECT_PLANE\n");
			vbp_on_vop_mp42(pcontext, list_index);
			break;
		default: {
			if ((current_sc >= MP4_SC_VIDEO_OBJECT_LAYER_MIN) && (current_sc
					<= MP4_SC_VIDEO_OBJECT_LAYER_MAX)) {
				query_data->codec_data.profile_and_level_indication
						= parser->info.profile_and_level_indication;
			} else if (current_sc <= MP4_SC_VIDEO_OBJECT_MAX) {
				if (parser->sc_seen == MP4_SC_SEEN_SVH) {
					VTRACE ("parser->sc_seen == MP4_SC_SEEN_SVH\n");
					vbp_on_vop_svh_mp42(pcontext, list_index);
				}
			}
		}
			break;
		}

	} else {
		if (parser->sc_seen == MP4_SC_SEEN_SVH) {
			VTRACE ("parser->sc_seen == MP4_SC_SEEN_SVH\n");
			vbp_on_vop_svh_mp42(pcontext, list_index);
		}
	}

	VTRACE ("End\n");

	return VBP_OK;
}

/*
 * This function fills viddec_pm_cxt_t by start codes
 * I may change the codes to make it more efficient later
 */

uint32 vbp_parse_start_code_mp42(vbp_context *pcontext)
{
	viddec_pm_cxt_t *cxt = pcontext->parser_cxt;
	/*viddec_parser_ops_t *func = pcontext->parser_ops; */
	uint8 *buf = NULL;
	uint32 size = 0;
	uint32 sc_phase = 0;
	uint32 sc_end_pos = -1;

	uint32 bytes_parsed = 0;

	viddec_mp4_parser_t *pinfo = NULL;

	vbp_data_mp42 *query_data = (vbp_data_mp42 *) pcontext->query_data;
	/* reset query data for the new sample buffer */
	query_data->number_pictures = 0;
	
	/* emulation prevention byte is always present */
	cxt->getbits.is_emul_reqd = 1;

	cxt->list.num_items = 0;
	cxt->list.data[0].stpos = 0;
	cxt->list.data[0].edpos = cxt->parse_cubby.size;

	buf = cxt->parse_cubby.buf;
	size = cxt->parse_cubby.size;

	pinfo = (viddec_mp4_parser_t *) &(cxt->codec_data[0]);

	uint8 is_normal_sc = 0;

	uint32 found_sc = 0;

	VTRACE ("begin cxt->parse_cubby.size= %d\n", size);

	while (1) {

		sc_phase = 0;

		found_sc = vbp_get_sc_pos_mp42(buf + bytes_parsed, size
				- bytes_parsed, &sc_phase, &sc_end_pos, &is_normal_sc);

		if (found_sc) {

			VTRACE ("sc_end_pos = %d\n", sc_end_pos);

			cxt->list.data[cxt->list.num_items].stpos = bytes_parsed
					+ sc_end_pos - 3;
			if (cxt->list.num_items != 0) {
				cxt->list.data[cxt->list.num_items - 1].edpos = bytes_parsed
						+ sc_end_pos - 3;
			}
			bytes_parsed += sc_end_pos;

			cxt->list.num_items++;
			pinfo->cur_sc_prefix = is_normal_sc;

		} else {

			if (cxt->list.num_items != 0) {
				cxt->list.data[cxt->list.num_items - 1].edpos
						= cxt->parse_cubby.size;
				break;
			} else {

				VTRACE ("I didn't find any sc in cubby buffer! The size of cubby is %d\n",
						size);

				cxt->list.num_items = 1;
				cxt->list.data[0].stpos = 0;
				cxt->list.data[0].edpos = cxt->parse_cubby.size;
				break;
			}
		}
	}

	return VBP_OK;
}

uint32 vbp_populate_query_data_mp42(vbp_context *pcontext) 
{
#if 0
	vbp_dump_query_data(pcontext);
#endif
	return VBP_OK;
}

void vbp_fill_codec_data(vbp_context *pcontext, int list_index) 
{

	/* fill vbp_codec_data_mp42 data */
	viddec_mp4_parser_t *parser =
			(viddec_mp4_parser_t *) &(pcontext->parser_cxt->codec_data[0]);
	vbp_data_mp42 *query_data = (vbp_data_mp42 *) pcontext->query_data;
	query_data->codec_data.profile_and_level_indication
			= parser->info.profile_and_level_indication;
}

void vbp_fill_slice_data(vbp_context *pcontext, int list_index) 
{

	viddec_mp4_parser_t *parser =
			(viddec_mp4_parser_t *) &(pcontext->parser_cxt->codec_data[0]);

	if (!parser->info.VisualObject.VideoObject.short_video_header) {
		vbp_process_slices_mp42(pcontext, list_index);
	} else {
		vbp_process_slices_svh_mp42(pcontext, list_index);
	}
}

void vbp_fill_picture_param(vbp_context *pcontext, int list_index) 
{

	viddec_mp4_parser_t *parser =
			(viddec_mp4_parser_t *) &(pcontext->parser_cxt->codec_data[0]);
	vbp_data_mp42 *query_data = (vbp_data_mp42 *) pcontext->query_data;

	vbp_picture_data_mp42 *picture_data = NULL;
	VAPictureParameterBufferMPEG4 *picture_param = NULL;

	picture_data = &(query_data->picture_data[query_data->number_pictures]);

	picture_param = &(picture_data->picture_param);

	uint8 idx = 0;

	picture_data->vop_coded
			= parser->info.VisualObject.VideoObject.VideoObjectPlane.vop_coded;
	VTRACE ("vop_coded = %d\n", picture_data->vop_coded);

	/*
	 * fill picture_param
	 */

	/* NOTE: for short video header, the parser saves vop_width and vop_height
	 * to VOL->video_object_layer_width and VOL->video_object_layer_height
	 */
	picture_param->vop_width
			= parser->info.VisualObject.VideoObject.video_object_layer_width;
	picture_param->vop_height
			= parser->info.VisualObject.VideoObject.video_object_layer_height;

	picture_param->forward_reference_picture = VA_INVALID_SURFACE;
	picture_param->backward_reference_picture = VA_INVALID_SURFACE;

	/*
	 * VAPictureParameterBufferMPEG4::vol_fields
	 */
	picture_param->vol_fields.bits.short_video_header
			= parser->info.VisualObject.VideoObject.short_video_header;
	picture_param->vol_fields.bits.chroma_format
			= parser->info.VisualObject.VideoObject.VOLControlParameters.chroma_format;

	/* TODO: find out why testsuite always set this value to be 0 */
	//	picture_param->vol_fields.bits.chroma_format = 0;

	picture_param->vol_fields.bits.interlaced
			= parser->info.VisualObject.VideoObject.interlaced;
	picture_param->vol_fields.bits.obmc_disable
			= parser->info.VisualObject.VideoObject.obmc_disable;
	picture_param->vol_fields.bits.sprite_enable
			= parser->info.VisualObject.VideoObject.sprite_enable;
	picture_param->vol_fields.bits.sprite_warping_accuracy
			= parser->info.VisualObject.VideoObject.sprite_info.sprite_warping_accuracy;
	picture_param->vol_fields.bits.quant_type
			= parser->info.VisualObject.VideoObject.quant_type;
	picture_param->vol_fields.bits.quarter_sample
			= parser->info.VisualObject.VideoObject.quarter_sample;
	picture_param->vol_fields.bits.data_partitioned
			= parser->info.VisualObject.VideoObject.data_partitioned;
	picture_param->vol_fields.bits.reversible_vlc
			= parser->info.VisualObject.VideoObject.reversible_vlc;
	picture_param->vol_fields.bits.resync_marker_disable
			= parser->info.VisualObject.VideoObject.resync_marker_disable;

	picture_param->no_of_sprite_warping_points
			= parser->info.VisualObject.VideoObject.sprite_info.no_of_sprite_warping_points;

	for (idx = 0; idx < 3; idx++) {
		picture_param->sprite_trajectory_du[idx]
				= parser->info.VisualObject.VideoObject.VideoObjectPlane.warping_mv_code_du[idx];
		picture_param->sprite_trajectory_dv[idx]
				= parser->info.VisualObject.VideoObject.VideoObjectPlane.warping_mv_code_dv[idx];
	}

	picture_param->quant_precision
			= parser->info.VisualObject.VideoObject.quant_precision;

	/*
	 *  VAPictureParameterBufferMPEG4::vop_fields
	 */

	if (!parser->info.VisualObject.VideoObject.short_video_header) {
		picture_param->vop_fields.bits.vop_coding_type
				= parser->info.VisualObject.VideoObject.VideoObjectPlane.vop_coding_type;
	} else {
		picture_param->vop_fields.bits.vop_coding_type
				= parser->info.VisualObject.VideoObject.VideoObjectPlaneH263.picture_coding_type;
	}

	/* TODO:
	 * fill picture_param->vop_fields.bits.backward_reference_vop_coding_type
	 * This shall be done in mixvideoformat_mp42. See M42 spec 7.6.7
	 */

	if (picture_param->vop_fields.bits.vop_coding_type != MP4_VOP_TYPE_B) {
		picture_param->vop_fields.bits.backward_reference_vop_coding_type
				= picture_param->vop_fields.bits.vop_coding_type;
	}

	picture_param->vop_fields.bits.vop_rounding_type
			= parser->info.VisualObject.VideoObject.VideoObjectPlane.vop_rounding_type;
	picture_param->vop_fields.bits.intra_dc_vlc_thr
			= parser->info.VisualObject.VideoObject.VideoObjectPlane.intra_dc_vlc_thr;
	picture_param->vop_fields.bits.top_field_first
			= parser->info.VisualObject.VideoObject.VideoObjectPlane.top_field_first;
	picture_param->vop_fields.bits.alternate_vertical_scan_flag
			= parser->info.VisualObject.VideoObject.VideoObjectPlane.alternate_vertical_scan_flag;

	picture_param->vop_fcode_forward
			= parser->info.VisualObject.VideoObject.VideoObjectPlane.vop_fcode_forward;
	picture_param->vop_fcode_backward
			= parser->info.VisualObject.VideoObject.VideoObjectPlane.vop_fcode_backward;
	picture_param->vop_time_increment_resolution
			= parser->info.VisualObject.VideoObject.vop_time_increment_resolution;

	/* short header related */
	picture_param->num_gobs_in_vop
			= parser->info.VisualObject.VideoObject.VideoObjectPlaneH263.num_gobs_in_vop;
	picture_param->num_macroblocks_in_gob
			= parser->info.VisualObject.VideoObject.VideoObjectPlaneH263.num_macroblocks_in_gob;

	/* for direct mode prediction */
	picture_param->TRB = parser->info.VisualObject.VideoObject.TRB;
	picture_param->TRD = parser->info.VisualObject.VideoObject.TRD;

#if 0
	printf(
			"parser->info.VisualObject.VideoObject.reduced_resolution_vop_enable = %d\n",
			parser->info.VisualObject.VideoObject.reduced_resolution_vop_enable);

	printf("parser->info.VisualObject.VideoObject.data_partitioned = %d\n",
			parser->info.VisualObject.VideoObject.data_partitioned);

	printf(
			"####parser->info.VisualObject.VideoObject.resync_marker_disable = %d####\n",
			parser->info.VisualObject.VideoObject.resync_marker_disable);
#endif
}

void vbp_fill_iq_matrix_buffer(vbp_context *pcontext, int list_index) 
{

	viddec_mp4_parser_t *parser =
			(viddec_mp4_parser_t *) &(pcontext->parser_cxt->codec_data[0]);
	vbp_data_mp42 *query_data = (vbp_data_mp42 *) pcontext->query_data;

	mp4_VOLQuant_mat_t *quant_mat_info =
			&(parser->info.VisualObject.VideoObject.quant_mat_info);

	vbp_picture_data_mp42 *picture_data = NULL;
	VAIQMatrixBufferMPEG4 *iq_matrix = NULL;

	picture_data = &(query_data->picture_data[query_data->number_pictures]);
	iq_matrix = &(picture_data->iq_matrix_buffer);

	iq_matrix->load_intra_quant_mat = quant_mat_info->load_intra_quant_mat;
	iq_matrix->load_non_intra_quant_mat
			= quant_mat_info->load_nonintra_quant_mat;
	memcpy(iq_matrix->intra_quant_mat, quant_mat_info->intra_quant_mat, 64);
	memcpy(iq_matrix->non_intra_quant_mat, quant_mat_info->nonintra_quant_mat,
			64);
}

void vbp_on_vop_mp42(vbp_context *pcontext, int list_index) 
{
	vbp_data_mp42 *query_data = (vbp_data_mp42 *) pcontext->query_data;

	vbp_fill_codec_data(pcontext, list_index);

	vbp_fill_picture_param(pcontext, list_index);
	vbp_fill_iq_matrix_buffer(pcontext, list_index);
	vbp_fill_slice_data(pcontext, list_index);

	query_data->number_pictures++;
}

void vbp_on_vop_svh_mp42(vbp_context *pcontext, int list_index) 
{
	vbp_data_mp42 *query_data = (vbp_data_mp42 *) pcontext->query_data;

	vbp_fill_codec_data(pcontext, list_index);

	vbp_fill_picture_param(pcontext, list_index);
	vbp_fill_iq_matrix_buffer(pcontext, list_index);
	vbp_fill_slice_data(pcontext, list_index);

	query_data->number_pictures++;
}

uint32 vbp_get_sc_pos_mp42(
	uint8 *buf, 
	uint32 length,
	uint32* sc_phase,
	uint32 *sc_end_pos,
	uint8 *is_normal_sc) 
{
	uint8 *ptr = buf;
	uint32 size;
	uint32 data_left = 0, phase = 0, ret = 0;
	size = 0;

	data_left = length;
	phase = *sc_phase;
	*sc_end_pos = -1;

	/* parse until there is more data and start code not found */
	while ((data_left > 0) && (phase < 3)) {
		/* Check if we are byte aligned & phase=0, if thats the case we can check
		 work at a time instead of byte*/
		if (((((uint32) ptr) & 0x3) == 0) && (phase == 0)) {
			while (data_left > 3) {
				uint32 data;
				char mask1 = 0, mask2 = 0;

				data = *((uint32 *) ptr);
#ifndef MFDBIGENDIAN
				data = SWAP_WORD(data);
#endif
				mask1 = (FIRST_STARTCODE_BYTE != (data & SC_BYTE_MASK0));
				mask2 = (FIRST_STARTCODE_BYTE != (data & SC_BYTE_MASK1));
				/* If second byte and fourth byte are not zero's then we cannot have a start code here as we need
				 two consecutive zero bytes for a start code pattern */
				if (mask1 && mask2) {/* Success so skip 4 bytes and start over */
					ptr += 4;
					size += 4;
					data_left -= 4;
					continue;
				} else {
					break;
				}
			}
		}

		/* At this point either data is not on a word boundary or phase > 0 or On a word boundary but we detected
		 two zero bytes in the word so we look one byte at a time*/
		if (data_left > 0) {
			if (*ptr == FIRST_STARTCODE_BYTE) {/* Phase can be 3 only if third start code byte is found */
				phase++;
				ptr++;
				size++;
				data_left--;
				if (phase > 2) {
					phase = 2;

					if ((((uint32) ptr) & 0x3) == 0) {
						while (data_left > 3) {
							if (*((uint32 *) ptr) != 0) {
								break;
							}
							ptr += 4;
							size += 4;
							data_left -= 4;
						}
					}
				}
			} else {
				uint8 normal_sc = 0, short_sc = 0;
				if (phase == 2) {
					normal_sc = (*ptr == THIRD_STARTCODE_BYTE);
					short_sc = (SHORT_THIRD_STARTCODE_BYTE == (*ptr & 0xFC));

					VTRACE ("short_sc = %d\n", short_sc);

					*is_normal_sc = normal_sc;
				}

				if (!(normal_sc | short_sc)) {
					phase = 0;
				} else {/* Match for start code so update context with byte position */
					*sc_end_pos = size;
					phase = 3;

					if (normal_sc) {
					} else {
						/* For short start code since start code is in one nibble just return at this point */
						phase += 1;
						ret = 1;
						break;
					}
				}
				ptr++;
				size++;
				data_left--;
			}
		}
	}
	if ((data_left > 0) && (phase == 3)) {
		(*sc_end_pos)++;
		phase++;
		ret = 1;
	}
	*sc_phase = phase;
	/* Return SC found only if phase is 4, else always success */
	return ret;
}

uint32 vbp_macroblock_number_length_mp42(uint32 numOfMbs)
{
	uint32 length = 0;
	numOfMbs--;
	do {
		numOfMbs >>= 1;
		length++;
	} while (numOfMbs);
	return length;
}

mp4_Status_t vbp_video_packet_header_mp42(	
	void *parent,
	viddec_mp4_parser_t *parser_cxt,
	uint16_t *quant_scale,
	uint32 *macroblock_number)
{

	mp4_Status_t ret = MP4_STATUS_OK;
	mp4_Info_t *pInfo = &(parser_cxt->info);
	mp4_VideoObjectLayer_t *vidObjLay = &(pInfo->VisualObject.VideoObject);
	mp4_VideoObjectPlane_t *vidObjPlane =
			&(pInfo->VisualObject.VideoObject.VideoObjectPlane);

	uint32 code = 0;
	int32_t getbits = 0;

	uint16_t _quant_scale = 0;
	uint32 _macroblock_number = 0;
	uint32 header_extension_codes = 0;
	uint8 vop_coding_type = vidObjPlane->vop_coding_type;

	do {
		if (vidObjLay->video_object_layer_shape != MP4_SHAPE_TYPE_RECTANGULAR) {
			ret = MP4_STATUS_NOTSUPPORT;
			break;
		}

		/* get macroblock_number */
		{
			uint16_t mbs_x = (vidObjLay->video_object_layer_width + 15) >> 4;
			uint16_t mbs_y = (vidObjLay->video_object_layer_height + 15) >> 4;
			uint32 length = vbp_macroblock_number_length_mp42(mbs_x
					* mbs_y);

			getbits = viddec_pm_get_bits(parent, &code, length);
			BREAK_GETBITS_FAIL(getbits, ret);

			length = code;
		}

		/* quant_scale */
		if (vidObjLay->video_object_layer_shape != MP4_SHAPE_TYPE_BINARYONLY) {
			getbits = viddec_pm_get_bits(parent, &code,
					vidObjLay->quant_precision);
			BREAK_GETBITS_FAIL(getbits, ret);
			_quant_scale = code;
		}

		/* header_extension_codes */
		if (vidObjLay->video_object_layer_shape == MP4_SHAPE_TYPE_RECTANGULAR) {
			getbits = viddec_pm_get_bits(parent, &code, 1);
			BREAK_GETBITS_FAIL(getbits, ret);
			header_extension_codes = code;
		}

		if (header_extension_codes) {
			do {
				getbits = viddec_pm_get_bits(parent, &code, 1);
				BREAK_GETBITS_FAIL(getbits, ret);
			} while (code);

			/* marker_bit */
			getbits = viddec_pm_get_bits(parent, &code, 1);
			BREAK_GETBITS_FAIL(getbits, ret);

			/* vop_time_increment */
			{
				uint32 numbits = 0;
				numbits = vidObjLay->vop_time_increment_resolution_bits;
				if (numbits == 0) {
					numbits = 1;
				}
				getbits = viddec_pm_get_bits(parent, &code, numbits);
				BREAK_GETBITS_FAIL(getbits, ret);
			}
			/* marker_bit */
			getbits = viddec_pm_get_bits(parent, &code, 1);
			BREAK_GETBITS_FAIL(getbits, ret);

			/* vop_coding_type */
			getbits = viddec_pm_get_bits(parent, &code, 2);
			BREAK_GETBITS_FAIL(getbits, ret);

			vop_coding_type = code & 0x3;

		/* Fixed Klocwork issue: Code is unreachable.
		 * Comment the following codes because we have
		 * already checked video_object_layer_shape
		 */
		 /* if (vidObjLay->video_object_layer_shape
					!= MP4_SHAPE_TYPE_RECTANGULAR) {
				ret = MP4_STATUS_NOTSUPPORT;
				break;
			}
		 */
			if (vidObjLay->video_object_layer_shape
					!= MP4_SHAPE_TYPE_BINARYONLY) {
				/* intra_dc_vlc_thr */
				getbits = viddec_pm_get_bits(parent, &code, 3);
				BREAK_GETBITS_FAIL(getbits, ret);
				if ((vidObjLay->sprite_enable == MP4_SPRITE_GMC)
						&& (vop_coding_type == MP4_VOP_TYPE_S)
						&& (vidObjLay->sprite_info.no_of_sprite_warping_points
								> 0)) {
					if (vbp_sprite_trajectory_mp42(parent, vidObjLay,
							vidObjPlane) != MP4_STATUS_OK) {
						break;
					}
				}

				if (vidObjLay->reduced_resolution_vop_enable
						&& (vidObjLay->video_object_layer_shape
								== MP4_SHAPE_TYPE_RECTANGULAR)
						&& ((vop_coding_type == MP4_VOP_TYPE_I)
								|| (vop_coding_type == MP4_VOP_TYPE_P))) {
					/* vop_reduced_resolution */
					getbits = viddec_pm_get_bits(parent, &code, 1);
					BREAK_GETBITS_FAIL(getbits, ret);
				}

				if (vop_coding_type == MP4_VOP_TYPE_I) {
					/* vop_fcode_forward */
					getbits = viddec_pm_get_bits(parent, &code, 3);
					BREAK_GETBITS_FAIL(getbits, ret);
				}

				if (vop_coding_type == MP4_VOP_TYPE_B) {
					/* vop_fcode_backward */
					getbits = viddec_pm_get_bits(parent, &code, 3);
					BREAK_GETBITS_FAIL(getbits, ret);
				}
			}
		}

		if (vidObjLay->newpred_enable) {
			/* New pred mode not supported in HW, but, does libva support this? */
			ret = MP4_STATUS_NOTSUPPORT;
			break;
		}

		*quant_scale = _quant_scale;
		*macroblock_number = _macroblock_number;
	} while (0);
	return ret;
}

uint32 vbp_resync_marker_Length_mp42(viddec_mp4_parser_t *parser_cxt)
{

	mp4_Info_t *pInfo = &(parser_cxt->info);
	mp4_VideoObjectPlane_t *vidObjPlane =
			&(pInfo->VisualObject.VideoObject.VideoObjectPlane);

	uint32 resync_marker_length = 0;
	if (vidObjPlane->vop_coding_type == MP4_VOP_TYPE_I) {
		resync_marker_length = 17;
	} else if (vidObjPlane->vop_coding_type == MP4_VOP_TYPE_B) {
		uint8 fcode_max = vidObjPlane->vop_fcode_forward;
		if (fcode_max < vidObjPlane->vop_fcode_backward) {
			fcode_max = vidObjPlane->vop_fcode_backward;
		}
		resync_marker_length = 16 + fcode_max;
	} else {
		resync_marker_length = 16 + vidObjPlane->vop_fcode_forward;
	}
	return resync_marker_length;
}

uint32 vbp_process_slices_svh_mp42(vbp_context *pcontext, int list_index)
{
	uint32 ret = MP4_STATUS_OK;

	vbp_data_mp42 *query_data = (vbp_data_mp42 *) pcontext->query_data;
	viddec_pm_cxt_t *parent = pcontext->parser_cxt;
	viddec_mp4_parser_t *parser_cxt =
			(viddec_mp4_parser_t *) &(parent->codec_data[0]);

	VTRACE ("begin\n");

	vbp_picture_data_mp42 *picture_data =
			&(query_data->picture_data[query_data->number_pictures]);
	vbp_slice_data_mp42 *slice_data = &(picture_data->slice_data[0]);
	VASliceParameterBufferMPEG4* slice_param = &(slice_data->slice_param);

	picture_data->number_slices = 1;

	uint8 is_emul = 0;
	uint32 bit_offset = 0;
	uint32 byte_offset = 0;

	/* The offsets are relative to parent->parse_cubby.buf */
	viddec_pm_get_au_pos(parent, &bit_offset, &byte_offset, &is_emul);

	slice_data->buffer_addr = parent->parse_cubby.buf;

	slice_data->slice_offset = byte_offset
			+ parent->list.data[list_index].stpos;
	slice_data->slice_size = parent->list.data[list_index].edpos
			- parent->list.data[list_index].stpos - byte_offset;

	slice_param->slice_data_size = slice_data->slice_size;
	slice_param->slice_data_flag = VA_SLICE_DATA_FLAG_ALL;
	slice_param->slice_data_offset = 0;
	slice_param->macroblock_offset = bit_offset;
	slice_param->macroblock_number = 0;
	slice_param->quant_scale
			= parser_cxt->info.VisualObject.VideoObject.VideoObjectPlaneH263.vop_quant;

	VTRACE ("end\n");

	return ret;
}

mp4_Status_t vbp_process_slices_mp42(vbp_context *pcontext, int list_index) 
{

	vbp_data_mp42 *query_data = (vbp_data_mp42 *) pcontext->query_data;
	viddec_pm_cxt_t *parent = pcontext->parser_cxt;
	viddec_mp4_parser_t *parser_cxt =
			(viddec_mp4_parser_t *) &(parent->codec_data[0]);

	vbp_picture_data_mp42 *picture_data = NULL;
	vbp_slice_data_mp42 *slice_data = NULL;
	VASliceParameterBufferMPEG4* slice_param = NULL;

	uint32 ret = MP4_STATUS_OK;

	uint8 is_emul = 0;
	uint32 bit_offset = 0;
	uint32 byte_offset = 0;

	uint32 code = 0;
	int32_t getbits = 0;
	uint32 resync_marker_length = 0;

	uint32 slice_index = 0;

#ifdef VBP_TRACE
	uint32 list_size_at_index = parent->list.data[list_index].edpos
	- parent->list.data[list_index].stpos;
#endif

	VTRACE ("list_index = %d list_size_at_index = %d\n", list_index,
			list_size_at_index);

	VTRACE ("list_index = %d edpos = %d stpos = %d\n", list_index,
			parent->list.data[list_index].edpos,
			parent->list.data[list_index].stpos);

	/* The offsets are relative to parent->parse_cubby.buf */
	viddec_pm_get_au_pos(parent, &bit_offset, &byte_offset, &is_emul);

#if 0
	if (is_emul) {
		g_print("*** emul != 0\n");
		/*byte_offset += 1;*/
	}
#endif

	picture_data = &(query_data->picture_data[query_data->number_pictures]);
	slice_data = &(picture_data->slice_data[slice_index]);
	slice_param = &(slice_data->slice_param);

	slice_data->buffer_addr = parent->parse_cubby.buf;

	slice_data->slice_offset = byte_offset
			+ parent->list.data[list_index].stpos;
	slice_data->slice_size = parent->list.data[list_index].edpos
			- parent->list.data[list_index].stpos - byte_offset;

	slice_param->slice_data_size = slice_data->slice_size;
	slice_param->slice_data_flag = VA_SLICE_DATA_FLAG_ALL;
	slice_param->slice_data_offset = 0;
	slice_param->macroblock_offset = bit_offset;
	slice_param->macroblock_number = 0;
	slice_param->quant_scale
			= parser_cxt->info.VisualObject.VideoObject.VideoObjectPlane.vop_quant;

	slice_index++;
	picture_data->number_slices = slice_index;

	/*
	 * scan for resync_marker
	 */

	if (!parser_cxt->info.VisualObject.VideoObject.resync_marker_disable) {

		viddec_pm_get_au_pos(parent, &bit_offset, &byte_offset, &is_emul);
		if (bit_offset) {
			getbits = viddec_pm_get_bits(parent, &code, 8 - bit_offset);
			if (getbits == -1) {
				ret = MP4_STATUS_PARSE_ERROR;
				return ret;
			}
		}

		/*
		 * get resync_marker_length
		 */
		resync_marker_length = vbp_resync_marker_Length_mp42(parser_cxt);

		while (1) {

			uint16_t quant_scale = 0;
			uint32 macroblock_number = 0;

			getbits = viddec_pm_peek_bits(parent, &code, resync_marker_length);
			BREAK_GETBITS_FAIL(getbits, ret);

			if (code != 1) {
				getbits = viddec_pm_get_bits(parent, &code, 8);
				BREAK_GETBITS_FAIL(getbits, ret);
				continue;
			}

			/*
			 * We found resync_marker
			 */

			viddec_pm_get_au_pos(parent, &bit_offset, &byte_offset, &is_emul);

			slice_data->slice_size -= (parent->list.data[list_index].edpos
					- parent->list.data[list_index].stpos - byte_offset);
			slice_param->slice_data_size = slice_data->slice_size;

			slice_data = &(picture_data->slice_data[slice_index]);
			slice_param = &(slice_data->slice_param);

			/*
			 * parse video_packet_header
			 */
			getbits = viddec_pm_get_bits(parent, &code, resync_marker_length);
			BREAK_GETBITS_FAIL(getbits, ret);

			vbp_video_packet_header_mp42(parent, parser_cxt,
					&quant_scale, &macroblock_number);

			viddec_pm_get_au_pos(parent, &bit_offset, &byte_offset, &is_emul);

			slice_data->buffer_addr = parent->parse_cubby.buf;

			slice_data->slice_offset = byte_offset
					+ parent->list.data[list_index].stpos;
			slice_data->slice_size = parent->list.data[list_index].edpos
					- parent->list.data[list_index].stpos - byte_offset;

			slice_param->slice_data_size = slice_data->slice_size;
			slice_param->slice_data_flag = VA_SLICE_DATA_FLAG_ALL;
			slice_param->slice_data_offset = 0;
			slice_param->macroblock_offset = bit_offset;
			slice_param->macroblock_number = macroblock_number;
			slice_param->quant_scale = quant_scale;

			slice_index++;

			if (slice_index >= MAX_NUM_SLICES) {
				ret = MP4_STATUS_PARSE_ERROR;
				break;
			}

			picture_data->number_slices = slice_index;
		}
	}
	return ret;
}

/* This is coppied from DHG MP42 parser */
static inline int32_t vbp_sprite_dmv_length_mp42(
	void * parent,
	int32_t *dmv_length) 
{
	uint32 code, skip;
	int32_t getbits = 0;
	mp4_Status_t ret = MP4_STATUS_PARSE_ERROR;
	*dmv_length = 0;
	skip = 3;
	do {
		getbits = viddec_pm_peek_bits(parent, &code, skip);
		BREAK_GETBITS_FAIL(getbits, ret);

		if (code == 7) {
			viddec_pm_skip_bits(parent, skip);
			getbits = viddec_pm_peek_bits(parent, &code, 9);
			BREAK_GETBITS_FAIL(getbits, ret);

			skip = 1;
			while ((code & 256) != 0) {/* count number of 1 bits */
				code <<= 1;
				skip++;
			}
			*dmv_length = 5 + skip;
		} else {
			skip = (code <= 1) ? 2 : 3;
			*dmv_length = code - 1;
		}
		viddec_pm_skip_bits(parent, skip);
		ret = MP4_STATUS_OK;

	} while (0);
	return ret;
}

/* This is coppied from DHG MP42 parser */
static inline mp4_Status_t vbp_sprite_trajectory_mp42(
	void *parent,
	mp4_VideoObjectLayer_t *vidObjLay, 
	mp4_VideoObjectPlane_t *vidObjPlane) 
{
	uint32 code, i;
	int32_t dmv_length = 0, dmv_code = 0, getbits = 0;
	mp4_Status_t ret = MP4_STATUS_OK;
	for (i = 0; i
			< (uint32) vidObjLay->sprite_info.no_of_sprite_warping_points; i++) {
		ret = vbp_sprite_dmv_length_mp42(parent, &dmv_length);
		if (ret != MP4_STATUS_OK) {
			break;
		}
		if (dmv_length <= 0) {
			dmv_code = 0;
		} else {
			getbits = viddec_pm_get_bits(parent, &code, (uint32) dmv_length);
			BREAK_GETBITS_FAIL(getbits, ret);
			dmv_code = (int32_t) code;
			if ((dmv_code & (1 << (dmv_length - 1))) == 0) {
				dmv_code -= (1 << dmv_length) - 1;
			}
		}
		getbits = viddec_pm_get_bits(parent, &code, 1);
		BREAK_GETBITS_FAIL(getbits, ret);
		if (code != 1) {
			ret = MP4_STATUS_PARSE_ERROR;
			break;
		}
		vidObjPlane->warping_mv_code_du[i] = dmv_code;
		/* TODO: create another inline function to avoid code duplication */
		ret = vbp_sprite_dmv_length_mp42(parent, &dmv_length);
		if (ret != MP4_STATUS_OK) {
			break;
		}
		if (dmv_length <= 0) {
			dmv_code = 0;
		} else {
			getbits = viddec_pm_get_bits(parent, &code, (uint32) dmv_length);
			BREAK_GETBITS_FAIL(getbits, ret);
			dmv_code = (int32_t) code;
			if ((dmv_code & (1 << (dmv_length - 1))) == 0) {
				dmv_code -= (1 << dmv_length) - 1;
			}
		}
		getbits = viddec_pm_get_bits(parent, &code, 1);
		BREAK_GETBITS_FAIL(getbits, ret);
		if (code != 1) {
			ret = MP4_STATUS_PARSE_ERROR;
			break;
		}
		vidObjPlane->warping_mv_code_dv[i] = dmv_code;

	}
	return ret;
}

/*
 * free memory of vbp_data_mp42 structure and its members
 */
uint32 vbp_free_query_data_mp42(vbp_context *pcontext) 
{

	vbp_data_mp42 *query_data = (vbp_data_mp42 *) pcontext->query_data;
	gint idx = 0;

	if (query_data) {
		if (query_data->picture_data) {
			for (idx = 0; idx < MAX_NUM_PICTURES_MP42; idx++) {
				g_free(query_data->picture_data[idx].slice_data);
			}
			g_free(query_data->picture_data);
		}

		g_free(query_data);
	}

	pcontext->query_data = NULL;
	return VBP_OK;
}

/*
 * Allocate memory for vbp_data_mp42 structure and all its members.
 */
uint32 vbp_allocate_query_data_mp42(vbp_context *pcontext) 
{

	gint idx = 0;
	vbp_data_mp42 *query_data;
	pcontext->query_data = NULL;

	query_data = g_try_new0(vbp_data_mp42, 1);
	if (query_data == NULL) {
		goto cleanup;
	}

	query_data->picture_data = g_try_new0(vbp_picture_data_mp42,
			MAX_NUM_PICTURES_MP42);
	if (NULL == query_data->picture_data) {
		goto cleanup;
	}

	for (idx = 0; idx < MAX_NUM_PICTURES_MP42; idx++) {
		query_data->picture_data[idx].number_slices = 0;
		query_data->picture_data[idx].slice_data = g_try_new0(
				vbp_slice_data_mp42, MAX_NUM_SLICES);

		if (query_data->picture_data[idx].slice_data == NULL) {
			goto cleanup;
		}
	}

	pcontext->query_data = (void *) query_data;
	return VBP_OK;

	cleanup:

	if (query_data) {
		if (query_data->picture_data) {
			for (idx = 0; idx < MAX_NUM_PICTURES_MP42; idx++) {
				g_free(query_data->picture_data[idx].slice_data);
			}
			g_free(query_data->picture_data);
		}

		g_free(query_data);
	}

	return VBP_MEM;
}

void vbp_dump_query_data(vbp_context *pcontext, int list_index) 
{
	vbp_data_mp42 *query_data = (vbp_data_mp42 *) pcontext->query_data;

	vbp_picture_data_mp42 *picture_data = NULL;
	VAPictureParameterBufferMPEG4 *picture_param = NULL;
	vbp_slice_data_mp42 *slice_data = NULL;

	uint32 idx = 0, jdx = 0;

	for (idx = 0; idx < query_data->number_pictures; idx++) {

		picture_data = &(query_data->picture_data[idx]);
		picture_param = &(picture_data->picture_param);
		slice_data = &(picture_data->slice_data[0]);

		g_print("======================= dump_begin ======================\n\n");
		g_print("======================= codec_data ======================\n");

		/* codec_data */
		g_print("codec_data.profile_and_level_indication = 0x%x\n",
				query_data->codec_data.profile_and_level_indication);

		g_print("==================== picture_param =======================\n");

		/* picture_param */
		g_print("picture_param->vop_width = %d\n", picture_param->vop_width);
		g_print("picture_param->vop_height = %d\n", picture_param->vop_height);

		g_print("picture_param->vol_fields.bits.short_video_header = %d\n",
				picture_param->vol_fields.bits.short_video_header);
		g_print("picture_param->vol_fields.bits.chroma_format = %d\n",
				picture_param->vol_fields.bits.chroma_format);
		g_print("picture_param->vol_fields.bits.interlaced = %d\n",
				picture_param->vol_fields.bits.interlaced);
		g_print("picture_param->vol_fields.bits.obmc_disable = %d\n",
				picture_param->vol_fields.bits.obmc_disable);
		g_print("picture_param->vol_fields.bits.sprite_enable = %d\n",
				picture_param->vol_fields.bits.sprite_enable);
		g_print(
				"picture_param->vol_fields.bits.sprite_warping_accuracy = %d\n",
				picture_param->vol_fields.bits.sprite_warping_accuracy);
		g_print("picture_param->vol_fields.bits.quant_type = %d\n",
				picture_param->vol_fields.bits.quant_type);
		g_print("picture_param->vol_fields.bits.quarter_sample = %d\n",
				picture_param->vol_fields.bits.quarter_sample);
		g_print("picture_param->vol_fields.bits.data_partitioned = %d\n",
				picture_param->vol_fields.bits.data_partitioned);
		g_print("picture_param->vol_fields.bits.reversible_vlc = %d\n",
				picture_param->vol_fields.bits.reversible_vlc);

		g_print("picture_param->no_of_sprite_warping_points = %d\n",
				picture_param->no_of_sprite_warping_points);
		g_print("picture_param->quant_precision = %d\n",
				picture_param->quant_precision);
		g_print("picture_param->sprite_trajectory_du = %d, %d, %d\n",
				picture_param->sprite_trajectory_du[0],
				picture_param->sprite_trajectory_du[1],
				picture_param->sprite_trajectory_du[2]);
		g_print("picture_param->sprite_trajectory_dv = %d, %d, %d\n",
				picture_param->sprite_trajectory_dv[0],
				picture_param->sprite_trajectory_dv[1],
				picture_param->sprite_trajectory_dv[2]);

		g_print("picture_param->vop_fields.bits.vop_coding_type = %d\n",
				picture_param->vop_fields.bits.vop_coding_type);
		g_print(
				"picture_param->vop_fields.bits.backward_reference_vop_coding_type = %d\n",
				picture_param->vop_fields.bits.backward_reference_vop_coding_type);
		g_print("picture_param->vop_fields.bits.vop_rounding_type = %d\n",
				picture_param->vop_fields.bits.vop_rounding_type);
		g_print("picture_param->vop_fields.bits.intra_dc_vlc_thr = %d\n",
				picture_param->vop_fields.bits.intra_dc_vlc_thr);
		g_print("picture_param->vop_fields.bits.top_field_first = %d\n",
				picture_param->vop_fields.bits.top_field_first);
		g_print(
				"picture_param->vop_fields.bits.alternate_vertical_scan_flag = %d\n",
				picture_param->vop_fields.bits.alternate_vertical_scan_flag);

		g_print("picture_param->vop_fcode_forward = %d\n",
				picture_param->vop_fcode_forward);
		g_print("picture_param->vop_fcode_backward = %d\n",
				picture_param->vop_fcode_backward);
		g_print("picture_param->num_gobs_in_vop = %d\n",
				picture_param->num_gobs_in_vop);
		g_print("picture_param->num_macroblocks_in_gob = %d\n",
				picture_param->num_macroblocks_in_gob);
		g_print("picture_param->TRB = %d\n", picture_param->TRB);
		g_print("picture_param->TRD = %d\n", picture_param->TRD);

		g_print("==================== slice_data ==========================\n");

		g_print("slice_data.buffer_addr = 0x%x\n",
				(unsigned int) slice_data->buffer_addr);
		g_print("slice_data.slice_offset = 0x%x\n", slice_data->slice_offset);
		g_print("slice_data.slice_size = 0x%x\n", slice_data->slice_size);

		g_print("slice_data.slice_param.macroblock_number = %d\n",
				slice_data->slice_param.macroblock_number);
		g_print("slice_data.slice_param.macroblock_offset = 0x%x\n",
				slice_data->slice_param.macroblock_offset);
		g_print("slice_data.slice_param.quant_scale = %d\n",
				slice_data->slice_param.quant_scale);
		g_print("slice_data.slice_param.slice_data_flag = %d\n",
				slice_data->slice_param.slice_data_flag);
		g_print("slice_data.slice_param.slice_data_offset = %d\n",
				slice_data->slice_param.slice_data_offset);
		g_print("slice_data.slice_param.slice_data_size = %d\n",
				slice_data->slice_param.slice_data_size);

		g_print("================= iq_matrix_buffer ======================\n");
		g_print("iq_matrix_buffer.load_intra_quant_mat = %d\n",
				picture_data->iq_matrix_buffer.load_intra_quant_mat);
		g_print("iq_matrix_buffer.load_non_intra_quant_mat = %d\n",
				picture_data->iq_matrix_buffer.load_non_intra_quant_mat);

		g_print("------- iq_matrix_buffer.intra_quant_mat ----------\n");
		for (jdx = 0; jdx < 64; jdx++) {

			g_print("%02x ",
					picture_data->iq_matrix_buffer.intra_quant_mat[jdx]);

			if ((jdx + 1) % 8 == 0) {
				g_print("\n");
			}
		}

		g_print("----- iq_matrix_buffer.non_intra_quant_mat --------\n");
		for (jdx = 0; jdx < 64; jdx++) {

			g_print("%02x ",
					picture_data->iq_matrix_buffer.non_intra_quant_mat[jdx]);

			if ((jdx + 1) % 8 == 0) {
				g_print("\n");
			}
		}

		g_print("-------- slice buffer begin ------------\n");

		for (jdx = 0; jdx < 64; jdx++) {
			g_print("%02x ", *(slice_data->buffer_addr
					+ slice_data->slice_offset + jdx));
			if ((jdx + 1) % 8 == 0) {
				g_print("\n");
			}
		}
		g_print("-------- slice buffer begin ------------\n");

		g_print("\n\n============== dump_end ==========================\n\n");

	}
}

