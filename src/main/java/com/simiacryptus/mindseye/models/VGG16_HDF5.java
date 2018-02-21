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
package com.simiacryptus.mindseye.models;

import com.simiacryptus.mindseye.lang.Layer;
import com.simiacryptus.mindseye.lang.Result;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.lang.TensorList;
import com.simiacryptus.mindseye.lang.cudnn.Precision;
import com.simiacryptus.mindseye.layers.cudnn.*;
import com.simiacryptus.mindseye.layers.java.BiasLayer;
import com.simiacryptus.mindseye.layers.java.ImgReshapeLayer;
import com.simiacryptus.mindseye.layers.java.SoftmaxActivationLayer;
import com.simiacryptus.mindseye.network.DAGNetwork;
import com.simiacryptus.mindseye.network.PipelineNetwork;
import com.simiacryptus.util.io.NotebookOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Details about this network architecture can be found in the following arXiv paper: Very Deep Convolutional Networks
 * for Large-Scale Image Recognition K. Simonyan, A. Zisserman arXiv:1409.1556 Please cite the paper if you use the
 * models.
 */
public class VGG16_HDF5 extends VGG16 implements DemoableNetworkFactory, HasHDF5 {
  protected static final Logger log = LoggerFactory.getLogger(Hdf5Archive.class);
  protected final Hdf5Archive hdf5;
  protected volatile Layer network;
  
  @Nullable
  Tensor prototype = new Tensor(224, 224, 3);
  int cnt = 1;
  @javax.annotation.Nonnull
  int[] convolutionOrder = {3, 2, 0, 1};
  @javax.annotation.Nonnull
  int[] fullyconnectedOrder = {1, 0};
  @javax.annotation.Nonnull
  protected
  PipelineNetwork model = new PipelineNetwork();
  @javax.annotation.Nonnull
  Precision precision = Precision.Double;
  private boolean large = true;
  private boolean dense = true;
  
  /**
   * Instantiates a new Vgg 16 hdf 5.
   *
   * @param hdf5 the hdf 5
   */
  public VGG16_HDF5(Hdf5Archive hdf5) {this.hdf5 = hdf5;}
  
  /**
   * Gets network.
   *
   * @return the network
   */
  public Layer getNetwork() {
    if (null == network) {
      synchronized (this) {
        if (null == network) {
          network = build();
        }
      }
    }
    return network;
  }
  
