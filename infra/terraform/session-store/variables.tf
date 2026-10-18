variable "region" {
  description = "AWS region of the ECS services."
  type        = string
  default     = "us-east-2"
}

variable "table_name" {
  description = "DynamoDB table for Active Travel Mode sessions. Must match TRAVEL_SESSION_TABLE."
  type        = string
  default     = "roam-travel-sessions"
}

variable "task_role_name" {
  description = "IAM role the travel-session ECS task runs as (taskRoleArn)."
  type        = string
  default     = "ai-travel-app-travel-session-task"
}
