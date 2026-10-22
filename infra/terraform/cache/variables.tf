variable "region" {
  description = "AWS region."
  type        = string
  default     = "us-east-2"
}

variable "name" {
  description = "Name of the cache and its security group."
  type        = string
  default     = "roam-cache"
}

variable "subnet_ids" {
  description = "Subnets for the cache endpoints: the ECS service subnets, in two or three Availability Zones."
  type        = list(string)
}

variable "client_security_group_ids" {
  description = "Security groups of the tasks that may connect (places-discovery and recommendation)."
  type        = list(string)
}

variable "max_data_storage_gb" {
  description = "Hard ceiling on stored data. The service bills at least 100 MB; the app stores far less."
  type        = number
  default     = 1
}

variable "max_ecpu_per_second" {
  description = "Hard ceiling on throughput, so a runaway loop cannot run up the bill."
  type        = number
  default     = 1000
}
