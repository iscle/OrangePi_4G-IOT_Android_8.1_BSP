/*
Copyright (c) 2007-2011, Troy D. Hanson   http://uthash.sourceforge.net
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

#ifndef UTLIST_H
#define UTLIST_H

#define UTLIST_VERSION 1.9.4

#include <assert.h>

/*
 * This file contains macros to manipulate singly and doubly-linked lists.
 *
 * 1. LL_ macros:  singly-linked lists.
 * 2. DL_ macros:  doubly-linked lists.
 * 3. CDL_ macros: circular doubly-linked lists.
 *
 * To use singly-linked lists, your structure must have a "next" pointer.
 * To use doubly-linked lists, your structure must "prev" and "next" pointers.
 * Either way, the pointer to the head of the list must be initialized to NULL.
 *
 * ----------------.EXAMPLE -------------------------
 * struct item {
 *      int id;
 *      struct item *prev, *next;
 * }
 *
 * struct item *list = NULL:
 *
 * int main() {
 *      struct item *item;
 *      ... allocate and populate item ...
 *      DL_APPEND(list, item);
 * }
 * --------------------------------------------------
 *
 * For doubly-linked lists, the append and delete macros are O(1)
 * For singly-linked lists, append and delete are O(n) but prepend is O(1)
 */

/******************************************************************************
 * Singly linked list macros (non-circular).
 *****************************************************************************/
#define LL_PREPEND(head, add)                                                  \
do {                                                                           \
	(add)->next = head;                                                    \
	head = add;                                                            \
} while (0)

#define LL_CONCAT(head1, head2)                                                \
do {                                                                           \
	__typeof(head1) _tmp;                                                  \
	if (head1) {                                                           \
		_tmp = head1;                                                  \
		while (_tmp->next)                                             \
			_tmp = _tmp->next;                                     \
		_tmp->next = (head2);                                          \
	} else                                                                 \
		(head1) = (head2);                                             \
} while (0)

#define LL_APPEND(head, add)                                                   \
do {                                                                           \
	__typeof(head) _tmp;                                                   \
	(add)->next = NULL;                                                    \
	if (head) {                                                            \
		_tmp = head;                                                   \
		while (_tmp->next)                                             \
			_tmp = _tmp->next;                                     \
		_tmp->next = (add);                                            \
	} else {                                                               \
		(head) = (add);                                                \
	}                                                                      \
} while (0)

#define LL_DELETE(head, del)                                                   \
	do {                                                                   \
		__typeof(head) _tmp;                                           \
		if ((head) == (del))                                           \
			(head) = (head)->next;                                 \
		else {                                                         \
			_tmp = head;                                           \
			while (_tmp->next && (_tmp->next != (del)))            \
				_tmp = _tmp->next;                             \
			if (_tmp->next)                                        \
				_tmp->next = ((del)->next);                    \
		}                                                              \
	} while (0)

#define LL_FOREACH(head, el)                                                   \
	for (el = head; el; el = el->next)

#define LL_FOREACH_SAFE(head, el, tmp)                                         \
	for ((el) = (head); (el) && (tmp = (el)->next, 1); (el) = tmp)

#define LL_SEARCH_SCALAR(head, out, field, val)                                \
	do {                                                                   \
		LL_FOREACH(head, out)                                          \
			if ((out)->field == (val))                             \
				break;                                         \
	} while (0)

#define LL_SEARCH_SCALAR_WITH_CAST(head, out, nout, field, val)	               \
	do {                                                                   \
		LL_FOREACH(head, out) {                                        \
			(nout) = (__typeof(nout))out;                          \
			if ((nout)->field == (val))                            \
				break;                                         \
			(nout) = 0;					       \
		}                                                              \
	} while (0)

#define LL_SEARCH(head, out, elt, cmp)                                         \
	do {                                                                   \
		LL_FOREACH(head, out)                                          \
			if ((cmp(out, elt)) == 0)                              \
				break;                                         \
	} while (0)

/******************************************************************************
 * Doubly linked list macros (non-circular).
 *****************************************************************************/
#define DL_PREPEND(head, add)                                                  \
	do {                                                                   \
		(add)->next = head;                                            \
		if (head) {                                                    \
			(add)->prev = (head)->prev;                            \
			(head)->prev = (add);                                  \
		} else                                                         \
			(add)->prev = (add);                                   \
		(head) = (add);                                                \
	} while (0)

#define DL_APPEND(head, add)                                                   \
	do {                                                                   \
		if (head) {                                                    \
			(add)->prev = (head)->prev;                            \
			(head)->prev->next = (add);                            \
			(head)->prev = (add);                                  \
			(add)->next = NULL;                                    \
		} else {                                                       \
			(head) = (add);                                        \
			(head)->prev = (head);                                 \
			(head)->next = NULL;                                   \
		}                                                              \
	} while (0)

#define DL_CONCAT(head1, head2)                                                \
	do {                                                                   \
		__typeof(head1) _tmp;                                          \
		if (head2) {                                                   \
			if (head1) {                                           \
				_tmp = (head2)->prev;                          \
				(head2)->prev = (head1)->prev;                 \
				(head1)->prev->next = (head2);                 \
				(head1)->prev = _tmp;                          \
			} else                                                 \
				(head1) = (head2);                             \
		}                                                              \
	} while (0)

#define DL_DELETE(head, del)                                                   \
	do {                                                                   \
		assert((del)->prev != NULL);                                   \
		if ((del)->prev == (del)) {                                    \
			(head) = NULL;                                         \
		} else if ((del) == (head)) {                                  \
			(del)->next->prev = (del)->prev;                       \
			(head) = (del)->next;                                  \
		} else {                                                       \
			(del)->prev->next = (del)->next;                       \
			if ((del)->next)                                       \
				(del)->next->prev = (del)->prev;               \
			else                                                   \
				(head)->prev = (del)->prev;                    \
		}                                                              \
	} while (0)


/* Create a variable name using given prefix and current line number. */
#define MAKE_NAME(prefix) TOKEN_PASTE2(prefix, __LINE__)
#define TOKEN_PASTE2(x, y) TOKEN_PASTE(x, y)
#define TOKEN_PASTE(x, y) x ## y

/* This version creates a temporary variable to to make it safe for deleting the
 * elements during iteration. */
#define DL_FOREACH(head, el)                                            \
        DL_FOREACH_INTERNAL(head, el, MAKE_NAME(_dl_foreach_))
#define DL_FOREACH_INTERNAL(head, el, tmp)                              \
        __typeof__(el) tmp;                                             \
        for ((el) = (head); (el) && (tmp = (el)->next, 1); (el) = tmp)

/* These are identical to their singly-linked list counterparts. */
#define DL_SEARCH_SCALAR LL_SEARCH_SCALAR
#define DL_SEARCH_SCALAR_WITH_CAST LL_SEARCH_SCALAR_WITH_CAST
#define DL_SEARCH LL_SEARCH

#endif /* UTLIST_H */

