/*
 * Copyright (C) 2014 - 2019 PayinTech, SAS - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.jackson42.play.ecv.internal;

import com.jackson42.play.ecv.interfaces.SecurityRule;
import play.Application;

/**
 * CachedBinder.
 *
 * @author Pierre Adam
 * @since 19.05.10
 */
public class CachedSecurityRule extends ClassCache<SecurityRule> {

    /**
     * Instantiates a new Cached binder.
     *
     * @param application the application
     */
    public CachedSecurityRule(final Application application) {
        super(application);
    }
}
