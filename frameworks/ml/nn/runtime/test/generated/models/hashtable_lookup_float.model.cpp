// Generated file (from: hashtable_lookup_float.mod.py). Do not edit
void CreateModel(Model *model) {
  OperandType type2(Type::TENSOR_FLOAT32, {3, 2});
  OperandType type3(Type::TENSOR_FLOAT32, {4, 2});
  OperandType type1(Type::TENSOR_INT32, {3});
  OperandType type0(Type::TENSOR_INT32, {4});
  OperandType type4(Type::TENSOR_QUANT8_ASYMM, {4}, 1.f, 0);
  // Phase 1, operands
  auto lookup = model->addOperand(&type0);
  auto key = model->addOperand(&type1);
  auto value = model->addOperand(&type2);
  auto output = model->addOperand(&type3);
  auto hits = model->addOperand(&type4);
  // Phase 2, operations
  model->addOperation(ANEURALNETWORKS_HASHTABLE_LOOKUP, {lookup, key, value}, {output, hits});
  // Phase 3, inputs and outputs
  model->identifyInputsAndOutputs(
    {lookup, key, value},
    {output, hits});
  assert(model->isValid());
}

bool is_ignored(int i) {
  static std::set<int> ignore = {};
  return ignore.find(i) != ignore.end();
}
