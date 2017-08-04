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

package com.simiacryptus.mindseye.layers.cudnn;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * The type Cu dnn resource.
 *
 * @param <T> the type parameter
 */
public class CudaResource<T> {

    private final T ptr;
    private final ToIntFunction<T> destructor;
    private boolean finalized = false;

    /**
     * Instantiates a new Cu dnn resource.
     *
     * @param obj        the obj
     * @param destructor the destructor
     */
    protected CudaResource(T obj, ToIntFunction<T> destructor) {
        this.ptr = obj;
        this.destructor = destructor;
    }

    /**
     * Is finalized boolean.
     *
     * @return the boolean
     */
    public boolean isFinalized() {
        return finalized;
    }

    @Override
    public synchronized void finalize() {
        if(!this.finalized) {
            if(null != this.destructor) free();
            this.finalized = true;
        }
        try {
            super.finalize();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    
    protected void free() {
        CuDNN.handle(this.destructor.applyAsInt(ptr));
    }
    
    /**
     * Gets ptr.
     *
     * @return the ptr
     */
    public T getPtr() {
        if(isFinalized()) return null;
        return ptr;
    }
}