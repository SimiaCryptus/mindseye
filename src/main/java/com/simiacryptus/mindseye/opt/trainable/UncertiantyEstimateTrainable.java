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

package com.simiacryptus.mindseye.opt.trainable;

import com.simiacryptus.mindseye.layers.DeltaSet;
import com.simiacryptus.mindseye.opt.TrainingMonitor;
import com.simiacryptus.util.data.DoubleStatistics;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The type Uncertianty estimate trainable.
 */
public class UncertiantyEstimateTrainable implements Trainable {
  
  
  private final List<Trainable> inner;
  private final TrainingMonitor monitor;
  private DoubleStatistics lastStatistics;
  private DoubleStatistics firstStatistics;
  
  /**
   * Instantiates a new Uncertianty estimate trainable.
   *
   * @param inner   the inner
   * @param monitor the monitor
   */
  public UncertiantyEstimateTrainable(List<Trainable> inner, TrainingMonitor monitor) {
    this.inner = inner;
    this.monitor = monitor;
    resetSampling();
  }
  
  /**
   * Instantiates a new Uncertianty estimate trainable.
   *
   * @param monitor the monitor
   * @param inner   the inner
   */
  public UncertiantyEstimateTrainable(TrainingMonitor monitor, Trainable... inner) {
    this(Arrays.asList(inner), monitor);
  }
  
  /**
   * Instantiates a new Uncertianty estimate trainable.
   *
   * @param n       the n
   * @param factory the factory
   * @param monitor the monitor
   */
  public UncertiantyEstimateTrainable(int n, Supplier<Trainable> factory, TrainingMonitor monitor) {
    this(IntStream.range(0, n).mapToObj(i -> factory.get()).collect(Collectors.toList()), monitor);
  }
  
  @Override
  public PointSample measure() {
    List<PointSample> results = this.inner.stream().map(x -> {
      return x.measure();
    }).collect(Collectors.toList());
    DeltaSet deltaSet = results.stream().map(x->x.delta).reduce((a,b)->a.add(b)).get().scale(1.0/results.size());
    DoubleStatistics statistics = new DoubleStatistics().accept(results.stream().mapToDouble(x -> x.value).toArray());
    double meanValue = statistics.getAverage();
    this.lastStatistics = statistics;
    if(null == this.firstStatistics) this.firstStatistics = statistics;
    monitor.log(String.format("Uncertianty (%03f %%) Measurement %s +- %s", 100.0 * (statistics.getStandardDeviation() / meanValue), meanValue, statistics.getStandardDeviation()));
    return new PointSample(deltaSet, results.get(0).weights, meanValue);
  }
  
  @Override
  public void resetToFull() {
    onReset();
    inner.forEach(x->x.resetToFull());
  }
  
  /**
   * On reset.
   */
  public void onReset() {
    if(null != monitor && null != firstStatistics && null != lastStatistics) {
      double improvement = (firstStatistics.getAverage() - lastStatistics.getAverage());
      double uncertianty = lastStatistics.getStandardDeviation();
      monitor.log(String.format("Uncertianty %s and Improvement %s", uncertianty, improvement));
    }
    this.firstStatistics = null;
  }
  
  @Override
  public boolean resetSampling() {
    onReset();
    return inner.stream().map(x->x.resetSampling()).reduce((a,b)->a||b).get();
  }
  
  /**
   * Gets uncertianty.
   *
   * @return the uncertianty
   */
  public double getUncertianty() {
    return (null==lastStatistics?firstStatistics:lastStatistics).getStandardDeviation();
  }
}
