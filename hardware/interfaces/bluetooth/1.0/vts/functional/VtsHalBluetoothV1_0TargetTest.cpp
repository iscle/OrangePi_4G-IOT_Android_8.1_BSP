/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "bluetooth_hidl_hal_test"
#include <android-base/logging.h>

#include <android/hardware/bluetooth/1.0/IBluetoothHci.h>
#include <android/hardware/bluetooth/1.0/IBluetoothHciCallbacks.h>
#include <android/hardware/bluetooth/1.0/types.h>
#include <hardware/bluetooth.h>
#include <utils/Log.h>

#include <VtsHalHidlTargetCallbackBase.h>
#include <VtsHalHidlTargetTestBase.h>
#include <queue>

using ::android::hardware::bluetooth::V1_0::IBluetoothHci;
using ::android::hardware::bluetooth::V1_0::IBluetoothHciCallbacks;
using ::android::hardware::bluetooth::V1_0::Status;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

#define HCI_MINIMUM_HCI_VERSION 5  // Bluetooth Core Specification 3.0 + HS
#define HCI_MINIMUM_LMP_VERSION 5  // Bluetooth Core Specification 3.0 + HS
#define NUM_HCI_COMMANDS_BANDWIDTH 1000
#define NUM_SCO_PACKETS_BANDWIDTH 1000
#define NUM_ACL_PACKETS_BANDWIDTH 1000
#define WAIT_FOR_INIT_TIMEOUT std::chrono::milliseconds(2000)
#define WAIT_FOR_HCI_EVENT_TIMEOUT std::chrono::milliseconds(2000)
#define WAIT_FOR_SCO_DATA_TIMEOUT std::chrono::milliseconds(1000)
#define WAIT_FOR_ACL_DATA_TIMEOUT std::chrono::milliseconds(1000)

#define COMMAND_HCI_SHOULD_BE_UNKNOWN \
  { 0xff, 0x3B, 0x08, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07 }
#define COMMAND_HCI_READ_LOCAL_VERSION_INFORMATION \
  { 0x01, 0x10, 0x00 }
#define COMMAND_HCI_READ_BUFFER_SIZE \
  { 0x05, 0x10, 0x00 }
#define COMMAND_HCI_WRITE_LOOPBACK_MODE_LOCAL \
  { 0x02, 0x18, 0x01, 0x01 }
#define COMMAND_HCI_RESET \
  { 0x03, 0x0c, 0x00 }
#define COMMAND_HCI_WRITE_LOCAL_NAME \
  { 0x13, 0x0c, 0xf8 }
#define HCI_STATUS_SUCCESS 0x00
#define HCI_STATUS_UNKNOWN_HCI_COMMAND 0x01

#define EVENT_CONNECTION_COMPLETE 0x03
#define EVENT_COMMAND_COMPLETE 0x0e
#define EVENT_COMMAND_STATUS 0x0f
#define EVENT_NUMBER_OF_COMPLETED_PACKETS 0x13
#define EVENT_LOOPBACK_COMMAND 0x19

#define EVENT_CODE_BYTE 0
#define EVENT_LENGTH_BYTE 1
#define EVENT_FIRST_PAYLOAD_BYTE 2
#define EVENT_COMMAND_STATUS_STATUS_BYTE 2
#define EVENT_COMMAND_STATUS_ALLOWED_PACKETS_BYTE 3
#define EVENT_COMMAND_STATUS_OPCODE_LSBYTE 4  // Bytes 4 and 5
#define EVENT_COMMAND_COMPLETE_ALLOWED_PACKETS_BYTE 2
#define EVENT_COMMAND_COMPLETE_OPCODE_LSBYTE 3  // Bytes 3 and 4
#define EVENT_COMMAND_COMPLETE_STATUS_BYTE 5
#define EVENT_COMMAND_COMPLETE_FIRST_PARAM_BYTE 6
#define EVENT_LOCAL_HCI_VERSION_BYTE EVENT_COMMAND_COMPLETE_FIRST_PARAM_BYTE
#define EVENT_LOCAL_LMP_VERSION_BYTE EVENT_LOCAL_HCI_VERSION_BYTE + 3

