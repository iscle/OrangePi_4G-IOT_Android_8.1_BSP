/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein is
 * confidential and proprietary to MediaTek Inc. and/or its licensors. Without
 * the prior written permission of MediaTek inc. and/or its licensors, any
 * reproduction, modification, use or disclosure of MediaTek Software, and
 * information contained herein, in whole or in part, shall be strictly
 * prohibited.
 * 
 * MediaTek Inc. (C) 2010. All rights reserved.
 * 
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER
 * ON AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL
 * WARRANTIES, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NONINFRINGEMENT. NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH
 * RESPECT TO THE SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY,
 * INCORPORATED IN, OR SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES
 * TO LOOK ONLY TO SUCH THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO.
 * RECEIVER EXPRESSLY ACKNOWLEDGES THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO
 * OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES CONTAINED IN MEDIATEK
 * SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE
 * RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S
 * ENTIRE AND CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE
 * RELEASED HEREUNDER WILL BE, AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE
 * MEDIATEK SOFTWARE AT ISSUE, OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE
 * CHARGE PAID BY RECEIVER TO MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek
 * Software") have been modified by MediaTek Inc. All revisions are subject to
 * any receiver's applicable license agreements with MediaTek Inc.
 */

/* mtk flv container support audio codec:
 * FLV_AUDIO_CODEC_ID_PCM       = 0
 * FLV_AUDIO_CODEC_ID_MP3       = 2
 * FLV_AUDIO_CODEC_ID_PCM_LE    = 3
 * FLV_AUDIO_CODEC_ID_AAC       = 10

 * mtk flv container support video codec:
 * FLV_VIDEO_CODEC_ID_SORENSON_SPARK    = 2
 * FLV_VIDEO_CODEC_ID_AVC               = 7
 * FLV_VIDEO_CODEC_ID_HEVC              = 12
 * FLV_VIDEO_CODEC_ID_HEVC_XL           = 8
 * FLV_VIDEO_CODEC_ID_HEVC_PPS          = 9
 */

#ifdef MTK_FLV_PLAYBACK_SUPPORT
#include "include/MtkFLVExtractor.h"
#include <arpa/inet.h>
#include <ctype.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <math.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/ABitReader.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaBufferGroup.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/Utils.h>
#include <utils/String8.h>
#include <cutils/properties.h>
#include "avc_utils.h"

#undef LOG_TAG
#define LOG_TAG "MtkFLVExtractor"

#define FLV_DEBUG_LOGE(x, ...) ALOGE(" " x,  ##__VA_ARGS__)
#define FLV_DEBUG_LOGD(x, ...)  // ALOGD(" " x,  ##__VA_ARGS__);
#define FLV_DEBUG_LOGV(x, ...)  // ALOGV(" " x,  ##__VA_ARGS__)
#define FLV_DEBUG_LOGM(x, ...)  // ALOGV(" " x,  ##__VA_ARGS__)

flvParser::flvParser(void* source, flv_io_read_func_ptr read,
                        flv_io_write_func_ptr write,
                        flv_io_seek_func_ptr seek)

{
    flv_iostream_str iostream;
    iostream.read = read;
    iostream.write = write;
    iostream.seek = seek;
    iostream.source = source;
    mError = FLV_OK;
    mfile =NULL;
    mSeekTable = NULL;
#ifdef FLV_DIRECT_SEEK_SUPPORT
    bUpdateSeekTable = true;
#endif
    //Initialize asf parser library
    mfile = flv_open_file(&iostream);
    if (!mfile)
        FLV_DEBUG_LOGE("flvParser:Error failed to Initialize FLV parser");

    FLV_DEBUG_LOGV("flvParser:mfile=0x%p\n",mfile);
}

flvParser::~flvParser()
{
    flv_close();
}

flv_file_str* flvParser::flv_open_file(flv_iostream_str *iostream)
{
    flv_file_str *flvfile = NULL;
    flv_seek_table * pTable=NULL;

    if(!iostream){
        FLV_DEBUG_LOGE("flv_open_file: error1, iostreamis NULL\n");
        return NULL;
    }
    flvfile = (flv_file_str *)calloc(1, sizeof(flv_file_str)); //malloc + set0
    if (!flvfile){
        FLV_DEBUG_LOGE("flv_open_file: error2, alloc mem fail\n");
        return NULL;
    }
    FLV_DEBUG_LOGM("[memory] alloc 0x%p\n",flvfile);

    flvfile->mIoStream.read = iostream->read;
    flvfile->mIoStream.write = iostream->write;
    flvfile->mIoStream.seek = iostream->seek;
    flvfile->mIoStream.source = iostream->source;

    flvfile->mMeta = (flv_meta_str *)calloc(1, sizeof(flv_meta_str));
    if(!flvfile->mMeta){
        FLV_DEBUG_LOGM("[memory] free 0x%p\n",flvfile);
        free(flvfile);
        flvfile=NULL;
        FLV_DEBUG_LOGE("flv_open_file: error3, alloc mem fail\n");
        return NULL;
    }
    flvfile->mMeta->audio_codec_id = 0xFF;
    flvfile->mMeta->video_codec_id = 0xFF;
    FLV_DEBUG_LOGM("[memory] alloc 0x%p\n",flvfile->mMeta);


    mSeekTable= (flv_seek_table *)calloc(1, sizeof(flv_seek_table));
    if(!mSeekTable){
        FLV_DEBUG_LOGM("[memory] free 0x%p\n",flvfile->mMeta);
        free(flvfile->mMeta);
        flvfile->mMeta = NULL;
        FLV_DEBUG_LOGM("[memory] free 0x%p\n",flvfile);
        free(flvfile);
        flvfile=NULL;
        FLV_DEBUG_LOGE("flv_open_file: error4, alloc mem fail\n");
        return NULL;
    }
    FLV_DEBUG_LOGM("[memory] alloc 0x%p\n",mSeekTable);

    pTable = (flv_seek_table *)(mSeekTable);
    pTable->pEntry = (flv_seek_table_entry*)calloc(FLV_SEEK_ENTRY_MAX_ENTRIES, sizeof(flv_seek_table_entry));
    pTable->LastTime = 0;
    pTable->MaxEntries= FLV_SEEK_ENTRY_MAX_ENTRIES;
    pTable->SetEntries = 0;
    pTable->TimeGranularity = FLV_SEEK_MAX_TIME_GRANULARITY;//ms
    pTable->RangeTime = 0;//the time range this table covers

    mError = FLV_OK;
    return flvfile;
}


void flvParser::flv_close()
{
    if (mfile){
       if(mfile->mMeta){
            if(mfile->mMeta->filepositions){
                FLV_DEBUG_LOGM("[memory] Free 0x%p\n",mfile->mMeta->filepositions);
                free(mfile->mMeta->filepositions);
                mfile->mMeta->filepositions = NULL;
            }

            if(mfile->mMeta->times){
                FLV_DEBUG_LOGM("[memory] Free 0x%p\n",mfile->mMeta->times);
                free(mfile->mMeta->times);
                mfile->mMeta->times = NULL;
            }

            FLV_DEBUG_LOGM("[memory] Free 0x%p\n",mfile->mMeta);
            free(mfile->mMeta);
            mfile->mMeta =NULL;
       }

       if(mSeekTable){
            if(mSeekTable->pEntry){
                FLV_DEBUG_LOGM("[memory] Free 0x%p\n",mfile->mMeta);
                free(mSeekTable->pEntry);
                mSeekTable->pEntry = NULL;
            }
            FLV_DEBUG_LOGM("[memory] Free 0x%p\n",mSeekTable);
            free(mSeekTable);
            mSeekTable = NULL;
       }

       FLV_DEBUG_LOGM("[memory] Free 0x%p\n",mfile);
       free(mfile);
       mfile = NULL;
    }
}

FLV_ERROR_TYPE flvParser::IsflvFile()
{
    char TAG[4];
    int32_t tmp;
    const char string[4]= "FLV";

    mfile->mIoStream.seek(mfile->mIoStream.source, 0, FLV_SEEK_FROM_SET);
    tmp = flv_byteio_read((uint8_t*)TAG,3,&(mfile->mIoStream));
    if(tmp < 3){
        FLV_DEBUG_LOGE("flv_parse_header: error read file,tmp=%d\n",tmp);
        return FLV_FILE_READ_ERR;
    }
    TAG[3] = '\0';
    mfile->mIoStream.seek(mfile->mIoStream.source, 0, FLV_SEEK_FROM_SET);

    tmp = strncmp(TAG, string, 3);
    if(tmp==0){
        FLV_DEBUG_LOGD("IsflvFile: this is an FLV file\n");
        return FLV_OK;
    }
    FLV_DEBUG_LOGD("IsflvFile:not FLV file, TAG=%s\n",TAG);
    return FLV_ERROR;
}

FLV_ERROR_TYPE flvParser::ParseflvFile()
{
    FLV_ERROR_TYPE ret;

    if(!mfile)
        return FLV_ERROR;

    mfile->file_hdr_position = 0;

    ret = flv_parse_header();
    if(ret!=FLV_OK)
        return ret;

    mfile->meta_tag_position = mfile->cur_file_offset;
    FLV_DEBUG_LOGD("flv_parse_header done: cur_file_offset = 0x%llx", (unsigned long long)mfile->cur_file_offset);

    ret = flv_parse_script();
    if(ret!=FLV_OK)
        return ret;

    mfile->data_tag_position = mfile->cur_file_offset;
    FLV_DEBUG_LOGD("flv_parse_script done: cur_file_offset = 0x%llx", (unsigned long long)mfile->cur_file_offset);

    ret = flv_setup_seektable();
    if(ret!=FLV_OK)
        return ret;
    FLV_DEBUG_LOGD("flv_setup_seektable done: cur_file_offset = 0x%llx", (unsigned long long)mfile->cur_file_offset);

    return FLV_OK;
}


FLV_ERROR_TYPE flvParser::flv_setup_seektable()
{
    uint32_t max_point, min_point,i ,u4SamplingCnt = 1, u4EntryIndx = 0;
    if(mfile->mMeta->filepositions && mfile->mMeta->times){
        mfile->hasSeekTable = 1;
        mSeekTable->MaxEntries = mfile->mMeta->timescnt;
        mSeekTable->SetEntries = mSeekTable->MaxEntries;

        if(mSeekTable->MaxEntries > FLV_SEEK_ENTRY_MAX_ENTRIES){
            u4SamplingCnt = (uint32_t)(mSeekTable->MaxEntries/FLV_SEEK_ENTRY_MAX_ENTRIES) + 1;

            for(i=0;i<mSeekTable->MaxEntries;i+=u4SamplingCnt){
                mSeekTable->pEntry[u4EntryIndx].ulTime = mfile->mMeta->times[i]*1000;//s->ms
                mSeekTable->pEntry[u4EntryIndx++].ulOffset = mfile->mMeta->filepositions[i];
            }
            mSeekTable->MaxEntries = u4EntryIndx;
            mSeekTable->SetEntries = mSeekTable->MaxEntries;
        }else{
            for(i=0;i<mSeekTable->MaxEntries;i++){
                mSeekTable->pEntry[i].ulTime = mfile->mMeta->times[i]*1000;//s->ms
                mSeekTable->pEntry[i].ulOffset = mfile->mMeta->filepositions[i];
            }
        }
        FLV_DEBUG_LOGD("flv_setup_seektable 1: MaxEntries = %d (limit %d)",
                mSeekTable->MaxEntries, FLV_SEEK_ENTRY_MAX_ENTRIES);
        FLV_DEBUG_LOGD("flv_setup_seektable 1: TimeGranularity = %llu ms",
                (unsigned long long)mSeekTable->TimeGranularity);
        FLV_DEBUG_LOGD("flv_setup_seektable 1: SetEntries = %d", mSeekTable->SetEntries);

        return FLV_OK;
    }
    //update seek table info
    mfile->hasSeekTable = 0;
    mSeekTable->TimeGranularity = FLV_SEEK_MAX_TIME_GRANULARITY;//ms

    min_point = mfile->duration/FLV_SEEK_MAX_TIME_GRANULARITY;
    max_point = mfile->duration/FLV_SEEK_MIN_TIME_GRANULARITY;

    if(mfile->duration == 0){
       // min_point = FLV_SEEK_ENTRY_MAX_ENTRIES>>2;
        max_point = FLV_SEEK_ENTRY_MAX_ENTRIES;
    }

    if(FLV_SEEK_ENTRY_MAX_ENTRIES >= max_point){
        mSeekTable->MaxEntries = max_point;
        mSeekTable->TimeGranularity = FLV_SEEK_MIN_TIME_GRANULARITY;
    }else if((FLV_SEEK_ENTRY_MAX_ENTRIES < max_point)
             && (FLV_SEEK_ENTRY_MAX_ENTRIES > min_point)){
        mSeekTable->MaxEntries = FLV_SEEK_ENTRY_MAX_ENTRIES;
        mSeekTable->TimeGranularity = mfile->duration / FLV_SEEK_ENTRY_MAX_ENTRIES;
    }else if(FLV_SEEK_ENTRY_MAX_ENTRIES <= min_point){
        mSeekTable->MaxEntries = FLV_SEEK_ENTRY_MAX_ENTRIES;
        mSeekTable->TimeGranularity = mfile->duration / FLV_SEEK_ENTRY_MAX_ENTRIES;
    }

    mSeekTable->SetEntries = 0;
    mSeekTable->RangeTime = 0;
    mSeekTable->LastTime =0;

    FLV_DEBUG_LOGD("flv_setup_seektable 2: seek MaxEntries=%d\n",mSeekTable->MaxEntries);
    FLV_DEBUG_LOGD("flv_setup_seektable 2: seek TimeGranularity = %llu ms",
            (unsigned long long)mSeekTable->TimeGranularity);
    FLV_DEBUG_LOGD("flv_setup_seektable 2: seek SetEntries=%d \n",mSeekTable->SetEntries);

    return FLV_OK;
}

FLV_ERROR_TYPE flvParser::flv_parse_script()
{
    flv_tag_str* pMeta=NULL;
    FLV_ERROR_TYPE ret;
    int32_t tmp;

    pMeta = (flv_tag_str *)calloc(1, sizeof(flv_tag_str)); //malloc + set0
    if(!pMeta){
        FLV_DEBUG_LOGE("flv_parse_script: error1,calloc failed \n");
        return FLV_ERR_NO_MEMORY;
    }
    FLV_DEBUG_LOGD("[memory]flv_parse_script: Alloc 0x%p \n",pMeta);

    while(1){
        ret = flv_read_tag_header(&(pMeta->tag_header)) ;//FLV_TAG_HEADER_SIZE
        if(ret != FLV_OK){
            FLV_DEBUG_LOGE("flv_parse_script: error2\n");
            ret = FLV_FILE_READ_ERR;
            break;
        }
        if(pMeta->tag_header.tag_type!=FLV_TAG_TYPE_META){
            ALOGE("this not a script, tag_type is %d, cur_file_offset = 0x%llx",
                    pMeta->tag_header.tag_type, (unsigned long long)mfile->cur_file_offset);
            mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset,FLV_SEEK_FROM_SET);
            ret = FLV_OK;
            break;
        }

        //FLV_TAG_TYPE_META
        if(mfile->hasMeta ==0){
            pMeta->tag_data = (uint8_t*)malloc(pMeta->tag_header.tag_data_size);
            if(!pMeta->tag_data){
                FLV_DEBUG_LOGE("flv_parse_script: error3,calloc failed \n");
                return FLV_ERR_NO_MEMORY;
            }
            FLV_DEBUG_LOGD("[memory]flv_parse_script: Alloc tag_data 0x%p\n",pMeta->tag_data);

            tmp = flv_byteio_read(pMeta->tag_data,pMeta->tag_header.tag_data_size, &mfile->mIoStream); //TAG_DATA size
            if ((int64_t)tmp < pMeta->tag_header.tag_data_size) {
                ret = FLV_FILE_READ_ERR;
                FLV_DEBUG_LOGE("flv_parse_script: error4,read failed \n");
                break;
            }

            ret = flv_parse_onMetaData(pMeta,mfile->mMeta);

            if(pMeta->tag_data){
                FLV_DEBUG_LOGD("[memory]flv_parse_script: Free 0x%p\n",pMeta->tag_data);
                free(pMeta->tag_data);
                pMeta->tag_data =NULL;
            }
        }else if(mfile->hasMeta ==1){
           mfile->mIoStream.seek(mfile->mIoStream.source, pMeta->tag_header.tag_data_size,FLV_SEEK_FROM_CUR);
           FLV_DEBUG_LOGD("flv_parse_script: has parserd Meta, not parser this script isze=%d\n",pMeta->tag_header.tag_data_size);
        }

        //verify tag size
        uint8_t data[FLV_TAG_PREV_SIZE];
        tmp = flv_byteio_read(data,FLV_TAG_PREV_SIZE, &mfile->mIoStream);

        if(tmp < FLV_TAG_PREV_SIZE){
            FLV_DEBUG_LOGE("flv_parse_script(prev_tag): error read file,tmp=%d\n",tmp);
            return FLV_FILE_READ_ERR;
        }
        pMeta->tag_header.prv_tag_size = flv_byteio_get_4byte(data);
        if(pMeta->tag_header.prv_tag_size != pMeta->tag_header.tag_data_size + FLV_TAG_HEADER_SIZE){
            FLV_DEBUG_LOGE("flv_parse_script tag size err, file offset = 0x%llx",
                    (unsigned long long)mfile->cur_file_offset);
        }
        //-- verify tag size end
        mfile->cur_file_offset = mfile->cur_file_offset + pMeta->tag_header.tag_data_size
                                + FLV_TAG_HEADER_SIZE + FLV_TAG_PREV_SIZE;
    }

    if(pMeta->tag_data){
        FLV_DEBUG_LOGD("[memory]flv_parse_script: Free 0x%p\n",pMeta->tag_data);
        free(pMeta->tag_data);
        pMeta->tag_data =NULL;
    }
    if(pMeta){
        FLV_DEBUG_LOGD("[memory]flv_parse_script: Free 0x%p\n",pMeta);
        free(pMeta);
        pMeta = NULL;
    }

    if(ret != FLV_OK){
        FLV_DEBUG_LOGE("flv_parse_script: error5\n");
        return FLV_ERROR;
    }
    mfile->duration =(uint64_t) mfile->mMeta->duration;   //ms
    mfile->file_size = (uint64_t)mfile->mMeta->file_size;

    FLV_DEBUG_LOGD("flv_parse_script: mfile->duration = %llu, mfile->file_size = %llu",
            (unsigned long long)mfile->duration, (unsigned long long)mfile->file_size);

    return FLV_OK;
}

