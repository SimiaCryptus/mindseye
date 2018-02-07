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

package com.simiacryptus.mindseye.labs.encoding;

import com.simiacryptus.mindseye.eval.ArrayTrainable;
import com.simiacryptus.mindseye.eval.SampledArrayTrainable;
import com.simiacryptus.mindseye.eval.SampledTrainable;
import com.simiacryptus.mindseye.eval.TrainableDataMask;
import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.layers.cudnn.ActivationLayer;
import com.simiacryptus.mindseye.layers.cudnn.ConvolutionLayer;
import com.simiacryptus.mindseye.layers.cudnn.ImgBandBiasLayer;
import com.simiacryptus.mindseye.layers.java.ImgCropLayer;
import com.simiacryptus.mindseye.layers.java.RescaledSubnetLayer;
import com.simiacryptus.mindseye.network.DAGNetwork;
import com.simiacryptus.mindseye.network.PipelineNetwork;
import com.simiacryptus.mindseye.opt.TrainingMonitor;
import com.simiacryptus.mindseye.opt.ValidatingTrainer;
import com.simiacryptus.mindseye.opt.line.QuadraticSearch;
import com.simiacryptus.mindseye.opt.orient.GradientDescent;
import com.simiacryptus.mindseye.opt.orient.QQN;
import com.simiacryptus.mindseye.test.StepRecord;
import com.simiacryptus.mindseye.test.TestUtil;
import com.simiacryptus.util.StreamNanoHTTPD;
import com.simiacryptus.util.Util;
import com.simiacryptus.util.io.HtmlNotebookOutput;
import com.simiacryptus.util.io.NotebookOutput;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The type Image encoding pca run.
 */
public class ImageDecompositionLab {
  
  /**
   * The MnistProblemData pipeline.
   */
  public @NotNull List<NNLayer> dataPipeline = new ArrayList<>();
  /**
   * The Display image.
   */
  int displayImage = 5;
  /**
   * The Model no.
   */
  int modelNo = 0;
  
  /**
   * Test.
   *
   * @param args the input arguments
   * @throws Exception the exception
   */
  public static void main(final String... args) throws Exception {
    final @NotNull ImageDecompositionLab lab = new ImageDecompositionLab();
    try (@NotNull NotebookOutput log = lab.report()) {
      lab.run(log);
    }
  }
  
  /**
   * Initialize.
   *
   * @param log              the log
   * @param features         the features
   * @param convolutionLayer the convolution layer
   * @param biasLayer        the bias layer
   */
  protected void initialize(final NotebookOutput log, final @NotNull Supplier<Stream<Tensor[]>> features, final @NotNull ConvolutionLayer convolutionLayer, final @NotNull ImgBandBiasLayer biasLayer) {
    final Tensor prototype = features.get().findAny().get()[1];
    final @NotNull int[] dimensions = prototype.getDimensions();
    final @NotNull int[] filterDimensions = convolutionLayer.getKernel().getDimensions();
    assert filterDimensions[0] == dimensions[0];
    assert filterDimensions[1] == dimensions[1];
    final int outputBands = dimensions[2];
    if (outputBands != biasLayer.getBias().length) {
      throw new AssertionError(String.format("%d != %d", outputBands, biasLayer.getBias().length));
    }
    final int inputBands = filterDimensions[2] / outputBands;
    final @NotNull FindFeatureSpace findFeatureSpace = new FindPCAFeatures(log, inputBands) {
      @Override
      public Stream<Tensor[]> getFeatures() {
        return features.get();
      }
    }.invoke();
    EncodingUtil.setInitialFeatureSpace(convolutionLayer, biasLayer, findFeatureSpace);
  }
  
