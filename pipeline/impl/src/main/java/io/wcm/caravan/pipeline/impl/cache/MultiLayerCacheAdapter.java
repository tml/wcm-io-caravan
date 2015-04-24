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
package io.wcm.caravan.pipeline.impl.cache;

import io.wcm.caravan.pipeline.cache.CachePersistencyOptions;
import io.wcm.caravan.pipeline.cache.spi.CacheAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;

/**
 * Implementation of {@link CacheAdapter}.
 * Wraps a list of references to multiple cache adapters and delegates
 * {@link #getCacheKey(String servicePrefix, String descriptor)} ,
 * {@link #get(String cacheKey, CachePersistencyOptions options)},
 * {@link #put(String cacheKey, String jsonString, CachePersistencyOptions options, CacheAdapter actualCacheAdapter)}
 * method calls according to the level of cache adapters.
 */
public class MultiLayerCacheAdapter implements CacheAdapter {

  private static final Logger log = LoggerFactory.getLogger(MultiLayerCacheAdapter.class);

  /**
   * Return multi layer cache key, if no cache key was returned by any child cache adapter.
   */
  public static final String MULTILAYER_CACHE_KEY = "NO_CACHEADAPTER_AVAILABLE";

  private List<CacheAdapter> cacheAdapters;

  /**
   * Creates a multi layer cache adapter specifying list of references cache adapters, provided in a strict order.
   * Position of each cache adapter in the list specifies its cache level and order, in which this cache adapter will be
   * called.
   * @param cacheAdapters List of {@link CacheAdapter} references
   */
  public MultiLayerCacheAdapter(List<CacheAdapter> cacheAdapters) {
    this.cacheAdapters = new ArrayList<CacheAdapter>(cacheAdapters);

  }

  /**
   * Retrieves first available item from wrapped caches. If available item was found in the second or lower cache
   * level, this item will be put into the first or other higher cache levels.
   * @param cacheKey Cache key
   * @param options valid cache persistency options
   * @return an observable, than emits first found cache item. If no cache item is found, returns an empty observable.
   */
  @Override
  public Observable<String> get(String cacheKey, CachePersistencyOptions options) {
    return Observable.create(subscriber -> {
      Observable<String> result = Observable.empty();
      CacheAdapter actualCacheAdapter = null;
      for (int i = 0; i < cacheAdapters.size(); i++) {
        CacheAdapter cacheAdapter = cacheAdapters.get(i);
        log.debug("Trying to retrieve document with id {} from cache level {} : {}", cacheKey, i, cacheAdapter.getClass().getSimpleName());
        result = cacheAdapter.get(cacheKey, options).cache();
        if (!result.isEmpty().toBlocking().first()) {
          log.debug("Retrieved document with id {} from cache level {} : {} : {}", cacheKey, i, cacheAdapter.getClass().getSimpleName(), result.toBlocking()
              .first());
          actualCacheAdapter = cacheAdapter;
          break;
        }
      }
      if (actualCacheAdapter != null) {
        String cachedValue = result.toBlocking().first();
        subscriber.onNext(cachedValue);
        put(cacheKey, cachedValue, options, actualCacheAdapter);
      }
      subscriber.onCompleted();
    });
  }

  /*
   * Puts cache item into the caches, which are specified to be earlier than actual CacheAdapter. Call this method, if expected that
   * actual CacheAdapter and later caches already have stored item.
   */
  private void put(String cacheKey, String jsonString, CachePersistencyOptions options, CacheAdapter actualCacheAdapter) {
    for (int i = 0; i < cacheAdapters.size(); i++) {
      CacheAdapter cacheAdapter = cacheAdapters.get(i);
      if (cacheAdapter != actualCacheAdapter) {
        log.debug("Trying to reput document {} into cache level {} : {} : ", cacheKey, i, cacheAdapter.getClass().getSimpleName(), jsonString);
        cacheAdapter.put(cacheKey, jsonString, options);
      }
      else {
        break;
      }
    }
  }

  /**
   * Store an item in each wrapped cache.
   * @param cacheKey Cache key
   * @param jsonString JSON data
   * @param options valid cache persistency options
   */
  @Override
  public void put(String cacheKey, String jsonString, CachePersistencyOptions options) {
    for (int i = 0; i < cacheAdapters.size(); i++) {
      CacheAdapter cacheAdapter = cacheAdapters.get(i);
      cacheAdapter.put(cacheKey, jsonString, options);
      log.debug("Trying to put document {} into cache level {} : {} : {}", cacheKey, i, cacheAdapter.getClass().getSimpleName(), jsonString);
    }
  }

  /**
   * @return amount of registered caching levels
   */
  public int cachingLevels() {
    return cacheAdapters.size();
  }

  /**
   * @return non modifiable list of cache adapters
   */
  public List<CacheAdapter> getCacheAdapters() {
    return Collections.unmodifiableList(cacheAdapters);
  }

}
