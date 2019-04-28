/* Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef _PKT_STATS_H_
#define _PKT_STATS_H_

/* Types of packet log events.
 * Tx stats will be sent from driver with the help of multiple events.
 * Need to parse the events PKTLOG_TYPE_TX_CTRL and PKTLOG_TYPE_TX_STAT
 * as of now for the required stats. Rest of the events can ignored.
 */
#define PKTLOG_TYPE_TX_CTRL         1
#define PKTLOG_TYPE_TX_STAT         2
#define PKTLOG_TYPE_TX_MSDU_ID      3
#define PKTLOG_TYPE_TX_FRM_HDR      4
/* Rx stats will be sent from driver with event ID- PKTLOG_TYPE_RX_STAT */
#define PKTLOG_TYPE_RX_STAT         5
#define PKTLOG_TYPE_RC_FIND         6
#define PKTLOG_TYPE_RC_UPDATE       7
#define PKTLOG_TYPE_TX_VIRT_ADDR    8
#define PKTLOG_TYPE_PKT_STATS       9
#define PKTLOG_TYPE_PKT_DUMP        10
#define PKTLOG_TYPE_PKT_DUMP_V2     11
#define PKTLOG_TYPE_MAX             12
#define BW_OFFSET 8
#define INVALID_RSSI 255

#define PKT_INFO_FLG_TX_LOCAL_S          0x1
#define PKT_INFO_FLG_RX_HOST_RXD         0x2
#define PKT_INFO_FLG_TX_REMOTE_S         0x4
#define PKT_INFO_FLG_RX_LOCAL_S          0x8
#define PKT_INFO_FLG_RX_REMOTE_S         0x10
#define PKT_INFO_FLG_RX_LOCAL_DISCARD_S  0x20
#define PKT_INFO_FLG_RX_REMOTE_DISCARD_S 0x40
#define PKT_INFO_FLG_RX_REORDER_STORE_S  0x80
#define PKT_INFO_FLG_RX_REORDER_DROP_S   0x100
#define PKT_INFO_FLG_RX_PEER_INFO_S      0x200
#define PKT_INFO_FLG_UNKNOWN_S           0x400
#define PKT_INFO_FLG_PKT_DUMP_V2         0x8000

/* MASK value of flags based on RX_STAT content.
 * These are the events that carry Rx decriptor
 */
#define PKT_INFO_FLG_RX_RXDESC_MASK \
        (PKT_INFO_FLG_RX_HOST_RXD | \
         PKT_INFO_FLG_RX_LOCAL_S | \
         PKT_INFO_FLG_RX_REMOTE_S | \
         PKT_INFO_FLG_RX_LOCAL_DISCARD_S | \
         PKT_INFO_FLG_RX_REMOTE_DISCARD_S)

/* Format of the packet stats event*/
typedef struct {
    u16 flags;
    u16 missed_cnt;
    u16 log_type;
    u16 size;
    u32 timestamp;
} __attribute__((packed)) wh_pktlog_hdr_t;

/* Format of the v2 packet stats event*/
typedef struct {
    u16 flags;
    u16 missed_cnt;
    u16 log_type;
    u16 size;
    u32 timestamp;
    u32 reserved;
} __attribute__((packed)) wh_pktlog_hdr_v2_t;

/*Rx stats specific structures. */
struct rx_attention {
    u32 first_mpdu                      :  1; //[0]
    u32 last_mpdu                       :  1; //[1]
    u32 reserved1                       :  6; //[7:2]
    u32 mgmt_type                       :  1; //[8]
    u32 ctrl_type                       :  1; //[9]
    u32 reserved2                       :  6; //[15:10]
    u32 overflow_err                    :  1; //[16]
    u32 msdu_length_err                 :  1; //[17]
    u32 tcp_udp_chksum_fail             :  1; //[18]
    u32 ip_chksum_fail                  :  1; //[19]
    u32 reserved3                       :  7; //[26:20]
    u32 mpdu_length_err                 :  1; //[27]
    u32 tkip_mic_err                    :  1; //[28]
    u32 decrypt_err                     :  1; //[29]
    u32 fcs_err                         :  1; //[30]
    u32 msdu_done                       :  1; //[31]
} __attribute__((packed));

