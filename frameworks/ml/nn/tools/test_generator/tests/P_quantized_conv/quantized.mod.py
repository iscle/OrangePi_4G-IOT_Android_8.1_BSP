i4 = Int32Scalar("b4", 2)
i5 = Int32Scalar("b5", 2)
i6 = Int32Scalar("b6", 2)
i7 = Int32Scalar("b7", 0)
i2 = Input("op2", "TENSOR_QUANT8_ASYMM", "{1, 2, 2, 1}") # input 0
i3 = Output("op3", "TENSOR_QUANT8_ASYMM", "{1, 1, 1, 1}") # output 0
i0 = Parameter("op0", "TENSOR_QUANT8_ASYMM", "{1, 2, 2, 1}", [1, 1, 1, 1]) # parameters
i1 = Parameter("op1", "TENSOR_INT32", "{1}", [0]) # parameters
model = Model()
model = model.Conv(i2, i0, i1, i4, i5, i6, i7).To(i3)

