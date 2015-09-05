package com.simiacryptus.mindseye.training;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.jblas.DoubleMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.layers.BiasLayer;
import com.simiacryptus.mindseye.layers.DenseSynapseLayer;
import com.simiacryptus.mindseye.layers.NNLayer;
import com.simiacryptus.mindseye.math.Coordinate;
import com.simiacryptus.mindseye.math.NDArray;
import com.simiacryptus.mindseye.training.TrainingContext.TerminationCondition;
import com.simiacryptus.mindseye.util.Util;

public class MutationTrainer {

  private static final Logger log = LoggerFactory.getLogger(MutationTrainer.class);
  
  private int currentGeneration = 0;
  private final DynamicRateTrainer inner = new DynamicRateTrainer();
  private int maxIterations = 100;
  double mutationAmplitude = 5.;
  private double mutationFactor = .1;
  private double stopError = 0.1;
  private boolean verbose = false;

  private PipelineNetwork initial;

  public boolean continueTraining(TrainingContext trainingContext) {
    if (this.maxIterations < this.currentGeneration) {
      if (this.verbose) {
        MutationTrainer.log.debug("Reached max iterations: " + this.currentGeneration);
      }
      return false;
    }
    if (getDynamicRateTrainer().error(trainingContext) < this.stopError) {
      if (this.verbose) {
        MutationTrainer.log.debug("Reached convergence: " + getDynamicRateTrainer().error(trainingContext));
      }
      return false;
    }
    return true;
  }

  private double entropy(final BiasLayer l) {
    return 0;
  }

  private double entropy(final DenseSynapseLayer l, final Coordinate idx) {
    final NDArray weights = l.weights;
    final int[] dims = weights.getDims();
    final int columns = dims[0];
    final int rows = dims[1];
    final DoubleMatrix matrix = new DoubleMatrix(columns, rows, weights.getData()).transpose();
    // DoubleMatrix matrix = new DoubleMatrix(rows, columns, l.weights.getData());
    return IntStream.range(0, rows).filter(i -> i == idx.coords[1]).mapToDouble(i -> i).flatMap(i -> {
      return IntStream.range(0, rows).mapToDouble(j -> {
        final ArrayRealVector vi = new ArrayRealVector(matrix.getRow((int) i).toArray());
        if (vi.getNorm() <= 0.) return 0.;
        vi.unitize();
        final ArrayRealVector vj = new ArrayRealVector(matrix.getRow(j).toArray());
        if (vj.getNorm() <= 0.) return 0.;
        vj.unitize();
        return Math.acos(vi.cosine(vj));
      });
    }).average().getAsDouble();
  }

  public double error(TrainingContext trainingContext) {
    return getGradientDescentTrainer().getError();
  }

  public GradientDescentTrainer getBest() {
    return getGradientDescentTrainer();
  }

  public int getGenerationsSinceImprovement() {
    return getDynamicRateTrainer().generationsSinceImprovement;
  }

  public DynamicRateTrainer getDynamicRateTrainer() {
    return this.inner;
  }
  
  public int getMaxIterations() {
    return this.maxIterations;
  }
  
  public double getMaxRate() {
    return getDynamicRateTrainer().getMaxRate();
  }
  
  public double getMinRate() {
    return getDynamicRateTrainer().minRate;
  }
  
  public double getMutationAmplitude() {
    return this.mutationAmplitude;
  }

  public double getMutationFactor() {
    return this.mutationFactor;
  }

  public double getRate() {
    return getDynamicRateTrainer().getRate();
  }
  
  public int getRecalibrationThreshold() {
    return getDynamicRateTrainer().recalibrationThreshold;
  }
  
  public double getStopError() {
    return this.stopError;
  }

  public boolean isVerbose() {
    return this.verbose;
  }

  public int mutate(final BiasLayer l, final double amount) {
    final double[] a = l.bias;
    final Random random = Util.R.get();
    int sum = 0;
    for (int i = 0; i < a.length; i++)
    {
      if (random.nextDouble() < amount) {
        final double prev = a[i];
        final double prevEntropy = entropy(l);
        a[i] = randomWeight(l, random);
        final double nextEntropy = entropy(l);
        if (nextEntropy < prevEntropy) {
          a[i] = prev;
        } else {
          sum += 1;
        }
      }
    }
    return sum;
  }

  public int mutate(final DenseSynapseLayer l, final double amount) {
    final double[] a = l.weights.getData();
    final Random random = Util.R.get();
    return l.weights.coordStream().mapToInt(idx -> {
      final int i = idx.index;
      if (random.nextDouble() < amount) {
        final double prev = a[i];
        final double prevEntropy = entropy(l, idx);
        a[i] = randomWeight(l, random);
        final double nextEntropy = entropy(l, idx);
        if (nextEntropy < prevEntropy) {
          a[i] = prev;
          return 0;
        } else return 1;
      } else return 0;
    }).sum();
  }
  
