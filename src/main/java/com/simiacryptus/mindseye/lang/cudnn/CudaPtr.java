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

package com.simiacryptus.mindseye.lang.cudnn;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.simiacryptus.mindseye.lang.ReshapedTensorList;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.lang.TensorList;
import com.simiacryptus.mindseye.layers.cudnn.SimpleConvolutionLayer;
import com.simiacryptus.mindseye.test.TestUtil;
import com.simiacryptus.util.lang.TimedResult;
import jcuda.Pointer;
import jcuda.runtime.cudaMemcpyKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static jcuda.runtime.cudaMemcpyKind.cudaMemcpyDeviceToHost;

/**
 * A GPU memory segment
 */
public class CudaPtr extends CudaResourceBase<Pointer> {
  /**
   * The constant METRICS.
   */
  public static final LoadingCache<Integer, GpuStats> METRICS = CacheBuilder.newBuilder().build(new CacheLoader<Integer, GpuStats>() {
    @javax.annotation.Nonnull
    @Override
    public GpuStats load(final Integer integer) throws Exception {
      return new GpuStats();
    }
  });
  /**
   * The constant logger.
   */
  protected static final Logger logger = LoggerFactory.getLogger(CudaPtr.class);
  /**
   * The Max.
   */
  static final long MAX = Precision.Double.size * (Integer.MAX_VALUE - 1L);
  private static final int K = 1024;
  private static final int MiB = K * 1024;
  private static final long GiB = 1024 * MiB;
  /**
   * The Size.
   */
  public final long size;
  private final int deviceId;
  @javax.annotation.Nonnull
  private final MemoryType type;
  
  /**
   * Instantiates a new Cuda ptr.
   *
   * @param size     the size
   * @param deviceId the device id
   * @param type     the type
   */
  private CudaPtr(final long size, final int deviceId, @javax.annotation.Nonnull MemoryType type) {
    super(acquire(deviceId, size, type, 1));
    this.size = size;
    this.deviceId = deviceId;
    this.type = type;
  }
  
  /**
   * Allocate cuda ptr.
   *
   * @param deviceId the device id
   * @param size     the size
   * @param type     the type
   * @param dirty    the dirty
   * @return the cuda ptr
   */
  @javax.annotation.Nonnull
  public static CudaPtr allocate(final int deviceId, final long size, @javax.annotation.Nonnull MemoryType type, boolean dirty) {
    @javax.annotation.Nonnull CudaPtr obtain = new CudaPtr(size, type == MemoryType.Device ? deviceId : -1, type);
    if (!dirty) obtain.clear();
    return obtain;
  }
  
  /**
   * Gets cuda ptr.
   *
   * @param precision the precision
   * @param data      the data
   * @return the cuda ptr
   */
  @Nullable
  public static CudaPtr getCudaPtr(@javax.annotation.Nonnull final Precision precision, @javax.annotation.Nonnull final TensorList data) {
    data.assertAlive();
    if (data instanceof ReshapedTensorList) {
      return getCudaPtr(precision, ((ReshapedTensorList) data).getInner());
    }
    if (data instanceof GpuTensorList && precision == ((GpuTensorList) data).getPrecision()) {
      @javax.annotation.Nonnull GpuTensorList gpuTensorList = (GpuTensorList) data;
      @Nullable final CudaPtr ptr = gpuTensorList.getPtr();
      assert null != ptr;
      return ptr;
    }
    else {
      final int listLength = data.length();
      final int elementLength = Tensor.dim(data.getDimensions());
      @javax.annotation.Nonnull final CudaPtr ptr = CudaPtr.allocate(GpuSystem.getDevice(), (long) elementLength * listLength * precision.size, MemoryType.Managed, true);
      for (int i = 0; i < listLength; i++) {
        Tensor tensor = data.get(i);
        ptr.write(precision, tensor.getData(), i * elementLength);
        tensor.freeRef();
      }
      return ptr;
    }
  }
  
