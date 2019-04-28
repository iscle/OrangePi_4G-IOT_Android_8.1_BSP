// Generated file (from: conv_quant8_channels_weights_as_inputs.mod.py). Do not edit
// Begin of an example
{
//Input(s)
{ // See tools/test_generator/include/TestHarness.h:MixedTyped
  // int -> FLOAT32 map
  {},
  // int -> INT32 map
  {{2, {0, 0, 0}}},
  // int -> QUANT8_ASYMM map
  {{0, {10, 10, 10}}, {1, {1, 2, 3, 4, 5, 6, 7, 8, 9}}}
},
//Output(s)
{ // See tools/test_generator/include/TestHarness.h:MixedTyped
  // int -> FLOAT32 map
  {},
  // int -> INT32 map
  {},
  // int -> QUANT8_ASYMM map
  {{0, {15, 38, 60}}}
}
}, // End of an example
