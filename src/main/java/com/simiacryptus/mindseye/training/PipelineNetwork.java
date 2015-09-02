package com.simiacryptus.mindseye.training;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simiacryptus.mindseye.deltas.NNResult;
import com.simiacryptus.mindseye.layers.NNLayer;
import com.simiacryptus.mindseye.math.NDArray;
import com.simiacryptus.mindseye.training.EvaluationContext.LazyResult;

/***
 * Builds a linear pipeline of NNLayer components, applied in sequence
 * 
 * @author Andrew Charneski
 */
public class PipelineNetwork extends NNLayer {
  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(PipelineNetwork.class);
  
  protected List<NNLayer> insertOrder = new ArrayList<NNLayer>();
  public final UUID inputHandle = UUID.randomUUID();
  public LazyResult<NNResult[]> head = new LazyResult<NNResult[]>() {
    @Override
    protected NNResult[] initialValue(EvaluationContext t) {
      return (NNResult[]) t.cache.get(inputHandle);
    }
  };
  
  public synchronized PipelineNetwork add(final NNLayer layer) {
    this.insertOrder.add(layer);
    LazyResult<NNResult[]> prevHead = head;
    head = new LazyResult<NNResult[]>() {
      @Override
      protected NNResult[] initialValue(EvaluationContext ctx) {
        NNResult[] input = prevHead.get(ctx);
        NNResult output = layer.eval(ctx, input);
        return new NNResult[] { output };
      }
    };
    return this;
  }
  
  public NNResult eval(EvaluationContext evaluationContext, NNResult... array) {
    evaluationContext.cache.put(inputHandle, array);
    return head.get(evaluationContext)[0];
  }
  
  public NNLayer get(final int i) {
    return this.insertOrder.get(i);
  }
  
  @Override
  public String toString() {
    return "PipelineNetwork [" + this.insertOrder + "]";
  }
  
  public Tester trainer(final NDArray[][] samples) {
    return new Tester().set(this, samples);
  }
  
  public NNResult eval(NDArray... array) {
    return eval(new EvaluationContext(), array);
  }
  
}