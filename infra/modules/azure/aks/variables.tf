variable "name" {
  description = "Cluster name prefix; clusters are named <name>-1, <name>-2, ..."
  type        = string
}

variable "location" {
  type = string
}

variable "resource_group_name" {
  type = string
}

variable "cluster_count" {
  type    = number
  default = 1

  validation {
    condition     = var.cluster_count >= 1 && var.cluster_count <= 5
    error_message = "cluster_count must be between 1 and 5."
  }
}

variable "kubernetes_version" {
  description = "null = the region's current default."
  type        = string
  default     = null
}

variable "node_vm_size" {
  # The free 750 h size (B2ats v2) has only 1 GiB RAM, below what AKS needs for
  # a system pool plus the backend. B2als v2 (2 vCPU / 4 GiB) is the smallest
  # size that fits; it is paid from the student credit.
  type    = string
  default = "Standard_B2als_v2"
}

variable "node_count" {
  type    = number
  default = 1
}

variable "grant_acr_pull" {
  description = "Give the clusters AcrPull on acr_id (needs User Access Administrator)."
  type        = bool
  default     = true
}

variable "acr_id" {
  description = "Registry the clusters pull from."
  type        = string
  default     = ""
}

variable "tags" {
  type    = map(string)
  default = {}
}
