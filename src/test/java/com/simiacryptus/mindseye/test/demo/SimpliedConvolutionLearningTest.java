package com.simiacryptus.mindseye.test.demo;

import java.awt.Graphics2D;
import java.io.IOException;
import java.util.Iterator;
import java.util.stream.Stream;

import com.simiacryptus.mindseye.LabeledObject;
import com.simiacryptus.mindseye.NDArray;
import com.simiacryptus.mindseye.Util;
import com.simiacryptus.mindseye.net.DAGNetwork;
import com.simiacryptus.mindseye.net.NNLayer;
import com.simiacryptus.mindseye.net.basic.EntropyLossLayer;
import com.simiacryptus.mindseye.net.basic.SoftmaxActivationLayer;
import com.simiacryptus.mindseye.net.basic.SqActivationLayer;
import com.simiacryptus.mindseye.net.media.ConvolutionSynapseLayer;
import com.simiacryptus.mindseye.net.media.SumSubsampleLayer;
import com.simiacryptus.mindseye.test.Tester;
import com.simiacryptus.mindseye.training.NetInitializer;
import com.simiacryptus.mindseye.training.TrainingContext;

public class SimpliedConvolutionLearningTest extends MNISTClassificationTests {

  @Override
  public NNLayer<DAGNetwork> buildNetwork() {
    DAGNetwork net = new DAGNetwork();
    int n = 2;
    int m = 28-n+1;
    net = net.add(new ConvolutionSynapseLayer(new int[] { n, n }, 10).addWeights(() -> Util.R.get().nextGaussian() * .001));
    net = net.add(new SqActivationLayer());
    net = net.add(new SumSubsampleLayer(new int[] { m, m, 1 }));
    net = net.add(new SoftmaxActivationLayer());
    return net;
  }

  public Stream<LabeledObject<NDArray>> getTrainingData() throws IOException {
    
    final Stream<LabeledObject<NDArray>> merged = Util.toStream(new Iterator<LabeledObject<NDArray>>() {
      @Override
      public boolean hasNext() {
        return true;
      }

      int cnt=0;
      @Override
      public LabeledObject<NDArray> next() {
        int index = cnt++;
        String id = "";
        NDArray imgData;
        while(true){
          java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(28,28,java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
          Graphics2D g = img.createGraphics();
          int cardinality = index%2;
          if(0==cardinality)
          {
            int x1 = Util.R.get().nextInt(28);
            int x2 = Util.R.get().nextInt(28);
            int y = Util.R.get().nextInt(28);
            if(Math.abs(x1-x2)<1) continue;
            g.drawLine(x1, y, x2, y);
            id = "[0]";
          } else if(1==cardinality) {
            int x = Util.R.get().nextInt(28);
            int y1 = Util.R.get().nextInt(28);
            int y2 = Util.R.get().nextInt(28);
            if(Math.abs(y1-y2)<1) continue;
            g.drawLine(x, y1, x, y2);
            id = "[1]";
          }
          imgData = new NDArray(new int[]{28,28,1}, img.getData().getSamples(0, 0, 28, 28, 0, (double[])null));
          break;
        }
        return new LabeledObject<NDArray>(imgData, id);
      }
    }, 1000).limit(1000);
    return merged;
  }

  @Override
  public Tester buildTrainer(final NDArray[][] samples, final NNLayer<DAGNetwork> net) {
    EntropyLossLayer lossLayer = new EntropyLossLayer();
    Tester trainer = new Tester(){
      @Override
      public NetInitializer getInitializer() {
        NetInitializer netInitializer = new NetInitializer();
        netInitializer.setAmplitude(0.);
        return netInitializer;
      }
    }.setMaxDynamicRate(1000).init(samples, net, lossLayer);
    trainer.setVerbose(true);
    TrainingContext trainingContext = trainer.trainingContext();
    trainingContext.setTimeout(5, java.util.concurrent.TimeUnit.MINUTES);
    return trainer;
  }

  @Override
  public void verify(final Tester trainer) {
    trainer.verifyConvergence(0.05, 1);
  }
  public int height() {
    return 300;
  }

  public int width() {
    return 300;
  }

  public double numberOfSymbols() {
    return 2.;
  }


}