  @javax.annotation.Nonnull
  @Override
  public Layer build(@javax.annotation.Nonnull NotebookOutput output) {
    try {
      phase0(output);
      phase1(output, large);
      phase2(output, dense, large);
      phase3(output);
      setPrecision(output);
      if (null != prototype) prototype.freeRef();
      prototype = null;
      return model;
    } catch (@javax.annotation.Nonnull final RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  protected static void add(Layer layer, PipelineNetwork model) {
    name(layer);
    if (layer instanceof Explodable) {
      Layer explode = ((Explodable) layer).explode();
      if (explode instanceof DAGNetwork) {
        ((DAGNetwork) explode).visitNodes(node -> name(node.getLayer()));
        log.info(String.format("Exploded %s to %s (%s nodes)", layer.getName(), explode.getClass().getSimpleName(), ((DAGNetwork) explode).getNodes().size()));
      }
      else {
        log.info(String.format("Exploded %s to %s (%s nodes)", layer.getName(), explode.getClass().getSimpleName(), explode.getName()));
      }
      add(explode, model);
    }
    else {
      model.add(layer);
    }
  }
  
  protected static Tensor evaluatePrototype(final Layer layer, final Tensor prevPrototype, int cnt) {
    int numberOfParameters = layer.state().stream().mapToInt(x -> x.length).sum();
    @javax.annotation.Nonnull int[] prev_dimensions = prevPrototype.getDimensions();
    Result eval = layer.eval(prevPrototype);
    TensorList data = eval.getData();
    if (null != prevPrototype) prevPrototype.freeRef();
    try {
      @javax.annotation.Nonnull int[] new_dimensions = prevPrototype.getDimensions();
      log.info(String.format("Added layer #%d: %s; %s params, dimensions %s (%s) -> %s (%s)", //
        cnt, layer, numberOfParameters, //
        Arrays.toString(prev_dimensions), Tensor.dim(prev_dimensions), //
        Arrays.toString(new_dimensions), Tensor.dim(new_dimensions)));
      return data.get(0);
    } finally {
      eval.freeRef();
      data.freeRef();
    }
  }
  
  protected static void name(final Layer layer) {
    if (layer.getName().contains(layer.getId().toString())) {
      if (layer instanceof ConvolutionLayer) {
        layer.setName(layer.getClass().getSimpleName() + ((ConvolutionLayer) layer).getConvolutionParams());
      }
      else if (layer instanceof SimpleConvolutionLayer) {
        layer.setName(String.format("%s: %s", layer.getClass().getSimpleName(),
          Arrays.toString(((SimpleConvolutionLayer) layer).getKernelDimensions())));
      }
      else if (layer instanceof FullyConnectedLayer) {
        layer.setName(String.format("%s:%sx%s",
          layer.getClass().getSimpleName(),
          Arrays.toString(((FullyConnectedLayer) layer).inputDims),
          Arrays.toString(((FullyConnectedLayer) layer).outputDims)));
      }
      else if (layer instanceof BiasLayer) {
        layer.setName(String.format("%s:%s",
          layer.getClass().getSimpleName(),
          ((BiasLayer) layer).bias.length));
      }
    }
  }
  
  protected void setPrecision(@javax.annotation.Nonnull final NotebookOutput output) {
    output.code(() -> {
      model.visitLayers(layer -> {
        if (layer instanceof MultiPrecision) {
          ((MultiPrecision) layer).setPrecision(precision);
        }
      });
      return precision;
    });
  }
  
  protected void phase0(@javax.annotation.Nonnull NotebookOutput output) {
    output.code(() -> {
      add(new ImgMinSizeLayer(226, 226));
    });
  }
  
  protected void phase1(@javax.annotation.Nonnull NotebookOutput output, final boolean large) {
    phase1a(output);
    phase1b(output);
    phase1c(output, large);
    phase1d(output, large);
    phase1e(output, large);
  }
  
  protected void phase1a(@javax.annotation.Nonnull final NotebookOutput output) {
    addConvolutionLayer(output, 3, 3, 64, ActivationLayer.Mode.RELU, "layer_1");
    addConvolutionLayer(output, 3, 64, 64, ActivationLayer.Mode.RELU, "layer_3");
  }
  
  protected void phase1b(@javax.annotation.Nonnull final NotebookOutput output) {
    addPoolingLayer(output, true);
    addConvolutionLayer(output, 3, 64, 128, ActivationLayer.Mode.RELU, "layer_6");
    addConvolutionLayer(output, 3, 128, 128, ActivationLayer.Mode.RELU, "layer_8");
  }
  
  protected void phase1c(@javax.annotation.Nonnull final NotebookOutput output, final boolean large) {
    addPoolingLayer(output, large);
    addConvolutionLayer(output, 3, 128, 256, ActivationLayer.Mode.RELU, "layer_11");
    addConvolutionLayer(output, 3, 256, 256, ActivationLayer.Mode.RELU, "layer_13");
    addConvolutionLayer(output, 3, 256, 256, ActivationLayer.Mode.RELU, "layer_15");
  }
  
  protected void phase1d(@javax.annotation.Nonnull final NotebookOutput output, final boolean large) {
    addPoolingLayer(output, large);
    addConvolutionLayer(output, 3, 256, 512, ActivationLayer.Mode.RELU, "layer_18");
    addConvolutionLayer(output, 3, 512, 512, ActivationLayer.Mode.RELU, "layer_20");
    addConvolutionLayer(output, 3, 512, 512, ActivationLayer.Mode.RELU, "layer_22");
  }
  
  protected void phase1e(@javax.annotation.Nonnull final NotebookOutput output, final boolean large) {
    addPoolingLayer(output, large);
    addConvolutionLayer(output, 3, 512, 512, ActivationLayer.Mode.RELU, "layer_25");
    addConvolutionLayer(output, 3, 512, 512, ActivationLayer.Mode.RELU, "layer_27");
    addConvolutionLayer(output, 3, 512, 512, ActivationLayer.Mode.RELU, "layer_29");
  }
  
  protected void phase2(@javax.annotation.Nonnull NotebookOutput output, boolean dense, final boolean large) {
    phase2a(output);
    phase2b(output, dense, large);
  }
  
  protected void phase2b(@javax.annotation.Nonnull final NotebookOutput output, final boolean dense, final boolean large) {
    if (large) {
      output.code(() -> {
        add(new ImgModulusPaddingLayer(7, 7));
      });
    }
    else {
      output.code(() -> {
        add(new ImgModulusPaddingLayer(-7, -7));
      });
    }
    
    if (dense) {
      output.code(() -> {
        add(new ConvolutionLayer(7, 7, 512, 4096)
          .setStrideXY(1, 1)
          .setPaddingXY(0, 0)
          .set(hdf5.readDataSet("param_0", "layer_32")
            .reshapeCast(7, 7, 512, 4096).permuteDimensions(0, 1, 3, 2)
          )
        );
      });
    }
    else {
      output.code(() -> {
        add(new ImgModulusPaddingLayer(7, 7));
      });
      output.code(() -> {
        add(new ImgReshapeLayer(7, 7, false));
      });
      output.code(() -> {
        add(new ConvolutionLayer(1, 1, 25088, 4096)
          .setPaddingXY(0, 0)
          .set(hdf5.readDataSet("param_0", "layer_32")
            .permuteDimensions(fullyconnectedOrder))
        );
      });
    }
    
    output.code(() -> {
      add(new ImgBandBiasLayer(4096)
        .set((hdf5.readDataSet("param_1", "layer_32"))));
    });
    output.code(() -> {
      add(new ActivationLayer(ActivationLayer.Mode.RELU));
    });
  }
  
  protected void phase2a(@javax.annotation.Nonnull final NotebookOutput output) {
    //  model.add(MaxPooling2D((2,2), strides=(2,2)))
    addPoolingLayer(output, true);
  }
  
  protected void phase3(@javax.annotation.Nonnull NotebookOutput output) {
    
    output.code(() -> {
      add(new ConvolutionLayer(1, 1, 4096, 4096)
        .setPaddingXY(0, 0)
        .set(hdf5.readDataSet("param_0", "layer_34")
          .permuteDimensions(fullyconnectedOrder))
      );
    });
    output.code(() -> {
      add(new ImgBandBiasLayer(4096)
        .set((hdf5.readDataSet("param_1", "layer_34"))));
    });
    output.code(() -> {
      add(new ActivationLayer(ActivationLayer.Mode.RELU));
    });
    
    output.code(() -> {
      add(new ConvolutionLayer(1, 1, 4096, 1000)
        .setPaddingXY(0, 0)
        .set(hdf5.readDataSet("param_0", "layer_36")
          .permuteDimensions(fullyconnectedOrder))
      );
    });
    output.code(() -> {
      add(new ImgBandBiasLayer(1000)
        .set((hdf5.readDataSet("param_1", "layer_36"))));
    });
    
    output.code(() -> {
      add(new BandReducerLayer()
        .setMode(PoolingLayer.PoolingMode.Avg));
    });
    
    output.code(() -> {
      add(new SoftmaxActivationLayer());
    });
  }
  
  protected void addPoolingLayer(@javax.annotation.Nonnull final NotebookOutput output, final boolean large) {
    if (large) {
      output.code(() -> {
        add(new ImgModulusPaddingLayer(2, 2));
      });
    }
    else {
      output.code(() -> {
        add(new ImgModulusPaddingLayer(-2, -2));
      });
    }
    output.code(() -> {
      add(new PoolingLayer()
        .setMode(PoolingLayer.PoolingMode.Max)
        .setWindowXY(2, 2)
        .setStrideXY(2, 2));
    });
  }
  
  protected void addConvolutionLayer(@javax.annotation.Nonnull final NotebookOutput output, final int radius, final int inputBands, final int outputBands, final ActivationLayer.Mode activationMode, final String hdf_group) {
    output.code(() -> {
      add(new ConvolutionLayer(radius, radius, inputBands, outputBands)
        .setPaddingXY(0, 0)
        .set(hdf5.readDataSet("param_0", hdf_group)
          .permuteDimensions(convolutionOrder))
      );
    });
    output.code(() -> {
      add(new ImgBandBiasLayer(outputBands)
        .set((hdf5.readDataSet("param_1", hdf_group))));
    });
    output.code(() -> {
      add(new ActivationLayer(activationMode));
    });
  }
  
  protected void add(Layer layer) {
    add(layer, model);
    this.prototype = evaluatePrototype(layer, this.prototype, cnt++);
  }
  
  /**
   * Gets hdf 5.
   *
   * @return the hdf 5
   */
  @Override
  public Hdf5Archive getHDF5() {
    return hdf5;
  }
  
  public boolean isLarge() {
    return large;
  }
  
  public VGG16_HDF5 setLarge(boolean large) {
    this.large = large;
    return this;
  }
  
  public boolean isDense() {
    return dense;
  }
  
  public VGG16_HDF5 setDense(boolean dense) {
    this.dense = dense;
    return this;
  }
}
