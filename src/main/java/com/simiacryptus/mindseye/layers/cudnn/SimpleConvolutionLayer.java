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
import com.simiacryptus.mindseye.lang.*;
import com.simiacryptus.mindseye.lang.cudnn.*;
import com.simiacryptus.mindseye.test.TestUtil;
import com.simiacryptus.util.Util;
import jcuda.jcudnn.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;

/**
 * This convolution layer only supports an equal number of input and output bands. It is used as the foundational
 * component for ConvolutionLayer, since the GpuSystem api has this restriction (in recent versions).
 */
@SuppressWarnings("serial")
public class SimpleConvolutionLayer extends NNLayer implements MultiPrecision<SimpleConvolutionLayer> {
  
  /**
   * The Log.
   */
  static final Logger log = LoggerFactory.getLogger(SimpleConvolutionLayer.class);
  private static final HashMap<SimpleConvolutionParameters, Supplier<CudaFwdParameters>> fwdWs = new HashMap<>();
  private static final HashMap<SimpleConvolutionParameters, Supplier<CudaRevParameters>> revWs = new HashMap<>();
  /**
   * The Filter.
   */
  public final Tensor kernel;
  private int paddingX;
  private int paddingY;
  private Precision precision = Precision.Double;
  private int strideX = 1;
  private int strideY = 1;
  
  /**
   * Instantiates a new Convolution layer.
   */
  protected SimpleConvolutionLayer() {
    this(null);
  }
  
  /**
   * Instantiates a new Convolution layer.
   *
   * @param width  the width
   * @param height the height
   * @param bands  the bands
   */
  public SimpleConvolutionLayer(final int width, final int height, final int bands) {
    this(new Tensor(width, height, bands));
    assert !false || 0 == (width - 1) % 2 : "Simple kernels must have odd width";
    assert !false || 0 == (height - 1) % 2 : "Simple kernels must have odd height";
  }
  
  /**
   * Instantiates a new Convolution layer.
   *
   * @param json      the json
   * @param resources the resources
   */
  protected SimpleConvolutionLayer(final JsonObject json, Map<String, byte[]> resources) {
    super(json);
    kernel = Tensor.fromJson(json.get("filter"), resources);
    strideX = json.get("strideX").getAsInt();
    strideY = json.get("strideY").getAsInt();
    setPaddingX(json.get("paddingX").getAsInt());
    setPaddingY(json.get("paddingY").getAsInt());
    precision = Precision.valueOf(json.get("precision").getAsString());
  }
  
  /**
   * Instantiates a new Convolution layer.
   *
   * @param kernel the filter
   */
  protected SimpleConvolutionLayer(final Tensor kernel) {
    super();
    int[] kernelSize = kernel.getDimensions();
    if (kernelSize.length != 3) throw new IllegalArgumentException();
    if (kernelSize[0] <= 0) throw new IllegalArgumentException();
    if (kernelSize[1] <= 0) throw new IllegalArgumentException();
    if (kernelSize[2] <= 0) throw new IllegalArgumentException();
    this.kernel = kernel;
    this.setPaddingX((int) Math.ceil((kernelSize[0] - 1) / 2.0));
    this.setPaddingY((int) Math.ceil((kernelSize[1] - 1) / 2.0));
    
  }
  
  /**
   * From json convolution layer.
   *
   * @param json the json
   * @param rs   the rs
   * @return the convolution layer
   */
  public static SimpleConvolutionLayer fromJson(final JsonObject json, Map<String, byte[]> rs) {
    return new SimpleConvolutionLayer(json, rs);
  }
  
  /**
   * Reverse int [ ].
   *
   * @param array the array
   * @return the int [ ]
   */
  public static int[] reverse(int... array) {
    for (int i = 0; i < array.length / 2; i++) {
      int j = array[array.length - (i + 1)];
      array[array.length - (i + 1)] = array[i];
      array[i] = j;
    }
    return array;
  }
  
