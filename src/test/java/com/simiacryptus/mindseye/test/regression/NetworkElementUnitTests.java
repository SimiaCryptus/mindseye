package com.simiacryptus.mindseye.test.regression;

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.math.NDArray;
import com.simiacryptus.mindseye.net.basic.BiasLayer;
import com.simiacryptus.mindseye.net.basic.DenseSynapseLayer;
import com.simiacryptus.mindseye.net.basic.SigmoidActivationLayer;
import com.simiacryptus.mindseye.net.basic.SoftmaxActivationLayer;
import com.simiacryptus.mindseye.net.dag.DAGNetwork;
import com.simiacryptus.mindseye.net.dev.ExpActivationLayer;
import com.simiacryptus.mindseye.net.dev.L1NormalizationLayer;
import com.simiacryptus.mindseye.net.dev.LinearActivationLayer;
import com.simiacryptus.mindseye.net.dev.SynapseActivationLayer;
import com.simiacryptus.mindseye.net.media.ConvolutionSynapseLayer;
import com.simiacryptus.mindseye.net.media.MaxSubsampleLayer;

import groovy.lang.Tuple2;

public class NetworkElementUnitTests {
  static final Logger log = LoggerFactory.getLogger(NetworkElementUnitTests.class);

  public static final Random random = new Random();

