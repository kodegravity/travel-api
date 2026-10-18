# API key secrets created by ../secrets. Values are set out of band, so they never enter Terraform state.
data "aws_secretsmanager_secret" "google_places_api_key" {
  name = var.google_places_api_key_secret_name
}

data "aws_secretsmanager_secret" "openai_api_key" {
  name = var.openai_api_key_secret_name
}

data "aws_ecs_cluster" "this" {
  cluster_name = var.cluster_name
}

locals {
  okta_env = [
    { name = "OKTA_ISSUER", value = var.okta_issuer },
    { name = "OKTA_AUDIENCE", value = var.okta_audience },
  ]
}

resource "aws_ecs_task_definition" "places_discovery" {
  family                   = "ai-travel-companion-places-discovery"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = var.execution_role_arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name         = "ai-travel-companion-places-discovery"
      image        = "${var.ecr_registry}/ai-travel-companion-places-discovery:${var.image_tag_places_discovery}"
      essential    = true
      user         = "10001:10001"
      portMappings = [{ name = "http", containerPort = 8082, hostPort = 8082, protocol = "tcp", appProtocol = "http" }]
      environment = concat([
        { name = "SERVER_PORT", value = "8082" },
        { name = "ATTRACTION_PROVIDER", value = var.attraction_provider },
        { name = "GOOGLE_PLACES_MAX_CALLS_PER_DAY", value = tostring(var.google_places_max_calls_per_day) },
        { name = "LOGGING_STRUCTURED_FORMAT_CONSOLE", value = "ecs" },
        { name = "LOGGING_LEVEL_COM_AITRAVEL", value = "INFO" },
      ], local.okta_env)
      # ECS cannot start a task whose secret has no value, so the key is injected only when Google is enabled.
      secrets = var.attraction_provider == "google" ? [
        { name = "GOOGLE_PLACES_API_KEY", valueFrom = data.aws_secretsmanager_secret.google_places_api_key.arn },
      ] : []
      stopTimeout    = 30
      mountPoints    = []
      volumesFrom    = []
      systemControls = []
      healthCheck = {
        command     = ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:8082/actuator/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 30
      }
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = "/ecs/ai-travel-companion-places-discovery"
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    },
  ])
}

resource "aws_ecs_task_definition" "recommendation" {
  family                   = "ai-travel-companion-recommendation"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = var.execution_role_arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name         = "ai-travel-companion-recommendation"
      image        = "${var.ecr_registry}/ai-travel-companion-recommendation:${var.image_tag_recommendation}"
      essential    = true
      user         = "10001:10001"
      portMappings = [{ name = "http", containerPort = 8081, hostPort = 8081, protocol = "tcp", appProtocol = "http" }]
      environment = concat([
        { name = "SERVER_PORT", value = "8081" },
        { name = "TRAVEL_AI_ENABLED", value = tostring(var.ai_enabled) },
        { name = "TRAVEL_AI_MAX_CALLS_PER_DAY", value = tostring(var.ai_max_calls_per_day) },
        { name = "TRAVEL_AI_MAX_TOKENS", value = tostring(var.ai_max_tokens) },
        { name = "PLACES_DISCOVERY_BASE_URL", value = "http://places-discovery:8082" },
        { name = "LOGGING_STRUCTURED_FORMAT_CONSOLE", value = "ecs" },
        { name = "LOGGING_LEVEL_COM_AITRAVEL", value = "INFO" },
      ], local.okta_env)
      # Injected only when AI is enabled, for the same reason as the Google key.
      secrets = var.ai_enabled ? [
        { name = "OPENAI_API_KEY", valueFrom = data.aws_secretsmanager_secret.openai_api_key.arn },
      ] : []
      stopTimeout    = 30
      mountPoints    = []
      volumesFrom    = []
      systemControls = []
      healthCheck = {
        command     = ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:8081/actuator/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 30
      }
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = "/ecs/ai-travel-companion-recommendation"
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    },
  ])
}

