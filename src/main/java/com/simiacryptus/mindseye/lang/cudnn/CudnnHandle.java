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

import com.simiacryptus.mindseye.lang.ReferenceCounting;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.lang.TensorList;
import jcuda.Pointer;
import jcuda.jcudnn.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

/**
 * The type Gpu handle.
 */
public class CudnnHandle extends CudaDevice {
  /**
   * The Thread context.
   */
  static final ThreadLocal<CudnnHandle> threadContext = new ThreadLocal<>();
  /**
   * The constant CLEANUP.
   */
  public final LinkedBlockingDeque<CudaResourceBase> cleanupNative = new LinkedBlockingDeque<>();
  /**
   * The Handle.
   */
  @Nullable
  public final jcuda.jcudnn.cudnnHandle handle;
  
  /**
   * Instantiates a new Cu dnn.
   *
   * @param deviceNumber the device number
   */
  CudnnHandle(final int deviceNumber) {
    super(deviceNumber);
    if (0 <= this.deviceId) {
      initThread();
      handle = new cudnnHandle();
      JCudnn.cudnnCreate(handle);
    }
    else {
      handle = null;
    }
    //cudaSetDevice();
  }
  
  /**
   * Gets thread handle.
   *
   * @return the thread handle
   */
  public static CudnnHandle getThreadHandle() {
    return threadContext.get();
  }
  
  /**
   * For each.
   *
   * @param fn the fn
   */
  public static void forEach(@javax.annotation.Nonnull final Consumer<? super CudaDevice> fn) {
    getPool().getAll().forEach(x -> {
      x.initThread();
      fn.accept(x);
    });
  }
  
  /**
   * Add cuda tensor list.
   *
   * @param left  the left
   * @param right the right
   * @return the cuda tensor list
   */
  @Nonnull
  public CudaTensorList add(final CudaTensorList left, final CudaTensorList right) {
    int length = left.length();
    int[] dimensions = right.getDimensions();
    assert dimensions.length <= 3;
    int d2 = dimensions.length < 3 ? 1 : dimensions[2];
    int d1 = dimensions.length < 2 ? 1 : dimensions[1];
    int d0 = dimensions[0];
    @Nonnull CudaTensor rPtr = getTensor(right, MemoryType.Device);
    @Nonnull CudaTensor lPtr = getTensor(left, MemoryType.Device);
    @Nonnull final CudaResource<cudnnOpTensorDescriptor> opDescriptor = newOpDescriptor(cudnnOpTensorOp.CUDNN_OP_TENSOR_ADD, left.precision.code);
    @Nonnull final CudaDevice.CudaTensorDescriptor sizeDescriptor = newTensorDescriptor(
      left.precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, length, d2, d1, d0);
    @Nonnull final CudaMemory outputPtr = allocate(lPtr.memory.size, MemoryType.Managed, true);
    try {
      cudnnOpTensor(opDescriptor.getPtr(),
        right.getPrecision().getPointer(1.0), sizeDescriptor.getPtr(), lPtr.memory.getPtr(),
        right.getPrecision().getPointer(1.0), sizeDescriptor.getPtr(), rPtr.memory.getPtr(),
        right.getPrecision().getPointer(0.0), sizeDescriptor.getPtr(), outputPtr.getPtr());
      return CudaTensorList.wrap(CudaTensor.wrap(outputPtr, sizeDescriptor), length, dimensions, left.precision);
    } finally {
      opDescriptor.freeRef();
      rPtr.freeRef();
      lPtr.freeRef();
    }
  }
  
  /**
   * Add in place cuda tensor list.
   *
   * @param left  the left
   * @param right the right
   * @return the cuda tensor list
   */
  public CudaTensorList addInPlace(final CudaTensorList left, final CudaTensorList right) {
    final int[] leftDimensions = left.getDimensions();
    final int[] rightDimensions = right.getDimensions();
    @Nonnull final CudaResource<cudnnTensorDescriptor> leftDescriptor = newTensorDescriptor(
      left.precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, left.length(), leftDimensions[2], leftDimensions[1], leftDimensions[0]);
    @Nonnull final CudaResource<cudnnTensorDescriptor> rightDescriptor = newTensorDescriptor(
      right.precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, right.length(), rightDimensions[2], rightDimensions[1], rightDimensions[0]);
    @Nullable final CudaTensor lPtr = left.ptr;//.moveTo(gpu.getDeviceNumber());
    @Nullable final CudaTensor rPtr = getTensor(right, MemoryType.Device);//.moveTo(gpu.getDeviceNumber());
    cudnnAddTensor(
      right.precision.getPointer(1.0), rightDescriptor.getPtr(), rPtr.memory.getPtr(),
      left.precision.getPointer(1.0), leftDescriptor.getPtr(), lPtr.memory.getPtr());
    rPtr.freeRef();
    rightDescriptor.freeRef();
    leftDescriptor.freeRef();
    return left;
  }
  
