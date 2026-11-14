package pl.przychodnia360.common.events;

import java.util.Map;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publikacja kopert do tematu SNS (jeden temat na usługę). Ciało to koperta w JSON; atrybuty {@code type}
 * i {@code tenantId} umożliwiają filtrowanie subskrypcji bez parsowania ciała. Błąd SDK przerywa wysyłkę,
 * a {@link OutboxRelay} ponowi próbę.
 */
public class SnsEventTransport implements EventTransport {

    private final SnsClient sns;
    private final String topicArn;
    private final JsonMapper mapper;

    public SnsEventTransport(SnsClient sns, String topicArn, JsonMapper mapper) {
        this.sns = sns;
        this.topicArn = topicArn;
        this.mapper = mapper;
    }

    @Override
    public void publish(EventEnvelope envelope) {
        sns.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(mapper.writeValueAsString(envelope))
                .messageAttributes(Map.of(
                        "type", attribute(envelope.type()),
                        "tenantId", attribute(String.valueOf(envelope.tenantId()))))
                .build());
    }

    private static MessageAttributeValue attribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }
}
