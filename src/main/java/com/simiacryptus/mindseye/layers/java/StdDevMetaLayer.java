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

package com.simiacryptus.mindseye.layers.java;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.network.DAGNode;
import com.simiacryptus.mindseye.network.PipelineNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * The type Std dev meta layer.
 */
@SuppressWarnings("serial")
public class StdDevMetaLayer extends PipelineNetwork {
  
  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(StdDevMetaLayer.class);
  
  /**
   * Instantiates a new Std dev meta layer.
   */
  public StdDevMetaLayer() {
    this(1);
  }
  
  /**
   * Instantiates a new Std dev meta layer.
   *
   * @param minBatchCount the min batch count
   */
  public StdDevMetaLayer(final int minBatchCount) {
    super(1);
    add(new AvgMetaLayer().setMinBatchCount(minBatchCount));
    add(new AvgReducerLayer());
    add(new SqActivationLayer());
    final DAGNode a = add(new LinearActivationLayer().setScale(-1).freeze());
    add(new SqActivationLayer(), getInput(0));
    add(new AvgMetaLayer().setMinBatchCount(minBatchCount));
    add(new AvgReducerLayer());
    add(new SumInputsLayer(), getHead(), a);
    add(new NthPowerActivationLayer().setPower(0.5));
  }
  
  /**
   * Instantiates a new Std dev meta layer.
   *
   * @param json the json
   * @param rs
   */
  protected StdDevMetaLayer(final JsonObject json, Map<String, byte[]> rs) {
    super(json, rs);
  }
  
  /**
   * From json std dev meta layer.
   *
   * @param json the json
   * @return the std dev meta layer
   */
  public static StdDevMetaLayer fromJson(final JsonObject json, Map<String, byte[]> rs) {
    return new StdDevMetaLayer(json, rs);
  }
  
}
