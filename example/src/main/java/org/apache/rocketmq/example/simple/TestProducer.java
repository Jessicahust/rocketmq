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
package org.apache.rocketmq.example.simple;

import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.common.RemotingHelper;

public class TestProducer {
    public static void main(String[] args) throws MQClientException, InterruptedException {
        System.setProperty(MixAll.NAMESRV_ADDR_PROPERTY, "11.162.141.67:9876");
        /**

         * 一个应用创建一个Producer，由应用来维护此对象，可以设置为全局对象或者单例<br>

         * 注意：ProducerGroupName需要由应用来保证唯一<br>

         * ProducerGroup这个概念发送普通的消息时，作用不大，但是发送分布式事务消息时，比较关键，

         * 因为服务器会回查这个Group下的任意一个Producer

         */
        DefaultMQProducer producer = new DefaultMQProducer("ProducerGroupName");
        producer.start();

        for (int i = 0; i < 1; i++)
            try {
                {
                    Message msg = new Message("TopicTest10",
                        "TagA",
                        "key113",
                        "Hello world".getBytes(RemotingHelper.DEFAULT_CHARSET));
                    SendResult sendResult = producer.send(msg);
                    System.out.printf("%s%n", sendResult);

                    QueryResult queryMessage =
                        producer.queryMessage("TopicTest4", "key113", 10, 0, System.currentTimeMillis());
                    for (MessageExt m : queryMessage.getMessageList()) {
                        System.out.printf("%s%n", m);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        producer.shutdown();
    }
}