#define EVENT_CONNECTION_COMPLETE_PARAM_LENGTH 11
#define EVENT_CONNECTION_COMPLETE_TYPE 11
#define EVENT_CONNECTION_COMPLETE_TYPE_SCO 0
#define EVENT_CONNECTION_COMPLETE_TYPE_ACL 1
#define EVENT_CONNECTION_COMPLETE_HANDLE_LSBYTE 3
#define EVENT_COMMAND_STATUS_LENGTH 4

#define EVENT_NUMBER_OF_COMPLETED_PACKETS_NUM_HANDLES 2

#define ACL_BROADCAST_FLAG_OFFSET 6
#define ACL_BROADCAST_FLAG_ACTIVE_SLAVE 0x1
#define ACL_BROADCAST_ACTIVE_SLAVE (ACL_BROADCAST_FLAG_ACTIVE_SLAVE << ACL_BROADCAST_FLAG_OFFSET)

#define ACL_PACKET_BOUNDARY_FLAG_OFFSET 4
#define ACL_PACKET_BOUNDARY_FLAG_COMPLETE 0x3
#define ACL_PACKET_BOUNDARY_COMPLETE \
    (ACL_PACKET_BOUNDARY_FLAG_COMPLETE << ACL_PACKET_BOUNDARY_FLAG_OFFSET)

constexpr char kCallbackNameAclEventReceived[] = "aclDataReceived";
constexpr char kCallbackNameHciEventReceived[] = "hciEventReceived";
constexpr char kCallbackNameInitializationComplete[] = "initializationComplete";
constexpr char kCallbackNameScoEventReceived[] = "scoDataReceived";

class ThroughputLogger {
 public:
  ThroughputLogger(std::string task)
      : task_(task), start_time_(std::chrono::steady_clock::now()) {}

  ~ThroughputLogger() {
    if (total_bytes_ == 0) return;
    std::chrono::duration<double> duration =
        std::chrono::steady_clock::now() - start_time_;
    double s = duration.count();
    if (s == 0) return;
    double rate_kb = (static_cast<double>(total_bytes_) / s) / 1024;
    ALOGD("%s %.1f KB/s (%zu bytes in %.3fs)", task_.c_str(), rate_kb,
          total_bytes_, s);
  }

  void setTotalBytes(size_t total_bytes) { total_bytes_ = total_bytes; }

 private:
  size_t total_bytes_;
  std::string task_;
  std::chrono::steady_clock::time_point start_time_;
};

// The main test class for Bluetooth HIDL HAL.
class BluetoothHidlTest : public ::testing::VtsHalHidlTargetTestBase {
 public:
  virtual void SetUp() override {
    // currently test passthrough mode only
    bluetooth =
        ::testing::VtsHalHidlTargetTestBase::getService<IBluetoothHci>();
    ASSERT_NE(bluetooth, nullptr);
    ALOGI("%s: getService() for bluetooth is %s", __func__,
          bluetooth->isRemote() ? "remote" : "local");

    bluetooth_cb = new BluetoothHciCallbacks(*this);
    ASSERT_NE(bluetooth_cb, nullptr);

    max_acl_data_packet_length = 0;
    max_sco_data_packet_length = 0;
    max_acl_data_packets = 0;
    max_sco_data_packets = 0;

    initialized = false;
    event_cb_count = 0;
    acl_cb_count = 0;
    sco_cb_count = 0;

    ASSERT_EQ(initialized, false);
    bluetooth->initialize(bluetooth_cb);

    bluetooth_cb->SetWaitTimeout(kCallbackNameInitializationComplete,
                                 WAIT_FOR_INIT_TIMEOUT);
    bluetooth_cb->SetWaitTimeout(kCallbackNameHciEventReceived,
                                 WAIT_FOR_HCI_EVENT_TIMEOUT);
    bluetooth_cb->SetWaitTimeout(kCallbackNameAclEventReceived,
                                 WAIT_FOR_ACL_DATA_TIMEOUT);
    bluetooth_cb->SetWaitTimeout(kCallbackNameScoEventReceived,
                                 WAIT_FOR_SCO_DATA_TIMEOUT);

    EXPECT_TRUE(
        bluetooth_cb->WaitForCallback(kCallbackNameInitializationComplete)
            .no_timeout);

    ASSERT_EQ(initialized, true);
  }

