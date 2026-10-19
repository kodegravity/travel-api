variable "region" {
  description = "AWS region for the bucket. CloudFront itself is global."
  type        = string
  default     = "us-east-2"
}

variable "bucket_name" {
  description = "Private S3 bucket holding the built web app."
  type        = string
  default     = "roam-ai-web-512383926725"
}

variable "github_repository" {
  description = "Repository whose main branch may deploy the web app."
  type        = string
  default     = "kodegravity/travel-ui"
}

variable "maps_key_secret_name" {
  description = "Secret with the Google Maps browser key, read at build time."
  type        = string
  default     = "ai-travel-app/google-maps-browser-key"
}
