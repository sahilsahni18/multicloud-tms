# EKS cluster(s) with one small managed node group each.
# cluster_count drives the Deployment Portal's "clusters 1-5"; the free demo uses 1.
# Cost: US$0.10 per cluster-hour plus the node, paid from credits.

terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
  }
}

locals {
  clusters = { for i in range(var.cluster_count) : tostring(i + 1) => "${var.name}-${i + 1}" }
}

# ------------------------------------------------------------------ IAM -----

data "aws_iam_policy_document" "cluster_trust" {
  statement {
    actions = ["sts:AssumeRole", "sts:TagSession"]
    principals {
      type        = "Service"
      identifiers = ["eks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "cluster" {
  name               = "${var.name}-cluster"
  assume_role_policy = data.aws_iam_policy_document.cluster_trust.json
}

resource "aws_iam_role_policy_attachment" "cluster" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

data "aws_iam_policy_document" "node_trust" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "node" {
  name               = "${var.name}-node"
  assume_role_policy = data.aws_iam_policy_document.node_trust.json
}

resource "aws_iam_role_policy_attachment" "node" {
  for_each = toset([
    "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy",
    "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy",
    "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly",
  ])
  role       = aws_iam_role.node.name
  policy_arn = each.value
}

# -------------------------------------------------------------- clusters ----

resource "aws_eks_cluster" "this" {
  for_each = local.clusters

  name     = each.value
  version  = var.kubernetes_version
  role_arn = aws_iam_role.cluster.arn

  vpc_config {
    subnet_ids              = var.subnet_ids
    endpoint_public_access  = true
    endpoint_private_access = true
  }

  access_config {
    authentication_mode                         = "API"
    bootstrap_cluster_creator_admin_permissions = true
  }

  depends_on = [aws_iam_role_policy_attachment.cluster]
}

# Extra admins (the GitHub Actions role) besides whoever ran tofu apply.
resource "aws_eks_access_entry" "admin" {
  for_each = { for pair in setproduct(keys(local.clusters), var.admin_role_arns) : "${pair[0]}|${pair[1]}" => pair }

  cluster_name  = aws_eks_cluster.this[each.value[0]].name
  principal_arn = each.value[1]
}

resource "aws_eks_access_policy_association" "admin" {
  for_each = aws_eks_access_entry.admin

  cluster_name  = each.value.cluster_name
  principal_arn = each.value.principal_arn
  policy_arn    = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"
  access_scope {
    type = "cluster"
  }
}

resource "aws_eks_node_group" "this" {
  for_each = local.clusters

  cluster_name    = aws_eks_cluster.this[each.key].name
  node_group_name = "default"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = var.subnet_ids
  instance_types  = [var.node_instance_type]
  capacity_type   = var.node_capacity_type
  disk_size       = 20

  scaling_config {
    desired_size = var.node_count
    min_size     = var.node_count
    max_size     = var.node_count + 1
  }

  update_config {
    max_unavailable = 1
  }

  depends_on = [aws_iam_role_policy_attachment.node]
}

# The backend HPA scales on CPU, which needs metrics-server (AKS has it built in).
resource "aws_eks_addon" "metrics_server" {
  for_each = local.clusters

  cluster_name = aws_eks_cluster.this[each.key].name
  addon_name   = "metrics-server"

  depends_on = [aws_eks_node_group.this]
}
