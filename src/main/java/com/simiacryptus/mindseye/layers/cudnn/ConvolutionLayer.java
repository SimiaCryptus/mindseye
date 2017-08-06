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
import com.simiacryptus.mindseye.layers.*;
import com.simiacryptus.util.Util;
import com.simiacryptus.util.ml.Coordinate;
import com.simiacryptus.util.ml.Tensor;
import jcuda.jcudnn.cudnnConvolutionDescriptor;
import jcuda.jcudnn.cudnnFilterDescriptor;
import jcuda.jcudnn.cudnnTensorDescriptor;
import jcuda.runtime.JCuda;

import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

import static jcuda.jcudnn.JCudnn.cudnnConvolutionBackwardData;
import static jcuda.jcudnn.JCudnn.cudnnConvolutionBackwardFilter;
import static jcuda.jcudnn.JCudnn.cudnnConvolutionForward;
import static jcuda.jcudnn.cudnnConvolutionMode.CUDNN_CONVOLUTION;
import static jcuda.jcudnn.cudnnDataType.CUDNN_DATA_DOUBLE;
import static jcuda.jcudnn.cudnnTensorFormat.CUDNN_TENSOR_NCHW;

/**
 * The type Convolution layer.
 */
public class ConvolutionLayer extends NNLayer {
  
  
  public JsonObject getJson() {
    JsonObject json = super.getJsonStub();
    json.add("filter", kernel.getJson());
    json.add("skip", skip.getJson());
    json.addProperty("simple", simple);
    return json;
  }
  
  /**
   * From json convolution layer.
   *
   * @param json the json
   * @return the convolution layer
   */
  public static ConvolutionLayer fromJson(JsonObject json) {
    return new ConvolutionLayer(json);
  }
  
  /**
   * Instantiates a new Convolution layer.
   *
   * @param json the json
   */
  protected ConvolutionLayer(JsonObject json) {
    super(json);
    this.kernel = Tensor.fromJson(json.getAsJsonObject("filter"));
    this.skip = Tensor.fromJson(json.getAsJsonObject("skip"));
    this.simple = json.getAsJsonPrimitive("simple").getAsBoolean();
  }
  
  
  /**
   * The Kernel.
   */
  public final Tensor kernel;
  /**
   * The Skip.
   */
  public final Tensor skip;
  /**
   * The Simple.
   */
  public final boolean simple;
  
  /**
   * Instantiates a new Convolution layer.
   */
  protected ConvolutionLayer() {
    this((Tensor)null, (Tensor)null, true);
  }
  
  /**
   * Instantiates a new Convolution layer.
   *
   * @param kernel the kernel
   * @param skip   the skip
   * @param simple the simple
   */
  protected ConvolutionLayer(Tensor kernel, Tensor skip, boolean simple) {
    super();
    this.simple = simple;
    this.skip = skip;
    if(kernel.getDimensions().length != 3) throw new IllegalArgumentException();
    if(kernel.getDimensions()[0] <= 0) throw new IllegalArgumentException();
    if(kernel.getDimensions()[1] <= 0) throw new IllegalArgumentException();
    if(kernel.getDimensions()[2] <= 0) throw new IllegalArgumentException();
    this.kernel = kernel;
  }
  
  /**
   * Instantiates a new Convolution layer.
   *
   * @param width       the width
   * @param height      the height
   * @param inputBands  the input bands
   * @param outputBands the output bands
   */
  public ConvolutionLayer(final int width, int height, final int inputBands, final int outputBands) {
    this(width, height, inputBands * outputBands);
  }
  
  /**
   * Instantiates a new Convolution layer.
   *
   * @param width  the width
   * @param height the height
   * @param bands  the bands
   * @param simple the simple
   */
  public ConvolutionLayer(final int width, int height, final int bands, boolean simple) {
    this(new Tensor(width,height,bands), new Tensor(new int[]{1,1}), simple);
    assert(!simple || 0 == (width-1) % 2) : "Simple kernels must have odd width";
    assert(!simple || 0 == (height-1) % 2) : "Simple kernels must have odd height";
  }
  
  /**
   * Instantiates a new Convolution layer.
   *
   * @param width  the width
   * @param height the height
   * @param bands  the bands
   */
  public ConvolutionLayer(final int width, int height, final int bands) {
    this(width, height, bands, true);
  }
  
