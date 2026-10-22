output "endpoint_address" {
  description = "Primary endpoint host. Set REDIS_HOST to this, with REDIS_SSL=true."
  value       = aws_elasticache_serverless_cache.this.endpoint[0].address
}

output "endpoint_port" {
  description = "Primary endpoint port."
  value       = aws_elasticache_serverless_cache.this.endpoint[0].port
}

output "security_group_id" {
  value = aws_security_group.cache.id
}
