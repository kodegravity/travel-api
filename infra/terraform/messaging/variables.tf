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
  description = "Existing SNS topic for alarm notifications. Empty: use this module's alerts topic (alarm_email)."
  type        = string
  default     = ""
}

variable "alarm_email" {
  description = "Email for alarm notifications; AWS sends a confirmation link first. Empty: no email subscription."
  type        = string
  default     = ""
}
