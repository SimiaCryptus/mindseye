package com.simiacryptus.mindseye.layers;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.DoubleSupplier;
import java.util.stream.IntStream;

import org.jblas.DoubleMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.Coordinate;
import com.simiacryptus.mindseye.NDArray;
import com.simiacryptus.mindseye.Util;
import com.simiacryptus.mindseye.learning.DeltaInversionBuffer;
import com.simiacryptus.mindseye.learning.DeltaMassMomentum;
import com.simiacryptus.mindseye.learning.DeltaFlushBuffer;
import com.simiacryptus.mindseye.learning.DeltaMemoryWriter;
import com.simiacryptus.mindseye.learning.DeltaTransaction;
import com.simiacryptus.mindseye.learning.GradientDescentAccumulator;
import com.simiacryptus.mindseye.learning.MassParameters;
import com.simiacryptus.mindseye.learning.NNResult;

public class ConvolutionSynapseLayer extends NNLayer implements MassParameters<ConvolutionSynapseLayer>, DeltaTransaction {
  private static final Logger log = LoggerFactory.getLogger(ConvolutionSynapseLayer.class);
  
  public final NDArray kernel;
  private DeltaMassMomentum massMomentum;
  private GradientDescentAccumulator deltaBuffer;
  private boolean verbose = false;
  private boolean frozen = false;
  private DeltaFlushBuffer flush;
//  NDArray _inputGradient;
  private boolean paralell = false;
  
  protected ConvolutionSynapseLayer() {
    super();
    kernel = null;
  }

  public ConvolutionSynapseLayer(final int[] kernelDims, final int bandwidth) {
    
    final int[] kernelDims2 = Arrays.copyOf(kernelDims, kernelDims.length + 1);
    kernelDims2[kernelDims2.length - 1] = bandwidth;
    this.kernel = new NDArray(kernelDims2);
    DeltaMemoryWriter writer = new DeltaMemoryWriter(this.kernel);
    this.massMomentum = new DeltaMassMomentum(writer);
    this.flush = new DeltaFlushBuffer(this.massMomentum);
    this.deltaBuffer = new GradientDescentAccumulator(this.flush);
  }

  @Override
  public NNResult eval(final NNResult inObj) {
    final NDArray input = inObj.data;
    final int[] inputDims = input.getDims();
    final int[] kernelDims = this.kernel.getDims();
    final int[] newDims = IntStream.range(0, kernelDims.length).map(
        i -> (i == kernelDims.length - 1) ? kernelDims[i] : (inputDims[i] - kernelDims[i] + 1)).toArray();
    final NDArray output = new NDArray(newDims);
    final NDArray weightGradient = this.frozen ? null : new NDArray(this.kernel.dim(), output.dim());
    this.kernel.coordStream(paralell).forEach(k -> {
      output.coordStream(paralell).forEach(o -> {
        final double a = this.kernel.get(k);
        final int[] i = Arrays.copyOfRange(Coordinate.add(k.coords, o.coords), 0, inputDims.length);
        final double b = input.get(i);
        if (null != weightGradient) {
          weightGradient.add(new int[] { k.index, o.index }, b);
        }
        output.add(o, b * a);
      });
    });
    if (isVerbose()) {
      log.debug(String.format("Feed forward: %s * %s %n\t=> %s", inObj.data, this.kernel, output));
    }
    return new NNResult(output) {
      @Override
      public void feedback(final NDArray errorSignal) {
        if (null != weightGradient) {
          ConvolutionSynapseLayer.this.deltaBuffer.feed(weightGradient, errorSignal.data);
        }
        if (inObj.isAlive()) {
          NDArray backprop = new NDArray(inputDims);
          
          new NDArray(kernelDims).coordStream(paralell).parallel().forEach(kernelCoord -> {
            errorSignal.coordStream(paralell)
            //.filter(o->IntStream.range(inputDims.length, o.coords.length).allMatch(i->o.coords[i]==kernelCoord.coords[i]))
            .forEach(errorCoord -> {
              final int[] i = Arrays.copyOfRange(Coordinate.add(kernelCoord.coords, errorCoord.coords), 0, inputDims.length);
              //final int[] i = Coordinate.add(k.coords, o.coords);
              final double kernelValue = kernel.get(kernelCoord);
              if(0. != kernelValue)
              {
                double errorValue = errorSignal.get(errorCoord);
                backprop.add(i, errorValue*kernelValue);
              }
            });
          });
          if (isVerbose()) {
            log.debug(String.format("Feed back: %s * -1 %n\t=> %s", errorSignal, backprop));
          }
          inObj.feedback(backprop);
        }
      }
      
      @Override
      public boolean isAlive() {
        return !frozen || inObj.isAlive();
      }
    };
  }
  
  public ConvolutionSynapseLayer fillWeights(final DoubleSupplier f) {
    Arrays.parallelSetAll(this.kernel.data, i -> f.getAsDouble());
    return this;
  }
  
  public ConvolutionSynapseLayer addWeights(final DoubleSupplier f) {
    Util.add(f, kernel.data);
    return this;
  }
  

  @Override
  public double getMass() {
    return this.massMomentum.getMass();
  }
  
  @Override
  public double getMomentumDecay() {
    return this.massMomentum.getMomentumDecay();
  }
  
  @Override
  public ConvolutionSynapseLayer setMass(final double mass) {
    this.massMomentum.setMass(mass);
    return this;
  }

  @Override
  public ConvolutionSynapseLayer setMomentumDecay(final double momentumDecay) {
    this.massMomentum.setMomentumDecay(momentumDecay);
    return this;
  }

  public boolean isVerbose() {
    return verbose;
  }

  public ConvolutionSynapseLayer setVerbose(boolean verbose) {
    this.verbose = verbose;
    return this;
  }

  
  public ConvolutionSynapseLayer freeze() {
    return freeze(true);
  }
  
  public ConvolutionSynapseLayer freeze(final boolean b) {
    this.frozen = b;
    return this;
  }

  public boolean isFrozen() {
    return frozen;
  }

  public void setFrozen(boolean frozen) {
    this.frozen = frozen;
  }
  
  @Override
  public void write(double factor) {
    if(isFrozen()) return;
    flush.write(factor);
  }

  public ConvolutionSynapseLayer setHalflife(final double halflife) {
    return setMomentumDecay(Math.exp(2 * Math.log(0.5) / halflife));
  }

  public boolean isParalell() {
    return paralell;
  }

  public ConvolutionSynapseLayer setParalell(boolean parallel) {
    this.paralell = parallel;
    return this;
  }
}
