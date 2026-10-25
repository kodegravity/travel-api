# API key secrets created by ../secrets. Values are set out of band, so they never enter Terraform state.
data "aws_secretsmanager_secret" "google_places_api_key" {
  name = var.google_places_api_key_secret_name
}

data "aws_secretsmanager_secret" "openai_api_key" {
  name = var.openai_api_key_secret_name
}

data "aws_secretsmanager_secret" "ticketmaster_api_key" {
  name = var.ticketmaster_api_key_secret_name
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

  # Empty, task-scoped scratch space mounted at /tmp.
  volume {
    name = "tmp"
  }

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
        { name = "TRAVEL_REDIS_ENABLED", value = tostring(var.redis_enabled) },
        { name = "REDIS_HOST", value = var.redis_host },
        { name = "REDIS_PORT", value = tostring(var.redis_port) },
        { name = "REDIS_SSL", value = "true" },
        { name = "GOOGLE_ROUTES_MAX_CALLS_PER_DAY", value = tostring(var.google_routes_max_calls_per_day) },
        { name = "GOOGLE_PLACE_PHOTOS_MAX_CALLS_PER_DAY", value = tostring(var.google_place_photos_max_calls_per_day) },
        { name = "EVENTS_PROVIDER", value = var.events_provider },
        { name = "EVENTS_MAX_CALLS_PER_DAY", value = tostring(var.events_max_calls_per_day) },
        { name = "LOGGING_STRUCTURED_FORMAT_CONSOLE", value = "ecs" },
        { name = "LOGGING_LEVEL_COM_AITRAVEL", value = "INFO" },
      ], local.okta_env)
      # ECS cannot start a task whose secret has no value, so the key is injected only when Google is enabled.
      secrets = concat(
        var.attraction_provider == "google" ? [
          { name = "GOOGLE_PLACES_API_KEY", valueFrom = data.aws_secretsmanager_secret.google_places_api_key.arn },
        ] : [],
        var.events_provider == "ticketmaster" ? [
          # The secret is key/value JSON; ECS injects just this field.
          { name = "TICKETMASTER_API_KEY", valueFrom = "${data.aws_secretsmanager_secret.ticketmaster_api_key.arn}:${var.ticketmaster_api_key_json_key}::" },
        ] : [],
      )
      stopTimeout = 30
      # Read-only root filesystem; /tmp is the only writable path (a task volume, see the image's VOLUME).
      readonlyRootFilesystem = true
      mountPoints            = [{ sourceVolume = "tmp", containerPath = "/tmp", readOnly = false }]
      volumesFrom            = []
      systemControls         = []
      healthCheck = {
        command  = ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:8082/actuator/health || exit 1"]
        interval = 30
        timeout  = 5
        retries  = 3
        # JVM startup takes 70-90 s on 0.25 vCPU; failed checks inside this window do not count.
        startPeriod = 150
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
  # Set once the messaging module exists: lets recommendation consume its SQS queue.
  task_role_arn = var.recommendation_task_role_arn == "" ? null : var.recommendation_task_role_arn

  # Empty, task-scoped scratch space mounted at /tmp.
  volume {
    name = "tmp"
  }

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
        { name = "TRAVEL_REDIS_ENABLED", value = tostring(var.redis_enabled) },
        { name = "REDIS_HOST", value = var.redis_host },
        { name = "REDIS_PORT", value = tostring(var.redis_port) },
        { name = "REDIS_SSL", value = "true" },
        { name = "TRAVEL_AI_MAX_TOKENS", value = tostring(var.ai_max_tokens) },
        { name = "TRAVEL_LIVE_AI_MAX_CALLS_PER_DAY", value = tostring(var.live_ai_max_calls_per_day) },
        { name = "TRAVEL_AI_TIMEOUT", value = var.ai_timeout },
        { name = "TRAVEL_CHAT_AI_MAX_CALLS_PER_DAY", value = tostring(var.chat_ai_max_calls_per_day) },
        { name = "TRAVEL_MESSAGING_ENABLED", value = tostring(var.messaging_enabled) },
        { name = "TRAVEL_MESSAGING_SESSION_QUEUE", value = var.recommendation_queue_name },
        { name = "PLACES_DISCOVERY_BASE_URL", value = "http://places-discovery:8082" },
        { name = "LOGGING_STRUCTURED_FORMAT_CONSOLE", value = "ecs" },
        { name = "LOGGING_LEVEL_COM_AITRAVEL", value = "INFO" },
      ], local.okta_env)
      # Injected only when AI is enabled, for the same reason as the Google key.
      secrets = var.ai_enabled ? [
        { name = "OPENAI_API_KEY", valueFrom = data.aws_secretsmanager_secret.openai_api_key.arn },
      ] : []
      stopTimeout = 30
      # Read-only root filesystem; /tmp is the only writable path (a task volume, see the image's VOLUME).
      readonlyRootFilesystem = true
      mountPoints            = [{ sourceVolume = "tmp", containerPath = "/tmp", readOnly = false }]
      volumesFrom            = []
      systemControls         = []
      healthCheck = {
        command     = ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:8081/actuator/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 150
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

  # Empty, task-scoped scratch space mounted at /tmp.
  volume {
    name = "tmp"
  }

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
        { name = "TRAVEL_MESSAGING_ENABLED", value = tostring(var.messaging_enabled) },
        { name = "TRAVEL_MESSAGING_TOPIC_ARN", value = var.messaging_topic_arn },
        { name = "TRAVEL_MESSAGING_NOTIFICATION_QUEUE", value = var.notification_queue_name },
        { name = "TRAVEL_SESSION_STORE", value = "dynamodb" },
        { name = "TRAVEL_SESSION_TABLE", value = var.session_table_name },
        { name = "TRAVEL_POSTGRES_ENABLED", value = tostring(var.postgres_enabled) },
        { name = "TRAVEL_POSTGRES_URL", value = var.postgres_url },
        { name = "TRAVEL_POSTGRES_SECRET_ID", value = var.postgres_secret_arn },
        { name = "TRAVEL_PUSH_ENABLED", value = tostring(var.push_enabled) },
        { name = "TRAVEL_PUSH_VAPID_PUBLIC_KEY", value = var.push_vapid_public_key },
        { name = "TRAVEL_PUSH_SUBJECT", value = var.push_subject },
        { name = "LOGGING_STRUCTURED_FORMAT_CONSOLE", value = "ecs" },
        { name = "LOGGING_LEVEL_COM_AITRAVEL", value = "INFO" },
      ], local.okta_env)
      # The VAPID private key, injected only while push is on.
      secrets = var.push_enabled ? [
        { name = "TRAVEL_PUSH_VAPID_PRIVATE_KEY", valueFrom = var.push_vapid_private_key_parameter_arn },
      ] : []
      stopTimeout = 30
      # ECS Exec needs a writable root filesystem, so the root is read-only only while Exec is off.
      readonlyRootFilesystem = !var.enable_execute_command
      mountPoints            = [{ sourceVolume = "tmp", containerPath = "/tmp", readOnly = false }]
      volumesFrom            = []
      systemControls         = []
      healthCheck = {
        command     = ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 150
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
  enable_execute_command        = var.enable_execute_command
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
