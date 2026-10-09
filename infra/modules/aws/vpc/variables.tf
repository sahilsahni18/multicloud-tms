variable "name" {
  description = "Name prefix, e.g. trackflow-dev-use1."
  type        = string
}

variable "cidr_block" {
  description = "VPC range. Must not overlap with the peered VPC in the other region."
  type        = string
}
