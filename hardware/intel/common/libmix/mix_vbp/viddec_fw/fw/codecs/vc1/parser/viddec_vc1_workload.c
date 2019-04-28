/* Any workload management goes in this file */

#include "viddec_fw_debug.h"
#include "vc1.h"
#include "vc1parse.h"
#include "viddec_fw_workload.h"
#include <auto_eas/gen4_mfd.h>
#include "viddec_pm_utils_bstream.h"

/* this function returns workload frame types corresponding to VC1 PTYPES (frame types)
 * VC1 frame types: can be found in vc1parse_common_defs.h
 * workload frame types are in viddec_workload.h
*/
static inline uint32_t vc1_populate_frame_type(uint32_t vc1_frame_type)
{
    uint32_t viddec_frame_type;

      switch(vc1_frame_type)
      {
        case VC1_I_FRAME: 
            viddec_frame_type = VIDDEC_FRAME_TYPE_I; 
            break;
        case VC1_P_FRAME: 
            viddec_frame_type = VIDDEC_FRAME_TYPE_P; 
            break;
        case VC1_B_FRAME: 
            viddec_frame_type = VIDDEC_FRAME_TYPE_B; 
            break;
        case VC1_BI_FRAME:  
            viddec_frame_type = VIDDEC_FRAME_TYPE_BI; 
            break;
        case VC1_SKIPPED_FRAME :  
            viddec_frame_type =  VIDDEC_FRAME_TYPE_SKIP; 
            break;
        default: 
            viddec_frame_type = VIDDEC_FRAME_TYPE_INVALID; 
            break;
      } // switch on vc1 frame type

   return(viddec_frame_type);
} // vc1_populate_frame_type

static void translate_parser_info_to_frame_attributes(void *parent, vc1_viddec_parser_t *parser)
{
    viddec_workload_t        *wl = viddec_pm_get_header( parent );
    viddec_frame_attributes_t *attrs = &wl->attrs;
    vc1_Info        *info = &parser->info;
    unsigned i;

    /* typical sequence layer and entry_point data */
    attrs->cont_size.height       = info->metadata.height * 2 + 2;
    attrs->cont_size.width        = info->metadata.width  * 2 + 2;
    
    /* frame type */
    /* we can have two fileds with different types for field interlace coding mode */
    if (info->picLayerHeader.FCM == VC1_FCM_FIELD_INTERLACE) {
      attrs->frame_type = vc1_populate_frame_type(info->picLayerHeader.PTypeField1);
      attrs->bottom_field_type = vc1_populate_frame_type(info->picLayerHeader.PTypeField2);
    } else {
      attrs->frame_type = vc1_populate_frame_type(info->picLayerHeader.PTYPE);
      attrs->bottom_field_type = VIDDEC_FRAME_TYPE_INVALID; //unknown
    }

    /* frame counter */
    attrs->vc1.tfcntr = info->picLayerHeader.TFCNTR;

    /* TFF, repeat frame, field */
    attrs->vc1.tff = info->picLayerHeader.TFF;
    attrs->vc1.rptfrm = info->picLayerHeader.RPTFRM;
    attrs->vc1.rff = info->picLayerHeader.RFF;

    /* PAN Scan */
    attrs->vc1.ps_present = info->picLayerHeader.PS_PRESENT;
    attrs->vc1.num_of_pan_scan_windows = info->picLayerHeader.number_of_pan_scan_window;
    for (i=0;i<attrs->vc1.num_of_pan_scan_windows;i++) {
      attrs->vc1.pan_scan_window[i].hoffset =  info->picLayerHeader.PAN_SCAN_WINDOW[i].hoffset;
      attrs->vc1.pan_scan_window[i].voffset =  info->picLayerHeader.PAN_SCAN_WINDOW[i].voffset;
      attrs->vc1.pan_scan_window[i].width =  info->picLayerHeader.PAN_SCAN_WINDOW[i].width;
      attrs->vc1.pan_scan_window[i].height =  info->picLayerHeader.PAN_SCAN_WINDOW[i].height;
    } //end for i

    return;
} // translate_parser_info_to_frame_attributes