  private static final PersistanceMode workspaceCachePersistance = PersistanceMode.Strong;
  private static CudaFwdParameters obtainFwd(SimpleConvolutionParameters parameters) {
    Supplier<CudaFwdParameters> supplier = fwdWs.get(parameters);
    CudaFwdParameters fwdParameters = supplier == null ? null : supplier.get();
    if (null == fwdParameters) {
      synchronized (fwdWs) {
        supplier = fwdWs.get(parameters);
        fwdParameters = supplier == null ? null : supplier.get();
        if (null == fwdParameters) {
          fwdParameters = new CudaFwdParameters(parameters);
          fwdWs.put(parameters, workspaceCachePersistance.wrap(fwdParameters));
        }
        return fwdParameters;
      }
    }
    return fwdParameters;
  }
  
  private static CudaRevParameters obtainRev(SimpleConvolutionParameters parameters) {
    Supplier<CudaRevParameters> supplier = revWs.get(parameters);
    CudaRevParameters revParameters = supplier == null ? null : supplier.get();
    if (null == revParameters) {
      synchronized (revWs) {
        supplier = revWs.get(parameters);
        revParameters = supplier == null ? null : supplier.get();
        if (null == revParameters) {
          revParameters = new CudaRevParameters(parameters);
          revWs.put(parameters, workspaceCachePersistance.wrap(revParameters));
        }
        return revParameters;
      }
    }
    return revParameters;
  }
  
  /**
   * Add weights convolution layer.
   *
   * @param f the f
   * @return the convolution layer
   */
  public SimpleConvolutionLayer addWeights(final DoubleSupplier f) {
    Util.add(f, kernel.getData());
    return this;
  }
  
  private boolean cmp(final int[] outputSize, final int[] outputDims) {
    if (4 != outputDims.length) return false;
    if (outputSize[0] != outputDims[3]) return false;
    if (outputSize[1] != outputDims[2]) return false;
    return outputSize[2] == outputDims[1];
  }
  
