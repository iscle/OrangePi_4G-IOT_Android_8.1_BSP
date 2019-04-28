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
 * MediaTek Inc. (C) 2017. All rights reserved.
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

package com.mediatek.cta;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageParser;
import android.os.SystemProperties;

import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseBooleanArray;

import java.util.List;

/**
* CtaManager used by other module to access the CTA implemetation.
* It requires sub-class to implement related methods.
*/
public class CtaManager {

    private static String PLATFORM_PACKAGE_NAME = "android";

    /**
     * To create CTA permission controller
     *
     * @param context Caller process' context
     */
    public void createCtaPermsController(Context context) {
    }

    /**
     * Link the CTA defined runtime permission to related package
     *
     * @param pkg The package that needs to link CTA defined runtime permission
     */
    public void linkCtaPermissions(PackageParser.Package pkg) {
    }

    /**
     * To record all the runtime permission usage
     *
     * @param permName Permission name, including CTA defined runtime permission
     * @param uid Specify caller package's user ID
     */
    public void reportPermRequestUsage(String permName, int uid) {
    }

    /**
     * Shutdown all the sessions for CTA
     */
    public void shutdown() {
    }

    /**
     * To notify the system was ready
     */
    public void systemReady() {
    }

    /**
     * To check whether current package needs permission review.
     *
     * @param pkg The package to check
     * @param userId Specify caller package's user ID
     * @param reviewRequiredByCache review require state in cache
     * @return boolean True as needs review
     */
    public boolean isPermissionReviewRequired(PackageParser.Package pkg,
            int userId, boolean reviewRequiredByCache) {
        return false;
    }

    /**
     * Return all the package names that has recorded permission uage
     *
     * @return List<String> Package names that has recorded permission uage
     */
    public List<String> getPermRecordPkgs() {
        return null;
    }

    /**
     * Return all the recorded permissions for specify package
     *
     * @param packageName Package name
     * @return List<String> Permission list
     */
    public List<String> getPermRecordPerms(String packageName) {
        return null;
    }

    /**
     * Return all the recorded time for specify package and permission name
     *
     * @param packageName Package name
     * @param permName Permission name
     * @return List<Long> Time list
     */
    public List<Long> getRequestTimes(String packageName, String permName) {
        return null;
    }

    /**
     * Show permission error dialog
     *
     * @param context Caller process context
     * @param uid Caller user id
     * @param processName Process name
     * @param pkgName Package name
     * @param exceptionMsg Exception message
     * @return boolean True as dialog displayed
     */
    public boolean showPermErrorDialog(Context context, int uid, String processName,
                                       String pkgName, String exceptionMsg) {
        return false;
    }

    /**
     * Filter out the actions to be monitored.
     *
     * @param context Caller process context
     * @param intent The intent(action)
     * @param receivers Receiver list
     * @param uid Caller user id
     */
    public void filterReceiver(Context context,
                               Intent intent,
                               List<ResolveInfo> receivers,
                               int userId) {
    }

    /**
     * Get the CTA feature supporting status
     *
     * @return Boolean True as supported
     */
    public boolean isCtaSupported() {
        return false;
    }

    /**
     * To check whether the passed in permission is a CTA defined permission
     *
     * @param perm Permission
     * @return Boolean True indicates it's a CTA defined permission
     */
    public boolean isCtaOnlyPermission(String perm) {
        return false;
    }

    /**
     * To check whether the passed in permission is a CTA monitored permission
     *
     * @param perm Permission
     * @return Boolean True indicates it's a CTA monitored permission
     */
    public boolean isCtaMonitoredPerms(String perm) {
        return false;
    }

    /**
     * To check whether the passed in permission group of
     * specify package is a platform permission group.
     *
     * @param pkgName Package name
     * @param groupName Permission group name
     * @return Boolean True indicates it's a platform permission group
     */
    public boolean isPlatformPermissionGroup(String pkgName, String groupName) {
        if (pkgName != null && PLATFORM_PACKAGE_NAME.equals(pkgName)) {
            return true;
        } else {
            return false;
        }
    }

    public String[] getCtaAddedPermissionGroups() {
        return null;
    }

    public boolean enforceCheckPermission(final String permission, final String action) {
        return false;
    }

    public boolean enforceCheckPermission(final String pkgName, final String permission,
            final String action) {
        return false;
    }

