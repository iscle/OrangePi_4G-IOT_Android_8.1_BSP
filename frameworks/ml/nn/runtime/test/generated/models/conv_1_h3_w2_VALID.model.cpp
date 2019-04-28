void CreateModel(Model *model) {
  OperandType type0(Type::INT32, {1});
  OperandType type3(Type::TENSOR_FLOAT32, {1, 3, 2, 3});
  OperandType type2(Type::TENSOR_FLOAT32, {1, 6, 7, 1});
  OperandType type1(Type::TENSOR_FLOAT32, {1, 8, 8, 3});
  OperandType type4(Type::TENSOR_FLOAT32, {1});
  // Phase 1, operands
  auto pad0 = model->addOperand(&type0);
  auto b5 = model->addOperand(&type0);
  auto b6 = model->addOperand(&type0);
  auto b7 = model->addOperand(&type0);
  auto op2 = model->addOperand(&type1);
  auto op3 = model->addOperand(&type2);
  auto op0 = model->addOperand(&type3);
  auto op1 = model->addOperand(&type4);
  // Phase 2, operations
  int32_t pad0_init[] = {0};
  model->setOperandValue(pad0, pad0_init, sizeof(int32_t) * 1);
  int32_t b5_init[] = {1};
  model->setOperandValue(b5, b5_init, sizeof(int32_t) * 1);
  int32_t b6_init[] = {1};
  model->setOperandValue(b6, b6_init, sizeof(int32_t) * 1);
  int32_t b7_init[] = {0};
  model->setOperandValue(b7, b7_init, sizeof(int32_t) * 1);
  float op0_init[] = {-0.966213, -0.467474, -0.82203, -0.579455, 0.0278809, -0.79946, -0.684259, 0.563238, 0.37289, 0.738216, 0.386045, -0.917775, 0.184325, -0.270568, 0.82236, 0.0973683, -0.941308, -0.144706};
  model->setOperandValue(op0, op0_init, sizeof(float) * 18);
  float op1_init[] = {0};
  model->setOperandValue(op1, op1_init, sizeof(float) * 1);
  model->addOperation(ANEURALNETWORKS_CONV_2D, {op2, op0, op1, pad0, pad0, pad0, pad0, b5, b6, b7}, {op3});
  // Phase 3, inputs and outputs
  model->identifyInputsAndOutputs(
    {op2},
    {op3});
  assert(model->isValid());
}
