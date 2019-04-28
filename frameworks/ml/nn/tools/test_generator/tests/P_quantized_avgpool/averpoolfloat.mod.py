# model
model = Model()
i1 = Input("op1", "TENSOR_QUANT8_ASYMM", "0.0f, 127.5f, {1, 2, 2, 1}")
cons1 = Int32Scalar("cons1", 1)
act = Int32Scalar("act", 0)
o = Output("op3", "TENSOR_QUANT8_ASYMM", "0.0f, 127.5f, {1, 2, 2, 1}")
model = model.Operation("AVERAGE_POOL", i1, cons1, cons1, cons1, cons1, cons1, act).To(o)

# Example 1. Input in operand 0,
input0 = {i1: # input 0
          [1, 2, 3, 4]}

output0 = {o: # output 0
          [1, 2, 3, 4]}

# Instantiate an example
Example((input0, output0))