  private static final long MAX_TOTAL_MEMORY = 8 * GiB;
  
  @javax.annotation.Nonnull
  private static Pointer acquire(int deviceId, long size, @javax.annotation.Nonnull MemoryType type, int retries) {
    if (retries < 0) throw new IllegalArgumentException();
    if (size < 0) {
      throw new OutOfMemoryError("Allocated block is too large: " + size);
    }
    if (size > CudaPtr.MAX) {
      throw new OutOfMemoryError("Allocated block is too large: " + size);
    }
    if (deviceId >= 0 && GpuSystem.getDevice() != deviceId) throw new IllegalArgumentException();
    final GpuStats metrics = CudaPtr.getGpuStats(deviceId);
    long totalGpuMem = METRICS.asMap().values().stream().mapToLong(x -> x.usedMemory.get()).sum();
    synchronized (CudaPtr.class) {
      long resultingTotalMemory = totalGpuMem + size;
      long resultingDeviceMemory = metrics.usedMemory.get() + size;
      if (resultingDeviceMemory > metrics.highMemoryThreshold || resultingTotalMemory > MAX_TOTAL_MEMORY) {
        logger.info(String.format("Clearing memory for device %s while allocating %s bytes (%s > %s)", deviceId, size, resultingDeviceMemory, metrics.highMemoryThreshold));
        clearMemory(deviceId);
      }
    }
    try {
      @javax.annotation.Nonnull Pointer pointer = new Pointer();
      type.alloc(size, pointer);
      final long finalMemory = metrics.usedMemory.addAndGet(size);
      metrics.peakMemory.updateAndGet(l -> Math.max(finalMemory, l));
      return pointer;
    } catch (@javax.annotation.Nonnull final ThreadDeath e) {
      throw e;
    } catch (@javax.annotation.Nonnull final Throwable e) {
      if (retries <= 0)
        throw new RuntimeException(String.format(String.format("Error allocating %d bytes; %s currently allocated to device %s", size, metrics.usedMemory, deviceId)), e);
      final long startMemory = metrics.usedMemory.get();
      @javax.annotation.Nonnull TimedResult<Void> timedResult = TimedResult.time(() -> clearMemory(deviceId));
      final long freedMemory = startMemory - metrics.usedMemory.get();
      logger.warn(String.format("Low GPU Memory while allocating %s bytes; %s freed in %.4fs resulting in %s total (triggered by %s)",
        size, freedMemory, timedResult.seconds(), metrics.usedMemory.get(), e.getMessage()));
    }
    if (retries < 0) throw new IllegalStateException();
    return acquire(deviceId, size, type, retries - 1);
  }
  
  /**
   * Clear memory.
   *
   * @param deviceId the device id
   */
  public static void clearMemory(final int deviceId) {
    if (TestUtil.CONSERVATIVE) {
      logLoad();
      logger.info(String.format("Running Garbage Collector"));
      System.gc();
    }
    logLoad();
    long bytes = SimpleConvolutionLayer.getInstances().mapToLong(x -> x.evictDeviceData(deviceId)).sum();
    logger.info(String.format("Cleared %s bytes from ConvolutionFilters for device %s", bytes, deviceId));
    GpuTensorList.evictToHeap(deviceId);
    logLoad();
  }
  
