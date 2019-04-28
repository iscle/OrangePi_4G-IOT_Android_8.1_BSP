package android.tests.binder;

interface IBenchmark {
  byte[] sendVec(in byte[] data);
}