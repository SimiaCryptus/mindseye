/*
 * Copyright (c) 2018 by Andrew Charneski.
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

package com.simiacryptus.mindseye.layers.cudnn;

import com.simiacryptus.mindseye.lang.Layer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;

/**
 * The type Rascaled subnet layer apply.
 */
public abstract class ImgLinearSubnetLayerTest extends CudaLayerTestBase {
  
  private final ConvolutionLayer convolutionLayer1 = new ConvolutionLayer(3, 3, 1, 1).set(() -> this.random());
  private final ConvolutionLayer convolutionLayer2 = new ConvolutionLayer(3, 3, 1, 1).set(() -> this.random());
  private final ConvolutionLayer convolutionLayer3 = new ConvolutionLayer(3, 3, 1, 1).set(() -> this.random());
  
  @Nonnull
  @Override
  public int[][] getSmallDims(Random random) {
    return new int[][]{
      {3, 3, 3}
    };
  }
  
  @Nonnull
  @Override
  public Layer getLayer(final int[][] inputSize, Random random) {
    return new ImgLinearSubnetLayer()
      .add(0, 1, convolutionLayer1)
      .add(1, 2, convolutionLayer2)
      .add(2, 3, convolutionLayer3);
  }
  
  @Nullable
  @Override
  public Class<? extends Layer> getReferenceLayerClass() {
    return null;
  }
  
  /**
   * Basic Test
   */
  public static class Basic extends ImgLinearSubnetLayerTest {
    
    @Nullable
    @Override
    public Class<? extends Layer> getReferenceLayerClass() {
      return null;
    }
    
  }
  
}