  virtual void TearDown() override {
    bluetooth->close();
    EXPECT_EQ(static_cast<size_t>(0), event_queue.size());
    EXPECT_EQ(static_cast<size_t>(0), sco_queue.size());
    EXPECT_EQ(static_cast<size_t>(0), acl_queue.size());
  }

  void setBufferSizes();

  // Functions called from within tests in loopback mode
  void sendAndCheckHCI(int num_packets);
  void sendAndCheckSCO(int num_packets, size_t size, uint16_t handle);
  void sendAndCheckACL(int num_packets, size_t size, uint16_t handle);

  // Helper functions to try to get a handle on verbosity
  void enterLoopbackMode(std::vector<uint16_t>& sco_handles,
                         std::vector<uint16_t>& acl_handles);
  void wait_for_command_complete_event(hidl_vec<uint8_t> cmd);
  int wait_for_completed_packets_event(uint16_t handle);

  // A simple test implementation of BluetoothHciCallbacks.
  class BluetoothHciCallbacks
      : public ::testing::VtsHalHidlTargetCallbackBase<BluetoothHidlTest>,
        public IBluetoothHciCallbacks {
    BluetoothHidlTest& parent_;

   public:
    BluetoothHciCallbacks(BluetoothHidlTest& parent) : parent_(parent){};

    virtual ~BluetoothHciCallbacks() = default;

    Return<void> initializationComplete(Status status) override {
      parent_.initialized = (status == Status::SUCCESS);
      NotifyFromCallback(kCallbackNameInitializationComplete);
      ALOGV("%s (status = %d)", __func__, static_cast<int>(status));
      return Void();
    };

    Return<void> hciEventReceived(
        const ::android::hardware::hidl_vec<uint8_t>& event) override {
      parent_.event_cb_count++;
      parent_.event_queue.push(event);
      NotifyFromCallback(kCallbackNameHciEventReceived);
      ALOGV("Event received (length = %d)", static_cast<int>(event.size()));
      return Void();
    };

    Return<void> aclDataReceived(
        const ::android::hardware::hidl_vec<uint8_t>& data) override {
      parent_.acl_cb_count++;
      parent_.acl_queue.push(data);
      NotifyFromCallback(kCallbackNameAclEventReceived);
      return Void();
    };

    Return<void> scoDataReceived(
        const ::android::hardware::hidl_vec<uint8_t>& data) override {
      parent_.sco_cb_count++;
      parent_.sco_queue.push(data);
      NotifyFromCallback(kCallbackNameScoEventReceived);
      return Void();
    };
  };

  sp<IBluetoothHci> bluetooth;
  sp<BluetoothHciCallbacks> bluetooth_cb;
  std::queue<hidl_vec<uint8_t>> event_queue;
  std::queue<hidl_vec<uint8_t>> acl_queue;
  std::queue<hidl_vec<uint8_t>> sco_queue;

  bool initialized;

  int event_cb_count;
  int sco_cb_count;
  int acl_cb_count;

  int max_acl_data_packet_length;
  int max_sco_data_packet_length;
  int max_acl_data_packets;
  int max_sco_data_packets;
};

// A class for test environment setup (kept since this file is a template).
class BluetoothHidlEnvironment : public ::testing::Environment {
 public:
  virtual void SetUp() {}
  virtual void TearDown() {}

 private:
};

