#!/usr/bin/env python3
#
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0

"""
Run precompiled Kotlin/Native test binaries in a Docker container for a specific Linux distribution and architecture.

This requires Docker multiarch support, see https://docs.docker.com/build/building/multi-platform/ and https://github.com/multiarch/qemu-user-static
In GitHub we use a provided action for this: https://github.com/docker/setup-qemu-action

Locally you would need to run one of:

`docker run --rm --privileged multiarch/qemu-user-static --reset -p yes --credential yes`

OR

`docker run --privileged --rm tonistiigi/binfmt --install all`
"""

import argparse
import os
import subprocess
import shlex
import shutil

VERBOSE = False

DISTRO_TO_IMAGE_NAME = {
    "ubuntu-22.04": "public.ecr.aws/lts/ubuntu:22.04_stable",
    "al2023": "public.ecr.aws/amazonlinux/amazonlinux:2023",
    "al2": "public.ecr.aws/amazonlinux/amazonlinux:2"
}

DOCKER_PLATFORM_BY_ARCH = {
    "x64": "linux/amd64",
    "arm64": "linux/arm64"
}


def vprint(message):
    global VERBOSE
    if VERBOSE:
        print(message)


def running_in_github_action():
    """
    Test if currently running in a GitHub action or running locally
    :return: True if running in GH, False otherwise
    """
    return "GITHUB_WORKFLOW" in os.environ


def shell(command, cwd=None, check=True, capture_output=False):
    """
    Run a command
    :param command: command to run
    :param cwd: the current working directory to change to before executing the command
    :param check: flag indicating if the status code should be checked. When true an exception will be
    thrown if the command exits with a non-zero exit status.
    :returns: the subprocess CompletedProcess output
    """
    vprint(f"running `{command}`")
    return subprocess.run(command, shell=True, check=check, cwd=cwd, capture_output=capture_output)


def oci_executable():
    """
    Attempt to find the OCI container executor used to build and run docker containers
    """
    oci_exe = os.environ.get('OCI_EXE')
    if oci_exe is not None:
        return oci_exe
    
    executors = ['finch', 'podman', 'docker']

    for exe in executors:
        if shutil.which(exe) is not None:
            return exe

    print("cannot find container executor")
    exit(1)


def run_docker_test(opts): 
    """
    Run a docker test for a precompiled Kotlin/Native binary

    :param opts: the parsed command line options
    """
    platform = DOCKER_PLATFORM_BY_ARCH[opts.arch]
    oci_exe = oci_executable()

    test_bin_dir = os.path.abspath(opts.test_bin_dir)
    image_name = DISTRO_TO_IMAGE_NAME[opts.distro]
    path_to_exe = f'./linux{opts.arch.capitalize()}/debugTest/test.kexe'

    cmd = [
        oci_exe,
        'run',
        '--rm',
        f'-v{test_bin_dir}:/test',
    ]
    if not opts.no_system_certs:
        cmd.append(f'-v/etc/ssl:/etc/ssl')

    cmd.extend(
        [
            '-w/test',
            '-e DEBIAN_FRONTEND=noninteractive',
            '--platform',
            platform,
            image_name,
            path_to_exe,
        ]
    )

    cmd = shlex.join(cmd)
    print(cmd)
    shell(cmd)


def create_cli():
    parser = argparse.ArgumentParser(
        prog="run-container-test",
        description="Run cross platform test binaries in a container",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter
    )

    parser.add_argument("-v", "--verbose", help="enable verbose output", action="store_true")

    parser.add_argument("--distro", required=True, choices=DISTRO_TO_IMAGE_NAME.keys(), help="the distribution name to run the task on")
    parser.add_argument("--arch", required=True, choices=DOCKER_PLATFORM_BY_ARCH.keys(), help="the architecture to use")
    parser.add_argument("--test-bin-dir", required=True, help="the path to the test binary directory root")
    parser.add_argument("--no-system-certs", action='store_true', help="disable mounting system certificates into the container")

    return parser


def main():
    cli = create_cli()
    opts = cli.parse_args()
    if opts.verbose:
        global VERBOSE
        VERBOSE = True

    run_docker_test(opts)


if __name__ == '__main__':
    main()