struct rx_mpdu_start {
    u32 reserved1                       : 13; //[12:0]
    u32 encrypted                       :  1; //[13]
    u32 retry                           :  1; //[14]
    u32 reserved2                       :  1; //[15]
    u32 seq_num                         : 12; //[27:16]
    u32 reserved3                       :  4; //[31:28]
    u32 reserved4;
    u32 reserved5                       : 28; //[27:0]
    u32 tid                             :  4; //[31:28]
} __attribute__((packed));

/*Indicates the decap-format of the packet*/
enum {
    RAW=0,      // RAW: No decapsulation
    NATIVEWIFI,
    ETHERNET2,  // (DIX)
    ETHERNET    // (SNAP/LLC)
};

struct rx_msdu_start {
    u32 reserved1[2];
    u32 reserved2                       :  8; //[7:0]
    u32 decap_format                    :  2; //[9:8]
    u32 reserved3                       : 22; //[31:10]
} __attribute__((packed));

struct rx_msdu_end {
    u32 reserved1[4];
    u32 reserved2                       : 15;
    u32 last_msdu                       :  1; //[15]
    u32 reserved3                       : 16; //[31:16]
} __attribute__((packed));

struct rx_mpdu_end {
    u32 reserved1                       : 13; //[12:0]
    u32 overflow_err                    :  1; //[13]
    u32 last_mpdu                       :  1; //[14]
    u32 post_delim_err                  :  1; //[15]
    u32 reserved2                       : 12; //[27:16]
    u32 mpdu_length_err                 :  1; //[28]
    u32 tkip_mic_err                    :  1; //[29]
    u32 decrypt_err                     :  1; //[30]
    u32 fcs_err                         :  1; //[31]
} __attribute__((packed));

#define PREAMBLE_L_SIG_RATE     0x04
#define PREAMBLE_VHT_SIG_A_1    0x08
#define PREAMBLE_VHT_SIG_A_2    0x0c

/* Wifi Logger preamble */
#define WL_PREAMBLE_CCK  0
#define WL_PREAMBLE_OFDM 1
#define WL_PREAMBLE_HT   2
#define WL_PREAMBLE_VHT  3

#define BITMASK(x) ((1<<(x)) - 1 )
#define MAX_BA_WINDOW_SIZE 64
#define SEQ_NUM_RANGE 4096
#define BITMAP_VAR_SIZE 32

/* Contains MCS related stats */
struct rx_ppdu_start {
    u32 reserved1[4];
    u32 rssi_comb                       :  8; //[7:0]
    u32 reserved2                       : 24; //[31:8]
    u32 l_sig_rate                      :  4; //[3:0]
    u32 l_sig_rate_select               :  1; //[4]
    u32 reserved3                       : 19; //[23:5]
    u32 preamble_type                   :  8; //[31:24]
    u32 ht_sig_vht_sig_a_1              : 24; //[23:0]
    u32 reserved4                       :  8; //[31:24]
    u32 ht_sig_vht_sig_a_2              : 24; //[23:0]
    u32 reserved5                       :  8; //[31:25]
    u32 reserved6[2];
} __attribute__((packed));

struct rx_ppdu_end {
    u32 reserved1[16];
    u32 tsf_timestamp;
    u32 reserved2[5];
} __attribute__((packed));

#define MAX_MSDUS_PER_MPDU 3
#define MAX_RXMPDUS_PER_AMPDU 64
#define RX_HTT_HDR_STATUS_LEN 64
typedef struct {
    struct rx_attention attention;
    u32 reserved1;
    struct rx_mpdu_start mpdu_start;
    struct rx_msdu_start msdu_start;
    struct rx_msdu_end   msdu_end;
    struct rx_mpdu_end   mpdu_end;
    struct rx_ppdu_start ppdu_start;
    struct rx_ppdu_end   ppdu_end;
    char rx_hdr_status[RX_HTT_HDR_STATUS_LEN];
}__attribute__((packed)) rb_pkt_stats_t;