    /**
     * To check whether the passed in permission of
     * specify package is a platform permission.
     *
     * @param pkgName Package name
     * @param permName Permission name
     * @return boolean True indicates it's a platform permission
     */
    public boolean isPlatformPermission(String pkgName, String permName) {
        if (pkgName != null && PLATFORM_PACKAGE_NAME.equals(pkgName)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * To check whether the passed in package is a system application.
     *
     * @param context Caller process'context
     * @param pkgName Package name to be checked
     * @return boolean True indicates it's a system application
     */
    public boolean isSystemApp(Context context ,String pkgName) {
        return false;
    }

    /**
     * To check whether needs to grant CTA defined runtime permission.
     *
     * @param isUpdated Indicates whether the caller process updated
     * @param targetSdkVersion Caller application's SDK version
     * @return boolean True as needs to grant
     */
    public boolean needGrantCtaRuntimePerm(boolean isUpdated, int targetSdkVersion) {
        return false;
    }

    /**
     * Return all the CTA defined runtime permissions.
     *
     * @return String[] CTA defined permission list
     */
    public String[] getCtaOnlyPermissions() {
        return null;
    }

    /**
     * Retrieve the op switch that controls the given operation.
     *
     * @param op The operation id
     * @return int Value after switched
     */
    public int opToSwitch(int op) {
        return -1;
    }

    /**
     * Retrieve a non-localized name for the operation, for debugging output.
     *
     * @param op The operation id
     * @return String Name of specify operation
     */
    public String opToName(int op) {
        return null;
    }

    /**
     * Retrieve the index in the sOpNamew array.
     *
     * @param op The operation's name
     * @return int The retrieve index
     */
    public int strDebugOpToOp(String op) {
        return -1;
    }

    /**
     * Retrieve the permission associated with an operation, or null if there is not one.
     *
     * @param op The operation id
     * @return String The permission associated with an operation
     */
    public String opToPermission(int op) {
        return null;
    }

    /**
     * Retrieve the user restriction associated with an operation, or null if there is not one.
     *
     * @param op The operation id
     * @return String The user restriction associated with an operation
     */
    public String opToRestriction(int op) {
        return null;
    }

    /**
     * Retrieve the app op code for a permission, or null if there is not one.
     * This API is intended to be used for mapping runtime or appop permissions
     * to the corresponding app op.
     *
     * @param permission The passed in permission
     * @return int The app op code for a permission
     */
    public int permissionToOpCode(String permission) {
        return -1;
    }

    /**
     * Retrieve whether the op allows the system (and system ui) to
     * bypass the user restriction.
     *
     * @param op The passed in operation id
     *
     * @return boolean True as allows the system (and system ui)
     *                 to bypass the user restriction
     */
    public boolean opAllowSystemBypassRestriction(int op) {
        return false;
    }

    /**
     * Retrieve the default mode for the operation.
     *
     * @param op The passed in operation id
     * @return int The default mode
     */
    public int opToDefaultMode(int op) {
        return -1;
    }

    /**
     * Retrieve whether the op allows itself to be reset.
     *
     * @param op The passed in operation id
     * @return boolean True as allows itself to be reset.
     */
    public boolean opAllowsReset(int op) {
        return false;
    }

    /**
     * Gets the app op name associated with a given permission.
     * The app op name is one of the public constants defined
     * in this class such as {@link #OPSTR_COARSE_LOCATION}.
     * This API is intended to be used for mapping runtime
     * permissions to the corresponding app op.
     *
     * @param permission The permission.
     * @return The app op associated with the permission or null.
     */
    public String permissionToOp(String permission) {
        return null;
    }

    /**
     * Retrieve the op id by string op
     *
     * @param op op in string
     * @return int op in integer
     */
    public int strOpToOp(String op) {
        return -1;
    }

    /**
     * Retrieve the string op by op id
     *
     * @param op op in integer
     * @return String op in string
     */
    public String getsOpToString(int op) {
        return null;
    }

    /**
     * To check whether need clear review flag after upgrade
     *
     * @param updatedPkgReviewRequired whether requires to update states
     * @param pkg Packge
     * @param name permission name
     * @return boolean True as  needs clear review flag.
     */
    public boolean needClearReviewFlagAfterUpgrade(boolean updatedPkgReviewRequired,
        String pkg, String name) {
        return false;
    }

    /**
     * Retrieve the op count.
     *
     * @return int op count
     */
    public int getOpNum() {
        return 0;
    }
}
