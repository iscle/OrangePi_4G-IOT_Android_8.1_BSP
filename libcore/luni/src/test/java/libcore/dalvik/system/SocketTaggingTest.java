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
 * limitations under the License
 */

package libcore.dalvik.system;

import android.system.ErrnoException;
import android.system.StructStat;
import dalvik.system.SocketTagger;
import junit.framework.TestCase;

import java.io.FileDescriptor;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SocketTaggingTest extends TestCase {
    static final class StatAndDescriptor {
        final int fd;
        final StructStat stat;

        StatAndDescriptor(FileDescriptor fd) {
            this.fd = fd.getInt$();
            this.stat = fstat(fd);
        }

        @Override
        public String toString() {
            return "[fd=" + fd + ", stat=" + stat + "]";
        }
    }

    static class RecordingSocketTagger extends SocketTagger {
        private final Map<Integer, StatAndDescriptor> liveDescriptors = new HashMap<>();

        @Override
        public void tag(FileDescriptor socketDescriptor) throws SocketException {
            liveDescriptors.put(socketDescriptor.getInt$(),
                    new StatAndDescriptor(socketDescriptor));
        }

        @Override
        public void untag(FileDescriptor socketDescriptor) throws SocketException {
            StatAndDescriptor existing = liveDescriptors.remove(socketDescriptor.getInt$());

            // We compare the current description of the descriptor with the description
            // we used to tag with and make sure they describe the same file. This helps test
            // whether we untag the socket at the "right" time.
            StructStat current = fstat(socketDescriptor);
            assertEquals(existing.stat.st_dev, current.st_dev);
            assertEquals(existing.stat.st_ino, current.st_ino);
        }

        public Map<Integer, StatAndDescriptor> getLiveDescriptors() {
            return liveDescriptors;
        }
    }

    private RecordingSocketTagger tagger;
    private SocketTagger original;

    private ServerSocketChannel server;

    @Override
    public void setUp() throws Exception {
        server = ServerSocketChannel.open();
        server.configureBlocking(false);
        server.bind(null);

        original = SocketTagger.get();
        tagger = new RecordingSocketTagger();
        SocketTagger.set(tagger);
    }

    @Override
    public void tearDown() {
        SocketTagger.set(original);
    }

    public void testSocketChannel() throws Exception {
        SocketChannel sc = SocketChannel.open();
        sc.connect(server.getLocalAddress());
        assertEquals(1, tagger.getLiveDescriptors().size());

        sc.close();

        assertEquals(Collections.EMPTY_MAP, tagger.getLiveDescriptors());
    }

    public void testServerSocketChannel() throws Exception {
        ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.bind(null);
        assertEquals(1, tagger.getLiveDescriptors().size());

        ssc.close();

        assertEquals(Collections.EMPTY_MAP, tagger.getLiveDescriptors());
    }

    public void testDatagramChannel() throws Exception {
        DatagramChannel dc = DatagramChannel.open();
        dc.connect(server.getLocalAddress());
        assertEquals(1, tagger.getLiveDescriptors().size());

        dc.close();

        assertEquals(Collections.EMPTY_MAP, tagger.getLiveDescriptors());
    }

    public void testSocket() throws Exception {
        Socket s = new Socket();
        s.connect(server.getLocalAddress());
        assertEquals(1, tagger.getLiveDescriptors().size());

        s.close();

        assertEquals(Collections.EMPTY_MAP, tagger.getLiveDescriptors());
    }

    public void testDatagramSocket() throws Exception {
        DatagramSocket d = new DatagramSocket();
        d.connect(server.getLocalAddress());
        assertEquals(1, tagger.getLiveDescriptors().size());

        d.close();

        assertEquals(Collections.EMPTY_MAP, tagger.getLiveDescriptors());
    }

    private static StructStat fstat(FileDescriptor fd) {
        try {
            return android.system.Os.fstat(fd);
        } catch (ErrnoException e) {
            throw new AssertionError(e);
        }
    }
}
