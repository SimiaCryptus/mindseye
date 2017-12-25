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

package com.simiacryptus.mindseye.test.unit;

import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.network.DAGNetwork;
import com.simiacryptus.mindseye.test.TestUtil;
import com.simiacryptus.mindseye.test.ToleranceStatistics;
import com.simiacryptus.util.Util;
import com.simiacryptus.util.io.NotebookOutput;
import com.simiacryptus.util.lang.CodeUtil;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.function.Consumer;

/**
 * The type Layer test base.
 */
public abstract class StandardLayerTests {
  /**
   * The Validate batch execution.
   */
  protected boolean validateBatchExecution = true;
  /**
   * The Validate differentials.
   */
  protected boolean validateDifferentials = true;
  private ArrayList<ComponentTest<?>> bigTests;
  private ArrayList<ComponentTest<?>> littleTests;
  
  /**
   * Gets batching tester.
   *
   * @return the batching tester
   */
  public ComponentTest<ToleranceStatistics> getBatchingTester() {
    if (!validateBatchExecution) return null;
    return new BatchingTester(1e-2) {
      @Override
      public double getRandom() {
        return random();
      }
    };
  }
  
  /**
   * Gets big tests.
   *
   * @return the big tests
   */
  public ArrayList<ComponentTest<?>> getBigTests() {
    if (null == bigTests) {
      synchronized (this) {
        if (null == bigTests) {
          bigTests = new ArrayList<>(Arrays.asList(
            getPerformanceTester(), getTrainingTester()
          ));
        }
      }
    }
    return bigTests;
  }
  
  /**
   * Gets derivative tester.
   *
   * @return the derivative tester
   */
  public ComponentTest<ToleranceStatistics> getDerivativeTester() {
    if (!validateDifferentials) return null;
    return new SingleDerivativeTester(1e-3, 1e-4);
  }
  
  /**
   * Gets equivalency tester.
   *
   * @return the equivalency tester
   */
  public ComponentTest<ToleranceStatistics> getEquivalencyTester() {
    final NNLayer referenceLayer = getReferenceLayer();
    if (null == referenceLayer) return null;
    return new EquivalencyTester(1e-2, referenceLayer);
  }
  
  /**
   * Get input dims int [ ] [ ].
   *
   * @return the int [ ] [ ]
   */
  public abstract int[][] getInputDims();
  
  /**
   * Gets json tester.
   *
   * @return the json tester
   */
  protected ComponentTest<ToleranceStatistics> getJsonTester() {
    return new JsonTest();
  }
  
  /**
   * Gets layer.
   *
   * @param inputSize the input size
   * @return the layer
   */
  public abstract NNLayer getLayer(int[][] inputSize);
  
  /**
   * Gets little tests.
   *
   * @return the little tests
   */
  public ArrayList<ComponentTest<?>> getLittleTests() {
    if (null == littleTests) {
      synchronized (this) {
        if (null == littleTests) {
          littleTests = new ArrayList<>(Arrays.asList(
            getJsonTester(), getReferenceIOTester(), getBatchingTester(), getDerivativeTester(), getEquivalencyTester()
          ));
        }
      }
    }
    return littleTests;
  }
  
  /**
   * Get perf dims int [ ] [ ].
   *
   * @return the int [ ] [ ]
   */
  public int[][] getPerfDims() {
    return getInputDims();
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
   * Gets reference io tester.
   *
   * @return the reference io tester
   */
  protected ComponentTest<ToleranceStatistics> getReferenceIOTester() {
    return new ReferenceIO(getReferenceIO());
  }
  
  /**
   * Gets reference layer.
   *
   * @return the reference layer
   */
  public NNLayer getReferenceLayer() {
    return null;
  }
  
  /**
   * Gets learning tester.
   *
   * @return the learning tester
   */
  public ComponentTest<TrainingTester.ComponentResult> getTrainingTester() {
    return new TrainingTester();
  }
  
  /**
   * Random double.
   *
   * @return the double
   */
  public double random() {
    return Math.round(1000.0 * (Util.R.get().nextDouble() - 0.5)) / 250.0;
  }
  
  /**
   * Random tensor [ ].
   *
   * @param inputDims the input dims
   * @return the tensor [ ]
   */
  public Tensor[] randomize(final int[][] inputDims) {
    return Arrays.stream(inputDims).map(dim -> new Tensor(dim).set(() -> random())).toArray(i -> new Tensor[i]);
  }
  
  /**
   * Test.
   *
   * @param log the log
   */
  public void test(final NotebookOutput log) {
    if (null != TestUtil.originalOut) {
      log.addCopy(TestUtil.originalOut);
    }
    final NNLayer layer = getLayer(getInputDims());
    log.h1("%s", layer.getClass().getSimpleName());
    log.p(String.format("Layer Type %s", log.link(CodeUtil.findFile(layer.getClass()), layer.getClass().getCanonicalName())));
    log.setFMProp("layer_class_short", layer.getClass().getSimpleName());
    log.setFMProp("test_class_short", getClass().getSimpleName());
    log.setFMProp("created_on", new Date().toString());
    log.setFMProp("layer_class_full", layer.getClass().getCanonicalName());
    log.setFMProp("test_class_full", getClass().getCanonicalName());
    log.p(CodeUtil.getJavadoc(layer.getClass()));
    log.h2("%s", getClass().getSimpleName());
    log.p(String.format("Test Type %s", log.link(CodeUtil.findFile(getClass()), getClass().getCanonicalName())));
    log.p(CodeUtil.getJavadoc(getClass()));
    if (layer instanceof DAGNetwork) {
      log.h3("Network Diagram");
      log.p("This is a network with the following layout:");
      log.code(() -> {
        return Graphviz.fromGraph(TestUtil.toGraph((DAGNetwork) layer))
          .height(400).width(600).render(Format.PNG).toImage();
      });
    }
    getLittleTests().stream().filter(x -> null != x).forEach(test -> {
      test.test(log, layer.copy(), randomize(getInputDims()));
    });
    final NNLayer perfLayer = getLayer(getPerfDims());
    getBigTests().stream().filter(x -> null != x).forEach(test -> {
      test.test(log, perfLayer.copy(), randomize(getPerfDims()));
    });
  }
  
  /**
   * With big tests standard layer tests.
   *
   * @param fn the fn
   * @return the standard layer tests
   */
  public StandardLayerTests withBigTests(final Consumer<ArrayList<ComponentTest<?>>> fn) {
    fn.accept(getBigTests());
    return this;
  }
  
  /**
   * With little tests standard layer tests.
   *
   * @param fn the fn
   * @return the standard layer tests
   */
  public StandardLayerTests withLittleTests(final Consumer<ArrayList<ComponentTest<?>>> fn) {
    fn.accept(getLittleTests());
    return this;
  }
  
}
