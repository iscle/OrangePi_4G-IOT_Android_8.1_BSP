#include <gtest/gtest.h>
#include <poll.h>
#include <private/dvr/buffer_hub_client.h>
#include <private/dvr/bufferhub_rpc.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>

#include <mutex>
#include <thread>

#define RETRY_EINTR(fnc_call)                 \
  ([&]() -> decltype(fnc_call) {              \
    decltype(fnc_call) result;                \
    do {                                      \
      result = (fnc_call);                    \
    } while (result == -1 && errno == EINTR); \
    return result;                            \
  })()

using android::dvr::BufferConsumer;
using android::dvr::BufferHubDefs::kConsumerStateMask;
using android::dvr::BufferHubDefs::kProducerStateBit;
using android::dvr::BufferProducer;
using android::pdx::LocalHandle;

const int kWidth = 640;
const int kHeight = 480;
const int kFormat = HAL_PIXEL_FORMAT_RGBA_8888;
const int kUsage = 0;
const uint64_t kContext = 42;

using LibBufferHubTest = ::testing::Test;

TEST_F(LibBufferHubTest, TestBasicUsage) {
  std::unique_ptr<BufferProducer> p = BufferProducer::Create(
      kWidth, kHeight, kFormat, kUsage, sizeof(uint64_t));
  ASSERT_TRUE(p.get() != nullptr);
  std::unique_ptr<BufferConsumer> c =
      BufferConsumer::Import(p->CreateConsumer());
  ASSERT_TRUE(c.get() != nullptr);
  // Check that consumers can spawn other consumers.
  std::unique_ptr<BufferConsumer> c2 =
      BufferConsumer::Import(c->CreateConsumer());
  ASSERT_TRUE(c2.get() != nullptr);

  // Producer state mask is unique, i.e. 1.
  EXPECT_EQ(p->buffer_state_bit(), kProducerStateBit);
  // Consumer state mask cannot have producer bit on.
  EXPECT_EQ(c->buffer_state_bit() & kProducerStateBit, 0);
  // Consumer state mask must be a single, i.e. power of 2.
  EXPECT_NE(c->buffer_state_bit(), 0);
  EXPECT_EQ(c->buffer_state_bit() & (c->buffer_state_bit() - 1), 0);
  // Consumer state mask cannot have producer bit on.
  EXPECT_EQ(c2->buffer_state_bit() & kProducerStateBit, 0);
  // Consumer state mask must be a single, i.e. power of 2.
  EXPECT_NE(c2->buffer_state_bit(), 0);
  EXPECT_EQ(c2->buffer_state_bit() & (c2->buffer_state_bit() - 1), 0);
  // Each consumer should have unique bit.
  EXPECT_EQ(c->buffer_state_bit() & c2->buffer_state_bit(), 0);

  // Initial state: producer not available, consumers not available.
  EXPECT_EQ(0, RETRY_EINTR(p->Poll(100)));
  EXPECT_EQ(0, RETRY_EINTR(c->Poll(100)));
  EXPECT_EQ(0, RETRY_EINTR(c2->Poll(100)));

  EXPECT_EQ(0, p->Post(LocalHandle(), kContext));

  // New state: producer not available, consumers available.
  EXPECT_EQ(0, RETRY_EINTR(p->Poll(100)));
  EXPECT_EQ(1, RETRY_EINTR(c->Poll(100)));
  EXPECT_EQ(1, RETRY_EINTR(c2->Poll(100)));

  uint64_t context;
  LocalHandle fence;
  EXPECT_EQ(0, c->Acquire(&fence, &context));
  EXPECT_EQ(kContext, context);
  EXPECT_EQ(0, RETRY_EINTR(c->Poll(100)));
  EXPECT_EQ(1, RETRY_EINTR(c2->Poll(100)));

  EXPECT_EQ(0, c2->Acquire(&fence, &context));
  EXPECT_EQ(kContext, context);
  EXPECT_EQ(0, RETRY_EINTR(c2->Poll(100)));
  EXPECT_EQ(0, RETRY_EINTR(c->Poll(100)));

  EXPECT_EQ(0, c->Release(LocalHandle()));
  EXPECT_EQ(0, RETRY_EINTR(p->Poll(100)));
  EXPECT_EQ(0, c2->Discard());

  EXPECT_EQ(1, RETRY_EINTR(p->Poll(100)));
  EXPECT_EQ(0, p->Gain(&fence));
  EXPECT_EQ(0, RETRY_EINTR(p->Poll(100)));
  EXPECT_EQ(0, RETRY_EINTR(c->Poll(100)));
  EXPECT_EQ(0, RETRY_EINTR(c2->Poll(100)));
}