  /**
   * Instantiates a new Convolution layer.
   *
   * @param width       the width
   * @param height      the height
   * @param inputBands  the input bands
   * @param outputBands the output bands
   * @param simple      the simple
   */
  public ConvolutionLayer(final int width, int height, final int inputBands, final int outputBands, boolean simple) {
    this(width, height, inputBands * outputBands, simple);
  }
  
  /**
   * Add weights convolution layer.
   *
   * @param f the f
   * @return the convolution layer
   */
  public ConvolutionLayer addWeights(final DoubleSupplier f) {
    Util.add(f, this.kernel.getData());
    return this;
  }
  
  @Override
  public NNResult eval(NNExecutionContext nncontext, final NNResult... inObj) {
    CuDNN.setDevice(nncontext.getCudaDeviceId());
    //assert Arrays.stream(inObj).flatMapToDouble(input->input.data.stream().flatMapToDouble(x-> Arrays.stream(x.getData()))).allMatch(v->Double.isFinite(v));
    
    final NNResult input = inObj[0];
    final TensorList batch = input.data;
    final int[] inputSize = batch.get(0).getDimensions();
    int[] kernelSize = this.kernel.getDimensions();
    int[] outputSize = IntStream.range(0, kernelSize.length).map(i -> {
      int x;
      if (i == kernelSize.length - 1) {
        x = kernelSize[i] / inputSize[i];
      } else if(simple) {
        x = inputSize[i];
      } else {
        x = 1 + inputSize[i] - kernelSize[i];
      }
      if (0 >= x) {
        assert false;
      }
      return x;
    }).toArray();
    Tensor[] output = IntStream.range(0, batch.length())
                           .mapToObj(dataIndex -> new Tensor(outputSize))
                           .toArray(i -> new Tensor[i]);
    try {
      double[][] inputBuffers = batch.stream().map(x -> x.getData()).toArray(i -> new double[i][]);
      double[][] outputBuffers = Arrays.stream(output).map(x -> x.getData()).toArray(i -> new double[i][]);
      convolve(nncontext.getCudaDeviceId(), inputSize, kernelSize, outputSize, simple, inputBuffers, this.kernel.getData(), outputBuffers);
    } catch (Throwable e) {
      throw new RuntimeException("Error with image res " + Arrays.toString(inputSize),e);
    }
    //assert Arrays.stream(output).flatMapToDouble(x-> Arrays.stream(x.getData())).allMatch(v->Double.isFinite(v));
  
    return new NNResult(output) {
      @Override
      public void accumulate(final DeltaSet buffer, final TensorList error) {
        CuDNN.setDevice(nncontext.getCudaDeviceId());
        //assert error.stream().flatMapToDouble(x-> Arrays.stream(x.getData())).allMatch(v->Double.isFinite(v));
        if (!isFrozen()) {
          double[][] inputBuffers = batch.stream().map(x -> x.getData()).toArray(i -> new double[i][]);
          double[][] outputBuffers = error.stream().map(x -> x.getData()).toArray(i -> new double[i][]);
          final Tensor kernel = ConvolutionLayer.this.kernel;
          final Tensor weightGradient = new Tensor(kernel.getDimensions());
          gradient(nncontext.getCudaDeviceId(), inputSize, kernelSize, outputSize, simple, inputBuffers, weightGradient.getData(), outputBuffers);
          buffer.get(ConvolutionLayer.this, kernel).accumulate(weightGradient.getData());
        }
        if (input.isAlive()) {
          Tensor[] inputBufferTensors = IntStream.range(0, data.length()).mapToObj(dataIndex -> new Tensor(inputSize)).toArray(i -> new Tensor[i]);
          double[][] inputBuffers = Arrays.stream(inputBufferTensors).map(x -> x.getData()).toArray(i -> new double[i][]);
          double[][] outputBuffers = error.stream().map(x -> x.getData()).toArray(i -> new double[i][]);
          backprop(nncontext.getCudaDeviceId(), inputSize, kernelSize, outputSize, simple, inputBuffers, ConvolutionLayer.this.kernel.getData(), outputBuffers);
          //assert Arrays.stream(inputBufferTensors).flatMapToDouble(x-> Arrays.stream(x.getData())).allMatch(v->Double.isFinite(v));
          input.accumulate(buffer, new TensorArray(inputBufferTensors));
        }
      }
      
      @Override
      public boolean isAlive() {
        return input.isAlive() || !isFrozen();
      }
    };
  }
  
