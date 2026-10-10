variable "repositories" {
  type    = list(string)
  default = ["trackflow-backend", "trackflow-frontend"]
}

variable "keep_images" {
  description = "Older images are expired to stay inside the free 500 MB."
  type        = number
  default     = 10
}
