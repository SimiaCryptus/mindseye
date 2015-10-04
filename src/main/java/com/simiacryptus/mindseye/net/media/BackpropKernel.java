package com.simiacryptus.mindseye.net.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BackpropKernel extends com.amd.aparapi.Kernel {

  private static final Logger log = LoggerFactory.getLogger(ConvolutionSynapseLayer.class);

  private static final boolean DEBUG = false;
  final int[] outputSize;
  final int[] kernelSize;
  final int[] inputSize;
  double[] output;
  double[] weights;
  double[] input;

  public BackpropKernel(int[] inputSize, double[] input, int[] kernelSize, double[] weights, int[] outputSize, double[] output) {
    this.outputSize = outputSize;
    this.output = output;
    this.kernelSize = kernelSize;
    this.weights = weights;
    this.inputSize = inputSize;
    this.input = input;
    assert (outputSize[0] * outputSize[1] * outputSize[2] == output.length);
    assert (inputSize[0] * inputSize[1] * inputSize[2] == input.length);
    assert (kernelSize[0] * kernelSize[1] * kernelSize[2] == weights.length);
  }

  @Override
  public void run() {
    int i = getGlobalId();
    input[i] = run(i);
  }

  public final double run(int i) {
    int is0 = inputSize[0];
    int is1 = is0 * inputSize[1];
    int i3 = i / is1;
    int i2 = (i % is1) / is0;
    int i1 = i % is0;
    
    double accum = 0;
    for(int k=0;k<weights.length;k++){
      if(0. == weights[k]) continue;
      int ks0 = kernelSize[0];
      int ks1 = ks0 * kernelSize[1];
      int k3 = k / ks1;
      int k2 = (k % ks1) / ks0;
      int k1 = k % ks0;
      
      //i3 = k3 - inputSize[2] * o3;
      if(0 != ((k3 - i3) % inputSize[2])) continue;
      int o3 = (k3 - i3) / inputSize[2];
      if(0 > o3 || o3 >= outputSize[2]) continue;
      int o2 = (i2-k2);
      if(0 > o2 || o2 >= outputSize[1]) continue;
      int o1 = (i1-k1);
      if(0 > o1 || o1 >= outputSize[0]) continue;
      int o = o1 + outputSize[0] * (o2 + outputSize[1] * o3);
      if(0. == output[o]) continue;
      
      accum += output[o] * weights[k];
      if (DEBUG) {
        log.debug(String.format("[%s](%s) += [%s](%s) * [%s](%s) [%s,%s,%s]",i,accum,o,output[o],k,weights[k],k1,k2,k3));
        log.debug(String.format("k=[%s,%s,%s]  i=[%s,%s,%s]  o=[%s,%s,%s]",k1,k2,k3,i1,i2,i3,o1,o2,o3));
      }
    }
    return accum;
  }
  
  public void exe(com.amd.aparapi.device.Device device){
    assert (outputSize[0] * outputSize[1] * outputSize[2] == output.length);
    assert (inputSize[0] * inputSize[1] * inputSize[2] == input.length);
    assert (kernelSize[0] * kernelSize[1] * kernelSize[2] == weights.length);
    if (DEBUG) {
      for (int i = 0; i < input.length; i++) {
        input[i] = run(i);
      }
    } else {
      execute(device.createRange(input.length));
    }
  }
}