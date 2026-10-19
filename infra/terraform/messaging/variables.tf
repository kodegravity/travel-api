variable "region" {
  description = "AWS region; the same as the rest of the app."
  type        = string
  default     = "us-east-2"
}

variable "name_prefix" {
  description = "Prefix for the topic, queues, alarms, and role."
  type        = string
  default     = "roam"
}

variable "travel_session_task_role_name" {
  description = "Existing ECS task role of travel-session: gets publish on the topic and receive on the notification queue."
  type        = string
  default     = "ai-travel-app-ecs-task"
}

variable "max_receive_count" {
  description = "Deliveries before a message moves to its dead-letter queue."
  type        = number
  default     = 3
}

variable "alarm_topic_arn" {
  description = "Optional SNS topic for alarm notifications (e.g. one with your email). Empty: alarms show in the console only."
  type        = string
  default     = ""
}
