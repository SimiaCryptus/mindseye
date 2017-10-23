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

import com.simiacryptus.mindseye.eval.Trainable;
import com.simiacryptus.mindseye.eval.Trainable.PointSample;
import com.simiacryptus.mindseye.lang.Delta;
import com.simiacryptus.mindseye.lang.DeltaSet;
import com.simiacryptus.mindseye.opt.TrainingMonitor;

import java.util.List;
import java.util.stream.IntStream;

/**
 * The type Simple line search cursor.
 */
public class SimpleLineSearchCursor implements LineSearchCursor {
  /**
   * The Origin.
   */
  public final PointSample origin;
  /**
   * The Direction.
   */
  public final DeltaSet direction;
  /**
   * The Subject.
   */
  public final Trainable subject;
  private final double directionMagnitude;
  private String type = "";
  
  /**
   * Instantiates a new Simple line search cursor.
   *
   * @param subject   the subject
   * @param origin    the origin
   * @param direction the direction
   */
  public SimpleLineSearchCursor(Trainable subject, PointSample origin, DeltaSet direction) {
    this.origin = origin;
    this.direction = direction;
    this.subject = subject;
    double magnitude = direction.getMagnitude();
    this.directionMagnitude = magnitude > 0 ? magnitude : 1;
  }
  
  /**
   * Dot double.
   *
   * @param a the a
   * @param b the b
   * @return the double
   */
  public static double dot(List<Delta> a, List<Delta> b) {
    if (a.size() != b.size()) throw new IllegalArgumentException(String.format("%s != %s", a.size(), b.size()));
    return IntStream.range(0, a.size()).mapToDouble(i -> a.get(i).dot(b.get(i))).sum();
  }
  
  @Override
  public String getDirectionType() {
    return type;
  }
  
  @Override
  public LineSearchPoint step(double alpha, TrainingMonitor monitor) {
    if (!Double.isFinite(alpha)) throw new IllegalArgumentException();
    reset();
    position(alpha).accumulate();
    PointSample sample = subject.measure().setRate(alpha);
    double deltaMagnitude = sample.delta.getMagnitude();
    if (deltaMagnitude <= 0) deltaMagnitude = 1;
    return new LineSearchPoint(sample, dot(direction.vector(), sample.delta.vector()));
  }
  
  @Override
  public DeltaSet position(double alpha) {
    return direction.scale(alpha);
  }
  
  @Override
  public void reset() {
    origin.reset();
  }
  
  /**
   * Sets direction type.
   *
   * @param type the type
   * @return the direction type
   */
  public SimpleLineSearchCursor setDirectionType(String type) {
    this.type = type;
    return this;
  }
}
