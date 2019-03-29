/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.permission.cts;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.system.ErrnoException;
import android.util.Pair;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStatVfs;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.LargeTest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Verify certain permissions on the filesystem
 *
 * TODO: Combine this file with {@link android.os.cts.FileAccessPermissionTest}
 */
public class FileSystemPermissionTest extends AndroidTestCase {

    @MediumTest
    public void testCreateFileHasSanePermissions() throws Exception {
        File myFile = new File(getContext().getFilesDir(), "hello");
        FileOutputStream stream = new FileOutputStream(myFile);
        stream.write("hello world".getBytes());
        stream.close();
        try {
            FileUtils.FileStatus status = new FileUtils.FileStatus();
            FileUtils.getFileStatus(myFile.getAbsolutePath(), status, false);
            int expectedPerms = FileUtils.S_IFREG
                    | FileUtils.S_IWUSR
                    | FileUtils.S_IRUSR;
            assertEquals(
                    "Newly created files should have 0600 permissions",
                    Integer.toOctalString(expectedPerms),
                    Integer.toOctalString(status.mode));
        } finally {
            assertTrue(myFile.delete());
        }
    }

    @MediumTest
    public void testCreateDirectoryHasSanePermissions() throws Exception {
        File myDir = new File(getContext().getFilesDir(), "helloDirectory");
        assertTrue(myDir.mkdir());
        try {
            FileUtils.FileStatus status = new FileUtils.FileStatus();
            FileUtils.getFileStatus(myDir.getAbsolutePath(), status, false);
            int expectedPerms = FileUtils.S_IFDIR
                    | FileUtils.S_IWUSR
                    | FileUtils.S_IRUSR
                    | FileUtils.S_IXUSR;
            assertEquals(
                    "Newly created directories should have 0700 permissions",
                    Integer.toOctalString(expectedPerms),
                    Integer.toOctalString(status.mode));

        } finally {
            assertTrue(myDir.delete());
        }
    }

    @MediumTest
    public void testOtherApplicationDirectoriesAreNotWritable() throws Exception {
        Set<File> writableDirs = new HashSet<File>();
        List<ApplicationInfo> apps = getContext()
                .getPackageManager()
                .getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES);
        String myAppDirectory = getContext().getApplicationInfo().dataDir;
        for (ApplicationInfo app : apps) {
            if (app.dataDir != null && !myAppDirectory.equals(app.dataDir)) {
                writableDirs.addAll(getWritableDirectoriesAndSubdirectoriesOf(new File(app.dataDir)));
            }
        }