  private static void logLoad() {
    logger.info(String.format("Current Load: %s", METRICS.asMap().entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> {
      return String.format("%d bytes", e.getValue().usedMemory.get());
    }))));
  }
  
  /**
   * From device double tensor.
   *
   * @param ptr        the filter data
   * @param precision  the precision
   * @param dimensions the dimensions  @return the tensor
   * @return the tensor
   */
  @javax.annotation.Nonnull
  public static Tensor read(@javax.annotation.Nonnull final CudaPtr ptr, @javax.annotation.Nonnull final Precision precision, final int[] dimensions) {
    GpuSystem.cudaDeviceSynchronize();
    @javax.annotation.Nonnull final Tensor tensor = new Tensor(dimensions);
    switch (precision) {
      case Float:
        final int length = tensor.dim();
        @javax.annotation.Nonnull final float[] data = new float[length];
        ptr.read(precision, data);
        @Nullable final double[] doubles = tensor.getData();
        for (int i = 0; i < length; i++) {
          doubles[i] = data[i];
        }
        break;
      case Double:
        ptr.read(precision, tensor.getData());
        break;
      default:
        throw new IllegalStateException();
    }
    return tensor;
  }
  
  
  /**
   * Gets gpu stats.
   *
   * @param deviceId the device id
   * @return the gpu stats
   */
  public static GpuStats getGpuStats(final int deviceId) {
    GpuStats devivceMemCtr;
    try {
      devivceMemCtr = CudaPtr.METRICS.get(deviceId);
    } catch (@javax.annotation.Nonnull final ExecutionException e) {
      throw new RuntimeException(e.getCause());
    }
    return devivceMemCtr;
  }
  
  /**
   * Copy to cuda ptr.
   *
   * @param deviceId the device id
   * @return the cuda ptr
   */
  public CudaPtr copyTo(int deviceId) {
    return GpuSystem.withDevice(deviceId, () -> {
      @javax.annotation.Nonnull CudaPtr copy = allocate(deviceId, size, MemoryType.Managed, false);
      GpuSystem.cudaMemcpy(copy.getPtr(), this.getPtr(), size, cudaMemcpyKind.cudaMemcpyDeviceToDevice);
      return copy;
    });
  }
  
  /**
   * Move to cuda ptr.
   *
   * @param deviceId the device id
   * @return the cuda ptr
   */
  public CudaPtr moveTo(int deviceId) {
    if (deviceId == getDeviceId()) return this;
    else return copyTo(deviceId);
  }
  
  @Override
  protected void _free() {
    if (isActiveObj()) {
      getType().free(ptr, deviceId);
      CudaPtr.getGpuStats(deviceId).usedMemory.addAndGet(-size);
    }
  }
  
  /**
   * Read cuda ptr.
   *
   * @param precision   the precision
   * @param destination the data
   * @return the cuda ptr
   */
  @Nonnull
  public CudaPtr read(@Nonnull final Precision precision, @Nonnull final double[] destination) {return read(precision, destination, 0);}
  
  /**
   * Read cuda ptr.
   *
   * @param precision   the precision
   * @param destination the data
   * @param offset      the offset
   * @return the cuda ptr
   */
  @javax.annotation.Nonnull
  public CudaPtr read(@javax.annotation.Nonnull final Precision precision, @javax.annotation.Nonnull final double[] destination, int offset) {
    if (size < offset + (long) destination.length * precision.size) {
      throw new IllegalArgumentException(size + " != " + destination.length * 1l * precision.size);
    }
    if (precision == Precision.Float) {
      @Nonnull float[] data = new float[destination.length];
      read(Precision.Float, data, offset);
      for (int i = 0; i < destination.length; i++) {
        destination[i] = data[i];
      }
    }
    else {
      GpuSystem.cudaMemcpy(precision.getPointer(destination), getPtr().withByteOffset((long) offset * precision.size), (long) destination.length * precision.size, cudaMemcpyDeviceToHost);
      CudaPtr.getGpuStats(deviceId).memoryReads.addAndGet((long) destination.length * precision.size);
    }
    return this;
  }
  
  /**
   * Read cuda ptr.
   *
   * @param precision   the precision
   * @param destination the data
   * @return the cuda ptr
   */
  @Nonnull
  public CudaPtr read(@Nonnull final Precision precision, @Nonnull final float[] destination) {return read(precision, destination, 0);}
  
  /**
   * Read cuda ptr.
   *
   * @param precision   the precision
   * @param destination the data
   * @param offset      the offset
   * @return the cuda ptr
   */
  @javax.annotation.Nonnull
  public CudaPtr read(@javax.annotation.Nonnull final Precision precision, @javax.annotation.Nonnull final float[] destination, int offset) {
    if (size < (long) destination.length * precision.size) {
      throw new IllegalArgumentException(size + " != " + (long) destination.length * precision.size);
    }
    if (precision == Precision.Double) {
      @Nonnull double[] data = new double[destination.length];
      read(Precision.Double, data, offset);
      for (int i = 0; i < destination.length; i++) {
        destination[i] = (float) data[i];
      }
    }
    else {
      GpuSystem.cudaMemcpy(precision.getPointer(destination), getPtr().withByteOffset((long) offset * precision.size), (long) destination.length * precision.size, cudaMemcpyDeviceToHost);
      CudaPtr.getGpuStats(deviceId).memoryReads.addAndGet((long) destination.length * precision.size);
    }
    return this;
  }
  
  /**
   * Write cuda ptr.
   *
   * @param precision the precision
   * @param data      the data
   * @return the cuda ptr
   */
  @Nonnull
  public CudaPtr write(@Nonnull final Precision precision, @Nonnull final double[] data) {return write(precision, data, 0);}
  
  /**
   * Write cuda ptr.
   *
   * @param precision the precision
   * @param data      the data
   * @param offset    the offset
   * @return the cuda ptr
   */
  @javax.annotation.Nonnull
  public CudaPtr write(@javax.annotation.Nonnull final Precision precision, @javax.annotation.Nonnull final double[] data, int offset) {
    if (size < (long) (offset + data.length) * precision.size)
      throw new IllegalArgumentException(String.format("%d != (%d + %d) * %d", size, offset, data.length, precision.size));
    GpuSystem.cudaMemcpy(getPtr().withByteOffset((long) offset * precision.size), precision.getPointer(data), (long) data.length * precision.size, cudaMemcpyKind.cudaMemcpyHostToDevice);
    CudaPtr.getGpuStats(deviceId).memoryWrites.addAndGet((long) data.length * precision.size);
    return this;
  }
  
  /**
   * Write cuda ptr.
   *
   * @param precision the precision
   * @param data      the data
   * @return the cuda ptr
   */
  @Nonnull
  public CudaPtr write(@Nonnull final Precision precision, @Nonnull final float[] data) {return write(precision, data, 0);}
  
  /**
   * Write cuda ptr.
   *
   * @param precision the precision
   * @param data      the data
   * @param offset    the offset
   * @return the cuda ptr
   */
  @javax.annotation.Nonnull
  public CudaPtr write(@javax.annotation.Nonnull final Precision precision, @javax.annotation.Nonnull final float[] data, int offset) {
    if (size < (long) (offset + data.length) * precision.size)
      throw new IllegalArgumentException(String.format("%d != %d * %d", size, data.length, precision.size));
    GpuSystem.cudaMemcpy(getPtr().withByteOffset((long) offset * precision.size), precision.getPointer(data), (long) data.length * precision.size, cudaMemcpyKind.cudaMemcpyHostToDevice);
    CudaPtr.getGpuStats(deviceId).memoryWrites.addAndGet((long) data.length * precision.size);
    return this;
  }
  
  /**
   * Gets device id.
   *
   * @return the device id
   */
  public int getDeviceId() {
    return deviceId;
  }
  
  /**
   * Gets type.
   *
   * @return the type
   */
  @javax.annotation.Nonnull
  public MemoryType getType() {
    return type;
  }
  
  @javax.annotation.Nonnull
  private CudaPtr clear() {
    GpuSystem.cudaMemset(getPtr(), 0, size);
    return this;
  }
  
  /**
   * As copy cuda ptr.
   *
   * @return the cuda ptr
   */
  public CudaPtr asCopy() {
    CudaPtr copy = copy();
    freeRef();
    return copy;
  }
  
  /**
   * Copy cuda ptr.
   *
   * @return the cuda ptr
   */
  public CudaPtr copy() {
    return copyTo(getDeviceId());
  }
}