  /**
   * Sets weights.
   *
   * @param f the f
   * @return the weights
   */
  public ConvolutionLayer setWeights(final ToDoubleFunction<Coordinate> f) {
    this.kernel.coordStream().parallel().forEach(c -> {
      this.kernel.set(c, f.applyAsDouble(c));
    });
    return this;
  }
  
  /**
   * Sets weights.
   *
   * @param f the f
   * @return the weights
   */
  public ConvolutionLayer setWeights(final DoubleSupplier f) {
    this.kernel.coordStream().parallel().forEach(c -> {
      this.kernel.set(c, f.getAsDouble());
    });
    return this;
  }
  
  @Override
  public List<double[]> state() {
    return Arrays.asList(this.kernel.getData());
  }
  
  /**
   * The constant MAX_BUFFER_SIZE.
   */
  public static int MAX_BUFFER_SIZE = 64 * 1024 * 1024;
  
  /**
   * Backprop.
   *
   * @param inputSize  the input size
   * @param kernelSize the kernel size
   * @param outputSize the output size
   * @param simple     the simple
   * @param input      the input
   * @param weights    the weights
   * @param output     the output
   */
  public static void backprop(int deviceId, final int[] inputSize, final int[] kernelSize, final int[] outputSize, boolean simple, final double[][] input, final double[] weights, final double[][] output) {
    int length = input.length;
    assert(length == output.length);
    int inLength = input[0].length;
    int outLength = output[0].length;
    int inputsPerRun = Math.min(Math.floorDiv(MAX_BUFFER_SIZE, inLength), length);
    int runs = length / inputsPerRun;
    int leftover = length - runs * inputsPerRun;
    double[] inputBuffer = null;
    double[] outputBuffer = null;
    CudaResource<cudnnFilterDescriptor> filterDescriptor = CuDNN.newFilterDescriptor(
            CUDNN_DATA_DOUBLE, CUDNN_TENSOR_NCHW, outputSize[2], inputSize[2], kernelSize[1], kernelSize[0]);
    CudaResource<cudnnConvolutionDescriptor> convolutionDescriptor = CuDNN.newConvolutionDescriptor(
            simple?((kernelSize[1] - 1) / 2):0, simple?((kernelSize[0] - 1) / 2):0,
            1, 1,
            CUDNN_CONVOLUTION);
    CudaPtr filterData = CuDNN.write(deviceId, weights);
    for(int run=0;run<runs;run++) {
      int currentIndexOffset = run * inputsPerRun;
      int currentNumItems = run < run - 1 ? inputsPerRun : leftover == 0 ? inputsPerRun : leftover;
      if(null == inputBuffer || inputBuffer.length != inLength * currentNumItems) {
        Tensor.recycle(inputBuffer);
        inputBuffer = Tensor.obtain(inLength * currentNumItems);
      }
      if(null == outputBuffer || outputBuffer.length != outLength * currentNumItems) {
        Tensor.recycle(outputBuffer);
        outputBuffer = Tensor.obtain(outLength * currentNumItems);
      }
      for (int i = 0; i< currentNumItems; i++) {
        assert outLength == output[currentIndexOffset+i].length;
        System.arraycopy(output[currentIndexOffset+i], 0, outputBuffer, i * outLength, outLength);
      }
      assert(0 < inputBuffer.length);
      assert(0 < weights.length);
      assert(0 < outputBuffer.length);
      assert kernelSize[0] * kernelSize[1] * kernelSize[2] == weights.length;
      double[] _inputBuffer = inputBuffer;
      double[] _outputBuffer = outputBuffer;
      CuDNN.devicePool.with(device -> {
        try {
          CudaResource<cudnnTensorDescriptor> inputDescriptor = CuDNN.newTensorDescriptor(
                  CUDNN_DATA_DOUBLE, CUDNN_TENSOR_NCHW, currentNumItems, inputSize[2], inputSize[1], inputSize[0]);
          backprop(deviceId, outputSize, _inputBuffer, filterData, _outputBuffer, device, inputDescriptor, filterDescriptor, convolutionDescriptor);
        } catch (Throwable e) {
          throw new RuntimeException("Error with " + Arrays.toString(kernelSize),e);
        }
      });
      for (int i = 0; i< currentNumItems; i++) {
        assert inLength == input[currentIndexOffset+i].length;
        System.arraycopy(inputBuffer, i * inLength, input[currentIndexOffset+i], 0, inLength);
      }
    }
    filterData.finalize();
    Tensor.recycle(inputBuffer);
    Tensor.recycle(outputBuffer);
  }
  