  @Test
  // @Ignore
  public void bias_feedback() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 1, 1 }), new NDArray(outSize, new double[] { -1, 2 }) } };
    new DAGNetwork().add(new BiasLayer(inputSize).setVerbose(true))
        // .add(new BiasLayer(inputSize).addWeights(() -> 10 *
        // SimpleNetworkTests.random.nextGaussian()).freeze())
        .trainer(samples)//
        .setVerbose(true)
        .verifyConvergence(0.1, 1);
  }

  @Test
  public void bias_permute_back() throws Exception {
    final BiasLayer layer = new BiasLayer(new int[] { 5 });
    java.util.Arrays.setAll(layer.bias, i -> i);
    layer.permuteOutput(java.util.Arrays.asList(new Tuple2<>(1, 3), new Tuple2<>(2, 1), new Tuple2<>(3, 2)));
    Assert.assertArrayEquals(layer.bias, new double[] { 0, 2, 3, 1, 4 }, 0.001);
  }

  @Test
  public void bias_permute_fwd() throws Exception {
    final BiasLayer layer = new BiasLayer(new int[] { 5 });
    java.util.Arrays.setAll(layer.bias, i -> i);
    layer.permuteInput(java.util.Arrays.asList(new Tuple2<>(1, 3), new Tuple2<>(2, 1), new Tuple2<>(3, 2)));
    Assert.assertArrayEquals(layer.bias, new double[] { 0, 2, 3, 1, 4 }, 0.001);
  }

  @Test
  public void bias_train() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 0, 0 }), new NDArray(outSize, new double[] { -1, 1 }) } };
    new DAGNetwork() //
        .add(new BiasLayer(inputSize)).trainer(samples)
        // .setVerbose(true)
        .verifyConvergence(0.01, 100);
  }

  @Test
  // @Ignore
  public void bias_train2() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 1, 1 }), new NDArray(outSize, new double[] { -1, 1 }) } };
    new DAGNetwork().add(new BiasLayer(inputSize).addWeights(() -> 10 * SimpleNetworkTests.random.nextGaussian()).freeze()).add(new BiasLayer(inputSize)).trainer(samples)
        .verifyConvergence(0.1, 100);
  }

  @Test
  public void convolutionSynapseLayer_feedback() throws Exception {
    final boolean verbose = false;
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 1 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 1, 1 }), new NDArray(outSize, new double[] { 1 }) } };
    new DAGNetwork().add(new BiasLayer(inputSize))
        .add(new ConvolutionSynapseLayer(inputSize, 1).addWeights(() -> 10.5 * SimpleNetworkTests.random.nextGaussian()).setVerbose(verbose).freeze()).trainer(samples)
        .setVerbose(verbose).setStaticRate(.1).verifyConvergence(0.1, 100);
  }

  @Test
  // @Ignore
  public void convolutionSynapseLayer_train() throws Exception {
    final boolean verbose = false;
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 1 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 1, 0 }), new NDArray(outSize, new double[] { -1 }) },
        { new NDArray(inputSize, new double[] { 0, 1 }), new NDArray(outSize, new double[] { 1 }) } };
    new DAGNetwork() //
        .add(new ConvolutionSynapseLayer(inputSize, 1).setVerbose(verbose)) //
        .trainer(samples) //
        // .setStaticRate(.5)
        .setVerbose(verbose).verifyConvergence(0.1, 100);
  }

  @Test
  public void convolutionSynapseLayer_train2() throws Exception {
    final boolean verbose = false;
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 1 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 1, 1 }), new NDArray(outSize, new double[] { 1 }) } };
    new DAGNetwork().add(new ConvolutionSynapseLayer(inputSize, 1).addWeights(() -> 10.5 * SimpleNetworkTests.random.nextGaussian()).setVerbose(verbose).freeze())
        .add(new BiasLayer(outSize)).trainer(samples).setVerbose(verbose).setStaticRate(.1).verifyConvergence(0.1, 100);
  }

  @Test
  public void denseSynapseLayer_feedback() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 1, 1 }), new NDArray(outSize, new double[] { 1, -1 }) } };
    new DAGNetwork().add(new BiasLayer(inputSize))
        .add(new DenseSynapseLayer(NDArray.dim(inputSize), outSize).addWeights(() -> 10 * SimpleNetworkTests.random.nextGaussian()).freeze()).trainer(samples)
        .verifyConvergence(0.1, 100);
  }

  @Test
  public void denseSynapseLayer_feedback2() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 1, 1 }), new NDArray(outSize, new double[] { 1, -1 }) } };

    new DAGNetwork().add(new BiasLayer(inputSize))
        .add(new DenseSynapseLayer(NDArray.dim(inputSize), outSize).addWeights(() -> 10 * SimpleNetworkTests.random.nextGaussian()).freeze()).trainer(samples)
        .verifyConvergence(0.1, 100);
  }

  @Test
  public void denseSynapseLayer_train() throws Exception {
    final boolean verbose = false;
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 1, 0 }), new NDArray(outSize, new double[] { 0, -1 }) },
        { new NDArray(inputSize, new double[] { 0, 1 }), new NDArray(outSize, new double[] { 1, 0 }) },
        { new NDArray(inputSize, new double[] { 1, 1 }), new NDArray(outSize, new double[] { 1, -1 }) } };
    new DAGNetwork() //
        .add(new DenseSynapseLayer(NDArray.dim(inputSize), outSize).setVerbose(verbose)) //
        .trainer(samples) //
        .setVerbose(true) //
        .verifyConvergence(0.1, 1);
  }

  @Test
  public void denseSynapseLayer_train2() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 1, 1 }), new NDArray(outSize, new double[] { 1, -1 }) } };
    new DAGNetwork().add(new BiasLayer(inputSize)).trainer(samples).verifyConvergence(0.1, 100);
    new DAGNetwork().add(new BiasLayer(inputSize).addWeights(() -> 10 * SimpleNetworkTests.random.nextGaussian()).freeze()).add(new BiasLayer(inputSize)).trainer(samples)
        .verifyConvergence(0.1, 100);
    new DAGNetwork().add(new BiasLayer(inputSize)).add(new BiasLayer(inputSize).addWeights(() -> 10 * SimpleNetworkTests.random.nextGaussian()).freeze()).trainer(samples)
        .verifyConvergence(0.1, 100);
    new DAGNetwork().add(new DenseSynapseLayer(NDArray.dim(inputSize), outSize).addWeights(() -> 10 * SimpleNetworkTests.random.nextGaussian()).freeze())
        .add(new BiasLayer(inputSize)).trainer(samples).verifyConvergence(0.1, 100);
    new DAGNetwork().add(new BiasLayer(inputSize))
        .add(new DenseSynapseLayer(NDArray.dim(inputSize), outSize).addWeights(() -> 10 * SimpleNetworkTests.random.nextGaussian()).freeze()).trainer(samples)
        .verifyConvergence(0.1, 100);
  }

  @Test
  public void effectiveSoftmaxActivationLayer_feedback() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 0, 0 }), new NDArray(outSize, new double[] { 0.9, 0.1 }) } };
    new DAGNetwork().add(new BiasLayer(inputSize)).add(new ExpActivationLayer()).add(new L1NormalizationLayer()).trainer(samples).verifyConvergence(0.1, 100);
  }

  @Test
  @org.junit.Ignore
  public void expActivationLayer_feedback() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 0, 0 }), new NDArray(outSize, new double[] { 0.01, 100. }) } };
    new DAGNetwork().add(new BiasLayer(inputSize)).add(new ExpActivationLayer()).trainer(samples).verifyConvergence(0.1, 100);
  }

  @Test
  public void linearActivationLayer_feedback() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 1, 1 }), new NDArray(outSize, new double[] { 1, -1 }) } };

    new DAGNetwork().add(new BiasLayer(inputSize)).add(new LinearActivationLayer().addWeights(() -> 10 * SimpleNetworkTests.random.nextGaussian()).freeze()).trainer(samples)
        .verifyConvergence(0.1, 100);
  }

  @Test
  public void linearActivationLayer_train() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 0.5, 2 }), new NDArray(outSize, new double[] { 1, 4 }) } };

    new DAGNetwork().add(new LinearActivationLayer()).trainer(samples).verifyConvergence(0.1, 100);
  }

  @Test
  public void maxSubsampleLayer_feedback() throws Exception {
    final boolean verbose = false;
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 1 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 0, -1 }), new NDArray(outSize, new double[] { 1 }) } };
    new DAGNetwork().add(new BiasLayer(inputSize)).add(new MaxSubsampleLayer(2)).trainer(samples).setVerbose(verbose).verifyConvergence(0.1, 100);
  }

  @Test
  public void n2ActivationLayer_feedback() throws Exception {
    final int[] inputSize = new int[] { 4 };
    final int[] outSize = inputSize;
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 0, 0, 0, 0 }), new NDArray(outSize, new double[] { 0.2, 0.3, 0.4, 0.1 }) } };
    new DAGNetwork().add(new BiasLayer(inputSize)).add(new L1NormalizationLayer()).trainer(samples).verifyConvergence(0.1, 100);
  }

  @Test
  public void nestingLayer_feedback() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 0, 0 }), new NDArray(outSize, new double[] { 0.9, 0.1 }) } };
    new DAGNetwork() //
        .add(new BiasLayer(inputSize)) //
        .add(new DAGNetwork() //
            .add(new ExpActivationLayer())//
            .add(new L1NormalizationLayer()))//
        .trainer(samples)//
        .verifyConvergence(0.1, 100);
  }

  @Test
  public void nestingLayer_feedback2() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 0, 0 }), new NDArray(outSize, new double[] { 0.9, 0.1 }) } };
    new DAGNetwork() //
        .add(new BiasLayer(inputSize)) //
        .add(new DAGNetwork() //
            .add(new SoftmaxActivationLayer()))//
        .trainer(samples)//
        .verifyConvergence(0.1, 100);
  }

  @Test
  public void sigmoidActivationLayer_feedback() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 0, 0 }), new NDArray(outSize, new double[] { 0.9, -.9 }) } };
    new DAGNetwork().add(new BiasLayer(inputSize)).add(new SigmoidActivationLayer()).trainer(samples).verifyConvergence(0.1, 100);
  }

  @Test
  public void softmaxActivationLayer_feedback() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 0, 0 }), new NDArray(outSize, new double[] { 0.1, 0.9 }) } };
    boolean verbose = false;
    new DAGNetwork() //
        .add(new BiasLayer(inputSize).setVerbose(verbose))//
        .add(new SoftmaxActivationLayer().setVerbose(verbose))//
        .trainer(samples) //
        .setVerbose(true) //
        //.setParallel(false)
        .verifyConvergence(0.1, 100);

  }

  @Test
  public void synapseActivationLayer_feedback() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 1, 1 }), new NDArray(outSize, new double[] { 1, -1 }) } };

    new DAGNetwork().add(new BiasLayer(inputSize)).add(new SynapseActivationLayer(NDArray.dim(inputSize)).addWeights(() -> 10 * SimpleNetworkTests.random.nextGaussian()).freeze())
        .trainer(samples).verifyConvergence(0.1, 100);
  }

  @Test
  public void synapseActivationLayer_train() throws Exception {
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final NDArray[][] samples = new NDArray[][] { { new NDArray(inputSize, new double[] { 0.5, 2 }), new NDArray(outSize, new double[] { 1, -1 }) } };

    new DAGNetwork().add(new SynapseActivationLayer(NDArray.dim(inputSize))).trainer(samples).verifyConvergence(0.1, 100);
  }

}
