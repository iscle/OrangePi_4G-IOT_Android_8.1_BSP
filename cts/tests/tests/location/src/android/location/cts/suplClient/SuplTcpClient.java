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

package android.location.cts.suplClient;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * A TCP client that is used to send and receive SUPL request and responses by the SUPL client. The
 * constructor establishes a connection to the SUPL server specified by a given address and port.
 */
public class SuplTcpClient {

  private static final int READ_TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(10);
  private static final short HEADER_SIZE = 2;
  /** BUFFER_SIZE data size that is enough to hold SUPL responses */
  private static final int SUPL_RESPONSE_BUFFER_SIZE = 16384;
  private static final byte[] SUPL_RESPONSE_BUFFER = new byte[SUPL_RESPONSE_BUFFER_SIZE];

  private Socket socket;
  private BufferedInputStream bufferedInputStream;

  public SuplTcpClient(String suplServerName, int suplServerPort)
      throws UnknownHostException, IOException {
    System.out.println("Connecting to " + suplServerName + " on port " + suplServerPort);
    socket = new Socket(suplServerName, suplServerPort);
    socket.setSoTimeout(READ_TIMEOUT_MILLIS);
    System.out.println("Connection established to " + socket.getOutputStream());
    bufferedInputStream = new BufferedInputStream(socket.getInputStream());

  }

  /** Sends a byte array of SUPL data to the server */
  public void sendSuplRequest(byte[] data) throws IOException {
    socket.getOutputStream().write(data);
  }

  /**
   * Reads SUPL server response and return it as a byte array. Upon the SUPL protocol, the size of
   * the payload is stored in the first two bytes of the response, hence these two bytes are read
   * first followed by reading a payload of that size. Null is returned if the size of the payload
   * is not readable.
   */
  public byte[] getSuplResponse() throws IOException {
    int sizeOfRead = bufferedInputStream.read(SUPL_RESPONSE_BUFFER, 0, HEADER_SIZE);
    if (sizeOfRead == HEADER_SIZE) {
      byte[] lengthArray = {SUPL_RESPONSE_BUFFER[0], SUPL_RESPONSE_BUFFER[1]};
      short dataLength = ByteBuffer.wrap(lengthArray).getShort();
      bufferedInputStream.read(SUPL_RESPONSE_BUFFER, 2, dataLength - HEADER_SIZE);
      return SUPL_RESPONSE_BUFFER;
    } else {
      return null;
    }
  }

  /** Closes the TCP socket */
  public void closeSocket() throws IOException {
    socket.close();
  }
}
