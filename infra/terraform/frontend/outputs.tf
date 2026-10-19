output "url" {
  description = "Public address of the web app."
  value       = "https://${aws_cloudfront_distribution.web.domain_name}"
}

output "bucket_name" {
  value = aws_s3_bucket.web.bucket
}

output "distribution_id" {
  value = aws_cloudfront_distribution.web.id
}

output "deploy_role_arn" {
  value = aws_iam_role.deploy.arn
}
