output "places_discovery_task_definition_arn" {
  description = "Task definition ARN for places-discovery."
  value       = aws_ecs_task_definition.places_discovery.arn
}

output "recommendation_task_definition_arn" {
  description = "Task definition ARN for recommendation."
  value       = aws_ecs_task_definition.recommendation.arn
}

output "travel_session_task_definition_arn" {
  description = "Task definition ARN for travel-session."
  value       = aws_ecs_task_definition.travel_session.arn
}