TEST_F(LibBufferHubTest, TestEpoll) {
  std::unique_ptr<BufferProducer> p = BufferProducer::Create(
      kWidth, kHeight, kFormat, kUsage, sizeof(uint64_t));
  ASSERT_TRUE(p.get() != nullptr);
  std::unique_ptr<BufferConsumer> c =
      BufferConsumer::Import(p->CreateConsumer());
  ASSERT_TRUE(c.get() != nullptr);

  LocalHandle epoll_fd{epoll_create1(EPOLL_CLOEXEC)};
  ASSERT_TRUE(epoll_fd.IsValid());

  epoll_event event;
  std::array<epoll_event, 64> events;

  auto event_sources = p->GetEventSources();
  ASSERT_LT(event_sources.size(), events.size());

  for (const auto& event_source : event_sources) {
    event = {.events = event_source.event_mask | EPOLLET,
             .data = {.fd = p->event_fd()}};
    ASSERT_EQ(0, epoll_ctl(epoll_fd.Get(), EPOLL_CTL_ADD, event_source.event_fd,
                           &event));
  }

  event_sources = c->GetEventSources();
  ASSERT_LT(event_sources.size(), events.size());

  for (const auto& event_source : event_sources) {
    event = {.events = event_source.event_mask | EPOLLET,
             .data = {.fd = c->event_fd()}};
    ASSERT_EQ(0, epoll_ctl(epoll_fd.Get(), EPOLL_CTL_ADD, event_source.event_fd,
                           &event));
  }

  // No events should be signaled initially.
  ASSERT_EQ(0, epoll_wait(epoll_fd.Get(), events.data(), events.size(), 0));

  // Post the producer and check for consumer signal.
  EXPECT_EQ(0, p->Post({}, kContext));
  ASSERT_EQ(1, epoll_wait(epoll_fd.Get(), events.data(), events.size(), 100));
  ASSERT_TRUE(events[0].events & EPOLLIN);
  ASSERT_EQ(c->event_fd(), events[0].data.fd);

  // Save the event bits to translate later.
  event = events[0];

  // Check for events again. Edge-triggered mode should prevent any.
  EXPECT_EQ(0, epoll_wait(epoll_fd.Get(), events.data(), events.size(), 100));
  EXPECT_EQ(0, epoll_wait(epoll_fd.Get(), events.data(), events.size(), 100));
  EXPECT_EQ(0, epoll_wait(epoll_fd.Get(), events.data(), events.size(), 100));
  EXPECT_EQ(0, epoll_wait(epoll_fd.Get(), events.data(), events.size(), 100));

  // Translate the events.
  auto event_status = c->GetEventMask(event.events);
  ASSERT_TRUE(event_status);
  ASSERT_TRUE(event_status.get() & EPOLLIN);

  // Check for events again. Edge-triggered mode should prevent any.
  EXPECT_EQ(0, epoll_wait(epoll_fd.Get(), events.data(), events.size(), 100));
}

TEST_F(LibBufferHubTest, TestStateMask) {
  std::unique_ptr<BufferProducer> p = BufferProducer::Create(
      kWidth, kHeight, kFormat, kUsage, sizeof(uint64_t));
  ASSERT_TRUE(p.get() != nullptr);

  // It's ok to create up to 63 consumer buffers.
  uint64_t buffer_state_bits = p->buffer_state_bit();
  std::array<std::unique_ptr<BufferConsumer>, 63> cs;
  for (size_t i = 0; i < 63; i++) {
    cs[i] = BufferConsumer::Import(p->CreateConsumer());
    ASSERT_TRUE(cs[i].get() != nullptr);
    // Expect all buffers have unique state mask.
    EXPECT_EQ(buffer_state_bits & cs[i]->buffer_state_bit(), 0);
    buffer_state_bits |= cs[i]->buffer_state_bit();
  }
  EXPECT_EQ(buffer_state_bits, kProducerStateBit | kConsumerStateMask);

  // The 64th creation will fail with out-of-memory error.
  auto state = p->CreateConsumer();
  EXPECT_EQ(state.error(), E2BIG);

  // Release any consumer should allow us to re-create.
  for (size_t i = 0; i < 63; i++) {
    buffer_state_bits &= ~cs[i]->buffer_state_bit();
    cs[i] = nullptr;
    cs[i] = BufferConsumer::Import(p->CreateConsumer());
    ASSERT_TRUE(cs[i].get() != nullptr);
    // The released state mask will be reused.
    EXPECT_EQ(buffer_state_bits & cs[i]->buffer_state_bit(), 0);
    buffer_state_bits |= cs[i]->buffer_state_bit();
    EXPECT_EQ(buffer_state_bits, kProducerStateBit | kConsumerStateMask);
  }
}

