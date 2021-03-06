/*
 * Copyright 2013-2014 eBay Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kylinolap.rest.interceptor;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import com.kylinolap.common.restclient.Broadcaster;

/**
 * @author xduo
 * 
 */
@Aspect
@Component("cacheIntercaptor")
public class CacheIntercaptor {

    @After("execution(public * com.kylinolap.rest.controller.CubeController.*(..)) || execution(public * com.kylinolap.rest.controller.ProjectController.*(..))")
    public void flush(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();

        if (methodName.matches("(update|create|save|disable|enable|delete|drop|purge)")) {
            Broadcaster.flush();
        }
    }
}