// Receive and check status events until a COMMAND_COMPLETE is received.
void BluetoothHidlTest::wait_for_command_complete_event(hidl_vec<uint8_t> cmd) {
  // Allow intermediate COMMAND_STATUS events
  int status_event_count = 0;
  hidl_vec<uint8_t> event;
  do {
      EXPECT_TRUE(bluetooth_cb->WaitForCallback(kCallbackNameHciEventReceived)
                      .no_timeout);
      EXPECT_LT(static_cast<size_t>(0), event_queue.size());
      if (event_queue.size() == 0) {
          event.resize(0);
          break;
    }
    event = event_queue.front();
    event_queue.pop();
    EXPECT_GT(event.size(),
              static_cast<size_t>(EVENT_COMMAND_STATUS_OPCODE_LSBYTE + 1));
    if (event[EVENT_CODE_BYTE] == EVENT_COMMAND_STATUS) {
      EXPECT_EQ(EVENT_COMMAND_STATUS_LENGTH, event[EVENT_LENGTH_BYTE]);
      EXPECT_EQ(cmd[0], event[EVENT_COMMAND_STATUS_OPCODE_LSBYTE]);
      EXPECT_EQ(cmd[1], event[EVENT_COMMAND_STATUS_OPCODE_LSBYTE + 1]);
      EXPECT_EQ(event[EVENT_COMMAND_STATUS_STATUS_BYTE], HCI_STATUS_SUCCESS);
      status_event_count++;
    }
  } while (event.size() > 0 && event[EVENT_CODE_BYTE] == EVENT_COMMAND_STATUS);

  EXPECT_GT(event.size(),
            static_cast<size_t>(EVENT_COMMAND_COMPLETE_STATUS_BYTE));
  EXPECT_EQ(EVENT_COMMAND_COMPLETE, event[EVENT_CODE_BYTE]);
  EXPECT_EQ(cmd[0], event[EVENT_COMMAND_COMPLETE_OPCODE_LSBYTE]);
  EXPECT_EQ(cmd[1], event[EVENT_COMMAND_COMPLETE_OPCODE_LSBYTE + 1]);
  EXPECT_EQ(HCI_STATUS_SUCCESS, event[EVENT_COMMAND_COMPLETE_STATUS_BYTE]);
}

// Send the command to read the controller's buffer sizes.
void BluetoothHidlTest::setBufferSizes() {
  hidl_vec<uint8_t> cmd = COMMAND_HCI_READ_BUFFER_SIZE;
  bluetooth->sendHciCommand(cmd);

  EXPECT_TRUE(
      bluetooth_cb->WaitForCallback(kCallbackNameHciEventReceived).no_timeout);

  EXPECT_LT(static_cast<size_t>(0), event_queue.size());
  if (event_queue.size() == 0) return;

  hidl_vec<uint8_t> event = event_queue.front();
  event_queue.pop();

  EXPECT_EQ(EVENT_COMMAND_COMPLETE, event[EVENT_CODE_BYTE]);
  EXPECT_EQ(cmd[0], event[EVENT_COMMAND_COMPLETE_OPCODE_LSBYTE]);
  EXPECT_EQ(cmd[1], event[EVENT_COMMAND_COMPLETE_OPCODE_LSBYTE + 1]);
  EXPECT_EQ(HCI_STATUS_SUCCESS, event[EVENT_COMMAND_COMPLETE_STATUS_BYTE]);

  max_acl_data_packet_length =
      event[EVENT_COMMAND_COMPLETE_STATUS_BYTE + 1] +
      (event[EVENT_COMMAND_COMPLETE_STATUS_BYTE + 2] << 8);
  max_sco_data_packet_length = event[EVENT_COMMAND_COMPLETE_STATUS_BYTE + 3];
  max_acl_data_packets = event[EVENT_COMMAND_COMPLETE_STATUS_BYTE + 4] +
                         (event[EVENT_COMMAND_COMPLETE_STATUS_BYTE + 5] << 8);
  max_sco_data_packets = event[EVENT_COMMAND_COMPLETE_STATUS_BYTE + 6] +
                         (event[EVENT_COMMAND_COMPLETE_STATUS_BYTE + 7] << 8);

  ALOGD("%s: ACL max %d num %d SCO max %d num %d", __func__,
        static_cast<int>(max_acl_data_packet_length),
        static_cast<int>(max_acl_data_packets),
        static_cast<int>(max_sco_data_packet_length),
        static_cast<int>(max_sco_data_packets));
}

