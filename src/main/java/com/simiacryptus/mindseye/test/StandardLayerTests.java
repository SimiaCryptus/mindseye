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

package com.simiacryptus.mindseye.test;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.network.DAGNetwork;
import com.simiacryptus.util.Util;
import com.simiacryptus.util.data.DoubleStatistics;
import com.simiacryptus.util.io.NotebookOutput;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;

/**
 * The type Layer test base.
 */
public abstract class StandardLayerTests {
  /**
   * The constant originalOut.
   */
  protected static final PrintStream originalOut = System.out;
  private static final Logger log = LoggerFactory.getLogger(StandardLayerTests.class);
  /**
   * The Validate batch execution.
   */
  protected boolean validateBatchExecution = true;
  /**
   * The Validate differentials.
   */
  protected boolean validateDifferentials = true;
  
  /**
   * Test.
   *
   * @param log the log
   */
  public void test(NotebookOutput log) {
    if (null != originalOut) log.addCopy(originalOut);
    NNLayer layer = getLayer();
    log.h1("%s", layer.getClass().getSimpleName());
    log.h2("%s", getClass().getSimpleName());
    log.h3("Json Serialization");
    log.code(() -> {
      JsonObject json = layer.getJson();
      NNLayer echo = NNLayer.fromJson(json);
      if ((echo == null)) throw new AssertionError("Failed to deserialize");
      if ((layer == echo)) throw new AssertionError("Serialization did not copy");
      if ((!layer.equals(echo))) throw new AssertionError("Serialization not equal");
      return new GsonBuilder().setPrettyPrinting().create().toJson(json);
    });
    
    if (layer instanceof DAGNetwork) {
      log.h3("Network Diagram");
      log.code(() -> {
        return Graphviz.fromGraph(TestUtil.toGraph((DAGNetwork) layer))
          .height(400).width(600).render(Format.PNG).toImage();
      });
    }
    
    
    testReferenceIO(log, layer, Arrays.stream(getReferenceInputDims()).map(dim -> new Tensor(dim).fill(() -> random())).toArray(i -> new Tensor[i]));
    
    NNLayer referenceLayer = getReferenceLayer();
    if (null != referenceLayer) {
      Tensor[] inputPrototype = Arrays.stream(getInputDims()).map(dim -> new Tensor(dim).fill(() -> random())).toArray(i -> new Tensor[i]);
      log.h3("Reference Implementation");
      log.code(() -> {
        System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(referenceLayer.getJson()));
        getEquivalencyTester().test(referenceLayer, layer, inputPrototype);
      });
    }
    
    if (validateBatchExecution) {
      Tensor[] inputPrototype = Arrays.stream(getInputDims()).map(dim -> new Tensor(dim).fill(() -> random())).toArray(i -> new Tensor[i]);
      log.h3("Batch Execution");
      log.code(() -> {
        return getBatchingTester().test(layer, inputPrototype);
      });
    }
    
    if (validateDifferentials) {
      Tensor[] inputPrototype = Arrays.stream(getInputDims()).map(dim -> new Tensor(dim).fill(() -> random())).toArray(i -> new Tensor[i]);
      log.h3("Differential Validation");
      log.code(() -> {
        return getDerivativeTester().test(layer, inputPrototype);
      });
    }
    
    log.h3("Performance");
    if (layer instanceof DAGNetwork) {
      TestUtil.instrumentPerformance(log, (DAGNetwork) layer);
    }
    Tensor[] permPrototype = Arrays.stream(getPerfDims()).map(dim -> new Tensor(dim).fill(() -> random())).toArray(i -> new Tensor[i]);
    log.code(() -> {
      getPerformanceTester().test(layer, permPrototype);
    });
    if (layer instanceof DAGNetwork) {
      TestUtil.extractPerformance(log, (DAGNetwork) layer);
    }
    
  }
  
  /**
   * Test reference io.
   *
   * @param log            the log
   * @param layer          the layer
   * @param inputPrototype the input prototype
   */
  public void testReferenceIO(NotebookOutput log, NNLayer layer, Tensor[] inputPrototype) {
    HashMap<Tensor[], Tensor> referenceIO = getReferenceIO();
    if (!referenceIO.isEmpty()) {
      log.h3("Reference Input/Output Pairs");
      referenceIO.forEach((input, output) -> {
        log.code(() -> {
          SimpleEval eval = SimpleEval.run(layer, input);
          DoubleStatistics error = new DoubleStatistics().accept(eval.getOutput().add(output.scale(-1)).getData());
          return String.format("--------------------\nInput: \n[%s]\n--------------------\nOutput: \n%s\nError: %s\n--------------------\nDerivative: \n%s",
            Arrays.stream(input).map(t -> t.prettyPrint()).reduce((a, b) -> a + ",\n" + b).get(),
            eval.getOutput().prettyPrint(), error,
            Arrays.stream(eval.getDerivative()).map(t -> t.prettyPrint()).reduce((a, b) -> a + ",\n" + b).get());
        });
      });
    }
    else {
      log.h3("Example Input/Output Pair");
      log.code(() -> {
        SimpleEval eval = SimpleEval.run(layer, inputPrototype);
        return String.format("--------------------\nInput: \n[%s]\n--------------------\nOutput: \n%s\n--------------------\nDerivative: \n%s",
          Arrays.stream(inputPrototype).map(t -> t.prettyPrint()).reduce((a, b) -> a + ",\n" + b).get(),
          eval.getOutput().prettyPrint(),
          Arrays.stream(eval.getDerivative()).map(t -> t.prettyPrint()).reduce((a, b) -> a + ",\n" + b).get());
      });
    }
  }
  
  /**
   * Gets batching tester.
   *
   * @return the batching tester
   */
  public BatchingTester getBatchingTester() {
    return new BatchingTester(1e-2) {
      @Override
      public double getRandom() {
        return random();
      }
    };
  }
  
  /**
   * Random double.
   *
   * @return the double
   */
  public double random() {
    return Math.round(1000 * (Util.R.get().nextDouble() - 0.5)) / 250.0;
  }
  
  /**
   * Gets equivalency tester.
   *
   * @return the equivalency tester
   */
  public EquivalencyTester getEquivalencyTester() {
    return new EquivalencyTester(1e-2);
  }
  
  /**
   * Gets performance tester.
   *
   * @return the performance tester
   */
  public PerformanceTester getPerformanceTester() {
    return new PerformanceTester();
  }
  
  /**
   * Gets reference io.
   *
   * @return the reference io
   */
  protected HashMap<Tensor[], Tensor> getReferenceIO() {
    return new HashMap<>();
  }
  
  /**
   * Gets derivative tester.
   *
   * @return the derivative tester
   */
  public DerivativeTester getDerivativeTester() {
    return new SingleDerivativeTester(1e-3, 1e-4);
  }
  
  /**
   * Gets layer.
   *
   * @return the layer
   */
  public abstract NNLayer getLayer();
  
  /**
   * Gets reference layer.
   *
   * @return the reference layer
   */
  public NNLayer getReferenceLayer() {
    return null;
  }
  
  /**
   * Get input dims int [ ] [ ].
   *
   * @return the int [ ] [ ]
   */
  public abstract int[][] getInputDims();
  
  /**
   * Get perf dims int [ ] [ ].
   *
   * @return the int [ ] [ ]
   */
  public int[][] getPerfDims() {
    return getInputDims();
  }
  
  /**
   * Get reference input dims int [ ] [ ].
   *
   * @return the int [ ] [ ]
   */
  public int[][] getReferenceInputDims() {
    return getInputDims();
  }
  
}