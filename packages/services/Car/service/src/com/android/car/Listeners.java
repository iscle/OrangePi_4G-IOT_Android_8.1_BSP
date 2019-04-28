/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.car.hardware.CarSensorManager;
import android.os.IBinder;
import java.util.LinkedList;

/**
 * This class wraps a set of listeners of a given type of event, grouped by event-transmission rate.
 * This is logic that is common to different services that need to receive and rebroadcast events.
 * @param <ClientType> The type of event listener.
 */
public class Listeners<ClientType extends com.android.car.Listeners.IListener> {
    public interface IListener extends IBinder.DeathRecipient {
        void release();
    }

    public static class ClientWithRate<ClientType extends IListener> {
        private final ClientType mClient;
        /** rate requested from client */
        private int mRate;

        ClientWithRate(ClientType client, int rate) {
            mClient = client;
            mRate = rate;
        }

        @Override
        public boolean equals(Object o) {
            //TODO(egranata): is this truly necessary?
            if (o instanceof ClientWithRate &&
                mClient == ((ClientWithRate) o).mClient) {
                return true;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return mClient.hashCode();
        }

        int getRate() {
            return mRate;
        }

        void setRate(int rate) {
            mRate = rate;
        }

        ClientType getClient() {
            return mClient;
        }
    }

    private final LinkedList<ClientWithRate<ClientType>> mClients = new LinkedList<>();
    /** rate sent to car */
    private int mRate;

    Listeners(int rate) {
        mRate = rate;
    }

    int getRate() {
        return mRate;
    }

    void setRate(int rate) {
        mRate = rate;
    }

    /** update rate from existing clients and return true if rate is changed. */
    boolean updateRate() {
        //TODO(egranata): we might need to support other rate ranges
        int fastestRate = CarSensorManager.SENSOR_RATE_NORMAL;
        for (ClientWithRate<ClientType> clientWithRate: mClients) {
            int clientRate = clientWithRate.getRate();
            if (clientRate < fastestRate) {
                fastestRate = clientRate;
            }
        }
        if (mRate != fastestRate) {
            mRate = fastestRate;
            return true;
        }
        return false;
    }

    void addClientWithRate(ClientWithRate<ClientType> clientWithRate) {
        mClients.add(clientWithRate);
    }

    void removeClientWithRate(ClientWithRate<ClientType> clientWithRate) {
        mClients.remove(clientWithRate);
    }

    int getNumberOfClients() {
        return mClients.size();
    }

    Iterable<ClientWithRate<ClientType>> getClients() {
        return mClients;
    }

    ClientWithRate<ClientType> findClientWithRate(ClientType client) {
        for (ClientWithRate<ClientType> clientWithRate: mClients) {
            if (clientWithRate.getClient() == client) {
                return clientWithRate;
            }
        }
        return null;
    }

    void release() {
        for (ClientWithRate<ClientType> clientWithRate: mClients) {
            clientWithRate.getClient().release();
        }
        mClients.clear();
    }
}