// Send an HCI command (in Loopback mode) and check the response.
void BluetoothHidlTest::sendAndCheckHCI(int num_packets) {
  ThroughputLogger logger = {__func__};
  for (int n = 0; n < num_packets; n++) {
    // Send an HCI packet
    std::vector<uint8_t> write_name = COMMAND_HCI_WRITE_LOCAL_NAME;
    // With a name
    char new_name[] = "John Jacob Jingleheimer Schmidt ___________________0";
    size_t new_name_length = strlen(new_name);
    for (size_t i = 0; i < new_name_length; i++)
      write_name.push_back(static_cast<uint8_t>(new_name[i]));
    // And the packet number
    {
      size_t i = new_name_length - 1;
      for (int digits = n; digits > 0; digits = digits / 10, i--)
        write_name[i] = static_cast<uint8_t>('0' + digits % 10);
    }
    // And padding
    for (size_t i = 0; i < 248 - new_name_length; i++)
      write_name.push_back(static_cast<uint8_t>(0));

    hidl_vec<uint8_t> cmd = write_name;
    bluetooth->sendHciCommand(cmd);

    // Check the loopback of the HCI packet
    EXPECT_TRUE(bluetooth_cb->WaitForCallback(kCallbackNameHciEventReceived)
                    .no_timeout);
    hidl_vec<uint8_t> event = event_queue.front();
    event_queue.pop();
    size_t compare_length =
        (cmd.size() > static_cast<size_t>(0xff) ? static_cast<size_t>(0xff)
                                                : cmd.size());
    EXPECT_GT(event.size(), compare_length + EVENT_FIRST_PAYLOAD_BYTE - 1);

    EXPECT_EQ(EVENT_LOOPBACK_COMMAND, event[EVENT_CODE_BYTE]);
    EXPECT_EQ(compare_length, event[EVENT_LENGTH_BYTE]);
    if (n == 0) logger.setTotalBytes(cmd.size() * num_packets * 2);

    for (size_t i = 0; i < compare_length; i++)
      EXPECT_EQ(cmd[i], event[EVENT_FIRST_PAYLOAD_BYTE + i]);
  }
}

// Send a SCO data packet (in Loopback mode) and check the response.
void BluetoothHidlTest::sendAndCheckSCO(int num_packets, size_t size,
                                        uint16_t handle) {
  ThroughputLogger logger = {__func__};
  for (int n = 0; n < num_packets; n++) {
    // Send a SCO packet
    hidl_vec<uint8_t> sco_packet;
    std::vector<uint8_t> sco_vector;
    sco_vector.push_back(static_cast<uint8_t>(handle & 0xff));
    sco_vector.push_back(static_cast<uint8_t>((handle & 0x0f00) >> 8));
    sco_vector.push_back(static_cast<uint8_t>(size & 0xff));
    sco_vector.push_back(static_cast<uint8_t>((size & 0xff00) >> 8));
    for (size_t i = 0; i < size; i++) {
      sco_vector.push_back(static_cast<uint8_t>(i + n));
    }
    sco_packet = sco_vector;
    bluetooth->sendScoData(sco_vector);

    // Check the loopback of the SCO packet
    EXPECT_TRUE(bluetooth_cb->WaitForCallback(kCallbackNameScoEventReceived)
                    .no_timeout);
    hidl_vec<uint8_t> sco_loopback = sco_queue.front();
    sco_queue.pop();

    EXPECT_EQ(sco_packet.size(), sco_loopback.size());
    size_t successful_bytes = 0;

    if (n == 0) logger.setTotalBytes(num_packets * size * 2);

    for (size_t i = 0; i < sco_packet.size(); i++) {
      if (sco_packet[i] == sco_loopback[i]) {
        successful_bytes = i;
      } else {
        ALOGE("Miscompare at %d (expected %x, got %x)", static_cast<int>(i),
              sco_packet[i], sco_loopback[i]);
        ALOGE("At %d (expected %x, got %x)", static_cast<int>(i + 1),
              sco_packet[i + 1], sco_loopback[i + 1]);
        break;
      }
    }
    EXPECT_EQ(sco_packet.size(), successful_bytes + 1);
  }
}