  /**
   * Add and free tensor list.
   *
   * @param precision the precision
   * @param left      the left
   * @param right     the right
   * @return the tensor list
   */
  @Nonnull
  public TensorList addAndFree(final Precision precision, final TensorList left, final TensorList right) {
    final int[] dimensions = left.getDimensions();
    assert left.length() == right.length();
    assert Tensor.length(left.getDimensions()) == Tensor.length(right.getDimensions());
    int length = left.length();
    assert length == right.length();
//    if (left.currentRefCount() == 1 && left instanceof CudaTensorList)
//      return this.addInPlaceAndFree((CudaTensorList) left, right);
//    if (right.currentRefCount() == 1 && right instanceof CudaTensorList)
//      return this.addInPlaceAndFree((CudaTensorList) right, left);
    @Nonnull final CudaResource<cudnnOpTensorDescriptor> opDescriptor = newOpDescriptor(cudnnOpTensorOp.CUDNN_OP_TENSOR_ADD, precision.code);
    @Nonnull final CudaDevice.CudaTensorDescriptor sizeDescriptor = newTensorDescriptor(
      precision.code, cudnnTensorFormat.CUDNN_TENSOR_NCHW, length, dimensions[2], dimensions[1], dimensions[0]);
    @Nullable final CudaTensor lPtr = getTensor(left, precision, MemoryType.Device);//.moveTo(gpu.getDeviceNumber());
    @Nullable final CudaTensor rPtr = getTensor(right, precision, MemoryType.Device);//.moveTo(gpu.getDeviceNumber());
    assert lPtr.memory.size == rPtr.memory.size;
    @Nonnull final CudaMemory outputPtr = allocate(lPtr.memory.size, MemoryType.Device, true);
    cudnnOpTensor(opDescriptor.getPtr(),
      precision.getPointer(1.0), sizeDescriptor.getPtr(), lPtr.memory.getPtr(),
      precision.getPointer(1.0), sizeDescriptor.getPtr(), rPtr.memory.getPtr(),
      precision.getPointer(0.0), sizeDescriptor.getPtr(), outputPtr.getPtr());
    Arrays.stream(new ReferenceCounting[]{lPtr, rPtr, opDescriptor, left, right}).forEach(ReferenceCounting::freeRef);
    return CudaTensorList.wrap(CudaTensor.wrap(outputPtr, sizeDescriptor), length, dimensions, precision);
  }
  
  /**
   * Cudnn activation forward int.
   *
   * @param activationDesc the activation desc
   * @param alpha          the alpha
   * @param xDesc          the x desc
   * @param x              the x
   * @param beta           the beta
   * @param yDesc          the y desc
   * @param y              the y
   * @return the int
   */
  public int cudnnActivationForward(
    final cudnnActivationDescriptor activationDesc,
    final Pointer alpha,
    final cudnnTensorDescriptor xDesc,
    final Pointer x,
    final Pointer beta,
    final cudnnTensorDescriptor yDesc,
    final Pointer y) {
    long startTime = System.nanoTime();
    final int result = JCudnn.cudnnActivationForward(this.handle, activationDesc, alpha, xDesc, x, beta, yDesc, y);
    cudnnActivationForward_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnActivationForward", result, new Object[]{this, activationDesc, alpha, xDesc, x, beta, yDesc, y});
    return result;
  }
  
