/*
 *  ******************************************************************************
 *  *
 *  *
 *  * This program and the accompanying materials are made available under the
 *  * terms of the Apache License, Version 2.0 which is available at
 *  * https://www.apache.org/licenses/LICENSE-2.0.
 *  *
 *  *  See the NOTICE file distributed with this work for additional
 *  *  information regarding copyright ownership.
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations
 *  * under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *  *****************************************************************************
 */

package org.nd4j.linalg.lossfunctions.impl;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.nd4j.common.base.Preconditions;
import org.nd4j.linalg.activations.IActivation;
import org.nd4j.linalg.activations.impl.ActivationSoftmax;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.CustomOp;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.impl.transforms.custom.SoftMax;
import org.nd4j.linalg.api.ops.impl.transforms.same.TimesOneMinus;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.ILossFunction;
import org.nd4j.linalg.lossfunctions.LossUtil;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.common.primitives.Pair;
import org.nd4j.serde.jackson.shaded.NDArrayTextDeSerializer;
import org.nd4j.serde.jackson.shaded.NDArrayTextSerializer;
import org.nd4j.shade.jackson.annotation.JsonInclude;
import org.nd4j.shade.jackson.annotation.JsonProperty;
import org.nd4j.shade.jackson.databind.annotation.JsonDeserialize;
import org.nd4j.shade.jackson.databind.annotation.JsonSerialize;

@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter @Setter
public class LossBinaryXENT implements ILossFunction {
    public static final double DEFAULT_CLIPPING_EPSILON = 1e-5;

    @JsonSerialize(using = NDArrayTextSerializer.class)
    @JsonDeserialize(using = NDArrayTextDeSerializer.class)
    private final INDArray weights;

    private double clipEps;

    public LossBinaryXENT() {
        this(null);
    }

    /**
     * Binary cross entropy where each the output is
     * (optionally) weighted/scaled by a fixed scalar value.
     * Note that the weights array must be a row vector, of length equal to
     * the labels/output dimension 1 size.
     * A weight vector of 1s should give identical results to no weight vector.
     *
     * @param weights Weights array (row vector). May be null.
     */
    public LossBinaryXENT(INDArray weights) {
        this(DEFAULT_CLIPPING_EPSILON, weights);
    }

    /**
     * Binary cross entropy where each the output is
     * (optionally) weighted/scaled by a fixed scalar value.
     * Note that the weights array must be a row vector, of length equal to
     * the labels/output dimension 1 size.
     * A weight vector of 1s should give identical results to no weight vector.
     *
     * @param clipEps Epsilon value for clipping. Probabilities are clipped in range of [eps, 1-eps]. Default eps: 1e-5
     */
    public LossBinaryXENT(double clipEps){
        this(clipEps, null);
    }

    /**
     * Binary cross entropy where each the output is
     * (optionally) weighted/scaled by a fixed scalar value.
     * Note that the weights array must be a row vector, of length equal to
     * the labels/output dimension 1 size.
     * A weight vector of 1s should give identical results to no weight vector.
     *
     * @param clipEps Epsilon value for clipping. Probabilities are clipped in range of [eps, 1-eps]. Default eps: 1e-5
     * @param weights Weights array (row vector). May be null.
     */
    public LossBinaryXENT(@JsonProperty("clipEps") double clipEps, @JsonProperty("weights") INDArray weights) {
        if (weights != null && !weights.isRowVectorOrScalar()) {
            throw new IllegalArgumentException("Weights array must be a row vector");
        }
        if(clipEps < 0 || clipEps > 0.5){
            throw new IllegalArgumentException("Invalid clipping epsilon value: epsilon should be >= 0 (but near zero)."
                    + "Got: " + clipEps);
        }

        this.clipEps = clipEps;
        this.weights = weights;
    }

