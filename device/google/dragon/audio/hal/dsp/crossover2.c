/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include <string.h>
#include "crossover2.h"
#include "biquad.h"

static void lr42_set(struct lr42 *lr42, enum biquad_type type, float freq)
{
	struct biquad q;
	biquad_set(&q, type, freq, 0, 0);
	memset(lr42, 0, sizeof(*lr42));
	lr42->b0 = q.b0;
	lr42->b1 = q.b1;
	lr42->b2 = q.b2;
	lr42->a1 = q.a1;
	lr42->a2 = q.a2;
}

/* Split input data using two LR4 filters, put the result into the input array
 * and another array.
 *
 * data0 --+-- lp --> data0
 *         |
 *         \-- hp --> data1
 */
#if defined(__ARM_NEON__)
#include <arm_neon.h>
static void lr42_split(struct lr42 *lp, struct lr42 *hp, int count,
		       float *data0L, float *data0R,
		       float *data1L, float *data1R)
{
	float32x4_t x1 = {lp->x1L, hp->x1L, lp->x1R, hp->x1R};
	float32x4_t x2 = {lp->x2L, hp->x2L, lp->x2R, hp->x2R};
	float32x4_t y1 = {lp->y1L, hp->y1L, lp->y1R, hp->y1R};
	float32x4_t y2 = {lp->y2L, hp->y2L, lp->y2R, hp->y2R};
	float32x4_t z1 = {lp->z1L, hp->z1L, lp->z1R, hp->z1R};
	float32x4_t z2 = {lp->z2L, hp->z2L, lp->z2R, hp->z2R};
	float32x4_t b0 = {lp->b0, hp->b0, lp->b0, hp->b0};
	float32x4_t b1 = {lp->b1, hp->b1, lp->b1, hp->b1};
	float32x4_t b2 = {lp->b2, hp->b2, lp->b2, hp->b2};
	float32x4_t a1 = {lp->a1, hp->a1, lp->a1, hp->a1};
	float32x4_t a2 = {lp->a2, hp->a2, lp->a2, hp->a2};

	__asm__ __volatile__(
		/* q0 = x, q1 = y, q2 = z */
		"1:                                     \n"
		"vmul.f32 q1, %q[b1], %q[x1]            \n"
		"vld1.32 d0[], [%[data0L]]              \n"
		"vld1.32 d1[], [%[data0R]]              \n"
		"subs %[count], #1                      \n"
		"vmul.f32 q2, %q[b1], %q[y1]            \n"
		"vmla.f32 q1, %q[b0], q0                \n"
		"vmla.f32 q1, %q[b2], %q[x2]            \n"
		"vmov.f32 %q[x2], %q[x1]                \n"
		"vmov.f32 %q[x1], q0                    \n"
		"vmls.f32 q1, %q[a1], %q[y1]            \n"
		"vmls.f32 q1, %q[a2], %q[y2]            \n"
		"vmla.f32 q2, %q[b0], q1                \n"
		"vmla.f32 q2, %q[b2], %q[y2]            \n"
		"vmov.f32 %q[y2], %q[y1]                \n"
		"vmov.f32 %q[y1], q1                    \n"
		"vmls.f32 q2, %q[a1], %q[z1]            \n"
		"vmls.f32 q2, %q[a2], %q[z2]            \n"
		"vmov.f32 %q[z2], %q[z1]                \n"
		"vmov.f32 %q[z1], q2                    \n"
		"vst1.f32 d4[0], [%[data0L]]!           \n"
		"vst1.f32 d4[1], [%[data1L]]!           \n"
		"vst1.f32 d5[0], [%[data0R]]!           \n"
		"vst1.f32 d5[1], [%[data1R]]!           \n"
		"bne 1b                                 \n"
		: /* output */
		  "=r"(data0L),
		  "=r"(data0R),
		  "=r"(data1L),
		  "=r"(data1R),
		  "=r"(count),
		  [x1]"+w"(x1),
		  [x2]"+w"(x2),
		  [y1]"+w"(y1),
		  [y2]"+w"(y2),
		  [z1]"+w"(z1),
		  [z2]"+w"(z2)
		: /* input */
		  [data0L]"0"(data0L),
		  [data0R]"1"(data0R),
		  [data1L]"2"(data1L),
		  [data1R]"3"(data1R),
		  [count]"4"(count),
		  [b0]"w"(b0),
		  [b1]"w"(b1),
		  [b2]"w"(b2),
		  [a1]"w"(a1),
		  [a2]"w"(a2)
		: /* clobber */
		  "q0", "q1", "q2", "memory", "cc"
		);

	lp->x1L = x1[0]; lp->x1R = x1[2];
	lp->x2L = x2[0]; lp->x2R = x2[2];
	lp->y1L = y1[0]; lp->y1R = y1[2];
	lp->y2L = y2[0]; lp->y2R = y2[2];
	lp->z1L = z1[0]; lp->z1R = z1[2];
	lp->z2L = z2[0]; lp->z2R = z2[2];

	hp->x1L = x1[1]; hp->x1R = x1[3];
	hp->x2L = x2[1]; hp->x2R = x2[3];
	hp->y1L = y1[1]; hp->y1R = y1[3];
	hp->y2L = y2[1]; hp->y2R = y2[3];
	hp->z1L = z1[1]; hp->z1R = z1[3];
	hp->z2L = z2[1]; hp->z2R = z2[3];
}
#elif defined(__SSE3__) && defined(__x86_64__)
#include <emmintrin.h>
static void lr42_split(struct lr42 *lp, struct lr42 *hp, int count,
		       float *data0L, float *data0R,
		       float *data1L, float *data1R)
{
	__m128 x1 = {lp->x1L, hp->x1L, lp->x1R, hp->x1R};
	__m128 x2 = {lp->x2L, hp->x2L, lp->x2R, hp->x2R};
	__m128 y1 = {lp->y1L, hp->y1L, lp->y1R, hp->y1R};
	__m128 y2 = {lp->y2L, hp->y2L, lp->y2R, hp->y2R};
	__m128 z1 = {lp->z1L, hp->z1L, lp->z1R, hp->z1R};
	__m128 z2 = {lp->z2L, hp->z2L, lp->z2R, hp->z2R};
	__m128 b0 = {lp->b0, hp->b0, lp->b0, hp->b0};
	__m128 b1 = {lp->b1, hp->b1, lp->b1, hp->b1};
	__m128 b2 = {lp->b2, hp->b2, lp->b2, hp->b2};
	__m128 a1 = {lp->a1, hp->a1, lp->a1, hp->a1};
	__m128 a2 = {lp->a2, hp->a2, lp->a2, hp->a2};

	__asm__ __volatile__(
		"1:                                     \n"
		"movss (%[data0L]), %%xmm2              \n"
		"movss (%[data0R]), %%xmm1              \n"
		"shufps $0, %%xmm1, %%xmm2              \n"
		"mulps %[b2],%[x2]                      \n"
		"movaps %[b0], %%xmm0                   \n"
		"mulps %[a2],%[z2]                      \n"
		"movaps %[b1], %%xmm1                   \n"
		"mulps %%xmm2,%%xmm0                    \n"
		"mulps %[x1],%%xmm1                     \n"
		"addps %%xmm1,%%xmm0                    \n"
		"movaps %[a1],%%xmm1                    \n"
		"mulps %[y1],%%xmm1                     \n"
		"addps %[x2],%%xmm0                     \n"
		"movaps %[b1],%[x2]                     \n"
		"mulps %[y1],%[x2]                      \n"
		"subps %%xmm1,%%xmm0                    \n"
		"movaps %[a2],%%xmm1                    \n"
		"mulps %[y2],%%xmm1                     \n"
		"mulps %[b2],%[y2]                      \n"
		"subps %%xmm1,%%xmm0                    \n"
		"movaps %[b0],%%xmm1                    \n"
		"mulps %%xmm0,%%xmm1                    \n"
		"addps %[x2],%%xmm1                     \n"
		"movaps %[x1],%[x2]                     \n"
		"movaps %%xmm2,%[x1]                    \n"
		"addps %[y2],%%xmm1                     \n"
		"movaps %[a1],%[y2]                     \n"
		"mulps %[z1],%[y2]                      \n"
		"subps %[y2],%%xmm1                     \n"
		"movaps %[y1],%[y2]                     \n"
		"movaps %%xmm0,%[y1]                    \n"
		"subps %[z2],%%xmm1                     \n"
		"movaps %[z1],%[z2]                     \n"
		"movaps %%xmm1,%[z1]                    \n"
		"movss %%xmm1, (%[data0L])              \n"
		"shufps $0x39, %%xmm1, %%xmm1           \n"
		"movss %%xmm1, (%[data1L])              \n"
		"shufps $0x39, %%xmm1, %%xmm1           \n"
		"movss %%xmm1, (%[data0R])              \n"
		"shufps $0x39, %%xmm1, %%xmm1           \n"
		"movss %%xmm1, (%[data1R])              \n"
		"add $4, %[data0L]                      \n"
		"add $4, %[data1L]                      \n"
		"add $4, %[data0R]                      \n"
		"add $4, %[data1R]                      \n"
		"sub $1, %[count]                       \n"
		"jnz 1b                                 \n"
		: /* output */
		  [data0L]"+r"(data0L),
		  [data0R]"+r"(data0R),
		  [data1L]"+r"(data1L),
		  [data1R]"+r"(data1R),
		  [count]"+r"(count),
		  [x1]"+x"(x1),
		  [x2]"+x"(x2),
		  [y1]"+x"(y1),
		  [y2]"+x"(y2),
		  [z1]"+x"(z1),
		  [z2]"+x"(z2)
		: /* input */
		  [b0]"x"(b0),
		  [b1]"x"(b1),
		  [b2]"x"(b2),
		  [a1]"x"(a1),
		  [a2]"x"(a2)
		: /* clobber */
		  "xmm0", "xmm1", "xmm2", "memory", "cc"
		);

	lp->x1L = x1[0]; lp->x1R = x1[2];
	lp->x2L = x2[0]; lp->x2R = x2[2];
	lp->y1L = y1[0]; lp->y1R = y1[2];
	lp->y2L = y2[0]; lp->y2R = y2[2];
	lp->z1L = z1[0]; lp->z1R = z1[2];
	lp->z2L = z2[0]; lp->z2R = z2[2];

	hp->x1L = x1[1]; hp->x1R = x1[3];
	hp->x2L = x2[1]; hp->x2R = x2[3];
	hp->y1L = y1[1]; hp->y1R = y1[3];
	hp->y2L = y2[1]; hp->y2R = y2[3];
	hp->z1L = z1[1]; hp->z1R = z1[3];
	hp->z2L = z2[1]; hp->z2R = z2[3];
}
#else
static void lr42_split(struct lr42 *lp, struct lr42 *hp, int count,
		       float *data0L, float *data0R,
		       float *data1L, float *data1R)
{
	float lx1L = lp->x1L, lx1R = lp->x1R;
	float lx2L = lp->x2L, lx2R = lp->x2R;
	float ly1L = lp->y1L, ly1R = lp->y1R;
	float ly2L = lp->y2L, ly2R = lp->y2R;
	float lz1L = lp->z1L, lz1R = lp->z1R;
	float lz2L = lp->z2L, lz2R = lp->z2R;
	float lb0 = lp->b0;
	float lb1 = lp->b1;
	float lb2 = lp->b2;
	float la1 = lp->a1;
	float la2 = lp->a2;

	float hx1L = hp->x1L, hx1R = hp->x1R;
	float hx2L = hp->x2L, hx2R = hp->x2R;
	float hy1L = hp->y1L, hy1R = hp->y1R;
	float hy2L = hp->y2L, hy2R = hp->y2R;
	float hz1L = hp->z1L, hz1R = hp->z1R;
	float hz2L = hp->z2L, hz2R = hp->z2R;
	float hb0 = hp->b0;
	float hb1 = hp->b1;
	float hb2 = hp->b2;
	float ha1 = hp->a1;
	float ha2 = hp->a2;

	int i;
	for (i = 0; i < count; i++) {
		float xL, yL, zL, xR, yR, zR;
		xL = data0L[i];
		xR = data0R[i];
		yL = lb0*xL + lb1*lx1L + lb2*lx2L - la1*ly1L - la2*ly2L;
		yR = lb0*xR + lb1*lx1R + lb2*lx2R - la1*ly1R - la2*ly2R;
		zL = lb0*yL + lb1*ly1L + lb2*ly2L - la1*lz1L - la2*lz2L;
		zR = lb0*yR + lb1*ly1R + lb2*ly2R - la1*lz1R - la2*lz2R;
		lx2L = lx1L;
		lx2R = lx1R;
		lx1L = xL;
		lx1R = xR;
		ly2L = ly1L;
		ly2R = ly1R;
		ly1L = yL;
		ly1R = yR;
		lz2L = lz1L;
		lz2R = lz1R;
		lz1L = zL;
		lz1R = zR;
		data0L[i] = zL;
		data0R[i] = zR;

		yL = hb0*xL + hb1*hx1L + hb2*hx2L - ha1*hy1L - ha2*hy2L;
		yR = hb0*xR + hb1*hx1R + hb2*hx2R - ha1*hy1R - ha2*hy2R;
		zL = hb0*yL + hb1*hy1L + hb2*hy2L - ha1*hz1L - ha2*hz2L;
		zR = hb0*yR + hb1*hy1R + hb2*hy2R - ha1*hz1R - ha2*hz2R;
		hx2L = hx1L;
		hx2R = hx1R;
		hx1L = xL;
		hx1R = xR;
		hy2L = hy1L;
		hy2R = hy1R;
		hy1L = yL;
		hy1R = yR;
		hz2L = hz1L;
		hz2R = hz1R;
		hz1L = zL;
		hz1R = zR;
		data1L[i] = zL;
		data1R[i] = zR;
	}

	lp->x1L = lx1L; lp->x1R = lx1R;
	lp->x2L = lx2L;	lp->x2R = lx2R;
	lp->y1L = ly1L;	lp->y1R = ly1R;
	lp->y2L = ly2L;	lp->y2R = ly2R;
	lp->z1L = lz1L;	lp->z1R = lz1R;
	lp->z2L = lz2L;	lp->z2R = lz2R;

	hp->x1L = hx1L; hp->x1R = hx1R;
	hp->x2L = hx2L;	hp->x2R = hx2R;
	hp->y1L = hy1L;	hp->y1R = hy1R;
	hp->y2L = hy2L;	hp->y2R = hy2R;
	hp->z1L = hz1L;	hp->z1R = hz1R;
	hp->z2L = hz2L;	hp->z2R = hz2R;
}
#endif