  /**
   * Convolve.
   *
   * @param inputSize  the input size
   * @param kernelSize the kernel size
   * @param outputSize the output size
   * @param simple     the simple
   * @param input      the input
   * @param weights    the weights
   * @param output     the output
   */
  public static void convolve(int deviceId, final int[] inputSize, final int[] kernelSize, final int[] outputSize, boolean simple, final double[][] input, final double[] weights, final double[][] output) {
    int length = input.length;
    assert(length == output.length);
    int inLength = input[0].length;
    int outLength = output[0].length;
    int inputsPerRun = Math.min(Math.floorDiv(MAX_BUFFER_SIZE, inLength), length);
    assert(0 < inputsPerRun) : "Requested buffer is over max of " + MAX_BUFFER_SIZE;
    int runs = length / inputsPerRun;
    int leftover = length - runs * inputsPerRun;
    double[] inputBuffer = null;
    double[] outputBuffer = null;
    CudaResource<cudnnFilterDescriptor> filterDescriptor = CuDNN.newFilterDescriptor(
            CUDNN_DATA_DOUBLE, CUDNN_TENSOR_NCHW, outputSize[2], inputSize[2], kernelSize[1], kernelSize[0]);
    CudaResource<cudnnConvolutionDescriptor> convolutionDescriptor = CuDNN.newConvolutionDescriptor(
            simple?((kernelSize[1] - 1) / 2):0, simple?((kernelSize[0] - 1) / 2):0,
            1, 1,
            CUDNN_CONVOLUTION);
    CudaPtr filterData = CuDNN.write(deviceId, weights);
    for(int run=0;run<=runs;run++) {
      int currentIndexOffset = run * inputsPerRun;
      int currentNumItems = run < runs ? inputsPerRun : leftover;
      if(0 == currentNumItems) continue;
      if(null == inputBuffer || inputBuffer.length != inLength * currentNumItems) {
        Tensor.recycle(inputBuffer);
        inputBuffer = Tensor.obtain(inLength * currentNumItems);
      }
      if(null == outputBuffer || outputBuffer.length != outLength * currentNumItems) {
        Tensor.recycle(outputBuffer);
        outputBuffer = Tensor.obtain(outLength * currentNumItems);
      }
      for (int i = 0; i< currentNumItems; i++) {
        assert inLength == input[currentIndexOffset+i].length;
        System.arraycopy(input[currentIndexOffset+i], 0, inputBuffer, i * inLength, inLength);
      }
      assert(0 < inputBuffer.length);
      assert(0 < weights.length);
      assert(0 < outputBuffer.length);
      double[] _inputBuffer = inputBuffer;
      double[] _outputBuffer = outputBuffer;
      CuDNN.devicePool.with(device -> {
        try {
          CudaResource<cudnnTensorDescriptor> inputDescriptor = CuDNN.newTensorDescriptor(
                  CUDNN_DATA_DOUBLE, CUDNN_TENSOR_NCHW, currentNumItems, inputSize[2], inputSize[1], inputSize[0]);
          convolve(deviceId, outputSize, _inputBuffer, filterData, _outputBuffer, device, inputDescriptor, filterDescriptor, convolutionDescriptor);
        } catch (Throwable e) {
          throw new RuntimeException("Error with " + Arrays.toString(kernelSize),e);
        }
      });
      for (int i = 0; i< currentNumItems; i++) {
        assert outLength == output[currentIndexOffset+i].length;
        System.arraycopy(outputBuffer, i * outLength, output[currentIndexOffset+i], 0, outLength);
      }
    }
    filterData.finalize();
    Tensor.recycle(inputBuffer);
    Tensor.recycle(outputBuffer);
  }
  