FLV_ERROR_TYPE flvParser::flv_parse_amf_obj(
        uint8_t* amf_data, uint32_t amf_data_len, uint32_t* offset, char* key,
        flv_meta_str* metaInfo, uint32_t depth) {
    FLV_AMF_V0_TYPE amf_type;

    char strVal[256];
    uint32_t strsize=0;
    double numVal;
    uint16_t date_offset=0;
    uint32_t array_size=0;
    numVal = 0.0;
    amf_type = (FLV_AMF_V0_TYPE)flv_byteio_get_byte(amf_data+(*offset));
    *offset = *offset+1;

    ALOGV("FLV_AMF: amf_type %d, depth %u, key %s, *offset %u, amf_data_len %u",
            amf_type, depth, key, *offset, amf_data_len);
    switch (amf_type) {
        case FLV_AMF_V0_TYPE_NUMBER:
        {
            // key = filepostions && depth == 3
            // key = times && depth == 3
            numVal = flv_amf_number2double(flv_byteio_get_8byte(amf_data + (*offset)));
            (*offset) = (*offset) + 8;
            break;
        }
        case FLV_AMF_V0_TYPE_BOOL:
        {
            numVal = flv_byteio_get_byte(amf_data+(*offset));
            (*offset) = (*offset)+1;
            break;
        }
        case FLV_AMF_V0_TYPE_STRING:
        {
            strsize = flv_byteio_get_2byte(amf_data+(*offset));
            (*offset) = (*offset)+2;
            flv_byteio_get_string((uint8_t*)strVal, strsize+1,amf_data+(*offset));
            (*offset) = (*offset)+strsize;
            break;
        }
        case FLV_AMF_V0_TYPE_OBJECT:
        {
            //depth == 1, key =keyframes
            uint32_t end;
            //if(depth==1 && key && (FLV_AMF_V0_TYPE_OBJECT == amf_type && (0 == strncmp(key, "keyframes", 9))))
            if(depth==1 && key && (FLV_AMF_V0_TYPE_OBJECT == amf_type && (0 == strcmp(key, "keyframes"))))
                FLV_DEBUG_LOGD("AMF: file has seek table info\n");

            while(*offset <  amf_data_len){
              FLV_DEBUG_LOGD("FLV_AMF: IN OBJECT: key =%s,*offset=%d\n",strVal,*offset);
              strsize = flv_byteio_get_2byte(amf_data+(*offset));
              (*offset) = (*offset)+2;
              if(strsize == 0){
                 end = flv_byteio_get_byte(amf_data+(*offset));
                 (*offset) = (*offset)+1;
                 if(end == 9) {
                     break;
                 }
              }
              flv_byteio_get_string((uint8_t*)strVal, strsize+1,amf_data+(*offset));
              (*offset) = (*offset)+strsize;
              flv_parse_amf_obj(amf_data,amf_data_len,offset,strVal,metaInfo,depth+1);
            }
            break;
        }
        case FLV_AMF_V0_TYPE_MOVIECLIP:
        case FLV_AMF_V0_TYPE_NULL:
        case FLV_AMF_V0_TYPE_UNDEFINED:
        case FLV_AMF_V0_TYPE_UNSUPPORTED:
        {
            break;
        }//not handle
        case FLV_AMF_V0_TYPE_LONG_STRING:
        {
            strsize = flv_byteio_get_4byte(amf_data+(*offset));
            (*offset) = (*offset)+4;
            flv_byteio_get_string((uint8_t*)strVal, strsize+1,amf_data+(*offset));
            (*offset) = (*offset)+strsize;
            break;
        }
        case FLV_AMF_V0_TYPE_DATE:
        {
            numVal = flv_amf_number2double(flv_byteio_get_8byte(amf_data+(*offset)));
            (*offset) = (*offset)+8;
            date_offset = flv_byteio_get_2byte(amf_data+(*offset));
            (*offset) = (*offset)+2;
            break;
        }
        case FLV_AMF_V0_TYPE_MIXED_ARRAY:
        {
            // depth == 0
            uint32_t end;
            array_size = flv_byteio_get_4byte(amf_data + (*offset));
            (*offset) = (*offset) + 4;  // array_size
            while (*offset < amf_data_len) {
                strsize = flv_byteio_get_2byte(amf_data + (*offset));
                (*offset) = (*offset) + 2;
                if (strsize == 0) {
                    end = flv_byteio_get_byte(amf_data + (*offset));
                    (*offset) = (*offset) + 1;
                    if (end == 9) {
                        break;
                    }
                }
                flv_byteio_get_string((uint8_t*)strVal, strsize+1,amf_data+(*offset));//add '\0'
                (*offset) = (*offset)+strsize;

                flv_parse_amf_obj(amf_data,amf_data_len,offset,strVal,metaInfo,depth+1);    //depth ==1
            }
            break;
        }

        case FLV_AMF_V0_TYPE_ARRAY:
        {
            //key= filepostion && depth ==2
            uint32_t i;
            array_size = flv_byteio_get_4byte(amf_data+(*offset));
            (*offset) = (*offset)+4;
            FLV_DEBUG_LOGD("FLV_AMF: strict array size =%d\n",array_size);

            if(depth==2 && key
               && (0 == strcmp(key, "filepositions"))
               && FLV_AMF_V0_TYPE_ARRAY == amf_type){
                metaInfo->fileposcnt = array_size;
                FLV_DEBUG_LOGD("AMF: file has seek filepositions %llu", (unsigned long long)metaInfo->fileposcnt);
                metaInfo->filepositions = (uint64_t*)calloc(metaInfo->fileposcnt,sizeof(uint64_t));
                FLV_DEBUG_LOGD("[memory]AMF: alloc mem 0x%p\n",metaInfo->filepositions );
            }else if(depth==2 && key  && (0 == strcmp(key, "times"))
                     && FLV_AMF_V0_TYPE_ARRAY == amf_type){
                metaInfo->timescnt = array_size;
                FLV_DEBUG_LOGD("AMF: file has seek times %llu", (unsigned long long)metaInfo->timescnt);
                metaInfo->times = (uint64_t*)calloc(metaInfo->timescnt,sizeof(uint64_t));
                FLV_DEBUG_LOGD("[memory]AMF: alloc mem 0x%p\n",metaInfo->times );//ms
            }

            FLV_ERROR_TYPE ret;
            for (i = 0; i < array_size; i++) {
                ret = flv_parse_amf_obj(amf_data, amf_data_len, offset, key, metaInfo, depth + 1);
                if (ret != FLV_OK) return ret;
            }
            break;
        }
        default: //unsupported type, we couldn't skip
            return FLV_ERROR;
    }

    if (depth == 1 && key && (FLV_AMF_V0_TYPE_NUMBER == amf_type || FLV_AMF_V0_TYPE_BOOL == amf_type)) {
        if (0 == strcmp(key, "duration")) {
           metaInfo->duration = numVal*1000;//s->ms
        } else if(0 == strcmp(key, "width")){
           metaInfo->width = numVal;
        } else if(0 == strcmp(key, "height")){
           metaInfo->height = numVal;
        } else if(0 == strcmp(key, "videodatarate")){
           metaInfo->video_data_rate = numVal;
        } else if(0 == strcmp(key, "framerate")){
           metaInfo->frame_rate = numVal;
        } else if(0 == strcmp(key, "videocodecid")){
           metaInfo->video_codec_id = numVal;
        } else if(0 == strcmp(key, "audiosamplerate")){
           metaInfo->audio_sample_rate = numVal;
        } else if(0 == strcmp(key, "audiosamplesize")){
           metaInfo->audio_sample_size = numVal;
        } else if(0 == strcmp(key, "stereo")){
           metaInfo->stereo = numVal;
        } else if(0 == strcmp(key, "audiocodecid")){
           metaInfo->audio_codec_id = numVal;
        } else if(0 == strcmp(key, "filesize")){
           metaInfo->file_size = numVal;
        } else if(0 == strcmp(key, "lasttimestamp")){
           metaInfo->last_time_ts = numVal;
        } else if(0 == strcmp(key, "lastkeyframetimestamp")){
           metaInfo->last_keyframe_ts = numVal;
        } else if(0 == strcmp(key, "audiodelay")){
           metaInfo->audio_delay = numVal;
        } else if(0 == strcmp(key, "canSeekToEnd")){
           metaInfo->can_seek_to_end = (bool)numVal;
        } else if(0 == strcmp(key, "audiodatarate")){
           metaInfo->audio_data_rate = numVal;
        }
    } else if(depth==3 && key  && (0 == strcmp(key, "filepositions")) && FLV_AMF_V0_TYPE_NUMBER == amf_type){
         metaInfo->filepositions[metaInfo->fileposidx++] = (uint64_t)numVal;
    } else if (depth==3 && key  && (0 == strcmp(key, "times")) && FLV_AMF_V0_TYPE_NUMBER == amf_type){
         metaInfo->times[metaInfo->timesidx++] = (uint64_t)numVal;  //s
    }
    return FLV_OK;
}

FLV_ERROR_TYPE flvParser::flv_parse_onMetaData(flv_tag_str* pMeta_tag,flv_meta_str* metaInfo)
{
    FLV_AMF_V0_TYPE type;
    uint32_t offset=0;
    char buffer[11]; //"onMetaData".
    FLV_ERROR_TYPE ret;

    type = (FLV_AMF_V0_TYPE)flv_byteio_get_byte(pMeta_tag->tag_data);
    offset = offset+1;

    offset =offset +2 ; //string size

    flv_byteio_get_string((uint8_t*)buffer, sizeof(buffer),pMeta_tag->tag_data+ offset);

    if(type != FLV_AMF_V0_TYPE_STRING || 0!=strncmp(buffer, "onMetaData",10)){
        FLV_DEBUG_LOGE("flv_parse_meta_amf: error1 type=%d,%s\n",type,buffer);
        return FLV_ERROR;
    }
    offset = offset + 10;

    //parse the second object (we want a mixed array)
    ret = flv_parse_amf_obj(pMeta_tag->tag_data, pMeta_tag->tag_header.tag_data_size, &offset, NULL, metaInfo, 0);
    if(ret == FLV_OK){
        mfile->hasMeta = 1;
        FLV_DEBUG_LOGV("flv_parse_onMetaData:metaInfo:audio_codec_id=%f,video_codec_id=%f\n",metaInfo->audio_codec_id,metaInfo->video_codec_id);
        FLV_DEBUG_LOGV("flv_parse_onMetaData:metaInfo:duration=%f,file_size=%f\n",metaInfo->duration,metaInfo->file_size);
        FLV_DEBUG_LOGV("flv_parse_onMetaData:metaInfo:width=%f,height=%f\n",metaInfo->width,metaInfo->height);
        FLV_DEBUG_LOGV("flv_parse_onMetaData:metaInfo:frame_rate=%f,can_seek_to_end=%d\n",metaInfo->frame_rate,metaInfo->can_seek_to_end);
    }
    return ret;
}


FLV_ERROR_TYPE flvParser::flv_read_tag_header(flv_tag_header_info* tag_header) {
    uint8_t data[FLV_TAG_HEADER_SIZE];
    uint32_t read_size = FLV_TAG_HEADER_SIZE;
    int32_t tmp;

    tmp = flv_byteio_read(data,read_size, &mfile->mIoStream);

    if (tmp < (int32_t)read_size) {
        ALOGE("Error read file, tmp = %d", tmp);
        return FLV_FILE_READ_ERR;
    }

    //tag_header->prv_tag_size = flv_byteio_get_4byte(data);
    tag_header->tag_type = flv_byteio_get_byte(data);
    tag_header->tag_data_size = flv_byteio_get_3byte(data+1);
    tag_header->tag_ts = flv_byteio_get_3byte(data+4);
    tag_header->tag_ts = flv_byteio_get_byte(data+7)<<24 | tag_header->tag_ts;
    tag_header->streamId = flv_byteio_get_3byte(data+8);

    ALOGV("prv_tag_size = %u, tag_type = %u, tag_data_size = %u, tag_ts = %llu",
            tag_header->prv_tag_size, tag_header->tag_type,
            tag_header->tag_data_size, (unsigned long long)tag_header->tag_ts);
    return FLV_OK;
}

FLV_ERROR_TYPE flvParser::flv_parse_header()
{
    uint8_t data[FLV_FILE_DEADER_SIZE]; //9
    uint32_t read_size = FLV_FILE_DEADER_SIZE;
    int32_t tmp;
    tmp = flv_byteio_read(data,read_size,&mfile->mIoStream);
    if (tmp < (int32_t)read_size) {
        FLV_DEBUG_LOGE("flv_parse_header: error read file,tmp=%d\n",tmp);
        return FLV_FILE_READ_ERR;
    }

    mfile->version  = flv_byteio_get_byte(data+3) & 0x000000FF;
    mfile->hasVideo = flv_byteio_get_byte(data+4) & FLV_HAS_VIDEO_BITMASK;
    mfile->hasAudio =( flv_byteio_get_byte(data+4) & FLV_HAS_AUDIO_BITMASK )>> 2;
    mfile->header_size = flv_byteio_get_4byte(data+5);

    // read first prev_tag_size, should be 0 and useless
    tmp = flv_byteio_read(data,FLV_TAG_PREV_SIZE, &mfile->mIoStream);
    if(tmp < FLV_TAG_PREV_SIZE){
        FLV_DEBUG_LOGE("flv_parse_header(prev tag): error read file,tmp=%d\n",tmp);
        return FLV_FILE_READ_ERR;
    }

    mfile->cur_file_offset = mfile->header_size + FLV_TAG_PREV_SIZE;

    FLV_DEBUG_LOGD("flv_parse_header: version=%d,hasVideo=%d, hasAudio=%d,header size =%d\n",
                            mfile->version, mfile->hasVideo,mfile->hasAudio,mfile->header_size);

    tmp = mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset,FLV_SEEK_FROM_SET);

    return FLV_OK;
}

flv_tag_str* flvParser::flv_tag_create() {
    flv_tag_str* tag;
    uint8_t* ptr=NULL;

    tag = (flv_tag_str*)calloc(1, sizeof(flv_tag_str));
    if (!tag) {
        FLV_DEBUG_LOGE("flv_tag_create: error1, alloc mem fail");
        return NULL;
    }
    FLV_DEBUG_LOGM("[memory] alloc 0x%p", tag);

    tag->tag_data = NULL;
    ptr = (uint8_t*)calloc(1, FLV_BS_BUFFER_SIZE);  // 1000K
    if (!ptr) {
        FLV_DEBUG_LOGE("flv_tag_create: error2, alloc mem fail");
        free(tag);
        return NULL;
    }
    FLV_DEBUG_LOGM("[memory] alloc 0x%p", ptr);
    tag->tag_data = ptr;

    return tag;
}

void flvParser::flv_tag_destroy(flv_tag_str *tag)
{
    if (tag){
        if(tag->tag_data){
            FLV_DEBUG_LOGM("[memory] Free 0x%p\n",tag->tag_data);
            free(tag->tag_data);
            tag->tag_data =NULL;
        }
        FLV_DEBUG_LOGM("[memory] Free 0x%p\n",tag);
        free(tag);
        tag = NULL;
    }
}


FLV_ERROR_TYPE flvParser::flv_read_a_tag(flv_tag_str *tag) {
    FLV_ERROR_TYPE ret;
    uint8_t* ptr=NULL;
    ALOGV("+file offset = %llu", (unsigned long long)mfile->cur_file_offset);

    if (!tag) {
        ALOGE("Error: input == NULL");
        return FLV_ERROR;
    }

READ_TAG_HEADER:
    ret = flv_read_tag_header(&(tag->tag_header));
    if (ret != FLV_OK) {
        ALOGE("Error: read tag_header == NULL");
        return ret;
    }

    if (tag->tag_header.tag_data_size > FLV_BS_BUFFER_SIZE) {
        ptr = (uint8_t*)realloc(tag->tag_data,tag->tag_header.tag_data_size);
        if (!ptr) {
            ALOGE("Error: alloc mem fail");
            return FLV_ERROR;
        }
        FLV_DEBUG_LOGM("[memory] free 0x%p\n",tag->tag_data);
        FLV_DEBUG_LOGM("[memory] alloc 0x%p\n",ptr);
        tag->tag_data = ptr;
    }

    // READ DATA
    int32_t tmp;
    tmp = flv_byteio_read(tag->tag_data,tag->tag_header.tag_data_size, &mfile->mIoStream);
    if (tmp < 0) {
        ALOGE("Error read file: tmp = %d", tmp);
        return FLV_FILE_READ_ERR;
    }

    if (tmp < ((int)(tag->tag_header.tag_data_size))) {
        ALOGE("Error read file, tmp = %d", tmp);
        return FLV_FILE_READ_ERR;
    }

    // read prev tag size
    uint8_t data[FLV_TAG_PREV_SIZE];
    tmp = flv_byteio_read(data,FLV_TAG_PREV_SIZE, &mfile->mIoStream);
    if (tmp < FLV_TAG_PREV_SIZE) {
        ALOGE("prev_tag: error read file, tmp = %d", tmp);
        return FLV_FILE_READ_ERR;
    }

    // verify tag size
    tag->tag_header.prv_tag_size = flv_byteio_get_4byte(data);
    if (tag->tag_header.prv_tag_size == 0) {
        ALOGE("size is full of zero, need stop");
        return FLV_FILE_EOF;
    }
    if((tag->tag_header.prv_tag_size != tag->tag_header.tag_data_size + FLV_TAG_HEADER_SIZE)&&
        ((flv_get_videocodecid() == FLV_VIDEO_CODEC_ID_AVC)
         ||(flv_get_videocodecid() == FLV_VIDEO_CODEC_ID_HEVC)
         ||(flv_get_videocodecid() == FLV_VIDEO_CODEC_ID_HEVC_XL)
         ||(flv_get_videocodecid() == FLV_VIDEO_CODEC_ID_HEVC_PPS))){
        FLV_DEBUG_LOGE("flv tag size err, file offset = 0x%llx", (unsigned long long)mfile->cur_file_offset);

        // find next I frame
#ifdef FLV_DIRECT_SEEK_SUPPORT
        uint64_t u8FileOffset = 0, u8SearchOffsetAcc = 0;
        int64_t u8SearchOffset = 0;
        uint8_t *pu1DataBuf = (uint8_t*)malloc(FLV_BS_BUFFER_SIZE*sizeof(uint8_t));

        // update file offset
        u8FileOffset = mfile->cur_file_offset + FLV_TAG_HEADER_SIZE + FLV_TAG_PREV_SIZE + tag->tag_header.tag_data_size ;
        while(pu1DataBuf && mfile->hasVideo && u8SearchOffsetAcc<0x1400000){
            tmp = flv_byteio_read(pu1DataBuf, FLV_BS_BUFFER_SIZE, &mfile->mIoStream);
            if(tmp < 0)
            {
                FLV_DEBUG_LOGE("flv_read_a_tag(findNextI): read to file end\n");
                free(pu1DataBuf);
                pu1DataBuf = NULL;
                return FLV_FILE_READ_ERR;
            }
            u8SearchOffset = flv_search_tag_pattern(&pu1DataBuf, tmp);
            if (u8SearchOffset < 0) {
                return FLV_FILE_EOF;
            }
            if (u8SearchOffset < tmp){
                mfile->cur_file_offset = u8FileOffset + u8SearchOffsetAcc + u8SearchOffset + FLV_TAG_PREV_SIZE;
                mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
                free(pu1DataBuf);
                pu1DataBuf = NULL;
                goto READ_TAG_HEADER;
            }else if(tmp < FLV_BS_BUFFER_SIZE){
                FLV_DEBUG_LOGE("flv_read_a_tag(findNextI): read to file end\n");
                free(pu1DataBuf);
                pu1DataBuf = NULL;
                return FLV_FILE_READ_ERR;
            }else{
                u8SearchOffsetAcc += FLV_BS_BUFFER_SIZE;
            }
        }
        if(pu1DataBuf){
            free(pu1DataBuf);
            pu1DataBuf = NULL;
            //can't find I frame, recovery to original position
            mfile->mIoStream.seek(mfile->mIoStream.source, u8FileOffset, FLV_SEEK_FROM_SET);
        }else{
            FLV_DEBUG_LOGE("flv_read_a_tag(alloc fail)\n");
        }
#endif
    }
    //-- verify tag size end

    tag->tag_data_offset = 0;//put tag data from 0 offset of tag data buffer
    //update seek table
    flv_update_seek_table(tag);
    //update file offset
    mfile->cur_file_offset = mfile->cur_file_offset + FLV_TAG_HEADER_SIZE + FLV_TAG_PREV_SIZE + tag->tag_header.tag_data_size;
    ALOGV("-file offset = %llu", (unsigned long long)mfile->cur_file_offset);
    return FLV_OK;
}


void flvParser::flv_dump_seektable() {
    uint32_t i;
    flv_seek_table_entry* pEntry = (flv_seek_table_entry*) mSeekTable->pEntry;
    FLV_DEBUG_LOGD("---- flv_dump_seektable ---");
    for (i = 0; i < mSeekTable->SetEntries; i++)
        ALOGV("--- entry %d ts = %llu offset = %llu ---",
                i, (unsigned long long)pEntry[i].ulTime, (unsigned long long)pEntry[i].ulOffset);
}

