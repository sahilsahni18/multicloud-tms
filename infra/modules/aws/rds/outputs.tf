output "address" {
  value = aws_db_instance.this.address
}

output "arn" {
  value = aws_db_instance.this.arn
}

output "jdbc_url" {
  value = "jdbc:mysql://${aws_db_instance.this.address}:3306/${var.database_name}?sslMode=REQUIRED"
}

output "username" {
  value = var.username
}

output "password" {
  value     = random_password.master.result
  sensitive = true
}
