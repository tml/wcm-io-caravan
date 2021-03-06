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
import io.wcm.caravan.pipeline.JsonPipeline;
import io.wcm.caravan.pipeline.JsonPipelineAction;
import io.wcm.caravan.pipeline.JsonPipelineContext;
import io.wcm.caravan.pipeline.JsonPipelineOutput;
import io.wcm.caravan.pipeline.cache.CacheControlUtils;
import io.wcm.caravan.pipeline.cache.CacheStrategy;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.osgi.annotation.versioning.ProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

/**
 * Action to load a HAL link and insert the content as embedded resource.
 */
@ProviderType
public final class EmbedLinks implements JsonPipelineAction {

  private static final Logger log = LoggerFactory.getLogger(EmbedLinks.class);

  private final String serviceName;
  private final String relation;
  private final Map<String, Object> parameters;
  private final int index;

  private boolean includeLinksInEmbeddedResources;

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
   * @param serviceName
   * @param relation
   * @param parameters
   * @param includeLinksInEmbeddedResources whether links in embedded resources should also be resolved
   */
  public EmbedLinks(String serviceName, String relation, Map<String, Object> parameters, boolean includeLinksInEmbeddedResources) {
    this.serviceName = serviceName;
    this.relation = relation;
    this.parameters = parameters;
    this.index = Integer.MIN_VALUE;
    this.includeLinksInEmbeddedResources = includeLinksInEmbeddedResources;
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

    final List<Link> links = getLinks(halResource);

    Observable<JsonPipeline> pipelinesToEmbed = getPipelinesForEachLink(links, previousStepOutput, context, halResource);

    return CacheControlUtils.zipWithLowestMaxAge(pipelinesToEmbed, (outputsToEmbed) -> {

      if (includeLinksInEmbeddedResources) {

        Map<String, HalResource> urlResourceMap = new HashMap<>();
        for (int i=0; i<links.size(); i++) {
          urlResourceMap.put(links.get(i).getHref(), new HalResource((ObjectNode)outputsToEmbed.get(i).getPayload()));
        }

        recursiveLinkReplacement(halResource, urlResourceMap);

      }
      else {

        // if links should not be resolved for the embedded resources, keep the previous logic (which is much simpler)
        for (JsonPipelineOutput output : outputsToEmbed) {
          halResource.addEmbedded(relation, new HalResource((ObjectNode)output.getPayload()));
        }
        removeLinks(halResource);
      }

      return previousStepOutput.withPayload(halResource.getModel());
    });
  }

  private Observable<JsonPipeline> getPipelinesForEachLink(List<Link> links, JsonPipelineOutput previousStepOutput, JsonPipelineContext context, HalResource halResource) {

    Observable<CaravanHttpRequest> requests = getRequests(previousStepOutput, links);
    return requests
        // create pipeline
        .map(request -> context.getFactory().create(request, context.getProperties()))
        // add Caching
        .map(pipeline -> cacheStrategy == null ? pipeline : pipeline.addCachePoint(cacheStrategy));
  }

  private List<Link> getLinks(HalResource halResource) {
    List<Link> links = new LinkedList<>();
    links.addAll(halResource.getLinks(relation));

    ImmutableListMultimap<String, HalResource> embeddedResources = halResource.getEmbedded();

    if (includeLinksInEmbeddedResources && embeddedResources != null) {
      for (String embeddedRel : embeddedResources.keySet()) {
        for (HalResource embedded : embeddedResources.get(embeddedRel)) {
          links.addAll(getLinks(embedded));
        }
      }
    }

    return index == Integer.MIN_VALUE ? links : Lists.newArrayList(links.get(index));
  }


  private Observable<CaravanHttpRequest> getRequests(JsonPipelineOutput previousStepOutput, List<Link> links) {
    Multimap<String, String> previousHeaders = previousStepOutput.getRequests().get(0).getHeaders();
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


  private void recursiveLinkReplacement(HalResource output, Map<String, HalResource> urlResourceMap) {
    List<Link> links = output.getLinks(relation);

    for (Link link : links) {
      HalResource resource = urlResourceMap.get(link.getHref());
      if (resource != null) {
        output.addEmbedded(relation, resource);
      }
      else {
        log.error("Did not find resource for href " + link.getHref());
      }
    }

    removeLinks(output);

    ImmutableListMultimap<String, HalResource> embeddedResources = output.getEmbedded();
    for (String embeddedRel : embeddedResources.keySet()) {
      for (HalResource embedded : embeddedResources.get(embeddedRel)) {
        recursiveLinkReplacement(embedded, urlResourceMap);
      }
    }
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