        assertTrue("Found writable directories: " + writableDirs.toString(),
                writableDirs.isEmpty());
    }

    @MediumTest
    public void testApplicationParentDirectoryNotWritable() throws Exception {
        String myDataDir = getContext().getApplicationInfo().dataDir;
        File parentDir = new File(myDataDir).getParentFile();
        assertFalse(parentDir.toString(), isDirectoryWritable(parentDir));
    }

    @MediumTest
    public void testDataDirectoryNotWritable() throws Exception {
        assertFalse(isDirectoryWritable(Environment.getDataDirectory()));
    }

    @MediumTest
    public void testAndroidRootDirectoryNotWritable() throws Exception {
        assertFalse(isDirectoryWritable(Environment.getRootDirectory()));
    }

    @MediumTest
    public void testDownloadCacheDirectoryNotWritable() throws Exception {
        assertFalse(isDirectoryWritable(Environment.getDownloadCacheDirectory()));
    }

    @MediumTest
    public void testRootDirectoryNotWritable() throws Exception {
        assertFalse(isDirectoryWritable(new File("/")));
    }

    @MediumTest
    public void testDevDirectoryNotWritable() throws Exception {
        assertFalse(isDirectoryWritable(new File("/dev")));
    }

    @MediumTest
    public void testProcDirectoryNotWritable() throws Exception {
        assertFalse(isDirectoryWritable(new File("/proc")));
    }

    @MediumTest
    public void testDevDiagSane() throws Exception {
        File f = new File("/dev/diag");
        assertFalse(f.canRead());
        assertFalse(f.canWrite());
        assertFalse(f.canExecute());
    }

    /* b/26813932 */
    @MediumTest
    public void testProcInterruptsNotReadable() throws Exception {
        File f = new File("/proc/interrupts");
        assertFalse(f.canRead());
        assertFalse(f.canWrite());
        assertFalse(f.canExecute());
    }

    /* b/26813932 */
    @MediumTest
    public void testProcStatNotReadable() throws Exception {
        File f = new File("/proc/stat");
        assertFalse(f.canRead());
        assertFalse(f.canWrite());
        assertFalse(f.canExecute());
    }

    @MediumTest
    public void testDevMemSane() throws Exception {
        File f = new File("/dev/mem");
        assertFalse(f.exists());
    }

    @MediumTest
    public void testDevkmemSane() throws Exception {
        File f = new File("/dev/kmem");
        assertFalse(f.exists());
    }

    @MediumTest
    public void testDevPortSane() throws Exception {
        File f = new File("/dev/port");
        assertFalse(f.exists());
    }

    @MediumTest
    public void testPn544Sane() throws Exception {
        File f = new File("/dev/pn544");
        assertFalse(f.canRead());
        assertFalse(f.canWrite());
        assertFalse(f.canExecute());

        assertFileOwnedBy(f, "nfc");
        assertFileOwnedByGroup(f, "nfc");
    }

    @MediumTest
    public void testBcm2079xSane() throws Exception {
        File f = new File("/dev/bcm2079x");
        assertFalse(f.canRead());
        assertFalse(f.canWrite());
        assertFalse(f.canExecute());

        assertFileOwnedBy(f, "nfc");
        assertFileOwnedByGroup(f, "nfc");
    }

    @MediumTest
    public void testBcm2079xi2cSane() throws Exception {
        File f = new File("/dev/bcm2079x-i2c");
        assertFalse(f.canRead());
        assertFalse(f.canWrite());
        assertFalse(f.canExecute());

        assertFileOwnedBy(f, "nfc");
        assertFileOwnedByGroup(f, "nfc");
    }

    @MediumTest
    public void testDevQtaguidSane() throws Exception {
        File f = new File("/dev/xt_qtaguid");
        assertTrue(f.canRead());
        assertFalse(f.canWrite());
        assertFalse(f.canExecute());

        assertFileOwnedBy(f, "root");
        assertFileOwnedByGroup(f, "root");
    }

    @MediumTest
    public void testProcQtaguidCtrlSane() throws Exception {
        File f = new File("/proc/net/xt_qtaguid/ctrl");
        assertTrue(f.canRead());
        assertTrue(f.canWrite());
        assertFalse(f.canExecute());

        assertFileOwnedBy(f, "root");
        assertFileOwnedByGroup(f, "net_bw_acct");
    }

    @MediumTest
    public void testProcQtaguidStatsSane() throws Exception {
        File f = new File("/proc/net/xt_qtaguid/stats");
        assertTrue(f.canRead());
        assertFalse(f.canWrite());
        assertFalse(f.canExecute());

        assertFileOwnedBy(f, "root");
        assertFileOwnedByGroup(f, "net_bw_stats");
    }

    @MediumTest
    public void testProcSelfOomAdjSane() {
        File f = new File("/proc/self/oom_adj");
        assertFalse(f.canWrite());
        assertFalse(f.canExecute());
    }

    @MediumTest
    public void testProcSelfOomScoreAdjSane() {
        File f = new File("/proc/self/oom_score_adj");
        assertFalse(f.canWrite());
        assertFalse(f.canExecute());
    }

    private static List<Pair<Long, Long>> mappedPageRanges() throws IOException {
        final BigInteger PAGE_SIZE = new BigInteger("4096");

        final Pattern mapsPattern = Pattern.compile("^(\\p{XDigit}+)-(\\p{XDigit}+)");
        List<Pair<Long, Long>> ret = new LinkedList<>();

        BufferedReader reader = new BufferedReader(new FileReader("/proc/self/maps"));
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                Matcher m = mapsPattern.matcher(line);
                m.find();

                long start = new BigInteger(m.group(1), 16).divide(PAGE_SIZE).longValue();
                long end = new BigInteger(m.group(2), 16).divide(PAGE_SIZE).longValue();

                ret.add(new Pair<>(start, end));
            }

            return ret;
        } finally {
            reader.close();
        }
    }

    private static boolean pfnIsZero(FileDescriptor pagemap, long start, long end) throws ErrnoException, IOException {
        // Note: reads from /proc/self/pagemap *must* be 64-bit aligned.  Use low-level android.system.Os routines to
        // ensure this.
        final int SIZEOF_U64 = 8;
        final long PAGE_PRESENT = 1L << 63;
        final long PFN_MASK = (1L << 55) - 1;

        for (long page = start; page < end; page++) {
            long offset = page * SIZEOF_U64;
            long seek = Os.lseek(pagemap, offset, OsConstants.SEEK_SET);
            if (offset != seek)
                throw new IOException("lseek(" + offset + ") returned " + seek);

            byte bytes[] = new byte[SIZEOF_U64];
            ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder());
            int read = Os.read(pagemap, buf);
            if (read != bytes.length)
                throw new IOException("read(" + bytes.length + ") returned " + read);

            buf.position(0);
            long entry = buf.getLong();
            if ((entry & PAGE_PRESENT) == PAGE_PRESENT && (entry & PFN_MASK) != 0)
                return false;
        }

        return true;
    }

    @MediumTest
    public void testProcSelfPagemapSane() throws ErrnoException, IOException {
        FileDescriptor pagemap = null;
        int dumpable = Os.prctl(OsConstants.PR_GET_DUMPABLE, 0, 0, 0, 0);
        Os.prctl(OsConstants.PR_SET_DUMPABLE, 1, 0, 0, 0);

        try {
            pagemap = Os.open("/proc/self/pagemap", OsConstants.O_RDONLY, 0);

            for (Pair<Long, Long> range : mappedPageRanges())
                if (!pfnIsZero(pagemap, range.first, range.second))
                    fail("Device is missing the following kernel security patch: "
                         + "https://git.kernel.org/cgit/linux/kernel/git/torvalds/linux.git/commit/?id=ab676b7d6fbf4b294bf198fb27ade5b0e865c7ce");
        } catch (ErrnoException e) {
            if (e.errno == OsConstants.EPERM)
                // expected before 4.2
                return;

            throw e;
        } finally {
            if (pagemap != null)
                Os.close(pagemap);
            Os.prctl(OsConstants.PR_SET_DUMPABLE, dumpable, 0, 0, 0);
        }
    }

    @MediumTest
    public void testTcpDefaultRwndSane() throws Exception {
        File f = new File("/proc/sys/net/ipv4/tcp_default_init_rwnd");
        assertTrue(f.canRead());
        assertFalse(f.canWrite());
        assertFalse(f.canExecute());

        assertFileOwnedBy(f, "root");
        assertFileOwnedByGroup(f, "root");
    }

    @MediumTest
    public void testIdletimerDirectoryExistsAndSane() throws Exception {
        File dir = new File("/sys/class/xt_idletimer");
        assertTrue(dir.isDirectory());
        assertFalse(dir.canWrite());
        assertTrue(dir.canExecute());

        assertFileOwnedBy(dir, "root");
        assertFileOwnedByGroup(dir, "root");
    }


    @MediumTest
    public void testProcfsMmapRndBitsExistsAndSane() throws Exception {
        String arch = System.getProperty("os.arch");
        boolean supported = false;
        boolean supported_64 = false;

        if (arch.equals("aarch64") || arch.equals("x86_64"))
            supported_64 = true;
        else if (arch.startsWith("arm") || arch.endsWith("86"))
            supported = true;

        /* 64-bit OS should support running 32-bit applications */
        if (supported_64) {
            File f = new File("/proc/sys/vm/mmap_rnd_compat_bits");
            assertTrue(f.exists());
            assertFalse(f.canRead());
            assertFalse(f.canWrite());
            assertFalse(f.canExecute());
        }

        if (supported_64 || supported) {
            File f = new File("/proc/sys/vm/mmap_rnd_bits");
            assertTrue(f.exists());
            assertFalse(f.canRead());
            assertFalse(f.canWrite());
            assertFalse(f.canExecute());
        }
    }

    /**
     * Assert that a file is owned by a specific owner. This is a noop if the
     * file does not exist.
     *
     * @param file The file to check.
     * @param expectedOwner The owner of the file.
     */
    private static void assertFileOwnedBy(File file, String expectedOwner) {
        FileUtils.FileStatus status = new FileUtils.FileStatus();
        String path = file.getAbsolutePath();
        if (file.exists() && FileUtils.getFileStatus(path, status, true)) {
            String actualOwner = FileUtils.getUserName(status.uid);
            if (!expectedOwner.equals(actualOwner)) {
                String msg = String.format("Wrong owner. Expected '%s', but found '%s' for %s.",
                        expectedOwner, actualOwner, path);
                fail(msg);
            }
        }
    }

    /**
     * Assert that a file is owned by a specific group. This is a noop if the
     * file does not exist.
     *
     * @param file The file to check.
     * @param expectedGroup The owner group of the file.
     */
    private static void assertFileOwnedByGroup(File file, String expectedGroup) {
        FileUtils.FileStatus status = new FileUtils.FileStatus();
        String path = file.getAbsolutePath();
        if (file.exists() && FileUtils.getFileStatus(path, status, true)) {
            String actualGroup = FileUtils.getGroupName(status.gid);
            if (!expectedGroup.equals(actualGroup)) {
                String msg = String.format("Wrong group. Expected '%s', but found '%s' for %s.",
                        expectedGroup, actualGroup, path);
                fail(msg);
            }
        }
    }

    @MediumTest
    public void testTtyO3Sane() throws Exception {
        File f = new File("/dev/ttyO3");
        assertFalse(f.canRead());
        assertFalse(f.canWrite());
        assertFalse(f.canExecute());
    }

    @MediumTest
    public void testDataMediaSane() throws Exception {
        final File f = new File("/data/media");
        assertFalse(f.canRead());
        assertFalse(f.canWrite());
        assertFalse(f.canExecute());
    }

    @MediumTest
    public void testMntShellSane() throws Exception {
        final File f = new File("/mnt/shell");
        assertFalse(f.canRead());
        assertFalse(f.canWrite());
        assertFalse(f.canExecute());
    }

    @MediumTest
    public void testMntSecureSane() throws Exception {
        final File f = new File("/mnt/secure");
        assertFalse(f.canRead());
        assertFalse(f.canWrite());
        assertFalse(f.canExecute());
    }

    private static boolean isDirectoryWritable(File directory) {
        File toCreate = new File(directory, "hello");
        try {
            toCreate.createNewFile();
            return true;
        } catch (IOException e) {
            // It's expected we'll get a "Permission denied" exception.
        } finally {
            toCreate.delete();
        }
        return false;
    }

    /**
     * Verify that any publicly readable directories reachable from
     * the root directory are not writable.  An application should only be
     * able to write to it's own home directory. World writable directories
     * are a security hole because they enable a number of different attacks.
     * <ul>
     *   <li><a href="http://en.wikipedia.org/wiki/Symlink_race">Symlink Races</a></li>
     *   <li>Data destruction by deleting or renaming files you don't own</li>
     *   <li>Data substitution by replacing trusted files with untrusted files</li>
     * </ul>
     *
     * Note: Because not all directories are readable, this is a best-effort
     * test only.  Writable directories within unreadable subdirectories
     * will NOT be detected by this code.
     */
    @LargeTest
    public void testAllOtherDirectoriesNotWritable() throws Exception {
        File start = new File("/");
        Set<File> writableDirs = getWritableDirectoriesAndSubdirectoriesOf(start);

        assertTrue("Found writable directories: " + writableDirs.toString(),
                writableDirs.isEmpty());
    }

    private static final Set<String> OTHER_RANDOM_DIRECTORIES = new HashSet<String>(
            Arrays.asList(
                    "/app-cache",
                    "/app-cache/ciq/socket",
                    "/cache/fotapkg",
                    "/cache/fotapkg/tmp",
                    "/data/_SamsungBnR_",
                    "/data/_SamsungBnR_/BR",
                    "/data/2nd-init",
                    "/data/amit",
                    "/data/anr",
                    "/data/app",
                    "/data/app-private",
                    "/data/backup",
                    "/data/battd",
                    "/data/bootlogo",
                    "/data/btips",
                    "/data/btips/TI",
                    "/data/btips/TI/opp",
                    "/data/cache",
                    "/data/calibration",
                    "/data/clipboard",
                    "/data/clp",
                    "/data/dalvik-cache",
                    "/data/data",
                    "/data/data/.drm",
                    "/data/data/.drm/.wmdrm",
                    "/data/data/cw",
                    "/data/data/com.android.htcprofile",
                    "/data/data/com.android.providers.drm/rights",
                    "/data/data/com.htc.android.qxdm2sd",
                    "/data/data/com.htc.android.qxdm2sd/bin",
                    "/data/data/com.htc.android.qxdm2sd/data",
                    "/data/data/com.htc.android.qxdm2sd/tmp",
                    "/data/data/com.htc.android.netlogger/data",
                    "/data/data/com.htc.messagecs/att",
                    "/data/data/com.htc.messagecs/pdu",
                    "/data/data/com.htc.loggers/bin",
                    "/data/data/com.htc.loggers/data",
                    "/data/data/com.htc.loggers/htclog",
                    "/data/data/com.htc.loggers/tmp",
                    "/data/data/com.htc.loggers/htcghost",
                    "/data/data/com.lge.ers/android",
                    "/data/data/com.lge.ers/arm9",
                    "/data/data/com.lge.ers/kernel",
                    "/data/data/com.lge.wmc",
                    "/data/data/com.redbend.vdmc/lib",
                    "/data/data/recovery",
                    "/data/data/recovery/HTCFOTA",
                    "/data/data/recovery/OMADM",
                    "/data/data/shared",
                    "/data/diag_logs",
                    "/data/dontpanic",
                    "/data/drm",
                    "/data/drm/fwdlock",
                    "/data/drm/IDM",
                    "/data/drm/IDM/HTTP",
                    "/data/drm/rights",
                    "/data/dump",
                    "/data/efslog",
                    "/data/emt",
                    "/data/factory",
                    "/data/fics",
                    "/data/fics/dev",
                    "/data/fota",
                    "/data/gps",
                    "/data/gps/log",
                    "/data/gps/var",
                    "/data/gps/var/run",
                    "/data/gpscfg",
                    "/data/hwvefs",
                    "/data/htcfs",
                    "/data/img",
                    "/data/install",
                    "/data/internal-device",
                    "/data/internal-device/DCIM",
                    "/data/last_alog",
                    "/data/last_klog",
                    "/data/local",
                    "/data/local/logs",
                    "/data/local/logs/kernel",
                    "/data/local/logs/logcat",
                    "/data/local/logs/resetlog",
                    "/data/local/logs/smem",
                    "/data/local/mono",
                    "/data/local/mono/pulse",
                    "/data/local/purple",
                    "/data/local/purple/sound",
                    "/data/local/rights",
                    "/data/local/rwsystag",
                    "/data/local/skel",
                    "/data/local/skel/default",
                    "/data/local/skel/defualt", // Mispelled "defualt" is intentional
                    "/data/local/tmp",
                    "/data/local/tmp/com.nuance.android.vsuite.vsuiteapp",
                    "/data/log",
                    "/data/logger",
                    "/data/logs",
                    "/data/logs/core",
                    "/data/lost+found",
                    "/data/mdl",
                    "/data/misc",
                    "/data/misc/bluetooth",
                    "/data/misc/bluetooth/logs",
                    "/data/misc/dhcp",
                    "/data/misc/lockscreen",
                    "/data/misc/sensor",
                    "/data/misc/webwidgets",
                    "/data/misc/webwidgets/chess",
                    "/data/misc/widgets",
                    "/data/misc/wifi",
                    "/data/misc/wifi/sockets",
                    "/data/misc/wimax",
                    "/data/misc/wimax/sockets",
                    "/data/misc/wminput",
                    "/data/misc/wpa_supplicant",
                    "/data/nv",
                    "/data/nvcam",
                    "/data/panic",
                    "/data/panicreports",
                    "/data/preinstall_md5",
                    "/data/property",
                    "/data/radio",
                    "/data/secure",
                    "/data/security",
                    "/data/sensors",
                    "/data/shared",
                    "/data/simcom",
                    "/data/simcom/btadd",
                    "/data/simcom/simlog",
                    "/data/system",
                    "/data/tmp",
                    "/data/tombstones",
                    "/data/tombstones/ramdump",
                    "/data/tpapi",
                    "/data/tpapi/etc",
                    "/data/tpapi/etc/tpa",
                    "/data/tpapi/etc/tpa/persistent",
                    "/data/tpapi/user.bin",
                    "/data/vpnch",
                    "/data/wapi",
                    "/data/wifi",
                    "/data/wimax",
                    "/data/wimax/log",
                    "/data/wiper",
                    "/data/wpstiles",
                    "/data/xt9",
                    "/dbdata/databases",
                    "/efs/.android",
                    "/mnt/sdcard",
                    "/mnt/usbdrive",
                    "/mnt_ext",
                    "/mnt_ext/badablk2",
                    "/mnt_ext/badablk3",
                    "/mnt_ext/cache",
                    "/mnt_ext/data",
                    "/system/etc/security/drm",
                    "/synthesis/hades",
                    "/synthesis/chimaira",
                    "/synthesis/shdisp",
                    "/synthesis/hdmi",
                    "/tmp"
            )
    );

    /**
     * Verify that directories not discoverable by
     * testAllOtherDirectoriesNotWritable are not writable.  An application
     * should only be able to write to it's own home directory. World
     * writable directories are a security hole because they enable a
     * number of different attacks.
     * <ul>
     *   <li><a href="http://en.wikipedia.org/wiki/Symlink_race">Symlink Races</a></li>
     *   <li>Data destruction by deleting or renaming files you don't own</li>
     *   <li>Data substitution by replacing trusted files with untrusted files</li>
     * </ul>
     *
     * Because /data and /data/data are not readable, we blindly try to
     * poke around in there looking for bad directories.  There has to be
     * a better way...
     */
    @LargeTest
    public void testOtherRandomDirectoriesNotWritable() throws Exception {
        Set<File> writableDirs = new HashSet<File>();
        for (String dir : OTHER_RANDOM_DIRECTORIES) {
            File start = new File(dir);
            writableDirs.addAll(getWritableDirectoriesAndSubdirectoriesOf(start));
        }

        assertTrue("Found writable directories: " + writableDirs.toString(),
                writableDirs.isEmpty());
    }

    @LargeTest
    public void testReadingSysFilesDoesntFail() throws Exception {
        ExecutorService executor = Executors.newCachedThreadPool();
        tryToReadFromAllIn(new File("/sys"), executor);
        executor.shutdownNow();
    }

    private static void tryToReadFromAllIn(File dir, ExecutorService executor) throws IOException {
        assertTrue(dir.isDirectory());

        if (isSymbolicLink(dir)) {
            // don't examine symbolic links.
            return;
        }

        File[] files = dir.listFiles();

        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    tryToReadFromAllIn(f, executor);
                } else {
                    tryFileOpenRead(f, executor);
                }
            }
        }
    }

    private static void tryFileOpenRead(final File f, ExecutorService executor) throws IOException {
        // Callable requires stack variables to be final.
        Callable<Boolean> readFile = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return tryFileRead(f);
            }
        };

        Boolean completed = false;
        String fileName = null;
        Future<Boolean> future = null;
        try {
            fileName = f.getCanonicalPath();

            future = executor.submit(readFile);

            // Block, waiting no more than set seconds.
            completed = future.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("TIMEOUT: " + fileName);
        } catch (InterruptedException e) {
            System.out.println("INTERRUPTED: " + fileName);
        } catch (ExecutionException e) {
            System.out.println("TASK WAS ABORTED BY EXCEPTION: " + fileName);
        } catch (IOException e) {
            // File.getCanonicalPath() will throw this.
        } finally {
            if (future != null) {
                future.cancel(true);
            }
        }
    }

    private static Boolean tryFileRead(File f) {
        byte[] b = new byte[1024];
        try {
            System.out.println("looking at " + f.getCanonicalPath());

            FileInputStream fis = new FileInputStream(f);
            while((fis.available() != 0) && (fis.read(b) != -1)) {
                // throw away data
            }

            fis.close();
        } catch (IOException e) {
            // ignore
        }
        return true;
    }

    private static final Set<File> SYS_EXCEPTIONS = new HashSet<File>(
            Arrays.asList(
                new File("/sys/kernel/debug/tracing/trace_marker"),
                new File("/sys/fs/selinux/member"),
                new File("/sys/fs/selinux/user"),
                new File("/sys/fs/selinux/relabel"),
                new File("/sys/fs/selinux/create"),
                new File("/sys/fs/selinux/access"),
                new File("/sys/fs/selinux/context")
            ));

    @LargeTest
    public void testAllFilesInSysAreNotWritable() throws Exception {
        Set<File> writable = getAllWritableFilesInDirAndSubDir(new File("/sys"));
        writable.removeAll(SYS_EXCEPTIONS);
        assertTrue("Found writable: " + writable.toString(),
                writable.isEmpty());
    }

    private static Set<File>
    getAllWritableFilesInDirAndSubDir(File dir) throws Exception {
        assertTrue(dir.isDirectory());
        Set<File> retval = new HashSet<File>();

        if (isSymbolicLink(dir)) {
            // don't examine symbolic links.
            return retval;
        }

        File[] subDirectories = dir.listFiles(new FileFilter() {
            @Override public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });


        /* recurse into subdirectories */
        if (subDirectories != null) {
            for (File f : subDirectories) {
                retval.addAll(getAllWritableFilesInDirAndSubDir(f));
            }
        }

        File[] filesInThisDirectory = dir.listFiles(new FileFilter() {
            @Override public boolean accept(File pathname) {
                return pathname.isFile();
            }
        });
        if (filesInThisDirectory == null) {
            return retval;
        }

        for (File f: filesInThisDirectory) {
            if (f.canWrite()) {
                retval.add(f.getCanonicalFile());
            }
        }
        return retval;
    }

    public void testSystemMountedRO() throws Exception {
        StructStatVfs vfs = Os.statvfs("/system");
        assertTrue("/system is not mounted read-only", (vfs.f_flag & OsConstants.ST_RDONLY) != 0);
    }

    public void testRootMountedRO() throws Exception {
        StructStatVfs vfs = Os.statvfs("/");
        assertTrue("rootfs is not mounted read-only", (vfs.f_flag & OsConstants.ST_RDONLY) != 0);
    }

    public void testDataMountedNoSuidNoDev() throws Exception {
        StructStatVfs vfs = Os.statvfs(getContext().getFilesDir().getAbsolutePath());
        assertTrue("/data is not mounted NOSUID", (vfs.f_flag & OsConstants.ST_NOSUID) != 0);
        assertTrue("/data is not mounted NODEV", (vfs.f_flag & OsConstants.ST_NODEV) != 0);
    }

    public void testAllBlockDevicesAreSecure() throws Exception {
        Set<File> insecure = getAllInsecureDevicesInDirAndSubdir(new File("/dev"), FileUtils.S_IFBLK);
        assertTrue("Found insecure block devices: " + insecure.toString(),
                insecure.isEmpty());
    }

    public void testDevRandomWorldReadableAndWritable() throws Exception {
        File f = new File("/dev/random");

        assertTrue(f + " cannot be opened for reading", canOpenForReading(f));
        assertTrue(f + " cannot be opened for writing", canOpenForWriting(f));

        FileUtils.FileStatus status = new FileUtils.FileStatus();
        assertTrue(FileUtils.getFileStatus(f.getPath(), status, false));
        assertTrue(
                f + " not world-readable/writable. Actual mode: 0"
                        + Integer.toString(status.mode, 8),
                (status.mode & 0666) == 0666);
    }

    public void testDevUrandomWorldReadableAndWritable() throws Exception {
        File f = new File("/dev/urandom");

        assertTrue(f + " cannot be opened for reading", canOpenForReading(f));
        assertTrue(f + " cannot be opened for writing", canOpenForWriting(f));

        FileUtils.FileStatus status = new FileUtils.FileStatus();
        assertTrue(FileUtils.getFileStatus(f.getPath(), status, false));
        assertTrue(
                f + " not world-readable/writable. Actual mode: 0"
                        + Integer.toString(status.mode, 8),
                (status.mode & 0666) == 0666);
    }

    public void testDevHwRandomLockedDown() throws Exception {
        File f = new File("/dev/hw_random");
        if (!f.exists()) {
            // HW RNG is not required to be exposed on all devices.
            return;
        }

        assertFalse(f + " can be opened for reading", canOpenForReading(f));
        assertFalse(f + " can be opened for writing", canOpenForWriting(f));

        FileUtils.FileStatus status = new FileUtils.FileStatus();
        assertFalse("stat permitted on " + f,
                FileUtils.getFileStatus(f.getPath(), status, false));
    }

    private static boolean canOpenForReading(File f) {
        try (InputStream in = new FileInputStream(f)) {
            return true;
        } catch (IOException expected) {
            return false;
        }
    }

    private static boolean canOpenForWriting(File f) {
        try (OutputStream out = new FileOutputStream(f)) {
            return true;
        } catch (IOException expected) {
            return false;
        }
    }

    public void testFileHasOnlyCapsThrowsOnInvalidCaps() throws Exception {
        try {
            // Ensure negative cap id fails.
            new FileUtils.CapabilitySet()
                    .add(-1)
                    .fileHasOnly("/system/bin/run-as");
            fail();
        }
        catch (IllegalArgumentException e) {
            // expected
        }

        try {
            // Ensure too-large cap throws.
            new FileUtils.CapabilitySet()
                    .add(OsConstants.CAP_LAST_CAP + 1)
                    .fileHasOnly("/system/bin/run-as");
            fail();
        }
        catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test that the /system/bin/run-as command has setuid and setgid
     * attributes set on the file.  If these calls fail, debugger
     * breakpoints for native code will not work as run-as will not
     * be able to perform required elevated-privilege functionality.
     */
    public void testRunAsHasCorrectCapabilities() throws Exception {
        // ensure file is user and group read/executable
        String filename = "/system/bin/run-as";
        FileUtils.FileStatus status = new FileUtils.FileStatus();
        assertTrue(FileUtils.getFileStatus(filename, status, false));
        assertTrue(status.hasModeFlag(FileUtils.S_IRUSR | FileUtils.S_IXUSR));
        assertTrue(status.hasModeFlag(FileUtils.S_IRGRP | FileUtils.S_IXGRP));

        // ensure file owner/group is set correctly
        File f = new File(filename);
        assertFileOwnedBy(f, "root");
        assertFileOwnedByGroup(f, "shell");

        // ensure file has setuid/setgid enabled
        assertTrue(FileUtils.hasSetUidCapability(filename));
        assertTrue(FileUtils.hasSetGidCapability(filename));

        // ensure file has *only* setuid/setgid attributes enabled
        assertTrue(new FileUtils.CapabilitySet()
                .add(OsConstants.CAP_SETUID)
                .add(OsConstants.CAP_SETGID)
                .fileHasOnly("/system/bin/run-as"));
    }

    private static Set<File>
    getAllInsecureDevicesInDirAndSubdir(File dir, int type) throws Exception {
        assertTrue(dir.isDirectory());
        Set<File> retval = new HashSet<File>();

        if (isSymbolicLink(dir)) {
            // don't examine symbolic links.
            return retval;
        }

        File[] subDirectories = dir.listFiles(new FileFilter() {
            @Override public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });


        /* recurse into subdirectories */
        if (subDirectories != null) {
            for (File f : subDirectories) {
                retval.addAll(getAllInsecureDevicesInDirAndSubdir(f, type));
            }
        }

        File[] filesInThisDirectory = dir.listFiles();
        if (filesInThisDirectory == null) {
            return retval;
        }

        for (File f: filesInThisDirectory) {
            FileUtils.FileStatus status = new FileUtils.FileStatus();
            FileUtils.getFileStatus(f.getAbsolutePath(), status, false);
            if (status.isOfType(type)) {
                if (f.canRead() || f.canWrite() || f.canExecute()) {
                    retval.add(f);
                }
                if (status.uid == 2000) {
                    // The shell user should not own any devices
                    retval.add(f);
                }

                // Don't allow devices owned by GIDs
                // accessible to non-privileged applications.
                if ((status.gid == 1007)           // AID_LOG
                          || (status.gid == 1015)  // AID_SDCARD_RW
                          || (status.gid == 1023)  // AID_MEDIA_RW
                          || (status.gid == 1028)  // AID_SDCARD_R
                          || (status.gid == 2000)) // AID_SHELL
                {
                    if (status.hasModeFlag(FileUtils.S_IRGRP)
                            || status.hasModeFlag(FileUtils.S_IWGRP)
                            || status.hasModeFlag(FileUtils.S_IXGRP))
                    {
                        retval.add(f);
                    }
                }
            }
        }
        return retval;
    }

    private Set<File> getWritableDirectoriesAndSubdirectoriesOf(File dir) throws Exception {
        Set<File> retval = new HashSet<File>();
        if (!dir.isDirectory()) {
            return retval;
        }

        if (isSymbolicLink(dir)) {
            // don't examine symbolic links.
            return retval;
        }

        String myHome = getContext().getApplicationInfo().dataDir;
        String thisDir = dir.getCanonicalPath();
        if (thisDir.startsWith(myHome)) {
            // Don't examine directories within our home directory.
            // We expect these directories to be writable.
            return retval;
        }

        if (isDirectoryWritable(dir)) {
            retval.add(dir);
        }

        File[] subFiles = dir.listFiles();
        if (subFiles == null) {
            return retval;
        }

        for (File f : subFiles) {
            retval.addAll(getWritableDirectoriesAndSubdirectoriesOf(f));
        }

        return retval;
    }

    private static boolean isSymbolicLink(File f) throws IOException {
        return !f.getAbsolutePath().equals(f.getCanonicalPath());
    }

}
