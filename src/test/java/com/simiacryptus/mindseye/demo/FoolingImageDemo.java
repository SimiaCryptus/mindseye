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

package com.simiacryptus.mindseye.demo;

import com.simiacryptus.mindseye.eval.ArrayTrainable;
import com.simiacryptus.mindseye.eval.Trainable;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.lang.cudnn.CudaSystem;
import com.simiacryptus.mindseye.layers.cudnn.ActivationLayer;
import com.simiacryptus.mindseye.layers.cudnn.BandReducerLayer;
import com.simiacryptus.mindseye.layers.cudnn.GateProductLayer;
import com.simiacryptus.mindseye.layers.cudnn.PoolingLayer;
import com.simiacryptus.mindseye.layers.cudnn.SoftmaxActivationLayer;
import com.simiacryptus.mindseye.layers.java.LinearActivationLayer;
import com.simiacryptus.mindseye.models.Hdf5Archive;
import com.simiacryptus.mindseye.models.ImageClassifier;
import com.simiacryptus.mindseye.models.VGG16;
import com.simiacryptus.mindseye.models.VGG16_HDF5;
import com.simiacryptus.mindseye.network.DAGNetwork;
import com.simiacryptus.mindseye.network.PipelineNetwork;
import com.simiacryptus.mindseye.opt.IterativeTrainer;
import com.simiacryptus.mindseye.opt.line.ArmijoWolfeSearch;
import com.simiacryptus.mindseye.opt.orient.QQN;
import com.simiacryptus.mindseye.test.StepRecord;
import com.simiacryptus.mindseye.test.TestUtil;
import com.simiacryptus.mindseye.test.data.Caltech101;
import com.simiacryptus.util.Util;
import com.simiacryptus.util.io.NotebookOutput;
import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The type Deep dream demo.
 */
public class FoolingImageDemo extends ArtistryDemo {
  
  /**
   * Run.
   */
  @Test
  public void run() {
    run(this::run);
  }
  
