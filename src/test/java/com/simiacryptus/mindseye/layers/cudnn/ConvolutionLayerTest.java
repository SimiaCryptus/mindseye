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

import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.layers.cudnn.lang.CuDNN;
import com.simiacryptus.mindseye.layers.cudnn.lang.Precision;
import com.simiacryptus.mindseye.test.ToleranceStatistics;
import com.simiacryptus.mindseye.test.unit.ComponentTest;
import com.simiacryptus.mindseye.test.unit.SingleDerivativeTester;
import com.simiacryptus.util.io.NotebookOutput;
import org.junit.Assert;
import org.junit.Test;

import java.io.PrintStream;
import java.util.Random;

/**
 * The type Convolution layer apply.
 */
public abstract class ConvolutionLayerTest extends CudnnLayerTestBase {
  
  /**
   * The Input bands.
   */
  final int inputBands;
  /**
   * The Output bands.
   */
  final int outputBands;
  /**
   * The Radius.
   */
  final int radius;
  /**
   * The Convolution layer.
   */
  ConvolutionLayer convolutionLayer;
  
  @Override
  public void run(NotebookOutput log) {
    String logName = "cuda_" + log.getName() + "_all.log";
    log.p(log.file((String) null, logName, "GPU Log"));
    PrintStream apiLog = new PrintStream(log.file(logName));
    CuDNN.addLog(apiLog);
    super.run(log);
    apiLog.close();
    CuDNN.apiLog.remove(apiLog);
  }
  
  /**
   * Instantiates a new Convolution layer apply.
   *
   * @param radius      the radius
   * @param inputBands  the input bands
   * @param outputBands the output bands
   * @param precision   the precision
   * @param batchBands  the batch bands
   */
  protected ConvolutionLayerTest(final int radius, final int inputBands, final int outputBands, final Precision precision, int batchBands) {
    this.radius = radius;
    this.inputBands = inputBands;
    this.outputBands = outputBands;
    convolutionLayer = new ConvolutionLayer(radius, radius, inputBands, outputBands).setPrecision(precision).setBatchBands(batchBands);
    Random random = getRandom();
    convolutionLayer.getKernel().set(() -> {
      return random(random);
    });
  }
  
  /**
   * Verify weights.
   */
  @Test
  public void verifyWeights() {
    ExplodedConvolutionGrid explodedNetwork = this.convolutionLayer.getExplodedNetwork();
    int[] kernelDims = this.convolutionLayer.getKernel().getDimensions();
    Tensor testData = new Tensor(kernelDims).map(x -> random());
    explodedNetwork.write(testData);
    Tensor echo = explodedNetwork.read();
    Assert.assertEquals(testData, echo);
  }
  
  @Override
  public int[][] getInputDims(Random random) {
    return new int[][]{
      {3, 3, inputBands}
    };
  }
  
  @Override
  public NNLayer getLayer(final int[][] inputSize, Random random) {
    return convolutionLayer.explode();
  }
  
  @Override
  public int[][] getPerfDims(Random random) {
    return new int[][]{
      {100, 100, inputBands}
    };
  }
  
  @Override
  public NNLayer getReferenceLayer() {
    return convolutionLayer.as(com.simiacryptus.mindseye.layers.aparapi.ConvolutionLayer.class);
  }
  
  @Override
  protected Class<?> getTargetClass() {
    return ConvolutionLayer.class;
  }
  
  /**
   * Increases the number of color bands from 3 to 6 (radius 3; 64-bit precision)
   */
  public static class BandExpand extends ConvolutionLayerTest {
  
    /**
     * Instantiates a new Asymmetric apply.
     */
    public BandExpand() {
      super(1, 3, 2, Precision.Double, 16);
    }
  
    @Override
    public int[][] getInputDims(Random random) {
      return new int[][]{
        {1, 1, inputBands}
      };
    }
  
    @Override
    public int[][] getPerfDims(Random random) {
      return getInputDims(random);
    }
    
  }
  
  /**
   * Increases the number of color bands from 3 to 6 (radius 3; 64-bit precision)
   */
  public static class BandLimit extends ConvolutionLayerTest {
    
