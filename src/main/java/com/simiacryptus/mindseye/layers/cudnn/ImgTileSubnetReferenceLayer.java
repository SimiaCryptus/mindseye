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

package com.simiacryptus.mindseye.layers.cudnn;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.DataSerializer;
import com.simiacryptus.mindseye.lang.Layer;
import com.simiacryptus.mindseye.lang.Result;
import com.simiacryptus.mindseye.lang.cudnn.Precision;
import com.simiacryptus.mindseye.layers.java.WrapperLayer;
import com.simiacryptus.mindseye.network.DAGNode;
import com.simiacryptus.mindseye.network.PipelineNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * This layer works as a scaling function, similar to a father wavelet. Allows convolutional and pooling layers to work
 * across larger image regions.
 */
@SuppressWarnings("serial")
public class ImgTileSubnetReferenceLayer extends WrapperLayer implements MultiPrecision<ImgTileSubnetReferenceLayer> {
  
  private static final Logger logger = LoggerFactory.getLogger(ImgTileSubnetReferenceLayer.class);
  private final int height;
  private final int width;
  private final int strideX;
  private final int strideY;
  private Precision precision = Precision.Double;
  private boolean parallel = true;
  
  /**
   * Instantiates a new Rescaled subnet layer.
   *
   * @param subnetwork the subnetwork
   * @param width      the width
   * @param height     the scale
   * @param strideX    the stride x
   * @param strideY    the stride y
   */
  public ImgTileSubnetReferenceLayer(final Layer subnetwork, final int width, final int height, final int strideX, final int strideY) {
    super(subnetwork);
    this.height = height;
    this.width = width;
    this.strideX = strideX;
    this.strideY = strideY;
  }
  
  /**
   * Instantiates a new Img tile subnet layer.
   *
   * @param subnetwork the subnetwork
   * @param width      the width
   * @param height     the height
   */
  public ImgTileSubnetReferenceLayer(final Layer subnetwork, final int width, final int height) {
    this(subnetwork, width, height, width, height);
  }
  
  /**
   * Instantiates a new Rescaled subnet layer.
   *
   * @param json the json
   * @param rs   the rs
   */
  protected ImgTileSubnetReferenceLayer(@Nonnull final JsonObject json, Map<String, byte[]> rs) {
    super(json, rs);
    this.precision = Precision.valueOf(json.getAsJsonPrimitive("precision").getAsString());
    height = json.getAsJsonPrimitive("height").getAsInt();
    width = json.getAsJsonPrimitive("width").getAsInt();
    strideX = json.getAsJsonPrimitive("strideX").getAsInt();
    strideY = json.getAsJsonPrimitive("strideY").getAsInt();
    setParallel(json.get("parallel").getAsBoolean());
    this.parallel = json.get("parallel").getAsBoolean();
  }
  
  /**
   * From json rescaled subnet layer.
   *
   * @param json the json
   * @param rs   the rs
   * @return the rescaled subnet layer
   */
  public static ImgTileSubnetReferenceLayer fromJson(@Nonnull final JsonObject json, Map<String, byte[]> rs) {
    return new ImgTileSubnetReferenceLayer(json, rs);
  }
  
  @Override
  protected void _free() {
    super._free();
  }
  
  @Nullable
  @Override
  public Result evalAndFree(@Nonnull final Result... inObj) {
    assert 1 == inObj.length;
    @Nonnull final int[] inputDims = inObj[0].getData().getDimensions();
    assert 3 == inputDims.length;
    @Nonnull final PipelineNetwork network = new PipelineNetwork();
    try {
      int cols = (int) (Math.ceil((inputDims[0] - width) * 1.0 / strideX) + 1);
      int rows = (int) (Math.ceil((inputDims[1] - height) * 1.0 / strideY) + 1);
      if (cols == 1 && rows == 1) return getInner().evalAndFree(inObj);
      DAGNode input = network.getInput(0);
      ArrayList<DAGNode> nodes = new ArrayList<>();
      for (int row = 0; row < rows; row++) {
        for (int col = 0; col < cols; col++) {
          int positionX = col * strideX;
          int positionY = row * strideY;
          assert positionX >= 0;
          assert positionY >= 0;
          assert positionX < inputDims[0];
          assert positionY < inputDims[1];
          nodes.add(
            network.add(getInner(),
              network.wrap(
                new ImgTileSelectLayer(width, height, positionX, positionY).setPrecision(precision),
                input))
          );
        }
      }
      logger.debug(String.format("Broke input %s into %s rows, %s cols", Arrays.toString(inputDims), rows, cols));
      network.wrap(new ImgTileAssemblyLayer(cols, rows).setParallel(parallel).setPrecision(precision), nodes.toArray(new DAGNode[]{})).setParallel(parallel);
      return network.evalAndFree(inObj);
    } finally {
      network.freeRef();
    }
  }
  
  @Nonnull
  @Override
  public JsonObject getJson(Map<String, byte[]> resources, DataSerializer dataSerializer) {
    @Nonnull final JsonObject json = super.getJson(resources, dataSerializer);
    json.addProperty("height", height);
    json.addProperty("width", width);
    json.addProperty("strideX", strideX);
    json.addProperty("strideY", strideY);
    json.addProperty("precision", precision.name());
    json.addProperty("parallel", isParallel());
    json.addProperty("parallel", isParallel());
    return json;
  }
  
  
  @Nonnull
  @Override
  public List<double[]> state() {
    return new ArrayList<>();
  }
  
  
  @Override
  public Precision getPrecision() {
    return precision;
  }
  
  @Nonnull
  @Override
  public ImgTileSubnetReferenceLayer setPrecision(Precision precision) {
    this.precision = precision;
    return this;
  }
  
  @Nonnull
  @Override
  public Layer setFrozen(final boolean frozen) {
    getInner().setFrozen(frozen);
    return super.setFrozen(frozen);
  }
  
  /**
   * Is parallel boolean.
   *
   * @return the boolean
   */
  public boolean isParallel() {
    return parallel;
  }
  
  /**
   * Sets parallel.
   *
   * @param parallel the parallel
   * @return the parallel
   */
  public ImgTileSubnetReferenceLayer setParallel(boolean parallel) {
    this.parallel = parallel;
    return this;
  }
}
