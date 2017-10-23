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

package com.simiacryptus.mindseye.lang;

/**
 * The type Component exception.
 */
public class ComponentException extends RuntimeException {
  
  /**
   * Instantiates a new Component exception.
   *
   * @param message the message
   * @param cause   the cause
   */
  public ComponentException(String message, Throwable cause) {
    super(message, cause);
  }
  
  /**
   * Instantiates a new Component exception.
   *
   * @param cause the cause
   */
  public ComponentException(Throwable cause) {
    super(cause);
  }
  
  /**
   * Instantiates a new Component exception.
   *
   * @param message            the message
   * @param cause              the cause
   * @param enableSuppression  the enable suppression
   * @param writableStackTrace the writable stack trace
   */
  public ComponentException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
  
  /**
   * Instantiates a new Component exception.
   */
  public ComponentException() {
  }
  
  /**
   * Instantiates a new Component exception.
   *
   * @param message the message
   */
  public ComponentException(String message) {
    super(message);
  }
}
