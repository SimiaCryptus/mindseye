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

package com.simiacryptus.mindseye.network.graph;

import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.NNResult;

import java.util.UUID;

/**
 * The type Input node.
 */
final class InputNode extends LazyResult {
  /**
   * The Handle.
   */
  public final UUID handle;
  private final DAGNetwork dagNetwork;
  
  /**
   * Instantiates a new Input node.
   *
   * @param dagNetwork the dag network
   */
  InputNode(DAGNetwork dagNetwork) {
    this(dagNetwork, null);
  }
  
  /**
   * Instantiates a new Input node.
   *
   * @param dagNetwork the dag network
   * @param handle     the handle
   */
  public InputNode(DAGNetwork dagNetwork, final UUID handle) {
    super(handle);
    this.dagNetwork = dagNetwork;
    this.handle = handle;
  }
  
  @Override
  protected NNResult eval(final EvaluationContext t, NNLayer.NNExecutionContext nncontext) {
    return t.cache.get(this.handle);
  }
  
  @Override
  public UUID getId() {
    return handle;
  }
  
  @Override
  public NNLayer getLayer() {
    return null;
  }
  
  /**
   * Add dag node.
   *
   * @param nextHead the next head
   * @return the dag node
   */
  public DAGNode add(NNLayer nextHead) {
    return dagNetwork.add(nextHead, InputNode.this);
  }
}