// Send an ACL data packet (in Loopback mode) and check the response.
void BluetoothHidlTest::sendAndCheckACL(int num_packets, size_t size,
                                        uint16_t handle) {
  ThroughputLogger logger = {__func__};
  for (int n = 0; n < num_packets; n++) {
    // Send an ACL packet
    hidl_vec<uint8_t> acl_packet;
    std::vector<uint8_t> acl_vector;
    acl_vector.push_back(static_cast<uint8_t>(handle & 0xff));
    acl_vector.push_back(static_cast<uint8_t>((handle & 0x0f00) >> 8) |
                         ACL_BROADCAST_ACTIVE_SLAVE |
                         ACL_PACKET_BOUNDARY_COMPLETE);
    acl_vector.push_back(static_cast<uint8_t>(size & 0xff));
    acl_vector.push_back(static_cast<uint8_t>((size & 0xff00) >> 8));
    for (size_t i = 0; i < size; i++) {
      acl_vector.push_back(static_cast<uint8_t>(i + n));
    }
    acl_packet = acl_vector;
    bluetooth->sendAclData(acl_vector);

    // Check the loopback of the ACL packet
    EXPECT_TRUE(bluetooth_cb->WaitForCallback(kCallbackNameAclEventReceived)
                    .no_timeout);
    hidl_vec<uint8_t> acl_loopback = acl_queue.front();
    acl_queue.pop();

    EXPECT_EQ(acl_packet.size(), acl_loopback.size());
    size_t successful_bytes = 0;

    if (n == 0) logger.setTotalBytes(num_packets * size * 2);

    for (size_t i = 0; i < acl_packet.size(); i++) {
      if (acl_packet[i] == acl_loopback[i]) {
        successful_bytes = i;
      } else {
        ALOGE("Miscompare at %d (expected %x, got %x)", static_cast<int>(i),
              acl_packet[i], acl_loopback[i]);
        ALOGE("At %d (expected %x, got %x)", static_cast<int>(i + 1),
              acl_packet[i + 1], acl_loopback[i + 1]);
        break;
      }
    }
    EXPECT_EQ(acl_packet.size(), successful_bytes + 1);
  }
}

// Return the number of completed packets reported by the controller.
int BluetoothHidlTest::wait_for_completed_packets_event(uint16_t handle) {
    if (!bluetooth_cb->WaitForCallback(kCallbackNameHciEventReceived).no_timeout) {
        ALOGW("%s: WaitForCallback timed out.", __func__);
    }
    int packets_processed = 0;
    while (event_queue.size() > 0) {
        hidl_vec<uint8_t> event = event_queue.front();
        event_queue.pop();

        EXPECT_EQ(EVENT_NUMBER_OF_COMPLETED_PACKETS, event[EVENT_CODE_BYTE]);
        EXPECT_EQ(1, event[EVENT_NUMBER_OF_COMPLETED_PACKETS_NUM_HANDLES]);

        uint16_t event_handle = event[3] + (event[4] << 8);
        EXPECT_EQ(handle, event_handle);

        packets_processed += event[5] + (event[6] << 8);
  }
  return packets_processed;
}

// Send local loopback command and initialize SCO and ACL handles.
void BluetoothHidlTest::enterLoopbackMode(std::vector<uint16_t>& sco_handles,
                                          std::vector<uint16_t>& acl_handles) {
  hidl_vec<uint8_t> cmd = COMMAND_HCI_WRITE_LOOPBACK_MODE_LOCAL;
  bluetooth->sendHciCommand(cmd);

  // Receive connection complete events with data channels
  int connection_event_count = 0;
  hidl_vec<uint8_t> event;
  do {
      EXPECT_TRUE(bluetooth_cb->WaitForCallback(kCallbackNameHciEventReceived)
                      .no_timeout);
      event = event_queue.front();
      event_queue.pop();
      EXPECT_GT(event.size(),
                static_cast<size_t>(EVENT_COMMAND_COMPLETE_STATUS_BYTE));
      if (event[EVENT_CODE_BYTE] == EVENT_CONNECTION_COMPLETE) {
          EXPECT_GT(event.size(),
                    static_cast<size_t>(EVENT_CONNECTION_COMPLETE_TYPE));
          EXPECT_EQ(event[EVENT_LENGTH_BYTE],
                    EVENT_CONNECTION_COMPLETE_PARAM_LENGTH);
          uint8_t connection_type = event[EVENT_CONNECTION_COMPLETE_TYPE];

          EXPECT_TRUE(connection_type == EVENT_CONNECTION_COMPLETE_TYPE_SCO ||
                      connection_type == EVENT_CONNECTION_COMPLETE_TYPE_ACL);

          // Save handles
          uint16_t handle = event[EVENT_CONNECTION_COMPLETE_HANDLE_LSBYTE] |
                            event[EVENT_CONNECTION_COMPLETE_HANDLE_LSBYTE + 1]
                                << 8;
          if (connection_type == EVENT_CONNECTION_COMPLETE_TYPE_SCO)
              sco_handles.push_back(handle);
          else
              acl_handles.push_back(handle);

          ALOGD("Connect complete type = %d handle = %d",
                event[EVENT_CONNECTION_COMPLETE_TYPE], handle);
          connection_event_count++;
    }
  } while (event[EVENT_CODE_BYTE] == EVENT_CONNECTION_COMPLETE);

  EXPECT_GT(connection_event_count, 0);

  EXPECT_EQ(EVENT_COMMAND_COMPLETE, event[EVENT_CODE_BYTE]);
  EXPECT_EQ(cmd[0], event[EVENT_COMMAND_COMPLETE_OPCODE_LSBYTE]);
  EXPECT_EQ(cmd[1], event[EVENT_COMMAND_COMPLETE_OPCODE_LSBYTE + 1]);
  EXPECT_EQ(HCI_STATUS_SUCCESS, event[EVENT_COMMAND_COMPLETE_STATUS_BYTE]);
}