  public int mutate(final double amount, TrainingContext trainingContext) {
    if (this.verbose) {
      MutationTrainer.log.debug(String.format("Mutating %s by %s", getDynamicRateTrainer(), amount));
    }
    final List<NNLayer> layers = trainingContext.getLayers();
    final int sum =
        layers.stream()
            .filter(l -> (l instanceof DenseSynapseLayer))
            .map(l -> (DenseSynapseLayer) l)
            .filter(l -> !l.isFrozen())
            .mapToInt(l -> mutate(l, amount))
            .sum() +
            layers.stream()
                .filter(l -> (l instanceof BiasLayer))
                .map(l -> (BiasLayer) l)
                .filter(l -> !l.isFrozen())
                .mapToInt(l -> mutate(l, amount))
                .sum();
    getGradientDescentTrainer().setError(Double.NaN);
    return sum;
  }

  public GradientDescentTrainer getGradientDescentTrainer() {
    return getDynamicRateTrainer().getGradientDescentTrainer();
  }
  
  public void mutateBest(TrainingContext trainingContext) {
    getDynamicRateTrainer().generationsSinceImprovement = getDynamicRateTrainer().recalibrationThreshold - 1;
    trainingContext.setNet(Util.kryo().copy(this.initial));
    while (0 >= mutate(getMutationFactor(), trainingContext)) {
    }
    getDynamicRateTrainer().lastCalibratedIteration = getDynamicRateTrainer().currentIteration;// - (this.recalibrationInterval + 2);
  }

  private double randomWeight(final BiasLayer l, final Random random) {
    return this.mutationAmplitude * random.nextGaussian() * 0.2;
  }
  
  public double randomWeight(final DenseSynapseLayer l, final Random random) {
    return this.mutationAmplitude * random.nextGaussian() / Math.sqrt(l.weights.getDims()[0]);
  }
  
  public MutationTrainer setGenerationsSinceImprovement(final int generationsSinceImprovement) {
    getDynamicRateTrainer().generationsSinceImprovement = generationsSinceImprovement;
    return this;
  }
  
  public MutationTrainer setMaxIterations(final int maxIterations) {
    this.maxIterations = maxIterations;
    return this;
  }
  
  public MutationTrainer setMaxRate(final double maxRate) {
    getDynamicRateTrainer().setMaxRate(maxRate);
    return this;
  }
  
  public MutationTrainer setMinRate(final double minRate) {
    getDynamicRateTrainer().minRate = minRate;
    return this;
  }
  
  public MutationTrainer setMutationAmount(final double mutationAmount) {
    getDynamicRateTrainer().setMutationFactor(mutationAmount);
    return this;
  }

  public MutationTrainer setMutationAmplitude(final double mutationAmplitude) {
    this.mutationAmplitude = mutationAmplitude;
    return this;
  }

  public void setMutationFactor(final double mutationRate) {
    this.mutationFactor = mutationRate;
  }

  public MutationTrainer setRate(final double rate) {
    getDynamicRateTrainer().setRate(rate);
    return this;
  }

  public MutationTrainer setRecalibrationThreshold(final int recalibrationThreshold) {
    getDynamicRateTrainer().recalibrationThreshold = recalibrationThreshold;
    return this;
  }
  
  public MutationTrainer setStopError(final double stopError) {
    getDynamicRateTrainer().setStopError(stopError);
    this.stopError = stopError;
    return this;
  }
  
  public MutationTrainer setVerbose(final boolean verbose) {
    this.verbose = verbose;
    getDynamicRateTrainer().setVerbose(verbose);
    return this;
  }
  
  public Double train(TrainingContext trainingContext) {
    final long startMs = System.currentTimeMillis();
    this.currentGeneration = 0;
    try {
      while (continueTraining(trainingContext)) {
        if (0 == this.currentGeneration++) {
          initialize(trainingContext);
          this.initial = Util.kryo().copy(trainingContext.getNet());
        } else {
          trainingContext.mutations.increment();
          mutateBest(trainingContext);
        }
        getDynamicRateTrainer().trainToLocalOptimum(trainingContext);
        if (this.verbose) {
          MutationTrainer.log.debug(String.format("Trained Iteration %s Error: %s (%s) with rate %s\n%s",
              this.currentGeneration, 
              getDynamicRateTrainer().error(trainingContext), 
              getGradientDescentTrainer().getError(),
              getGradientDescentTrainer().getRate(),
              trainingContext.getNet()));
        }
      } 
    } catch (TerminationCondition e) {
      log.debug("Terminated training",e);
    }
    MutationTrainer.log.info(String.format("Completed training to %.5f in %.03fs (%s iterations) - %s", getDynamicRateTrainer().error(trainingContext),
        (System.currentTimeMillis() - startMs) / 1000.,
        this.currentGeneration, trainingContext));
    final GradientDescentTrainer best = getBest();
    return null == best ? Double.POSITIVE_INFINITY : best.getError();
  }

  public void initialize(TrainingContext trainingContext) {
    for(int i=0;i<5;i++) mutate(1, trainingContext);
  }
}