resource "aws_ecs_task_definition" "travel_session" {
  family                   = "ai-travel-companion-travel-session"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = var.execution_role_arn
  task_role_arn            = var.travel_session_task_role_arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name         = "ai-travel-companion-travel-session"
      image        = "${var.ecr_registry}/ai-travel-companion-travel-session:${var.image_tag_travel_session}"
      essential    = true
      user         = "10001:10001"
      portMappings = [{ name = "http", containerPort = 8080, hostPort = 8080, protocol = "tcp", appProtocol = "http" }]
      environment = concat([
        { name = "SERVER_PORT", value = "8080" },
        { name = "RECOMMENDATION_SERVICE_BASE_URL", value = "http://recommendation:8081" },
        { name = "CORS_ALLOWED_ORIGINS", value = var.cors_allowed_origins },
        { name = "TRAVEL_SESSION_STORE", value = "dynamodb" },
        { name = "TRAVEL_SESSION_TABLE", value = var.session_table_name },
        { name = "LOGGING_STRUCTURED_FORMAT_CONSOLE", value = "ecs" },
        { name = "LOGGING_LEVEL_COM_AITRAVEL", value = "INFO" },
      ], local.okta_env)
      stopTimeout    = 30
      mountPoints    = []
      volumesFrom    = []
      systemControls = []
      healthCheck = {
        command     = ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 30
      }
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = "/ecs/ai-travel-companion-travel-session"
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    },
  ])
}

resource "aws_ecs_service" "places_discovery" {
  name                          = "places-discovery"
  cluster                       = data.aws_ecs_cluster.this.arn
  task_definition               = "${aws_ecs_task_definition.places_discovery.family}:${aws_ecs_task_definition.places_discovery.revision}"
  desired_count                 = var.desired_count
  launch_type                   = "FARGATE"
  availability_zone_rebalancing = "ENABLED"

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = [var.places_security_group_id]
    assign_public_ip = true
  }


  service_connect_configuration {
    enabled   = true
    namespace = var.service_connect_namespace_arn

    service {
      port_name      = "http"
      discovery_name = "places-discovery"

      client_alias {
        dns_name = "places-discovery"
        port     = 8082
      }
    }
  }
}

resource "aws_ecs_service" "recommendation" {
  name                          = "recommendation"
  cluster                       = data.aws_ecs_cluster.this.arn
  task_definition               = "${aws_ecs_task_definition.recommendation.family}:${aws_ecs_task_definition.recommendation.revision}"
  desired_count                 = var.desired_count
  launch_type                   = "FARGATE"
  availability_zone_rebalancing = "ENABLED"

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = [var.recommendation_security_group_id]
    assign_public_ip = true
  }


  service_connect_configuration {
    enabled   = true
    namespace = var.service_connect_namespace_arn

    service {
      port_name      = "http"
      discovery_name = "recommendation"

      client_alias {
        dns_name = "recommendation"
        port     = 8081
      }
    }
  }
}

resource "aws_ecs_service" "travel_session" {
  name                          = "travel-session"
  cluster                       = data.aws_ecs_cluster.this.arn
  task_definition               = "${aws_ecs_task_definition.travel_session.family}:${aws_ecs_task_definition.travel_session.revision}"
  desired_count                 = var.desired_count
  launch_type                   = "FARGATE"
  enable_execute_command        = true
  availability_zone_rebalancing = "ENABLED"

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = [var.travel_session_security_group_id]
    assign_public_ip = true
  }


  # Registered as a server so API Gateway can find the task in Cloud Map (IP and port) through its VPC link.
  service_connect_configuration {
    enabled   = true
    namespace = var.service_connect_namespace_arn

    service {
      port_name      = "http"
      discovery_name = "travel-session"

      # ECS requires an alias for every Service Connect server; nothing calls travel-session by this name today.
      client_alias {
        dns_name = "travel-session"
        port     = 8080
      }
    }
  }
}
