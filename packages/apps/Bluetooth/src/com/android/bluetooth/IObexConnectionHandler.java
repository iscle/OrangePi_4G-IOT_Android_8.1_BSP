/*
* Copyright (C) 2014 Samsung System LSI
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
package com.android.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

public interface IObexConnectionHandler {

    /**
     * Called to validate if a connection to the Bluetooth device should be accepted.
     *
     * @param device the connecting BluetoothDevice. Use .getType() to determine the type of
     *         connection.
     * @return Shall return TRUE if the connection should be accepted.
     * FALSE otherwise
     */
    public boolean onConnect(BluetoothDevice device, BluetoothSocket socket);

    /**
     * Will be called in case the accept call fails.
     * When called, at lease one of the accept threads are about to terminate.
     * The behavior needed is to shutdown the ObexServerSockets object, and create a
     * new one.
     */
    public void onAcceptFailed();
}
