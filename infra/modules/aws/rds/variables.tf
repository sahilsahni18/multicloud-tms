variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  description = "Database subnets in at least two availability zones."
  type        = list(string)
}

variable "allowed_security_group_ids" {
  description = "Security groups allowed to connect (EKS node groups in this VPC)."
  type        = list(string)
  default     = []
}

variable "allowed_cidr_blocks" {
  description = "Ranges allowed to connect (the peered standby VPC)."
  type        = list(string)
  default     = []
}

variable "engine_version" {
  type    = string
  default = "8.0"
}

variable "instance_class" {
  description = "Free-tier eligible size."
  type        = string
  default     = "db.t4g.micro"
}

variable "database_name" {
  type    = string
  default = "trackflow"
}

variable "username" {
  type    = string
  default = "trackflow"
}

variable "backup_retention_days" {
  description = "Automated backups are the DR source (RPO <= 24 h). Must be > 0 for replicas. Free-plan accounts may reject more than 1 day; raise it on a paid account."
  type        = number
  default     = 1
}
