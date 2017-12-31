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

package com.simiacryptus.mindseye.layers.cudnn;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.DataSerializer;
import com.simiacryptus.mindseye.lang.NNExecutionContext;
import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.NNResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * This layer works as a scaling function, similar to a father wavelet. Allows convolutional and pooling layers to work
 * across larger image regions. Implemented via CuDNN.
 */
@SuppressWarnings("serial")
public class RescaledSubnetLayer extends NNLayer implements LayerPrecision<RescaledSubnetLayer> {
  private static final Logger logger = LoggerFactory.getLogger(RescaledSubnetLayer.class);
  
  private int scale;
  private NNLayer layer;
  private Precision precision = Precision.Double;
  
  /**
   * Instantiates a new Img concat layer.
   */
  private RescaledSubnetLayer() {
  }
  
  public RescaledSubnetLayer(int scale, NNLayer layer) {
    this.scale = scale;
    this.layer = layer;
  }
  
  /**
   * Instantiates a new Img concat layer.
   *
   * @param json the json
   * @param rs
   */
  protected RescaledSubnetLayer(final JsonObject json, Map<String, byte[]> rs) {
    super(json);
    scale = json.get("scale").getAsInt();
    layer = NNLayer.fromJson(json, rs);
    this.precision = Precision.valueOf(json.getAsJsonPrimitive("precision").getAsString());
  }
  
  /**
   * From json img concat layer.
   *
   * @param json the json
   * @param rs   the rs
   * @return the img concat layer
   */
  public static RescaledSubnetLayer fromJson(final JsonObject json, Map<String, byte[]> rs) {
    return new RescaledSubnetLayer(json, rs);
  }
  
  public NNLayer getCompatibilityLayer() {
    return new com.simiacryptus.mindseye.layers.java.RescaledSubnetLayer(scale, layer);
  }
  
  @Override
  public NNResult eval(final NNExecutionContext nncontext, final NNResult... inObj) {
    if (((CudaExecutionContext) nncontext).getDeviceNumber() < 0) return getCompatibilityLayer().eval(nncontext, inObj);
    
    logger.warn("Not Implemented: " + getClass().getCanonicalName());
    return getCompatibilityLayer().eval(nncontext, inObj);
  }
  
  @Override
  public JsonObject getJson(Map<String, byte[]> resources, DataSerializer dataSerializer) {
    final JsonObject json = super.getJsonStub();
    json.addProperty("scale", scale);
    json.add("layer", layer.getJson(resources, dataSerializer));
    json.addProperty("precision", precision.name());
    return json;
  }
  
  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }
  
  @Override
  public Precision getPrecision() {
    return precision;
  }
  
  @Override
  public RescaledSubnetLayer setPrecision(final Precision precision) {
    this.precision = precision;
    return this;
  }
}