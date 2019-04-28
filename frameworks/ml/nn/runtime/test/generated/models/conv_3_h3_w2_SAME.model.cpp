void CreateModel(Model *model) {
  OperandType type0(Type::INT32, {1});
  OperandType type1(Type::TENSOR_FLOAT32, {1, 8, 8, 3});
  OperandType type2(Type::TENSOR_FLOAT32, {3, 3, 2, 3});
  OperandType type3(Type::TENSOR_FLOAT32, {3});
  // Phase 1, operands
  auto pad0 = model->addOperand(&type0);
  auto pad1 = model->addOperand(&type0);
  auto b5 = model->addOperand(&type0);
  auto b6 = model->addOperand(&type0);
  auto b7 = model->addOperand(&type0);
  auto op2 = model->addOperand(&type1);
  auto op3 = model->addOperand(&type1);
  auto op0 = model->addOperand(&type2);
  auto op1 = model->addOperand(&type3);
  // Phase 2, operations
  int32_t pad0_init[] = {0};
  model->setOperandValue(pad0, pad0_init, sizeof(int32_t) * 1);
  int32_t pad1_init[] = {1};
  model->setOperandValue(pad1, pad1_init, sizeof(int32_t) * 1);
  int32_t b5_init[] = {1};
  model->setOperandValue(b5, b5_init, sizeof(int32_t) * 1);
  int32_t b6_init[] = {1};
  model->setOperandValue(b6, b6_init, sizeof(int32_t) * 1);
  int32_t b7_init[] = {0};
  model->setOperandValue(b7, b7_init, sizeof(int32_t) * 1);
  static float op0_init[] = {-0.966213, -0.579455, -0.684259, 0.738216, 0.184325, 0.0973683, -0.176863, -0.23936, -0.000233404, 0.055546, -0.232658, -0.316404, -0.012904, 0.320705, -0.326657, -0.919674, 0.868081, -0.824608, -0.467474, 0.0278809, 0.563238, 0.386045, -0.270568, -0.941308, -0.779227, -0.261492, -0.774804, -0.79665, 0.22473, -0.414312, 0.685897, -0.327792, 0.77395, -0.714578, -0.972365, 0.0696099, -0.82203, -0.79946, 0.37289, -0.917775, 0.82236, -0.144706, -0.167188, 0.268062, 0.702641, -0.412223, 0.755759, 0.721547, -0.43637, -0.274905, -0.269165, 0.16102, 0.819857, -0.312008};
  model->setOperandValue(op0, op0_init, sizeof(float) * 54);
  float op1_init[] = {0, 0, 0};
  model->setOperandValue(op1, op1_init, sizeof(float) * 3);
  model->addOperation(ANEURALNETWORKS_CONV_2D, {op2, op0, op1, pad0, pad1, pad1, pad1, b5, b6, b7}, {op3});
  // Phase 3, inputs and outputs
  model->identifyInputsAndOutputs(
    {op2},
    {op3});
  assert(model->isValid());
}
