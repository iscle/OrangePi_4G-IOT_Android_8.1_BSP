/*
// Copyright (c) 2014 Intel Corporation 
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/

#ifndef MOOFPLATFORMFACTORY_H_
#define MOOFPLATFORMFACTORY_H_

#include <IPlatFactory.h>


namespace android {
namespace intel {

class PlatFactory : public  IPlatFactory {
public:
    PlatFactory();
    virtual ~PlatFactory();

    virtual DisplayPlaneManager* createDisplayPlaneManager();
    virtual BufferManager* createBufferManager();
    virtual IDisplayDevice* createDisplayDevice(int disp);
    virtual IDisplayContext* createDisplayContext();
    virtual IVideoPayloadManager *createVideoPayloadManager();

};

} //namespace intel
} //namespace android


#endif /* MOOFPLATFORMFACTORY_H_ */
