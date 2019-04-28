#ifndef ANDROID_DVR_VSYNC_CLIENT_H_
#define ANDROID_DVR_VSYNC_CLIENT_H_

#include <stdint.h>

#include <pdx/client.h>

struct dvr_vsync_client {};

namespace android {
namespace dvr {

/*
 * VSyncClient is a remote interface to the vsync service in displayd.
 * This class is used to wait for and retrieve information about the
 * display vsync.
 */
class VSyncClient : public pdx::ClientBase<VSyncClient>,
                    public dvr_vsync_client {
 public:
  /*
   * Wait for the next vsync signal.
   * The timestamp (in ns) is written into *ts when ts is non-NULL.
   */
  int Wait(int64_t* timestamp_ns);

  /*
   * Returns the file descriptor used to communicate with the vsync system
   * service or -1 on error.
   */
  int GetFd();

  /*
   * Clears the select/poll/epoll event so that subsequent calls to
   * these will not signal until the next vsync.
   */
  int Acknowledge();

  /*
   * Get the timestamp of the last vsync event in ns. This call has
   * the same side effect on events as Acknowledge(), which saves
   * an IPC message.
   */
  int GetLastTimestamp(int64_t* timestamp_ns);

  /*
   * Get vsync scheduling info.
   * Get the estimated timestamp of the next GPU lens warp preemption event in
   * ns. Also returns the corresponding vsync count that the next lens warp
   * operation will target. This call has the same side effect on events as
   * Acknowledge(), which saves an IPC message.
   */
  int GetSchedInfo(int64_t* vsync_period_ns, int64_t* next_timestamp_ns,
                   uint32_t* next_vsync_count);

 private:
  friend BASE;

  VSyncClient();
  explicit VSyncClient(long timeout_ms);

  VSyncClient(const VSyncClient&) = delete;
  void operator=(const VSyncClient&) = delete;
};

}  // namespace dvr
}  // namespace android

#endif  // ANDROID_DVR_VSYNC_CLIENT_H_
