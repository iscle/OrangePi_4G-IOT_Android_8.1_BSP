void CreateModel(Model *model) {
  OperandType type0(Type::INT32, {1});
  OperandType type2(Type::TENSOR_FLOAT32, {1, 1, 1, 3});
  OperandType type1(Type::TENSOR_FLOAT32, {1, 8, 8, 3});
  OperandType type3(Type::TENSOR_FLOAT32, {3});
  // Phase 1, operands
  auto pad0 = model->addOperand(&type0);
  auto b5 = model->addOperand(&type0);
  auto b6 = model->addOperand(&type0);
  auto b7 = model->addOperand(&type0);
  auto b8 = model->addOperand(&type0);
  auto op2 = model->addOperand(&type1);
  auto op3 = model->addOperand(&type1);
  auto op0 = model->addOperand(&type2);
  auto op1 = model->addOperand(&type3);
  // Phase 2, operations
  int32_t pad0_init[] = {0};
  model->setOperandValue(pad0, pad0_init, sizeof(int32_t) * 1);
  int32_t b5_init[] = {1};
  model->setOperandValue(b5, b5_init, sizeof(int32_t) * 1);
  int32_t b6_init[] = {1};
  model->setOperandValue(b6, b6_init, sizeof(int32_t) * 1);
  int32_t b7_init[] = {1};
  model->setOperandValue(b7, b7_init, sizeof(int32_t) * 1);
  int32_t b8_init[] = {0};
  model->setOperandValue(b8, b8_init, sizeof(int32_t) * 1);
  float op0_init[] = {-0.966213, -0.467474, -0.82203};
  model->setOperandValue(op0, op0_init, sizeof(float) * 3);
  float op1_init[] = {0, 0, 0};
  model->setOperandValue(op1, op1_init, sizeof(float) * 3);
  model->addOperation(ANEURALNETWORKS_DEPTHWISE_CONV_2D, {op2, op0, op1, pad0, pad0, pad0, pad0, b5, b6, b7, b8}, {op3});
  // Phase 3, inputs and outputs
  model->identifyInputsAndOutputs(
    {op2},
    {op3});
  assert(model->isValid());
}
