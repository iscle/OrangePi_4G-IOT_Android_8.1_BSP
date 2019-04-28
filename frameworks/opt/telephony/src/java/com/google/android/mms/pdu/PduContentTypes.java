/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2017. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */
/*
 * Copyright (C) 2007 Esmertec AG.
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.mms.pdu;

public class PduContentTypes {
    /*MTK Change access type*/
    /**
     * All content types. From:
     * http://www.openmobilealliance.org/tech/omna/omna-wsp-content-type.htm
     */
    public static final String[] contentTypes = {
        "*/*",                                        /* 0x00 */
        "text/*",                                     /* 0x01 */
        "text/html",                                  /* 0x02 */
        "text/plain",                                 /* 0x03 */
        "text/x-hdml",                                /* 0x04 */
        "text/x-ttml",                                /* 0x05 */
        "text/x-vCalendar",                           /* 0x06 */
        "text/x-vCard",                               /* 0x07 */
        "text/vnd.wap.wml",                           /* 0x08 */
        "text/vnd.wap.wmlscript",                     /* 0x09 */
        "text/vnd.wap.wta-event",                     /* 0x0A */
        "multipart/*",                                /* 0x0B */
        "multipart/mixed",                            /* 0x0C */
        "multipart/form-data",                        /* 0x0D */
        "multipart/byterantes",                       /* 0x0E */
        "multipart/alternative",                      /* 0x0F */
        "application/*",                              /* 0x10 */
        "application/java-vm",                        /* 0x11 */
        "application/x-www-form-urlencoded",          /* 0x12 */
        "application/x-hdmlc",                        /* 0x13 */
        "application/vnd.wap.wmlc",                   /* 0x14 */
        "application/vnd.wap.wmlscriptc",             /* 0x15 */
        "application/vnd.wap.wta-eventc",             /* 0x16 */
        "application/vnd.wap.uaprof",                 /* 0x17 */
        "application/vnd.wap.wtls-ca-certificate",    /* 0x18 */
        "application/vnd.wap.wtls-user-certificate",  /* 0x19 */
        "application/x-x509-ca-cert",                 /* 0x1A */
        "application/x-x509-user-cert",               /* 0x1B */
        "image/*",                                    /* 0x1C */
        "image/gif",                                  /* 0x1D */
        "image/jpeg",                                 /* 0x1E */
        "image/tiff",                                 /* 0x1F */
        "image/png",                                  /* 0x20 */
        "image/vnd.wap.wbmp",                         /* 0x21 */
        "application/vnd.wap.multipart.*",            /* 0x22 */
        "application/vnd.wap.multipart.mixed",        /* 0x23 */
        "application/vnd.wap.multipart.form-data",    /* 0x24 */
        "application/vnd.wap.multipart.byteranges",   /* 0x25 */
        "application/vnd.wap.multipart.alternative",  /* 0x26 */
        "application/xml",                            /* 0x27 */
        "text/xml",                                   /* 0x28 */
        "application/vnd.wap.wbxml",                  /* 0x29 */
        "application/x-x968-cross-cert",              /* 0x2A */
        "application/x-x968-ca-cert",                 /* 0x2B */
        "application/x-x968-user-cert",               /* 0x2C */
        "text/vnd.wap.si",                            /* 0x2D */
        "application/vnd.wap.sic",                    /* 0x2E */
        "text/vnd.wap.sl",                            /* 0x2F */
        "application/vnd.wap.slc",                    /* 0x30 */
        "text/vnd.wap.co",                            /* 0x31 */
        "application/vnd.wap.coc",                    /* 0x32 */
        "application/vnd.wap.multipart.related",      /* 0x33 */
        "application/vnd.wap.sia",                    /* 0x34 */
        "text/vnd.wap.connectivity-xml",              /* 0x35 */
        "application/vnd.wap.connectivity-wbxml",     /* 0x36 */
        "application/pkcs7-mime",                     /* 0x37 */
        "application/vnd.wap.hashed-certificate",     /* 0x38 */
        "application/vnd.wap.signed-certificate",     /* 0x39 */
        "application/vnd.wap.cert-response",          /* 0x3A */
        "application/xhtml+xml",                      /* 0x3B */
        "application/wml+xml",                        /* 0x3C */
        "text/css",                                   /* 0x3D */
        "application/vnd.wap.mms-message",            /* 0x3E */
        "application/vnd.wap.rollover-certificate",   /* 0x3F */
        "application/vnd.wap.locc+wbxml",             /* 0x40 */
        "application/vnd.wap.loc+xml",                /* 0x41 */
        "application/vnd.syncml.dm+wbxml",            /* 0x42 */
        "application/vnd.syncml.dm+xml",              /* 0x43 */
        "application/vnd.syncml.notification",        /* 0x44 */
        "application/vnd.wap.xhtml+xml",              /* 0x45 */
        "application/vnd.wv.csp.cir",                 /* 0x46 */
        "application/vnd.oma.dd+xml",                 /* 0x47 */
        "application/vnd.oma.drm.message",            /* 0x48 */
        "application/vnd.oma.drm.content",            /* 0x49 */
        "application/vnd.oma.drm.rights+xml",         /* 0x4A */
        "application/vnd.oma.drm.rights+wbxml",       /* 0x4B */
        "application/vnd.wv.csp+xml",                 /* 0x4C */
        "application/vnd.wv.csp+wbxml",               /* 0x4D */
        "application/vnd.syncml.ds.notification",     /* 0x4E */
        "audio/*",                                    /* 0x4F */
        "video/*",                                    /* 0x50 */
        "application/vnd.oma.dd2+xml",                /* 0x51 */
        "application/mikey"                           /* 0x52 */
    };
}
