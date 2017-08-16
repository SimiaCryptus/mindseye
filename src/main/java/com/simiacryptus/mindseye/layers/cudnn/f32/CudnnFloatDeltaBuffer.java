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

package com.simiacryptus.mindseye.layers.cudnn.f32;

import com.simiacryptus.mindseye.layers.DeltaBuffer;
import com.simiacryptus.mindseye.layers.NNLayer;
import com.simiacryptus.mindseye.layers.cudnn.CuDNN;
import com.simiacryptus.mindseye.layers.cudnn.CudaPtr;
import com.simiacryptus.mindseye.layers.cudnn.CudaResource;
import com.simiacryptus.util.ml.Tensor;
import jcuda.Pointer;
import jcuda.jcudnn.cudnnTensorDescriptor;

import static jcuda.jcudnn.JCudnn.cudnnAddTensor;

public class CudnnFloatDeltaBuffer extends DeltaBuffer {
  public CudnnFloatDeltaBuffer(double[] values, NNLayer layer) {
    super(values, null, layer);
  }
  
  CudaPtr buffer;

  public void accumulate(CudaResource<cudnnTensorDescriptor> size, CudaPtr data) {
    if(null != buffer) {
      CuDNN.devicePool.with(handle->{
        CuDNN.handle(cudnnAddTensor(handle.cudnnHandle,
          Pointer.to(new float[]{1.0f}), size.getPtr(), data.getPtr(),
          Pointer.to(new float[]{1.0f}), size.getPtr(), buffer.getPtr()));
      });
      data.finalize();
    } else {
      buffer = data;
    }
  }
  
  @Override
  public double[] getDelta() {
    if(null == delta) {
      float[] data = new float[length()];
      buffer.read(data);
      this.delta = Tensor.toDoubles(data);
    }
    return super.getDelta();
  }
}