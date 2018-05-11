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

package org.apache.rocketmq.filtersrv.filter;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.ThreadFactoryImpl;
import org.apache.rocketmq.common.UtilAll;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.filter.MessageFilter;
import org.apache.rocketmq.filtersrv.FiltersrvController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilterClassManager {
    private static final Logger log = LoggerFactory.getLogger(LoggerName.FILTERSRV_LOGGER_NAME);

    private final Object compileLock = new Object();
    private final FiltersrvController filtersrvController;

    private final ScheduledExecutorService scheduledExecutorService = Executors
        .newSingleThreadScheduledExecutor(new ThreadFactoryImpl("FSGetClassScheduledThread"));
    private ConcurrentHashMap<String/* topic@consumerGroup */, FilterClassInfo> filterClassTable =
        new ConcurrentHashMap<String, FilterClassInfo>(128);
    private FilterClassFetchMethod filterClassFetchMethod;

    public FilterClassManager(FiltersrvController filtersrvController) {
        this.filtersrvController = filtersrvController;
        this.filterClassFetchMethod =
            new HttpFilterClassFetchMethod(this.filtersrvController.getFiltersrvConfig()
                .getFilterClassRepertoryUrl());
    }

    private static String buildKey(final String consumerGroup, final String topic) {
        return topic + "@" + consumerGroup;
    }

    public void start() {
        if (!this.filtersrvController.getFiltersrvConfig().isClientUploadFilterClassEnable()) {
            this.scheduledExecutorService.scheduleAtFixedRate(new Runnable() {

                @Override
                public void run() {
                    fetchClassFromRemoteHost();
                }
            }, 1, 1, TimeUnit.MINUTES);
        }
    }

    private void fetchClassFromRemoteHost() {
        Iterator<Entry<String, FilterClassInfo>> it = this.filterClassTable.entrySet().iterator();
        while (it.hasNext()) {
            try {
                Entry<String, FilterClassInfo> next = it.next();
                FilterClassInfo filterClassInfo = next.getValue();
                String[] topicAndGroup = next.getKey().split("@");
                String responseStr =
                    this.filterClassFetchMethod.fetch(topicAndGroup[0], topicAndGroup[1],
                        filterClassInfo.getClassName());
                byte[] filterSourceBinary = responseStr.getBytes("UTF-8");
                int classCRC = UtilAll.crc32(responseStr.getBytes("UTF-8"));
                if (classCRC != filterClassInfo.getClassCRC()) {
                    String javaSource = new String(filterSourceBinary, MixAll.DEFAULT_CHARSET);
                    Class<?> newClass =
                        DynaCode.compileAndLoadClass(filterClassInfo.getClassName(), javaSource);
                    Object newInstance = newClass.newInstance();
                    filterClassInfo.setMessageFilter((MessageFilter) newInstance);
                    filterClassInfo.setClassCRC(classCRC);

                    log.info("fetch Remote class File OK, {} {}", next.getKey(),
                        filterClassInfo.getClassName());
                }
            } catch (Exception e) {
                log.error("fetchClassFromRemoteHost Exception", e);
            }
        }
    }

    public void shutdown() {
        this.scheduledExecutorService.shutdown();
    }
    /**
     * 由于MessageFilter是在Consumer端实现的，如果在实现类中用了第三方的api，并且在filtersrv中不存在，
     * 那么将会导致传输到filtersrv的java源码无法编译，如果需要能够编译通过，需要将该第三方api的jar包也放到filtersrv
     * 的classpath下面。同时还需要注意的是，你的实现类不能有包名，不然也会导致编译不通过
     3:  * 注册过滤类
     4:  *
     5:  * @param consumerGroup 消费分组
     6:  * @param topic Topic
     7:  * @param className 过滤类名
     8:  * @param classCRC 过滤类源码CRC
     9:  * @param filterSourceBinary 过滤类源码
     10:  * @return 是否注册成功
     */
    public boolean registerFilterClass(final String consumerGroup, final String topic,
        final String className, final int classCRC, final byte[] filterSourceBinary) {
        final String key = buildKey(consumerGroup, topic);
        // // 先检查是否存在，是否CRC相同
        boolean registerNew = false;
        FilterClassInfo filterClassInfoPrev = this.filterClassTable.get(key);
        if (null == filterClassInfoPrev) {
            registerNew = true;
        } else {
            if (this.filtersrvController.getFiltersrvConfig().isClientUploadFilterClassEnable()) {
                if (filterClassInfoPrev.getClassCRC() != classCRC && classCRC != 0) {
                    registerNew = true;
                }
            }
        }
        // 注册新的Class
        if (registerNew) {
            synchronized (this.compileLock) {
                filterClassInfoPrev = this.filterClassTable.get(key);
                if (null != filterClassInfoPrev && filterClassInfoPrev.getClassCRC() == classCRC) {
                    return true;
                }

                try {

                    FilterClassInfo filterClassInfoNew = new FilterClassInfo();
                    filterClassInfoNew.setClassName(className);
                    filterClassInfoNew.setClassCRC(0);
                    filterClassInfoNew.setMessageFilter(null);

                    if (this.filtersrvController.getFiltersrvConfig().isClientUploadFilterClassEnable()) {
                        String javaSource = new String(filterSourceBinary, MixAll.DEFAULT_CHARSET);
                        Class<?> newClass = DynaCode.compileAndLoadClass(className, javaSource);
                        Object newInstance = newClass.newInstance();
                        filterClassInfoNew.setMessageFilter((MessageFilter) newInstance);
                        filterClassInfoNew.setClassCRC(classCRC);
                    }

                    this.filterClassTable.put(key, filterClassInfoNew);
                } catch (Throwable e) {
                    String info =
                        String
                            .format(
                                "FilterServer, registerFilterClass Exception, consumerGroup: %s topic: %s className: %s",
                                consumerGroup, topic, className);
                    log.error(info, e);
                    return false;
                }
            }
        }

        return true;
    }

    public FilterClassInfo findFilterClass(final String consumerGroup, final String topic) {
        return this.filterClassTable.get(buildKey(consumerGroup, topic));
    }

    public FilterClassFetchMethod getFilterClassFetchMethod() {
        return filterClassFetchMethod;
    }

    public void setFilterClassFetchMethod(FilterClassFetchMethod filterClassFetchMethod) {
        this.filterClassFetchMethod = filterClassFetchMethod;
    }
}