  /**
   * Gets log.
   *
   * @return the log
   */
  public @NotNull HtmlNotebookOutput report() {
    try {
      final @NotNull String directoryName = new SimpleDateFormat("YYYY-MM-dd-HH-mm").format(new Date());
      final @NotNull File path = new File(Util.mkString(File.separator, "www", directoryName));
      path.mkdirs();
      final @NotNull File logFile = new File(path, "index.html");
      final @NotNull StreamNanoHTTPD server = new StreamNanoHTTPD(1999, "text/html", logFile).init();
      final @NotNull HtmlNotebookOutput log = new HtmlNotebookOutput(path, server.dataReciever);
      return log;
    } catch (final @NotNull IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Run.
   *
   * @param log the log
   */
  public void run(final @NotNull NotebookOutput log) {
    final int pretrainMinutes = 30;
    final int timeoutMinutes = 30;
    final int images = 10;
    final int size = 400;
    @NotNull String source = "H:\\SimiaCryptus\\photos";
    displayImage = images;
  
    final Tensor[][] trainingImages = null == source ? EncodingUtil.getImages(log, size, images, "kangaroo") :
      Arrays.stream(new File(source).listFiles()).map(input -> {
        try {
          return ImageIO.read(input);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }).map(img -> new Tensor[]{
        new Tensor(1.0),
        Tensor.fromRGB(TestUtil.resize(img, size))
      }).toArray(i -> new Tensor[i][]);
  
    Arrays.stream(trainingImages).map(x -> x[1]).map(x -> x.toImage()).map(x -> {
      try {
        return log.image(x, "example");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }).forEach(str -> log.p(str));
    
    log.h1("First Layer");
    final @NotNull InitializationStep step0 = log.code(() -> {
      return new InitializationStep(log, trainingImages,
                                    size, pretrainMinutes, timeoutMinutes, 3, 9, 5);
    }).invoke(); // output: 260
    
    log.h1("Second Layer");
    final @NotNull AddLayerStep step1 = log.code(() -> {
      return new AddLayerStep(log, step0.trainingData, step0.model,
                              2, step0.toSize, pretrainMinutes * 2, timeoutMinutes,
                              step0.band1, 18, 3, 4);
    }).invoke(); // output: 274
    
    log.h1("Third Layer");
    final @NotNull AddLayerStep step2 = log.code(() -> {
      return new AddLayerStep(log, step1.trainingData, step1.integrationModel,
                              3, step1.toSize, pretrainMinutes * 3, timeoutMinutes,
                              step1.band2, 48, 3, 1);
    }).invoke(); // 276
    
    log.h1("Fourth Layer");
    final @NotNull AddLayerStep step3 = log.code(() -> {
      return new AddLayerStep(log, step2.trainingData, step2.integrationModel,
                              4, step2.toSize, pretrainMinutes * 4, timeoutMinutes,
                              step2.band2, 48, 5, 4);
    }).invoke(); // 278
    
    log.h1("Transcoding Different Category");
    log.code(() -> {
      return new TranscodeStep(log, "yin_yang",
                               images, size, timeoutMinutes * 5, step3.integrationModel, step3.toSize, step3.toSize, step3.band2);
    }).invoke();
  }
  
  /**
   * Train.
   *
   * @param log            the log
   * @param monitor        the monitor
   * @param network        the network
   * @param data           the data
   * @param timeoutMinutes the timeout minutes
   * @param mask           the mask
   */
  protected void train(final @NotNull NotebookOutput log, final TrainingMonitor monitor, final NNLayer network, final @NotNull Tensor[][] data, final int timeoutMinutes, final boolean... mask) {
    log.out("Training for %s minutes, mask=%s", timeoutMinutes, Arrays.toString(mask));
    log.code(() -> {
      @NotNull SampledTrainable trainingSubject = new SampledArrayTrainable(data, network, data.length);
      trainingSubject = (SampledTrainable) ((TrainableDataMask) trainingSubject).setMask(mask);
      final @NotNull ValidatingTrainer validatingTrainer = new ValidatingTrainer(trainingSubject, new ArrayTrainable(data, network))
        .setMaxTrainingSize(data.length)
        .setMinTrainingSize(5)
        .setMonitor(monitor)
        .setTimeout(timeoutMinutes, TimeUnit.MINUTES)
        .setMaxIterations(1000);
      validatingTrainer.getRegimen().get(0)
                       .setOrientation(new GradientDescent())
                       .setLineSearchFactory(name -> name.equals(QQN.CURSOR_NAME) ?
                         new QuadraticSearch().setCurrentRate(1.0) :
                         new QuadraticSearch().setCurrentRate(1.0));
      validatingTrainer
        .run();
    });
  }
  
  /**
   * The type Add layer runStep.
   */
  protected class AddLayerStep {
    /**
     * The Band 1.
     */
    public final int band1;
    /**
     * The Band 2.
     */
    public final int band2;
    /**
     * The Bias layer.
     */
    public final @NotNull ImgBandBiasLayer biasLayer;
    /**
     * The Convolution layer.
     */
    public final @NotNull ConvolutionLayer convolutionLayer;
    /**
     * The History.
     */
    public final @NotNull List<StepRecord> history;
    /**
     * The Inner model.
     */
    public final DAGNetwork innerModel;
    /**
     * The Integration model.
     */
    public final PipelineNetwork integrationModel;
    /**
     * The Layer number.
     */
    public final int layerNumber;
    /**
     * The Log.
     */
    public final @NotNull NotebookOutput log;
    /**
     * The Monitor.
     */
    public final @NotNull TrainingMonitor monitor;
    /**
     * The Original out.
     */
    public final @NotNull PrintStream originalOut;
    /**
     * The Pretrain minutes.
     */
    public final int pretrainMinutes;
    /**
     * The Radius.
     */
    public final int radius;
    /**
     * The Scale.
     */
    public final int scale;
    /**
     * The Timeout minutes.
     */
    public final int timeoutMinutes;
    /**
     * The To size.
     */
    public final int toSize;
    /**
     * The Training data.
     */
    public final Tensor[][] trainingData;
    private final int fromSize;
  
    /**
     * Instantiates a new Add layer runStep.
     *
     * @param log             the log
     * @param trainingData    the training data
     * @param priorModel      the prior model
     * @param layerNumber     the layer number
     * @param fromSize        the from size
     * @param pretrainMinutes the pretrain minutes
     * @param timeoutMinutes  the timeout minutes
     * @param band1           the band 1
     * @param band2           the band 2
     * @param radius          the radius
     * @param scale           the scale
     */
    public AddLayerStep(final @NotNull NotebookOutput log, final @NotNull Tensor[][] trainingData, final DAGNetwork priorModel,
                        final int layerNumber, final int fromSize, final int pretrainMinutes, final int timeoutMinutes,
                        final int band1, final int band2, final int radius, final int scale) {
      originalOut = EncodingUtil.rawOut;
      this.log = log;
      this.band1 = band1;
      this.band2 = band2;
      this.layerNumber = layerNumber;
      this.scale = scale;
      if (0 != fromSize % scale) throw new IllegalArgumentException(fromSize + " % " + scale);
      this.fromSize = fromSize;
      toSize = (fromSize / scale + radius - 1) * scale; // 70
      Arrays.stream(trainingData).allMatch(x -> x.length == this.layerNumber - 1);
      this.trainingData = EncodingUtil.addColumn(trainingData, toSize, toSize, band2);
      this.pretrainMinutes = pretrainMinutes;
      this.timeoutMinutes = timeoutMinutes;
      this.radius = radius;
      history = new ArrayList<>();
      monitor = EncodingUtil.getMonitor(history);
      convolutionLayer = new ConvolutionLayer(radius, radius, band2, band1).set(i -> 0.01 * (Math.random() - 0.5));
      biasLayer = new ImgBandBiasLayer(band1);
      innerModel = buildNetwork();
      integrationModel = log.code(() -> {
        final @NotNull PipelineNetwork network = new PipelineNetwork(1);
        network.add(innerModel);
        network.add(priorModel);
        return network;
      });
    }
  
    /**
     * Build network pipeline network.
     *
     * @return the pipeline network
     */
    public PipelineNetwork buildNetwork() {
      return log.code(() -> {
        return new PipelineNetwork(1,
                                   new RescaledSubnetLayer(scale,
                                                           new PipelineNetwork(1,
                                                                               convolutionLayer,
                                                                               biasLayer
                                                           )
                                   ), new ImgCropLayer(fromSize, fromSize)
        );
      });
    }
  
    /**
     * Gets integration model.
     *
     * @return the integration model
     */
    public PipelineNetwork getIntegrationModel() {
      return integrationModel;
    }
  
    /**
     * Get training mask boolean [ ].
     *
     * @return the boolean [ ]
     */
    public @NotNull boolean[] getTrainingMask() {
      final @NotNull boolean[] mask = new boolean[layerNumber + 2];
      mask[layerNumber + 1] = true;
      return mask;
    }
  
    /**
     * Invoke add layer runStep.
     *
     * @return the add layer runStep
     */
    public @NotNull AddLayerStep invoke() {
      dataPipeline.add(innerModel);
      log.code(() -> {
        initialize(log, () -> {
          final @NotNull Stream<Tensor[]> tensors = EncodingUtil.downExplodeTensors(Arrays.stream(trainingData).map(x -> new Tensor[]{x[0], x[layerNumber]}), scale);
          return EncodingUtil.convolutionFeatures(tensors, radius, 1);
        }, convolutionLayer, biasLayer);
      });
      final @NotNull boolean[] mask = getTrainingMask();
  
      {
        log.h2("Initialization");
        log.h3("Training");
        final @NotNull DAGNetwork trainingModel0 = EncodingUtil.buildTrainingModel(innerModel.copy().freeze(), layerNumber, layerNumber + 1);
        train(log, monitor, trainingModel0, trainingData, pretrainMinutes, mask);
        com.simiacryptus.mindseye.test.TestUtil.printHistory(log, history);
        log.h3("Results");
        EncodingUtil.validationReport(log, trainingData, dataPipeline, displayImage);
        EncodingUtil.printModel(log, innerModel, modelNo++);
        com.simiacryptus.mindseye.test.TestUtil.printDataStatistics(log, trainingData);
        history.clear();
      }
  
      log.h2("Tuning");
      log.h3("Training");
      final @NotNull DAGNetwork trainingModel0 = EncodingUtil.buildTrainingModel(innerModel, layerNumber, layerNumber + 1);
      train(log, monitor, trainingModel0, trainingData, timeoutMinutes, mask);
      com.simiacryptus.mindseye.test.TestUtil.printHistory(log, history);
      log.h3("Results");
      EncodingUtil.validationReport(log, trainingData, dataPipeline, displayImage);
      EncodingUtil.printModel(log, innerModel, modelNo++);
      com.simiacryptus.mindseye.test.TestUtil.printDataStatistics(log, trainingData);
      history.clear();
  
      log.h2("Integration Training");
      log.h3("Training");
      final @NotNull DAGNetwork trainingModel1 = EncodingUtil.buildTrainingModel(integrationModel, 1, layerNumber + 1);
      train(log, monitor, trainingModel1, trainingData, timeoutMinutes, mask);
      com.simiacryptus.mindseye.test.TestUtil.printHistory(log, history);
      log.h3("Results");
      EncodingUtil.validationReport(log, trainingData, dataPipeline, displayImage);
      EncodingUtil.printModel(log, innerModel, modelNo++);
      com.simiacryptus.mindseye.test.TestUtil.printDataStatistics(log, trainingData);
      history.clear();
      return this;
    }
  
    @Override
    public @NotNull String toString() {
      return "AddLayerStep{" +
        "toSize=" + toSize +
        ", layerNumber=" + layerNumber +
        ", pretrainMinutes=" + pretrainMinutes +
        ", timeoutMinutes=" + timeoutMinutes +
        ", radius=" + radius +
        ", scale=" + scale +
        ", band1=" + band1 +
        ", band2=" + band2 +
        '}';
    }
  }
  
  /**
   * The type Initialization runStep.
   */
  protected class InitializationStep {
    /**
     * The Band 0.
     */
    public final int band0;
    /**
     * The Band 1.
     */
    public final int band1;
    /**
     * The Bias layer.
     */
    public final @NotNull ImgBandBiasLayer biasLayer;
    /**
     * The Convolution layer.
     */
    public final @NotNull ConvolutionLayer convolutionLayer;
    /**
     * The From size.
     */
    public final int fromSize;
    /**
     * The History.
     */
    public final List<StepRecord> history = new ArrayList<>();
    /**
     * The Log.
     */
    public final NotebookOutput log;
    /**
     * The Model.
     */
    public final DAGNetwork model;
    /**
     * The Monitor.
     */
    public final @NotNull TrainingMonitor monitor;
    /**
     * The Pretrain minutes.
     */
    public final int pretrainMinutes;
    /**
     * The Radius.
     */
    public final int radius;
    /**
     * The Timeout minutes.
     */
    public final int timeoutMinutes;
    /**
     * The To size.
     */
    public final int toSize;
    /**
     * The Training data.
     */
    public final Tensor[][] trainingData;
  
    /**
     * Instantiates a new Initialization runStep.
     *
     * @param log                  the log
     * @param originalTrainingData the original training data
     * @param fromSize             the from size
     * @param pretrainMinutes      the pretrain minutes
     * @param timeoutMinutes       the timeout minutes
     * @param band0                the band 0
     * @param band1                the band 1
     * @param radius               the radius
     */
    public InitializationStep(final NotebookOutput log, final @NotNull Tensor[][] originalTrainingData, final int fromSize, final int pretrainMinutes, final int timeoutMinutes, final int band0, final int band1, final int radius) {
      this.band1 = band1;
      this.band0 = band0;
      this.log = log;
      monitor = EncodingUtil.getMonitor(history);
      this.pretrainMinutes = pretrainMinutes;
      this.timeoutMinutes = timeoutMinutes;
      this.fromSize = fromSize;
      toSize = fromSize + radius - 1;
      trainingData = EncodingUtil.addColumn(originalTrainingData, toSize, toSize, band1);
      this.radius = radius;
      convolutionLayer = new ConvolutionLayer(radius, radius, band1, band0).set(i -> 0.1 * (Math.random() - 0.5));
      biasLayer = new ImgBandBiasLayer(band0);
      model = buildModel();
    }
  
    /**
     * Build model pipeline network.
     *
     * @return the pipeline network
     */
    public PipelineNetwork buildModel() {
      return log.code(() -> {
        final @NotNull PipelineNetwork network = new PipelineNetwork(1);
        network.add(convolutionLayer);
        network.add(biasLayer);
        network.add(new ImgCropLayer(fromSize, fromSize));
        network.add(new ActivationLayer(ActivationLayer.Mode.RELU));
        //addLogging(network);
        return network;
      });
    }
  
    /**
     * Invoke initialization runStep.
     *
     * @return the initialization runStep
     */
    public @NotNull InitializationStep invoke() {
      dataPipeline.add(model);
      log.code(() -> {
        initialize(log, () -> EncodingUtil.convolutionFeatures(Arrays.stream(trainingData).map(x1 -> new Tensor[]{x1[0], x1[1]}), radius, 1), convolutionLayer, biasLayer);
      });
      
      {
        log.h2("Initialization");
        log.h3("Training");
        final @NotNull DAGNetwork trainingModel0 = EncodingUtil.buildTrainingModel(model.copy().freeze(), 1, 2);
        train(log, monitor, trainingModel0, trainingData, pretrainMinutes, false, false, true);
        com.simiacryptus.mindseye.test.TestUtil.printHistory(log, history);
        log.h3("Results");
        EncodingUtil.validationReport(log, trainingData, dataPipeline, displayImage);
        EncodingUtil.printModel(log, model, modelNo++);
        com.simiacryptus.mindseye.test.TestUtil.printDataStatistics(log, trainingData);
        history.clear();
      }
      
      log.h2("Tuning");
      log.h3("Training");
      final @NotNull DAGNetwork trainingModel0 = EncodingUtil.buildTrainingModel(model, 1, 2);
      train(log, monitor, trainingModel0, trainingData, timeoutMinutes, false, false, true);
      com.simiacryptus.mindseye.test.TestUtil.printHistory(log, history);
      log.h3("Results");
      EncodingUtil.validationReport(log, trainingData, dataPipeline, displayImage);
      EncodingUtil.printModel(log, model, modelNo++);
      com.simiacryptus.mindseye.test.TestUtil.printDataStatistics(log, trainingData);
      history.clear();
      
      return this;
    }
  
    @Override
    public @NotNull String toString() {
      return "InitializationStep{" +
        ", fromSize=" + fromSize +
        ", toSize=" + toSize +
        ", pretrainMinutes=" + pretrainMinutes +
        ", timeoutMinutes=" + timeoutMinutes +
        ", radius=" + radius +
        ", band0=" + band0 +
        ", band1=" + band1 +
        '}';
    }
    
  }
  
  /**
   * The type Transcode runStep.
   */
  protected class TranscodeStep {
    /**
     * The Category.
     */
    public final String category;
    /**
     * The History.
     */
    public final List<StepRecord> history = new ArrayList<>();
    /**
     * The Image count.
     */
    public final int imageCount;
    /**
     * The Log.
     */
    public final @NotNull NotebookOutput log;
    /**
     * The Model.
     */
    public final NNLayer model;
    /**
     * The Monitor.
     */
    public final @NotNull TrainingMonitor monitor;
    /**
     * The Size.
     */
    public final int size;
    /**
     * The Training data.
     */
    public final Tensor[][] trainingData;
    /**
     * The Train minutes.
     */
    public final int trainMinutes;
  
    /**
     * Instantiates a new Transcode runStep.
     *
     * @param log                the log
     * @param category           the category
     * @param imageCount         the image count
     * @param size               the size
     * @param trainMinutes       the trainCjGD minutes
     * @param model              the model
     * @param representationDims the representation dims
     */
    public TranscodeStep(final @NotNull NotebookOutput log, final String category, final int imageCount, final int size, final int trainMinutes, final NNLayer model, final int... representationDims) {
      this.category = category;
      this.imageCount = imageCount;
      this.log = log;
      this.size = size;
      this.model = model;
      trainingData = EncodingUtil.addColumn(EncodingUtil.getImages(log, size, imageCount, category), representationDims);
      monitor = EncodingUtil.getMonitor(history);
      this.trainMinutes = trainMinutes;
    }
  
    /**
     * Invoke transcode runStep.
     *
     * @return the transcode runStep
     */
    public @NotNull TranscodeStep invoke() {
      log.h3("Training");
      final @NotNull DAGNetwork trainingModel0 = EncodingUtil.buildTrainingModel(model.copy().freeze(), 1, 2);
      train(log, monitor, trainingModel0, trainingData, trainMinutes, false, false, true);
      com.simiacryptus.mindseye.test.TestUtil.printHistory(log, history);
      log.h3("Results");
      EncodingUtil.validationReport(log, trainingData, Arrays.asList(model), imageCount);
      com.simiacryptus.mindseye.test.TestUtil.printDataStatistics(log, trainingData);
      history.clear();
      return this;
    }
  
    @Override
    public @NotNull String toString() {
      return "TranscodeStep{" +
        "category='" + category + '\'' +
        ", imageCount=" + imageCount +
        ", trainMinutes=" + trainMinutes +
        '}';
    }
  }
  
}