void vc1_intcomp(vc1_viddec_parser_t *parser, vc1_Info *pInfo, VC1D_SPR_REGS *spr) 
{
    vc1_metadata_t *md = &pInfo->metadata;
    vc1_PictureLayerHeader *pic = &pInfo->picLayerHeader;
    uint32_t intcomp1 = 1;
    uint32_t intcomp2 = 0;

    // Get the intensity compensation from the bitstream
    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, LUMA_SCALE_1, intcomp1, pic->LUMSCALE);
    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, LUMA_SHIFT_1, intcomp1, pic->LUMSHIFT);

    if(md->INTCOMPFIELD == VC1_INTCOMP_BOTH_FIELD) 
    {
        intcomp2 = 1;
        BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, LUMA_SCALE_1, intcomp2, md->LUMSCALE2);
        BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, LUMA_SHIFT_1, intcomp2, md->LUMSHIFT2);
    }

    switch(md->INTCOMPFIELD)
    {
        case VC1_INTCOMP_TOP_FIELD:
            if(pic->CurrField == 0) // First field decoded
            {
                if(pic->TFF)
                {
                    //parser->intcomp_bot[0] = intcomp1 << 13;
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_2, spr->intcomp_fwd_top, intcomp1);
                }
                else
                {
                    parser->intcomp_top[0] = intcomp1;
                    parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].intcomp_top = intcomp1;
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_1, spr->intcomp_fwd_top, intcomp1);
                }
            }
            else // Second field
            {
                if(pic->TFF)
                {
                    parser->intcomp_top[0] = intcomp1;
                    parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].intcomp_top = intcomp1;
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_1, spr->intcomp_bwd_top, intcomp1);
                }
                else
                {
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_2, spr->intcomp_fwd_top, intcomp1);
                }
            }
            break;
        case VC1_INTCOMP_BOTTOM_FIELD:
            if(pic->CurrField == 0) // First field decoded
            {
                if(pic->TFF)
                {
                    parser->intcomp_bot[0] = intcomp1;
                    parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].intcomp_bot = intcomp1;
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_1, spr->intcomp_fwd_bot, intcomp1);
                }
                else
                {
                    parser->intcomp_bot[0] = intcomp1 << 13;
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_2, spr->intcomp_fwd_bot, intcomp1);
                }
            }
            else // Second field
            {
                if(pic->TFF)
                {
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_2, spr->intcomp_fwd_bot, intcomp1);
                }
                else
                {
                    parser->intcomp_bot[0] = intcomp1;
                    parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].intcomp_bot = intcomp1;
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_1, spr->intcomp_bwd_bot, intcomp1);
                }
            }
            break;
        case VC1_INTCOMP_BOTH_FIELD:
            if(pic->CurrField == 0) // First field decoded
            {
                if(pic->TFF)
                {
                    parser->intcomp_bot[0] = intcomp2;
                    parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].intcomp_bot = intcomp2;
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_2, spr->intcomp_fwd_top, intcomp1);
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_1, spr->intcomp_fwd_bot, intcomp2);
                }
                else
                {
                    parser->intcomp_top[0] = intcomp2;
                    parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].intcomp_top = intcomp2;
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_2, spr->intcomp_fwd_bot, intcomp1);
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_1, spr->intcomp_fwd_top, intcomp2);
                }
            }
            else // Second field
            {
                if(pic->TFF)
                {
                    parser->intcomp_top[0] = intcomp1;
                    parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].intcomp_top = intcomp1;
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_1, spr->intcomp_bwd_top, intcomp1);
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_2, spr->intcomp_fwd_bot, intcomp2);
                }
                else
                {
                    parser->intcomp_bot[0] = intcomp1;
                    parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].intcomp_bot = intcomp1;
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_1, spr->intcomp_bwd_bot, intcomp1);
                    BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_2, spr->intcomp_fwd_top, intcomp2);
                }
            }
            break;
        default:
            break;
    } // switch on INTCOMPFIELD

    return;
} // vc1_intcomp

