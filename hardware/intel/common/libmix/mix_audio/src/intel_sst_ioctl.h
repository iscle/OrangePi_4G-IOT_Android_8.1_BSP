#ifndef __INTEL_SST_IOCTL_H__
#define __INTEL_SST_IOCTL_H__

enum sst_codec_types {
/*  AUDIO/MUSIC CODEC Type Definitions */
	SST_CODEC_TYPE_UNKNOWN = 0,
	SST_CODEC_TYPE_PCM,	/* Pass through Audio codec */
	SST_CODEC_TYPE_MP3,
	SST_CODEC_TYPE_MP24,
	SST_CODEC_TYPE_AAC,
	SST_CODEC_TYPE_AACP,
	SST_CODEC_TYPE_eAACP,
	SST_CODEC_TYPE_WMA9,
	SST_CODEC_TYPE_WMA10,
	SST_CODEC_TYPE_WMA10P,
	SST_CODEC_TYPE_RA,
	SST_CODEC_TYPE_DDAC3,
	SST_CODEC_TYPE_STEREO_TRUE_HD,
	SST_CODEC_TYPE_STEREO_HD_PLUS,

	/*  VOICE CODEC Type Definitions */
	SST_CODEC_TYPE_VOICE_PCM = 0x21, /* Pass through voice codec */
	SST_CODEC_SRC = 0x64,
	SST_CODEC_MIXER = 0x65,
	SST_CODEC_DOWN_MIXER = 0x66,
	SST_CODEC_VOLUME_CONTROL = 0x67,
	SST_CODEC_OEM1 = 0xC8,
	SST_CODEC_OEM2 = 0xC9,
};

enum snd_sst_stream_ops {
	STREAM_OPS_PLAYBACK = 0,	/* Decode */
	STREAM_OPS_CAPTURE,		/* Encode */
	STREAM_OPS_PLAYBACK_DRM,	/* Play Audio/Voice */
	STREAM_OPS_PLAYBACK_ALERT,	/* Play Audio/Voice */
	STREAM_OPS_CAPTURE_VOICE_CALL,	/* CSV Voice recording */
};

enum stream_type {
	STREAM_TYPE_MUSIC = 1,
	STREAM_TYPE_VOICE
};

/* Firmware Version info */
struct snd_sst_fw_version {
	__u8 build;	/* build number*/
	__u8 minor;	/* minor number*/
	__u8 major;	/* major number*/
	__u8 type; /* build type*/
};

/* Port info structure */
struct snd_sst_port_info {
	__u16 port_type;
	__u16  reserved;
};

/* Mixer info structure */
struct snd_sst_mix_info {
	__u16 max_streams;
	__u16 reserved;
};

/* PCM Parameters */
struct snd_pcm_params {
	__u16 codec;	/* codec type */
	__u8 num_chan;	/* 1=Mono, 2=Stereo	*/
	__u8 pcm_wd_sz;	/* 16/24 - bit*/
	__u32 brate;	/* Bitrate in bits per second */
	__u32 sfreq;	/* Sampling rate in Hz */
	__u16 frame_size;
	__u16 samples_per_frame;	/* Frame size num samples per frame */
	__u32 period_count; /* period elapsed time count, in samples,*/
};

/* MP3 Music Parameters Message */
struct snd_mp3_params {
	__u16  codec;
	__u8   num_chan;	/* 1=Mono, 2=Stereo	*/
	__u8   pcm_wd_sz; /* 16/24 - bit*/
	__u32  brate; /* Use the hard coded value. */
	__u32  sfreq; /* Sampling freq eg. 8000, 441000, 48000 */
	__u8  crc_check; /* crc_check - disable (0) or enable (1) */
	__u8  op_align; /* op align 0- 16 bit, 1- MSB, 2 LSB*/
	__u16  reserved;	/* Unused */
};

#define AAC_BIT_STREAM_ADTS		0
#define AAC_BIT_STREAM_ADIF		1
#define AAC_BIT_STREAM_RAW		2

