#!/bin/bash
# PeSIT Wizard - Cleanup k3d cluster

echo "Deleting k3d cluster 'pesitwizard'..."
k3d cluster delete pesitwizard

echo "Cleanup complete."