static void handle_intensity_compensation(vc1_viddec_parser_t *parser, vc1_Info *pInfo, VC1D_SPR_REGS *spr)
{
    vc1_PictureLayerHeader *pic = &pInfo->picLayerHeader;
    uint8_t intcomp_present = false;

    if((pic->MVMODE == VC1_MVMODE_INTENSCOMP) || (pic->INTCOMP))
    {
        intcomp_present = true;
        if(pic->FCM == VC1_FCM_FIELD_INTERLACE)  
        {
            vc1_intcomp(parser, pInfo, spr);
        } 
        else 
        {
            BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_1, spr->intcomp_fwd_top, 1);
            BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, LUMA_SCALE_1, spr->intcomp_fwd_top, pic->LUMSCALE);
            BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, LUMA_SHIFT_1, spr->intcomp_fwd_top, pic->LUMSHIFT);

            if(parser->ref_frame[VC1_REF_FRAME_T_MINUS_1].fcm == VC1_FCM_FIELD_INTERLACE)
            {
               BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, INT_COMP_2, spr->intcomp_fwd_bot, 1);
               BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, LUMA_SCALE_2, spr->intcomp_fwd_bot, pic->LUMSCALE);
               BF_WRITE(VC1_0_SEQPIC_INTENSITY_COMPENSATION, LUMA_SHIFT_2, spr->intcomp_fwd_bot, pic->LUMSHIFT);
            }

            parser->intcomp_top[0] = spr->intcomp_fwd_top;
            parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].intcomp_top = spr->intcomp_fwd_top;
            parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].intcomp_bot = spr->intcomp_fwd_top;
        }
    }

    // Propagate the previous picture's intensity compensation
    if(pic->FCM == VC1_FCM_FIELD_INTERLACE)
    {
        if( (pic->CurrField) || 
            ((pic->CurrField == 0) && (parser->ref_frame[VC1_REF_FRAME_T_MINUS_1].fcm == VC1_FCM_FIELD_INTERLACE)))
        { 
            spr->intcomp_fwd_top |= parser->intcomp_top[1];
            spr->intcomp_fwd_bot |= parser->intcomp_bot[1];
        }
    }
    if(pic->FCM == VC1_FCM_FRAME_INTERLACE)
    {
        if( (pic->CurrField) || 
            ((pic->CurrField == 0) && (parser->ref_frame[VC1_REF_FRAME_T_MINUS_1].fcm == VC1_FCM_FIELD_INTERLACE)))
        {
            spr->intcomp_fwd_bot |= parser->intcomp_bot[1];
        }
    }

    switch(pic->PTYPE)
    {
        case VC1_B_FRAME:
            spr->intcomp_fwd_top = parser->intcomp_last[0];
            spr->intcomp_fwd_bot = parser->intcomp_last[1];
            spr->intcomp_bwd_top = parser->intcomp_last[2];
            spr->intcomp_bwd_bot = parser->intcomp_last[3];
            break;
        case VC1_P_FRAME:
            // If first field, store the intcomp values to propagate.
            // If second field has valid intcomp values, store them
            // to propagate.
            if(pic->CurrField == 0) // first field
            {
                parser->intcomp_last[0] = spr->intcomp_fwd_top;
                parser->intcomp_last[1] = spr->intcomp_fwd_bot;
                parser->intcomp_last[2] = spr->intcomp_bwd_top;
                parser->intcomp_last[3] = spr->intcomp_bwd_bot;
            }
            else // Second field
            {
                    parser->intcomp_last[0] |= spr->intcomp_fwd_top;
                    parser->intcomp_last[1] |= spr->intcomp_fwd_bot;
                    parser->intcomp_last[2] |= spr->intcomp_bwd_top;
                    parser->intcomp_last[3] |= spr->intcomp_bwd_bot;
            }
            break;
        case VC1_I_FRAME:
        case VC1_BI_FRAME:
            break;
        default:
            break;
    }

    return; 
} // handle_intensity_compensation

/**
 * This function populates the registers for range reduction (main profile) 
 * This function assumes pInfo->metadata.RANGERED is ON at the sequence layer (J.1.17)
 * A frame is marked as range reduced by the RANGEREDFRM flag at the picture layer,  
 * and the output of the decoded range reduced frame needs to be scaled up (8.1.1.4).
 * Previous reference frame needs be upscaled or downscaled based on the RR status of
 * current and previous frame (8.3.4.11)
 */
static inline void vc1_fill_RR_hw_struct(vc1_viddec_parser_t *parser, vc1_Info *pInfo, VC1D_SPR_REGS *spr)
{
    vc1_PictureLayerHeader *pic = &pInfo->picLayerHeader;
    int is_previous_ref_rr=0;
   
    /* range reduction applies to luma and chroma component 
    which are the same register bit as RANGE_MAPY_FLAG, RANGE_MAPUV_FLAG */
    BF_WRITE(VC1_0_SEQPIC_RANGE_MAP, RANGE_MAP_Y_FLAG, spr->range_map, pic->RANGEREDFRM);
    BF_WRITE(VC1_0_SEQPIC_RANGE_MAP, RANGE_MAP_UV_FLAG, spr->range_map, pic->RANGEREDFRM); 
    
    /* Get the range reduced status of the previous frame */
    switch (pic->PTYPE) 
    {
        case VC1_P_FRAME:
        {
            is_previous_ref_rr = parser->ref_frame[VC1_REF_FRAME_T_MINUS_1].rr_frm;
            break;
        }
        case VC1_B_FRAME:
        {
            is_previous_ref_rr = parser->ref_frame[VC1_REF_FRAME_T_MINUS_2].rr_frm;
            break;
        }
        default:
        {
            break;
        }
    }

    /* if current frame is RR and previous frame is not 
        donwscale the reference pixel ( RANGE_REF_RED_TYPE =1 in register) */
    if(pic->RANGEREDFRM) 
    {
        if(!is_previous_ref_rr) 
        {
            BF_WRITE(VC1_0_SEQPIC_RECON_CONTROL, RANGE_REF_RED_EN, spr->recon_control, 1);
            BF_WRITE(VC1_0_SEQPIC_RECON_CONTROL, RANGE_REF_RED_TYPE, spr->recon_control, 1);
        }
    } 
    else 
    {  
        /* if current frame is not RR but previous was RR,  scale up the reference frame ( RANGE_REF_RED_TYPE = 0) */
        if(is_previous_ref_rr) 
        {
            BF_WRITE(VC1_0_SEQPIC_RECON_CONTROL, RANGE_REF_RED_EN, spr->recon_control, 1);
            BF_WRITE(VC1_0_SEQPIC_RECON_CONTROL, RANGE_REF_RED_TYPE, spr->recon_control, 0);
        }
    } // end for RR upscale

} // vc1_fill_RR_hw_struct

