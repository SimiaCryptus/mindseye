package com.simiacryptus.mindseye.test.demo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.encog.ml.data.MLData;
import org.encog.ml.data.basic.BasicMLDataSet;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.training.propagation.Propagation;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;
import org.encog.util.Format;
import org.encog.util.simple.EncogUtility;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.NDArray;
import com.simiacryptus.mindseye.Util;
import com.simiacryptus.mindseye.data.GaussianDistribution;
import com.simiacryptus.mindseye.data.Simple2DCircle;
import com.simiacryptus.mindseye.data.Simple2DLine;
import com.simiacryptus.mindseye.data.SnakeDistribution;
import com.simiacryptus.mindseye.data.UnionDistribution;
import com.simiacryptus.mindseye.net.DAGNetwork;
import com.simiacryptus.mindseye.net.NNLayer;
import com.simiacryptus.mindseye.net.basic.EntropyLossLayer;
import com.simiacryptus.mindseye.test.Tester;

public class EncogClassificationTests {

  public static class ClassificationResultMetrics {
    public double classificationAccuracy;
    public NDArray classificationMatrix;
    public double pts = 0;
    public double sumSqErr;

    public ClassificationResultMetrics(final int categories) {
      this.classificationMatrix = new NDArray(categories, categories);
    }

    @Override
    public String toString() {
      final StringBuilder builder = new StringBuilder();
      builder.append("ClassificationResultMetrics [");
      if (this.pts > 0) {
        builder.append("error=");
        builder.append(Math.sqrt(this.sumSqErr / this.pts));
      }
      builder.append(", accuracy=");
      builder.append(this.classificationAccuracy);
      builder.append(", classificationMatrix=");
      builder.append(this.classificationMatrix);
      builder.append("]");
      return builder.toString();
    }

  }

  static final List<Color> colorMap = Arrays.asList(Color.RED, Color.GREEN, EncogClassificationTests.randomColor(), EncogClassificationTests.randomColor(),
      EncogClassificationTests.randomColor(), EncogClassificationTests.randomColor(), EncogClassificationTests.randomColor(), EncogClassificationTests.randomColor(),
      EncogClassificationTests.randomColor(), EncogClassificationTests.randomColor());

  static final Logger log = LoggerFactory.getLogger(EncogClassificationTests.class);

  public static Color randomColor() {
    final Random r = Util.R.get();
    return new Color(r.nextInt(255), r.nextInt(255), r.nextInt(255));
  }

  boolean drawBG = true;

  public EncogClassificationTests() {
    super();
  }

  public Tester buildTrainer(final NDArray[][] samples, final NNLayer<DAGNetwork> net) {
    return new Tester().init(samples, net, (NNLayer<?>) new EntropyLossLayer());
  }

  public Color getColor(final NDArray input, final int classificationActual, final int classificationExpected) {
    final Color color = EncogClassificationTests.colorMap.get(classificationExpected);
    return color;
  }

  public NDArray[][] getTrainingData(final int dimensions, final List<Function<Void, double[]>> populations) throws FileNotFoundException, IOException {
    return getTrainingData(dimensions, populations, 100);
  }

  public NDArray[][] getTrainingData(final int dimensions, final List<Function<Void, double[]>> populations, final int sampleN) throws FileNotFoundException, IOException {
    final int[] inputSize = new int[] { dimensions };
    final int[] outSize = new int[] { populations.size() };
    final NDArray[][] samples = IntStream.range(0, populations.size()).mapToObj(x -> x).flatMap(p -> IntStream.range(0, sampleN).mapToObj(i -> {
      return new NDArray[] { new NDArray(inputSize, populations.get(p).apply(null)),
          new NDArray(inputSize, IntStream.range(0, outSize[0]).mapToDouble(x -> p.equals(x) ? 1 : 0).toArray()) };
    })).toArray(i -> new NDArray[i][]);
    return samples;
  }

  public double[] inputToXY(final NDArray input, final int classificationActual, final int classificationExpected) {
    final double xf = input.get(0);
    final double yf = input.get(1);
    return new double[] { xf, yf };
  }

  public Integer outputToClassification(final double[] actual) {
    return IntStream.range(0, actual.length).mapToObj(o -> o).max(Comparator.comparing(o -> actual[o])).get();
  }

