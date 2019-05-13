/*
 * Copyright (C) 2014 - 2019 PayinTech, SAS - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.jackson42.play.ecv.annotations;

import com.jackson42.play.ecv.ECValidateImpl;
import com.jackson42.play.ecv.interfaces.SecurityRule;
import play.mvc.With;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ECValidate.
 *
 * @author Pierre Adam
 * @since 19.05.09
 */
@With(ECValidateImpl.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ECValidate {
    Class<? extends SecurityRule>[] value() default {};
}