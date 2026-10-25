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
  description = "Hard cap on Google Places calls per UTC day, shared by all tasks when Redis is on. 180 keeps Google near $16/month, so the whole project stays under $100/month with Redis and Postgres."
  type        = number
  default     = 180
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

variable "messaging_enabled" {
  description = "Event-driven messaging (SNS/SQS from ../messaging). Off until that module is applied and its outputs are set below."
  type        = bool
  default     = false
}

variable "messaging_topic_arn" {
  description = "travel events topic ARN (output topic_arn of ../messaging). Required when messaging_enabled."
  type        = string
  default     = ""
}

variable "notification_queue_name" {
  description = "Queue travel-session consumes (output notification_queue_name of ../messaging)."
  type        = string
  default     = "roam-notification-events"
}

variable "recommendation_queue_name" {
  description = "Queue recommendation consumes (output recommendation_queue_name of ../messaging)."
  type        = string
  default     = "roam-recommendation-events"
}

variable "recommendation_task_role_arn" {
  description = "Task role for recommendation (output recommendation_task_role_arn of ../messaging). Empty: no task role, as before."
  type        = string
  default     = ""
}

variable "events_provider" {
  description = "Live events in places-discovery: \"none\" (off), \"mock\" (sample events), or \"ticketmaster\" (free; needs a value in the Ticketmaster secret)."
  type        = string
  default     = "none"

  validation {
    condition     = contains(["none", "mock", "ticketmaster"], var.events_provider)
    error_message = "events_provider must be \"none\", \"mock\", or \"ticketmaster\"."
  }
}

variable "events_max_calls_per_day" {
  description = "Hard cap on Ticketmaster calls per UTC day per task. The free tier allows 5,000."
  type        = number
  default     = 1000
}

variable "chat_ai_max_calls_per_day" {
  description = "Hard cap on OpenAI replies for the Ask ROAM chat per UTC day per task. 200 is about $6/month at most on gpt-4o-mini."
  type        = number
  default     = 200
}

variable "ai_timeout" {
  description = "Longest wait for one OpenAI reply before falling back to ranking. On 0.25 vCPU the first calls after a restart need more than 4s."
  type        = string
  default     = "10s"
}

variable "live_ai_max_calls_per_day" {
  description = "Hard cap on OpenAI calls for live event alerts per UTC day per task. 100 is about $1/month at most on gpt-4o-mini."
  type        = number
  default     = 100
}

variable "ticketmaster_api_key_json_key" {
  description = "Field inside the Ticketmaster secret's JSON that holds the key."
  type        = string
  default     = "ticketmaster-api-key"
}

variable "ticketmaster_api_key_secret_name" {
  description = "Secrets Manager secret holding the Ticketmaster API key (see ../secrets)."
  type        = string
  default     = "ai-travel-app/ticketmaster-api-key"
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

variable "redis_enabled" {
  description = "Share daily call caps and small caches through the Valkey cache (infra/terraform/cache)."
  type        = bool
  default     = false
}

variable "redis_host" {
  description = "Valkey primary endpoint (cache module output endpoint_address). Used when redis_enabled."
  type        = string
  default     = ""
}

variable "redis_port" {
  description = "Valkey primary endpoint port."
  type        = number
  default     = 6379
}

variable "postgres_enabled" {
  description = "Keep traveler profiles in PostgreSQL (infra/terraform/database) instead of task memory."
  type        = bool
  default     = false
}

variable "postgres_url" {
  description = "JDBC URL (database module output jdbc_url). Used when postgres_enabled."
  type        = string
  default     = ""
}

variable "postgres_secret_arn" {
  description = "RDS-managed master user secret (database module output secret_arn). The task reads it at connect time."
  type        = string
  default     = ""
}

variable "enable_execute_command" {
  description = "ECS Exec into travel-session (debugging). Needs a writable root filesystem, so turning it on drops readonlyRootFilesystem for that container."
  type        = bool
  default     = false
}

variable "push_enabled" {
  description = "Web Push from travel-session. Needs the VAPID key pair (docs/WEB_PUSH.md)."
  type        = bool
  default     = false
}

variable "push_vapid_public_key" {
  description = "Web Push VAPID public key, base64url. Not secret."
  type        = string
  default     = ""
}

variable "push_vapid_private_key_parameter_arn" {
  description = "ARN of the SSM SecureString holding the VAPID private key (output of ../secrets)."
  type        = string
  default     = ""
}

variable "push_subject" {
  description = "VAPID contact (mailto: or https:) push services can use to reach the operator."
  type        = string
  default     = "mailto:admin@localhost"
}