  /**
   * Cudnn add tensor int.
   *
   * @param alpha the alpha
   * @param aDesc the a desc
   * @param A     the a
   * @param beta  the beta
   * @param cDesc the c desc
   * @param C     the c
   * @return the int
   */
  public int cudnnAddTensor(
    final Pointer alpha,
    final cudnnTensorDescriptor aDesc,
    final Pointer A,
    final Pointer beta,
    final cudnnTensorDescriptor cDesc,
    final Pointer C) {
    long startTime = System.nanoTime();
    final int result = JCudnn.cudnnAddTensor(this.handle, alpha, aDesc, A, beta, cDesc, C);
    cudnnAddTensor_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnAddTensor", result, new Object[]{this, alpha, aDesc, A, beta, cDesc, C});
    CudaSystem.handle(result);
    return result;
  }
  
  /**
   * Cudnn convolution backward bias int.
   *
   * @param alpha  the alpha
   * @param dyDesc the dy desc
   * @param dy     the dy
   * @param beta   the beta
   * @param dbDesc the db desc
   * @param db     the db
   * @return the int
   */
  public int cudnnConvolutionBackwardBias(
    final Pointer alpha,
    final cudnnTensorDescriptor dyDesc,
    final Pointer dy,
    final Pointer beta,
    final cudnnTensorDescriptor dbDesc,
    final Pointer db) {
    long startTime = System.nanoTime();
    final int result = JCudnn.cudnnConvolutionBackwardBias(this.handle, alpha, dyDesc, dy, beta, dbDesc, db);
    cudnnConvolutionBackwardBias_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnConvolutionBackwardBias", result, new Object[]{this, alpha, dyDesc, dy, beta, dbDesc, db});
    return result;
  }
  
  /**
   * Cudnn convolution backward data int.
   *
   * @param alpha                the alpha
   * @param wDesc                the w desc
   * @param w                    the w
   * @param dyDesc               the dy desc
   * @param dy                   the dy
   * @param convDesc             the conv desc
   * @param algo                 the algo
   * @param workSpace            the work space
   * @param workSpaceSizeInBytes the work space size in bytes
   * @param beta                 the beta
   * @param dxDesc               the dx desc
   * @param dx                   the dx
   * @return the int
   */
  public int cudnnConvolutionBackwardData(
    final Pointer alpha,
    final cudnnFilterDescriptor wDesc,
    final Pointer w,
    final cudnnTensorDescriptor dyDesc,
    final Pointer dy,
    final cudnnConvolutionDescriptor convDesc,
    final int algo,
    final Pointer workSpace,
    final long workSpaceSizeInBytes,
    final Pointer beta,
    final cudnnTensorDescriptor dxDesc,
    final Pointer dx) {
    long startTime = System.nanoTime();
    final int result = JCudnn.cudnnConvolutionBackwardData(this.handle, alpha, wDesc, w, dyDesc, dy, convDesc, algo, workSpace, workSpaceSizeInBytes, beta, dxDesc, dx);
    cudnnConvolutionBackwardData_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnConvolutionBackwardData", result, new Object[]{this, alpha, wDesc, w, dyDesc, dy, convDesc, algo, workSpace, workSpaceSizeInBytes, beta, dxDesc, dx});
    return result;
  }
  
  /**
   * Cudnn convolution backward filter int.
   *
   * @param alpha                the alpha
   * @param xDesc                the x desc
   * @param x                    the x
   * @param dyDesc               the dy desc
   * @param dy                   the dy
   * @param convDesc             the conv desc
   * @param algo                 the algo
   * @param workSpace            the work space
   * @param workSpaceSizeInBytes the work space size in bytes
   * @param beta                 the beta
   * @param dwDesc               the dw desc
   * @param dw                   the dw
   * @return the int
   */
  public int cudnnConvolutionBackwardFilter(
    final Pointer alpha,
    final cudnnTensorDescriptor xDesc,
    final Pointer x,
    final cudnnTensorDescriptor dyDesc,
    final Pointer dy,
    final cudnnConvolutionDescriptor convDesc,
    final int algo,
    final Pointer workSpace,
    final long workSpaceSizeInBytes,
    final Pointer beta,
    final cudnnFilterDescriptor dwDesc,
    final Pointer dw) {
    long startTime = System.nanoTime();
    final int result = JCudnn.cudnnConvolutionBackwardFilter(this.handle, alpha, xDesc, x, dyDesc, dy, convDesc, algo, workSpace, workSpaceSizeInBytes, beta, dwDesc, dw);
    cudnnConvolutionBackwardFilter_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnConvolutionBackwardFilter", result, new Object[]{this, alpha, xDesc, x, dyDesc, dy, convDesc, algo, workSpace, workSpaceSizeInBytes, beta, dwDesc, dw});
    return result;
  }
  
