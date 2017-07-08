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

package com.simiacryptus.mindseye.opt.line;

import com.simiacryptus.mindseye.opt.TrainingMonitor;
import com.simiacryptus.mindseye.opt.trainable.Trainable.PointSample;

public class StaticLearningRate implements LineSearchStrategy {
  
  private double rate = 1e-4;
  private double minimumRate = 1e-12;
  
  @Override
  public PointSample step(LineSearchCursor cursor, TrainingMonitor monitor) {
    double thisRate = rate;
    LineSearchPoint startPoint = cursor.step(0, monitor);
    double startLineDeriv = startPoint.derivative; // theta'(0)
    double startValue = startPoint.point.value; // theta(0)
    LineSearchPoint lastStep = null;
    while (true) {
      double lastValue = (null == lastStep)?Double.POSITIVE_INFINITY:lastStep.point.value;
      if(!Double.isFinite(lastValue)) lastValue = Double.POSITIVE_INFINITY;
      lastStep = cursor.step(thisRate, monitor);
      lastValue = lastStep.point.value;
      if(!Double.isFinite(lastValue)) lastValue = Double.POSITIVE_INFINITY;
      if (lastValue > startValue) {
        monitor.log(String.format("Non-decreasing step. %s > %s at " + thisRate, lastValue, startValue));
        thisRate /= 2;
        if(thisRate < getMinimumRate()) {
          return startPoint.point;
        }
      } else {
        return lastStep.point;
      }
    }
  }
  
  public double getRate() {
    return rate;
  }
  
  public StaticLearningRate setRate(double rate) {
    this.rate = rate;
    return this;
  }
  
  public double getMinimumRate() {
    return minimumRate;
  }
  
  public StaticLearningRate setMinimumRate(double minimumRate) {
    this.minimumRate = minimumRate;
    return this;
  }
}