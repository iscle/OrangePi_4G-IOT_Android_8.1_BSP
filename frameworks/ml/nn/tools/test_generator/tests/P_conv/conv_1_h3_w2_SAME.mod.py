i4 = Int32Scalar("b4", 1)
i5 = Int32Scalar("b5", 1)
i6 = Int32Scalar("b6", 1)
i7 = Int32Scalar("b7", 0)
i2 = Input("op2", "TENSOR_FLOAT32", "{1, 8, 8, 3}") # input 0
i3 = Output("op3", "TENSOR_FLOAT32", "{1, 8, 8, 1}") # output 0
i0 = Parameter("op0", "TENSOR_FLOAT32", "{1, 3, 2, 3}", [-0.966213, -0.467474, -0.82203, -0.579455, 0.0278809, -0.79946, -0.684259, 0.563238, 0.37289, 0.738216, 0.386045, -0.917775, 0.184325, -0.270568, 0.82236, 0.0973683, -0.941308, -0.144706]) # parameters
i1 = Parameter("op1", "TENSOR_FLOAT32", "{1}", [0]) # parameters
model = Model()
model = model.Conv(i2, i0, i1, i4, i5, i6, i7).To(i3)

