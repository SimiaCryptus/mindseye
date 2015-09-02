package com.simiacryptus.mindseye.layers;

import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.deltas.DeltaBuffer;
import com.simiacryptus.mindseye.deltas.NNResult;
import com.simiacryptus.mindseye.math.LogNDArray;
import com.simiacryptus.mindseye.math.LogNumber;
import com.simiacryptus.mindseye.math.NDArray;
import com.simiacryptus.mindseye.training.EvaluationContext;

public class ExpActivationLayer extends NNLayer {
  
  private static final Logger log = LoggerFactory.getLogger(ExpActivationLayer.class);
  
  private boolean verbose;
  
  public ExpActivationLayer() {
  }
  
  @Override
  public NNResult eval(EvaluationContext evaluationContext, final NNResult... inObj) {
    assert(1==inObj.length);
    NNResult in = inObj[0];
    final NDArray input = in.data;
    final NDArray output = new NDArray(in.data.getDims());
    final NDArray inputGradient = new NDArray(input.dim());
    IntStream.range(0, input.dim()).forEach(i -> {
      final double x = input.getData()[i];
      double max = 100;
      final double ex = Math.exp(Math.max(Math.min(max, x), -max));
      double d = ex;
      double f = ex;
      inputGradient.set(new int[] { i }, d);
      output.set(i, f);
    });
    if (isVerbose()) {
      ExpActivationLayer.log.debug(String.format("Feed forward: %s => %s", in.data, output));
    }
    return new NNResult(output) {
      @Override
      public void feedback(final LogNDArray data, final DeltaBuffer buffer) {
        if (in.isAlive()) {
          final LogNDArray passback = new LogNDArray(data.getDims());
          IntStream.range(0, passback.dim()).forEach(i -> {
            LogNumber x = data.getData()[i];
            double dx = inputGradient.getData()[i];
            passback.set(i, x.multiply(dx));
          });
          if (isVerbose()) {
            ExpActivationLayer.log.debug(String.format("Feed back @ %s: %s => %s", output, data, passback));
          }
          in.feedback(passback, buffer);
        }
      }
      
      @Override
      public boolean isAlive() {
        return in.isAlive();
      }
    };
  }
  
  public boolean isVerbose() {
    return this.verbose;
  }
  
  public ExpActivationLayer setVerbose(final boolean verbose) {
    this.verbose = verbose;
    return this;
  }
}