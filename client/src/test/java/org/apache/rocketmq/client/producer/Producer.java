package org.apache.rocketmq.client.producer;

import java.io.UnsupportedEncodingException;
import java.util.List;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.exception.RemotingException;

/**
 * Created by wuchanming on 17/6/14.
 */
public class Producer {
    public static void main(String[] args) throws UnsupportedEncodingException {
        try {
            System.setProperty(MixAll.NAMESRV_ADDR_PROPERTY, "11.163.14.232:9876");
            MQProducer producer = new DefaultMQProducer("rocketmq-study");

            producer.start();

            String[] tags = new String[]{"TagA", "TagB", "TagC", "TagD", "TagE"};

            for (int i = 0; i < 100; i++) {

                int orderId = i % 10;
                Message msg =
                    new Message("TopicTestjjj", tags[i % tags.length], "KEY" + i,
                        ("Hello RocketMQ " + i).getBytes(RemotingHelper.DEFAULT_CHARSET));

                SendResult sendResult = producer.send(msg, new MessageQueueSelector() {
                    @Override
                    public MessageQueue select(List<MessageQueue> mqs, Message msg, Object arg) {
                        Integer id = (Integer) arg;
                        int index = id % mqs.size();
                        return mqs.get(index);
                    }
                }, orderId);

                System.out.println(sendResult);
            }

            producer.shutdown();
        } catch (MQClientException e) {
            e.printStackTrace();
        } catch (RemotingException e) {
            e.printStackTrace();
        } catch (MQBrokerException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