  public void test(final NDArray[][] samples) throws FileNotFoundException, IOException {

    final Map<BufferedImage, String> images = new HashMap<>();
    final int categories = samples[0][1].dim();
    final Function<BasicNetwork, Void> handler = net -> {
      try {
        final ClassificationResultMetrics correct = new ClassificationResultMetrics(categories);
        final BufferedImage img = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB) {
          {
            if (EncogClassificationTests.this.drawBG) {
              for (int xpx = 0; xpx < getWidth(); xpx++) {
                for (int ypx = 0; ypx < getHeight(); ypx++) {
                  final double xf = (xpx * 1. / getWidth() - .5) * 6;
                  final double yf = (ypx * 1. / getHeight() - .5) * 6;
                  final MLData eval = net.compute(new org.encog.ml.data.basic.BasicMLData(new double[] { xf, yf }));
                  // .eval(new NDArray(new int[] { 2 }, new double[] { xf, yf
                  // }));
                  final int classificationActual = outputToClassification(eval.getData());
                  final int color = 0 == classificationActual ? 0x1F0000 : 0x001F00;
                  this.setRGB(xpx, ypx, color);
                }
              }
            }
            final Graphics2D g = (Graphics2D) getGraphics();
            correct.pts++;
            correct.classificationAccuracy = Stream.of(samples).mapToDouble(pt -> {
              final NDArray expectedOutput = pt[1];
              final NDArray input = pt[0];
              final MLData output = net.compute(new org.encog.ml.data.basic.BasicMLData(input.getData()));
              final double[] actualOutput = output.getData();
              correct.sumSqErr += IntStream.range(0, actualOutput.length).mapToDouble(i -> {
                final double x = expectedOutput.get(i) - actualOutput[i];
                return x * x;
              }).average().getAsDouble();

              final int classificationExpected = outputToClassification(expectedOutput.getData());
              final int classificationActual = outputToClassification(actualOutput);
              final double[] coords = inputToXY(input, classificationActual, classificationExpected);
              final double xf = coords[0];
              final double yf = coords[1];
              final int xpx = (int) ((xf + 3) / 6 * getHeight());
              final int ypx = (int) ((yf + 3) / 6 * getHeight());
              final Color color = getColor(input, classificationActual, classificationExpected);
              g.setColor(color);
              g.drawOval(xpx, ypx, 1, 1);
              correct.classificationMatrix.add(new int[] { classificationExpected, classificationActual }, 1.);
              return classificationExpected == classificationActual ? 1. : 0.;
            }).average().getAsDouble();
          }
        };
        final String label = correct.toString();
        EncogClassificationTests.log.debug(label);
        images.put(img, label);
      } catch (final Exception e) {
        e.printStackTrace();
      }
      return null;
    };
    try {
      IntStream.range(0, 10).parallel().forEach(thread -> {
        final BasicNetwork network = EncogUtility.simpleFeedForward(2, 10, 0, 2, false);
        final BasicMLDataSet trainingSet = new BasicMLDataSet(Stream.of(samples).map(x -> x[0].getData()).toArray(i -> new double[i][]),
            Stream.of(samples).map(x -> x[1].getData()).toArray(i -> new double[i][]));
        final BasicNetwork network1 = network;
        final Propagation train = new ResilientPropagation(network1, trainingSet);
        train.setThreadCount(0);
        int epoch = 1;
        System.out.println("Beginning training...");
        final double target = 0.001;
        final long timeout = System.currentTimeMillis() + java.util.concurrent.TimeUnit.SECONDS.toMillis(15);
        do {
          train.iteration();
          System.out.println("Iteration #" + Format.formatInteger(epoch) + " Error:" + Format.formatPercent(train.getError()) + " Target Error: " + Format.formatPercent(target));
          epoch++;
        } while (train.getError() > target && !train.isTrainingDone() && System.currentTimeMillis() < timeout);
        train.finishTraining();
        handler.apply(network);
      });
    } finally {
      final Stream<String> map = images.entrySet().stream().map(e -> Util.toInlineImage(e.getKey(), e.getValue().toString()));
      final String[] array = map.toArray(i -> new String[i]);
      Util.report(array);
    }
  }

  @Test(expected = RuntimeException.class)
  @Ignore
  public void test_Gaussians() throws Exception {
    test(getTrainingData(2, Arrays.<Function<Void, double[]>>asList(new GaussianDistribution(2), new GaussianDistribution(2)), 100));
  }

  @Test(expected = RuntimeException.class)
  public void test_II() throws Exception {
    final double e = 1e-1;
    test(getTrainingData(2, Arrays.<Function<Void, double[]>>asList(new Simple2DLine(new double[] { -1, -1 }, new double[] { 1, 1 }),
        new Simple2DLine(new double[] { -1 + e, -1 - e }, new double[] { 1 + e, 1 - e })), 100));
  }

  @Test(expected = RuntimeException.class)
  public void test_III() throws Exception {
    final double e = 1e-1;
    test(getTrainingData(2, Arrays.<Function<Void, double[]>>asList(new UnionDistribution(new Simple2DLine(new double[] { -1 + e, -1 - e }, new double[] { 1 + e, 1 - e }),
        new Simple2DLine(new double[] { -1 - e, -1 + e }, new double[] { 1 - e, 1 + e })), new Simple2DLine(new double[] { -1, -1 }, new double[] { 1, 1 })), 100));
  }

  @Test(expected = RuntimeException.class)
  @Ignore
  public void test_Lines() throws Exception {
    test(getTrainingData(2, Arrays.<Function<Void, double[]>>asList(new Simple2DLine(Util.R.get()), new Simple2DLine(Util.R.get())), 100));
  }

  @Test(expected = RuntimeException.class)
  public void test_O() throws Exception {
    test(getTrainingData(2, Arrays.<Function<Void, double[]>>asList(new UnionDistribution(new Simple2DCircle(2, new double[] { 0, 0 })),
        new UnionDistribution(new Simple2DCircle(0.1, new double[] { 0, 0 }))), 100));
  }

  @Test(expected = RuntimeException.class)
  public void test_O2() throws Exception {
    test(getTrainingData(2, Arrays.<Function<Void, double[]>>asList(new UnionDistribution(new Simple2DCircle(2, new double[] { 0, 0 })),
        new UnionDistribution(new Simple2DCircle(1.75, new double[] { 0, 0 }))), 100));
  }

  @Test(expected = RuntimeException.class)
  // @Ignore
  public void test_O22() throws Exception {
    test(getTrainingData(2,
        Arrays.<Function<Void, double[]>>asList(
            new UnionDistribution(new Simple2DCircle(2, new double[] { 0, 0 }), new Simple2DCircle(2 * (1.75 * 1.75) / 4, new double[] { 0, 0 })),
            new UnionDistribution(new Simple2DCircle(1.75, new double[] { 0, 0 }))),
        100));
  }

  @Test(expected = RuntimeException.class)
  // @Ignore
  public void test_O3() throws Exception {
    test(getTrainingData(2, Arrays.<Function<Void, double[]>>asList(new UnionDistribution(new GaussianDistribution(2, new double[] { 0, 0 }, 1)),
        new UnionDistribution(new Simple2DCircle(.5, new double[] { 0, 0 }))), 1000));
  }

  @Test(expected = RuntimeException.class)
  public void test_oo() throws Exception {
    test(getTrainingData(2, Arrays.<Function<Void, double[]>>asList(new UnionDistribution(new Simple2DCircle(1, new double[] { -0.5, 0 })),
        new UnionDistribution(new Simple2DCircle(1, new double[] { 0.5, 0 }))), 100));
  }

  @Test(expected = RuntimeException.class)
  public void test_simple() throws Exception {
    test(getTrainingData(2, Arrays.<Function<Void, double[]>>asList(new UnionDistribution(new GaussianDistribution(2, new double[] { 0, 0 }, 0.1)),
        new UnionDistribution(new GaussianDistribution(2, new double[] { 1, 1 }, 0.1))), 100));
  }

  @Test(expected = RuntimeException.class)
  @Ignore
  public void test_snakes() throws Exception {
    test(getTrainingData(2, Arrays.<Function<Void, double[]>>asList(new SnakeDistribution(2, Util.R.get(), 7, 0.01), new SnakeDistribution(2, Util.R.get(), 7, 0.01)), 100));
  }

  @Test(expected = RuntimeException.class)
  public void test_sos() throws Exception {
    test(getTrainingData(2, Arrays.<Function<Void, double[]>>asList(new UnionDistribution(new GaussianDistribution(2, new double[] { 0, 0 }, 0.1)),
        new UnionDistribution(new GaussianDistribution(2, new double[] { -1, 0 }, 0.1), new GaussianDistribution(2, new double[] { 1, 0 }, 0.1))), 100));
  }

  @Test(expected = RuntimeException.class)
  public void test_X() throws Exception {
    test(getTrainingData(2,
        Arrays.<Function<Void, double[]>>asList(new Simple2DLine(new double[] { -1, -1 }, new double[] { 1, 1 }), new Simple2DLine(new double[] { -1, 1 }, new double[] { 1, -1 })),
        100));
  }

  @Test(expected = RuntimeException.class)
  public void test_xor() throws Exception {
    test(getTrainingData(2,
        Arrays.<Function<Void, double[]>>asList(
            new UnionDistribution(new GaussianDistribution(2, new double[] { 0, 0 }, 0.1), new GaussianDistribution(2, new double[] { 1, 1 }, 0.1)),
            new UnionDistribution(new GaussianDistribution(2, new double[] { 1, 0 }, 0.1), new GaussianDistribution(2, new double[] { 0, 1 }, 0.1))),
        100));
  }

}
