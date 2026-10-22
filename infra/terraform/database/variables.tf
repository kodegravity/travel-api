variable "region" {
  description = "AWS region."
  type        = string
  default     = "us-east-2"
}

variable "name" {
  description = "Identifier of the database instance, its subnet group, and its security group."
  type        = string
  default     = "roam-db"
}

variable "subnet_ids" {
  description = "Subnets for the database: the ECS service subnets, in at least two Availability Zones."
  type        = list(string)
}

variable "client_security_group_ids" {
  description = "Security groups of the tasks that may connect (travel-session only)."
  type        = list(string)
}

variable "task_role_name" {
  description = "Existing IAM role the travel-session ECS task runs as. Gets read access to the database secret."
  type        = string
  default     = "ai-travel-app-ecs-task"
}

variable "engine_version" {
  description = "PostgreSQL major version. Minor versions are upgraded automatically in the maintenance window."
  type        = string
  default     = "17"
}

variable "instance_class" {
  description = "Instance size. db.t4g.micro (2 vCPU burstable, 1 GB) is plenty for profiles."
  type        = string
  default     = "db.t4g.micro"
}

variable "storage_gb" {
  description = "Allocated gp3 storage (20 GB is the minimum)."
  type        = number
  default     = 20
}
