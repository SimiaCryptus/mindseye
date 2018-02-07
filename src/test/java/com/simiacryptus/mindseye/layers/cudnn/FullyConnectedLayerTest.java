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
import com.simiacryptus.mindseye.lang.cudnn.GpuSystem;
import com.simiacryptus.mindseye.network.PipelineNetwork;
import com.simiacryptus.mindseye.test.ToleranceStatistics;
import com.simiacryptus.mindseye.test.unit.BatchingTester;
import com.simiacryptus.mindseye.test.unit.ComponentTest;
import com.simiacryptus.util.io.NotebookOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.Random;

/**
 * The type Fully connected layer run.
 */
public abstract class FullyConnectedLayerTest extends CuDNNLayerTestBase {
  
  /**
   * The Input dim.
   */
  protected final @NotNull int[] inputDim;
  /**
   * The Fully connected layer.
   */
  protected final @NotNull FullyConnectedLayer fullyConnectedLayer;
  /**
   * The Layer.
   */
  protected final @NotNull PipelineNetwork layer;
  
  /**
   * Instantiates a new Fully connected layer allocationOverflow.
   *
   * @param inputDims  the input dims
   * @param outputDims the output dims
   * @param batchBands the batch bands
   */
  public FullyConnectedLayerTest(@NotNull int[] inputDims, @NotNull int[] outputDims, int batchBands) {
    this.inputDim = inputDims;
    this.fullyConnectedLayer = new FullyConnectedLayer(inputDims, outputDims).setWeightsLog(-2);
    this.layer = this.fullyConnectedLayer.setBatchBands(batchBands).explode();
  }
  
  @Override
  public @NotNull int[][] getSmallDims(Random random) {
    return new int[][]{
      inputDim
    };
  }
  
  @Override
  protected @NotNull Class<?> getTargetClass() {
    return FullyConnectedLayer.class;
  }
  
  @Override
  public @NotNull NNLayer getLayer(final int[][] inputSize, Random random) {
    layer.addRef();
    return layer;
  }
  
  @Override
  public NNLayer getReferenceLayer() {
    @Nullable Class<? extends NNLayer> referenceLayerClass = getReferenceLayerClass();
    return null == referenceLayerClass ? null : this.fullyConnectedLayer.as(referenceLayerClass);
  }
  
  @Override
  public @Nullable Class<? extends NNLayer> getReferenceLayerClass() {
    return com.simiacryptus.mindseye.layers.java.FullyConnectedReferenceLayer.class;
  }
  
  @Override
  public void run(NotebookOutput log) {
    @NotNull String logName = "cuda_" + log.getName() + "_all.log";
    log.p(log.file((String) null, logName, "GPU Log"));
    GpuSystem.addLog(new PrintStream(log.file(logName)));
    super.run(log);
  }
  
  /**
   * Basic Test
   */
  public static class Basic extends FullyConnectedLayerTest {
    /**
     * Instantiates a new Basic.
     */
    public Basic() {
      super(new int[]{2}, new int[]{2}, 512);
    }
  }
  
  /**
   * Basic Test
   */
  public abstract static class Big extends FullyConnectedLayerTest {
  
    /**
     * Instantiates a new Big.
     *
     * @param inputDims  the input dims
     * @param outputDims the output dims
     * @param batchBands the batch bands
     */
    public Big(@NotNull int[] inputDims, @NotNull int[] outputDims, int batchBands) {
      super(inputDims, outputDims, batchBands);
      validateDifferentials = false;
    }
  
    @Override
    public Class<? extends NNLayer> getReferenceLayerClass() {
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
  
    @Override
    protected @Nullable ComponentTest<ToleranceStatistics> getJsonTester() {
      logger.warn("Disabled Json Test");
      return null;
      //return super.getJsonTester();
    }
  
    @Override
    public @Nullable ComponentTest<ToleranceStatistics> getPerformanceTester() {
      logger.warn("Disabled Performance Test");
      return null;
      //return super.getPerformanceTester();
    }
  }
  
  /**
   * Large-dimension test using the size of the largest layer in VGG16
   */
  public static class Big_VGG extends Big {
    /**
     * Instantiates a new Big.
     */
    public Big_VGG() {
      super(new int[]{25088}, new int[]{4096}, 512);
    }
    
  }
  
  /**
   * Large-dimension test
   */
  public static class Big1 extends Big {
    /**
     * Instantiates a new Big.
     */
    public Big1() {
      super(new int[]{2 * 1024}, new int[]{2 * 1024}, 512);
    }
    
  }
  
  
  /**
   * Large-dimension test
   */
  public static class Big_Temp2 extends Big {
    /**
     * Instantiates a new Big.
     */
    public Big_Temp2() {
      super(new int[]{1024}, new int[]{512}, 32);
    }
    
  }
  
  /**
   * Large-dimension test
   */
  public static class Big_Temp3 extends Big {
    /**
     * Instantiates a new Big.
     */
    public Big_Temp3() {
      super(new int[]{2 * 1024}, new int[]{512}, 32);
    }
    
  }
  
  /**
   * Large-dimension test
   */
  public static class Big_Temp0 extends Big {
    /**
     * Instantiates a new Big.
     */
    public Big_Temp0() {
      super(new int[]{1024}, new int[]{256}, 32);
    }
    
  }
  
  /**
   * Large-dimension test
   */
  public static class Big_Temp1 extends Big {
    /**
     * Instantiates a new Big.
     */
    public Big_Temp1() {
      super(new int[]{2 * 1024}, new int[]{256}, 32);
    }
    
  }
  
}