  @Override
  public NNResult eval(final NNResult... inObj) {
    if (!GpuSystem.isEnabled()) return getCompatibilityLayer().eval(inObj);
  
    final NNResult input = inObj[0];
    final TensorList batch = input.getData();
    final int[] inputSize = batch.getDimensions();
    final int[] kernelSize = kernel.getDimensions();
    final int[] outputSize = getOutputSize(inputSize);
    final int length = batch.length();
    Arrays.stream(inObj).forEach(nnResult -> nnResult.addRef());
    batch.addRef();
  
    return new NNResult(CuDNNHandle.run(gpu -> {
      try {
        final int deviceNumber = gpu.getDeviceNumber();
        CudaFwdParameters cudaParameters = obtainFwd(new SimpleConvolutionParameters(kernel, paddingX, paddingY, precision, strideX, strideY, length, inputSize, outputSize, kernelSize, gpu));
        assert 0 < kernel.getData().length;
        assert kernelSize[0] * kernelSize[1] * kernelSize[2] == kernel.getData().length;
        CudaPtr filterPtr = CudaPtr.allocate(deviceNumber, (long) (kernel.getData().length * precision.size), MemoryType.Device, true)
                                   .write(precision, kernel.getData());
        final CudaPtr inputData = CudaPtr.getCudaPtr(precision, batch);
        final CudaPtr outputBuffer = CudaPtr.allocate(deviceNumber, Tensor.dim(cudaParameters.outputDims) * 1l * length * precision.size, MemoryType.Managed, true);
        GpuSystem.handle(CuDNNHandle.cudnnConvolutionForward(gpu.getHandle(), precision.getPointer(1.0),
                                                             cudaParameters.inputDescriptor.getPtr(), inputData.getPtr(),
                                                             cudaParameters.filterDescriptor.getPtr(), filterPtr.getPtr(),
                                                             cudaParameters.convolutionDescriptor.getPtr(),
                                                             cudaParameters.forwardAlgorithm,
                                                             cudaParameters.forwardWorkspace.getPtr(),
                                                             cudaParameters.forwardWorkspace.size,
                                                             precision.getPointer(0.0), cudaParameters.outputDescriptor.getPtr(), outputBuffer.getPtr()));
        gpu.registerForCleanup(filterPtr, inputData);
        return GpuTensorList.wrap(outputBuffer, length, cudaParameters.outputDims, precision);
      } catch (final Throwable e) {
        throw new ComponentException(String.format("Error in convolution %s x %s", Arrays.toString(inputSize), Arrays.toString(kernelSize)), e);
      }
    }), (final DeltaSet<NNLayer> buffer, final TensorList delta) -> {
      assert delta.length() == batch.length();
      TestUtil.runAllSerial(() -> {
        CuDNNHandle.apply(gpu -> {
          if (!isFrozen()) {
            CudaRevParameters cudaParameters = obtainRev(new SimpleConvolutionParameters(kernel, paddingX, paddingY, precision, strideX, strideY, length, inputSize, outputSize, kernelSize, gpu));
            assert cudaParameters.precision == precision;
            final CudaPtr errorPtr = CudaPtr.getCudaPtr(precision, delta);
            final CudaPtr inputData = CudaPtr.getCudaPtr(precision, batch);
            CudaPtr filterPtr = CudaPtr.allocate(gpu.getDeviceNumber(), kernel.dim() * 1l * precision.size, MemoryType.Device, true);
            try {
              GpuSystem.handle(CuDNNHandle.cudnnConvolutionBackwardFilter(gpu.getHandle(), cudaParameters.precision.getPointer(1.0),
                                                                          cudaParameters.inputDescriptor.getPtr(), inputData.getPtr(),
                                                                          cudaParameters.outputDescriptor.getPtr(), errorPtr.getPtr(),
                                                                          cudaParameters.convolutionDescriptor.getPtr(),
                                                                          cudaParameters.backwardFilterAlgorithm,
                                                                          cudaParameters.backwardsFilterWorkSpace.getPtr(),
                                                                          cudaParameters.backwardsFilterWorkSpace.size,
                                                                          precision.getPointer(0.0), cudaParameters.filterDescriptor.getPtr(), filterPtr.getPtr()));
            } catch (final Throwable e) {
              throw new ComponentException(String.format("Error in convolution %s x %s => %s", Arrays.toString(inputSize), Arrays.toString(kernelSize), Arrays.toString(outputSize)), e);
            }
            final Tensor weightGradient = CudaPtr.read(filterPtr, precision, kernel.getDimensions());
            buffer.get(SimpleConvolutionLayer.this, kernel.getData()).addInPlace(weightGradient.getData());
            gpu.registerForCleanup(weightGradient, inputData, filterPtr, errorPtr);
          }
        });
      }, () -> {
        if (input.isAlive()) {
          final TensorList inputBufferTensors = CuDNNHandle.run(gpu -> {
            CudaRevParameters cudaParameters = obtainRev(new SimpleConvolutionParameters(kernel, paddingX, paddingY, precision, strideX, strideY, length, inputSize, outputSize, kernelSize, gpu));
            final CudaPtr inputBuffer = CudaPtr.allocate(gpu.getDeviceNumber(), Tensor.dim(batch.getDimensions()) * 1l * length * precision.size, MemoryType.Managed, true);
            try {
              final CudaPtr errorPtr = CudaPtr.getCudaPtr(precision, delta);
              final CudaPtr filterPtr = CudaPtr.allocate(gpu.getDeviceNumber(), (long) (kernel.getData().length * precision.size), MemoryType.Device, true)
                                               .write(precision, kernel.getData());
              GpuSystem.handle(CuDNNHandle.cudnnConvolutionBackwardData(gpu.getHandle(), precision.getPointer(1.0),
                                                                        cudaParameters.filterDescriptor.getPtr(), filterPtr.getPtr(),
                                                                        cudaParameters.outputDescriptor.getPtr(), errorPtr.getPtr(),
                                                                        cudaParameters.convolutionDescriptor.getPtr(),
                                                                        cudaParameters.backwardDataAlgorithm,
                                                                        cudaParameters.backwardsDataWorkSpace.getPtr(),
                                                                        cudaParameters.backwardsDataWorkSpace.size,
                                                                        precision.getPointer(0.0), cudaParameters.inputDescriptor.getPtr(), inputBuffer.getPtr()));
              gpu.registerForCleanup(errorPtr, filterPtr);
            } catch (final Throwable e) {
              throw new ComponentException(String.format("Error in convolution %s x %s => %s", Arrays.toString(inputSize), Arrays.toString(kernelSize), Arrays.toString(outputSize)), e);
            }
            return GpuTensorList.wrap(inputBuffer, length, inputSize, precision);
          });
          if (null != inputBufferTensors) {
            input.accumulate(buffer, inputBufferTensors);
            inputBufferTensors.freeRef();
          }
        }
      });
    }) {
    
      @Override
      protected void _free() {
        batch.freeRef();
        Arrays.stream(inObj).forEach(nnResult -> nnResult.freeRef());
      }
      
      @Override
      public boolean isAlive() {
        return input.isAlive() || !isFrozen();
      }
    };
  }
  
