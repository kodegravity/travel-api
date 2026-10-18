variable "region" {
  description = "AWS region of the ECS services."
  type        = string
  default     = "us-east-2"
}

variable "api_name" {
  description = "Name of the HTTP API."
  type        = string
  default     = "roam-ai-api"
}

variable "subnet_ids" {
  description = "Subnets for the VPC link. Use the same subnets as the ECS tasks."
  type        = list(string)
}

variable "travel_session_security_group_id" {
  description = "Security group of travel-session; gets an ingress rule from the VPC link."
  type        = string
}

variable "service_connect_namespace_id" {
  description = "Cloud Map namespace ID (ns-...) used by ECS Service Connect."
  type        = string
}

variable "travel_session_discovery_name" {
  description = "Cloud Map service name of travel-session (its Service Connect discovery_name)."
  type        = string
  default     = "travel-session"
}

variable "okta_issuer" {
  description = "Okta authorization server issuer, e.g. https://<domain>.okta.com/oauth2/default."
  type        = string
}

variable "okta_audience" {
  description = "Expected aud claim in Okta access tokens."
  type        = string
}

variable "throttling_rate_limit" {
  description = "Steady-state requests per second across the API. Protects the paid API caps from bursts."
  type        = number
  default     = 5
}

variable "throttling_burst_limit" {
  description = "Maximum burst of requests."
  type        = number
  default     = 10
}

variable "access_log_retention_days" {
  description = "Retention of API access logs in CloudWatch."
  type        = number
  default     = 14
}
