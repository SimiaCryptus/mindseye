package com.simiacryptus.mindseye.test.demo;

import com.simiacryptus.mindseye.layers.BiasLayer;
import com.simiacryptus.mindseye.layers.DenseSynapseLayer;
import com.simiacryptus.mindseye.layers.LinearActivationLayer;
import com.simiacryptus.mindseye.layers.NNLayer;
import com.simiacryptus.mindseye.layers.SigmoidActivationLayer;
import com.simiacryptus.mindseye.layers.SoftmaxActivationLayer;
import com.simiacryptus.mindseye.layers.SynapseActivationLayer;
import com.simiacryptus.mindseye.math.NDArray;
import com.simiacryptus.mindseye.training.PipelineNetwork;
import com.simiacryptus.mindseye.training.Tester;

public class SoftmaxTests3 extends SimpleClassificationTests {
  
  @Override
  public PipelineNetwork buildNetwork() {
    
    final int[] inputSize = new int[] { 2 };
    final int[] outSize = new int[] { 2 };
    final int[] midSize = new int[] { 8 };
    final int midLayers = 0;
    PipelineNetwork net = new PipelineNetwork();
    
    NNLayer inputLayer = new PipelineNetwork()
        .add(new DenseSynapseLayer(NDArray.dim(inputSize), midSize))
        .add(new BiasLayer(midSize))
        .add(new SigmoidActivationLayer());
    net = net.add(inputLayer);
    
    for (int i = 0; i < midLayers; i++) {
      NNLayer hiddenLayer = new PipelineNetwork()
          .add(new DenseSynapseLayer(NDArray.dim(midSize), midSize))
          .add(new BiasLayer(midSize))
          .add(new SigmoidActivationLayer());
      net = net.add(hiddenLayer);
    }
    
    PipelineNetwork outputLayer = new PipelineNetwork();
    outputLayer = outputLayer.add(new SynapseActivationLayer(NDArray.dim(midSize)));
    outputLayer = outputLayer.add(new DenseSynapseLayer(NDArray.dim(midSize), outSize));
    outputLayer = outputLayer.add(new BiasLayer(outSize));
    outputLayer = outputLayer.add(new SynapseActivationLayer(NDArray.dim(outSize)));
    net = net.add(outputLayer);
    
    // outputLayer = outputLayer.add(new ExpActivationLayer());
    // outputLayer = outputLayer.add(new L1NormalizationLayer());
    // outputLayer = outputLayer.add(new SigmoidActivationLayer());
    outputLayer = outputLayer.add(new LinearActivationLayer());
    outputLayer = outputLayer.add(new SoftmaxActivationLayer());
    
    return net;
  }
  
  @Override
  public void test_Gaussians() throws Exception {
    super.test_Gaussians();
  }
  
  @Override
  public void test_II() throws Exception {
    super.test_II();
  }
  
  @Override
  public void test_III() throws Exception {
    super.test_III();
  }
  
  @Override
  public void test_Lines() throws Exception {
    
    super.test_Lines();
  }
  
  @Override
  public void test_O() throws Exception {
    super.test_O();
  }
  
  @Override
  public void test_oo() throws Exception {
    super.test_oo();
  }
  
  @Override
  public void test_simple() throws Exception {
    super.test_simple();
  }
  
  @Override
  public void test_snakes() throws Exception {
    super.test_snakes();
  }
  
  @Override
  public void test_sos() throws Exception {
    super.test_sos();
  }
  
  @Override
  public void test_X() throws Exception {
    super.test_X();
  }
  
  @Override
  public void test_O2() throws Exception {
    super.test_O2();
  }
  
  @Override
  public void test_O3() throws Exception {
    super.test_O3();
  }
  
  @Override
  public void test_xor() throws Exception {
    super.test_xor();
  }
  
  @Override
  public void verify(final Tester trainer) {
    // trainer.setVerbose(true).verifyConvergence(0, 0.0, 1);
    trainer.verifyConvergence(0, 0.0, 5);
  }
  
}