  /**
   * Gets compatibility layer.
   *
   * @return the compatibility layer
   */
  public NNLayer getCompatibilityLayer() {
    log.info(String.format("Using compatibility layer for %s", this));
    int bands = (int) Math.sqrt(this.kernel.getDimensions()[2]);
    final com.simiacryptus.mindseye.layers.aparapi.ConvolutionLayer convolutionLayer = new com.simiacryptus.mindseye.layers.aparapi.ConvolutionLayer(this.kernel.getDimensions()[0], this.kernel.getDimensions()[1], this.kernel.getDimensions()[2], true);
    final Tensor tensor = new Tensor(kernel.getDimensions());
    tensor.setByCoord(c -> {
      final int band = c.getCoords()[2];
      final int bandX = band % bands;
      final int bandY = (band - bandX) / bands;
      assert band == bandX + bandY * bands;
      final int bandT = bandY + bandX * bands;
      return kernel.get(c.getCoords()[0], c.getCoords()[1], bandT);
    });
    convolutionLayer.kernel.set(tensor);
    return new NNLayer() {
      @Override
      public NNResult eval(NNResult... array) {
        Arrays.stream(array).forEach(x -> x.addRef());
        NNResult result = convolutionLayer.eval(array);
        return new NNResult(result.getData(), (DeltaSet<NNLayer> buffer, TensorList data) -> {
          throw new IllegalStateException();
        }) {
  
  
          @Override
          protected void _free() {
            Arrays.stream(array).forEach(x -> x.freeRef());
          }
  
          @Override
          public boolean isAlive() {
            return false;
          }
        };
      }
  
      @Override
      public JsonObject getJson(Map<String, byte[]> resources, DataSerializer dataSerializer) {
        throw new IllegalStateException();
      }
  
      @Override
      public List<double[]> state() {
        throw new IllegalStateException();
      }
    };
  }
  
  @Override
  public JsonObject getJson(Map<String, byte[]> resources, DataSerializer dataSerializer) {
    final JsonObject json = super.getJsonStub();
    json.add("filter", kernel.toJson(resources, dataSerializer));
    json.addProperty("strideX", strideX);
    json.addProperty("strideY", strideY);
    json.addProperty("paddingX", getPaddingX());
    json.addProperty("paddingY", getPaddingY());
    json.addProperty("precision", precision.name());
    return json;
  }
  
  /**
   * Get output size int [ ].
   *
   * @param inputSize the input size
   * @return the int [ ]
   */
  public int[] getOutputSize(final int... inputSize) {
    final int[] kernelSize = kernel.getDimensions();
    return IntStream.range(0, kernelSize.length).map(i -> {
      int x;
      if (i == kernelSize.length - 1) {
        //assert kernelSize[i] == inputSize[i];
        x = kernelSize[i] / inputSize[i];
      }
      else {
        int padding;
        if (i == 0) {
          padding = this.paddingX;
        }
        else if (i == 1) {
          padding = this.paddingY;
        }
        else {
          throw new IllegalStateException();
        }
        x = inputSize[i] - (kernelSize[i] - 1) + padding * 2;
      }
      assert 0 < x;
      return x;
    }).toArray();
  }
  
  @Override
  public Precision getPrecision() {
    return precision;
  }
  
  @Override
  public SimpleConvolutionLayer setPrecision(final Precision precision) {
    this.precision = precision;
    return this;
  }
  
  /**
   * The Stride x.
   *
   * @return the stride x
   */
  public int getStrideX() {
    return strideX;
  }
  
  /**
   * Sets stride x.
   *
   * @param strideX the stride x
   * @return the stride x
   */
  public SimpleConvolutionLayer setStrideX(final int strideX) {
    this.strideX = strideX;
    return this;
  }
  
  /**
   * The Stride y.
   *
   * @return the stride y
   */
  public int getStrideY() {
    return strideY;
  }
  