int64_t flvParser::flv_seek_to_msec(int64_t msec)
{
    int64_t newTs;
    uint32_t i;
    FLV_DEBUG_LOGD("flv_seek_to_msec: seekto %lld ms", (long long)msec);

    if(msec<=0){
        mfile->cur_file_offset  = mfile->data_tag_position ;
        mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
#ifdef FLV_DIRECT_SEEK_SUPPORT
    bUpdateSeekTable = true;
#endif
        FLV_DEBUG_LOGD("flv_seek_to_msec: seek to 0, mfile->cur_file_offset = 0x%llx",
                (unsigned long long)mfile->cur_file_offset);
        return 0;
    }else if(mfile->hasSeekTable){
        FLV_DEBUG_LOGD("flv_seek_to_msec: hasSeekTable path");
        flv_seek_table_entry* pEntry=(flv_seek_table_entry*) mSeekTable->pEntry;
#ifdef FLV_DIRECT_SEEK_SUPPORT
        bUpdateSeekTable = true;
#endif
        // flv_dump_seektable();
        for(i =0;i< mSeekTable->MaxEntries;i++){
            FLV_DEBUG_LOGV("flv_seek_to_msec1: msec =%lld pEntry[%d].ulTime=%lld, pEntry[%d].ulTime=%lld \n",msec,i,pEntry[i].ulTime , i+1,pEntry[i+1].ulTime);
            if ((unsigned)msec >= pEntry[i].ulTime && (unsigned)msec < pEntry[i+1].ulTime)
                break;
        }

        if(i < mSeekTable->MaxEntries){
            newTs =  pEntry[i].ulTime;
            mfile->cur_file_offset = pEntry[i].ulOffset; //tag start offset
            mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
            FLV_DEBUG_LOGD("flv_seek_to_msec: return 1 %d newTs = %lld, offset = 0x%llx",
                    i, (long long)newTs, (unsigned long long)mfile->cur_file_offset);
            return newTs;
        }else{
            newTs =  pEntry[mSeekTable->MaxEntries-1].ulTime;
            mfile->cur_file_offset = pEntry[mSeekTable->MaxEntries-1].ulOffset; //tag start offset
            mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
            FLV_DEBUG_LOGD("flv_seek_to_msec: return 2 %d newTs = %lld, offset = 0x%llx",
                    i, (long long)newTs, (unsigned long long)mfile->cur_file_offset);
            return newTs;
        }
/* handle wrong seek offset
        if (i < mSeekTable->MaxEntries) {
            newTs = pEntry[i].ulTime;
            mfile->cur_file_offset = pEntry[i].ulOffset;
        } else {
            newTs = pEntry[mSeekTable->MaxEntries-1].ulTime;
            mfile->cur_file_offset = pEntry[mSeekTable->MaxEntries-1].ulOffset;
        }
        if (mfile->cur_file_offset < mfile->data_tag_position) {
            newTs = 0;
            mfile->cur_file_offset = mfile->data_tag_position;
        }
        mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
        ALOGV("return newTs = %llu, mfile->cur_file_offset = %llu, mfile->data_tag_position = %llu",
                (long long)newTs, (unsigned long long)mfile->cur_file_offset,
                (unsigned long long)mfile->data_tag_position);
        return newTs;
*/
    } else if (mfile->hasVideo) {  // no seektable, but has video
        ALOGD("No Seektable, but has video");
        flv_seek_table_entry* pEntry= (flv_seek_table_entry*)mSeekTable->pEntry;

        // 1. search from the exist table
        for (i = 0; i < mSeekTable->SetEntries; i++) {
            FLV_DEBUG_LOGV("flv_seek_to_msec2: msec =%lld pEntry[%d].ulTime=%lld, pEntry[%d].ulTime=%lld",
                    msec, i, pEntry[i].ulTime, i+1, pEntry[i+1].ulTime);
            if ((unsigned)msec >= pEntry[i].ulTime && (unsigned)msec < pEntry[i+1].ulTime) {
                // concern : the ts gap between 2 entry should > 1/fps
                break;
            }
        }
        if(i < mSeekTable->SetEntries){
#ifdef FLV_DIRECT_SEEK_SUPPORT
            bUpdateSeekTable = true;
#endif
            newTs = pEntry[i].ulTime;
            mfile->cur_file_offset = pEntry[i].ulOffset; //tag start offset
            mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
            FLV_DEBUG_LOGD("flv_seek_to_msec: return 3 %d newTs = %lld, offset = 0x%llx",
                    i, (long long)newTs, (unsigned long long)mfile->cur_file_offset);
            return newTs;
        }else{
            if(mSeekTable->SetEntries == mSeekTable->MaxEntries){
#ifdef FLV_DIRECT_SEEK_SUPPORT
                bUpdateSeekTable = true;
#endif
                newTs =  pEntry[mSeekTable->MaxEntries-1].ulTime;
                mfile->cur_file_offset = pEntry[mSeekTable->MaxEntries-1].ulOffset; //tag start offset
                mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
                FLV_DEBUG_LOGD("flv_seek_to_msec: return 4 %d newTs = %lld, offset = 0x%llx",
                        i, (long long)newTs, (unsigned long long)mfile->cur_file_offset);
                return newTs;
            } else {
                flv_tag_str  tag;
                FLV_ERROR_TYPE ret = FLV_ERROR;
                uint64_t ts = 0;
#ifdef FLV_DIRECT_SEEK_SUPPORT
                // jump to latest seek point first
                if((mSeekTable->SetEntries != 0)){
                    mfile->cur_file_offset = pEntry[mSeekTable->SetEntries-1].ulOffset; //tag start offset
                    mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
                    ts = pEntry[mSeekTable->SetEntries-1].ulTime;
                    ALOGI("jump to latest seek point first: offset = 0x%llx",
                            (unsigned long long)mfile->cur_file_offset);
                } else {
                    ts = 0;
                    mfile->cur_file_offset = mfile->data_tag_position;
                    mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
                }

                // use direct seek method(only support avc)
                FLV_VIDEO_CODEC_ID videoCodecId = flv_get_videocodecid();
                if ((msec - ts > FLV_DIRECT_SEEK_THD) && (videoCodecId == FLV_VIDEO_CODEC_ID_AVC)) {
                    ALOGI("use direct seek: time diff = %llu ms, videoCodecId = %d",
                            (unsigned long long)(msec - ts), videoCodecId);
                    bUpdateSeekTable = false;
                    ret = flv_direct_seek_to_msec(msec, ts, &newTs);
                    if (ret == FLV_OK) {
                        bUpdateSeekTable = false;
                        return newTs;
                    } else if (ret == FLV_FILE_EOF){
                        return -10;
                    }
                } else {
                   ALOGI("do not use direct seek: time diff = %lld ms, videoCodecId = %d",
                            (unsigned long long)(msec - ts), videoCodecId);
                }

                if (ret != FLV_OK) {
                    bUpdateSeekTable = true;
                    // jump to latest seek point first
                    if ((mSeekTable->SetEntries != 0)) {
                        mfile->cur_file_offset = pEntry[mSeekTable->SetEntries-1].ulOffset;
                        mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
                        ts = pEntry[mSeekTable->SetEntries-1].ulTime;
                        ALOGI("jump to latest seek point first: offset = 0x%llx",
                                (unsigned long long)mfile->cur_file_offset);
                    } else {
                        mfile->cur_file_offset = mfile->data_tag_position;
                        mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
                    }
#endif
                tag.tag_data = (uint8_t*)malloc(FLV_BS_BUFFER_SIZE*sizeof(uint8_t));
                FLV_DEBUG_LOGM("[memory]flv_seek_to_msec alloc 0x%p\n",ptr);
                while(1){
                    ret = flv_read_a_tag(&tag);
                    if (ret == FLV_FILE_EOF) {
                        FLV_DEBUG_LOGE("fatal error in bitstream, must eos");
                        if(tag.tag_data)
                        {
                            FLV_DEBUG_LOGM("[memory]flv_seek_to_msec free 0x%p\n",tag->tag_data);
                            free(tag.tag_data);
                            tag.tag_data=NULL;
                        }
                        return -10;
                    } else if(ret ==FLV_OK){
                            if (mSeekTable->SetEntries == 0) {
                                ALOGI("there is no entries in seekTable");
                                continue;
                            } else {
                                ts = pEntry[mSeekTable->SetEntries - 1].ulTime;
                                if (ts >= (unsigned)msec) {
                                    ALOGI("flv_seek_to_msec: find the tag: ts = %llu, entry = %d",
                                            (unsigned long long)ts, mSeekTable->SetEntries);
                                    break;
                                }
                            }
                    }else if(ret == FLV_FILE_READ_ERR){
                        FLV_DEBUG_LOGE("flv_seek_to_msec:EOS!!\n");
                        //flv_dump_seektable();
                        break;
                    }else if(ret == FLV_ERROR){
                        FLV_DEBUG_LOGE("flv_seek_to_msec:Error!!\n");
                        break;
                    }
                }
                    if (tag.tag_data) {
                        free(tag.tag_data);
                        tag.tag_data = NULL;
                    }
                //flv_dump_seektable();
                if (mSeekTable->SetEntries == 0 && ret != FLV_OK) {
                    newTs =  0;
                    mfile->cur_file_offset = mfile->data_tag_position;
                    mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
                    FLV_DEBUG_LOGE("flv_seek_to_msec: return 5: entry %d newTs = %lld, offset = 0x%llx",
                            i, (long long)newTs, (unsigned long long)mfile->cur_file_offset);
                } else {
                    //re-search table after setup more
                    for (i = 0; i < mSeekTable->SetEntries; i++) {
                        if ((unsigned)msec >= pEntry[i].ulTime && (unsigned)msec < pEntry[i+1].ulTime) {
                            // concern : the ts gap between 2 entry should > 1/fps
                            break;
                        }
                    }
                    if (i < mSeekTable->SetEntries) {
                        newTs = pEntry[i].ulTime;
                        mfile->cur_file_offset = pEntry[i].ulOffset; //tag start offset
                        mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
                        ALOGD("return newTs = %lld ms, offset = 0x%llx",
                                (long long)newTs, (unsigned long long)mfile->cur_file_offset);
                    } else {
                        newTs = pEntry[mSeekTable->SetEntries-1].ulTime;
                        mfile->cur_file_offset = pEntry[mSeekTable->SetEntries-1].ulOffset; //tag start offset
                        mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
                        ALOGD("return newTs = %lld ms, offset = 0x%llx",
                                (long long)newTs, (unsigned long long)mfile->cur_file_offset);
                    }
                }
                return newTs;
#ifdef FLV_DIRECT_SEEK_SUPPORT
                }
#endif
            }
        }
    }else{
        mfile->cur_file_offset  = mfile->data_tag_position ;
        mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
        FLV_DEBUG_LOGD("flv_seek_to_msec: set to 0, mfile->cur_file_offset = 0x%llx",
                (unsigned long long)mfile->cur_file_offset);
        return 0;
    }
    FLV_DEBUG_LOGD("flv_seek_to_msec: return 9 %d newTs = %lld, offset = %llu",
            i, (long long)newTs, (unsigned long long)mfile->cur_file_offset);
    return msec;
}

#ifdef FLV_DIRECT_SEEK_SUPPORT
FLV_ERROR_TYPE flvParser::flv_direct_seek_to_msec(int64_t Trgmsec, int64_t Currmsec, int64_t *Retmsec)
{
    uint32_t cnt = 0;
    int32_t read_size = 0;
    uint64_t file_offset = 0;
    uint64_t file_step = 0;
    uint64_t tag_ts = 0, ts_diff=0;
    int32_t buf_offset = 0;
    uint8_t* tag_data;
    uint64_t fwd_offset = 0, beh_offset = 0, beh_ts = 0;
    uint64_t seek_diff_thd;
    bool forward = true;
    bool find = false;

    if((mfile->duration == 0) || (mfile->file_size == 0)){
        ALOGE("duration or file_size is zero");
        return FLV_ERROR;
    }

    tag_data = (uint8_t*)malloc(FLV_BS_BUFFER_SIZE*sizeof(uint8_t));
    file_offset = mfile->cur_file_offset + ((Trgmsec - Currmsec - FLV_DIRECT_SEEK_THD)*(mfile->file_size)/mfile->duration);
    file_step = ((FLV_DIRECT_SEEK_THD*(mfile->file_size)/2)/mfile->duration);
    seek_diff_thd = mfile->duration/mSeekTable->MaxEntries;
    ALOGI("seek_diff_thd = %llu ms, file_step = 0x%llx",
            (unsigned long long)seek_diff_thd, (unsigned long long)file_step);

    while(1){
        mfile->mIoStream.seek(mfile->mIoStream.source, file_offset, FLV_SEEK_FROM_SET);
        // ALOGD("jump to 0x%llx/0x%llx/0x%llx", file_offset, beh_offset, fwd_offset);
        while (1) {
            read_size = flv_byteio_read(tag_data,FLV_BS_BUFFER_SIZE, &mfile->mIoStream);
            if (read_size < 0) {
                ALOGE("return FLV_FILE_READ_ERR");
                return FLV_FILE_READ_ERR;
            }
            buf_offset = flv_search_tag_pattern(&tag_data, read_size);
            if (buf_offset < 0) {
                ALOGD("flv_search_tag_pattern error detected, return FLV_FILE_EOF");
                return FLV_FILE_EOF;
            }
            if (buf_offset < read_size) {
                tag_ts = flv_byteio_get_3byte(tag_data+buf_offset+8);
                tag_ts = flv_byteio_get_byte(tag_data+buf_offset+11)<<24 | tag_ts;
                if (tag_ts > (unsigned)Trgmsec) {
                    ALOGD("find time forward = %llu ms", (unsigned long long)tag_ts);
                    forward = false;
                    ts_diff = tag_ts - Trgmsec;
                    if ((file_offset < fwd_offset) || (fwd_offset == 0))
                        fwd_offset = file_offset;
                } else {
                    ALOGD("find time behind = %llu ms", (unsigned long long)tag_ts);
                    ts_diff = Trgmsec - tag_ts;
                    if (ts_diff < seek_diff_thd)
                        find = true;

                    if ((file_offset + buf_offset) > beh_offset) {
                        beh_offset = file_offset + buf_offset;
                        beh_ts = tag_ts;
                    }
                    forward = true;
                }
                break;
            } else if (read_size < FLV_BS_BUFFER_SIZE) {
                ALOGD("read to file end");
                forward = false;
                ts_diff = mfile->duration - Trgmsec;
                if ((file_offset < fwd_offset) || (fwd_offset == 0))
                    fwd_offset = file_offset;
                break;
            } else {
                // ALOGV("no tag pattern in tag: file_offset = 0x%llx", file_offset);
                file_offset += FLV_BS_BUFFER_SIZE;
            }
        }
        if (find || (cnt >= SEARCH_THD)) {
            if (find) {
                mfile->cur_file_offset = beh_offset + FLV_TAG_PREV_SIZE;
                *Retmsec = beh_ts;
            }
            break;
        }
        if ((fwd_offset != 0) && (beh_offset != 0)){
            file_offset = (fwd_offset + beh_offset)/2;
        }else{
            if (forward){
                if ((file_offset+file_step) < mfile->file_size)
                    file_offset  += file_step;
                else
                   file_offset = (file_offset + mfile->file_size)/2;
            }else{
                if (file_offset > file_step)
                    file_offset -= file_step;
                else
                    file_offset = file_offset/2;
            }
        }
        cnt++;
    }
    if (tag_data) {
        free(tag_data);
        tag_data = NULL;
    }
    ALOGD("final offset = 0x%llx, cnt = %u, find = %d", (unsigned long long)mfile->cur_file_offset, cnt, find);
    mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
    if (mfile->file_size > (uint64_t)(3*1024*1024*1024LL) && !find && beh_offset != 0) {
        // for file_size > 3G, even if we fails to find the closest I frame to Trgmsec,
        // we will return the I frame of beh_offset, which is closer to Trgmsec.
        mfile->mIoStream.seek(mfile->mIoStream.source, beh_offset + FLV_TAG_PREV_SIZE, FLV_SEEK_FROM_SET);
        *Retmsec = beh_ts;
        find = true;
        ALOGI("file_size > 3G, return I frame which we found, but not accurate");
    }
    return find ? FLV_OK : FLV_ERROR;
}

int32_t flvParser::flv_search_tag_pattern(uint8_t **data, uint32_t size)
{
    uint8_t *start = *data;
    uint8_t zerocnt = 0;
    uint32_t offset = size, i=0;
    //char pattern0[8] = {0x00, 0x00, 0x00, 0x17, 0x00, 0x00, 0x00, 0x00};
    //char pattern1[8] = {0x00, 0x00, 0x00, 0x17, 0x01, 0x??, 0x??, 0x??};
    int32_t zeroNum = 0;
    int32_t j = 0;
    for (j = 0; j<10; j++) {
        if(start[j] == 0)
            zeroNum++;
        else
            break;
    }
    if (zeroNum > 8) {
        ALOGD("zeroNum(%d) > 8", zeroNum);
        return -10;
    }

    if (0 == size) {
        ALOGD("size = 0");
        return 0;
    }
    do {
        do {
            if (start[i] == 0x00) {
                zerocnt++;
            } else {
                zerocnt = 0;
            }
            i++;
        } while (((zerocnt < 3) || (start[i] == 0x00)) && (i < (size - 5)));

        if((start[i] == 0x17) &&
            (((start[i+1] == 0x00) && (start[i+2] == 0x00) && (start[i+3] == 0x00) && (start[i+4] == 0x00))||
            (start[i+1] == 0x01))){
            if((i >= 15) && ((start[i - 11]&0x1F) == 0x09)){
                offset = i - 15;
                break;
            }
        }
    }while(i < (size - 8));

    ALOGV("find offset = %u", offset);
    return offset;
}
#endif


uint32_t flvParser::flv_search_video_tag_pattern(uint8_t *data, uint32_t size) {
    uint8_t *start = data;
    uint8_t zerocnt = 0;
    uint32_t offset = 0, i = 0, tag_data_size = 0, prv_tag_size = 0;

    ALOGV("flv_search_video_tag_pattern: start %d", start[0]);
    if (0 == size) {
        ALOGD("flv_search_video_tag_pattern: size 0");
        return 0;
    }
    do {
        do {
            if (start[i] == 0x00) {
                zerocnt++;
            } else {
                zerocnt = 0;
            }
            i++;
        } while (((zerocnt < 3) || (start[i] == 0x00)) && (i < (size - 5)));

        if ((zerocnt >= 3) &&
                ((start[i] & 0xF0) >= 0x10) && ((start[i] & 0xF0) <= 0x50) &&  // Frame Type(top 4bits) should in [1, 5]
                ((start[i] & 0x0F) >= 0x02) && ((start[i] & 0x0F) <= 0x07) &&  // Codec ID(low 4bits) should in [2, 7]
                (((start[i] & 0x0F) != 0x07) ||  // not AVC
                    // AVC with sequence header
                    (((start[i+1] == 0x00) && (start[i+2] == 0x00) && (start[i+3] == 0x00) && (start[i+4] == 0x00)) ||
                    (start[i+1] == 0x01)))) {
            if ((i >= 15) && ((start[i - 11] & 0x1F) == 0x09)) {
                // check if DataSize header match prev_data_size
                tag_data_size = flv_byteio_get_3byte(&start[i - 10]);
                if ((i + tag_data_size + FLV_TAG_HEADER_SIZE) < size) {
                    prv_tag_size = flv_byteio_get_4byte(&start[i + tag_data_size]);
                    if (prv_tag_size == tag_data_size + FLV_TAG_HEADER_SIZE) {
                        offset = i;
                        break;
                    }
                }
            }
        }
    } while (i < (size - 8));

    ALOGV("flv_search_video_tag_pattern: find offset 0x%u", offset);
    return offset;
}

uint32_t flvParser::flv_search_audio_tag_pattern(uint8_t *data, uint32_t size) {
    uint8_t *start = data;
    uint8_t zerocnt = 0;
    uint32_t offset = 0, i = 0, tag_data_size = 0, prv_tag_size = 0;

    ALOGV("flv_search_audio_tag_pattern: start %d", start[0]);
    if (0 == size) {
        ALOGD("flv_search_audio_tag_pattern: size = 0");
        return 0;
    }
    do {
        do {
            if (start[i] == 0x00) {
                zerocnt++;
            } else {
                zerocnt = 0;
            }
            i++;
        } while (((zerocnt < 3) || (start[i] == 0x00)) && (i < (size - 5)));

        if ((zerocnt >= 3) &&
                ((start[i] & 0xF0) != 0x70) &&  // sound format
                ((start[i] & 0xF0) != 0x80) &&
                ((start[i] & 0xF0) != 0xe0) &&
                ((start[i] & 0xF0) != 0xf0)) {
            if ((i >= 15) && ((start[i - 11] & 0x1F) == 0x08)) {
                // check if DataSize header match prev_data_size
                tag_data_size = flv_byteio_get_3byte(&start[i-10]);
                if ((i + tag_data_size + FLV_TAG_HEADER_SIZE) < size) {
                    prv_tag_size = flv_byteio_get_4byte(&start[i + tag_data_size]);
                    if (prv_tag_size == tag_data_size + FLV_TAG_HEADER_SIZE) {
                        offset = i;
                        break;
                    }
                }
            }
        }
    } while (i < (size - 8));

    ALOGV("flv_search_audio_tag_pattern: find offset %u start[offset] %u", offset, start[offset]);
    return offset;
}

uint8_t flvParser::flv_get_stream_count()
{
       return 1;
}

bool flvParser::flv_is_seekable()
{
    if(!mfile){
        FLV_DEBUG_LOGE("flv_is_seekable: !mfile ,can not seek\n");
        return false;
    }else if(mfile->hasSeekTable){
        return true;
        FLV_DEBUG_LOGD("flv_is_seekable: hasSeekTable can seek\n");
    }else if(mfile->hasVideo){
        FLV_DEBUG_LOGD("flv_is_seekable: hasVideo tag ,can seek\n");
        return true;
    }else {
        FLV_DEBUG_LOGD("flv_is_seekable: !hasVideo && !hasSeekTabletag ,can not seek\n");
        return false;
    }
    return false;
}

uint64_t flvParser::flv_get_file_size()
{
    if(mfile)
         return (uint64_t)mfile->file_size;

    FLV_DEBUG_LOGD("flv_get_file_size: error return 0\n");
    return 0;
}

void flvParser::flv_set_file_size(uint64_t file_size) {
    uint64_t u8VideoDuration = 0, u8AudioDuration = 0;

    if (mfile && ((0 == mfile->file_size) || (file_size != mfile->file_size))) {
        if (file_size != mfile->file_size) {
            uint64_t file_offset = 0;
            uint8_t* tag_data;
            uint32_t buf_offset = 0, total_offset = 0;
            int32_t read_size = 0;
            ALOGD("file size mismatch: meta/real %llu/%llu",
                    (unsigned long long)mfile->file_size, (unsigned long long)file_size);
            tag_data = (uint8_t*)malloc(FLV_BS_BUFFER_SIZE*sizeof(uint8_t));
            file_offset = (file_size > FLV_BS_BUFFER_SIZE) ? (file_size - FLV_BS_BUFFER_SIZE) : mfile->cur_file_offset;

            // find the last video tag and calculate the u8VideoDuration
            if (mfile->hasVideo) {
                mfile->mIoStream.seek(mfile->mIoStream.source, file_offset, FLV_SEEK_FROM_SET);
                read_size = flv_byteio_read(tag_data, FLV_BS_BUFFER_SIZE, &mfile->mIoStream);
                if (read_size < 0) {
                    ALOGE("[Video]read size out of range");
                } else {
                    FLV_DEBUG_LOGD("flv_set_file_size: start = %d", tag_data[0]);
                    do {
                        buf_offset = flv_search_video_tag_pattern(&tag_data[total_offset], (read_size - total_offset));
                        total_offset += buf_offset;
                        ALOGV("flv_set_file_size: video total_offset %d", total_offset);
                    } while ((0 != buf_offset) && (total_offset < (unsigned)read_size));

                    if (total_offset >= 7) {
                        u8VideoDuration = flv_byteio_get_3byte(&tag_data[total_offset-7]);
                        u8VideoDuration |= (flv_byteio_get_byte(&tag_data[total_offset-4]) << 24);
                    }
                }
            }

            // find the last audio tag and calculate the u8AudioDuration
            if (mfile->hasAudio) {
                buf_offset = 0;
                total_offset = 0;
                mfile->mIoStream.seek(mfile->mIoStream.source, file_offset, FLV_SEEK_FROM_SET);
                read_size = flv_byteio_read(tag_data, FLV_BS_BUFFER_SIZE, &mfile->mIoStream);
                if (read_size < 0) {
                    ALOGE("[Audio]read size out of range");
                } else {
                    FLV_DEBUG_LOGD("flv_set_file_size: start = %d\n", tag_data[0]);
                    do {
                        buf_offset = flv_search_audio_tag_pattern(&tag_data[total_offset], (read_size-total_offset));
                        total_offset += buf_offset;
                        ALOGV("flv_set_file_size: audio total_offset = %d", total_offset);
                    } while ((0 != buf_offset) && (total_offset < (unsigned)read_size));

                    if (total_offset >= 7) {
                        u8AudioDuration = flv_byteio_get_3byte(&tag_data[total_offset-7]);
                        u8AudioDuration |= (flv_byteio_get_byte(&tag_data[total_offset-4]) << 24);
                    }
                }
            }

            // recorvery current position
            mfile->mIoStream.seek(mfile->mIoStream.source, mfile->cur_file_offset, FLV_SEEK_FROM_SET);
            // update duration
            if (u8VideoDuration > 0 || u8AudioDuration > 0) {
                ALOGD("oldDuration/newVideoDura/newAudioDura = %llu/%llu/%llu", (unsigned long long)mfile->duration,
                        (unsigned long long)u8VideoDuration, (unsigned long long)u8AudioDuration);
                mfile->duration = (u8VideoDuration > u8AudioDuration) ? u8VideoDuration : u8AudioDuration;
                flv_setup_seektable();
            }

            if (tag_data) {
                FLV_DEBUG_LOGM("[memory]flv_set_file_size free 0x%p", tag_data);
                free(tag_data);
                tag_data = NULL;
            }
        }
        mfile->file_size = file_size;
    }
    return;
}

uint64_t flvParser::flv_get_creation_date()
{
    return 1;
}

