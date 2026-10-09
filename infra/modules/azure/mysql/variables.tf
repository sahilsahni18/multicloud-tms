variable "name" {
  description = "Globally unique server name."
  type        = string
}

variable "location" {
  type = string
}

variable "resource_group_name" {
  type = string
}

variable "sku_name" {
  description = "B_Standard_B1ms is the free 750 h size (Burstable: no read replicas)."
  type        = string
  default     = "B_Standard_B1ms"
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
  type    = number
  default = 7
}

variable "allowed_ips" {
  description = "Public IPs allowed through the firewall (the AKS egress IPs)."
  type        = list(string)
  default     = []
}

variable "tags" {
  type    = map(string)
  default = {}
}
