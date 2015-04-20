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

import io.wcm.caravan.pipeline.util.JsonPipelineOutputUtil;

import org.osgi.annotation.versioning.ProviderType;

import rx.Observable;
import rx.functions.Func1;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Default implementations of {@link JsonPipelineAction}
 */
@ProviderType
public final class JsonPipelineActions {

  private JsonPipelineActions() {
    // static methods only
  }

  /**
   * Applies a custom transformation on the {@link JsonNode} (e.g. a HAL representation) of actual pipeline, specifying
   * a function as transformation mechanism. Function receives {@link JsonNode} as parameter, which transformation
   * should be applied and returns a new {@link JsonNode} with the transformation result.
   * @param transformationId an unique id of the actual transformation
   * @param transformation a function that provides transformation algorithm
   * @return a new action that will emit the result of the transformation
   */
  public static JsonPipelineAction simpleTransformation(String transformationId, Func1<JsonNode, JsonNode> transformation) {
    return new JsonPipelineAction() {

      @Override
      public String getId() {
        return transformationId;
      }

      @Override
      public Observable<JsonPipelineOutput> execute(JsonPipelineOutput previousStepOutput, JsonPipelineContext context) {
        JsonNode transformedPayload = transformation.call(previousStepOutput.getPayload());
        JsonPipelineOutput transformedOutput = previousStepOutput.withPayload(transformedPayload);
        return Observable.just(transformedOutput);
      }

    };
  }

  /**
   * Applies a custom function on the {@link JsonPipelineOutput} of this pipeline. Function receives the
   * {@link JsonPipelineOutput} of actual JSON pipeline as parameter, which should be handled, and returns a new or the
   * same {@link JsonPipelineOutput} as the action result.
   * @param functionId an unique id of the actual action
   * @param function a function that provides action algorithm
   * @return a new action that will emit the result of the applied function
   */
  public static JsonPipelineAction applyFunction(String functionId, Func1<JsonPipelineOutput, JsonPipelineOutput> function) {
    return new JsonPipelineAction() {

      @Override
      public String getId() {
        return functionId;
      }

      @Override
      public Observable<JsonPipelineOutput> execute(JsonPipelineOutput previousStepOutput, JsonPipelineContext context) {
        JsonPipelineOutput nextStepOutput = function.call(previousStepOutput);
        return Observable.just(nextStepOutput);
      }

    };
  }

  /**
   * Provides an action which compares actual max age value of the {@link JsonPipelineOutput} with max age value of
   * {@link JsonPipelineOutput} specifying as parameter. Enriches the {@link JsonPipelineOutput} of actual pipeline with
   * the lowest found max age value. See {@link JsonPipelineOutput#getMaxAge()}.
   * Returns the actual {@link JsonPipelineOutput} without any modification, if it has the lowest max age value or equal
   * to the max age value of the compared parameter. Otherwise if max age value of the compared parameter is the lowest,
   * returns a new {@link JsonPipelineOutput} created from the actual output enriched with max age value from the
   * compared parameter.
   * @param ageToCompareOutput a JSON pipeline output which max age value should be compared
   * @return a new action that will emit the enriched result
   */
  public static JsonPipelineAction enrichWithLowestAge(JsonPipelineOutput ageToCompareOutput) {
    return JsonPipelineActions.applyFunction("enrichJsonPipelineOutputWithLowestAge", new LowestAgeEnricher(ageToCompareOutput));
  }

  /**
   * Enrichment function compares the max age value of the actual {@link JsonPipelineOutput} provided by the pipeline
   * and the compare {@link JsonPipelineOutput} provided as function constructor parameter.
   * Returns the actual {@link JsonPipelineOutput} without any modification, if it has the lowest or equal max age value
   * to the max age value of the compared parameter. if max age value of the compared parameter is the lowest, returns
   * a new {@link JsonPipelineOutput} created from the actual output enriched with max age value from the compared
   * parameter.
   */
  private static class LowestAgeEnricher implements Func1<JsonPipelineOutput, JsonPipelineOutput> {

    private JsonPipelineOutput ageToCompareOutput;

    public LowestAgeEnricher(JsonPipelineOutput ageToCompareOutput) {
      this.ageToCompareOutput = ageToCompareOutput;
    }

    @Override
    public JsonPipelineOutput call(JsonPipelineOutput actualPipelineOutput) {
      return JsonPipelineOutputUtil.enrichWithLowestAge(actualPipelineOutput, ageToCompareOutput);
    }

  }
}
