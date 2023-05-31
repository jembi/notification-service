package org.jembi.ciol.notificationservice;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jembi.ciol.AppConfig;
import org.jembi.ciol.RestConfig;
import org.jembi.ciol.models.*;

import java.io.File;
import java.io.IOException;

import static org.jembi.ciol.shared.utils.AppUtils.OBJECT_MAPPER;

public class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);
    private HttpServer httpServer;
    public static RestConfig restConfig = null;

    private Main() {
    }

    public static void main(final String[] args) {
        new Main().run();
    }

    public Behavior<Void> create() {
        return Behaviors.setup(
                context -> {
                    ActorRef<BackEnd.Event> backEnd = context.spawn(BackEnd.create(), "BackEnd");
                    context.watch(backEnd);
                    final FrontEndStream frontEndStream = new FrontEndStream();
                    frontEndStream.open(context.getSystem(), backEnd);
                    httpServer = new HttpServer();
                    httpServer.open(context.getSystem(), backEnd);
                    return Behaviors.receive(Void.class)
                                    .onSignal(akka.actor.typed.Terminated.class, sig -> Behaviors.stopped())
                                    .build();
                });
    }

    private void run() {
        LOGGER.info("KAFKA: {} {} {} {}",
                    AppConfig.KAFKA_BOOTSTRAP_SERVERS,
                    AppConfig.KAFKA_APPLICATION_ID,
                    AppConfig.KAFKA_CLIENT_ID,
                    GlobalConstants.TOPIC_NOTIFICATIONS);
        try {
            restConfig  = OBJECT_MAPPER.readValue(new File("/app/conf/myConfig.json") , RestConfig.class);
        } catch (IOException e) {
            LOGGER.warn("No configuration file");
            restConfig = null;
        }
        if (restConfig != null){
            EmailService.getInstance().init();
        }
        ActorSystem.create(this.create(), "Main");
    }
}