  /**
   * Sets stride y.
   *
   * @param strideY the stride y
   * @return the stride y
   */
  public SimpleConvolutionLayer setStrideY(final int strideY) {
    this.strideY = strideY;
    return this;
  }
  
  /**
   * Sets weights.
   *
   * @param f the f
   * @return the weights
   */
  public SimpleConvolutionLayer set(final DoubleSupplier f) {
    kernel.coordStream(true).parallel().forEach(c -> {
      kernel.set(c, f.getAsDouble());
    });
    return this;
  }
  
  /**
   * Sets weights.
   *
   * @param f the f
   * @return the weights
   */
  public SimpleConvolutionLayer set(final ToDoubleFunction<Coordinate> f) {
    kernel.coordStream(true).parallel().forEach(c -> {
      kernel.set(c, f.applyAsDouble(c));
    });
    return this;
  }
  
  @Override
  public List<double[]> state() {
    return Arrays.asList(kernel.getData());
  }
  
  /**
   * Gets padding x.
   *
   * @return the padding x
   */
  public int getPaddingX() {
    return paddingX;
  }
  
  /**
   * Sets padding x.
   *
   * @param paddingX the padding x
   * @return the padding x
   */
  public SimpleConvolutionLayer setPaddingX(int paddingX) {
    this.paddingX = paddingX;
    return this;
  }
  
  /**
   * Gets padding y.
   *
   * @return the padding y
   */
  public int getPaddingY() {
    return paddingY;
  }
  
  /**
   * Sets padding y.
   *
   * @param paddingY the padding y
   * @return the padding y
   */
  public SimpleConvolutionLayer setPaddingY(int paddingY) {
    this.paddingY = paddingY;
    return this;
  }
  
  /**
   * Sets padding xy.
   *
   * @param x the x
   * @param y the y
   * @return the padding xy
   */
  public SimpleConvolutionLayer setPaddingXY(int x, int y) {
    return setPaddingX(x).setPaddingY(y);
  }
  
  /**
   * Sets weights log.
   *
   * @param f the f
   * @return the weights log
   */
  public SimpleConvolutionLayer setWeightsLog(double f) {
    return set(() -> Math.pow(10, f) * (Math.random() - 0.5));
  }
  
  /**
   * Set.
   *
   * @param kernel the kernel
   */
  public void set(Tensor kernel) {
    this.kernel.set(kernel);
  }
  
  private static class SimpleConvolutionParameters {
    /**
     * The Length.
     */
    public final int length;
    /**
     * The Input size.
     */
    public final int[] inputSize;
    /**
     * The Output size.
     */
    public final int[] outputSize;
    /**
     * The Kernel size.
     */
    public final int[] kernelSize;
    /**
     * The Kernel.
     */
    public final Tensor kernel;
    /**
     * The Padding x.
     */
    public final int paddingX;
    /**
     * The Padding y.
     */
    public final int paddingY;
    /**
     * The Precision.
     */
    public final Precision precision;
    /**
     * The Stride x.
     */
    public final int strideX;
    /**
     * The Stride y.
     */
    public final int strideY;
    /**
     * The Gpu.
     */
    public final CuDNNHandle gpu;
  
