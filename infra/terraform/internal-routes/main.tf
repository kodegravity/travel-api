resource "aws_lb_target_group" "recommendation" {
  name        = "recommendation-tg"
  port        = 8081
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id

  health_check {
    path                = "/actuator/health"
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 3
    matcher             = "200"
  }
}

resource "aws_lb_target_group" "places" {
  name        = "places-tg"
  port        = 8082
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id

  health_check {
    path                = "/actuator/health"
    interval            = 30
    healthy_threshold   = 2
    unhealthy_threshold = 3
    matcher             = "200"
  }
}

resource "aws_lb_listener_rule" "recommendation" {
  listener_arn = var.listener_arn
  priority     = var.recommendation_rule_priority

  condition {
    path_pattern {
      values = ["/internal/v1/recommendations*"]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.recommendation.arn
  }
}

resource "aws_lb_listener_rule" "places" {
  listener_arn = var.listener_arn
  priority     = var.places_rule_priority

  condition {
    path_pattern {
      values = ["/internal/v1/places/*"]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.places.arn
  }
}

resource "aws_security_group_rule" "recommendation_from_alb" {
  type                     = "ingress"
  from_port                = 8081
  to_port                  = 8081
  protocol                 = "tcp"
  security_group_id        = var.recommendation_security_group_id
  source_security_group_id = var.alb_security_group_id
  description              = "ALB to recommendation-service"
}

resource "aws_security_group_rule" "places_from_alb" {
  type                     = "ingress"
  from_port                = 8082
  to_port                  = 8082
  protocol                 = "tcp"
  security_group_id        = var.places_security_group_id
  source_security_group_id = var.alb_security_group_id
  description              = "ALB to places-discovery-service"
}
