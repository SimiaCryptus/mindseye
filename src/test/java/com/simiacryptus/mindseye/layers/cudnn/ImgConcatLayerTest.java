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

package com.simiacryptus.mindseye.layers.cudnn;

import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.layers.LayerTestBase;

/**
 * The type Img concat layer run.
 */
public abstract class ImgConcatLayerTest extends CudnnLayerTestBase {
  
  public static class Double extends ImgConcatLayerTest {
    public Double() {
      super(Precision.Double);
    }
  }
  
  public static class Float extends ImgConcatLayerTest {
    public Float() {
      super(Precision.Float);
    }
  }
  
  final Precision precision;
  
  public ImgConcatLayerTest(Precision precision) {
    this.precision = precision;
  }
  
  @Override
  public NNLayer getLayer() {
    return new ImgConcatLayer();
  }
  
  @Override
  public int[][] getInputDims() {
    return new int[][]{
      {2, 2, 1}, {2, 2, 1}
    };
  }
  
  /**
   * The type Band limit run.
   */
  public static class BandLimitTest extends ImgConcatLayerTest {
  
    public BandLimitTest() {
      super(Precision.Double);
    }
  
    @Override
    public NNLayer getLayer() {
      return new ImgConcatLayer().setMaxBands(3);
    }
    
    @Override
    public int[][] getInputDims() {
      return new int[][]{
        {2, 2, 2}, {2, 2, 2}
      };
    }
  }
  
}
