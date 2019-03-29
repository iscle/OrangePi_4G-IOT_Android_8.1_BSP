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

#ifndef SYSTEMPARAMS_H_
#define SYSTEMPARAMS_H_

namespace ndkaudio {

class SystemParams {
 public:
    static void init(int sysSampleRate, int sysBuffFrames);

    static int getSampleRate() {
        return sysSampleRate_;
    }
    static int getNumBufferFrames() {
        return sysBuffFrames_;
    }

 private:
    static int sysSampleRate_;
    static int sysBuffFrames_;
};

} // namespace ndkaudio

#endif /* SYSTEMPARAMS_H_ */