  /**
   * Run.
   *
   * @param log the log
   */
  public void run(@Nonnull NotebookOutput log) {
    init();
    
    @Nonnull String logName = "cuda_" + log.getName() + ".log";
    log.p(log.file((String) null, logName, "GPU Log"));
    CudaSystem.addLog(new PrintStream(log.file(logName)));
    
    log.h1("Model");
    VGG16_HDF5 classifier = log.code(() -> {
      final VGG16_HDF5 result;
      try {
        result = new VGG16_HDF5(new Hdf5Archive(Util.cacheFile(TestUtil.S3_ROOT.resolve("vgg16_weights.h5")))) {
          @Override
          protected void phase3b() {
            add(new SoftmaxActivationLayer()
              .setAlgorithm(SoftmaxActivationLayer.SoftmaxAlgorithm.ACCURATE)
              .setMode(SoftmaxActivationLayer.SoftmaxMode.CHANNEL));
            add(new BandReducerLayer()
              .setMode(getFinalPoolingMode()));
          }
        };
      } catch (@Nonnull final RuntimeException e) {
        throw e;
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
      return result.setLarge(true).setFinalPoolingMode(PoolingLayer.PoolingMode.Avg);
    });
    VGG16_HDF5 dreamer = log.code(() -> {
      final VGG16_HDF5 result;
      try {
        result = new VGG16_HDF5(new Hdf5Archive(Util.cacheFile(TestUtil.S3_ROOT.resolve("vgg16_weights.h5")))) {
          @Override
          protected void phase3b() {
            add(new BandReducerLayer()
              .setMode(getFinalPoolingMode()));
          }
        };
      } catch (@Nonnull final RuntimeException e) {
        throw e;
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
      return result.setLarge(true).setFinalPoolingMode(PoolingLayer.PoolingMode.Avg);
    });

//    Tensor[] images = getImages_Caltech(log);
    Tensor[] images = getImages_Artistry(log);
    
    List<String> vgg16Categories = classifier.getCategories();
    for (int itemNumber = 0; itemNumber < images.length; itemNumber++) {
      log.h1("Image " + itemNumber);
      Tensor image = images[itemNumber];
      TestUtil.monitorImage(image, false);
      List<String> categories = classifier.predict(8, image).stream().flatMap(x -> x.keySet().stream()).collect(Collectors.toList());
      log.p("Predictions: %s", categories.stream().reduce((a, b) -> a + "; " + b).get());
      log.p("Evolve from %s to %s", categories.get(0), categories.get(2));
      int totalCategories = vgg16Categories.size();
      Function<IterativeTrainer, IterativeTrainer> config = train -> train
        .setTimeout(90, TimeUnit.MINUTES)
        .setIterationsPerSample(5);
      DAGNetwork network = (DAGNetwork) classifier.getNetwork();
      TestUtil.instrumentPerformance(log, network);
      addLayersHandler(network, server);
      @Nonnull List<Tensor[]> data = Arrays.<Tensor[]>asList(new Tensor[]{
        image, new Tensor(1, 1, totalCategories)
        .set(vgg16Categories.indexOf(categories.get(0)), (double) 1)
        .set(vgg16Categories.indexOf(categories.get(1)), (double) -1)
        .set(vgg16Categories.indexOf(categories.get(2)), (double) 1)
      });
      log.code(() -> {
        for (Tensor[] tensors : data) {
          try {
            log.p(log.image(tensors[0].toImage(), "") + tensors[1]);
          } catch (IOException e1) {
            throw new RuntimeException(e1);
          }
        }
      });
      log.code(() -> {
        @Nonnull ArrayList<StepRecord> history = new ArrayList<>();
        @Nonnull PipelineNetwork clamp = new PipelineNetwork(1);
        clamp.add(new ActivationLayer(ActivationLayer.Mode.RELU));
        clamp.add(new LinearActivationLayer().setBias(255).setScale(-1).freeze());
        clamp.add(new ActivationLayer(ActivationLayer.Mode.RELU));
        clamp.add(new LinearActivationLayer().setBias(255).setScale(-1).freeze());
        @Nonnull PipelineNetwork supervised = new PipelineNetwork(2);
        supervised.wrap(new GateProductLayer(),
          supervised.add(network.freeze(),
            supervised.wrap(clamp, supervised.getInput(0))),
          supervised.getInput(1));
        //      TensorList[] gpuInput = data.stream().map(data1 -> {
        //        return CudnnHandle.eval(gpu -> {
        //          Precision precision = Precision.Float;
        //          return CudaTensorList.wrap(gpu.getPtr(TensorArray.wrap(data1), precision, MemoryType.Managed), 1, image.getDimensions(), precision);
        //        });
        //      }).toArray(i -> new TensorList[i]);
        //      @Nonnull Trainable trainable = new TensorListTrainable(supervised, gpuInput).setVerbosity(1).setMask(true);
        
        @Nonnull Trainable trainable = new ArrayTrainable(supervised, 1).setVerbose(true).setMask(true, false).setData(data);
        config.apply(new IterativeTrainer(trainable)
          .setMonitor(ImageClassifier.getTrainingMonitor(history, supervised))
          .setOrientation(new QQN())
          .setLineSearchFactory(name -> new ArmijoWolfeSearch())
          .setTimeout(60, TimeUnit.MINUTES))
          .setTerminateThreshold(Double.NEGATIVE_INFINITY)
          .runAndFree();
        return TestUtil.plot(history);
      });
      try {
        log.p(log.image(image.toImage(), "result"));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    
    log.setFrontMatterProperty("status", "OK");
  }
  
  /**
   * Get images artistry tensor [ ].
   *
   * @param log the log
   * @return the tensor [ ]
   */
  public Tensor[] getImages_Artistry(@Nonnull final NotebookOutput log) {
    return Stream.of(
      "H:\\SimiaCryptus\\Artistry\\monkeydog.jpg",
      "H:\\SimiaCryptus\\Artistry\\landscape.jpg",
      "H:\\SimiaCryptus\\Artistry\\chimps\\winner.jpg",
      "H:\\SimiaCryptus\\Artistry\\chimps\\working.jpg",
      "H:\\SimiaCryptus\\Artistry\\autumn.jpg"
    ).map(file -> {
      try {
        BufferedImage image = ImageIO.read(new File(file));
        image = TestUtil.resize(image, 400, true);
        return Tensor.fromRGB(image);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }).toArray(i -> new Tensor[i]);
  }
  
  /**
   * Get images caltech tensor [ ].
   *
   * @param log the log
   * @return the tensor [ ]
   */
  public Tensor[] getImages_Caltech(@Nonnull final NotebookOutput log) {
    log.h1("Data");
    return log.code(() -> {
      return Caltech101.trainingDataStream().sorted(getShuffleComparator()).map(labeledObj -> {
        @Nullable BufferedImage img = labeledObj.data.get();
        //img = TestUtil.resize(img, 224);
        return Tensor.fromRGB(img);
      }).limit(50).toArray(i1 -> new Tensor[i1]);
    });
  }
  
  /**
   * Gets shuffle comparator.
   *
   * @param <T> the type parameter
   * @return the shuffle comparator
   */
  public <T> Comparator<T> getShuffleComparator() {
    final int seed = (int) ((System.nanoTime() >>> 8) % (Integer.MAX_VALUE - 84));
    return Comparator.comparingInt(a1 -> System.identityHashCode(a1) ^ seed);
  }
  
  @Nonnull
  protected Class<?> getTargetClass() {
    return VGG16.class;
  }
  
  @Nonnull
  @Override
  public ReportType getReportType() {
    return ReportType.Demos;
  }
}