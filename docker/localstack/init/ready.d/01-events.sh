#!/bin/sh
# Zasoby zdarzeń dla lokalnego środowiska (LocalStack): jeden temat na usługę, jedna kolejka na konsumenta, DLQ.
# Wykonywane przy starcie LocalStack (ready.d). Nazwy zgodne z docs/adr/ADR-M03 (SNS + SQS z outboxem).
set -eu

REGION="${AWS_DEFAULT_REGION:-eu-central-1}"
create_topic() {
  awslocal sns create-topic --name "$1" --region "$REGION" --query TopicArn --output text
}

# Jeden temat na usługę-producenta (ADR-M03): iam publikuje UserDeactivated, scheduling fakty wizyt, catalog i patient
# własne fakty.
IAM_TOPIC_ARN=$(create_topic p360-iam-events)
SCHEDULING_TOPIC_ARN=$(create_topic p360-scheduling-events)
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

# Konsumenci: notification (planowany) czyta fakty wizyt ze scheduling; catalog, patient i scheduling czytają
# UserDeactivated (odwołanie dostępu, ADR-M04) z tematu iam; scheduling czyta też fakty katalogu i pacjentów (kopie
# *_ref). Subskrypcja scheduling-inbox do tematu patient jest ostatnia: sprawdza ją healthcheck (docker-compose.yml).
create_consumer_queue p360-notification-events "$SCHEDULING_TOPIC_ARN"
create_consumer_queue p360-catalog-inbox "$IAM_TOPIC_ARN"
create_consumer_queue p360-patient-inbox "$IAM_TOPIC_ARN"
create_consumer_queue p360-scheduling-inbox "$IAM_TOPIC_ARN" "$CATALOG_TOPIC_ARN" "$PATIENT_TOPIC_ARN"

echo "Zdarzenia: tematy iam, scheduling, catalog, patient; kolejki notification-events, catalog-inbox, patient-inbox, scheduling-inbox (+ DLQ)"
