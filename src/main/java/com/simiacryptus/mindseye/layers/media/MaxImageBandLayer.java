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

package com.simiacryptus.mindseye.layers.media;

import com.google.gson.JsonObject;
import com.simiacryptus.lang.Tuple2;
import com.simiacryptus.mindseye.layers.DeltaSet;
import com.simiacryptus.mindseye.layers.NNLayer;
import com.simiacryptus.mindseye.layers.NNResult;
import com.simiacryptus.util.Util;
import com.simiacryptus.util.io.JsonUtil;
import com.simiacryptus.util.ml.Tensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MaxImageBandLayer extends NNLayer {
  
  public JsonObject getJson() {
    JsonObject json = super.getJsonStub();
    return json;
  }
  
  public static MaxImageBandLayer fromJson(JsonObject json) {
    return new MaxImageBandLayer(json,
                                 JsonUtil.getIntArray(json.getAsJsonArray("inner")));
  }
  protected MaxImageBandLayer(JsonObject id, int... kernelDims) {
    super(id);
  }
  
  
  private static final Function<MaxImageBandLayer.CalcRegionsParameter, List<Tuple2<Integer, int[]>>> calcRegionsCache = Util.cache(MaxImageBandLayer::calcRegions);
  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(MaxImageBandLayer.class);
  
  public MaxImageBandLayer() {
    super();
  }
  
  private static List<Tuple2<Integer, int[]>> calcRegions(final MaxImageBandLayer.CalcRegionsParameter p) {
    final Tensor input = new Tensor(p.inputDims);
    final int[] newDims = IntStream.range(0, p.inputDims.length).map(i -> {
      //assert 0 == p.inputDims[i] % p.kernelDims[i];
      return (int) Math.ceil(p.inputDims[i] * 1.0 / p.kernelDims[i]);
    }).toArray();
    final Tensor output = new Tensor(newDims);
    
    return output.coordStream(false).map(o -> {
      final int[] inCoords = new Tensor(p.kernelDims).coordStream(false).mapToInt(kernelCoord -> {
        final int[] result = new int[o.coords.length];
        for (int index = 0; index < o.coords.length; index++) {
          int outputCoordinate = o.coords[index];
          int kernelSize = p.kernelDims[index];
          int baseCoordinate = Math.min(outputCoordinate * kernelSize, p.inputDims[index] - kernelSize);
          int kernelCoordinate = kernelCoord.coords[index];
          result[index] = baseCoordinate + kernelCoordinate;
        }
        return input.index(result);
      }).toArray();
      return new Tuple2<>(o.index, inCoords);
    }).collect(Collectors.toList());
  }
  
  @Override
  public NNResult eval(final NNResult... inObj) {
  
    final NNResult in = inObj[0];
    int itemCnt = in.data.length;
    
    final int[] inputDims = in.data[0].getDims();
      final List<Tuple2<Integer, int[]>> regions = calcRegionsCache.apply(new MaxImageBandLayer.CalcRegionsParameter(inputDims, inputDims));
    Tensor[] outputA = IntStream.range(0, in.data.length).mapToObj(dataIndex -> {
      final int[] newDims = IntStream.range(0, inputDims.length).map(i -> 1).toArray();
      final Tensor output = new Tensor(newDims);
      return output;
    }).toArray(i -> new Tensor[i]);
    int sum = Arrays.stream(outputA).mapToInt(x -> x.dim()).sum();
    @SuppressWarnings("unchecked") final int[][] gradientMapA = new int[in.data.length][];
    IntStream.range(0, in.data.length).forEach(dataIndex -> {
      final Tensor input = in.data[dataIndex];
      final Tensor output = outputA[dataIndex];
      final IntToDoubleFunction keyExtractor = inputCoords -> input.get(inputCoords);
      int[] gradientMap = new int[input.dim()];
      regions.parallelStream().forEach(tuple -> {
        final Integer from = tuple.getFirst();
        int[] toList = tuple.getSecond();
        int toMax = -1;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (int c : toList) {
          double value = keyExtractor.applyAsDouble(c);
          if (-1 == toMax || bestValue < value) {
            bestValue = value;
            toMax = c;
          }
        }
        gradientMap[from] = toMax;
        output.set(from, input.get(toMax));
      });
      gradientMapA[dataIndex] = gradientMap;
    });
    return new NNResult(outputA) {
      @Override
      public void accumulate(final DeltaSet buffer, final Tensor[] data) {
        if (in.isAlive()) {
          Tensor[] passbackA = IntStream.range(0, in.data.length).parallel().mapToObj(dataIndex -> {
            final Tensor backSignal = new Tensor(inputDims);
            int[] ints = gradientMapA[dataIndex];
            Tensor datum = data[dataIndex];
            for(int i=0;i<datum.dim();i++){
              backSignal.add(ints[i], datum.get(i));
            }
            return backSignal;
          }).toArray(i -> new Tensor[i]);
          in.accumulate(buffer, passbackA);
        }
      }
      
      @Override
      public boolean isAlive() {
        return in.isAlive();
      }
    };
  }
  
  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }
  
  public static class CalcRegionsParameter {
    public int[] inputDims;
    public int[] kernelDims;
    
    public CalcRegionsParameter(final int[] inputDims, final int[] kernelDims) {
      this.inputDims = inputDims;
      this.kernelDims = kernelDims;
    }
    
    @Override
    public boolean equals(final Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      final MaxImageBandLayer.CalcRegionsParameter other = (MaxImageBandLayer.CalcRegionsParameter) obj;
      if (!Arrays.equals(this.inputDims, other.inputDims))
        return false;
      return Arrays.equals(this.kernelDims, other.kernelDims);
    }
    
    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + Arrays.hashCode(this.inputDims);
      result = prime * result + Arrays.hashCode(this.kernelDims);
      return result;
    }
    
  }
}