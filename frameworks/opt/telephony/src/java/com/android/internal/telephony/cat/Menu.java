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

package com.android.internal.telephony.cat;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * Container class for CAT menu (SET UP MENU, SELECT ITEM) parameters.
 *
 */
public class Menu implements Parcelable {
    public List<Item> items;
    public List<TextAttribute> titleAttrs;
    public PresentationType presentationType;
    public String title;
    public Bitmap titleIcon;
    public int defaultItem;
    public boolean softKeyPreferred;
    public boolean helpAvailable;
    public boolean titleIconSelfExplanatory;
    public boolean itemsIconSelfExplanatory;

    public Menu() {
        // Create an empty list.
        items = new ArrayList<Item>();
        title = null;
        titleAttrs = null;
        defaultItem = 0;
        softKeyPreferred = false;
        helpAvailable = false;
        titleIconSelfExplanatory = false;
        itemsIconSelfExplanatory = false;
        titleIcon = null;
        // set default style to be navigation menu.
        presentationType = PresentationType.NAVIGATION_OPTIONS;
    }
    // MTK-START
    protected /*private*/ Menu(Parcel in) {
    // MTK-END
        title = in.readString();
        titleIcon = in.readParcelable(null);
        // rebuild items list.
        items = new ArrayList<Item>();
        int size = in.readInt();
        for (int i=0; i<size; i++) {
            Item item = in.readParcelable(null);
            items.add(item);
        }
        defaultItem = in.readInt();
        softKeyPreferred = in.readInt() == 1 ? true : false;
        helpAvailable = in.readInt() == 1 ? true : false;
        titleIconSelfExplanatory = in.readInt() == 1 ? true : false;
        itemsIconSelfExplanatory = in.readInt() == 1 ? true : false;
        presentationType = PresentationType.values()[in.readInt()];
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(title);
        dest.writeParcelable(titleIcon, flags);
        // write items list to the parcel.
        int size = items.size();
        dest.writeInt(size);
        for (int i=0; i<size; i++) {
            dest.writeParcelable(items.get(i), flags);
        }
        dest.writeInt(defaultItem);
        dest.writeInt(softKeyPreferred ? 1 : 0);
        dest.writeInt(helpAvailable ? 1 : 0);
        dest.writeInt(titleIconSelfExplanatory ? 1 : 0);
        dest.writeInt(itemsIconSelfExplanatory ? 1 : 0);
        dest.writeInt(presentationType.ordinal());
    }

    public static final Parcelable.Creator<Menu> CREATOR = new Parcelable.Creator<Menu>() {
        @Override
        public Menu createFromParcel(Parcel in) {
            return new Menu(in);
        }

        @Override
        public Menu[] newArray(int size) {
            return new Menu[size];
        }
    };
}