uint64_t flvParser::flv_get_duration()
{
    if(mfile)
        return mfile->duration;

    FLV_DEBUG_LOGD("flv_get_duration: error return 0");
    return 0;
}

uint32_t flvParser::flv_get_max_bitrate()
{
    return 1;
}


bool flvParser::flv_has_video()
{
    if(mfile)
        return mfile->hasVideo;

    FLV_DEBUG_LOGD("flv_has_video: error return false\n");
    return false;
}


bool flvParser::flv_has_audio()
{
    if(mfile)
        return mfile->hasAudio;

     FLV_DEBUG_LOGD("flv_has_audio: error return false\n");
    return false;
}


FLV_VIDEO_CODEC_ID flvParser::flv_get_videocodecid()
{
    if(mfile && mfile->mMeta)
        return (FLV_VIDEO_CODEC_ID)(uint32_t)(mfile->mMeta->video_codec_id);

    FLV_DEBUG_LOGD("flv_get_videocodecid: error return FLV_VIDEO_CODEC_ID_UNKHNOWN\n");
    return FLV_VIDEO_CODEC_ID_UNKHNOWN;
}


FLV_AUDIO_CODEC_ID flvParser::flv_get_audiocodecid()
{
    if(mfile && mfile->mMeta)
        return (FLV_AUDIO_CODEC_ID)(uint32_t)(mfile->mMeta->audio_codec_id);

    FLV_DEBUG_LOGD("flv_get_audiocodecid: error return FLV_AUDIO_CODEC_ID_UNKHNOWN\n");
    return FLV_AUDIO_CODEC_ID_UNKHNOWN;
}

void flvParser::flv_get_resolution(uint32_t* width,uint32_t* height)
{
    if(mfile && mfile->mMeta){
        *width  = (uint32_t)mfile->mMeta->width;
        *height = (uint32_t)mfile->mMeta->height;
    }else{
        FLV_DEBUG_LOGD("flv_get_resolution: error return 0\n");
        *width  = 0;
        *height = 0;
    }
}

flv_meta_str* flvParser::flv_get_meta()
{
    if(mfile && mfile->hasMeta){
        return mfile->mMeta;
    }else{
        FLV_DEBUG_LOGD("flv_get_meta: error return 0\n");
        return NULL;
    }
}

int64_t flvParser::flv_update_seek_table(flv_tag_str* cur_tag)
{
    flv_seek_table* table;
    flv_seek_table_entry* pEntry;
    uint8_t check_byte;
    uint64_t tag_ts;
    table = mSeekTable;

    if(mfile->hasSeekTable){
        FLV_DEBUG_LOGV("flv_update_seek_table: have ssektable already,not update\n");
        return 1;
    }

    if(mSeekTable->SetEntries == mSeekTable->MaxEntries){
        FLV_DEBUG_LOGV("flv_update_seek_table: don't update table as entries full\n");
        return -1;
    }

#ifdef FLV_DIRECT_SEEK_SUPPORT
    if (!bUpdateSeekTable){
        FLV_DEBUG_LOGV("flv_update_seek_table: don't update table because bUpdateSeekTable is false\n");
        return -1;
    }
#endif

    if(!cur_tag ||  cur_tag->tag_header.tag_type != FLV_TAG_TYPE_VIDEO){
        FLV_DEBUG_LOGV("flv_update_seek_table: don't update table,as not video tag\n");
        return -1;
    }

    check_byte = *(uint8_t*)(cur_tag->tag_data);
    tag_ts = cur_tag->tag_header.tag_ts;

    if((check_byte & FLV_VIDEO_FRAME_TYPE_BITMASK) != FLV_VIDEO_FRAME_TYPE_KEY){
        FLV_DEBUG_LOGV("flv_update_seek_table: don't update table,as not video key tag\n");
        return -1;
    }

    if(mSeekTable->SetEntries > 0 && tag_ts < (mSeekTable->RangeTime + mSeekTable->TimeGranularity)){
        FLV_DEBUG_LOGV("flv_update_seek_table:don't update table as cur ts=%lld < gap %lld\n",tag_ts,(mSeekTable->RangeTime + mSeekTable->TimeGranularity));
        return -1;
    }

    pEntry = &(mSeekTable->pEntry[mSeekTable->SetEntries]);
    pEntry->ulTime   = tag_ts;
    pEntry->ulOffset = mfile->cur_file_offset;  // as our read tag : 1st read lastsize
    mSeekTable->RangeTime = tag_ts ;
    FLV_DEBUG_LOGM("flv_update_seek_table: update %d entry,ts=%lld,Offset=0x%p\n",mSeekTable->SetEntries,pEntry->ulTime,pEntry->ulOffset);
    mSeekTable->SetEntries++;
    return tag_ts;
}

uint8_t  flv_byteio_get_byte(uint8_t  *data)
{
    if(data){
        return (*data);
    }else{
        ALOGE("flv_byteio_get_byte error\n");
        return FLV_ERROR;
    }
}
uint16_t flv_byteio_get_2byte(uint8_t  *data)
{
        uint16_t out;
        out = flv_byteio_get_byte(data) << 8;
        out |= flv_byteio_get_byte(data+1);
        return out;
}
uint32_t flv_byteio_get_3byte(uint8_t *data)
{
        uint32_t out = 0;
        out &=0x00000000;
        out = flv_byteio_get_2byte(data) << 8;
        out |= flv_byteio_get_byte(data+2);
        return out;
}
uint32_t flv_byteio_get_4byte(uint8_t *data)
{
        uint32_t out = 0;
        out &=0x00000000;
        out = flv_byteio_get_2byte(data) << 16;
        out |= flv_byteio_get_2byte(data+2);
        return out;
}

uint64_t flv_byteio_get_8byte(uint8_t *data) {
    uint64_t out = 0;
    out &=0x0000000000000000;
    out = flv_byteio_get_4byte(data);
    out= out << 32;
    out |= flv_byteio_get_4byte(data+4);
    return out;
}

void flv_byteio_get_string(uint8_t *string, uint32_t strlen, uint8_t *data) {
    uint i = 0;
    char c;
    if (strlen >= 256) {
        ALOGE("flv_byteio_get_string(): Error! (because strlen %u > 256)", strlen);
        return;
    }

    while ((c = flv_byteio_get_byte(data++))) {
        if (i < (strlen-1)) {
            string[i++] = c;
        }
    }

    string[i] = 0; /* Ensure null terminated, but may be truncated */
}

int32_t flv_byteio_read(uint8_t *Out,uint32_t size,flv_iostream_str *iostream)
{
    int32_t tmp;

    if (!iostream || !Out || !iostream->read || !iostream->source) {
            ALOGE("flv_byteio_read error\n");
            return FLV_ERROR;
    }

    tmp = iostream->read(iostream->source, Out, size);

    if ((int64_t)tmp != size) ALOGE("flv_byteio_read error: read %d, need read %d", tmp, size);
    return tmp;
}

double flv_amf_number2double(uint64_t number) {
    if ((number + number) > 0xFFEULL << 52) {
        return 0.0/0.0;
    }
    return ldexp(((number & ((1LL << 52) - 1)) + (1LL << 52)) * (number >> 63 | 1), (int)(number >> 52 & 0x7FF) - 1075);
}


namespace android {

//debug
//#define DUMP_DEBUG
#ifdef DUMP_DEBUG
     FILE* pfile=NULL;
#endif
     //debug
#ifdef MTK_DEMUXER_QUERY_CAPABILITY_FROM_DRV_SUPPORT
#include "vdec_drv_if.h"
#include "val_types.h"
#define QUERY_FROM_DRV
#endif

     //------------------------------------------------------------------

#define FLV_LOGE(x, ...) ALOGE("[ERROR]:" x, ##__VA_ARGS__)
#define FLV_LOGD(x, ...)  // ALOGD(" " x, ##__VA_ARGS__)
#define FLV_LOGM(x, ...)  // ALOGD(" "x, ##__VA_ARGS__)
#define FLV_LOGV(x, ...)  // ALOGV(" "x, ##__VA_ARGS__)

extern "C" bool mtk_flv_extractor_recognize(const sp<DataSource> &source) {
     FLV_LOGD("[FLV]mtk_flv_extractor_recognize IN\n");
     sp<FLVExtractor> extractor = new FLVExtractor(source);
     bool ret = extractor->bIsValidFlvFile;
     extractor = NULL;//deconstruct
     FLV_LOGE("[FLV]mtk_flv_extractor_recognize OUT\n");
     return ret;
}

struct FLVSource : public MediaSource {
    FLVSource(const sp<FLVExtractor> &extractor, size_t index);
    virtual status_t start(MetaData *params);
    virtual status_t stop();
    virtual sp<MetaData> getFormat();
    virtual status_t read(MediaBuffer **buffer, const ReadOptions *options);

 private:
    enum FLV_Type {
    VIDEO,
    AUDIO,
    OTHER
    };
    sp<FLVExtractor> mExtractor;
    size_t mTrackIndex;
    FLV_Type mType;
    FLVSource(const FLVSource &);
    FLVSource &operator=(const FLVSource &);
 };

uint32_t  flv_util_show_bits(uint8_t * data, uint32_t   bitcnt, uint32_t  num)
{
    uint32_t  tmp, out, tmp1;
    tmp = (bitcnt & 0x7) + num;
    if (tmp <= 8){
        out = (data[bitcnt >> 3] >> (8 - tmp)) & ((1 << num) - 1);
    }else{
        out = data[bitcnt >> 3]&((1 << (8 - (bitcnt & 0x7))) - 1);
        tmp -= 8;
        bitcnt += (8 - (bitcnt & 0x7));

        while (tmp > 8){
            out = (out << 8) + data[bitcnt >> 3];
            tmp -= 8;
            bitcnt += 8;
        }

        tmp1 = (data[bitcnt >> 3] >> (8 - tmp)) & ((1 << tmp) - 1);
        out = (out << tmp) + tmp1;
    }

    return out;
}

uint32_t  flv_util_get_bits(uint8_t * data, uint32_t  * bitcnt, uint32_t  num)
{
    uint32_t  ret;
    ret = flv_util_show_bits(data, *bitcnt, num);
    (*bitcnt) += num;

    return ret;
}

uint32_t  flv_util_show_word(uint8_t * a)
{
    return ((a[0] << 24) + (a[1] << 16) + (a[2] << 8) + a[3]);
}

#define FLV_START_CODE_0                     0x020       ///< short_video_start_marker, FLV version0 (22bit)
#define FLV_START_CODE_1                     0x021       ///< short_video_start_marker, FLV version1 (22bit)
#define I_VOP       0
#define P_VOP       1
#define B_VOP       2

bool isSupportedStream(FLV_Type stream_type, uint32_t codec_id)
{
    if(stream_type == VIDEO){
        switch (codec_id) {
            case FLV_VIDEO_CODEC_ID_AVC:
            {
                return true;
            }
            case FLV_VIDEO_CODEC_ID_HEVC:
            case FLV_VIDEO_CODEC_ID_HEVC_PPS:
            case FLV_VIDEO_CODEC_ID_HEVC_XL:
            {
                return true;
            }
            case FLV_VIDEO_CODEC_ID_SORENSON_SPARK:
            {
                return true;
            }
            case FLV_VIDEO_CODEC_ID_VP6:
            {
                return false;
            }
            default:
            {
                ALOGE("[FLV capability error]Unsupport video format! mStreamType = 0x%u", codec_id);
                return false;
            }
        }
    }

    return true;
}

void flv_parse_s263_info(uint8_t* data,uint32_t* width, uint32_t* height,uint32_t* key_frame)
{
    uint32_t  source_format, bitcnt = 0, tmp;
    if (flv_util_show_bits(data, bitcnt, 22) != FLV_START_CODE_0 &&
        flv_util_show_bits(data, bitcnt, 22) != FLV_START_CODE_1){
        FLV_LOGE("flv_parse_s263_info: FLV_START_CODE_0(0x020,0x021) not found!!");
        FLV_LOGE("[FLV Playback capability Error] capability not support as :Un-support S263 source_forma\n");
        return ;
    }
    bitcnt += 22;   // short_video_start_marker
    tmp = flv_util_get_bits(data, &bitcnt, 8);
    source_format = flv_util_get_bits(data, &bitcnt, 3);

    switch(source_format)
    {
        case 0:
        {
            *width = flv_util_get_bits(data, &bitcnt, 8);
            *height = flv_util_get_bits(data, &bitcnt, 8);
        }
        break;
        case 1:
        {
            *width = flv_util_get_bits(data, &bitcnt, 16);
            *height = flv_util_get_bits(data, &bitcnt, 16);
        }
        break;
        case 2:
        {
            *width = 352;
            *height = 288;
        }
        break;
        case 3:
        {
            *width = 176;
            *height = 144;
        }
        break;
        case 4:
        {
            *width = 128;
            *height = 96;
        }
        break;
        case 5:
        {
            *width = 320;
            *height = 240;
        }
        break;
        case 6:
        {
            *width = 160;
            *height = 120;
        }
        break;
        default:
        {
            FLV_LOGE("[source_format is not support!!");
            FLV_LOGE("[FLV Playback capability Error] capability not support as :Un-support S263 source_forma\n");
            return ;
        }
    }

    // set to 16 bytes alignment
    *width = (*width + 15) & 0xFFFFFFF0;
    // set to 16 bytes alignment
    *height = (*height + 15) & 0xFFFFFFF0;

    if (flv_util_show_bits(data, bitcnt, 2) == 0){
        *key_frame = I_VOP;
    }else if (flv_util_show_bits(data, bitcnt, 2) <= 2){
        *key_frame = P_VOP;
    }else{
        FLV_LOGE("FLV vop_coding_type is not support!!");
        return ;}
    /*
	bitcnt+=2;  // picture coding type

	bitcnt++;  // DeblockingFlag

	//hdr->vop_quant = flv_util_get_bits(data, &bitcnt, 5);
	tmp = flv_util_get_bits(data, &bitcnt, 5);
	
	do
	{
		source_format = flv_util_get_bits(data, &bitcnt, 1);
		if (source_format == 1)
		{
			bitcnt += 8;  
		}

	} while (source_format == 1);
	*/
}

void flv_parse_avc_sps(uint8_t* buffer ,uint32_t data_size,uint32_t* width, uint32_t* height)
{
    uint8_t *ptr = buffer;

    // uint8_t profile = ptr[1];
    // uint8_t level = ptr[3];

    size_t numSeqParameterSets = ptr[5] & 31;
    ptr += 6;

    off_t bytesRead = 6;
    status_t err = OK;
    int32_t maxwidth=0, maxheight=0;
    uint32_t maxprofile=0, maxlevel=0;
    for (uint32_t i=0; i < numSeqParameterSets; i++)
    {
        uint16_t size;
        uint8_t *sps;
        struct SPSInfo spsinfo;
        size = U16_AT((const uint8_t *)&ptr[0]);
        bytesRead += size;
        if (bytesRead > (off_t)data_size)
        {
            ALOGE("avcC SPS size error!!");
            err = ERROR_MALFORMED;
            break;
        }
        sps = ptr + 2;
        err = FindAVCSPSInfo(sps, size, &spsinfo);

        if (err != OK)
        {
            ALOGE("Parse SPS fail!!");
            break;
        }

        if (spsinfo.width > maxwidth)
            maxwidth = spsinfo.width;
        if (spsinfo.height > maxheight)
            maxheight = spsinfo.height;
        if (spsinfo.profile > maxprofile)
            maxprofile = spsinfo.profile;
        if (spsinfo.level > maxlevel)
            maxlevel = spsinfo.level;

        ptr += (size + 2);
    }
    *width = maxwidth;
    *height = maxheight;
}
static bool get_audio_tage_header_info(
        uint8_t header,
        int * /* out_format */,
        int *out_sampling_rate = NULL,
        int *out_sampling_size = NULL,
        int *out_channels = NULL)
{
        uint8_t tmp =0;
        *out_channels = (header & 0x01) + 1;  // 0 mono. 1 stereo

        tmp = (header & 0x02) >> 1;
        if(tmp == 0)
            *out_sampling_size =8;
        else if(tmp == 1)
            *out_sampling_size =16;

        tmp = (header & 0x0C) >> 2;
        switch(tmp)
        {
         case 0:
            *out_sampling_rate = 5500;
            break;
         case 1:
             *out_sampling_rate = 11000;
             break;
         case 2:
             *out_sampling_rate = 22000;
             break;
         case 3:
             *out_sampling_rate = 44000;
             break;
         default:
             *out_sampling_rate = 44000;
             ALOGE("get_audio_tage_header_info:error case tmp=%d",tmp);
             break;
        }
        return true;
 }



static bool get_mp3_frame_size(
        uint32_t header, uint32_t *frame_size,
        int *out_sampling_rate = NULL, int *out_channels = NULL,
        int *out_bitrate = NULL) {
    *frame_size = 0;

    if (out_sampling_rate)
        *out_sampling_rate = 0;

    if (out_channels)
        *out_channels = 0;

    if (out_bitrate)
        *out_bitrate = 0;

    if ((header & 0xffe00000) != 0xffe00000) {
        FLV_LOGD("get_mp3_frame_size error1\n");
        return false;
    }

    unsigned version = (header >> 19) & 3;
    if (version == 0x01){
        FLV_LOGD("get_mp3_frame_size error2\n");
        return false;
    }

    unsigned layer = (header >> 17) & 3;
    if (layer == 0x00){
        FLV_LOGD("get_mp3_frame_size error3\n");
        return false;
    }

    unsigned bitrate_index = (header >> 12) & 0x0f;

    if (bitrate_index == 0 || bitrate_index == 0x0f){
        // Disallow "free" bitrate.
        FLV_LOGD("get_mp3_frame_size error4\n");
        return false;
    }

    unsigned sampling_rate_index = (header >> 10) & 3;
    if (sampling_rate_index == 3){
        FLV_LOGD("get_mp3_frame_size error5\n");
        return false;
    }

    static const int kSamplingRateV1[] = { 44100, 48000, 32000 };
    int sampling_rate = kSamplingRateV1[sampling_rate_index];
    if (version == 2 /* V2 */) {
        sampling_rate /= 2;
    }else if(version == 0 /* V2.5 */) {
        sampling_rate /= 4;
    }

    unsigned padding = (header >> 9) & 1;

    if (layer == 3) {
        // layer I
        static const int kBitrateV1[] = {
            32, 64, 96, 128, 160, 192, 224, 256,
            288, 320, 352, 384, 416, 448
        };

        static const int kBitrateV2[] = {
            32, 48, 56, 64, 80, 96, 112, 128,
            144, 160, 176, 192, 224, 256
        };

        int bitrate =
            (version == 3 /* V1 */)
                ? kBitrateV1[bitrate_index - 1]
                : kBitrateV2[bitrate_index - 1];

        if(out_bitrate)
            *out_bitrate = bitrate;

        *frame_size = (12000 * bitrate / sampling_rate + padding) * 4;
    }else{
         // layer II or III
        static const int kBitrateV1L2[] = {
            32, 48, 56, 64, 80, 96, 112, 128,
            160, 192, 224, 256, 320, 384
        };

        static const int kBitrateV1L3[] = {
            32, 40, 48, 56, 64, 80, 96, 112,
            128, 160, 192, 224, 256, 320
        };

        static const int kBitrateV2[] = {
            8, 16, 24, 32, 40, 48, 56, 64,
            80, 96, 112, 128, 144, 160
        };

        int bitrate;
        if (version == 3 /* V1 */) {
            bitrate = (layer == 2 /* L2 */)
                ? kBitrateV1L2[bitrate_index - 1]
                : kBitrateV1L3[bitrate_index - 1];
        }else{
            // V2 (or 2.5)
            bitrate = kBitrateV2[bitrate_index - 1];
        }

        if (out_bitrate)
            *out_bitrate = bitrate;

        if(version == 3 /* V1 */){
             *frame_size = 144000 * bitrate / sampling_rate + padding;
        }else{
            // V2 or V2.5
            *frame_size = 72000 * bitrate / sampling_rate + padding;
        }
    }

    if(out_sampling_rate)
        *out_sampling_rate = sampling_rate;


    if(out_channels){
        int channel_mode = (header >> 6) & 3;
        *out_channels = (channel_mode == 3) ? 1 : 2;
    }

    return true;
}

uint32_t flv_io_read_func_ptr(void *pFlvExtractor, void *aBuffer, uint32_t aSize)
{
    FLVExtractor* _pExtractor = (FLVExtractor*)pFlvExtractor;

    ALOGV("iFlvParserReadOffset = %llu, aSize = %u", (unsigned long long)_pExtractor->iFlvParserReadOffset, aSize);
    if (_pExtractor) {
        int32_t bytesRead = _pExtractor->mDataSource->readAt(_pExtractor->iFlvParserReadOffset, aBuffer, aSize);
        _pExtractor->iFlvParserReadOffset += bytesRead;
        return bytesRead;
    }
    FLV_LOGE("flv_io_read_func_ptr:retrun 0\n");
    return 0;
}

uint32_t flv_io_write_func_ptr(void * /* pFlvExtractor */, void * /* aBuffer */, uint32_t /* aSize */)
{
     return 0;
}

uint64_t flv_io_seek_func_ptr(void *pFlvExtractor, uint64_t aOffset,FLV_SEEK_FLAG flag)
{
    FLVExtractor* _pExtractor = (FLVExtractor*)pFlvExtractor;

    switch(flag)
    {
        case FLV_SEEK_FROM_SET:
        {
            _pExtractor->iFlvParserReadOffset = aOffset;
            break;
        }
        case FLV_SEEK_FROM_CUR:
        {
            _pExtractor->iFlvParserReadOffset += aOffset;
            break;
        }
        case FLV_SEEK_FROM_END:
        {
            off64_t _source_size;
            _pExtractor->mDataSource->getSize(&_source_size);
            _pExtractor->iFlvParserReadOffset = _source_size-aOffset;
            break;
        }
     }
     FLV_LOGV("flv_io_seek_func_ptr: iFlvParserReadOffset=%lld\n",_pExtractor->iFlvParserReadOffset);
     return _pExtractor->iFlvParserReadOffset;
}

FLVSource::FLVSource(const sp<FLVExtractor> &extractor, size_t index)
    : mExtractor(extractor),
      mTrackIndex(index)
{
    const char *mime;
    CHECK(mExtractor->mTracks.itemAt(index).mMeta->findCString(kKeyMIMEType, &mime));

    FLV_LOGD("New FLVSource:mime=%s\n",mime);
    if((!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_AVC))
        || (!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_SORENSON_SPARK))) {
        mType = VIDEO;
        FLV_LOGD("New FLVSource:mType = VIDEO\n");
    }
    else if(!strcasecmp(mime, MEDIA_MIMETYPE_VIDEO_HEVC)){
       mType = VIDEO;
       FLV_LOGD("New FLVSource:mType = VIDEO\n");
    }
    else if((!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_AAC))
          || (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_MPEG))
          || (!strcasecmp(mime, MEDIA_MIMETYPE_AUDIO_RAW))){
        mType = AUDIO;
        FLV_LOGD("New FLVSource:mType = AUDIO\n");
    }else{
        mType = OTHER;
        FLV_LOGD("New FLVSource:mType = OTHER\n");
    }
}

