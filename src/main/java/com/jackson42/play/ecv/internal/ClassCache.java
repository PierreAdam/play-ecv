/*
 * Copyright (C) 2014 - 2019 PayinTech, SAS - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.jackson42.play.ecv.internal;

import play.Application;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * ClassCache.
 *
 * @param <T> the type parameter
 * @author Pierre Adam
 * @since 19.05.10
 */
public class ClassCache<T> {

    /**
     * The Application.
     */
    private final Application application;

    /**
     * The Cache.
     */
    private final Map<Class<? extends T>, T> cache;

    /**
     * Instantiates a new Cached binder.
     *
     * @param application the application
     */
    protected ClassCache(final Application application) {
        this.application = application;
        this.cache = Collections.synchronizedMap(new HashMap<>());
    }

    /**
     * Gets instance.
     *
     * @param tClass the class
     * @return the instance
     */
    public T getInstance(final Class<? extends T> tClass) {
        if (this.cache.containsKey(tClass)) {
            return this.cache.get(tClass);
        }
        try {
            final T binder = this.application.injector().instanceOf(tClass);
            if (binder == null) {
                throw new RuntimeException("Binder is null.");
            }
            return binder;
        } catch (final Exception e) {
            throw new RuntimeException(String.format("Unable to initialize the binder '%s'.", tClass.getName()), e);
        }
    }
}
