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

package com.simiacryptus.mindseye.layers.java;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * The type Static scalar loss layer.
 */
@SuppressWarnings("serial")
public class StaticScalarLossLayer extends NNLayer {
  
  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(StaticScalarLossLayer.class);
  private double target = 0.0;
  
  /**
   * Instantiates a new Static scalar loss layer.
   */
  public StaticScalarLossLayer() {
  }
  
  
  /**
   * Instantiates a new Static scalar loss layer.
   *
   * @param id the id
   */
  protected StaticScalarLossLayer(final @NotNull JsonObject id) {
    super(id);
  }
  
  /**
   * From json static scalar loss layer.
   *
   * @param json the json
   * @param rs   the rs
   * @return the static scalar loss layer
   */
  public static StaticScalarLossLayer fromJson(final @NotNull JsonObject json, Map<String, byte[]> rs) {
    return new StaticScalarLossLayer(json);
  }
  
  @Override
  public @NotNull NNResult eval(final @NotNull NNResult... inObj) {
    if (1 != inObj.length) throw new IllegalArgumentException();
    Arrays.stream(inObj).forEach(nnResult -> nnResult.addRef());
    //if (inObj[0].getData().length() != 1) throw new IllegalArgumentException();
    final NNResult in0 = inObj[0];
    TensorList indata = in0.getData();
    indata.addRef();
    return new NNResult(TensorArray.wrap(IntStream.range(0, indata.length()).parallel().mapToObj(dataIndex -> {
      final Tensor a = indata.get(dataIndex);
      final double diff = Math.abs(a.get(0) - getTarget());
      return new Tensor(new double[]{diff}, 1);
    }).toArray(i -> new Tensor[i])), (final @NotNull DeltaSet<NNLayer> buffer, final @NotNull TensorList data) -> {
      if (in0.isAlive()) {
        @NotNull TensorArray tensorArray = TensorArray.wrap(IntStream.range(0, data.length()).parallel().mapToObj(dataIndex -> {
          final Tensor a = indata.get(dataIndex);
          final double deriv = data.get(dataIndex).get(0) * (a.get(0) - getTarget() < 0 ? -1 : 1);
          return new Tensor(new double[]{deriv}, 1);
        }).toArray(i -> new Tensor[i]));
        in0.accumulate(buffer, tensorArray);
        tensorArray.freeRef();
      }
    }) {
      
      @Override
      protected void _free() {
        indata.freeRef();
        Arrays.stream(inObj).forEach(nnResult -> nnResult.freeRef());
      }
  
  
      @Override
      public boolean isAlive() {
        return in0.isAlive();
      }
      
    };
  }
  
  @Override
  public @NotNull JsonObject getJson(Map<String, byte[]> resources, DataSerializer dataSerializer) {
    return super.getJsonStub();
  }
  
  /**
   * Gets target.
   *
   * @return the target
   */
  public double getTarget() {
    return target;
  }
  
  /**
   * Sets target.
   *
   * @param target the target
   * @return the target
   */
  public @NotNull StaticScalarLossLayer setTarget(final double target) {
    this.target = target;
    return this;
  }
  
  @Override
  public @NotNull List<double[]> state() {
    return Arrays.asList();
  }
}
