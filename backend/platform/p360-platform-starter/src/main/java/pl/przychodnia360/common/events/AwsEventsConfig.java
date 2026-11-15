package pl.przychodnia360.common.events;

import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transport zdarzeń przez AWS. Włączany właściwościami: {@code p360.events.sns.topic-arn} (publikacja) i
 * {@code p360.events.sqs.queue-url} (konsumpcja). {@code p360.events.aws.endpoint} wskazuje LocalStack
 * (wtedy używane są poświadczenia testowe); w AWS endpoint zostaje pusty, a poświadczenia pochodzą z roli zadania.
 */
@Configuration
class AwsEventsConfig {

    private static AwsCredentialsProvider credentials(String endpoint) {
        return endpoint.isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
    }

    @Bean
    @ConditionalOnExpression("!'${p360.events.sns.topic-arn:}'.isEmpty()")
    SnsClient snsClient(@Value("${p360.events.aws.endpoint:}") String endpoint,
            @Value("${p360.events.aws.region:eu-central-1}") String region) {
        var builder = SnsClient.builder().region(Region.of(region)).credentialsProvider(credentials(endpoint));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnExpression("!'${p360.events.sns.topic-arn:}'.isEmpty()")
    EventTransport snsEventTransport(SnsClient sns, @Value("${p360.events.sns.topic-arn}") String topicArn,
            JsonMapper mapper) {
        return new SnsEventTransport(sns, topicArn, mapper);
    }

    @Bean
    @ConditionalOnExpression("!'${p360.events.sqs.queue-url:}'.isEmpty()")
    SqsClient sqsClient(@Value("${p360.events.aws.endpoint:}") String endpoint,
            @Value("${p360.events.aws.region:eu-central-1}") String region) {
        var builder = SqsClient.builder().region(Region.of(region)).credentialsProvider(credentials(endpoint));
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnExpression("!'${p360.events.sqs.queue-url:}'.isEmpty()")
    SqsEventConsumer sqsEventConsumer(SqsClient sqs, @Value("${p360.events.sqs.queue-url}") String queueUrl,
            @Value("${spring.application.name}") String consumerName, JsonMapper mapper, InboxGuard inbox,
            PlatformTransactionManager transactionManager, List<EventHandler> handlers, EventTracing tracing,
            @Value("${p360.events.sqs.wait-seconds:10}") int waitSeconds) {
        return new SqsEventConsumer(sqs, queueUrl, consumerName, mapper, inbox, transactionManager, handlers,
                tracing, waitSeconds);
    }
}
