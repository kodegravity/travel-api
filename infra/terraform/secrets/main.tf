# Secret values are NOT managed here so they never enter Terraform state.
# Set them with: aws secretsmanager put-secret-value --secret-id <name> --secret-string "<key>"

resource "aws_secretsmanager_secret" "openai_api_key" {
  name        = "ai-travel-app/openai-api-key"
  description = "OpenAI API key for the recommendation service."
}

resource "aws_secretsmanager_secret" "google_places_api_key" {
  name        = "ai-travel-app/google-places-api-key"
  description = "Google Places API key for the places-discovery service."
}

resource "aws_iam_role_policy" "ecs_execution_read_secrets" {
  name = "read-ai-travel-app-api-keys"
  role = var.execution_role_name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["secretsmanager:GetSecretValue"]
      Resource = [
        aws_secretsmanager_secret.openai_api_key.arn,
        aws_secretsmanager_secret.google_places_api_key.arn,
      ]
    }]
  })
}
