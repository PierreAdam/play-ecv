/*
 * Copyright (C) 2014 - 2019 PayinTech, SAS - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.jackson42.play.ecv;

import com.jackson42.play.ecv.annotations.ECValidate;
import com.jackson42.play.ecv.internal.CachedBinder;
import com.jackson42.play.ecv.internal.CachedSecurityRule;
import com.jackson42.play.ecv.internal.MethodInstance;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Application;
import play.api.routing.HandlerDef;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import play.routing.Router;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * ECValidateImpl.
 *
 * @author Pierre Adam
 * @since 19.05.09
 */
public class ECValidateImpl extends Action<ECValidate> {

    /**
     * The Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * The Config.
     */
    private final Config config;

    /**
     * The Cached binder.
     */
    private final CachedBinder cachedBinder;

    /**
     * The Cached security rule.
     */
    private final CachedSecurityRule cachedSecurityRule;

    /**
     * The Method instance cache.
     */
    private final Map<String, MethodInstance> methodInstanceCache;

    /**
     * Build a new instance.
     *
     * @param application the application
     * @param config      Handle to application configuration
     */
    @Inject
    public ECValidateImpl(final Application application, final Config config) {
        this.config = config;
        this.cachedBinder = new CachedBinder(application);
        this.cachedSecurityRule = new CachedSecurityRule(application);
        this.methodInstanceCache = Collections.synchronizedMap(new HashMap<>());
    }

    @Override
    public CompletionStage<Result> call(final Http.Request request) {
        final HandlerDef handlerDef = request.attrs().get(Router.Attrs.HANDLER_DEF);
        final String key = handlerDef.controller() + "." + handlerDef.method();
        final MethodInstance methodInstance;
        if (this.methodInstanceCache.containsKey(key)) {
            methodInstance = this.methodInstanceCache.get(key);
        } else {
            methodInstance = new MethodInstance(this.configuration.value(), handlerDef, this.cachedBinder, this.cachedSecurityRule);
            this.methodInstanceCache.put(key, methodInstance);
        }

        final CompletionStage<Result> result = methodInstance.validate(request);

        if (result == null) {
            return this.delegate.call(request);
        } else {
            return result;
        }
    }
}
