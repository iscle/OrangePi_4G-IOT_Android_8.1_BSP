// Generated file (from: hashtable_lookup_quant8.mod.py). Do not edit
// Begin of an example
{
//Input(s)
{ // See tools/test_generator/include/TestHarness.h:MixedTyped
  // int -> FLOAT32 map
  {},
  // int -> INT32 map
  {{0, {123, 250, 255, 0}}, {1, {0, 123, 255}}},
  // int -> QUANT8_ASYMM map
  {{2, {0, 1, 10, 11, 20, 21}}}
},
//Output(s)
{ // See tools/test_generator/include/TestHarness.h:MixedTyped
  // int -> FLOAT32 map
  {},
  // int -> INT32 map
  {},
  // int -> QUANT8_ASYMM map
  {{0, {10, 11, 0, 0, 20, 21, 0, 1}}, {1, {1, 0, 1, 1}}}
}
}, // End of an example
