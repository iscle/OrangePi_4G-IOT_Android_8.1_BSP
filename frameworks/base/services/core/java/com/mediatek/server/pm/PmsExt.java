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
package com.mediatek.server.pm;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInfoLite;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;

import com.android.server.pm.PackageManagerException;
import com.android.server.pm.PackageManagerService;
import com.android.server.pm.PackageSetting;
import com.android.server.pm.Settings;
import com.android.server.pm.UserManagerService;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class PmsExt {

    public static final int INDEX_CIP_FW = 1;
    public static final int INDEX_VENDOR_FW = 2;
    public static final int INDEX_VENDOR_PRIV = 3;
    public static final int INDEX_OP_APP = 4;
    public static final int INDEX_ROOT_PLUGIN = 5;
    public static final int INDEX_VENDOR_PLUGIN = 6;
    public static final int INDEX_CUSTOM_APP = 7;
    public static final int INDEX_CUSTOM_PLUGIN = 8;

    public PmsExt() {
    }

    public boolean isVendorApp(PackageSetting ps) {
        return false;
    }

    public boolean isVendorApp(PackageParser.Package pkg) {
        return false;
    }

    public boolean isVendorApp(ApplicationInfo info) {
        return false;
    }

    public void init(PackageManagerService pms, UserManagerService ums) {
    }

    public void scanDirLI(int ident, int defParseFlags, int defScanFlags,
            long currentTime) {
    }

    public void carrierExpressInstall(int defParseFlags, int defScanFlags,
            long currentTime) {
    }

    public boolean isNotOperatorApp(PackageSetting ps) {
        return true;
    }

    public void clearExtFlags(PackageParser.Package deletedPkg,
            PackageSetting deletedPs) {
    }

    public boolean needSkipScanning(PackageParser.Package pkg,
            PackageSetting updatedPkg, PackageSetting ps, File scanFile) {
        return false;
    }

    public boolean needSkipForGetInstalledApps(ApplicationInfo ai,
            PackageSetting ps, int userId) {
        return false;
    }

    public boolean updateUpdatedSysPkgFlag(boolean isUpdatedSystemPkg,
            boolean isUpdatedPkg, int policyFlags) {
        return isUpdatedSystemPkg;
    }

    public boolean allowDowngrade(PackageParser.Package pkg, int policyFlags) {
        return false;
    }

    public int updatePolicyFlagsForUpdatedPkg(int policyFlags,
            PackageSetting updatedPkg, PackageSetting ps) {
        return policyFlags;
    }

    public void setExtFlags(PackageSetting pkgSetting, PackageParser.Package pkg) {
    }

    public int updateUpdatedSystemAppFlag(int flags, Settings setting,
            PackageParser.Package pkg) {
        return flags;
    }

    public boolean isNotParseOperator(int policyFlags) {
        return true;
    }

    public void setExtFlags(int policyFlags, PackageParser.Package pkg) {
    }

    public void checkMtkResPkg(PackageParser.Package pkg)
            throws PackageManagerException {
    }

    public boolean updateNativeLibDir(ApplicationInfo info, String codePath,
            File libInstallDir) {
        return false;
    }

    public boolean replaceOperatorPkgIfNeeded(
            PackageParser.Package deletedPackage, PackageParser.Package pkg,
            int policyFlags, int scanFlags, UserHandle user, int[] allUsers,
            String installerPackageName,
            PackageManagerService.PackageInstalledInfo res, int installReason) {
        return false;
    }

    public Bundle replaceSysPkgPrepare(
            ArrayMap<String, PackageParser.Package> packages,
            ArrayMap<String, PackageSetting> settingsPackages,
            PackageParser.Package deletedPackage) {
        return null;
    }

    public void updateUpdatedSystemAppFlagForReplace(Bundle data,
            PackageParser.Package pkg) {
    }

    public boolean scanVendorPackageForReplaceIfNeeded(Bundle data,
            PackageParser.Package pkg, int policyFlags, int scanFlags,
            long currentTime, UserHandle user) throws PackageManagerException {
        return false;
    }

    public void replaceSysPkgDone(Bundle data,
            ArrayMap<String, PackageSetting> settingsPackages) {
    }

    public boolean isOperatorApp(
            ArrayMap<String, PackageParser.Package> packages,
            ArrayMap<String, PackageSetting> settingsPackages, String pkgName) {
        return false;
    }

    public int updateInstallSysPartFlag(int parseFlags, File codePath) {
        return parseFlags;
    }

    public boolean customizeDeteleSysPkg(Settings setting, String packageName) {
        return false;
    }

    public void deleteSysPkgDone(PackageSetting ps,
            PackageManagerService.PackageRemovedInfo outInfo, UserHandle user,
            ArrayMap<String, PackageSetting> settingsPackages,
            String packageName) {
    }

    public boolean confirmCanHaveOatDir(PackageParser.Package p,
            boolean defValue) {
        return defValue;
    }

    public void settingsWriteDisabledSysPkgEnhance(XmlSerializer serializer,
            PackageSetting pkg) throws IOException {
    }

    public void settingsWritePkgEnhance(XmlSerializer serializer,
            PackageSetting pkg) throws IOException {
    }

    public Bundle settingsReadDisabledSysPkgEnhance(XmlPullParser parser) {
        return null;
    }

    public int updateSettingsReadDisabledSysPkgFlags(Bundle data, int flags,
            File codePathFile) {
        return flags;
    }

    public void settingsReadPkgEnhance(PackageSetting packageSetting,
            XmlPullParser parser) {
    }

    public void settingsPrintFlagExt(PrintWriter pw, String prefix,
            PackageSetting ps) {
    }

    public boolean needSkipAppInfo(Bundle data, ApplicationInfo ai, PackageSetting ps, int userId) {
        return false;
    }

    public void updatePackageSetting(Bundle data, PackageSetting ps) {
    }

    public void onPackageAdded(String packageName, int userId) {
    }

    public void initBeforeScan() {
    }

    public void initAfterScan(ArrayMap<String, PackageSetting> settingsPackages) {
    }

    public int customizeInstallPkgFlags(int installFlags,
            PackageInfoLite pkgLite,
            ArrayMap<String, PackageSetting> settingsPackages, UserHandle user) {
        return installFlags;
    }

    public void updatePackageSettings(int userId, String pkgName,
            PackageParser.Package newPackage, PackageSetting ps,
            int[] allUsers, String installerPackageName) {
    }

    public int customizeDeletePkgFlags(int deleteFlags, String packageName) {
        return deleteFlags;
    }

    public int customizeDeletePkg(int[] users, String packageName,
            int versionCode, int delFlags) {
        return PackageManager.DELETE_SUCCEEDED;
    }

    public boolean dumpCmdHandle(String cmd, PrintWriter pw, String[] args,
            int opti) {
        return false;
    }

    public ApplicationInfo updateApplicationInfoForRemovable(
            ApplicationInfo oldAppInfo) {
        return oldAppInfo;
    }

    public ApplicationInfo updateApplicationInfoForRemovable(String nameForUid,
            ApplicationInfo oldAppInfo) {
        return oldAppInfo;
    }

    public ActivityInfo updateActivityInfoForRemovable(ActivityInfo info)
            throws RemoteException {
        return info;
    }

    public List<ResolveInfo> updateResolveInfoListForRemovable(
            List<ResolveInfo> apps) throws RemoteException {
        return apps;
    }

    public PackageInfo updatePackageInfoForRemovable(PackageInfo oldPkgInfo) {
        return oldPkgInfo;
    }

    public boolean needSkipRemovableSystemApp(ApplicationInfo appInfo) {
        return false;
    }

    public boolean isRemovableSysApp(String pkgName) {
        return false;
    }
}