/* AAC Music Parameters Message */
struct snd_aac_params {
	__u16 codec;
	__u8 num_chan; /* 1=Mono, 2=Stereo*/
	__u8 pcm_wd_sz; /* 16/24 - bit*/
	__u32 brate;
	__u32 sfreq; /* Sampling freq eg. 8000, 441000, 48000 */
	__u32 aac_srate;	/* Plain AAC decoder operating sample rate */
	__u8 mpg_id; /* 0=MPEG-2, 1=MPEG-4 */
	__u8 bs_format; /* input bit stream format adts=0, adif=1, raw=2 */
	__u8 aac_profile; /* 0=Main Profile, 1=LC profile, 3=SSR profile */
	__u8 ext_chl; /* No.of external channels */
	__u8 aot; /* Audio object type. 1=Main , 2=LC , 3=SSR, 4=SBR*/
	__u8 op_align; /* output alignment 0=16 bit , 1=MSB, 2= LSB align */
	__u8 brate_type; /* 0=CBR, 1=VBR */
	__u8 crc_check; /* crc check 0= disable, 1=enable */
	__s8 bit_stream_format[8]; /* input bit stream format adts/adif/raw */
	__u8 jstereo; /* Joint stereo Flag */
	__u8 sbr_present; /* 1 = SBR Present, 0 = SBR absent, for RAW */
	__u8 downsample;       /* 1 = Downsampling ON, 0 = Downsampling OFF */
	__u8 num_syntc_elems; /* 1- Mono/stereo, 0 - Dual Mono, 0 - for raw */
	__s8 syntc_id[2]; /* 0 for ID_SCE(Dula Mono), -1 for raw */
	__s8 syntc_tag[2]; /* raw - -1 and 0 -16 for rest of the streams */
	__u8 pce_present; /* Flag. 1- present 0 - not present, for RAW */
	__u8 reserved;
	__u16 reserved1;

};

/* WMA Music Parameters Message */
struct snd_wma_params {
	__u16  codec;
	__u8   num_chan;	/* 1=Mono, 2=Stereo	*/
	__u8   pcm_wd_sz;	/* 16/24 - bit*/
	__u32  brate; 	/* Use the hard coded value. */
	__u32  sfreq;	/* Sampling freq eg. 8000, 441000, 48000 */
	__u32  channel_mask;  /* Channel Mask */
	__u16  format_tag;	/* Format Tag */
	__u16  block_align;	/* packet size */
	__u16  wma_encode_opt;/* Encoder option */
	__u8 op_align;	/* op align 0- 16 bit, 1- MSB, 2 LSB*/
	__u8 pcm_src;	/* input pcm bit width*/
};

/* Pre processing param structure */
struct snd_prp_params {
	__u32  reserved;	/* No pre-processing defined yet */
};

/* Post processing Capability info structure */
struct snd_sst_postproc_info {
	__u32 src_min;		/* Supported SRC Min sampling freq */
	__u32 src_max;		/* Supported SRC Max sampling freq */
	__u8  src;		/* 0=Not supported, 1=Supported */
	__u8  bass_boost;		/* 0=Not Supported, 1=Supported */
	__u8  stereo_widening;	/* 0=Not Supported, 1=Supported */
	__u8  volume_control; 	/* 0=Not Supported, 1=Supported */
	__s16 min_vol;		/* Minimum value of Volume in dB */
	__s16 max_vol;		/* Maximum value of Volume in dB */
	__u8 mute_control;		/*0=No Mute, 1=Mute*/
	__u8 reserved1;
	__u16 reserved2;
};

/* pre processing Capability info structure */
struct snd_sst_prp_info {
	__s16 min_vol;			/* Minimum value of Volume in dB */
	__s16 max_vol;			/* Maximum value of Volume in dB */
	__u8 volume_control; 		/* 0=Not Supported, 1=Supported */
	__u8 reserved1;			/* for 32 bit alignment */
	__u16 reserved2;			/* for 32 bit alignment */
} __attribute__ ((packed));

/* Firmware capabilities info */
struct snd_sst_fw_info {
	struct snd_sst_fw_version fw_version; /* Firmware version */
	__u8 audio_codecs_supported[8];	/* Codecs supported by FW */
	__u32 recommend_min_duration; /* Min duration for Low power Playback*/
	__u8 max_pcm_streams_supported; /*Max number of PCM streams supported */
	__u8 max_enc_streams_supported;	/*Max number of Encoded streams */
	__u16 reserved;			/* 32 bit alignment*/
	struct snd_sst_postproc_info pop_info; /* Post processing capability*/
	struct snd_sst_prp_info prp_info; /* pre_processing mod cap info */
	struct snd_sst_port_info port_info[2]; /* Port info */
	struct snd_sst_mix_info mix_info; 	/* Mixer info */
	__u32 min_input_buf; /*minmum i/p buffer for decode*/
};

/* Add the codec parameter structures for new codecs to be supported */
#define CODEC_PARAM_STRUCTURES \
	struct snd_pcm_params pcm_params; \
	struct snd_mp3_params mp3_params; \
	struct snd_aac_params aac_params; \
	struct snd_wma_params wma_params;

/* Pre and Post Processing param structures */
#define PPP_PARAM_STRUCTURES \
	struct snd_prp_params prp_params;

/* Codec params struture */
union  snd_sst_codec_params {
	 CODEC_PARAM_STRUCTURES;
};

/* Pre-processing params struture */
union snd_sst_ppp_params{
	 PPP_PARAM_STRUCTURES;
};

struct snd_sst_stream_params {
	union snd_sst_codec_params uc;
} __attribute__ ((packed));

struct snd_sst_params {
	__u32 result;
	__u32 stream_id;
	__u8 codec;
	__u8 ops;
	__u8 stream_type;
	struct snd_sst_stream_params sparams;
};

