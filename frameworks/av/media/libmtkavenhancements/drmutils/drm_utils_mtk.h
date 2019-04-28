
#include <drm/DrmManagerClient.h>  // OMA DRM v1 implementation
#include <media/mediascanner.h>


namespace android {

bool IsOMADrm(int fd);
bool IsOMADrm(const char *url);
String8 GetDrmProc(int pid);
bool DrmCheck(String8 DrmProc, int fd);
bool DrmCheck(String8 DrmProc, const char * url);
void ConsumeRight(sp<DecryptHandle> mDecryptHandle, DrmManagerClient *mDrmManagerClient, String8 mDrmProcName);
MediaScanResult ScanDcf(const char *path, MediaScannerClient &client, bool *isOMADrmDcf);

}  // namespace android

