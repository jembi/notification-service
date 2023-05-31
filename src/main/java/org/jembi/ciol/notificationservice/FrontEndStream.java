package org.jembi.ciol.notificationservice;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jembi.ciol.AppConfig;
import org.jembi.ciol.shared.serdes.JsonPojoDeserializer;
import org.jembi.ciol.shared.serdes.JsonPojoSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jembi.ciol.models.*;

public class FrontEndStream {

    private static final Logger LOGGER = LogManager.getLogger(FrontEndStream.class);
    private KafkaStreams notificationKafkaStreams;

    FrontEndStream() {
        LOGGER.info("FrontEndStream constructor");
    }

    void sendNotification(ActorSystem<Void> system,
                          final ActorRef<BackEnd.Event> backEnd,
                          String key,
                          NotificationMessage notification) {
        LOGGER.debug("{} {}", key, notification);
        CompletionStage<BackEnd.EventSendNotificationRsp> result =
                AskPattern.ask(
                        backEnd,
                        replyTo -> new BackEnd.EventSendNotification(key, notification, replyTo),
                        java.time.Duration.ofSeconds(30),
                        system.scheduler());
        var completableFuture = result.toCompletableFuture();
        try {
            var reply = completableFuture.get(35, TimeUnit.SECONDS);
            LOGGER.info("Notification processed");
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOGGER.error(e.getLocalizedMessage(), e);
        }
    }

    public void open(final ActorSystem<Void> system,
                     final ActorRef<BackEnd.Event> backEnd) {
        LOGGER.info("Notification Service Stream Processor");

        final Properties props = loadConfig();
        final Serde<String> stringSerde = Serdes.String();

        final Serializer<NotificationMessage> pojoSerializer = new JsonPojoSerializer<>();
        final Deserializer<NotificationMessage> pojoDeserializer = new JsonPojoDeserializer<>();
        final Map<String, Object> serdeProps = new HashMap<>();
        serdeProps.put(JsonPojoDeserializer.CLASS_TAG, NotificationMessage.class);
        pojoDeserializer.configure(serdeProps, false);
        final Serde<NotificationMessage> notificationMessageSerde = Serdes.serdeFrom(pojoSerializer, pojoDeserializer);

        final StreamsBuilder streamsBuilder = new StreamsBuilder();
        final KStream<String, NotificationMessage> notificationKStream = streamsBuilder.stream(
                GlobalConstants.TOPIC_NOTIFICATIONS,
                Consumed.with(stringSerde, notificationMessageSerde));

        notificationKStream.foreach((key, notification) -> sendNotification(system, backEnd, key, notification));

        notificationKafkaStreams = new KafkaStreams(streamsBuilder.build(), props);
        notificationKafkaStreams.cleanUp();
        notificationKafkaStreams.start();
        LOGGER.info("KafkaStreams started");
    }

    public void close() {
        LOGGER.warn("Stream closed");
        notificationKafkaStreams.close();
    }

    private Properties loadConfig() {
        final Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, AppConfig.KAFKA_APPLICATION_ID);
        props.put(StreamsConfig.CLIENT_ID_CONFIG, AppConfig.KAFKA_CLIENT_ID);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, AppConfig.KAFKA_BOOTSTRAP_SERVERS);
        return props;
    }
}