  /**
   * Cudnn convolution forward int.
   *
   * @param alpha                the alpha
   * @param xDesc                the x desc
   * @param x                    the x
   * @param wDesc                the w desc
   * @param w                    the w
   * @param convDesc             the conv desc
   * @param algo                 the algo
   * @param workSpace            the work space
   * @param workSpaceSizeInBytes the work space size in bytes
   * @param beta                 the beta
   * @param yDesc                the y desc
   * @param y                    the y
   * @return the int
   */
  public int cudnnConvolutionForward(
    final Pointer alpha,
    final cudnnTensorDescriptor xDesc,
    final Pointer x,
    final cudnnFilterDescriptor wDesc,
    final Pointer w,
    final cudnnConvolutionDescriptor convDesc,
    final int algo,
    final Pointer workSpace,
    final long workSpaceSizeInBytes,
    final Pointer beta,
    final cudnnTensorDescriptor yDesc,
    final Pointer y) {
    long startTime = System.nanoTime();
    final int result = JCudnn.cudnnConvolutionForward(this.handle, alpha, xDesc, x, wDesc, w, convDesc, algo, workSpace, workSpaceSizeInBytes, beta, yDesc, y);
    cudnnConvolutionForward_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnConvolutionForward", result, new Object[]{this, alpha, xDesc, x, wDesc, w, convDesc, algo, workSpace, workSpaceSizeInBytes, beta, yDesc, y});
    return result;
  }
  
  /**
   * Cudnn op tensor int.
   *
   * @param opTensorDesc the op tensor desc
   * @param alpha1       the alpha 1
   * @param aDesc        the a desc
   * @param A            the a
   * @param alpha2       the alpha 2
   * @param bDesc        the b desc
   * @param B            the b
   * @param beta         the beta
   * @param cDesc        the c desc
   * @param C            the c
   * @return the int
   */
  public int cudnnOpTensor(
    final cudnnOpTensorDescriptor opTensorDesc,
    final Pointer alpha1,
    final cudnnTensorDescriptor aDesc,
    final Pointer A,
    final Pointer alpha2,
    final cudnnTensorDescriptor bDesc,
    final Pointer B,
    final Pointer beta,
    final cudnnTensorDescriptor cDesc,
    final Pointer C) {
    long startTime = System.nanoTime();
    final int result = JCudnn.cudnnOpTensor(this.handle, opTensorDesc, alpha1, aDesc, A, alpha2, bDesc, B, beta, cDesc, C);
    cudnnOpTensor_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnOpTensor", result, new Object[]{this, opTensorDesc, alpha1, aDesc, A, alpha2, bDesc, B, beta, cDesc, C});
    return result;
  }
  
  /**
   * Cudnn pooling backward int.
   *
   * @param poolingDesc the pooling desc
   * @param alpha       the alpha
   * @param yDesc       the y desc
   * @param y           the y
   * @param dyDesc      the dy desc
   * @param dy          the dy
   * @param xDesc       the x desc
   * @param x           the x
   * @param beta        the beta
   * @param dxDesc      the dx desc
   * @param dx          the dx
   * @return the int
   */
  public int cudnnPoolingBackward(
    final cudnnPoolingDescriptor poolingDesc,
    final Pointer alpha,
    final cudnnTensorDescriptor yDesc,
    final Pointer y,
    final cudnnTensorDescriptor dyDesc,
    final Pointer dy,
    final cudnnTensorDescriptor xDesc,
    final Pointer x,
    final Pointer beta,
    final cudnnTensorDescriptor dxDesc,
    final Pointer dx) {
    long startTime = System.nanoTime();
    final int result = JCudnn.cudnnPoolingBackward(this.handle, poolingDesc, alpha, yDesc, y, dyDesc, dy, xDesc, x, beta, dxDesc, dx);
    cudnnPoolingBackward_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnPoolingBackward", result, new Object[]{this, poolingDesc, alpha, yDesc, y, dyDesc, dy, xDesc, x, beta, dxDesc, dx});
    return result;
  }
  
