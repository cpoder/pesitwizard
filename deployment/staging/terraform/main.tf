# PeSIT Wizard Staging Environment - Main Terraform Configuration
#
# This configuration creates a production-like staging environment for testing
# high-availability, performance, and security before production deployment.
#
# Supported cloud providers:
# - AWS (EKS)
# - GCP (GKE)
# - Azure (AKS)
#
# Usage:
#   terraform init
#   terraform plan -var-file=staging.tfvars
#   terraform apply -var-file=staging.tfvars

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.11"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.5"
    }
  }

  backend "s3" {
    # Configure backend in staging.tfvars
    # bucket = "pesitwizard-terraform-state"
    # key    = "staging/terraform.tfstate"
    # region = "eu-west-1"
  }
}

# Provider configurations
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Environment = var.environment
      Project     = "pesitwizard"
      ManagedBy   = "terraform"
    }
  }
}

# Local values
locals {
  cluster_name = "${var.project_name}-${var.environment}"
  common_tags = {
    Environment = var.environment
    Project     = var.project_name
    ManagedBy   = "terraform"
  }
}

# VPC for staging environment
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = "${local.cluster_name}-vpc"
  cidr = var.vpc_cidr

  azs             = var.availability_zones
  private_subnets = var.private_subnets
  public_subnets  = var.public_subnets

  enable_nat_gateway     = true
  single_nat_gateway     = var.environment == "staging" ? true : false
  enable_dns_hostnames   = true
  enable_dns_support     = true

  # Tags required for EKS
  public_subnet_tags = {
    "kubernetes.io/cluster/${local.cluster_name}" = "shared"
    "kubernetes.io/role/elb"                      = "1"
  }

  private_subnet_tags = {
    "kubernetes.io/cluster/${local.cluster_name}" = "shared"
    "kubernetes.io/role/internal-elb"             = "1"
  }

  tags = local.common_tags
}

# EKS Cluster
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 19.0"

  cluster_name    = local.cluster_name
  cluster_version = var.kubernetes_version

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  # Cluster endpoint access
  cluster_endpoint_public_access  = true
  cluster_endpoint_private_access = true

  # Enable IRSA
  enable_irsa = true

  # Cluster addons
  cluster_addons = {
    coredns = {
      most_recent = true
    }
    kube-proxy = {
      most_recent = true
    }
    vpc-cni = {
      most_recent = true
    }
    aws-ebs-csi-driver = {
      most_recent = true
    }
  }

  # Node groups
  eks_managed_node_groups = {
    # Main node group for pesitwizard workloads
    main = {
      name = "${local.cluster_name}-main"

      instance_types = var.node_instance_types
      capacity_type  = "ON_DEMAND"

      min_size     = var.min_nodes
      max_size     = var.max_nodes
      desired_size = var.desired_nodes

      disk_size = 100

      labels = {
        role = "main"
      }

      tags = merge(local.common_tags, {
        NodeGroup = "main"
      })
    }
  }

  # Fargate profiles (optional for burst workloads)
  fargate_profiles = var.enable_fargate ? {
    default = {
      name = "default"
      selectors = [
        {
          namespace = "kube-system"
          labels = {
            k8s-app = "kube-dns"
          }
        }
      ]
    }
  } : {}

  tags = local.common_tags
}

# Configure kubernetes provider
provider "kubernetes" {
  host                   = module.eks.cluster_endpoint
  cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args        = ["eks", "get-token", "--cluster-name", local.cluster_name]
  }
}

# Configure helm provider
provider "helm" {
  kubernetes {
    host                   = module.eks.cluster_endpoint
    cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)

    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args        = ["eks", "get-token", "--cluster-name", local.cluster_name]
    }
  }
}

# Staging namespace
resource "kubernetes_namespace" "staging" {
  metadata {
    name = "staging"

    labels = {
      environment = "staging"
      project     = "pesitwizard"
    }
  }

  depends_on = [module.eks]
}

# Resource quotas for staging namespace
resource "kubernetes_resource_quota" "staging" {
  metadata {
    name      = "staging-quota"
    namespace = kubernetes_namespace.staging.metadata[0].name
  }

  spec {
    hard = {
      "requests.cpu"    = "20"
      "requests.memory" = "40Gi"
      "limits.cpu"      = "40"
      "limits.memory"   = "80Gi"
      "pods"            = "100"
    }
  }
}

# Outputs
output "cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "EKS cluster endpoint"
  value       = module.eks.cluster_endpoint
  sensitive   = true
}

output "cluster_certificate_authority_data" {
  description = "Base64 encoded certificate data"
  value       = module.eks.cluster_certificate_authority_data
  sensitive   = true
}

output "configure_kubectl" {
  description = "Configure kubectl command"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${local.cluster_name}"
}

output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}
