package org.jembi.ciol.notificationservice;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.javadsl.*;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jembi.ciol.RestConfig;
import org.jembi.ciol.models.NotificationMessage;

import java.io.File;
import java.io.IOException;

import static org.jembi.ciol.shared.utils.AppUtils.OBJECT_MAPPER;

public class BackEnd extends AbstractBehavior<BackEnd.Event> {

    private static final Logger LOGGER = LogManager.getLogger(BackEnd.class);

    private BackEnd(ActorContext<Event> context) {
        super(context);
        context.getSystem()
                .dispatchers()
                .lookup(DispatcherSelector.fromConfig("my-blocking-dispatcher"));
    }

    public static Behavior<BackEnd.Event> create() {
        return Behaviors.setup(BackEnd::new);
    }

    @Override
    public Receive<Event> createReceive() {
        ReceiveBuilder<BackEnd.Event> builder = newReceiveBuilder();
        return builder
                .onMessage(EventSendNotification.class, this::eventSendNotificationHandler)
                .onMessage(EventSetConfig.class, this::eventSetConfigHandler)
                .build();
    }

    private Behavior<Event> eventSendNotificationHandler(EventSendNotification notification) {
        LOGGER.info("{}/{}", notification.key, notification.notificationMessage);
        var notificationMessages = notification.notificationMessage.messageList();
        LOGGER.debug("{}", notificationMessages);

        if (notificationMessages.size() > 0) {
            LOGGER.debug("SEND ERROR MESSAGES");
            notificationMessages.forEach((message) -> {
                LOGGER.debug("{}", message);
                EmailService.getInstance()
                        .sendErrorEmail(notification.notificationMessage.source(),
                                message);
            });
        }
        notification.replyTo.tell(EventSendNotificationRsp.INSTANCE);
        return Behaviors.same();
    }

    private Behavior<Event> eventSetConfigHandler(EventSetConfig event) {
        LOGGER.info("{}", event.restConfig);

        if ("notification_service".equals(event.restConfig.appID())) {
            try {
                Main.restConfig = event.restConfig;
                LOGGER.debug("{}",Main.restConfig);
                OBJECT_MAPPER.writeValue(new File("/app/conf/myConfig.json"), Main.restConfig);
                EmailService.getInstance().init();
                event.replyTo.tell(new EventSetConfigRsp(StatusCodes.OK));
            } catch (IOException e) {
                LOGGER.error(e.getLocalizedMessage(), e);
                event.replyTo.tell(new EventSetConfigRsp(StatusCodes.IM_A_TEAPOT));
            }
        } else {
            event.replyTo.tell(new EventSetConfigRsp(StatusCodes.IM_A_TEAPOT));
        }

        return Behaviors.same();
    }

    public enum EventSendNotificationRsp {
        INSTANCE
    }

    interface Event {
    }

    interface EventResponse {
    }

    public record EventSendNotification(
            String key,
            NotificationMessage notificationMessage,
            ActorRef<EventSendNotificationRsp> replyTo) implements Event {
    }

    public record EventSetConfig(
            RestConfig restConfig,
            ActorRef<EventSetConfigRsp> replyTo) implements Event {
    }

    public record EventSetConfigRsp(StatusCode responseCode) {
    }
}
