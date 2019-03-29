// Copyright 2017 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package build

import (
	"fmt"
	"io/ioutil"
	"os"
	"os/exec"
	"path/filepath"
	"syscall"
	"testing"

	"android/soong/ui/logger"
)

// some util methods and data structures that aren't directly part of a test
func makeLockDir() (path string, err error) {
	return ioutil.TempDir("", "soong_lock_test")
}
func lockOrFail(t *testing.T) (lock fileLock) {
	lockDir, err := makeLockDir()
	var lockPointer *fileLock
	if err == nil {
		lockPointer, err = newLock(lockDir)
	}
	if err != nil {
		os.RemoveAll(lockDir)
		t.Fatalf("Failed to create lock: %v", err)
	}

	return *lockPointer
}
func removeTestLock(fileLock fileLock) {
	lockdir := filepath.Dir(fileLock.File.Name())
	os.RemoveAll(lockdir)
}

// countWaiter only exists for the purposes of testing lockSynchronous
type countWaiter struct {
	numWaitsElapsed int
	maxNumWaits     int
}

func newCountWaiter(count int) (waiter *countWaiter) {
	return &countWaiter{0, count}
}

func (c *countWaiter) wait() {
	c.numWaitsElapsed++
}
func (c *countWaiter) checkDeadline() (done bool, remainder string) {
	numWaitsRemaining := c.maxNumWaits - c.numWaitsElapsed
	if numWaitsRemaining < 1 {
		return true, ""
	}
	return false, fmt.Sprintf("%v waits remain", numWaitsRemaining)
}
func (c countWaiter) summarize() (summary string) {
	return fmt.Sprintf("waiting %v times", c.maxNumWaits)
}

// countLock only exists for the purposes of testing lockSynchronous
type countLock struct {
	nextIndex    int
	successIndex int
}

var _ lockable = (*countLock)(nil)

// returns a countLock that succeeds on iteration <index>
func testLockCountingTo(index int) (lock *countLock) {
	return &countLock{nextIndex: 0, successIndex: index}
}
func (c *countLock) description() (message string) {
	return fmt.Sprintf("counter that counts from %v to %v", c.nextIndex, c.successIndex)
}
func (c *countLock) tryLock() (err error) {
	currentIndex := c.nextIndex
	c.nextIndex++
	if currentIndex == c.successIndex {
		return nil
	}
	return fmt.Errorf("Lock busy: %s", c.description())
}
func (c *countLock) Unlock() (err error) {
	if c.nextIndex == c.successIndex {
		return nil
	}
	return fmt.Errorf("Not locked: %s", c.description())
}

// end of util methods

// start of tests

// simple test
func TestGetLock(t *testing.T) {
	lockfile := lockOrFail(t)
	defer removeTestLock(lockfile)
}

// a more complicated test that spans multiple processes
var lockPathVariable = "LOCK_PATH"
var successStatus = 0
var unexpectedError = 1
var busyStatus = 2

func TestTrylock(t *testing.T) {
	lockpath := os.Getenv(lockPathVariable)
	if len(lockpath) < 1 {
		checkTrylockMainProcess(t)
	} else {
		getLockAndExit(lockpath)
	}
}

// the portion of TestTrylock that runs in the main process
func checkTrylockMainProcess(t *testing.T) {
	var err error
	lockfile := lockOrFail(t)
	defer removeTestLock(lockfile)
	lockdir := filepath.Dir(lockfile.File.Name())
	otherAcquired, message, err := forkAndGetLock(lockdir)
	if err != nil {
		t.Fatalf("Unexpected error in subprocess trying to lock uncontested fileLock: %v. Subprocess output: %q", err, message)
	}
	if !otherAcquired {
		t.Fatalf("Subprocess failed to lock uncontested fileLock. Subprocess output: %q", message)
	}

	err = lockfile.tryLock()
	if err != nil {
		t.Fatalf("Failed to lock fileLock: %v", err)
	}

	reacquired, message, err := forkAndGetLock(filepath.Dir(lockfile.File.Name()))
	if err != nil {
		t.Fatal(err)
	}
	if reacquired {
		t.Fatalf("Permitted locking fileLock twice. Subprocess output: %q", message)
	}

	err = lockfile.Unlock()
	if err != nil {
		t.Fatalf("Error unlocking fileLock: %v", err)
	}

	reacquired, message, err = forkAndGetLock(filepath.Dir(lockfile.File.Name()))
	if err != nil {
		t.Fatal(err)
	}
	if !reacquired {
		t.Fatalf("Subprocess failed to acquire lock after it was released by the main process. Subprocess output: %q", message)
	}
}
func forkAndGetLock(lockDir string) (acquired bool, subprocessOutput []byte, err error) {
	cmd := exec.Command(os.Args[0], "-test.run=TestTrylock")
	cmd.Env = append(os.Environ(), fmt.Sprintf("%s=%s", lockPathVariable, lockDir))
	subprocessOutput, err = cmd.CombinedOutput()
	exitStatus := successStatus
	if exitError, ok := err.(*exec.ExitError); ok {
		if waitStatus, ok := exitError.Sys().(syscall.WaitStatus); ok {
			exitStatus = waitStatus.ExitStatus()
		}
	}
	if exitStatus == successStatus {
		return true, subprocessOutput, nil
	} else if exitStatus == busyStatus {
		return false, subprocessOutput, nil
	} else {
		return false, subprocessOutput, fmt.Errorf("Unexpected status %v", exitStatus)
	}
}

// This function runs in a different process. See TestTrylock
func getLockAndExit(lockpath string) {
	fmt.Printf("Will lock path %q\n", lockpath)
	lockfile, err := newLock(lockpath)
	exitStatus := unexpectedError
	if err == nil {
		err = lockfile.tryLock()
		if err == nil {
			exitStatus = successStatus
		} else {
			exitStatus = busyStatus
		}
	}
	fmt.Printf("Tried to lock path %s. Received error %v. Exiting with status %v\n", lockpath, err, exitStatus)
	os.Exit(exitStatus)
}

func TestLockFirstTrySucceeds(t *testing.T) {
	noopLogger := logger.New(ioutil.Discard)
	lock := testLockCountingTo(0)
	waiter := newCountWaiter(0)
	err := lockSynchronous(lock, waiter, noopLogger)
	if err != nil {
		t.Fatal(err)
	}
	if waiter.numWaitsElapsed != 0 {
		t.Fatalf("Incorrect number of waits elapsed; expected 0, got %v", waiter.numWaitsElapsed)
	}
}
func TestLockThirdTrySucceeds(t *testing.T) {
	noopLogger := logger.New(ioutil.Discard)
	lock := testLockCountingTo(2)
	waiter := newCountWaiter(2)
	err := lockSynchronous(lock, waiter, noopLogger)
	if err != nil {
		t.Fatal(err)
	}
	if waiter.numWaitsElapsed != 2 {
		t.Fatalf("Incorrect number of waits elapsed; expected 2, got %v", waiter.numWaitsElapsed)
	}
}
func TestLockTimedOut(t *testing.T) {
	noopLogger := logger.New(ioutil.Discard)
	lock := testLockCountingTo(3)
	waiter := newCountWaiter(2)
	err := lockSynchronous(lock, waiter, noopLogger)
	if err == nil {
		t.Fatalf("Appeared to have acquired lock on iteration %v which should not be available until iteration %v", waiter.numWaitsElapsed, lock.successIndex)
	}
	if waiter.numWaitsElapsed != waiter.maxNumWaits {
		t.Fatalf("Waited an incorrect number of times; expected %v, got %v", waiter.maxNumWaits, waiter.numWaitsElapsed)
	}
}