    /**
     * Instantiates a new Simple convolution parameters.
     *
     * @param kernel     the kernel
     * @param paddingX   the padding x
     * @param paddingY   the padding y
     * @param precision  the precision
     * @param strideX    the stride x
     * @param strideY    the stride y
     * @param length     the length
     * @param inputSize  the input size
     * @param outputSize the output size
     * @param kernelSize the kernel size
     * @param gpu        the gpu
     */
    public SimpleConvolutionParameters(Tensor kernel, int paddingX, int paddingY, Precision precision, int strideX, int strideY, int length, int[] inputSize, int[] outputSize, int[] kernelSize, CuDNNHandle gpu) {
      this.paddingX = paddingX;
      this.gpu = gpu;
      this.strideX = strideX;
      this.strideY = strideY;
      this.paddingY = paddingY;
      this.precision = precision;
      this.kernel = kernel;
      this.length = length;
      this.inputSize = Arrays.copyOf(inputSize, inputSize.length);
      this.outputSize = Arrays.copyOf(outputSize, outputSize.length);
      this.kernelSize = Arrays.copyOf(kernelSize, kernelSize.length);
    }
    
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SimpleConvolutionParameters)) return false;
      SimpleConvolutionParameters that = (SimpleConvolutionParameters) o;
      if (length != that.length) return false;
      if (paddingX != that.paddingX) return false;
      if (paddingY != that.paddingY) return false;
      if (strideX != that.strideX) return false;
      if (strideY != that.strideY) return false;
      if (!gpu.equals(that.gpu)) return false;
      if (!Arrays.equals(inputSize, that.inputSize)) return false;
      if (!Arrays.equals(outputSize, that.outputSize)) return false;
      if (!Arrays.equals(kernelSize, that.kernelSize)) return false;
      if (kernel != null ? !kernel.equals(that.kernel) : that.kernel != null) return false;
      return precision == that.precision;
    }
    
    @Override
    public int hashCode() {
      int result = length;
      result = 31 * result + Arrays.hashCode(inputSize);
      result = 31 * result + Arrays.hashCode(outputSize);
      result = 31 * result + Arrays.hashCode(kernelSize);
      result = 31 * result + (kernel != null ? kernel.hashCode() : 0);
      result = 31 * result + paddingX;
      result = 31 * result + paddingY;
      result = 31 * result + (precision != null ? precision.hashCode() : 0);
      result = 31 * result + strideX;
      result = 31 * result + strideY;
      result = 31 * result + gpu.hashCode();
      return result;
    }
    
    @Override
    public String toString() {
      return getClass().getSimpleName() + "{" +
        "length=" + length +
        ", inputSize=" + Arrays.toString(inputSize) +
        ", outputSize=" + Arrays.toString(outputSize) +
        ", kernelSize=" + Arrays.toString(kernelSize) +
        ", kernel=" + kernel +
        ", paddingX=" + paddingX +
        ", paddingY=" + paddingY +
        ", precision=" + precision +
        ", strideX=" + strideX +
        ", strideY=" + strideY +
        ", gpu=" + gpu +
        '}';
    }
  }
  
  private static class CudaRevParameters extends SimpleConvolutionParameters {
    /**
     * The Backward data algorithm.
     */
    public final int backwardDataAlgorithm;
    /**
     * The Backward filter algorithm.
     */
    public final int backwardFilterAlgorithm;
    /**
     * The Output dims.
     */
    public final int[] outputDims;
    /**
     * The Backwards work space.
     */
    public final CudaPtr backwardsFilterWorkSpace;
    /**
     * The Output descriptor.
     */
    public final CudaResource<cudnnTensorDescriptor> outputDescriptor;
    /**
     * The Input descriptor.
     */
    public final CudaResource<cudnnTensorDescriptor> inputDescriptor;
    /**
     * The Filter descriptor.
     */
    public final CudaResource<cudnnFilterDescriptor> filterDescriptor;
    /**
     * The Convolution descriptor.
     */
    public final CudaResource<cudnnConvolutionDescriptor> convolutionDescriptor;
    /**
     * Backwards data workspace
     */
    public final CudaPtr backwardsDataWorkSpace;
  
    /**
     * Instantiates a new Cuda rev parameters.
     *
     * @param obj the obj
     */
    CudaRevParameters(SimpleConvolutionParameters obj) {
      super(obj.kernel, obj.paddingX, obj.paddingY, obj.precision, obj.strideX, obj.strideY, obj.length, obj.inputSize, obj.outputSize, obj.kernelSize, obj.gpu);
      inputDescriptor = GpuSystem.newTensorDescriptor(
        precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, length, inputSize[2], inputSize[1], inputSize[0]);
      filterDescriptor = GpuSystem.newFilterDescriptor(
        precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, outputSize[2], inputSize[2], kernelSize[1], kernelSize[0]);
      convolutionDescriptor = GpuSystem.newConvolutions2dDescriptor(cudnnConvolutionMode.CUDNN_CONVOLUTION, precision.code,
                                                                    paddingY, paddingX,
                                                                    strideY, strideX,
                                                                    1, 1);
      outputDims = IntStream.of(reverse(GpuSystem.getOutputDims(inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr()))).limit(3).toArray();
      outputDescriptor = GpuSystem.newTensorDescriptor(
        precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, length, outputDims[2], outputDims[1], outputDims[0]);
      backwardDataAlgorithm = CuDNNHandle.getBackwardDataAlgorithm(gpu.getHandle(),
                                                                   inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr());
      backwardFilterAlgorithm = CuDNNHandle.getBackwardFilterAlgorithm(gpu.getHandle(),
                                                                       inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr());
      backwardsFilterWorkSpace = CuDNNHandle.allocateBackwardFilterWorkspace(gpu.getHandle(), gpu.getDeviceNumber(),
                                                                             inputDescriptor.getPtr(), filterDescriptor.getPtr(),
                                                                             convolutionDescriptor.getPtr(), outputDescriptor.getPtr(), backwardFilterAlgorithm);
      backwardsDataWorkSpace = CuDNNHandle.allocateBackwardDataWorkspace(gpu.getHandle(), gpu.getDeviceNumber(),
                                                                         inputDescriptor.getPtr(), filterDescriptor.getPtr(),
                                                                         convolutionDescriptor.getPtr(), outputDescriptor.getPtr(), backwardDataAlgorithm);
    }
  
    /**
     * Free.
     */
    public void free() {
      this.convolutionDescriptor.freeRef();
      this.filterDescriptor.freeRef();
      this.inputDescriptor.freeRef();
      this.outputDescriptor.freeRef();
      this.backwardsFilterWorkSpace.freeRef();
      this.backwardsDataWorkSpace.freeRef();
    }
    
  }
  
  private static class CudaFwdParameters extends SimpleConvolutionParameters {
    /**
     * The Forward algorithm.
     */
    public final int forwardAlgorithm;
    /**
     * The Output dims.
     */
    public final int[] outputDims;
    /**
     * The Output descriptor.
     */
    public final CudaResource<cudnnTensorDescriptor> outputDescriptor;
    /**
     * The Input descriptor.
     */
    public final CudaResource<cudnnTensorDescriptor> inputDescriptor;
    /**
     * The Filter descriptor.
     */
    public final CudaResource<cudnnFilterDescriptor> filterDescriptor;
    /**
     * The Convolution descriptor.
     */
    public final CudaResource<cudnnConvolutionDescriptor> convolutionDescriptor;
    /**
     * The Forward workspace.
     */
    public final CudaPtr forwardWorkspace;
  
    /**
     * Instantiates a new Cuda fwd parameters.
     *
     * @param obj the obj
     */
    CudaFwdParameters(SimpleConvolutionParameters obj) {
      super(obj.kernel, obj.paddingX, obj.paddingY, obj.precision, obj.strideX, obj.strideY, obj.length, obj.inputSize, obj.outputSize, obj.kernelSize, obj.gpu);
      inputDescriptor = GpuSystem.newTensorDescriptor(
        precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, length, inputSize[2], inputSize[1], inputSize[0]);
      filterDescriptor = GpuSystem.newFilterDescriptor(
        precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, outputSize[2], inputSize[2], kernelSize[1], kernelSize[0]);
      convolutionDescriptor = GpuSystem.newConvolutions2dDescriptor(cudnnConvolutionMode.CUDNN_CONVOLUTION, precision.code,
                                                                    paddingY, paddingX,
                                                                    strideY, strideX,
                                                                    1, 1);
      outputDims = IntStream.of(reverse(GpuSystem.getOutputDims(inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr()))).limit(3).toArray();
      outputDescriptor = GpuSystem.newTensorDescriptor(
        precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, length, outputDims[2], outputDims[1], outputDims[0]);
      forwardAlgorithm = CuDNNHandle.getForwardAlgorithm(gpu.getHandle(),
                                                         inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr());
      forwardWorkspace = CuDNNHandle.allocateForwardWorkspace(gpu.getHandle(), gpu.getDeviceNumber(),
                                                              inputDescriptor.getPtr(), filterDescriptor.getPtr(), convolutionDescriptor.getPtr(), outputDescriptor.getPtr(), forwardAlgorithm);
    }
  
    /**
     * Free.
     */
    public void free() {
      this.convolutionDescriptor.freeRef();
      this.filterDescriptor.freeRef();
      this.inputDescriptor.freeRef();
      this.outputDescriptor.freeRef();
    }
    
  }
}
