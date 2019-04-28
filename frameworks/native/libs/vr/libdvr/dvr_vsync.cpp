#include "include/dvr/dvr_vsync.h"

#include <utils/Log.h>

#include <private/dvr/vsync_client.h>

extern "C" {

struct DvrVSyncClient {
  std::unique_ptr<android::dvr::VSyncClient> client;
};

int dvrVSyncClientCreate(DvrVSyncClient** client_out) {
  auto client = android::dvr::VSyncClient::Create();
  if (!client) {
    ALOGE("dvrVSyncClientCreate: Failed to create vsync client!");
    return -EIO;
  }

  *client_out = new DvrVSyncClient{std::move(client)};
  return 0;
}

void dvrVSyncClientDestroy(DvrVSyncClient* client) { delete client; }

int dvrVSyncClientGetSchedInfo(DvrVSyncClient* client, int64_t* vsync_period_ns,
                               int64_t* next_timestamp_ns,
                               uint32_t* next_vsync_count) {
  return client->client->GetSchedInfo(vsync_period_ns, next_timestamp_ns,
                                      next_vsync_count);
}

}  // extern "C"
