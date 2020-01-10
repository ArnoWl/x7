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
package io.xream.x7.cache;


import io.xream.x7.common.util.ExceptionUtil;
import io.xream.x7.common.util.JsonX;
import io.xream.x7.common.util.StringUtil;
import io.xream.x7.exception.DistributionLockException;
import io.xream.x7.exception.L3CacheException;
import io.xream.x7.lock.DistributionLock;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


public interface L3CacheResolver {

    L3CacheStorage getStorage();

    default String resolve(String key, long expireTime, TimeUnit timeUnit, Callable caller) {
        try {
            String value = getStorage().get(key, expireTime, timeUnit); //从缓存里获取
            if (StringUtil.isNotNull(value)) {//如果有
                PeriodCounter.reset(key);
                if (L3CacheStorage.DEFAULT_VALUE.equals(value)) //防止缓存击穿
                    return null;
                return value;//就返回缓存结果
            }
        }catch (Exception e) {
            PeriodCounter.reset(key);
            throw new RuntimeException(ExceptionUtil.getMessage(e));
        }

        try {
            return DistributionLock.by(key).lock(t -> { //分布式锁，锁住资源
                try {
                    Object obj = caller.call();//读数据库或远程调用
                    String str = JsonX.toJson(obj);//转成JSON字符串
                    getStorage().set(key, str, expireTime, timeUnit);//然后存到缓存
                    return str;//锁住资源的人，等待至同步返回查询结果
                } catch (Throwable e) {
                    throw new RuntimeException(ExceptionUtil.getMessage(e));
                }finally {
                    PeriodCounter.reset(key);
                }
            });
        } catch (DistributionLockException dle) { // 如果别的请求已经锁了资源
            PeriodCounter.increment(key);
            try {
                Thread.sleep(PeriodCounter.SLEEP_MILLIS);
            } catch (InterruptedException e) {
                PeriodCounter.reset(key);
                throw new RuntimeException(ExceptionUtil.getMessage(e));
            }
            return resolve(key, expireTime, timeUnit, caller);//递归请求
        } catch (Exception e) {
            PeriodCounter.reset(key);
            throw new RuntimeException(ExceptionUtil.getMessage(e));
        }
    }


    class PeriodCounter {

        private final static Map<String, Long> countMap = new ConcurrentHashMap<>();
        private final static long MAX_COUNT = 40;
        private final static long SLEEP_MILLIS = 300;

        private static void increment(String key) {
            key = wrapKey(key);
            Long count = countMap.get(key);
            if (count == null);
            {
                count = 0L;
            }
            if (count >= MAX_COUNT) {
                reset(key);
                throw new L3CacheException("SERVER BUSY");
            }

            countMap.put(key,count + 1);
        }

        private static void reset(String key) {
            key = wrapKey(key);
            countMap.remove(key);
        }

        private static String wrapKey(String key) {
            String threadId = String.valueOf(Thread.currentThread().getId());
            String k = key + "." + threadId;
            return k;
        }
    }


}
