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

package com.simiacryptus.mindseye.app;

import com.simiacryptus.mindseye.applications.ArtistryUtil;
import com.simiacryptus.mindseye.applications.StyleTransfer;
import com.simiacryptus.mindseye.lang.cudnn.Precision;
import com.simiacryptus.mindseye.models.MultiLayerVGG19;
import com.simiacryptus.mindseye.models.VGG19;
import com.simiacryptus.mindseye.test.TestUtil;
import com.simiacryptus.util.FastRandom;
import com.simiacryptus.util.io.NotebookOutput;

import javax.annotation.Nonnull;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The type Style transfer vgg 19.
 */
public class StyleTransfer_VGG19 extends ArtistryAppBase {
  
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
    StyleTransfer.VGG19 styleTransfer = new StyleTransfer.VGG19();
    init(log);
    Precision precision = Precision.Float;
    int imageSize = 400;
    styleTransfer.parallelLossFunctions = true;
    double growthFactor = Math.sqrt(1.5);
    CharSequence lakeAndForest = "H:\\SimiaCryptus\\Artistry\\Owned\\IMG_20170624_153541213-EFFECTS.jpg";
    String monkey = "H:\\SimiaCryptus\\Artistry\\capuchin-monkey-2759768_960_720.jpg";
    CharSequence vanGogh1 = "H:\\SimiaCryptus\\Artistry\\portraits\\vangogh\\Van_Gogh_-_Portrait_of_Pere_Tanguy_1887-8.jpg";
    CharSequence vanGogh2 = "H:\\SimiaCryptus\\Artistry\\portraits\\vangogh\\800px-Vincent_van_Gogh_-_Dr_Paul_Gachet_-_Google_Art_Project.jpg";
    CharSequence threeMusicians = "H:\\SimiaCryptus\\Artistry\\portraits\\picasso\\800px-Pablo_Picasso,_1921,_Nous_autres_musiciens_(Three_Musicians),_oil_on_canvas,_204.5_x_188.3_cm,_Philadelphia_Museum_of_Art.jpg";
    CharSequence maJolie = "H:\\SimiaCryptus\\Artistry\\portraits\\picasso\\Ma_Jolie_Pablo_Picasso.jpg";
    
    Map<List<CharSequence>, StyleTransfer.StyleCoefficients> styles = new HashMap<>();
    double coeff_mean = 1e1;
    double coeff_cov = 1e0;
    styles.put(Arrays.asList(
      //threeMusicians, maJolie
      vanGogh1, vanGogh2
      ), new StyleTransfer.StyleCoefficients(StyleTransfer.CenteringMode.Origin)
//      .set(MultiLayerVGG19.LayerType.Layer_0, 1e0, 1e0)
//        .set(MultiLayerVGG19.LayerType.Layer_1a, coeff_mean, coeff_cov)
        .set(MultiLayerVGG19.LayerType.Layer_1b, coeff_mean, coeff_cov)
//        .set(MultiLayerVGG19.LayerType.Layer_1c, coeff_mean, coeff_cov)
        .set(MultiLayerVGG19.LayerType.Layer_1d, coeff_mean, coeff_cov)
    );
    StyleTransfer.ContentCoefficients contentCoefficients = new StyleTransfer.ContentCoefficients()
      .set(MultiLayerVGG19.LayerType.Layer_1c, 1e0);
    int trainingMinutes = 90;
    
    log.h1("Phase 0");
    BufferedImage canvasImage = ArtistryUtil.load(monkey, imageSize);
    canvasImage = TestUtil.resize(canvasImage, imageSize, true);
    canvasImage = TestUtil.resize(TestUtil.resize(canvasImage, 25, true), imageSize, true);
//    canvasImage = randomize(canvasImage, x -> 10 * (FastRandom.INSTANCE.random()) * (FastRandom.INSTANCE.random() < 0.9 ? 1 : 0));
    canvasImage = ArtistryUtil.randomize(canvasImage, x -> x + 2 * 1 * (FastRandom.INSTANCE.random() - 0.5));
//    canvasImage = randomize(canvasImage, x -> 10*(FastRandom.INSTANCE.random()-0.5));
//    canvasImage = randomize(canvasImage, x -> x*(FastRandom.INSTANCE.random()));
    Map<CharSequence, BufferedImage> styleImages = new HashMap<>();
    styleImages.clear();
    styleImages.putAll(styles.keySet().stream().flatMap(x -> x.stream()).collect(Collectors.toMap(x -> x, file -> ArtistryUtil.load(file))));
    StyleTransfer.StyleSetup styleSetup = new StyleTransfer.StyleSetup(precision,
      ArtistryUtil.load(monkey, canvasImage.getWidth(), canvasImage.getHeight()),
      contentCoefficients, styleImages, styles);
    StyleTransfer.NeuralSetup measureStyle = styleTransfer.measureStyle(styleSetup);
    canvasImage = styleTransfer.styleTransfer(server, log, canvasImage, styleSetup, trainingMinutes, measureStyle);
    for (int i = 1; i < 10; i++) {
      log.h1("Phase " + i);
      imageSize = (int) (imageSize * growthFactor);
      canvasImage = TestUtil.resize(canvasImage, imageSize, true);
      canvasImage = styleTransfer.styleTransfer(server, log, canvasImage, styleSetup, trainingMinutes, measureStyle);
    }
    log.setFrontMatterProperty("status", "OK");
  }
  
}
