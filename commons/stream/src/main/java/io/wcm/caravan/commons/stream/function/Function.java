/*
 * #%L
 * wcm.io
 * %%
 * Copyright (C) 2014 wcm.io
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package io.wcm.caravan.commons.stream.function;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * Represents a function that accepts one argument and produces a result.
 * @param <T> the type of the input to the function
 * @param <R> the type of the result of the function
 */
@ConsumerType
public interface Function<T, R> {

  /**
   * Applies this function to the given argument.
   * @param t the function argument
   * @return the function result
   */
  R apply(T t);

}
