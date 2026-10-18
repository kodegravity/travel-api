variable "region" {
  description = "AWS region of the existing ALB and ECS services."
  type        = string
  default     = "us-east-2"
}

variable "vpc_id" {
  description = "VPC that contains the ALB and ECS tasks."
  type        = string
}

variable "listener_arn" {
  description = "ARN of the existing HTTP :80 listener on ai-travel-app-alb."
  type        = string
}

variable "alb_security_group_id" {
  description = "Security group attached to the ALB."
  type        = string
}

variable "recommendation_security_group_id" {
  description = "Security group of the recommendation ECS service."
  type        = string
}

variable "places_security_group_id" {
  description = "Security group of the places-discovery ECS service."
  type        = string
}

variable "recommendation_rule_priority" {
  description = "Listener rule priority for /internal/v1/recommendations*. Must be unique on the listener."
  type        = number
  default     = 20
}

variable "places_rule_priority" {
  description = "Listener rule priority for /internal/v1/places/*. Must be unique on the listener."
  type        = number
  default     = 30
}
