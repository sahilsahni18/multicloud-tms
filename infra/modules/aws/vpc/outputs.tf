output "vpc_id" {
  value = aws_vpc.this.id
}

output "cidr_block" {
  value = aws_vpc.this.cidr_block
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "database_subnet_ids" {
  value = aws_subnet.database[*].id
}

output "public_route_table_id" {
  value = aws_route_table.public.id
}

output "database_route_table_id" {
  value = aws_route_table.database.id
}