  /**
   * Cudnn pooling forward int.
   *
   * @param poolingDesc the pooling desc
   * @param alpha       the alpha
   * @param xDesc       the x desc
   * @param x           the x
   * @param beta        the beta
   * @param yDesc       the y desc
   * @param y           the y
   * @return the int
   */
  public int cudnnPoolingForward(
    final cudnnPoolingDescriptor poolingDesc,
    final Pointer alpha,
    final cudnnTensorDescriptor xDesc,
    final Pointer x,
    final Pointer beta,
    final cudnnTensorDescriptor yDesc,
    final Pointer y) {
    long startTime = System.nanoTime();
    final int result = JCudnn.cudnnPoolingForward(this.handle, poolingDesc, alpha, xDesc, x, beta, yDesc, y);
    cudnnPoolingForward_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnPoolingForward", result, new Object[]{this, poolingDesc, alpha, xDesc, x, beta, yDesc, y});
    return result;
  }
  
  /**
   * Cudnn transform tensor int.
   *
   * @param alpha the alpha
   * @param xDesc the x desc
   * @param x     the x
   * @param beta  the beta
   * @param yDesc the y desc
   * @param y     the y
   * @return the int
   */
  public int cudnnTransformTensor(
    final Pointer alpha,
    final cudnnTensorDescriptor xDesc,
    final Pointer x,
    final Pointer beta,
    final cudnnTensorDescriptor yDesc,
    final Pointer y) {
    long startTime = System.nanoTime();
    final int result = JCudnn.cudnnTransformTensor(this.handle, alpha, xDesc, x, beta, yDesc, y);
    cudnnTransformTensor_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnTransformTensor", result, new Object[]{this, alpha, xDesc, x, beta, yDesc, y});
    return result;
  }
  
  /**
   * Allocate backward data workspace cuda ptr.
   *
   * @param deviceId   the device id
   * @param inputDesc  the input desc
   * @param filterDesc the filter desc
   * @param convDesc   the conv desc
   * @param outputDesc the output desc
   * @param algorithm  the algorithm
   * @return the cuda ptr
   */
  public CudaMemory allocateBackwardDataWorkspace(final CudaDevice deviceId, final cudnnTensorDescriptor inputDesc, final cudnnFilterDescriptor filterDesc, final cudnnConvolutionDescriptor convDesc, final cudnnTensorDescriptor outputDesc, final int algorithm) {
    long startTime = System.nanoTime();
    @javax.annotation.Nonnull final long sizeInBytesArray[] = {0};
    final int result = JCudnn.cudnnGetConvolutionBackwardDataWorkspaceSize(handle,
      filterDesc, outputDesc, convDesc, inputDesc,
      algorithm, sizeInBytesArray);
    allocateBackwardDataWorkspace_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnGetConvolutionBackwardDataWorkspaceSize", result, new Object[]{this, filterDesc, outputDesc, convDesc, inputDesc, algorithm, sizeInBytesArray});
    CudaSystem.handle(result);
    final long size = sizeInBytesArray[0];
    return deviceId.allocate(Math.max(1, size), MemoryType.Device, true);
  }
  
  /**
   * Allocate backward filter workspace cuda ptr.
   *
   * @param deviceId      the device id
   * @param srcTensorDesc the src tensor desc
   * @param filterDesc    the filter desc
   * @param convDesc      the conv desc
   * @param dstTensorDesc the dst tensor desc
   * @param algorithm     the algorithm
   * @return the cuda ptr
   */
  public CudaMemory allocateBackwardFilterWorkspace(final CudaDevice deviceId, final cudnnTensorDescriptor srcTensorDesc, final cudnnFilterDescriptor filterDesc, final cudnnConvolutionDescriptor convDesc, final cudnnTensorDescriptor dstTensorDesc, final int algorithm) {
    long startTime = System.nanoTime();
    @javax.annotation.Nonnull final long sizeInBytesArray[] = {0};
    final int result = JCudnn.cudnnGetConvolutionBackwardFilterWorkspaceSize(handle,
      srcTensorDesc, dstTensorDesc, convDesc, filterDesc,
      algorithm, sizeInBytesArray);
    allocateBackwardFilterWorkspace_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnGetConvolutionBackwardFilterWorkspaceSize", result, new Object[]{this, srcTensorDesc, dstTensorDesc, convDesc, filterDesc, algorithm, sizeInBytesArray});
    CudaSystem.handle(result);
    final long size = sizeInBytesArray[0];
    return deviceId.allocate(Math.max(1, size), MemoryType.Device, true);
  }
  
