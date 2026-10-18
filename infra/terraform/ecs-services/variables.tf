variable "region" {
  description = "AWS region of the ECS cluster."
  type        = string
  default     = "us-east-2"
}

variable "cluster_name" {
  description = "Existing ECS cluster that runs the services."
  type        = string
  default     = "ai-travel-app"
}

variable "ecr_registry" {
  description = "ECR registry host, e.g. <account>.dkr.ecr.us-east-2.amazonaws.com."
  type        = string
}

variable "execution_role_arn" {
  description = "ECS task execution role shared by all services."
  type        = string
}

variable "travel_session_task_role_arn" {
  description = "Task role for travel-session."
  type        = string
}

variable "subnet_ids" {
  description = "Subnets for the Fargate tasks."
  type        = list(string)
}

variable "okta_issuer" {
  description = "Okta issuer URI passed to every service."
  type        = string
}

variable "okta_audience" {
  description = "Okta audience passed to every service."
  type        = string
}

variable "image_tag_places_discovery" {
  description = "ECR image tag for places-discovery."
  type        = string
}

variable "image_tag_recommendation" {
  description = "ECR image tag for recommendation."
  type        = string
}

variable "image_tag_travel_session" {
  description = "ECR image tag for travel-session."
  type        = string
}

variable "places_security_group_id" {
  description = "Security group of places-discovery."
  type        = string
}

variable "recommendation_security_group_id" {
  description = "Security group of recommendation."
  type        = string
}

variable "travel_session_security_group_id" {
  description = "Security group of travel-session."
  type        = string
}

variable "places_target_group_arn" {
  description = "Target group of places-discovery (internal ALB route)."
  type        = string
}

variable "recommendation_target_group_arn" {
  description = "Target group of recommendation (internal ALB route)."
  type        = string
}

variable "travel_session_target_group_arn" {
  description = "Target group of travel-session (public ALB default route)."
  type        = string
}

variable "cors_allowed_origins" {
  description = "Allowed browser origins for travel-session."
  type        = string
  default     = "http://localhost:5173"
}

variable "desired_count" {
  description = "Desired task count per service."
  type        = number
  default     = 1
}

variable "service_connect_namespace_arn" {
  description = "Cloud Map namespace used by Service Connect."
  type        = string
}

variable "session_table_name" {
  description = "DynamoDB session table (see ../session-store)."
  type        = string
  default     = "roam-travel-sessions"
}
