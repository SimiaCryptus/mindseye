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

package com.simiacryptus.mindseye.layers.cudnn.f32;

import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.layers.aparapi.ConvolutionLayer;

/**
 * The type Convolution layer run.
 */
public class SimpleConvolutionLayerTest extends F32LayerTestBase {
  
  final int radius;
  final int bands;
  
  /**
   * The Convolution layer.
   */
  SimpleConvolutionLayer convolutionLayer;
  
  /**
   * Instantiates a new Simple convolution layer test.
   */
  public SimpleConvolutionLayerTest() {
    this(1,1);
  }
  
  protected SimpleConvolutionLayerTest(int radius, int bands) {
    this.radius = radius;
    this.bands = bands;
    convolutionLayer = new SimpleConvolutionLayer(radius, radius, bands*bands);
    convolutionLayer.filter.fill(() -> random());
  }
  
  @Override
  public NNLayer getLayer() {
    return convolutionLayer;
  }
  
  @Override
  public NNLayer getReferenceLayer() {
    ConvolutionLayer referenceLayer = new ConvolutionLayer(radius, radius, bands, bands, true);
    referenceLayer.kernel.set(convolutionLayer.filter);
    return referenceLayer;
  }
  
  @Override
  public int[][] getInputDims() {
    return new int[][]{
      {radius, radius, bands}
    };
  }
  
  public static class MultiBand extends com.simiacryptus.mindseye.layers.cudnn.f64.SimpleConvolutionLayerTest {
    public MultiBand() {
      super(1,2);
    }
  }
  
}