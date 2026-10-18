output "recommendation_target_group_arn" {
  description = "Use for the recommendation ECS service load balancer attachment."
  value       = aws_lb_target_group.recommendation.arn
}

output "places_target_group_arn" {
  description = "Use for the places-discovery ECS service load balancer attachment."
  value       = aws_lb_target_group.places.arn
}
