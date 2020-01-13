/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.xream.x7.common.event;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;
import io.xream.x7.common.util.StringUtil;

import java.lang.reflect.Method;


/**
 * @author Sim Wang
 * @date 2017/5/12.
 */
public class EventListenerBeanPostProcesser implements BeanPostProcessor {

    private static Logger logger = LoggerFactory.getLogger(EventListenerBeanPostProcesser.class);

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Method[] methods = ReflectionUtils.getAllDeclaredMethods(bean.getClass());
        if (methods != null) {
            for (Method method : methods) {
                EventListener listener = AnnotationUtils.findAnnotation(method,EventListener.class);
                if (listener != null){
                    String type = listener.type();
                    String tag = listener.type();
                    if (StringUtil.isNullOrEmpty(type)) {
                        type = listener.value();
                    }
                    final String t = type;

                    Event event = new Event(){

                        @Override
                        public String getType() {
                            return t;
                        }

                        @Override
                        public EventOwner getOwner() {
                            return null;
                        }

                        @Override
                        public String getTag() {
                            return tag;
                        }

                        @Override
                        public long getReTimes() {
                            return 0;
                        }
                    };

                    if (! EventDispatcher.isEventListenerEnabled()){
                        EventDispatcher.enableEventListener();
                    }

                    EventDispatcher.addEventTemplate(event);
                    logger.info("@EventListener event: " + event.getType() + " " + event.getTag() );

                    String key = type + tag;
                    EventDispatcher.addEventListener(key, event1 -> {
                        try {
                            method.invoke(bean, event1);
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw new RuntimeException("handle the event, got exception");
                        }
                    });
                }

            }
        }
        return bean;
    }
}