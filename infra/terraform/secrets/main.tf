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

resource "aws_secretsmanager_secret" "ticketmaster_api_key" {
  name        = "ai-travel-app/ticketmaster-api-key"
  description = "Ticketmaster Discovery API consumer key for live events in places-discovery (free tier). Plain text."
}

# Browser key for the Google Maps JavaScript API. Read only by the web app's dev script (it ends up in the
# browser by design, so it must be restricted in Google Cloud to the site's referrers and the Maps JavaScript API).
# No ECS task reads it.
resource "aws_secretsmanager_secret" "google_maps_browser_key" {
  name        = "ai-travel-app/google-maps-browser-key"
  description = "Google Maps JavaScript API browser key for the web app (VITE_GOOGLE_MAPS_API_KEY)."
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
        aws_secretsmanager_secret.ticketmaster_api_key.arn,
      ]
    }]
  })
}
