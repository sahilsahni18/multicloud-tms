variable "name" {
  description = "Cluster name prefix; clusters are named <name>-1, <name>-2, ..."
  type        = string
}

variable "cluster_count" {
  description = "Number of clusters in this region (Deployment Portal allows 1-5)."
  type        = number
  default     = 1

  validation {
    condition     = var.cluster_count >= 1 && var.cluster_count <= 5
    error_message = "cluster_count must be between 1 and 5."
  }
}

variable "kubernetes_version" {
  type    = string
  default = "1.33"
}

variable "subnet_ids" {
  description = "Subnets in at least two availability zones."
  type        = list(string)
}

variable "node_instance_type" {
  description = "2 vCPU / 4 GiB fits the backend (2 x 512Mi), ingress and system pods. Must be Free-plan eligible on a Free plan account (c7i-flex.large is; t3.medium is not)."
  type        = string
  default     = "c7i-flex.large"
}

variable "node_capacity_type" {
  description = "ON_DEMAND or SPOT (SPOT is cheaper but can be reclaimed mid-demo)."
  type        = string
  default     = "ON_DEMAND"
}

variable "node_count" {
  type    = number
  default = 1
}

variable "admin_role_arns" {
  description = "IAM roles given cluster-admin, e.g. the GitHub Actions role."
  type        = list(string)
  default     = []
}