  /**
   * Allocate forward workspace cuda ptr.
   *
   * @param deviceId      the device id
   * @param srcTensorDesc the src tensor desc
   * @param filterDesc    the filter desc
   * @param convDesc      the conv desc
   * @param dstTensorDesc the dst tensor desc
   * @param algorithm     the algorithm
   * @return the cuda ptr
   */
  public CudaMemory allocateForwardWorkspace(final CudaDevice deviceId, final cudnnTensorDescriptor srcTensorDesc, final cudnnFilterDescriptor filterDesc, final cudnnConvolutionDescriptor convDesc, final cudnnTensorDescriptor dstTensorDesc, final int algorithm) {
    long startTime = System.nanoTime();
    @javax.annotation.Nonnull final long sizeInBytesArray[] = {0};
    final int result = JCudnn.cudnnGetConvolutionForwardWorkspaceSize(handle,
      srcTensorDesc, filterDesc, convDesc, dstTensorDesc,
      algorithm, sizeInBytesArray);
    allocateForwardWorkspace_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnGetConvolutionForwardWorkspaceSize", result, new Object[]{this, srcTensorDesc, filterDesc, convDesc, dstTensorDesc, algorithm, sizeInBytesArray});
    CudaSystem.handle(result);
    final long size = sizeInBytesArray[0];
    return deviceId.allocate(Math.max(1, size), MemoryType.Device, true);
  }
  
  /**
   * Gets backward data algorithm.
   *
   * @param inputDesc          the src tensor desc
   * @param filterDesc         the filter desc
   * @param convDesc           the conv desc
   * @param outputDesc         the weight desc
   * @param memoryLimitInBytes the memory limit in bytes
   * @return the backward data algorithm
   */
  public int getBackwardDataAlgorithm(final cudnnTensorDescriptor inputDesc, final cudnnFilterDescriptor filterDesc, final cudnnConvolutionDescriptor convDesc, final cudnnTensorDescriptor outputDesc, final int memoryLimitInBytes) {
    long startTime = System.nanoTime();
    @javax.annotation.Nonnull final int algoArray[] = {-1};
    final int result = JCudnn.cudnnGetConvolutionBackwardDataAlgorithm(handle,
      filterDesc, inputDesc, convDesc, outputDesc,
      cudnnConvolutionBwdDataPreference.CUDNN_CONVOLUTION_BWD_DATA_SPECIFY_WORKSPACE_LIMIT, memoryLimitInBytes, algoArray);
    getBackwardDataAlgorithm_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnGetConvolutionBackwardDataAlgorithm", result, new Object[]{this, filterDesc, inputDesc, convDesc, outputDesc, cudnnConvolutionBwdDataPreference.CUDNN_CONVOLUTION_BWD_DATA_SPECIFY_WORKSPACE_LIMIT, memoryLimitInBytes, algoArray});
    CudaSystem.handle(result);
    return algoArray[0];
  }
  
  /**
   * Gets backward filter algorithm.
   *
   * @param inputDesc          the input desc
   * @param filterDesc         the filter desc
   * @param convDesc           the conv desc
   * @param outputDesc         the output desc
   * @param memoryLimitInBytes the memory limit in bytes
   * @return the backward filter algorithm
   */
  public int getBackwardFilterAlgorithm(final cudnnTensorDescriptor inputDesc, final cudnnFilterDescriptor filterDesc, final cudnnConvolutionDescriptor convDesc, final cudnnTensorDescriptor outputDesc, final int memoryLimitInBytes) {
    long startTime = System.nanoTime();
    @javax.annotation.Nonnull final int algoArray[] = {-1};
    final int result = JCudnn.cudnnGetConvolutionBackwardFilterAlgorithm(handle,
      inputDesc, outputDesc, convDesc, filterDesc,
      cudnnConvolutionBwdFilterPreference.CUDNN_CONVOLUTION_BWD_FILTER_SPECIFY_WORKSPACE_LIMIT, memoryLimitInBytes, algoArray);
    getBackwardFilterAlgorithm_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnGetConvolutionBackwardFilterAlgorithm", result, new Object[]{this, inputDesc, outputDesc, convDesc, filterDesc, cudnnConvolutionBwdFilterPreference.CUDNN_CONVOLUTION_BWD_FILTER_SPECIFY_WORKSPACE_LIMIT, memoryLimitInBytes, algoArray});
    CudaSystem.handle(result);
    return algoArray[0];
  }
  
