variable "name" {
  type = string
}

variable "location" {
  description = "Static Web Apps runs only in a few regions: westus2, centralus, eastus2, westeurope, eastasia."
  type        = string
  default     = "westeurope"
}

variable "resource_group_name" {
  type = string
}

variable "tags" {
  type    = map(string)
  default = {}
}