// Empty test: Initialize()/Close() are called in SetUp()/TearDown().
TEST_F(BluetoothHidlTest, InitializeAndClose) {}

// Send an HCI Reset with sendHciCommand and wait for a command complete event.
TEST_F(BluetoothHidlTest, HciReset) {
  hidl_vec<uint8_t> cmd = COMMAND_HCI_RESET;
  bluetooth->sendHciCommand(cmd);

  wait_for_command_complete_event(cmd);
}

// Read and check the HCI version of the controller.
TEST_F(BluetoothHidlTest, HciVersionTest) {
  hidl_vec<uint8_t> cmd = COMMAND_HCI_READ_LOCAL_VERSION_INFORMATION;
  bluetooth->sendHciCommand(cmd);

  EXPECT_TRUE(
      bluetooth_cb->WaitForCallback(kCallbackNameHciEventReceived).no_timeout);

  hidl_vec<uint8_t> event = event_queue.front();
  event_queue.pop();
  EXPECT_GT(event.size(), static_cast<size_t>(EVENT_LOCAL_LMP_VERSION_BYTE));

  EXPECT_EQ(EVENT_COMMAND_COMPLETE, event[EVENT_CODE_BYTE]);
  EXPECT_EQ(cmd[0], event[EVENT_COMMAND_COMPLETE_OPCODE_LSBYTE]);
  EXPECT_EQ(cmd[1], event[EVENT_COMMAND_COMPLETE_OPCODE_LSBYTE + 1]);
  EXPECT_EQ(HCI_STATUS_SUCCESS, event[EVENT_COMMAND_COMPLETE_STATUS_BYTE]);

  EXPECT_LE(HCI_MINIMUM_HCI_VERSION, event[EVENT_LOCAL_HCI_VERSION_BYTE]);
  EXPECT_LE(HCI_MINIMUM_LMP_VERSION, event[EVENT_LOCAL_LMP_VERSION_BYTE]);
}

// Send an unknown HCI command and wait for the error message.
TEST_F(BluetoothHidlTest, HciUnknownCommand) {
  hidl_vec<uint8_t> cmd = COMMAND_HCI_SHOULD_BE_UNKNOWN;
  bluetooth->sendHciCommand(cmd);

  EXPECT_TRUE(
      bluetooth_cb->WaitForCallback(kCallbackNameHciEventReceived).no_timeout);

  hidl_vec<uint8_t> event = event_queue.front();
  event_queue.pop();
  EXPECT_GT(event.size(),
            static_cast<size_t>(EVENT_COMMAND_STATUS_OPCODE_LSBYTE + 1));

  EXPECT_EQ(EVENT_COMMAND_COMPLETE, event[EVENT_CODE_BYTE]);
  EXPECT_EQ(cmd[0], event[EVENT_COMMAND_COMPLETE_OPCODE_LSBYTE]);
  EXPECT_EQ(cmd[1], event[EVENT_COMMAND_COMPLETE_OPCODE_LSBYTE + 1]);
  EXPECT_EQ(HCI_STATUS_UNKNOWN_HCI_COMMAND,
            event[EVENT_COMMAND_COMPLETE_STATUS_BYTE]);
}

