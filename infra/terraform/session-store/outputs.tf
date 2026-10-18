output "table_name" {
  description = "Set as TRAVEL_SESSION_TABLE on the travel-session task."
  value       = aws_dynamodb_table.sessions.name
}

output "task_role_arn" {
  description = "Set as taskRoleArn on the travel-session task definition."
  value       = aws_iam_role.travel_session_task.arn
}
