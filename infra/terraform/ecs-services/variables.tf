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

# Paid providers. Both are off by default; turning one on is a cost decision (budget: $30/month each).

variable "attraction_provider" {
  description = "places-discovery provider: \"mock\" (free) or \"google\" (paid; needs a value in the Google secret)."
  type        = string
  default     = "mock"

  validation {
    condition     = contains(["mock", "google"], var.attraction_provider)
    error_message = "attraction_provider must be \"mock\" or \"google\"."
  }
}

variable "ai_enabled" {
  description = "Enable OpenAI selection in recommendation (paid; needs a value in the OpenAI secret)."
  type        = bool
  default     = false
}

variable "google_places_max_calls_per_day" {
  description = "Hard cap on Google Places calls per UTC day per task. 190 keeps Google under $30/month."
  type        = number
  default     = 190
}

variable "google_routes_max_calls_per_day" {
  description = "Hard cap on Google Routes optimizations per UTC day per task. 20 stays inside the free 1,000/month."
  type        = number
  default     = 20
}

variable "google_place_photos_max_calls_per_day" {
  description = "Hard cap on Google Place Photos per UTC day per task (details page and cards share it). 30 stays inside the free 1,000/month."
  type        = number
  default     = 30
}

variable "ai_max_calls_per_day" {
  description = "Hard cap on OpenAI calls per UTC day per task. Past it, deterministic ranking is used."
  type        = number
  default     = 300
}

variable "ai_max_tokens" {
  description = "Maximum output tokens per OpenAI call."
  type        = number
  default     = 500
}

variable "google_places_api_key_secret_name" {
  description = "Secrets Manager secret holding the Google Places API key (see ../secrets)."
  type        = string
  default     = "ai-travel-app/google-places-api-key"
}

variable "openai_api_key_secret_name" {
  description = "Secrets Manager secret holding the OpenAI API key (see ../secrets)."
  type        = string
  default     = "ai-travel-app/openai-api-key"
}
