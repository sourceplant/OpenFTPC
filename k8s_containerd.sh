#!/bin/bash
# Set up a containerd (Container Runtime) on worker node.
# Use: bash <(curl -s https://raw.githubusercontent.com/sourceplant/sourceleaf/master/k8s_containerd.sh)
GREEN=$'\e[0;32m' ; RED=$'\e[0;31m' ; NC=$'\e[0m'
^^^^^ () {
echo "#"
echo "# ${RED}${BASH_COMMAND}${NC}"
read -r -p "" -t 120 -n 1 -s
# If you wanted to see output in a clear screen, keep +v -v section disabled
#clear
#echo "# ${RED}${BASH_COMMAND}${NC}"
}
# If you wanted to enable comments on screen
#set +v
trap '^^^^^' DEBUG
#set -v
# The container runtime is the software that is responsible for running containers.
# Kubernetes supports several container runtimes: Docker
#, containerd
#, CRI-O
#, and any implementation of the Kubernetes CRI (Container Runtime Interface).
# This is about <containerd>
#####  Containerd - A container Runtime #########
# Runtimes Component/feature -  in of scope.
  # execution       - Provide an extensible execution layer for executing a container - in - Create,start, stop pause, resume exec, signal, delete
  # cow filesystem  - Built in functionality for overlay, aufs, and other copy on write filesystems for containers - in
  # distribution    - Having the ability to push and pull images as well as operations on images as a first class API object - in 
  # metrics         - container-level metrics, cgroup stats, and OOM events - in

# Runtime Componente/feature - out of scope
  # networking      - creation and management of network interfaces - Networking will be handled and provided to containerd via higher level systems.
  # build           - Build is a higher level tooling feature and can be implemented in many different ways on top of containerd
  # volumes         - The API supports mounts, binds, etc where all volumes type systems can be built on top of containerd.
  # logging         - Persisting container logs - Logging can be build on top of containerd because the container’s STDIO will be provided to the clients and they can persist any way they see fit. There is no io copying of container STDIO in containerd.

# Note: containerd is designed to be embedded into a larger system, hence it only includes a barebone CLI (ctr) specifically for development and debugging purpose, with no mandate to be human-friendly, and no guarantee of interface stability over time.

# Check system..
uname -n

# Persist and load overlay and br_netfilter
cat > /etc/modules-load.d/containerd.conf <<EOF
overlay
br_netfilter
EOF

modprobe overlay
modprobe br_netfilter

# Setup required sysctl params, these persist across reboots.
cat > /etc/sysctl.d/99-kubernetes-cri.conf <<EOF
net.bridge.bridge-nf-call-iptables  = 1
net.ipv4.ip_forward                 = 1
net.bridge.bridge-nf-call-ip6tables = 1
EOF

sysctl --system

### Install required packages
yum install -y yum-utils device-mapper-persistent-data lvm2

# Download the containerd tarball...
cd /tmp && curl -C -O -L https://github.com/containerd/containerd/releases/download/v1.3.4/containerd-1.3.4.linux-amd64.tar.gz && cd ~ 

# Extract to /usr ...  
cd /usr && tar -xvf /tmp/containerd-1.3.4.linux-amd64.tar.gz && cd ~

# Package does not contain any systemd service files, crete one....
cat > /usr/lib/systemd/system/containerd.service << EOF
[Unit]
Description=containerd container runtime
Documentation=https://containerd.io
After=network.target

[Service]
ExecStartPre=-/sbin/modprobe overlay
ExecStart=/usr/bin/containerd
KillMode=process
Delegate=yes
LimitNOFILE=1048576
# Having non-zero Limit*s causes performance problems due to accounting overhead
# in the kernel. We recommend using cgroups to do container-local accounting.
LimitNPROC=infinity
LimitCORE=infinity
TasksMax=infinity

[Install]
WantedBy=multi-user.target
EOF

# Verify the service file
ls -l /usr/lib/systemd/system/containerd.service

# Package does not contain any config file, creating one...
mkdir -p /etc/containerd
containerd config default > /etc/containerd/config.toml

# Note: You can check all supported plugins in previously created /etc/containerd/config.toml file...
cat /etc/containerd//config.toml | grep -i plugin

# Note: Indentation shows the plugin configs
# Use cases of this config file
# plugins."io.containerd.grpc.v1.cri" is the cri interface for kubelet, earlier it was a standalone binary cri-containerd
# this plugin allows to specify runtime, snapshotter, registry mirrors, cni, x509_key_pair_streaming etc.
# default runtime is runc, snapshotter is "overlayfs", registry is docker
# You can configure CNI plugin for networking
# You can configure runtime for kata etc..  
pwd

# A systemd init system eg centos7, the init process consumes "cgroup" and  acts as a cgroup manager
# Container runtime and the kubelet use the "cgroupfs" cgroup manager
# Two managers end up with two views of the resources and could cause unstability
# Use systemd as the cgroup driver for container runtime as well.
# To use the systemd cgroup driver instead of cgroups, set plugins.cri.systemd_cgroup = true in /etc/containerd/config.toml.


# Let me explain overlay concept, why it is here..
# ? Docker images can be really big
# ? every container needs a copy of its image
# solution: overlay - containers can use the same base image without wasting space
# ? how docker uses overlay 
# 1. Unpacks the base image in a directory, 2. makes an empty directory for changes, 3. overlay the new directory on top of base 4. start the container.
# Basically:
# the lower directory of the filesystem is read-only
# the upper directory of the filesystem can be both read to and written from
# When a process reads a file, the overlayfs filesystem driver looks in the upper directory and reads the file from there if it’s present. Otherwise, it looks in the lower directory.
# When a process writes a file, overlayfs will just write it to the upper directory.
pwd

# Note:
# The overlay and overlay2 drivers are supported on xfs backing filesystems
# but only with d_type=true enabled. Use xfs_info to verify that the ftype option is set to 1.
# To format an xfs filesystem correctly, use the flag -n ftype=1
pwd

# Check ftype for the filesystem
xfs_info / | grep -Po "ftype([^$]*)"

# If ftype is 0, use a separate backing filesystem from the one used by /var/lib/,
# create and mount it into /var/lib/containerd.
# Make sure add this mount to /etc/fstab to make it permanent.
# Below are the commands, leaving on you to decide
cat << EOF
mkfs.xfs -f -n ftype=1 /dev/<disk>
mkdir -p /var/lib/containerd/
mount /dev/sdb/ /var/lib/containerd/
EOF

# Start up the containerd...
systemctl status containerd.service
systemctl enable containerd.service
systemctl start containerd.service
systemctl status containerd.service

# Test the setup...
# Fetch a image from docker repo
ctr image pull docker.io/library/hello-world:latest

# List the image
ctr image ls

# List the image... friendly one
ctr image list -q

# Check where it is stored on system..
find /var/lib/containerd/io.containerd.snapshotter.v1.overlayfs

# Run a container demo with the image..
ctr container create docker.io/library/hello-world:latest demo

# Oops, Note: the output is not redirected to the CLI by default. Unlike Docker, we need to use the full path with the object every time we use a container image. Also, the image needs to be pulled before being able to run a container, which we already did in last step.

# Whatever, lets check is it up..
ctr container list

# To delete the image.
ctr image remove docker.io/library/hello-world:latest
# ? This would delete the image. What would happen to your container?
ctr container list
# Your container would still be running. This is because containerd works on references, and in this case, the image is no longer being referenced as an image but it is still being referenced by the container (as a snapshot), so it wouldn’t be deleted as long as it’s being referenced.
# ? Did not get it, now worries

# Delete the container
ctr container remove demo
