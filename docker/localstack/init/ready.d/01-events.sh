#!/bin/sh
# Zasoby zdarzeń dla lokalnego środowiska (LocalStack): jeden temat na usługę, jedna kolejka na konsumenta, DLQ.
# Wykonywane przy starcie LocalStack (ready.d). Nazwy zgodne z docs/adr/ADR-M03 (SNS + SQS z outboxem).
set -eu

REGION="${AWS_DEFAULT_REGION:-eu-central-1}"
TOPIC_ARN=$(awslocal sns create-topic --name p360-clinic-core-events --region "$REGION" --query TopicArn --output text)

create_consumer_queue() {
  name="$1"
  dlq_url=$(awslocal sqs create-queue --queue-name "$name-dlq" --region "$REGION" --query QueueUrl --output text)
  dlq_arn=$(awslocal sqs get-queue-attributes --queue-url "$dlq_url" --attribute-names QueueArn \
    --region "$REGION" --query Attributes.QueueArn --output text)
  queue_url=$(awslocal sqs create-queue --queue-name "$name" --region "$REGION" \
    --attributes "{\"VisibilityTimeout\":\"30\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$dlq_arn\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\"}" \
    --query QueueUrl --output text)
  queue_arn=$(awslocal sqs get-queue-attributes --queue-url "$queue_url" --attribute-names QueueArn \
    --region "$REGION" --query Attributes.QueueArn --output text)
  awslocal sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs --notification-endpoint "$queue_arn" \
    --attributes RawMessageDelivery=true --region "$REGION" > /dev/null
}

create_consumer_queue p360-notification-events

echo "Zdarzenia: temat $TOPIC_ARN, kolejka p360-notification-events (+ DLQ)"
