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
package io.wcm.caravan.pipeline.extensions.halclient.action;

import static io.wcm.caravan.io.http.request.CaravanHttpRequest.CORRELATION_ID_HEADER_NAME;
import io.wcm.caravan.commons.hal.resource.HalResource;
import io.wcm.caravan.commons.hal.resource.Link;
import io.wcm.caravan.io.http.request.CaravanHttpRequest;
import io.wcm.caravan.io.http.request.CaravanHttpRequestBuilder;
import io.wcm.caravan.pipeline.JsonPipelineAction;
import io.wcm.caravan.pipeline.JsonPipelineContext;
import io.wcm.caravan.pipeline.JsonPipelineOutput;
import io.wcm.caravan.pipeline.cache.CacheStrategy;
import io.wcm.caravan.pipeline.util.JsonPipelineOutputUtil;

import java.util.List;
import java.util.Map;

import org.osgi.annotation.versioning.ProviderType;

import rx.Observable;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

/**
 * Action to load a HAL link and insert the content as embedded resource.
 */
@ProviderType
public final class EmbedLinks implements JsonPipelineAction {

  private final String serviceName;
  private final String relation;
  private final Map<String, Object> parameters;
  private final int index;

  private CacheStrategy cacheStrategy;

  /**
   * @param serviceName
   * @param relation
   * @param parameters
   * @param index
   */
  public EmbedLinks(String serviceName, String relation, Map<String, Object> parameters, int index) {
    this.serviceName = serviceName;
    this.relation = relation;
    this.parameters = parameters;
    this.index = index;
  }

  /**
   * Sets the cache strategy for this action.
   * @param newCacheStrategy Caching strategy
   * @return Embed links action
   */
  public EmbedLinks setCacheStrategy(CacheStrategy newCacheStrategy) {
    this.cacheStrategy = newCacheStrategy;
    return this;
  }

  @Override
  public String getId() {
    return "EMBED-LINKS(" + relation + '-' + parameters.hashCode() + (index == Integer.MIN_VALUE ? "" : ('-' + index)) + ")";
  }

  @Override
  public Observable<JsonPipelineOutput> execute(JsonPipelineOutput previousStepOutput, JsonPipelineContext context) {
    HalResource halResource = new HalResource((ObjectNode)previousStepOutput.getPayload());

    Observable<JsonPipelineOutput> rxPipelineOutputsToEmbed = getPipelineOutputsForLinks(previousStepOutput, context, halResource).cache();
    Observable<List<HalResource>> rxResourcesToEmbed = getResourcesToEmbed(rxPipelineOutputsToEmbed);
    Observable<JsonPipelineOutput> rxReducedJsonPipelineOutput = getPipelineOutput(previousStepOutput, rxPipelineOutputsToEmbed);

    return createOutput(halResource, rxResourcesToEmbed, rxReducedJsonPipelineOutput);
  }

  private Observable<JsonPipelineOutput> getPipelineOutputsForLinks(JsonPipelineOutput previousStepOutput, JsonPipelineContext context, HalResource halResource) {
    List<Link> links = getLinks(halResource);
    Observable<CaravanHttpRequest> requests = getRequests(previousStepOutput, links);
    return requests
        // create pipeline
        .map(request -> context.getFactory().create(request, context.getProperties()))
        // add Caching
        .map(pipeline -> cacheStrategy == null ? pipeline : pipeline.addCachePoint(cacheStrategy))
        // create asynchron output
        .flatMap(pipeline -> pipeline.getOutput());
  }

  private List<Link> getLinks(HalResource halResource) {
    List<Link> links = halResource.getLinks(relation);
    return index == Integer.MIN_VALUE ? links : Lists.newArrayList(links.get(index));
  }

  private Observable<CaravanHttpRequest> getRequests(JsonPipelineOutput previousStepOutput, List<Link> links) {
    Multimap<String, String> previousHeaders = previousStepOutput.getRequests().get(0).headers();
    return Observable.from(links)
        // create request, and main cache-control headers from previous request
        .map(link -> {
          CaravanHttpRequestBuilder builder = new CaravanHttpRequestBuilder(serviceName)
          .append(link.getHref())
          .header("Cache-Control", previousHeaders.get("Cache-Control"));

          // also make sure that the correlation-id is passed on to the follow-up requests
          if (previousStepOutput.getCorrelationId() != null) {
            builder.header(CORRELATION_ID_HEADER_NAME, previousStepOutput.getCorrelationId());
          }

          return builder.build(parameters);
        });
  }

  private Observable<List<HalResource>> getResourcesToEmbed(Observable<JsonPipelineOutput> rxPipelineOutputsToEmbed) {
    return rxPipelineOutputsToEmbed
        .map(output -> (ObjectNode)output.getPayload())
        .map(json -> new HalResource(json)).toList();
  }

  private Observable<JsonPipelineOutput> getPipelineOutput(JsonPipelineOutput previousStepOutput, Observable<JsonPipelineOutput> rxPipelineOutputsToEmbed) {
    return rxPipelineOutputsToEmbed
        .reduce(previousStepOutput, (recent, next) -> JsonPipelineOutputUtil.enrichWithLowestAge(recent, next));
  }

  private Observable<JsonPipelineOutput> createOutput(HalResource halResource, Observable<List<HalResource>> rxResourcesToEmbed,
      Observable<JsonPipelineOutput> rxReducedJsonPipelineOutput) {
    return Observable.zip(rxResourcesToEmbed, rxReducedJsonPipelineOutput, (resourcesToEmbed, pipelineOutput) -> {
      halResource.addEmbedded(relation, resourcesToEmbed);
      removeLinks(halResource);
      return pipelineOutput.withPayload(halResource.getModel());
    });
  }

  private void removeLinks(HalResource halResource) {
    if (index == Integer.MIN_VALUE) {
      halResource.removeLinks(relation);
    }
    else {
      halResource.removeLink(relation, index);
    }
  }

}