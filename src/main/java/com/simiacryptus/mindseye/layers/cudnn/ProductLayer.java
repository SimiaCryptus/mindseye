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
import com.simiacryptus.mindseye.lang.*;
import jcuda.jcudnn.cudnnOpTensorDescriptor;
import jcuda.jcudnn.cudnnTensorDescriptor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static jcuda.jcudnn.JCudnn.cudnnOpTensor;
import static jcuda.jcudnn.cudnnOpTensorOp.CUDNN_OP_TENSOR_MUL;
import static jcuda.jcudnn.cudnnTensorFormat.CUDNN_TENSOR_NCHW;

/**
 * The type Product inputs layer.
 */
public class ProductLayer extends NNLayer implements LayerPrecision<ProductLayer> {
  
  private Precision precision = Precision.Double;
  
  /**
   * Instantiates a new Product inputs layer.
   *
   * @param id the id
   */
  protected ProductLayer(JsonObject id) {
    super(id);
  }
  
  /**
   * Instantiates a new Product inputs layer.
   */
  public ProductLayer() {
  }
  
  /**
   * From json product inputs layer.
   *
   * @param json the json
   * @return the product inputs layer
   */
  public static ProductLayer fromJson(JsonObject json) {
    return new ProductLayer(json);
  }
  
  public JsonObject getJson() {
    return super.getJsonStub();
  }
  
  @Override
  public NNResult eval(NNExecutionContext nncontext, final NNResult... inObj) {
    ((CudaExecutionContext) nncontext).initThread();
    if (inObj.length <= 1) {
      throw new IllegalArgumentException("inObj.length=" + inObj.length);
    }
    int[] dimensions = inObj[0].getData().getDimensions();
    int length = inObj[0].getData().length();
    if (3 != dimensions.length) {
      throw new IllegalArgumentException("dimensions=" + Arrays.toString(dimensions));
    }
    for (int i = 1; i < inObj.length; i++) {
      if (Tensor.dim(dimensions) != Tensor.dim(inObj[i].getData().getDimensions())) {
        throw new IllegalArgumentException(Arrays.toString(dimensions) + " != " + Arrays.toString(inObj[i].getData().getDimensions()));
      }
    }
    final CudaResource<cudnnOpTensorDescriptor> opDescriptor = CuDNN.newOpDescriptor(CUDNN_OP_TENSOR_MUL, precision.code);
    CudaResource<cudnnTensorDescriptor> sizeDescriptor = CuDNN.newTensorDescriptor(
      precision.code, CUDNN_TENSOR_NCHW, length, dimensions[2], dimensions[1], dimensions[0]);
    TensorList result = Arrays.stream(inObj).map(x -> x.getData()).reduce((l, r) -> {
      CudaPtr lPtr = CudaPtr.write(((CudaExecutionContext) nncontext).getDeviceNumber(), this.precision, l);
      CudaPtr rPtr = CudaPtr.write(((CudaExecutionContext) nncontext).getDeviceNumber(), this.precision, r);
      assert lPtr.size == rPtr.size;
      CudaPtr outputPtr = CuDNN.alloc(((CudaExecutionContext) nncontext).getDeviceNumber(), lPtr.size);
      CuDNN.handle(cudnnOpTensor(((CuDNN) nncontext).cudnnHandle, opDescriptor.getPtr(),
        precision.getPointer(1.0f), sizeDescriptor.getPtr(), lPtr.getPtr(),
        precision.getPointer(1.0f), sizeDescriptor.getPtr(), rPtr.getPtr(),
        precision.getPointer(0.0f), sizeDescriptor.getPtr(), outputPtr.getPtr()));
      return new GpuTensorList(outputPtr, length, dimensions, ((CuDNN) nncontext).cudnnHandle, this.precision);
    }).get();
    
    return new NNResult(result) {
      @Override
      public void accumulate(final DeltaSet buffer, final TensorList delta) {
        ((CudaExecutionContext) nncontext).initThread();
        assert delta.stream().flatMapToDouble(x -> Arrays.stream(x.getData())).allMatch(v -> Double.isFinite(v));
        for (int index = 0; index < inObj.length; index++) {
          final NNResult input = inObj[index];
          if (input.isAlive()) {
            int _index = index;
            input.accumulate(buffer, IntStream.range(0, inObj.length).mapToObj(i -> i == _index ? delta : inObj[i].getData()).reduce((l, r) -> {
              CudaPtr lPtr = CudaPtr.write(((CudaExecutionContext) nncontext).getDeviceNumber(), ProductLayer.this.precision, l);
              CudaPtr rPtr = CudaPtr.write(((CudaExecutionContext) nncontext).getDeviceNumber(), ProductLayer.this.precision, r);
              assert lPtr.size == rPtr.size;
              CudaPtr outputPtr = CuDNN.alloc(((CudaExecutionContext) nncontext).getDeviceNumber(), lPtr.size);
              CuDNN.handle(cudnnOpTensor(((CuDNN) nncontext).cudnnHandle, opDescriptor.getPtr(),
                precision.getPointer(1.0f), sizeDescriptor.getPtr(), lPtr.getPtr(),
                precision.getPointer(1.0f), sizeDescriptor.getPtr(), rPtr.getPtr(),
                precision.getPointer(0.0f), sizeDescriptor.getPtr(), outputPtr.getPtr()));
              return new GpuTensorList(outputPtr, length, dimensions, ((CuDNN) nncontext).cudnnHandle, ProductLayer.this.precision);
            }).get());
          }
        }
      }
      
      @Override
      public boolean isAlive() {
        for (final NNResult element : inObj)
          if (element.isAlive()) {
            return true;
          }
        return false;
      }
      
    };
  }
  
  @Override
  public List<double[]> state() {
    return Arrays.asList();
  }
  
  public Precision getPrecision() {
    return precision;
  }
  
  public ProductLayer setPrecision(Precision precision) {
    this.precision = precision;
    return this;
  }
}