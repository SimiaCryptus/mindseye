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

package com.simiacryptus.mindseye.test.unit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.simiacryptus.mindseye.lang.NNLayer;
import com.simiacryptus.mindseye.lang.SerialPrecision;
import com.simiacryptus.mindseye.lang.Tensor;
import com.simiacryptus.mindseye.test.ToleranceStatistics;
import com.simiacryptus.util.Util;
import com.simiacryptus.util.io.NotebookOutput;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipFile;

/**
 * The type Json test.
 */
public class JsonTest implements ComponentTest<ToleranceStatistics> {
  /**
   * Compress gz byte [ ].
   *
   * @param prettyPrint the pretty print
   * @return the byte [ ]
   */
  public static byte[] compressGZ(String prettyPrint) {
    return compressGZ(prettyPrint.getBytes(Charset.forName("UTF-8")));
  }
  
  /**
   * Compress gz byte [ ].
   *
   * @param bytes the bytes
   * @return the byte [ ]
   */
  public static byte[] compressGZ(byte[] bytes) {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try {
      try (GZIPOutputStream out = new GZIPOutputStream(byteArrayOutputStream)) {
        IOUtils.write(bytes, out);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return byteArrayOutputStream.toByteArray();
  }
  
  @Override
  public ToleranceStatistics test(final NotebookOutput log, final NNLayer layer, final Tensor... inputPrototype) {
    log.h1("Json Serialization");
    log.p("This test will demonstrate the layer's JSON serialization, and verify deserialization integrity.");
  
    log.h2("Raw Json");
    try {
      String prettyPrint = log.code(() -> {
        final JsonObject json = layer.getJson();
        final NNLayer echo = NNLayer.fromJson(json);
        if (echo == null) throw new AssertionError("Failed to deserialize");
        if (layer == echo) throw new AssertionError("Serialization did not copy");
        if (!layer.equals(echo)) throw new AssertionError("Serialization not equal");
        return new GsonBuilder().setPrettyPrinting().create().toJson(json);
      });
      String filename = layer.getClass().getSimpleName() + "_" + log.getName() + ".json";
      log.p(log.file(prettyPrint, filename, String.format("Wrote Model to %s; %s characters", filename, prettyPrint.length())));
    } catch (RuntimeException e) {
      e.printStackTrace();
      Util.sleep(1000);
    } catch (OutOfMemoryError e) {
      e.printStackTrace();
      Util.sleep(1000);
    }
  
    log.h2("Json Zipfile");
    try {
      File file = log.code(() -> {
        File zip = new File(log.getResourceDir(), layer.getClass().getSimpleName() + "_" + log.getName() + ".zip");
        layer.writeZip(zip, SerialPrecision.Float);
        final NNLayer echo = NNLayer.fromZip(new ZipFile(zip));
        if (echo == null) throw new AssertionError("Failed to deserialize");
        if (layer == echo) throw new AssertionError("Serialization did not copy");
        if (!layer.equals(echo)) throw new AssertionError("Serialization not equal");
        return zip;
      });
      log.p(log.link(file, String.format("Wrote Model to %s; %.3fMiB bytes", file.getName(), file.length() * 1.0 / (0x100000))));
    } catch (RuntimeException e) {
      e.printStackTrace();
    } catch (OutOfMemoryError e) {
      e.printStackTrace();
    }
    
    return null;
  }
}
