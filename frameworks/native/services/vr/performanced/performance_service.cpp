#include "performance_service.h"

#include <sched.h>
#include <sys/prctl.h>
#include <unistd.h>

#include <pdx/default_transport/service_endpoint.h>
#include <pdx/rpc/argument_encoder.h>
#include <pdx/rpc/message_buffer.h>
#include <pdx/rpc/remote_method.h>
#include <private/android_filesystem_config.h>
#include <private/dvr/performance_rpc.h>
#include <private/dvr/trusted_uids.h>

#include "task.h"

// This prctl is only available in Android kernels.
#define PR_SET_TIMERSLACK_PID 41

using android::dvr::IsTrustedUid;
using android::dvr::Task;
using android::pdx::ErrorStatus;
using android::pdx::Message;
using android::pdx::Status;
using android::pdx::default_transport::Endpoint;
using android::pdx::rpc::DispatchRemoteMethod;

namespace {

const char kCpuSetBasePath[] = "/dev/cpuset";

const char kRootCpuSet[] = "/";

constexpr unsigned long kTimerSlackForegroundNs = 50000;
constexpr unsigned long kTimerSlackBackgroundNs = 40000000;

// Expands the given parameter pack expression using an initializer list to
// guarantee ordering and a comma expression to guarantee even void expressions
// are valid elements of the initializer list.
#define EXPAND_PACK(...) \
  std::initializer_list<int> { (__VA_ARGS__, 0)... }

// Returns true if the sender's euid matches any of the uids in |UIDs|.
template <uid_t... UIDs>
struct UserId {
  static bool Check(const Message& sender, const Task&) {
    const uid_t uid = sender.GetEffectiveUserId();
    bool allow = false;
    EXPAND_PACK(allow |= (uid == UIDs));
    return allow;
  }
};

// Returns true if the sender's egid matches any of the gids in |GIDs|.
template <gid_t... GIDs>
struct GroupId {
  static bool Check(const Message& sender, const Task&) {
    const gid_t gid = sender.GetEffectiveGroupId();
    bool allow = false;
    EXPAND_PACK(allow |= (gid == GIDs));
    return allow;
  }
};

// Returns true if the sender's euid is trusted according to VR manager service.
struct Trusted {
  static bool Check(const Message& sender, const Task&) {
    return IsTrustedUid(sender.GetEffectiveUserId());
  }
};

// Returns returns true if the task belongs to the sending process.
struct SameProcess {
  static bool Check(const Message& sender, const Task& task) {
    return sender.GetProcessId() == task.thread_group_id();
  }
};

// Returns true if any of the checks in |Allows| pass, false otherwise.
template <typename... Allows>
struct CheckOr {
  static bool Check(const Message& sender, const Task& task) {
    bool allow = false;
    EXPAND_PACK(allow |= Allows::Check(sender, task));
    return allow;
  }
};

// Returns true if all of the checks in |Allows| pass, false otherwise.
template <typename... Allows>
struct CheckAnd {
  static bool Check(const Message& sender, const Task& task) {
    bool allow = true;
    EXPAND_PACK(allow &= Allows::Check(sender, task));
    return allow;
  }
};

}  // anonymous namespace

