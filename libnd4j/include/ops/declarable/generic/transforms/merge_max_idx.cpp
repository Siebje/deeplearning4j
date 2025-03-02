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
// Created by raver119 on 24.11.17.
//

#include <system/op_boilerplate.h>
#if NOT_EXCLUDED(OP_mergemaxindex)

#include <ops/declarable/CustomOperations.h>
#include <ops/declarable/helpers/transforms.h>

namespace sd {
namespace ops {

CUSTOM_OP_IMPL(mergemaxindex, -1, 1, false, 0, 0) {
  REQUIRE_OK(this->validateInputDimensionsMatch(block));
  auto output = OUTPUT_VARIABLE(0);

  std::vector<NDArray*> inArrs(block.width());

  for (int i = 0; i < block.width(); ++i) inArrs[i] = INPUT_VARIABLE(i);

  helpers::mergeMaxIndex(block.launchContext(), inArrs, *output);

  return Status::OK;
}

DECLARE_SYN(MergeMaxIndex, mergemaxindex);

DECLARE_TYPES(mergemaxindex) {
  getOpDescriptor()->setAllowedInputTypes({ALL_INTS, ALL_FLOATS})->setAllowedOutputTypes({ALL_INDICES});
}
}  // namespace ops
DECLARE_SHAPE_FN(mergemaxindex) {
  auto in = inputShape->at(0);
  auto dtype = INT32;
  if (block.getIArguments()->size() > 0) dtype = (DataType)INT_ARG(0);

  auto resShape = ShapeBuilders::copyShapeInfoAndType(in, dtype, block.workspace());
  return SHAPELIST(CONSTANT(resShape));
}
}  // namespace sd

#endif
