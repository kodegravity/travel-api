output "openai_api_key_secret_arn" {
  value = aws_secretsmanager_secret.openai_api_key.arn
}

output "google_places_api_key_secret_arn" {
  value = aws_secretsmanager_secret.google_places_api_key.arn
}

output "google_maps_browser_key_secret_name" {
  value = aws_secretsmanager_secret.google_maps_browser_key.name
}
