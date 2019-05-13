/*
 * Copyright (C) 2014 - 2019 PayinTech, SAS - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.jackson42.play.ecv.internal;

import com.jackson42.play.ecv.RouteExtractor;
import com.jackson42.play.ecv.annotations.RouteParam;
import com.jackson42.play.ecv.interfaces.SecurityRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.routing.HandlerDef;
import play.mvc.Http;
import play.mvc.PathBindable;
import play.mvc.Result;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * MethodInstance.
 *
 * @author Pierre Adam
 * @since 19.05.10
 */
public class MethodInstance {

    /**
     * The Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * The Bindable args.
     */
    private final Map<String, PathBindable> bindableArgs;

    /**
     * The Bindable args.
     */
    private final Map<Class<? extends SecurityRule>, SecurityRule> securityRules;

    /**
     * The Bindable args.
     */
    private final Map<Class<? extends Annotation>, Annotation> annotationArgs;

    /**
     * Instantiates a new Cached method.
     *
     * @param securityRules      the security rules
     * @param handlerDef         the handler def
     * @param cachedBinder       the cached binder
     * @param cachedSecurityRule the cached security rule
     */
    public MethodInstance(final Class<? extends SecurityRule>[] securityRules, final HandlerDef handlerDef,
                          final CachedBinder cachedBinder, final CachedSecurityRule cachedSecurityRule) {
        this.bindableArgs = new HashMap<>();
        this.securityRules = new HashMap<>();
        this.annotationArgs = new HashMap<>();
        this.resolveArgs(securityRules, cachedBinder, handlerDef);
        this.feedSecurityRules(securityRules, cachedSecurityRule);
    }

    /**
     * Resolve args.
     *
     * @param securityRules the security rules
     * @param cachedBinder  the cached binder
     * @param handlerDef    the handler def
     */
    private void resolveArgs(final Class<? extends SecurityRule>[] securityRules, final CachedBinder cachedBinder, final HandlerDef handlerDef) {
        Method controllerMethod = null;
        try {
            final Class<?> cClass = handlerDef.classLoader().loadClass(handlerDef.controller());
            for (final Method m : cClass.getMethods()) {
                if (m.getName().equals(handlerDef.method())) {
                    controllerMethod = m;
                    break;
                }
            }
            if (controllerMethod == null) {
                throw new NoSuchMethodException("MethodInstance is null");
            }
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException(String.format("Unable to load the controller %s", handlerDef.controller()));
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException(String.format("Unable to find the method %s in %s", handlerDef.method(), handlerDef.controller()));
        }

        for (final Class<? extends SecurityRule> securityRule : securityRules) {
            for (final Method method : securityRule.getMethods()) {
                if (method.getName().startsWith("validate") && method.getReturnType().equals(CompletionStage.class)) {
                    final String methodPath = securityRule.getName() + "." + method.getName();
                    for (final Parameter parameter : method.getParameters()) {
                        if (Http.Request.class.isAssignableFrom(parameter.getType())) {
                            // Ask for the request. It will be added on demand.
                            continue;
                        }
                        if (Annotation.class.isAssignableFrom(parameter.getType())) {
                            // Ask for an annotation on the controller method.
                            final Class<? extends Annotation> type = (Class<? extends Annotation>) parameter.getType();
                            final Annotation annotation = controllerMethod.getAnnotation(type);
                            this.annotationArgs.put(type, annotation);
                            continue;
                        }
                        final RouteParam annotation = parameter.getAnnotation(RouteParam.class);
                        if (annotation == null) {
                            throw new RuntimeException(String.format("Invalid validation method. Missing @RouteParam on a parameters of the following method : %s", methodPath));
                        }
                        if (!PathBindable.class.isAssignableFrom(parameter.getType())) {
                            throw new RuntimeException(
                                String.format("Invalid validation method. On '%s', the object '%s' does not implement PathBindable.", methodPath, parameter.getType().getName())
                            );
                        }
                        if (this.bindableArgs.containsKey(annotation.value()) && !this.bindableArgs.get(annotation.value()).getClass().equals(parameter.getType())) {
                            throw new RuntimeException(
                                String.format("Invalid validation method. Parameter '%s' expected to be '%s' in '%s'. But this parameter was already assigned to '%s'",
                                    annotation.value(), parameter.getType().getName(), methodPath, this.bindableArgs.get(annotation.value()).getClass().getName())
                            );
                        }
                        this.bindableArgs.put(annotation.value(), cachedBinder.getInstance((Class<? extends PathBindable>) parameter.getType()));
                    }
                }
            }
        }
    }

    /**
     * Feed security rules.
     *
     * @param securityRules      the security rules
     * @param cachedSecurityRule the cached security rule
     */
    private void feedSecurityRules(final Class<? extends SecurityRule>[] securityRules, final CachedSecurityRule cachedSecurityRule) {
        for (final Class<? extends SecurityRule> securityRule : securityRules) {
            this.securityRules.put(securityRule, cachedSecurityRule.getInstance(securityRule));
        }
    }

    /**
     * Validate completion stage.
     *
     * @param request the request
     * @return the completion stage
     */
    public CompletionStage<Result> validate(final Http.Request request) {
        final Map<String, String> extractedValues = RouteExtractor.extract(request);
        final Map<String, Object> resolvedArgs = new HashMap<>();
        for (final Map.Entry<String, PathBindable> entry : this.bindableArgs.entrySet()) {
            if (!extractedValues.containsKey(entry.getKey())) {
                throw new RuntimeException(String.format("Invalid validation method. Parameter '%s' has not been found on the route.", entry.getKey()));
            }
            try {
                resolvedArgs.put(entry.getKey(), entry.getValue().bind(entry.getKey(), extractedValues.get(entry.getKey())));
            } catch (final Exception ignore) {
                resolvedArgs.put(entry.getKey(), null);
            }
        }

        for (final Map.Entry<Class<? extends SecurityRule>, SecurityRule> entry : this.securityRules.entrySet()) {
            final Class<? extends SecurityRule> sClass = entry.getKey();
            final SecurityRule instance = entry.getValue();
            for (final Method method : sClass.getDeclaredMethods()) {
                if (method.getName().startsWith("validate") && method.getReturnType().equals(CompletionStage.class)) {
                    try {
                        final CompletionStage<Result> completionStage = (CompletionStage<Result>) method.invoke(instance, this.toMethodArgs(method, request, resolvedArgs));
                        if (completionStage != null) {
                            return completionStage;
                        }
                    } catch (final IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException("Unable to invoke the method.", e);
                    }
                }
            }
        }
        return null;
    }

    /**
     * To method args object [ ].
     *
     * @param method       the method
     * @param request      the request
     * @param resolvedArgs the resolved args
     * @return the object [ ]
     */
    private Object[] toMethodArgs(final Method method, final Http.Request request, final Map<String, Object> resolvedArgs) {
        final List<Object> args = new ArrayList<>();

        for (final Parameter parameter : method.getParameters()) {
            if (Http.Request.class.isAssignableFrom(parameter.getType())) {
                args.add(request);
                continue;
            }
            if (Annotation.class.isAssignableFrom(parameter.getType())) {
                args.add(this.annotationArgs.get(parameter.getType()));
                continue;
            }
            final RouteParam annotation = parameter.getAnnotation(RouteParam.class);
            if (resolvedArgs.containsKey(annotation.value())) {
                args.add(resolvedArgs.get(annotation.value()));
            }
        }

        return args.toArray();
    }
}
