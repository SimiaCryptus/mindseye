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
import com.simiacryptus.mindseye.lang.NNExecutionContext;
import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.NNResult;

import java.util.List;

/**
 * The type Wrapper layer.
 */
public abstract class WrapperLayer extends NNLayer {
  /**
   * The Inner.
   */
  protected NNLayer inner;
  
  /**
   * Instantiates a new Wrapper layer.
   *
   * @param json the json
   */
  public WrapperLayer(JsonObject json) {
    super(json);
    this.inner = fromJson(json.getAsJsonObject("inner"));
  }
  
  /**
   * Instantiates a new Wrapper layer.
   *
   * @param inner the inner
   */
  public WrapperLayer(NNLayer inner) {
    this.inner = inner;
  }
  
  /**
   * Instantiates a new Wrapper layer.
   */
  protected WrapperLayer() {
    this.inner = null;
  }
  
  @Override
  public List<double[]> state() {
    return this.inner.state();
  }
  
  @Override
  public NNResult eval(NNExecutionContext nncontext, final NNResult... array) {
    return inner.eval(nncontext, array);
  }
  
  /**
   * Gets inner.
   *
   * @return the inner
   */
  public final NNLayer getInner() {
    return this.inner;
  }
  
  @Override
  public boolean isFrozen() {
    if (null == inner) return true;
    return inner.isFrozen();
  }
  
  @Override
  public NNLayer setFrozen(boolean frozen) {
    if (inner != null) inner.setFrozen(frozen);
    return this;
  }
}