namespace android {
namespace dvr {

PerformanceService::PerformanceService()
    : BASE("PerformanceService",
           Endpoint::Create(PerformanceRPC::kClientPath)) {
  cpuset_.Load(kCpuSetBasePath);

  Task task(getpid());
  ALOGI("Running in cpuset=%s uid=%d gid=%d", task.GetCpuSetPath().c_str(),
        task.user_id()[Task::kUidReal], task.group_id()[Task::kUidReal]);

  // Errors here are checked in IsInitialized().
  sched_fifo_min_priority_ = sched_get_priority_min(SCHED_FIFO);
  sched_fifo_max_priority_ = sched_get_priority_max(SCHED_FIFO);

  const int fifo_range = sched_fifo_max_priority_ - sched_fifo_min_priority_;
  const int fifo_low = sched_fifo_min_priority_;
  const int fifo_medium = sched_fifo_min_priority_ + fifo_range / 5;

  // TODO(eieio): Make this configurable on the command line or config file.
  cpuset_.MoveUnboundTasks("/kernel");

  // TODO(eieio): Replace this witha device-specific config file. This is just a
  // hack for now to put some form of permission logic in place while a longer
  // term solution is developed.
  using AllowRootSystem =
      CheckAnd<SameProcess,
               CheckOr<UserId<AID_ROOT, AID_SYSTEM>, GroupId<AID_SYSTEM>>>;
  using AllowRootSystemGraphics =
      CheckAnd<SameProcess, CheckOr<UserId<AID_ROOT, AID_SYSTEM, AID_GRAPHICS>,
                                    GroupId<AID_SYSTEM, AID_GRAPHICS>>>;
  using AllowRootSystemAudio =
      CheckAnd<SameProcess, CheckOr<UserId<AID_ROOT, AID_SYSTEM, AID_AUDIO>,
                                    GroupId<AID_SYSTEM, AID_AUDIO>>>;
  using AllowRootSystemTrusted =
      CheckOr<Trusted, UserId<AID_ROOT, AID_SYSTEM>, GroupId<AID_SYSTEM>>;

  partition_permission_check_ = AllowRootSystemTrusted::Check;

  // Setup the scheduler classes.
  // TODO(eieio): Replace this with a device-specific config file.
  scheduler_policies_ = {
      {"audio:low",
       {.timer_slack = kTimerSlackForegroundNs,
        .scheduler_policy = SCHED_FIFO | SCHED_RESET_ON_FORK,
        .priority = fifo_medium,
        .permission_check = AllowRootSystemAudio::Check}},
      {"audio:high",
       {.timer_slack = kTimerSlackForegroundNs,
        .scheduler_policy = SCHED_FIFO | SCHED_RESET_ON_FORK,
        .priority = fifo_medium + 3,
        .permission_check = AllowRootSystemAudio::Check}},
      {"graphics",
       {.timer_slack = kTimerSlackForegroundNs,
        .scheduler_policy = SCHED_FIFO | SCHED_RESET_ON_FORK,
        .priority = fifo_medium,
        .permission_check = AllowRootSystemGraphics::Check}},
      {"graphics:low",
       {.timer_slack = kTimerSlackForegroundNs,
        .scheduler_policy = SCHED_FIFO | SCHED_RESET_ON_FORK,
        .priority = fifo_medium,
        .permission_check = AllowRootSystemGraphics::Check}},
      {"graphics:high",
       {.timer_slack = kTimerSlackForegroundNs,
        .scheduler_policy = SCHED_FIFO | SCHED_RESET_ON_FORK,
        .priority = fifo_medium + 2,
        .permission_check = AllowRootSystemGraphics::Check}},
      {"sensors",
       {.timer_slack = kTimerSlackForegroundNs,
        .scheduler_policy = SCHED_FIFO | SCHED_RESET_ON_FORK,
        .priority = fifo_low,
        .permission_check = AllowRootSystem::Check}},
      {"sensors:low",
       {.timer_slack = kTimerSlackForegroundNs,
        .scheduler_policy = SCHED_FIFO | SCHED_RESET_ON_FORK,
        .priority = fifo_low,
        .permission_check = AllowRootSystem::Check}},
      {"sensors:high",
       {.timer_slack = kTimerSlackForegroundNs,
        .scheduler_policy = SCHED_FIFO | SCHED_RESET_ON_FORK,
        .priority = fifo_low + 1,
        .permission_check = AllowRootSystem::Check}},
      {"vr:system:arp",
       {.timer_slack = kTimerSlackForegroundNs,
        .scheduler_policy = SCHED_FIFO | SCHED_RESET_ON_FORK,
        .priority = fifo_medium + 2,
        .permission_check = AllowRootSystemTrusted::Check,
        "/system/performance"}},
      {"vr:app:render",
       {.timer_slack = kTimerSlackForegroundNs,
        .scheduler_policy = SCHED_FIFO | SCHED_RESET_ON_FORK,
        .priority = fifo_medium + 1,
        .permission_check = AllowRootSystemTrusted::Check,
        "/application/performance"}},
      {"normal",
       {.timer_slack = kTimerSlackForegroundNs,
        .scheduler_policy = SCHED_NORMAL,
        .priority = 0}},
      {"foreground",
       {.timer_slack = kTimerSlackForegroundNs,
        .scheduler_policy = SCHED_NORMAL,
        .priority = 0}},
      {"background",
       {.timer_slack = kTimerSlackBackgroundNs,
        .scheduler_policy = SCHED_BATCH,
        .priority = 0}},
      {"batch",
       {.timer_slack = kTimerSlackBackgroundNs,
        .scheduler_policy = SCHED_BATCH,
        .priority = 0}},
  };
}

bool PerformanceService::IsInitialized() const {
  return BASE::IsInitialized() && cpuset_ && sched_fifo_min_priority_ >= 0 &&
         sched_fifo_max_priority_ >= 0;
}

std::string PerformanceService::DumpState(size_t /*max_length*/) {
  return cpuset_.DumpState();
}

Status<void> PerformanceService::OnSetSchedulerPolicy(
    Message& message, pid_t task_id, const std::string& scheduler_policy) {
  ALOGI(
      "PerformanceService::OnSetSchedulerPolicy: task_id=%d "
      "scheduler_policy=%s",
      task_id, scheduler_policy.c_str());

  Task task(task_id);
  if (!task) {
    ALOGE(
        "PerformanceService::OnSetSchedulerPolicy: Unable to access /proc/%d "
        "to gather task information.",
        task_id);
    return ErrorStatus(EINVAL);
  }

  auto search = scheduler_policies_.find(scheduler_policy);
  if (search != scheduler_policies_.end()) {
    auto config = search->second;

    // Make sure the sending process is allowed to make the requested change to
    // this task.
    if (!config.IsAllowed(message, task))
      return ErrorStatus(EINVAL);

    // Get the thread group's cpu set. Policies that do not specify a cpuset
    // should default to this cpuset.
    std::string thread_group_cpuset;
    Task thread_group{task.thread_group_id()};
    if (thread_group) {
      thread_group_cpuset = thread_group.GetCpuSetPath();
    } else {
      ALOGE(
          "PerformanceService::OnSetSchedulerPolicy: Failed to get thread "
          "group tgid=%d for task_id=%d",
          task.thread_group_id(), task_id);
      thread_group_cpuset = kRootCpuSet;
    }

    std::string target_cpuset;
    if (config.cpuset.empty()) {
      target_cpuset = thread_group_cpuset;
    } else {
      target_cpuset = config.cpuset;
    }
    ALOGI("PerformanceService::OnSetSchedulerPolicy: Using cpuset=%s",
          target_cpuset.c_str());

    auto target_set = cpuset_.Lookup(target_cpuset);
    if (target_set) {
      auto attach_status = target_set->AttachTask(task_id);
      ALOGW_IF(!attach_status,
               "PerformanceService::OnSetSchedulerPolicy: Failed to attach "
               "task=%d to cpuset=%s: %s",
               task_id, target_cpuset.c_str(),
               attach_status.GetErrorMessage().c_str());
    } else {
      ALOGW(
          "PerformanceService::OnSetSchedulerPolicy: Failed to lookup "
          "cpuset=%s",
          target_cpuset.c_str());
    }

    struct sched_param param;
    param.sched_priority = config.priority;

    sched_setscheduler(task_id, config.scheduler_policy, &param);
    prctl(PR_SET_TIMERSLACK_PID, config.timer_slack, task_id);
    return {};
  } else {
    ALOGE(
        "PerformanceService::OnSetSchedulerPolicy: Invalid scheduler_policy=%s "
        "requested by task=%d.",
        scheduler_policy.c_str(), task_id);
    return ErrorStatus(EINVAL);
  }
}

Status<void> PerformanceService::OnSetCpuPartition(
    Message& message, pid_t task_id, const std::string& partition) {
  Task task(task_id);
  if (!task || task.thread_group_id() != message.GetProcessId())
    return ErrorStatus(EINVAL);

  // Temporary permission check.
  // TODO(eieio): Replace this with a configuration file.
  if (partition_permission_check_ &&
      !partition_permission_check_(message, task)) {
    return ErrorStatus(EINVAL);
  }

  auto target_set = cpuset_.Lookup(partition);
  if (!target_set)
    return ErrorStatus(ENOENT);

  auto attach_status = target_set->AttachTask(task_id);
  if (!attach_status)
    return attach_status;

  return {};
}

Status<void> PerformanceService::OnSetSchedulerClass(
    Message& message, pid_t task_id, const std::string& scheduler_class) {
  Task task(task_id);
  if (!task)
    return ErrorStatus(EINVAL);

  auto search = scheduler_policies_.find(scheduler_class);
  if (search != scheduler_policies_.end()) {
    auto config = search->second;

    // Make sure the sending process is allowed to make the requested change to
    // this task.
    if (!config.IsAllowed(message, task))
      return ErrorStatus(EINVAL);

    struct sched_param param;
    param.sched_priority = config.priority;

    sched_setscheduler(task_id, config.scheduler_policy, &param);
    prctl(PR_SET_TIMERSLACK_PID, config.timer_slack, task_id);
    ALOGI("PerformanceService::OnSetSchedulerClass: Set task=%d to class=%s.",
          task_id, scheduler_class.c_str());
    return {};
  } else {
    ALOGE(
        "PerformanceService::OnSetSchedulerClass: Invalid class=%s requested "
        "by task=%d.",
        scheduler_class.c_str(), task_id);
    return ErrorStatus(EINVAL);
  }
}

Status<std::string> PerformanceService::OnGetCpuPartition(Message& message,
                                                          pid_t task_id) {
  // Make sure the task id is valid and belongs to the sending process.
  Task task(task_id);
  if (!task || task.thread_group_id() != message.GetProcessId())
    return ErrorStatus(EINVAL);

  return task.GetCpuSetPath();
}

Status<void> PerformanceService::HandleMessage(Message& message) {
  ALOGD_IF(TRACE, "PerformanceService::HandleMessage: op=%d", message.GetOp());
  switch (message.GetOp()) {
    case PerformanceRPC::SetSchedulerPolicy::Opcode:
      DispatchRemoteMethod<PerformanceRPC::SetSchedulerPolicy>(
          *this, &PerformanceService::OnSetSchedulerPolicy, message);
      return {};

    case PerformanceRPC::SetCpuPartition::Opcode:
      DispatchRemoteMethod<PerformanceRPC::SetCpuPartition>(
          *this, &PerformanceService::OnSetCpuPartition, message);
      return {};

    case PerformanceRPC::SetSchedulerClass::Opcode:
      DispatchRemoteMethod<PerformanceRPC::SetSchedulerClass>(
          *this, &PerformanceService::OnSetSchedulerClass, message);
      return {};

    case PerformanceRPC::GetCpuPartition::Opcode:
      DispatchRemoteMethod<PerformanceRPC::GetCpuPartition>(
          *this, &PerformanceService::OnGetCpuPartition, message);
      return {};

    default:
      return Service::HandleMessage(message);
  }
}

}  // namespace dvr
}  // namespace android
