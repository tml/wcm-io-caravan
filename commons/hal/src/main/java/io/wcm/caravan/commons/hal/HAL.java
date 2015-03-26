/* Copyright (c) pro!vision GmbH. All rights reserved. */
package io.wcm.caravan.commons.hal;

import io.wcm.caravan.commons.hal.domain.HalResource;
import io.wcm.caravan.commons.hal.domain.Link;
import io.wcm.caravan.commons.hal.mapper.ResourceMapper;

import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Iterables;

/**
 * Short named helper for HAL resources.
 */
public class HAL {

  /**
   * Pattern that will hit an RFC 6570 URI template.
   */
  private static final Pattern URI_TEMPLATE_PATTERN = Pattern.compile("\\{.+\\}");

  private final HalResource instance;

  /**
   * @see HalResourceFactory#createResource(String)
   * @param href Link HREF
   */
  public HAL(String href) {
    instance = HalResourceFactory.createResource(href);
  }

  /**
   * @see HalResourceFactory#createResource(ObjectNode, String)
   * @param state Resource state
   * @param href Link HREF
   */
  public HAL(Object state, String href) {
    instance = HalResourceFactory.createResource(state, href);
  }

  /**
   * @see HalResourceFactory#createResource(ObjectNode, String)
   * @param state Resource state
   * @param href Link HREF
   */
  public HAL(ObjectNode state, String href) {
    instance = HalResourceFactory.createResource(state, href);
  }

  /**
   * @see HalResourceFactory#createResource(Object, ResourceMapper)
   * @param input Resource pre-mapped state
   * @param mapper Resource state mapper
   */
  public HAL(Object input, ResourceMapper<?> mapper) {
    instance = HalResourceFactory.createResource(input, mapper);
  }

  /**
   * @see HalResourceFactory#createEmbeddedResource(Object, ResourceMapper)
   * @see HalResource#addEmbedded(String, HalResource...)
   * @param name Embedded resource name
   * @param input Embedded resource pre-mapped state
   * @param mapper Embedded resource state mapper
   * @return Helper
   */
  public HAL embed(String name, Object input, ResourceMapper<?> mapper) {
    HalResource embeddedResource = HalResourceFactory.createEmbeddedResource(input, mapper);
    instance.addEmbedded(name, embeddedResource);
    return this;
  }

  /**
   * @see HalResourceFactory#createEmbeddedResources(Iterable, ResourceMapper)
   * @see HalResource#addEmbedded(String, HalResource...)
   * @param name Embedded resources name
   * @param inputs Embedded resources pre-mapped state
   * @param mapper Embedded resources state mapper
   * @return Helper
   */
  public HAL embedAll(String name, Iterable<?> inputs, ResourceMapper<?> mapper) {
    List<HalResource> embeddedResource = HalResourceFactory.createEmbeddedResources(inputs, mapper);
    instance.addEmbedded(name, Iterables.toArray(embeddedResource, HalResource.class));
    return this;
  }

  /**
   * @see HalResource#addLinks(String, Link...)
   * @param relation Link relation
   * @param href Link HREF
   * @return Helper
   */
  public HAL link(String relation, String href) {
    instance.addLinks(relation, HalResourceFactory.createLink(href));
    return this;
  }

  /**
   * @see HalResource#addLinks(String, Link...)
   * @param relation Link relation
   * @param href Link HREF
   * @param name Link name
   * @return Helper
   */
  public HAL link(String relation, String href, String name) {
    boolean templated = href != null && URI_TEMPLATE_PATTERN.matcher(href).find();
    instance.addLinks(relation, HalResourceFactory.createLink(href).setName(name).setTemplated(templated));
    return this;
  }

  /**
   * @see HalResource#addLinks(String, Link...)
   * @param href Link HREF
   * @param name Link name
   * @return Helper
   */
  public HAL curi(String href, String name) {
    return link("curies", href, name);
  }

  /**
   * @return The HAL resource
   */
  public HalResource get() {
    return instance;
  }

}
