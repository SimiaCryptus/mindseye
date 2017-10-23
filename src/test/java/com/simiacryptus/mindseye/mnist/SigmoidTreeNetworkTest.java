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

package com.simiacryptus.mindseye.mnist;

import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.layers.activation.SoftmaxActivationLayer;
import com.simiacryptus.mindseye.layers.cudnn.f32.ConvolutionLayer;
import com.simiacryptus.mindseye.layers.cudnn.f32.PoolingLayer;
import com.simiacryptus.mindseye.layers.synapse.BiasLayer;
import com.simiacryptus.mindseye.layers.synapse.DenseSynapseLayer;
import com.simiacryptus.mindseye.mnist.LinearTest;
import com.simiacryptus.mindseye.network.PipelineNetwork;
import com.simiacryptus.mindseye.network.PolynomialConvolutionNetwork;
import com.simiacryptus.mindseye.network.SigmoidTreeNetwork;
import com.simiacryptus.mindseye.opt.Step;
import com.simiacryptus.mindseye.opt.TrainingMonitor;
import com.simiacryptus.util.MonitoredObject;
import com.simiacryptus.util.io.NotebookOutput;

import java.util.List;

/**
 * The type Sigmoid tree network test.
 */
public class SigmoidTreeNetworkTest extends LinearTest {

  /**
   * The Description.
   */
  protected String description = "This report demonstrates a basic linear model fit against the MNIST dataset. " +
                                   "It serves as a reference report to compare algorithm variants.";
  /**
   * The Tree.
   */
  protected SigmoidTreeNetwork tree;

  @Override
  public PipelineNetwork buildModel(NotebookOutput log) {
    log.p("This is a very simple model that performs basic logistic regression. " +
            "It is expected to be trainable to about 91% accuracy on MNIST.");
    return log.code(() -> {
      PipelineNetwork network = new PipelineNetwork();
      network.add(new ConvolutionLayer(3,3,5,false));
      network.add(new PoolingLayer().setMode(PoolingLayer.PoolingMode.Avg));
      this.tree = new SigmoidTreeNetwork(
                                          new DenseSynapseLayer(new int[]{28, 28, 1}, new int[]{10}),
                                          new BiasLayer(28, 28, 1)
      ).setSkipFuzzy(true).setSkipChildStage(true);
      network.add(tree);
      network.add(new SoftmaxActivationLayer());
      return network;
    });
  }

  @Override
  public PipelineNetwork _test(NotebookOutput log, MonitoredObject monitoringRoot, TrainingMonitor monitor, Tensor[][] trainingData, List<Step> history) {
    log.p(description);
    log.p("First, define a model:");
    PipelineNetwork network = buildModel(log);
    run(log, monitoringRoot, monitor, trainingData, history, network);
    tree.nextPhase();
    run(log, monitoringRoot, monitor, trainingData, history, network);
    tree.nextPhase();
    run(log, monitoringRoot, monitor, trainingData, history, network);
    return network;
  }
}