/**
 * fill workload items that will load registers for HW decoder
 */
static void vc1_fill_hw_struct(vc1_viddec_parser_t *parser, vc1_Info* pInfo, VC1D_SPR_REGS *spr)
{
    vc1_metadata_t *md = &pInfo->metadata;
    vc1_PictureLayerHeader *pic = &pInfo->picLayerHeader;
    int field = pic->CurrField;
    int ptype;

    ptype = pic->PTYPE;

    LOG_CRIT("ptype = %d, field = %d, topfield = %d, slice = %d", ptype, pic->CurrField, pic->BottomField, pic->SLICE_ADDR);

    /* Common to both fields */
    BF_WRITE(VC1_0_SEQPIC_STREAM_FORMAT_1, PROFILE, spr->stream_format1, md->PROFILE);

    BF_WRITE(VC1_0_SEQPIC_CODED_SIZE, WIDTH, spr->coded_size, md->width);
    BF_WRITE(VC1_0_SEQPIC_CODED_SIZE, HEIGHT, spr->coded_size, md->height);

    BF_WRITE(VC1_0_SEQPIC_STREAM_FORMAT_2, INTERLACE, spr->stream_format2, md->INTERLACE);

    BF_WRITE(VC1_0_SEQPIC_ENTRY_POINT_1, LOOPFILTER,    spr->entrypoint1, md->LOOPFILTER);
    BF_WRITE(VC1_0_SEQPIC_ENTRY_POINT_1, FASTUVMC,      spr->entrypoint1, md->FASTUVMC);
    BF_WRITE(VC1_0_SEQPIC_ENTRY_POINT_1, EXTENDED_MV,   spr->entrypoint1, md->EXTENDED_MV);
    BF_WRITE(VC1_0_SEQPIC_ENTRY_POINT_1, DQUANT,        spr->entrypoint1, md->DQUANT);
    BF_WRITE(VC1_0_SEQPIC_ENTRY_POINT_1, VS_TRANSFORM,  spr->entrypoint1, md->VSTRANSFORM);
    BF_WRITE(VC1_0_SEQPIC_ENTRY_POINT_1, OVERLAP,       spr->entrypoint1, md->OVERLAP);
    BF_WRITE(VC1_0_SEQPIC_ENTRY_POINT_1, QUANTIZER,     spr->entrypoint1, md->QUANTIZER);
    BF_WRITE(VC1_0_SEQPIC_ENTRY_POINT_1, EXTENDED_DMV,  spr->entrypoint1, md->EXTENDED_DMV);

    /* if range reduction is indicated at seq. layer, populate range reduction registers for the frame*/
    if(md->RANGERED)
    {
        vc1_fill_RR_hw_struct(parser, pInfo, spr );
    } 
    else 
    { //range mapping
        BF_WRITE( VC1_0_SEQPIC_RANGE_MAP, RANGE_MAP_Y_FLAG, spr->range_map, md->RANGE_MAPY_FLAG);
        BF_WRITE( VC1_0_SEQPIC_RANGE_MAP, RANGE_MAP_Y, spr->range_map, md->RANGE_MAPY);
        BF_WRITE( VC1_0_SEQPIC_RANGE_MAP, RANGE_MAP_UV_FLAG, spr->range_map, md->RANGE_MAPUV_FLAG);
        BF_WRITE( VC1_0_SEQPIC_RANGE_MAP, RANGE_MAP_UV, spr->range_map, md->RANGE_MAPUV);
    }

    BF_WRITE(VC1_0_SEQPIC_FRAME_TYPE, FCM, spr->frame_type, pic->FCM);
    BF_WRITE(VC1_0_SEQPIC_FRAME_TYPE, PTYPE, spr->frame_type, pic->PTYPE);

    BF_WRITE( VC1_0_SEQPIC_RECON_CONTROL, RNDCTRL, spr->recon_control, md->RNDCTRL);
    BF_WRITE( VC1_0_SEQPIC_RECON_CONTROL, UVSAMP, spr->recon_control, pic->UVSAMP);
    BF_WRITE( VC1_0_SEQPIC_RECON_CONTROL, PQUANT, spr->recon_control, pic->PQUANT);
    BF_WRITE( VC1_0_SEQPIC_RECON_CONTROL, HALFQP, spr->recon_control, pic->HALFQP);
    BF_WRITE( VC1_0_SEQPIC_RECON_CONTROL, UNIFORM_QNT, spr->recon_control, pic->UniformQuant);
    BF_WRITE( VC1_0_SEQPIC_RECON_CONTROL, POSTPROC, spr->recon_control, pic->POSTPROC);
    BF_WRITE( VC1_0_SEQPIC_RECON_CONTROL, CONDOVER, spr->recon_control, pic->CONDOVER);
    BF_WRITE( VC1_0_SEQPIC_RECON_CONTROL, PQINDEX_LE8, spr->recon_control, (pic->PQINDEX <= 8));

    BF_WRITE( VC1_0_SEQPIC_MOTION_VECTOR_CONTROL, MVRANGE,   spr->mv_control, pic->MVRANGE);
    if ( pic->MVMODE == VC1_MVMODE_INTENSCOMP)
        BF_WRITE( VC1_0_SEQPIC_MOTION_VECTOR_CONTROL, MVMODE,    spr->mv_control, pic->MVMODE2);
    else
        BF_WRITE( VC1_0_SEQPIC_MOTION_VECTOR_CONTROL, MVMODE,    spr->mv_control, pic->MVMODE);
    BF_WRITE( VC1_0_SEQPIC_MOTION_VECTOR_CONTROL, MVTAB,  spr->mv_control,  pic->MVTAB);
    BF_WRITE( VC1_0_SEQPIC_MOTION_VECTOR_CONTROL, DMVRANGE,  spr->mv_control, pic->DMVRANGE);
    BF_WRITE( VC1_0_SEQPIC_MOTION_VECTOR_CONTROL, MV4SWITCH, spr->mv_control, pic->MV4SWITCH);
    BF_WRITE( VC1_0_SEQPIC_MOTION_VECTOR_CONTROL, MBMODETAB, spr->mv_control, pic->MBMODETAB);
    BF_WRITE( VC1_0_SEQPIC_MOTION_VECTOR_CONTROL, NUMREF,    spr->mv_control,
        pic->NUMREF || ((pic->PTYPE == VC1_B_FRAME) && ( pic->FCM == VC1_FCM_FIELD_INTERLACE )  ));
    BF_WRITE( VC1_0_SEQPIC_MOTION_VECTOR_CONTROL, REFFIELD,  spr->mv_control, pic->REFFIELD);

    handle_intensity_compensation(parser, pInfo, spr);

    BF_WRITE(VC1_0_SEQPIC_REFERENCE_B_FRACTION, BFRACTION_DEN, spr->ref_bfraction, pic->BFRACTION_DEN);
    BF_WRITE(VC1_0_SEQPIC_REFERENCE_B_FRACTION, BFRACTION_NUM, spr->ref_bfraction, pic->BFRACTION_NUM);
    BF_WRITE(VC1_0_SEQPIC_REFERENCE_B_FRACTION, REFDIST, spr->ref_bfraction, md->REFDIST);

    // BLOCK CONTROL REGISTER Offset 0x2C
    BF_WRITE( VC1_0_SEQPIC_BLOCK_CONTROL, CBPTAB, spr->blk_control, pic->CBPTAB);
    BF_WRITE(VC1_0_SEQPIC_BLOCK_CONTROL, TTMFB, spr->blk_control, pic->TTMBF);
    BF_WRITE(VC1_0_SEQPIC_BLOCK_CONTROL, TTFRM, spr->blk_control, pic->TTFRM);
    BF_WRITE(VC1_0_SEQPIC_BLOCK_CONTROL, MV2BPTAB, spr->blk_control, pic->MV2BPTAB);
    BF_WRITE(VC1_0_SEQPIC_BLOCK_CONTROL, MV4BPTAB, spr->blk_control, pic->MV4BPTAB);
    if((field == 1) && (pic->SLICE_ADDR)) 
    {
        int mby = md->height * 2 + 2;
        mby = (mby + 15 ) / 16;
        pic->SLICE_ADDR -= (mby/2);
    }
    BF_WRITE(VC1_0_SEQPIC_BLOCK_CONTROL, INITIAL_MV_Y, spr->blk_control, pic->SLICE_ADDR);
    BF_WRITE(VC1_0_SEQPIC_BLOCK_CONTROL, BP_RAW_ID2, spr->blk_control, md->bp_raw[0]);
    BF_WRITE(VC1_0_SEQPIC_BLOCK_CONTROL, BP_RAW_ID1, spr->blk_control, md->bp_raw[1]);
    BF_WRITE(VC1_0_SEQPIC_BLOCK_CONTROL, BP_RAW_ID0, spr->blk_control, md->bp_raw[2]);

    BF_WRITE( VC1_0_SEQPIC_TRANSFORM_DATA, TRANSACFRM,  spr->trans_data, pic->TRANSACFRM);
    BF_WRITE( VC1_0_SEQPIC_TRANSFORM_DATA, TRANSACFRM2, spr->trans_data, pic->TRANSACFRM2);
    BF_WRITE( VC1_0_SEQPIC_TRANSFORM_DATA, TRANSDCTAB,  spr->trans_data, pic->TRANSDCTAB);

    // When DQUANT is 1 or 2, we have the VOPDQUANT structure in the bitstream that
    // controls the value calculated for ALTPQUANT
    // ALTPQUANT must be in the range of 1 and 31 for it to be valid 
    // DQUANTFRM is present only when DQUANT is 1 and ALTPQUANT setting should be dependent on DQUANT instead
    if(md->DQUANT)
    {
        if(pic->PQDIFF == 7)
            BF_WRITE( VC1_0_SEQPIC_VOP_DEQUANT, PQUANT_ALT, spr->vop_dquant, pic->ABSPQ);
        else if (pic->DQUANTFRM == 1)
            BF_WRITE( VC1_0_SEQPIC_VOP_DEQUANT, PQUANT_ALT, spr->vop_dquant, pic->PQUANT + pic->PQDIFF + 1);
    }
    BF_WRITE( VC1_0_SEQPIC_VOP_DEQUANT, DQUANTFRM, spr->vop_dquant, pic->DQUANTFRM);
    BF_WRITE( VC1_0_SEQPIC_VOP_DEQUANT, DQPROFILE, spr->vop_dquant, pic->DQPROFILE);
    BF_WRITE( VC1_0_SEQPIC_VOP_DEQUANT, DQES,      spr->vop_dquant, pic->DQSBEDGE);
    BF_WRITE( VC1_0_SEQPIC_VOP_DEQUANT, DQBILEVEL, spr->vop_dquant, pic->DQBILEVEL);

    BF_WRITE(VC1_0_SEQPIC_CURR_FRAME_ID,FCM, spr->ref_frm_id[VC1_FRAME_CURRENT_REF], pic->FCM );

    if ( ptype == VC1_B_FRAME) {
         // Forward reference is past reference and is the second temporally closest reference - hence minus_2
         BF_WRITE(VC1_0_SEQPIC_FWD_REF_FRAME_ID, FCM, parser->spr.ref_frm_id[VC1_FRAME_PAST], parser->ref_frame[VC1_REF_FRAME_T_MINUS_2].fcm );
         // Backward reference is future reference frame and is temporally the closest - hence minus_1
         BF_WRITE(VC1_0_SEQPIC_BWD_REF_FRAME_ID, FCM, parser->spr.ref_frm_id[VC1_FRAME_FUTURE], parser->ref_frame[VC1_REF_FRAME_T_MINUS_1].fcm );
    } else {
         // Only Forward reference is valid and is the temporally closest reference - hence minus_1, backward is set same as forward
         BF_WRITE(VC1_0_SEQPIC_FWD_REF_FRAME_ID, FCM, parser->spr.ref_frm_id[VC1_FRAME_PAST], parser->ref_frame[VC1_REF_FRAME_T_MINUS_1].fcm );
         BF_WRITE(VC1_0_SEQPIC_BWD_REF_FRAME_ID, FCM, parser->spr.ref_frm_id[VC1_FRAME_FUTURE], parser->ref_frame[VC1_REF_FRAME_T_MINUS_1].fcm );
    }

    BF_WRITE( VC1_0_SEQPIC_FIELD_REF_FRAME_ID, TOP_FIELD,    spr->fieldref_ctrl_id, pic->BottomField);
    BF_WRITE( VC1_0_SEQPIC_FIELD_REF_FRAME_ID, SECOND_FIELD, spr->fieldref_ctrl_id, pic->CurrField);
    if(parser->info.picLayerHeader.PTYPE == VC1_I_FRAME)
    {
        BF_WRITE(VC1_0_SEQPIC_FIELD_REF_FRAME_ID, ANCHOR, spr->fieldref_ctrl_id, 1);
    }
    else
    {
        BF_WRITE(VC1_0_SEQPIC_FIELD_REF_FRAME_ID, ANCHOR, spr->fieldref_ctrl_id, parser->ref_frame[VC1_REF_FRAME_T_MINUS_1].anchor[pic->CurrField]);
    }

    if( pic->FCM == VC1_FCM_FIELD_INTERLACE ) {
        BF_WRITE(VC1_0_SEQPIC_IMAGE_STRUCTURE, IMG_STRUC, spr->imgstruct, (pic->BottomField) ? 2 : 1);
    }

    return;
} // vc1_fill_hw_struct

