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

package com.android.bluetooth.avrcp;

/*************************************************************************************************
 * Grouped all HAL constants into a file to be consistent with the stack.
 * Moved the constants used in Avrcp to this new file to be used across multiple files.
 * Helps in easier modifications and future enhancements in the constants.
 ************************************************************************************************/

/*
 * @hide
 */
final class AvrcpConstants {

    /* Do not modify without upating the HAL bt_rc.h file */
    /** Response Error codes **/
    static final byte RSP_BAD_CMD        = 0x00; /* Invalid command */
    static final byte RSP_BAD_PARAM      = 0x01; /* Invalid parameter */
    static final byte RSP_NOT_FOUND      = 0x02; /* Specified parameter is
                                                              * wrong or not found */
    static final byte RSP_INTERNAL_ERR   = 0x03; /* Internal Error */
    static final byte RSP_NO_ERROR       = 0x04; /* Operation Success */
    static final byte RSP_UID_CHANGED    = 0x05; /* UIDs changed */
    static final byte RSP_RESERVED       = 0x06; /* Reserved */
    static final byte RSP_INV_DIRN       = 0x07; /* Invalid direction */
    static final byte RSP_INV_DIRECTORY  = 0x08; /* Invalid directory */
    static final byte RSP_INV_ITEM       = 0x09; /* Invalid Item */
    static final byte RSP_INV_SCOPE      = 0x0a; /* Invalid scope */
    static final byte RSP_INV_RANGE      = 0x0b; /* Invalid range */
    static final byte RSP_DIRECTORY      = 0x0c; /* UID is a directory */
    static final byte RSP_MEDIA_IN_USE   = 0x0d; /* Media in use */
    static final byte RSP_PLAY_LIST_FULL = 0x0e; /* Playing list full */
    static final byte RSP_SRCH_NOT_SPRTD = 0x0f; /* Search not supported */
    static final byte RSP_SRCH_IN_PROG   = 0x10; /* Search in progress */
    static final byte RSP_INV_PLAYER     = 0x11; /* Invalid player */
    static final byte RSP_PLAY_NOT_BROW  = 0x12; /* Player not browsable */
    static final byte RSP_PLAY_NOT_ADDR  = 0x13; /* Player not addressed */
    static final byte RSP_INV_RESULTS    = 0x14; /* Invalid results */
    static final byte RSP_NO_AVBL_PLAY   = 0x15; /* No available players */
    static final byte RSP_ADDR_PLAY_CHGD = 0x16; /* Addressed player changed */

    /* valid scopes for get_folder_items */
    static final byte BTRC_SCOPE_PLAYER_LIST  = 0x00; /* Media Player List */
    static final byte BTRC_SCOPE_FILE_SYSTEM  = 0x01; /* Virtual File System */
    static final byte BTRC_SCOPE_SEARCH       = 0x02; /* Search */
    static final byte BTRC_SCOPE_NOW_PLAYING  = 0x03; /* Now Playing */

    /* valid directions for change path */
    static final byte DIR_UP   = 0x00;
    static final byte DIR_DOWN = 0x01;

    /* item type to browse */
    static final byte BTRC_ITEM_PLAYER  = 0x01;
    static final byte BTRC_ITEM_FOLDER  = 0x02;
    static final byte BTRC_ITEM_MEDIA   = 0x03;

    /* valid folder types */
    static final byte FOLDER_TYPE_MIXED      = 0x00;
    static final byte FOLDER_TYPE_TITLES     = 0x01;
    static final byte FOLDER_TYPE_ALBUMS     = 0x02;
    static final byte FOLDER_TYPE_ARTISTS    = 0x03;
    static final byte FOLDER_TYPE_GENRES     = 0x04;
    static final byte FOLDER_TYPE_PLAYLISTS  = 0x05;
    static final byte FOLDER_TYPE_YEARS      = 0x06;

    /* valid playable flags */
    static final byte ITEM_NOT_PLAYABLE  = 0x00;
    static final byte ITEM_PLAYABLE      = 0x01;

    /* valid Attribute ids for media elements */
    static final int ATTRID_TITLE      = 0x01;
    static final int ATTRID_ARTIST     = 0x02;
    static final int ATTRID_ALBUM      = 0x03;
    static final int ATTRID_TRACK_NUM  = 0x04;
    static final int ATTRID_NUM_TRACKS = 0x05;
    static final int ATTRID_GENRE      = 0x06;
    static final int ATTRID_PLAY_TIME  = 0x07;
    static final int ATTRID_COVER_ART  = 0x08;

    /* constants to send in Track change response */
    static final byte[] NO_TRACK_SELECTED = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    static final byte[] TRACK_IS_SELECTED = {(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};

    /* UID size */
    static final int UID_SIZE = 8;

    static final short DEFAULT_UID_COUNTER = 0x0000;

    /* Bitmask size for Media Players */
    static final int AVRC_FEATURE_MASK_SIZE = 16;

    /* Maximum attributes for media item */
    static final int MAX_NUM_ATTR = 8;

    /* notification types for remote device */
    static final int NOTIFICATION_TYPE_INTERIM = 0;
    static final int NOTIFICATION_TYPE_CHANGED = 1;

    static final int TRACK_ID_SIZE = 8;

    /* player feature bit mask constants */
    static final short AVRC_PF_PLAY_BIT_NO = 40;
    static final short AVRC_PF_STOP_BIT_NO = 41;
    static final short AVRC_PF_PAUSE_BIT_NO = 42;
    static final short AVRC_PF_REWIND_BIT_NO = 44;
    static final short AVRC_PF_FAST_FWD_BIT_NO = 45;
    static final short AVRC_PF_FORWARD_BIT_NO = 47;
    static final short AVRC_PF_BACKWARD_BIT_NO = 48;
    static final short AVRC_PF_ADV_CTRL_BIT_NO = 58;
    static final short AVRC_PF_BROWSE_BIT_NO = 59;
    static final short AVRC_PF_ADD2NOWPLAY_BIT_NO = 61;
    static final short AVRC_PF_UID_UNIQUE_BIT_NO = 62;
    static final short AVRC_PF_NOW_PLAY_BIT_NO = 65;
    static final short AVRC_PF_GET_NUM_OF_ITEMS_BIT_NO = 67;

    static final byte PLAYER_TYPE_AUDIO = 1;
    static final int PLAYER_SUBTYPE_NONE = 0;

    // match up with btrc_play_status_t enum of bt_rc.h
    static final int PLAYSTATUS_STOPPED = 0;
    static final int PLAYSTATUS_PLAYING = 1;
    static final int PLAYSTATUS_PAUSED = 2;
    static final int PLAYSTATUS_FWD_SEEK = 3;
    static final int PLAYSTATUS_REV_SEEK = 4;
    static final int PLAYSTATUS_ERROR = 255;

    static final byte NUM_ATTR_ALL = (byte)0x00;
    static final byte NUM_ATTR_NONE = (byte)0xFF;

    static final int KEY_STATE_PRESS = 1;
    static final int KEY_STATE_RELEASE = 0;
}