  /**
   * Gradient.
   *
   * @param inputSize  the input size
   * @param kernelSize the kernel size
   * @param outputSize the output size
   * @param simple     the simple
   * @param input      the input
   * @param weights    the weights
   * @param output     the output
   */
  public static void gradient(int deviceId, final int[] inputSize, final int[] kernelSize, final int[] outputSize, boolean simple, final double[][] input, final double[] weights, final double[][] output) {
    int length = input.length;
    assert(length == output.length);
    int inLength = input[0].length;
    int outLength = output[0].length;
    int inputsPerRun = Math.min(Math.floorDiv(MAX_BUFFER_SIZE, Math.max(inLength,outLength)), length);
    int runs = length / inputsPerRun;
    int leftover = length - runs * inputsPerRun;
    double[] inputBuffer = null;
    double[] outputBuffer = null;
    CudaResource<cudnnFilterDescriptor> filterDescriptor = CuDNN.newFilterDescriptor(
            CUDNN_DATA_DOUBLE, CUDNN_TENSOR_NCHW, outputSize[2], inputSize[2], kernelSize[1], kernelSize[0]);
    CudaResource<cudnnConvolutionDescriptor> convolutionDescriptor = CuDNN.newConvolutionDescriptor(
            simple?((kernelSize[1] - 1) / 2):0, simple?((kernelSize[0] - 1) / 2):0,
            1, 1,
            CUDNN_CONVOLUTION);
    for(int run=0;run<runs;run++) {
      int currentIndexOffset = run * inputsPerRun;
      int currentNumItems = run < run - 1 ? inputsPerRun : leftover == 0 ? inputsPerRun : leftover;
      if(null == inputBuffer || inputBuffer.length != inLength * currentNumItems) {
        Tensor.recycle(inputBuffer);
        inputBuffer = Tensor.obtain(inLength * currentNumItems);
      }
      if(null == outputBuffer || outputBuffer.length != outLength * currentNumItems) {
        Tensor.recycle(outputBuffer);
        outputBuffer = Tensor.obtain(outLength * currentNumItems);
      }
      for (int i = 0; i< currentNumItems; i++) {
        assert inLength == input[currentIndexOffset+i].length;
        assert outLength == output[currentIndexOffset+i].length;
        System.arraycopy(input[currentIndexOffset+i], 0, inputBuffer, i * inLength, inLength);
        System.arraycopy(output[currentIndexOffset+i], 0, outputBuffer, i * outLength, outLength);
      }
      double[] buffer = Tensor.obtain(weights.length);
      assert(0 < inputBuffer.length);
      assert(0 < buffer.length);
      assert(0 < outputBuffer.length);
      double[] _inputBuffer = inputBuffer;
      double[] _outputBuffer = outputBuffer;
      CuDNN.devicePool.with(device -> {
        try {
          int items = currentNumItems;
          CudaResource<cudnnTensorDescriptor> inputDescriptor = CuDNN.newTensorDescriptor(
                  CUDNN_DATA_DOUBLE, CUDNN_TENSOR_NCHW, items, inputSize[2], inputSize[1], inputSize[0]);
          gradient(deviceId, outputSize, _inputBuffer, buffer, _outputBuffer, device, inputDescriptor, filterDescriptor, convolutionDescriptor);
        } catch (Throwable e) {
          throw new RuntimeException("Error with " + Arrays.toString(kernelSize),e);
        }
      });
      IntStream.range(0, weights.length).forEach(weightIndex -> {
        for (int i = weightIndex; i < buffer.length; i += weights.length) {
          weights[weightIndex] += buffer[i];
        }
      });
      Tensor.recycle(buffer);
    }
    Tensor.recycle(inputBuffer);
    Tensor.recycle(outputBuffer);
  }

  private static void backprop(int deviceId, final int[] outputSize, double[] input, CudaPtr filterData, double[] output, CuDNN device, CudaResource<cudnnTensorDescriptor> inputDescriptor, CudaResource<cudnnFilterDescriptor> filterDescriptor, CudaResource<cudnnConvolutionDescriptor> convolutionDescriptor) {
    int[] outputDims = CuDNN.getOutputDims(inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr());
    assert(4 == outputDims.length);
    assert(outputSize[0] == outputDims[3]);
    assert(outputSize[1] == outputDims[2]);
    assert(outputSize[2] == outputDims[1]);
    CudaResource<cudnnTensorDescriptor> outputDescriptor = device.newTensorDescriptor(
            CUDNN_DATA_DOUBLE, CUDNN_TENSOR_NCHW, outputDims[0], outputDims[1], outputDims[2], outputDims[3]);
    int algorithm = device.getBackwardDataAlgorithm(
            inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr());
    CudaPtr workSpace = device.allocateBackwardDataWorkspace(deviceId,
      inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr(), algorithm);
    CudaPtr alpha = device.javaPtr(deviceId, 1.0);
    CudaPtr beta = device.javaPtr(deviceId, 0.0);
    CudaPtr inputData = CuDNN.alloc(deviceId, input);
    CudaPtr outputData = device.write(deviceId, output);
    CuDNN.handle(cudnnConvolutionBackwardData(device.cudnnHandle, alpha.getPtr(),
            filterDescriptor.getPtr(), filterData.getPtr(),
            outputDescriptor.getPtr(), outputData.getPtr(),
            convolutionDescriptor.getPtr(), algorithm, workSpace.getPtr(), workSpace.size, beta.getPtr(),
            inputDescriptor.getPtr(), inputData.getPtr()));
    inputData.read(input);
    inputData.finalize();
    outputData.finalize();
  }

