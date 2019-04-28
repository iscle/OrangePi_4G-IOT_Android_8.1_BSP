#!/bin/bash

javah -jni -classpath ../../bin/classes:../../../../../../prebuilts/sdk/current/android.jar -o tunertvinput_jni.h com.android.tv.tuner.TunerHal
