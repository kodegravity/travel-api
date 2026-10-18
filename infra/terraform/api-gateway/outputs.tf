output "api_url" {
  description = "HTTPS base URL for the frontend (VITE_API_BASE_URL)."
  value       = aws_apigatewayv2_api.this.api_endpoint
}

output "vpc_link_security_group_id" {
  description = "Security group of the VPC link."
  value       = aws_security_group.vpc_link.id
}
