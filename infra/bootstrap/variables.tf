variable "github_repository" {
  description = "owner/name of the GitHub repository allowed to assume the cloud roles."
  type        = string

  validation {
    condition     = can(regex("^[^/]+/[^/]+$", var.github_repository))
    error_message = "Use the form owner/name, e.g. octocat/multicloud-tms."
  }
}

variable "github_immutable_subject_prefix" {
  description = <<-EOT
    OIDC subject prefix when the repo uses immutable subjects, e.g.
    repo:octocat@123/multicloud-tms@456. Find it with:
    gh api repos/<owner>/<repo>/actions/oidc/customization/sub --jq .sub_claim_prefix
    Empty = classic subjects only.
  EOT
  type        = string
  default     = ""
}

variable "aws_region" {
  description = "Region for the S3 state bucket."
  type        = string
  default     = "us-east-1"
}

variable "azure_subscription_id" {
  description = "Azure subscription (az account show --query id -o tsv)."
  type        = string
}

variable "azure_location" {
  description = "Region for the state storage account (must be allowed by your subscription)."
  type        = string
  default     = "eastasia"
}