status_t FLVSource::start(MetaData *params)
{
    int32_t val;
    if (mType==VIDEO && params && params->findInt32(kKeyWantsNALFragments, &val)
            && val != 0) {
        mExtractor->mWantsNALFragments = true;
    }
    ALOGI("mWantsNALFragment = %d", mExtractor->mWantsNALFragments);
    return OK;
}

status_t FLVSource::stop()
{
    return OK;
}

sp<MetaData> FLVSource::getFormat()
{
    return mExtractor->mTracks.itemAt(mTrackIndex).mMeta;
}

status_t FLVSource::read(MediaBuffer **out, const ReadOptions *options)
{
    android::Mutex::Autolock autoLock(mExtractor->mCacheLock);

    int64_t seekTimeUs,cur_ts,bJumpToKey;
    ReadOptions::SeekMode mode;
    FLV_ERROR_TYPE retVal=FLV_OK;
    bJumpToKey=false;

    FLV_LOGV("+FLVSource::read use mode, seek to %lld us (mType=%d, mode=%d)", seekTimeUs, mType, mode);

    if (options && options->getSeekTo(&seekTimeUs, &mode)) {
#if defined(ENABLE_PERF_JUMP_KEY_MECHANISM)
   if((mode == ReadOptions::SEEK_NEXT_SYNC)&&(mType==VIDEO)){
         bJumpToKey = true;
         seekTimeUs=-1;
         LOGE("###bJumpToKey true====seekTimeUs=-1===========)" );
       }else
#endif  //#if defined(ENABLE_PERF_JUMP_KEY_MECHANISM)
    {
        if(mType==VIDEO){
            if(mode == ReadOptions::SEEK_CLOSEST){
                mExtractor->mtargetSampleTimeUs = seekTimeUs;
            }else {
                mExtractor->mtargetSampleTimeUs = -1;
            }
        }else{
            mExtractor->mtargetSampleTimeUs = seekTimeUs;
        }

        if (mExtractor->bHasVideo) {
            if (VIDEO == mType) {
                mExtractor->ClearVideoFrameQueue();
                mExtractor->ClearAudioFrameQueue();

                if (false == mExtractor->FLVSeekTo((seekTimeUs/1000))) {
                    FLV_LOGE ("FLVSeekTo failed A");
                    return ERROR_END_OF_STREAM;
                }
            }
        }else{   // audio only
            mExtractor->ClearVideoFrameQueue();
            mExtractor->ClearAudioFrameQueue();
            if (false == mExtractor->FLVSeekTo((seekTimeUs/1000))) {
                FLV_LOGE ("FLVSeekTo failed B");
                return ERROR_END_OF_STREAM;
            }
        }
    }
         }
    if(AUDIO == mType) {
        FLV_LOGV ("FLVSource::read mAudioFrames.size()=%d", mExtractor->mAudioFrames.size());

        if(mExtractor->mAudioFrames.size() >= FLV_CACHE_POOL_HIGH) {     // dequeue
            *out = mExtractor->DequeueAudioFrame(mExtractor->mtargetSampleTimeUs);
            FLV_LOGV("FLVSource read AUDIO buffer1=0x%p\n",*out);
            return OK;
        }else if(mExtractor->mAudioFrames.size() >= FLV_CACHE_POOL_LOW) {
            retVal = mExtractor->CacheMore(CACHE_FRAME);
            if(FLV_FILE_EOF == retVal){
                ALOGE("AUDIO CacheMore EOS, BUT still queued %zu frames", mExtractor->mAudioFrames.size());
                return ERROR_END_OF_STREAM; //will lost several frames
            }else if (FLV_FILE_READ_ERR== retVal){
                FLV_LOGE("FLVSource read: AUDIO CacheMore FLV_FILE_READ_ERR\n");
                return ERROR_IO;
            }
            *out = mExtractor->DequeueAudioFrame(mExtractor->mtargetSampleTimeUs);
            FLV_LOGV("FLVSource read AUDIO buffer2=0x%p\n",*out);
            return OK;
        }else{
            retVal = mExtractor->CacheMore(CACHE_FRAME);
            if (FLV_FILE_EOF == retVal)
            {
                if(mExtractor->mAudioFrames.size() == 1){
                    *out = mExtractor->DequeueAudioFrame(mExtractor->mtargetSampleTimeUs);
                    FLV_LOGD("FLVSource read AUDIO buffer3=0x%p\n",*out);
                    return OK;
                }

                if(mExtractor->mAudioFrames.isEmpty()){
                     // send EOS
                     FLV_LOGE ("FLV_FILE_EOF (AUDIO)1");
                     return ERROR_END_OF_STREAM;
                }
            }else if(FLV_FILE_READ_ERR == retVal){
                return ERROR_IO;
            }else if (FLV_OK == retVal){     // cache samples for the first time
                while (mExtractor->mAudioFrames.size() < FLV_CACHE_POOL_HIGH) {
                    retVal = mExtractor->CacheMore(CACHE_FRAME);
                    if (FLV_OK != retVal)
                        break;
                }

                if (mExtractor->mAudioFrames.size() >= 1){
                    *out = mExtractor->DequeueAudioFrame(mExtractor->mtargetSampleTimeUs);
                    FLV_LOGV("FLVSource read AUDIO buffer4=0x%p\n",*out);
                    return OK;
                }

                if (mExtractor->mAudioFrames.isEmpty()){
                    // send EOS
                    FLV_LOGE ("FLV_FILE_EOF (AUDIO)2\n");
                    return ERROR_END_OF_STREAM;
                }
            }// initial
        }
    }
#if defined(ENABLE_PERF_JUMP_KEY_MECHANISM)
READ_AGAIN:
#endif
    if (VIDEO == mType) {
        ALOGV("mVideoFrames.size() = %zu", mExtractor->mVideoFrames.size());
    if (mExtractor->mVideoFrames.size() >= FLV_CACHE_POOL_HIGH) { // dequeue a video frame directly
            *out = mExtractor->DequeueVideoFrame(mExtractor->mtargetSampleTimeUs);
        (*out)->meta_data()->findInt64(kKeyTime, &cur_ts);
        FLV_LOGD("FLVSource read VIDEO buffer1 = 0x%p, ts = %lld", *out, (long long)(cur_ts/1000));
#if defined(ENABLE_PERF_JUMP_KEY_MECHANISM)
            if (bJumpToKey == true) {
                int32_t keyFrame;
        CHECK((*out)->meta_data()->findInt32(kKeyIsSyncFrame, &keyFrame));
        if (!keyFrame) {
            FLV_LOGV ("###FLVSource::read mVideoFrames bJumpToKey1 :this is not Key Frame=======\n");
            goto READ_AGAIN;
        }
        bJumpToKey=false;
        FLV_LOGD ("###FLVSource::read mVideoFrames bJumpToKey1: find KEY!!!");
        }
#endif
            return OK;
    } else if (mExtractor->mVideoFrames.size() >= FLV_CACHE_POOL_LOW) {
        //CacheMore a video frame and dequeue a video frame
        retVal = mExtractor->CacheMore(CACHE_FRAME);
        if (FLV_FILE_EOF == retVal) {
            FLV_LOGD("FLVSource read eos, but mVideoFrames still has queued %zu frames",
                    mExtractor->mVideoFrames.size());
        } else if (FLV_FILE_READ_ERR == retVal) {
            FLV_LOGE("FLVSource read:VIDEO CacheMore FLV_FILE_READ_ERR\n");
        return ERROR_IO;
        }
        *out = mExtractor->DequeueVideoFrame(mExtractor->mtargetSampleTimeUs);
        (*out)->meta_data()->findInt64(kKeyTime, &cur_ts);
        FLV_LOGV("FLVSource read VIDEO buffer2 = 0x%p, ts = %lld\n", *out, (cur_ts/1000));
#if defined(ENABLE_PERF_JUMP_KEY_MECHANISM)
            if (bJumpToKey == true) {
                int32_t keyFrame;
        CHECK((*out)->meta_data()->findInt32(kKeyIsSyncFrame, &keyFrame));
        if (!keyFrame) {
            FLV_LOGV ("###FLVSource::read mVideoFrames bJumpToKey2 :this is not Key Frame\n");
            goto READ_AGAIN;
        }
        bJumpToKey=false;
        FLV_LOGD ("###FLVSource::read mVideoFrames bJumpToKey2: find KEY!!!");
        }
#endif
        return OK;
    } else {
        retVal = mExtractor->CacheMore(CACHE_FRAME);
        if (FLV_FILE_EOF == retVal) {
        if (mExtractor->mVideoFrames.size() == 1) {
            *out = mExtractor->DequeueVideoFrame(mExtractor->mtargetSampleTimeUs);
            (*out)->meta_data()->findInt64(kKeyTime, &cur_ts);
            FLV_LOGV("FLVSource read VIDEO buffer3=0x%p,ts= %lld\n",*out,(cur_ts/1000));
            return OK;
        }
                if (mExtractor->mVideoFrames.isEmpty()) {
            // send EOS
            FLV_LOGD("FLV read video eos");
            return ERROR_END_OF_STREAM;
            }
        } else if (FLV_FILE_READ_ERR == retVal) {
        return ERROR_IO;
        } else if (FLV_OK == retVal) { // cache samples for the first time
        while (mExtractor->mVideoFrames.size() < FLV_CACHE_POOL_HIGH) {
            retVal = mExtractor->CacheMore(CACHE_FRAME);
            if (FLV_OK != retVal) break;
        }
        if (mExtractor->mVideoFrames.size() >= 1) {
            *out = mExtractor->DequeueVideoFrame(mExtractor->mtargetSampleTimeUs);
            (*out)->meta_data()->findInt64(kKeyTime, &cur_ts);
            FLV_LOGV("FLVSource read VIDEO buffer4=0x%p,ts= %lld\n",*out,(cur_ts/1000));
#if defined(ENABLE_PERF_JUMP_KEY_MECHANISM)
            if (bJumpToKey == true) {
            int32_t keyFrame;
            CHECK((*out)->meta_data()->findInt32(kKeyIsSyncFrame, &keyFrame));
            if (!keyFrame) {
                FLV_LOGV ("FLVSource::read mVideoFrames bJumpToKey3 :this is not Key Frame\n");
                goto READ_AGAIN;
            }
            bJumpToKey=false;
            FLV_LOGD ("###FLVSource::read mVideoFrames bJumpToKey3: find KEY!!!");
            }
#endif
            return OK;
        }

        if (mExtractor->mVideoFrames.isEmpty()) {
            FLV_LOGE("ERROR_END_OF_STREAM (VIDEO)2");
            return ERROR_END_OF_STREAM;
        }
        }// initial
    }
    }
    return retVal;
}

FLVExtractor::FLVExtractor(const sp<DataSource> &source)
     :mflvParser(NULL),
      mDataSource(source),
      iDataSourceLength(0),
      iFlvParserReadOffset(0),
      iDurationMs(0),
      bSeekable(false),
      isAVCCSend(false),
      bThumbnailMode(false),
      bHaveParsed(false),
      bHasVideo(false),
      vcodec_state(NO_VIDEO),
      bHasVideoTrack(false),
      bHasAudio(false),
      iWidth(0),
      iHeight(0),
      mTag(NULL),
      video_codec_id(FLV_VIDEO_CODEC_ID_UNKHNOWN),
      audio_codec_id(FLV_AUDIO_CODEC_ID_UNKHNOWN),
      mStatus(FLV_INIT),
      mtargetSampleTimeUs(-1),
      iChannel_cnt(0),
      iSampleRate(0),
      iSampleSize(0),
      iDecVideoFramesCnt(0),
      iDecAudioFramesCnt(0)
{
    FLV_LOGD ("+FLVExtractor 0x%p, tid=%d\n", this, gettid());
    bExtractedThumbnails = false;
    bIsValidFlvFile = false;
    mNALLengthSize = 4;
    mWantsNALFragments = false;
    mflvParser = new flvParser((void*)this, flv_io_read_func_ptr, flv_io_write_func_ptr, flv_io_seek_func_ptr);

    if(!mflvParser){
        FLV_LOGE ("mflvParser creation failed\n");
    }

    int retVal = FLV_ERROR;
    retVal = mflvParser->IsflvFile();//parse the file here
    if(FLV_OK == retVal){
        FLV_LOGD("This is an FLV file!!!\n");
        bIsValidFlvFile = true;
    }else{
        FLV_LOGE ("Not an FLV file!!!\n");
        bIsValidFlvFile = false;
    }
}

size_t FLVExtractor::countTracks()
{
    FLV_LOGD("countTracks:bHaveParsed=%d \n",bHaveParsed );
    if(false == bHaveParsed) {//noFLVal first parsed here
        ParseFLV();
    }
    FLV_LOGD ("countTracks return %zu", mTracks.size());
    return mTracks.size();
}
 sp<IMediaSource> FLVExtractor::getTrack(size_t index)
 {
     FLV_LOGD("getTrack:indx=%zu,mTracks.size()=%zu",index,mTracks.size());
     if (index >= mTracks.size())
     {
         return NULL;
     }
     return new FLVSource(this, index);
 }
 sp<MetaData> FLVExtractor::getTrackMetaData(size_t index, uint32_t flags)
 {
     FLV_LOGD ("getTrackMetaData:bHaveParsed=%d,index=%zu\n",bHaveParsed,index);
     if (index >= mTracks.size())
     {
         return NULL;
     }

     if ((flags & kIncludeExtensiveMetaData) && (false == bExtractedThumbnails))
     {
         findThumbnail();
         bExtractedThumbnails = true;
     }
     return mTracks.itemAt(index).mMeta;
 }
 sp<MetaData> FLVExtractor::getMetaData()
 {
     FLV_LOGD ("getMetaData()\n");
     if (false == bHaveParsed)
     {
         ParseFLV();
     }

     sp<MetaData> meta = new MetaData;

     meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_CONTAINER_FLV);

     if (countTracks() > 0) {
         if (bHasVideo || (vcodec_state == UNSUPPORT_VIDEO)) {
              meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_FLV);
            } else {
              meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_FLV);
            }
     }
     if(!bHasVideo && bHasVideoTrack)
     {
         meta->setInt32(kKeyHasUnsupportVideo, true);
         ALOGD("FLV has unsupport video track");
     }

     /* no one use kKeyVideoPreCheck on alps-trunk-o0.tk
     //ALPS00427466
     //h264 decoder need to set OMX_IndexVendorMtkOmxVdecSeekMode to stop waiting IDR
     //but omxcodec.cpp will not handle "mseeking" at first read() after play
     //so reference other extractor to enable precheck mechanism
     meta->setInt32(kKeyVideoPreCheck, 1);
      */
     return meta;
 }
 uint32_t FLVExtractor::flags() const
 {
     FLV_LOGD("flags: bSeekable=%d\n",bSeekable);
     if(bSeekable)
     {
         return CAN_SEEK_BACKWARD | CAN_SEEK_FORWARD | CAN_PAUSE | CAN_SEEK;
     }
     else
     {
         FLV_LOGD("[flags: can not seek,just can pasue\n");
         return CAN_PAUSE;
     }
 }

 FLVExtractor::~FLVExtractor()
 {
     FLV_LOGD ("~FLVExtractor 0x%p, tid=%d", this, gettid());

     if(mTag)
         mflvParser->flv_tag_destroy(mTag);
     if (mflvParser)
         delete mflvParser;

     ClearVideoFrameQueue();
     ClearAudioFrameQueue();
 }

 bool FLVExtractor::ParseFLV()
 {
     if(!mflvParser)
     {
         FLV_LOGE("mflvParser is null, can not parse\n");
     }
     if (mflvParser->ParseflvFile() != FLV_OK)
     {
         FLV_LOGE("ParseflvFile Failed");
     }
     mDataSource->getSize(&iDataSourceLength);

     mflvParser->flv_set_file_size(iDataSourceLength);

     iDurationMs = mflvParser->flv_get_duration();

     bSeekable= mflvParser->flv_is_seekable();

     bHasVideo = mflvParser->flv_has_video();

     bHasVideoTrack = true;
     bHasAudio = mflvParser->flv_has_audio();

     video_codec_id= mflvParser->flv_get_videocodecid();
     audio_codec_id= mflvParser->flv_get_audiocodecid();

     mflvParser->flv_get_resolution(&iWidth,&iHeight);

     flv_meta_str * metainfo;
     metainfo = mflvParser->flv_get_meta();
     if (!metainfo) {
         FLV_LOGE ("flv_get_meta failed \n");
         metainfo = NULL;
     }
#ifdef DUMP_DEBUG
     pfile = fopen("/sdcard/out.bin", "ab");
#endif

     ALOGD("--- meta info from onMetaData ---");
     ALOGD("bSeekable = %d, iDurationMs = %llu ms, iDataSourceLength = %llu",
            bSeekable, (unsigned long long)iDurationMs, (unsigned long long)iDataSourceLength);
     ALOGD("iWidth = %d, iHeight=%d", iWidth, iHeight);
     ALOGD("video_codec_id = %d, audio_codec_id = %d", video_codec_id, audio_codec_id);
     ALOGD("bHasVideo = %d, bHasAudio = %d", bHasVideo, bHasAudio);

     mTag = mflvParser->flv_tag_create();

     CacheMore(CACHE_ANYHOW);   //to get the codec id info and other info

     if(  audio_codec_id == FLV_AUDIO_CODEC_ID_ADPCM
       || audio_codec_id == FLV_AUDIO_CODEC_ID_NELLYMOSER_16KHZ_MONO
       || audio_codec_id == FLV_AUDIO_CODEC_ID_NELLYMOSER_8KHZ_MONO
       || audio_codec_id == FLV_AUDIO_CODEC_ID_NELLYMOSER
       || audio_codec_id == FLV_AUDIO_CODEC_ID_G711_ALAW    // reserved in 10.1 version
       || audio_codec_id == FLV_AUDIO_CODEC_ID_G711_MLAW   // reserved in 10.1 version
       || audio_codec_id == FLV_AUDIO_CODEC_ID_RESERVED
       || audio_codec_id == FLV_AUDIO_CODEC_ID_SPEEX
       || audio_codec_id == FLV_AUDIO_CODEC_ID_MP3_8K            // reserved in 10.1 version
       || audio_codec_id == FLV_AUDIO_CODEC_ID_DEVICE            // reserved in 10.1 version
       || audio_codec_id == FLV_AUDIO_CODEC_ID_UNKHNOWN)
     {
         bHasAudio =false;
         FLV_LOGE("[FLV Playback capability Error] not support audio format = %d", audio_codec_id);
     }

    if(bHasVideo && (video_codec_id == FLV_VIDEO_CODEC_ID_AVC))
    {
       if(mVideoConfigs.size()<=0)
       {
            FLV_LOGE("H.264 video without config info, ignore it");
            bHasVideo = false;
       }
    }

    if (bHasVideo && ((video_codec_id == FLV_VIDEO_CODEC_ID_HEVC)
                || (video_codec_id == FLV_VIDEO_CODEC_ID_HEVC_PPS)
                || (video_codec_id == FLV_VIDEO_CODEC_ID_HEVC_XL))) {
        if (mVideoConfigs.size() <= 0) {
            FLV_LOGE("H.265 video without config info, ignore it");
            bHasVideo = false;
        }
    }

    if(bHasAudio && (audio_codec_id == FLV_AUDIO_CODEC_ID_AAC))
    {
       if(mAudioConfigs.size()<=0)
       {
            FLV_LOGE("AAC Audio without config info, ignore it");
            bHasAudio = false;
       }
    }

    char value[PROPERTY_VALUE_MAX];
    int _res = 0;

    property_get("flv.ignoreaudio", value, "0");
    _res = atoi(value);
    if (_res) bHasAudio =0;

    property_get("flv.ignorevideo", value, "0");
    _res = atoi(value);
    if (_res) bHasVideo = 0;

    ALOGD("--- meta info from tag data ---");
    ALOGD("iWidth = %d, iHeight = %d", iWidth, iHeight);
    ALOGD("video_codec_id = %d, audio_codec_id = %d", video_codec_id, audio_codec_id);
    ALOGD("bHasVideo = %d, bHasAudio = %d", bHasVideo, bHasAudio);

    // check meta over, then start to queue data and setup track info
    ClearVideoFrameQueue();
    ClearAudioFrameQueue();  // clear not need frame

    FLVSeekTo(0);  // reset the read offset

    CacheMore(CACHE_FRAME);  // recache by checking bHasVideo bHasAudio

     sp<MetaData> VideoMeta = new MetaData;
     sp<MetaData> AudioMeta = new MetaData;

     if(bHasVideo)//setup video track
     {
         VideoMeta->setInt64(kKeyDuration, iDurationMs*1000LL);
         VideoMeta->setInt32(kKeyWidth, iWidth);
         VideoMeta->setInt32(kKeyHeight, iHeight);
         VideoMeta->setInt32(kKeyMaxInputSize, MAX_VIDEO_INPUT_SIZE_FLV);
         switch(video_codec_id)
         {
              case FLV_VIDEO_CODEC_ID_SORENSON_SPARK:
              {
                  VideoMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_SORENSON_SPARK);
                  sp<ABuffer> csd = new ABuffer(0);
                  sp<ABuffer> esds = MakeESDS(csd);
                  VideoMeta->setData(kKeyESDS, kTypeESDS, esds->data(), esds->size());
                  FLV_LOGD("video_codec_id:MEDIA_MIMETYPE_VIDEO_SORENSON_SPARK\n");
                  break;
              }

              case FLV_VIDEO_CODEC_ID_AVC:
              {
                 VideoMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_AVC);
                 FLV_LOGD("video_codec_id:MEDIA_MIMETYPE_VIDEO_AVC\n");
                 CHECK(mVideoConfigs.size()>0);
                 flv_tag_str* pTag = (flv_tag_str*)&(mVideoConfigs.itemAt(0));
                 VideoMeta->setData(kKeyAVCC, kTypeAVCC, (pTag->tag_data+FLV_VIDEO_AVC_TAG_DATA_OFFSET),
                         (pTag->tag_header.tag_data_size-FLV_VIDEO_AVC_TAG_DATA_OFFSET));

                 // find the parseNALSize
                 {
                     uint32_t type;
                     const void *data;
                     size_t size;
                     CHECK(VideoMeta->findData(kKeyAVCC, &type, &data, &size));

                     const uint8_t *ptr = (const uint8_t *)data;

                     CHECK(size >= 7);
                     CHECK_EQ((unsigned)ptr[0], 1u);  // configurationVersion == 1

                     // The number of bytes used to encode the length of a NAL unit.
                     mNALLengthSize = 1 + (ptr[4] & 3);
                     ALOGI("mNALLengthSize = %d", mNALLengthSize);
                 }
                 isAVCCSend = true;

#ifdef DUMP_DEBUG
                 if (pfile) {
                     uint8_t startCode[4] = {0, 0, 0, 1};
                     fwrite(startCode, 1, 4, pfile);
                     fwrite((void *)pTag->tag_data+FLV_VIDEO_AVC_TAG_DATA_OFFSET, 1, (pTag->tag_header.tag_data_size-FLV_VIDEO_AVC_TAG_DATA_RAW_OFFSET), pfile);
                     fclose(pfile);
                 }
#endif
                 free(pTag->tag_data);
                 pTag->tag_data = NULL;
                 mVideoConfigs.removeItemsAt(0);
                 break;
              }
            case FLV_VIDEO_CODEC_ID_HEVC:
            case FLV_VIDEO_CODEC_ID_HEVC_PPS:
            case FLV_VIDEO_CODEC_ID_HEVC_XL:
            {
                VideoMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_HEVC);
                FLV_LOGD("video_codec_id:MEDIA_MIMETYPE_VIDEO_HEVC\n");
                CHECK(mVideoConfigs.size() > 0);
                if (mVideoConfigs.size() < 3) {
                    VideoMeta = NULL;
                    bHasVideo = false;
                    break;
                }
                flv_tag_str *pTag1 = (flv_tag_str *) & (mVideoConfigs.itemAt(0));
                /* iqiyi style hevc file , vps/sps/pps */
                flv_tag_str *pTag2 = (flv_tag_str *) & (mVideoConfigs.itemAt(1));
                flv_tag_str *pTag3 = (flv_tag_str *) & (mVideoConfigs.itemAt(2));
                // config_size
                uint32_t tag1_cfg_size = pTag1->tag_header.tag_data_size - FLV_VIDEO_AVC_TAG_DATA_OFFSET;
                uint32_t tag2_cfg_size = pTag2->tag_header.tag_data_size - FLV_VIDEO_AVC_TAG_DATA_OFFSET;
                uint32_t tag3_cfg_size = pTag3->tag_header.tag_data_size - FLV_VIDEO_AVC_TAG_DATA_OFFSET;
                sp<ABuffer> csd = new ABuffer(tag1_cfg_size + tag2_cfg_size + tag3_cfg_size);
                memcpy(csd->data(), pTag1->tag_data + FLV_VIDEO_AVC_TAG_DATA_OFFSET, tag1_cfg_size);
                memcpy(csd->data() + tag1_cfg_size,
                        pTag2->tag_data + FLV_VIDEO_AVC_TAG_DATA_OFFSET, tag2_cfg_size);
                memcpy(csd->data() + tag1_cfg_size + tag2_cfg_size,
                        pTag3->tag_data + FLV_VIDEO_AVC_TAG_DATA_OFFSET, tag3_cfg_size);
                if (flvMakeHEVCCodecSpecificData(csd, VideoMeta) != FLV_OK) {
                    ALOGI("flvMakeHEVCCodecSpecificData fail!!");
                    VideoMeta = NULL;
                    bHasVideo = false;
                }
                mVideoConfigs.removeItemsAt(0);
                mVideoConfigs.removeItemsAt(0);
                mVideoConfigs.removeItemsAt(0);
                break;
            }
              case FLV_VIDEO_CODEC_ID_SCREEN_VIDEO:
              {
                 VideoMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_FLV);
                 FLV_LOGE("[FLV Playback capability Error] capability not support as : not support FLV_VIDEO_CODEC_ID_SCREEN_VIDEO\n");
                 break;
              }
              case FLV_VIDEO_CODEC_ID_VP6:
              {
                 VideoMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_FLV);
                 FLV_LOGE("[FLV Playback capability Error] capability not support as : not supportFLV_VIDEO_CODEC_ID_VP6\n");
                 break;
              }
              case FLV_VIDEO_CODEC_ID_VP6_ALPH:
              {
                 VideoMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_FLV);
                 FLV_LOGE("[FLV Playback capability Error] capability not support as : not supportFLV_VIDEO_CODEC_ID_VP6_ALPH\n");
                 break;
              }
              case FLV_VIDEO_CODEC_ID_SCREEN2_VIDEO:
              {
                 VideoMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_FLV);
                 FLV_LOGE("[FLV Playback capability Error] capability not support as : not supportFLV_VIDEO_CODEC_ID_SCREEN2_VIDEO\n");
                 break;
             }
             default:
             {
                FLV_LOGE("out of bound video codec, no meta");
        VideoMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_VIDEO_FLV);
                break;
             }
        }
     }

     if(bHasAudio)//setup Audio track
     {
         AudioMeta->setInt64(kKeyDuration, iDurationMs*1000LL);
         // AudioMeta->setInt32(kKeySampleRate, metainfo->audio_sample_rate);
         AudioMeta->setInt32(kKeyMaxInputSize, MAX_AUDIO_INPUT_SIZE);//4K
         //meta->setInt32(kKeyBitRate, bitrate * 1000);

         switch(audio_codec_id)
         {
             case FLV_AUDIO_CODEC_ID_AAC:
             {
                 AudioMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_AAC);
                 FLV_LOGD("audio_codec_id:MEDIA_MIMETYPE_AUDIO_AAC\n");
                 CHECK(mAudioConfigs.size() > 0);
                 flv_tag_str *pTag = (flv_tag_str *) & (mAudioConfigs.itemAt(0));
                 sp<ABuffer> csd = new ABuffer(pTag->tag_header.tag_data_size - FLV_AUDIO_AAC_TAG_DATA_RAW_OFFSET);
                 memcpy(csd->data(), pTag->tag_data + FLV_AUDIO_AAC_TAG_DATA_RAW_OFFSET, pTag->tag_header.tag_data_size - FLV_AUDIO_AAC_TAG_DATA_RAW_OFFSET);
                 sp<ABuffer> esds = MakeESDS(csd);
                 AudioMeta->setData(kKeyESDS, kTypeESDS, esds->data(), esds->size());

                 AudioMeta->setInt32(kKeyChannelCount, iChannel_cnt);
                 AudioMeta->setInt32(kKeySampleRate, iSampleRate);

                 free(pTag->tag_data);
                 pTag->tag_data = NULL;
                 mAudioConfigs.removeItemsAt(0);
                 break;
             }

             case FLV_AUDIO_CODEC_ID_MP3:
             case FLV_AUDIO_CODEC_ID_MP3_8K:
             {
                 AudioMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG);
                 FLV_LOGD("audio_codec_id:MEDIA_MIMETYPE_AUDIO_MPEG\n");
                 AudioMeta->setInt32(kKeyChannelCount, iChannel_cnt);
          AudioMeta->setInt32(kKeySampleRate, iSampleRate);
                 break;
             }
             //the following it TODO
             case FLV_AUDIO_CODEC_ID_PCM:
             case FLV_AUDIO_CODEC_ID_PCM_LE:
             {
                 AudioMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_RAW);
                 AudioMeta->setInt32(kKeyChannelCount, iChannel_cnt);
                 AudioMeta->setInt32(kKeySampleRate, iSampleRate);
