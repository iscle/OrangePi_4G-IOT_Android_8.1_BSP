/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef DRC_MATH_H_
#define DRC_MATH_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stddef.h>
#include <math.h>

/* Uncomment to use the slow but accurate functions. */
/* #define SLOW_DB_TO_LINEAR */
/* #define SLOW_LINEAR_TO_DB */
/* #define SLOW_WARP_SIN */
/* #define SLOW_KNEE_EXP */
/* #define SLOW_FREXPF */

#define PI_FLOAT 3.141592653589793f
#define PI_OVER_TWO_FLOAT 1.57079632679489661923f
#define TWO_OVER_PI_FLOAT 0.63661977236758134f
#define NEG_TWO_DB 0.7943282347242815f /* -2dB = 10^(-2/20) */

#ifndef max
#define max(a, b) ({ __typeof__(a) _a = (a);	\
			__typeof__(b) _b = (b);	\
			_a > _b ? _a : _b; })
#endif

#ifndef min
#define min(a, b) ({ __typeof__(a) _a = (a);	\
			__typeof__(b) _b = (b);	\
			_a < _b ? _a : _b; })
#endif

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define PURE __attribute__ ((pure))
static inline float decibels_to_linear(float decibels) PURE;
static inline float linear_to_decibels(float linear) PURE;
static inline float warp_sinf(float x) PURE;
static inline float warp_asinf(float x) PURE;
static inline float knee_expf(float input) PURE;

extern float db_to_linear[201]; /* from -100dB to 100dB */

void drc_math_init();

union ieee754_float {
    float f;

    /* This is the IEEE 754 single-precision format.  */
    struct
    {
        /* Little endian.  */
        unsigned int mantissa:23;
        unsigned int exponent:8;
        unsigned int negative:1;
    } ieee;
};

/* Rounds the input number to the nearest integer */
#ifdef __arm__
static inline float round_int(float x)
{
	return x < 0 ? (int)(x - 0.5f) : (int)(x + 0.5f);
}
#else
#define round_int rintf /* glibc will use roundss if SSE4.1 is available */
#endif

static inline float decibels_to_linear(float decibels)
{
#ifdef SLOW_DB_TO_LINEAR
	/* 10^(x/20) = e^(x * log(10^(1/20))) */
	return expf(0.1151292546497022f * decibels);
#else
	float x;
	float fi;
	int i;

	fi = round_int(decibels);
	x = decibels - fi;
	i = (int)fi;
	i = max(min(i, 100), -100);

	/* Coefficients obtained from:
	 * fpminimax(10^(x/20), [|1,2,3|], [|SG...|], [-0.5;0.5], 1, absolute);
	 * max error ~= 7.897e-8
	 */
	const float A3 = 2.54408805631101131439208984375e-4f;
	const float A2 = 6.628888659179210662841796875e-3f;
	const float A1 = 0.11512924730777740478515625f;
	const float A0 = 1.0f;

	float x2 = x * x;
	return ((A3 * x + A2)*x2 + (A1 * x + A0)) * db_to_linear[i+100];
#endif
}

static inline float frexpf_fast(float x, int *e)
{
#ifdef SLOW_FREXPF
	return frexpf(x, e);
#else
	union ieee754_float u;
	u.f = x;
	int exp = u.ieee.exponent;
	if (exp == 0xff)
		return NAN;
	*e = exp - 126;
	u.ieee.exponent = 126;
	return u.f;
#endif
}

static inline float linear_to_decibels(float linear)
{
	/* For negative or zero, just return a very small dB value. */
	if (linear <= 0)
		return -1000;

#ifdef SLOW_LINEAR_TO_DB
	/* 20 * log10(x) = 20 / log(10) * log(x) */
	return 8.6858896380650366f * logf(linear);
#else
	int e = 0;
	float x = frexpf_fast(linear, &e);
	float exp = e;

	if (x > 0.707106781186548f) {
		x *= 0.707106781186548f;
		exp += 0.5f;
	}

	/* Coefficients obtained from:
	 * fpminimax(log10(x), 5, [|SG...|], [1/2;sqrt(2)/2], absolute);
	 * max err ~= 6.088e-8
	 */
	const float A5 = 1.131880283355712890625f;
	const float A4 = -4.258677959442138671875f;
	const float A3 = 6.81631565093994140625f;
	const float A2 = -6.1185703277587890625f;
	const float A1 = 3.6505267620086669921875f;
	const float A0 = -1.217894077301025390625f;

	float x2 = x * x;
	float x4 = x2 * x2;
	return ((A5 * x + A4)*x4 + (A3 * x + A2)*x2 + (A1 * x + A0)) * 20.0f
		+ exp * 6.0205999132796239f;
#endif
}


static inline float warp_sinf(float x)
{
#ifdef SLOW_WARP_SIN
	return sinf(PI_OVER_TWO_FLOAT * x);
#else
	/* Coefficients obtained from:
	 * fpminimax(sin(x*pi/2), [|1,3,5,7|], [|SG...|], [-1e-30;1], absolute)
	 * max err ~= 5.901e-7
	 */
	const float A7 = -4.3330336920917034149169921875e-3f;
	const float A5 = 7.9434238374233245849609375e-2f;
	const float A3 = -0.645892798900604248046875f;
	const float A1 = 1.5707910060882568359375f;

	float x2 = x * x;
	float x4 = x2 * x2;
	return x * ((A7 * x2 + A5) * x4 + (A3 * x2 + A1));
#endif
}

static inline float warp_asinf(float x)
{
	return asinf(x) * TWO_OVER_PI_FLOAT;
}

static inline float knee_expf(float input)
{
#ifdef SLOW_KNEE_EXP
	return expf(input);
#else
	/* exp(x) = decibels_to_linear(20*log10(e)*x) */
	return decibels_to_linear(8.685889638065044f * input);
#endif
}

/* Returns 1 for nan or inf, 0 otherwise. This is faster than the alternative
 * return x != 0 && !isnormal(x);
 */
static inline int isbadf(float x)
{
	union ieee754_float u;
	u.f = x;
	return u.ieee.exponent == 0xff;
}

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* DRC_MATH_H_ */
