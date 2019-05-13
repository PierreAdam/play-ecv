/*
 * Copyright (C) 2014 - 2019 PayinTech, SAS - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.jackson42.play.ecv.internal;

import com.jackson42.play.ecv.RouteExtractor;
import com.jackson42.play.ecv.annotations.OptionalParam;
import com.jackson42.play.ecv.annotations.RequiredParam;
import com.jackson42.play.ecv.interfaces.ECValidationRule;
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
    private final Map<Class<? extends ECValidationRule>, ECValidationRule> securityRules;

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
    public MethodInstance(final Class<? extends ECValidationRule>[] securityRules, final HandlerDef handlerDef,
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
    private void resolveArgs(final Class<? extends ECValidationRule>[] securityRules, final CachedBinder cachedBinder, final HandlerDef handlerDef) {
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

        for (final Class<? extends ECValidationRule> securityRule : securityRules) {
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
                        final RequiredParam required = parameter.getAnnotation(RequiredParam.class);
                        final OptionalParam optional = parameter.getAnnotation(OptionalParam.class);

                        final String paramKey;
                        if (required != null) {
                            paramKey = required.value();
                        } else if (optional != null) {
                            paramKey = optional.value();
                        } else {
                            throw new RuntimeException(String.format("Invalid validation method. Missing @RequiredParam or @OptionalParam on a parameters of the following method : %s", methodPath));
                        }

                        if (!PathBindable.class.isAssignableFrom(parameter.getType())) {
                            throw new RuntimeException(
                                    String.format("Invalid validation method. On '%s', the object '%s' does not implement PathBindable.", methodPath, parameter.getType().getName())
                            );
                        }
                        if (this.bindableArgs.containsKey(paramKey) && !this.bindableArgs.get(paramKey).getClass().equals(parameter.getType())) {
                            throw new RuntimeException(
                                    String.format("Invalid validation method. Parameter '%s' expected to be '%s' in '%s'. But this parameter was already assigned to '%s'",
                                            paramKey, parameter.getType().getName(), methodPath, this.bindableArgs.get(required.value()).getClass().getName())
                            );
                        }
                        this.bindableArgs.put(paramKey, cachedBinder.getInstance((Class<? extends PathBindable>) parameter.getType()));
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
    private void feedSecurityRules(final Class<? extends ECValidationRule>[] securityRules, final CachedSecurityRule cachedSecurityRule) {
        for (final Class<? extends ECValidationRule> securityRule : securityRules) {
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
        final Map<String, Object> resolvedArgs = this.argsToObj(extractedValues);

        for (final Map.Entry<Class<? extends ECValidationRule>, ECValidationRule> entry : this.securityRules.entrySet()) {
            final Class<? extends ECValidationRule> sClass = entry.getKey();
            final ECValidationRule instance = entry.getValue();
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
     * Args to obj map.
     *
     * @param extractedValues the extracted values
     * @return the map
     */
    private Map<String, Object> argsToObj(final Map<String, String> extractedValues) {
        final Map<String, Object> args = new HashMap<>();
        for (final Map.Entry<String, PathBindable> entry : this.bindableArgs.entrySet()) {
            if (!extractedValues.containsKey(entry.getKey())) {
                continue;
            }
            try {
                args.put(entry.getKey(), entry.getValue().bind(entry.getKey(), extractedValues.get(entry.getKey())));
            } catch (final Exception ignore) {
                args.put(entry.getKey(), null);
            }
        }
        return args;
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
            final RequiredParam required = parameter.getAnnotation(RequiredParam.class);
            final OptionalParam optional = parameter.getAnnotation(OptionalParam.class);

            if (required != null) {
                if (resolvedArgs.containsKey(required.value())) {
                    args.add(resolvedArgs.get(required.value()));
                } else {
                    throw new RuntimeException(String.format("Invalid validation method. Parameter '%s' has not been found on the route.", required.value()));
                }
            } else if (optional != null) {
                args.add(resolvedArgs.getOrDefault(optional.value(), null));
            } else {
                final String methodPath = method.getDeclaringClass().getName() + "." + method.getName();
                throw new RuntimeException(String.format("Invalid validation method. Missing @RequiredParam or @OptionalParam on a parameters of the following method : %s", methodPath));
            }
        }

        return args.toArray();
    }
}