/*Tx stats specific structures. */
struct ppdu_status {
    u32 ba_start_seq_num                : 12; //[11:0]
    u32 reserved1                       :  3; //[14:12]
    u32 ba_status                       :  1; //[15]
    u32 reserved2                       : 15; //[30:16]
    u32 tx_ok                           :  1; //[31]
    u32 ba_bitmap_31_0                  : 32; //[31:0]
    u32 ba_bitmap_63_32                 : 32; //[31:0]
    u32 reserved3[8];
    u32 ack_rssi_ave                    :  8; //[7:0]
    u32 reserved4                       : 16; //[23:8]
    u32 total_tries                     :  5; //[28:24]
    u32 reserved5                       :  3; //[31:29]
    u32 reserved6[4];
} __attribute__((packed));

/*Contains tx timestamp*/
struct try_status {
    u32 timestamp                       : 23; //[22:0]
    u32 reserved1                       :  1; //[23]
    u32 series                          :  1; //[24]
    u32 reserved2                       :  3; //[27:25]
    u32 packet_bw                       :  2; //[29:28]
    u32 reserved3                       :  1; //[30]
    u32 tx_packet                       :  1; //[31]
} __attribute__((packed));

struct try_list {
    struct try_status try_st[16];
} __attribute__((packed));


struct tx_ppdu_end {
    struct try_list try_list;
    struct ppdu_status stat;
} __attribute__((packed));

/*Tx MCS and data rate ralated stats */
struct series_bw {
    u32 reserved1                       : 28; //[27:0]
    u32 short_gi                        :  1; //[28]
    u32 reserved2                       :  3; //[31:29]
    u32 reserved3                       : 24; //[23:21]
    u32 rate                            :  4; //[27:24]
    u32 nss                             :  2; //[29:28]
    u32 preamble_type                   :  2; //[31:30]
    u32 reserved4[2];
} __attribute__((packed));

enum tx_bw {
    BW_20_MHZ,
    BW_40_MHZ,
    BW_80_MHZ,
    BW_160_MHZ
};

#define DATA_PROTECTED 14
struct tx_ppdu_start {
    u32 reserved1[2];
    u32 start_seq_num                   : 12; //[11:0]
    u32 reserved2                       : 20; //[31:12]
    u32 seqnum_bitmap_31_0              : 32; //[31:0]
    u32 seqnum_bitmap_63_32             : 32; //[31:0]
    u32 reserved3[8];
    u32 reserved4                       : 15; //[14:0]
    u32 ampdu                           :  1; //[15]
    u32 no_ack                          :  1; //[16]
    u32 reserved5                       : 15; //[31:17]
    u32 reserved6                       : 16; //[15:0]
    u32 frame_control                   : 16; //[31:16]
    u32 reserved7                       : 16; //[23:21]
    u32 qos_ctl                         : 16; //[31:16]
    u32 reserved8[4];
    u32 reserved9                       : 24; //[23:21]
    u32 valid_s0_bw20                   :  1; //[24]
    u32 valid_s0_bw40                   :  1; //[25]
    u32 valid_s0_bw80                   :  1; //[26]
    u32 valid_s0_bw160                  :  1; //[27]
    u32 valid_s1_bw20                   :  1; //[28]
    u32 valid_s1_bw40                   :  1; //[29]
    u32 valid_s1_bw80                   :  1; //[30]
    u32 valid_s1_bw160                  :  1; //[31]
    struct series_bw s0_bw20;
    struct series_bw s0_bw40;
    struct series_bw s0_bw80;
    struct series_bw s0_bw160;
    struct series_bw s1_bw20;
    struct series_bw s1_bw40;
    struct series_bw s1_bw80;
    struct series_bw s1_bw160;
    u32 reserved10[3];
} __attribute__((packed));

#define PKTLOG_MAX_TXCTL_WORDS 57 /* +2 words for bitmap */
typedef struct {
    u32 reserved1[3];
    union {
        u32 txdesc_ctl[PKTLOG_MAX_TXCTL_WORDS];
        struct tx_ppdu_start ppdu_start;
    }u;
} __attribute__((packed)) wh_pktlog_txctl;

