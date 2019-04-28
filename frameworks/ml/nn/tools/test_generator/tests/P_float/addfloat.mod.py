# model
i1 = Input("operand1","TENSOR_FLOAT32", "{3,4}")
i2 = Input("operand2","TENSOR_FLOAT32", "{3,4}")
i3 = Input("operand3","TENSOR_FLOAT32", "{3,4}")
o = Output("operand4","TENSOR_FLOAT32", "{3,4}")

Model().Add(i1, i2).Add(i3).Out(o)

