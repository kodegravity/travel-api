# Shared Redis-compatible cache (ElastiCache Serverless, Valkey engine) for daily call caps and small caches.
# Cost: about $6/month at the 100 MB storage minimum. Reachable only from the service tasks, over TLS.

data "aws_subnet" "first" {
  id = var.subnet_ids[0]
}

resource "aws_security_group" "cache" {
  name        = "${var.name}-sg"
  description = "Valkey cache: TLS on 6379 (primary) and 6380 (reader) from the service tasks only"
  vpc_id      = data.aws_subnet.first.vpc_id

  tags = {
    Name = "${var.name}-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "from_tasks" {
  for_each = toset(var.client_security_group_ids)

  security_group_id            = aws_security_group.cache.id
  referenced_security_group_id = each.value
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6380
  description                  = "Valkey from service tasks"
}

resource "aws_elasticache_serverless_cache" "this" {
  name                 = var.name
  engine               = "valkey"
  major_engine_version = "8"
  description          = "ROAM AI shared daily call caps and small caches"

  subnet_ids         = var.subnet_ids
  security_group_ids = [aws_security_group.cache.id]

  # Cache data only: nothing here needs a backup.
  snapshot_retention_limit = 0

  cache_usage_limits {
    data_storage {
      maximum = var.max_data_storage_gb
      unit    = "GB"
    }
    ecpu_per_second {
      maximum = var.max_ecpu_per_second
    }
  }
}
