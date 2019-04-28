#include "viddec_mp4_shortheader.h"

typedef struct
{
    uint16_t vop_width;
    uint16_t vop_height;
    uint16_t num_macroblocks_in_gob;
    uint16_t num_gobs_in_vop;
    uint8_t  num_rows_in_gob;
} svh_src_fmt_params_t;

const svh_src_fmt_params_t svh_src_fmt_defaults[5] =
{
   {128,    96,   8,  6, 1},
   {176,   144,  11,  9, 1},
   {352,   288,  22, 18, 1},
   {704,   576,  88, 18, 2},
   {1408, 1152, 352, 18, 4},
};

mp4_Status_t mp4_Parse_VideoObjectPlane_svh(void *parent, viddec_mp4_parser_t *parser)
{
    mp4_Status_t ret = MP4_STATUS_OK;
    unsigned int data;
    mp4_VideoObjectPlaneH263 *svh = &(parser->info.VisualObject.VideoObject.VideoObjectPlaneH263);
    int32_t getbits = 0;
    
    do
    {
        getbits = viddec_pm_get_bits(parent, &data, 27);
        BREAK_GETBITS_REQD_MISSING(getbits, ret);

        data = data >> 1; // zero_bit
        svh->vop_quant = (data & 0x1F);
        data = data >> 9; // vop_quant + four_reserved_zero_bits
        svh->picture_coding_type = (data & 0x1);
        data = data >> 1; // vop_quant + four_reserved_zero_bits
        svh->source_format = (data & 0x7);
        data = data >> 8; // source_format + full_picture_freeze_release + document_camera_indicator + split_screen_indicator + zero_bit + marker_bit
        svh->temporal_reference = data;

        if (svh->source_format == 0 || svh->source_format > 5)
        {
            DEB("Error: Bad value for VideoPlaneWithShortHeader.source_format\n");
            ret = MP4_STATUS_NOTSUPPORT;
            break;
        }

        for (;;) 
        {
            getbits = viddec_pm_get_bits(parent, &data, 1); // pei
            BREAK_GETBITS_FAIL(getbits, ret);
            if (!data)
                break;
            getbits = viddec_pm_get_bits(parent, &data, 8); // psupp
            BREAK_GETBITS_FAIL(getbits, ret);
        }

        // Anything after this needs to be fed to the decoder as PIXEL_ES
    } while(0);

    return ret;
}

mp4_Status_t mp4_Parse_VideoObject_svh(void *parent, viddec_mp4_parser_t *parser)
{
    mp4_Status_t             ret=MP4_STATUS_OK;
    mp4_Info_t              *pInfo = &(parser->info);
    mp4_VideoSignalType_t *vst = &(pInfo->VisualObject.VideoSignalType);
    mp4_VideoObjectLayer_t  *vol = &(pInfo->VisualObject.VideoObject);
    mp4_VideoObjectPlane_t  *vop = &(pInfo->VisualObject.VideoObject.VideoObjectPlane);
    mp4_VideoObjectPlaneH263 *svh = &(pInfo->VisualObject.VideoObject.VideoObjectPlaneH263);
    uint8_t index = 0;

    ret = mp4_Parse_VideoObjectPlane_svh(parent, parser);
    if(ret == MP4_STATUS_OK)
    {
        // Populate defaults for the svh
        vol->short_video_header = 1;
        vol->video_object_layer_shape = MP4_SHAPE_TYPE_RECTANGULAR;
        vol->obmc_disable = 1;
        vol->quant_type = 0;
        vol->resync_marker_disable = 1;
        vol->data_partitioned = 0;
        vol->reversible_vlc = 0;
        vol->interlaced = 0;
        vol->complexity_estimation_disable = 1;
        vol->scalability = 0;
        vol->not_8_bit = 0;
        vol->bits_per_pixel = 8;
        vol->quant_precision = 5;
        vol->vop_time_increment_resolution = 30000;
        vol->fixed_vop_time_increment = 1001;
        vol->aspect_ratio_info = MP4_ASPECT_RATIO_12_11;

        vop->vop_rounding_type = 0;
        vop->vop_fcode_forward = 1;
        vop->vop_coded = 1;
        vop->vop_coding_type = svh->picture_coding_type ? MP4_VOP_TYPE_P: MP4_VOP_TYPE_I;
        vop->vop_quant = svh->vop_quant;

        vst->colour_primaries = 1;
        vst->transfer_characteristics = 1;
        vst->matrix_coefficients = 6;

        index = svh->source_format - 1;
        vol->video_object_layer_width = svh_src_fmt_defaults[index].vop_width;
        vol->video_object_layer_height = svh_src_fmt_defaults[index].vop_height;
        svh->num_macroblocks_in_gob = svh_src_fmt_defaults[index].num_macroblocks_in_gob;
        svh->num_gobs_in_vop = svh_src_fmt_defaults[index].num_gobs_in_vop;
        svh->num_rows_in_gob = svh_src_fmt_defaults[index].num_rows_in_gob;
    }

    mp4_set_hdr_bitstream_error(parser, false, ret);

    // POPULATE WORKLOAD ITEM
    {
        viddec_workload_item_t wi;

        wi.vwi_type = VIDDEC_WORKLOAD_MPEG4_VIDEO_PLANE_SHORT;

        wi.mp4_vpsh.info = 0;
        wi.mp4_vpsh.pad1 = 0;
        wi.mp4_vpsh.pad2 = 0;

        viddec_fw_mp4_vpsh_set_source_format(&wi.mp4_vpsh, svh->source_format);

        ret = viddec_pm_append_workitem(parent, &wi);
        if(ret == 1)
            ret = MP4_STATUS_OK;
    }

    return ret;
}