    private INDArray scoreArray(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {
        if(!labels.equalShapes(preOutput)) {
            Preconditions.throwEx("Labels and preOutput must have equal shapes: got shapes %s vs %s", labels.shape(), preOutput.shape());
        }
        labels = labels.castTo(preOutput.dataType());   //No-op if already correct dtype

        INDArray scoreArr;
        if (activationFn instanceof ActivationSoftmax) {
            //TODO Post GPU support for custom ops: Use LogSoftMax op to avoid numerical issues when calculating score
            INDArray logsoftmax = Nd4j.exec((CustomOp) new SoftMax(preOutput, preOutput.ulike(), -1))[0];
            Transforms.log(logsoftmax, false);
            scoreArr = logsoftmax.muli(labels);

        } else {
            INDArray output = activationFn.getActivation(preOutput.dup(), true);
            if (clipEps > 0.0) {
                CustomOp op = DynamicCustomOp.builder("clipbyvalue")
                        .addInputs(output)
                        .callInplace(true)
                        .addFloatingPointArguments(clipEps, 1.0-clipEps)
                        .build();
                Nd4j.getExecutioner().execAndReturn(op);
            }

            INDArray logOut = Transforms.log(output,true);
            scoreArr = logOut.muli(labels);
            INDArray secondTerm = output.rsubi(1);
            Transforms.log(secondTerm, false);
            secondTerm.muli(labels.rsub(1));
            scoreArr.addi(secondTerm);
        }

        //Weighted loss function
        if (weights != null) {
            if (weights.length() != preOutput.size(1)) {
                throw new IllegalStateException("Weights vector (length " + weights.length()
                                + ") does not match output.size(1)=" + preOutput.size(1));
            }

            scoreArr.muliRowVector(weights.castTo(scoreArr.dataType()));
        }

        if (mask != null) {
            LossUtil.applyMask(scoreArr, mask);
        }
        return scoreArr;
    }

    @Override
    public double computeScore(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask,
                    boolean average) {

        INDArray scoreArr = scoreArray(labels, preOutput, activationFn, mask);

        double score = -scoreArr.sumNumber().doubleValue();

        if (average) {
            score /= scoreArr.size(0);
        }

        return score;
    }

    @Override
    public INDArray computeScoreArray(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {

        INDArray scoreArr = scoreArray(labels, preOutput, activationFn, mask);
        return scoreArr.sum(true,1).muli(-1);
    }

    @Override
    public INDArray computeGradient(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {
        if(!labels.equalShapes(preOutput)){
            Preconditions.throwEx("Labels and preOutput must have equal shapes: got shapes %s vs %s", labels.shape(), preOutput.shape());
        }
        labels = labels.castTo(preOutput.dataType());   //No-op if already correct dtype

        INDArray output = activationFn.getActivation(preOutput.dup(), true);
        if (clipEps > 0.0) {
            CustomOp op = DynamicCustomOp.builder("clipbyvalue")
                    .addInputs(output)
                    .callInplace(true)
                    .addFloatingPointArguments(clipEps, 1.0-clipEps)
                    .build();
            Nd4j.getExecutioner().execAndReturn(op);
        }

        INDArray numerator = output.sub(labels);
        INDArray denominator = Nd4j.getExecutioner().exec(new TimesOneMinus(output)); // output * (1-output)
        INDArray dLda = numerator.divi(denominator);

        if (mask != null && LossUtil.isPerOutputMasking(dLda, mask)) {
            //For *most* activation functions: we don't actually need to mask dL/da in addition to masking dL/dz later
            //but: some, like softmax, require both (due to dL/dz_i being a function of dL/da_j, for i != j)
            //We could add a special case for softmax (activationFn instanceof ActivationSoftmax) but that would be
            // error prone - but buy us a tiny bit of performance
            LossUtil.applyMask(dLda, mask);
        }

        INDArray grad = activationFn.backprop(preOutput, dLda).getFirst(); //TODO activation functions with weights

        //Weighted loss function
        if (weights != null) {
            if (weights.length() != output.size(1)) {
                throw new IllegalStateException("Weights vector (length " + weights.length()
                                + ") does not match output.size(1)=" + output.size(1));
            }

            grad.muliRowVector(weights.castTo(grad.dataType()));
        }

        if (mask != null) {
            LossUtil.applyMask(grad, mask);
        }

        return grad;
    }

    @Override
    public Pair<Double, INDArray> computeGradientAndScore(INDArray labels, INDArray preOutput, IActivation activationFn,
                    INDArray mask, boolean average) {
        //TODO: probably a more efficient way to do this...

        return new Pair<>(computeScore(labels, preOutput, activationFn, mask, average),
                        computeGradient(labels, preOutput, activationFn, mask));
    }

    /**
     * The opName of this function
     *
     * @return
     */
    @Override
    public String name() {
        return toString();
    }

    @Override
    public String toString() {
        if (weights == null)
            return "LossBinaryXENT()";
        return "LossBinaryXENT(weights=" + weights + ")";
    }
}