// Enter loopback mode, but don't send any packets.
TEST_F(BluetoothHidlTest, WriteLoopbackMode) {
  std::vector<uint16_t> sco_connection_handles;
  std::vector<uint16_t> acl_connection_handles;
  enterLoopbackMode(sco_connection_handles, acl_connection_handles);
}

// Enter loopback mode and send single packets.
TEST_F(BluetoothHidlTest, LoopbackModeSinglePackets) {
  setBufferSizes();
  EXPECT_LT(0, max_sco_data_packet_length);
  EXPECT_LT(0, max_acl_data_packet_length);

  std::vector<uint16_t> sco_connection_handles;
  std::vector<uint16_t> acl_connection_handles;
  enterLoopbackMode(sco_connection_handles, acl_connection_handles);

  sendAndCheckHCI(1);

  // This should work, but breaks on some current platforms.  Figure out how to
  // grandfather older devices but test new ones.
  if (0 && sco_connection_handles.size() > 0) {
    sendAndCheckSCO(1, max_sco_data_packet_length, sco_connection_handles[0]);
    int sco_packets_sent = 1;
    int completed_packets = wait_for_completed_packets_event(sco_connection_handles[0]);
    if (sco_packets_sent != completed_packets) {
        ALOGW("%s: packets_sent (%d) != completed_packets (%d)", __func__, sco_packets_sent,
              completed_packets);
    }
  }

  if (acl_connection_handles.size() > 0) {
    sendAndCheckACL(1, max_acl_data_packet_length, acl_connection_handles[0]);
    int acl_packets_sent = 1;
    int completed_packets = wait_for_completed_packets_event(acl_connection_handles[0]);
    if (acl_packets_sent != completed_packets) {
        ALOGW("%s: packets_sent (%d) != completed_packets (%d)", __func__, acl_packets_sent,
              completed_packets);
    }
  }
}

// Enter loopback mode and send packets for bandwidth measurements.
TEST_F(BluetoothHidlTest, LoopbackModeBandwidth) {
  setBufferSizes();

  std::vector<uint16_t> sco_connection_handles;
  std::vector<uint16_t> acl_connection_handles;
  enterLoopbackMode(sco_connection_handles, acl_connection_handles);

  sendAndCheckHCI(NUM_HCI_COMMANDS_BANDWIDTH);

  // This should work, but breaks on some current platforms.  Figure out how to
  // grandfather older devices but test new ones.
  if (0 && sco_connection_handles.size() > 0) {
    sendAndCheckSCO(NUM_SCO_PACKETS_BANDWIDTH, max_sco_data_packet_length,
                    sco_connection_handles[0]);
    int sco_packets_sent = NUM_SCO_PACKETS_BANDWIDTH;
    int completed_packets = wait_for_completed_packets_event(sco_connection_handles[0]);
    if (sco_packets_sent != completed_packets) {
        ALOGW("%s: packets_sent (%d) != completed_packets (%d)", __func__, sco_packets_sent,
              completed_packets);
    }
  }

  if (acl_connection_handles.size() > 0) {
    sendAndCheckACL(NUM_ACL_PACKETS_BANDWIDTH, max_acl_data_packet_length,
                    acl_connection_handles[0]);
    int acl_packets_sent = NUM_ACL_PACKETS_BANDWIDTH;
    int completed_packets = wait_for_completed_packets_event(acl_connection_handles[0]);
    if (acl_packets_sent != completed_packets) {
        ALOGW("%s: packets_sent (%d) != completed_packets (%d)", __func__, acl_packets_sent,
              completed_packets);
    }
  }
}

int main(int argc, char** argv) {
  ::testing::AddGlobalTestEnvironment(new BluetoothHidlEnvironment);
  ::testing::InitGoogleTest(&argc, argv);
  int status = RUN_ALL_TESTS();
  ALOGI("Test result = %d", status);
  return status;
}
