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
import com.simiacryptus.mindseye.lang.cudnn.GpuSystem;
import com.simiacryptus.mindseye.lang.cudnn.Precision;
import com.simiacryptus.mindseye.test.ToleranceStatistics;
import com.simiacryptus.mindseye.test.unit.BatchingTester;
import com.simiacryptus.mindseye.test.unit.ComponentTest;
import com.simiacryptus.mindseye.test.unit.SingleDerivativeTester;
import com.simiacryptus.util.io.NotebookOutput;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.PrintStream;
import java.util.Random;

/**
 * The type Convolution layer run.
 */
public abstract class ConvolutionLayerTest extends CuDNNLayerTestBase {
  
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
  
  /**
   * Instantiates a new Convolution layer run.
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
    @javax.annotation.Nonnull Random random = getRandom();
    convolutionLayer.getKernel().set(() -> {
      return random(random);
    });
  }
  
  @Override
  public void run(NotebookOutput log) {
    @javax.annotation.Nonnull String logName = "cuda_" + log.getName() + "_all.log";
    log.p(log.file((String) null, logName, "GPU Log"));
    @javax.annotation.Nonnull PrintStream apiLog = new PrintStream(log.file(logName));
    GpuSystem.addLog(apiLog);
    super.run(log);
    apiLog.close();
    GpuSystem.apiLog.remove(apiLog);
  }
  
  /**
   * Verify weights.
   */
  @Test
  public void verifyWeights() {
    @Nonnull ExplodedConvolutionGrid explodedNetwork = this.convolutionLayer.getExplodedNetwork();
    @javax.annotation.Nonnull int[] kernelDims = this.convolutionLayer.getKernel().getDimensions();
    @Nullable Tensor testData = new Tensor(kernelDims).map(x -> random());
    explodedNetwork.write(testData);
    Tensor echo = explodedNetwork.read();
    explodedNetwork.freeRef();
    Assert.assertEquals(testData, echo);
  }
  
  @javax.annotation.Nonnull
  @Override
  public int[][] getSmallDims(Random random) {
    return new int[][]{
      {3, 3, inputBands}
    };
  }
  
  @Nonnull
  @Override
  public NNLayer getLayer(final int[][] inputSize, Random random) {
    return convolutionLayer.explode();
  }
  
  @javax.annotation.Nonnull
  @Override
  public int[][] getLargeDims(Random random) {
    return new int[][]{
      {100, 100, inputBands}
    };
  }
  
  @Nullable
  @Override
  public NNLayer getReferenceLayer() {
    return convolutionLayer.as(com.simiacryptus.mindseye.layers.aparapi.ConvolutionLayer.class);
  }
  
  @javax.annotation.Nonnull
  @Override
  protected Class<?> getTargetClass() {
    return ConvolutionLayer.class;
  }
  
  /**
   * Increases the number of color bands from 3 to 6 (radius 3; 64-bit precision)
   */
  public static class BandExpand extends ConvolutionLayerTest {
  
    /**
     * Instantiates a new Asymmetric run.
     */
    public BandExpand() {
      super(1, 3, 2, Precision.Double, 16);
    }
  
    @javax.annotation.Nonnull
    @Override
    public int[][] getSmallDims(Random random) {
      return new int[][]{
        {1, 1, inputBands}
      };
    }
  
    @javax.annotation.Nonnull
    @Override
    public int[][] getLargeDims(Random random) {
      return getSmallDims(random);
    }
    
  }
  
  /**
   * Increases the number of color bands from 3 to 6 (radius 3; 64-bit precision)
   */
  public static class BandLimit extends ConvolutionLayerTest {
  
    /**
     * Instantiates a new Asymmetric run.
     */
    public BandLimit() {
      super(1, 3, 2, Precision.Double, 16);
    }

//    @Override
//    public int[][] getDims(Random random) {
//      return new int[][]{
//        {10, 10, inputBands}
//      };
//    }
//
//    @Override
//    public int[][] getLargeDims(Random random) {
//      return getDims(random);
//    }
  
  }
  
  /**
   * Increases the number of color bands from 3 to 6 (radius 3; 64-bit precision)
   */
  public static class Temp extends ConvolutionLayerTest {
  
    /**
     * Instantiates a new Asymmetric run.
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
     * Instantiates a new Asymmetric run.
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
     * Instantiates a new Asymmetric run.
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
     * Instantiates a new Asymmetric run.
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
   * where GpuSystem does not seem to support convolutions that change resolution.
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
//    public int[][] getDims(Random random) {
//      return new int[][]{
//        {50, 50, inputBands}
//      };
//    }
//
//    @Override
//    public int[][] getLargeDims(Random random) {
//      return getDims(random);
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
     * Instantiates a new Irregular run.
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
     * Instantiates a new Irregular run float.
     */
    public IrregularTest_Float() {
      super(3, 7, 5, Precision.Float, 16);
    }
  
    @Override
    public ComponentTest<ToleranceStatistics> getDerivativeTester() {
      if (!validateDifferentials) return null;
      return new SingleDerivativeTester(1e-1, 1e-4);
    }
  }
  
  /**
   * The type BigTests temp 0.
   */
  public static class Big0 extends VeryBigTest {
    /**
     * Instantiates a new BigTests.
     */
    public Big0() {this(512);}
    
    /**
     * Instantiates a new BigTests.
     *
     * @param size
     */
    private Big0(int size) {
      super(1, 16 * size, 16 * size, Precision.Double, size);
    }
    
  }
  
  /**
   * Basic Test
   */
  public abstract static class VeryBigTest extends Big {
    
    /**
     * Instantiates a new V big.
     *
     * @param radius      the radius
     * @param inputBands  the input bands
     * @param outputBands the output bands
     * @param precision   the precision
     * @param batchBands  the batch bands
     */
    protected VeryBigTest(final int radius, final int inputBands, final int outputBands, final Precision precision, final int batchBands) {
      super(radius, inputBands, outputBands, precision, batchBands);
    }
    
    @Nonnull
    @Override
    public int[][] getSmallDims(final Random random) {
      return new int[][]{
        {1, 1, inputBands}
      };
    }
    
    @Nonnull
    @Override
    public int[][] getLargeDims(final Random random) {
      return new int[][]{
        {1, 1, inputBands}
      };
    }
  }
  
  /**
   * Basic Test
   */
  public abstract static class Big extends ConvolutionLayerTest {
    
    /**
     * Instantiates a new BigTests.
     *
     * @param radius      the radius
     * @param inputBands  the input bands
     * @param outputBands the output bands
     * @param precision   the precision
     * @param batchBands  the batch bands
     */
    public Big(final int radius, final int inputBands, final int outputBands, final Precision precision, int batchBands) {
      super(radius, inputBands, outputBands, precision, batchBands);
      validateDifferentials = false;
    }
    
    @Nullable
    @Override
    public NNLayer getReferenceLayer() {
      return null;
    }
    
    @Override
    public ComponentTest<ToleranceStatistics> getBatchingTester() {
      if (!validateBatchExecution) return null;
      return (new BatchingTester(1e-2) {
        @Override
        public double getRandom() {
          return random();
        }
      }).setBatchSize(5);
    }
    
    @Nullable
    @Override
    protected ComponentTest<ToleranceStatistics> getJsonTester() {
      logger.warn("Disabled Json Test");
      return null;
      //return super.getJsonTester();
    }
    
    @Nullable
    @Override
    public ComponentTest<ToleranceStatistics> getPerformanceTester() {
      logger.warn("Disabled Performance Test");
      return null;
      //return super.getPerformanceTester();
    }
  }
  
}
