// Generated file (from: depth_to_space_float_1.mod.py). Do not edit
void CreateModel(Model *model) {
  OperandType type1(Type::INT32, {});
  OperandType type0(Type::TENSOR_FLOAT32, {1, 1, 1, 8});
  OperandType type2(Type::TENSOR_FLOAT32, {1, 2, 2, 2});
  // Phase 1, operands
  auto input = model->addOperand(&type0);
  auto block_size = model->addOperand(&type1);
  auto output = model->addOperand(&type2);
  // Phase 2, operations
  static int32_t block_size_init[] = {2};
  model->setOperandValue(block_size, block_size_init, sizeof(int32_t) * 1);
  model->addOperation(ANEURALNETWORKS_DEPTH_TO_SPACE, {input, block_size}, {output});
  // Phase 3, inputs and outputs
  model->identifyInputsAndOutputs(
    {input},
    {output});
  assert(model->isValid());
}

bool is_ignored(int i) {
  static std::set<int> ignore = {};
  return ignore.find(i) != ignore.end();
}
