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
package io.wcm.caravan.pipeline;

import org.osgi.annotation.versioning.ConsumerType;

import rx.Observable;
import rx.functions.Func2;

/**
 * A functional interface that allow users of the JsonPipeline to specify custom exception handling for 404 responses
 * and other server-side or network exceptions via {@link JsonPipeline#handleException(JsonPipelineExceptionHandler)}.
 * You can get common implementations in the {@link JsonPipelineExceptionHandlers} class.
 */
@FunctionalInterface
@ConsumerType
public interface JsonPipelineExceptionHandler extends Func2<JsonPipelineOutput, RuntimeException, Observable<JsonPipelineOutput>> {

  /**
   * The method that defines the exception handling behavior. You can either
   * <ul>
   * <li>return static fallback content (based on the given default fallback content) )</li>
   * <li>Setup another {@link JsonPipeline} to fetch fallback content, and return its output observable as obtained by
   * {@link JsonPipeline#getOutput()}</li>
   * <li>Rethrow the exception</li>
   * </ul>
   * @param defaultFallbackContent a default pipeline output object (which already has the appropriate status code set)
   * @param caughtException the exception being handled
   */
  @Override
  Observable<JsonPipelineOutput> call(JsonPipelineOutput defaultFallbackContent, RuntimeException caughtException);

}
