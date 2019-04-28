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

package libcore.io;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.fail;

@RunWith(JUnit4.class)
public class BlockGuardOsTest {

    final static Pattern pattern = Pattern.compile("[\\w\\$]+\\([^)]*\\)");

    /**
     * Checks that BlockGuardOs is updated when the Os interface changes. BlockGuardOs extends
     * ForwardingOs so doing so isn't an obvious step and it can be missed. When adding methods to
     * Os developers must give consideration to whether extra behavior should be added to
     * BlockGuardOs. Developers failing this test should add to the list of method below
     * (if the calls cannot block) or should add an override for the method with the appropriate
     * calls to BlockGuard (if the calls can block).
     */
    @Test
    public void test_checkNewMethodsInPosix() {
        List<String> methodsNotRequireBlockGuardChecks = Arrays.asList(
                "android_getaddrinfo(java.lang.String,android.system.StructAddrinfo,int)",
                "bind(java.io.FileDescriptor,java.net.InetAddress,int)",
                "bind(java.io.FileDescriptor,java.net.SocketAddress)",
                "capget(android.system.StructCapUserHeader)",
                "capset(android.system.StructCapUserHeader,android.system.StructCapUserData[])",
                "dup(java.io.FileDescriptor)",
                "dup2(java.io.FileDescriptor,int)",
                "environ()",
                "fcntlFlock(java.io.FileDescriptor,int,android.system.StructFlock)",
                "fcntlInt(java.io.FileDescriptor,int,int)",
                "fcntlVoid(java.io.FileDescriptor,int)",
                "gai_strerror(int)",
                "getegid()",
                "getenv(java.lang.String)",
                "geteuid()",
                "getgid()",
                "getifaddrs()",
                "getnameinfo(java.net.InetAddress,int)",
                "getpeername(java.io.FileDescriptor)",
                "getpgid(int)",
                "getpid()",
                "getppid()",
                "getpwnam(java.lang.String)",
                "getpwuid(int)",
                "getrlimit(int)",
                "getsockname(java.io.FileDescriptor)",
                "getsockoptByte(java.io.FileDescriptor,int,int)",
                "getsockoptInAddr(java.io.FileDescriptor,int,int)",
                "getsockoptInt(java.io.FileDescriptor,int,int)",
                "getsockoptLinger(java.io.FileDescriptor,int,int)",
                "getsockoptTimeval(java.io.FileDescriptor,int,int)",
                "getsockoptUcred(java.io.FileDescriptor,int,int)",
                "gettid()",
                "getuid()",
                "if_indextoname(int)",
                "if_nametoindex(java.lang.String)",
                "inet_pton(int,java.lang.String)",
                "ioctlFlags(java.io.FileDescriptor,java.lang.String)",
                "ioctlInetAddress(java.io.FileDescriptor,int,java.lang.String)",
                "ioctlInt(java.io.FileDescriptor,int,android.util.MutableInt)",
                "ioctlMTU(java.io.FileDescriptor,java.lang.String)",
                "isatty(java.io.FileDescriptor)",
                "kill(int,int)",
                "listen(java.io.FileDescriptor,int)",
                "listxattr(java.lang.String)",
                "mincore(long,long,byte[])",
                "mlock(long,long)",
                "mmap(long,long,int,int,java.io.FileDescriptor,long)",
                "munlock(long,long)",
                "munmap(long,long)",
                "pipe2(int)",
                "prctl(int,long,long,long,long)",
                "setegid(int)",
                "setenv(java.lang.String,java.lang.String,boolean)",
                "seteuid(int)",
                "setgid(int)",
                "setpgid(int,int)",
                "setregid(int,int)",
                "setreuid(int,int)",
                "setsid()",
                "setsockoptByte(java.io.FileDescriptor,int,int,int)",
                "setsockoptGroupReq(java.io.FileDescriptor,int,int,android.system.StructGroupReq)",
                "setsockoptGroupSourceReq(java.io.FileDescriptor,int,int,android.system.StructGroupSourceReq)",
                "setsockoptIfreq(java.io.FileDescriptor,int,int,java.lang.String)",
                "setsockoptInt(java.io.FileDescriptor,int,int,int)",
                "setsockoptIpMreqn(java.io.FileDescriptor,int,int,int)",
                "setsockoptLinger(java.io.FileDescriptor,int,int,android.system.StructLinger)",
                "setsockoptTimeval(java.io.FileDescriptor,int,int,android.system.StructTimeval)",
                "setuid(int)",
                "shutdown(java.io.FileDescriptor,int)",
                "strerror(int)",
                "strsignal(int)",
                "sysconf(int)",
                "tcdrain(java.io.FileDescriptor)",
                "tcsendbreak(java.io.FileDescriptor,int)",
                "umask(int)",
                "uname()",
                "unsetenv(java.lang.String)",
                "waitpid(int,android.util.MutableInt,int)" );
        Set<String> methodsNotRequiredBlockGuardCheckSet = new HashSet<>(
                methodsNotRequireBlockGuardChecks);

        Set<String> methodsInBlockGuardOs = new HashSet<>();

        // Populate the set of the public methods implemented in BlockGuardOs.
        for (Method method : BlockGuardOs.class.getDeclaredMethods()) {
            String methodNameAndParameters = getMethodNameAndParameters(method.toString());
            methodsInBlockGuardOs.add(methodNameAndParameters);
        }

        // Verify that all the methods in libcore.io.Os should either be overridden in BlockGuardOs
        // or else they should be in the "methodsNotRequiredBlockGuardCheckSet".
        for (Method method : Os.class.getDeclaredMethods()) {
            String methodSignature = method.toString();
            String methodNameAndParameters = getMethodNameAndParameters(methodSignature);
            if (!methodsNotRequiredBlockGuardCheckSet.contains(methodNameAndParameters) &&
                    !methodsInBlockGuardOs.contains(methodNameAndParameters)) {
                fail(methodNameAndParameters + " is not present in "
                        + "methodsNotRequiredBlockGuardCheckSet and is also not overridden in"
                        + " BlockGuardOs class. Either override the method in BlockGuardOs or"
                        + " add it in the methodsNotRequiredBlockGuardCheckSet");

            }
        }
    }

    /**
     * Extract method name and parameter information from the method signature.
     * For example, for input "public void package.class.method(A,B)", the output will be
     * "method(A,B)".
     */
    private static String getMethodNameAndParameters(String methodSignature) {
        Matcher methodPatternMatcher = pattern.matcher(methodSignature);
        if (methodPatternMatcher.find()) {
            return methodPatternMatcher.group();
        } else {
            throw new IllegalArgumentException(methodSignature);
        }
    }
}
