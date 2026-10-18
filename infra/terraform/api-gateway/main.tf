# Single public entry point: HTTPS API Gateway (HTTP API) -> VPC link -> travel-session.
# recommendation and places-discovery stay private; travel-session reaches them over Service Connect.

data "aws_subnet" "first" {
  id = var.subnet_ids[0]
}

# Service Connect registers each travel-session task here with AWS_INSTANCE_IPV4 and AWS_INSTANCE_PORT,
# which is what an HTTP API private integration needs. ../ecs-services must be applied first.
data "aws_service_discovery_service" "travel_session" {
  name         = var.travel_session_discovery_name
  namespace_id = var.service_connect_namespace_id
}

resource "aws_security_group" "vpc_link" {
  name        = "${var.api_name}-vpc-link"
  description = "API Gateway VPC link to travel-session"
  vpc_id      = data.aws_subnet.first.vpc_id

  tags = {
    project = "roam-ai"
  }
}

resource "aws_vpc_security_group_egress_rule" "vpc_link_to_travel_session" {
  security_group_id            = aws_security_group.vpc_link.id
  referenced_security_group_id = var.travel_session_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  description                  = "VPC link to travel-session"
}

resource "aws_vpc_security_group_ingress_rule" "travel_session_from_vpc_link" {
  security_group_id            = var.travel_session_security_group_id
  referenced_security_group_id = aws_security_group.vpc_link.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  description                  = "API Gateway VPC link"
}

# VPC links for HTTP APIs have no hourly charge.
resource "aws_apigatewayv2_vpc_link" "this" {
  name               = var.api_name
  subnet_ids         = var.subnet_ids
  security_group_ids = [aws_security_group.vpc_link.id]

  tags = {
    project = "roam-ai"
  }
}

resource "aws_apigatewayv2_api" "this" {
  name          = var.api_name
  protocol_type = "HTTP"
  description   = "ROAM / AI public API. Fronts travel-session only."
  # CORS stays in travel-session (WebCorsConfig), which already validates origins; preflights pass through.

  tags = {
    project = "roam-ai"
  }
}

# Rejects missing, expired, or foreign tokens before they reach Fargate. travel-session checks them again.
resource "aws_apigatewayv2_authorizer" "okta" {
  api_id           = aws_apigatewayv2_api.this.id
  name             = "okta"
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]

  jwt_configuration {
    issuer   = var.okta_issuer
    audience = [var.okta_audience]
  }
}

resource "aws_apigatewayv2_integration" "travel_session" {
  api_id                 = aws_apigatewayv2_api.this.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  integration_uri        = data.aws_service_discovery_service.travel_session.arn
  connection_type        = "VPC_LINK"
  connection_id          = aws_apigatewayv2_vpc_link.this.id
  payload_format_version = "1.0"
  # Below the 30 s maximum and above travel-session's own 10 s downstream timeout.
  timeout_milliseconds = 29000
}

resource "aws_apigatewayv2_route" "api" {
  api_id             = aws_apigatewayv2_api.this.id
  route_key          = "ANY /api/v1/{proxy+}"
  target             = "integrations/${aws_apigatewayv2_integration.travel_session.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.okta.id
}

# Browsers send CORS preflights without a token, so they must not hit the JWT authorizer.
resource "aws_apigatewayv2_route" "preflight" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "OPTIONS /api/v1/{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.travel_session.id}"
}

resource "aws_cloudwatch_log_group" "access" {
  name              = "/aws/apigateway/${var.api_name}"
  retention_in_days = var.access_log_retention_days

  tags = {
    project = "roam-ai"
  }
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.this.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_rate_limit  = var.throttling_rate_limit
    throttling_burst_limit = var.throttling_burst_limit
  }

  # No headers, query strings, or bodies, so tokens and coordinates never reach the logs.
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.access.arn
    format = jsonencode({
      requestId          = "$context.requestId"
      time               = "$context.requestTime"
      method             = "$context.httpMethod"
      path               = "$context.path"
      status             = "$context.status"
      latencyMs          = "$context.responseLatency"
      integrationStatus  = "$context.integrationStatus"
      integrationLatency = "$context.integrationLatency"
      authorizerError    = "$context.authorizer.error"
    })
  }

  tags = {
    project = "roam-ai"
  }
}
