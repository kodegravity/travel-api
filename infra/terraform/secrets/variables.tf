variable "region" {
  description = "AWS region for the secrets."
  type        = string
  default     = "us-east-2"
}

variable "execution_role_name" {
  description = "ECS task execution role that reads the secrets at task start."
  type        = string
  default     = "ai-travel-app-ecs-execution"
}