#ifdef MTK_AUDIO_RAW_SUPPORT
                 //add pcm component support
                 AudioMeta->setInt32(kKeyBitWidth,iSampleSize);
                 AudioMeta->setInt32(kKeyEndian, 2);
                 AudioMeta->setInt32(kKeyPCMType,1);
                 //AudioMeta->setInt32(kKeyChannelAssignment, 1);
                 /* support unsigned PCM */
                 if(iSampleSize == 8)
                 {
                     AudioMeta->setInt32(kKeyNumericalType, 2);
                 }
#endif
                 FLV_LOGD("audio_codec_id:FLV_AUDIO_CODEC_ID_PCM=%d\n",audio_codec_id);
                 break;
             }
             case FLV_AUDIO_CODEC_ID_ADPCM:
             {
                 //meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG);
                 FLV_LOGE("[FLV Playback capability Error] not support FLV_AUDIO_CODEC_ID_ADPCM");
                 break;
             }
             case FLV_AUDIO_CODEC_ID_NELLYMOSER_8KHZ_MONO:
             {
                 //meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG);
                 FLV_LOGE("[FLV Playback capability Error] not support FLV_AUDIO_CODEC_ID_NELLYMOSER_8KHZ_MONO");
                 break;
             }
             case FLV_AUDIO_CODEC_ID_NELLYMOSER:
             {
                 //meta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_MPEG);
                 FLV_LOGE("[FLV Playback capability Error] not support FLV_AUDIO_CODEC_ID_NELLYMOSER");
                 break;
             }
             case FLV_AUDIO_CODEC_ID_G711_ALAW:
             {
                 AudioMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_G711_ALAW);
                 FLV_LOGD("audio_codec_id:MEDIA_MIMETYPE_AUDIO_G711_ALAW\n");
                 break;
             }
             case FLV_AUDIO_CODEC_ID_G711_MLAW:
             {
                 AudioMeta->setCString(kKeyMIMEType, MEDIA_MIMETYPE_AUDIO_G711_MLAW);
                 FLV_LOGD("audio_codec_id:MEDIA_MIMETYPE_AUDIO_G711_MLAW\n");
                 break;
             }

             case FLV_AUDIO_CODEC_ID_SPEEX :
             {
                 //meta->setCString(kKeyMIMEType, FLV_AUDIO_CODEC_ID_SPEEX);
                 FLV_LOGE("[FLV Playback capability Error] capability not support as : not support FLV_AUDIO_CODEC_ID_SPEEX\n");
                 break;
             }
             default:
                 break;
         }
     }


     bHaveParsed = true;

     if (bHasVideo) {
        vcodec_state = HAS_VIDEO;
     } else {
        vcodec_state = NO_VIDEO;
     }

     if(bHasVideo==true && VideoMeta!=NULL)
     {
          bHasVideo = isSupportedStream(VIDEO, video_codec_id);
          if(!bHasVideo)
          {
             VideoMeta=NULL;
             vcodec_state = UNSUPPORT_VIDEO;
          }
        }

     if(bHasVideo==true && VideoMeta!=NULL)
     {
          mTracks.push();
             TrackInfo *trackInfo = &mTracks.editItemAt(mTracks.size() - 1);
             trackInfo->mTrackNum = 1;   // stream id
             trackInfo->mMeta = VideoMeta;//track meta is important
     }
     if(bHasAudio==true && AudioMeta!=NULL)
     {
         mTracks.push();
         TrackInfo *trackInfo = &mTracks.editItemAt(mTracks.size() - 1);
         trackInfo->mTrackNum = 2;   // stream id
         trackInfo->mMeta = AudioMeta;//track meta is important
     }
     ALOGD("bHasVideo = %d, bHasAudio = %d", bHasVideo, bHasAudio);

#ifdef DUMP_DEBUG
     for (size_t i = 0; i < mTracks.size(); ++i)
     {
         TrackInfo *info = &mTracks.editItemAt(i);
         const char *mime;
         CHECK(info->mMeta->findCString(kKeyMIMEType, &mime));

         FLV_LOGD("mTracks i=%d,mime=%s",i,mime);
     }
#endif//#ifdef DUMP_DEBUG

     return true;
 }
 bool FLVExtractor::FLVSeekTo(int64_t targetNPT)
 {
     int64_t _new_ts = mflvParser->flv_seek_to_msec(targetNPT);  // ms
     FLV_LOGD ("FLVSeekTo [%lld ms] return %lld", (long long)targetNPT, (long long)_new_ts);
     if (_new_ts < -1)
        return false;
     else
        return true;
 }

 void FLVExtractor::findThumbnail()
 {
     FLV_LOGD("+FLVExtractor::findThumbnail");

     bThumbnailMode = true;

     if(false == bSeekable)
     {
         for (size_t i = 0; i < mTracks.size(); ++i)
         {
             TrackInfo *info = &mTracks.editItemAt(i);
             const char *mime;
             CHECK(info->mMeta->findCString(kKeyMIMEType, &mime));
             if (strncasecmp(mime, "video/", 6)) {
                 continue;
             }
             info->mMeta->setInt64(kKeyThumbnailTime, 0);//just get the first frame to show
         }
         FLV_LOGD("findThumbnail: can not seek, kKeyThumbnailTime=0 \n");
     }//bSeekable == false
     else
     {
         for (size_t i = 0; i < mTracks.size(); ++i)
         {
             TrackInfo *info = &mTracks.editItemAt(i);
             const char *mime;
             CHECK(info->mMeta->findCString(kKeyMIMEType, &mime));

             if (strncasecmp(mime, "video/", 6))
             {
                 continue;
             }

             ClearVideoFrameQueue();
             ClearAudioFrameQueue();

             while ((FLV_FILE_EOF != CacheMore(CACHE_VIDEO_KEY_FRAME)) && (mVideoFrames.size() < FLV_THUMBNAIL_SCAN_SIZE)) {
             }

             uint32_t _max_frame_len = 0;
             int64_t _thumbnail_frame_ts = 0;
             int64_t _cur_frame_ts = 0;
             for (size_t i = 0; i < mVideoFrames.size(); i++)
             {
                 MediaBuffer* out;
                 out = DequeueVideoFrame(-1);
                 out->meta_data()->findInt64(kKeyTime, &_cur_frame_ts);
                 FLV_LOGD ("Thumbnail frame TS=%lld, Len=%zu\n", (long long)_cur_frame_ts, out->range_length());
                 if (out->range_length() >= _max_frame_len)
                 {
                     _max_frame_len = out->range_length();
                     _thumbnail_frame_ts = _cur_frame_ts;
                 }
             }

             info->mMeta->setInt64(kKeyThumbnailTime, _thumbnail_frame_ts);
             FLV_LOGD("findThumbnail: can seek, _thumbnail_frame_ts=%lld \n", (long long)_thumbnail_frame_ts);
         }
     }//bSeekable == true

     ClearVideoFrameQueue();
     ClearAudioFrameQueue();

     FLVSeekTo(0);

     iDecVideoFramesCnt = 0;
     iDecAudioFramesCnt = 0;

     bThumbnailMode = false;
 }
 int FLVExtractor::cancelVideoRead()
 {
     ClearVideoFrameQueue();
     bHasVideo = false;
     return 0;
 }

 status_t FLVExtractor::parseAVCCodecSpecificData(
        uint8_t *data, size_t size, int *buffer_size) {
    const uint8_t *ptr = (uint8_t *)data;

    *buffer_size = 0;
    // verify minimum size and configurationVersion == 1.
    if (size < 7 || ptr[0] != 1) {
        return ERROR_MALFORMED;
    }


    // There is decodable content out there that fails the following
    // assertion, let's be lenient for now...
    // CHECK((ptr[4] >> 2) == 0x3f);  // reserved

    // commented out check below as H264_QVGA_500_NO_AUDIO.3gp
    // violates it...
    // CHECK((ptr[5] >> 5) == 7);  // reserved

    size_t numSeqParameterSets = ptr[5] & 31;

    ptr += 6;
    size -= 6;

    for (size_t i = 0; i < numSeqParameterSets; ++i) {
        if (size < 2) {
            return ERROR_MALFORMED;
        }

        size_t length = U16_AT(ptr);

        ptr += 2;
        size -= 2;

        if (size < length) {
            return ERROR_MALFORMED;
        }

        (*buffer_size) += (4+length);
        //addCodecSpecificData(ptr, length);

        ptr += length;
        size -= length;
    }

    if (size < 1) {
        return ERROR_MALFORMED;
    }

    size_t numPictureParameterSets = *ptr;
    ++ptr;
    --size;

    for (size_t i = 0; i < numPictureParameterSets; ++i) {
        if (size < 2) {
            return ERROR_MALFORMED;
        }

        size_t length = U16_AT(ptr);

        ptr += 2;
        size -= 2;

        if (size < length) {
            return ERROR_MALFORMED;
        }

        (*buffer_size) += (4+length);
        //addCodecSpecificData(ptr, length);

        ptr += length;
        size -= length;
    }
    ALOGV("avc codec specific info is right");
    return OK;
}

uint8_t* FLVExtractor::cutAVCC2Buffer(uint8_t *data, size_t size, int buffer_size)
{
    const uint8_t *ptr = (uint8_t *)data;
    uint8_t* out_data = NULL;
    out_data = (uint8_t*)malloc(buffer_size*sizeof(uint8_t));
    int tmp_loc = 0;

    // verify minimum size and configurationVersion == 1.
    if (size < 7 || ptr[0] != 1) {
        return out_data;
    }


    // There is decodable content out there that fails the following
    // assertion, let's be lenient for now...
    // CHECK((ptr[4] >> 2) == 0x3f);  // reserved

    // commented out check below as H264_QVGA_500_NO_AUDIO.3gp
    // violates it...
    // CHECK((ptr[5] >> 5) == 7);  // reserved

    size_t numSeqParameterSets = ptr[5] & 31;

    ptr += 6;
    size -= 6;

    for (size_t i = 0; i < numSeqParameterSets; ++i) {
        if (size < 2) {
            return out_data;
        }

        size_t length = U16_AT(ptr);

        ptr += 2;
        size -= 2;

        if (size < length) {
            return out_data;
        }
        out_data[tmp_loc] = 0x00;
        out_data[tmp_loc+1] = 0x00;
        out_data[tmp_loc+2] = 0x00;
        out_data[tmp_loc+3] = 0x01;
        tmp_loc += 4;
        memcpy(out_data+tmp_loc, ptr, length);
        tmp_loc += length;
        //addCodecSpecificData(ptr, length);

        ptr += length;
        size -= length;
    }

    if (size < 1) {
        return out_data;
    }

    size_t numPictureParameterSets = *ptr;
    ++ptr;
    --size;

    for (size_t i = 0; i < numPictureParameterSets; ++i) {
        if (size < 2) {
            return out_data;
        }

        size_t length = U16_AT(ptr);

        ptr += 2;
        size -= 2;

        if (size < length) {
            return out_data;
        }
        out_data[tmp_loc] = 0x00;
        out_data[tmp_loc+1] = 0x00;
        out_data[tmp_loc+2] = 0x00;
        out_data[tmp_loc+3] = 0x01;
        tmp_loc += 4;
        memcpy(out_data+tmp_loc, ptr, length);
        tmp_loc += length;

        ptr += length;
        size -= length;
    }
    ALOGV("cutter AVC config info to nal done");
    return out_data;
}

