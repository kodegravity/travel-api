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

variable "github_oidc_subject_prefix" {
  description = <<-EOT
    OIDC subject prefix of the repository whose main branch may deploy the web app. This account uses GitHub's
    immutable subjects (owner and repository IDs), shown by: gh api repos/kodegravity/travel-ui/actions/oidc/customization/sub
  EOT
  type        = string
  default     = "repo:kodegravity@260714910/travel-ui@1424879526"
}

variable "maps_key_secret_name" {
  description = "Secret with the Google Maps browser key, read at build time."
  type        = string
  default     = "ai-travel-app/google-maps-browser-key"
}
