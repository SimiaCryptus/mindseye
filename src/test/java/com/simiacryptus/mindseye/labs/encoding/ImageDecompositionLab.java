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
import com.simiacryptus.mindseye.test.StepRecord;
import com.simiacryptus.util.StreamNanoHTTPD;
import com.simiacryptus.util.Util;
import com.simiacryptus.util.io.HtmlNotebookOutput;
import com.simiacryptus.util.io.NotebookOutput;

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
  public List<NNLayer> dataPipeline = new ArrayList<>();
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
    final ImageDecompositionLab lab = new ImageDecompositionLab();
    try (NotebookOutput log = lab.report()) {
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
  protected void initialize(final NotebookOutput log, final Supplier<Stream<Tensor[]>> features, final ConvolutionLayer convolutionLayer, final ImgBandBiasLayer biasLayer) {
    final Tensor prototype = features.get().findAny().get()[1];
    final int[] dimensions = prototype.getDimensions();
    final int[] filterDimensions = convolutionLayer.kernel.getDimensions();
    assert filterDimensions[0] == dimensions[0];
    assert filterDimensions[1] == dimensions[1];
    final int outputBands = dimensions[2];
    assert outputBands == biasLayer.getBias().length;
    final int inputBands = filterDimensions[2] / outputBands;
    final FindFeatureSpace findFeatureSpace = new FindPCAFeatures(log, inputBands) {
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
  public HtmlNotebookOutput report() {
    try {
      final String directoryName = new SimpleDateFormat("YYYY-MM-dd-HH-mm").format(new Date());
      final File path = new File(Util.mkString(File.separator, "www", directoryName));
      path.mkdirs();
      final File logFile = new File(path, "index.html");
      final StreamNanoHTTPD server = new StreamNanoHTTPD(1999, "text/html", logFile).init();
      final HtmlNotebookOutput log = new HtmlNotebookOutput(path, server.dataReciever);
      log.addCopy(EncodingUtil.rawOut);
      return log;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Run.
   *
   * @param log the log
   */
  public void run(final NotebookOutput log) {
    final int pretrainMinutes = 5;
    final int timeoutMinutes = 5;
    final int size = 256;
  
    final Tensor[][] trainingImages = EncodingUtil.getImages(log, size, 50, "kangaroo");
    
    log.h1("First Layer");
    final InitializationStep step0 = log.code(() -> {
      return new InitializationStep(log, trainingImages,
        size, pretrainMinutes, timeoutMinutes, 3, 12, 5);
    }).invoke(); // output: 260
    
    log.h1("Second Layer");
    final AddLayerStep step1 = log.code(() -> {
      return new AddLayerStep(log, step0.trainingData, step0.model,
        2, step0.toSize, pretrainMinutes * 2, timeoutMinutes,
        step0.band1, 24, 5, 4);
    }).invoke(); // output: 274
    
    log.h1("Third Layer");
    final AddLayerStep step2 = log.code(() -> {
      return new AddLayerStep(log, step1.trainingData, step1.integrationModel,
        3, step1.toSize, pretrainMinutes * 3, timeoutMinutes,
        step1.band2, 48, 5, 1);
    }).invoke(); // 276
    
    log.h1("Fourth Layer");
    final AddLayerStep step3 = log.code(() -> {
      return new AddLayerStep(log, step2.trainingData, step2.integrationModel,
        4, step2.toSize, pretrainMinutes * 4, timeoutMinutes,
        step2.band2, 48, 5, 4);
    }).invoke(); // 278
    
    log.h1("Transcoding Different Category");
    log.code(() -> {
      return new TranscodeStep(log, "yin_yang",
        100, size, timeoutMinutes * 5, step3.integrationModel, step3.toSize, step3.toSize, step3.band2);
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
  protected void train(final NotebookOutput log, final TrainingMonitor monitor, final NNLayer network, final Tensor[][] data, final int timeoutMinutes, final boolean... mask) {
    log.out("Training for %s minutes, mask=%s", timeoutMinutes, Arrays.toString(mask));
    log.code(() -> {
      SampledTrainable trainingSubject = new SampledArrayTrainable(data, network, data.length);
      trainingSubject = (SampledTrainable) ((TrainableDataMask) trainingSubject).setMask(mask);
      final ValidatingTrainer validatingTrainer = new ValidatingTrainer(trainingSubject, new ArrayTrainable(data, network))
        .setMaxTrainingSize(data.length)
        .setMinTrainingSize(5)
        .setMonitor(monitor)
        .setTimeout(timeoutMinutes, TimeUnit.MINUTES)
        .setMaxIterations(1000);
      validatingTrainer.getRegimen().get(0)
        .setOrientation(new GradientDescent())
        .setLineSearchFactory(name -> new QuadraticSearch().setCurrentRate(1.0));
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
    public final ImgBandBiasLayer biasLayer;
    /**
     * The Convolution layer.
     */
    public final ConvolutionLayer convolutionLayer;
    /**
     * The History.
     */
    public final List<StepRecord> history;
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
    public final NotebookOutput log;
    /**
     * The Monitor.
     */
    public final TrainingMonitor monitor;
    /**
     * The Original out.
     */
    public final PrintStream originalOut;
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
    public AddLayerStep(final NotebookOutput log, final Tensor[][] trainingData, final DAGNetwork priorModel, final int layerNumber, final int fromSize, final int pretrainMinutes, final int timeoutMinutes, final int band1, final int band2, final int radius, final int scale) {
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
        final PipelineNetwork network = new PipelineNetwork(1);
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
    public boolean[] getTrainingMask() {
      final boolean[] mask = new boolean[layerNumber + 3];
      mask[layerNumber + 1] = true;
      return mask;
    }
  
    /**
     * Invoke add layer runStep.
     *
     * @return the add layer runStep
     */
    public AddLayerStep invoke() {
      dataPipeline.add(innerModel);
      log.code(() -> {
        initialize(log, () -> {
          final Stream<Tensor[]> tensors = EncodingUtil.downExplodeTensors(Arrays.stream(trainingData).map(x -> new Tensor[]{x[0], x[layerNumber]}), scale);
          return EncodingUtil.convolutionFeatures(tensors, radius);
        }, convolutionLayer, biasLayer);
      });
      final boolean[] mask = getTrainingMask();
    
      {
        log.h2("Initialization");
        log.h3("Training");
        final DAGNetwork trainingModel0 = EncodingUtil.buildTrainingModel(innerModel.copy().freeze(), layerNumber, layerNumber + 1);
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
      final DAGNetwork trainingModel0 = EncodingUtil.buildTrainingModel(innerModel, layerNumber, layerNumber + 1);
      train(log, monitor, trainingModel0, trainingData, timeoutMinutes, mask);
      com.simiacryptus.mindseye.test.TestUtil.printHistory(log, history);
      log.h3("Results");
      EncodingUtil.validationReport(log, trainingData, dataPipeline, displayImage);
      EncodingUtil.printModel(log, innerModel, modelNo++);
      com.simiacryptus.mindseye.test.TestUtil.printDataStatistics(log, trainingData);
      history.clear();
    
      log.h2("Integration Training");
      log.h3("Training");
      final DAGNetwork trainingModel1 = EncodingUtil.buildTrainingModel(integrationModel, 1, layerNumber + 1);
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
    public String toString() {
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
    public final ImgBandBiasLayer biasLayer;
    /**
     * The Convolution layer.
     */
    public final ConvolutionLayer convolutionLayer;
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
    public final TrainingMonitor monitor;
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
    public InitializationStep(final NotebookOutput log, final Tensor[][] originalTrainingData, final int fromSize, final int pretrainMinutes, final int timeoutMinutes, final int band0, final int band1, final int radius) {
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
        final PipelineNetwork network = new PipelineNetwork(1);
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
    public InitializationStep invoke() {
      dataPipeline.add(model);
      log.code(() -> {
        initialize(log, () -> EncodingUtil.convolutionFeatures(Arrays.stream(trainingData).map(x1 -> new Tensor[]{x1[0], x1[1]}), radius), convolutionLayer, biasLayer);
      });
      
      {
        log.h2("Initialization");
        log.h3("Training");
        final DAGNetwork trainingModel0 = EncodingUtil.buildTrainingModel(model.copy().freeze(), 1, 2);
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
      final DAGNetwork trainingModel0 = EncodingUtil.buildTrainingModel(model, 1, 2);
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
    public String toString() {
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
    public final NotebookOutput log;
    /**
     * The Model.
     */
    public final NNLayer model;
    /**
     * The Monitor.
     */
    public final TrainingMonitor monitor;
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
    public TranscodeStep(final NotebookOutput log, final String category, final int imageCount, final int size, final int trainMinutes, final NNLayer model, final int... representationDims) {
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
    public TranscodeStep invoke() {
      log.h3("Training");
      final DAGNetwork trainingModel0 = EncodingUtil.buildTrainingModel(model.copy().freeze(), 1, 2);
      train(log, monitor, trainingModel0, trainingData, trainMinutes, false, false, true);
      com.simiacryptus.mindseye.test.TestUtil.printHistory(log, history);
      log.h3("Results");
      EncodingUtil.validationReport(log, trainingData, Arrays.asList(model), imageCount);
      com.simiacryptus.mindseye.test.TestUtil.printDataStatistics(log, trainingData);
      history.clear();
      return this;
    }
  
    @Override
    public String toString() {
      return "TranscodeStep{" +
        "category='" + category + '\'' +
        ", imageCount=" + imageCount +
        ", trainMinutes=" + trainMinutes +
        '}';
    }
  }
  
}
