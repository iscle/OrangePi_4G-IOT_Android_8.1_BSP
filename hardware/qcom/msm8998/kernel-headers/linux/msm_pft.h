/****************************************************************************
 ****************************************************************************
 ***
 ***   This header was automatically generated from a Linux kernel header
 ***   of the same name, to make information necessary for userspace to
 ***   call into the kernel available to libc.  It contains only constants,
 ***   structures, and macros generated from the original header, and thus,
 ***   contains no copyrightable information.
 ***
 ***   To edit the content of this header, modify the corresponding
 ***   source file (e.g. under external/kernel-headers/original/) then
 ***   run bionic/libc/kernel/tools/update_all.py
 ***
 ***   Any manual change here will be lost the next time this script will
 ***   be run. You've been warned!
 ***
 ****************************************************************************
 ****************************************************************************/
#ifndef MSM_PFT_H_
#define MSM_PFT_H_
#include <linux/types.h>
enum pft_command_opcode {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  PFT_CMD_OPCODE_SET_STATE,
  PFT_CMD_OPCODE_UPDATE_REG_APP_UID,
  PFT_CMD_OPCODE_PERFORM_IN_PLACE_FILE_ENC,
  PFT_CMD_OPCODE_MAX_COMMAND_INDEX
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
enum pft_state {
  PFT_STATE_DEACTIVATED,
  PFT_STATE_DEACTIVATING,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  PFT_STATE_KEY_REMOVED,
  PFT_STATE_REMOVING_KEY,
  PFT_STATE_KEY_LOADED,
  PFT_STATE_MAX_INDEX
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
enum pft_command_response_code {
  PFT_CMD_RESP_SUCCESS,
  PFT_CMD_RESP_GENERAL_ERROR,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  PFT_CMD_RESP_INVALID_COMMAND,
  PFT_CMD_RESP_INVALID_CMD_PARAMS,
  PFT_CMD_RESP_INVALID_STATE,
  PFT_CMD_RESP_ALREADY_IN_STATE,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
  PFT_CMD_RESP_INPLACE_FILE_IS_OPEN,
  PFT_CMD_RESP_ENT_FILES_CLOSING_FAILURE,
  PFT_CMD_RESP_MAX_INDEX
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct pft_command_response {
  __u32 command_id;
  __u32 error_code;
};
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
struct pft_command {
  __u32 opcode;
  union {
    struct {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
      __u32 state;
    } set_state;
    struct {
      __u32 items_count;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
      uid_t table[0];
    } update_app_list;
    struct {
      __u32 file_descriptor;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
    } preform_in_place_file_enc;
  };
};
#endif
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */

