#ifndef __DDP_AAL_H__
#define __DDP_AAL_H__

#define AAL_HIST_BIN        33	/* [0..32] */
#define AAL_DRE_POINT_NUM   29

#define AAL_SERVICE_FORCE_UPDATE 0x1

typedef struct {
	/* DRE */
	int dre_map_bypass;
	/* ESS */
	int cabc_gainlmt[33];
} DISP_AAL_INITREG;

typedef struct {
	unsigned int serviceFlags;
	int backlight;
	int colorHist;
	unsigned int maxHist[AAL_HIST_BIN];
	int requestPartial;
} DISP_AAL_HIST;

enum DISP_AAL_REFRESH_LATENCY {
	AAL_REFRESH_17MS = 17,
	AAL_REFRESH_33MS = 33
};

typedef struct {
	int DREGainFltStatus[AAL_DRE_POINT_NUM];
	int cabc_fltgain_force;	/* 10-bit ; [0,1023] */
	int cabc_gainlmt[33];
	int FinalBacklight;	/* 10-bit ; [0,1023] */
	int allowPartial;
	int refreshLatency;	/* DISP_AAL_REFRESH_LATENCY */
} DISP_AAL_PARAM;

#endif