/*ioctl related stuff here*/
struct snd_sst_pmic_config {
	__u32  sfreq;                /* Sampling rate in Hz */
	__u16  num_chan;             /* Mono =1 or Stereo =2 */
	__u16  pcm_wd_sz;            /* Number of bits per sample */
} __attribute__ ((packed));

struct snd_sst_get_stream_params {
	struct snd_sst_params codec_params;
	struct snd_sst_pmic_config pcm_params;
};

enum snd_sst_target_type {
	SND_SST_TARGET_PMIC = 1,
	SND_SST_TARGET_OTHER,
};

enum snd_sst_port_action {
	SND_SST_PORT_PREPARE = 1,
	SND_SST_PORT_ACTIVATE,
};

/* Target selection per device structure */
struct snd_sst_slot_info {
	__u8 mix_enable;		/* Mixer enable or disable */
	__u8 device_type;
	__u8 device_instance; 	/* 0, 1, 2 */
	__u8 target_type;
	__u16 slot[2];
	__u8 master;
	__u8 action;
	__u16 reserved;
	struct snd_sst_pmic_config pcm_params;
} __attribute__ ((packed));

/* Target device list structure */
struct snd_sst_target_device  {
	__u32 device_route;
	struct snd_sst_slot_info devices[2];
} __attribute__ ((packed));

struct snd_sst_driver_info {
	__u32 version;	/* Version of the driver */
	__u32 active_pcm_streams;
	__u32 active_enc_streams;
	__u32 max_pcm_streams;
	__u32 max_enc_streams;
	__u32 buf_per_stream;
};

struct snd_sst_vol {
	__u32	stream_id;
	__s32		volume;
	__u32	ramp_duration;
	__u32 ramp_type;		/* Ramp type, default=0 */
};

struct snd_sst_mute {
	__u32	stream_id;
	__u32	mute;
};

enum snd_sst_buff_type {
	SST_BUF_USER = 1,
	SST_BUF_MMAP,
	SST_BUF_RAR,
};

struct snd_sst_mmap_buff_entry {
	unsigned int offset;
	unsigned int size;
};

struct snd_sst_mmap_buffs {
	unsigned int entries;
	enum snd_sst_buff_type type;
	struct snd_sst_mmap_buff_entry *buff;
};

struct snd_sst_buff_entry {
	void *buffer;
	unsigned int size;
};

struct snd_sst_buffs {
	unsigned int entries;
	__u8 type;
	struct snd_sst_buff_entry *buff_entry;
};

struct snd_sst_dbufs  {
	unsigned long long input_bytes_consumed;
	unsigned long long output_bytes_produced;
	struct snd_sst_buffs *ibufs;
	struct snd_sst_buffs *obufs;
};

/*IOCTL defined here*/
/*SST MMF IOCTLS only*/
#define SNDRV_SST_STREAM_SET_PARAMS _IOR('L', 0x00, \
					struct snd_sst_stream_params *)
#define SNDRV_SST_STREAM_GET_PARAMS _IOWR('L', 0x01, \
					struct snd_sst_get_stream_params *)
#define SNDRV_SST_STREAM_GET_TSTAMP _IOWR('L', 0x02, __u64 *)
#define	SNDRV_SST_STREAM_DECODE	_IOWR('L', 0x03, struct snd_sst_dbufs *)
#define SNDRV_SST_STREAM_BYTES_DECODED _IOWR('L', 0x04, __u64 *)
#define SNDRV_SST_STREAM_START	_IO('A', 0x42)
#define SNDRV_SST_STREAM_DROP 	_IO('A', 0x43)
#define SNDRV_SST_STREAM_DRAIN	_IO('A', 0x44)
#define SNDRV_SST_STREAM_PAUSE 	_IOW('A', 0x45, int)
#define SNDRV_SST_STREAM_RESUME _IO('A', 0x47)
#define SNDRV_SST_MMAP_PLAY	_IOW('L', 0x05, struct snd_sst_mmap_buffs *)
#define SNDRV_SST_MMAP_CAPTURE _IOW('L', 0x06, struct snd_sst_mmap_buffs *)
/*SST common ioctls */
#define SNDRV_SST_DRIVER_INFO	_IOR('L', 0x10, struct snd_sst_driver_info *)
#define SNDRV_SST_SET_VOL	_IOW('L', 0x11, struct snd_sst_vol *)
#define SNDRV_SST_GET_VOL	_IOW('L', 0x12, struct snd_sst_vol *)
#define SNDRV_SST_MUTE		_IOW('L', 0x13, struct snd_sst_mute *)
/*AM Ioctly only*/
#define SNDRV_SST_FW_INFO	_IOR('L', 0x20,  struct snd_sst_fw_info *)
#define SNDRV_SST_SET_TARGET_DEVICE _IOW('L', 0x21, \
					struct snd_sst_target_device *)

#endif /*__INTEL_SST_IOCTL_H__*/
