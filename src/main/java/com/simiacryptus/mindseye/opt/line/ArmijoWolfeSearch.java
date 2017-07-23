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

public class ArmijoWolfeSearch implements LineSearchStrategy {
  
  private double minAlpha = 1e-15;
  private double maxAlpha = 1e2;
  private double c1 = 1e-6;
  private double c2 = 0.9;
  private double alpha = 1.0;
  private double alphaGrowth = Math.pow(10.0, Math.pow(3.0, -1.0));
  private boolean strongWolfe = true;
  
  @Override
  public PointSample step(LineSearchCursor cursor, TrainingMonitor monitor) {
    alpha *= alphaGrowth; // Keep memory of alpha from one iteration to next, but have a bias for growing the value
    double mu = 0;
    double nu = Double.POSITIVE_INFINITY;
    final LineSearchPoint startPoint = cursor.step(0, monitor);
    final double startLineDeriv = startPoint.derivative; // theta'(0)
    if(0<=startPoint.derivative) return cursor.step(0, monitor).point;
    final double startValue = startPoint.point.value; // theta(0)
    monitor.log(String.format("th(0)=%s;dx=%s", startValue, startLineDeriv));
    LineSearchPoint lastStep = null;
    int stepBias = 0;
    while (true) {
      if (!isAlphaValid()) {
        monitor.log(String.format("INVALID ALPHA: th(0)=%s;th'(0)=%s;", startValue, startLineDeriv));
        return cursor.step(0, monitor).point;
      }
      double lastValue = (null == lastStep)?Double.POSITIVE_INFINITY:lastStep.point.value;
      if(!Double.isFinite(lastValue)) lastValue = Double.POSITIVE_INFINITY;
      if (mu >= nu) {
        monitor.log(String.format("mu >= nu: th(0)=%s;th'(0)=%s;", startValue, startLineDeriv));
        loosenMetaparameters();
        if(null != lastStep && lastValue < startValue) return lastStep.point;
        return cursor.step(0, monitor).point;
      }
      if ((nu / mu) < (11.0 / 10.0)) {
        monitor.log(String.format("mu >= nu: th(0)=%s;th'(0)=%s;", startValue, startLineDeriv));
        loosenMetaparameters();
        if(null != lastStep && lastValue < startValue) return lastStep.point;
        return cursor.step(0, monitor).point;
      }
      if (Math.abs(alpha) < minAlpha) {
        alpha = 1;
        monitor.log(String.format("MIN ALPHA: th(0)=%s;th'(0)=%s;", startValue, startLineDeriv));
        if(null != lastStep && lastValue < startValue) return lastStep.point;
        return cursor.step(0, monitor).point;
      }
      if (Math.abs(alpha) > maxAlpha) {
        alpha = 1;
        monitor.log(String.format("MAX ALPHA: th(0)=%s;th'(0)=%s;", startValue, startLineDeriv));
        if(null != lastStep && lastValue < startValue) return lastStep.point;
        return cursor.step(0, monitor).point;
      }
      lastStep = cursor.step(alpha, monitor);
      lastValue = lastStep.point.value;
      if(!Double.isFinite(lastValue)) lastValue = Double.POSITIVE_INFINITY;
      if (lastValue > startValue + alpha * c1 * startLineDeriv) {
        // Value did not decrease (enough) - It is gauranteed to decrease given an infitefimal rate; the rate must be less than this; this is a new ceiling
        monitor.log(String.format("Armijo: th(%s)=%s; dx=%s delta=%s", alpha, lastValue, lastStep.derivative, startValue - lastValue));
        nu = alpha;
        stepBias = Math.min(-1, stepBias-1);
      } else  if (isStrongWolfe() && lastStep.derivative > 0) {
        // If the slope is increasing, then we can go lower by choosing a lower rate; this is a new ceiling
        monitor.log(String.format("WOLF (strong): th(%s)=%s; dx=%s delta=%s", alpha, lastValue, lastStep.derivative, startValue - lastValue));
        nu = alpha;
        stepBias = Math.min(-1, stepBias-1);
      } else if (lastStep.derivative < c2 * startLineDeriv) {
        // Current slope decreases at no more than X - If it is still decreasing that fast, we know we want a rate of least this value; this is a new floor
        monitor.log(String.format("WOLFE (weak): th(%s)=%s; dx=%s delta=%s", alpha, lastValue, lastStep.derivative, startValue - lastValue));
        mu = alpha;
        stepBias = Math.max(1, stepBias+1);
      } else {
        monitor.log(String.format("END: th(%s)=%s; dx=%s delta=%s", alpha, lastValue, lastStep.derivative, startValue - lastValue));
        stepBias = 0;
        return lastStep.point;
      }
      if (!Double.isFinite(nu)) {
        alpha = (1+Math.abs(stepBias)) * alpha;
      } else if (0.0 == mu) {
        alpha = nu / (1+Math.abs(stepBias));
      } else {
        alpha = (mu + nu) / 2;
      }
    }
  }
  
  public void loosenMetaparameters() {
    c1 *= 0.2;
    c2 = Math.pow(c2,c2<1?1.5:(1/1.5));
    strongWolfe = false;
  }
  
  private boolean isAlphaValid() {
    return Double.isFinite(alpha) && (0 <= alpha);
  }
  
  public double getAlphaGrowth() {
    return alphaGrowth;
  }
  
  public ArmijoWolfeSearch setAlphaGrowth(double alphaGrowth) {
    this.alphaGrowth = alphaGrowth;
    return this;
  }
  
  public double getC1() {
    return c1;
  }
  
  public ArmijoWolfeSearch setC1(double c1) {
    this.c1 = c1;
    return this;
  }
  
  public double getC2() {
    return c2;
  }
  
  public ArmijoWolfeSearch setC2(double c2) {
    this.c2 = c2;
    return this;
  }
  
  public double getAlpha() {
    return alpha;
  }
  
  public ArmijoWolfeSearch setAlpha(double alpha) {
    this.alpha = alpha;
    return this;
  }
  
  public double getMinAlpha() {
    return minAlpha;
  }
  
  public ArmijoWolfeSearch setMinAlpha(double minAlpha) {
    this.minAlpha = minAlpha;
    return this;
  }
  
  public double getMaxAlpha() {
    return maxAlpha;
  }
  
  public ArmijoWolfeSearch setMaxAlpha(double maxAlpha) {
    this.maxAlpha = maxAlpha;
    return this;
  }
  
  public boolean isStrongWolfe() {
    return strongWolfe;
  }
  
  public ArmijoWolfeSearch setStrongWolfe(boolean strongWolfe) {
    this.strongWolfe = strongWolfe;
    return this;
  }
}