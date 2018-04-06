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

package com.simiacryptus.mindseye.applications;

import com.simiacryptus.mindseye.lang.cudnn.Precision;
import com.simiacryptus.mindseye.models.CVPipe_VGG19;
import com.simiacryptus.mindseye.models.VGG19;
import com.simiacryptus.util.io.NotebookOutput;

import javax.annotation.Nonnull;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The type Style transfer vgg 19.
 */
public class TextureDream_VGG19 extends ArtistryAppBase {
  
  /**
   * Gets target class.
   *
   * @return the target class
   */
  @Nonnull
  protected Class<?> getTargetClass() {
    return VGG19.class;
  }
  
  /**
   * Test.
   *
   * @param log the log
   */
  public void run(@Nonnull NotebookOutput log) {
    TextureGeneration.VGG19 styleTransfer = new TextureGeneration.VGG19();
    DeepDream.VGG19 deepDream = new DeepDream.VGG19();
    deepDream.setTiled(true);
    init(log);
    Precision precision = Precision.Float;
    styleTransfer.parallelLossFunctions = true;
    double growthFactor = Math.sqrt(2);
    BufferedImage canvas = TextureGeneration.initCanvas(new AtomicInteger(256));
    
    int iterations = 10;
    {
      Map<List<CharSequence>, TextureGeneration.StyleCoefficients> textureStyle = new HashMap<>();
      textureStyle.put(picasso, new TextureGeneration.StyleCoefficients(TextureGeneration.CenteringMode.Origin)
        .set(CVPipe_VGG19.Layer.Layer_1a, 1e0, 1e0)
      );
      textureStyle.put(this.michelangelo, new TextureGeneration.StyleCoefficients(TextureGeneration.CenteringMode.Origin)
        .set(CVPipe_VGG19.Layer.Layer_1c, 1e0, 1e0)
        .set(CVPipe_VGG19.Layer.Layer_1d, 1e0, 1e0)
      );
      canvas = TextureGeneration.generate(log, styleTransfer, precision, new AtomicInteger(256), growthFactor, textureStyle, 90, canvas, 1, iterations, server);
    }
    
    {
      Map<CVPipe_VGG19.Layer, DeepDream.ContentCoefficients> dreamCoeff = new HashMap<>();
      dreamCoeff.put(CVPipe_VGG19.Layer.Layer_1d, new DeepDream.ContentCoefficients(0, 1e-2));
      dreamCoeff.put(CVPipe_VGG19.Layer.Layer_1e, new DeepDream.ContentCoefficients(0, 1e-1));
      dreamCoeff.put(CVPipe_VGG19.Layer.Layer_2b, new DeepDream.ContentCoefficients(0, 1e0));
      dreamCoeff.put(CVPipe_VGG19.Layer.Layer_3a, new DeepDream.ContentCoefficients(0, 1e1));
      canvas = deepDream.deepDream(server, log, canvas, new DeepDream.StyleSetup(precision, canvas, dreamCoeff), 90, iterations);
    }
    
    {
      Map<List<CharSequence>, TextureGeneration.StyleCoefficients> textureStyle = new HashMap<>();
      textureStyle.put(this.michelangelo, new TextureGeneration.StyleCoefficients(TextureGeneration.CenteringMode.Origin)
        .set(CVPipe_VGG19.Layer.Layer_0, 1e0, 1e0)
        .set(CVPipe_VGG19.Layer.Layer_1b, 1e0, 1e0)
        .set(CVPipe_VGG19.Layer.Layer_1d, 1e0, 1e0)
      );
      canvas = TextureGeneration.generate(log, styleTransfer, precision, new AtomicInteger(256), growthFactor, textureStyle, 90, canvas, 2, iterations, server);
    }
    
    Map<CVPipe_VGG19.Layer, DeepDream.ContentCoefficients> dreamCoeff = new HashMap<>();
    dreamCoeff.put(CVPipe_VGG19.Layer.Layer_1d, new DeepDream.ContentCoefficients(0, 1e-1));
    dreamCoeff.put(CVPipe_VGG19.Layer.Layer_2b, new DeepDream.ContentCoefficients(0, 1e0));
    dreamCoeff.put(CVPipe_VGG19.Layer.Layer_3a, new DeepDream.ContentCoefficients(0, 1e1));
    canvas = deepDream.deepDream(server, log, canvas, new DeepDream.StyleSetup(precision, canvas, dreamCoeff), 90, iterations);
    
    Map<List<CharSequence>, TextureGeneration.StyleCoefficients> textureStyle = new HashMap<>();
    textureStyle.put(this.michelangelo, new TextureGeneration.StyleCoefficients(TextureGeneration.CenteringMode.Origin)
      .set(CVPipe_VGG19.Layer.Layer_0, 1e0, 1e0)
      .set(CVPipe_VGG19.Layer.Layer_1b, 1e0, 1e0)
      .set(CVPipe_VGG19.Layer.Layer_1d, 1e0, 1e0)
    );
    canvas = TextureGeneration.generate(log, styleTransfer, precision, new AtomicInteger(256), growthFactor, textureStyle, 90, canvas, 2, iterations, server);
    
    log.setFrontMatterProperty("status", "OK");
  }
  
}
