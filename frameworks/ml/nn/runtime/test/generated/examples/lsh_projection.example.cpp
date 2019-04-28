// Generated file (from: lsh_projection.mod.py). Do not edit
// Begin of an example
{
//Input(s)
{ // See tools/test_generator/include/TestHarness.h:MixedTyped
  // int -> FLOAT32 map
  {{1, {0.12f, 0.34f, 0.56f}}},
  // int -> INT32 map
  {{0, {12345, 54321, 67890, 9876, -12345678, -87654321}}},
  // int -> QUANT8_ASYMM map
  {}
},
//Output(s)
{ // See tools/test_generator/include/TestHarness.h:MixedTyped
  // int -> FLOAT32 map
  {},
  // int -> INT32 map
  {{0, {1, 1, 1, 0, 1, 1, 1, 0}}},
  // int -> QUANT8_ASYMM map
  {}
}
}, // End of an example
