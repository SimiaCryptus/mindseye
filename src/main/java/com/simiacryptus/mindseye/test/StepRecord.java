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

package com.simiacryptus.mindseye.test;

/**
 * The type Step record.
 */
public class StepRecord {
  /**
   * The Fitness.
   */
  public final double fitness;
  /**
   * The Epoch time.
   */
  public final long epochTime;
  /**
   * The Iteraton.
   */
  public final long iteraton;
  
  /**
   * Instantiates a new Step record.
   *
   * @param fitness   the fitness
   * @param epochTime the epoch time
   * @param iteraton  the iteraton
   */
  public StepRecord(double fitness, long epochTime, long iteraton) {
    this.fitness = fitness;
    this.epochTime = epochTime;
    this.iteraton = iteraton;
  }
}