  private static void convolve(int deviceId, final int[] outputSize, double[] input, CudaPtr filterData, double[] output, CuDNN device, CudaResource<cudnnTensorDescriptor> inputDescriptor, CudaResource<cudnnFilterDescriptor> filterDescriptor, CudaResource<cudnnConvolutionDescriptor> convolutionDescriptor) {
    int[] outputDims = CuDNN.getOutputDims(inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr());
    assert(4 == outputDims.length);
    assert(outputSize[0] == outputDims[3]);
    assert(outputSize[1] == outputDims[2]);
    assert(outputSize[2] == outputDims[1]);
    CudaResource<cudnnTensorDescriptor> outputDescriptor = device.newTensorDescriptor(
            CUDNN_DATA_DOUBLE, CUDNN_TENSOR_NCHW, outputDims[0], outputDims[1], outputDims[2], outputDims[3]);
    int algorithm = device.getForwardAlgorithm(
            inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr());
    CudaPtr workSpace = device.allocateForwardWorkspace(deviceId,
      inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr(), algorithm);
    CudaPtr alpha = device.javaPtr(deviceId, 1.0);
    CudaPtr beta = device.javaPtr(deviceId, 0.0);
    CudaPtr inputData = device.write(deviceId, input);
    CudaPtr outputData = CuDNN.alloc(deviceId, output);
    CuDNN.handle(cudnnConvolutionForward(device.cudnnHandle, alpha.getPtr(),
            inputDescriptor.getPtr(), inputData.getPtr(),
            filterDescriptor.getPtr(), filterData.getPtr(),
            convolutionDescriptor.getPtr(), algorithm, workSpace.getPtr(), workSpace.size, beta.getPtr(),
            outputDescriptor.getPtr(), outputData.getPtr()));
    outputData.read(output);
    inputData.finalize();
    outputData.finalize();
  }

  private static void gradient(int deviceId, final int[] outputSize, double[] input, double[] weights, double[] output, CuDNN device, CudaResource<cudnnTensorDescriptor> inputDescriptor, CudaResource<cudnnFilterDescriptor> filterDescriptor, CudaResource<cudnnConvolutionDescriptor> convolutionDescriptor) {
    int[] outputDims = CuDNN.getOutputDims(inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr());
    assert(4 == outputDims.length);
    assert(outputSize[0] == outputDims[3]);
    assert(outputSize[1] == outputDims[2]);
    assert(outputSize[2] == outputDims[1]);
    CudaResource<cudnnTensorDescriptor> outputDescriptor = device.newTensorDescriptor(
            CUDNN_DATA_DOUBLE, CUDNN_TENSOR_NCHW, outputDims[0], outputDims[1], outputDims[2], outputDims[3]);
    int algorithm = device.getBackwardFilterAlgorithm(
            inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr());
    CudaPtr workSpace = device.allocateBackwardFilterWorkspace(deviceId,
      inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr(), algorithm);
    CudaPtr alpha = device.javaPtr(deviceId, 1.0);
    CudaPtr beta = device.javaPtr(deviceId, 0.0);
    CudaPtr inputData = device.write(deviceId, input);
    CudaPtr filterData = CuDNN.alloc(deviceId, weights);
    CudaPtr outputData = device.write(deviceId, output);

    CuDNN.handle(cudnnConvolutionBackwardFilter(device.cudnnHandle, alpha.getPtr(),
            inputDescriptor.getPtr(), inputData.getPtr(),
            outputDescriptor.getPtr(), outputData.getPtr(),
            convolutionDescriptor.getPtr(), algorithm, workSpace.getPtr(), workSpace.size, beta.getPtr(),
            filterDescriptor.getPtr(), filterData.getPtr()));

    filterData.read(weights);
    inputData.finalize();
    filterData.finalize();
    outputData.finalize();
  }

}