int32_t vc1_parse_emit_current_frame(void *parent, vc1_viddec_parser_t *parser)
{
   viddec_workload_item_t wi;
   const uint32_t *pl;
   int i;
   int nitems;

    if( parser->info.picLayerHeader.PTYPE == VC1_SKIPPED_FRAME ) {
        translate_parser_info_to_frame_attributes( parent, parser );
        return 0;
    }

   translate_parser_info_to_frame_attributes( parent, parser );
   memset(&parser->spr, 0, sizeof(VC1D_SPR_REGS));
   vc1_fill_hw_struct( parser, &parser->info, &parser->spr );

   /* STUFF BSP Data Memory it into a variety of workload items */

   pl = (const uint32_t *) &parser->spr;

   // How many payloads must be generated
   nitems = (sizeof(parser->spr) + 7) / 8; /* In QWORDs rounded up */


   // Dump DMEM to an array of workitems
   for( i = 0; (i < nitems) && ( (parser->info.picLayerHeader.SLICE_ADDR == 0) || parser->info.picture_info_has_changed ); i++ )
   {
      wi.vwi_type           = VIDDEC_WORKLOAD_DECODER_SPECIFIC;
      wi.data.data_offset   = (unsigned int)pl - (unsigned int)&parser->spr; // offset within struct
      wi.data.data_payload[0] = pl[0];
      wi.data.data_payload[1] = pl[1];
      pl += 2;

      viddec_pm_append_workitem( parent, &wi );
   }
   
   {
      uint32_t bit, byte;
      uint8_t is_emul;
      viddec_pm_get_au_pos(parent, &bit, &byte, &is_emul);
      // Send current bit offset and current slice
      wi.vwi_type          = VIDDEC_WORKLOAD_VC1_BITOFFSET;
      // If slice data starts in the middle of the emulation prevention sequence - 
      // Eg: 00 00 03 01 - slice data starts at the second byte of 0s, we still feed the data 
      // to the decoder starting at the first byte of 0s so that the decoder can detect the 
      // emulation prevention. But the actual data starts are offset 8 in this bit sequence.
      wi.vwi_payload[0]    = bit + (is_emul*8);
      wi.vwi_payload[1]    = parser->info.picLayerHeader.SLICE_ADDR;
      wi.vwi_payload[2]    = 0xdeaddead;
      viddec_pm_append_workitem( parent, &wi );
   }

   viddec_pm_append_pixeldata( parent );

   return(0);
}

