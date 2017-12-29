/*
 * Copyright (c) 2017 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.deeplearning4j.nn.modelimport.keras.preprocessors;

import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.inputs.InvalidInputTypeException;
import org.deeplearning4j.nn.conf.preprocessor.BaseInputPreProcessor;
import org.nd4j.linalg.api.ndarray.INDArray;

/**
 * Preprocessor to flatten input of RNN type
 *
 * @author Max Pumperla
 */
public class KerasFlattenRnnPreprocessor extends BaseInputPreProcessor {
  
  int tsLength;
  int depth;
  
  public KerasFlattenRnnPreprocessor(int depth, int tsLength) {
    super();
    this.tsLength = Math.abs(tsLength);
    this.depth = depth;
  }
  
  @Override
  public INDArray preProcess(INDArray input, int miniBatchSize) {
    INDArray output = input.dup('c');
    output.reshape(input.size(0), depth * tsLength);
    return output;
  }
  
  @Override
  public INDArray backprop(INDArray epsilons, int miniBatchSize) {
    return epsilons.dup().reshape(miniBatchSize, depth, tsLength);
  }
  
  @Override
  public KerasFlattenRnnPreprocessor clone() {
    try {
      return (KerasFlattenRnnPreprocessor) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException();
    }
  }
  
  @Override
  public InputType getOutputType(InputType inputType) throws InvalidInputTypeException {
    
    return InputType.feedForward(depth * tsLength);
    
  }
}