/* Split input data using two LR4 filters and sum them back to the original
 * data array.
 *
 * data --+-- lp --+--> data
 *        |        |
 *        \-- hp --/
 */
#if defined(__ARM_NEON__)
#include <arm_neon.h>
static void lr42_merge(struct lr42 *lp, struct lr42 *hp, int count,
		       float *dataL, float *dataR)
{
	float32x4_t x1 = {lp->x1L, hp->x1L, lp->x1R, hp->x1R};
	float32x4_t x2 = {lp->x2L, hp->x2L, lp->x2R, hp->x2R};
	float32x4_t y1 = {lp->y1L, hp->y1L, lp->y1R, hp->y1R};
	float32x4_t y2 = {lp->y2L, hp->y2L, lp->y2R, hp->y2R};
	float32x4_t z1 = {lp->z1L, hp->z1L, lp->z1R, hp->z1R};
	float32x4_t z2 = {lp->z2L, hp->z2L, lp->z2R, hp->z2R};
	float32x4_t b0 = {lp->b0, hp->b0, lp->b0, hp->b0};
	float32x4_t b1 = {lp->b1, hp->b1, lp->b1, hp->b1};
	float32x4_t b2 = {lp->b2, hp->b2, lp->b2, hp->b2};
	float32x4_t a1 = {lp->a1, hp->a1, lp->a1, hp->a1};
	float32x4_t a2 = {lp->a2, hp->a2, lp->a2, hp->a2};

	__asm__ __volatile__(
		/* q0 = x, q1 = y, q2 = z */
		"1:                                     \n"
		"vmul.f32 q1, %q[b1], %q[x1]            \n"
		"vld1.32 d0[], [%[dataL]]               \n"
		"vld1.32 d1[], [%[dataR]]               \n"
		"subs %[count], #1                      \n"
		"vmul.f32 q2, %q[b1], %q[y1]            \n"
		"vmla.f32 q1, %q[b0], q0                \n"
		"vmla.f32 q1, %q[b2], %q[x2]            \n"
		"vmov.f32 %q[x2], %q[x1]                \n"
		"vmov.f32 %q[x1], q0                    \n"
		"vmls.f32 q1, %q[a1], %q[y1]            \n"
		"vmls.f32 q1, %q[a2], %q[y2]            \n"
		"vmla.f32 q2, %q[b0], q1                \n"
		"vmla.f32 q2, %q[b2], %q[y2]            \n"
		"vmov.f32 %q[y2], %q[y1]                \n"
		"vmov.f32 %q[y1], q1                    \n"
		"vmls.f32 q2, %q[a1], %q[z1]            \n"
		"vmls.f32 q2, %q[a2], %q[z2]            \n"
		"vmov.f32 %q[z2], %q[z1]                \n"
		"vmov.f32 %q[z1], q2                    \n"
		"vpadd.f32 d4, d4, d5                   \n"
		"vst1.f32 d4[0], [%[dataL]]!            \n"
		"vst1.f32 d4[1], [%[dataR]]!            \n"
		"bne 1b                                 \n"
		: /* output */
		  "=r"(dataL),
		  "=r"(dataR),
		  "=r"(count),
		  [x1]"+w"(x1),
		  [x2]"+w"(x2),
		  [y1]"+w"(y1),
		  [y2]"+w"(y2),
		  [z1]"+w"(z1),
		  [z2]"+w"(z2)
		: /* input */
		  [dataL]"0"(dataL),
		  [dataR]"1"(dataR),
		  [count]"2"(count),
		  [b0]"w"(b0),
		  [b1]"w"(b1),
		  [b2]"w"(b2),
		  [a1]"w"(a1),
		  [a2]"w"(a2)
		: /* clobber */
		  "q0", "q1", "q2", "memory", "cc"
		);

	lp->x1L = x1[0]; lp->x1R = x1[2];
	lp->x2L = x2[0]; lp->x2R = x2[2];
	lp->y1L = y1[0]; lp->y1R = y1[2];
	lp->y2L = y2[0]; lp->y2R = y2[2];
	lp->z1L = z1[0]; lp->z1R = z1[2];
	lp->z2L = z2[0]; lp->z2R = z2[2];

	hp->x1L = x1[1]; hp->x1R = x1[3];
	hp->x2L = x2[1]; hp->x2R = x2[3];
	hp->y1L = y1[1]; hp->y1R = y1[3];
	hp->y2L = y2[1]; hp->y2R = y2[3];
	hp->z1L = z1[1]; hp->z1R = z1[3];
	hp->z2L = z2[1]; hp->z2R = z2[3];
}
#elif defined(__SSE3__) && defined(__x86_64__)
#include <emmintrin.h>
static void lr42_merge(struct lr42 *lp, struct lr42 *hp, int count,
		       float *dataL, float *dataR)
{
	__m128 x1 = {lp->x1L, hp->x1L, lp->x1R, hp->x1R};
	__m128 x2 = {lp->x2L, hp->x2L, lp->x2R, hp->x2R};
	__m128 y1 = {lp->y1L, hp->y1L, lp->y1R, hp->y1R};
	__m128 y2 = {lp->y2L, hp->y2L, lp->y2R, hp->y2R};
	__m128 z1 = {lp->z1L, hp->z1L, lp->z1R, hp->z1R};
	__m128 z2 = {lp->z2L, hp->z2L, lp->z2R, hp->z2R};
	__m128 b0 = {lp->b0, hp->b0, lp->b0, hp->b0};
	__m128 b1 = {lp->b1, hp->b1, lp->b1, hp->b1};
	__m128 b2 = {lp->b2, hp->b2, lp->b2, hp->b2};
	__m128 a1 = {lp->a1, hp->a1, lp->a1, hp->a1};
	__m128 a2 = {lp->a2, hp->a2, lp->a2, hp->a2};

	__asm__ __volatile__(
		"1:                                     \n"
		"movss (%[dataL]), %%xmm2               \n"
		"movss (%[dataR]), %%xmm1               \n"
		"shufps $0, %%xmm1, %%xmm2              \n"
		"mulps %[b2],%[x2]                      \n"
		"movaps %[b0], %%xmm0                   \n"
		"mulps %[a2],%[z2]                      \n"
		"movaps %[b1], %%xmm1                   \n"
		"mulps %%xmm2,%%xmm0                    \n"
		"mulps %[x1],%%xmm1                     \n"
		"addps %%xmm1,%%xmm0                    \n"
		"movaps %[a1],%%xmm1                    \n"
		"mulps %[y1],%%xmm1                     \n"
		"addps %[x2],%%xmm0                     \n"
		"movaps %[b1],%[x2]                     \n"
		"mulps %[y1],%[x2]                      \n"
		"subps %%xmm1,%%xmm0                    \n"
		"movaps %[a2],%%xmm1                    \n"
		"mulps %[y2],%%xmm1                     \n"
		"mulps %[b2],%[y2]                      \n"
		"subps %%xmm1,%%xmm0                    \n"
		"movaps %[b0],%%xmm1                    \n"
		"mulps %%xmm0,%%xmm1                    \n"
		"addps %[x2],%%xmm1                     \n"
		"movaps %[x1],%[x2]                     \n"
		"movaps %%xmm2,%[x1]                    \n"
		"addps %[y2],%%xmm1                     \n"
		"movaps %[a1],%[y2]                     \n"
		"mulps %[z1],%[y2]                      \n"
		"subps %[y2],%%xmm1                     \n"
		"movaps %[y1],%[y2]                     \n"
		"movaps %%xmm0,%[y1]                    \n"
		"subps %[z2],%%xmm1                     \n"
		"movaps %[z1],%[z2]                     \n"
		"movaps %%xmm1,%[z1]                    \n"
		"haddps %%xmm1, %%xmm1                  \n"
		"movss %%xmm1, (%[dataL])               \n"
		"shufps $0x39, %%xmm1, %%xmm1           \n"
		"movss %%xmm1, (%[dataR])               \n"
		"add $4, %[dataL]                       \n"
		"add $4, %[dataR]                       \n"
		"sub $1, %[count]                       \n"
		"jnz 1b                                 \n"
		: /* output */
		  [dataL]"+r"(dataL),
		  [dataR]"+r"(dataR),
		  [count]"+r"(count),
		  [x1]"+x"(x1),
		  [x2]"+x"(x2),
		  [y1]"+x"(y1),
		  [y2]"+x"(y2),
		  [z1]"+x"(z1),
		  [z2]"+x"(z2)
		: /* input */
		  [b0]"x"(b0),
		  [b1]"x"(b1),
		  [b2]"x"(b2),
		  [a1]"x"(a1),
		  [a2]"x"(a2)
		: /* clobber */
		  "xmm0", "xmm1", "xmm2", "memory", "cc"
		);

	lp->x1L = x1[0]; lp->x1R = x1[2];
	lp->x2L = x2[0]; lp->x2R = x2[2];
	lp->y1L = y1[0]; lp->y1R = y1[2];
	lp->y2L = y2[0]; lp->y2R = y2[2];
	lp->z1L = z1[0]; lp->z1R = z1[2];
	lp->z2L = z2[0]; lp->z2R = z2[2];

	hp->x1L = x1[1]; hp->x1R = x1[3];
	hp->x2L = x2[1]; hp->x2R = x2[3];
	hp->y1L = y1[1]; hp->y1R = y1[3];
	hp->y2L = y2[1]; hp->y2R = y2[3];
	hp->z1L = z1[1]; hp->z1R = z1[3];
	hp->z2L = z2[1]; hp->z2R = z2[3];
}
#else
static void lr42_merge(struct lr42 *lp, struct lr42 *hp, int count,
		       float *dataL, float *dataR)
{
	float lx1L = lp->x1L, lx1R = lp->x1R;
	float lx2L = lp->x2L, lx2R = lp->x2R;
	float ly1L = lp->y1L, ly1R = lp->y1R;
	float ly2L = lp->y2L, ly2R = lp->y2R;
	float lz1L = lp->z1L, lz1R = lp->z1R;
	float lz2L = lp->z2L, lz2R = lp->z2R;
	float lb0 = lp->b0;
	float lb1 = lp->b1;
	float lb2 = lp->b2;
	float la1 = lp->a1;
	float la2 = lp->a2;

	float hx1L = hp->x1L, hx1R = hp->x1R;
	float hx2L = hp->x2L, hx2R = hp->x2R;
	float hy1L = hp->y1L, hy1R = hp->y1R;
	float hy2L = hp->y2L, hy2R = hp->y2R;
	float hz1L = hp->z1L, hz1R = hp->z1R;
	float hz2L = hp->z2L, hz2R = hp->z2R;
	float hb0 = hp->b0;
	float hb1 = hp->b1;
	float hb2 = hp->b2;
	float ha1 = hp->a1;
	float ha2 = hp->a2;

	int i;
	for (i = 0; i < count; i++) {
		float xL, yL, zL, xR, yR, zR;
		xL = dataL[i];
		xR = dataR[i];
		yL = lb0*xL + lb1*lx1L + lb2*lx2L - la1*ly1L - la2*ly2L;
		yR = lb0*xR + lb1*lx1R + lb2*lx2R - la1*ly1R - la2*ly2R;
		zL = lb0*yL + lb1*ly1L + lb2*ly2L - la1*lz1L - la2*lz2L;
		zR = lb0*yR + lb1*ly1R + lb2*ly2R - la1*lz1R - la2*lz2R;
		lx2L = lx1L;
		lx2R = lx1R;
		lx1L = xL;
		lx1R = xR;
		ly2L = ly1L;
		ly2R = ly1R;
		ly1L = yL;
		ly1R = yR;
		lz2L = lz1L;
		lz2R = lz1R;
		lz1L = zL;
		lz1R = zR;

		yL = hb0*xL + hb1*hx1L + hb2*hx2L - ha1*hy1L - ha2*hy2L;
		yR = hb0*xR + hb1*hx1R + hb2*hx2R - ha1*hy1R - ha2*hy2R;
		zL = hb0*yL + hb1*hy1L + hb2*hy2L - ha1*hz1L - ha2*hz2L;
		zR = hb0*yR + hb1*hy1R + hb2*hy2R - ha1*hz1R - ha2*hz2R;
		hx2L = hx1L;
		hx2R = hx1R;
		hx1L = xL;
		hx1R = xR;
		hy2L = hy1L;
		hy2R = hy1R;
		hy1L = yL;
		hy1R = yR;
		hz2L = hz1L;
		hz2R = hz1R;
		hz1L = zL;
		hz1R = zR;
		dataL[i] = zL + lz1L;
		dataR[i] = zR + lz1R;
	}

	lp->x1L = lx1L; lp->x1R = lx1R;
	lp->x2L = lx2L;	lp->x2R = lx2R;
	lp->y1L = ly1L;	lp->y1R = ly1R;
	lp->y2L = ly2L;	lp->y2R = ly2R;
	lp->z1L = lz1L;	lp->z1R = lz1R;
	lp->z2L = lz2L;	lp->z2R = lz2R;

	hp->x1L = hx1L; hp->x1R = hx1R;
	hp->x2L = hx2L;	hp->x2R = hx2R;
	hp->y1L = hy1L;	hp->y1R = hy1R;
	hp->y2L = hy2L;	hp->y2R = hy2R;
	hp->z1L = hz1L;	hp->z1R = hz1R;
	hp->z2L = hz2L;	hp->z2R = hz2R;
}
#endif

void crossover2_init(struct crossover2 *xo2, float freq1, float freq2)
{
	int i;
	for (i = 0; i < 3; i++) {
		float f = (i == 0) ? freq1 : freq2;
		lr42_set(&xo2->lp[i], BQ_LOWPASS, f);
		lr42_set(&xo2->hp[i], BQ_HIGHPASS, f);
	}
}

void crossover2_process(struct crossover2 *xo2, int count,
			float *data0L, float *data0R,
			float *data1L, float *data1R,
			float *data2L, float *data2R)
{
	if (!count)
		return;

	lr42_split(&xo2->lp[0], &xo2->hp[0], count, data0L, data0R,
		   data1L, data1R);
	lr42_merge(&xo2->lp[1], &xo2->hp[1], count, data0L, data0R);
	lr42_split(&xo2->lp[2], &xo2->hp[2], count, data1L, data1R,
		   data2L, data2R);
}