TEST_F(LibBufferHubTest, TestStateTransitions) {
  std::unique_ptr<BufferProducer> p = BufferProducer::Create(
      kWidth, kHeight, kFormat, kUsage, sizeof(uint64_t));
  ASSERT_TRUE(p.get() != nullptr);
  std::unique_ptr<BufferConsumer> c =
      BufferConsumer::Import(p->CreateConsumer());
  ASSERT_TRUE(c.get() != nullptr);

  uint64_t context;
  LocalHandle fence;

  // The producer buffer starts in gained state.

  // Acquire, release, and gain in gained state should fail.
  EXPECT_EQ(-EBUSY, c->Acquire(&fence, &context));
  EXPECT_EQ(-EBUSY, c->Release(LocalHandle()));
  EXPECT_EQ(-EALREADY, p->Gain(&fence));

  // Post in gained state should succeed.
  EXPECT_EQ(0, p->Post(LocalHandle(), kContext));

  // Post, release, and gain in posted state should fail.
  EXPECT_EQ(-EBUSY, p->Post(LocalHandle(), kContext));
  EXPECT_EQ(-EBUSY, c->Release(LocalHandle()));
  EXPECT_EQ(-EBUSY, p->Gain(&fence));

  // Acquire in posted state should succeed.
  EXPECT_LE(0, c->Acquire(&fence, &context));

  // Acquire, post, and gain in acquired state should fail.
  EXPECT_EQ(-EBUSY, c->Acquire(&fence, &context));
  EXPECT_EQ(-EBUSY, p->Post(LocalHandle(), kContext));
  EXPECT_EQ(-EBUSY, p->Gain(&fence));

  // Release in acquired state should succeed.
  EXPECT_EQ(0, c->Release(LocalHandle()));
  EXPECT_LT(0, RETRY_EINTR(p->Poll(10)));

  // Release, acquire, and post in released state should fail.
  EXPECT_EQ(-EBUSY, c->Release(LocalHandle()));
  EXPECT_EQ(-EBUSY, c->Acquire(&fence, &context));
  EXPECT_EQ(-EBUSY, p->Post(LocalHandle(), kContext));

  // Gain in released state should succeed.
  EXPECT_EQ(0, p->Gain(&fence));

  // Acquire, release, and gain in gained state should fail.
  EXPECT_EQ(-EBUSY, c->Acquire(&fence, &context));
  EXPECT_EQ(-EBUSY, c->Release(LocalHandle()));
  EXPECT_EQ(-EALREADY, p->Gain(&fence));
}

TEST_F(LibBufferHubTest, TestWithCustomMetadata) {
  struct Metadata {
    int64_t field1;
    int64_t field2;
  };
  std::unique_ptr<BufferProducer> p = BufferProducer::Create(
      kWidth, kHeight, kFormat, kUsage, sizeof(Metadata));
  ASSERT_TRUE(p.get() != nullptr);
  std::unique_ptr<BufferConsumer> c =
      BufferConsumer::Import(p->CreateConsumer());
  ASSERT_TRUE(c.get() != nullptr);

  Metadata m = {1, 3};
  EXPECT_EQ(0, p->Post(LocalHandle(), m));
  EXPECT_LE(0, RETRY_EINTR(c->Poll(10)));

  LocalHandle fence;
  Metadata m2 = {};
  EXPECT_EQ(0, c->Acquire(&fence, &m2));
  EXPECT_EQ(m.field1, m2.field1);
  EXPECT_EQ(m.field2, m2.field2);

  EXPECT_EQ(0, c->Release(LocalHandle()));
  EXPECT_LT(0, RETRY_EINTR(p->Poll(0)));
}

