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

package com.simiacryptus.mindseye.opt.orient;

import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.eval.ArrayTrainable;
import com.simiacryptus.mindseye.eval.BasicTrainable;
import com.simiacryptus.mindseye.eval.Trainable;
import com.simiacryptus.mindseye.lang.*;
import com.simiacryptus.mindseye.layers.java.PlaceholderLayer;
import com.simiacryptus.mindseye.opt.IterativeTrainer;
import com.simiacryptus.mindseye.opt.TrainingMonitor;
import com.simiacryptus.mindseye.opt.line.ArmijoWolfeSearch;
import com.simiacryptus.mindseye.opt.line.SimpleLineSearchCursor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * An recursive optimization strategy which projects the current space into a reduced-dimensional subspace for a
 * sub-optimization batch run.
 */
public class RecursiveSubspace extends OrientationStrategyBase<SimpleLineSearchCursor> {
  
  /**
   * The constant CURSOR_LABEL.
   */
  public static final String CURSOR_LABEL = "RecursiveSubspace";
  private int iterations = 4;
  private @Nullable double[] weights = null;
  
  @Override
  public @NotNull SimpleLineSearchCursor orient(@NotNull Trainable subject, @NotNull PointSample measurement, @NotNull TrainingMonitor monitor) {
    PointSample origin = measurement.copyFull().backup();
    @Nullable NNLayer macroLayer = buildSubspace(subject, measurement, monitor);
    train(monitor, macroLayer);
    NNResult eval = macroLayer.eval((NNResult) null);
    macroLayer.freeRef();
    eval.getData().freeRef();
    eval.freeRef();
    StateSet<NNLayer> backupCopy = origin.weights.backupCopy();
    DeltaSet<NNLayer> delta = backupCopy.subtract(origin.weights);
    backupCopy.freeRef();
    origin.restore();
    @NotNull SimpleLineSearchCursor simpleLineSearchCursor = new SimpleLineSearchCursor(subject, origin, delta);
    delta.freeRef();
    origin.freeRef();
    return simpleLineSearchCursor.setDirectionType(CURSOR_LABEL);
  }
  
  /**
   * Build subspace nn layer.
   *
   * @param subject     the subject
   * @param measurement the measurement
   * @param monitor     the monitor
   * @return the nn layer
   */
  public @Nullable NNLayer buildSubspace(@NotNull Trainable subject, @NotNull PointSample measurement, @NotNull TrainingMonitor monitor) {
    PointSample origin = measurement.copyFull().backup();
    final DeltaSet<NNLayer> direction = measurement.delta.scale(-1);
    final double magnitude = direction.getMagnitude();
    if (Math.abs(magnitude) < 1e-10) {
      monitor.log(String.format("Zero gradient: %s", magnitude));
    }
    else if (Math.abs(magnitude) < 1e-5) {
      monitor.log(String.format("Low gradient: %s", magnitude));
    }
    boolean hasPlaceholders = direction.getMap().entrySet().stream().filter(x -> x.getKey() instanceof PlaceholderLayer).findAny().isPresent();
  
    List<NNLayer> deltaLayers = direction.getMap().entrySet().stream().map(x -> x.getKey())
                                         .filter(x -> !(x instanceof PlaceholderLayer))
                                         .collect(Collectors.toList());
    int size = deltaLayers.size() + (hasPlaceholders ? 1 : 0);
    if (null == weights || weights.length != size) weights = new double[size];
    return new NNLayer() {
      @NotNull NNLayer self = this;
      
      @Override
      public @NotNull NNResult eval(NNResult... array) {
        assertAlive();
        origin.restore();
        IntStream.range(0, deltaLayers.size()).forEach(i -> {
          direction.getMap().get(deltaLayers.get(i)).accumulate(weights[hasPlaceholders ? (i + 1) : i]);
        });
        if (hasPlaceholders) {
          direction.getMap().entrySet().stream()
                   .filter(x -> x.getKey() instanceof PlaceholderLayer).distinct()
                   .forEach(entry -> entry.getValue().accumulate(weights[0]));
        }
        PointSample measure = subject.measure(monitor);
        double mean = measure.getMean();
        monitor.log(String.format("RecursiveSubspace: %s <- %s", mean, Arrays.toString(weights)));
        direction.addRef();
        return new NNResult(TensorArray.wrap(new Tensor(mean)), (DeltaSet<NNLayer> buffer, TensorList data) -> {
          DoubleStream deltaStream = deltaLayers.stream().mapToDouble(layer -> {
            Delta<NNLayer> a = direction.getMap().get(layer);
            Delta<NNLayer> b = measure.delta.getMap().get(layer);
            return b.dot(a) / Math.max(Math.sqrt(a.dot(a)), 1e-8);
          });
          if (hasPlaceholders) {
            deltaStream = DoubleStream.concat(DoubleStream.of(
              direction.getMap().keySet().stream().filter(x -> x instanceof PlaceholderLayer).distinct().mapToDouble(layer -> {
                Delta<NNLayer> a = direction.getMap().get(layer);
                Delta<NNLayer> b = measure.delta.getMap().get(layer);
                return b.dot(a) / Math.max(Math.sqrt(a.dot(a)), 1e-8);
              }).sum()), deltaStream);
          }
          buffer.get(self, weights).addInPlace(deltaStream.toArray()).freeRef();
        }) {
          @Override
          protected void _free() {
            measure.freeRef();
            direction.freeRef();
          }
  
          @Override
          public boolean isAlive() {
            return true;
          }
        };
      }
  
      @Override
      protected void _free() {
        direction.freeRef();
        origin.freeRef();
        super._free();
      }
  
      @Override
      public @NotNull JsonObject getJson(Map<String, byte[]> resources, DataSerializer dataSerializer) {
        throw new IllegalStateException();
      }
      
      @Override
      public @Nullable List<double[]> state() {
        return null;
      }
    };
  }
  
  /**
   * Train.
   *
   * @param monitor    the monitor
   * @param macroLayer the macro layer
   */
  public void train(@NotNull TrainingMonitor monitor, NNLayer macroLayer) {
    @NotNull BasicTrainable inner = new BasicTrainable(macroLayer);
    @NotNull Tensor tensor = new Tensor();
    @NotNull ArrayTrainable trainable = new ArrayTrainable(inner, new Tensor[][]{{tensor}});
    inner.freeRef();
    tensor.freeRef();
    new IterativeTrainer(trainable)
      .setOrientation(new LBFGS())
      .setLineSearchFactory(n -> new ArmijoWolfeSearch())
      .setMonitor(new TrainingMonitor() {
        @Override
        public void log(String msg) {
          monitor.log("\t" + msg);
        }
      })
      .setMaxIterations(getIterations())
      .setIterationsPerSample(getIterations())
      .runAndFree();
    trainable.freeRef();
  }
  
  @Override
  public void reset() {
    weights = null;
  }
  
  /**
   * Gets iterations.
   *
   * @return the iterations
   */
  public int getIterations() {
    return iterations;
  }
  
  /**
   * Sets iterations.
   *
   * @param iterations the iterations
   * @return the iterations
   */
  public @NotNull RecursiveSubspace setIterations(int iterations) {
    this.iterations = iterations;
    return this;
  }
  
  @Override
  protected void _free() {
  }
}