/* sends VIDDEC_WORKLOAD_VC1_PAST_FRAME item */
static inline void vc1_send_past_ref_items(void *parent)
{
   viddec_workload_item_t wi;
   wi.vwi_type = VIDDEC_WORKLOAD_VC1_PAST_FRAME;
   wi.ref_frame.reference_id = 0;
   wi.ref_frame.luma_phys_addr = 0;
   wi.ref_frame.chroma_phys_addr = 0;
   viddec_pm_append_workitem( parent, &wi );
   return;
}

/* send future frame item */
static inline void vc1_send_future_ref_items(void *parent)
{
   viddec_workload_item_t wi;
   wi.vwi_type = VIDDEC_WORKLOAD_VC1_FUTURE_FRAME;
   wi.ref_frame.reference_id = 0;
   wi.ref_frame.luma_phys_addr = 0;
   wi.ref_frame.chroma_phys_addr = 0;
   viddec_pm_append_workitem( parent, &wi );
   return;
}

/* send reorder frame item to host
 * future frame gets push to past   */
static inline void send_reorder_ref_items(void *parent)
{
   viddec_workload_item_t wi;
   wi.vwi_type = VIDDEC_WORKLOAD_REFERENCE_FRAME_REORDER;
   wi.ref_reorder.ref_table_offset = 0;
   wi.ref_reorder.ref_reorder_00010203 = 0x01010203; //put reference frame index 1 as reference index 0
   wi.ref_reorder.ref_reorder_04050607 = 0x04050607; // index 4,5,6,7 stay the same
   viddec_pm_append_workitem( parent, &wi );
   return;
} // send_reorder_ref_items

