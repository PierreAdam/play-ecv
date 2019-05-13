/*
 * Copyright (C) 2014 - 2019 PayinTech, SAS - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.jackson42.play.ecv;

import play.api.routing.HandlerDef;
import play.mvc.Http;
import play.routing.Router;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RouteExtractor.
 *
 * @author Pierre Adam
 * @since 19.05.07
 */
public final class RouteExtractor {

    /**
     * The constant INDEX_PATTERN.
     */
    private static final Pattern INDEX_PATTERN = Pattern.compile("\\$(.+?)\\<([^\\>]+)\\>");

    /**
     * Instantiates a new Route extractor.
     */
    private RouteExtractor() {
    }

    /**
     * Extract positions map.
     *
     * @param routePattern the route pattern
     * @return the map
     */
    private static Map<Integer, String> extractPositions(final String routePattern) {
        final Matcher matcher = INDEX_PATTERN.matcher(routePattern);
        final Map<Integer, String> results = new HashMap<>();
        int index = 0;
        while (matcher.find()) {
            results.put(index++, matcher.group(1));
        }
        return results;
    }

    /**
     * Replace route pattern with group string.
     *
     * @param routePattern the route pattern
     * @return the string
     */
    private static String replaceRoutePatternWithGroup(final String routePattern) {
        final StringBuffer stringBuffer = new StringBuffer();
        final Matcher matcher = INDEX_PATTERN.matcher(routePattern);
        while (matcher.find()) {
            final String regex = matcher.group(2);
            matcher.appendReplacement(stringBuffer, String.format("(%s)", regex));
        }
        matcher.appendTail(stringBuffer);
        return stringBuffer.toString();
    }

    /**
     * Extract map.
     *
     * @param request the request
     * @return the map
     */
    public static Map<String, String> extract(final Http.Request request) {
        final HandlerDef handlerDef = request.attrs().get(Router.Attrs.HANDLER_DEF);
        final String routePattern = handlerDef.path();
        final String path = request.path();

        final Pattern pattern = Pattern.compile(replaceRoutePatternWithGroup(routePattern));
        final Matcher matcher = pattern.matcher(path);

        final Map<String, String> results = new HashMap<>();
        if (matcher.find()) {
            extractPositions(routePattern).forEach((key, value) -> results.put(value, matcher.group(key + 1)));
        }
        return results;
    }
}