FLV_ERROR_TYPE FLVExtractor::CacheMore(CacheType HowCache) {
    // cache data and check if it config data
    FLV_ERROR_TYPE retVal = FLV_OK;
    FLV_ERROR_TYPE Ret = FLV_OK;
    uint8_t byteVal1, byteVal2;
    uint32_t u4ReadTagCnt = 0;
    ALOGV("HowCache = %d, bHasVideo = %d, bHasAudio = %d", HowCache, bHasVideo, bHasAudio);

    if (iFlvParserReadOffset >= iDataSourceLength) {
        ALOGE("read pointer is beyond the end of file!");
        return FLV_FILE_EOF;
    }

READ_TAG:
    retVal = mflvParser->flv_read_a_tag(mTag);
    u4ReadTagCnt++;

    if (retVal == FLV_OK) {
        if (mTag->tag_header.tag_type == FLV_TAG_TYPE_VIDEO) {
            if (bHasVideo || CACHE_ANYHOW == HowCache) {
                byteVal1 = *(mTag->tag_data);
                ALOGV("video_codec_id = %d", byteVal1 & 0x0F);
                video_codec_id = (FLV_VIDEO_CODEC_ID)(byteVal1 & 0x0F);
                if (HowCache == CACHE_VIDEO_KEY_FRAME) {
                    if ((byteVal1 & FLV_VIDEO_FRAME_TYPE_BITMASK) != FLV_VIDEO_FRAME_TYPE_KEY) {
                        ALOGV("not Key frame, read again");
                        goto READ_TAG;
                    }
                }

                 switch(byteVal1 & 0x0F)
                 {
                     case FLV_VIDEO_CODEC_ID_SORENSON_SPARK:
                     {
                         if(mTag->tag_header.tag_data_size <= FLV_VIDEO_S263_TAG_DATA_RAW_OFFSET)
                         {
                             FLV_LOGE("CacheMore: S263: no data in this tag, drop it, tag_data_size = %d",
                                     mTag->tag_header.tag_data_size);
                         }
                         else
                         {
                             mVideoFrames.push();
                             flv_tag_str* tag = &mVideoFrames.editItemAt(mVideoFrames.size() - 1);
                             memcpy(&(tag->tag_header) ,&(mTag->tag_header),sizeof(flv_tag_header_info));
                             tag->tag_data_offset = mTag->tag_data_offset;
                             tag->tag_data =( uint8_t* )malloc(sizeof(uint8_t)*(mTag->tag_header.tag_data_size));
                             memcpy(tag->tag_data ,mTag->tag_data,mTag->tag_header.tag_data_size);
                             FLV_LOGM("[memory]CacheMore mVideoFrames alloc buffer =0x%p\n",tag->tag_data);

                             if(iWidth == 0 || iHeight == 0)
                             {
                                 uint32_t  key_frame;
                                 FLV_LOGD("CacheMore cache S263 Frame: check resolution\n");
                                 flv_parse_s263_info((mTag->tag_data+FLV_VIDEO_S263_TAG_DATA_RAW_OFFSET),&iWidth, &iHeight,&key_frame);
                             }
                         }
                         break;
                     }
                     case FLV_VIDEO_CODEC_ID_VP6:
                     {
                         mVideoFrames.push();
                         flv_tag_str* tag = &mVideoFrames.editItemAt(mVideoFrames.size() - 1);
                         memcpy(&(tag->tag_header) ,&(mTag->tag_header),sizeof(flv_tag_header_info));
                         tag->tag_data_offset = mTag->tag_data_offset;
                         tag->tag_data =( uint8_t* )malloc(sizeof(uint8_t)*(mTag->tag_header.tag_data_size));
                         memcpy(tag->tag_data ,mTag->tag_data,mTag->tag_header.tag_data_size);
                         FLV_LOGM("[memory]CacheMore mVideoFrames alloc buffer =0x%p\n",tag->tag_data);
                         break;
                     }
                     case FLV_VIDEO_CODEC_ID_AVC:
                     {
                         if(mTag->tag_header.tag_data_size <= FLV_VIDEO_AVC_TAG_DATA_OFFSET)
                         {
                             FLV_LOGE("CacheMore: AVC: no data in this tag, drop it, tag_data_size = %d",
                                     mTag->tag_header.tag_data_size);
                         }
                         else
                         {
                             byteVal2 = *(mTag->tag_data+1);
                             if(byteVal2 == FLV_VIDEO_AVC_PACKET_TYPE_CONFIG)
                             {
                                 if (isAVCCSend)
                                 {
                                     mVideoFrames.push();
                                     flv_tag_str* tag = &(mVideoFrames.editItemAt(mVideoFrames.size() - 1));
                                     memcpy(&(tag->tag_header) ,&(mTag->tag_header),sizeof(flv_tag_header_info));

                                     tag->tag_data_offset = mTag->tag_data_offset;

                                     tag->tag_data =( uint8_t* )malloc(sizeof(uint8_t)*(mTag->tag_header.tag_data_size));
                                     memcpy(tag->tag_data ,mTag->tag_data,mTag->tag_header.tag_data_size);
                                     FLV_LOGM("[memory]CacheMore mVideoFrames alloc buffer =0x%p\n",tag->tag_data);

                                     FLV_LOGV("+CacheMore -HowCache=%d-tag_data_size=%d,tag_data_offset=%d\n",HowCache,tag->tag_header.tag_data_size,tag->tag_data_offset);
                                 }
                                 else
                                 {
                                     mVideoConfigs.push();
                                     flv_tag_str* tag = &mVideoConfigs.editItemAt(mVideoConfigs.size() - 1);
                                     memcpy(&(tag->tag_header) ,&(mTag->tag_header),sizeof(flv_tag_header_info));
                                     tag->tag_data_offset = mTag->tag_data_offset;
                                     tag->tag_data =( uint8_t* )malloc(sizeof(uint8_t)*(mTag->tag_header.tag_data_size));
                                     memcpy(tag->tag_data ,mTag->tag_data,mTag->tag_header.tag_data_size);
                                     FLV_LOGM("[memory]CacheMore mVideoConfigs alloc buffer =0x%p\n",tag->tag_data);

                                     FLV_LOGD("CacheMore cache avc csps/pps: check resolution\n");
                                     flv_parse_avc_sps((mTag->tag_data+FLV_VIDEO_AVC_TAG_DATA_OFFSET),(mTag->tag_header.tag_data_size-FLV_VIDEO_AVC_TAG_DATA_OFFSET),&iWidth, &iHeight);
                                 }
                             }
                             else
                             {
                                 mVideoFrames.push();
                                 flv_tag_str* tag = &(mVideoFrames.editItemAt(mVideoFrames.size() - 1));
                                 memcpy(&(tag->tag_header) ,&(mTag->tag_header),sizeof(flv_tag_header_info));

                                tag->tag_data_offset = mTag->tag_data_offset;

                                tag->tag_data =( uint8_t* )malloc(sizeof(uint8_t)*(mTag->tag_header.tag_data_size));
                                memcpy(tag->tag_data ,mTag->tag_data,mTag->tag_header.tag_data_size);
                                FLV_LOGM("[memory]CacheMore mVideoFrames alloc buffer =0x%p\n",tag->tag_data);

                                FLV_LOGV("+CacheMore -HowCache=%d-tag_data_size=%d,tag_data_offset=%d\n",HowCache,tag->tag_header.tag_data_size,tag->tag_data_offset);

                            }
                        }
                        break;
                    }
                    case FLV_VIDEO_CODEC_ID_HEVC:
                    case FLV_VIDEO_CODEC_ID_HEVC_PPS:
                    case FLV_VIDEO_CODEC_ID_HEVC_XL:
                    {
                        if(mTag->tag_header.tag_data_size <= FLV_VIDEO_AVC_TAG_DATA_OFFSET)
                        {
                            FLV_LOGE("CacheMore: HEVC: no data in this tag, drop it, tag_data_size = %d",
                                     mTag->tag_header.tag_data_size);
                        }
                        else
                        {
                            byteVal2 = *(mTag->tag_data+1);
                            if(byteVal2 == FLV_VIDEO_AVC_PACKET_TYPE_CONFIG)
                            {
                                mVideoConfigs.push();
                                flv_tag_str* tag = &mVideoConfigs.editItemAt(mVideoConfigs.size() - 1);
                                memcpy(&(tag->tag_header) ,&(mTag->tag_header),sizeof(flv_tag_header_info));
                                tag->tag_data_offset = mTag->tag_data_offset;
                                tag->tag_data =( uint8_t* )malloc(sizeof(uint8_t)*(mTag->tag_header.tag_data_size));
                                memcpy(tag->tag_data ,mTag->tag_data,mTag->tag_header.tag_data_size);
                                FLV_LOGM("[memory]CacheMore mVideoConfigs alloc buffer =0x%p\n",tag->tag_data);

                                FLV_LOGD("CacheMore cache hevc sps/pps");
                                //flv_parse_avc_sps((mTag->tag_data+FLV_VIDEO_AVC_TAG_DATA_OFFSET),(mTag->tag_header.tag_data_size-FLV_VIDEO_AVC_TAG_DATA_OFFSET),&iWidth, &iHeight);
                            }
                            else
                            {
                                mVideoFrames.push();
                                flv_tag_str* tag = &(mVideoFrames.editItemAt(mVideoFrames.size() - 1));
                                memcpy(&(tag->tag_header) ,&(mTag->tag_header),sizeof(flv_tag_header_info));

                                tag->tag_data_offset = mTag->tag_data_offset;

                                tag->tag_data =( uint8_t* )malloc(sizeof(uint8_t)*(mTag->tag_header.tag_data_size));
                                memcpy(tag->tag_data ,mTag->tag_data,mTag->tag_header.tag_data_size);
                                FLV_LOGM("[memory]CacheMore mVideoFrames alloc buffer =0x%p\n",tag->tag_data);

                                FLV_LOGV("+CacheMore -HowCache=%d-tag_data_size=%d,tag_data_offset=%d\n",HowCache,tag->tag_header.tag_data_size,tag->tag_data_offset);

                            }
                        }
                        break;
                    }
                 }
                 bHasVideo = true;
            } else {
                ALOGD("read video frame When (bhasVideo == false && CACHE_ANYHOW != HowCache), so skip this frame");
            }
        } else if (mTag->tag_header.tag_type == FLV_TAG_TYPE_AUDIO) {
            if (bHasAudio || CACHE_ANYHOW  == HowCache) {
                byteVal1 = *(mTag->tag_data);
                ALOGV("audio_codec_id = %d", (byteVal1 & 0xF0)>>4);
                audio_codec_id = (FLV_AUDIO_CODEC_ID)((byteVal1 & 0xF0)>>4);
                switch ((byteVal1 & 0xF0) >> 4) {
                     case FLV_AUDIO_CODEC_ID_MP3:
                     {
                         if(mTag->tag_header.tag_data_size <= FLV_AUDIO_MP3_TAG_DATA_RAW_OFFSET)
                         {
                             FLV_LOGE("CacheMore: MP3: no data in this tag, drop it, tag_data_size = %d",
                                     mTag->tag_header.tag_data_size);
                         }
                         else
                         {
                             mAudioFrames.push();
                             flv_tag_str* tag = &mAudioFrames.editItemAt(mAudioFrames.size() - 1);
                             memcpy(&(tag->tag_header) ,&(mTag->tag_header),sizeof(flv_tag_header_info));
                             tag->tag_data_offset = mTag->tag_data_offset;
                             tag->tag_data =( uint8_t* )malloc(sizeof(uint8_t)*(mTag->tag_header.tag_data_size));
                             memcpy(tag->tag_data ,mTag->tag_data,mTag->tag_header.tag_data_size);
                             FLV_LOGM("[memory]CacheMore mAudioFrames alloc buffer =0x%p\n",tag->tag_data);
                             if(!bHaveParsed)
                             {
                    uint8_t Val8 = *((uint8_t*)(tag->tag_data));
                    get_audio_tage_header_info(Val8,NULL,&iSampleRate,&iSampleSize,&iChannel_cnt);
                    //make sure by mp3 data
                    uint32_t  frame_size;
                                 uint32_t header;
                                 int out_bitrate;
                                 bool ret;
                                 header = U32_AT(( uint8_t* )(tag->tag_data+FLV_AUDIO_MP3_TAG_DATA_RAW_OFFSET));
                                 FLV_LOGV("CacheMore MP3: headert=0x%p\n",header);
                                 ret = get_mp3_frame_size( header,&frame_size,&iSampleRate,&iChannel_cnt, &out_bitrate);
                                 if(ret)
                                 {
                                     FLV_LOGD("CacheMore MP3: iChannel_cnt=%d,iSampleSize=%d,iSampleRate=%d\n",iChannel_cnt,iSampleSize,iSampleRate);
                                 }
                             }
                         }
                         break;
                     }
                     case FLV_AUDIO_CODEC_ID_PCM:
                     case FLV_AUDIO_CODEC_ID_PCM_LE:
                     {
                         if(mTag->tag_header.tag_data_size <= FLV_AUDIO_PCM_TAG_DATA_RAW_OFFSET)
                         {
                             FLV_LOGE("CacheMore: PCM: no data in this tag, drop it, tag_data_size = %d",
                                     mTag->tag_header.tag_data_size);
                         }
                         else
                         {
                             mAudioFrames.push();
                             flv_tag_str* tag = &mAudioFrames.editItemAt(mAudioFrames.size() - 1);
                             memcpy(&(tag->tag_header) ,&(mTag->tag_header),sizeof(flv_tag_header_info));
                             tag->tag_data_offset = mTag->tag_data_offset;
                             tag->tag_data =( uint8_t* )malloc(sizeof(uint8_t)*(mTag->tag_header.tag_data_size));
                             memcpy(tag->tag_data ,mTag->tag_data,mTag->tag_header.tag_data_size);
                             FLV_LOGM("[memory]CacheMore mAudioFrames alloc buffer =0x%p\n",tag->tag_data);
                                 if(!bHaveParsed)
                          {
                                     uint8_t Val8 = *((uint8_t*)(tag->tag_data));
                        get_audio_tage_header_info(Val8,NULL,&iSampleRate,&iSampleSize,&iChannel_cnt);

                                     FLV_LOGD("CacheMore PCM: iChannel_cnt=%d,iSampleSize=%d,iSampleRate=%d\n",iChannel_cnt,iSampleSize,iSampleRate);
                             }
                         }
                         break;
                     }
                     case FLV_AUDIO_CODEC_ID_AAC:
                     {
                         if(mTag->tag_header.tag_data_size <= FLV_AUDIO_AAC_TAG_DATA_RAW_OFFSET)
                         {
                             FLV_LOGE("CacheMore: AAC: no data in this tag, drop it, tag_data_size = %d",
                                     mTag->tag_header.tag_data_size);
                         } else {
                             byteVal2 = *(mTag->tag_data+1);
                             if (byteVal2 == FLV_AUDIO_AAC_PACKET_TYPE_CONFIG) {
                                 mAudioConfigs.push();
                                 flv_tag_str* tag = &mAudioConfigs.editItemAt(mAudioConfigs.size() - 1);
                                 memcpy(&(tag->tag_header) ,&(mTag->tag_header),sizeof(flv_tag_header_info));
                                 tag->tag_data_offset = mTag->tag_data_offset;
                                 tag->tag_data =( uint8_t* )malloc(sizeof(uint8_t)*(mTag->tag_header.tag_data_size));
                                 memcpy(tag->tag_data ,mTag->tag_data,mTag->tag_header.tag_data_size);
                                 FLV_LOGM("[memory]CacheMore mAudioConfigs alloc buffer =0x%p\n",tag->tag_data);
                                 if (!bHaveParsed) {
                                     uint8_t Val8 = *((uint8_t*)(tag->tag_data));
                                     get_audio_tage_header_info(Val8, NULL, &iSampleRate, &iSampleSize, &iChannel_cnt);
                                     ALOGV("AAC: iChannel_cnt = %d, iSampleSize = %d, iSampleRate = %d",
                                            iChannel_cnt, iSampleSize, iSampleRate);
                                 }
                             } else {
                                 mAudioFrames.push();
                                 flv_tag_str* tag = &mAudioFrames.editItemAt(mAudioFrames.size() - 1);
                                 memcpy(&(tag->tag_header) ,&(mTag->tag_header),sizeof(flv_tag_header_info));
                                 tag->tag_data_offset = mTag->tag_data_offset;
                                 tag->tag_data =( uint8_t* )malloc(sizeof(uint8_t)*(mTag->tag_header.tag_data_size));
                                 memcpy(tag->tag_data ,mTag->tag_data,mTag->tag_header.tag_data_size);
                                 FLV_LOGM("[memory]CacheMore mAudioFrames alloc buffer =0x%p\n",tag->tag_data);
                             }
                         }
                         break;
                     }
                  }
                 bHasAudio = true;
            } else {
                ALOGD("read audio When (bhasAudio == false && CACHE_ANYHOW != HowCache), so skip this frame");
            }
        } else {
            byteVal1 = *(mTag->tag_data);
            ALOGV("not video or audio tag, Skip it, byteVal1 = %hhu", byteVal1);
        }

        if (!bHaveParsed && (CACHE_ANYHOW == HowCache) && (u4ReadTagCnt > FLV_INITIAL_TAG_COUNT_THD)) {
            ALOGE("FLV_VIDEO_OR_AUDIO_FLAG Maybe Error");
        } else {
            if (!bHaveParsed && ((mVideoFrames.size() == 0 && bHasVideo) || (mAudioFrames.size() == 0 && bHasAudio))) {
                ALOGV("read again, bHaveParsed = %d, mVideoFrames.size = %zu, mAudioFrames.size = %zu",
                        bHaveParsed, mVideoFrames.size(), mAudioFrames.size());
                goto READ_TAG;
            } else if (!bHaveParsed && CACHE_ANYHOW == HowCache && (mVideoFrames.size() + mAudioFrames.size()) < 5) {
                ALOGV("CACHE_ANYHOW read again, bHaveParsed = %d, mVideoFrames.size = %zu, mAudioFrames.size = %zu",
                        bHaveParsed, mVideoFrames.size(), mAudioFrames.size());
                goto READ_TAG;
            }
        }
        Ret = FLV_OK;
    } else if (retVal == FLV_FILE_READ_ERR) {
        ALOGE("FLV_FILE_EOF");
        Ret = FLV_FILE_EOF;
    } else if (retVal == FLV_FILE_EOF) {
        ALOGE("FLV_FILE_EOF");
        Ret = FLV_FILE_EOF;
    } else if (retVal == FLV_ERROR) {
        ALOGE("FLV_ERROR");
        Ret = FLV_ERROR;
    }
    ALOGV("quit: mAudioConfigs.size=%zu, mVideoConfigs.size=%zu, mVideoFrames.size=%zu, mAudioFrames.size=%zu",
            mAudioConfigs.size(), mVideoConfigs.size(), mVideoFrames.size(), mAudioFrames.size());
    return Ret;
}

 MediaBuffer* FLVExtractor::DequeueVideoFrame(int64_t targetSampleTimeUs)
 {
     flv_tag_str* pTag =(flv_tag_str*)&(mVideoFrames.itemAt(0));

     uint8_t byteVal = *(pTag->tag_data);
     FLV_LOGV ("DequeueVideoFrame :video codec id =%d",byteVal & 0x0F);
     iDecVideoFramesCnt++;
     FLV_LOGV ("DequeueVideoFrame :iDecVideoFramesCnt =%d,+FLVExtractor 0x%p, tid=%d",iDecVideoFramesCnt, this, gettid());

     //isSyncFrame: if is I frame
     int32_t isSyncFrame = 0;
     if ((byteVal & FLV_VIDEO_FRAME_TYPE_BITMASK) == FLV_VIDEO_FRAME_TYPE_KEY) isSyncFrame = 1;
     else isSyncFrame = 0;

     switch(byteVal & 0x0F)
     {
         case FLV_VIDEO_CODEC_ID_SORENSON_SPARK:
         {
             MediaBuffer *buffer = new MediaBuffer(pTag->tag_header.tag_data_size-FLV_VIDEO_S263_TAG_DATA_RAW_OFFSET);
             buffer->meta_data()->setInt64(kKeyTime, pTag->tag_header.tag_ts*1000LL);
             uint8_t *data = (uint8_t *)buffer->data();
             memcpy(data, pTag->tag_data+FLV_VIDEO_S263_TAG_DATA_RAW_OFFSET, pTag->tag_header.tag_data_size-FLV_VIDEO_S263_TAG_DATA_RAW_OFFSET);
             buffer->set_range(0, pTag->tag_header.tag_data_size-FLV_VIDEO_S263_TAG_DATA_RAW_OFFSET);
             buffer->meta_data()->setInt64(kKeyTargetTime, targetSampleTimeUs);
             FLV_LOGM("[memory]DequeueVideoFrame mVideoFrames Free buffer =0x%p\n",pTag->tag_data);
             buffer->meta_data()->setInt32(kKeyIsSyncFrame, isSyncFrame);
             free(pTag->tag_data);
             pTag->tag_data = NULL;
             mVideoFrames.removeItemsAt(0);
             return buffer;
             break;
         }
         case FLV_VIDEO_CODEC_ID_VP6:
         {
              break;
         }
         case FLV_VIDEO_CODEC_ID_AVC:
         {
             uint8_t AVCPacketType = *(pTag->tag_data + 1);
             if (0 == AVCPacketType) {
                 int buffer_size = 0;
                 int32_t CompositionTime;
                 uint64_t ts;
                 if (OK == parseAVCCodecSpecificData(pTag->tag_data+FLV_VIDEO_AVC_TAG_DATA_OFFSET,
                             pTag->tag_header.tag_data_size, &buffer_size)) {
                     MediaBuffer *mybuffer = new MediaBuffer(buffer_size);
                     uint8_t* out_data = cutAVCC2Buffer(pTag->tag_data + FLV_VIDEO_AVC_TAG_DATA_OFFSET,
                             pTag->tag_header.tag_data_size, buffer_size);
                     uint8_t* clone = (uint8_t*)mybuffer->data();
                     memcpy(clone, out_data, buffer_size);
                     mybuffer->set_range(0, buffer_size);
                     //calc ts :
                     uint32_t u4Var;
                     u4Var  = ((U32_AT(( uint8_t* )(pTag->tag_data+FLV_VIDEO_AVC_TAG_CT_OFFSET)))>>8) & 0x00FFFFFF;

                     FLV_LOGV("DequeueVideoFrame :u4Var =0x%p",u4Var);
                     if ((u4Var & 0x800000) != 0) {
                         CompositionTime = -(0x1000000 - u4Var);
                     } else {
                         CompositionTime = u4Var;
                     }

                     ts = pTag->tag_header.tag_ts + CompositionTime;

                     mybuffer->meta_data()->setInt64(kKeyTime, ts*1000LL);
                     mybuffer->meta_data()->setInt64(kKeyTargetTime, targetSampleTimeUs);

                     free(pTag->tag_data);
                     pTag->tag_data = NULL;
                     mVideoFrames.removeItemsAt(0);
                     free(out_data);
                     return mybuffer;
                 } else {
                     ALOGE("not a good avc codec config info, just send to decoder");
                 }
             }
             // calc ts:
             uint32_t u4Var;
             int32_t CompositionTime;
             uint64_t ts;
             u4Var = ((U32_AT(( uint8_t* )(pTag->tag_data + FLV_VIDEO_AVC_TAG_CT_OFFSET)))>>8) & 0x00FFFFFF;
             if ((u4Var & 0x800000) != 0) {
                 CompositionTime = -(0x1000000 - (int32_t)u4Var);
             } else {
                 CompositionTime = u4Var;
             }
             ts = (int64_t)pTag->tag_header.tag_ts + CompositionTime;
             if (mWantsNALFragments == false) {
                 // size + nal on default
                 if (pTag->tag_data_offset == 0) {
                     pTag->tag_data_offset = pTag->tag_data_offset + FLV_VIDEO_AVC_TAG_DATA_OFFSET;
                 }

                 /*
                  * Before frame: tag_data_size = 5B(FLV_VIDEO_AVC_TAG_DATA_OFFSET) + mNALLengthSize + nal + mNALLengthSize + nal + ...
                  * New frame: size = 4B(start code) + nal + 4B + nal + ...
                  * mNALLengthSize may be smaller than 4B
                  * Suppose mNALLengthSize = 2 and a frame contains 3 nals, then new_frame_size will bigger than tag_data_size
                  * So in order to make a frame can contains 10 nals, then MediaBuffer need at least allocate memory of (tag_data_size + 15B)
                  */
                 MediaBuffer *tempBuffer = new MediaBuffer(pTag->tag_header.tag_data_size + 15);
                 uint8_t *data = (uint8_t *)tempBuffer->data();
                 uint32_t full_frame_size = 0;
                 while (pTag->tag_data_offset < (pTag->tag_header.tag_data_size - mNALLengthSize)) {
                     // cut and assemble to a full frame
                     uint32_t this_nal_size;
                     uint8_t startcode[4] = {0, 0, 0, 1};
                     this_nal_size = parseNALSize(( uint8_t* )(pTag->tag_data + pTag->tag_data_offset));

                     uint32_t remain_data_in_tag =
                         pTag->tag_header.tag_data_size - pTag->tag_data_offset - mNALLengthSize;
                     if (this_nal_size > remain_data_in_tag) {
                         ALOGE("DequeueVideoFrame: this_nal_size %u > remain_data_in_tag %u",
                                 this_nal_size, remain_data_in_tag);
                         this_nal_size = remain_data_in_tag;
                     }
                     memcpy(data + full_frame_size, startcode, 4);
                     full_frame_size += 4;
                     memcpy(data + full_frame_size, (uint8_t *)(pTag->tag_data + pTag->tag_data_offset + mNALLengthSize), this_nal_size);
                     pTag->tag_data_offset = pTag->tag_data_offset + mNALLengthSize + this_nal_size;
                     full_frame_size += this_nal_size;
                     tempBuffer->set_range(0, full_frame_size);
                 }
                 MediaBuffer *buffer = new MediaBuffer(full_frame_size);
                 memcpy(buffer->data(), tempBuffer->data(), full_frame_size);
                 buffer->set_range(0, full_frame_size);
                 buffer->meta_data()->setInt64(kKeyTime, ts*1000LL);
                 buffer->meta_data()->setInt64(kKeyTargetTime, targetSampleTimeUs);
                 buffer->meta_data()->setInt32(kKeyIsSyncFrame, isSyncFrame);
                 tempBuffer->release();
                 tempBuffer = NULL;
                 data = NULL;

                 FLV_LOGD("-DequeueVideoFrame: tag_data = %p, ts = %lld, full_frame_size = %d",
                         pTag->tag_data, (long long)ts, full_frame_size);
                 // free buffer
                 free(pTag->tag_data);
                 pTag->tag_data = NULL;
                 mVideoFrames.removeItemsAt(0);
                 return buffer;
             } else {
                 uint32_t this_nal_size;
                 FLV_LOGD("+DequeueVideoFrame: tag_data=%p, tag_data_offset=%d, tag_data_size=%d", pTag->tag_data, pTag->tag_data_offset, pTag->tag_header.tag_data_size);

                 if (pTag->tag_data_offset == 0) {  // first read this tag. skip the tag data header info
                     pTag->tag_data_offset = pTag->tag_data_offset + FLV_VIDEO_AVC_TAG_DATA_OFFSET;
                 }
                 this_nal_size = parseNALSize(( uint8_t* )(pTag->tag_data+pTag->tag_data_offset));

                 uint32_t remain_data_in_tag = pTag->tag_header.tag_data_size - pTag->tag_data_offset - mNALLengthSize;

                 if (this_nal_size > remain_data_in_tag) {  // handle error tag data
                     ALOGE("DequeueVideoFrame: this_nal_size %d > remain_data_in_tag %d",
                            this_nal_size, remain_data_in_tag);
                     this_nal_size = remain_data_in_tag;
                 }

                 MediaBuffer *buffer = new MediaBuffer(this_nal_size);
                 uint8_t *data = (uint8_t *)buffer->data();

                 memcpy(data, (uint8_t *)(pTag->tag_data+pTag->tag_data_offset+mNALLengthSize), this_nal_size);
                 buffer->set_range(0, this_nal_size);

                 buffer->meta_data()->setInt64(kKeyTime, ts*1000LL);
                 buffer->meta_data()->setInt64(kKeyTargetTime, targetSampleTimeUs);
                 buffer->meta_data()->setInt32(kKeyIsSyncFrame, isSyncFrame);
#ifdef DUMP_DEBUG
                 if (pfile) {
                     uint8_t startCode[4] = {0, 0, 0, 1};
                     fwrite(startCode, 1, 4, pfile);
                     fwrite((void *)pTag->tag_data+pTag->tag_data_offset+mNALLengthSize, 1, this_nal_size, pfile);
                     fclose(pfile);
                 }
#endif
                 pTag->tag_data_offset = pTag->tag_data_offset + mNALLengthSize + this_nal_size;
                 FLV_LOGD("-DequeueVideoFrame: tag_data = %p, ts = %lld, nal_size = %d, tag_data_offset = %d",
                         pTag->tag_data, (long long)ts, this_nal_size, pTag->tag_data_offset);
                 if (pTag->tag_data_offset >= (pTag->tag_header.tag_data_size - mNALLengthSize)) {
                     // handle error tag data
                     FLV_LOGM("[memory]DequeueVideoFrame mVideoFrames Free buffer =0x%p\n",pTag->tag_data);
                     FLV_LOGV("DequeueVideoFrame:read all nals, current nal size =%d,tag_data_offset=%d\n",this_nal_size,pTag->tag_data_offset);
                     free(pTag->tag_data);
                     pTag->tag_data = NULL;
                     mVideoFrames.removeItemsAt(0);
                 }
                 return buffer;
             }
             break;
         }
        case FLV_VIDEO_CODEC_ID_HEVC:
        case FLV_VIDEO_CODEC_ID_HEVC_PPS:
        case FLV_VIDEO_CODEC_ID_HEVC_XL:
        {
             uint32_t this_nal_size, u4Var;
             int32_t CompositionTime;
             uint64_t ts;
             FLV_LOGD("DequeueVideoFrame:bf tag_data_size=%d tag_data_offset =%u, tag_data=0x%p\n",
               pTag->tag_header.tag_data_size,  pTag->tag_data_offset,pTag->tag_data);

             if(pTag->tag_data_offset==0)//first read this tag.skip the tag data header info
             {
                pTag->tag_data_offset = pTag->tag_data_offset + FLV_VIDEO_AVC_TAG_DATA_OFFSET;
             }
             this_nal_size = pTag->tag_header.tag_data_size - 5;

             MediaBuffer *buffer = new MediaBuffer(this_nal_size);
             uint8_t *data = (uint8_t *)buffer->data();

             memcpy(data, (uint8_t *)(pTag->tag_data+5), this_nal_size);
             buffer->set_range(0, this_nal_size);


            //calc ts :
            u4Var  = ((U32_AT(( uint8_t* )(pTag->tag_data+FLV_VIDEO_AVC_TAG_CT_OFFSET)))>>8) & 0x00FFFFFF;

            FLV_LOGV("DequeueVideoFrame :u4Var =0x%p",u4Var);
            if((u4Var & 0x800000) != 0)
            {
                CompositionTime = -(0x1000000 - u4Var);
            }
            else
            {
                CompositionTime = u4Var;
            }

            ts = pTag->tag_header.tag_ts + CompositionTime;
            buffer->meta_data()->setInt64(kKeyTime, ts*1000LL);
            buffer->meta_data()->setInt64(kKeyTargetTime, targetSampleTimeUs);
            buffer->meta_data()->setInt32(kKeyIsSyncFrame, isSyncFrame);

            FLV_LOGV("DequeueVideoFrame :CompositionTime =0x%p current nal size =%d,tag_data_offset=%d\n",CompositionTime,this_nal_size,pTag->tag_data_offset);
            free(pTag->tag_data);
            pTag->tag_data = NULL;
            mVideoFrames.removeItemsAt(0);
            return buffer;
            break;
        }
    }
    return NULL;
 }

 MediaBuffer* FLVExtractor::DequeueAudioFrame(int64_t targetSampleTimeUs)
 {
     flv_tag_str* pTag = (flv_tag_str*)&(mAudioFrames.itemAt(0));
     uint8_t byteVal = *(pTag->tag_data);
     FLV_LOGV ("DequeueAudioFrame :audio codec id =%d",(byteVal & 0xF0)>>4);
     iDecAudioFramesCnt++;
     FLV_LOGV ("DequeueAudioFrame :iDecAudioFramesCnt =%d, tag_data_size=%d",iDecAudioFramesCnt,pTag->tag_header.tag_data_size);
     switch((byteVal & 0xF0)>>4)
     {
         case FLV_AUDIO_CODEC_ID_MP3:
         {
             MediaBuffer *buffer = new MediaBuffer(pTag->tag_header.tag_data_size-FLV_AUDIO_MP3_TAG_DATA_RAW_OFFSET);
             buffer->meta_data()->setInt64(kKeyTime, pTag->tag_header.tag_ts*1000LL);
             uint8_t *data = (uint8_t *)buffer->data();
             memcpy(data, pTag->tag_data+FLV_AUDIO_MP3_TAG_DATA_RAW_OFFSET, pTag->tag_header.tag_data_size-FLV_AUDIO_MP3_TAG_DATA_RAW_OFFSET);
             buffer->set_range(0, pTag->tag_header.tag_data_size-FLV_AUDIO_MP3_TAG_DATA_RAW_OFFSET);
             buffer->meta_data()->setInt64(kKeyTargetTime, targetSampleTimeUs);
             FLV_LOGM("[memory]DequeueAudioFrame mAudioFrames Free buffer =0x%p\n",pTag->tag_data);
             free(pTag->tag_data);
             pTag->tag_data = NULL;
             mAudioFrames.removeItemsAt(0);
             return buffer;
             break;
         }
         case FLV_AUDIO_CODEC_ID_PCM:
         case FLV_AUDIO_CODEC_ID_PCM_LE:
         {
             MediaBuffer *buffer = new MediaBuffer(pTag->tag_header.tag_data_size-FLV_AUDIO_PCM_TAG_DATA_RAW_OFFSET);
             buffer->meta_data()->setInt64(kKeyTime, pTag->tag_header.tag_ts*1000LL);
             uint8_t *data = (uint8_t *)buffer->data();
             memcpy(data, pTag->tag_data+FLV_AUDIO_PCM_TAG_DATA_RAW_OFFSET, pTag->tag_header.tag_data_size-FLV_AUDIO_PCM_TAG_DATA_RAW_OFFSET);
             buffer->set_range(0, pTag->tag_header.tag_data_size-FLV_AUDIO_PCM_TAG_DATA_RAW_OFFSET);
             buffer->meta_data()->setInt64(kKeyTargetTime, targetSampleTimeUs);
             FLV_LOGM("[memory]DequeueAudioFrame mAudioFrames Free buffer =0x%p\n",pTag->tag_data);
             free(pTag->tag_data);
             pTag->tag_data = NULL;
             mAudioFrames.removeItemsAt(0);
             return buffer;
             break;
         }
         case FLV_AUDIO_CODEC_ID_AAC:
         {
              MediaBuffer *buffer = new MediaBuffer(pTag->tag_header.tag_data_size-FLV_AUDIO_AAC_TAG_DATA_RAW_OFFSET);
              buffer->meta_data()->setInt64(kKeyTime, pTag->tag_header.tag_ts*1000LL);
              uint8_t *data = (uint8_t *)buffer->data();
              memcpy(data, pTag->tag_data+FLV_AUDIO_AAC_TAG_DATA_RAW_OFFSET, pTag->tag_header.tag_data_size-FLV_AUDIO_AAC_TAG_DATA_RAW_OFFSET);
              buffer->set_range(0, pTag->tag_header.tag_data_size-FLV_AUDIO_AAC_TAG_DATA_RAW_OFFSET);
              buffer->meta_data()->setInt64(kKeyTargetTime, targetSampleTimeUs);
              FLV_LOGM("[memory]DequeueAudioFrame mAudioFrames Free buffer =0x%p\n",pTag->tag_data);
              free(pTag->tag_data);
              pTag->tag_data = NULL;
              mAudioFrames.removeItemsAt(0);
              return buffer;
              break;
         }
     }
     //dump
	 /*
	 for (size_t i = 0; i < mAudioFrames.size(); i++) {
		 FLV_LOGD("DequeueAudioFrame dump\n");
		 flv_tag_str* pTag = (flv_tag_str*)&(mAudioFrames.itemAt(i));
		 FLV_LOGD("i=%d,pTag->tag_header.tag_data_size=%d\n",i,pTag->tag_header.tag_data_size);
		 FLV_LOGD("i=%d,pTag->tag_header.tag_type=%d\n",i,pTag->tag_header.tag_type);
		 FLV_LOGD("i=%d,pTag->tag_data=0x%p\n",i,pTag->tag_data);
		 FLV_LOGD("i=%d,*(mTag->tag_data)=0x%p\n",i,*(pTag->tag_data));
	 }
	 */
     return NULL;
 }

