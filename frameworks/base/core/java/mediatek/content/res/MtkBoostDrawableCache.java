/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 *
 * MediaTek Inc. (C) 2017. All rights reserved.
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
package mediatek.content.res;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.Slog;

public class MtkBoostDrawableCache {
    static final String TAG = "MtkBoostDrawableCache";

    private static final boolean DEBUG_CONFIG = false;
    private static final ArrayMap<String, LongSparseArray<Drawable.ConstantState>>
            sBoostDrawableCache = new ArrayMap<String, LongSparseArray<Drawable.ConstantState>>();
    private String mBoostKey = "";

    public MtkBoostDrawableCache() {
    }

    /**
     * Clear boost drawable cache when configure changed.
     * @param configChanges
     */
    public void onConfigurationChange(int configChanges) {
        if (isBoostApp(mBoostKey)) {
            LongSparseArray<Drawable.ConstantState> boostCache = sBoostDrawableCache.get(mBoostKey);
            if (boostCache != null) {
                clearBoostDrawableCacheLocked(boostCache, configChanges);
                Slog.w(TAG, "Clear boost cache");
            }
        }
    }

    /**
     * Clear boost drawable cache if configure changed.
     * @param cache
     * @param configChanges
     */
    public void clearBoostDrawableCacheLocked(
            LongSparseArray<Drawable.ConstantState> cache, int configChanges) {
        if (DEBUG_CONFIG) {
            Log.d(TAG, "Cleaning up boost drawables config changes: 0x"
                    + Integer.toHexString(configChanges));
        }
        final int N = cache.size();
        for (int i = 0; i < N; i++) {
            final Drawable.ConstantState cs = cache.valueAt(i);
            if (cs != null) {
                if (Configuration.needNewResources(configChanges,
                        cs.getChangingConfigurations())) {
                    if (DEBUG_CONFIG) {
                        Log.d(TAG, "FLUSHING #0x" + Long.toHexString(cache.keyAt(i)) + " / " + cs
                            + " with changes: 0x"+ Integer.toHexString(
                            cs.getChangingConfigurations()));
                    }
                    cache.setValueAt(i, null);
                } else if (DEBUG_CONFIG) {
                    Log.d(TAG, "(Keeping #0x" + Long.toHexString(cache.keyAt(i)) + " / " + cs
                        + " with changes: 0x" + Integer.toHexString(
                        cs.getChangingConfigurations()) + ")");
                }
            }
        }
    }

    /**
     * Get Boost Cache drawable from cache list if tencent mm package.
     * @param wrapper
     * @param key
     * @return
     */
    public Drawable getBoostCachedDrawable(Resources wrapper, long key) {
        mBoostKey = wrapper.toString().split("@")[0];
        final String boostKey = mBoostKey;
        if (isBoostApp(boostKey)) {
            LongSparseArray<Drawable.ConstantState> boostCache = sBoostDrawableCache.get(boostKey);
            if (boostCache != null) {
                final Drawable boostDrawable = getBoostCachedDrawableLocked(
                        wrapper, key, boostCache);
                if (boostDrawable != null) {
                    return boostDrawable;
                }
            }
        }
        // No cached drawable, we'll need to create a new one.
        return null;
    }

    public Drawable getBoostCachedDrawableLocked(Resources wrapper, long key,
            LongSparseArray<Drawable.ConstantState> drawableCache) {
        final Drawable.ConstantState entry = drawableCache.get(key);
        if (entry != null) {
            return entry.newDrawable(wrapper);
        } else {
            drawableCache.delete(key);
        }
        return null;
    }

    /**
     * Judge application name, get boost application if it is "com.tencent.mm".
     * @param appname
     * @return
     */
    public boolean isBoostApp(String appname) {
        if (appname.equals("android.content.res.Resources"))
            return false;

        String[] applist = { "com.tencent.mm" };
        for (String name : applist) {
            if (appname.contains(name))
                return true;
        }
        return false;
    }

    /**
     * Put drawable in boost cache if it is boost application's drawable.
     * @param key
     * @param cs
     */
    public void putBoostCache(long key, Drawable.ConstantState cs) {
        // /Boost cache on system @{
        if (isBoostApp(mBoostKey)) {
            LongSparseArray<Drawable.ConstantState> boostCache = sBoostDrawableCache
                    .get(mBoostKey);
            if (boostCache == null) {
                boostCache = new LongSparseArray<Drawable.ConstantState>(1);
                sBoostDrawableCache.put(mBoostKey, boostCache);
                for (String resKey : sBoostDrawableCache.keySet())
                    Slog.w(TAG, "ResourceKey:" + resKey);
            }
            boostCache.put(key, cs);
            Slog.w(TAG, "CacheKey:" + key + " Resource:" + mBoostKey);
        }
    }
}