TEST_F(LibBufferHubTest, TestPostWithWrongMetaSize) {
  struct Metadata {
    int64_t field1;
    int64_t field2;
  };
  struct OverSizedMetadata {
    int64_t field1;
    int64_t field2;
    int64_t field3;
  };
  std::unique_ptr<BufferProducer> p = BufferProducer::Create(
      kWidth, kHeight, kFormat, kUsage, sizeof(Metadata));
  ASSERT_TRUE(p.get() != nullptr);
  std::unique_ptr<BufferConsumer> c =
      BufferConsumer::Import(p->CreateConsumer());
  ASSERT_TRUE(c.get() != nullptr);

  // It is illegal to post metadata larger than originally requested during
  // buffer allocation.
  OverSizedMetadata evil_meta = {};
  EXPECT_NE(0, p->Post(LocalHandle(), evil_meta));
  EXPECT_GE(0, RETRY_EINTR(c->Poll(10)));

  // It is ok to post metadata smaller than originally requested during
  // buffer allocation.
  int64_t sequence = 42;
  EXPECT_EQ(0, p->Post(LocalHandle(), sequence));
}

TEST_F(LibBufferHubTest, TestAcquireWithWrongMetaSize) {
  struct Metadata {
    int64_t field1;
    int64_t field2;
  };
  struct OverSizedMetadata {
    int64_t field1;
    int64_t field2;
    int64_t field3;
  };
  std::unique_ptr<BufferProducer> p = BufferProducer::Create(
      kWidth, kHeight, kFormat, kUsage, sizeof(Metadata));
  ASSERT_TRUE(p.get() != nullptr);
  std::unique_ptr<BufferConsumer> c =
      BufferConsumer::Import(p->CreateConsumer());
  ASSERT_TRUE(c.get() != nullptr);

  Metadata m = {1, 3};
  EXPECT_EQ(0, p->Post(LocalHandle(), m));

  LocalHandle fence;
  int64_t sequence;
  OverSizedMetadata e;

  // It is illegal to acquire metadata larger than originally requested during
  // buffer allocation.
  EXPECT_NE(0, c->Acquire(&fence, &e));

  // It is ok to acquire metadata smaller than originally requested during
  // buffer allocation.
  EXPECT_EQ(0, c->Acquire(&fence, &sequence));
  EXPECT_EQ(m.field1, sequence);
}

TEST_F(LibBufferHubTest, TestAcquireWithNoMeta) {
  std::unique_ptr<BufferProducer> p = BufferProducer::Create(
      kWidth, kHeight, kFormat, kUsage, sizeof(uint64_t));
  ASSERT_TRUE(p.get() != nullptr);
  std::unique_ptr<BufferConsumer> c =
      BufferConsumer::Import(p->CreateConsumer());
  ASSERT_TRUE(c.get() != nullptr);

  int64_t sequence = 3;
  EXPECT_EQ(0, p->Post(LocalHandle(), sequence));

  LocalHandle fence;
  EXPECT_EQ(0, c->Acquire(&fence));
}

TEST_F(LibBufferHubTest, TestWithNoMeta) {
  std::unique_ptr<BufferProducer> p =
      BufferProducer::Create(kWidth, kHeight, kFormat, kUsage);
  ASSERT_TRUE(p.get() != nullptr);
  std::unique_ptr<BufferConsumer> c =
      BufferConsumer::Import(p->CreateConsumer());
  ASSERT_TRUE(c.get() != nullptr);

  LocalHandle fence;

  EXPECT_EQ(0, p->Post<void>(LocalHandle()));
  EXPECT_EQ(0, c->Acquire(&fence));
}

TEST_F(LibBufferHubTest, TestFailureToPostMetaFromABufferWithoutMeta) {
  std::unique_ptr<BufferProducer> p =
      BufferProducer::Create(kWidth, kHeight, kFormat, kUsage);
  ASSERT_TRUE(p.get() != nullptr);
  std::unique_ptr<BufferConsumer> c =
      BufferConsumer::Import(p->CreateConsumer());
  ASSERT_TRUE(c.get() != nullptr);

  int64_t sequence = 3;
  EXPECT_NE(0, p->Post(LocalHandle(), sequence));
}

