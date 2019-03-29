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

#ifndef _LIST_H_
#define _LIST_H_

#include <stdbool.h>

struct link_t
{
    struct link_t *prev, *next;
} typedef link_t;

#define list_iterate(list, cur_link, tmp_link)                  \
    for ((cur_link) = (list)->next,                             \
            (tmp_link) = (cur_link) ? (cur_link)->next : NULL;  \
         (cur_link) != NULL && (cur_link) != (list);            \
         (cur_link) = (tmp_link), (tmp_link) = (cur_link)->next)

#define DECLARE_LIST(list) \
    link_t list = { .prev = &list, .next = &list }

static inline void list_init(struct link_t *list)
{
    list->prev = list->next = list;
}

static inline void list_add_tail(struct link_t *list, struct link_t *item)
{
    if (!list->next)
        list_init(list);

    item->prev = list->prev;
    item->next = list;
    list->prev->next = item;
    list->prev = item;
}

static inline void list_delete(struct link_t *item)
{
    item->prev->next = item->next;
    item->next->prev = item->prev;
    item->next = item->prev = item;
}

static inline bool list_is_empty(struct link_t *list)
{
    return !list->next || list->next == list;
}

#endif

