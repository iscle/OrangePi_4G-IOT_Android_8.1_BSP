#ifndef ANDROID_DVR_SERVICES_DISPLAYD_VSYNC_SERVICE_H_
#define ANDROID_DVR_SERVICES_DISPLAYD_VSYNC_SERVICE_H_

#include <pdx/service.h>

#include <list>
#include <memory>
#include <mutex>
#include <thread>

#include "display_service.h"

namespace android {
namespace dvr {

// VSyncWaiter encapsulates a client blocked waiting for the next vsync.
// It is used to enqueue the Message to reply to when the next vsync event
// occurs.
class VSyncWaiter {
 public:
  explicit VSyncWaiter(pdx::Message& message) : message_(std::move(message)) {}

  void Notify(int64_t timestamp);

 private:
  pdx::Status<int64_t> OnWait(pdx::Message& message);

  pdx::Message message_;
  int64_t timestamp_ = 0;

  VSyncWaiter(const VSyncWaiter&) = delete;
  void operator=(const VSyncWaiter&) = delete;
};

// VSyncChannel manages the service-side per-client context for each client
// using the service.
class VSyncChannel : public pdx::Channel {
 public:
  VSyncChannel(pdx::Service& service, int pid, int cid)
      : service_(service), pid_(pid), cid_(cid) {}

  void Ack();
  void Signal();

 private:
  pdx::Service& service_;
  pid_t pid_;
  int cid_;

  VSyncChannel(const VSyncChannel&) = delete;
  void operator=(const VSyncChannel&) = delete;
};

// VSyncService implements the displayd vsync service over ServiceFS.
class VSyncService : public pdx::ServiceBase<VSyncService> {
 public:
  ~VSyncService() override;

  pdx::Status<void> HandleMessage(pdx::Message& message) override;

  std::shared_ptr<pdx::Channel> OnChannelOpen(pdx::Message& message) override;
  void OnChannelClose(pdx::Message& message,
                      const std::shared_ptr<pdx::Channel>& channel) override;

  // Called by the hardware composer HAL, or similar, whenever a vsync event
  // occurs. |compositor_time_ns| is the number of ns before the next vsync when
  // the compositor will preempt the GPU to do EDS and lens warp.
  void VSyncEvent(int display, int64_t timestamp_ns, int64_t compositor_time_ns,
                  uint32_t vsync_count);

 private:
  friend BASE;

  VSyncService();

  pdx::Status<int64_t> OnGetLastTimestamp(pdx::Message& message);
  pdx::Status<display::VSyncSchedInfo> OnGetSchedInfo(pdx::Message& message);
  pdx::Status<void> OnAcknowledge(pdx::Message& message);

  void NotifierThreadFunction();

  void AddWaiter(pdx::Message& message);
  void NotifyWaiters();
  void UpdateClients();

  void AddClient(const std::shared_ptr<VSyncChannel>& client);
  void RemoveClient(const std::shared_ptr<VSyncChannel>& client);

  int64_t last_vsync_;
  int64_t current_vsync_;
  int64_t compositor_time_ns_;
  uint32_t current_vsync_count_;

  std::mutex mutex_;

  std::list<std::unique_ptr<VSyncWaiter>> waiters_;
  std::list<std::shared_ptr<VSyncChannel>> clients_;

  VSyncService(const VSyncService&) = delete;
  void operator=(VSyncService&) = delete;
};

}  // namespace dvr
}  // namespace android

#endif  // ANDROID_DVR_SERVICES_DISPLAYD_VSYNC_SERVICE_H_
