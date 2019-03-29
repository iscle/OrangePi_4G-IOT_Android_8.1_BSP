#!/bin/bash -eu
#
# Copyright 2017 Google Inc. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# This file is used in our continous build infrastructure to run a variety of
# tests related to the build system.
#
# Currently, it's used to build and run multiproduct_kati, so it'll attempt
# to build ninja files for every product in the tree. I expect this to
# evolve as we find interesting things to test or track performance for.
#

# To track how long we took to startup. %N isn't supported on Darwin, but
# that's detected in the Go code, which skips calculating the startup time.
export TRACE_BEGIN_SOONG=$(date +%s%N)

export TOP=$(cd $(dirname ${BASH_SOURCE[0]})/../..; PWD= /bin/pwd)
source "${TOP}/build/soong/cmd/microfactory/microfactory.bash"

case $(uname) in
  Linux)
    export LD_PRELOAD=/lib/x86_64-linux-gnu/libSegFault.so
    ;;
esac

build_go multiproduct_kati android/soong/cmd/multiproduct_kati
exec "$(getoutdir)/multiproduct_kati" "$@"
