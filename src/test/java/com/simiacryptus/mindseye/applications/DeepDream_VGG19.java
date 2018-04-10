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
import com.simiacryptus.mindseye.test.TestUtil;
import com.simiacryptus.util.io.NotebookOutput;

import javax.annotation.Nonnull;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The type Deep dream vgg 19.
 */
public class DeepDream_VGG19 extends ArtistryAppBase_VGG19 {
  
  /**
   * Test.
   *
   * @param log the log
   */
  public void run(@Nonnull NotebookOutput log) {
    DeepDream<CVPipe_VGG19.Layer, CVPipe_VGG19> dreamBase = new DeepDream.VGG19();
    init(log);
    Precision precision = Precision.Float;
    final AtomicInteger imageSize = new AtomicInteger(800);
  
    Map<CVPipe_VGG19.Layer, DeepDream.ContentCoefficients> dreamCoeff = new HashMap<>();
    dreamCoeff.put(CVPipe_VGG19.Layer.Layer_1d, new DeepDream.ContentCoefficients(0, 1e-1));
//    dreamCoeff.put(CVPipe_VGG19.Layer.Layer_1e, new ContentCoefficients(0, 1e0));
    dreamCoeff.put(CVPipe_VGG19.Layer.Layer_2b, new DeepDream.ContentCoefficients(0, 1e0));
    dreamCoeff.put(CVPipe_VGG19.Layer.Layer_3a, new DeepDream.ContentCoefficients(0, 1e1));
    int trainingMinutes = 180;
    
    log.h1("Phase 0");
    BufferedImage canvasImage = ArtistryUtil.load(lakeAndForest, imageSize.get());
    //canvasImage = randomize(canvasImage);
    canvasImage = TestUtil.resize(canvasImage, imageSize.get(), true);
    BufferedImage contentImage = ArtistryUtil.load(lakeAndForest, canvasImage.getWidth(), canvasImage.getHeight());
    dreamBase.deepDream(server, log, canvasImage, new DeepDream.StyleSetup(precision, contentImage, dreamCoeff), trainingMinutes, 50);
    
    log.setFrontMatterProperty("status", "OK");
  }
  
}