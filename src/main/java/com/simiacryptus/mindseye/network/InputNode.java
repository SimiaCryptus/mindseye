/*
 * Copyright (c) 2018 by Andrew Charneski.
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

package com.simiacryptus.mindseye.network;

import com.simiacryptus.mindseye.lang.NNExecutionContext;
import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.NNResult;

import java.util.UUID;

/**
 * A node providing access to given inputs for NNLayer evaluation.
 */
@SuppressWarnings("serial")
final class InputNode extends LazyResult {
  private final DAGNetwork dagNetwork;
  
  /**
   * Instantiates a new Input node.
   *
   * @param dagNetwork the dag network
   */
  InputNode(final DAGNetwork dagNetwork) {
    this(dagNetwork, null);
  }
  
  /**
   * Instantiates a new Input node.
   *
   * @param dagNetwork the dag network
   * @param key        the key
   */
  public InputNode(final DAGNetwork dagNetwork, final UUID key) {
    super(key);
    this.dagNetwork = dagNetwork;
  }
  
  /**
   * Add dag node.
   *
   * @param nextHead the next head
   * @return the dag node
   */
  public DAGNode add(final NNLayer nextHead) {
    return dagNetwork.add(nextHead, InputNode.this);
  }
  
  @Override
  protected NNResult eval(final GraphEvaluationContext context, final NNExecutionContext nncontext) {
    return context.cache.get(id);
  }
  
  @Override
  public <T extends NNLayer> T getLayer() {
    return null;
  }
  
  @Override
  public void setLayer(final NNLayer layer) {
    throw new IllegalStateException();
  }
}
