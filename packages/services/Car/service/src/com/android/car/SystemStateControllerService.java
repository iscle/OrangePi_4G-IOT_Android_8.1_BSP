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
package com.android.car;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import com.android.car.CarPowerManagementService.PowerEventProcessingHandler;
import com.android.car.CarPowerManagementService.PowerServiceEventListener;

import java.io.PrintWriter;

public class SystemStateControllerService implements CarServiceBase,
    PowerServiceEventListener, PowerEventProcessingHandler {

    private final CarPowerManagementService mCarPowerManagementService;
    private final CarAudioService mCarAudioService;
    private final ICarImpl mICarImpl;
    private final boolean mLockWhenMuting;

    public SystemStateControllerService(Context context,
            CarPowerManagementService carPowerManagementService,
            CarAudioService carAudioService, ICarImpl carImpl) {
        mCarPowerManagementService = carPowerManagementService;
        mCarAudioService = carAudioService;
        mICarImpl = carImpl;
        Resources res = context.getResources();
        mLockWhenMuting = res.getBoolean(R.bool.displayOffMuteLockAllAudio);
    }

    @Override
    public long onPrepareShutdown(boolean shuttingDown) {
        //TODO add state saving here for things to restore on power on. bug: 32096079
        return 0;
    }

    @Override
    public void onPowerOn(boolean displayOn) {
        if (displayOn) {
            Log.i(CarLog.TAG_SYS, "Media unmute");
            mCarAudioService.unMuteMedia();
        } else {
            Log.i(CarLog.TAG_SYS, "Media mute");
            mCarAudioService.muteMediaWithLock(mLockWhenMuting);
            //TODO store last context and resume or silence radio on display on. bug: 32096079
        }
    }

    @Override
    public int getWakeupTime() {
        return 0;
    }

    @Override
    public void onShutdown() {
        // TODO bug: 32096079
    }

    @Override
    public void onSleepEntry() {
        // TODO bug: 32096079
    }

    @Override
    public void onSleepExit() {
        // TODO bug: 32096079
    }

    @Override
    public void init() {
        mCarPowerManagementService.registerPowerEventListener(this);
        mCarPowerManagementService.registerPowerEventProcessingHandler(this);
    }

    @Override
    public void release() {
    }

    @Override
    public void dump(PrintWriter writer) {
    }
}
