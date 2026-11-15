#!/bin/sh
# Zasoby zdarzeń dla lokalnego środowiska (LocalStack): jeden temat na usługę, jedna kolejka na konsumenta, DLQ.
# Wykonywane przy starcie LocalStack (ready.d). Nazwy zgodne z docs/adr/ADR-M03 (SNS + SQS z outboxem).
set -eu

REGION="${AWS_DEFAULT_REGION:-eu-central-1}"
create_topic() {
  awslocal sns create-topic --name "$1" --region "$REGION" --query TopicArn --output text
}

# Jeden temat na usługę-producenta (ADR-M03). clinic-core (iam) publikuje UserDeactivated, catalog i patient własne fakty.
TOPIC_ARN=$(create_topic p360-clinic-core-events)
CATALOG_TOPIC_ARN=$(create_topic p360-catalog-events)
PATIENT_TOPIC_ARN=$(create_topic p360-patient-events)

# create_consumer_queue <kolejka> <temat...>: kolejka z DLQ subskrybowana do jednego lub kilku tematów.
create_consumer_queue() {
  name="$1"
  shift
  dlq_url=$(awslocal sqs create-queue --queue-name "$name-dlq" --region "$REGION" --query QueueUrl --output text)
  dlq_arn=$(awslocal sqs get-queue-attributes --queue-url "$dlq_url" --attribute-names QueueArn \
    --region "$REGION" --query Attributes.QueueArn --output text)
  queue_url=$(awslocal sqs create-queue --queue-name "$name" --region "$REGION" \
    --attributes "{\"VisibilityTimeout\":\"30\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$dlq_arn\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\"}" \
    --query QueueUrl --output text)
  queue_arn=$(awslocal sqs get-queue-attributes --queue-url "$queue_url" --attribute-names QueueArn \
    --region "$REGION" --query Attributes.QueueArn --output text)
  for topic_arn in "$@"; do
    awslocal sns subscribe --topic-arn "$topic_arn" --protocol sqs --notification-endpoint "$queue_arn" \
      --attributes RawMessageDelivery=true --region "$REGION" > /dev/null
  done
}

# Konsumenci: notification (planowany) czyta zdarzenia clinic-core; catalog i patient czytają UserDeactivated (odwołanie
# dostępu, ADR-M04) z tematu clinic-core; scheduling (clinic-core) czyta fakty katalogu i pacjentów (kopie *_ref).
create_consumer_queue p360-notification-events "$TOPIC_ARN"
create_consumer_queue p360-catalog-inbox "$TOPIC_ARN"
create_consumer_queue p360-patient-inbox "$TOPIC_ARN"
create_consumer_queue p360-scheduling-inbox "$CATALOG_TOPIC_ARN" "$PATIENT_TOPIC_ARN"

echo "Zdarzenia: tematy clinic-core, catalog, patient; kolejki notification-events, catalog-inbox, patient-inbox, scheduling-inbox (+ DLQ)"
