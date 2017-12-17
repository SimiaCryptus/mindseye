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

import com.simiacryptus.mindseye.lang.NNExecutionContext;
import com.simiacryptus.util.lang.StaticResourcePool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The type Cuda execution context.
 */
public class CudaExecutionContext extends CuDNN implements NNExecutionContext {
  
  /**
   * The constant gpuContexts.
   */
  public static StaticResourcePool<CudaExecutionContext> gpuContexts = new StaticResourcePool<>(CudaExecutionContext.loadGpuContexts());
  
  /**
   * Instantiates a new Cuda execution context.
   *
   * @param deviceNumber the device number
   */
  public CudaExecutionContext(final int deviceNumber) {
    this(deviceNumber, false);
  }
  
  /**
   * Instantiates a new Cuda execution context.
   *
   * @param deviceNumber the device number
   * @param isStatic     the is static
   */
  public CudaExecutionContext(final int deviceNumber, final boolean isStatic) {
    super(deviceNumber);
  }
  
  /**
   * Load gpu contexts list.
   *
   * @return the list
   */
  static List<CudaExecutionContext> loadGpuContexts() {
    final int deviceCount = CuDNN.deviceCount();
    System.out.println(String.format("Found %s devices", deviceCount));
    List<Integer> devices = new ArrayList<>();
    for (int device = 0; device < deviceCount; device++) {
      //if(device>0) System.err.println(String.format("IGNORING Device %s - %s", device, getDeviceName(device)));
      System.out.println(String.format("Device %s - %s", device, CuDNN.getDeviceName(device)));
      devices.add(device);
    }
    if (System.getProperties().containsKey("gpus")) {
      devices = Arrays.stream(System.getProperty("gpus").split(","))
        .map(Integer::parseInt).collect(Collectors.toList());
      
    }
    System.out.println(String.format("Found %s devices; using devices %s", deviceCount, devices));
    return devices.stream()
      .map(i -> new CudaExecutionContext(i)).collect(Collectors.toList());
  }
  
}
