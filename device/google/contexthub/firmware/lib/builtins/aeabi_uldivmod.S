//===-- aeabi_uldivmod.S - EABI uldivmod implementation -------------------===//
//
//	             The LLVM Compiler Infrastructure
//
// This file is dual licensed under the MIT and the University of Illinois Open
// Source Licenses. See LICENSE.TXT for details.
//
//===----------------------------------------------------------------------===//

// struct { uint64_t quot, uint64_t rem}
//	__aeabi_uldivmod(uint64_t numerator, uint64_t denominator) {
//   uint64_t rem, quot;
//   quot = __udivmoddi4(numerator, denominator, &rem);
//   return {quot, rem};
// }

	.syntax unified
	.text
	.align	2
	.global	__aeabi_uldivmod
	.thumb
	.thumb_func
	.type	__aeabi_uldivmod, %function
__aeabi_uldivmod:
	push	{lr}
	sub	sp, sp, #16
	add	r12, sp, #8
	str	r12, [sp]
	bl	__udivmoddi4
	ldr	r2, [sp, #8]
	ldr	r3, [sp, #12]
	add	sp, sp, #16
	pop	{pc}
	.size __aeabi_uldivmod, .-__aeabi_uldivmod