  /**
   * Gets forward algorithm.
   *
   * @param srcTensorDesc      the src tensor desc
   * @param filterDesc         the filter desc
   * @param convDesc           the conv desc
   * @param dstTensorDesc      the dst tensor desc
   * @param memoryLimitInBytes the memory limit in bytes
   * @return the forward algorithm
   */
  public int getForwardAlgorithm(final cudnnTensorDescriptor srcTensorDesc, final cudnnFilterDescriptor filterDesc, final cudnnConvolutionDescriptor convDesc, final cudnnTensorDescriptor dstTensorDesc, final int memoryLimitInBytes) {
    long startTime = System.nanoTime();
    @javax.annotation.Nonnull final int algoArray[] = {-1};
    final int result = JCudnn.cudnnGetConvolutionForwardAlgorithm(handle,
      srcTensorDesc, filterDesc, convDesc, dstTensorDesc,
      cudnnConvolutionFwdPreference.CUDNN_CONVOLUTION_FWD_SPECIFY_WORKSPACE_LIMIT, memoryLimitInBytes, algoArray);
    getForwardAlgorithm_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnGetConvolutionForwardAlgorithm", result, new Object[]{this, srcTensorDesc, filterDesc, convDesc, dstTensorDesc, cudnnConvolutionFwdPreference.CUDNN_CONVOLUTION_FWD_SPECIFY_WORKSPACE_LIMIT, memoryLimitInBytes, algoArray});
    CudaSystem.handle(result);
    return algoArray[0];
  }
  
  /**
   * Cudnn activation backward int.
   *
   * @param activationDesc the activation desc
   * @param alpha          the alpha
   * @param yDesc          the y desc
   * @param y              the y
   * @param dyDesc         the dy desc
   * @param dy             the dy
   * @param xDesc          the x desc
   * @param x              the x
   * @param beta           the beta
   * @param dxDesc         the dx desc
   * @param dx             the dx
   * @return the int
   */
  public int cudnnActivationBackward(
    final cudnnActivationDescriptor activationDesc,
    final Pointer alpha,
    final cudnnTensorDescriptor yDesc,
    final Pointer y,
    final cudnnTensorDescriptor dyDesc,
    final Pointer dy,
    final cudnnTensorDescriptor xDesc,
    final Pointer x,
    final Pointer beta,
    final cudnnTensorDescriptor dxDesc,
    final Pointer dx) {
    long startTime = System.nanoTime();
    final int result = JCudnn.cudnnActivationBackward(this.handle, activationDesc, alpha, yDesc, y, dyDesc, dy, xDesc, x, beta, dxDesc, dx);
    cudnnActivationBackward_execution.accept((System.nanoTime() - startTime) / 1e9);
    this.dirty();
    log("cudnnActivationBackward", result, new Object[]{this, activationDesc, alpha, yDesc, y, dyDesc, dy, xDesc, x, beta, dxDesc, dx});
    return result;
  }
  
  @javax.annotation.Nonnull
  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + deviceId + "; " + deviceName + "}@" + Long.toHexString(System.identityHashCode(this));
  }
  
  @Override
  public void finalize() throws Throwable {
    final int result = JCudnn.cudnnDestroy(handle);
    this.dirty();
    log("cudnnDestroy", result, new Object[]{handle});
    CudaSystem.handle(result);
  }
  
  @Override
  protected void cleanup() {
    super.cleanup();
    ArrayList<CudaResourceBase> objsToFree = new ArrayList<>();
    cleanupNative.drainTo(objsToFree);
    objsToFree.stream().forEach(CudaResourceBase::release);
  }
}