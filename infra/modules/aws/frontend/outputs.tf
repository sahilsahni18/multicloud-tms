output "bucket" {
  description = "CD uploads frontend/dist here."
  value       = aws_s3_bucket.this.bucket
}

output "distribution_id" {
  description = "CD invalidates this after an upload."
  value       = aws_cloudfront_distribution.this.id
}

output "domain_name" {
  value = aws_cloudfront_distribution.this.domain_name
}

output "hosted_zone_id" {
  value = aws_cloudfront_distribution.this.hosted_zone_id
}
