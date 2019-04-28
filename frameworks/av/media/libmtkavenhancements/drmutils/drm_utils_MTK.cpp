

#define LOG_TAG "DrmUtilNative"
#include <fcntl.h>

#include <drm/DrmInfoRequest.h>
#include <drm/DrmMtkUtil.h>
#include <drm/DrmMetadata.h>
#include <drm/DrmMtkDef.h>
#include <utils/String8.h>
#include <utils/Log.h>
#include "drm_utils_mtk.h"
#include <media/mediametadataretriever.h>


namespace android {

bool IsOMADrm(int fd) {
    return DrmMtkUtil::isDcf(fd);
}

bool IsOMADrm(const  char * url) {
    String8 path(url);
    return DrmMtkUtil::isDcf(path);
}

String8 GetDrmProc(int pid) {
    return DrmMtkUtil::getProcessName(pid);
}

bool DrmCheck(String8 DrmProc, int fd) {
    if (!DrmMtkUtil::isTrustedClient(DrmProc)) {
        ALOGW("setDataSource with fd: untrusted client [%s], denied to access drm fd [%d]",
                 DrmProc.string(), fd);
        return UNKNOWN_ERROR;
    }
    // If OMA DRM file, try to trigger check whether need show drm dialog
    android::DrmManagerClient* drmManagerClient = new DrmManagerClient();
    const int infoType = 2021;  // DrmRequestType::TYPE_SET_DRM_INFO
    const String8 mimeType = String8("application/vnd.oma.drm.content");
    DrmInfoRequest* drmInfoRequest = new DrmInfoRequest(infoType, mimeType);
    drmInfoRequest->put(String8("action"), String8("showDrmDialogIfNeed"));
    char fdStr[32] = {0};
    sprintf(fdStr, "FileDescriptor[%d]", fd);
    drmInfoRequest->put(String8("FileDescriptorKey"), String8(fdStr));
    drmManagerClient->acquireDrmInfo(drmInfoRequest);
    delete drmManagerClient; drmManagerClient = NULL;
    delete drmInfoRequest; drmInfoRequest = NULL;

    return OK;
}

bool DrmCheck(String8 DrmProc, const  char * url) {
    String8 path(url);

    if (!DrmMtkUtil::isTrustedClient(DrmProc)) {
        ALOGW("setDataSource with url: untrusted client [%s], denied to access drm source [%s]",
             DrmProc.string(), path.string());
        return UNKNOWN_ERROR;
    }

    // If OMA DRM file, try to trigger check whether need show drm dialog
    int dcfFd = open(path.string(), O_RDONLY);
    if (dcfFd != -1) {
        DrmManagerClient* drmManagerClient = new DrmManagerClient();
        const int infoType = 2021;  // DrmRequestType::TYPE_SET_DRM_INFO
        const String8 mimeType = String8("application/vnd.oma.drm.content");
        DrmInfoRequest* drmInfoRequest = new DrmInfoRequest(infoType, mimeType);
        drmInfoRequest->put(String8("action"), String8("showDrmDialogIfNeed"));
        char fdStr[32] = {0};
        sprintf(fdStr, "FileDescriptor[%d]", dcfFd);
        drmInfoRequest->put(String8("FileDescriptorKey"), String8(fdStr));
        drmManagerClient->acquireDrmInfo(drmInfoRequest);
        close(dcfFd);
        delete drmManagerClient; drmManagerClient = NULL;
        delete drmInfoRequest; drmInfoRequest = NULL;
        return OK;
    } else {
        ALOGD("open dcffd fail");
        return UNKNOWN_ERROR;
    }
}
void ConsumeRight(sp<DecryptHandle> mDecryptHandle, DrmManagerClient *mDrmManagerClient, String8 mDrmProcName) {
    if (mDecryptHandle != NULL && DecryptApiType::CONTAINER_BASED == mDecryptHandle->decryptApiType) {
        mDecryptHandle->extendedData.add(String8("clientProcName"), mDrmProcName);
        mDrmManagerClient->consumeRights(mDecryptHandle, 0x01, false);
        ALOGD("%s consumeRights done", __FUNCTION__);
    }
}

MediaScanResult ScanDcf(const char *path, MediaScannerClient &client, bool *isOMADrmDcf ) {
        * isOMADrmDcf = false;
        String8 tmp(path);
        DrmManagerClient* drmManagerClient = new DrmManagerClient();
        DrmMetadata* dcfMetadata = drmManagerClient->getMetadata(&tmp);

        if (dcfMetadata == NULL) {
            ALOGW("scan: OMA DRM v1: failed to get drm metadata, not scanned into db.");
            delete drmManagerClient;
            client.setMimeType("bad mime type");
            return MEDIA_SCAN_RESULT_SKIPPED;
        }

        struct Map {
            const char* from;
            int to;
        };
        static const Map kMap[] = {
            {DrmMetaKey::META_KEY_IS_DRM,
                METADATA_KEY_IS_DRM}, // "is_drm"
            {DrmMetaKey::META_KEY_CONTENT_URI,
                METADATA_KEY_DRM_CONTENT_URI},
            {DrmMetaKey::META_KEY_OFFSET,
                METADATA_KEY_DRM_OFFSET},
            {DrmMetaKey::META_KEY_DATALEN,
                METADATA_KEY_DRM_DATALEN},
            {DrmMetaKey::META_KEY_RIGHTS_ISSUER,
                METADATA_KEY_DRM_RIGHTS_ISSUER},
            {DrmMetaKey::META_KEY_CONTENT_NAME,
                METADATA_KEY_DRM_CONTENT_NAME},
            {DrmMetaKey::META_KEY_CONTENT_DESCRIPTION,
                METADATA_KEY_DRM_CONTENT_DES},
            {DrmMetaKey::META_KEY_CONTENT_VENDOR,
                METADATA_KEY_DRM_CONTENT_VENDOR},
            {DrmMetaKey::META_KEY_ICON_URI,
                METADATA_KEY_DRM_ICON_URI} ,
            {DrmMetaKey::META_KEY_METHOD,
                METADATA_KEY_DRM_METHOD},
            {DrmMetaKey::META_KEY_MIME,
                METADATA_KEY_DRM_MIME}
        };
        static const size_t kNumMapEntries = sizeof(kMap) / sizeof(kMap[0]);

        int action = Action::PLAY;
        String8 type;
        for (size_t i = 0; i < kNumMapEntries; ++i) {
            String8 value = dcfMetadata->get(String8(kMap[i].from));
            if (value.length() != 0) {
                if (kMap[i].to == METADATA_KEY_DRM_MIME) {
                    value = DrmMtkUtil::toCommonMime(value.string());
                    // not audio/video/image -> not scan into db
                    type.setTo(value.string(), 6);
                    if (0 != strcasecmp(type.string(), "audio/")
                        && 0 != strcasecmp(type.string(), "video/")
                        && 0 != strcasecmp(type.string(), "image/")) {
                        ALOGW("scan: OMA DRM v1: invalid drm media file mime type[%s], not added into db.",
                                value.string());
                        delete dcfMetadata;
                        delete drmManagerClient;
                        client.setMimeType("bad mime type");
                        return MEDIA_SCAN_RESULT_SKIPPED;
                    }

                    client.setMimeType(value.string());
                    ALOGD("scan: OMA DRM v1: drm original mime type[%s].",
                            value.string());

                    // determine the Action it shall used.
                    if ((0 == strcasecmp(type.string(), "audio/"))
                        || (0 == strcasecmp(type.string(), "video/"))) {
                        action = Action::PLAY;
                    } else if ((0 == strcasecmp(type.string(), "image/"))) {
                        action = Action::DISPLAY;
                    }
                }

                if (kMap[i].to == METADATA_KEY_IS_DRM) {
                    * isOMADrmDcf = (value == String8("1"));
                }

                client.addStringTag(kMap[i].from, value.string());
                ALOGD("scan: OMA DRM v1: client.addString tag[%s] value[%s].",
                        kMap[i].from, value.string());
            }
        }

        // if there's no valid rights for this file currently, just return OK
        // to make sure it can be scanned into db.
        if (* isOMADrmDcf
            && RightsStatus::RIGHTS_VALID != drmManagerClient->checkRightsStatus(tmp, action)) {
            ALOGD("scan: OMA DRM v1: current no valid rights, return OK so that it can be added into db.");
            delete dcfMetadata;
            delete drmManagerClient;
            return MEDIA_SCAN_RESULT_OK;
        }

        // when there's valid rights, should contine to add extra metadata
        ALOGD("scan: OMA DRM v1: current valid rights, continue to add extra info.");
        delete dcfMetadata; dcfMetadata = NULL;
        delete drmManagerClient; drmManagerClient = NULL;

        // if picture then we need not to scan with extractors.
        if (* isOMADrmDcf && 0 == strcasecmp(type.string(), "image/")) {
            ALOGD("scan: OMA DRM v1: for DRM image we do not sniff with extractors.");
            return MEDIA_SCAN_RESULT_OK;
        }
        return MEDIA_SCAN_RESULT_OK;
    }
}  //  namespace android







































