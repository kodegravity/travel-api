output "table_name" {
  description = "Set as TRAVEL_SESSION_TABLE on the travel-session task."
  value       = aws_dynamodb_table.sessions.name
}

output "task_role_arn" {
  description = "Task role that received DynamoDB access; must be the travel-session taskRoleArn."
  value       = data.aws_iam_role.travel_session_task.arn
}