TEST_F(LibBufferHubTest, TestPersistentBufferPersistence) {
  auto p = BufferProducer::Create("TestPersistentBuffer", -1, -1, kWidth,
                                  kHeight, kFormat, kUsage);
  ASSERT_NE(nullptr, p);

  // Record the original buffer id for later comparison.
  const int buffer_id = p->id();

  auto c = BufferConsumer::Import(p->CreateConsumer());
  ASSERT_NE(nullptr, c);

  EXPECT_EQ(0, p->Post<void>(LocalHandle()));

  // Close the connection to the producer. This should not affect the consumer.
  p = nullptr;

  LocalHandle fence;
  EXPECT_EQ(0, c->Acquire(&fence));
  EXPECT_EQ(0, c->Release(LocalHandle()));

  // Attempt to reconnect to the persistent buffer.
  p = BufferProducer::Create("TestPersistentBuffer");
  ASSERT_NE(nullptr, p);
  EXPECT_EQ(buffer_id, p->id());
  EXPECT_EQ(0, p->Gain(&fence));
}

TEST_F(LibBufferHubTest, TestPersistentBufferMismatchParams) {
  auto p = BufferProducer::Create("TestPersistentBuffer", -1, -1, kWidth,
                                  kHeight, kFormat, kUsage);
  ASSERT_NE(nullptr, p);

  // Close the connection to the producer.
  p = nullptr;

  // Mismatch the params.
  p = BufferProducer::Create("TestPersistentBuffer", -1, -1, kWidth * 2,
                             kHeight, kFormat, kUsage);
  ASSERT_EQ(nullptr, p);
}

TEST_F(LibBufferHubTest, TestRemovePersistentBuffer) {
  auto p = BufferProducer::Create("TestPersistentBuffer", -1, -1, kWidth,
                                  kHeight, kFormat, kUsage);
  ASSERT_NE(nullptr, p);

  LocalHandle fence;
  auto c = BufferConsumer::Import(p->CreateConsumer());
  ASSERT_NE(nullptr, c);
  EXPECT_EQ(0, p->Post<void>(LocalHandle()));
  EXPECT_EQ(0, c->Acquire(&fence));
  EXPECT_EQ(0, c->Release(LocalHandle()));
  EXPECT_LT(0, RETRY_EINTR(p->Poll(10)));

  // Test that removing persistence and closing the producer orphans the
  // consumer.
  EXPECT_EQ(0, p->Gain(&fence));
  EXPECT_EQ(0, p->Post<void>(LocalHandle()));
  EXPECT_EQ(0, p->RemovePersistence());
  p = nullptr;

  // Orphaned consumer can acquire the posted buffer one more time in
  // asynchronous manner. But synchronous call will fail.
  DvrNativeBufferMetadata meta;
  EXPECT_EQ(0, c->AcquireAsync(&meta, &fence));
  EXPECT_EQ(-EPIPE, c->Release(LocalHandle()));
}

namespace {

int PollFd(int fd, int timeout_ms) {
  pollfd p = {fd, POLLIN, 0};
  return poll(&p, 1, timeout_ms);
}

}  // namespace

