# Event-driven messaging: travel-session publishes travel events to one SNS topic; each consumer reads only the
# event types it handles from its own SQS queue, with a dead-letter queue behind it.
# Cost: SNS and SQS stay inside the always-free tier (1M publishes, 1M requests a month); about $0.40/month for the
# four alarms. No KMS cost: AWS-managed keys only.

data "aws_iam_role" "travel_session_task" {
  name = var.travel_session_task_role_name
}

resource "aws_sns_topic" "travel_events" {
  name              = "${var.name_prefix}-travel-events"
  kms_master_key_id = "alias/aws/sns"
}

locals {
  # Which event types each consumer receives (SNS filter policies on the eventType message attribute).
  consumers = {
    recommendation = ["TravelSessionStarted"]
    notification   = ["RecommendationsReady", "NearbyEventDiscovered", "TravelSessionEnded"]
  }
}

resource "aws_sqs_queue" "dlq" {
  for_each                  = local.consumers
  name                      = "${var.name_prefix}-${each.key}-events-dlq"
  message_retention_seconds = 1209600 # 14 days to inspect and redrive
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "events" {
  for_each                   = local.consumers
  name                       = "${var.name_prefix}-${each.key}-events"
  visibility_timeout_seconds = 60
  receive_wait_time_seconds  = 20     # long polling: fewer, cheaper requests
  message_retention_seconds  = 345600 # 4 days
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq[each.key].arn
    maxReceiveCount     = var.max_receive_count
  })
}

resource "aws_sqs_queue_redrive_allow_policy" "dlq" {
  for_each  = local.consumers
  queue_url = aws_sqs_queue.dlq[each.key].id
  redrive_allow_policy = jsonencode({
    redrivePermission = "byQueue"
    sourceQueueArns   = [aws_sqs_queue.events[each.key].arn]
  })
}

# Only this topic may send to the queues.
resource "aws_sqs_queue_policy" "from_topic" {
  for_each  = local.consumers
  queue_url = aws_sqs_queue.events[each.key].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowTravelEventsTopic"
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.events[each.key].arn
      Condition = { ArnEquals = { "aws:SourceArn" = aws_sns_topic.travel_events.arn } }
    }]
  })
}

resource "aws_sns_topic_subscription" "events" {
  for_each             = local.consumers
  topic_arn            = aws_sns_topic.travel_events.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.events[each.key].arn
  raw_message_delivery = true # the queue body is the event envelope itself
  filter_policy_scope  = "MessageAttributes"
  filter_policy        = jsonencode({ eventType = each.value })
  depends_on           = [aws_sqs_queue_policy.from_topic]
}

# Least privilege: travel-session publishes to the topic and consumes the notification queue.
resource "aws_iam_role_policy" "travel_session_messaging" {
  name = "travel-events-messaging"
  role = data.aws_iam_role.travel_session_task.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "PublishTravelEvents"
        Effect   = "Allow"
        Action   = "sns:Publish"
        Resource = aws_sns_topic.travel_events.arn
      },
      {
        Sid      = "ConsumeNotificationEvents"
        Effect   = "Allow"
        Action   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility", "sqs:GetQueueAttributes", "sqs:GetQueueUrl"]
        Resource = aws_sqs_queue.events["notification"].arn
      },
    ]
  })
}

# recommendation-service had no task role; this one can only consume its own queue.
resource "aws_iam_role" "recommendation_task" {
  name = "${var.name_prefix}-recommendation-task"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "recommendation_messaging" {
  name = "travel-events-messaging"
  role = aws_iam_role.recommendation_task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "ConsumeRecommendationEvents"
      Effect   = "Allow"
      Action   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility", "sqs:GetQueueAttributes", "sqs:GetQueueUrl"]
      Resource = aws_sqs_queue.events["recommendation"].arn
    }]
  })
}

# Monitoring: anything in a dead-letter queue, or messages waiting too long (a consumer is down or failing).
resource "aws_cloudwatch_metric_alarm" "dlq_not_empty" {
  for_each            = local.consumers
  alarm_name          = "${var.name_prefix}-${each.key}-events-dlq-not-empty"
  alarm_description   = "Messages failed ${var.max_receive_count} times and reached the ${each.key} dead-letter queue."
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  dimensions          = { QueueName = aws_sqs_queue.dlq[each.key].name }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_topic_arn == "" ? [] : [var.alarm_topic_arn]
}

resource "aws_cloudwatch_metric_alarm" "oldest_message_age" {
  for_each            = local.consumers
  alarm_name          = "${var.name_prefix}-${each.key}-events-backlog"
  alarm_description   = "The oldest ${each.key} message has waited over 5 minutes: the consumer is down or failing."
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateAgeOfOldestMessage"
  dimensions          = { QueueName = aws_sqs_queue.events[each.key].name }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 2
  threshold           = 300
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.alarm_topic_arn == "" ? [] : [var.alarm_topic_arn]
}
