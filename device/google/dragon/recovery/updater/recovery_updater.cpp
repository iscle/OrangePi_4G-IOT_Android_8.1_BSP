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
/* Add firmware update command to recovery script */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <memory>
#include <string>
#include <vector>

#include "edify/expr.h"
#include "update_fw.h"

Value* firmware_update(const char *name, State * state,
                       const std::vector<std::unique_ptr<Expr>>& argv) {
	printf("%s: running %s.\n", __func__, name);
	if (argv.size() != 2) {
		ErrorAbort(state, kArgsParsingFailure, "syntax: %s bios.bin ec.bin", name);
		return nullptr;
	}
	std::vector<std::unique_ptr<Value>> args;
	if (!ReadValueArgs(state, argv, &args)) {
		ErrorAbort(state, kArgsParsingFailure, "%s: invalid arguments", name);
		return nullptr;
	}
	const Value *firmware = args[0].get();
	const Value *ec = args[1].get();

	Value *retval = nullptr;
	int res = update_fw(firmware, ec, 0);
	if (res < 0) {
		ErrorAbort(state, kVendorFailure, "%s: firmware update error", name);
	} else {
		retval = StringValue(res ? "UPDATED" : "");
	}

	printf("%s: [%s] done.\n", __func__,
		retval ? retval->data.c_str() : state->errmsg.c_str());
	return retval;
}

void Register_librecovery_updater_dragon() {
	RegisterFunction("dragon.firmware_update", firmware_update);
}
