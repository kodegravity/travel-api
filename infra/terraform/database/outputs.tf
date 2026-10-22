output "jdbc_url" {
  description = "Set TRAVEL_POSTGRES_URL to this."
  value       = "jdbc:postgresql://${aws_db_instance.this.address}:${aws_db_instance.this.port}/${aws_db_instance.this.db_name}?sslmode=require"
}

output "secret_arn" {
  description = "RDS-managed master user secret. Set TRAVEL_POSTGRES_SECRET_ID to this."
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "security_group_id" {
  value = aws_security_group.db.id
}
