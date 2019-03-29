# Copyright (C) 2014 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

its-dir-name := CameraITS
its-dir := $(HOST_OUT)/$(its-dir-name)
its-build-stamp := $(its-dir)/build_stamp

camera-its: $(its-build-stamp)

.PHONY: camera-its

$(its-dir): $(its-build-stamp)

$(its-build-stamp): $(ACP)
	echo $(its_dir)
	mkdir -p $(its-dir)
	$(ACP) -rfp cts/apps/$(its-dir-name)/* $(its-dir)
	rm $(its-dir)/Android.mk
	touch $@
