package com.seu.config;

import com.rabbitmq.client.Channel;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.RabbitUtils;
import org.springframework.amqp.rabbit.core.ChannelAwareMessageListener;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


import java.io.IOException;
import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author ：caozhiyuan
 * @date ：Created in 2019/7/21 9:35
 */
@Configuration
public class RabbitMQConfig {

    static final String topicExchangeName = "asynctest";

    static final String queueName = "asynctest";

    @Bean
    Queue queue() {
        return new Queue(queueName, true,false,false,null);
    }

    @Bean
    TopicExchange exchange() {
        return new TopicExchange(topicExchangeName,true,false);
    }

    @Bean
    Binding binding(Queue queue, TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("asynctest");
    }

    @Bean
    MessageListener testListener(){
       return new TestListener();
    }

    @Bean
    DirectConcurrentMessageListenerContainer container(ConnectionFactory connectionFactory, MessageListener testListener) {
        DirectConcurrentMessageListenerContainer container = new DirectConcurrentMessageListenerContainer();
        container.addQueueNames(queueName);
        container.setConsumersPerQueue(1);
        container.setPrefetchCount(50);
        container.setConnectionFactory(connectionFactory);
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        container.setMessageListener(testListener);
        return container;
    }

    public class DirectConcurrentMessageListenerContainer extends DirectMessageListenerContainer{

        protected final Log logger = LogFactory.getLog(getClass());
        private ExecutorService executor;

        @Override
        public void setMessageListener(MessageListener messageListener) {
            this.setMessageListener(new ConcurrentMessageListener(messageListener));
        }

        @Override
        public void setMessageListener(Object object) {
            if (object instanceof ConcurrentMessageListener) {
                super.setMessageListener(object);
            } else {
                throw new IllegalArgumentException("Message listener needs to be of type [" + ConcurrentMessageListener.class.getName() + "]");
            }
        }

        @Override
        public void setChannelAwareMessageListener(ChannelAwareMessageListener messageListener) {
            throw new NotImplementedException();
        }

        @Override
        protected void doStart() throws Exception {
            super.doStart();
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            executor = new ThreadPoolExecutor(availableProcessors,
                    availableProcessors * 2,
                    200, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(),
                    new DefaultThreadFactory());
        }

        class DefaultThreadFactory implements ThreadFactory {
            private final AtomicInteger poolNumber = new AtomicInteger(1);
            private final ThreadGroup group;
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            private final String namePrefix;

            DefaultThreadFactory() {
                SecurityManager s = System.getSecurityManager();
                group = (s != null) ? s.getThreadGroup() :
                        Thread.currentThread().getThreadGroup();
                namePrefix = "directConcurrentMessageListener-" +
                        poolNumber.getAndIncrement() +
                        "-thread-";
            }

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(group, r,
                        namePrefix + threadNumber.getAndIncrement(),
                        0);
                if (t.isDaemon()) {
                    t.setDaemon(false);
                }
                if (t.getPriority() != Thread.NORM_PRIORITY) {
                    t.setPriority(Thread.NORM_PRIORITY);
                }
                return t;
            }
        }

        @Override
        public void doStop() {
            super.doStop();
            executor.shutdown();
            executor = null;
        }

        public class ConcurrentMessageListener implements ChannelAwareMessageListener {

            private MessageListener messageListener;

            ConcurrentMessageListener(MessageListener messageListener) {
                this.messageListener = messageListener;
            }

            @Override
            public void onMessage(Message message, Channel channel) {
                if (executor == null) {
                    return;
                }

                CompletableFuture.runAsync(() -> {
                    messageListener.onMessage(message);
                }, executor).whenComplete((r, e)->{
                    try {
                        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }).exceptionally(e->{
                    try {
                        //need AckStrategy requeue avoid repeat error
                        boolean requeue = RabbitUtils.shouldRequeue(isDefaultRequeueRejected(), e, DirectConcurrentMessageListenerContainer.this.logger);
                        channel.basicNack(message.getMessageProperties().getDeliveryTag(),false, requeue);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    return null;
                });
            }
        }
    }

    public class TestListener implements MessageListener {

        @Override
        public void onMessage(Message message) {
            try {
                //Thread.sleep(50);
                System.out.println("in :" + new Date());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
