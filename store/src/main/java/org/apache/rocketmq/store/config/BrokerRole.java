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
package org.apache.rocketmq.store.config;

//Broker角色，用在主备机中，异步复制的主机为 ASYNC_MASTER，同步双写的主机为 SYNC_MASTER，备机为 SLAVE
public enum BrokerRole {
    ASYNC_MASTER, //异步复制,即一条消息写到主机后就返回给客户端，主机定时将数据同步到备机，如果主机宕机，肯能有部分消息会丢失；
    SYNC_MASTER,  //同步复制,即一条消息写到主机以及备机后才返回给客户端，能够提供比异步复制更高的可靠性；
    SLAVE;  //备机
}