/** update workload with more workload items for ref and update values to store...
 */
void vc1_start_new_frame(void *parent, vc1_viddec_parser_t *parser)
{
   vc1_metadata_t *md = &(parser->info.metadata);
   viddec_workload_t *wl = viddec_pm_get_header(parent);
   int frame_type = parser->info.picLayerHeader.PTYPE;
   int frame_id = 1; // new reference frame is assigned index 1

   /* init */
   memset(&parser->spr, 0, sizeof(parser->spr));
   wl->is_reference_frame = 0;

   /* set flag - extra ouput frame needed for range adjustment (range mapping or range reduction */
   if (parser->info.metadata.RANGE_MAPY_FLAG || 
        parser->info.metadata.RANGE_MAPUV_FLAG || 
        parser->info.picLayerHeader.RANGEREDFRM)
   {
      wl->is_reference_frame |= WORKLOAD_FLAGS_RA_FRAME;
   }
   
   LOG_CRIT("vc1_start_new_frame: frame_type=%d \n",frame_type);

   parser->is_reference_picture = ((VC1_B_FRAME != frame_type) && (VC1_BI_FRAME != frame_type));

   /* reference / anchor frames processing 
    * we need to send reorder before reference frames */ 
   if (parser->is_reference_picture)
   {
      /* one frame has been sent */
      if (parser->ref_frame[VC1_REF_FRAME_T_MINUS_1].id != -1) 
      {
         /* there is a frame in the reference buffer, move it to the past */
         send_reorder_ref_items(parent); 
      }
   }

   /* send workitems for reference frames */
   switch( frame_type ) 
   {
      case VC1_B_FRAME:
      {
          vc1_send_past_ref_items(parent);
          vc1_send_future_ref_items(parent);
          break;
      }
      case VC1_SKIPPED_FRAME:
      {
         wl->is_reference_frame |= WORKLOAD_SKIPPED_FRAME;
           vc1_send_past_ref_items(parent);
          break;
      }
      case VC1_P_FRAME:
      {
          vc1_send_past_ref_items( parent);
         break;
      }
    default:
        break;
   }

    /* reference / anchor frames from previous code 
     * we may need it for frame reduction */ 
    if (parser->is_reference_picture)
    {
        wl->is_reference_frame |= WORKLOAD_REFERENCE_FRAME | (frame_id & WORKLOAD_REFERENCE_FRAME_BMASK);

        parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].id      = frame_id;
        parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].fcm     = parser->info.picLayerHeader.FCM;
        parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].anchor[0]  = (parser->info.picLayerHeader.PTYPE == VC1_I_FRAME);
        if(parser->info.picLayerHeader.FCM == VC1_FCM_FIELD_INTERLACE)
        {
            parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].anchor[1] = (parser->info.picLayerHeader.PTypeField2 == VC1_I_FRAME);
        }
        else
        {
            parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].anchor[1] = parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].anchor[0];
        }

        parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].type = parser->info.picLayerHeader.PTYPE;
        parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].rr_en = md->RANGERED;
        parser->ref_frame[VC1_REF_FRAME_T_MINUS_0].rr_frm = parser->info.picLayerHeader.RANGEREDFRM;

        LOG_CRIT("anchor[0] = %d, anchor[1] = %d",
            parser->ref_frame[VC1_REF_FRAME_T_MINUS_1].anchor[0],
            parser->ref_frame[VC1_REF_FRAME_T_MINUS_1].anchor[1] );
    }

    return;
} // vc1_start_new_frame

void vc1_end_frame(vc1_viddec_parser_t *parser)
{
    /* update status of reference frames */
    if(parser->is_reference_picture)
    {
        parser->ref_frame[VC1_REF_FRAME_T_MINUS_2] = parser->ref_frame[VC1_REF_FRAME_T_MINUS_1];
        parser->ref_frame[VC1_REF_FRAME_T_MINUS_1] = parser->ref_frame[VC1_REF_FRAME_T_MINUS_0];
    }

    return;
} // vc1_end_frame

