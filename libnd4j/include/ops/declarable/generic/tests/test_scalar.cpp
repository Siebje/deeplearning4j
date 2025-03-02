/* ******************************************************************************
 *
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 *  See the NOTICE file distributed with this work for additional
 *  information regarding copyright ownership.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

//
// Created by raver119 on 24.02.18.
//

#include <system/op_boilerplate.h>
#if NOT_EXCLUDED(OP_test_scalar)

#include <ops/declarable/headers/tests.h>

namespace sd {
namespace ops {
CUSTOM_OP_IMPL(test_scalar, 1, 1, false, 0, 0) {
  auto input = INPUT_VARIABLE(0);
  auto output = OUTPUT_VARIABLE(0);

  double val = input->e<double>(0) + 2.0;
  output->p(0, val);

  return Status::OK;
}

DECLARE_SHAPE_FN(test_scalar) {
  LongType *newShape;
  ALLOCATE(newShape, block.workspace(), shape::shapeInfoLength(2), sd::LongType);

  newShape[0] = 2;
  newShape[1] = 1;
  newShape[2] = 1;
  newShape[3] = 1;
  newShape[4] = 1;
  newShape[5] = 0;
  newShape[6] = 1;
  newShape[7] = 99;

  ArrayOptions::setDataType(newShape, ArrayOptions::dataType(inputShape->at(0)));
  auto desc = new ShapeDescriptor(newShape, false);
  auto shape = ConstantShapeHelper::getInstance().createShapeInfo(desc);
  RELEASE(newShape, block.getWorkspace());
  if (Environment::getInstance().isDeleteShapeInfo()) delete desc;
  return SHAPELIST(shape);
}

DECLARE_TYPES(test_scalar) { getOpDescriptor()->setAllowedInputTypes(ANY)->setSameMode(true); }
}  // namespace ops
}  // namespace sd

#endif
