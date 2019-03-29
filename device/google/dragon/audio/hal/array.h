/* Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef CRAS_ARRAY_H_
#define CRAS_ARRAY_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <string.h>

/*

Sample usage:

DECLARE_ARRAY_TYPE(double, double_array);

void f()
{
	int i;
	double *p;
	double_array a = ARRAY_INIT;

	ARRAY_APPEND(&a, 1.0);
	*ARRAY_APPEND_ZERO(&a) = 2.0;

	FOR_ARRAY_ELEMENT(&a, i, p) {
		printf("%f\n", *p);  // prints 1.0 2.0
	}

	ARRAY_FREE(&a);
}

*/

/* Define a type for the array given the element type */
#define DECLARE_ARRAY_TYPE(element_type, array_type) \
	typedef struct { \
		int count; \
		int size; \
		element_type *element; \
	} array_type;

/* The initializer for an empty array is the zero value. */
#define ARRAY_INIT {}

#define _ARRAY_EXTEND(a)						\
	({								\
		if ((a)->count >= (a)->size) {				\
			if ((a)->size == 0)				\
				(a)->size = 4;				\
			else						\
				(a)->size *= 2;				\
			(a)->element = (__typeof((a)->element))		\
				realloc((a)->element,			\
					(a)->size *			\
					sizeof((a)->element[0]));	\
		}							\
		&(a)->element[((a)->count)++];				\
	})

/* Append an element with the given value to the array a */
#define ARRAY_APPEND(a, value)						\
	do {								\
		*_ARRAY_EXTEND(a) = (value);				\
	} while (0)

/* Append a zero element to the array a and return the pointer to the element */
#define ARRAY_APPEND_ZERO(a)					    \
	({							    \
		typeof((a)->element) _tmp_ptr = _ARRAY_EXTEND(a);   \
		memset(_tmp_ptr, 0, sizeof(*_tmp_ptr));		    \
		_tmp_ptr;					    \
	})

/* Return the number of elements in the array a */
#define ARRAY_COUNT(a) ((a)->count)

/* Return a pointer to the i-th element in the array a */
#define ARRAY_ELEMENT(a, i) ((a)->element + (i))

/* Return the index of the element pointed by p in the array a */
#define ARRAY_INDEX(a, p) ((p) - (a)->element)

/* Go through each element in the array a and assign index and pointer
   to the element to the variable i and ptr */
#define FOR_ARRAY_ELEMENT(a, i, ptr)				    \
	for ((i) = 0, (ptr) = (a)->element; (i) < (a)->count;	    \
	     (i)++, (ptr)++)

/* Free the memory used by the array a. The array becomes an empty array. */
#define ARRAY_FREE(a)				\
	do {					\
		free((a)->element);		\
		(a)->element = NULL;		\
		(a)->size = 0;			\
		(a)->count = 0;			\
	} while (0)


/* Return the index of the element with the value x. -1 if not found */
#define ARRAY_FIND(a, x)						\
	({								\
		typeof((a)->element) _bptr = (a)->element;		\
		typeof((a)->element) _eptr = (a)->element + (a)->count; \
		for (; _bptr != _eptr && *_bptr != x; _bptr++)		\
			;						\
		(_bptr == _eptr) ? -1 : (_bptr - (a)->element);		\
	})

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* CRAS_ARRAY_H_ */