/* Required stats are spread across multiple
 * events(PKTLOG_TYPE_TX_CTRL and PKTLOG_TYPE_TX_STAT here).
 * Need to aggregate the stats collected in each event and write to the
 * ring buffer only after receiving all the expected stats.
 * Need to preserve the stats in hal_info till then and use tx_stats_events
 * flag to track the events.
 * prev_seq_no: Can used to track the events that come from driver and identify
 * if any event is missed.
 */

#define RING_BUF_ENTRY_SIZE 512
#define PKT_STATS_BUF_SIZE 128
struct pkt_stats_s {
    u8 tx_stats_events;
    /* TODO: Need to handle the case if size of the stats are more
     * than 512 bytes. Currently, the tx size is 34 bytes and ring buffer entry
     * size is 12 bytes.
     */
    u8 tx_stats[PKT_STATS_BUF_SIZE];
    u8 num_msdu;
    u16 start_seq_num;
    u16 ba_seq_num;
    u32 ba_bitmap_31_0;
    u32 ba_bitmap_63_32;
    u32 tx_seqnum_bitmap_31_0;
    u32 tx_seqnum_bitmap_63_32;
    u32 shifted_bitmap_31_0;
    u32 shifted_bitmap_63_32;
    bool isBlockAck;
    u8 tx_bandwidth;
    u8 series;
};

typedef union {
    struct {
        u16 rate                            :  4;
        u16 nss                             :  2;
        u16 preamble                        :  2;
        u16 bw                              :  2;
        u16 short_gi                        :  1;
        u16 reserved                        :  5;
    } mcs_s;
    u16 mcs;
} MCS;

typedef struct {
    MCS RxMCS;
    u16 last_transmit_rate;
    u16 rssi;
    u32 timestamp;
    u8  tid;
} rx_aggr_stats;


typedef struct drv_msg_s
{
    u16 length;
    u16 event_type;
    u32 timestamp_low;
    u32 timestamp_high;
    union {
        struct {
            u32 version;
            u32 msg_seq_no;
            u32 payload_len;
            u8  payload[0];
        } __attribute__((packed)) pkt_stats_event;
    } u;
} __attribute__((packed)) drv_msg_t;

typedef enum {
    START_MONITOR = 1,
    STOP_MONITOR,
    TX_MGMT_PKT,
    TX_DATA_PKT,
    RX_MGMT_PKT,
    RX_DATA_PKT,
} pktdump_event_type;

typedef struct {
    u8 status;
    u8 type;
    u32 driver_ts;
    u16 fw_ts;
} __attribute__((packed)) pktdump_hdr;

typedef struct {
    frame_type payload_type;
    u32 driver_timestamp_usec;
    u32 firmware_timestamp_usec;
    size_t frame_len;
    char *frame_content;
} frame_info_i;

typedef struct {
    // Prefix of MD5 hash of |frame_inf.frame_content|. If frame
    // content is not provided, prefix of MD5 hash over the same data
    // that would be in frame_content, if frame content were provided.
    char md5_prefix[MD5_PREFIX_LEN];  // Prefix of MD5 hash of packet bytes
    wifi_tx_packet_fate fate;
    frame_info_i frame_inf;
} wifi_tx_report_i;

typedef struct {
    // Prefix of MD5 hash of |frame_inf.frame_content|. If frame
    // content is not provided, prefix of MD5 hash over the same data
    // that would be in frame_content, if frame content were provided.
    char md5_prefix[MD5_PREFIX_LEN];
    wifi_rx_packet_fate fate;
    frame_info_i frame_inf;
} wifi_rx_report_i;

typedef struct {
    wifi_tx_report_i tx_fate_stats[MAX_FATE_LOG_LEN];
    size_t n_tx_stats_collected;
    wifi_rx_report_i rx_fate_stats[MAX_FATE_LOG_LEN];
    size_t n_rx_stats_collected;
} packet_fate_monitor_info;

#endif