    /**
     * Instantiates a new Asymmetric apply.
     */
    public BandLimit() {
      super(1, 3, 2, Precision.Double, 16);
    }

//    @Override
//    public int[][] getInputDims(Random random) {
//      return new int[][]{
//        {10, 10, inputBands}
//      };
//    }
//
//    @Override
//    public int[][] getPerfDims(Random random) {
//      return getInputDims(random);
//    }
  
  }
  
  /**
   * Increases the number of color bands from 3 to 6 (radius 3; 64-bit precision)
   */
  public static class Temp extends ConvolutionLayerTest {
    
    /**
     * Instantiates a new Asymmetric apply.
     */
    public Temp() {
      super(1, 4, 4, Precision.Double, 2);
      validateDifferentials = false;
    }
    
  }
  
  /**
   * Increases the number of color bands from 3 to 6 (radius 3; 64-bit precision)
   */
  public static class SqGrid extends ConvolutionLayerTest {
  
    /**
     * Instantiates a new Asymmetric apply.
     */
    public SqGrid() {
      super(3, 4, 4, Precision.Double, 2);
    }
    
  }
  
  /**
   * Increases the number of color bands from 3 to 6 (radius 3; 64-bit precision)
   */
  public static class IrregularGrid extends ConvolutionLayerTest {
  
    /**
     * Instantiates a new Asymmetric apply.
     */
    public IrregularGrid() {
      super(3, 5, 3, Precision.Double, 2);
    }
    
  }
  
  /**
   * Reduces the number of color bands from 6 to 3 (radius 3; 64-bit precision)
   */
  public static class BandReduceTest extends ConvolutionLayerTest {
  
    /**
     * Instantiates a new Asymmetric apply.
     */
    public BandReduceTest() {
      super(3, 6, 3, Precision.Double, 16);
    }
    
  }
  
  /**
   * Test using 64-bit precision with a radius of 1
   */
  public static class Double extends ConvolutionLayerTest {
  
    /**
     * Instantiates a new Double.
     */
    public Double() {
      super(3, 4, 4, Precision.Double, 16);
    }
    
  }
  
  /**
   * Tests with no zero-padding; the output will be radius-1 smaller than the input. This currently tests a workaround
   * where CuDNN does not seem to support convolutions that change resolution.
   */
  public static class NoPadding extends ConvolutionLayerTest {
    /**
     * Instantiates a new Double.
     */
    public NoPadding() {
      super(3, 3, 3, Precision.Double, 16);
      convolutionLayer.setPaddingXY(0, 0);
    }

//
//    @Override
//    public int[][] getInputDims(Random random) {
//      return new int[][]{
//        {50, 50, inputBands}
//      };
//    }
//
//    @Override
//    public int[][] getPerfDims(Random random) {
//      return getInputDims(random);
//    }
//
  
    @Override
    public NNLayer getReferenceLayer() {
      // BUG: Reference aparapi implementation does not seem to implement nonstandard padding correctly
      return null;
    }
  }
  
  /**
   * Test using 32-bit precision with a radius of 1
   */
  public static class Float extends ConvolutionLayerTest {
    /**
     * Instantiates a new Float.
     */
    public Float() {
      super(1, 2, 2, Precision.Float, 16);
    }
  }
  
  /**
   * Convert from 7 bands to 5; this is meant to not divide evenly for testing. (64-bit)
   */
  public static class IrregularTest extends ConvolutionLayerTest {
  
    /**
     * Instantiates a new Irregular apply.
     */
    public IrregularTest() {
      super(3, 7, 5, Precision.Double, 16);
    }
  }
  
  /**
   * Convert from 7 bands to 5; this is meant to not divide evenly for testing. (32-bit)
   */
  public static class IrregularTest_Float extends ConvolutionLayerTest {
  
    /**
     * Instantiates a new Irregular apply float.
     */
    public IrregularTest_Float() {
      super(3, 7, 5, Precision.Float, 16);
    }
  
    @Override
    public ComponentTest<ToleranceStatistics> getDerivativeTester() {
      if (!validateDifferentials) return null;
      return new SingleDerivativeTester(1e-2, 1e-4);
    }
  }
  
}
