i1 = Input("op1", "TENSOR_FLOAT32", "{1, 8, 8, 3}") # input 0
i2 = Output("op2", "TENSOR_FLOAT32", "{1, 8, 8, 3}") # output 0
i0 = Internal("op0", "TENSOR_FLOAT32", "{1, 8, 8, 3}") # intermediate result
model = Model()
model = model.RawAdd(i1, i1).To(i0)
model = model.RawAdd(i0, i1).To(i2)

