output "topic_arn" {
  description = "TRAVEL_MESSAGING_TOPIC_ARN for travel-session."
  value       = aws_sns_topic.travel_events.arn
}

output "notification_queue_name" {
  value = aws_sqs_queue.events["notification"].name
}

output "recommendation_queue_name" {
  value = aws_sqs_queue.events["recommendation"].name
}

output "recommendation_task_role_arn" {
  value = aws_iam_role.recommendation_task.arn
}
