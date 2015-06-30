package com.simiacryptus.mindseye.learning;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import com.simiacryptus.mindseye.NDArray;

public class NNResult {
  
  public final NDArray data;
  
  public NNResult(final NDArray data) {
    super();
    this.data = data;
  }

  public NDArray delta(final double d, final int k) {
    return delta(d, ideal(k));
  }

  public NDArray delta(final double d, final NDArray out) {
    final NDArray delta = new NDArray(this.data.getDims());
    Arrays.parallelSetAll(delta.data, i -> (out.data[i] - NNResult.this.data.data[i]) * d);
    return delta;
  }
  
  public double errMisclassification(final int k) {
    final int prediction = IntStream.range(0, this.data.dim()).mapToObj(i -> i).sorted(Comparator.comparing(i -> this.data.data[i])).findFirst().get();
    return k == prediction ? 0 : 1;
  }
  
  public double errRms(final int k) {
    return errRms(ideal(k));
  }
  
  public double errRms(final NDArray out) {
    final double[] mapToDouble = IntStream.range(0, this.data.dim()).mapToDouble(i -> Math.pow(NNResult.this.data.data[i] - out.data[i], 2.)).toArray();
    final double sum = DoubleStream.of(mapToDouble).average().getAsDouble();
    return Math.sqrt(sum);
  }
  
  public void feedback(final NDArray data) {
    feedback(data);
  }
  
  public NDArray ideal(final int k) {
    final NDArray delta = new NDArray(this.data.getDims());
    Arrays.parallelSetAll(delta.data, i -> i == k ? 1. : 0.);
    return delta;
  }
  
  public boolean isAlive() {
    return false;
  }
  
  public final void learn(final double d, final int k) {
    feedback(delta(d, k));
  }
  
  public final void learn(final double d, final NDArray out) {
    feedback(delta(d, out));
  }

}