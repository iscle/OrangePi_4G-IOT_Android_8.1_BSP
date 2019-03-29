/*
 * Copyright (C) 2015 The Android Open Source Project
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
#ifndef _RECOVERY_DEBUG_CMD_H_
#define _RECOVERY_DEBUG_CMD_H_

struct command {
	int (*handler)(int argc, const char *argv[]);
	struct command *subcmd;
	const char *name;
	const char *help;
};

#define CMD(n, helpstr) \
	{.handler = cmd_##n, .subcmd = NULL, .name = #n, .help = helpstr}

#define CMDS(n, helpstr) \
	{.handler = cmd_##n, .subcmd = subcmds_##n, .name = #n, .help = helpstr}

#define SUBCMDS(n, helpstr) \
	{.handler = NULL, .subcmd = subcmds_##n, \
	 .name = #n, .help = helpstr}

#define CMD_GUARD_LAST { .name = NULL }

extern struct command subcmds_ec[];

#endif /* _RECOVERY_DEBUG_CMD_H_ */
