# Force VTS mode
Configuration.vts = True
# model
model = Model()
i0 = Input("operand0","TENSOR_FLOAT32", "{1, 2, 2, 1}")
b0 = Int32Scalar("b0", 0)
p0 = Parameter("p0", "TENSOR_FLOAT32", "{1, 2, 2, 1}", [5.0, 6.0, 7.0, 8.0])
o = Output("out","TENSOR_FLOAT32", "{1, 2, 2, 1}")

model.Operation("ADD", i0, p0, b0).To(o)

input0 = {i0: # input 0
          [1.0, 2.0, 3.0, 4.0]}

output0 = {o: # output 0
           [6.0, 8.0, 10.0, 12.0]}

# Instantiate an example
Example((input0, output0))