TEST_F(LibBufferHubTest, TestAcquireFence) {
  std::unique_ptr<BufferProducer> p = BufferProducer::Create(
      kWidth, kHeight, kFormat, kUsage, /*metadata_size=*/0);
  ASSERT_TRUE(p.get() != nullptr);
  std::unique_ptr<BufferConsumer> c =
      BufferConsumer::Import(p->CreateConsumer());
  ASSERT_TRUE(c.get() != nullptr);

  DvrNativeBufferMetadata meta;
  LocalHandle f1(eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK));

  // Post with unsignaled fence.
  EXPECT_EQ(0, p->PostAsync(&meta, f1));

  // Should acquire a valid fence.
  LocalHandle f2;
  EXPECT_LT(0, RETRY_EINTR(c->Poll(10)));
  EXPECT_EQ(0, c->AcquireAsync(&meta, &f2));
  EXPECT_TRUE(f2.IsValid());
  // The original fence and acquired fence should have different fd number.
  EXPECT_NE(f1.Get(), f2.Get());
  EXPECT_GE(0, PollFd(f2.Get(), 0));

  // Signal the original fence will trigger the new fence.
  eventfd_write(f1.Get(), 1);
  // Now the original FD has been signaled.
  EXPECT_LT(0, PollFd(f2.Get(), 10));

  // Release the consumer with an invalid fence.
  EXPECT_EQ(0, c->ReleaseAsync(&meta, LocalHandle()));

  // Should gain an invalid fence.
  LocalHandle f3;
  EXPECT_LT(0, RETRY_EINTR(p->Poll(10)));
  EXPECT_EQ(0, p->GainAsync(&meta, &f3));
  EXPECT_FALSE(f3.IsValid());

  // Post with a signaled fence.
  EXPECT_EQ(0, p->PostAsync(&meta, f1));

  // Should acquire a valid fence and it's already signalled.
  LocalHandle f4;
  EXPECT_LT(0, RETRY_EINTR(c->Poll(10)));
  EXPECT_EQ(0, c->AcquireAsync(&meta, &f4));
  EXPECT_TRUE(f4.IsValid());
  EXPECT_LT(0, PollFd(f4.Get(), 10));

  // Release with an unsignalled fence and signal it immediately after release
  // without producer gainning.
  LocalHandle f5(eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK));
  EXPECT_EQ(0, c->ReleaseAsync(&meta, f5));
  eventfd_write(f5.Get(), 1);

  // Should gain a valid fence, which is already signaled.
  LocalHandle f6;
  EXPECT_LT(0, RETRY_EINTR(p->Poll(10)));
  EXPECT_EQ(0, p->GainAsync(&meta, &f6));
  EXPECT_TRUE(f6.IsValid());
  EXPECT_LT(0, PollFd(f6.Get(), 10));
}

TEST_F(LibBufferHubTest, TestOrphanedAcquire) {
  std::unique_ptr<BufferProducer> p = BufferProducer::Create(
      kWidth, kHeight, kFormat, kUsage, sizeof(uint64_t));
  ASSERT_TRUE(p.get() != nullptr);
  std::unique_ptr<BufferConsumer> c1 =
      BufferConsumer::Import(p->CreateConsumer());
  ASSERT_TRUE(c1.get() != nullptr);
  const uint64_t consumer_state_bit1 = c1->buffer_state_bit();

  DvrNativeBufferMetadata meta;
  EXPECT_EQ(0, p->PostAsync(&meta, LocalHandle()));

  LocalHandle fence;
  EXPECT_LT(0, RETRY_EINTR(c1->Poll(10)));
  EXPECT_LE(0, c1->AcquireAsync(&meta, &fence));
  // Destroy the consumer now will make it orphaned and the buffer is still
  // acquired.
  c1 = nullptr;
  EXPECT_GE(0, RETRY_EINTR(p->Poll(10)));

  std::unique_ptr<BufferConsumer> c2 =
      BufferConsumer::Import(p->CreateConsumer());
  ASSERT_TRUE(c2.get() != nullptr);
  const uint64_t consumer_state_bit2 = c2->buffer_state_bit();
  EXPECT_NE(consumer_state_bit1, consumer_state_bit2);

  // The new consumer is available for acquire.
  EXPECT_LT(0, RETRY_EINTR(c2->Poll(10)));
  EXPECT_LE(0, c2->AcquireAsync(&meta, &fence));
  // Releasing the consumer makes the buffer gainable.
  EXPECT_EQ(0, c2->ReleaseAsync(&meta, LocalHandle()));

  // The buffer is now available for the producer to gain.
  EXPECT_LT(0, RETRY_EINTR(p->Poll(10)));

  // But if another consumer is created in released state.
  std::unique_ptr<BufferConsumer> c3 =
      BufferConsumer::Import(p->CreateConsumer());
  ASSERT_TRUE(c3.get() != nullptr);
  const uint64_t consumer_state_bit3 = c3->buffer_state_bit();
  EXPECT_NE(consumer_state_bit2, consumer_state_bit3);
  // The consumer buffer is not acquirable.
  EXPECT_GE(0, RETRY_EINTR(c3->Poll(10)));
  EXPECT_EQ(-EBUSY, c3->AcquireAsync(&meta, &fence));

  // Producer should be able to gain no matter what.
  EXPECT_EQ(0, p->GainAsync(&meta, &fence));
}
