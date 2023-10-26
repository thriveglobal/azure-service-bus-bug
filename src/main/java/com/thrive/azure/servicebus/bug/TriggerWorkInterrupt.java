package com.thrive.azure.servicebus.bug;

import com.azure.messaging.servicebus.*;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.azure.messaging.servicebus.models.SubQueue;
import com.google.common.util.concurrent.Uninterruptibles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;


public class TriggerWorkInterrupt {
    private static final Logger LOG = LoggerFactory.getLogger(TriggerWorkInterrupt.class);

    private static final AtomicBoolean RUNNING = new AtomicBoolean(true);
    private static final Duration SESSION_IDLE_TIMEOUT = Duration.ofSeconds(5);

    static void processMessage(ServiceBusReceivedMessageContext msg) {
        try {
            LOG.info("Started handling message for session: {} message: {}", msg.getMessage().getSessionId(), msg.getMessage().getMessageId());
            if (msg.getMessage().getSessionId().equals("constant")) {
                LOG.info("message on constant session sleeping");
                Thread.sleep(SESSION_IDLE_TIMEOUT.toMillis() / 10);
            }
            msg.complete();
            LOG.info("Finished handling message for session: {} message: {}", msg.getMessage().getSessionId(), msg.getMessage().getMessageId());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static void processError(ServiceBusErrorContext ctx) {
        LOG.error("Got Error: {} {} {} ", ctx.getErrorSource(),
                ctx.getFullyQualifiedNamespace(), ctx.getEntityPath(), ctx.getException());
    }

    public static void sendMessageForSession(ServiceBusSenderClient azureSender, String id, String session) {
        try {
            LOG.info("Sending message: {} session: {} ", id, session);
            azureSender.sendMessage(
                    new ServiceBusMessage("message for session " + session)
                            .setMessageId(id)
                            .setSessionId(session)
            );
        } catch (Exception e) {
            LOG.error("Error sending", e);
        }
    }

    public static void main(String... args) {
        ServiceBusProcessorClient processor = new ServiceBusClientBuilder()
                .fullyQualifiedNamespace(Config.FULLY_QUALIFIED_NAMESPACE)
                .credential(Config.CREDENTIAL)
                .sessionProcessor()
                .topicName(Config.TOPIC)
                .subscriptionName(Config.SUBSCRIPTION)
                .disableAutoComplete()
                .processMessage(TriggerWorkInterrupt::processMessage)
                .processError(TriggerWorkInterrupt::processError)
                .maxAutoLockRenewDuration(Duration.ofMinutes(5))
                .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
                .subQueue(SubQueue.NONE)
                .maxConcurrentSessions(2)
                .maxConcurrentCalls(1)
                .prefetchCount(0)
                .sessionIdleTimeout(SESSION_IDLE_TIMEOUT)
                .buildProcessorClient();
        processor.start();

        ServiceBusSenderClient azureSender = new ServiceBusClientBuilder()
                .credential(Config.CREDENTIAL)
                .fullyQualifiedNamespace(Config.FULLY_QUALIFIED_NAMESPACE)
                .sender()
                .topicName(Config.TOPIC)
                .buildClient();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            RUNNING.set(false);
            processor.stop();
            processor.close();
            azureSender.close();
        }));

        while (RUNNING.get()) {
            LOG.info("Starting repro attempt:\n\n");
            sendMessageForSession(azureSender, UUID.randomUUID().toString(), "intermittent");
            for (int i = 0; i < 11; i++) {
                sendMessageForSession(azureSender, String.valueOf(i), "constant");
            }

            // Wait a bit between messages just to avoid log spam
            Uninterruptibles.sleepUninterruptibly(SESSION_IDLE_TIMEOUT.multipliedBy(5));
        }
    }
}
