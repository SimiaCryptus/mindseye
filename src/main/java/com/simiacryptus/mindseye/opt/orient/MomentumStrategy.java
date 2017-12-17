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

package com.simiacryptus.mindseye.opt.orient;

import com.simiacryptus.mindseye.eval.Trainable;
import com.simiacryptus.mindseye.lang.DeltaSet;
import com.simiacryptus.mindseye.lang.DoubleBuffer;
import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.PointSample;
import com.simiacryptus.mindseye.opt.TrainingMonitor;
import com.simiacryptus.mindseye.opt.line.LineSearchCursor;
import com.simiacryptus.mindseye.opt.line.SimpleLineSearchCursor;
import com.simiacryptus.util.ArrayUtil;

/**
 * A simple momentum module which uses a cumulative decay algorithm
 * to add a momentum term to any orientation strategy
 * (if it yields a SimpleLineSearch cursor)
 */
public class MomentumStrategy implements OrientationStrategy<SimpleLineSearchCursor> {
  
  /**
   * The Inner.
   */
  public final OrientationStrategy<SimpleLineSearchCursor> inner;
  /**
   * The Prev delta.
   */
  DeltaSet<NNLayer> prevDelta = new DeltaSet<NNLayer>();
  private double carryOver = 0.1;
  
  /**
   * Instantiates a new Momentum strategy.
   *
   * @param inner the inner
   */
  public MomentumStrategy(final OrientationStrategy<SimpleLineSearchCursor> inner) {
    this.inner = inner;
  }
  
  /**
   * Gets carry over.
   *
   * @return the carry over
   */
  public double getCarryOver() {
    return carryOver;
  }
  
  /**
   * Sets carry over.
   *
   * @param carryOver the carry over
   * @return the carry over
   */
  public MomentumStrategy setCarryOver(final double carryOver) {
    this.carryOver = carryOver;
    return this;
  }
  
  @Override
  public SimpleLineSearchCursor orient(final Trainable subject, final PointSample measurement, final TrainingMonitor monitor) {
    final LineSearchCursor orient = inner.orient(subject, measurement, monitor);
    final DeltaSet<NNLayer> direction = ((SimpleLineSearchCursor) orient).direction;
    final DeltaSet<NNLayer> newDelta = new DeltaSet<NNLayer>();
    direction.getMap().forEach((layer, delta) -> {
      final DoubleBuffer<NNLayer> prevBuffer = prevDelta.get(layer, delta.target);
      newDelta.get(layer, delta.target).addInPlace(ArrayUtil.add(ArrayUtil.multiply(prevBuffer.getDelta(), carryOver), delta.getDelta()));
    });
    prevDelta = newDelta;
    return new SimpleLineSearchCursor(subject, measurement, newDelta);
  }
  
  @Override
  public void reset() {
    inner.reset();
  }
}
