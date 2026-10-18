# Session store for travel-session-service (TRAVEL_SESSION_STORE=dynamodb).
# On-demand billing, so an idle table costs nothing beyond storage, which the always-free 25 GB tier covers.

resource "aws_dynamodb_table" "sessions" {
  name         = var.table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "sessionId"

  attribute {
    name = "sessionId"
    type = "S"
  }

  # Items are deleted in the background once purgeAt (epoch seconds) has passed. The app already treats them as gone.
  ttl {
    attribute_name = "purgeAt"
    enabled        = true
  }

  # Sessions are short-lived (2 hours at most), so continuous backups are not worth their cost.
  point_in_time_recovery {
    enabled = false
  }

  # Encrypted at rest with the AWS-owned key (no KMS charge).
  server_side_encryption {
    enabled = false
  }

  tags = {
    project = "roam-ai"
  }
}

# The travel-session task role already exists (passed to the task definition by ../ecs-services).
data "aws_iam_role" "travel_session_task" {
  name = var.task_role_name
}

# Least privilege: only the item operations the repository uses, only on this table.
data "aws_iam_policy_document" "sessions_access" {
  statement {
    actions = [
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:UpdateItem",
      "dynamodb:Scan",
    ]
    resources = [aws_dynamodb_table.sessions.arn]
  }
}

resource "aws_iam_role_policy" "sessions_access" {
  name   = "travel-session-dynamodb"
  role   = data.aws_iam_role.travel_session_task.id
  policy = data.aws_iam_policy_document.sessions_access.json
}
