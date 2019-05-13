/*
 * Copyright (C) 2014 - 2019 PayinTech, SAS - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.jackson42.play.ecv.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RequiredParam.
 *
 * @author Pierre Adam
 * @since 19.05.09
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredParam {

    /**
     * The name of the variable on the route.
     *
     * @return the name of the variable on the route
     */
    String value();
}
