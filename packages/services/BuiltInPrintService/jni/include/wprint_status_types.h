/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
 * Copyright (C) 2013 Hewlett-Packard Development Company, L.P.
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
#ifndef __WPRINT_STATUS_TYPES_H__
#define __WPRINT_STATUS_TYPES_H__

#define PRINTER_IDLE_BIT       (1 << PRINT_STATUS_MAX_STATE)
#define PRINTER_IS_IDLE(X)     ((X) << PRINT_STATUS_MAX_STATE)
#define PRINTER_STATUS_MASK(X) ((X) & (PRINTER_IDLE_BIT - 1))

#define BLOCKED_REASON_UNABLE_TO_CONNECT (1 << PRINT_STATUS_UNABLE_TO_CONNECT)
#define BLOCKED_REASONS_PRINTER_BUSY     (1 << PRINT_STATUS_BUSY)
#define BLOCKED_REASONS_CANCELLED        (1 << PRINT_STATUS_CANCELLED)
#define BLOCKED_REASON_OUT_OF_PAPER      (1 << PRINT_STATUS_OUT_OF_PAPER)
#define BLOCKED_REASON_OUT_OF_INK        (1 << PRINT_STATUS_OUT_OF_INK)
#define BLOCKED_REASON_OUT_OF_TONER      (1 << PRINT_STATUS_OUT_OF_TONER)
#define BLOCKED_REASON_JAMMED            (1 << PRINT_STATUS_JAMMED)
#define BLOCKED_REASON_DOOR_OPEN         (1 << PRINT_STATUS_DOOR_OPEN)
#define BLOCKED_REASON_SVC_REQUEST       (1 << PRINT_STATUS_SVC_REQUEST)
#define BLOCKED_REASON_LOW_ON_INK        (1 << PRINT_STATUS_LOW_ON_INK)
#define BLOCKED_REASON_LOW_ON_TONER      (1 << PRINT_STATUS_LOW_ON_TONER)
#define BLOCKED_REASON_UNKNOWN           (1 << PRINT_STATUS_UNKNOWN)
#define BLOCKED_REASON_BUSY              (1 << PRINT_STATUS_PRINTING)
#define BLOCKED_REASON_IDLE              (1 << PRINT_STATUS_IDLE)
#define BLOCKED_REASON_CANCELLED         (1 << PRINT_STATUS_CANCELLED)
#define BLOCKED_REASON_PRINT_STATUS_VERY_LOW_ON_INK (1 << PRINT_STATUS_VERY_LOW_ON_INK)
#define BLOCKED_REASON_PARTIAL_CANCEL   (1 << PRINT_STATUS_PARTIAL_CANCEL)

/*
 * Enumeration for printer statuses
 */
typedef enum {
    PRINT_STATUS_INITIALIZING,
    PRINT_STATUS_SHUTTING_DOWN,
    PRINT_STATUS_UNABLE_TO_CONNECT,

    PRINT_STATUS_UNKNOWN,
    PRINT_STATUS_OFFLINE,

    PRINT_STATUS_BUSY,
    PRINT_STATUS_CANCELLED,

    PRINT_STATUS_IDLE,
    PRINT_STATUS_PRINTING,
    PRINT_STATUS_JAMMED,
    PRINT_STATUS_OUT_OF_PAPER,
    PRINT_STATUS_OUT_OF_INK,
    PRINT_STATUS_OUT_OF_TONER,
    PRINT_STATUS_DOOR_OPEN,
    PRINT_STATUS_SVC_REQUEST,

    PRINT_STATUS_LOW_ON_INK,
    PRINT_STATUS_LOW_ON_TONER,

    PRINT_STATUS_VERY_LOW_ON_INK,
    PRINT_STATUS_PARTIAL_CANCEL,

    PRINT_STATUS_MAX_STATE // Add new entries above this line.
} print_status_t;

/*
 * Structure for handling printer status
 */
typedef struct printer_state_dyn_s {
    // Printer state (idle, printing, service request, unknown)
    print_status_t printer_status;

    // all current print status events
    print_status_t printer_reasons[PRINT_STATUS_MAX_STATE + 1];
} printer_state_dyn_t;

#endif // __WPRINT_STATUS_TYPES_H__