void FLVExtractor::ClearVideoFrameQueue() {
    FLV_LOGD("+ClearVideoFrameQueue");
    for (size_t i = 0; i < mVideoFrames.size(); i++) {
        flv_tag_str* pTag = (flv_tag_str*)&(mVideoFrames.itemAt(i));
        if (pTag->tag_data) {
            free(pTag->tag_data);
            pTag->tag_data = NULL;
        }
    }
    mVideoFrames.clear();

    for (size_t i = 0; i < mVideoConfigs.size(); i++) {
        flv_tag_str* pTag = (flv_tag_str*)&(mVideoConfigs.itemAt(i));
        if (pTag->tag_data) {
            FLV_LOGM("[memory]ClearmVideoConfigsQueue flush %d mVideoConfigs Free buffer 0x%p", i, pTag->tag_data);
            free(pTag->tag_data);
            pTag->tag_data = NULL;
        }
    }
    mVideoConfigs.clear();
}

void FLVExtractor::ClearAudioFrameQueue() {
    FLV_LOGD("+ClearAudioFrameQueue");
    for (size_t i = 0; i < mAudioFrames.size(); i++) {
        flv_tag_str* pTag = (flv_tag_str*)&(mAudioFrames.itemAt(i));
        if (pTag->tag_data) {
            FLV_LOGM("[memory]ClearAudioFrameQueue flush %d mAudioFrames Free buffer 0x%p", i, pTag->tag_data);
            free(pTag->tag_data);
            pTag->tag_data = NULL;
        }
    }
    mAudioFrames.clear();

    for (size_t i = 0; i < mAudioConfigs.size(); i++) {
        flv_tag_str* pTag = (flv_tag_str*)&(mAudioConfigs.itemAt(i));
        if (pTag->tag_data) {
            FLV_LOGM("[memory]ClearmAudioConfigsQueue flush %d mAudioConfigs Free buffer 0x%p", i, pTag->tag_data);
            free(pTag->tag_data);
            pTag->tag_data = NULL;
        }
    }
    mAudioConfigs.clear();
}

uint32_t  FLVExtractor::parseNALSize(const uint8_t *data) {
    switch (mNALLengthSize) {
        case 1:
            return *data;
        case 2:
            return U16_AT(data);
        case 3:
            return ((size_t)data[0] << 16) | U16_AT(&data[1]);
        case 4:
            return U32_AT(data);
    }

    // This cannot happen, mNALLengthSize springs to life by adding 1 to
    // a 2-bit integer.
    ALOGE("Should not be here.");

    return 0;
}

FLV_ERROR_TYPE FLVExtractor::flvMakeHEVCCodecSpecificData(const sp<ABuffer> &buffer,
        const sp<MetaData>& mMeta) {
    sp<MetaData> metaHEVC;
    int32_t width = 0;
    int32_t height = 0;
    const void *csd = NULL;
    size_t csdSize = 0;
    uint32_t type;

    if (NULL == buffer->data()) {
        return FLV_ERROR;
    }

    metaHEVC = MakeHEVCCodecSpecificData(buffer);
    if (metaHEVC == NULL) {
        ALOGE("Unable to extract HEVC codec specific data");
        return FLV_ERROR;
    }
    CHECK(metaHEVC->findInt32(kKeyWidth, &width));
    CHECK(metaHEVC->findInt32(kKeyHeight, &height));
    CHECK(metaHEVC->findData(kKeyHVCC, &type, &csd, &csdSize));
    mMeta->setInt32(kKeyWidth, width);
    mMeta->setInt32(kKeyHeight, height);
    mMeta->setData(kKeyHVCC, type, csd, csdSize);
    return FLV_OK;
}

void scaling_list(int32_t sizeOfScalingList, ABitReader *br) {
    int lastScale = 8;
    int nextScale = 8;
    for (int j = 0; j < sizeOfScalingList; j++) {
        if (nextScale != 0) {
            int32_t delta_scale = parseSE(br);
            nextScale = (lastScale + delta_scale + 256) % 256;
        }
        lastScale = (nextScale == 0) ? lastScale : nextScale;
    }
}

void parse_seq_scaling_matrix_present(ABitReader *br) {
    for (int i = 0; i < 8; i++) {
        uint32_t seq_scaling_list_present_flag = br->getBits(1);
        ALOGV("seq_scaling_list_presetn_flag :%d", seq_scaling_list_present_flag);
        if (seq_scaling_list_present_flag) {
            if (i < 6)
                scaling_list(16, br);
            else
                scaling_list(64, br);
        }
    }
}

status_t FindAVCSPSInfo(
        uint8_t *seqParamSet, size_t size, struct SPSInfo *pSPSInfo) {
    if (pSPSInfo == NULL) {
        ALOGE("pSPSInfo == NULL");
        return -EINVAL;
    }

    ABitReader br(seqParamSet + 1, size - 1);

    unsigned profile_idc = br.getBits(8);
    pSPSInfo->profile = profile_idc;
    br.skipBits(8);

    pSPSInfo->level = br.getBits(8);
    parseUE(&br);  // seq_parameter_set_id

    unsigned chroma_format_idc = 1;  // 4:2:0 chroma format

    if (profile_idc == 100 || profile_idc == 110
            || profile_idc == 122 || profile_idc == 244
            || profile_idc == 44 || profile_idc == 83 || profile_idc == 86) {
        chroma_format_idc = parseUE(&br);
        if (chroma_format_idc == 3) {
            br.skipBits(1);  // residual_colour_transform_flag
        }
        parseUE(&br);  // bit_depth_luma_minus8
        parseUE(&br);  // bit_depth_chroma_minus8
        br.skipBits(1);  // qpprime_y_zero_transform_bypass_flag
        // CHECK_EQ(br.getBits(1), 0u);  // seq_scaling_matrix_present_flag
        if (br.getBits(1) != 0) {
            ALOGW("seq_scaling_matrix_present_flag != 0");
            parse_seq_scaling_matrix_present(&br);
        }
    }

    parseUE(&br);  // log2_max_frame_num_minus4
    unsigned pic_order_cnt_type = parseUE(&br);

    if (pic_order_cnt_type == 0) {
        parseUE(&br);  // log2_max_pic_order_cnt_lsb_minus4
    } else if (pic_order_cnt_type == 1) {
        // offset_for_non_ref_pic, offset_for_top_to_bottom_field and
        // offset_for_ref_frame are technically se(v), but since we are
        // just skipping over them the midpoint does not matter.

        br.getBits(1);  // delta_pic_order_always_zero_flag
        parseUE(&br);  // offset_for_non_ref_pic
        parseUE(&br);  // offset_for_top_to_bottom_field

        unsigned num_ref_frames_in_pic_order_cnt_cycle = parseUE(&br);
        for (unsigned i = 0; i < num_ref_frames_in_pic_order_cnt_cycle; ++i) {
            parseUE(&br);  // offset_for_ref_frame
        }
    }

    parseUE(&br);  // num_ref_frames
    br.getBits(1);  // gaps_in_frame_num_value_allowed_flag

    unsigned pic_width_in_mbs_minus1 = parseUE(&br);
    unsigned pic_height_in_map_units_minus1 = parseUE(&br);
    unsigned frame_mbs_only_flag = br.getBits(1);

    pSPSInfo->width = pic_width_in_mbs_minus1 * 16 + 16;

    pSPSInfo->height = (2 - frame_mbs_only_flag)
        * (pic_height_in_map_units_minus1 * 16 + 16);

    if (!frame_mbs_only_flag) {
        br.getBits(1);  // mb_adaptive_frame_field_flag
    }

    br.getBits(1);  // direct_8x8_inference_flag

    if (br.getBits(1)) {  // frame_cropping_flag
        unsigned frame_crop_left_offset = parseUE(&br);
        unsigned frame_crop_right_offset = parseUE(&br);
        unsigned frame_crop_top_offset = parseUE(&br);
        unsigned frame_crop_bottom_offset = parseUE(&br);

        unsigned cropUnitX, cropUnitY;
        if (chroma_format_idc == 0  /* monochrome */) {
            cropUnitX = 1;
            cropUnitY = 2 - frame_mbs_only_flag;
        } else {
            unsigned subWidthC = (chroma_format_idc == 3) ? 1 : 2;
            unsigned subHeightC = (chroma_format_idc == 1) ? 2 : 1;

            cropUnitX = subWidthC;
            cropUnitY = subHeightC * (2 - frame_mbs_only_flag);
        }

        ALOGV("frame_crop = (%u, %u, %u, %u), cropUnitX = %u, cropUnitY = %u",
                frame_crop_left_offset, frame_crop_right_offset,
                frame_crop_top_offset, frame_crop_bottom_offset,
                cropUnitX, cropUnitY);

        pSPSInfo->width -=
            (frame_crop_left_offset + frame_crop_right_offset) * cropUnitX;
        pSPSInfo->height -=
            (frame_crop_top_offset + frame_crop_bottom_offset) * cropUnitY;
    }

    return OK;
}

bool SniffFLV(const sp<DataSource> &source, String8 *mimeType, float *confidence, sp<AMessage> *) {
    char header[12];
    if (source->readAt(0, header, sizeof(header)) != sizeof(header)) {
        return false;
    }

    if (memcmp(header, "FLV", 3)) {
        return false;
    } else {
        *mimeType = MEDIA_MIMETYPE_CONTAINER_FLV;
        *confidence = 0.8;
        return true;
    }

    return false;
}
}//end namespace android
#endif
