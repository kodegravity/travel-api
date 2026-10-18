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
  description = "Existing IAM role the travel-session ECS task runs as (its taskRoleArn). Gets DynamoDB access."
  type        = string
  default     = "ai-travel-app-ecs-task"
}
